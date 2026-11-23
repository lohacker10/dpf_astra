# Avvio automatico e limiti Android

Il processo Flutter può essere assente: il servizio Kotlin possiede connessione,
polling e notifiche. L'interfaccia ascolta soltanto eventi del servizio.

Un foreground service di tipo `connectedDevice` espone una notifica di servizio;
la presenza companion consente ad Android di risvegliare il processo per un
dispositivo associato. La prima configurazione richiede interazione esplicita.
L'associazione companion non apre da sola la connessione GATT/SPP.

Android 12 limita gli avvii in background; l'esenzione companion è il percorso
previsto per questo caso. Android 13 aggiunge il consenso alle notifiche.
Android 14 richiede il permesso specifico del tipo di servizio e il prerequisito
Bluetooth. Android 15 vieta l'avvio da boot di alcuni tipi di servizio, ma
`connectedDevice` non è tra i tipi elencati. Android 16 aggiunge le nuove API
presence; le API precedenti sono deprecate, non eliminate.

L'app non può garantire avvio o notifica quando è stata arrestata forzatamente,
Bluetooth è spento, i permessi sono revocati, il canale notifiche è disattivato o
il sistema del produttore impedisce la ripresa. Dopo riavvio possono essere
necessari sblocco e inizializzazione Bluetooth. Il receiver di boot ripristina la presenza e tenta anche l’avvio del servizio
connectedDevice già abilitato: un diniego Android viene gestito senza crash.
La presenza Classic può dipendere
dalla connessione ACL: un adattatore SPP che non si connette spontaneamente può
richiedere il servizio già attivo per essere rilevato. La variante BLE deve
annunciare la presenza e avere un indirizzo supportato dall'associazione.

La contemporaneità con l'autoradio è consentita dai profili separati; non è una
garanzia di stabilità del firmware o del telefono. L'app non usa audio Bluetooth.

Fonti ufficiali consultate il 5 novembre 2026:

- [Comunicare in background](https://developer.android.com/develop/connectivity/bluetooth/ble/background)
- [Associazione companion](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing)
- [Tipi foreground service](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [CompanionDeviceService](https://developer.android.com/reference/android/companion/CompanionDeviceService)
- [Restrizioni avvio da boot Android 15](https://developer.android.com/about/versions/15/behavior-changes-15)

Queste sono capacità della piattaforma. La matrice pratica in
[vehicle-validation.md](vehicle-validation.md) verifica che il codice e il telefono
le realizzino nella combinazione usata dall'utente.
