import 'package:flutter/services.dart';
import 'package:flutter_contacts/flutter_contacts.dart';
import 'package:flutter_phone_direct_caller/flutter_phone_direct_caller.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:url_launcher/url_launcher.dart';

/// Real phone actions: calls, SMS, WhatsApp, opening apps, contacts.
class PhoneActions {
  static List<Contact>? _cache;
  static const _device = MethodChannel('jarvis/device'); // MainActivity.kt

  static Future<bool> autoSendEnabled() async => await _device.invokeMethod<bool>('accessibilityEnabled') ?? false;
  static Future<void> openAutoSendSettings() => _device.invokeMethod('openAccessibilitySettings');

  /// Opens an installed app by (approximate) name, e.g. "insta", "Google Pay", "camera".
  static Future<String> openApp(String name) async {
    final apps = List<Map>.from(await _device.invokeMethod('listApps'));
    final q = name.toLowerCase().trim();
    String label(Map a) => '${a['name']}'.toLowerCase();
    final hit = apps.where((a) => label(a) == q).firstOrNull ??
        apps.where((a) => label(a).startsWith(q)).firstOrNull ??
        apps.where((a) => label(a).contains(q) || q.contains(label(a))).firstOrNull ??
        apps.where((a) => '${a['package']}'.toLowerCase().contains(q.replaceAll(' ', ''))).firstOrNull;
    if (hit == null) throw Exception('No app named "$name" is installed');
    await _device.invokeMethod('openApp', {'package': hit['package']});
    return 'Opened ${hit['name']}';
  }

  static Future<List<Contact>> contacts() async {
    if (_cache != null) return _cache!;
    if (!await Permission.contacts.request().isGranted) throw Exception('Contacts permission denied');
    final all = await FlutterContacts.getAll(properties: {ContactProperty.phone});
    return _cache = all.where((c) => c.phones.isNotEmpty).toList()
      ..sort((a, b) => (a.displayName ?? '').compareTo(b.displayName ?? ''));
  }

  /// A phone number from either a number or a contact name ("Mom", "rahul sharma").
  static Future<({String name, String number})> resolve(String target) async {
    final digits = target.replaceAll(RegExp(r'[^0-9+]'), '');
    if (digits.length >= 3 && digits.length >= target.replaceAll(' ', '').length - 1) return (name: target, number: digits);
    final q = target.toLowerCase().trim();
    final list = await contacts();
    Contact? hit = list.where((c) => (c.displayName ?? '').toLowerCase() == q).firstOrNull ??
        list.where((c) => (c.displayName ?? '').toLowerCase().startsWith(q)).firstOrNull ??
        list.where((c) => (c.displayName ?? '').toLowerCase().contains(q)).firstOrNull ??
        list.where((c) => q.split(' ').every((w) => (c.displayName ?? '').toLowerCase().contains(w))).firstOrNull;
    if (hit == null) throw Exception('No contact named "$target"');
    return (name: hit.displayName ?? target, number: hit.phones.first.number);
  }

  static Future<String> call(String target) async {
    final c = await resolve(target);
    if (await Permission.phone.request().isGranted && (await FlutterPhoneDirectCaller.callNumber(c.number) ?? false)) {
      return 'Calling ${c.name} (${c.number})';
    }
    await launchUrl(Uri(scheme: 'tel', path: c.number));
    return 'Opened dialer for ${c.name} (${c.number})';
  }

  static Future<String> sms(String target, String text) async {
    final c = await resolve(target);
    if (text.isNotEmpty && await Permission.sms.request().isGranted) {
      await _device.invokeMethod('sendSms', {'number': c.number, 'text': text});
      return 'SMS sent to ${c.name}';
    }
    await launchUrl(Uri(scheme: 'sms', path: c.number, queryParameters: {'body': text}));
    return 'SMS to ${c.name} ready to send';
  }

  static Future<String> whatsapp(String target, String text) async {
    final c = await resolve(target);
    var n = c.number.replaceAll(RegExp(r'[^0-9]'), '');
    if (n.length == 10) n = '91$n'; // shortcut: assumes Indian numbers when no country code is saved
    final auto = text.isNotEmpty && await autoSendEnabled();
    if (auto) await _device.invokeMethod('armWhatsAppSend');
    await launchUrl(Uri.parse('https://wa.me/$n?text=${Uri.encodeComponent(text)}'), mode: LaunchMode.externalApplication);
    return auto
        ? 'WhatsApp message sent to ${c.name}'
        : 'WhatsApp message to ${c.name} is ready; press send (turn on JARVIS auto-send in Privacy & Security to skip this)';
  }
}
