import 'package:flutter_test/flutter_test.dart';
import 'package:jarvis_poco_app/services/edge_tts.dart';

void main() {
  test('Sec-MS-GEC token matches python edge-tts 7.3.1 reference', () {
    expect(EdgeTts.secMsGec(1792142169),
        '696A661AC0DB060A-9328A3569D35244928A186C7D52D2E4CF059D854ACD8D3DC9ACC43CEE6F3EF98');
    expect(EdgeTts.secMsGec(1700000000),
        '2CD23A2CA0B2595A-F3C846C370E05F9CC83ACC5F995C5F5D87A4D0F14CE8936A0F902E64099E38A2');
    expect(EdgeTts.secMsGec(1800000123),
        '99E54519B490BE5C-E162DB0977FBDCB9B86DD8148BF1BCDC3C0303176CE39852A42B415935C20654');
  });
}
