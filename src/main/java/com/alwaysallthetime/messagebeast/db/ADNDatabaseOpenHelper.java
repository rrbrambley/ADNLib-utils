package com.alwaysallthetime.messagebeast.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class ADNDatabaseOpenHelper extends SQLiteOpenHelper {

    private static final String TAG = "MessageBeast_AADNDatabaseOpenHelper";

    private static final String CREATE_MESSAGES_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_MESSAGES + "(" +
            ADNDatabase.COL_MESSAGE_ID + " INTEGER PRIMARY KEY, " +
            ADNDatabase.COL_MESSAGE_MESSAGE_ID + " TEXT UNIQUE, " +
            ADNDatabase.COL_MESSAGE_CHANNEL_ID + " TEXT NOT NULL, " +
            ADNDatabase.COL_MESSAGE_DATE + " INTEGER NOT NULL, " +
            ADNDatabase.COL_MESSAGE_JSON + " TEXT NOT NULL, " +
            ADNDatabase.COL_MESSAGE_TEXT + " TEXT, " +
            ADNDatabase.COL_MESSAGE_UNSENT + " BOOLEAN, " +
            ADNDatabase.COL_MESSAGE_SEND_ATTEMPTS + " INTEGER " +
            ")";

    private static final String CREATE_MESSAGE_DRAFTS_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_MESSAGE_DRAFTS + "(" +
            ADNDatabase.COL_MESSAGE_DRAFT_ID + " TEXT PRIMARY KEY, " +
            ADNDatabase.COL_MESSAGE_DRAFT_CHANNEL_ID + " TEXT NOT NULL, " +
            ADNDatabase.COL_MESSAGE_DRAFT_DATE + " INTEGER NOT NULL, " +
            ADNDatabase.COL_MESSAGE_DRAFT_JSON + " TEXT NOT NULL " +
            ")";

    private static final String CREATE_MESSAGES_SEARCH_TABLE = "CREATE VIRTUAL TABLE " + ADNDatabase.TABLE_MESSAGES_SEARCH + " USING fts4(" +
            "content=" + "\"" + ADNDatabase.TABLE_MESSAGES + "\"," +
            ADNDatabase.COL_MESSAGE_MESSAGE_ID + " TEXT, " +
            ADNDatabase.COL_MESSAGE_CHANNEL_ID + " TEXT, " +
            ADNDatabase.COL_MESSAGE_TEXT + " TEXT " +
            ")";

    private static final String CREATE_HASHTAG_INSTANCES_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_HASHTAG_INSTANCES + "(" +
            ADNDatabase.COL_HASHTAG_INSTANCE_NAME + " TEXT NOT NULL, " +
            ADNDatabase.COL_HASHTAG_INSTANCE_MESSAGE_ID + " TEXT NOT NULL, " +
            ADNDatabase.COL_HASHTAG_INSTANCE_CHANNEL_ID + " TEXT NOT NULL, " +
            ADNDatabase.COL_HASHTAG_INSTANCE_DATE + " INTEGER NOT NULL, " +
            "PRIMARY KEY (" + ADNDatabase.COL_HASHTAG_INSTANCE_NAME + ", " + ADNDatabase.COL_HASHTAG_INSTANCE_MESSAGE_ID + " ))";

    private static final String CREATE_GEOLOCATIONS_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_GEOLOCATIONS + "(" +
            ADNDatabase.COL_GEOLOCATION_LOCALITY + " TEXT NOT NULL, " +
            ADNDatabase.COL_GEOLOCATION_SUBLOCALITY + " TEXT, " +
            ADNDatabase.COL_GEOLOCATION_LATITUDE + " REAL NOT NULL, " +
            ADNDatabase.COL_GEOLOCATION_LONGITUDE + " REAL NOT NULL, " +
            "PRIMARY KEY (" + ADNDatabase.COL_GEOLOCATION_LATITUDE + ", " + ADNDatabase.COL_GEOLOCATION_LONGITUDE + " ))";

    private static final String CREATE_PLACES_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_PLACES + "(" +
            ADNDatabase.COL_PLACE_ID + " TEXT PRIMARY KEY, " +
            ADNDatabase.COL_PLACE_NAME + " TEXT NOT NULL, " +
            ADNDatabase.COL_PLACE_ROUNDED_LATITUDE + " REAL NOT NULL, " +
            ADNDatabase.COL_PLACE_ROUNDED_LONGITUDE + " REAL NOT NULL, " +
            ADNDatabase.COL_PLACE_IS_CUSTOM + " INTEGER NOT NULL, " +
            ADNDatabase.COL_PLACE_JSON + " TEXT NOT NULL " +
            ")";

    private static final String CREATE_LOCATION_INSTANCES_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_LOCATION_INSTANCES + "(" +
            ADNDatabase.COL_LOCATION_INSTANCE_ID + " INTEGER PRIMARY KEY, " +
            ADNDatabase.COL_LOCATION_INSTANCE_MESSAGE_ID + " TEXT UNIQUE, " +
            ADNDatabase.COL_LOCATION_INSTANCE_NAME + " TEXT NOT NULL, " +
            ADNDatabase.COL_LOCATION_INSTANCE_SHORT_NAME + " TEXT, " +
            ADNDatabase.COL_LOCATION_INSTANCE_CHANNEL_ID + " TEXT NOT NULL, " +
            ADNDatabase.COL_LOCATION_INSTANCE_LATITUDE + " REAL NOT NULL, " +
            ADNDatabase.COL_LOCATION_INSTANCE_LONGITUDE + " REAL NOT NULL, " +
            ADNDatabase.COL_LOCATION_INSTANCE_FACTUAL_ID + " TEXT, " +
            ADNDatabase.COL_LOCATION_INSTANCE_DATE + " INTEGER NOT NULL " +
            ")";

    private static final String CREATE_LOCATION_INSTANCES_SEARCH_TABLE = "CREATE VIRTUAL TABLE " + ADNDatabase.TABLE_LOCATION_INSTANCES_SEARCH + " USING fts4(" +
            "content=" + "\"" + ADNDatabase.TABLE_LOCATION_INSTANCES + "\"," +
            ADNDatabase.COL_LOCATION_INSTANCE_MESSAGE_ID + " TEXT, " +
            ADNDatabase.COL_LOCATION_INSTANCE_CHANNEL_ID + " TEXT, " +
            ADNDatabase.COL_LOCATION_INSTANCE_NAME + " TEXT " +
            ")";

    private static final String CREATE_ANNOTATION_INSTANCES_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_ANNOTATION_INSTANCES + "(" +
            ADNDatabase.COL_ANNOTATION_INSTANCE_TYPE + " TEXT NOT NULL, " +
            ADNDatabase.COL_ANNOTATION_INSTANCE_MESSAGE_ID + " TEXT NOT NULL, " +
            ADNDatabase.COL_ANNOTATION_INSTANCE_CHANNEL_ID + " TEXT NOT NULL, " +
            ADNDatabase.COL_ANNOTATION_INSTANCE_COUNT + " INTEGER NOT NULL, " +
            ADNDatabase.COL_ANNOTATION_INSTANCE_DATE + " INTEGER NOT NULL, " +
            "PRIMARY KEY (" + ADNDatabase.COL_ANNOTATION_INSTANCE_TYPE + ", " + ADNDatabase.COL_ANNOTATION_INSTANCE_MESSAGE_ID + " ))";

    public static final String CREATE_PENDING_FILES_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_PENDING_FILES + "(" +
            ADNDatabase.COL_PENDING_FILE_ID + " TEXT PRIMARY KEY, " +
            ADNDatabase.COL_PENDING_FILE_URI + " TEXT NOT NULL, " +
            ADNDatabase.COL_PENDING_FILE_TYPE + " TEXT NOT NULL, " +
            ADNDatabase.COL_PENDING_FILE_NAME + " TEXT NOT NULL, " +
            ADNDatabase.COL_PENDING_FILE_MIMETYPE + " TEXT NOT NULL, " +
            ADNDatabase.COL_PENDING_FILE_KIND + " TEXT, " +
            ADNDatabase.COL_PENDING_FILE_PUBLIC + " BOOLEAN, " +
            ADNDatabase.COL_PENDING_FILE_SEND_ATTEMPTS + " INTEGER " +
            ")";

    public static final String CREATE_PENDING_MESSAGE_DELETIONS_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_PENDING_MESSAGE_DELETIONS + "(" +
            ADNDatabase.COL_PENDING_MESSAGE_DELETION_MESSAGE_ID + " TEXT PRIMARY KEY, " +
            ADNDatabase.COL_PENDING_MESSAGE_DELETION_CHANNEL_ID + " TEXT NOT NULL" +
            ")";

    public static final String CREATE_PENDING_FILE_DELETIONS_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_PENDING_FILE_DELETIONS + "(" +
            ADNDatabase.COL_PENDING_FILE_DELETION_FILE_ID + " TEXT PRIMARY KEY " +
            ")";

    public static final String CREATE_PENDING_FILE_ATTACHMENTS_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_PENDING_FILE_ATTACHMENTS + "(" +
            ADNDatabase.COL_PENDING_FILE_ATTACHMENT_PENDING_FILE_ID + " TEXT NOT NULL, " +
            ADNDatabase.COL_PENDING_FILE_ATTACHMENT_MESSAGE_ID + " TEXT NOT NULL, " +
            ADNDatabase.COL_PENDING_FILE_ATTACHMENT_CHANNEL_ID + " TEXT NOT NULL, " +
            ADNDatabase.COL_PENDING_FILE_ATTACHMENT_IS_OEMBED + " INTEGER NOT NULL, " +
            "PRIMARY KEY (" + ADNDatabase.COL_PENDING_FILE_ATTACHMENT_PENDING_FILE_ID + ", " +
                              ADNDatabase.COL_PENDING_FILE_ATTACHMENT_MESSAGE_ID + "))";

    public static final String CREATE_ACTION_MESSAGES_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_ACTION_MESSAGES + "(" +
            ADNDatabase.COL_ACTION_MESSAGE_ID + " TEXT PRIMARY KEY, " +
            ADNDatabase.COL_ACTION_MESSAGE_CHANNEL_ID + " TEXT NOT NULL, " +
            ADNDatabase.COL_ACTION_MESSAGE_TARGET_MESSAGE_ID + " TEXT NOT NULL, " +
            ADNDatabase.COL_ACTION_MESSAGE_TARGET_CHANNEL_ID + " TEXT NOT NULL, " +
            ADNDatabase.COL_ACTION_MESSAGE_TARGET_MESSAGE_DISPLAY_DATE + " INTEGER NOT NULL " +
            ")";

    public ADNDatabaseOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.beginTransaction();

        try {
            db.execSQL(CREATE_MESSAGES_TABLE);
            db.execSQL(CREATE_MESSAGE_DRAFTS_TABLE);
            db.execSQL(CREATE_HASHTAG_INSTANCES_TABLE);
            db.execSQL(CREATE_GEOLOCATIONS_TABLE);
            db.execSQL(CREATE_PLACES_TABLE);
            db.execSQL(CREATE_LOCATION_INSTANCES_TABLE);
            db.execSQL(CREATE_ANNOTATION_INSTANCES_TABLE);
            db.execSQL(CREATE_PENDING_FILES_TABLE);
            db.execSQL(CREATE_PENDING_MESSAGE_DELETIONS_TABLE);
            db.execSQL(CREATE_PENDING_FILE_DELETIONS_TABLE);
            db.execSQL(CREATE_PENDING_FILE_ATTACHMENTS_TABLE);
            db.execSQL(CREATE_ACTION_MESSAGES_TABLE);

            if(ADNDatabase.isFullTextSearchAvailable()) {
                db.execSQL(CREATE_MESSAGES_SEARCH_TABLE);
            }
            if(ADNDatabase.isFullTextSearchAvailable()) {
                db.execSQL(CREATE_LOCATION_INSTANCES_SEARCH_TABLE);
            }
            db.setTransactionSuccessful();
        } catch(Exception exception) {
            Log.e(TAG, exception.getMessage(), exception);
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
