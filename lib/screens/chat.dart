import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../services/jarvis.dart';
import '../services/store.dart';
import '../services/voice.dart';
import '../ui/theme.dart';
import 'home.dart';

/// Full conversation with JARVIS.
class ChatPage extends StatelessWidget {
  const ChatPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Column(children: [
      PageTitle('Conversation', 'Everything you and JARVIS said.',
          trailing: IconButton(
              tooltip: 'New conversation',
              icon: Icon(Icons.add_comment_outlined, color: C.accent),
              onPressed: jarvis.newConversation)),
      Expanded(
        child: ListenableBuilder(
          listenable: jarvis,
          builder: (_, __) {
            final msgs = store.chat.reversed.toList();
            return ListView.builder(
              reverse: true,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              itemCount: msgs.length + (jarvis.busy ? 1 : 0),
              itemBuilder: (_, i) {
                if (jarvis.busy && i == 0) {
                  return Padding(
                      padding: const EdgeInsets.all(10), child: Text(jarvis.status, style: TextStyle(color: C.accent)));
                }
                return _Bubble(msgs[i - (jarvis.busy ? 1 : 0)]);
              },
            );
          },
        ),
      ),
      const Padding(padding: EdgeInsets.fromLTRB(12, 4, 12, 6), child: JarvisInput()),
    ]);
  }
}

class _Bubble extends StatelessWidget {
  final Msg m;
  const _Bubble(this.m);
  @override
  Widget build(BuildContext context) {
    final me = m.role == 'user';
    return Align(
      alignment: me ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.82),
        margin: const EdgeInsets.symmetric(vertical: 5),
        child: Glass(
          radius: 14,
          border: me ? C.accent.withValues(alpha: 0.7) : null,
          gradient: me ? LinearGradient(colors: [C.accent.withValues(alpha: 0.25), C.cardBottom]) : null,
          padding: const EdgeInsets.all(12),
          onTap: me ? null : () => voice.speak(m.text),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            if (m.imagePath != null && File(m.imagePath!).existsSync())
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: ClipRRect(borderRadius: BorderRadius.circular(8), child: Image.file(File(m.imagePath!), height: 160)),
              ),
            if (m.imageUrl != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: GestureDetector(
                  onTap: () => openLink(m.imageUrl!),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: Image.network(m.imageUrl!,
                        loadingBuilder: (_, w, p) => p == null
                            ? w
                            : const SizedBox(height: 200, child: Center(child: CircularProgressIndicator(strokeWidth: 2)))),
                  ),
                ),
              ),
            SelectableText(m.text, style: const TextStyle(color: Colors.white, fontSize: 14.5, height: 1.35)),
            const SizedBox(height: 4),
            Row(mainAxisSize: MainAxisSize.min, children: [
              Text(timeAgo(m.time), style: const TextStyle(color: C.text2, fontSize: 11)),
              if (!me) ...[
                const SizedBox(width: 10),
                GestureDetector(
                  onTap: () {
                    Clipboard.setData(ClipboardData(text: m.text));
                    toast(context, 'Copied');
                  },
                  child: const Icon(Icons.copy, size: 14, color: C.text2),
                ),
              ],
            ]),
          ]),
        ),
      ),
    );
  }
}
