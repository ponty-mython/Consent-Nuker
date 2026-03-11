package com.consentnuker;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView statusText;
    private Button enableButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusText = findViewById(R.id.statusText);
        enableButton = findViewById(R.id.enableButton);
        enableButton.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 100);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean enabled = isAccessibilityServiceEnabled();
        if (enabled) {
            statusText.setText("Consent Nuker is ACTIVE.\n\nThe service is running. When a consent dialog is detected, you'll get a notification. Tap it to nuke all toggles.\n\nYou can close this app now.");
            statusText.setTextColor(0xFF2E7D32);
            enableButton.setText("Open Accessibility Settings");
        } else {
            statusText.setText("Consent Nuker is NOT ACTIVE.\n\nTap below to open Accessibility Settings, then find 'Consent Nuker' and enable it.");
            statusText.setTextColor(0xFFC62828);
            enableButton.setText("Enable Accessibility Service");
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/" + ConsentNukerService.class.getName());
    }
}
