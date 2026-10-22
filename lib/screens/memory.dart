import 'package:flutter/material.dart';

import '../services/store.dart';
import '../ui/theme.dart';

/// Long-term memory: facts JARVIS knows about you and uses in every answer.
class MemoryScreen extends StatelessWidget {
  const MemoryScreen({super.key});

  Future<void> _edit(BuildContext context, [int? i]) async {
    final c = TextEditingController(text: i == null ? '' : store.memory[i]);
    final ok = await showDialog<bool>(
      context: context,
      builder: (d) => AlertDialog(
        backgroundColor: C.cardTop,
        title: Text(i == null ? 'Teach JARVIS' : 'Edit memory', style: const TextStyle(color: Colors.white)),
        content: TextField(
            controller: c,
            autofocus: true,
            maxLines: 3,
            style: const TextStyle(color: Colors.white),
            decoration: field('e.g. Shri Radha Krishna Jewellers is my business')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(d, false), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(d, true), child: const Text('Save')),
        ],
      ),
    );
    if (ok != true || c.text.trim().isEmpty) return;
    i == null ? store.memory.add(c.text.trim()) : store.memory[i] = c.text.trim();
    store.saveMemory();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: store,
      builder: (context, _) => ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
        PageTitle('Memory', 'What JARVIS knows about you.', trailing: CircleBtn(Icons.add, () => _edit(context), size: 46)),
        if (store.memory.isEmpty)
          const Padding(
            padding: EdgeInsets.all(20),
            child: Text(
                'Nothing yet. Tell JARVIS things like "remember that my sister\'s name is Priya" or add them here.',
                style: sub,
                textAlign: TextAlign.center),
          ),
        for (var i = 0; i < store.memory.length; i++)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Glass(
              onTap: () => _edit(context, i),
              padding: const EdgeInsets.fromLTRB(14, 6, 4, 6),
              child: Row(children: [
                NeonIcon(Icons.psychology_outlined, size: 22),
                const SizedBox(width: 12),
                Expanded(child: Text(store.memory[i], style: const TextStyle(color: Colors.white, fontSize: 14))),
                IconButton(
                    icon: const Icon(Icons.delete_outline, color: C.text2),
                    onPressed: () {
                      store.memory.removeAt(i);
                      store.saveMemory();
                    }),
              ]),
            ),
          ),
      ]),
    );
  }
}
