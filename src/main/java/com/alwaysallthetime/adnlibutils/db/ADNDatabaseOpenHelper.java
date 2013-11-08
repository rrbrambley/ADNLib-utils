package com.alwaysallthetime.adnlibutils.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class ADNDatabaseOpenHelper extends SQLiteOpenHelper {

    private static final String TAG = "ADNLibUtils_AADNDatabaseOpenHelper";

    private static final String CREATE_MESSAGES_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_MESSAGES + "(" +
            ADNDatabase.COL_MESSAGE_ID + " STRING PRIMARY KEY, " +
            ADNDatabase.COL_MESSAGE_CHANNEL_ID + " STRING NOT NULL, " +
            ADNDatabase.COL_MESSAGE_DATE + " INTEGER NOT NULL, " +
            ADNDatabase.COL_MESSAGE_JSON + " STRING NOT NULL " +
            ")";

    private static final String CREATE_HASHTAG_INSTANCES_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_HASHTAG_INSTANCES + "(" +
            ADNDatabase.COL_HASHTAG_INSTANCE_NAME + " STRING NOT NULL, " +
            ADNDatabase.COL_HASHTAG_INSTANCE_MESSAGE_ID + " STRING NOT NULL, " +
            ADNDatabase.COL_HASHTAG_INSTANCE_CHANNEL_ID + " STRING NOT NULL, " +
            ADNDatabase.COL_HASHTAG_INSTANCE_DATE + " INTEGER NOT NULL, " +
            "PRIMARY KEY (" + ADNDatabase.COL_HASHTAG_INSTANCE_NAME + ", " + ADNDatabase.COL_HASHTAG_INSTANCE_MESSAGE_ID + " ))";

    private static final String CREATE_GEOLOCATIONS_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_GEOLOCATIONS + "(" +
            ADNDatabase.COL_GEOLOCATION_LOCALITY + " STRING NOT NULL, " +
            ADNDatabase.COL_GEOLOCATION_SUBLOCALITY + " STRING, " +
            ADNDatabase.COL_GEOLOCATION_LATITUDE + " REAL NOT NULL, " +
            ADNDatabase.COL_GEOLOCATION_LONGITUDE + " REAL NOT NULL, " +
            "PRIMARY KEY (" + ADNDatabase.COL_GEOLOCATION_LATITUDE + ", " + ADNDatabase.COL_GEOLOCATION_LONGITUDE + " ))";

    private static final String CREATE_LOCATION_INSTANCES_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_LOCATION_INSTANCES + "(" +
            ADNDatabase.COL_LOCATION_INSTANCE_NAME + " STRING NOT NULL, " +
            ADNDatabase.COL_LOCATION_INSTANCE_SHORT_NAME + " STRING, " +
            ADNDatabase.COL_LOCATION_INSTANCE_MESSAGE_ID + " STRING NOT NULL, " +
            ADNDatabase.COL_LOCATION_INSTANCE_CHANNEL_ID + " STRING NOT NULL, " +
            ADNDatabase.COL_LOCATION_INSTANCE_LATITUDE + " REAL NOT NULL, " +
            ADNDatabase.COL_LOCATION_INSTANCE_LONGITUDE + " REAL NOT NULL, " +
            ADNDatabase.COL_LOCATION_INSTANCE_FACTUAL_ID + " STRING, " +
            ADNDatabase.COL_LOCATION_INSTANCE_DATE + " INTEGER NOT NULL, " +
            "PRIMARY KEY (" + ADNDatabase.COL_LOCATION_INSTANCE_NAME + ", " + ADNDatabase.COL_LOCATION_INSTANCE_MESSAGE_ID + ", " +
                              ADNDatabase.COL_LOCATION_INSTANCE_LATITUDE + ", " + ADNDatabase.COL_LOCATION_INSTANCE_LONGITUDE + " ))";

    private static final String CREATE_OEMBED_INSTANCES_TABLE = "CREATE TABLE IF NOT EXISTS " + ADNDatabase.TABLE_OEMBED_INSTANCES + "(" +
            ADNDatabase.COL_OEMBED_INSTANCE_TYPE + " STRING NOT NULL, " +
            ADNDatabase.COL_OEMBED_INSTANCE_MESSAGE_ID + " STRING NOT NULL, " +
            ADNDatabase.COL_OEMBED_INSTANCE_CHANNEL_ID + " STRING NOT NULL, " +
            ADNDatabase.COL_OEMBED_INSTANCE_COUNT + " INTEGER NOT NULL, " +
            ADNDatabase.COL_OEMBED_INSTANCE_DATE + " INTEGER NOT NULL, " +
            "PRIMARY KEY (" + ADNDatabase.COL_OEMBED_INSTANCE_TYPE + ", " + ADNDatabase.COL_OEMBED_INSTANCE_MESSAGE_ID + " ))";

    public ADNDatabaseOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.beginTransaction();

        try {
            db.execSQL(CREATE_MESSAGES_TABLE);
            db.execSQL(CREATE_HASHTAG_INSTANCES_TABLE);
            db.execSQL(CREATE_GEOLOCATIONS_TABLE);
            db.execSQL(CREATE_LOCATION_INSTANCES_TABLE);
            db.execSQL(CREATE_OEMBED_INSTANCES_TABLE);
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
