import 'dart:async';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:speech_to_text/speech_to_text.dart';

import 'edge_tts.dart';
import 'store.dart';

enum JState { idle, listening, thinking, speaking }

final voice = Voice();

/// JARVIS's ears (Android speech recognition) and mouth (free neural voice, phone TTS fallback).
class Voice {
  final state = ValueNotifier(JState.idle);
  final level = ValueNotifier(0.0); // 0..1, drives the reactor waveform
  final _stt = SpeechToText();
  final _tts = FlutterTts();
  late final _player = AudioPlayer();
  bool _sttReady = false;
  int _speakId = 0;
  Completer<void>? _playing;

  static final _devanagari = RegExp(r'[ऀ-ॿ]');

  /// Strip markdown/code so it sounds natural when spoken.
  static String speakable(String t) => t
      .replaceAll(RegExp(r'```[\s\S]*?```'), ' I have put the code on screen. ')
      .replaceAll(RegExp(r'https?://\S+'), '')
      .replaceAll(RegExp(r'[*_#`>|]'), '')
      .replaceAll(RegExp(r'\n+'), '. ')
      .trim();

  Future<void> speak(String text) async {
    await stop();
    final id = ++_speakId;
    var t = speakable(text);
    if (t.isEmpty) return;
    if (t.length > 1200) t = '${t.substring(0, 1200)}…';
    final hindi = _devanagari.hasMatch(t);
    state.value = JState.speaking;
    final pulse = Timer.periodic(const Duration(milliseconds: 90), (_) => level.value = 0.3 + 0.7 * (DateTime.now().millisecond % 7) / 7);
    try {
      if (store.get<bool>('neuralVoice')) {
        try {
          final rate = store.get<int>('rate'), pitch = store.get<int>('pitch');
          final mp3 = await EdgeTts.synthesize(t,
              voice: store.get<String>(hindi ? 'voiceHi' : 'voiceEn'),
              rate: '${rate >= 0 ? '+' : ''}$rate%',
              pitch: '${pitch >= 0 ? '+' : ''}${pitch}Hz');
          if (id != _speakId) return;
          final done = _playing = Completer<void>();
          final sub = _player.onPlayerComplete.listen((_) => done.isCompleted ? null : done.complete());
          await _player.play(BytesSource(mp3, mimeType: 'audio/mpeg'));
          await done.future; // completed by playback end or by stop()
          await sub.cancel();
          return;
        } catch (e) {
          debugPrint('Neural voice failed, using phone voice: $e');
        }
      }
      if (id != _speakId) return;
      await _phoneVoice(hindi);
      await _tts.speak(t);
    } finally {
      pulse.cancel();
      level.value = 0;
      if (id == _speakId && state.value == JState.speaking) state.value = JState.idle;
    }
  }

  Future<void> _phoneVoice(bool hindi) async {
    await _tts.awaitSpeakCompletion(true);
    await _tts.setLanguage(hindi ? 'hi-IN' : 'en-GB');
    await _tts.setSpeechRate(0.47);
    await _tts.setPitch(0.85);
    // Prefer a male British voice from Google TTS, the closest offline match to JARVIS.
    try {
      final voices = List<Map>.from(await _tts.getVoices);
      const preferred = ['en-gb-x-rjs', 'en-gb-x-gbd', 'en-gb-x-gbb', 'hi-in-x-hid', 'hi-in-x-hie'];
      for (final p in preferred) {
        final v = voices.where((v) => '${v['name']}'.startsWith(p)).firstOrNull;
        if (v != null && '${v['locale']}'.toLowerCase().startsWith(hindi ? 'hi' : 'en')) {
          await _tts.setVoice({'name': '${v['name']}', 'locale': '${v['locale']}'});
          break;
        }
      }
    } catch (_) {}
  }

  Future<void> stop() async {
    _speakId++;
    if (_playing?.isCompleted == false) _playing!.complete();
    await _player.stop();
    await _tts.stop();
    if (state.value == JState.speaking) state.value = JState.idle;
  }

  /// Listens once; returns the recognised sentence ('' if nothing heard).
  Future<String> listen({void Function(String partial)? onPartial}) async {
    await stop();
    if (!_sttReady) {
      _sttReady = await _stt.initialize(onError: (_) {});
      if (!_sttReady) throw Exception('Speech recognition unavailable. Allow microphone permission.');
    }
    final done = Completer<String>();
    var last = '';
    state.value = JState.listening;
    await _stt.listen(
      onResult: (r) {
        last = r.recognizedWords;
        onPartial?.call(last);
        if (r.finalResult && !done.isCompleted) done.complete(last);
      },
      onSoundLevelChange: (l) => level.value = ((l + 2) / 12).clamp(0.0, 1.0),
      listenOptions: SpeechListenOptions(
        localeId: store.get<String>('sttLocale'),
        listenFor: const Duration(seconds: 30),
        pauseFor: const Duration(seconds: 3),
        partialResults: true,
        cancelOnError: true,
      ),
    );
    // speech_to_text reports "done"/"notListening" without a final result on silence.
    var seen = false;
    final watcher = Timer.periodic(const Duration(milliseconds: 300), (t) {
      if (_stt.isListening) seen = true;
      if (!_stt.isListening && (seen || t.tick > 8) && !done.isCompleted) {
        Future.delayed(const Duration(milliseconds: 400), () {
          if (!done.isCompleted) done.complete(last);
        });
        t.cancel();
      }
    });
    final text = await done.future;
    watcher.cancel();
    level.value = 0;
    if (state.value == JState.listening) state.value = JState.idle;
    return text.trim();
  }

  Future<void> stopListening() => _stt.stop();
}
