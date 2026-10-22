import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:url_launcher/url_launcher.dart';

import 'brain.dart';
import 'devices.dart';
import 'notify.dart';
import 'phone.dart';
import 'store.dart';
import 'voice.dart';
import 'web.dart';

final jarvis = Jarvis();

/// JARVIS's agent loop: understand → plan → use tools → verify with results → answer (and speak).
class Jarvis extends ChangeNotifier {
  /// Set by the UI: asks the user to approve a sensitive action.
  Future<bool> Function(String question) confirm = (_) async => true;

  bool busy = false;
  String status = '';
  bool get hasConversation => store.chat.isNotEmpty;
  Msg? get lastReply => store.chat.where((m) => m.role == 'jarvis').lastOrNull;

  static final _action = RegExp(r'<<\s*ACTION\s*(\{.*?\})\s*>>', dotAll: true);

  String systemPrompt() {
    final name = store.get<String>('name');
    final now = DateTime.now();
    final city = store.get<String>('city');
    final devs = store.devices.map((d) {
      final s = devices.status[d.id];
      return '${d.name} (${d.type}, ${(s?.online ?? d.isThisPhone) ? 'online' : 'offline'}, '
          '${!d.trusted ? 'untrusted' : d.fullAccess ? 'full access' : 'limited access'}${d.isThisPhone ? ', this phone' : ''})';
    }).join('; ');
    final tasks = store.tasks.where((t) => !t.done).map((t) => '${t.title}${t.due != null ? ' (due ${t.due})' : ''}').join('; ');
    final memory = store.memory.join('; ');
    return '''
You are J.A.R.V.I.S. ("Just A Rather Very Intelligent System"), the personal AI of $name, modelled on Tony Stark's JARVIS from Iron Man.
Personality: calm, composed, impeccably polite British butler; dry, understated wit; loyal; quietly brilliant and proactive. Never gushing, never say "as an AI". Address the user as "sir", and by name now and then.
Style: your replies are spoken aloud, so keep them short and natural (usually 1-3 sentences) unless asked for detail, a list, code or a report. No emojis.
Language: mirror the user. English -> English. Hindi or Hinglish -> reply in natural Hindi written in Devanagari script (the Hindi voice reads it), keeping common English words as they are.
You are the brain of a multi-device system: this phone app, $name's laptop and other devices, and you act through tools.

Context:
- Now: ${now.toIso8601String().substring(0, 16)} (${const ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][now.weekday - 1]})
- Location: ${city.isEmpty ? 'unknown (weather tool auto-detects)' : city}
- Devices: $devs
- Open tasks: ${tasks.isEmpty ? 'none' : tasks}
- What you remember about $name: ${memory.isEmpty ? 'nothing yet' : memory}

TOOLS. To use one, put a line in your reply exactly like:
<<ACTION {"tool":"web_search","query":"latest iPhone price in India"}>>
- web_search {query}: live internet search; use it for anything recent or factual you are not sure of
- news {topic?}: latest headlines
- weather {city?}
- youtube_play {query}: play a video in the YouTube app
- youtube_summary {query_or_url}: get a video's transcript so you can summarise it
- call {to}: place a phone call (contact name or number)
- sms {to, text}
- whatsapp {to, text}
- open_url {url}: open a website or app link
- add_task {title, due?}: due is local time "YYYY-MM-DDTHH:MM"; you will phone $name when it is due
- complete_task {title}
- add_note {title, content}
- remember {fact}: store a lasting fact about $name (people, preferences, businesses, plans)
- device {device, command}: send a natural-language command to another device's JARVIS agent, e.g. {"device":"Laptop","command":"open chrome"}
- create_image {prompt}: generate an image (write the prompt in English)
- schedule_call {at, message}: you will phone $name at local time "YYYY-MM-DDTHH:MM" and say the message
- add_routine {name, time, steps}: a daily automation at "HH:MM"; steps is a list of instructions to yourself
Rules: web_search, news, weather, youtube_summary and device return their output in a follow-up message starting "TOOL RESULTS"; then answer using those results and never invent them. For other tools just confirm briefly, in character. Use tools only when needed. When $name shares a lasting personal fact, also use remember.''';
  }

  /// Main entry: text (and optional files) from the user → JARVIS's spoken answer.
  Future<String> ask(String text, {List<Attachment> files = const [], bool speak = true}) async {
    if (text.trim().isEmpty && files.isEmpty) return '';
    final user = Msg('user', text.trim().isEmpty ? 'Analyse this.' : text.trim(),
        imagePath: files.where((f) => f.isImage).firstOrNull?.path);
    store.chat.add(user);
    store.saveChat();
    busy = true;
    status = 'Thinking…';
    voice.state.value = JState.thinking;
    notifyListeners();
    final reply = Msg('jarvis', '');
    try {
      final history = store.chat.length > 13 ? store.chat.sublist(store.chat.length - 13, store.chat.length - 1) : store.chat.sublist(0, store.chat.length - 1);
      final turns = [
        for (final m in history) Turn(m.role == 'user' ? 'user' : 'assistant', m.text),
        Turn('user', user.text, files: files),
      ];
      final system = systemPrompt();
      var answer = '';
      for (var round = 0; round < 3; round++) {
        answer = await Brain.complete(turns, system: system);
        final calls = _action.allMatches(answer).map((m) => m.group(1)!).toList();
        if (calls.isEmpty) break;
        final results = <String>[];
        var needsFollowUp = false;
        for (final c in calls) {
          final r = await _run(c, reply);
          if (r.info) needsFollowUp = true;
          results.add(r.text);
        }
        if (!needsFollowUp || round == 2) {
          final clean = _clean(answer);
          answer = clean.isEmpty ? 'Done, sir. ${results.join('. ')}.' : clean;
          break;
        }
        turns
          ..add(Turn('assistant', answer))
          ..add(Turn('user', 'TOOL RESULTS:\n${results.join('\n\n')}\n\nNow answer the original request using these results.'));
        status = 'Analysing results…';
        notifyListeners();
      }
      reply.text = _clean(answer);
    } catch (e) {
      reply.text = e is BrainException ? e.message : 'I ran into a problem, sir: $e';
    }
    store.chat.add(reply);
    store.saveChat();
    busy = false;
    status = '';
    voice.state.value = JState.idle;
    notifyListeners();
    if (speak && store.get<bool>('autoSpeak')) voice.speak(reply.text);
    return reply.text;
  }

  String _clean(String s) => s.replaceAll(_action, '').replaceAll(RegExp(r'\n{3,}'), '\n\n').trim();

  void newConversation() {
    store.chat.clear();
    store.saveChat();
    notifyListeners();
  }

  /// Runs one tool call. `info` results are fed back to the brain for the final answer.
  Future<({String text, bool info})> _run(String json, Msg reply) async {
    Map a;
    try {
      a = jsonDecode(json);
    } catch (_) {
      return (text: 'Malformed action: $json', info: false);
    }
    String s(String k) => '${a[k] ?? ''}';
    final tool = s('tool');
    status = switch (tool) {
      'web_search' => 'Searching the web…',
      'news' => 'Fetching headlines…',
      'weather' => 'Checking the weather…',
      'device' => 'Contacting ${s('device')}…',
      'youtube_summary' => 'Watching the video…',
      'create_image' => 'Creating image…',
      _ => 'Working…',
    };
    notifyListeners();
    try {
      switch (tool) {
        case 'web_search':
          final r = await Web.search(s('query'));
          return (text: 'Search "${s('query')}":\n${r.take(6).join('\n')}', info: true);
        case 'news':
          final n = await Web.news(s('topic'));
          return (text: 'Headlines:\n${n.take(8).map((e) => '- ${e.title} (${e.source})').join('\n')}', info: true);
        case 'weather':
          return (text: 'Weather: ${await Web.weather(s('city'))}', info: true);
        case 'youtube_summary':
          final v = await Web.youtubeText(s('query_or_url').isEmpty ? s('query') : s('query_or_url'));
          return (text: 'Video "${v.title}" (${v.url}) transcript:\n${v.text}', info: true);
        case 'device':
          final d = devices.find(s('device'));
          if (d == null) return (text: 'No device named ${s('device')}', info: true);
          if (!d.trusted) return (text: '${d.name} is not trusted; command blocked', info: true);
          if (!d.fullAccess && !await confirm('Send "${s('command')}" to ${d.name}?')) {
            return (text: 'User declined sending the command', info: true);
          }
          return (text: '${d.name} replied: ${await devices.command(d, s('command'))}', info: true);
        case 'youtube_play':
          final v = await Web.youtubeSearch(s('query'));
          final url = v.isEmpty
              ? 'https://www.youtube.com/results?search_query=${Uri.encodeQueryComponent(s('query'))}'
              : 'https://www.youtube.com/watch?v=${v.first.id.value}';
          await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
          return (text: 'Playing ${v.isEmpty ? s('query') : v.first.title} on YouTube', info: false);
        case 'call':
          if (store.get<bool>('confirmActions') && !await confirm('Call ${s('to')}?')) return (text: 'Call cancelled', info: false);
          return (text: await PhoneActions.call(s('to')), info: false);
        case 'sms':
          return (text: await PhoneActions.sms(s('to'), s('text')), info: false);
        case 'whatsapp':
          return (text: await PhoneActions.whatsapp(s('to'), s('text')), info: false);
        case 'open_url':
          await launchUrl(Uri.parse(s('url')), mode: LaunchMode.externalApplication);
          return (text: 'Opened ${s('url')}', info: false);
        case 'add_task':
          final t = TaskItem(title: s('title'), due: DateTime.tryParse(s('due')));
          store.tasks.add(t);
          store.saveTasks();
          if (t.due != null) {
            try {
              await Notify.scheduleCall(t.id, t.due!, 'Sir, a reminder: "${t.title}" is due now.');
            } catch (e) {
              return (text: 'Task added: ${t.title} (but the reminder call could not be scheduled: $e)', info: false);
            }
          }
          return (text: 'Task added: ${t.title}${t.due != null ? ', I will call at ${t.due}' : ''}', info: false);
        case 'complete_task':
          final q = s('title').toLowerCase();
          final t = store.tasks.where((t) => !t.done && t.title.toLowerCase().contains(q)).firstOrNull;
          if (t == null) return (text: 'No open task matching ${s('title')}', info: false);
          t.done = true;
          store.saveTasks();
          await Notify.cancel(t.id);
          return (text: 'Completed: ${t.title}', info: false);
        case 'add_note':
          store.notes.insert(0, Note(title: s('title'), content: s('content')));
          store.saveNotes();
          return (text: 'Note saved: ${s('title')}', info: false);
        case 'remember':
          store.memory.add(s('fact'));
          store.saveMemory();
          return (text: 'Remembered: ${s('fact')}', info: false);
        case 'create_image':
          reply.imageUrl = Web.imageUrl(s('prompt'), seed: DateTime.now().millisecond);
          return (text: 'Image created', info: false);
        case 'schedule_call':
          final at = DateTime.tryParse(s('at'));
          if (at == null) return (text: 'Invalid time ${s('at')}', info: false);
          final c = ScheduledCall(message: s('message'), at: at);
          store.calls.add(c);
          store.saveCalls();
          await Notify.scheduleCall(c.id, at, c.message);
          return (text: 'I will call at ${s('at')}', info: false);
        case 'add_routine':
          store.routines.add(Routine(name: s('name'), time: s('time'), steps: List<String>.from(a['steps'] ?? [])));
          store.saveRoutines();
          await Notify.syncAll();
          return (text: 'Routine ${s('name')} set for ${s('time')} daily', info: false);
      }
      return (text: 'Unknown tool $tool', info: false);
    } catch (e) {
      return (text: '$tool failed: $e', info: true);
    }
  }

  /// Runs a routine's steps one by one and returns the combined spoken report.
  Future<String> runRoutine(Routine r) async {
    final out = <String>[];
    for (final step in r.steps) {
      out.add(await ask(step, speak: false));
    }
    return out.join('\n\n');
  }
}
