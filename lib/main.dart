import 'dart:async';
import 'package:flutter/material.dart';
import 'monitor.dart';
import 'settings.dart';
import 'updates.dart';

void main() => runApp(const AstraApp());

const appName = 'DPF Astra';

class AstraApp extends StatelessWidget {
  const AstraApp({super.key, this.checkForUpdate = fetchUpdate});

  final Future<ReleaseUpdate?> Function() checkForUpdate;

  ThemeData _theme(Brightness brightness) => ThemeData(
    colorScheme: ColorScheme.fromSeed(
      seedColor: const Color(0xff34765c),
      brightness: brightness,
    ),
  );

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: appName,
    debugShowCheckedModeBanner: false,
    theme: _theme(Brightness.light),
    darkTheme: _theme(Brightness.dark),
    themeMode: ThemeMode.system,
    home: MonitorPage(checkForUpdate: checkForUpdate),
  );
}

class MonitorPage extends StatefulWidget {
  const MonitorPage({super.key, required this.checkForUpdate});

  final Future<ReleaseUpdate?> Function() checkForUpdate;

  @override
  State<MonitorPage> createState() => _MonitorPageState();
}

class _MonitorPageState extends State<MonitorPage> {
  MonitorReading reading = const MonitorReading();
  StreamSubscription<dynamic>? subscription;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_offerUpdate());
    });
    subscription = events.receiveBroadcastStream().listen(
      (event) {
        if (mounted) setState(() => reading = MonitorReading.fromEvent(event));
      },
      onError: (Object error) {
        if (mounted) setState(() => reading = const MonitorReading());
      },
    );
  }

  Future<void> _offerUpdate() async {
    final update = await widget.checkForUpdate();
    if (!mounted || update == null) return;

    final openRelease = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        scrollable: true,
        title: const Text('Aggiornamento disponibile'),
        content: Text(
          '$appName ${update.version} (build ${update.build}) è disponibile. Apri GitHub per '
          'scaricare dpf-astra.apk e installare l’aggiornamento.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Più tardi'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Apri GitHub'),
          ),
        ],
      ),
    );

    if (!mounted || openRelease != true) return;
    if (!await update.open() && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Impossibile aprire GitHub.')),
      );
    }
  }

  @override
  void dispose() {
    subscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final color = reading.active
        ? scheme.brightness == Brightness.dark
              ? const Color(0xffffbd6d)
              : const Color(0xff925300)
        : reading.inactive
        ? scheme.primary
        : scheme.onSurfaceVariant;
    final saturation = reading.saturation;
    final status = !reading.connected
        ? 'Non connesso'
        : reading.active
        ? reading.elapsed == null
              ? 'In corso · durata non disponibile'
              : 'In corso · ${elapsedText(reading.elapsed!)}'
        : reading.inactive
        ? 'Non in corso'
        : 'Dato non disponibile';
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          appName,
          style: TextStyle(fontWeight: FontWeight.w600),
        ),
        actions: [
          Tooltip(
            message: reading.connected ? 'OBD connesso' : 'OBD non connesso',
            child: Icon(
              reading.connected
                  ? Icons.bluetooth_connected
                  : Icons.bluetooth_disabled,
              size: 20,
              color: color,
            ),
          ),
          IconButton(
            tooltip: 'Impostazioni',
            icon: const Icon(Icons.settings_outlined),
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => SettingsPage(detail: reading.detail),
              ),
            ),
          ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(28, 32, 28, 40),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Text(
                    'Saturazione DPF',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 18),
                  ),
                  const SizedBox(height: 28),
                  Center(
                    child: SizedBox(
                      width: 220,
                      height: 220,
                      child: Stack(
                        alignment: Alignment.center,
                        children: [
                          Positioned.fill(
                            child: ExcludeSemantics(
                              child: CircularProgressIndicator(
                                value: saturation == null
                                    ? 0
                                    : saturation / 100,
                                strokeWidth: 10,
                                backgroundColor: scheme.surfaceContainerHighest,
                                color: color,
                              ),
                            ),
                          ),
                          Text(
                            saturation == null
                                ? '—'
                                : '${saturation.toStringAsFixed(0)}%',
                            semanticsLabel: saturation == null
                                ? 'Saturazione DPF non disponibile'
                                : 'Saturazione DPF ${saturation.toStringAsFixed(0)} per cento',
                            style: TextStyle(
                              fontSize: 56,
                              fontWeight: FontWeight.w500,
                              color: color,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 40),
                  const Divider(),
                  const SizedBox(height: 20),
                  const Text(
                    "Dall'ultima rigenerazione",
                    style: TextStyle(fontSize: 16),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    reading.distance == null
                        ? 'Dato non disponibile'
                        : '${reading.distance!.toStringAsFixed(0)} km',
                    style: const TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  const SizedBox(height: 24),
                  const Divider(),
                  const SizedBox(height: 20),
                  const Text('Rigenerazione', style: TextStyle(fontSize: 16)),
                  const SizedBox(height: 8),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Padding(
                        padding: const EdgeInsets.only(top: 5),
                        child: Icon(
                          reading.active
                              ? Icons.local_fire_department_outlined
                              : reading.inactive
                              ? Icons.check_circle_outline
                              : Icons.help_outline,
                          color: color,
                          size: 22,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          status,
                          style: TextStyle(
                            fontSize: 24,
                            color: color,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
