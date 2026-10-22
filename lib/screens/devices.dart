import 'package:flutter/material.dart';

import '../services/devices.dart';
import '../services/store.dart';
import '../ui/shell.dart';
import '../ui/theme.dart';

IconData deviceIcon(String type) => switch (type) {
      'phone' => Icons.smartphone,
      'laptop' => Icons.laptop_windows,
      'tv' => Icons.tv,
      'speaker' => Icons.speaker,
      'tablet' => Icons.tablet_android,
      _ => Icons.devices_other,
    };

const deviceTypes = {'phone': 'Phone', 'laptop': 'Laptop / PC', 'tv': 'TV', 'speaker': 'Smart Speaker', 'tablet': 'Tablet', 'other': 'Other'};

class DevicesScreen extends StatefulWidget {
  const DevicesScreen({super.key});
  @override
  State<DevicesScreen> createState() => _DevicesScreenState();
}

class _DevicesScreenState extends State<DevicesScreen> {
  @override
  void initState() {
    super.initState();
    devices.refresh();
  }

  @override
  Widget build(BuildContext context) {
    final shell = Shell.of(context);
    return ListenableBuilder(
      listenable: Listenable.merge([store, devices]),
      builder: (_, __) => RefreshIndicator(
        onRefresh: devices.refresh,
        child: ListView(padding: const EdgeInsets.only(bottom: 12), children: [
          PageTitle('Connected Devices', 'Manage and control your devices.',
              trailing: CircleBtn(Icons.add, () => shell.push(const DeviceEditPage()), size: 46)),
          for (final d in store.devices)
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 0, 14, 10),
              child: _DeviceCard(d, onTap: () => shell.push(DeviceEditPage(device: d))),
            ),
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 4, 14, 0),
            child: DashedBox(
              onTap: () => shell.push(const DeviceEditPage()),
              child: Row(children: [
                const SizedBox(width: 40),
                CircleBtn(Icons.add, () => shell.push(const DeviceEditPage()), size: 40),
                const SizedBox(width: 14),
                const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('Pair New Device', style: TextStyle(color: Colors.white, fontSize: 15)),
                  SizedBox(height: 2),
                  Text('Connect a new device to your JARVIS', style: TextStyle(color: Color(0xFFD0DCE8), fontSize: 12)),
                ]),
              ]),
            ),
          ),
        ]),
      ),
    );
  }
}

class _DeviceCard extends StatelessWidget {
  final Device d;
  final VoidCallback onTap;
  const _DeviceCard(this.d, {required this.onTap});

  @override
  Widget build(BuildContext context) {
    final s = devices.status[d.id];
    final online = s?.online ?? false;
    Widget tag(IconData i, String t) => Row(mainAxisSize: MainAxisSize.min, children: [
          Icon(i, size: 15, color: Colors.white),
          const SizedBox(width: 5),
          Text(t, style: const TextStyle(color: Colors.white, fontSize: 12)),
          const SizedBox(width: 14),
        ]);
    return Glass(
      onTap: onTap,
      padding: const EdgeInsets.fromLTRB(10, 12, 8, 12),
      child: Row(children: [
        Container(
          width: 74,
          height: 62,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            gradient: const RadialGradient(colors: [Color(0xFF0E4A78), Color(0xFF041522)]),
          ),
          child: Icon(deviceIcon(d.type), size: 46, color: const Color(0xFF8FD8FF), shadows: [Shadow(color: C.accent, blurRadius: 14)]),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(d.name, style: const TextStyle(color: Colors.white, fontSize: 16)),
            const SizedBox(height: 4),
            StatusDot(online),
            const SizedBox(height: 7),
            Wrap(runSpacing: 4, children: [
              tag(d.trusted ? Icons.verified_user_outlined : Icons.gpp_bad_outlined, d.trusted ? 'Trusted' : 'Untrusted'),
              tag(Icons.shield_outlined, d.fullAccess ? 'Full Access' : 'Limited Access'),
              if (s?.battery != null && s!.battery! >= 0)
                Row(mainAxisSize: MainAxisSize.min, children: [
                  Icon(Icons.battery_full, size: 16, color: s.battery! > 20 ? C.green : C.red),
                  const SizedBox(width: 3),
                  Text('${s.battery}%', style: const TextStyle(color: Colors.white, fontSize: 12)),
                ]),
            ]),
          ]),
        ),
        const Icon(Icons.chevron_right, color: Colors.white),
      ]),
    );
  }
}

/// Add, edit, test and control one device.
class DeviceEditPage extends StatefulWidget {
  final Device? device;
  const DeviceEditPage({super.key, this.device});
  @override
  State<DeviceEditPage> createState() => _DeviceEditPageState();
}

class _DeviceEditPageState extends State<DeviceEditPage> {
  late final Device d = widget.device ?? Device(name: '', type: 'laptop', fullAccess: false);
  late final _name = TextEditingController(text: d.name);
  late final _url = TextEditingController(text: d.url);
  late final _token = TextEditingController(text: d.token);
  final _cmd = TextEditingController();
  final _log = <String>[];
  bool _sending = false;

  bool get _isNew => widget.device == null;

  void _save() {
    if (_name.text.trim().isEmpty) return toast(context, 'Give the device a name');
    d
      ..name = _name.text.trim()
      ..url = _url.text.trim()
      ..token = _token.text.trim();
    if (_isNew) store.devices.add(d);
    store.saveDevices();
    devices.refreshOne(d);
    toast(context, 'Saved');
    if (_isNew) Shell.of(context).pop();
  }

  Future<void> _send() async {
    final c = _cmd.text.trim();
    if (c.isEmpty) return;
    setState(() {
      _sending = true;
      _log.insert(0, '› $c');
    });
    _cmd.clear();
    try {
      _log.insert(0, await devices.command(d, c));
    } catch (e) {
      _log.insert(0, 'Error: $e');
    }
    if (mounted) setState(() => _sending = false);
  }

  @override
  Widget build(BuildContext context) {
    final s = devices.status[d.id];
    return ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
      PageTitle(_isNew ? 'Pair New Device' : d.name,
          _isNew ? 'Connect a device running a JARVIS agent.' : (s?.online ?? false) ? 'Online${s?.detail != null ? ' • ${s!.detail}' : ''}' : 'Offline'),
      TextField(controller: _name, style: const TextStyle(color: Colors.white), decoration: field('Device name (e.g. Laptop)')),
      const SizedBox(height: 10),
      if (!d.isThisPhone) ...[
        DropdownButtonFormField<String>(
          initialValue: d.type,
          dropdownColor: C.cardTop,
          style: const TextStyle(color: Colors.white),
          decoration: field('Type'),
          items: [for (final e in deviceTypes.entries) DropdownMenuItem(value: e.key, child: Text(e.value))],
          onChanged: (v) => d.type = v!,
        ),
        const SizedBox(height: 10),
        TextField(
            controller: _url,
            style: const TextStyle(color: Colors.white),
            decoration: field('Agent address, e.g. https://xyz.ngrok-free.app or http://192.168.1.5:5000')),
        const SizedBox(height: 10),
        TextField(
            controller: _token, style: const TextStyle(color: Colors.white), decoration: field('Access token (optional)')),
        const SizedBox(height: 6),
        SwitchListTile(
          value: d.trusted,
          activeThumbColor: C.accent,
          title: const Text('Trusted', style: TextStyle(color: Colors.white)),
          subtitle: const Text('Untrusted devices are blocked from commands', style: sub),
          onChanged: (v) => setState(() => d.trusted = v),
        ),
        SwitchListTile(
          value: d.fullAccess,
          activeThumbColor: C.accent,
          title: const Text('Full Access', style: TextStyle(color: Colors.white)),
          subtitle: const Text('Off = JARVIS asks you before sending each command', style: sub),
          onChanged: (v) => setState(() => d.fullAccess = v),
        ),
      ] else
        const Padding(
          padding: EdgeInsets.all(8),
          child: Text('This is the phone you are holding. JARVIS uses its microphone, speaker, camera, contacts and calls.', style: sub),
        ),
      const SizedBox(height: 8),
      Row(children: [
        Expanded(child: NeonButton('Save', icon: Icons.save_outlined, onTap: _save)),
        if (!_isNew && !d.isThisPhone) ...[
          const SizedBox(width: 10),
          Expanded(
            child: NeonButton('Test', icon: Icons.wifi_tethering, onTap: () async {
              d.url = _url.text.trim();
              d.token = _token.text.trim();
              final r = await devices.refreshOne(d);
              if (context.mounted) toast(context, r.online ? '${d.name} is online' : '${d.name} is not reachable');
              setState(() {});
            }),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: NeonButton('Remove', icon: Icons.delete_outline, color: C.red, onTap: () {
              store.devices.remove(d);
              store.saveDevices();
              Shell.of(context).pop();
            }),
          ),
        ],
      ]),
      if (!_isNew && !d.isThisPhone) ...[
        const SizedBox(height: 18),
        Text('Command ${d.name}', style: h1(16)),
        const SizedBox(height: 8),
        TextField(
          controller: _cmd,
          style: const TextStyle(color: Colors.white),
          onSubmitted: (_) => _send(),
          decoration: field('e.g. open chrome, take a screenshot…',
              suffix: _sending
                  ? const Padding(padding: EdgeInsets.all(12), child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)))
                  : IconButton(icon: Icon(Icons.send, color: C.accent), onPressed: _send)),
        ),
        const SizedBox(height: 8),
        for (final l in _log)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 3),
            child: SelectableText(l, style: TextStyle(color: l.startsWith('›') ? C.accent : Colors.white, fontSize: 13)),
          ),
      ],
      if (_isNew)
        const Padding(
          padding: EdgeInsets.only(top: 14),
          child: Text(
              'The device needs a small JARVIS agent that answers GET /status and POST /command '
              '(see DEVICE_PROTOCOL.md in the project). Your laptop JARVIS server already speaks /command.',
              style: sub),
        ),
    ]);
  }
}
