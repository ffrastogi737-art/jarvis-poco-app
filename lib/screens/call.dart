import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../services/jarvis.dart';
import '../services/notify.dart';
import '../services/phone.dart';
import '../services/store.dart';
import '../services/voice.dart';
import '../ui/reactor.dart';
import '../ui/shell.dart';
import '../ui/theme.dart';

/// A voice call with JARVIS. `incoming` = JARVIS is calling you (reminder, routine, device report);
/// otherwise you are calling JARVIS for a hands-free conversation.
class CallScreen extends StatefulWidget {
  final CallRequest? incoming;
  const CallScreen({super.key, this.incoming});
  @override
  State<CallScreen> createState() => _CallScreenState();
}

class _CallScreenState extends State<CallScreen> {
  late bool _ringing = widget.incoming != null;
  bool _muted = false, _ended = false;
  String _caption = '';
  final _start = DateTime.now();
  Timer? _ticker, _vibrate, _timeout;

  @override
  void initState() {
    super.initState();
    if (_ringing) {
      PhoneActions.ring(true);
      _vibrate = Timer.periodic(const Duration(milliseconds: 1200), (_) => HapticFeedback.heavyImpact());
      _timeout = Timer(const Duration(seconds: 45), () => _hangUp(missed: true));
    } else {
      WidgetsBinding.instance.addPostFrameCallback((_) => _answer());
    }
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) => mounted ? setState(() {}) : null);
  }

  void _stopRinging() {
    PhoneActions.ring(false);
    _vibrate?.cancel();
    _timeout?.cancel();
  }

  Future<void> _answer() async {
    _stopRinging();
    setState(() => _ringing = false);
    final c = widget.incoming;
    final routine = c?.routineId == null ? null : store.routines.where((r) => r.id == c!.routineId).firstOrNull;
    if (routine != null) {
      await _say('${_hello()} Sir, आपका ${routine.name} routine शुरू कर रहा हूँ।');
      _caption = 'Working on it…';
      if (mounted) setState(() {});
      await _say(await jarvis.runRoutine(routine));
    } else if (c != null) {
      await _say(c.message);
    } else {
      await _say('${_hello()} Sir, JARVIS here. बताइए, क्या करना है?');
    }
    _loop();
  }

  String _hello() {
    final h = DateTime.now().hour;
    return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
  }

  Future<void> _say(String text) async {
    if (_ended) return;
    setState(() => _caption = text);
    await voice.speak(text);
  }

  /// Hands-free: listen → think → speak, until the call ends.
  Future<void> _loop() async {
    var silence = 0;
    while (!_ended && mounted) {
      if (_muted) {
        await Future.delayed(const Duration(milliseconds: 500));
        continue;
      }
      String heard;
      try {
        heard = await voice.listen(onPartial: (p) => mounted ? setState(() => _caption = p) : null);
      } catch (e) {
        await _say('Sir, मैं आपको सुन नहीं पा रहा। $e');
        return;
      }
      if (_ended) return;
      if (heard.isEmpty) {
        if (++silence >= 3) {
          await _say('ठीक है Boss, जब ज़रूरत हो call कर लीजिए।');
          return _hangUp();
        }
        continue;
      }
      silence = 0;
      if (RegExp(r'\b(bye|goodbye|hang up|end call|that.?s all)\b|बाय|फोन रखो', caseSensitive: false).hasMatch(heard)) {
        await _say('Very good, Sir. Signing off.');
        return _hangUp();
      }
      setState(() => _caption = 'Thinking…');
      await _say(await jarvis.ask(heard, speak: false));
    }
  }

  Future<void> _hangUp({bool missed = false}) async {
    if (_ended) return;
    _ended = true;
    _stopRinging();
    await voice.stop();
    await voice.stopListening();
    if (missed && widget.incoming != null) {
      // Fallback when the call is not picked up: leave the message in the conversation, like a voice note.
      store.chat.add(Msg('jarvis', '📞 Missed call from JARVIS: ${widget.incoming!.message}'));
      store.saveChat();
    }
    if (mounted) Shell.of(context).pop();
  }

  @override
  void dispose() {
    _ended = true;
    _stopRinging();
    _ticker?.cancel();
    voice.stop();
    voice.stopListening();
    super.dispose();
  }

  String get _duration {
    final d = DateTime.now().difference(_start);
    return '${d.inMinutes.toString().padLeft(2, '0')}:${(d.inSeconds % 60).toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, box) => SingleChildScrollView(
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: box.maxHeight),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14),
            child: Column(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              const SizedBox(height: 10),
              Text(_ringing ? 'Incoming Call' : 'JARVIS Call', style: h1(19)),
              Reactor(
                height: (box.maxHeight * 0.36).clamp(200.0, 280.0),
                label: false,
                center: Container(
                  width: 96,
                  height: 96,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: const RadialGradient(colors: [Color(0xFF0E4166), Color(0xFF041828)]),
                    boxShadow: [BoxShadow(color: C.accent.withValues(alpha: 0.5), blurRadius: 18)],
                  ),
                  child: Icon(Icons.person, size: 78, color: const Color(0xFF6FD3FF), shadows: [Shadow(color: C.accent, blurRadius: 10)]),
                ),
              ),
              Column(children: [
                Text('J.A.R.V.I.S.', style: h1(22)),
                const SizedBox(height: 4),
                Text(_ringing ? 'JARVIS Line  •  Incoming call...' : 'JARVIS Line  •  $_duration',
                    style: TextStyle(color: C.accent, fontSize: 14)),
                const SizedBox(height: 10),
                SizedBox(
                  height: 60,
                  child: Text(_caption,
                      textAlign: TextAlign.center,
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: Color(0xFFD0DCE8), fontSize: 13.5)),
                ),
              ]),
              Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
                _round(_muted ? Icons.mic_off : Icons.mic_off_outlined, _muted ? 'Unmute' : 'Mute', () async {
                  setState(() => _muted = !_muted);
                  if (_muted) await voice.stopListening();
                }, active: _muted),
                _round(Icons.dialpad, 'Keypad', _keypad),
                _round(Icons.chat_bubble_outline, 'Message', _message),
              ]),
              const SizedBox(height: 18),
              Row(children: [
                Expanded(child: _pill('Decline', Icons.call_end, C.red, () => _hangUp(missed: _ringing))),
                const SizedBox(width: 14),
                Expanded(
                  child: _ringing
                      ? _pill('Answer', Icons.call, C.green, _answer)
                      : _pill('End', Icons.call_end, C.red.withValues(alpha: 0.75), _hangUp),
                ),
              ]),
              const SizedBox(height: 14),
            ]),
          ),
        ),
      ),
    );
  }

  Widget _round(IconData icon, String label, VoidCallback onTap, {bool active = false}) => Column(children: [
        InkWell(
          customBorder: const CircleBorder(),
          onTap: onTap,
          child: Container(
            width: 58,
            height: 58,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: active ? C.accent.withValues(alpha: 0.3) : C.cardTop,
              border: Border.all(color: C.accent.withValues(alpha: 0.6)),
              boxShadow: [BoxShadow(color: C.accent.withValues(alpha: 0.25), blurRadius: 10)],
            ),
            child: Icon(icon, color: Colors.white, size: 27),
          ),
        ),
        const SizedBox(height: 8),
        Text(label, style: const TextStyle(color: Colors.white, fontSize: 14)),
      ]);

  Widget _pill(String label, IconData icon, Color color, VoidCallback onTap) => InkWell(
        borderRadius: BorderRadius.circular(32),
        onTap: onTap,
        child: Container(
          height: 64,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(32),
            gradient: LinearGradient(colors: [Color.lerp(color, Colors.white, 0.15)!, color]),
            boxShadow: [BoxShadow(color: color.withValues(alpha: 0.55), blurRadius: 18)],
          ),
          child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            Icon(icon, color: Colors.white, size: 30),
            const SizedBox(width: 10),
            Text(label, style: const TextStyle(color: Colors.white, fontSize: 17, fontWeight: FontWeight.w600)),
          ]),
        ),
      );

  /// Dial a real phone number from inside the call.
  Future<void> _keypad() async {
    final number = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: C.cardTop,
      isScrollControlled: true,
      builder: (c) => const _Dialer(),
    );
    if (number == null || number.isEmpty) return;
    await _hangUp();
    try {
      await PhoneActions.call(number);
    } catch (_) {}
  }

  /// Type to JARVIS when you can't speak.
  Future<void> _message() async {
    final ctrl = TextEditingController();
    final text = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: C.cardTop,
      isScrollControlled: true,
      builder: (c) => Padding(
        padding: EdgeInsets.fromLTRB(16, 16, 16, MediaQuery.of(c).viewInsets.bottom + 16),
        child: TextField(
          controller: ctrl,
          autofocus: true,
          style: const TextStyle(color: Colors.white),
          decoration: field('Message JARVIS…',
              suffix: IconButton(icon: Icon(Icons.send, color: C.accent), onPressed: () => Navigator.pop(c, ctrl.text))),
          onSubmitted: (v) => Navigator.pop(c, v),
        ),
      ),
    );
    if (text == null || text.trim().isEmpty) return;
    final wasMuted = _muted;
    setState(() => _muted = true);
    await voice.stopListening();
    if (_ringing) await _answer();
    await _say(await jarvis.ask(text, speak: false));
    if (mounted) setState(() => _muted = wasMuted);
  }
}

class _Dialer extends StatefulWidget {
  const _Dialer();
  @override
  State<_Dialer> createState() => _DialerState();
}

class _DialerState extends State<_Dialer> {
  String number = '';
  @override
  Widget build(BuildContext context) {
    const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#'];
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Row(children: [
            Expanded(child: Text(number.isEmpty ? 'Enter number' : number, textAlign: TextAlign.center, style: h1(26))),
            IconButton(
                icon: const Icon(Icons.backspace_outlined, color: Colors.white),
                onPressed: () => setState(() => number = number.isEmpty ? '' : number.substring(0, number.length - 1))),
          ]),
          const SizedBox(height: 10),
          GridView.count(
            shrinkWrap: true,
            crossAxisCount: 3,
            childAspectRatio: 1.8,
            physics: const NeverScrollableScrollPhysics(),
            children: [
              for (final k in keys)
                Padding(
                  padding: const EdgeInsets.all(5),
                  child: Glass(
                      radius: 30,
                      padding: EdgeInsets.zero,
                      onTap: () => setState(() => number += k),
                      child: Center(child: Text(k, style: h1(22)))),
                ),
            ],
          ),
          const SizedBox(height: 10),
          FloatingActionButton(
            backgroundColor: C.green,
            onPressed: () => Navigator.pop(context, number),
            child: const Icon(Icons.call, color: Colors.white),
          ),
        ]),
      ),
    );
  }
}
