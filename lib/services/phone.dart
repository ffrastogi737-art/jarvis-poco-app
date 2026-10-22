import 'package:flutter_contacts/flutter_contacts.dart';
import 'package:flutter_phone_direct_caller/flutter_phone_direct_caller.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:url_launcher/url_launcher.dart';

/// Real phone actions: calls, SMS, WhatsApp, contacts.
class PhoneActions {
  static List<Contact>? _cache;

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
    await launchUrl(Uri(scheme: 'sms', path: c.number, queryParameters: {'body': text}));
    return 'SMS to ${c.name} ready to send';
  }

  static Future<String> whatsapp(String target, String text) async {
    final c = await resolve(target);
    var n = c.number.replaceAll(RegExp(r'[^0-9]'), '');
    if (n.length == 10) n = '91$n'; // shortcut: assumes Indian numbers when no country code is saved
    await launchUrl(Uri.parse('https://wa.me/$n?text=${Uri.encodeComponent(text)}'), mode: LaunchMode.externalApplication);
    return 'WhatsApp message to ${c.name} ready to send';
  }
}
