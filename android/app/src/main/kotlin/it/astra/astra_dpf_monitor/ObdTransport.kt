package it.astra.astra_dpf_monitor

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import java.io.Closeable
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

interface ObdTransport : Closeable { fun connect(); fun command(command: String): String }
@SuppressLint("MissingPermission")
class ClassicTransport(private val device: BluetoothDevice) : ObdTransport {
    private val socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"))
    override fun connect() { socket.connect() }
    override fun command(command: String): String {
        require(ElmProtocol.permitted(command)) { "Comando non consentito" }
        socket.outputStream.write((command + "\r").toByteArray(Charsets.US_ASCII)); socket.outputStream.flush()
        val result=StringBuilder()
        while (result.length < 8192) { val c=socket.inputStream.read(); if(c<0) throw IOException("SPP disconnesso"); if(c==62) return result.toString(); result.append(c.toChar()) }
        throw IOException("Risposta ELM troppo lunga")
    }
    override fun close() { socket.close() }
}

@SuppressLint("MissingPermission", "NewApi")
class BleTransport(private val context: Context, private val device: BluetoothDevice) : ObdTransport {
    @Volatile private var gatt: BluetoothGatt? = null
    private var tx: BluetoothGattCharacteristic? = null
    private val ready=CountDownLatch(1)
    private var descriptorAck=CountDownLatch(1)
    private var writeAck=CountDownLatch(1)
    private val input=ArrayBlockingQueue<Int>(8192)
    @Volatile private var failure: String? = null
    @Volatile private var connected=false
    private val callback=object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g:BluetoothGatt,status:Int,newState:Int) {
            if(status==BluetoothGatt.GATT_SUCCESS && newState==BluetoothProfile.STATE_CONNECTED) { connected=true; if(!g.discoverServices()) fail("Impossibile leggere servizi BLE") }
            else { connected=false;fail("BLE disconnesso ($status)") }
        }
        override fun onServicesDiscovered(g:BluetoothGatt,status:Int) {
            if(status!=BluetoothGatt.GATT_SUCCESS) { fail("Servizi BLE non disponibili");return }
            // These are UART profiles, not arbitrary writable GATT characteristics.
            val profiles=listOf(
                Triple("000018f0-0000-1000-8000-00805f9b34fb","00002af1-0000-1000-8000-00805f9b34fb","00002af0-0000-1000-8000-00805f9b34fb"),
                Triple("e7810a71-73ae-499d-8c15-faa9aef0c3f2","bef8d6c9-9c21-4c9e-b632-bd58c1009f9f","bef8d6c9-9c21-4c9e-b632-bd58c1009f9f"),
                Triple("0000fff0-0000-1000-8000-00805f9b34fb","0000fff2-0000-1000-8000-00805f9b34fb","0000fff1-0000-1000-8000-00805f9b34fb"),
                Triple("6e400001-b5a3-f393-e0a9-e50e24dcca9e","6e400002-b5a3-f393-e0a9-e50e24dcca9e","6e400003-b5a3-f393-e0a9-e50e24dcca9e"))
            for ((service,write,notify) in profiles) {
                val s=g.getService(UUID.fromString(service)) ?: continue
                val w=s.getCharacteristic(UUID.fromString(write)) ?: continue
                val n=s.getCharacteristic(UUID.fromString(notify)) ?: continue
                if(w.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)==0) continue
                val notification=n.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                val indication=n.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                if(!notification && !indication) continue
                val descriptor=n.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) ?: continue
                tx=w
                if(!g.setCharacteristicNotification(n,true)) { fail("Notifiche UART non disponibili");return }
                descriptor.value=if(notification) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                if(!g.writeDescriptor(descriptor)) fail("Configurazione UART fallita")
                return
            }
            fail("Variante BLE non riconosciuta: nessun profilo UART compatibile")
        }
        override fun onDescriptorWrite(g:BluetoothGatt,d:BluetoothGattDescriptor,status:Int) { if(status!=0) failure="CCCD fallito ($status)";descriptorAck.countDown();ready.countDown() }
        override fun onCharacteristicWrite(g:BluetoothGatt,c:BluetoothGattCharacteristic,status:Int) { if(status!=0) failure="Scrittura UART fallita ($status)";writeAck.countDown() }
        @Deprecated("API legacy callback") override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic) { accept(c.value ?: byteArrayOf()) }
        override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic,value:ByteArray) { accept(value) }
    }
    private fun accept(bytes:ByteArray) { for(b in bytes) if(!input.offer(b.toInt() and 255)) fail("Buffer UART pieno") }
    private fun fail(message:String) { failure=message;ready.countDown();descriptorAck.countDown();writeAck.countDown();input.offer(-1) }
    override fun connect() {
        gatt=device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE)
        if(!ready.await(20,TimeUnit.SECONDS)) throw IOException("Timeout connessione BLE")
        failure?.let { throw IOException(it) };if(!connected || tx==null) throw IOException("UART assente")
    }
    override fun command(command:String):String {
        require(ElmProtocol.permitted(command)) { "Comando non consentito" }
        failure?.let { throw IOException(it) }
        val g=gatt ?: throw IOException("BLE chiuso"); val c=tx ?: throw IOException("UART assente")
        input.clear();writeAck=CountDownLatch(1)
        val withResponse=c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        c.writeType=if(withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        c.value=(command+"\r").toByteArray(Charsets.US_ASCII)
        if(!g.writeCharacteristic(c)) throw IOException("UART occupata")
        if(withResponse && !writeAck.await(5,TimeUnit.SECONDS)) throw IOException("Timeout scrittura UART")
        failure?.let { throw IOException(it) }
        val result=StringBuilder();val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while(result.length<8192) {
            val wait=deadline-System.nanoTime();if(wait<=0) throw IOException("Timeout ELM")
            val b=input.poll(wait,TimeUnit.NANOSECONDS) ?: throw IOException("Timeout ELM")
            if(b<0) throw IOException(failure ?: "BLE disconnesso");if(b==62) return result.toString();result.append(b.toChar())
        }
        throw IOException("Risposta ELM troppo lunga")
    }
    override fun close() { connected=false;gatt?.disconnect();gatt?.close();gatt=null;fail("Connessione chiusa") }
}
