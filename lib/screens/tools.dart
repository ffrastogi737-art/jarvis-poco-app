import 'package:flutter/material.dart';

import '../ui/shell.dart';
import '../ui/theme.dart';
import 'tool_pages.dart';

export 'tool_pages.dart';

class ToolsScreen extends StatelessWidget {
  const ToolsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final shell = Shell.of(context);
    final tools = <(Widget, String, String, VoidCallback)>[
      (NeonIcon(Icons.search, size: 34), 'Search', 'Real-time web search', () => shell.push(const SearchPage())),
      (NeonIcon(Icons.photo_camera_outlined, size: 34), 'Vision', 'Analyze images, text and videos', () => shell.push(const VisionPage())),
      (NeonIcon(Icons.call_outlined, size: 34), 'Calling', 'Make & manage calls', () => shell.push(const CallingPage())),
      (const _YtIcon(), 'YouTube', 'Play, search & summarize videos', () => shell.push(const YouTubePage())),
      (NeonIcon(Icons.check_box_outlined, size: 34), 'Tasks', 'Create & manage tasks', () => shell.goTab(1)),
      (NeonIcon(Icons.settings_outlined, size: 34), 'Automation', 'Set routines & workflows', () => shell.push(const AutomationPage())),
      (NeonIcon(Icons.description_outlined, size: 34), 'Notes', 'Write, summarize and organize', () => shell.push(const NotesPage())),
      (NeonIcon(Icons.code, size: 34), 'Code', 'Generate & debug code', () => shell.push(const CodePage())),
    ];
    return ListView(padding: const EdgeInsets.fromLTRB(12, 0, 12, 12), children: [
      PageTitle('Tools', 'Powerful tools at your command.',
          trailing: CircleBtn(Icons.search, () => shell.push(const SearchPage()), size: 46)),
      GridView.count(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        crossAxisCount: 2,
        mainAxisSpacing: 10,
        crossAxisSpacing: 10,
        childAspectRatio: 1.48,
        children: [
          for (final t in tools)
            Glass(
              onTap: t.$4,
              padding: const EdgeInsets.fromLTRB(12, 12, 10, 8),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                t.$1,
                const Spacer(),
                Text(t.$2, style: const TextStyle(color: Colors.white, fontSize: 16)),
                Text(t.$3, style: const TextStyle(color: Color(0xFFC8D6E3), fontSize: 12), maxLines: 2, overflow: TextOverflow.ellipsis),
              ]),
            ),
        ],
      ),
      const SizedBox(height: 10),
      Row(children: [
        Expanded(child: _rowTile(Icons.image_outlined, 'Create', 'Images, ideas, content', () => shell.push(const CreatePage()))),
        const SizedBox(width: 10),
        Expanded(child: _rowTile(Icons.grid_view_outlined, 'More Tools', 'Explore all tools', () => shell.push(const MoreToolsPage()))),
      ]),
    ]);
  }

  Widget _rowTile(IconData icon, String title, String subtitle, VoidCallback onTap) => Glass(
        onTap: onTap,
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 14),
        child: Row(children: [
          NeonIcon(icon, size: 34),
          const SizedBox(width: 10),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(title, style: const TextStyle(color: Colors.white, fontSize: 15.5)),
              Text(subtitle, style: const TextStyle(color: Color(0xFFC8D6E3), fontSize: 10.5), maxLines: 1, overflow: TextOverflow.ellipsis),
            ]),
          ),
        ]),
      );
}

class _YtIcon extends StatelessWidget {
  const _YtIcon();
  @override
  Widget build(BuildContext context) => Container(
        width: 34,
        height: 30,
        decoration: BoxDecoration(
          color: C.youtube,
          borderRadius: BorderRadius.circular(7),
          boxShadow: [BoxShadow(color: C.youtube.withValues(alpha: 0.6), blurRadius: 10)],
        ),
        child: const Icon(Icons.play_arrow_outlined, color: Colors.white, size: 24),
      );
}
