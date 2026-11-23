package it.astra.astra_dpf_monitor

import android.companion.CompanionDeviceService
import android.content.*
import android.companion.CompanionDeviceManager
import android.annotation.SuppressLint

class CompanionPresenceService:CompanionDeviceService() {
    @Deprecated("String API supported from Android 12") override fun onDeviceAppeared(address:String) {
        val prefs=getSharedPreferences("monitor",0)
        if(prefs.getBoolean("enabled",false) && prefs.getString("address",null)==address) MonitorService.start(this)
    }
    // Loss of presence is not a regeneration transition; service retries its existing read-only session.
    @Deprecated("String API supported from Android 12") override fun onDeviceDisappeared(address:String) {}
}
class RecoveryReceiver:BroadcastReceiver() {
    @SuppressLint("MissingPermission") override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!=Intent.ACTION_BOOT_COMPLETED && intent.action!=android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED) return
        if(intent.action==android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED && intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE,-1)!=android.bluetooth.BluetoothAdapter.STATE_ON) return
        val prefs=context.getSharedPreferences("monitor",0)
        if(!prefs.getBoolean("enabled",false)) return
        val address=prefs.getString("address",null) ?: return
        try { context.getSystemService(CompanionDeviceManager::class.java).startObservingDevicePresence(address) } catch(_:Exception) {}
        if(context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)==android.content.pm.PackageManager.PERMISSION_GRANTED) MonitorService.start(context)
        // Connected-device FGS can start at boot; denied starts remain visible on the next app launch.
    }
}
