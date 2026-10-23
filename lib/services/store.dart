import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// All persistent JARVIS data lives here (on-device, shared_preferences JSON).
final store = Store();

String newId() => DateTime.now().microsecondsSinceEpoch.toRadixString(36);

class TaskItem {
  String id, title;
  DateTime? due;
  bool done;
  TaskItem({String? id, required this.title, this.due, this.done = false}) : id = id ?? newId();
  Map<String, dynamic> toJson() => {'id': id, 'title': title, 'due': due?.toIso8601String(), 'done': done};
  factory TaskItem.fromJson(Map j) =>
      TaskItem(id: j['id'], title: j['title'], due: j['due'] == null ? null : DateTime.parse(j['due']), done: j['done'] ?? false);
}

class Note {
  String id, title, content, tag;
  DateTime updated;
  Note({String? id, required this.title, required this.content, this.tag = 'General', DateTime? updated})
      : id = id ?? newId(),
        updated = updated ?? DateTime.now();
  Map<String, dynamic> toJson() =>
      {'id': id, 'title': title, 'content': content, 'tag': tag, 'updated': updated.toIso8601String()};
  factory Note.fromJson(Map j) =>
      Note(id: j['id'], title: j['title'], content: j['content'], tag: j['tag'] ?? 'General', updated: DateTime.parse(j['updated']));
}

class Device {
  String id, name, type, url, token;
  bool trusted, fullAccess, isThisPhone;
  Device(
      {String? id,
      required this.name,
      required this.type,
      this.url = '',
      this.token = '',
      this.trusted = true,
      this.fullAccess = false,
      this.isThisPhone = false})
      : id = id ?? newId();
  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'type': type,
        'url': url,
        'token': token,
        'trusted': trusted,
        'fullAccess': fullAccess,
        'isThisPhone': isThisPhone
      };
  factory Device.fromJson(Map j) => Device(
      id: j['id'],
      name: j['name'],
      type: j['type'],
      url: j['url'] ?? '',
      token: j['token'] ?? '',
      trusted: j['trusted'] ?? true,
      fullAccess: j['fullAccess'] ?? false,
      isThisPhone: j['isThisPhone'] ?? false);
}

class Routine {
  String id, name, time; // time is "HH:mm", runs daily
  List<String> steps;
  bool enabled;
  Routine({String? id, required this.name, required this.time, required this.steps, this.enabled = true}) : id = id ?? newId();
  Map<String, dynamic> toJson() => {'id': id, 'name': name, 'time': time, 'steps': steps, 'enabled': enabled};
  factory Routine.fromJson(Map j) =>
      Routine(id: j['id'], name: j['name'], time: j['time'], steps: List<String>.from(j['steps']), enabled: j['enabled'] ?? true);
}

/// A call JARVIS will place to you at a set time.
class ScheduledCall {
  String id, message;
  DateTime at;
  ScheduledCall({String? id, required this.message, required this.at}) : id = id ?? newId();
  Map<String, dynamic> toJson() => {'id': id, 'message': message, 'at': at.toIso8601String()};
  factory ScheduledCall.fromJson(Map j) => ScheduledCall(id: j['id'], message: j['message'], at: DateTime.parse(j['at']));
}

class Msg {
  final String role; // user | jarvis
  String text;
  final DateTime time;
  String? imageUrl, imagePath;
  Msg(this.role, this.text, {DateTime? time, this.imageUrl, this.imagePath}) : time = time ?? DateTime.now();
  Map<String, dynamic> toJson() =>
      {'role': role, 'text': text, 'time': time.toIso8601String(), 'imageUrl': imageUrl, 'imagePath': imagePath};
  factory Msg.fromJson(Map j) =>
      Msg(j['role'], j['text'], time: DateTime.parse(j['time']), imageUrl: j['imageUrl'], imagePath: j['imagePath']);
}

class Store extends ChangeNotifier {
  late SharedPreferences _p;
  Map<String, dynamic> settings = {};
  List<TaskItem> tasks = [];
  List<Note> notes = [];
  List<String> memory = [];
  List<Device> devices = [];
  List<Routine> routines = [];
  List<ScheduledCall> calls = [];
  List<Msg> chat = [];

  static const defaults = <String, dynamic>{
    'name': 'Krishna',
    'city': '',
    'geminiKey': '',
    'geminiModel': 'gemini-flash-latest',
    'neuralVoice': true,
    'voiceEn': 'en-GB-RyanNeural',
    'voiceHi': 'hi-IN-MadhurNeural',
    'rate': -5, // percent
    'pitch': -4, // Hz
    'sttLocale': 'en_IN',
    'autoSpeak': true,
    'confirmActions': true,
    'accent': 0xFF19C6FF,
    'animations': true,
    'pollDevices': true,
  };

  T get<T>(String k) => (settings[k] ?? defaults[k]) as T;
  void set(String k, Object? v) {
    settings[k] = v;
    _save('settings', settings);
  }

  Future<void> load() async {
    _p = await SharedPreferences.getInstance();
    dynamic read(String k) => _p.getString(k) == null ? null : jsonDecode(_p.getString(k)!);
    settings = Map<String, dynamic>.from(read('settings') ?? {});
    tasks = [for (final j in read('tasks') ?? []) TaskItem.fromJson(j)];
    notes = [for (final j in read('notes') ?? []) Note.fromJson(j)];
    memory = List<String>.from(read('memory') ?? []);
    routines = [for (final j in read('routines') ?? []) Routine.fromJson(j)];
    calls = [for (final j in read('calls') ?? []) ScheduledCall.fromJson(j)];
    chat = [for (final j in read('chat') ?? []) Msg.fromJson(j)];
    final dev = read('devices');
    devices = dev == null
        ? [
            Device(name: 'Phone', type: 'phone', isThisPhone: true, fullAccess: true),
            Device(name: 'Laptop', type: 'laptop', url: 'https://settle-usher-livable.ngrok-free.dev', fullAccess: true),
            Device(name: 'TV', type: 'tv'),
            Device(name: 'Smart Speaker', type: 'speaker'),
            Device(name: 'Tablet', type: 'tablet'),
          ]
        : [for (final j in dev) Device.fromJson(j)];
  }

  void _save(String key, Object value) {
    _p.setString(key, jsonEncode(value));
    notifyListeners();
  }

  void saveTasks() => _save('tasks', tasks);
  void saveNotes() => _save('notes', notes);
  void saveMemory() => _save('memory', memory);
  void saveDevices() => _save('devices', devices);
  void saveRoutines() => _save('routines', routines);
  void saveCalls() => _save('calls', calls);
  void saveChat() {
    if (chat.length > 200) chat = chat.sublist(chat.length - 200);
    _save('chat', chat);
  }

  Future<void> clearAll() async {
    await _p.clear();
    await load();
    notifyListeners();
  }
}
