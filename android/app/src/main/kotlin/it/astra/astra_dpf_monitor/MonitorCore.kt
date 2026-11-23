package it.astra.astra_dpf_monitor

/** Only decoded, coherent ECU samples enter the state machine. Offline intervals never imply completion. */
data class Reading(val saturation: Double?, val distance: Double?, val active: Boolean?)
data class Transition(val kind: String, val started: Long, val duration: Long)
class RegenMachine(var activeSince: Long = 0L, var uncertain: Boolean = activeSince != 0L) {
    var state = if (activeSince != 0L) "unknown" else "disconnected"
        private set
    private var candidate: Boolean? = null
    private var count = 0
    private var last = 0L
    private var baseline = false
    private var startDistance: Double? = null
    private var distanceReset = 0
    fun gap() { candidate = null; count = 0; baseline = false; startDistance = null; distanceReset = 0; if (activeSince != 0L) uncertain = true; state = "unknown" }
    fun sample(active: Boolean?, now: Long, distance: Double? = null): Transition? {
        if (active == null) { gap(); return null }
        if (last != 0L && (now <= last || now - last > 20_000)) gap()
        last = now
        val previous = startDistance
        distanceReset = if (activeSince != 0L && previous != null && distance != null && previous > distance + 1) distanceReset + 1 else 0
        if (candidate == active) count++ else { candidate = active; count = 1 }
        if (count < 3) return null
        if (active) {
            state = "active"
            if (activeSince == 0L) { activeSince = now; uncertain = !baseline; distanceReset = 0; startDistance = distance; return Transition("started", now, 0) }
        } else {
            state = "monitoring"; baseline = true
            if (activeSince != 0L) {
                val start = activeSince
                val kind = if (uncertain || distanceReset < 3) "unknown" else "completed"
                activeSince = 0; uncertain = false; distanceReset = 0
                return Transition(kind, start, now - start)
            }
        }
        return null
    }
}

object ElmProtocol {
    // Only adapter configuration and fixed diagnostic reads are accepted at the transport boundary.
    val initialization = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP6", "ATCAF1", "ATSH7E0")
    val requests = listOf("22336A", "2220F2", "223039")
    fun permitted(command: String) = command in initialization || command in requests
    fun payload(raw: String, did: String, bytes: Int): ByteArray? {
        if (raw.length > 8192) return null
        val lines = raw.uppercase().replace(">", "").split('\r', '\n').map { it.replace(" ", "").trim() }.filter { it.isNotEmpty() && it != "22$did" }
        if (lines.size != 1) return null
        val hex = lines.single()
        if (!hex.startsWith("62$did") || hex.length != 6 + bytes * 2 || !hex.matches(Regex("[0-9A-F]+"))) return null
        return ByteArray(bytes) { hex.substring(6 + it * 2, 8 + it * 2).toInt(16).toByte() }
    }
    fun decode(replies: Map<String, String>): Reading {
        val soot = replies["22336A"]?.let { payload(it,"336A",1) }
        val regen = replies["2220F2"]?.let { payload(it,"20F2",1) }
        val km = replies["223039"]?.let { payload(it,"3039",2) }
        return Reading(soot?.let { (it[0].toInt() and 255).toDouble() },
            km?.let { (((it[0].toInt() and 255) shl 8) or (it[1].toInt() and 255)).toDouble() },
            regen?.let { (it[0].toInt() and 255) > 0 })
    }
}
