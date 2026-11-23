import 'package:flutter/services.dart';

class MonitorReading {
  const MonitorReading({
    this.connected = false,
    this.saturation,
    this.distance,
    this.regen = 'unknown',
    this.elapsed,
    this.detail = '',
  });

  factory MonitorReading.fromEvent(dynamic event) {
    if (event is! Map) return const MonitorReading();
    final connected = event['connected'] == true;
    num? validNumber(String key, {num? max}) {
      final value = event[key];
      return connected &&
              value is num &&
              value.isFinite &&
              value >= 0 &&
              (max == null || value <= max)
          ? value
          : null;
    }

    return MonitorReading(
      connected: connected,
      saturation: validNumber('saturation', max: 255)?.toDouble(),
      distance: validNumber('distance')?.toDouble(),
      regen: connected ? (event['regen']?.toString() ?? 'unknown') : 'unknown',
      elapsed: validNumber('elapsed')?.toInt(),
      detail: event['detail']?.toString() ?? '',
    );
  }

  final bool connected;
  final double? saturation;
  final double? distance;
  final String regen;
  final int? elapsed;
  final String detail;

  bool get active => regen == 'active' || regen == 'REGEN_ACTIVE';
  bool get inactive =>
      regen == 'inactive' || regen == 'monitoring' || regen == 'MONITORING';
}

const control = MethodChannel('astra/control');
const events = EventChannel('astra/events');

String elapsedText(int seconds) {
  final minutes = seconds ~/ 60;
  return '$minutes min ${seconds % 60} s';
}
