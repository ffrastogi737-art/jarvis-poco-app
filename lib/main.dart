import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'services/notify.dart';
import 'services/store.dart';
import 'ui/shell.dart';
import 'ui/theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await store.load();
  await Notify.init();
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
    systemNavigationBarColor: C.bg,
  ));
  runApp(const JarvisApp());
}

class JarvisApp extends StatelessWidget {
  const JarvisApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: store,
      builder: (_, __) => MaterialApp(
        debugShowCheckedModeBanner: false,
        title: 'J.A.R.V.I.S.',
        theme: ThemeData(
          brightness: Brightness.dark,
          scaffoldBackgroundColor: C.bg,
          colorScheme: ColorScheme.dark(primary: C.accent, secondary: C.accent, surface: C.cardTop),
          textSelectionTheme: TextSelectionThemeData(cursorColor: C.accent),
        ),
        home: const Shell(),
      ),
    );
  }
}
