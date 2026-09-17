import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

void main() => runApp(MyApp());

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'JARVIS OS',
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: const Color(0xFF070503),
        primaryColor: Colors.orangeAccent,
      ),
      home: JarvisHome(),
    );
  }
}

class JarvisHome extends StatefulWidget {
  @override
  _JarvisHomeState createState() => _JarvisHomeState();
}

class _JarvisHomeState extends State<JarvisHome> with SingleTickerProviderStateMixin {
  final TextEditingController _controller = TextEditingController();
  String _responseMessage = "ULTRON CORE ACTIVE. Ready for instructions, Sir.";
  bool _isLoading = false;
  late AnimationController _animationController;

  @override
  void initState() {
    super.initState();
    _animationController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 8),
    )..repeat();
  }

  @override
  void dispose() {
    _animationController.dispose();
    _controller.dispose();
    super.dispose();
  }

  Future<void> sendCommandToLaptop(String command) async {
    setState(() {
      _isLoading = true;
    });

    try {
      final url = Uri.parse('https://settle-usher-livable.ngrok-free.dev/command');
      
      // Fixed Ngrok Bypass Warning Header
      final response = await http.post(
        url,
        headers: {
          "Content-Type": "application/json",
          "ngrok-skip-browser-warning": "true",
          "User-Agent": "JarvisPocoApp"
        },
        body: jsonEncode({"command": command}),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        setState(() {
          _responseMessage = data['reply'] ?? "Command executed by Ultron Core.";
        });
      } else {
        setState(() {
          _responseMessage = "Core Alert: Server HTTP ${response.statusCode}";
        });
      }
    } catch (e) {
      setState(() {
        _responseMessage = "Core Offline: Check laptop server & ngrok URL.";
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: const Text(
          'J.A.R.V.I.S — ULTRON CORE',
          style: TextStyle(
            color: Colors.amberAccent,
            letterSpacing: 3,
            fontWeight: FontWeight.bold,
            fontSize: 18,
          ),
        ),
        centerTitle: true,
      ),
      body: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 10.0),
        child: Column(
          children: [
            const Spacer(),
            // Animated Ultron 3D Orbiting Glowing Core
            AnimatedBuilder(
              animation: _animationController,
              builder: (context, child) {
                return CustomPaint(
                  painter: UltronCorePainter(
                    progress: _animationController.value,
                    isLoading: _isLoading,
                  ),
                  child: const SizedBox(
                    width: 260,
                    height: 260,
                  ),
                );
              },
            ),
            const Spacer(),
            // Glowing Orange Ultron Status Terminal Card
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.orange.withOpacity(0.08),
                borderRadius: BorderRadius.circular(15),
                border: Border.all(color: Colors.amber.withOpacity(0.5), width: 1.5),
                boxShadow: [
                  BoxShadow(
                    color: Colors.orangeAccent.withOpacity(0.15),
                    blurRadius: 15,
                    spreadRadius: 2,
                  )
                ],
              ),
              child: Text(
                _responseMessage,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: Colors.amberAccent,
                  fontSize: 15,
                  letterSpacing: 1,
                  fontFamily: 'monospace',
                ),
              ),
            ),
            const SizedBox(height: 25),
            // Futuristic Input Field
            TextField(
              controller: _controller,
              style: const TextStyle(color: Colors.amberAccent),
              decoration: InputDecoration(
                hintText: 'Execute core protocol...',
                hintStyle: TextStyle(color: Colors.orange.withOpacity(0.5)),
                filled: true,
                fillColor: const Color(0xFF140D07),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide(color: Colors.orange.withOpacity(0.3)),
                ),
                focusedBorder: OutlineInputBorder(
                  borderSide: const BorderSide(color: Colors.amberAccent, width: 2),
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
            const SizedBox(height: 15),
            // Glowing Action Button
            SizedBox(
              width: double.infinity,
              height: 52,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.amberAccent,
                  foregroundColor: Colors.black,
                  elevation: 8,
                  shadowColor: Colors.orangeAccent,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                onPressed: _isLoading
                    ? null
                    : () {
                        if (_controller.text.isNotEmpty) {
                          sendCommandToLaptop(_controller.text);
                        }
                      },
                child: _isLoading
                    ? const CircularProgressIndicator(color: Colors.black)
                    : const Text(
                        'INITIALIZE COMMAND',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                          letterSpacing: 2,
                        ),
                      ),
              ),
            ),
            const SizedBox(height: 15),
          ],
        ),
      ),
    );
  }
}

// Custom Painter for 3-Axis Rotating Glowing Ultron Rings & Core Center
class UltronCorePainter extends CustomPainter {
  final double progress;
  final bool isLoading;

  UltronCorePainter({required this.progress, required this.isLoading});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2.6;

    final glowPaint = Paint()
      ..color = Colors.deepOrangeAccent.withOpacity(0.6)
      ..style = PaintingStyle.fill
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 35);

    // Center Radiant Sun Core
    canvas.drawCircle(center, radius * 0.28, glowPaint);
    
    final corePaint = Paint()
      ..color = Colors.amberAccent
      ..style = PaintingStyle.fill;
    canvas.drawCircle(center, radius * 0.22, corePaint);

    final ringPaint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3.5;

    final double speedMultiplier = isLoading ? 3.0 : 1.0;

    // Draw 3 Rotating Orbital Ellipses at Off-Axis Rotations
    for (int i = 0; i < 3; i++) {
      final double angleShift = (i * math.pi / 3);
      final double rotationAngle = (progress * 2 * math.pi * speedMultiplier) + angleShift;

      canvas.save();
      canvas.translate(center.dx, center.dy);
      canvas.rotate(rotationAngle);

      final rect = Rect.fromCenter(
        center: Offset.zero,
        width: radius * 2.1,
        height: radius * 1.1,
      );

      ringPaint.color = i % 2 == 0 ? Colors.amberAccent : Colors.deepOrangeAccent;

      // Glow Layer for Rings
      final ringGlow = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 6.0
        ..color = ringPaint.color.withOpacity(0.5)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 8);

      canvas.drawOval(rect, ringGlow);
      canvas.drawOval(rect, ringPaint);

      canvas.restore();
    }
  }

  @override
  bool shouldRepaint(covariant UltronCorePainter oldDelegate) => true;
}
