package com.alwaysallthetime.adnlibutils.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.alwaysallthetime.adnlib.AppDotNetClient;
import com.alwaysallthetime.adnlib.GeneralParameter;
import com.alwaysallthetime.adnlib.QueryParameters;
import com.alwaysallthetime.adnlib.data.Annotation;
import com.alwaysallthetime.adnlib.data.Channel;
import com.alwaysallthetime.adnlib.data.Message;
import com.alwaysallthetime.adnlibutils.ADNApplication;
import com.alwaysallthetime.adnlibutils.AnnotationFactory;
import com.alwaysallthetime.adnlibutils.PrivateChannelUtility;
import com.alwaysallthetime.adnlibutils.db.ADNDatabase;
import com.alwaysallthetime.adnlibutils.db.ActionMessage;
import com.alwaysallthetime.adnlibutils.model.MessagePlus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class ActionMessageManager {
    private static final String TAG = "ADNLibUtils_ActionMessageManager";

    public static final QueryParameters FAVORITES_QUERY_PARAMETERS = new QueryParameters(GeneralParameter.INCLUDE_MACHINE,
            GeneralParameter.INCLUDE_MESSAGE_ANNOTATIONS, GeneralParameter.EXCLUDE_DELETED);

    private static final int MAX_BATCH_LOAD_FROM_DISK = 40;

    private MessageManager mMessageManager;
    private ADNDatabase mDatabase;

    //action channel id : (target message id : target message plus)
    private HashMap<String, LinkedHashMap<String, MessagePlus>> mActionedMessages;

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
        mActionedMessages = new HashMap<String, LinkedHashMap<String, MessagePlus>>(1);

        Context context = ADNApplication.getContext();
        mDatabase = ADNDatabase.getInstance(context);
        context.registerReceiver(sentMessageReceiver, new IntentFilter(MessageManager.INTENT_ACTION_UNSENT_MESSAGES_SENT));
    }

    public void retrieveAndPersistAllActionMessages(final String actionChannelId, final String targetChannelId, final MessageManager.MessageManagerSyncResponseHandler responseHandler) {
        mMessageManager.retrieveAndPersistAllMessages(actionChannelId, new MessageManager.MessageManagerSyncResponseHandler() {
            @Override
            public void onSuccess(List<MessagePlus> responseData, boolean appended) {
                responseHandler.onSuccess(responseData, appended);
            }

            @Override
            public void onBatchSynced(List<MessagePlus> messages) {
                super.onBatchSynced(messages);

                for(MessagePlus actionMessage : messages) {
                    String targetMessageId = getTargetMessageId(actionMessage);
                    mDatabase.insertOrReplaceActionMessage(actionMessage, targetMessageId, targetChannelId);
                }
            }

            @Override
            public void onError(Exception exception) {
                Log.e(TAG, exception.getMessage(), exception);
                //TODO
            }
        });
    }

    public boolean isActioned(String actionChannelId, String targetMessageId) {
        return getOrCreateActionMessageMap(actionChannelId).get(targetMessageId) != null ||
                mDatabase.hasActionMessage(actionChannelId, targetMessageId);
    }

    public synchronized void initActionChannel(final String actionType, final Channel targetChannel, final ActionChannelInitializedHandler handler) {
        Channel actionChannel = PrivateChannelUtility.getActionChannel(actionType, targetChannel.getId());
        if(actionChannel == null) {
            final AppDotNetClient client = mMessageManager.getClient();
            PrivateChannelUtility.retrieveActionChannel(client, actionType, targetChannel.getId(), new PrivateChannelUtility.PrivateChannelHandler() {
                @Override
                public void onResponse(Channel channel) {
                    if(channel == null) {
                        PrivateChannelUtility.createActionChannel(client, actionType, targetChannel.getId(), new PrivateChannelUtility.PrivateChannelHandler() {
                            @Override
                            public void onResponse(Channel channel) {
                                mActionChannels.put(channel.getId(), channel);
                                mMessageManager.setParameters(channel.getId(), FAVORITES_QUERY_PARAMETERS);
                                handler.onInitialized(channel);
                            }

                            @Override
                            public void onError(Exception error) {
                                Log.d(TAG, error.getMessage(), error);
                                handler.onException(error);
                            }
                        });
                    } else {
                        mActionChannels.put(channel.getId(), channel);
                        mMessageManager.setParameters(channel.getId(), FAVORITES_QUERY_PARAMETERS);
                        handler.onInitialized(channel);
                    }
                }

                @Override
                public void onError(Exception error) {
                    Log.d(TAG, error.getMessage(), error);
                    handler.onException(error);
                }
            });
        } else {
            mActionChannels.put(actionChannel.getId(), actionChannel);
            mMessageManager.setParameters(actionChannel.getId(), FAVORITES_QUERY_PARAMETERS);
            handler.onInitialized(actionChannel);
        }
    }

    private LinkedHashMap<String, MessagePlus> getOrCreateActionMessageMap(String channelId) {
        LinkedHashMap<String, MessagePlus> channelMap = mActionedMessages.get(channelId);
        if(channelMap == null) {
            channelMap = new LinkedHashMap<String, MessagePlus>();
            mActionedMessages.put(channelId, channelMap);
        }
        return channelMap;
    }

    private String getTargetMessageId(MessagePlus actionMessage) {
        Annotation targetMessage = actionMessage.getMessage().getFirstAnnotationOfType(PrivateChannelUtility.MESSAGE_ANNOTATION_TARGET_MESSAGE);
        if(targetMessage != null) {
            return (String) targetMessage.getValue().get(PrivateChannelUtility.TARGET_MESSAGE_KEY_ID);
        } else {
            Log.e(TAG, "Action message " + actionMessage.getMessage().getId() + " does not have target message annotation");
            return null;
        }
    }

    private Set<String> getTargetMessageIds(Collection<MessagePlus> messagePlusses) {
        HashSet<String> newTargetMessageIds = new HashSet<String>(messagePlusses.size());
        for(MessagePlus mp : messagePlusses) {
            newTargetMessageIds.add(getTargetMessageId(mp));
        }
        return newTargetMessageIds;
    }

    public synchronized List<MessagePlus> getActionedMessages(String actionChannelId, String targetChannelId) {
        LinkedHashMap<String, MessagePlus> channelActionedMessages = mActionedMessages.get(actionChannelId);
        if(channelActionedMessages == null) {
            LinkedHashMap<String, MessagePlus> loadedMessagesFromActionChannel = mMessageManager.getMessageMap(actionChannelId);
            if(loadedMessagesFromActionChannel == null || loadedMessagesFromActionChannel.size() == 0) {
                loadedMessagesFromActionChannel = mMessageManager.loadPersistedMessages(actionChannelId, MAX_BATCH_LOAD_FROM_DISK);
            }
            Set<String> newTargetMessageIds = getTargetMessageIds(loadedMessagesFromActionChannel.values());
            LinkedHashMap<String, MessagePlus> newTargetMessages = mMessageManager.loadAndConfigureTemporaryMessages(targetChannelId, newTargetMessageIds);
            mActionedMessages.put(actionChannelId, newTargetMessages);
            return new ArrayList<MessagePlus>(newTargetMessages.values());
        } else {
            return new ArrayList<MessagePlus>(channelActionedMessages.values());
        }
    }

    public synchronized void getMoreActionedMessages(final String actionChannelId, final String targetChannelId, final MessageManager.MessageManagerResponseHandler responseHandler) {
        LinkedHashMap<String, MessagePlus> more = mMessageManager.loadPersistedMessages(actionChannelId, MAX_BATCH_LOAD_FROM_DISK);
        if(more.size() > 0) {
            Set<String> newTargetMessageIds = getTargetMessageIds(more.values());
            LinkedHashMap<String, MessagePlus> moreTargetMessages = mMessageManager.loadAndConfigureTemporaryMessages(targetChannelId, newTargetMessageIds);

            //save them to the in-memory map
            LinkedHashMap<String, MessagePlus> channelActionMessages = getOrCreateActionMessageMap(actionChannelId);
            channelActionMessages.putAll(moreTargetMessages);

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
                    LinkedHashMap<String, MessagePlus> moreTargetMessages = mMessageManager.loadAndConfigureTemporaryMessages(targetChannelId, newTargetMessageIds);

                    //save them to the in-memory map
                    LinkedHashMap<String, MessagePlus> channelActionMessages = getOrCreateActionMessageMap(actionChannelId);
                    channelActionMessages.putAll(moreTargetMessages);

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

    public synchronized void applyChannelAction(String actionChannelId, MessagePlus targetMessagePlus) {
        LinkedHashMap<String, MessagePlus> actionedMessages = getOrCreateActionMessageMap(actionChannelId);
        Message message = targetMessagePlus.getMessage();
        String targetMessageId = message.getId();
        if(actionedMessages.get(targetMessageId) == null) {
            //create machine only message in action channel that points to the target message id.
            Message m = new Message(true);
            Annotation a = AnnotationFactory.getSingleValueAnnotation(PrivateChannelUtility.MESSAGE_ANNOTATION_TARGET_MESSAGE, PrivateChannelUtility.TARGET_MESSAGE_KEY_ID, targetMessageId);
            m.addAnnotation(a);

            MessagePlus unsentActionMessage = mMessageManager.createUnsentMessageAndAttemptSend(actionChannelId, m);

            LinkedHashMap<String, MessagePlus> newActionedMessages = new LinkedHashMap<String, MessagePlus>(actionedMessages.size() + 1);
            newActionedMessages.put(targetMessageId, targetMessagePlus);
            newActionedMessages.putAll(actionedMessages);
            mActionedMessages.put(actionChannelId, newActionedMessages);

            mDatabase.insertOrReplaceActionMessage(unsentActionMessage, targetMessageId, message.getChannelId());
        }
    }

    public synchronized void unapplyChannelAction(MessagePlus messagePlus) {
        //TODO
    }

    private final BroadcastReceiver sentMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(MessageManager.INTENT_ACTION_UNSENT_MESSAGES_SENT.equals(intent.getAction())) {
                String channelId = intent.getStringExtra(MessageManager.EXTRA_CHANNEL_ID);
                ArrayList<String> sentMessageIds = intent.getStringArrayListExtra(MessageManager.EXTRA_SENT_MESSAGE_IDS);

                //this is not an action channel.
                //it might be a target channel of one of our action channels though.
                if(mActionChannels.get(channelId) == null) {
                    //some messages were sent, instead of just removing the faked messages,
                    //just remove the whole channel's map. we will have to reload them later, but
                    //this way we can assure that they'll be all in the right order, etc.
                    mActionedMessages.remove(channelId);

                    //remove all action messages that point to this now nonexistent target message id
                    List<ActionMessage> sentTargetMessages = mDatabase.getActionMessagesForTargetMessages(sentMessageIds);
                    for(ActionMessage actionMessage : sentTargetMessages) {
                        String actionChannelId = actionMessage.getActionChannelId();
                        mDatabase.deleteActionMessage(actionChannelId, actionMessage.getTargetMessageId());
                    }
                } else {
                    //it's an action channel
                    //delete the action messages in the database with the sent message ids,
                    //retrieve the new ones

                    Channel actionChannel = mActionChannels.get(channelId);
                    final String targetChannelId = PrivateChannelUtility.getTargetChannelId(actionChannel);
                    mMessageManager.retrieveNewestMessages(channelId, new MessageManager.MessageManagerResponseHandler() {
                        @Override
                        public void onSuccess(List<MessagePlus> responseData, boolean appended) {
                            for(MessagePlus mp : responseData) {
                                String targetMessageId = getTargetMessageId(mp);
                                mDatabase.insertOrReplaceActionMessage(mp, targetMessageId, targetChannelId);
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
}
