package com.krishna.jarvis.jarvis_poco_app

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/** Phone-control bridge for JARVIS: open apps, send SMS, auto-send WhatsApp. */
class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "jarvis/device").setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "listApps" -> {
                        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        result.success(packageManager.queryIntentActivities(launcher, 0).map {
                            mapOf("name" to it.loadLabel(packageManager).toString(), "package" to it.activityInfo.packageName)
                        })
                    }
                    "openApp" -> {
                        val intent = packageManager.getLaunchIntentForPackage(call.argument<String>("package")!!)
                        if (intent == null) result.success(false) else {
                            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            result.success(true)
                        }
                    }
                    "sendSms" -> {
                        val sms = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
                        val parts = sms.divideMessage(call.argument<String>("text")!!)
                        sms.sendMultipartTextMessage(call.argument<String>("number")!!, null, parts, null, null)
                        result.success(true)
                    }
                    "armWhatsAppSend" -> {
                        JarvisAccessibility.armedUntil = System.currentTimeMillis() + 20000
                        result.success(true)
                    }
                    "accessibilityEnabled" -> {
                        val me = ComponentName(this, JarvisAccessibility::class.java).flattenToString()
                        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                        result.success(enabled.split(':').any { it.equals(me, ignoreCase = true) })
                    }
                    "openAccessibilitySettings" -> {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            } catch (e: Exception) {
                result.error("jarvis", e.message, null)
            }
        }
    }
}
