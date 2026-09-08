package com.omnex.ffpanel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class OmnexApp extends Application {
    private static final String TAG = "OMNEX_APP";
    private static Context context;
    private static SharedPreferences prefs;
    
    static {
        try {
            System.loadLibrary("omnex_core");
            Log.i(TAG, "✅ Native library loaded");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "❌ Native library failed: " + e.getMessage());
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        prefs = getSharedPreferences("omnex_prefs", MODE_PRIVATE);
        Log.i(TAG, "OmnexApp initialized");
    }
    
    public static Context getContext() {
        return context;
    }
    
    public static SharedPreferences getPrefs() {
        return prefs;
    }
}