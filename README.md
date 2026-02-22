# Consent Nuker

One-tap rejection of all cookie consent toggles in Android apps.

## What It Does

Runs as an Android Accessibility Service in the background. When it detects a consent management dialog (the ones with hundreds of vendor toggles), it sends you a notification. Tap the notification and it will:

1. Scroll through the current consent screen, switching OFF every "Consent" and "Legitimate interest" toggle
2. Find and tap the "Vendors" tab/section if one exists
3. Scroll through the vendor list, switching OFF all those toggles too
4. Tap "Confirm choices" (or similar) to submit
5. Show a toast telling you how many toggles it switched off

**Safety rule:** It can only switch toggles OFF, never ON.

## Setup

### Prerequisites
- Android Studio (any recent version)
- An Android phone running Android 7.0+ (API 24+)
- USB debugging enabled on your phone

### Build and Install
1. Open this folder as a project in Android Studio
2. Connect your phone via USB
3. In Android Studio, click Run (green play button) or use `Build > Build APK`
4. If building APK manually, transfer it to your phone and install (you'll need to allow "Install from unknown sources")

### Enable the Service
1. Open the Consent Nuker app on your phone
2. Tap "Enable Accessibility Service"
3. Find "Consent Nuker" in the list and toggle it ON
4. Confirm the permissions dialog
5. On Android 13+, also allow notifications when prompted

## Usage

1. Use your phone normally
2. When a consent dialog appears in any app, you'll see a notification: "Consent dialog detected - Tap to nuke"
3. Tap the notification
4. Wait a few seconds while it processes
5. A toast will confirm: "Consent Nuker: X toggles switched OFF"

## Detection Patterns

The service looks for screens containing at least 2 of these indicators:
- "vendor preferences", "cookie duration", "legitimate interest"
- "consent management", "manage consent", "cookie consent"
- "privacy preferences", "we value your privacy", "we use cookies"
- And several others

## Customisation

### Adding New CMP Button Text
If a consent dialog uses different wording for its confirm button, edit the `CONFIRM_PATTERNS` array in `ConsentNukerService.java`.

### Adding New Detection Keywords
Edit the `CONSENT_SCREEN_INDICATORS` array to add patterns that identify consent dialogs you encounter.

### Adding Vendor Tab Wording
Edit the `VENDOR_TAB_PATTERNS` array if a CMP uses unusual text for its vendor section link.

## Limitations

- Accessibility trees vary between apps and CMP providers. Some WebView-based CMPs may not expose toggles as standard switch elements.
- The scroll detection tries both accessibility-based scrolling and gesture-based scrolling, but deeply nested dialogs may need manual scrolling before tapping nuke.
- Detection requires at least 2 matching keywords to avoid false positives. Very minimal consent dialogs might not trigger detection - you can always open the app and tweak the patterns.
- There's a 5-second cooldown between detections to prevent notification spam.

## Troubleshooting

**No notification appears:**
- Check the service is enabled in Accessibility Settings
- Check notification permissions are granted
- The dialog might not contain enough matching keywords - check logcat for "ConsentNuker" tags

**Toggles not flipping:**
- The CMP may use non-standard UI elements. Check logcat for what the service is finding.
- WebView-based CMPs sometimes don't expose their elements to the accessibility tree.

**Wrong toggles flipped:**
- Only use when on a consent screen. The service checks for consent-related context around each toggle, but unusual UIs could cause misidentification.
