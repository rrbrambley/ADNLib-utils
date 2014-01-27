package com.alwaysallthetime.messagebeast.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.alwaysallthetime.adnlib.GeneralParameter;
import com.alwaysallthetime.adnlib.QueryParameters;
import com.alwaysallthetime.adnlib.data.Annotation;
import com.alwaysallthetime.adnlib.data.Channel;
import com.alwaysallthetime.adnlib.data.Message;
import com.alwaysallthetime.messagebeast.ADNApplication;
import com.alwaysallthetime.messagebeast.AnnotationFactory;
import com.alwaysallthetime.messagebeast.AnnotationUtility;
import com.alwaysallthetime.messagebeast.PrivateChannelUtility;
import com.alwaysallthetime.messagebeast.db.ADNDatabase;
import com.alwaysallthetime.messagebeast.db.ActionMessageSpec;
import com.alwaysallthetime.messagebeast.model.MessagePlus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * The ActionMessageManager is used to perform mutable actions on Messages.<br><br>
 *
 * Since Annotations are not mutable, user-invoked actions on individual Messages (e.g.
 * marking a Message as a favorite, as read/unread, etc.) are not manageable with the App.net API.
 * This Manager class is used to hack around this limitation.<br><br>
 *
 * We use Action Channels (Channels of type com.alwaysallthetime.action)
 * with machine-only Messages to perform Actions on another Channel's Messages. These Action Messages
 * have metadata annotations (type com.alwaysallthetime.action.metadata) that point to their associated
 * "target" message in another Channel. All Messages in an Action Channel correspond to the same
 * action (i.e. there is only one action per Action Channel). Since Messages can be deleted, deleting
 * an Action Message effectively undoes a performed Action on the target Message.<br><br>
 *
 * The ActionMessageManager abstracts away this hack by providing simple applyChannelAction()
 * and removeChannelAction() methods. Before performing either of these actions, you must call
 * initActionChannel() to create or get an existing Channel to host the Action Messages.
 * To check if an action has been performed on a specific target message, use the isActioned() method.
 */
public class ActionMessageManager {
    private static final String TAG = "ADNLibUtils_ActionMessageManager";

    public static final QueryParameters ACTION_MESSAGE_QUERY_PARAMETERS = new QueryParameters(GeneralParameter.INCLUDE_MACHINE,
            GeneralParameter.INCLUDE_MESSAGE_ANNOTATIONS, GeneralParameter.EXCLUDE_DELETED);

    private static final int MAX_BATCH_LOAD_FROM_DISK = 40;

    private MessageManager mMessageManager;
    private ADNDatabase mDatabase;

    private HashMap<String, Channel> mActionChannels;

    public interface ActionChannelInitializedHandler {
        public void onInitialized(Channel channel);
        public void onException(Exception exception);
    }

    private static ActionMessageManager sActionMessageManager;
    public static ActionMessageManager getInstance(MessageManager messageManager) {
        if(sActionMessageManager == null) {
            sActionMessageManager = new ActionMessageManager(messageManager);
        }
        return sActionMessageManager;
    }

    private ActionMessageManager(MessageManager messageManager) {
        mMessageManager = messageManager;
        mActionChannels = new HashMap<String, Channel>(1);

        Context context = ADNApplication.getContext();
        mDatabase = ADNDatabase.getInstance(context);
        context.registerReceiver(sentMessageReceiver, new IntentFilter(MessageManager.INTENT_ACTION_UNSENT_MESSAGES_SENT));
    }

    /**
     * Get the MessageManager used by this ActionMessageManager
     *
     * @return MessageManager
     */
    public MessageManager getMessageManager() {
        return mMessageManager;
    }

    /**
     * Sync and persist all Action Messages in a Channel.
     *
     * Instead of using the similar MessageManager method, this method should be used
     * to sync messages for an Action Channel. As batches of Messages are obtained, the Target Message id
     * for each Action Message will be extracted from annotations and stored to the sqlite database
     * for lookup at a later time.
     *
     * @param actionChannelId The id of the Action Channel for which messages will be synced.
     * @param responseHandler MessageManagerSyncResponseHandler
     *
     * @see com.alwaysallthetime.messagebeast.manager.MessageManager#retrieveAndPersistAllMessages(String, com.alwaysallthetime.messagebeast.manager.MessageManager.MessageManagerSyncResponseHandler)
     */
    public void retrieveAndPersistAllActionMessages(final String actionChannelId, final MessageManager.MessageManagerSyncResponseHandler responseHandler) {
        final String targetChannelId = getTargetChannelId(actionChannelId);
        mMessageManager.retrieveAndPersistAllMessages(actionChannelId, new MessageManager.MessageManagerSyncResponseHandler() {
            @Override
            public void onSuccess(List<MessagePlus> responseData, boolean appended) {
                responseHandler.onSuccess(responseData, appended);
                Log.d(TAG, "Synced " + getNumMessagesSynced() + " messages for action channel " + actionChannelId);
            }

            @Override
            public void onBatchSynced(List<MessagePlus> messages) {
                super.onBatchSynced(messages);

                for(MessagePlus actionMessage : messages) {
                    String targetMessageId = AnnotationUtility.getTargetMessageId(actionMessage.getMessage());
                    mDatabase.insertOrReplaceActionMessageSpec(actionMessage, targetMessageId, targetChannelId);
                }
            }

            @Override
            public void onError(Exception exception) {
                Log.e(TAG, exception.getMessage(), exception);
                responseHandler.onError(exception);
            }
        });
    }

    /**
     * Return true if the specified target Message has had an action performed on it (the action
     * whose id is actionChannelId).
     *
     * @param actionChannelId the id of the Action Channel
     * @param targetMessageId The id of the target Message
     *
     * @return true if the specified target Message has had an action performed on it.
     */
    public boolean isActioned(String actionChannelId, String targetMessageId) {
        return mDatabase.hasActionMessageSpec(actionChannelId, targetMessageId);
    }

    /**
     * Given a Collection of message Ids, return those which are associated with Messages that
     * have had an action performed on them (the action associated with the provided Action Channel id).
     *
     * This is more efficient than looping through message ids and calling isActioned() on them one
     * at a time because a single database query is used in this case.
     *
     * @param actionChannelId the id of the Action Channel
     * @param messageIds the ids of the target messages
     * @return a subset of the messageIds Collection, containing the ids associated with messages to
     * which the action has been applied.
     */
    public Set<String> getActionedMessageIds(String actionChannelId, Collection<String> messageIds) {
        return mDatabase.getTargetMessageIdsWithSpecs(actionChannelId, messageIds);
    }

    /**
     * Return true if there are any ActionMessageSpecs persisted for the provided Action Channel id.
     *
     * @param actionChannelId the id of the Action Channel
     * @return true if there are any ActionMessageSpecs persisted for the provided Action Channel id,
     * false otherwise.
     */
    public boolean hasActionedMessages(String actionChannelId) {
        return mDatabase.getActionMessageSpecCount(actionChannelId) > 0;
    }

    /**
     * Initialize an Action Channel. This is typically done at app startup and must be done before
     * any other ActionMessageManager methods are used on the channel.
     *
     * @param actionType The identifier for the Action Channel (e.g. com.alwaysallthetime.pizzaparty)
     * @param targetChannel The Channel whose messages will have actions performed.
     * @param handler ActionChannelInitializedHandler
     */
    public synchronized void initActionChannel(final String actionType, final Channel targetChannel, final ActionChannelInitializedHandler handler) {
        PrivateChannelUtility.getOrCreateActionChannel(mMessageManager.getClient(), actionType, targetChannel, new PrivateChannelUtility.PrivateChannelGetOrCreateHandler() {
            @Override
            public void onResponse(Channel channel, boolean createdNewChannel) {
                mActionChannels.put(channel.getId(), channel);
                mMessageManager.setParameters(channel.getId(), ACTION_MESSAGE_QUERY_PARAMETERS);
                handler.onInitialized(channel);
            }

            @Override
            public void onError(Exception error) {
                Log.e(TAG, error.getMessage(), error);
                handler.onException(error);
            }
        });
    }

    private Set<String> getTargetMessageIds(Collection<MessagePlus> messagePlusses) {
        HashSet<String> newTargetMessageIds = new HashSet<String>(messagePlusses.size());
        for(MessagePlus mp : messagePlusses) {
            newTargetMessageIds.add(AnnotationUtility.getTargetMessageId(mp.getMessage()));
        }
        return newTargetMessageIds;
    }

    /**
     * Get all target Messages that have an action applied.
     *
     * @param actionChannelId the the id of the Action Channel associated with the action of interest
     * @return a List consisting of MessagePlus Objects corresponding to Messages in the target Channel
     * that have had the action applied.
     */
    public synchronized List<MessagePlus> getActionedMessages(String actionChannelId) {
        LinkedHashMap<String, MessagePlus> loadedMessagesFromActionChannel =  mMessageManager.getMessages(actionChannelId, MAX_BATCH_LOAD_FROM_DISK);
        return getTargetMessages(loadedMessagesFromActionChannel.values());
    }

    private synchronized List<MessagePlus> getTargetMessages(Collection<MessagePlus> actionMessages) {
        Set<String> newTargetMessageIds = getTargetMessageIds(actionMessages);
        LinkedHashMap<String, MessagePlus> targetMessages = mMessageManager.getMessages(newTargetMessageIds);
        return new ArrayList<MessagePlus>(targetMessages.values());
    }

    //TODO: does this method even make sense?
    public synchronized void getMoreActionedMessages(final String actionChannelId, final MessageManager.MessageManagerResponseHandler responseHandler) {
        final String targetChannelId = getTargetChannelId(actionChannelId);
        LinkedHashMap<String, MessagePlus> more = mMessageManager.loadPersistedMessages(actionChannelId, MAX_BATCH_LOAD_FROM_DISK);
        if(more.size() > 0) {
            Set<String> newTargetMessageIds = getTargetMessageIds(more.values());
            LinkedHashMap<String, MessagePlus> moreTargetMessages = mMessageManager.getMessages(newTargetMessageIds);

            responseHandler.setIsMore(more.size() == MAX_BATCH_LOAD_FROM_DISK);
            responseHandler.onSuccess(new ArrayList(moreTargetMessages.values()), true);
        } else {
            //
            //load more action messages, then get the target messages
            //
            mMessageManager.retrieveMoreMessages(actionChannelId, new MessageManager.MessageManagerResponseHandler() {
                @Override
                public void onSuccess(List<MessagePlus> responseData, boolean appended) {
                    Set<String> newTargetMessageIds = getTargetMessageIds(responseData);
                    LinkedHashMap<String, MessagePlus> moreTargetMessages = mMessageManager.getMessages(newTargetMessageIds);

                    responseHandler.setIsMore(isMore());
                    responseHandler.onSuccess(new ArrayList(moreTargetMessages.values()), true);
                }

                @Override
                public void onError(Exception exception) {
                    Log.d(TAG, exception.getMessage(), exception);
                    responseHandler.onError(exception);
                }
            });
        }
    }

    /**
     * Retrieve the newest Messages in an Action Channel.
     *
     * @param actionChannelId the id of the Action Channel
     * @param responseHandler MessageManagerResponseHandler
     * @return false if unsent Messages are preventing more Messages from being retrieved, true otherwise.
     */
    public synchronized boolean retrieveNewestMessages(final String actionChannelId, final MessageManager.MessageManagerResponseHandler responseHandler) {
        final String targetChannelId = getTargetChannelId(actionChannelId);
        LinkedHashMap<String, MessagePlus> channelMessages = mMessageManager.getMessageMap(actionChannelId);
        if(channelMessages == null || channelMessages.size() == 0) {
            //we do this so that the max id is known.
            mMessageManager.loadPersistedMessages(actionChannelId, 1);
        }
        boolean canRetrieve = mMessageManager.retrieveNewestMessages(actionChannelId, new MessageManager.MessageManagerResponseHandler() {
            @Override
            public void onSuccess(List<MessagePlus> responseData, boolean appended) {
                for(MessagePlus actionMessage : responseData) {
                    String targetMessageId = AnnotationUtility.getTargetMessageId(actionMessage.getMessage());
                    mDatabase.insertOrReplaceActionMessageSpec(actionMessage, targetMessageId, targetChannelId);
                }
                responseHandler.onSuccess(responseData, appended);
            }

            @Override
            public void onError(Exception exception) {
                Log.d(TAG, exception.getMessage(), exception);
                responseHandler.onError(exception);
            }
        });
        return canRetrieve;
    }

    /**
     * Apply an Action Channel action to a target Message.
     *
     * Nothing happens if the provided target Message already has the action applied.
     *
     * @param actionChannelId the id of the Action Channel
     * @param targetMessagePlus the Message to have the action applied.
     */
    public synchronized void applyChannelAction(String actionChannelId, MessagePlus targetMessagePlus) {
        if(!isActioned(actionChannelId, targetMessagePlus.getMessage().getId())) {
            Message message = targetMessagePlus.getMessage();
            String targetMessageId = message.getId();
            //create machine only message in action channel that points to the target message id.
            Message m = new Message(true);
            Annotation a = AnnotationFactory.getSingleValueAnnotation(PrivateChannelUtility.MESSAGE_ANNOTATION_TARGET_MESSAGE, PrivateChannelUtility.TARGET_MESSAGE_KEY_ID, targetMessageId);
            m.addAnnotation(a);

            MessagePlus unsentActionMessage = mMessageManager.createUnsentMessageAndAttemptSend(actionChannelId, m);
            mDatabase.insertOrReplaceActionMessageSpec(unsentActionMessage, targetMessageId, message.getChannelId());
        }
    }

    /**
     * Remove an Action Channel action from a target Message.
     *
     * Nothing happens if the provided target Message does not already have the action applied.
     *
     * @param actionChannelId the id of the Action Channel
     * @param targetMessageId the id of the target Message
     */
    public synchronized void removeChannelAction(final String actionChannelId, final String targetMessageId) {
        ArrayList<String> targetMessageIds = new ArrayList<String>(1);
        targetMessageIds.add(targetMessageId);
        final List<ActionMessageSpec> actionMessageSpecs = mDatabase.getActionMessageSpecsForTargetMessages(actionChannelId, targetMessageIds);

        if(actionMessageSpecs.size() > 0) {
            mDatabase.deleteActionMessageSpec(actionChannelId, targetMessageId);

            deleteActionMessages(actionMessageSpecs, 0, new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Finished deleting " + actionMessageSpecs.size() + " action messages in channel " + actionChannelId);
                }
            });
        } else {
            Log.e(TAG, "Calling removeChannelAction, but actionChannelId " + actionChannelId + " and targetMessageId " + targetMessageId + " yielded 0 db results. wtf.");
        }
    }

    private String getTargetChannelId(String actionChannelId) {
        Channel actionChannel = mActionChannels.get(actionChannelId);
        String targetChannelId = null;
        if(actionChannel == null) {
            throw new RuntimeException("The specified Action Channel is unknown. Make sure you have called initActionChannel() before trying to use an Action Channel");
        } else if((targetChannelId = AnnotationUtility.getTargetChannelId(actionChannel)) == null) {
            throw new RuntimeException("The specified Channel does not have the proper Action Channel metadata Annotation; no target Channel is known.");
        }
        return targetChannelId;
    }

    private synchronized void deleteActionMessages(final List<ActionMessageSpec> actionMessageSpecs, final int currentIndex, final Runnable completionRunnable) {
        final ActionMessageSpec actionMessageSpec = actionMessageSpecs.get(0);
        final String actionChannelId = actionMessageSpec.getActionChannelId();
        MessagePlus actionMessagePlus = mDatabase.getMessage(actionMessageSpec.getActionMessageId());

        //the success/failure of this should not matter - on failure, it will be a pending deletion
        mMessageManager.deleteMessage(actionMessagePlus, new MessageManager.MessageDeletionResponseHandler() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully deleted action message " + actionMessageSpec.getActionMessageId() + " for target message " + actionMessageSpec.getTargetMessageId());
                deleteNext();
            }

            @Override
            public void onError(Exception exception) {
                Log.d(TAG, "Failed to delete action message " + actionMessageSpec.getActionMessageId() + " for target message " + actionMessageSpec.getTargetMessageId());
                deleteNext();
            }

            private void deleteNext() {
                int nextIndex = currentIndex + 1;
                if(nextIndex < actionMessageSpecs.size()) {
                    deleteActionMessages(actionMessageSpecs, nextIndex, completionRunnable);
                } else {
                    completionRunnable.run();
                }
            }
        });
    }

    private final BroadcastReceiver sentMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(MessageManager.INTENT_ACTION_UNSENT_MESSAGES_SENT.equals(intent.getAction())) {
                final String channelId = intent.getStringExtra(MessageManager.EXTRA_CHANNEL_ID);
                final ArrayList<String> sentMessageIds = intent.getStringArrayListExtra(MessageManager.EXTRA_SENT_MESSAGE_IDS);

                //this is not an action channel.
                //it might be a target channel of one of our action channels though.
                if(mActionChannels.get(channelId) == null) {
                    //remove all action messages that point to this now nonexistent target message id
                    List<ActionMessageSpec> sentTargetMessages = mDatabase.getActionMessageSpecsForTargetMessages(sentMessageIds);
                    for(ActionMessageSpec actionMessageSpec : sentTargetMessages) {
                        String actionChannelId = actionMessageSpec.getActionChannelId();
                        mDatabase.deleteActionMessageSpec(actionChannelId, actionMessageSpec.getTargetMessageId());
                    }
                } else {
                    //it's an action channel
                    //delete the action messages in the database with the sent message ids,
                    //retrieve the new ones

                    Channel actionChannel = mActionChannels.get(channelId);
                    final String targetChannelId = AnnotationUtility.getTargetChannelId(actionChannel);
                    mMessageManager.retrieveNewestMessages(channelId, new MessageManager.MessageManagerResponseHandler() {
                        @Override
                        public void onSuccess(List<MessagePlus> responseData, boolean appended) {
                            for(String sentMessageId : sentMessageIds) {
                                mDatabase.deleteActionMessageSpec(sentMessageId);
                            }
                            for(MessagePlus mp : responseData) {
                                String targetMessageId = AnnotationUtility.getTargetMessageId(mp.getMessage());
                                mDatabase.insertOrReplaceActionMessageSpec(mp, targetMessageId, targetChannelId);
                            }
                        }

                        @Override
                        public void onError(Exception exception) {
                            Log.e(TAG, exception.getMessage(), exception);
                        }
                    });
                }
            }
        }
    };

    private static Comparator<String> sIdComparator = new Comparator<String>() {
        @Override
        public int compare(String lhs, String rhs) {
            Integer lhsInteger = new Integer(Integer.parseInt(lhs));
            Integer rhsInteger = new Integer(Integer.parseInt(rhs));
            //we want desc order (reverse chronological order)
            return rhsInteger.compareTo(lhsInteger);
        }
    };
}
