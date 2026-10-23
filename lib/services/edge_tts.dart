// Free neural text-to-speech through Microsoft Edge's "Read Aloud" service.
// Port of the open-source python `edge-tts` client (v7.3.1). No API key needed.
// shortcut: Microsoft rotates the token scheme now and then; if voice falls back
// to the phone TTS, bump these constants from the latest edge-tts release.
import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

const _trustedToken = '6A5AA1D4EAFF4E9FB37E23D68491D6F4';
const _chromiumFull = '143.0.3650.75';
const _chromiumMajor = '143';
const _userAgent =
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$_chromiumMajor.0.0.0 Safari/537.36 Edg/$_chromiumMajor.0.0.0';
const _wssUrl =
    'wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=$_trustedToken';

class EdgeTts {
  /// Returns mp3 bytes for [text] spoken by [voice] (e.g. en-GB-RyanNeural).
  static Future<Uint8List> synthesize(String text,
      {String voice = 'en-GB-RyanNeural',
      String rate = '+0%',
      String pitch = '+0Hz'}) async {
    final ws = await WebSocket.connect(
      '$_wssUrl&ConnectionId=${_uuid()}&Sec-MS-GEC=${secMsGec()}&Sec-MS-GEC-Version=2-$_chromiumFull',
      headers: {
        'Pragma': 'no-cache',
        'Cache-Control': 'no-cache',
        'Origin': 'chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold',
        'Accept-Encoding': 'gzip, deflate, br, zstd',
        'Accept-Language': 'en-US,en;q=0.9',
        'Cookie': 'muid=${_uuid().toUpperCase()};',
      },
      // A custom client so the browser User-Agent replaces dart's instead of being added to it.
      customClient: HttpClient()..userAgent = _userAgent,
    ).timeout(const Duration(seconds: 10));
    final ts = _jsDate();
    ws.add('X-Timestamp:$ts\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n'
        '{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"true","wordBoundaryEnabled":"false"},'
        '"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}\r\n');
    ws.add('X-RequestId:${_uuid()}\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:${ts}Z\r\nPath:ssml\r\n\r\n'
        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>"
        "<voice name='$voice'><prosody pitch='$pitch' rate='$rate' volume='+0%'>"
        '${_escape(text)}</prosody></voice></speak>');
    final audio = BytesBuilder(copy: false);
    await for (final msg in ws.timeout(const Duration(seconds: 20))) {
      if (msg is String) {
        if (msg.contains('Path:turn.end')) break;
      } else if (msg is List<int>) {
        final headerLen = (msg[0] << 8) | msg[1];
        final header = latin1.decode(msg.sublist(2, 2 + headerLen));
        if (header.contains('Path:audio') && header.contains('audio/mpeg')) {
          audio.add(msg.sublist(2 + headerLen));
        }
      }
    }
    await ws.close();
    if (audio.isEmpty) throw Exception('No audio received');
    return audio.takeBytes();
  }

  static String _escape(String s) => s
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll("'", '&apos;')
      .replaceAll('"', '&quot;');

  static String _uuid() {
    final r = Random.secure();
    return List.generate(16, (_) => r.nextInt(256).toRadixString(16).padLeft(2, '0')).join();
  }

  static String _jsDate() {
    final d = DateTime.now().toUtc();
    const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    String p(int v) => v.toString().padLeft(2, '0');
    return '${days[d.weekday - 1]} ${months[d.month - 1]} ${p(d.day)} ${d.year} '
        '${p(d.hour)}:${p(d.minute)}:${p(d.second)} GMT+0000 (Coordinated Universal Time)';
  }

  // ---- Sec-MS-GEC v2 token (edge_tts/drm.py) ----
  static String secMsGec([int? unixSeconds]) {
    var secs = (unixSeconds ?? DateTime.now().millisecondsSinceEpoch ~/ 1000) + 11644473600;
    secs -= secs % 300;
    final data = ascii.encode('${secs * 10000000}$_trustedToken');
    final digest = _sha256Digest(data);
    final cs = _cs64(data, digest);
    final csBytes = ByteData(8)
      ..setUint32(0, cs[0], Endian.little)
      ..setUint32(4, cs[1], Endian.little);
    return '${_hex(csBytes.buffer.asUint8List())}-${_hex(digest)}';
  }

  static String _hex(List<int> b) =>
      b.map((x) => x.toRadixString(16).padLeft(2, '0')).join().toUpperCase();
}

const _m = 0xFFFFFFFF;
const _init = [0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19];
const _state = [0xFD9FBEA2, 0x91243CE7, 0xBE499094, 0xCD2E1E0A, 0x6C006F49, 0x04BD35AB, 0xFEB0A7E4, 0x03EFFBD2];
const _schedule = [
  0xEB24BF9E, 0x1558691F, 0xA0C09EE2, 0x0CFA32BA, 0xA622EE8C, 0xD1DD9491, 0xC0DD25A0, //
  0xF9E70AB6, 0xF0C31573, 0x7058CE4E, 0x7188E606, 0x00E5AB33, 0xD258B060, 0x72766564,
];
const _k = [
  0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5, 0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5, //
  0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3, 0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
  0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
  0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7, 0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
  0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13, 0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
  0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3, 0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
  0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5, 0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
  0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208, 0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2,
];
const _adj = 0xC9DC06A7;
const _cs64Coef = [
  [0xE82E91B1, 0xB32700D0], [0x44DE24C6, 0x88DFF51D], [0x3BEB65BC, 0x29514038], [0xC89C5933, 0xB328CBDF],
  [0x8D1EC211, 0xD405B4D5], [0x314EF5D5, 0x26E55F3C], [0xE92491B1, 0x607801C6], [0xF0A5B9D5, 0x82B2C1FB],
  [0x06064CF3, 0xFA51A3DA], [0xDA79FA60, 0x207CC464], [0xBED20560, 0x3C3C74F3], [0x89F9C9F8, 0xD2D14B61],
  [0x35FA38C6, 0xBB04A2C1], [0xCB49E3C6, 0xC5626167],
];

int _rol16(int v) {
  v &= _m;
  return ((v << 16) | (v >>> 16)) & _m;
}

int _sigma0(int v) => ((v >>> 7) ^ (v << 25) ^ (v >>> 18) ^ (v << 14) ^ (v >>> 3)) & _m;
int _sigma1(int v) => ((v >>> 17) ^ (v << 15) ^ (v >>> 19) ^ (v << 13) ^ (v >>> 10)) & _m;

List<int> _rounds(List<int> s, List<int> w, [int first = 0]) {
  var a = s[0], b = s[1], c = s[2], d = s[3], e = s[4], f = s[5], g = s[6], h = s[7];
  for (var i = first; i < 64; i++) {
    // ROTR via a 64-bit duplicated word, as in the reference implementation.
    final re = e * 0x100000001, ra = a * 0x100000001;
    final kc = (first == 14 && i == 14) ? _adj : _k[i];
    final t1 = h + ((re >>> 6) ^ (re >>> 11) ^ (re >>> 25)) + (g ^ (e & (f ^ g))) + kc + w[i];
    final t2 = ((ra >>> 2) ^ (ra >>> 13) ^ (ra >>> 22)) + ((a & b) | (c & (a | b)));
    h = g;
    g = f;
    f = e;
    e = (d + t1) & _m;
    d = c;
    c = b;
    b = a;
    a = (t1 + t2) & _m;
  }
  return [a, b, c, d, e, f, g, h];
}

void _expand(List<int> w, [int first = 16]) {
  for (var i = first; i < 64; i++) {
    w.add((_sigma1(w[i - 2]) + w[i - 7] + _sigma0(w[i - 15]) + w[i - 16]) & _m);
  }
}

int _be(List<int> b) => b.fold(0, (acc, x) => (acc << 8) | x);

List<int> _sha256Init(List<int> data) {
  final w = <int>[...List.filled(14, 0), _be(data.sublist(0, 3)), _be(data.sublist(3, 7))];
  for (var i = 16; i < 30; i++) {
    var v = _schedule[i - 16];
    if (i - 2 >= 14) v += i == 16 ? (_sigma1(w[i - 2]) ^ 0x000B92C0) : _sigma1(w[i - 2]);
    if (i - 7 >= 14) v += w[i - 7];
    if (i - 15 >= 14) v += i == 29 ? (_sigma0(w[i - 15]) ^ 0x059C0B80) : _sigma0(w[i - 15]);
    w.add(v & _m);
  }
  w.add((_sigma1(w[28]) + w[23] + _sigma0(w[15]) + w[14] + _adj - _k[14]) & _m);
  _expand(w, 31);
  final work = _rounds(_state, w, 14);
  return [for (var i = 0; i < 8; i++) (_init[i] + work[i]) & _m];
}

Uint8List _sha256Digest(List<int> data) {
  final length = 57 + data.length;
  final padded = BytesBuilder()
    ..add(data.sublist(7))
    ..addByte(0x80)
    ..add(List.filled((55 - length) % 64, 0))
    ..add((ByteData(8)..setUint64(0, length * 8)).buffer.asUint8List());
  final bytes = padded.takeBytes();
  var state = _sha256Init(data.sublist(0, 7));
  for (var off = 0; off < bytes.length; off += 64) {
    final bd = ByteData.sublistView(bytes, off, off + 64);
    final w = [for (var i = 0; i < 16; i++) bd.getUint32(i * 4)];
    _expand(w);
    final work = _rounds(state, w);
    state = [for (var i = 0; i < 8; i++) (state[i] + work[i]) & _m];
  }
  final out = ByteData(32);
  for (var i = 0; i < 8; i++) {
    out.setUint32(i * 4, state[i]);
  }
  return out.buffer.asUint8List();
}

List<int> _cs64(List<int> data, Uint8List digest) {
  final dv = ByteData.sublistView(digest);
  final ka = dv.getUint32(0, Endian.little) | 1, kb = dv.getUint32(4, Endian.little) | 1;
  final factors = [(ka * 0x82893933) & _m, (kb * 0x82893933) & _m];
  final terms = [for (var i = 0; i < _cs64Coef.length; i++) (_cs64Coef[i][0] * factors[i % 2]) & _m];
  final offsets = [for (final c in _cs64Coef) c[1]];
  final src = [0, ...data];
  while (src.length % 4 != 0) {
    src.add(0);
  }
  final words = [
    for (var i = 0; i < src.length; i += 4) src[i] | (src[i + 1] << 8) | (src[i + 2] << 16) | (src[i + 3] << 24)
  ];
  if (words.length.isOdd) words.add(0);
  for (var i = 0; i < words.length; i++) {
    final wd = words[i];
    var term = wd * (i.isEven ? ka : kb);
    var offset = _rol16(wd) * (i.isEven ? 0xEF0569FB : 0xC31713DB);
    if (i == 0) {
      term += 0xF2F90B1A * factors[0];
      offset += 0x0B1A0000;
    }
    terms.add(term & _m);
    offsets.add(offset & _m);
  }
  var swapAcc = 0, swapLast = 0, state = 0, acc = 0;
  for (var i = 0; i < terms.length; i += 2) {
    final x = terms[i], y = terms[i + 1];
    var t = _rol16((offsets[i] + x) & _m);
    var q = (t * 0x76F50F34) & _m;
    swapAcc += (_rol16(q) * 0xC3EFEA97 + q) & _m;
    t = _rol16((offsets[i + 1] + y) & _m);
    q = (t * 0x1881CE3C) & _m;
    swapLast = (_rol16(q) * 0x35BD1EC9 + q) & _m;
    swapAcc = (swapAcc + swapLast) & _m;
    state = (state * ka + x) & _m;
    state = (_rol16(state) * 0xCF98B111) & _m;
    state = (_rol16(state) * 0x87085B9F) & _m;
    state = (_rol16(state) * 0x12CEB96D) & _m;
    state = (_rol16(state) * 0x257E1D83) & _m;
    acc += state;
    t = (state * kb + y) & _m;
    t = (_rol16(t) * 0xA27416F5) & _m;
    t = (_rol16(t) * 0xD38396FF) & _m;
    state = (_rol16((_rol16(t) * 0x7C932B89) & _m) * 0xBFA49F69) & _m;
    acc = (acc + state) & _m;
  }
  return [swapLast ^ state, swapAcc ^ acc];
}
