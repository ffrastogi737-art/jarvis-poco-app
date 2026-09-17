import 'package:flutter/material.dart';
import 'http/http.dart' as http; // सर्वर से बात करने के लिए
import 'dart:convert';

void main() => runApp(MyApp());

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'JARVIS OS',
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: Colors.black,
        primaryColor: Colors.cyanAccent,
      ),
      home: JarvisHome(),
    );
  }
}

class JarvisHome extends StatefulWidget {
  @override
  _JarvisHomeState createState() => _JarvisHomeState();
}

class _JarvisHomeState extends State<JarvisHome> {
  final TextEditingController _controller = TextEditingController();
  String _responseMessage = "JARVIS Online. Awaiting command, Sir.";
  bool _isLoading = false;

  // लैपटॉप के Ngrok सर्वर पर कमांड भेजने का फंक्शन
  Future<void> sendCommandToLaptop(String command) async {
    setState(() {
      _isLoading = true;
    });

    try {
      // यहाँ आपका Ngrok वाला लिंक डलेगा
      final url = Uri.parse('https://settle-usher-livable.ngrok-free.dev/command');
      
      final response = await http.post(
        url,
        headers: {"Content-Type": "application/json"},
        body: jsonEncode({"command": command}),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        setState(() {
          _responseMessage = data['reply'] ?? "Action executed successfully.";
        });
      } else {
        setState(() {
          _responseMessage = "Error: Server returned status ${response.statusCode}";
        });
      }
    } catch (e) {
      setState(() {
        _responseMessage = "Connection Failed: Check laptop/ngrok status.";
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
      appBar. AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: Text('JARVIS — AI OPERATING SYSTEM', style: TextStyle(color: Colors.cyanAccent, letterSpacing: 2)),
        centerTitle: true,
      ),
      body: Padding(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Sci-Fi Arc Reactor Visual Core (Glowing Circle)
            Container(
              width: 150,
              height: 150,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(color: Colors.cyanAccent, width: 4),
                boxShadow: [
                  BoxShadow(color: Colors.cyanAccent.withOpacity(0.6), blurRadius: 20, spreadRadius: 5)
                ],
              ),
              child: Center(
                child: Icon(Icons.mic, size: 50, color: Colors.cyanAccent),
              ),
            ),
            SizedBox(height: 40),
            // Response Box (Glassmorphism style container)
            Container(
              padding: EdgeInsets.all(15),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.05),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Colors.cyanAccent.withOpacity(0.3)),
              ),
              child: Text(
                _responseMessage,
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.white70, fontSize: 16),
              ),
            ),
            SizedBox(height: 30),
            // Command Input Field
            TextField(
              controller: _controller,
              style: TextStyle(color: Colors.white),
              decoration: InputDecoration(
                hintText: 'Enter command (e.g., open calculator)...',
                hintStyle: TextStyle(color: Colors.grey),
                filled: true,
                fillColor: Colors.grey[900],
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                focusedBorder: OutlineInputBorder(
                  borderSide: BorderSide(color: Colors.cyanAccent),
                  borderRadius: BorderRadius.circular(10),
                ),
              ),
            ),
            SizedBox(height: 20),
            // Send Button
            SizedBox(
              width: double.infinity,
              height: 50,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.cyanAccent,
                  foregroundColor: Colors.black,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
                onPressed: _isLoading ? null : () {
                  if (_controller.text.isNotEmpty) {
                    sendCommandToLaptop(_controller.text);
                  }
                },
                child: _isLoading 
                  ? CircularProgressIndicator(color: Colors.black)
                  : Text('EXECUTE COMMAND', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}main
