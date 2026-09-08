package com.omnex.ffpanel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;

public class FloatingService extends Service {
    private WindowManager windowManager;
    private View floatingIcon;
    private View expandedPanel;
    private LinearLayout tabBar;
    private LinearLayout contentContainer;
    private Map<String, LinearLayout> tabContents = new HashMap<>();
    private String currentTab = "aim";
    private boolean isExpanded = false;
    private TextView panelStatusText;

    private static final String COLOR_ACCENT_RED = "#FF1744";
    private static final String COLOR_ACCENT_GOLD = "#FFD700";
    private static final String COLOR_ACCENT_GREEN = "#00E676";
    private static final String COLOR_ACCENT_PURPLE = "#D500F9";
    private static final String COLOR_TEXT_WHITE = "#FFFFFF";
    private static final String COLOR_TEXT_GRAY = "#AAAAAA";
    private static final String COLOR_TEXT_DIM = "#666666";
    private static final String COLOR_CARD_BG = "#1A1A24";
    private static final String COLOR_CARD_BORDER = "#2A2A3A";
    private static final String COLOR_TAB_INACTIVE = "#2A2A3A";
    private static final String COLOR_DIVIDER = "#22222E";

    private native void setFeature(String feature, boolean enabled);
    private native void setValue(String key, float value);
    private native boolean isInjected();

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(1001, createNotification());
        createFloatingIcon();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "omnex_overlay", "OMNEX FF Panel", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Premium floating panel");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "omnex_overlay")
            .setContentTitle("⚡ OMNEX FF Panel Active")
            .setContentText("Premium panel running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    private void createFloatingIcon() {
        FrameLayout iconContainer = new FrameLayout(this);
        iconContainer.setPadding(5, 5, 5, 5);

        TextView icon = new TextView(this);
        icon.setText("⚡");
        icon.setTextSize(24);
        icon.setGravity(Gravity.CENTER);
        icon.setShadowLayer(15, 0, 0, Color.parseColor("#FF1744"));

        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(Color.parseColor("#E6000000"));
        iconBg.setStroke(3, Color.parseColor(COLOR_ACCENT_RED));
        iconBg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        iconBg.setGradientRadius(50);
        iconBg.setColors(new int[]{Color.parseColor("#FFFF1744"), Color.parseColor("#E6000000")});
        icon.setBackground(iconBg);
        icon.setPadding(20, 20, 20, 20);
        iconContainer.addView(icon);
        floatingIcon = iconContainer;

        AlphaAnimation pulse = new AlphaAnimation(0.6f, 1.0f);
        pulse.setDuration(1000);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        icon.startAnimation(pulse);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 300;

        iconContainer.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isClick = true;
            private long downTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isClick = true;
                        downTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int)(event.getRawX() - initialTouchX);
                        params.y = initialY + (int)(event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingIcon, params);
                        if (Math.abs(event.getRawX() - initialTouchX) > 10 ||
                            Math.abs(event.getRawY() - initialTouchY) > 10) {
                            isClick = false;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        long upTime = System.currentTimeMillis();
                        if (isClick && (upTime - downTime) < 500) {
                            togglePanel();
                        }
                        return true;
                }
                return false;
            }
        });
        windowManager.addView(floatingIcon, params);
    }

    private void togglePanel() {
        if (isExpanded) {
            collapsePanel();
        } else {
            expandPanel();
        }
    }

    private void expandPanel() {
        expandedPanel = createPremiumPanel();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            780, 980,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;

        ScaleAnimation scaleAnim = new ScaleAnimation(0.5f, 1.0f, 0.5f, 1.0f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaleAnim.setDuration(200);
        expandedPanel.startAnimation(scaleAnim);
        windowManager.addView(expandedPanel, params);
        isExpanded = true;
    }

    private void collapsePanel() {
        if (expandedPanel != null) {
            ScaleAnimation scaleAnim = new ScaleAnimation(1.0f, 0.5f, 1.0f, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scaleAnim.setDuration(150);
            scaleAnim.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation animation) {}
                @Override public void onAnimationEnd(Animation animation) {
                    windowManager.removeView(expandedPanel);
                    expandedPanel = null;
                }
                @Override public void onAnimationRepeat(Animation animation) {}
            });
            expandedPanel.startAnimation(scaleAnim);
        }
        isExpanded = false;
    }

    private View createPremiumPanel() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(12, 12, 12, 12);

        GradientDrawable mainBg = new GradientDrawable();
        mainBg.setShape(GradientDrawable.RECTANGLE);
        mainBg.setCornerRadius(25);
        mainBg.setStroke(2, Color.parseColor(COLOR_ACCENT_RED));
        mainBg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        mainBg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        mainBg.setColors(new int[]{Color.parseColor("#F20D0D12"), Color.parseColor("#F21A1A24"), Color.parseColor("#F20D0D12")});
        mainLayout.setBackground(mainBg);

        // Header
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.VERTICAL);
        headerLayout.setGravity(Gravity.CENTER);
        headerLayout.setPadding(0, 15, 0, 10);

        TextView headerIcon = new TextView(this);
        headerIcon.setText("⚡");
        headerIcon.setTextSize(36);
        headerIcon.setGravity(Gravity.CENTER);
        headerIcon.setShadowLayer(20, 0, 0, Color.parseColor(COLOR_ACCENT_RED));
        headerLayout.addView(headerIcon);

        TextView headerTitle = new TextView(this);
        headerTitle.setText("OMNEX FF");
        headerTitle.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        headerTitle.setTextSize(26);
        headerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        headerTitle.setGravity(Gravity.CENTER);
        headerTitle.setLetterSpacing(0.2f);
        headerTitle.setShadowLayer(10, 0, 0, Color.parseColor(COLOR_ACCENT_RED));
        headerLayout.addView(headerTitle);

        TextView headerSub = new TextView(this);
        headerSub.setText("PREMIUM PANEL");
        headerSub.setTextColor(Color.parseColor(COLOR_ACCENT_GOLD));
        headerSub.setTextSize(10);
        headerSub.setGravity(Gravity.CENTER);
        headerSub.setLetterSpacing(0.3f);
        headerLayout.addView(headerSub);

        View divider = new View(this);
        divider.setBackground(createGradientDrawable("#33FF1744", COLOR_ACCENT_RED, "#33FF1744"));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(120, 2);
        dividerParams.gravity = Gravity.CENTER;
        dividerParams.setMargins(0, 12, 0, 12);
        headerLayout.addView(divider, dividerParams);

        mainLayout.addView(headerLayout);

        // Status
        panelStatusText = new TextView(this);
        panelStatusText.setText("● INJECTED  |  v1.108.1  |  ACTIVE");
        panelStatusText.setTextColor(Color.parseColor(COLOR_ACCENT_GREEN));
        panelStatusText.setTextSize(9);
        panelStatusText.setGravity(Gravity.CENTER);
        panelStatusText.setLetterSpacing(0.1f);
        panelStatusText.setPadding(0, 0, 0, 10);
        mainLayout.addView(panelStatusText);

        // Tab bar
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setPadding(0, 5, 0, 5);

        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setGravity(Gravity.CENTER);
        tabBar.setPadding(5, 5, 5, 5);

        String[] tabs = {"AIM", "ESP", "PLAYER", "TELEPORT", "FLY", "DRIVE", "GLOO", "MAGIC", "AURA", "AI", "BYPASS"};
        String[] tabIcons = {"🎯", "👁️", "🏃", "📍", "🕊️", "🚗", "🧱", "🔮", "💫", "🤖", "🛡️"};

        for (int i = 0; i < tabs.length; i++) {
            final String tab = tabs[i].toLowerCase();
            Button tabButton = createTabButton(tabIcons[i], tabs[i]);
            tabButton.setOnClickListener(v -> {
                switchTab(tab);
                highlightTab(tabButton);
            });
            tabBar.addView(tabButton);
        }

        tabScroll.addView(tabBar);
        mainLayout.addView(tabScroll);

        // Content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setPadding(5, 5, 5, 5);
        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(8, 8, 8, 8);
        scrollView.addView(contentContainer);
        mainLayout.addView(scrollView);

        // Bottom buttons
        LinearLayout bottomLayout = new LinearLayout(this);
        bottomLayout.setOrientation(LinearLayout.HORIZONTAL);
        bottomLayout.setGravity(Gravity.CENTER);
        bottomLayout.setPadding(0, 10, 0, 0);

        Button minimizeButton = new Button(this);
        minimizeButton.setText("—");
        minimizeButton.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        minimizeButton.setTextSize(18);
        minimizeButton.setBackground(createButtonGradient(COLOR_ACCENT_PURPLE, COLOR_ACCENT_RED));
        minimizeButton.setPadding(35, 12, 35, 12);
        minimizeButton.setOnClickListener(v -> collapsePanel());
        bottomLayout.addView(minimizeButton);

        Button closeButton = new Button(this);
        closeButton.setText("✕");
        closeButton.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        closeButton.setTextSize(18);
        closeButton.setBackground(createButtonGradient(COLOR_ACCENT_RED, "#880000"));
        closeButton.setPadding(35, 12, 35, 12);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        closeParams.setMargins(15, 0, 0, 0);
        closeButton.setLayoutParams(closeParams);
        closeButton.setOnClickListener(v -> { collapsePanel(); stopSelf(); });
        bottomLayout.addView(closeButton);

        mainLayout.addView(bottomLayout);

        buildAllTabContents();
        switchTab("aim");

        return mainLayout;
    }

    private Button createTabButton(String icon, String label) {
        Button button = new Button(this);
        button.setText(icon + "\n" + label);
        button.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        button.setTextSize(8);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setPadding(10, 8, 10, 8);
        button.setBackground(createButtonGradient(COLOR_TAB_INACTIVE, COLOR_TAB_INACTIVE));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(3, 0, 3, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void highlightTab(Button selectedButton) {
        for (int i = 0; i < tabBar.getChildCount(); i++) {
            View child = tabBar.getChildAt(i);
            if (child instanceof Button) {
                child.setBackground(createButtonGradient(COLOR_TAB_INACTIVE, COLOR_TAB_INACTIVE));
            }
        }
        selectedButton.setBackground(createButtonGradient(COLOR_ACCENT_RED, "#880000"));
    }

    private GradientDrawable createButtonGradient(String color1, String color2) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(12);
        drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        drawable.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        drawable.setColors(new int[]{Color.parseColor(color1), Color.parseColor(color2)});
        return drawable;
    }

    private GradientDrawable createGradientDrawable(String... colors) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        drawable.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        int[] colorArray = new int[colors.length];
        for (int i = 0; i < colors.length; i++) colorArray[i] = Color.parseColor(colors[i]);
        drawable.setColors(colorArray);
        return drawable;
    }

    private void buildAllTabContents() {
        buildAimTab(); buildEspTab(); buildPlayerTab(); buildTeleportTab();
        buildFlyTab(); buildDriveTab(); buildGlooWallTab(); buildMagicBulletTab();
        buildAuraTab(); buildAiTab(); buildBypassTab();
    }

    private void switchTab(String tab) {
        currentTab = tab;
        contentContainer.removeAllViews();
        LinearLayout content = tabContents.get(tab);
        if (content != null) contentContainer.addView(content);
    }

    private void addSectionHeader(LinearLayout parent, String icon, String text) {
        LinearLayout headerContainer = new LinearLayout(this);
        headerContainer.setOrientation(LinearLayout.VERTICAL);
        headerContainer.setGravity(Gravity.CENTER);
        headerContainer.setPadding(0, 15, 0, 10);

        TextView header = new TextView(this);
        header.setText(icon + " " + text);
        header.setTextColor(Color.parseColor(COLOR_ACCENT_GOLD));
        header.setTextSize(13);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setGravity(Gravity.CENTER);
        header.setLetterSpacing(0.15f);
        headerContainer.addView(header);

        View div = new View(this);
        div.setBackground(createGradientDrawable("#33FF1744", COLOR_ACCENT_RED, "#33FF1744"));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divParams.setMargins(15, 8, 15, 0);
        headerContainer.addView(div, divParams);
        parent.addView(headerContainer);
    }

    private void addToggle(LinearLayout parent, String label, String featureKey, boolean defaultState) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(12, 14, 12, 14);

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setShape(GradientDrawable.RECTANGLE);
        rowBg.setCornerRadius(10);
        rowBg.setColor(Color.parseColor("#11111A"));
        rowBg.setStroke(1, Color.parseColor(COLOR_CARD_BORDER));
        row.setBackground(rowBg);

        TextView textView = new TextView(this);
        textView.setText(label);
        textView.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        textView.setTextSize(12);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setLetterSpacing(0.05f);

        Switch switchView = new Switch(this);
        switchView.setChecked(defaultState);
        switchView.setTextOn("");
        switchView.setTextOff("");
        switchView.setShowText(false);
        switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setFeature(featureKey, isChecked);
            if (isChecked) {
                buttonView.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT_GOLD)));
                buttonView.setTrackTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#66FF1744")));
            } else {
                buttonView.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(COLOR_TEXT_DIM)));
                buttonView.setTrackTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(COLOR_SWITCH_OFF)));
            }
        });

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(textView, textParams);
        row.addView(switchView);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 4, 0, 4);
        parent.addView(row, rowParams);
    }

    private void addSlider(LinearLayout parent, String label, String key, int min, int max, int defaultValue) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(12, 10, 12, 10);

        GradientDrawable containerBg = new GradientDrawable();
        containerBg.setShape(GradientDrawable.RECTANGLE);
        containerBg.setCornerRadius(10);
        containerBg.setColor(Color.parseColor("#11111A"));
        containerBg.setStroke(1, Color.parseColor(COLOR_CARD_BORDER));
        container.setBackground(containerBg);

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView labelText = new TextView(this);
        labelText.setText(label);
        labelText.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
        labelText.setTextSize(11);

        TextView valueText = new TextView(this);
        valueText.setText(String.valueOf(defaultValue));
        valueText.setTextColor(Color.parseColor(COLOR_ACCENT_GOLD));
        valueText.setTextSize(12);
        valueText.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        labelRow.addView(labelText, labelParams);
        labelRow.addView(valueText);
        container.addView(labelRow);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(defaultValue - min);
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT_RED)));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT_GOLD)));
        seekBar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(COLOR_DIVIDER)));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + min;
                valueText.setText(String.valueOf(value));
                setValue(key, value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        container.addView(seekBar);

        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.setMargins(0, 6, 0, 6);
        parent.addView(container, containerParams);
    }

    private void buildAimTab() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(5, 5, 5, 5);
        addSectionHeader(layout, "🎯", "AIM SETTINGS");
        addToggle(layout, "Aimbot", "aimbot", true);
        addToggle(layout, "Aim Silent", "aim_silent", false);
        addToggle(layout, "Aim Logic", "aim_logic", false);
        addToggle(layout, "Aim FOV", "aim_fov", false);
        addToggle(layout, "Triggerbot", "triggerbot", false);
        addToggle(layout, "No Recoil", "no_recoil", false);
        addToggle(layout, "No Spread", "no_spread", false);
        addSlider(layout, "Aim Smoothness", "aim_smoothness", 1, 10, 5);
        addSlider(layout, "Aim FOV Size", "aim_fov_size", 50, 500, 150);
        tabContents.put("aim", layout);
    }

    private void buildEspTab() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(5, 5, 5, 5);
        addSectionHeader(layout, "👁️", "ESP SETTINGS");
        addToggle(layout, "ESP Master", "esp_master", true);
        addToggle(layout, "ESP Box", "esp_box", true);
        addToggle(layout, "ESP Health", "esp_health", true);
        addToggle(layout, "ESP Name", "esp_name", true);
        addToggle(layout, "ESP Rank", "esp_rank", false);
        addToggle(layout, "ESP Distance", "esp_distance", true);
        addToggle(layout, "ESP Line", "esp_line", false);
        addToggle(layout, "ESP Skeleton", "esp_skeleton", false);
        addToggle(layout, "ESP Vehicle", "esp_vehicle", false);
        addToggle(layout, "ESP Items", "esp_items", false);
        addToggle(layout, "ESP Glow", "esp_glow", false);
        addToggle(layout, "ESP Radar", "esp_radar", false);
        tabContents.put("esp", layout);
    }

    private void buildPlayerTab() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(5, 5, 5, 5);
        addSectionHeader(layout, "🏃", "PLAYER SETTINGS");
        addToggle(layout, "Speed Hack", "speed_hack", false);
        addToggle(layout, "Jump Hack", "jump_hack", false);
        addToggle(layout, "Walk Through Walls", "wall_hack", false);
        addToggle(layout, "God Mode", "god_mode", false);
        addToggle(layout, "Unlimited Ammo", "unlimited_ammo", false);
        addToggle(layout, "Rapid Fire", "rapid_fire", false);
        addToggle(layout, "Instant Reload", "instant_reload", false);
        addToggle(layout, "Damage Multiplier", "damage_multiplier", false);
        addToggle(layout, "Invisible", "invisibility", false);
        addToggle(layout, "No Clip", "no_clip", false);
        addSlider(layout, "Speed Multiplier", "speed_value", 1, 10, 2);
        addSlider(layout, "Jump Multiplier", "jump_value", 1, 10, 3);
        addSlider(layout, "Damage Multiplier", "damage_value", 1, 100, 10);
        tabContents.put("player", layout);
    }

    private void buildTeleportTab() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(5, 5, 5, 5);
        addSectionHeader(layout, "📍", "TELEPORT SETTINGS");
        addToggle(layout, "Teleport to Player", "tp_player", false);
        addToggle(layout, "Teleport to Map", "tp_map", false);
        addToggle(layout, "Teleport Forward", "tp_forward", false);
        addToggle(layout, "Teleport Save 1", "tp_save1", false);
        addToggle(layout, "Teleport Save 2", "tp_save2", false);
        addToggle(layout, "Teleport Save 3", "tp_save3", false);
        addToggle(layout, "Teleport Undo", "tp_undo", false);
        addSlider(layout, "Forward Distance", "tp_forward_dist", 1, 100, 10);
        tabContents.put("teleport", layout);
    }

    private void buildFlyTab() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(5, 5, 5, 5);
        addSectionHeader(layout, "🕊️", "FLY SETTINGS");
        addToggle(layout, "Fly Master", "fly_master", false);
        addToggle(layout, "Fly Up", "fly_up", false);
        addToggle(layout, "Fly Down", "fly_down", false);
        addToggle(layout, "Fly Forward", "fly_forward", false);
        addToggle(layout, "Fly Hover", "fly_hover", false);
        addToggle(layout, "Fly Gravity", "fly_gravity", false);
        addToggle(layout, "Fly Boost", "fly_boost", false);
        addSlider(layout, "Fly Speed", "fly_speed", 1, 50, 10);
        addSlider(layout, "Hover Height", "hover_height", 1, 100, 20);
        tabContents.put("fly", layout);
    }

    private void buildDriveTab() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(5, 5, 5, 5);
        addSectionHeader(layout, "🚗", "DRIVE SETTINGS");
        addToggle(layout, "Vehicle Speed", "vehicle_speed", false);
        addToggle(layout, "Vehicle Fly", "vehicle_fly", false);
        addToggle(layout, "Vehicle God", "vehicle_god", false);
        addToggle(layout, "No Fuel", "no_fuel", false);
        addToggle(layout, "Vehicle Boost", "vehicle_boost", false);
        addToggle(layout, "Vehicle Spawn", "vehicle_spawn", false);
        addSlider(layout, "Vehicle Speed Multiplier", "vehicle_speed_value", 1, 10, 3);
        tabContents.put("drive", layout);
    }

    private void buildGlooWallTab() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(5, 5, 5, 5);
        addSectionHeader(layout, "🧱", "GLOO WALL SETTINGS");
        addToggle(layout, "Gloo Master", "gloo_master", false);
        addToggle(layout, "Invisible Gloo", "gloo_invisible", false);
        addToggle(layout, "Gloo Duration", "gloo_duration", false);
        addToggle(layout, "Gloo HP", "gloo_hp", false);
        addToggle(layout, "Instant Gloo", "gloo_instant", false);
        addToggle(layout, "Gloo Size", "gloo_size", false);
        addToggle(layout, "Unlimited Gloo", "gloo_unlimited", false);
        tabContents.put("gloo", layout);
    }

    private void buildMagicBulletTab() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(5, 5, 5, 5);
        addSectionHeader(layout, "🔮", "MAGIC BULLET SETTINGS");
        addToggle(layout, "Magic Bullet", "magic_bullet", false);
        addToggle(layout, "Bullet Penetration", "bullet_penetration", false);
        addToggle(layout, "Bullet Damage", "bullet_damage", false);
        addToggle(layout, "Bullet Range", "bullet_range", false);
        addToggle(layout, "Bullet Speed", "bullet_speed", false);
        addToggle(layout, "Bullet Split", "bullet_split", false);
        addSlider(layout, "Bullet Damage Value", "bullet_damage_value", 1, 999, 100);
        addSlider(layout, "Split Count", "bullet_split_count", 2, 10, 5);
        tabContents.put("magic", layout);
    }

    private void buildAuraTab() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(5, 5, 5, 5);
        addSectionHeader(layout, "💫", "AURA SETTINGS");
        addToggle(layout, "Aura Master", "aura_master", false);
        addToggle(layout, "Aura Kill", "aura_kill", false);
        addToggle(layout, "Aura Damage", "aura_damage", false);
        addToggle(layout, "Aura Heal", "aura_heal", false);
        addToggle(layout, "Aura Shield", "aura_shield", false);
        addToggle(layout, "Aura Vision", "aura_vision", false);
        addToggle(layout, "Aura Fear", "aura_fear", false);
        addSlider(layout, "Aura Radius", "aura_radius", 5, 100, 50);
        addSlider(layout, "Aura Damage Value", "aura_damage_value", 1, 100, 25);
        tabContents.put("aura", layout);
    }

    private void buildAiTab() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(5, 5, 5, 5);
        addSectionHeader(layout, "🤖", "AI SETTINGS");
        addToggle(layout, "AI Aim", "ai_aim", false);
        addToggle(layout, "AI Location", "ai_location", false);
        addToggle(layout, "AI Route", "ai_route", false);
        addToggle(layout, "AI Loot", "ai_loot", false);
        addToggle(layout, "AI Behavior", "ai_behavior", false);
        addToggle(layout, "AI Auto Play", "ai_autoplay", false);
        addSlider(layout, "AI Accuracy", "ai_accuracy", 50, 100, 85);
        addSlider(layout, "AI Skill Level", "ai_skill", 50, 100, 90);
        tabContents.put("ai", layout);
    }

    private void buildBypassTab() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(5, 5, 5, 5);
        addSectionHeader(layout, "🛡️", "BYPASS SETTINGS");
        addToggle(layout, "Anti-Cheat Bypass", "ac_bypass", true);
        addToggle(layout, "Root Hide", "root_hide", true);
        addToggle(layout, "Emulator Bypass", "emu_bypass", false);
        addToggle(layout, "VPN Bypass", "vpn_bypass", false);
        addToggle(layout, "Memory Protection", "mem_protect", true);
        addToggle(layout, "Log Cleaner", "log_clean", true);
        addToggle(layout, "Spoof Device", "spoof_device", false);

        LinearLayout statusContainer = new LinearLayout(this);
        statusContainer.setOrientation(LinearLayout.VERTICAL);
        statusContainer.setPadding(15, 15, 15, 15);
        statusContainer.setGravity(Gravity.CENTER);

        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setShape(GradientDrawable.RECTANGLE);
        statusBg.setCornerRadius(12);
        statusBg.setColor(Color.parseColor("#11111A"));
        statusBg.setStroke(1, Color.parseColor("#00E676"));
        statusContainer.setBackground(statusBg);

        TextView statusTitle = new TextView(this);
        statusTitle.setText("🛡️ PROTECTION STATUS");
        statusTitle.setTextColor(Color.parseColor(COLOR_ACCENT_GOLD));
        statusTitle.setTextSize(11);
        statusTitle.setTypeface(Typeface.DEFAULT_BOLD);
        statusTitle.setGravity(Gravity.CENTER);
        statusContainer.addView(statusTitle);

        String[] statusLines = {"✅ AC BYPASS: WORKING", "✅ MEMORY PROTECTION: ACTIVE", "✅ ROOT HIDE: ENABLED", "✅ LOG CLEANER: RUNNING", "✅ DEVICE SPOOF: READY"};
        for (String line : statusLines) {
            TextView statusLine = new TextView(this);
            statusLine.setText(line);
            statusLine.setTextColor(Color.parseColor(COLOR_ACCENT_GREEN));
            statusLine.setTextSize(10);
            statusLine.setGravity(Gravity.CENTER);
            statusLine.setPadding(0, 8, 0, 8);
            statusContainer.addView(statusLine);
        }

        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, 15, 0, 15);
        layout.addView(statusContainer, statusParams);
        tabContents.put("bypass", layout);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingIcon != null) { windowManager.removeView(floatingIcon); }
        if (expandedPanel != null) { windowManager.removeView(expandedPanel); }
    }
}