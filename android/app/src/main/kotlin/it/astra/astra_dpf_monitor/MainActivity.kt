package it.astra.astra_dpf_monitor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanResult
import android.companion.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

@SuppressLint("MissingPermission")
class MainActivity:FlutterActivity() {
    private val prefs by lazy { getSharedPreferences("monitor",0) }
    private var pendingAssociation:MethodChannel.Result?=null
    private var pendingPermission:MethodChannel.Result?=null
    private var pendingExport:MethodChannel.Result?=null
    private var chosenTransport="classic"
    private val companion by lazy { getSystemService(CompanionDeviceManager::class.java) }
    override fun configureFlutterEngine(flutterEngine:FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger,"astra/control").setMethodCallHandler { call,result->
            try { when(call.method) {
                "devices"->{
                    if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) { result.success(emptyList<Any>());return@setMethodCallHandler }
                    result.success(getSystemService(BluetoothManager::class.java).adapter?.bondedDevices?.map { mapOf("name" to (it.name ?: "Dispositivo Bluetooth"),"address" to it.address,"type" to when(it.type){BluetoothDevice.DEVICE_TYPE_LE->"ble";BluetoothDevice.DEVICE_TYPE_DUAL->"dual";else->"classic"}) } ?: emptyList<Any>())
                }
                "associate"->{
                    if(pendingAssociation!=null) { result.error("busy","Associazione già in corso",null);return@setMethodCallHandler }
                    chosenTransport=call.argument<String>("transport") ?: "classic"
                    require(chosenTransport in listOf("classic","ble"))
                    val address=call.argument<String>("address")
                    if(address!=null) require(BluetoothAdapter.checkBluetoothAddress(address))
                    val request=AssociationRequest.Builder().setSingleDevice(address!=null)
                    if(chosenTransport=="ble") {
                        val scan=android.bluetooth.le.ScanFilter.Builder().apply { if(address!=null) setDeviceAddress(address) }.build()
                        request.addDeviceFilter(BluetoothLeDeviceFilter.Builder().setScanFilter(scan).build())
                    } else request.addDeviceFilter(BluetoothDeviceFilter.Builder().apply { if(address!=null) setAddress(address) }.build())
                    pendingAssociation=result
                    companion.associate(request.build(),object:CompanionDeviceManager.Callback(){
                        override fun onDeviceFound(chooserLauncher:IntentSender) { runOnUiThread { try { startIntentSenderForResult(chooserLauncher,41,null,0,0,0) } catch(e:Exception) { failAssociation(e.message ?: "Associazione fallita") } } }
                        override fun onFailure(error:CharSequence?) { runOnUiThread { failAssociation(error?.toString() ?: "Associazione annullata") } }
                    },Handler(Looper.getMainLooper()))
                }
                "permissions"->{
                    val required=mutableListOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN)
                    if(Build.VERSION.SDK_INT>=33) required+=Manifest.permission.POST_NOTIFICATIONS
                    val missing=required.filter { checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED }
                    if(missing.isEmpty()) result.success(permissionSnapshot()) else if(pendingPermission!=null) result.error("busy","Richiesta già in corso",null) else { pendingPermission=result;requestPermissions(missing.toTypedArray(),42) }
                }
                "start"->{ require(prefs.getString("address",null)!=null) { "Seleziona prima il Vgate" };prefs.edit().putBoolean("enabled",true).apply();MonitorService.start(this);result.success(true) }
                "stop"->{ prefs.edit().putBoolean("enabled",false).apply();stopService(Intent(this,MonitorService::class.java));result.success(true) }
                "getSnapshot"->result.success(MonitorBus.snapshot)
                "diagnostics"->result.success("Trasporto: ${prefs.getString("transport","non configurato")}\nAssociazione companion: ${companion.associations.isNotEmpty()}\nMonitoraggio abilitato: ${prefs.getBoolean("enabled",false)}\nLog grezzi: ${prefs.getBoolean("logging",false)}\nProfilo ECU: da validare sul veicolo\n${MonitorBus.snapshot["detail"] ?: ""}")
                "logging"->{ call.argument<Boolean>("enabled")?.let { prefs.edit().putBoolean("logging",it).apply() };result.success(prefs.getBoolean("logging",false)) }
                "exportLogs"->{ if(pendingExport!=null) result.error("busy","Esportazione già in corso",null) else { pendingExport=result;startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/x-ndjson").putExtra(Intent.EXTRA_TITLE,"astra-dpf-log.jsonl"),43) } }
                "settings"->{
                    val section=call.argument<String>("section")
                    val intent=if(section=="notifications") Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))
                    startActivity(intent);result.success(true)
                }
                else->result.notImplemented()
            } } catch(e:Exception) { if(pendingAssociation===result) pendingAssociation=null;result.error("android",e.message ?: "Operazione non riuscita",null) }
        }
        EventChannel(flutterEngine.dartExecutor.binaryMessenger,"astra/events").setStreamHandler(object:EventChannel.StreamHandler {
            override fun onListen(arguments:Any?,events:EventChannel.EventSink) { MonitorBus.listener={ events.success(it) };events.success(MonitorBus.snapshot) }
            override fun onCancel(arguments:Any?) { MonitorBus.listener=null }
        })
    }
    private fun failAssociation(message:String) { pendingAssociation?.error("association",message,null);pendingAssociation=null }
    private fun permissionSnapshot()=mapOf("bluetooth" to (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED),"notifications" to (Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED))
    @Deprecated("Activity result compatibility") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==41) {
            if(resultCode!=Activity.RESULT_OK) { failAssociation("Associazione annullata");return }
            try {
                val selected=data?.getParcelableExtra<android.os.Parcelable>(CompanionDeviceManager.EXTRA_DEVICE)
                val info=if(Build.VERSION.SDK_INT>=33) data?.getParcelableExtra<AssociationInfo>(CompanionDeviceManager.EXTRA_ASSOCIATION) else null
                val address=when(selected) { is BluetoothDevice->selected.address;is ScanResult->selected.device.address;else->if(Build.VERSION.SDK_INT>=33) info?.deviceMacAddress?.toString() else null } ?: throw IllegalStateException("Indirizzo companion non disponibile")
                prefs.edit().putString("address",address).putString("transport",chosenTransport).putBoolean("enabled",true).apply()
                companion.startObservingDevicePresence(address)
                MonitorService.start(this)
                pendingAssociation?.success(true);pendingAssociation=null
            } catch(e:Exception) { failAssociation(e.message ?: "Associazione fallita") }
        }
        if(requestCode==43) {
            try { val uri=data?.data;if(resultCode==Activity.RESULT_OK && uri!=null) { contentResolver.openOutputStream(uri)?.use { it.write(TechnicalLog(this).text().toByteArray()) } ?: throw IllegalStateException("File non disponibile");pendingExport?.success(true) } else pendingExport?.success(false) }
            catch(e:Exception) { pendingExport?.error("export",e.message,null) };pendingExport=null
        }
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) { super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==42) { pendingPermission?.success(permissionSnapshot());pendingPermission=null } }
    override fun onDestroy() { MonitorBus.listener=null;super.onDestroy() }
}
