import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import '../services/brain.dart';
import '../services/jarvis.dart';
import '../services/notify.dart';
import '../services/store.dart';
import '../services/voice.dart';
import '../ui/shell.dart';
import '../ui/theme.dart';
import 'devices.dart';
import 'home.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final shell = Shell.of(context);
    final items = <(Widget, String, String, Widget)>[
      (
        Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(shape: BoxShape.circle, gradient: LinearGradient(colors: [C.accent, const Color(0xFF0B5C9C)])),
          child: const Icon(Icons.person, color: Colors.white, size: 30),
        ),
        'Your Profile',
        'Manage your account',
        const ProfilePage()
      ),
      (NeonIcon(Icons.mic_none_rounded), 'Voice & Language', 'Choose voice, language and style', const VoicePage()),
      (NeonIcon(Icons.desktop_windows_outlined), 'Connected Devices', 'Manage your devices', const DevicesScreen()),
      (NeonIcon(Icons.storage_rounded), 'Memory', 'Personalization & saved info', const _MemoryLink()),
      (NeonIcon(Icons.shield_outlined), 'Privacy & Security', 'Data, permissions and security', const PrivacyPage()),
      (NeonIcon(Icons.palette_outlined), 'Appearance', 'Themes, colors and animations', const AppearancePage()),
      (NeonIcon(Icons.settings_outlined), 'Advanced', 'Experimental features', const AdvancedSettings()),
      (NeonIcon(Icons.settings_suggest_outlined), 'About', 'Version, updates and help', const AboutPage()),
    ];
    return ListView(padding: const EdgeInsets.fromLTRB(12, 0, 12, 12), children: [
      const PageTitle('Settings', 'Customize your JARVIS experience.'),
      for (final i in items)
        Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Glass(
            onTap: () => i.$4 is _MemoryLink ? shell.goTab(3) : shell.push(i.$4),
            padding: const EdgeInsets.fromLTRB(12, 9, 8, 9),
            child: Row(children: [
              SizedBox(width: 40, child: Center(child: i.$1)),
              const SizedBox(width: 14),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(i.$2, style: const TextStyle(color: Colors.white, fontSize: 15.5)),
                  const SizedBox(height: 2),
                  Text(i.$3, style: const TextStyle(color: Color(0xFFD0DCE8), fontSize: 12.5)),
                ]),
              ),
              const Icon(Icons.chevron_right, color: Colors.white),
            ]),
          ),
        ),
    ]);
  }
}

class _MemoryLink extends StatelessWidget {
  const _MemoryLink();
  @override
  Widget build(BuildContext context) => const SizedBox();
}

Widget _section(String t) => Padding(padding: const EdgeInsets.fromLTRB(4, 16, 4, 6), child: Text(t, style: TextStyle(color: C.accent, fontSize: 13)));

SwitchListTile _switch(String title, String subtitle, String key, {VoidCallback? after}) => SwitchListTile(
      value: store.get<bool>(key),
      activeThumbColor: C.accent,
      contentPadding: const EdgeInsets.symmetric(horizontal: 4),
      title: Text(title, style: const TextStyle(color: Colors.white)),
      subtitle: Text(subtitle, style: sub),
      onChanged: (v) {
        store.set(key, v);
        after?.call();
      },
    );

class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});
  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  final _name = TextEditingController(text: store.get<String>('name'));
  final _city = TextEditingController(text: store.get<String>('city'));

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        const PageTitle('Your Profile', 'How JARVIS knows you.'),
        TextField(controller: _name, style: const TextStyle(color: Colors.white), decoration: field('Your name')),
        const SizedBox(height: 10),
        TextField(controller: _city, style: const TextStyle(color: Colors.white), decoration: field('Your city (for weather; blank = auto)')),
        const SizedBox(height: 12),
        NeonButton('Save', icon: Icons.save_outlined, onTap: () {
          store.set('name', _name.text.trim().isEmpty ? 'Sir' : _name.text.trim());
          store.set('city', _city.text.trim());
          toast(context, 'Profile saved');
        }),
        const SizedBox(height: 16),
        Text('${store.memory.length} memories • ${store.notes.length} notes • ${store.tasks.where((t) => !t.done).length} open tasks', style: sub),
      ]);
}

class VoicePage extends StatefulWidget {
  const VoicePage({super.key});
  @override
  State<VoicePage> createState() => _VoicePageState();
}

class _VoicePageState extends State<VoicePage> {
  static const en = {
    'en-GB-RyanNeural': 'Ryan — British (JARVIS)',
    'en-GB-ThomasNeural': 'Thomas — British',
    'en-US-GuyNeural': 'Guy — American',
    'en-US-AndrewNeural': 'Andrew — American',
    'en-IN-PrabhatNeural': 'Prabhat — Indian English',
    'en-GB-SoniaNeural': 'Sonia — British (female)',
  };
  static const hi = {'hi-IN-MadhurNeural': 'Madhur — Hindi (male)', 'hi-IN-SwaraNeural': 'Swara — Hindi (female)'};
  static const stt = {'en_IN': 'English (India) / Hinglish', 'en_US': 'English (US)', 'en_GB': 'English (UK)', 'hi_IN': 'हिन्दी (Hindi)'};

  Widget _drop(String label, String key, Map<String, String> options) => Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: DropdownButtonFormField<String>(
          initialValue: options.containsKey(store.get<String>(key)) ? store.get<String>(key) : options.keys.first,
          dropdownColor: C.cardTop,
          style: const TextStyle(color: Colors.white),
          decoration: field(label).copyWith(labelText: label, labelStyle: sub),
          items: [for (final e in options.entries) DropdownMenuItem(value: e.key, child: Text(e.value))],
          onChanged: (v) => setState(() => store.set(key, v)),
        ),
      );

  Widget _slider(String label, String key, double min, double max, String unit) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('$label: ${store.get<int>(key)}$unit', style: const TextStyle(color: Colors.white)),
        Slider(
            value: store.get<int>(key).toDouble(),
            min: min,
            max: max,
            divisions: (max - min).round(),
            activeColor: C.accent,
            onChanged: (v) => setState(() => store.set(key, v.round()))),
      ]);

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        const PageTitle('Voice & Language', 'Choose voice, language and style.'),
        _switch('Neural JARVIS voice', 'Free Microsoft neural voice (needs internet). Off = phone voice.', 'neuralVoice', after: () => setState(() {})),
        _switch('Speak replies', 'JARVIS answers out loud', 'autoSpeak', after: () => setState(() {})),
        const SizedBox(height: 8),
        _drop('English voice', 'voiceEn', en),
        _drop('Hindi voice', 'voiceHi', hi),
        _drop('I speak in', 'sttLocale', stt),
        _slider('Speed', 'rate', -30, 30, '%'),
        _slider('Pitch', 'pitch', -20, 10, ' Hz'),
        Row(children: [
          Expanded(child: NeonButton('Test English', icon: Icons.volume_up, onTap: () => voice.speak('Good evening, sir. All systems are online and at your service.'))),
          const SizedBox(width: 8),
          Expanded(child: NeonButton('Test Hindi', icon: Icons.volume_up, onTap: () => voice.speak('नमस्ते सर, सभी सिस्टम ऑनलाइन हैं।'))),
        ]),
      ]);
}

class PrivacyPage extends StatefulWidget {
  const PrivacyPage({super.key});
  @override
  State<PrivacyPage> createState() => _PrivacyPageState();
}

class _PrivacyPageState extends State<PrivacyPage> {
  final _perms = {
    'Microphone': Permission.microphone,
    'Contacts': Permission.contacts,
    'Phone calls': Permission.phone,
    'Notifications': Permission.notification,
  };

  Future<bool> _sure(String q) async =>
      await showDialog<bool>(
          context: context,
          builder: (c) => AlertDialog(
                backgroundColor: C.cardTop,
                content: Text(q, style: const TextStyle(color: Colors.white)),
                actions: [
                  TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
                  TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Delete', style: TextStyle(color: C.red))),
                ],
              )) ??
      false;

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        const PageTitle('Privacy & Security', 'Data, permissions and security.'),
        _switch('Confirm before calling', 'JARVIS asks before placing phone calls', 'confirmActions', after: () => setState(() {})),
        _section('PERMISSIONS'),
        for (final p in _perms.entries)
          FutureBuilder<PermissionStatus>(
            future: p.value.status,
            builder: (_, s) => ListTile(
              contentPadding: const EdgeInsets.symmetric(horizontal: 4),
              title: Text(p.key, style: const TextStyle(color: Colors.white)),
              subtitle: Text(s.data?.isGranted == true ? 'Allowed' : 'Not allowed', style: TextStyle(color: s.data?.isGranted == true ? C.green : C.text2)),
              trailing: TextButton(
                  onPressed: () async {
                    final r = await p.value.request();
                    if (r.isPermanentlyDenied) await openAppSettings();
                    setState(() {});
                  },
                  child: const Text('Allow')),
            ),
          ),
        ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 4),
          title: const Text('JARVIS calls (alarms & full-screen)', style: TextStyle(color: Colors.white)),
          subtitle: const Text('Lets JARVIS ring you on time, even on the lock screen', style: sub),
          trailing: TextButton(onPressed: Notify.requestPermissions, child: const Text('Allow')),
        ),
        _section('YOUR DATA'),
        const Text('Everything (chats, memory, notes, tasks, keys) is stored only on this phone. '
            'Messages go to the AI service you choose to get answers.', style: sub),
        const SizedBox(height: 10),
        NeonButton('Clear conversation', icon: Icons.chat_bubble_outline, onTap: () async {
          if (await _sure('Delete the whole conversation?')) jarvis.newConversation();
        }),
        const SizedBox(height: 8),
        NeonButton('Clear memory', icon: Icons.psychology_outlined, onTap: () async {
          if (await _sure('Forget everything JARVIS remembers about you?')) {
            store.memory.clear();
            store.saveMemory();
          }
        }),
        const SizedBox(height: 8),
        NeonButton('Reset JARVIS (delete all data)', icon: Icons.delete_forever_outlined, color: C.red, onTap: () async {
          if (await _sure('Delete ALL data, settings and keys from this phone?')) await store.clearAll();
        }),
      ]);
}

class AppearancePage extends StatefulWidget {
  const AppearancePage({super.key});
  @override
  State<AppearancePage> createState() => _AppearancePageState();
}

class _AppearancePageState extends State<AppearancePage> {
  static const colors = {
    'Arc Blue': 0xFF19C6FF,
    'Deep Blue': 0xFF3D7BFF,
    'Iron Gold': 0xFFFFB627,
    'Hot Rod Red': 0xFFFF4D4D,
    'Vibranium Purple': 0xFFA35CFF,
    'Matrix Green': 0xFF2EE06B,
  };
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        const PageTitle('Appearance', 'Themes, colors and animations.'),
        _section('ACCENT COLOR'),
        Wrap(spacing: 10, runSpacing: 10, children: [
          for (final c in colors.entries)
            GestureDetector(
              onTap: () => setState(() => store.set('accent', c.value)),
              child: Column(children: [
                Container(
                  width: 52,
                  height: 52,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: Color(c.value),
                    border: Border.all(color: Colors.white, width: store.get<int>('accent') == c.value ? 3 : 0),
                    boxShadow: [BoxShadow(color: Color(c.value).withValues(alpha: 0.7), blurRadius: 12)],
                  ),
                ),
                const SizedBox(height: 4),
                SizedBox(width: 70, child: Text(c.key, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white, fontSize: 11))),
              ]),
            ),
        ]),
        _section('MOTION'),
        _switch('Animations', 'Animated arc reactor and waveforms', 'animations', after: () => setState(() {})),
      ]);
}

class AdvancedSettings extends StatefulWidget {
  const AdvancedSettings({super.key});
  @override
  State<AdvancedSettings> createState() => _AdvancedSettingsState();
}

class _AdvancedSettingsState extends State<AdvancedSettings> {
  final _key = TextEditingController(text: store.get<String>('geminiKey'));
  final _model = TextEditingController(text: store.get<String>('geminiModel'));
  bool _show = false, _testing = false;

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        const PageTitle('Advanced', 'Experimental features.'),
        _section('CLOUD BRAIN'),
        Text('Now using: ${Brain.providerName}', style: const TextStyle(color: Colors.white)),
        const SizedBox(height: 6),
        const Text(
            'Without a key JARVIS uses a free key-less model (text only). Add a FREE Google Gemini key for a smarter brain '
            'that can also see photos, videos, PDFs and watch YouTube.',
            style: sub),
        const SizedBox(height: 10),
        TextField(
          controller: _key,
          obscureText: !_show,
          style: const TextStyle(color: Colors.white),
          decoration: field('Gemini API key',
              suffix: IconButton(icon: Icon(_show ? Icons.visibility_off : Icons.visibility, color: C.text2), onPressed: () => setState(() => _show = !_show))),
        ),
        TextButton.icon(
            onPressed: () => openLink('https://aistudio.google.com/apikey'),
            icon: Icon(Icons.open_in_new, color: C.accent, size: 16),
            label: const Text('Get a free key at aistudio.google.com', style: TextStyle(color: Colors.white))),
        TextField(controller: _model, style: const TextStyle(color: Colors.white), decoration: field('Gemini model (default gemini-flash-latest)')),
        const SizedBox(height: 10),
        Row(children: [
          Expanded(
            child: NeonButton('Save', icon: Icons.save_outlined, onTap: () {
              store.set('geminiKey', _key.text.trim());
              store.set('geminiModel', _model.text.trim().isEmpty ? 'gemini-flash-latest' : _model.text.trim());
              setState(() {});
              toast(context, 'Saved');
            }),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: NeonButton(_testing ? 'Testing…' : 'Test brain', icon: Icons.bolt, onTap: () async {
              setState(() => _testing = true);
              try {
                final r = await Brain.ask('Reply with exactly: Systems online.');
                if (context.mounted) toast(context, '${Brain.providerName}: $r');
              } catch (e) {
                if (context.mounted) toast(context, '$e');
              }
              if (mounted) setState(() => _testing = false);
            }),
          ),
        ]),
        _section('DEVICES'),
        _switch('Listen to devices', 'While the app is open, check every 30 s if your laptop JARVIS wants to call you', 'pollDevices',
            after: () => setState(() {})),
      ]);
}

class AboutPage extends StatelessWidget {
  const AboutPage({super.key});
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        const PageTitle('About', 'Version, updates and help.'),
        Glass(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('J.A.R.V.I.S. for POCO X6', style: h1(18)),
            const SizedBox(height: 4),
            const Text('Version 1.0.0', style: sub),
            const SizedBox(height: 12),
            const Text(
                'Your personal AI system: phone interface, cloud brain, and connected devices.\n\n'
                'Free services used: Microsoft Edge neural voice, Google Gemini (optional key), Pollinations AI, '
                'DuckDuckGo & Wikipedia search, Google News, Open-Meteo weather, YouTube.\n\n'
                'Tips:\n• "Call Mom" / "Play lo-fi music on YouTube" / "Remind me to pay rent tomorrow at 10 am"\n'
                '• "Laptop par Chrome kholo" — sends the command to your laptop JARVIS\n'
                '• "Call me at 6 pm and remind me about the meeting"',
                style: TextStyle(color: Colors.white, height: 1.4)),
          ]),
        ),
        const SizedBox(height: 10),
        NeonButton('Project on GitHub', icon: Icons.open_in_new, onTap: () => openLink('https://github.com/ffrastogi737-art/jarvis-poco-app')),
      ]);
}
