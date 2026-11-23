# Analisi di interoperabilità DPF Info 1.142

Analisi statica del 5 novembre 2026. Nessuna connessione all'automobile; nessuna esecuzione dell'app originale. **Identificato nel binario non significa validato sulla specifica ECU.** Non sono stati aggirati acquisti, licenze o protezioni. Nel progetto non vengono inclusi APK, DEX, risorse, sorgenti decompilati o routine proprietarie.

## Identità e strumenti

| Artefatto | SHA-256 |
|---|---|
| XAPK fornito, 12122728 byte | `189d4283a50bdbe17838fa2af082ff071228c351e2325f557d439a1629937dba` |
| `tm.dpfinfo.apk` | `0b554cccffa0ccc925c29a49927b98dc99c2cb9aca9c8e2a9f8776c70a1c0aef` |
| `config.hdpi.apk` | `a98a4bfed0da0b0097e59831940e2652805cb5c646c59147bd783d4c1b03d8f7` |

REA `open_binary` e `inspect_artifact` hanno identificato l'archivio e il relativo hash, ma questa sessione ha prodotto solo l'osservazione root, senza inventario affidabile dei membri. Evidenza root: `ev_956b73f24f895c9b1be97a4848ae1df0f23eecad4585ce4600f752a3fc78db79`. L'inventario dei membri deriva separatamente da Python `zipfile`: APK principale, split hdpi e `manifest.json`. Il normale `unzip -l` di questo ambiente restituisceva erroneamente zero membri: per l'estrazione è stato usato il central directory parser di Python, con controllo CRC durante la lettura.

Il provider Android REA richiedeva un JAR assente. È stato scaricato il [JADX headless MCP 0.7.1](https://github.com/1013503897/jadx-headless-mcp/releases/tag/v0.7.1), verificando l'hash pubblicato da REA `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`. Il JAR è uno strumento di analisi, non una dipendenza dell'app.

REA `inspect_android_package`: evidenza `ev_0b7a1027e246412daea4ac1d0ad2608e880d3911f23972b444f7a172c508178a`; package `tm.dpfinfo`, versione `1.142`/142, minSdk32, targetSdk36, 11906 classi e 627 risorse. Non è stata verificata la firma dell'APK. Un solo `classes.dex`; nessuna libreria `lib/*.so` nei due APK. Lo split contiene 44 membri e nessun DEX.

Il manifest dichiara `MainActivity`, servizio `tm.dpfinfo.service.Service` con tipo `connectedDevice`, receiver boot `Autostart`, provider privato `LogDumpProvider`. Dichiara Bluetooth connect/scan, notifiche, foreground service, boot e wake lock; contiene inoltre componenti ads, billing e rete che non vengono riutilizzati. Queste dichiarazioni non provano efficacia dell'avvio automatico.

## Catena di identificazione del profilo

Sono stati consultati i metodi con REA e usata un'estrazione aggiuntiva dei sorgenti con le API JADX esclusivamente in `/tmp/astra-re`, fuori dal repository, per cercare le classi offuscate. Le costanti stringa sono offuscate: la loro decodifica statica locale ricostruisce i valori dei comandi, senza eseguire APK o verifiche di licenza. La routine di decodifica non è inclusa nel prodotto.

La tabella dei profili `tm.dpfinfo.oOO.<clinit>` nomina `Opel: B16DT*, B20DT*` / `opel_b16dt*_b20dt*__ver00`. La factory `tm.dpfinfo.h5` lo associa al commander `tm.dpfinfo.nk` con discriminante **5**. Le formule si trovano nei corrispondenti rami di `tm.dpfinfo.lk.OooOo0O`. Il collegamento delle letture ai significati è esplicito in `tm.dpfinfo.j5.OooO00o`, e corroborato dai campi serializzati di `tm.dpfinfo.h3`.

## Parametri effettivamente identificati

Byte A, B in ordine di ricezione, unsigned. Header richiesta `7E0`, risposta `7E8`; servizio diagnostico `22` (ReadDataByIdentifier), risposta positiva `62`.

| Grandezza nel profilo B16DT/B20DT | Richiesta UDS | Payload risposta | Formula statica osservata | Interpretazione |
|---|---|---|---|---|
| DPFPrimaryLoad | `22 33 6A` | `62 33 6A A` | `A` | Carico/saturazione percentuale, un byte. NON `A*100/255` in questo profilo. |
| DPFRegenerationStatus | `22 20 F2` | `62 20 F2 A` | `A` | Il classificatore originale considera attivo un valore `>0`. Non è una percentuale di avanzamento. |
| DistanceSinceLastRegen | `22 30 39` | `62 30 39 A B` | `256*A+B` | Chilometri dall'ultima rigenerazione; non chilometraggio assoluto dell'ultima rigenerazione. |
| DPFTemperature | `22 30 3B` | `62 30 3B A B` | `(256*A+B)*0.1-40` °C | Identificato ma non necessario al monitor principale. |

Per il profilo B16DT il metodo relativo a DPFRegenerationProgress restituisce **non supportato**. Nessun tempo residuo o durata è stato ricavato dalla ECU. Nessuna semantica documentata di singoli bit dello stato è stata recuperata: `A>0` descrive il comportamento dell'app originale, non una specifica ufficiale GM.

Il carico può superare 100 nell'app originale, che distingue anche sovraccarico. Non trasformare 120 in 100 né riscalare automaticamente: conservare il valore grezzo e verificarlo sul veicolo. La scala deve essere confrontata con DPF Info sulla stessa ECU.

### Sessione ELM327 osservata

Setup comune: `AT WS`, `AT E0`, `AT L0`, `AT S0`, `AT H1`, `AT AT0`, `AT ST64`, `AT CAF0`, selezione protocollo `AT SP A <protocollo>`, controllo `AT DP`. Il commander B16DT configura poi `AT AR`, `AT CRA`, `AT SH 7E0`. Il protocollo enum include CAN 11 bit 500 kbit/s (6); occorre verificare quello negoziato sul veicolo.

Con `CAF0` l'originale invia il byte di lunghezza ISO-TP: `03 22 33 6A`, `03 22 20 F2`, `03 22 30 39`. I prefissi attesi sono rispettivamente `7E8 04 62 33 6A`, `7E8 04 62 20 F2`, `7E8 05 62 30 39`. Con `CAF1` una nuova implementazione può inviare solo il payload UDS; è una scelta indipendente documentata dal [datasheet ELM327](https://www.elmelectronics.com/wp-content/uploads/2016/07/ELM327DS.pdf), non un comando diverso per la ECU.

I comandi AT configurano l'adattatore, non la centralina. Nel prodotto sono ammessi esclusivamente i tre DID di lettura necessari, più eventuale RPM di sola lettura; nessuna console arbitraria, cancellazione DTC, coding, service control, security access o rigenerazione forzata.

## Evidenze REA decisive

| Oggetto/metodo | Evidence ID |
|---|---|
| Registro profili `oOO.<clinit>` | `ev_3740f6ef5ea5db12819531d60fc2bb74e04e1f72a2b969985fcfa59bf3ef4707` |
| Factory `h5.OooO00o` | `ev_a5b4f8dc0401683f3934ece878696ee235ac606c30bae5400cffdab72c00efc7` |
| Carico `nk.OooO00o`, ramo5 | `ev_47c1baf318fb41eb0c731dba63fbfcbb79d752263c9c441b9b4f7f7dc0509811` |
| Distanza `nk.OooO0o`, ramo5 | `ev_3ca06af830de168b0ef4f39a10f1e65d740ca763ba8ac2718b47e1d70ea0a806` |
| Stato `nk.OooO0oo`, ramo5 | `ev_4748df5e4b7a59497c2462ae7029d239ea70405d6d0f06bc02e99b5bde819b0f` |
| Conversioni `lk.OooOo0O`, rami26,28,default | `ev_b1bd481979e89c1da4b7238d87b79bc5f9e1fc0b97a65dff142cd86d089a1e17` |
| Assegnazioni semantiche `j5.OooO00o` | `ev_7344be6af5145c69f7f63319ab27fb6349b90959f20fe350950802142f2a6ab6` |
| Classificazione `iu.OooOo0O` | `ev_73cc51476f88baaa00857edff4fb0324182af0f7d2545a5e4e5b3442a34ca20c` |
| Setup adattatore `lq.OooO0Oo` | `ev_ab2db4c65abd89383a2020590715a8f66982074b25cefb3e27508fe6014fc06d` |
| Decodifica stringhe `iu.OooOO0o` | `ev_cfe7f2405f57d16a1f6a067254c112032879679babb0f676cff39d8befdef2c6` |

Gli ID si riferiscono alla sessione REA, non sono URL pubblici. Non si distribuiscono i corpi decompilati. Alcuni metodi generici hanno richiesto fallback smali (es. worker `cu0.OooO0O0`, `ev_9c8c84ed2ec220cbf76e6dad9a0a0d56047b88d0dd0059351bde212d7a62a697`); non si assume decompilazione completa dell'intera applicazione.

## Osservazioni, inferenze e limiti

- **Osservato staticamente:** tre DID, formule, header, associazione del profilo, stato `>0`, mancanza di progresso nel profilo, etichetta distanza dall'ultima rigenerazione.
- **Inferenza da validare:** il profilo B16DT è appropriato all'Astra K 1.6 CDTI 110 CV descritta. Anno/potenza non identificano univocamente versione software ECU e motore installato.
- **Non stabilito:** codifica OEM ufficiale dello stato, valore che distingua completamento da interruzione, tempo ECU di inizio/fine, significato dei valori eccezionali, variante esatta dell'adattatore, comportamento radio del telefono.
- L'app originale usa anche una temperatura DPF >=550 °C per classificare una rigenerazione. È un'euristica osservata, **non una prova ECU** e non viene adottata come conferma indipendente.
- Un passaggio attivo→inattivo prova la cessazione dello stato, non necessariamente il successo della pulizia. Perciò il nuovo monitor richiede letture coerenti e corroborazione della distanza azzerata; offline o perdita Bluetooth non equivalgono a completamento. Anche tale corroborazione resta da validare sull'auto.

## Fonti pubbliche consultate e conflitti

Il [datasheet ELM327 del produttore](https://www.elmelectronics.com/wp-content/uploads/2016/07/ELM327DS.pdf) documenta il trasporto AT/CAN. Il [produttore Vgate](https://vgatemall.com/products-detail/i-9/) dichiara una variante BLE per Android/iOS con sleep e wake automatici; ciò non identifica l'esemplare dell'utente né garantisce UUID GATT o risveglio sotto polling.

La ricerca pubblica ha restituito anche una [tabella di un altro progetto](https://github.com/miskibin/obd2-dashboard/blob/main/docs/research-multibrand-extended-pids.md) che propone scale conflittuali per 336A e un DID diverso per lo stato. È una fonte secondaria non adottata come specifica. Si mantiene la formula realmente osservata nel profilo dell'APK, da confrontare con il veicolo; non si provano entrambe le scale scegliendo quella che sembra plausibile. Nessuna fonte pubblica OEM completa dei tre DID è stata reperita in questa ricerca mirata.

## Prima validazione sull'Astra reale

1. Verificare sigla motore e adattatore. Non modificare configurazioni ECU.
2. A veicolo fermo, confrontare risposte grezze 336A/20F2/3039 e letture simultanee o consecutive di DPF Info; annotare profilo e scala.
3. Registrare una rigenerazione naturale completa e una sessione con disconnessione; verificare stato, reset3039 e comportamento allo spegnimento. Non forzare una rigenerazione dall'app.
4. Controllare che la distanza3039 avanzi e si azzeri con il ciclo; un valore statico diverso richiede rivedere etichetta/profilo.
5. Solo dopo queste prove abilitare fiducia nell'avviso di completamento. Conservare i log minimizzati senza VIN, MAC, targhe o posizione.
