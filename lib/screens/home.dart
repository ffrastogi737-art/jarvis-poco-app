import 'dart:io';

import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../services/brain.dart' show Attachment;
import '../services/devices.dart';
import '../services/jarvis.dart';
import '../services/store.dart';
import '../services/voice.dart';
import '../services/web.dart';
import '../ui/pick.dart';
import '../ui/reactor.dart';
import '../ui/shell.dart';
import '../ui/theme.dart';
import 'call.dart';
import 'chat.dart';
import 'devices.dart';
import 'tools.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});
  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  static Future<Weather>? _weather;
  static Future<List<NewsItem>>? _news;

  void _load({bool force = false}) {
    if (force || _weather == null) _weather = Web.weather();
    if (force || _news == null) _news = Web.news();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([jarvis, store, devices]),
      builder: (context, _) {
        final dashboard = jarvis.hasConversation;
        if (dashboard) _load();
        return Column(children: [
          Expanded(child: dashboard ? _dashboard() : _idle()),
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 4, 12, 6),
            child: JarvisInput(hint: dashboard ? 'Ask a follow-up...' : 'Talk to JARVIS...'),
          ),
        ]);
      },
    );
  }

  String get _greeting {
    final h = DateTime.now().hour;
    return h < 5 ? 'Good night' : h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
  }

  Widget _idle() {
    final shell = Shell.of(context);
    return LayoutBuilder(
      builder: (context, box) => SingleChildScrollView(
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: box.maxHeight),
          child: Column(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
            const SizedBox(height: 8),
            Text('$_greeting, ${store.get<String>('name')}.',
                style: const TextStyle(color: Color(0xFFBDEBFF), fontSize: 22, fontWeight: FontWeight.w500)),
            Reactor(height: (box.maxHeight * 0.48).clamp(220.0, 320.0)),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 52),
              child: Glass(
                radius: 12,
                onTap: () => shell.push(const DevicesScreen()),
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                child: Row(children: [
                  Icon(Icons.cloud_outlined, color: C.accent, size: 34),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      const Text('CONNECTED DEVICES:', style: TextStyle(color: C.text2, fontSize: 12, letterSpacing: 0.5)),
                      const SizedBox(height: 3),
                      Text(devices.connectedSummary(),
                          style: const TextStyle(color: Colors.white, fontSize: 13, letterSpacing: 0.3),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis),
                    ]),
                  ),
                ]),
              ),
            ),
            Text(jarvis.busy ? jarvis.status : '“A smarter you, every day.”',
                style: const TextStyle(color: Color(0xFFDDE8F2), fontSize: 15)),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Row(children: [
                for (final c in [
                  ('Research', () => shell.push(const SearchPage(research: true))),
                  ('Analyze', () => _analyze()),
                  ('Create', () => shell.push(const CreatePage())),
                  ('Vision', () => shell.push(const VisionPage(startCamera: true))),
                ])
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: Glass(
                        radius: 10,
                        onTap: c.$2,
                        padding: const EdgeInsets.symmetric(vertical: 11),
                        child: Center(child: Text(c.$1, style: const TextStyle(color: Colors.white, fontSize: 13.5))),
                      ),
                    ),
                  ),
              ]),
            ),
          ]),
        ),
      ),
    );
  }

  Future<void> _analyze() async {
    final a = await pickAttachment(context);
    if (a != null) jarvis.ask('Analyse this ${a.isImage ? 'image' : 'file'} and give me the key points.', files: [a]);
  }

  Widget _dashboard() {
    final shell = Shell.of(context);
    final last = jarvis.lastReply;
    final open = store.tasks.where((t) => !t.done).toList()
      ..sort((a, b) => (a.due ?? DateTime(9999)).compareTo(b.due ?? DateTime(9999)));
    return RefreshIndicator(
      onRefresh: () async {
        setState(() => _load(force: true));
        await devices.refresh();
      },
      child: ListView(padding: const EdgeInsets.symmetric(horizontal: 12), children: [
        const Reactor(height: 118, compact: true, label: false),
        Center(
          child: Text(jarvis.busy ? jarvis.status : 'Here\'s what I found for you.',
              style: const TextStyle(color: Color(0xFFDDE8F2), fontSize: 15)),
        ),
        const SizedBox(height: 14),
        IntrinsicHeight(
          child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Expanded(flex: 10, child: _weatherCard()),
            const SizedBox(width: 10),
            Expanded(flex: 11, child: _newsCard()),
          ]),
        ),
        const SizedBox(height: 10),
        Glass(
          padding: const EdgeInsets.fromLTRB(14, 10, 14, 6),
          child: Column(children: [
            Row(children: [
              NeonIcon(Icons.check_box_outlined, size: 20),
              const SizedBox(width: 8),
              const Expanded(child: Text('Your Tasks', style: TextStyle(color: Colors.white, fontSize: 14.5))),
              GestureDetector(
                onTap: () => shell.goTab(1),
                child: Text('View all  ›', style: TextStyle(color: C.accent, fontSize: 13)),
              ),
            ]),
            const SizedBox(height: 4),
            if (open.isEmpty)
              const Padding(padding: EdgeInsets.all(10), child: Text('No pending tasks. Splendid.', style: sub)),
            for (final t in open.take(3))
              InkWell(
                onTap: () {
                  t.done = true;
                  store.saveTasks();
                },
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Row(children: [
                    const Icon(Icons.check_box_outline_blank, color: Colors.white, size: 18),
                    const SizedBox(width: 10),
                    Expanded(child: Text(t.title, style: const TextStyle(color: Colors.white, fontSize: 13.5), overflow: TextOverflow.ellipsis)),
                    if (t.due != null) Text(friendlyDue(t.due!), style: const TextStyle(color: Color(0xFFD0DCE8), fontSize: 12.5)),
                  ]),
                ),
              ),
          ]),
        ),
        const SizedBox(height: 10),
        if (last != null)
          Glass(
            onTap: () => shell.push(const ChatPage()),
            padding: const EdgeInsets.fromLTRB(14, 10, 10, 14),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                NeonIcon(Icons.history_toggle_off, size: 20),
                const SizedBox(width: 8),
                const Expanded(child: Text('Recent Response', style: TextStyle(color: Colors.white, fontSize: 15.5))),
                ValueListenableBuilder(
                  valueListenable: voice.state,
                  builder: (_, s, __) => GestureDetector(
                    onTap: () => s == JState.speaking ? voice.stop() : voice.speak(last.text),
                    child: SizedBox(
                      width: 48,
                      height: 48,
                      child: Container(
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          border: Border.all(color: C.accent, width: 2),
                          boxShadow: [BoxShadow(color: C.accent.withValues(alpha: 0.6), blurRadius: 14)],
                        ),
                        child: Center(
                          child: s == JState.speaking
                              ? Icon(Icons.stop, color: C.accent)
                              : Container(
                                  width: 18,
                                  height: 18,
                                  decoration: BoxDecoration(shape: BoxShape.circle, color: C.accent, boxShadow: [
                                    BoxShadow(color: C.accent, blurRadius: 10),
                                  ]),
                                ),
                        ),
                      ),
                    ),
                  ),
                ),
              ]),
              const SizedBox(height: 6),
              Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                ClipRRect(borderRadius: BorderRadius.circular(8), child: SizedBox(width: 72, height: 64, child: _thumb(last))),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(_title(last.text), style: const TextStyle(color: Colors.white, fontSize: 15), maxLines: 1, overflow: TextOverflow.ellipsis),
                    const SizedBox(height: 3),
                    Text(last.text, style: const TextStyle(color: Color(0xFFD0DCE8), fontSize: 13), maxLines: 3, overflow: TextOverflow.ellipsis),
                    const SizedBox(height: 4),
                    Text(timeAgo(last.time), style: const TextStyle(color: C.text2, fontSize: 12)),
                  ]),
                ),
              ]),
            ]),
          ),
        const SizedBox(height: 8),
      ]),
    );
  }

  String _title(String t) {
    final first = t.split(RegExp(r'(?<=[.!?।])\s|\n')).first.replaceAll(RegExp(r'[*#]'), '');
    return first.length > 42 ? '${first.substring(0, 42)}…' : first;
  }

  Widget _thumb(Msg last) {
    if (last.imageUrl != null) return Image.network(last.imageUrl!, fit: BoxFit.cover);
    final idx = store.chat.indexOf(last);
    final user = idx > 0 ? store.chat[idx - 1] : null;
    if (user?.imagePath != null && File(user!.imagePath!).existsSync()) return Image.file(File(user.imagePath!), fit: BoxFit.cover);
    return Container(
      decoration: const BoxDecoration(gradient: LinearGradient(colors: [Color(0xFF0B3050), Color(0xFF041422)])),
      child: Center(child: NeonIcon(Icons.blur_circular, size: 34)),
    );
  }

  Widget _weatherCard() => Glass(
        padding: const EdgeInsets.all(12),
        onTap: () => Shell.of(context).push(const WeatherPage()),
        child: FutureBuilder<Weather>(
          future: _weather,
          builder: (_, s) {
            if (s.hasError) return const Center(child: Text('Weather unavailable', style: sub));
            if (!s.hasData) return const Center(child: CircularProgressIndicator(strokeWidth: 2));
            final w = s.data!;
            return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Icon(weatherIcon(w.code), color: const Color(0xFFFFD45C), size: 38),
                const SizedBox(width: 8),
                Text('${w.temp.round()}°C', style: const TextStyle(color: Colors.white, fontSize: 25)),
              ]),
              Padding(
                padding: const EdgeInsets.only(left: 46),
                child: Text(w.summary, style: const TextStyle(color: Color(0xFFD0DCE8), fontSize: 12)),
              ),
              const Spacer(),
              Text('H: ${w.high.round()}°    L: ${w.low.round()}°', style: const TextStyle(color: Colors.white, fontSize: 13)),
            ]);
          },
        ),
      );

  Widget _newsCard() => FutureBuilder<List<NewsItem>>(
        future: _news,
        builder: (_, s) {
          final n = s.data?.firstOrNull;
          return Glass(
            padding: const EdgeInsets.all(10),
            onTap: () => Shell.of(context).push(const NewsPage()),
            child: SizedBox(
              height: 92,
              child: Row(children: [
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(children: [
                      Icon(Icons.newspaper, color: C.accent, size: 15),
                      const SizedBox(width: 5),
                      const Text('Latest News', style: TextStyle(color: Colors.white, fontSize: 12.5)),
                    ]),
                    const SizedBox(height: 4),
                    Expanded(
                      child: Text(n?.title ?? (s.hasError ? 'News unavailable' : 'Loading…'),
                          style: const TextStyle(color: Color(0xFFD0DCE8), fontSize: 11.5), maxLines: 3, overflow: TextOverflow.ellipsis),
                    ),
                    if (n?.date != null) Text(timeAgo(n!.date!), style: const TextStyle(color: C.text2, fontSize: 11)),
                  ]),
                ),
                if (n != null) ...[
                  const SizedBox(width: 6),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: Image.network(
                      Web.imageUrl('abstract futuristic neon illustration, ${n.title}', size: 256),
                      width: 46,
                      height: 76,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => const SizedBox(width: 46),
                    ),
                  ),
                ],
              ]),
            ),
          );
        },
      );
}

IconData weatherIcon(int code) => switch (code) {
      0 => Icons.wb_sunny,
      1 || 2 => Icons.wb_cloudy,
      3 || 45 || 48 => Icons.cloud,
      >= 95 => Icons.thunderstorm,
      >= 71 && <= 77 => Icons.ac_unit,
      >= 51 => Icons.grain,
      _ => Icons.cloud,
    };

/// The "Talk to JARVIS…" bar: text, push-to-talk mic, voice-call mode and attachments.
class JarvisInput extends StatefulWidget {
  final String hint;
  const JarvisInput({super.key, this.hint = 'Talk to JARVIS...'});
  @override
  State<JarvisInput> createState() => _JarvisInputState();
}

class _JarvisInputState extends State<JarvisInput> {
  final _text = TextEditingController();
  Attachment? _file;

  Future<void> _send() async {
    final t = _text.text, f = _file;
    if (t.trim().isEmpty && f == null) return;
    _text.clear();
    setState(() => _file = null);
    FocusScope.of(context).unfocus();
    await jarvis.ask(t, files: [if (f != null) f]);
  }

  Future<void> _mic() async {
    if (voice.state.value == JState.listening) return voice.stopListening();
    try {
      final heard = await voice.listen(onPartial: (p) => _text.text = p);
      if (heard.isNotEmpty) await _send();
    } catch (e) {
      if (mounted) toast(context, '$e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final shell = Shell.of(context);
    return Column(mainAxisSize: MainAxisSize.min, children: [
      if (_file != null)
        Padding(
          padding: const EdgeInsets.only(bottom: 6),
          child: Chip(
            backgroundColor: C.cardTop,
            avatar: Icon(_file!.isImage ? Icons.image : Icons.attach_file, color: C.accent, size: 18),
            label: Text(_file!.name, style: const TextStyle(color: Colors.white, fontSize: 12)),
            onDeleted: () => setState(() => _file = null),
          ),
        ),
      Container(
        height: 58,
        padding: const EdgeInsets.symmetric(horizontal: 6),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(30),
          gradient: const LinearGradient(colors: [C.cardTop, C.cardBottom]),
          border: Border.all(color: C.accent.withValues(alpha: 0.6), width: 1.2),
          boxShadow: [BoxShadow(color: C.accent.withValues(alpha: 0.25), blurRadius: 14)],
        ),
        child: Row(children: [
          IconButton(
              tooltip: 'Conversation',
              icon: Icon(Icons.open_with, color: C.accent, shadows: [Shadow(color: C.accent, blurRadius: 8)]),
              onPressed: () => shell.push(const ChatPage())),
          Expanded(
            child: TextField(
              controller: _text,
              style: const TextStyle(color: Colors.white, fontSize: 15.5),
              textInputAction: TextInputAction.send,
              onSubmitted: (_) => _send(),
              decoration: InputDecoration(
                  hintText: widget.hint,
                  hintStyle: const TextStyle(color: Color(0xFFB9C8D6), fontSize: 15.5),
                  border: InputBorder.none),
            ),
          ),
          ListenableBuilder(
            listenable: Listenable.merge([_text, jarvis]),
            builder: (_, __) => _text.text.isNotEmpty || _file != null
                ? IconButton(icon: Icon(Icons.send_rounded, color: C.accent), onPressed: jarvis.busy ? null : _send)
                : IconButton(
                    tooltip: 'Call JARVIS (hands-free)',
                    icon: Icon(Icons.blur_on, color: C.accent),
                    onPressed: () => shell.push(const CallScreen())),
          ),
          ValueListenableBuilder(
            valueListenable: voice.state,
            builder: (_, s, __) => IconButton(
                tooltip: 'Speak',
                icon: Icon(s == JState.listening ? Icons.mic : Icons.mic_none_rounded,
                    color: s == JState.listening ? C.accent : Colors.white, size: 27),
                onPressed: _mic),
          ),
          IconButton(
            tooltip: 'Attach',
            icon: Transform.rotate(angle: 0.6, child: const Icon(Icons.attach_file, color: Colors.white)),
            onPressed: () async {
              final a = await pickAttachment(context);
              if (a != null) setState(() => _file = a);
            },
          ),
        ]),
      ),
    ]);
  }
}

Future<void> openLink(String url) => launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
