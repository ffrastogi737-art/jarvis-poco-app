import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jarvis_poco_app/main.dart';
import 'package:jarvis_poco_app/screens/call.dart';
import 'package:jarvis_poco_app/screens/devices.dart';
import 'package:jarvis_poco_app/services/notify.dart';
import 'package:jarvis_poco_app/services/store.dart';
import 'package:jarvis_poco_app/ui/shell.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Renders every main screen to make sure none of them throws.
/// Set SCREENSHOT_DIR to also save PNGs (real fonts are loaded from the Flutter SDK when available).
void main() {
  final shots = Platform.environment['SCREENSHOT_DIR'];

  setUpAll(() async {
    TestWidgetsFlutterBinding.ensureInitialized();
    AndroidFlutterLocalNotificationsPlugin.registerWith();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(const MethodChannel('dexterous.com/flutter/local_notifications'), (_) async => null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(const MethodChannel('flutter_ringtone_player'), (_) async => null);
    final fonts = '${Platform.environment['FLUTTER_ROOT'] ?? ''}/bin/cache/artifacts/material_fonts';
    if (File('$fonts/Roboto-Regular.ttf').existsSync()) {
      Future<void> load(String family, List<String> files) async {
        final l = FontLoader(family);
        for (final f in files) {
          l.addFont(Future.value(ByteData.sublistView(File('$fonts/$f').readAsBytesSync())));
        }
        await l.load();
      }

      await load('Roboto', ['Roboto-Regular.ttf', 'Roboto-Medium.ttf']);
      await load('MaterialIcons', ['MaterialIcons-Regular.otf']);
    }
  });

  final screens = <String, void Function(ShellState)>{
    'home': (s) {},
    'dashboard': (s) {
      store.chat
        ..add(Msg('user', 'What is in this image?'))
        ..add(Msg('jarvis', 'Analyzed image. Here are the main points: a futuristic city skyline at dusk with flying vehicles.'));
      store.tasks.addAll([
        TaskItem(title: 'Complete Project Report', due: DateTime.now().copyWith(hour: 18, minute: 0)),
        TaskItem(title: 'Workout - Push Day', due: DateTime.now().copyWith(hour: 19, minute: 0)),
        TaskItem(title: 'Plan Weekend Trip', due: DateTime.now().add(const Duration(days: 1)).copyWith(hour: 10, minute: 0)),
      ]);
      store.saveChat();
    },
    'tools': (s) => s.goTab(2),
    'devices': (s) => s.push(const DevicesScreen()),
    'call': (s) => s.push(const CallScreen(incoming: CallRequest('Sir, your meeting starts in 10 minutes.'))),
    'settings': (s) => s.goTab(4),
    'tasks': (s) => s.goTab(1),
    'memory': (s) => s.goTab(3),
  };

  for (final e in screens.entries) {
    testWidgets('renders ${e.key}', (tester) async {
      SharedPreferences.setMockInitialValues({});
      await store.load();
      tester.view.physicalSize = const Size(1080, 2340);
      tester.view.devicePixelRatio = 2.75;
      await tester.pumpWidget(const JarvisApp());
      await tester.pump(const Duration(milliseconds: 300));
      e.value(tester.state<ShellState>(find.byType(Shell)));
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(milliseconds: 250));
      }
      if (shots != null) {
        await expectLater(find.byType(Shell), matchesGoldenFile(Uri.file('$shots/${e.key}.png')));
      }
      await tester.pumpWidget(const SizedBox());
      await tester.pump(const Duration(seconds: 1));
    });
  }
}
