import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_contacts/flutter_contacts.dart' show Contact;
import 'package:youtube_explode_dart/youtube_explode_dart.dart' show Video;

import '../services/brain.dart';
import '../services/notify.dart';
import '../services/phone.dart';
import '../services/store.dart';
import '../services/voice.dart';
import '../services/web.dart';
import '../ui/pick.dart';
import '../ui/shell.dart';
import '../ui/theme.dart';
import 'call.dart';
import 'chat.dart';
import 'devices.dart';
import 'home.dart';
import 'settings.dart';

/// Shows an AI answer with copy / speak actions, or a spinner / error.
class AiResult extends StatelessWidget {
  final Future<String>? future;
  final bool mono;
  const AiResult(this.future, {super.key, this.mono = false});
  @override
  Widget build(BuildContext context) {
    if (future == null) return const SizedBox();
    return FutureBuilder<String>(
      future: future,
      builder: (context, s) {
        if (s.connectionState != ConnectionState.done) {
          return const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator(strokeWidth: 2)));
        }
        final text = s.hasError ? '${s.error}' : s.data!;
        return Glass(
          padding: const EdgeInsets.fromLTRB(14, 12, 6, 6),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            SelectableText(text,
                style: TextStyle(
                    color: s.hasError ? C.red : Colors.white, fontSize: mono ? 13 : 14.5, height: 1.4, fontFamily: mono ? 'monospace' : null)),
            Row(mainAxisAlignment: MainAxisAlignment.end, children: [
              IconButton(
                  icon: const Icon(Icons.copy, size: 18, color: C.text2),
                  onPressed: () {
                    Clipboard.setData(ClipboardData(text: text));
                    toast(context, 'Copied');
                  }),
              IconButton(icon: Icon(Icons.volume_up_outlined, size: 20, color: C.accent), onPressed: () => voice.speak(text)),
            ]),
          ]),
        );
      },
    );
  }
}

Widget _inputRow(TextEditingController c, String hint, VoidCallback go, {IconData icon = Icons.arrow_forward, int maxLines = 1}) =>
    TextField(
      controller: c,
      maxLines: maxLines,
      minLines: 1,
      style: const TextStyle(color: Colors.white),
      textInputAction: maxLines == 1 ? TextInputAction.go : TextInputAction.newline,
      onSubmitted: (_) => go(),
      decoration: field(hint, suffix: IconButton(icon: Icon(icon, color: C.accent), onPressed: go)),
    );

// ───────────────────────── Search ─────────────────────────
class SearchPage extends StatefulWidget {
  final bool research;
  const SearchPage({super.key, this.research = false});
  @override
  State<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<SearchPage> {
  final _q = TextEditingController();
  Future<List<SearchResult>>? _results;
  Future<String>? _answer;

  void _go() {
    final q = _q.text.trim();
    if (q.isEmpty) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _results = Web.search(q);
      _answer = _results!.then((r) async {
        var context = r.map((e) => '- ${e.title}: ${e.snippet} (${e.url})').join('\n');
        if (widget.research) {
          final news = await Web.news(q).catchError((_) => <NewsItem>[]);
          context += '\nNews:\n${news.take(6).map((n) => '- ${n.title} (${n.source})').join('\n')}';
        }
        return Brain.ask(
          widget.research
              ? 'Write a well-structured research brief on: "$q". Sections: Overview, Key facts, Latest developments, Opportunities/risks, Sources. Use these web results:\n$context'
              : 'Answer "$q" concisely using these live web results, cite site names:\n$context',
        );
      });
    });
  }

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        PageTitle(widget.research ? 'Research' : 'Search', widget.research ? 'Deep research with live sources.' : 'Real-time web search.'),
        _inputRow(_q, widget.research ? 'What should I research?' : 'Search the web…', _go, icon: Icons.search),
        const SizedBox(height: 12),
        AiResult(_answer),
        const SizedBox(height: 10),
        if (_results != null)
          FutureBuilder<List<SearchResult>>(
            future: _results,
            builder: (_, s) => Column(children: [
              for (final r in s.data ?? <SearchResult>[])
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Glass(
                    onTap: () => openLink(r.url),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(r.title, style: TextStyle(color: C.accent, fontSize: 14.5)),
                      const SizedBox(height: 3),
                      Text(r.snippet, style: const TextStyle(color: Colors.white, fontSize: 12.5), maxLines: 3, overflow: TextOverflow.ellipsis),
                      const SizedBox(height: 3),
                      Text(Uri.tryParse(r.url)?.host ?? r.url, style: const TextStyle(color: C.text2, fontSize: 11)),
                    ]),
                  ),
                ),
            ]),
          ),
      ]);
}

// ───────────────────────── Vision ─────────────────────────
class VisionPage extends StatefulWidget {
  final bool startCamera;
  const VisionPage({super.key, this.startCamera = false});
  @override
  State<VisionPage> createState() => _VisionPageState();
}

class _VisionPageState extends State<VisionPage> {
  Attachment? _file;
  Future<String>? _result;
  final _q = TextEditingController();

  @override
  void initState() {
    super.initState();
    if (widget.startCamera) WidgetsBinding.instance.addPostFrameCallback((_) => _pick('camera'));
  }

  Future<void> _pick(String? kind) async {
    final a = await pickAttachment(context, kind: kind);
    if (a == null) return;
    setState(() => _file = a);
    _analyze();
  }

  void _analyze([String? prompt]) {
    if (_file == null) return;
    final q = prompt ?? (_q.text.trim().isEmpty ? null : _q.text.trim());
    setState(() => _result = Brain.ask(
          q ?? 'Describe what you see and give the key points. If there is text, include it. If it is a receipt/bill, list items and total.',
          files: [_file!],
          system: 'You are J.A.R.V.I.S. analysing visual input for ${store.get<String>('name')}. Be precise, concise and practical.',
        ));
  }

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        const PageTitle('Vision', 'Analyze images, text and videos.'),
        if (!Brain.hasVision)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Glass(
              border: C.red.withValues(alpha: 0.6),
              onTap: () => Shell.of(context).push(const AdvancedSettings()),
              child: const Text(
                  'Vision needs a free Google Gemini API key. Tap here → paste it in Advanced settings (get one free at aistudio.google.com).',
                  style: TextStyle(color: Colors.white)),
            ),
          ),
        Row(children: [
          for (final k in const [('camera', Icons.photo_camera_outlined, 'Camera'), ('gallery', Icons.photo_library_outlined, 'Gallery'), ('video', Icons.videocam_outlined, 'Video'), ('document', Icons.description_outlined, 'File')])
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 3),
                child: Glass(
                  onTap: () => _pick(k.$1),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Column(children: [NeonIcon(k.$2, size: 26), const SizedBox(height: 4), Text(k.$3, style: const TextStyle(color: Colors.white, fontSize: 12))]),
                ),
              ),
            ),
        ]),
        const SizedBox(height: 12),
        if (_file != null) ...[
          if (_file!.isImage && _file!.path != null)
            ClipRRect(borderRadius: BorderRadius.circular(12), child: Image.file(File(_file!.path!), height: 220, fit: BoxFit.cover))
          else
            Glass(child: Row(children: [NeonIcon(Icons.insert_drive_file_outlined), const SizedBox(width: 10), Expanded(child: Text(_file!.name, style: const TextStyle(color: Colors.white)))])),
          const SizedBox(height: 10),
          _inputRow(_q, 'Ask about it (optional)…', () => _analyze()),
          const SizedBox(height: 8),
          Wrap(spacing: 8, runSpacing: 8, children: [
            for (final p in const [
              ('Read text', 'Extract all the text exactly as written.'),
              ('Translate', 'Read the text and translate it into English and Hindi.'),
              ('Explain', 'Explain this in simple words.'),
              ('Solve', 'If this is a question or problem, solve it step by step.'),
            ])
              ActionChip(
                backgroundColor: C.cardTop,
                side: BorderSide(color: C.accent.withValues(alpha: 0.4)),
                label: Text(p.$1, style: const TextStyle(color: Colors.white)),
                onPressed: () => _analyze(p.$2),
              ),
          ]),
          const SizedBox(height: 10),
          AiResult(_result),
        ],
      ]);
}

// ───────────────────────── Calling ─────────────────────────
class CallingPage extends StatefulWidget {
  const CallingPage({super.key});
  @override
  State<CallingPage> createState() => _CallingPageState();
}

class _CallingPageState extends State<CallingPage> {
  final _q = TextEditingController();
  Future<List<Contact>>? _contacts;

  Future<void> _do(Future<String> Function() f) async {
    try {
      final r = await f();
      if (mounted) toast(context, r);
    } catch (e) {
      if (mounted) toast(context, '$e');
    }
  }

  Future<void> _voiceCall() async {
    try {
      final who = await voice.listen(onPartial: (p) => _q.text = p);
      if (who.isNotEmpty) _do(() => PhoneActions.call(who.replaceFirst(RegExp(r'^(call|कॉल करो|फोन करो)\s*', caseSensitive: false), '')));
    } catch (e) {
      if (mounted) toast(context, '$e');
    }
  }

  Future<void> _schedule() async {
    final msg = TextEditingController(text: 'Sir, this is your scheduled call.');
    final now = DateTime.now();
    final d = await showDatePicker(context: context, initialDate: now, firstDate: now, lastDate: DateTime(now.year + 2));
    if (d == null || !mounted) return;
    final t = await showTimePicker(context: context, initialTime: TimeOfDay.fromDateTime(now.add(const Duration(minutes: 5))));
    if (t == null || !mounted) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        backgroundColor: C.cardTop,
        title: const Text('What should JARVIS say?', style: TextStyle(color: Colors.white)),
        content: TextField(controller: msg, maxLines: 3, style: const TextStyle(color: Colors.white), decoration: field('Message')),
        actions: [TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Schedule'))],
      ),
    );
    if (ok != true) return;
    final call = ScheduledCall(message: msg.text, at: DateTime(d.year, d.month, d.day, t.hour, t.minute));
    store.calls.add(call);
    store.saveCalls();
    await Notify.scheduleCall(call.id, call.at, call.message);
    if (mounted) toast(context, 'JARVIS will call you ${friendlyDue(call.at)}');
  }

  @override
  Widget build(BuildContext context) {
    final shell = Shell.of(context);
    return ListenableBuilder(
      listenable: store,
      builder: (context, _) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        const PageTitle('Calling', 'Make & manage calls.'),
        Row(children: [
          Expanded(child: NeonButton('Call JARVIS', icon: Icons.blur_on, onTap: () => shell.push(const CallScreen()))),
          const SizedBox(width: 10),
          Expanded(child: NeonButton('JARVIS, call me at…', icon: Icons.schedule, onTap: _schedule)),
        ]),
        const SizedBox(height: 10),
        TextField(
          controller: _q,
          style: const TextStyle(color: Colors.white),
          onChanged: (_) => setState(() {}),
          onSubmitted: (v) => _do(() => PhoneActions.call(v)),
          decoration: field('Name or number',
              suffix: Row(mainAxisSize: MainAxisSize.min, children: [
                IconButton(icon: const Icon(Icons.mic_none, color: Colors.white), onPressed: _voiceCall),
                IconButton(icon: const Icon(Icons.call, color: C.green), onPressed: () => _do(() => PhoneActions.call(_q.text))),
              ])),
        ),
        if (store.calls.isNotEmpty) ...[
          const SizedBox(height: 14),
          const Text('Scheduled JARVIS calls', style: sub),
          for (final c in store.calls)
            ListTile(
              leading: Icon(Icons.phone_callback, color: C.accent),
              title: Text(c.message, style: const TextStyle(color: Colors.white), maxLines: 1, overflow: TextOverflow.ellipsis),
              subtitle: Text(friendlyDue(c.at), style: sub),
              trailing: IconButton(
                  icon: const Icon(Icons.close, color: C.text2),
                  onPressed: () {
                    store.calls.remove(c);
                    store.saveCalls();
                    Notify.cancel(c.id);
                  }),
            ),
        ],
        const SizedBox(height: 14),
        Row(children: [
          const Expanded(child: Text('Contacts', style: sub)),
          if (_contacts == null) TextButton(onPressed: () => setState(() => _contacts = PhoneActions.contacts()), child: const Text('Load contacts')),
        ]),
        if (_contacts != null)
          FutureBuilder<List<Contact>>(
            future: _contacts,
            builder: (_, s) {
              if (s.hasError) return Text('${s.error}', style: const TextStyle(color: C.red));
              if (!s.hasData) return const Center(child: CircularProgressIndicator(strokeWidth: 2));
              final q = _q.text.toLowerCase();
              final list = s.data!.where((c) => q.isEmpty || (c.displayName ?? '').toLowerCase().contains(q)).take(80);
              return Column(children: [
                for (final c in list)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: CircleAvatar(
                        backgroundColor: C.cardTop,
                        child: Text((c.displayName ?? '?').characters.first, style: TextStyle(color: C.accent))),
                    title: Text(c.displayName ?? '', style: const TextStyle(color: Colors.white)),
                    subtitle: Text(c.phones.first.number, style: sub),
                    trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                      IconButton(
                          icon: const Icon(Icons.message_outlined, color: Colors.white),
                          onPressed: () => _do(() => PhoneActions.sms(c.phones.first.number, ''))),
                      IconButton(
                          icon: const Icon(Icons.call, color: C.green),
                          onPressed: () => _do(() => PhoneActions.call(c.phones.first.number))),
                    ]),
                  ),
              ]);
            },
          ),
      ]),
    );
  }
}

// ───────────────────────── YouTube ─────────────────────────
class YouTubePage extends StatefulWidget {
  const YouTubePage({super.key});
  @override
  State<YouTubePage> createState() => _YouTubePageState();
}

class _YouTubePageState extends State<YouTubePage> {
  final _q = TextEditingController();
  Future<List<Video>>? _videos;
  final Map<String, Future<String>> _summaries = {};

  Future<String> _summarize(String url) async {
    const prompt = 'Summarise this YouTube video: a one-line gist, then 5-8 key points, then a one-line takeaway.';
    if (Brain.hasVision) {
      try {
        return await Brain.ask(prompt, youtubeUrl: url);
      } catch (_) {}
    }
    final v = await Web.youtubeText(url);
    return Brain.ask('$prompt\nTitle: ${v.title}\nTranscript/description:\n${v.text}');
  }

  void _go() {
    final q = _q.text.trim();
    if (q.isEmpty) return;
    FocusScope.of(context).unfocus();
    // A pasted link is summarised directly.
    if (q.contains('youtu')) {
      setState(() => _summaries[q] = _summarize(q));
      return;
    }
    setState(() => _videos = Web.youtubeSearch(q));
  }

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        const PageTitle('YouTube', 'Play, search & summarize videos.'),
        _inputRow(_q, 'Search YouTube or paste a link…', _go, icon: Icons.search),
        const SizedBox(height: 10),
        for (final e in _summaries.entries.where((e) => e.key.contains('youtu'))) AiResult(e.value),
        if (_videos != null)
          FutureBuilder<List<Video>>(
            future: _videos,
            builder: (_, s) {
              if (s.hasError) return Text('YouTube search failed: ${s.error}', style: const TextStyle(color: C.red));
              if (!s.hasData) return const Center(child: CircularProgressIndicator(strokeWidth: 2));
              return Column(children: [
                for (final v in s.data!)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Glass(
                      padding: const EdgeInsets.all(8),
                      child: Column(children: [
                        Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: Image.network(v.thumbnails.mediumResUrl, width: 128, height: 72, fit: BoxFit.cover,
                                errorBuilder: (_, __, ___) => const SizedBox(width: 128, height: 72)),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                              Text(v.title, style: const TextStyle(color: Colors.white, fontSize: 13.5), maxLines: 2, overflow: TextOverflow.ellipsis),
                              const SizedBox(height: 3),
                              Text('${v.author}${v.duration != null ? ' • ${v.duration.toString().split('.').first}' : ''}', style: sub),
                            ]),
                          ),
                        ]),
                        Row(mainAxisAlignment: MainAxisAlignment.end, children: [
                          TextButton.icon(
                              onPressed: () => openLink('https://www.youtube.com/watch?v=${v.id.value}'),
                              icon: const Icon(Icons.play_arrow, color: C.youtube),
                              label: const Text('Play', style: TextStyle(color: Colors.white))),
                          TextButton.icon(
                              onPressed: () => setState(() => _summaries[v.id.value] = _summarize('https://www.youtube.com/watch?v=${v.id.value}')),
                              icon: Icon(Icons.auto_awesome, color: C.accent),
                              label: const Text('Summarize', style: TextStyle(color: Colors.white))),
                        ]),
                        if (_summaries[v.id.value] != null) AiResult(_summaries[v.id.value]),
                      ]),
                    ),
                  ),
              ]);
            },
          ),
      ]);
}

// ───────────────────────── Automation ─────────────────────────
class AutomationPage extends StatelessWidget {
  const AutomationPage({super.key});

  static final _templates = [
    Routine(name: 'Morning Briefing', time: '07:00', steps: [
      'Tell me today\'s weather in one line.',
      'Give me the top 5 news headlines briefly.',
      'Summarise my pending tasks and what to focus on first.',
    ]),
    Routine(name: 'Evening Wrap-up', time: '21:00', steps: [
      'Summarise my pending tasks.',
      'Suggest my top 3 priorities for tomorrow.',
    ]),
  ];

  Future<void> _edit(BuildContext context, [Routine? r]) async {
    final name = TextEditingController(text: r?.name);
    final steps = TextEditingController(text: r?.steps.join('\n'));
    var time = r?.time ?? '08:00';
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => StatefulBuilder(
        builder: (c, set) => AlertDialog(
          backgroundColor: C.cardTop,
          title: Text(r == null ? 'New routine' : 'Edit routine', style: const TextStyle(color: Colors.white)),
          content: SingleChildScrollView(
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              TextField(controller: name, style: const TextStyle(color: Colors.white), decoration: field('Name')),
              const SizedBox(height: 10),
              NeonButton('Every day at $time', icon: Icons.schedule, onTap: () async {
                final p = time.split(':');
                final t = await showTimePicker(context: c, initialTime: TimeOfDay(hour: int.parse(p[0]), minute: int.parse(p[1])));
                if (t != null) set(() => time = '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}');
              }),
              const SizedBox(height: 10),
              TextField(
                  controller: steps,
                  maxLines: 6,
                  style: const TextStyle(color: Colors.white),
                  decoration: field('Steps, one per line — anything you would ask JARVIS\n(e.g. "Ask the laptop to back up my Documents")')),
            ]),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
            TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Save')),
          ],
        ),
      ),
    );
    final list = steps.text.split('\n').map((s) => s.trim()).where((s) => s.isNotEmpty).toList();
    if (ok != true || name.text.trim().isEmpty || list.isEmpty) return;
    if (r == null) {
      store.routines.add(Routine(name: name.text.trim(), time: time, steps: list));
    } else {
      r
        ..name = name.text.trim()
        ..time = time
        ..steps = list;
    }
    store.saveRoutines();
    await Notify.syncAll();
  }

  @override
  Widget build(BuildContext context) {
    final shell = Shell.of(context);
    return ListenableBuilder(
      listenable: store,
      builder: (context, _) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        PageTitle('Automation', 'JARVIS calls you daily and runs these.', trailing: CircleBtn(Icons.add, () => _edit(context), size: 46)),
        if (store.routines.isEmpty) ...[
          const Text('Start from a template:', style: sub),
          const SizedBox(height: 8),
          for (final t in _templates)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: NeonButton('${t.name} • ${t.time}', icon: Icons.add, onTap: () async {
                store.routines.add(Routine(name: t.name, time: t.time, steps: [...t.steps]));
                store.saveRoutines();
                await Notify.syncAll();
              }),
            ),
        ],
        for (final r in store.routines)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Glass(
              onTap: () => _edit(context, r),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  NeonIcon(Icons.settings_suggest_outlined, size: 26),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(r.name, style: const TextStyle(color: Colors.white, fontSize: 15.5)),
                      Text('Every day at ${r.time} • ${r.steps.length} steps', style: sub),
                    ]),
                  ),
                  Switch(
                      value: r.enabled,
                      activeThumbColor: C.accent,
                      onChanged: (v) {
                        r.enabled = v;
                        store.saveRoutines();
                        Notify.syncAll();
                      }),
                ]),
                const SizedBox(height: 4),
                for (final s in r.steps) Text('•  $s', style: const TextStyle(color: Color(0xFFD0DCE8), fontSize: 12.5)),
                Row(mainAxisAlignment: MainAxisAlignment.end, children: [
                  TextButton.icon(
                      onPressed: () {
                        store.routines.remove(r);
                        store.saveRoutines();
                        Notify.cancel(r.id);
                      },
                      icon: const Icon(Icons.delete_outline, color: C.text2),
                      label: const Text('Delete', style: TextStyle(color: C.text2))),
                  TextButton.icon(
                      onPressed: () => shell.push(CallScreen(incoming: CallRequest('Running your routine: ${r.name}', routineId: r.id))),
                      icon: Icon(Icons.play_arrow, color: C.accent),
                      label: const Text('Run now', style: TextStyle(color: Colors.white))),
                ]),
              ]),
            ),
          ),
      ]),
    );
  }
}

// ───────────────────────── Notes ─────────────────────────
class NotesPage extends StatefulWidget {
  const NotesPage({super.key});
  @override
  State<NotesPage> createState() => _NotesPageState();
}

class _NotesPageState extends State<NotesPage> {
  String _tag = 'All';
  bool _organizing = false;

  /// Asks the brain to file every note under a short category.
  Future<void> _organizeAll() async {
    if (store.notes.isEmpty) return;
    setState(() => _organizing = true);
    try {
      final list = store.notes.map((n) => {'id': n.id, 'title': n.title, 'text': n.content.length > 300 ? n.content.substring(0, 300) : n.content}).toList();
      final r = await Brain.ask('Give each note a short category (1-2 words, e.g. Business, Ideas, Personal, Shopping, Work). '
          'Reply ONLY with JSON mapping id to category.\n${jsonEncode(list)}');
      final map = jsonDecode(RegExp(r'\{[\s\S]*\}').firstMatch(r)!.group(0)!) as Map;
      for (final n in store.notes) {
        if (map[n.id] != null) n.tag = '${map[n.id]}';
      }
      store.saveNotes();
    } catch (e) {
      if (mounted) toast(context, 'Could not organise: $e');
    }
    if (mounted) setState(() => _organizing = false);
  }

  @override
  Widget build(BuildContext context) {
    final shell = Shell.of(context);
    return ListenableBuilder(
      listenable: store,
      builder: (context, _) {
        final tags = {'All', ...store.notes.map((n) => n.tag)};
        final notes = store.notes.where((n) => _tag == 'All' || n.tag == _tag).toList()..sort((a, b) => b.updated.compareTo(a.updated));
        return ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
          PageTitle('Notes', 'Write, summarize and organize.', trailing: CircleBtn(Icons.add, () => shell.push(const NoteEditor()), size: 46)),
          Row(children: [
            Expanded(
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(children: [
                  for (final t in tags)
                    Padding(
                      padding: const EdgeInsets.only(right: 6),
                      child: ChoiceChip(
                        selected: _tag == t,
                        selectedColor: C.accent.withValues(alpha: 0.3),
                        backgroundColor: C.cardTop,
                        label: Text(t, style: const TextStyle(color: Colors.white)),
                        onSelected: (_) => setState(() => _tag = t),
                      ),
                    ),
                ]),
              ),
            ),
            _organizing
                ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2))
                : IconButton(tooltip: 'Auto-organize', icon: Icon(Icons.auto_awesome, color: C.accent), onPressed: _organizeAll),
          ]),
          const SizedBox(height: 8),
          if (notes.isEmpty) const Padding(padding: EdgeInsets.all(20), child: Center(child: Text('No notes yet.', style: sub))),
          for (final n in notes)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Glass(
                onTap: () => shell.push(NoteEditor(note: n)),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(children: [
                    Expanded(child: Text(n.title, style: const TextStyle(color: Colors.white, fontSize: 15))),
                    Text(n.tag, style: TextStyle(color: C.accent, fontSize: 11)),
                  ]),
                  const SizedBox(height: 4),
                  Text(n.content, style: const TextStyle(color: Color(0xFFD0DCE8), fontSize: 12.5), maxLines: 3, overflow: TextOverflow.ellipsis),
                  const SizedBox(height: 4),
                  Text(timeAgo(n.updated), style: const TextStyle(color: C.text2, fontSize: 11)),
                ]),
              ),
            ),
        ]);
      },
    );
  }
}

class NoteEditor extends StatefulWidget {
  final Note? note;
  const NoteEditor({super.key, this.note});
  @override
  State<NoteEditor> createState() => _NoteEditorState();
}

class _NoteEditorState extends State<NoteEditor> {
  late final _title = TextEditingController(text: widget.note?.title);
  late final _body = TextEditingController(text: widget.note?.content);
  Future<String>? _ai;

  void _save({bool leave = true}) {
    if (_title.text.trim().isEmpty && _body.text.trim().isEmpty) return;
    final n = widget.note ?? Note(title: '', content: '');
    n
      ..title = _title.text.trim().isEmpty ? _body.text.trim().split('\n').first : _title.text.trim()
      ..content = _body.text
      ..updated = DateTime.now();
    if (widget.note == null && !store.notes.contains(n)) store.notes.add(n);
    store.saveNotes();
    if (leave) Shell.of(context).pop();
  }

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        PageTitle(widget.note == null ? 'New note' : 'Edit note', widget.note?.tag ?? 'Notes',
            trailing: Row(mainAxisSize: MainAxisSize.min, children: [
              if (widget.note != null)
                IconButton(
                    icon: const Icon(Icons.delete_outline, color: C.text2),
                    onPressed: () {
                      store.notes.remove(widget.note);
                      store.saveNotes();
                      Shell.of(context).pop();
                    }),
              CircleBtn(Icons.check, _save, size: 44),
            ])),
        TextField(controller: _title, style: h1(18), decoration: field('Title')),
        const SizedBox(height: 10),
        TextField(controller: _body, minLines: 10, maxLines: null, style: const TextStyle(color: Colors.white, height: 1.4), decoration: field('Write…')),
        const SizedBox(height: 10),
        Row(children: [
          Expanded(
            child: NeonButton('Summarize', icon: Icons.short_text, onTap: () {
              setState(() => _ai = Brain.ask('Summarise this note in 3-5 bullet points:\n${_body.text}'));
            }),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: NeonButton('Organize', icon: Icons.auto_fix_high, onTap: () async {
              setState(() => _ai = Brain.ask(
                  'Rewrite this note neatly organised with headings and bullet points. Keep every fact. Plain text, no markdown symbols except "-" bullets:\n${_body.text}'));
              final text = await _ai!;
              if (mounted && !text.startsWith('My cloud brain')) {
                _body.text = text;
                _save(leave: false);
              }
            }),
          ),
        ]),
        const SizedBox(height: 10),
        AiResult(_ai),
      ]);
}

// ───────────────────────── Code ─────────────────────────
class CodePage extends StatefulWidget {
  const CodePage({super.key});
  @override
  State<CodePage> createState() => _CodePageState();
}

class _CodePageState extends State<CodePage> {
  String _mode = 'Generate';
  final _q = TextEditingController();
  Future<String>? _out;

  void _go() {
    if (_q.text.trim().isEmpty) return;
    final task = switch (_mode) {
      'Debug' => 'Find the bugs in this code, explain each briefly, then give the complete fixed code:',
      'Explain' => 'Explain what this code does, step by step, simply:',
      _ => 'Write complete, working, well-commented code for this request. Say which language and how to run it:',
    };
    setState(() => _out = Brain.ask('$task\n${_q.text}',
        system: 'You are J.A.R.V.I.S., an expert senior software engineer. Give correct, minimal, runnable code.'));
  }

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        const PageTitle('Code', 'Generate & debug code.'),
        SegmentedButton<String>(
          segments: const [
            ButtonSegment(value: 'Generate', label: Text('Generate')),
            ButtonSegment(value: 'Debug', label: Text('Debug')),
            ButtonSegment(value: 'Explain', label: Text('Explain')),
          ],
          selected: {_mode},
          onSelectionChanged: (s) => setState(() => _mode = s.first),
        ),
        const SizedBox(height: 10),
        _inputRow(_q, _mode == 'Generate' ? 'Describe what to build…' : 'Paste code (and the error)…', _go, icon: Icons.play_arrow, maxLines: 10),
        const SizedBox(height: 10),
        AiResult(_out, mono: true),
      ]);
}

// ───────────────────────── Create ─────────────────────────
class CreatePage extends StatefulWidget {
  const CreatePage({super.key});
  @override
  State<CreatePage> createState() => _CreatePageState();
}

class _CreatePageState extends State<CreatePage> {
  String _mode = 'Image', _kind = 'Instagram caption';
  final _q = TextEditingController();
  String? _image;
  Future<String>? _text;

  void _go() {
    final q = _q.text.trim();
    if (q.isEmpty) return;
    FocusScope.of(context).unfocus();
    setState(() {
      if (_mode == 'Image') {
        _image = Web.imageUrl(q, seed: DateTime.now().millisecondsSinceEpoch % 100000);
      } else if (_mode == 'Ideas') {
        _text = Brain.ask('Give me 10 creative, practical ideas for: $q. Number them, one line each.');
      } else {
        _text = Brain.ask('Write a $_kind about: $q. Make it engaging and ready to use.');
      }
    });
  }

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        const PageTitle('Create', 'Images, ideas, content.'),
        SegmentedButton<String>(
          segments: const [
            ButtonSegment(value: 'Image', label: Text('Image'), icon: Icon(Icons.image_outlined)),
            ButtonSegment(value: 'Ideas', label: Text('Ideas'), icon: Icon(Icons.lightbulb_outline)),
            ButtonSegment(value: 'Content', label: Text('Content'), icon: Icon(Icons.edit_note)),
          ],
          selected: {_mode},
          onSelectionChanged: (s) => setState(() => _mode = s.first),
        ),
        const SizedBox(height: 10),
        if (_mode == 'Content')
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: DropdownButtonFormField<String>(
              initialValue: _kind,
              dropdownColor: C.cardTop,
              style: const TextStyle(color: Colors.white),
              decoration: field('Type'),
              items: [
                for (final k in const ['Instagram caption', 'Blog post', 'Professional email', 'Ad copy', 'YouTube script', 'WhatsApp business message'])
                  DropdownMenuItem(value: k, child: Text(k)),
              ],
              onChanged: (v) => _kind = v!,
            ),
          ),
        _inputRow(_q, _mode == 'Image' ? 'Describe the image…' : 'Topic…', _go, icon: Icons.auto_awesome, maxLines: 3),
        const SizedBox(height: 12),
        if (_mode == 'Image' && _image != null) ...[
          ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: Image.network(_image!,
                key: ValueKey(_image),
                loadingBuilder: (_, w, p) => p == null
                    ? w
                    : const SizedBox(height: 300, child: Center(child: Text('Creating your image…', style: sub))),
                errorBuilder: (_, __, ___) => const Text('Image service busy, try again.', style: TextStyle(color: C.red))),
          ),
          const SizedBox(height: 8),
          Row(children: [
            Expanded(child: NeonButton('New variation', icon: Icons.refresh, onTap: _go)),
            const SizedBox(width: 8),
            Expanded(child: NeonButton('Open / Save', icon: Icons.download, onTap: () => openLink(_image!))),
          ]),
        ],
        if (_mode != 'Image') AiResult(_text),
      ]);
}

// ───────────────────────── More tools ─────────────────────────
class MoreToolsPage extends StatelessWidget {
  const MoreToolsPage({super.key});
  @override
  Widget build(BuildContext context) {
    final shell = Shell.of(context);
    final items = <(IconData, String, String, VoidCallback)>[
      (Icons.wb_cloudy_outlined, 'Weather', 'Live forecast', () => shell.push(const WeatherPage())),
      (Icons.newspaper, 'News', 'Headlines & briefings', () => shell.push(const NewsPage())),
      (Icons.translate, 'Translate', 'Any language', () => shell.push(const TranslatePage())),
      (Icons.manage_search, 'Research', 'Deep research reports', () => shell.push(const SearchPage(research: true))),
      (Icons.devices_other, 'Devices', 'Control laptop & more', () => shell.push(const DevicesScreen())),
      (Icons.blur_on, 'Call JARVIS', 'Hands-free voice', () => shell.push(const CallScreen())),
      (Icons.forum_outlined, 'Conversation', 'Full chat history', () => shell.push(const ChatPage())),
      (Icons.psychology_outlined, 'Memory', 'What JARVIS knows', () => shell.goTab(3)),
    ];
    return ListView(padding: const EdgeInsets.fromLTRB(12, 0, 12, 12), children: [
      const PageTitle('More Tools', 'Explore all tools.'),
      GridView.count(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        crossAxisCount: 2,
        mainAxisSpacing: 10,
        crossAxisSpacing: 10,
        childAspectRatio: 1.6,
        children: [
          for (final t in items)
            Glass(
              onTap: t.$4,
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                NeonIcon(t.$1, size: 30),
                const Spacer(),
                Text(t.$2, style: const TextStyle(color: Colors.white, fontSize: 15)),
                Text(t.$3, style: const TextStyle(color: Color(0xFFC8D6E3), fontSize: 11.5)),
              ]),
            ),
        ],
      ),
    ]);
  }
}

class WeatherPage extends StatefulWidget {
  const WeatherPage({super.key});
  @override
  State<WeatherPage> createState() => _WeatherPageState();
}

class _WeatherPageState extends State<WeatherPage> {
  late final _city = TextEditingController(text: store.get<String>('city'));
  late Future<Weather> _w = Web.weather();

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        const PageTitle('Weather', 'Live conditions where you are.'),
        _inputRow(_city, 'City (blank = auto-detect)', () {
          store.set('city', _city.text.trim());
          setState(() => _w = Web.weather());
        }),
        const SizedBox(height: 14),
        FutureBuilder<Weather>(
          future: _w,
          builder: (_, s) {
            if (s.hasError) return Text('${s.error}', style: const TextStyle(color: C.red));
            if (!s.hasData) return const Center(child: CircularProgressIndicator(strokeWidth: 2));
            final w = s.data!;
            return Glass(
              onTap: () => voice.speak('It is currently ${w.temp.round()} degrees and ${w.summary.toLowerCase()} in ${w.city}, sir. '
                  'Expect a high of ${w.high.round()} and a low of ${w.low.round()}.'),
              child: Column(children: [
                Text(w.city, style: h1(20)),
                const SizedBox(height: 8),
                Icon(weatherIcon(w.code), size: 72, color: const Color(0xFFFFD45C)),
                Text('${w.temp.round()}°C', style: h1(44)),
                Text(w.summary, style: sub),
                const SizedBox(height: 6),
                Text('High ${w.high.round()}°   •   Low ${w.low.round()}°', style: const TextStyle(color: Colors.white)),
                const SizedBox(height: 6),
                const Text('Tap to hear it', style: sub),
              ]),
            );
          },
        ),
      ]);
}

class NewsPage extends StatefulWidget {
  const NewsPage({super.key});
  @override
  State<NewsPage> createState() => _NewsPageState();
}

class _NewsPageState extends State<NewsPage> {
  String _topic = '';
  late Future<List<NewsItem>> _n = Web.news();
  Future<String>? _brief;

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        PageTitle('News', 'Latest headlines.',
            trailing: NeonButton('Brief me', icon: Icons.record_voice_over_outlined, onTap: () async {
              final list = await _n;
              setState(() => _brief = Brain.ask(
                  'As JARVIS, give a spoken-style 5-headline news briefing (2 short sentences each) from:\n${list.take(10).map((e) => '- ${e.title} (${e.source})').join('\n')}'));
              voice.speak(await _brief!);
            })),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(children: [
            for (final t in const ['', 'India', 'Technology', 'Business', 'Sports', 'World', 'AI'])
              Padding(
                padding: const EdgeInsets.only(right: 6),
                child: ChoiceChip(
                  selected: _topic == t,
                  selectedColor: C.accent.withValues(alpha: 0.3),
                  backgroundColor: C.cardTop,
                  label: Text(t.isEmpty ? 'Top' : t, style: const TextStyle(color: Colors.white)),
                  onSelected: (_) => setState(() {
                    _topic = t;
                    _n = Web.news(t);
                  }),
                ),
              ),
          ]),
        ),
        const SizedBox(height: 10),
        AiResult(_brief),
        FutureBuilder<List<NewsItem>>(
          future: _n,
          builder: (_, s) {
            if (s.hasError) return Text('${s.error}', style: const TextStyle(color: C.red));
            if (!s.hasData) return const Center(child: CircularProgressIndicator(strokeWidth: 2));
            return Column(children: [
              for (final n in s.data!)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Glass(
                    onTap: () => openLink(n.link),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(n.title, style: const TextStyle(color: Colors.white, fontSize: 14)),
                      const SizedBox(height: 4),
                      Text('${n.source}${n.date != null ? ' • ${timeAgo(n.date!)}' : ''}', style: sub),
                    ]),
                  ),
                ),
            ]);
          },
        ),
      ]);
}

class TranslatePage extends StatefulWidget {
  const TranslatePage({super.key});
  @override
  State<TranslatePage> createState() => _TranslatePageState();
}

class _TranslatePageState extends State<TranslatePage> {
  final _q = TextEditingController();
  String _to = 'Hindi';
  Future<String>? _out;

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        const PageTitle('Translate', 'Any language, instantly.'),
        DropdownButtonFormField<String>(
          initialValue: _to,
          dropdownColor: C.cardTop,
          style: const TextStyle(color: Colors.white),
          decoration: field('Translate to'),
          items: [
            for (final l in const ['Hindi', 'English', 'Hinglish', 'Marathi', 'Gujarati', 'Punjabi', 'Bengali', 'Tamil', 'Urdu', 'Spanish', 'French', 'German', 'Arabic', 'Japanese', 'Chinese'])
              DropdownMenuItem(value: l, child: Text(l)),
          ],
          onChanged: (v) => _to = v!,
        ),
        const SizedBox(height: 10),
        _inputRow(_q, 'Text to translate…', () {
          if (_q.text.trim().isEmpty) return;
          setState(() => _out = Brain.ask('Translate into $_to. Reply with only the translation:\n${_q.text}'));
        }, icon: Icons.translate, maxLines: 5),
        const SizedBox(height: 10),
        AiResult(_out),
      ]);
}
