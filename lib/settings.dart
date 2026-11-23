import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'monitor.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key, required this.detail});
  final String detail;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  bool busy = false;
  bool logging = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      final value = await invoke('logging');
      if (mounted && value is bool) setState(() => logging = value);
    });
  }

  Future<void> discoverBle() async {
    final success = await invoke('associate', {'transport': 'ble'});
    if (mounted && success == true) {
      showMessage('Vgate configurato. Monitoraggio avviato.');
    }
  }

  Future<void> setLogging(bool enabled) async {
    final result = await invoke('logging', {'enabled': enabled});
    if (mounted && result is bool) setState(() => logging = result);
  }

  Future<void> requestPermissions() async {
    final result = await invoke('permissions');
    if (mounted && result is Map) {
      showMessage(
        result['bluetooth'] == true && result['notifications'] == true
            ? 'Autorizzazioni concesse.'
            : 'Autorizzazioni incomplete. Verifica Bluetooth e notifiche nelle impostazioni Android.',
      );
    }
  }

  Future<dynamic> invoke(
    String method, [
    Map<String, dynamic>? arguments,
  ]) async {
    setState(() => busy = true);
    try {
      return await control.invokeMethod<dynamic>(method, arguments);
    } on PlatformException catch (error) {
      if (mounted) showMessage(error.message ?? 'Operazione non riuscita.');
    } on MissingPluginException {
      if (mounted) showMessage('Funzione disponibile su Android.');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  void showMessage(String message) => ScaffoldMessenger.of(
    context,
  ).showSnackBar(SnackBar(content: Text(message)));

  Future<void> selectAdapter() async {
    final result = await invoke('devices');
    if (!mounted || result is! List) return;
    if (result.isEmpty) {
      showMessage(
        'Nessun adattatore associato. Associa il Vgate nelle impostazioni Bluetooth Android e riprova.',
      );
      return;
    }
    final device = await showDialog<Map<dynamic, dynamic>>(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text('Seleziona il Vgate'),
        children: result
            .whereType<Map>()
            .map(
              (device) => SimpleDialogOption(
                onPressed: () => Navigator.pop(context, device),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Text(
                    '${device['name'] ?? 'Dispositivo Bluetooth'}\n${device['address'] ?? ''}',
                  ),
                ),
              ),
            )
            .toList(),
      ),
    );
    if (!mounted || device == null) return;
    final transport = await showDialog<String>(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text('Variante dell’adattatore'),
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(24, 8, 24, 16),
            child: Text(
              'Scegli il collegamento supportato dal tuo Vgate. BLE verifica i servizi compatibili alla connessione.',
            ),
          ),
          SimpleDialogOption(
            onPressed: () => Navigator.pop(context, 'classic'),
            child: const Text('Bluetooth Classic · SPP'),
          ),
          SimpleDialogOption(
            onPressed: () => Navigator.pop(context, 'ble'),
            child: const Text('Bluetooth Low Energy · BLE'),
          ),
        ],
      ),
    );
    if (!mounted || transport == null) return;
    final success = await invoke('associate', {
      'address': device['address'],
      'transport': transport,
    });
    if (mounted && success != false && success != null) {
      showMessage('Configurazione salvata. Monitoraggio avviato.');
    }
  }

  Future<void> diagnostics() async {
    final result = await invoke('diagnostics');
    if (!mounted || result == null) return;
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Diagnostica tecnica'),
        content: SingleChildScrollView(
          child: SelectableText('${widget.detail}\n\n$result'.trim()),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Chiudi'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Impostazioni')),
    body: ListView(
      children: [
        const Padding(
          padding: EdgeInsets.all(24),
          child: Text(
            'Configura una volta il Vgate e le autorizzazioni. Il servizio Android mantiene il monitoraggio quando lo schermo è spento. L’avvio automatico dipende dalle autorizzazioni e dalla gestione batteria del telefono.',
          ),
        ),
        if (busy) const LinearProgressIndicator(),
        ListTile(
          leading: const Icon(Icons.bluetooth_searching),
          title: const Text('Seleziona il Vgate'),
          subtitle: const Text('Adattatore già associato · Classic o BLE'),
          onTap: busy ? null : selectAdapter,
        ),
        ListTile(
          leading: const Icon(Icons.radar),
          title: const Text('Cerca Vgate BLE non associato'),
          subtitle: const Text('Selezione tramite Android Companion Device'),
          onTap: busy ? null : discoverBle,
        ),
        ListTile(
          leading: const Icon(Icons.security_outlined),
          title: const Text('Autorizzazioni Android'),
          subtitle: const Text('Bluetooth e notifiche'),
          onTap: busy ? null : requestPermissions,
        ),
        ListTile(
          leading: const Icon(Icons.play_circle_outline),
          title: const Text('Avvia monitoraggio'),
          onTap: busy ? null : () => invoke('start'),
        ),
        ListTile(
          leading: const Icon(Icons.stop_circle_outlined),
          title: const Text('Sospendi monitoraggio'),
          subtitle: const Text('Disattiva anche il collegamento automatico'),
          onTap: busy ? null : () => invoke('stop'),
        ),
        const Divider(),
        ListTile(
          leading: const Icon(Icons.notifications_outlined),
          title: const Text('Notifiche e suoni'),
          subtitle: const Text('Impostazioni dei canali Android'),
          onTap: busy
              ? null
              : () => invoke('settings', {'section': 'notifications'}),
        ),
        ListTile(
          leading: const Icon(Icons.battery_saver_outlined),
          title: const Text('Impostazioni batteria e app'),
          onTap: busy ? null : () => invoke('settings', {'section': 'app'}),
        ),
        ListTile(
          leading: const Icon(Icons.code),
          title: const Text('Diagnostica tecnica'),
          onTap: busy ? null : diagnostics,
        ),
        SwitchListTile(
          secondary: const Icon(Icons.bug_report_outlined),
          title: const Text('Registra risposte DPF grezze'),
          subtitle: const Text('Attiva durante le prove sulla tua Astra'),
          value: logging,
          onChanged: busy ? null : setLogging,
        ),
        ListTile(
          leading: const Icon(Icons.ios_share),
          title: const Text('Esporta log tecnici'),
          subtitle: const Text(
            'Solo richieste DPF e risposte, senza VIN o identificativi Bluetooth',
          ),
          onTap: busy ? null : () => invoke('exportLogs'),
        ),
        const Padding(
          padding: EdgeInsets.all(24),
          child: Text(
            'Versione di validazione: verifica le letture sulla tua Astra prima di fare affidamento sugli avvisi.',
          ),
        ),
      ],
    ),
  );
}
