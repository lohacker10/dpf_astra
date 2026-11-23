package it.astra.astra_dpf_monitor

import org.junit.Assert.*
import org.junit.Test

/** Synthetic ELM replies exist only in tests, never in the vehicle transport. */
class ObdSimulationTest {
    private fun reply(soot: Int, active: Int, km: Int) = ElmProtocol.decode(mapOf(
        "22336A" to "62336A%02X".format(soot),
        "2220F2" to "6220F2%02X".format(active),
        "223039" to "623039%04X".format(km)
    ))
    @Test fun simulateWholeTripWithDuplicatePollsAndNotifications() {
        val machine = RegenMachine()
        val notifications = mutableListOf<String>()
        var time = 1000L
        for ((soot, active, km) in listOf(
            Triple(78, 0, 247), Triple(78, 1, 247), Triple(42, 1, 250),
            Triple(18, 0, 0), Triple(18, 0, 0)
        )) {
            repeat(4) {
                val r = reply(soot, active, km)
                machine.sample(r.active, time, r.distance)?.let { notifications.add(it.kind) }
                time += 3000
            }
        }
        assertEquals(listOf("started", "completed"), notifications)
        assertEquals("monitoring", machine.state)
    }
    @Test fun simulateBluetoothLossAndReconnectNeverInventsCompletion() {
        val machine = RegenMachine()
        var time = 1000L
        for (active in listOf(0, 1)) repeat(3) {
            val r = reply(78, active, 247)
            machine.sample(r.active, time, r.distance)
            time += 3000
        }
        machine.gap()
        val notifications = mutableListOf<String>()
        repeat(4) {
            val r = reply(18, 0, 0)
            machine.sample(r.active, time, r.distance)?.let { notifications.add(it.kind) }
            time += 3000
        }
        assertEquals(listOf("unknown"), notifications)
    }
}
