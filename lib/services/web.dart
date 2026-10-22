import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:youtube_explode_dart/youtube_explode_dart.dart';

import 'store.dart';

/// Free, key-less internet services: web search, news, weather, YouTube, image generation.
class SearchResult {
  final String title, url, snippet;
  SearchResult(this.title, this.url, this.snippet);
  @override
  String toString() => '$title — $snippet ($url)';
}

class NewsItem {
  final String title, link, source;
  final DateTime? date;
  NewsItem(this.title, this.link, this.source, this.date);
}

class Weather {
  final String city, summary;
  final double temp, high, low;
  final int code;
  Weather(this.city, this.temp, this.high, this.low, this.code, this.summary);
  @override
  String toString() => '$city: ${temp.round()}°C, $summary, high ${high.round()}°C, low ${low.round()}°C';
}

const _ua = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36';

String _unescape(String s) => s
    .replaceAll(RegExp(r'<[^>]+>'), '')
    .replaceAll('&amp;', '&')
    .replaceAll('&quot;', '"')
    .replaceAll('&#x27;', "'")
    .replaceAll('&#39;', "'")
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll(RegExp(r'\s+'), ' ')
    .trim();

class Web {
  static Future<List<SearchResult>> search(String q) async {
    try {
      final res = await http
          .get(Uri.parse('https://lite.duckduckgo.com/lite/?q=${Uri.encodeQueryComponent(q)}'), headers: {'User-Agent': _ua})
          .timeout(const Duration(seconds: 12));
      final links = RegExp(r'''href="([^"]+)" class='result-link'>(.*?)</a>''').allMatches(res.body).toList();
      final snippets = RegExp(r"class='result-snippet'>(.*?)</td>", dotAll: true).allMatches(res.body).toList();
      final out = <SearchResult>[];
      for (var i = 0; i < links.length && out.length < 8; i++) {
        var url = links[i].group(1)!.replaceAll('&amp;', '&');
        final real = Uri.tryParse(url.startsWith('//') ? 'https:$url' : url)?.queryParameters['uddg'];
        if (real != null) url = real;
        if (url.contains('duckduckgo.com/y.js')) continue; // ads
        out.add(SearchResult(_unescape(links[i].group(2)!), url, i < snippets.length ? _unescape(snippets[i].group(1)!) : ''));
      }
      if (out.isNotEmpty) return out;
    } catch (_) {}
    // Fallback: Wikipedia full-text search.
    final res = await http.get(Uri.parse(
        'https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=6&srsearch=${Uri.encodeQueryComponent(q)}'));
    final hits = jsonDecode(res.body)['query']['search'] as List;
    return [
      for (final h in hits)
        SearchResult(h['title'], 'https://en.wikipedia.org/wiki/${Uri.encodeComponent(h['title'].replaceAll(' ', '_'))}',
            _unescape(h['snippet']))
    ];
  }

  static Future<List<NewsItem>> news([String topic = '']) async {
    final base = topic.trim().isEmpty
        ? 'https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en'
        : 'https://news.google.com/rss/search?q=${Uri.encodeQueryComponent(topic)}&hl=en-IN&gl=IN&ceid=IN:en';
    final res = await http.get(Uri.parse(base), headers: {'User-Agent': _ua}).timeout(const Duration(seconds: 12));
    final body = utf8.decode(res.bodyBytes);
    String tag(String item, String t) =>
        _unescape((RegExp('<$t[^>]*>(.*?)</$t>', dotAll: true).firstMatch(item)?.group(1) ?? '').replaceAll(RegExp(r'<!\[CDATA\[|\]\]>'), ''));
    return [
      for (final m in RegExp(r'<item>(.*?)</item>', dotAll: true).allMatches(body).take(10))
        () {
          final item = m.group(1)!;
          final source = tag(item, 'source');
          var title = tag(item, 'title');
          if (source.isNotEmpty && title.endsWith(' - $source')) title = title.substring(0, title.length - source.length - 3);
          return NewsItem(title, tag(item, 'link'), source, _parseRfc822(tag(item, 'pubDate')));
        }()
    ];
  }

  static DateTime? _parseRfc822(String s) {
    final m = RegExp(r'(\d{1,2}) (\w{3}) (\d{4}) (\d{2}):(\d{2}):(\d{2})').firstMatch(s);
    if (m == null) return null;
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    return DateTime.utc(int.parse(m[3]!), months.indexOf(m[2]!) + 1, int.parse(m[1]!), int.parse(m[4]!), int.parse(m[5]!),
            int.parse(m[6]!))
        .toLocal();
  }

  static Future<Weather> weather([String? city]) async {
    city = (city == null || city.trim().isEmpty) ? store.get<String>('city') : city;
    double lat, lon;
    String name;
    if (city.trim().isNotEmpty) {
      final g = jsonDecode((await http.get(Uri.parse(
              'https://geocoding-api.open-meteo.com/v1/search?count=1&name=${Uri.encodeQueryComponent(city)}')))
          .body)['results'];
      if (g == null) throw Exception('City "$city" not found');
      lat = g[0]['latitude'];
      lon = g[0]['longitude'];
      name = g[0]['name'];
    } else {
      final ip = jsonDecode((await http.get(Uri.parse('https://ipwho.is/'))).body);
      lat = (ip['latitude'] as num).toDouble();
      lon = (ip['longitude'] as num).toDouble();
      name = ip['city'] ?? 'your area';
    }
    final w = jsonDecode((await http.get(Uri.parse('https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon'
            '&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1')))
        .body);
    final code = w['current']['weather_code'] as int;
    return Weather(name, (w['current']['temperature_2m'] as num).toDouble(), (w['daily']['temperature_2m_max'][0] as num).toDouble(),
        (w['daily']['temperature_2m_min'][0] as num).toDouble(), code, weatherText(code));
  }

  static String weatherText(int c) => switch (c) {
        0 => 'Clear Sky',
        1 || 2 => 'Partly Cloudy',
        3 => 'Overcast',
        45 || 48 => 'Foggy',
        >= 51 && <= 57 => 'Drizzle',
        >= 61 && <= 67 => 'Rain',
        >= 71 && <= 77 => 'Snow',
        >= 80 && <= 82 => 'Rain Showers',
        >= 95 => 'Thunderstorm',
        _ => 'Cloudy',
      };

  static String imageUrl(String prompt, {int seed = 0, int size = 1024}) =>
      'https://image.pollinations.ai/prompt/${Uri.encodeComponent(prompt)}?width=$size&height=$size&nologo=true&seed=$seed';

  // ---- YouTube ----
  static Future<List<Video>> youtubeSearch(String q) async {
    final yt = YoutubeExplode();
    try {
      return (await yt.search.search(q)).take(12).toList();
    } finally {
      yt.close();
    }
  }

  /// Transcript text (or description when captions are unavailable) for summarising.
  static Future<({String title, String text, String url})> youtubeText(String queryOrUrl) async {
    final yt = YoutubeExplode();
    try {
      final id = VideoId.parseVideoId(queryOrUrl);
      final video = id != null ? await yt.videos.get(id) : (await yt.search.search(queryOrUrl)).first;
      final url = 'https://www.youtube.com/watch?v=${video.id.value}';
      try {
        final manifest = await yt.videos.closedCaptions.getManifest(video.id);
        if (manifest.tracks.isNotEmpty) {
          final track = manifest.tracks.firstWhere((t) => t.language.code.startsWith('en'), orElse: () => manifest.tracks.first);
          final cc = await yt.videos.closedCaptions.get(track);
          final text = cc.captions.map((c) => c.text).join(' ');
          return (title: video.title, text: text.length > 24000 ? text.substring(0, 24000) : text, url: url);
        }
      } catch (_) {}
      return (title: video.title, text: 'No transcript. Channel: ${video.author}. Description: ${video.description}', url: url);
    } finally {
      yt.close();
    }
  }
}
