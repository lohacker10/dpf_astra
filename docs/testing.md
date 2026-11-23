# Verifiche eseguite

Ambiente Linux, Flutter 3.41.4 / Dart 3.11.1, JDK17, SDK36.

- `flutter analyze`: nessun problema.
- `flutter test`: 9 test superati.
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew testDebugUnitTest lintDebug`
  da `android`: 17 test JVM superati, zero errori lint. Restano avvisi di
  aggiornamento toolchain, API deprecate compatibili e suggerimenti KTX.
- Prima build `flutter build apk --debug`: riuscita.

La shell selezionava un JDK27 non supportato dalla toolchain Gradle: il primo
lancio diretto di Gradle è fallito prima dei test. Ripetuto con JDK17 è riuscito.
Flutter aveva già selezionato correttamente JDK17 per la build APK.

I test JVM verificano payload, formule (incluso carico oltre100%), rifiuto delle
scritture, NO DATA, risposte negative/malformate, tre letture coerenti, deduplica
eventi, avvio già in rigenerazione, riavvio, perdita connessione, intervalli
obsoleti e reset distanza anomalo isolato. I due scenari in
`ObdSimulationTest.kt` generano risposte ELM sintetiche e percorrono parser e
macchina a stati. Si eseguono senza auto con `./gradlew testDebugUnitTest`.
Il simulatore è soltanto nei test: non può alimentare il monitor normale.

I test Flutter verificano valori mancanti/invalidi, dati reali ricevuti dal
canale, eliminazione dei valori dopo disconnessione, durata sconosciuta,
configurazione Classic/BLE, scoperta BLE senza pairing e layout320px con testo2x.

**Non verificato:** consegna effettiva delle notifiche Android a schermo bloccato,
radio SPP/GATT, associazione companion su dispositivo, riavvio reale, consumi,
coabitazione autoradio, versione ECU e completamento DPF sulla vettura.
La deduplica verificata è quella degli eventi della macchina a stati; non è una
prova della visualizzazione delle notifiche sul telefono. Nessun test stradale
o connessione all'automobile è stato effettuato.

Lo storico conserva al massimo20 cicli corroborati. Non viene esposta una stima
finché non è stata verificata l'affidabilità delle transizioni e dei tempi.
Il backoff arriva a120s: dopo una lunga assenza dell'adattatore il rilevamento
può essere ritardato di questo intervallo, più connessione e conferma letture.
Il consumo con adattatore sempre alimentato va misurato: richieste diagnostiche
possono influenzare il riposo della vettura. I comandi sono di sola lettura,
ma questo non certifica consumo nullo né assenza di risvegli delle centraline.
