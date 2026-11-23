package it.astra.astra_dpf_monitor

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object MonitorBus {
    @Volatile var snapshot: Map<String,Any?> = mapOf("connected" to false,"status" to "disconnected","regen" to "unknown","detail" to "Seleziona il Vgate nelle impostazioni")
    var listener: ((Map<String,Any?>)->Unit)?=null
    fun publish(value:Map<String,Any?>) { snapshot=value;Handler(Looper.getMainLooper()).post { listener?.invoke(value) } }
}
class TechnicalLog(private val context:Context) {
    @Synchronized fun record(event:String, command:String?=null,response:String?=null) {
        val prefs=context.getSharedPreferences("monitor",0)
        if(!prefs.getBoolean("logging",false)) return
        if(command!=null && !ElmProtocol.permitted(command)) return
        try {
        val file=File(context.filesDir,"diagnostic.jsonl")
        if(file.length()>256*1024) file.writeText("")
        // Only fixed adapter/DPF replies are retained: no VIN, MAC, device name or arbitrary ECU command.
        val row=JSONObject().put("time",System.currentTimeMillis()).put("event",event.take(80))
        if(command!=null) row.put("command",command)
        if(response!=null && command in ElmProtocol.requests) {
            val errors=listOf("NO DATA","STOPPED","UNABLE TO CONNECT","CAN ERROR","BUS ERROR","BUFFER FULL","?")
            val knownError=errors.firstOrNull { response.uppercase().contains(it) }
            row.put("response",knownError ?: response.filter { it in "0123456789abcdefABCDEF \r\n>:" }.take(4096))
        }
        file.appendText(row.toString()+"\n")
        } catch(_:java.io.IOException) { /* Diagnostic storage must not interrupt monitoring. */ }
    }
    fun text():String=File(context.filesDir,"diagnostic.jsonl").let { if(it.exists()) it.readText() else "Nessun log. Attiva registrazione nelle impostazioni prima della prova." }
}
class DpfNotifications(private val context:Context) {
    private val manager=context.getSystemService(NotificationManager::class.java)
    init {
        manager.createNotificationChannel(NotificationChannel("monitor","Monitoraggio DPF",NotificationManager.IMPORTANCE_LOW).apply { setSound(null,null) })
        manager.createNotificationChannel(NotificationChannel("regen","Rigenerazione DPF",NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun build(title:String,text:String,ongoing:Boolean,alert:Boolean=false):Notification {
        val intent=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(context,if(alert) "regen" else "monitor").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(intent)
            .setOngoing(ongoing).setOnlyAlertOnce(!alert).setVisibility(Notification.VISIBILITY_PUBLIC).build()
    }
    fun ongoing(title:String,text:String)=build(title,text,true)
    fun alert(id:Int,title:String,text:String) { if(Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) manager.notify(id,build(title,text,false,true)) }
    fun update(title:String,text:String) { manager.notify(1,ongoing(title,text)) }
}

@SuppressLint("MissingPermission", "WakelockTimeout")
class MonitorService:Service() {
    private val prefs by lazy { getSharedPreferences("monitor",0) }
    private val worker=Executors.newSingleThreadExecutor()
    private val deadlines=Executors.newSingleThreadScheduledExecutor()
    @Volatile private var running=false
    @Volatile private var transport:ObdTransport?=null
    private var lock:PowerManager.WakeLock?=null
    private lateinit var alerts:DpfNotifications
    private lateinit var log:TechnicalLog
    private lateinit var machine:RegenMachine
    override fun onBind(intent:Intent?)=null
    override fun onCreate() {
        super.onCreate();alerts=DpfNotifications(this);log=TechnicalLog(this)
        machine=RegenMachine(prefs.getLong("activeSince",0L))
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(!prefs.getBoolean("enabled",false) || prefs.getString("address",null)==null) { stopSelf();return START_NOT_STICKY }
        if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) { stopSelf();return START_NOT_STICKY }
        startForeground(1,alerts.ongoing("Astra DPF Monitor","In attesa del Vgate"),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        if(!running) { running=true;worker.execute { monitor() } }
        return START_STICKY
    }
    private fun persist() { prefs.edit().putLong("activeSince",machine.activeSince).apply() }
    private fun publish(reading:Reading?=null, connected:Boolean=false, detail:String="", status:String=machine.state) {
        MonitorBus.publish(mapOf("connected" to connected,"status" to status,"regen" to machine.state,"saturation" to reading?.saturation,"distance" to reading?.distance,
            "elapsed" to if(machine.activeSince>0 && !machine.uncertain) ((System.currentTimeMillis()-machine.activeSince)/1000).coerceAtLeast(0) else null,"detail" to detail))
    }
    private fun monitor() {
        var retry=0
        while(running) {
            try {
                val adapter=getSystemService(android.bluetooth.BluetoothManager::class.java).adapter
                if(adapter==null || !adapter.isEnabled) throw java.io.IOException("Bluetooth spento")
                val device=adapter.getRemoteDevice(prefs.getString("address","")!!)
                val t=if(prefs.getString("transport","classic")=="ble") BleTransport(this,device) else ClassicTransport(device)
                transport=t
                val deadline=deadlines.schedule({ try { t.close() } catch(_:Exception) {} },25,TimeUnit.SECONDS)
                try { t.connect() } finally { deadline.cancel(false) }
                lock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"astra:obd").also { it.acquire(10*60*1000L) }
                for(command in ElmProtocol.initialization) {
                    val response=exchange(t,command)
                    if(command!="ATZ" && !response.contains("OK",true)) throw java.io.IOException("Inizializzazione ELM rifiutata: $command")
                }
                var invalidSamples=0;log.record("connected")
                while(running) {
                    if(lock?.isHeld!=true) lock?.acquire(10*60*1000L)
                    val replies=ElmProtocol.requests.associateWith { command->exchange(t,command) }
                    if(!running) break
                    val reading=ElmProtocol.decode(replies)
                    if(reading.active==null && reading.saturation==null && reading.distance==null) invalidSamples++ else { invalidSamples=0;retry=0 }
                    if(invalidSamples>=3) throw java.io.IOException("ECU non risponde: attesa prossimo viaggio")
                    val transition=machine.sample(reading.active,System.currentTimeMillis(),reading.distance)
                    persist()
                    transition?.let {
                        when(it.kind) {
                            "started"->alerts.alert(2,"Rigenerazione DPF iniziata","${saturation(reading)}\nDurata stimata non disponibile")
                            "completed"->{
                                val history=org.json.JSONArray(prefs.getString("history","[]"))
                                history.put(JSONObject().put("ended",System.currentTimeMillis()).put("durationMillis",it.duration).put("finalSaturation",reading.saturation))
                                val bounded=org.json.JSONArray();for(i in maxOf(0,history.length()-20) until history.length()) bounded.put(history.get(i))
                                prefs.edit().putString("history",bounded.toString()).apply()
                                alerts.alert(3,"Rigenerazione DPF completata","Durata osservata: ${it.duration/60000} min ${it.duration/1000%60} sec\n${saturation(reading)}")
                            }
                            "unknown"->alerts.alert(4,"Stato rigenerazione non determinabile","Rigenerazione non più osservata. Completamento non confermato.")
                        }
                    }
                    val detail=if(ElmProtocol.requests.isEmpty()) "Profilo ECU non verificato: dati DPF non disponibili" else "Profilo B16DT: verifica necessaria sulla tua Astra"
                    publish(reading,true,detail)
                    if(machine.state=="active") alerts.update("Rigenerazione DPF in corso","${saturation(reading)}\n"+if(machine.uncertain) "Inizio non osservato: durata non disponibile" else "Durata trascorsa: ${(System.currentTimeMillis()-machine.activeSince)/60000} min")
                    else alerts.update("Astra DPF Monitor",if(reading.active==null) "Dati ECU non disponibili" else "Monitoraggio DPF attivo")
                    Thread.sleep(3000)
                }
            } catch(_:InterruptedException) { break }
            catch(error:Exception) {
                machine.gap();persist();publish(detail="Connessione assente o risposta non valida",status="disconnected")
                log.record("connection_or_read_error_${error.javaClass.simpleName}")
                alerts.update("Astra DPF Monitor","Vgate non disponibile. Riconnessione automatica.")
            } finally {
                try { transport?.close() } catch(_:Exception) {};transport=null
                if(lock?.isHeld==true) lock?.release();lock=null
            }
            if(running) try { Thread.sleep(minOf(120_000L,2000L shl retry.coerceAtMost(6)));retry++ } catch(_:InterruptedException) { break }
        }
    }
    private fun exchange(t:ObdTransport,command:String):String {
        val timeout=deadlines.schedule({ try { t.close() } catch(_:Exception) {} },if(command=="ATZ") 10 else 6,TimeUnit.SECONDS)
        return try { t.command(command).also { log.record("reply",command,it) } } finally { timeout.cancel(false) }
    }
    private fun saturation(r:Reading)="Saturazione DPF: "+(r.saturation?.let { "${"%.1f".format(it)}%" } ?: "non disponibile")
    override fun onDestroy() {
        running=false;try { transport?.close() } catch(_:Exception) {};worker.shutdownNow();deadlines.shutdownNow()
        machine.gap();persist();publish(detail="Monitoraggio arrestato",status="disconnected");super.onDestroy()
    }
    companion object {
        fun start(context:Context) {
            try { context.startForegroundService(Intent(context,MonitorService::class.java)) }
            catch(_:Exception) { MonitorBus.publish(mapOf("connected" to false,"status" to "disconnected","regen" to "unknown","detail" to "Android richiede di aprire l’app per riattivare il monitoraggio")) }
        }
    }
}
