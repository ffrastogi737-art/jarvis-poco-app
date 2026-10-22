import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import 'store.dart';

class Attachment {
  final Uint8List bytes;
  final String mime, name;
  final String? path; // local file, for thumbnails
  Attachment(this.bytes, this.mime, this.name, {this.path});
  bool get isImage => mime.startsWith('image/');
}

class Turn {
  final String role; // user | assistant
  final String text;
  final List<Attachment> files;
  final String? youtubeUrl; // Gemini can watch YouTube videos directly
  Turn(this.role, this.text, {this.files = const [], this.youtubeUrl});
}

class BrainException implements Exception {
  final String message;
  BrainException(this.message);
  @override
  String toString() => message;
}

/// The cloud brain. Google Gemini (free API key, sees images/video/PDF) when a key is set,
/// otherwise Pollinations' key-less free model (text only).
class Brain {
  static final online = ValueNotifier<bool?>(null);

  static String get _key => store.get<String>('geminiKey').trim();
  static bool get hasVision => _key.isNotEmpty;
  static String get providerName => hasVision ? 'Gemini (${store.get<String>('geminiModel')})' : 'Pollinations (free, text only)';

  static Future<String> complete(List<Turn> turns, {required String system, double temperature = 0.7}) async {
    final needsVision = turns.any((t) => t.files.isNotEmpty || t.youtubeUrl != null);
    try {
      if (_key.isNotEmpty) {
        try {
          return _ok(await _retry(() => _gemini(turns, system, temperature)));
        } catch (e) {
          if (needsVision) rethrow;
          debugPrint('Gemini failed, falling back: $e');
        }
      } else if (turns.any((t) => t.files.isNotEmpty)) {
        throw BrainException(
            'To see images, videos and documents I need a free Google Gemini API key, sir. Add it in Settings → Advanced.');
      }
      return _ok(await _retry(() => _pollinations(turns, system, temperature)));
    } catch (e) {
      online.value = false;
      if (e is BrainException) rethrow;
      throw BrainException('My cloud brain is unreachable at the moment, sir. Please check the internet connection. ($e)');
    }
  }

  static String _ok(String s) {
    online.value = true;
    return s.trim();
  }

  static Future<String> _retry(Future<String> Function() f) async {
    try {
      return await f();
    } catch (e) {
      if (e is BrainException) rethrow;
      await Future.delayed(const Duration(seconds: 2));
      return f();
    }
  }

  static Future<String> _gemini(List<Turn> turns, String system, double temperature) async {
    final model = store.get<String>('geminiModel').trim();
    final body = {
      'system_instruction': {
        'parts': [
          {'text': system}
        ]
      },
      'contents': [
        for (final t in turns)
          {
            'role': t.role == 'assistant' ? 'model' : 'user',
            'parts': [
              for (final f in t.files) {'inline_data': {'mime_type': f.mime, 'data': base64Encode(f.bytes)}},
              if (t.youtubeUrl != null) {'file_data': {'file_uri': t.youtubeUrl}},
              {'text': t.text.isEmpty ? '(no text)' : t.text},
            ]
          }
      ],
      'generationConfig': {'temperature': temperature},
    };
    final r = await http
        .post(Uri.parse('https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent'),
            headers: {'Content-Type': 'application/json', 'x-goog-api-key': _key}, body: jsonEncode(body))
        .timeout(const Duration(seconds: 90));
    final j = jsonDecode(utf8.decode(r.bodyBytes));
    if (r.statusCode != 200) {
      final msg = j['error']?['message'] ?? 'HTTP ${r.statusCode}';
      if (r.statusCode == 400 || r.statusCode == 403) throw BrainException('Gemini rejected the request: $msg');
      throw Exception(msg);
    }
    final parts = j['candidates']?[0]?['content']?['parts'] as List?;
    if (parts == null) throw Exception('Empty answer (${j['promptFeedback'] ?? j['candidates']?[0]?['finishReason']})');
    return parts.map((p) => p['text'] ?? '').join();
  }

  static Future<String> _pollinations(List<Turn> turns, String system, double temperature) async {
    final r = await http
        .post(Uri.parse('https://text.pollinations.ai/openai'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'model': 'openai',
              'messages': [
                {'role': 'system', 'content': system},
                for (final t in turns)
                  {'role': t.role, 'content': t.youtubeUrl == null ? t.text : '${t.text}\n(Video: ${t.youtubeUrl})'}
              ],
            }))
        .timeout(const Duration(seconds: 90));
    if (r.statusCode != 200) throw Exception('Pollinations HTTP ${r.statusCode}');
    final msg = jsonDecode(utf8.decode(r.bodyBytes))['choices'][0]['message'];
    // The free model sometimes answers with native tool calls; turn them into JARVIS action lines.
    final calls = [
      for (final c in msg['tool_calls'] ?? [])
        '<<ACTION ${jsonEncode({'tool': c['function']['name'], ...jsonDecode(c['function']['arguments'] ?? '{}')})}>>'
    ];
    final content = [if (msg['content'] != null) '${msg['content']}', ...calls].join('\n').trim();
    if (content.isEmpty) throw Exception('Empty answer');
    return content;
  }

  /// One-shot helper for tool pages (summaries, code, translation...).
  static Future<String> ask(String prompt, {String? system, List<Attachment> files = const [], String? youtubeUrl}) =>
      complete([Turn('user', prompt, files: files, youtubeUrl: youtubeUrl)],
          system: system ?? 'You are J.A.R.V.I.S., a precise and helpful assistant. Be clear and well structured.');
}
