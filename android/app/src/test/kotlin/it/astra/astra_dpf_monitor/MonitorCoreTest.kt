package it.astra.astra_dpf_monitor
import org.junit.Assert.*
import org.junit.Test
class MonitorCoreTest {
    private fun feed(m:RegenMachine,active:Boolean,from:Long,distance:Double?=200.0):List<Transition> = (0..2).mapNotNull { m.sample(active,from+it*3000,distance) }
    @Test fun coherentTransitionsAndDistanceResetAreDeduplicated() { val m=RegenMachine();feed(m,false,1000);assertEquals("started",feed(m,true,10000).single().kind);assertFalse(m.uncertain);assertTrue(feed(m,true,19000).isEmpty());assertEquals("completed",feed(m,false,28000,0.0).single().kind);assertTrue(feed(m,false,37000,0.0).isEmpty()) }
    @Test fun flagOffWithoutResetIsNotCompletion() { val m=RegenMachine();feed(m,false,1000);feed(m,true,10000);assertEquals("unknown",feed(m,false,19000).single().kind) }
    @Test fun disconnectIsNeverCompletion() { val m=RegenMachine();feed(m,false,1000);feed(m,true,10000);m.gap();assertEquals("unknown",feed(m,false,19000,0.0).single().kind) }
    @Test fun isolatedSpikesDoNotTrigger() { val m=RegenMachine();m.sample(true,1000);m.sample(false,4000);m.sample(true,7000);assertEquals(0L,m.activeSince) }
    @Test fun startupDuringActiveHasUnknownDuration() { val m=RegenMachine();assertEquals("started",feed(m,true,1000).single().kind);assertTrue(m.uncertain);assertEquals("unknown",feed(m,false,10000,0.0).single().kind) }
    @Test fun restartIsUncertain() { val m=RegenMachine(1000);assertEquals("unknown",feed(m,false,2000,0.0).single().kind) }
    @Test fun staleSamplesCannotComplete() { val m=RegenMachine();feed(m,false,1000);feed(m,true,10000);assertEquals("unknown",feed(m,false,60000,0.0).single().kind) }
    @Test fun parserRejectsWrongDidNegativeAndMixedResponses() { assertNotNull(ElmProtocol.payload("62 20 F2 01\r>","20F2",1));assertNull(ElmProtocol.payload("7F2231","20F2",1));assertNull(ElmProtocol.payload("6220F201\r6220F200","20F2",1));assertNull(ElmProtocol.payload("6220F2","20F2",1));assertNull(ElmProtocol.payload("7E8046220F201","20F2",1)) }
    @Test fun verifiedDirectPercentAndDistance() { val r=ElmProtocol.decode(mapOf("22336A" to "62336A4E","2220F2" to "6220F201","223039" to "62303900F7"));assertEquals(78.0,r.saturation!!,0.0);assertEquals(247.0,r.distance!!,0.0);assertEquals(true,r.active) }
    @Test fun statusIsNonzeroNotBitZeroOnly() { assertEquals(true,ElmProtocol.decode(mapOf("2220F2" to "6220F202")).active);assertEquals(false,ElmProtocol.decode(mapOf("2220F2" to "6220F200")).active) }
    @Test fun malformedSamplesStayUnavailable() { assertEquals(Reading(null,null,null),ElmProtocol.decode(mapOf("22336A" to "NO DATA","2220F2" to "?","223039" to "STOPPED"))) }
    @Test fun singleDistanceGlitchCannotComplete() { val m=RegenMachine();feed(m,false,1000);feed(m,true,10000);m.sample(true,19000,0.0);m.sample(true,22000,200.0);assertEquals("unknown",feed(m,false,25000,200.0).single().kind) }
    @Test fun overloadedSaturationKeepsDirectPercent() { assertEquals(255.0,ElmProtocol.decode(mapOf("22336A" to "62336AFF")).saturation!!,0.0) }
    @Test fun missingDistanceDoesNotHideVerifiedRegeneration() {
        val r=ElmProtocol.decode(mapOf("22336A" to "62336A4E", "2220F2" to "6220F201", "223039" to "NO DATA"))
        assertEquals(78.0,r.saturation!!,0.0);assertEquals(true,r.active);assertNull(r.distance)
    }
    @Test fun writesAreRejected() { listOf("04","2E20F201","3101","ATSH7DF","0902").forEach { assertFalse(ElmProtocol.permitted(it)) } }
}
