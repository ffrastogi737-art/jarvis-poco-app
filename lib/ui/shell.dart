import 'dart:async';

import 'package:flutter/material.dart';

import '../screens/call.dart';
import '../screens/chat.dart';
import '../screens/devices.dart';
import '../screens/home.dart';
import '../screens/memory.dart';
import '../screens/settings.dart';
import '../screens/tasks.dart';
import '../screens/tools.dart';
import '../services/brain.dart';
import '../services/devices.dart';
import '../services/jarvis.dart';
import '../services/notify.dart';
import '../services/store.dart';
import 'theme.dart';

/// App frame from the design: header on top, bottom nav, pages in between.
/// Sub-pages (devices, calls, tool pages) open inside the frame so header and nav stay visible.
class Shell extends StatefulWidget {
  const Shell({super.key});
  static ShellState of(BuildContext context) => context.findAncestorStateOfType<ShellState>()!;
  @override
  State<Shell> createState() => ShellState();
}

class ShellState extends State<Shell> with WidgetsBindingObserver {
  int tab = 0;
  final List<Widget> _pages = [];
  final _scaffold = GlobalKey<ScaffoldState>();
  Timer? _poll;
  StreamSubscription? _calls;

  static const _tabs = [HomeScreen(), TasksScreen(), ToolsScreen(), MemoryScreen(), SettingsScreen()];

  void push(Widget page) => setState(() => _pages.add(page));
  void pop() => setState(() => _pages.isEmpty ? null : _pages.removeLast());
  void goTab(int i) => setState(() {
        _pages.clear();
        tab = i;
      });

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    jarvis.confirm = _confirm;
    _calls = Notify.incoming.stream.listen((c) => push(CallScreen(incoming: c)));
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (Notify.launchCall != null) push(CallScreen(incoming: Notify.launchCall!));
      Notify.launchCall = null;
      await Notify.requestPermissions();
      await Notify.syncAll();
    });
    devices.refresh();
    _poll = Timer.periodic(const Duration(seconds: 30), (_) => _tick());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) devices.refresh();
  }

  /// Foreground housekeeping: device status, device→phone events, calls that are due right now.
  Future<void> _tick() async {
    _dueCalls();
    await devices.refresh();
    if (store.get<bool>('pollDevices')) {
      for (final d in store.devices.where((d) => d.trusted && (devices.status[d.id]?.online ?? false))) {
        for (final e in await devices.events(d)) {
          final msg = '${e['message'] ?? e['text'] ?? ''}';
          if (msg.isEmpty) continue;
          if (e['type'] == 'call') {
            push(CallScreen(incoming: CallRequest('${d.name} reports: $msg')));
          } else if (mounted) {
            toast(context, '${d.name}: $msg');
          }
        }
      }
    }
  }

  final _fired = <String>{};

  /// While the app is open, ring in-app instead of relying on the notification.
  void _dueCalls() {
    final now = DateTime.now();
    bool due(DateTime t) => !t.isAfter(now) && now.difference(t).inSeconds < 31;
    void ring(String id, String msg, [String? routine]) {
      if (!_fired.add(id)) return;
      if (routine == null) Notify.cancel(id); // daily routine alarms must stay scheduled
      push(CallScreen(incoming: CallRequest(msg, routineId: routine)));
    }

    for (final t in store.tasks.where((t) => !t.done && t.due != null && due(t.due!))) {
      ring(t.id, 'Sir, a reminder: "${t.title}" is due now.');
    }
    for (final c in store.calls.where((c) => due(c.at))) {
      ring(c.id, c.message);
    }
    final hhmm = '${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}';
    for (final r in store.routines.where((r) => r.enabled && r.time == hhmm)) {
      ring('${r.id}@${now.day}', 'Running your routine: ${r.name}', r.id);
    }
  }

  Future<bool> _confirm(String q) async =>
      await showDialog<bool>(
        context: context,
        builder: (c) => AlertDialog(
          backgroundColor: C.cardTop,
          title: const Text('J.A.R.V.I.S.', style: TextStyle(color: Colors.white)),
          content: Text(q, style: const TextStyle(color: Colors.white)),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
            TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Yes')),
          ],
        ),
      ) ??
      false;

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _poll?.cancel();
    _calls?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: _pages.isEmpty && tab == 0,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        _pages.isNotEmpty ? pop() : goTab(0);
      },
      child: Scaffold(
        key: _scaffold,
        backgroundColor: C.bg,
        drawer: _drawer(),
        body: Container(
          decoration: const BoxDecoration(
            gradient: RadialGradient(center: Alignment(0, -0.4), radius: 1.2, colors: [Color(0xFF04172A), C.bg]),
          ),
          child: SafeArea(
            child: Column(children: [
              _header(),
              Expanded(
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 220),
                  child: KeyedSubtree(
                    key: ValueKey(_pages.isEmpty ? 'tab$tab' : '${_pages.length}${_pages.last.runtimeType}'),
                    child: _pages.isEmpty ? _tabs[tab] : _pages.last,
                  ),
                ),
              ),
              _nav(),
            ]),
          ),
        ),
      ),
    );
  }

  Widget _header() => Padding(
        padding: const EdgeInsets.fromLTRB(10, 8, 10, 4),
        child: Glass(
          radius: 16,
          padding: const EdgeInsets.fromLTRB(8, 8, 10, 8),
          child: Row(children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: C.accent.withValues(alpha: 0.35)),
              ),
              child: IconButton(
                  padding: EdgeInsets.zero,
                  icon: const Icon(Icons.menu, color: Colors.white),
                  onPressed: () => _scaffold.currentState!.openDrawer()),
            ),
            Expanded(
              child: GestureDetector(
                onTap: () => goTab(0),
                child: Column(mainAxisSize: MainAxisSize.min, children: [
                  Text('J.A.R.V.I.S.',
                      style: TextStyle(
                          color: Colors.white,
                          fontSize: 20,
                          letterSpacing: 3,
                          fontWeight: FontWeight.w500,
                          shadows: [Shadow(color: C.accent, blurRadius: 10)])),
                  ValueListenableBuilder(
                    valueListenable: Brain.online,
                    builder: (_, on, __) => Row(mainAxisSize: MainAxisSize.min, children: [
                      Text(on == false ? 'CLOUD BRAIN OFFLINE' : 'CLOUD BRAIN ONLINE',
                          style: TextStyle(color: on == false ? C.red : C.accent, fontSize: 10.5, letterSpacing: 0.6)),
                      const SizedBox(width: 5),
                      Icon(Icons.cloud_done_outlined, size: 14, color: on == false ? C.red : C.green),
                    ]),
                  ),
                ]),
              ),
            ),
            IconButton(
                icon: Icon(Icons.shield_outlined, color: C.accent, shadows: [Shadow(color: C.accent, blurRadius: 8)]),
                onPressed: () => push(const PrivacyPage())),
            IconButton(
                icon: const Icon(Icons.manage_accounts_outlined, color: Colors.white), onPressed: () => push(const ProfilePage())),
          ]),
        ),
      );

  Widget _nav() {
    const items = [
      (Icons.home_rounded, Icons.home_outlined, 'Home'),
      (Icons.check_box, Icons.check_box_outlined, 'Tasks'),
      (Icons.grid_view_rounded, Icons.grid_view_outlined, 'Tools'),
      (Icons.psychology, Icons.psychology_outlined, 'Memory'),
      (Icons.person, Icons.person_outline, 'Profile'),
    ];
    return Container(
      margin: const EdgeInsets.fromLTRB(8, 4, 8, 6),
      padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 6),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        color: const Color(0xFF040D18),
        border: Border(top: BorderSide(color: C.accent.withValues(alpha: 0.35))),
      ),
      child: Row(children: [
        for (var i = 0; i < items.length; i++)
          Expanded(
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: () => goTab(i),
              child: () {
                final sel = _pages.isEmpty && tab == i;
                return Container(
                  padding: const EdgeInsets.symmetric(vertical: 7),
                  margin: const EdgeInsets.symmetric(horizontal: 3),
                  decoration: sel
                      ? BoxDecoration(
                          borderRadius: BorderRadius.circular(10),
                          gradient: LinearGradient(
                              begin: Alignment.topCenter,
                              end: Alignment.bottomCenter,
                              colors: [C.accent.withValues(alpha: 0.35), C.accent.withValues(alpha: 0.08)]),
                          boxShadow: [BoxShadow(color: C.accent.withValues(alpha: 0.35), blurRadius: 12)],
                        )
                      : null,
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Icon(sel ? items[i].$1 : items[i].$2, color: sel ? C.accent : const Color(0xFFD2DEEA), size: 25),
                    const SizedBox(height: 3),
                    Text(items[i].$3, style: TextStyle(fontSize: 11, color: sel ? C.accent : const Color(0xFFD2DEEA))),
                  ]),
                );
              }(),
            ),
          ),
      ]),
    );
  }

  Widget _drawer() {
    Widget item(IconData i, String t, VoidCallback f) => ListTile(
          leading: NeonIcon(i, size: 24),
          title: Text(t, style: const TextStyle(color: Colors.white)),
          onTap: () {
            Navigator.pop(context);
            f();
          },
        );
    return Drawer(
      backgroundColor: const Color(0xFF040E1A),
      child: SafeArea(
        child: ListView(children: [
          Padding(
            padding: const EdgeInsets.all(20),
            child: Text('J.A.R.V.I.S.', style: TextStyle(color: C.accent, fontSize: 22, letterSpacing: 3)),
          ),
          item(Icons.add_comment_outlined, 'New conversation', () {
            jarvis.newConversation();
            goTab(0);
          }),
          item(Icons.forum_outlined, 'Conversation', () => push(const ChatPage())),
          item(Icons.call, 'Call JARVIS', () => push(const CallScreen())),
          item(Icons.devices_other, 'Connected Devices', () => push(const DevicesScreen())),
          item(Icons.check_box_outlined, 'Tasks', () => goTab(1)),
          item(Icons.grid_view_rounded, 'Tools', () => goTab(2)),
          item(Icons.psychology_outlined, 'Memory', () => goTab(3)),
          item(Icons.settings_outlined, 'Settings', () => goTab(4)),
        ]),
      ),
    );
  }
}
