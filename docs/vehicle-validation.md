# Validazione su Astra K 2016 1.6 CDTI 110 CV

Questa build è sperimentale. La corrispondenza di un parametro nell'APK non
certifica il significato sulla centralina della vettura. Annotare versione
software ECU e motore solo in documentazione privata: non esportare VIN.

## Prima configurazione, a veicolo fermo

1. Associare il Vgate nelle impostazioni Bluetooth se Classic; per BLE usare
   la configurazione companion dell'app. Verificare la variante sull'etichetta.
2. Concedere Dispositivi nelle vicinanze e Notifiche. Accettare l'associazione
   companion. Lasciare attive le notifiche del monitor e degli eventi.
3. Se richiesto dal sistema, attivare Bluetooth e localizzazione per il solo
   rilevamento companion. Impostare batteria senza restrizioni se il produttore
   interrompe il servizio. Non basta rimuovere l'app dalle ottimizzazioni per
   superare un arresto forzato Android.
4. Selezionare l'adattatore e avviare una prima sessione in primo piano.
   Controllare l'identificazione e i dati; dati mancanti devono restare mancanti.
5. Non usare contemporaneamente DPF Info e questa app sullo stesso adattatore.
   L'autoradio usa altri profili Bluetooth e può rimanere collegata; la reale
   stabilità della combinazione telefono/firmware/adattatore va provata.

## Confronto dei dati

Registrare log tecnici solo nel viaggio di prova, con passeggero o da fermi.
Confrontare letture non simultanee con uno strumento già validato. Servono:

- almeno tre punti di saturazione prima/durante/dopo rigenerazione;
- distanza che aumenta con il viaggio e si azzera in modo coerente dopo una
  rigenerazione (oppure identificazione esplicita del suo diverso significato);
- valori grezzi stato prima/durante/dopo, con transizioni ripetute;
- prova che fine attività distingue successo da interruzione: in assenza di
  conferma supplementare l'app deve dichiarare fine attività incerta;
- risposte negative, NO DATA, motore spento: nessun valore precedente presentato
  come attuale e nessuna notifica di completamento.

## Matrice pratica Android 12–16

Per ciascun telefono disponibile annotare versione OS, firmware Vgate e risultato:

| Prova | Risultato atteso |
| --- | --- |
| Display spento per un intero viaggio | Polling e notifiche locali continuano |
| App rimossa dai recenti | Servizio continua, salvo policy del produttore |
| Auto spenta e riaccesa | Ripresa con backoff/presenza, senza aprire l'app |
| Bluetooth disattivato/riattivato | Recupero senza falsa fine rigenerazione |
| Perdita segnale durante rigenerazione | Stato sconosciuto; nessuna fine inventata |
| Riavvio telefono durante rigenerazione | Recupero conservativo dopo sblocco |
| Notifiche negate o canale silenziato | Limite visibile; nessun suono garantito |
| Arresto forzato da impostazioni | Riapertura manuale necessaria |
| Autoradio connessa e chiamata | Nessun uso dei profili audio da parte del monitor |
| Vgate alimentato anche a quadro spento | Assenza di polling indefinito della ECU |

La notifica di avvio può arrivare solo dopo la prima lettura confermata. Una
rigenerazione interamente avvenuta offline non è ricostruibile. Nessuna durata
storica va usata finché il completamento e la continuità non sono convalidati.
