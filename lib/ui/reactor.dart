import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../services/store.dart';
import '../services/voice.dart';
import 'theme.dart';

/// The JARVIS arc-reactor core with side sound waves.
/// Idle: slow breathing · Listening: waves follow your voice · Thinking: fast spin · Speaking: pulsing.
class Reactor extends StatefulWidget {
  final double height;
  final bool label; // "JARVIS ONLINE" text in the core
  final bool compact; // small dashboard version
  final Widget? center; // e.g. caller avatar instead of the orb
  const Reactor({super.key, this.height = 280, this.label = true, this.compact = false, this.center});
  @override
  State<Reactor> createState() => _ReactorState();
}

class _ReactorState extends State<Reactor> with SingleTickerProviderStateMixin {
  late final _c = AnimationController(vsync: this, duration: const Duration(seconds: 1))..repeat();
  double _spin = 0, _time = 0;
  Duration _last = Duration.zero;

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: widget.height,
      width: double.infinity,
      child: AnimatedBuilder(
        animation: Listenable.merge([_c, voice.state, voice.level]),
        builder: (context, _) {
          final animate = store.get<bool>('animations');
          final now = _c.lastElapsedDuration ?? Duration.zero;
          final dt = animate ? ((now - _last).inMicroseconds / 1e6).clamp(0.0, 0.1) : 0.0;
          _last = now;
          final st = voice.state.value;
          _time += dt;
          _spin += dt * (st == JState.thinking ? 2.4 : 0.25);
          return Stack(alignment: Alignment.center, children: [
            CustomPaint(
              size: Size.infinite,
              painter: _ReactorPainter(
                  spin: _spin, time: _time, state: st, level: voice.level.value, accent: C.accent, label: widget.label, compact: widget.compact, avatar: widget.center != null),
            ),
            if (widget.center != null) widget.center!,
          ]);
        },
      ),
    );
  }
}

class _ReactorPainter extends CustomPainter {
  final double spin, time, level;
  final JState state;
  final Color accent;
  final bool label, compact, avatar;
  _ReactorPainter(
      {required this.spin,
      required this.time,
      required this.state,
      required this.level,
      required this.accent,
      required this.label,
      required this.compact,
      required this.avatar});

  @override
  void paint(Canvas canvas, Size size) {
    final c = size.center(Offset.zero);
    final r = size.height * (compact ? 0.40 : 0.42);
    final active = state == JState.listening || state == JState.speaking;
    final breath = 0.5 + 0.5 * math.sin(time * 1.6);
    final amp = active ? 0.45 + level * 0.75 : (state == JState.thinking ? 0.6 : 0.45 + 0.1 * breath);
    final glow = Paint()..maskFilter = const MaskFilter.blur(BlurStyle.normal, 8);

    // Ambient glow.
    canvas.drawCircle(
        c,
        r * 1.5,
        Paint()
          ..shader = RadialGradient(colors: [accent.withValues(alpha: 0.28), accent.withValues(alpha: 0.0)])
              .createShader(Rect.fromCircle(center: c, radius: r * 1.5)));

    // Side sound waves.
    final wave = Paint()..strokeCap = StrokeCap.round;
    for (final dir in [-1, 1]) {
      final start = r * 1.04, end = size.width / 2 - 4;
      canvas.drawLine(c + Offset(dir * start, 0), c + Offset(dir * end, 0),
          Paint()
            ..color = accent.withValues(alpha: 0.5)
            ..strokeWidth = 1);
      var i = 0;
      for (double x = start; x < end; x += compact ? 4 : 4.5, i++) {
        final t = (x - start) / (end - start);
        final env = math.pow(1 - t, 1.25).toDouble() * (0.55 + 0.45 * math.sin(i * 1.7 + 3));
        final h = r * 0.95 * amp * env * (0.55 + 0.45 * math.sin(time * 9 + i * 0.9 + dir));
        wave
          ..color = Color.lerp(Colors.white, accent, 0.25 + t * 0.6)!.withValues(alpha: (1 - t) * 0.9 + 0.1)
          ..strokeWidth = compact ? 1.6 : 2;
        canvas.drawLine(c + Offset(dir * x, -h.abs() - 1), c + Offset(dir * x, h.abs() + 1), wave);
      }
    }

    // Outer heavy metallic ring with bright rim.
    final ringW = r * 0.13;
    final outer = Rect.fromCircle(center: c, radius: r - ringW / 2);
    canvas.drawCircle(c, r - ringW / 2,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = ringW
          ..shader = SweepGradient(
            transform: GradientRotation(-spin * 0.3),
            colors: const [Color(0xFF0E2A40), Color(0xFF3B6E8F), Color(0xFF0A1C2C), Color(0xFF2C5875), Color(0xFF0E2A40)],
          ).createShader(outer));
    final rim = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2
      ..color = accent;
    canvas.drawCircle(c, r, rim..color = accent.withValues(alpha: 0.9));
    canvas.drawCircle(c, r, glow..color = accent.withValues(alpha: 0.5)..style = PaintingStyle.stroke..strokeWidth = 4);
    canvas.drawCircle(c, r - ringW, rim..color = accent.withValues(alpha: 0.6)..strokeWidth = 1.2);
    // Bright arc segments on the outer ring.
    final seg = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = ringW * 0.35
      ..strokeCap = StrokeCap.butt
      ..color = accent.withValues(alpha: 0.85);
    for (var k = 0; k < 8; k++) {
      canvas.drawArc(Rect.fromCircle(center: c, radius: r - ringW / 2), spin * 0.5 + k * math.pi / 4 + 0.08, math.pi / 4 - 0.35,
          false, seg);
    }

    // Tick ring.
    final r2 = r * 0.80;
    canvas.drawCircle(c, r2, Paint()..style = PaintingStyle.stroke..strokeWidth = 1..color = accent.withValues(alpha: 0.55));
    final tick = Paint()..strokeWidth = 1.2;
    for (var k = 0; k < 72; k++) {
      final a = -spin * 0.6 + k * 2 * math.pi / 72;
      final long = k % 6 == 0;
      tick.color = accent.withValues(alpha: long ? 0.95 : 0.45);
      canvas.drawLine(c + Offset(math.cos(a), math.sin(a)) * (r2 - (long ? r * 0.07 : r * 0.035)),
          c + Offset(math.cos(a), math.sin(a)) * r2, tick);
    }

    // Inner segmented ring (12 bright dashes).
    final r3 = r * 0.62;
    final dash = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = r * 0.06
      ..color = accent;
    for (var k = 0; k < 12; k++) {
      final a = spin + k * math.pi / 6;
      canvas.drawArc(Rect.fromCircle(center: c, radius: r3), a, math.pi / 6 - 0.22, false, dash);
    }
    canvas.drawCircle(c, r3, glow..color = accent.withValues(alpha: 0.35)..strokeWidth = r * 0.08);
    canvas.drawCircle(c, r * 0.5, Paint()..style = PaintingStyle.stroke..strokeWidth = 1.4..color = accent.withValues(alpha: 0.7));

    if (avatar) return;

    // Core orb.
    final pulse = state == JState.speaking ? level : (state == JState.thinking ? 0.5 + 0.5 * math.sin(time * 8) : breath * 0.4);
    final coreR = r * (0.28 + 0.04 * pulse);
    canvas.drawCircle(
        c,
        coreR * 1.9,
        Paint()
          ..shader = RadialGradient(colors: [accent.withValues(alpha: 0.75), accent.withValues(alpha: 0.0)])
              .createShader(Rect.fromCircle(center: c, radius: coreR * 1.9)));
    canvas.drawCircle(
        c,
        coreR,
        Paint()
          ..shader = RadialGradient(colors: [Colors.white, Colors.white, Color.lerp(Colors.white, accent, 0.5)!, accent],
                  stops: const [0, 0.35, 0.7, 1])
              .createShader(Rect.fromCircle(center: c, radius: coreR)));

    if (label && !compact) {
      final tp = TextPainter(
        text: TextSpan(
          text: 'JARVIS\n${switch (state) { JState.listening => 'LISTENING', JState.thinking => 'THINKING', JState.speaking => 'SPEAKING', _ => 'ONLINE' }}',
          style: TextStyle(fontFamily: 'Roboto', color: accent, fontSize: r * 0.075, letterSpacing: 1.5, height: 1.15, fontWeight: FontWeight.w500),
        ),
        textAlign: TextAlign.center,
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, c + Offset(-tp.width / 2, r * 0.36));
    }
  }

  @override
  bool shouldRepaint(_ReactorPainter old) => true;
}
