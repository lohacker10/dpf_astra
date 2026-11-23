# Astra DPF Monitor

Monitor Android locale per Opel Astra K 2016 1.6 CDTI 110 CV, con schermata
Flutter Material 3 e servizio Kotlin indipendente dal processo grafico.
Niente account, pubblicità, rete, backend o servizi a pagamento.

**MVP sperimentale da validare sulla vettura. Non pronto per uso quotidiano.**
Le conversioni ricavate da DPF Info sono riscontri statici, non misure eseguite
sulla tua ECU. Il progetto non contiene codice o componenti dell'APK originale.

## Installazione e prima configurazione

1. Installare l'APK di test. Richiede Android 12 o successivo.
2. Da fermi, accendere il Vgate e aprire l'app una volta.
3. In Impostazioni concedere Bluetooth e notifiche; selezionare il Vgate e il
   trasporto Classic SPP oppure BLE. Completare il consenso companion Android.
4. Avviare il monitoraggio e verificare connessione e letture.
5. Verificare canali notifiche e impostazioni batteria del proprio produttore.
   Terminata la configurazione il servizio tenta la riconnessione automaticamente.

Il display principale mostra soltanto saturazione, distanza dall'ultima
rigenerazione e stato. Nessun dato simulato è utilizzato in produzione.
I valori assenti restano indisponibili. Le stime di durata sono intenzionalmente
indisponibili finché non esiste uno storico di completamenti affidabili.

La presenza companion e il foreground service `connectedDevice` sono i meccanismi
supportati per lavorare a schermo spento. L'avvio non è garantito dopo arresto
forzato, revoca permessi o blocchi del produttore. Un adattatore Classic può non
annunciare presenza finché non è già connesso: il servizio mantiene quindi i
propri tentativi di connessione. Vedere [limiti Android](docs/background.md).

## Protocollo e sicurezza

Consultare [evidenze del reverse engineering](docs/protocol-evidence.md) prima
di interpretare le letture. Nessuna scansione indiscriminata dei DID.
Il trasporto accetta soltanto una lista fissa di inizializzazione ELM e richieste
di lettura DPF. Nessuna cancellazione DTC, codifica, sessione di programmazione,
security access o comando di rigenerazione forzata.

Le risposte malformate, negative o incoerenti non diventano zero. Le transizioni
richiedono letture ripetute; una disconnessione non equivale a completamento.
Un intervallo offline rende la continuità della rigenerazione sconosciuta.

## Struttura

- `lib/`: interfaccia Flutter, modello degli eventi e configurazione.
- `android/app/src/main/kotlin/it/astra/astra_dpf_monitor/`:
  trasporti SPP/GATT, parser, macchina a stati, servizio, notifiche e bridge.
- `test/`: test Flutter e dei valori mancanti.
- `android/app/src/test/`: test JVM, parser e scenari simulati.
- `docs/`: evidenze, restrizioni e prove sul veicolo.

La macchina a stati è nativa perché deve funzionare anche senza Flutter aperto;
Flutter riceve snapshot su EventChannel `astra/events` e invia comandi su
MethodChannel `astra/control`. Persistenza privata leggera tramite preferenze;
i log tecnici restano locali e sono esportati solo su richiesta.

## Build riproducibile

Toolchain usata: Flutter 3.41.4 / Dart 3.11.1, JDK 17, Android SDK 36.

```sh
flutter pub get
flutter analyze
flutter test
flutter build apk --release --split-per-abi
cd android
# Usare JAVA_HOME del proprio JDK 17, se la shell seleziona un JDK diverso.
./gradlew testDebugUnitTest lintDebug
```

APK consigliato per telefoni ARM64: `build/app/outputs/flutter-apk/app-arm64-v8a-release.apk` (circa16.6 MB).
Sono disponibili anche `app-armeabi-v7a-release.apk` e `app-x86_64-release.apk`
nella stessa cartella. La build ottimizzata è firmata con chiave di sviluppo. Per distribuzione
pubblica configurare una propria chiave release; non conservare segreti nel repo.
Il workflow [Android MVP](.github/workflows/android.yml) esegue gli stessi controlli
ed esporta l'APK come artifact. Il workflow sarà eseguito solo dopo push su GitHub.

## Esito verifiche

9 test Flutter e 17 test JVM superati; analisi Dart senza problemi e lint
Android senza errori. Dettagli e limiti in [verifiche eseguite](docs/testing.md).
Le notifiche reali e la radio Bluetooth richiedono ancora un dispositivo.

## Prove necessarie

Seguire la [checklist sulla vettura](docs/vehicle-validation.md). Sono obbligatori
il confronto dei tre parametri, una rigenerazione completa e prove di perdita
Bluetooth, riavvio, autoradio contemporanea e un viaggio a schermo spento.
Non usare le notifiche come motivo per ignorare spie o istruzioni del veicolo.
