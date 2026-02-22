package com.consentnuker;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
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
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 100);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        if (enabled) {
            statusText.setText("Consent Nuker is ACTIVE.\n\nThe service is running in the background. " +
                "When a consent dialog is detected, you'll get a notification. Tap it to nuke all toggles.\n\n" +
                "You can close this app now.");
            statusText.setTextColor(0xFF2E7D32); // Green
            enableButton.setText("Open Accessibility Settings");
        } else {
            statusText.setText("Consent Nuker is NOT ACTIVE.\n\n" +
                "Tap the button below to open Accessibility Settings, then find " +
                "'Consent Nuker' and enable it.");
            statusText.setTextColor(0xFFC62828); // Red
            enableButton.setText("Enable Accessibility Service");
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices != null) {
            return enabledServices.contains(getPackageName() + "/" +
                ConsentNukerService.class.getName());
        }
        return false;
    }
}
