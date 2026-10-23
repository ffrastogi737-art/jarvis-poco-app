import 'package:flutter/material.dart';

import '../services/notify.dart';
import '../services/store.dart';
import '../ui/theme.dart';

/// Tasks with due times. JARVIS calls you when a task is due.
class TasksScreen extends StatelessWidget {
  const TasksScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: store,
      builder: (context, _) {
        final open = store.tasks.where((t) => !t.done).toList()
          ..sort((a, b) => (a.due ?? DateTime(9999)).compareTo(b.due ?? DateTime(9999)));
        final done = store.tasks.where((t) => t.done).toList();
        return ListView(padding: const EdgeInsets.fromLTRB(14, 0, 14, 16), children: [
          PageTitle('Tasks', 'JARVIS calls you when a task is due.',
              trailing: CircleBtn(Icons.add, () => editTask(context), size: 46)),
          if (open.isEmpty) const Padding(padding: EdgeInsets.all(20), child: Center(child: Text('No pending tasks, sir.', style: sub))),
          for (final t in open) _tile(context, t),
          if (done.isNotEmpty) ...[
            Padding(
              padding: const EdgeInsets.fromLTRB(4, 18, 4, 8),
              child: Row(children: [
                const Expanded(child: Text('Completed', style: TextStyle(color: C.text2))),
                TextButton(
                    onPressed: () {
                      store.tasks.removeWhere((t) => t.done);
                      store.saveTasks();
                    },
                    child: const Text('Clear')),
              ]),
            ),
            for (final t in done) _tile(context, t),
          ],
        ]);
      },
    );
  }

  Widget _tile(BuildContext context, TaskItem t) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Dismissible(
          key: ValueKey(t.id),
          onDismissed: (_) {
            store.tasks.remove(t);
            store.saveTasks();
            Notify.cancel(t.id);
          },
          background: Container(
              alignment: Alignment.centerRight,
              padding: const EdgeInsets.only(right: 20),
              child: const Icon(Icons.delete_outline, color: C.red)),
          child: Glass(
            onTap: () => editTask(context, t),
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
            child: Row(children: [
              Checkbox(
                value: t.done,
                activeColor: C.accent,
                side: const BorderSide(color: Colors.white),
                onChanged: (v) {
                  t.done = v!;
                  store.saveTasks();
                  v ? Notify.cancel(t.id) : (t.due != null ? Notify.scheduleCall(t.id, t.due!, 'Sir, a reminder: "${t.title}" is due now.') : null);
                },
              ),
              Expanded(
                child: Text(t.title,
                    style: TextStyle(
                        color: t.done ? C.text2 : Colors.white,
                        fontSize: 14.5,
                        decoration: t.done ? TextDecoration.lineThrough : null)),
              ),
              if (t.due != null)
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: Text(friendlyDue(t.due!),
                      style: TextStyle(color: t.due!.isBefore(DateTime.now()) && !t.done ? C.red : const Color(0xFFD0DCE8), fontSize: 12)),
                ),
            ]),
          ),
        ),
      );
}

Future<void> editTask(BuildContext context, [TaskItem? task]) async {
  final title = TextEditingController(text: task?.title);
  DateTime? due = task?.due;
  final ok = await showDialog<bool>(
    context: context,
    builder: (c) => StatefulBuilder(
      builder: (c, set) => AlertDialog(
        backgroundColor: C.cardTop,
        title: Text(task == null ? 'New task' : 'Edit task', style: const TextStyle(color: Colors.white)),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(controller: title, autofocus: true, style: const TextStyle(color: Colors.white), decoration: field('What needs doing?')),
          const SizedBox(height: 10),
          NeonButton(due == null ? 'Set due time (JARVIS will call)' : friendlyDue(due!), icon: Icons.alarm, onTap: () async {
            final now = DateTime.now();
            final d = await showDatePicker(
                context: c, initialDate: due ?? now, firstDate: now.subtract(const Duration(days: 1)), lastDate: DateTime(now.year + 5));
            if (d == null || !c.mounted) return;
            final t = await showTimePicker(context: c, initialTime: TimeOfDay.fromDateTime(due ?? now.add(const Duration(hours: 1))));
            if (t == null) return;
            set(() => due = DateTime(d.year, d.month, d.day, t.hour, t.minute));
          }),
          if (due != null) TextButton(onPressed: () => set(() => due = null), child: const Text('Remove due time')),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Save')),
        ],
      ),
    ),
  );
  if (ok != true || title.text.trim().isEmpty) return;
  final t = task ?? TaskItem(title: '');
  t
    ..title = title.text.trim()
    ..due = due;
  if (task == null) store.tasks.add(t);
  store.saveTasks();
  await Notify.cancel(t.id);
  if (due != null && !t.done) await Notify.scheduleCall(t.id, due!, 'Sir, a reminder: "${t.title}" is due now.');
}
