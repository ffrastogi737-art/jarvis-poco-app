import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../services/brain.dart';
import 'theme.dart';

/// Lets the user attach a photo, video or document for JARVIS to analyse.
Future<Attachment?> pickAttachment(BuildContext context, {String? kind}) async {
  kind ??= await showModalBottomSheet<String>(
    context: context,
    backgroundColor: C.cardTop,
    builder: (c) => SafeArea(
      child: Wrap(children: [
        for (final o in const [
          ('camera', Icons.photo_camera_outlined, 'Take photo'),
          ('gallery', Icons.photo_library_outlined, 'Photo from gallery'),
          ('video', Icons.videocam_outlined, 'Video'),
          ('document', Icons.description_outlined, 'Document (PDF, text, CSV)'),
        ])
          ListTile(
              leading: NeonIcon(o.$2, size: 24),
              title: Text(o.$3, style: const TextStyle(color: Colors.white)),
              onTap: () => Navigator.pop(c, o.$1)),
      ]),
    ),
  );
  if (kind == null) return null;
  final picker = ImagePicker();
  switch (kind) {
    case 'camera':
    case 'gallery':
      final x = await picker.pickImage(
          source: kind == 'camera' ? ImageSource.camera : ImageSource.gallery, maxWidth: 1600, imageQuality: 82);
      if (x == null) return null;
      return Attachment(await x.readAsBytes(), 'image/jpeg', x.name, path: x.path);
    case 'video':
      final x = await picker.pickVideo(source: ImageSource.gallery, maxDuration: const Duration(minutes: 2));
      if (x == null) return null;
      final bytes = await x.readAsBytes();
      // Gemini accepts inline files up to ~20 MB.
      if (bytes.length > 19 * 1024 * 1024) {
        if (context.mounted) toast(context, 'Video too large (max ~19 MB). Please trim it.');
        return null;
      }
      return Attachment(bytes, 'video/mp4', x.name);
    case 'document':
      final files = await FilePicker.pickFiles(type: FileType.custom, allowedExtensions: ['pdf', 'txt', 'csv', 'md', 'json', 'html']);
      if (files.isEmpty) return null;
      final f = files.first;
      final ext = (f.extension ?? '').toLowerCase().replaceAll('.', '');
      final mime = switch (ext) {
        'pdf' => 'application/pdf',
        'csv' => 'text/csv',
        'html' => 'text/html',
        'md' => 'text/md',
        'json' => 'application/json',
        _ => 'text/plain',
      };
      return Attachment(await f.xFile.readAsBytes(), mime, f.name);
  }
  return null;
}
