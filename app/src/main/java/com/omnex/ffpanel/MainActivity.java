package com.omnex.ffpanel;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION = 1001;
    private static final int REQUEST_OVERLAY = 1002;
    private static final int REQUEST_BATTERY = 1003;
    
    private LinearLayout permissionList;
    private TextView statusText;
    private TextView logoText;
    private Button btnLaunch;
    private Button btnShizuku;
    private Handler handler = new Handler();
    
    private boolean fileGranted = false;
    private boolean displayGranted = false;
    private boolean shizukuGranted = false;
    private boolean phoneGranted = false;
    
    private native void initNative();
    private native void setFeature(String feature, boolean enabled);
    private native void setValue(String key, float value);
    private native long getGamePid();
    private native boolean injectGame(int pid);
    private native boolean isInjected();
    private native void bypassAntiCheat();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        initNative();
        checkAllPermissions();
    }
    
    private void initViews() {
        permissionList = findViewById(R.id.permissionList);
        statusText = findViewById(R.id.statusText);
        logoText = findViewById(R.id.logoText);
        btnLaunch = findViewById(R.id.btnLaunch);
        btnShizuku = findViewById(R.id.btnShizuku);
        
        // Animated logo
        handler.postDelayed(new Runnable() {
            int alpha = 0;
            boolean increasing = true;
            
            @Override
            public void run() {
                if (increasing) {
                    alpha += 15;
                    if (alpha >= 255) {
                        alpha = 255;
                        increasing = false;
                    }
                } else {
                    alpha -= 15;
                    if (alpha <= 50) {
                        alpha = 50;
                        increasing = true;
                    }
                }
                logoText.setAlpha(alpha / 255.0f);
                handler.postDelayed(this, 50);
            }
        }, 50);
        
        btnLaunch.setOnClickListener(v -> {
            if (fileGranted && displayGranted) {
                statusText.setText("🚀 Launching Free Fire...");
                statusText.setTextColor(Color.GREEN);
                launchFreeFire();
                startFloatingService();
                startGameMonitor();
            } else {
                Toast.makeText(this, "⚠️ Grant all permissions first", Toast.LENGTH_SHORT).show();
                statusText.setText("❌ Permissions missing");
                statusText.setTextColor(Color.RED);
            }
        });
        
        btnShizuku.setOnClickListener(v -> openShizukuApp());
    }
    
    private void checkAllPermissions() {
        // File permission (Android R+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (androidx.core.content.ContextCompat.isSelfOwnApp(this)) {
                // For Android 12+, need special handling
                if (androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    // Show explanation
                }
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED) {
                fileGranted = true;
                addPermissionStatus("✅ File Permission: GRANTED", Color.GREEN);
            } else {
                addPermissionStatus("❌ File Permission: REQUESTING", Color.YELLOW);
                ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, REQUEST_PERMISSION);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED) {
                fileGranted = true;
                addPermissionStatus("✅ File Permission: GRANTED", Color.GREEN);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, REQUEST_PERMISSION);
            }
        }
        
        // Display overlay
        if (Settings.canDrawOverlays(this)) {
            displayGranted = true;
            addPermissionStatus("✅ Display Permission: GRANTED", Color.GREEN);
        } else {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY);
        }
        
        // Phone state
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) 
            == PackageManager.PERMISSION_GRANTED) {
            phoneGranted = true;
            addPermissionStatus("✅ Phone Permission: GRANTED", Color.GREEN);
        } else {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_PERMISSION);
        }
        
        // Shizuku
        checkShizukuPermission();
        
        // Battery optimization
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this) || 
                !isIgnoringBatteryOptimizations()) {
                Intent batteryIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                batteryIntent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(batteryIntent, REQUEST_BATTERY);
            }
        }
    }
    
    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }
    
    private void checkShizukuPermission() {
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            boolean isAlive = (boolean) shizukuClass.getMethod("pingBinder").invoke(null);
            
            if (isAlive) {
                shizukuGranted = true;
                addPermissionStatus("✅ Shizuku Permission: GRANTED", Color.GREEN);
            } else {
                addPermissionStatus("⚠️ Shizuku Permission: NOT ACTIVE - Tap to open", Color.YELLOW);
            }
        } catch (Exception e) {
            addPermissionStatus("❌ Shizuku Permission: NOT INSTALLED", Color.RED);
        }
    }
    
    private void openShizukuApp() {
        try {
            Intent intent = new Intent();
            intent.setClassName("moe.shizuku.privileged.api", 
                "moe.shizuku.manager.MainActivity");
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage(
                    "moe.shizuku.privileged.api");
                if (intent != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Install Shizuku from Play Store", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e2) {
                Toast.makeText(this, "Shizuku not found", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void addPermissionStatus(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(13);
        tv.setPadding(15, 10, 15, 10);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        permissionList.addView(tv);
    }
    
    private void launchFreeFire() {
        try {
            Intent intent = new Intent();
            intent.setClassName("com.dts.freefireth", "com.dts.freefireth.FFMainActivity");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            statusText.setText("✅ Free Fire Launched");
            statusText.setTextColor(Color.GREEN);
        } catch (Exception e) {
            Toast.makeText(this, "Free Fire not installed", Toast.LENGTH_SHORT).show();
            statusText.setText("❌ Free Fire not found");
            statusText.setTextColor(Color.RED);
        }
    }
    
    private void startFloatingService() {
        Intent serviceIntent = new Intent(this, FloatingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
    
    private void startGameMonitor() {
        new Thread(() -> {
            for (int attempt = 0; attempt < 60; attempt++) {
                long pid = getGamePid();
                if (pid > 0) {
                    runOnUiThread(() -> {
                        statusText.setText("🎯 Game Found - Injecting...");
                        statusText.setTextColor(Color.YELLOW);
                    });
                    
                    boolean injected = injectGame((int) pid);
                    
                    if (injected) {
                        runOnUiThread(() -> {
                            statusText.setText("✅ INJECTED SUCCESSFULLY!");
                            statusText.setTextColor(Color.GREEN);
                        });
                        break;
                    } else {
                        runOnUiThread(() -> {
                            statusText.setText("❌ Injection Failed");
                            statusText.setTextColor(Color.RED);
                        });
                    }
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
            }
        }).start();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (Settings.canDrawOverlays(this)) {
            displayGranted = true;
        }
        checkShizukuPermission();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fileGranted = true;
                addPermissionStatus("✅ File Permission: GRANTED", Color.GREEN);
            }
        }
    }
}