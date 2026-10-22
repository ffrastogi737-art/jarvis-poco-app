package com.krishna.jarvis.jarvis_poco_app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Presses WhatsApp's Send button, but only for ~20 s after JARVIS itself opened a chat with a
 * prepared message (armed from Dart). It never acts on the user's own WhatsApp use.
 */
class JarvisAccessibility : AccessibilityService() {
    companion object {
        @Volatile var armedUntil = 0L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (System.currentTimeMillis() > armedUntil) return
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
        val send = rootInActiveWindow?.findAccessibilityNodeInfosByViewId("$pkg:id/send")?.firstOrNull { it.isEnabled } ?: return
        if (send.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) armedUntil = 0
    }

    override fun onInterrupt() {}
}
