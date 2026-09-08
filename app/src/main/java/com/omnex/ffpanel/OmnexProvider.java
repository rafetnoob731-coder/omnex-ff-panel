package com.omnex.ffpanel;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class OmnexProvider extends ContentProvider {
    private static final String TAG = "OMNEX_PROVIDER";

    @Override
    public boolean onCreate() {
        Log.i(TAG, "Provider created");
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                       String[] selectionArgs, String sortOrder) { return null; }

    @Override
    public String getType(Uri uri) { return null; }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                     String[] selectionArgs) { return 0; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        if (method != null) {
            switch (method) {
                case "getStatus":
                    result.putString("status", "active");
                    result.putString("version", "1.108.1");
                    result.putString("bypass", "working");
                    break;
                case "inject":
                    result.putBoolean("success", true);
                    break;
                case "setFeature":
                    if (extras != null) {
                        String feature = extras.getString("feature");
                        boolean enabled = extras.getBoolean("enabled");
                        result.putBoolean("success", true);
                    }
                    break;
                case "getFeatures":
                    result.putStringArray("features", new String[]{
                        "aimbot", "aim_silent", "aim_logic", "triggerbot",
                        "esp_master", "esp_box", "esp_health", "esp_name",
                        "speed_hack", "jump_hack", "god_mode",
                        "fly_master", "magic_bullet", "aura_kill", "ac_bypass"
                    });
                    break;
            }
        }
        return result;
    }
}