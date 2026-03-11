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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    // Detection patterns for identifying consent screens
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
        "iab vendors",
        "confirm choices",
        "accept all"
    };

    // Patterns for the "Vendors" tab/button
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
            Log.d(TAG, "Consent screen detected! Showing notification.");
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
     * Main nuke entry point.
     */
    public void executeNuke() {
        if (isNuking) return;
        isNuking = true;
        totalTogglesFlipped = 0;

        Log.d(TAG, "=== STARTING NUKE SEQUENCE ===");

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }

        // First, dump the accessibility tree so we can debug
        handler.post(() -> {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                Log.d(TAG, "=== ACCESSIBILITY TREE DUMP ===");
                dumpNodeTree(root, 0);
                root.recycle();
            }

            // Phase 1: Process current screen
            processCurrentScreen(() -> {
                // Phase 2: Look for Vendors tab
                handler.postDelayed(() -> {
                    if (navigateToVendors()) {
                        // Phase 3: Process vendor screen
                        handler.postDelayed(() -> {
                            processCurrentScreen(() -> {
                                handler.postDelayed(() -> tapConfirmAndFinish(), 500);
                            });
                        }, 1500);
                    } else {
                        handler.postDelayed(() -> tapConfirmAndFinish(), 500);
                    }
                }, 500);
            });
        });
    }

    /**
     * Dumps the entire accessibility tree to logcat for debugging.
     */
    private void dumpNodeTree(AccessibilityNodeInfo node, int depth) {
        if (node == null) return;
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("  ");

        String className = node.getClassName() != null ? node.getClassName().toString() : "null";
        String text = node.getText() != null ? node.getText().toString() : "";
        String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
        String stateDesc = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            stateDesc = node.getStateDescription() != null ? node.getStateDescription().toString() : "";
        }
        String roleDesc = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // No direct getRoleDescription, but we log what we can
        }

        Log.d(TAG, indent + "CLASS=" + className
            + " TEXT=[" + text + "]"
            + " DESC=[" + desc + "]"
            + " STATE=[" + stateDesc + "]"
            + " checkable=" + node.isCheckable()
            + " checked=" + node.isChecked()
            + " clickable=" + node.isClickable()
            + " enabled=" + node.isEnabled()
            + " focusable=" + node.isFocusable()
        );

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                dumpNodeTree(child, depth + 1);
            }
        }
    }

    /**
     * Strategy for finding and flipping toggles:
     *
     * APPROACH 1 (Native): Look for standard Switch/ToggleButton widgets
     * APPROACH 2 (WebView): Find labels saying "Consent" or "Legitimate interest",
     *   then find the nearest clickable sibling/cousin that appears to be a toggle
     * APPROACH 3 (Brute force): Find ALL checkable elements, check if they're in
     *   a consent-related context
     * APPROACH 4 (Click by coordinates): If a label "Legitimate interest" has a
     *   toggle-like element to its right, click it by bounds
     */
    private void processCurrentScreen(Runnable onComplete) {
        processVisibleToggles(() -> {
            scrollDownAndProcess(0, 8, onComplete);
        });
    }

    private void scrollDownAndProcess(int scrollCount, int maxScrolls, Runnable onComplete) {
        if (scrollCount >= maxScrolls) {
            if (onComplete != null) onComplete.run();
            return;
        }

        boolean scrolled = tryScroll();
        if (scrolled) {
            handler.postDelayed(() -> {
                processVisibleToggles(() -> {
                    scrollDownAndProcess(scrollCount + 1, maxScrolls, onComplete);
                });
            }, 700);
        } else {
            if (onComplete != null) onComplete.run();
        }
    }

    private boolean tryScroll() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        AccessibilityNodeInfo scrollable = findScrollableNode(root);
        if (scrollable != null) {
            boolean result = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            root.recycle();
            return result;
        }
        root.recycle();

        // Gesture-based scroll fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(540, 1600);
            path.lineTo(540, 600);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 300));
            dispatchGesture(builder.build(), null, handler);
            return true;
        }
        return false;
    }

    private void processVisibleToggles(Runnable onComplete) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        List<ToggleCandidate> candidates = new ArrayList<>();

        // APPROACH 1: Standard checkable widgets that are checked
        findCheckableToggles(root, candidates);
        Log.d(TAG, "Approach 1 (checkable widgets): found " + candidates.size() + " candidates");

        // APPROACH 2: Label-based - find "Consent" / "Legitimate interest" labels
        // and look for the toggle near each one
        findLabelBasedToggles(root, candidates);
        Log.d(TAG, "After Approach 2 (label-based): total " + candidates.size() + " candidates");

        // Deduplicate by bounds
        List<ToggleCandidate> deduped = deduplicateCandidates(candidates);
        Log.d(TAG, "After dedup: " + deduped.size() + " unique candidates");

        flipCandidatesSequentially(deduped, 0, () -> {
            root.recycle();
            if (onComplete != null) onComplete.run();
        });
    }

    /**
     * APPROACH 1: Find any node that is checkable AND checked,
     * regardless of its class name. This catches WebView switches
     * that expose checkable state.
     */
    private void findCheckableToggles(AccessibilityNodeInfo node, List<ToggleCandidate> candidates) {
        if (node == null) return;

        // Check if this node is a toggle that's currently ON
        if (node.isCheckable() && node.isChecked() && node.isEnabled()) {
            String context = getAncestorText(node, 4).toLowerCase(Locale.ROOT);
            // Only target consent-related toggles
            if (context.contains("consent") || context.contains("legitimate interest")
                || context.contains("vendor") || context.contains("partner")
                || context.contains("cookie")) {
                Log.d(TAG, "APPROACH 1 HIT: checkable+checked node in consent context");
                candidates.add(new ToggleCandidate(node, "checkable"));
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findCheckableToggles(child, candidates);
            }
        }
    }

    /**
     * APPROACH 2: Find text labels "Consent" or "Legitimate interest",
     * then search for a clickable/checkable sibling element that acts as the toggle.
     * This is the key strategy for WebView-based CMPs.
     */
    private void findLabelBasedToggles(AccessibilityNodeInfo root, List<ToggleCandidate> candidates) {
        List<AccessibilityNodeInfo> consentLabels = new ArrayList<>();
        findLabelNodes(root, consentLabels);

        Log.d(TAG, "Found " + consentLabels.size() + " consent/legitimate interest labels");

        for (AccessibilityNodeInfo label : consentLabels) {
            String labelText = getNodeTextLower(label);
            Log.d(TAG, "Processing label: [" + labelText.trim() + "]");

            // Strategy A: Check siblings of the label's parent
            AccessibilityNodeInfo toggle = findToggleNearLabel(label);
            if (toggle != null) {
                Log.d(TAG, "APPROACH 2A: Found toggle near label via tree traversal");
                candidates.add(new ToggleCandidate(toggle, "label-sibling"));
                continue;
            }

            // Strategy B: Find a clickable element to the right of or below the label
            // by comparing screen bounds
            toggle = findToggleByPosition(root, label);
            if (toggle != null) {
                Log.d(TAG, "APPROACH 2B: Found toggle near label by position");
                candidates.add(new ToggleCandidate(toggle, "label-position"));
                continue;
            }

            // Strategy C: The label itself might be the toggle row.
            // Some CMPs make the whole row clickable.
            AccessibilityNodeInfo clickableParent = findClickableAncestor(label, 3);
            if (clickableParent != null && isToggleLikeState(clickableParent)) {
                Log.d(TAG, "APPROACH 2C: Label's clickable ancestor is toggle-like");
                candidates.add(new ToggleCandidate(clickableParent, "label-ancestor"));
            }
        }
    }

    /**
     * Finds all text nodes containing "consent" or "legitimate interest"
     * that appear to be toggle labels (not headers or descriptions).
     */
    private void findLabelNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> results) {
        if (node == null) return;

        String text = getNodeTextLower(node);

        // Match standalone "consent" or "legitimate interest" labels
        // Avoid matching long description text or headers
        if (text.length() < 80) {
            boolean isConsentLabel = text.contains("consent") && !text.contains("consent management")
                && !text.contains("cookie consent") && !text.contains("confirm");
            boolean isLegitLabel = text.contains("legitimate interest");

            if (isConsentLabel || isLegitLabel) {
                results.add(node);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findLabelNodes(child, results);
            }
        }
    }

    /**
     * Walk up from a label to its parent, then search siblings for a toggle element.
     */
    private AccessibilityNodeInfo findToggleNearLabel(AccessibilityNodeInfo label) {
        // Go up 1-3 levels and search children for toggle-like elements
        AccessibilityNodeInfo current = label;
        for (int level = 0; level < 4; level++) {
            AccessibilityNodeInfo parent = current.getParent();
            if (parent == null) break;

            for (int i = 0; i < parent.getChildCount(); i++) {
                AccessibilityNodeInfo sibling = parent.getChild(i);
                if (sibling == null || sibling.equals(current)) continue;

                // Is this sibling a toggle?
                if (isToggleElement(sibling)) {
                    if (isToggledOn(sibling)) {
                        return sibling;
                    }
                }

                // Check sibling's children too (one level deep)
                for (int j = 0; j < sibling.getChildCount(); j++) {
                    AccessibilityNodeInfo nephewNode = sibling.getChild(j);
                    if (nephewNode != null && isToggleElement(nephewNode) && isToggledOn(nephewNode)) {
                        return nephewNode;
                    }
                }
            }
            current = parent;
        }
        return null;
    }

    /**
     * Find a toggle element positioned to the right of the label on screen.
     */
    private AccessibilityNodeInfo findToggleByPosition(AccessibilityNodeInfo root, AccessibilityNodeInfo label) {
        Rect labelBounds = new Rect();
        label.getBoundsInScreen(labelBounds);

        List<AccessibilityNodeInfo> allClickables = new ArrayList<>();
        findAllClickableNodes(root, allClickables);

        AccessibilityNodeInfo bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        for (AccessibilityNodeInfo clickable : allClickables) {
            Rect clickBounds = new Rect();
            clickable.getBoundsInScreen(clickBounds);

            // Must be roughly on the same vertical line (within 100px)
            int verticalDist = Math.abs(clickBounds.centerY() - labelBounds.centerY());
            if (verticalDist > 100) continue;

            // Must be to the right of the label
            if (clickBounds.centerX() <= labelBounds.centerX()) continue;

            // Prefer closer elements
            int dist = clickBounds.centerX() - labelBounds.centerX();
            if (dist < bestDistance) {
                // Check if it looks like a toggle that's ON
                if (isToggleElement(clickable) && isToggledOn(clickable)) {
                    bestDistance = dist;
                    bestMatch = clickable;
                } else if (clickable.isCheckable() && clickable.isChecked()) {
                    bestDistance = dist;
                    bestMatch = clickable;
                }
            }
        }

        return bestMatch;
    }

    /**
     * Determines if a node looks like a toggle/switch element.
     */
    private boolean isToggleElement(AccessibilityNodeInfo node) {
        if (node == null) return false;

        String className = node.getClassName() != null ? node.getClassName().toString() : "";

        // Native switches
        if (className.contains("Switch") || className.contains("ToggleButton") ||
            className.contains("CompoundButton")) {
            return true;
        }

        // Checkable elements (WebView toggles often expose this)
        if (node.isCheckable()) {
            return true;
        }

        // Clickable View with state description (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && node.isClickable()) {
            CharSequence stateDesc = node.getStateDescription();
            if (stateDesc != null) {
                String state = stateDesc.toString().toLowerCase(Locale.ROOT);
                if (state.contains("on") || state.contains("off") ||
                    state.contains("checked") || state.contains("unchecked")) {
                    return true;
                }
            }
        }

        // Clickable element with content description suggesting toggle
        if (node.isClickable()) {
            String desc = node.getContentDescription() != null ?
                node.getContentDescription().toString().toLowerCase(Locale.ROOT) : "";
            if (desc.contains("toggle") || desc.contains("switch") ||
                desc.contains("on") || desc.contains("off")) {
                return true;
            }
        }

        // WebView: clickable View with role=switch
        // The role may appear in the className or extras
        if (node.isClickable() && className.equals("android.view.View")) {
            // Generic clickable View - could be a WebView toggle
            // Check if it has a small-ish size (toggles are usually compact)
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            int width = bounds.width();
            int height = bounds.height();
            if (width > 30 && width < 300 && height > 15 && height < 150) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines if a toggle-like element is currently in the ON state.
     * CRITICAL: We only flip OFF, never ON.
     */
    private boolean isToggledOn(AccessibilityNodeInfo node) {
        // Standard checked state
        if (node.isCheckable() && node.isChecked()) {
            return true;
        }

        // State description (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            CharSequence stateDesc = node.getStateDescription();
            if (stateDesc != null) {
                String state = stateDesc.toString().toLowerCase(Locale.ROOT);
                // "ON" or "checked" means it's toggled on
                if (state.equals("on") || state.contains("checked") || state.equals("true")) {
                    return true;
                }
            }
        }

        // Content description
        String desc = node.getContentDescription() != null ?
            node.getContentDescription().toString().toLowerCase(Locale.ROOT) : "";
        if (desc.contains("on") || desc.contains("enabled") || desc.contains("active")) {
            // Make sure it's not "consent" containing "on" coincidentally
            if (desc.equals("on") || desc.contains("toggle on") || desc.contains("switch on")
                || desc.contains("turned on") || desc.endsWith(" on")) {
                return true;
            }
        }

        // Text content
        String text = node.getText() != null ?
            node.getText().toString().toLowerCase(Locale.ROOT) : "";
        if (text.equals("on") || text.equals("true")) {
            return true;
        }

        return false;
    }

    /**
     * Check if an ancestor-level node has toggle-like state.
     */
    private boolean isToggleLikeState(AccessibilityNodeInfo node) {
        if (node.isCheckable() && node.isChecked()) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            CharSequence stateDesc = node.getStateDescription();
            if (stateDesc != null) {
                String state = stateDesc.toString().toLowerCase(Locale.ROOT);
                return state.equals("on") || state.contains("checked");
            }
        }
        return false;
    }

    private AccessibilityNodeInfo findClickableAncestor(AccessibilityNodeInfo node, int maxLevels) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < maxLevels; i++) {
            current = current.getParent();
            if (current == null) return null;
            if (current.isClickable()) return current;
        }
        return null;
    }

    private void findAllClickableNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> results) {
        if (node == null) return;
        if (node.isClickable() || node.isCheckable()) {
            results.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findAllClickableNodes(child, results);
            }
        }
    }

    /**
     * Flips toggle candidates one at a time.
     */
    private void flipCandidatesSequentially(List<ToggleCandidate> candidates, int index, Runnable onComplete) {
        if (index >= candidates.size()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        ToggleCandidate candidate = candidates.get(index);
        AccessibilityNodeInfo node = candidate.node;

        Log.d(TAG, "Flipping candidate " + index + " (found via " + candidate.source + ")");

        // Try click action
        boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if (!clicked) {
            // Try clicking by tapping the center of the element's bounds
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            Log.d(TAG, "Direct click failed, trying gesture at " + bounds.centerX() + "," + bounds.centerY());
            performTapGesture(bounds.centerX(), bounds.centerY());
        }

        totalTogglesFlipped++;

        handler.postDelayed(() -> {
            flipCandidatesSequentially(candidates, index + 1, onComplete);
        }, 200);
    }

    /**
     * Tap at specific screen coordinates using a gesture.
     */
    private void performTapGesture(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
            dispatchGesture(builder.build(), null, handler);
        }
    }

    /**
     * Navigate to the Vendors tab.
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
     * Find and tap the confirm button, then show results toast.
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
                // Try broader search
                List<AccessibilityNodeInfo> allClickables = new ArrayList<>();
                findAllClickableNodes(root, allClickables);
                for (AccessibilityNodeInfo node : allClickables) {
                    String nodeText = getNodeTextLower(node);
                    for (String pattern : CONFIRM_PATTERNS) {
                        if (nodeText.contains(pattern)) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            confirmed = true;
                            break;
                        }
                    }
                    if (confirmed) break;
                }
            }

            root.recycle();
        }

        final int count = totalTogglesFlipped;
        handler.post(() -> {
            String message;
            if (count == 0) {
                message = "Consent Nuker: No toggles needed flipping. Check logcat for debug info.";
            } else {
                message = String.format(Locale.UK, "Consent Nuker: %d toggle%s switched OFF",
                    count, count == 1 ? "" : "s");
            }
            Toast.makeText(ConsentNukerService.this, message, Toast.LENGTH_LONG).show();
            Log.d(TAG, message);
        });

        isNuking = false;
        Log.d(TAG, "=== NUKE COMPLETE. Total flipped: " + totalTogglesFlipped + " ===");
    }

    private List<ToggleCandidate> deduplicateCandidates(List<ToggleCandidate> candidates) {
        List<ToggleCandidate> result = new ArrayList<>();
        List<String> seenBounds = new ArrayList<>();

        for (ToggleCandidate candidate : candidates) {
            Rect bounds = new Rect();
            candidate.node.getBoundsInScreen(bounds);
            String key = bounds.toShortString();
            if (!seenBounds.contains(key)) {
                seenBounds.add(key);
                result.add(candidate);
            }
        }
        return result;
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
            AccessibilityNodeInfo parent = root.getParent();
            for (int i = 0; i < 4 && parent != null; i++) {
                if (parent.isClickable()) return parent;
                parent = parent.getParent();
            }
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

    private String getNodeTextLower(AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();
        if (node.getText() != null) sb.append(node.getText().toString().toLowerCase(Locale.ROOT)).append(" ");
        if (node.getContentDescription() != null)
            sb.append(node.getContentDescription().toString().toLowerCase(Locale.ROOT));
        return sb.toString();
    }

    /**
     * Get text from ancestors up to maxLevels above this node.
     */
    private String getAncestorText(AccessibilityNodeInfo node, int maxLevels) {
        StringBuilder sb = new StringBuilder();
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < maxLevels; i++) {
            current = current.getParent();
            if (current == null) break;
            if (current.getText() != null) sb.append(current.getText().toString()).append(" ");
            if (current.getContentDescription() != null)
                sb.append(current.getContentDescription().toString()).append(" ");
        }
        return sb.toString();
    }

    /**
     * Holds a toggle candidate with metadata about how it was found.
     */
    private static class ToggleCandidate {
        final AccessibilityNodeInfo node;
        final String source;

        ToggleCandidate(AccessibilityNodeInfo node, String source) {
            this.node = node;
            this.source = source;
        }
    }
}
