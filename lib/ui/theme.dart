import 'package:flutter/material.dart';

import '../services/store.dart';

/// Neon-on-black JARVIS look, matching the reference design.
class C {
  static Color get accent => Color(store.get<int>('accent'));
  static const bg = Color(0xFF01050B);
  static const cardTop = Color(0xFF0C2133);
  static const cardBottom = Color(0xFF050F1B);
  static const text2 = Color(0xFFA8BCD0);
  static const green = Color(0xFF2EE06B);
  static const red = Color(0xFFFF3347);
  static const youtube = Color(0xFFFF2D2D);
}

TextStyle h1([double size = 22]) => TextStyle(color: Colors.white, fontSize: size, fontWeight: FontWeight.w500);
const sub = TextStyle(color: C.text2, fontSize: 13);

class Glass extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final double radius;
  final VoidCallback? onTap;
  final Color? border;
  final bool glow;
  final Gradient? gradient;
  const Glass(
      {super.key,
      required this.child,
      this.padding = const EdgeInsets.all(14),
      this.radius = 14,
      this.onTap,
      this.border,
      this.glow = true,
      this.gradient});

  @override
  Widget build(BuildContext context) {
    final r = BorderRadius.circular(radius);
    return Container(
      decoration: BoxDecoration(
        borderRadius: r,
        gradient: gradient ??
            const LinearGradient(
                begin: Alignment.topLeft, end: Alignment.bottomRight, colors: [C.cardTop, C.cardBottom]),
        border: Border.all(color: border ?? C.accent.withValues(alpha: 0.35)),
        boxShadow: glow ? [BoxShadow(color: C.accent.withValues(alpha: 0.10), blurRadius: 14, spreadRadius: 0.5)] : null,
      ),
      child: Material(
        type: MaterialType.transparency,
        child: InkWell(borderRadius: r, onTap: onTap, child: Padding(padding: padding, child: child)),
      ),
    );
  }
}

/// Dashed outline card ("Pair New Device").
class DashedBox extends StatelessWidget {
  final Widget child;
  final VoidCallback? onTap;
  const DashedBox({super.key, required this.child, this.onTap});
  @override
  Widget build(BuildContext context) => CustomPaint(
        painter: _DashPainter(C.accent),
        child: Material(
          type: MaterialType.transparency,
          child: InkWell(
              borderRadius: BorderRadius.circular(14),
              onTap: onTap,
              child: Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14), child: child)),
        ),
      );
}

class _DashPainter extends CustomPainter {
  final Color color;
  _DashPainter(this.color);
  @override
  void paint(Canvas canvas, Size size) {
    final path = Path()..addRRect(RRect.fromRectAndRadius(Offset.zero & size, const Radius.circular(14)));
    final p = Paint()
      ..color = color.withValues(alpha: 0.8)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.2;
    for (final m in path.computeMetrics()) {
      for (double d = 0; d < m.length; d += 9) {
        canvas.drawPath(m.extractPath(d, d + 5), p);
      }
    }
  }

  @override
  bool shouldRepaint(_) => false;
}

/// Glowing outlined icon like the tool tiles.
class NeonIcon extends StatelessWidget {
  final IconData icon;
  final double size;
  final Color? color;
  const NeonIcon(this.icon, {super.key, this.size = 30, this.color});
  @override
  Widget build(BuildContext context) {
    final c = color ?? C.accent;
    return Icon(icon, size: size, color: c, shadows: [Shadow(color: c.withValues(alpha: 0.9), blurRadius: 12)]);
  }
}

class StatusDot extends StatelessWidget {
  final bool online;
  const StatusDot(this.online, {super.key});
  @override
  Widget build(BuildContext context) => Row(mainAxisSize: MainAxisSize.min, children: [
        Container(
          width: 9,
          height: 9,
          decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: online ? C.green : const Color(0xFF6FB6E8),
              boxShadow: [BoxShadow(color: (online ? C.green : C.accent).withValues(alpha: 0.6), blurRadius: 6)]),
        ),
        const SizedBox(width: 6),
        Text(online ? 'Online' : 'Offline', style: TextStyle(color: online ? C.green : const Color(0xFF8FB8DA), fontSize: 12)),
      ]);
}

/// Page title block used by Tools / Devices / Settings.
class PageTitle extends StatelessWidget {
  final String title, subtitle;
  final Widget? trailing;
  const PageTitle(this.title, this.subtitle, {super.key, this.trailing});
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 14, 16, 12),
        child: Row(children: [
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(title, style: h1(23)),
              const SizedBox(height: 3),
              Text(subtitle, style: const TextStyle(color: Color(0xFFD5E2EE), fontSize: 13.5)),
            ]),
          ),
          if (trailing != null) trailing!,
        ]),
      );
}

class CircleBtn extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;
  final double size;
  const CircleBtn(this.icon, this.onTap, {super.key, this.size = 44});
  @override
  Widget build(BuildContext context) => InkWell(
        customBorder: const CircleBorder(),
        onTap: onTap,
        child: Container(
          width: size,
          height: size,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: const RadialGradient(colors: [C.cardTop, C.cardBottom]),
            border: Border.all(color: C.accent.withValues(alpha: 0.6), width: 1.3),
            boxShadow: [BoxShadow(color: C.accent.withValues(alpha: 0.25), blurRadius: 10)],
          ),
          child: Icon(icon, color: Colors.white, size: size * 0.55),
        ),
      );
}

/// Styled text field for forms.
InputDecoration field(String hint, {Widget? suffix}) => InputDecoration(
      hintText: hint,
      hintStyle: const TextStyle(color: C.text2),
      filled: true,
      fillColor: C.cardBottom,
      suffixIcon: suffix,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12), borderSide: BorderSide(color: C.accent.withValues(alpha: 0.35))),
      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide(color: C.accent)),
    );

class NeonButton extends StatelessWidget {
  final String label;
  final IconData? icon;
  final VoidCallback? onTap;
  final Color? color;
  const NeonButton(this.label, {super.key, this.icon, this.onTap, this.color});
  @override
  Widget build(BuildContext context) => Glass(
        onTap: onTap,
        radius: 12,
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
        border: (color ?? C.accent).withValues(alpha: 0.7),
        child: Row(mainAxisSize: MainAxisSize.min, mainAxisAlignment: MainAxisAlignment.center, children: [
          if (icon != null) ...[Icon(icon, size: 18, color: color ?? C.accent), const SizedBox(width: 8)],
          Flexible(child: Text(label, style: const TextStyle(color: Colors.white, fontSize: 14), overflow: TextOverflow.ellipsis)),
        ]),
      );
}

String timeAgo(DateTime t) {
  final d = DateTime.now().difference(t);
  if (d.inMinutes < 1) return 'just now';
  if (d.inMinutes < 60) return '${d.inMinutes} min ago';
  if (d.inHours < 24) return '${d.inHours} hour${d.inHours == 1 ? '' : 's'} ago';
  return '${d.inDays} day${d.inDays == 1 ? '' : 's'} ago';
}

String friendlyDue(DateTime d) {
  final now = DateTime.now();
  final day = DateTime(d.year, d.month, d.day).difference(DateTime(now.year, now.month, now.day)).inDays;
  final h = d.hour % 12 == 0 ? 12 : d.hour % 12;
  final time = '$h:${d.minute.toString().padLeft(2, '0')} ${d.hour < 12 ? 'AM' : 'PM'}';
  final label = switch (day) { 0 => 'Today', 1 => 'Tomorrow', -1 => 'Yesterday', _ => '${d.day}/${d.month}' };
  return '$label, $time';
}

void toast(BuildContext context, String msg) =>
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg), backgroundColor: C.cardTop));
