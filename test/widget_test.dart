import 'package:astra_dpf_monitor/main.dart';
import 'package:astra_dpf_monitor/monitor.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const codec = StandardMethodCodec();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final calls = <MethodCall>[];

  setUp(() {
    calls.clear();
    messenger.setMockMethodCallHandler(
      const MethodChannel('astra/events'),
      (_) async => null,
    );
    messenger.setMockMethodCallHandler(control, (call) async {
      calls.add(call);
      if (call.method == 'devices') {
        return [
          {
            'name': 'Vgate iCar Pro',
            'address': '00:11:22:33:44:55',
            'type': 'dual',
          },
        ];
      }
      return true;
    });
  });

  Future<void> emit(WidgetTester tester, Map<String, dynamic> event) async {
    await messenger.handlePlatformMessage(
      'astra/events',
      codec.encodeSuccessEnvelope(event),
      (_) {},
    );
    await tester.pump();
  }

  testWidgets('shows unavailable data until a real reading arrives', (
    tester,
  ) async {
    await tester.pumpWidget(const AstraApp());
    await tester.pump();
    expect(find.text('Saturazione DPF'), findsOneWidget);
    expect(find.text('—'), findsOneWidget);
    expect(find.text('Non connesso'), findsOneWidget);
    expect(find.text('0%'), findsNothing);
    expect(find.text('0 km'), findsNothing);
  });

  testWidgets(
    'simulated event sequence displays active then hides stale data',
    (tester) async {
      await tester.pumpWidget(const AstraApp());
      await tester.pump();
      await emit(tester, {
        'connected': true,
        'saturation': 78,
        'distance': 247,
        'regen': 'active',
        'elapsed': 362,
      });
      expect(find.text('78%'), findsOneWidget);
      expect(find.text('247 km'), findsOneWidget);
      expect(find.text('In corso · 6 min 2 s'), findsOneWidget);
      await emit(tester, {
        'connected': false,
        'saturation': 78,
        'distance': 247,
        'regen': 'active',
        'elapsed': 362,
      });
      expect(find.text('78%'), findsNothing);
      expect(find.text('247 km'), findsNothing);
      expect(find.text('Non connesso'), findsOneWidget);
    },
  );

  testWidgets(
    'reconnected active regeneration never invents an elapsed duration',
    (tester) async {
      await tester.pumpWidget(const AstraApp());
      await tester.pump();
      await emit(tester, {
        'connected': true,
        'regen': 'active',
        'elapsed': null,
      });
      expect(find.text('In corso · durata non disponibile'), findsOneWidget);
      expect(find.text('In corso · 0 min 0 s'), findsNothing);
    },
  );

  testWidgets('BLE discovery does not require an existing Bluetooth bond', (
    tester,
  ) async {
    await tester.pumpWidget(const AstraApp());
    await tester.pump();
    await tester.tap(find.byTooltip('Impostazioni'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cerca Vgate BLE non associato'));
    await tester.pumpAndSettle();
    expect(calls.last.method, 'associate');
    expect(calls.last.arguments, {'transport': 'ble'});
  });

  testWidgets('setup selects a device and explicitly chooses transport', (
    tester,
  ) async {
    await tester.pumpWidget(const AstraApp());
    await tester.pump();
    await tester.tap(find.byTooltip('Impostazioni'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Seleziona il Vgate'));
    await tester.pumpAndSettle();
    await tester.tap(find.textContaining('Vgate iCar Pro'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Bluetooth Low Energy · BLE'));
    await tester.pumpAndSettle();
    expect(calls.last.method, 'associate');
    expect(calls.last.arguments, {
      'address': '00:11:22:33:44:55',
      'transport': 'ble',
    });
  });

  testWidgets('main screen fits narrow phone with large text', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(const AstraApp());
    await tester.pump();
    expect(tester.takeException(), isNull);
  });
}
