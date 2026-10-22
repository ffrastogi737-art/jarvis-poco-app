import 'dart:async';
import 'dart:convert';

import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_timezone/flutter_timezone.dart';
import 'package:timezone/data/latest_all.dart' as tzdata;
import 'package:timezone/timezone.dart' as tz;

import 'store.dart';

/// What an incoming JARVIS call is about.
class CallRequest {
  final String message;
  final String? routineId;
  const CallRequest(this.message, {this.routineId});
  String toPayload() => jsonEncode({'message': message, 'routine': routineId});
  static CallRequest fromPayload(String p) {
    final j = jsonDecode(p);
    return CallRequest(j['message'] ?? '', routineId: j['routine']);
  }
}

/// JARVIS calling you: full-screen "incoming call" notifications at scheduled times,
/// for task reminders, routines and calls you ask him to make.
class Notify {
  static final _plugin = FlutterLocalNotificationsPlugin();
  static final incoming = StreamController<CallRequest>.broadcast();
  static CallRequest? launchCall;

  static const _details = NotificationDetails(
    android: AndroidNotificationDetails(
      'jarvis_calls',
      'JARVIS Calls',
      channelDescription: 'JARVIS calling you with reminders and results',
      importance: Importance.max,
      priority: Priority.max,
      category: AndroidNotificationCategory.call,
      fullScreenIntent: true,
      audioAttributesUsage: AudioAttributesUsage.notificationRingtone,
      ongoing: true,
      autoCancel: true,
      timeoutAfter: 60000,
    ),
  );

  static Future<void> init() async {
    tzdata.initializeTimeZones();
    try {
      tz.setLocalLocation(tz.getLocation((await FlutterTimezone.getLocalTimezone()).identifier));
    } catch (_) {
      tz.setLocalLocation(tz.getLocation('Asia/Kolkata'));
    }
    await _plugin.initialize(
      settings: const InitializationSettings(android: AndroidInitializationSettings('@mipmap/ic_launcher')),
      onDidReceiveNotificationResponse: (r) {
        if (r.payload != null) incoming.add(CallRequest.fromPayload(r.payload!));
      },
    );
    final launch = await _plugin.getNotificationAppLaunchDetails();
    if ((launch?.didNotificationLaunchApp ?? false) && launch!.notificationResponse?.payload != null) {
      launchCall = CallRequest.fromPayload(launch.notificationResponse!.payload!);
    }
  }

  static AndroidFlutterLocalNotificationsPlugin? get _android =>
      _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();

  static Future<void> requestPermissions() async {
    await _android?.requestNotificationsPermission();
    await _android?.requestExactAlarmsPermission();
    await _android?.requestFullScreenIntentPermission();
  }

  static int _id(String s) => s.hashCode & 0x7fffffff;

  static Future<void> scheduleCall(String id, DateTime at, String message, {String? routineId, bool daily = false}) async {
    if (!daily && at.isBefore(DateTime.now())) return;
    var when = tz.TZDateTime.from(at, tz.local);
    if (daily) {
      final now = tz.TZDateTime.now(tz.local);
      when = tz.TZDateTime(tz.local, now.year, now.month, now.day, at.hour, at.minute);
      if (when.isBefore(now)) when = when.add(const Duration(days: 1));
    }
    final exact = await _android?.canScheduleExactNotifications() ?? false;
    await _plugin.zonedSchedule(
      id: _id(id),
      title: 'J.A.R.V.I.S. is calling',
      body: message,
      scheduledDate: when,
      notificationDetails: _details,
      androidScheduleMode: exact ? AndroidScheduleMode.exactAllowWhileIdle : AndroidScheduleMode.inexactAllowWhileIdle,
      payload: CallRequest(message, routineId: routineId).toPayload(),
      matchDateTimeComponents: daily ? DateTimeComponents.time : null,
    );
  }

  static Future<void> cancel(String id) => _plugin.cancel(id: _id(id));

  /// Re-creates every scheduled call from stored data (after edits or app start).
  static Future<void> syncAll() async {
    for (final t in store.tasks) {
      await cancel(t.id);
      if (!t.done && t.due != null) await scheduleCall(t.id, t.due!, 'Sir, a reminder: "${t.title}" is due now.');
    }
    for (final r in store.routines) {
      await cancel(r.id);
      final p = r.time.split(':');
      if (r.enabled) {
        await scheduleCall(r.id, DateTime(2000, 1, 1, int.parse(p[0]), int.parse(p[1])), 'Running your routine: ${r.name}',
            routineId: r.id, daily: true);
      }
    }
    store.calls.removeWhere((c) => c.at.isBefore(DateTime.now().subtract(const Duration(minutes: 5))));
    for (final c in store.calls) {
      await scheduleCall(c.id, c.at, c.message);
    }
  }
}
