package com.consentnuker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ConsentNukerService extends AccessibilityService {

    private static final String TAG = "ConsentNuker";
    private static final String CHANNEL_ID = "consent_nuker_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_NUKE = "com.consentnuker.ACTION_NUKE";

    private static ConsentNukerService instance;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isNuking = false;
    private long lastDetectionTime = 0;
    private static final long DETECTION_COOLDOWN_MS = 5000;
    private int totalTogglesFlipped = 0;

    // Detection patterns - text that indicates a consent management dialog
    private static final String[] CONSENT_SCREEN_INDICATORS = {
        "vendor preferences",
        "cookie duration",
        "legitimate interest",
        "data collected and processed",
        "consent management",
        "manage consent",
        "cookie consent",
        "privacy preferences",
        "manage preferences",
        "your privacy choices",
        "we value your privacy",
        "we use cookies",
        "partner preferences",
        "manage partners",
        "tcf vendors",
        "iab vendors"
    };

    // Patterns for toggles we want to switch OFF
    private static final String[] TOGGLE_TARGET_PATTERNS = {
        "consent",
        "legitimate interest"
    };

    // Patterns for the "Vendors" tab/button we need to navigate to
    private static final String[] VENDOR_TAB_PATTERNS = {
        "vendors",
        "vendor list",
        "vendor preferences",
        "see vendors",
        "view vendors",
        "partners",
        "partner list",
        "our partners",
        "see our partners"
    };

    // Patterns for the confirm button
    private static final String[] CONFIRM_PATTERNS = {
        "confirm choices",
        "confirm my choices",
        "save choices",
        "save preferences",
        "save my preferences",
        "save settings",
        "save & exit",
        "save and exit",
        "confirm",
        "accept selected",
        "reject all",
        "deny all",
        "refuse all"
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        Log.d(TAG, "ConsentNuker service created");
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public static ConsentNukerService getInstance() {
        return instance;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (isNuking) return;

        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastDetectionTime < DETECTION_COOLDOWN_MS) return;

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        if (isConsentScreen(rootNode)) {
            lastDetectionTime = now;
            showNukeNotification();
        }

        rootNode.recycle();
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    private boolean isConsentScreen(AccessibilityNodeInfo root) {
        int matchCount = 0;
        for (String indicator : CONSENT_SCREEN_INDICATORS) {
            if (findNodeWithText(root, indicator) != null) {
                matchCount++;
                // Require at least 2 matching indicators to reduce false positives
                if (matchCount >= 2) return true;
            }
        }
        return false;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Consent Nuker",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alerts when consent dialogs are detected");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showNukeNotification() {
        Intent nukeIntent = new Intent(this, NukeReceiver.class);
        nukeIntent.setAction(ACTION_NUKE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            this, 0, nukeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
            .setSmallIcon(android.R.drawable.ic_delete)
            .setContentTitle("Consent dialog detected")
            .setContentText("Tap to nuke all consent toggles")
            .setPriority(Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    /**
     * Main entry point - called when user taps the notification.
     * Orchestrates the full nuke sequence across both screens.
     */
    public void executeNuke() {
        if (isNuking) return;
        isNuking = true;
        totalTogglesFlipped = 0;

        Log.d(TAG, "Starting nuke sequence");

        // Dismiss the notification
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }

        // Phase 1: Process the current screen (main consent toggles)
        handler.post(() -> {
            processCurrentScreen(() -> {
                // Phase 2: Look for and tap a "Vendors" tab/button
                handler.postDelayed(() -> {
                    if (navigateToVendors()) {
                        // Phase 3: Wait for vendor screen to load, then process it
                        handler.postDelayed(() -> {
                            processCurrentScreen(() -> {
                                // Phase 4: Confirm and finish
                                handler.postDelayed(() -> tapConfirmAndFinish(), 500);
                            });
                        }, 1500);
                    } else {
                        // No vendor tab found - just confirm
                        handler.postDelayed(() -> tapConfirmAndFinish(), 500);
                    }
                }, 500);
            });
        });
    }

    /**
     * Processes the currently visible screen:
     * scrolls through all content and flips OFF any consent/legitimate interest toggles.
     */
    private void processCurrentScreen(Runnable onComplete) {
        processVisibleToggles(() -> {
            // Try scrolling down to find more
            scrollDownAndProcess(0, 5, onComplete);
        });
    }

    /**
     * Recursively scrolls down and processes toggles found after each scroll.
     */
    private void scrollDownAndProcess(int scrollCount, int maxScrolls, Runnable onComplete) {
        if (scrollCount >= maxScrolls) {
            if (onComplete != null) onComplete.run();
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // Find a scrollable container
        AccessibilityNodeInfo scrollable = findScrollableNode(root);
        if (scrollable != null) {
            boolean scrolled = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            if (scrolled) {
                handler.postDelayed(() -> {
                    processVisibleToggles(() -> {
                        scrollDownAndProcess(scrollCount + 1, maxScrolls, onComplete);
                    });
                }, 600);
            } else {
                // Can't scroll further
                if (onComplete != null) onComplete.run();
            }
        } else {
            // Try gesture-based scrolling as fallback
            performScrollGesture(() -> {
                handler.postDelayed(() -> {
                    processVisibleToggles(() -> {
                        scrollDownAndProcess(scrollCount + 1, maxScrolls, onComplete);
                    });
                }, 600);
            });
        }

        root.recycle();
    }

    /**
     * Finds and flips OFF all consent-related toggles currently visible on screen.
     * CRITICAL: Only switches toggles OFF, never ON.
     */
    private void processVisibleToggles(Runnable onComplete) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        List<AccessibilityNodeInfo> toggles = new ArrayList<>();
        findAllToggles(root, toggles);

        int toggleIndex = 0;
        flipTogglesSequentially(toggles, toggleIndex, () -> {
            root.recycle();
            if (onComplete != null) onComplete.run();
        });
    }

    /**
     * Flips toggles one at a time with a small delay to allow UI to update.
     */
    private void flipTogglesSequentially(List<AccessibilityNodeInfo> toggles, int index, Runnable onComplete) {
        if (index >= toggles.size()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        AccessibilityNodeInfo toggle = toggles.get(index);

        // SAFETY: Only flip if currently ON (checked). Never switch anything ON.
        if (toggle.isChecked() && isConsentRelatedToggle(toggle)) {
            Log.d(TAG, "Flipping toggle OFF: " + getToggleContext(toggle));
            toggle.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            totalTogglesFlipped++;

            // Small delay to let UI update before processing next toggle
            handler.postDelayed(() -> {
                flipTogglesSequentially(toggles, index + 1, onComplete);
            }, 150);
        } else {
            // Skip - either already off or not consent-related
            flipTogglesSequentially(toggles, index + 1, onComplete);
        }
    }

    /**
     * Determines if a toggle is associated with consent or legitimate interest.
     * Searches the toggle's own text, content description, and nearby sibling/parent text.
     */
    private boolean isConsentRelatedToggle(AccessibilityNodeInfo node) {
        // Check the toggle's own text and content description
        String selfText = getNodeTextLower(node);
        for (String pattern : TOGGLE_TARGET_PATTERNS) {
            if (selfText.contains(pattern)) return true;
        }

        // Check parent and sibling nodes for context
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            String parentContext = getAllTextInSubtree(parent).toLowerCase(Locale.ROOT);
            for (String pattern : TOGGLE_TARGET_PATTERNS) {
                if (parentContext.contains(pattern)) {
                    return true;
                }
            }

            // Check grandparent for wider context
            AccessibilityNodeInfo grandparent = parent.getParent();
            if (grandparent != null) {
                String gpContext = getAllTextInSubtree(grandparent).toLowerCase(Locale.ROOT);
                for (String pattern : TOGGLE_TARGET_PATTERNS) {
                    if (gpContext.contains(pattern)) {
                        return true;
                    }
                }
            }
        }

        // Also check: if we're on a screen that's been identified as a consent screen
        // and the toggle is inside a list of vendors, it's likely consent-related.
        // Check for vendor-related context higher up the tree.
        AccessibilityNodeInfo ancestor = node;
        for (int i = 0; i < 6; i++) {
            ancestor = ancestor.getParent();
            if (ancestor == null) break;
            String ancestorText = getNodeTextLower(ancestor);
            if (ancestorText.contains("vendor") || ancestorText.contains("partner")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Finds all toggle/switch elements in the node tree.
     */
    private void findAllToggles(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> toggles) {
        if (node == null) return;

        String className = node.getClassName() != null ? node.getClassName().toString() : "";

        if (className.contains("Switch") ||
            className.contains("ToggleButton") ||
            className.contains("CompoundButton") ||
            "android.widget.Switch".equals(className) ||
            "androidx.appcompat.widget.SwitchCompat".equals(className)) {
            toggles.add(node);
        }

        // Also check role description for WebView-based toggles
        if (node.isCheckable()) {
            String role = "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // No direct role access - but isCheckable covers most cases
            }
            if (!className.contains("CheckBox")) {
                // Include checkable items that aren't checkboxes
                // (checkboxes might be purpose-specific elsewhere)
                if (!toggles.contains(node)) {
                    toggles.add(node);
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findAllToggles(child, toggles);
            }
        }
    }

    /**
     * Attempts to find and tap a "Vendors" navigation element.
     */
    private boolean navigateToVendors() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        for (String pattern : VENDOR_TAB_PATTERNS) {
            AccessibilityNodeInfo vendorNode = findClickableNodeWithText(root, pattern);
            if (vendorNode != null) {
                Log.d(TAG, "Found vendor tab: " + pattern);
                vendorNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                root.recycle();
                return true;
            }
        }

        root.recycle();
        return false;
    }

    /**
     * Finds the confirm/save button and taps it, then shows the toast summary.
     */
    private void tapConfirmAndFinish() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            boolean confirmed = false;
            for (String pattern : CONFIRM_PATTERNS) {
                AccessibilityNodeInfo confirmNode = findClickableNodeWithText(root, pattern);
                if (confirmNode != null) {
                    Log.d(TAG, "Tapping confirm: " + pattern);
                    confirmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    confirmed = true;
                    break;
                }
            }

            if (!confirmed) {
                Log.d(TAG, "No confirm button found - looking for any button with confirm-like text");
                // Broader search - look for any clickable element
                AccessibilityNodeInfo broadConfirm = findAnyNodeWithConfirmText(root);
                if (broadConfirm != null) {
                    broadConfirm.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    confirmed = true;
                }
            }

            root.recycle();
        }

        // Show summary toast
        final int count = totalTogglesFlipped;
        handler.post(() -> {
            String message;
            if (count == 0) {
                message = "Consent Nuker: No toggles needed flipping";
            } else {
                message = String.format(Locale.UK, "Consent Nuker: %d toggle%s switched OFF",
                    count, count == 1 ? "" : "s");
            }
            Toast.makeText(ConsentNukerService.this, message, Toast.LENGTH_LONG).show();
        });

        isNuking = false;
        Log.d(TAG, "Nuke complete. Total toggles flipped: " + totalTogglesFlipped);
    }

    // --- Utility methods ---

    private AccessibilityNodeInfo findNodeWithText(AccessibilityNodeInfo root, String text) {
        if (root == null) return null;

        String nodeText = getNodeTextLower(root);
        if (nodeText.contains(text.toLowerCase(Locale.ROOT))) {
            return root;
        }

        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findNodeWithText(child, text);
                if (result != null) return result;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findClickableNodeWithText(AccessibilityNodeInfo root, String text) {
        if (root == null) return null;

        String nodeText = getNodeTextLower(root);
        if (nodeText.contains(text.toLowerCase(Locale.ROOT))) {
            if (root.isClickable()) return root;
            // Walk up to find clickable parent
            AccessibilityNodeInfo parent = root.getParent();
            for (int i = 0; i < 4 && parent != null; i++) {
                if (parent.isClickable()) return parent;
                parent = parent.getParent();
            }
            // If nothing clickable found, try clicking the node itself
            return root;
        }

        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findClickableNodeWithText(child, text);
                if (result != null) return result;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findAnyNodeWithConfirmText(AccessibilityNodeInfo root) {
        if (root == null) return null;

        String nodeText = getNodeTextLower(root);
        for (String pattern : CONFIRM_PATTERNS) {
            if (nodeText.contains(pattern.toLowerCase(Locale.ROOT))) {
                return root;
            }
        }

        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findAnyNodeWithConfirmText(child);
                if (result != null) return result;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo root) {
        if (root == null) return null;
        if (root.isScrollable()) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findScrollableNode(child);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void performScrollGesture(Runnable onComplete) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path path = new Path();
            // Swipe from bottom-center to top-center
            path.moveTo(540, 1600);
            path.lineTo(540, 800);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 300));
            dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    if (onComplete != null) onComplete.run();
                }
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    if (onComplete != null) onComplete.run();
                }
            }, handler);
        } else {
            if (onComplete != null) onComplete.run();
        }
    }

    private String getNodeTextLower(AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();
        if (node.getText() != null) sb.append(node.getText().toString().toLowerCase(Locale.ROOT)).append(" ");
        if (node.getContentDescription() != null) sb.append(node.getContentDescription().toString().toLowerCase(Locale.ROOT));
        return sb.toString();
    }

    private String getAllTextInSubtree(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        if (node.getText() != null) sb.append(node.getText().toString()).append(" ");
        if (node.getContentDescription() != null) sb.append(node.getContentDescription().toString()).append(" ");
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                sb.append(getAllTextInSubtree(child));
            }
        }
        return sb.toString();
    }

    private String getToggleContext(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            return getAllTextInSubtree(parent).trim().substring(0,
                Math.min(80, getAllTextInSubtree(parent).trim().length()));
        }
        return getNodeTextLower(node);
    }
}
