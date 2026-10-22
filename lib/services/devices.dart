import 'dart:convert';

import 'package:battery_plus/battery_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import 'store.dart';

/// Live status of one device. See DEVICE_PROTOCOL.md for what a device must expose.
class DeviceStatus {
  final bool online;
  final int? battery;
  final String? detail; // e.g. "Windows 11"
  const DeviceStatus(this.online, {this.battery, this.detail});
}

final devices = DeviceHub();

class DeviceHub extends ChangeNotifier {
  final Map<String, DeviceStatus> status = {};

  Map<String, String> _headers(Device d) => {
        'Content-Type': 'application/json',
        'ngrok-skip-browser-warning': 'true',
        'User-Agent': 'JarvisPocoApp',
        if (d.token.isNotEmpty) 'Authorization': 'Bearer ${d.token}',
      };

  Uri _uri(Device d, String path) => Uri.parse('${d.url.replaceAll(RegExp(r'/+$'), '')}$path');

  Future<void> refresh() async {
    await Future.wait(store.devices.map(refreshOne));
  }

  Future<DeviceStatus> refreshOne(Device d) async {
    await Future<void>.delayed(Duration.zero); // never notify listeners synchronously (callers may be mid-build)
    DeviceStatus s;
    if (d.isThisPhone) {
      int? battery;
      try {
        battery = await Battery().batteryLevel;
      } catch (_) {}
      s = DeviceStatus(true, battery: battery, detail: 'This phone');
    } else if (d.url.isEmpty) {
      s = const DeviceStatus(false, detail: 'Not connected');
    } else {
      try {
        final r = await http.get(_uri(d, '/status'), headers: _headers(d)).timeout(const Duration(seconds: 6));
        if (r.statusCode == 200) {
          final j = jsonDecode(r.body);
          s = DeviceStatus(j['online'] ?? true, battery: (j['battery'] as num?)?.round(), detail: j['os'] ?? j['name']);
        } else {
          // Older servers without /status are still alive if they answer at all (and it's not ngrok's offline page).
          s = DeviceStatus(r.statusCode < 500 && !r.body.contains('ERR_NGROK'));
        }
      } catch (_) {
        s = const DeviceStatus(false);
      }
    }
    status[d.id] = s;
    notifyListeners();
    return s;
  }

  Device? find(String name) {
    final q = name.toLowerCase();
    return store.devices.where((d) => d.name.toLowerCase() == q).firstOrNull ??
        store.devices.where((d) => d.name.toLowerCase().contains(q) || d.type == q || q.contains(d.type)).firstOrNull;
  }

  /// Sends a natural-language command to a device's JARVIS agent, returns its reply.
  Future<String> command(Device d, String command) async {
    if (d.url.isEmpty) throw Exception('${d.name} is not connected yet. Add its address in Connected Devices.');
    final r = await http
        .post(_uri(d, '/command'), headers: _headers(d), body: jsonEncode({'command': command, 'from': 'phone'}))
        .timeout(const Duration(seconds: 90));
    if (r.statusCode != 200) throw Exception('${d.name} answered HTTP ${r.statusCode}');
    final j = jsonDecode(r.body);
    return '${j['reply'] ?? j['result'] ?? 'Done'}';
  }

  /// Messages devices want to push to the phone (e.g. "task finished, call Krishna").
  Future<List<Map>> events(Device d) async {
    if (d.url.isEmpty || d.isThisPhone) return [];
    try {
      final r = await http.get(_uri(d, '/events'), headers: _headers(d)).timeout(const Duration(seconds: 6));
      if (r.statusCode != 200) return [];
      final j = jsonDecode(r.body);
      return List<Map>.from(j is List ? j : (j['events'] ?? []));
    } catch (_) {
      return [];
    }
  }

  String connectedSummary() {
    final on = store.devices.where((d) => status[d.id]?.online ?? d.isThisPhone).map((d) => d.name.toUpperCase());
    return on.isEmpty ? 'NONE' : on.join(', ');
  }
}
