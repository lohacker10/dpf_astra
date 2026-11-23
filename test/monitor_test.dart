import 'package:astra_dpf_monitor/monitor.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'invalid and disconnected measurements are never rendered as real data',
    () {
      for (final value in [-1, 256, double.nan, double.infinity, '78']) {
        expect(
          MonitorReading.fromEvent({
            'connected': true,
            'saturation': value,
          }).saturation,
          isNull,
        );
      }
      final offline = MonitorReading.fromEvent({
        'connected': false,
        'saturation': 78,
        'distance': 247,
        'regen': 'active',
        'elapsed': 12,
      });
      expect(offline.saturation, isNull);
      expect(offline.distance, isNull);
      expect(offline.active, isFalse);
      expect(offline.elapsed, isNull);
      expect(MonitorReading.fromEvent('malformed').connected, isFalse);
    },
  );

  test('overloaded ECU percent remains visible without rescaling', () {
    expect(
      MonitorReading.fromEvent({
        'connected': true,
        'saturation': 120,
      }).saturation,
      120,
    );
  });

  test('only confirmed states render active and inactive', () {
    expect(
      MonitorReading.fromEvent({'connected': true, 'regen': 'active'}).active,
      isTrue,
    );
    expect(
      MonitorReading.fromEvent({
        'connected': true,
        'regen': 'inactive',
      }).inactive,
      isTrue,
    );
    expect(
      MonitorReading.fromEvent({
        'connected': true,
        'regen': 'unknown',
      }).inactive,
      isFalse,
    );
    expect(elapsedText(702), '11 min 42 s');
  });
}
