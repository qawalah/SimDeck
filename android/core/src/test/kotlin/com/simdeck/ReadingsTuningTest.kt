package com.simdeck

import com.simdeck.model.BeamNGCommands
import com.simdeck.model.ControlType
import com.simdeck.model.GameAction
import com.simdeck.model.Page
import com.simdeck.model.ReadingInfo
import com.simdeck.model.Readings
import com.simdeck.model.TuningState
import com.simdeck.model.TuningVar
import com.simdeck.model.VehicleFeature
import com.simdeck.profiles.BuiltInProfiles
import com.simdeck.profiles.ProfileJson
import com.simdeck.profiles.Presets
import com.simdeck.protocol.Incoming
import com.simdeck.protocol.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** SimDeck 1.4: live readings, the grouped command picker, and the tuning page. */
class ReadingsTuningTest {
    @Test
    fun readingsCatalogueIsGroupedAndIncludesWhatTheVehicleReports() {
        val info = listOf(
            ReadingInfo("oil.temp", "Engine", "Oil temperature", "C"),
            ReadingInfo("brake.FL.temp", "Brakes", "Brake temperature FL", "C"),
            ReadingInfo("tire.RR.deflated", "Tyres", "Tyre deflated RR", ""),
            ReadingInfo("e.watertemp", "All signals", "watertemp", ""),
        )
        val cat = Readings.catalog(info, listOf("has.diff.front", "hydro.dump_bed", "speed"))
        val groups = cat.map { it.group }.distinct()
        assertEquals(groups, groups.sortedBy { Readings.GROUPS.indexOf(it) }, "groups in picker order")
        assertEquals(1, cat.count { it.key == "oil.temp" }, "known readings are not repeated")
        val brake = cat.single { it.key == "brake.FL.temp" }
        assertEquals("°C", brake.unit); assertEquals("Brakes", brake.group)
        assertTrue(cat.single { it.key == "tire.RR.deflated" }.lamp, "deflated tyres are lamps")
        assertEquals("All signals", cat.single { it.key == "e.watertemp" }.group)
        assertEquals("Vehicle has", cat.single { it.key == "has.diff.front" }.group)
        assertEquals("Hydraulics", cat.single { it.key == "hydro.dump_bed" }.group)
        // search: every word must match, in title, key, group or keywords
        assertEquals(listOf("oil.temp", "warning.oil"), Readings.search(cat, "oil temp").map { it.key })
        assertTrue(Readings.search(cat, "coolant").any { it.key == "coolant.temp" })
        assertTrue(Readings.search(cat, "water").any { it.key == "coolant.temp" }, "keywords: water finds coolant")
        assertTrue(Readings.search(cat, "brake FL").any { it.key == "brake.FL.temp" })
    }

    @Test
    fun readingsFormatAndBecomeGaugesOrLamps() {
        val speed = Readings.forKey("speed")
        assertEquals("72 km/h", Readings.format(speed, 20.0))
        assertEquals("0.52 g", Readings.format(Readings.forKey("g.x"), 0.5199))
        assertEquals("On", Readings.format(Readings.forKey("warning.oil"), true))
        val oil = Readings.control(Readings.forKey("oil.temp"))
        assertEquals(ControlType.GAUGE, oil.type); assertEquals("oil.temp", oil.feedbackKey); assertEquals("steam", oil.skin)
        assertTrue(oil.bands.isNotEmpty() && oil.max == 160.0)
        assertEquals(ControlType.INDICATOR, Readings.control(Readings.forKey("warning.checkengine")).type)
        // the mod's units: ratios become %, m/s km/h
        val ratio = Readings.forKey("x.ratio", listOf(ReadingInfo("x.ratio", "Engine", "Something", "ratio")))
        assertEquals(100.0, ratio.scale); assertEquals("%", ratio.unit)
        // presets: every known reading, BeamNG only
        assertEquals(Readings.known.size, Presets.readings.size)
        assertTrue(Presets.search(Presets.builtIn, "coolant").any { it.control.feedbackKey == "coolant.temp" })
    }

    @Test
    fun theWatchListIsWhatTheProfileShows() {
        val bng = BuiltInProfiles.beamng()
        val keys = Readings.watchKeys(bng)
        assertTrue("oil.temp" in keys && "brake.FL.temp" in keys && "warning.checkengine" in keys && "hazard" in keys)
        assertEquals(keys.sorted(), keys)
        val msg = Protocol.json.parseToJsonElement(Protocol.readingsWatch(keys, all = true)).toString()
        assertTrue("\"type\":\"readings.watch\"" in msg && "\"all\":true" in msg && "\"oil.temp\"" in msg)
    }

    @Test
    fun commandsAreGroupedSearchableAndFilteredForSliders() {
        val vehicle = listOf(
            VehicleFeature("action.toggle_tailgate", "Tailgate", "button", false, "input_actions"),
            VehicleFeature("hydro.dump_bed", "Dump bed", "axis", true, "hydro"),
        )
        val actions = listOf(GameAction("toggle_traffic", "Traffic on/off", "gameplay"), GameAction("accelerate", "Accelerate", "vehicle", axis = true))
        val all = BeamNGCommands.catalog(vehicle, actions, reported = listOf("hazard.toggle", "brand.new"))
        assertEquals("This vehicle", all.first().group, "the vehicle's own functions come first")
        assertTrue(all.any { it.id == "hazard.toggle" && it.group == "Lights & signals" })
        assertTrue(all.any { it.id == "hazard" && it.title.contains("on / off") }, "set and flip variants")
        assertTrue(all.any { it.id == "brand.new" && it.group == "Other commands" }, "commands this app doesn't know are still offered")
        assertTrue(all.any { it.id == "action.toggle_traffic" && it.group.startsWith("Game actions") })
        assertEquals(1, all.count { it.id == "hydro.dump_bed" })
        val axes = BeamNGCommands.catalog(vehicle, actions, axisOnly = true)
        assertEquals(setOf("hydro.dump_bed", "action.accelerate"), axes.map { it.id }.toSet(), "a slider only gets what takes a value")
        assertEquals(listOf("diff.rear.toggle", "diff.rear"), BeamNGCommands.search(all, "rear diff").map { it.id })
        assertTrue(BeamNGCommands.search(all, "blinker").any { it.id == "signal.left.toggle" }, "keywords")
        assertEquals("Hazard lights", BeamNGCommands.title("hazard.toggle"))
    }

    private fun v(unit: String = "psi", min: Double = 0.0, max: Double = 50.0, minDis: Double? = null, maxDis: Double? = null, step: Double? = null, value: Double = 30.0) =
        TuningVar("\$x", "X", "Wheels", null, null, unit, min, max, minDis, maxDis, step, 30.0, value)

    @Test
    fun tuningShowsValuesLikeTheGame() {
        val psi = v(step = 0.5)
        assertEquals("30.0 psi", psi.format(30.0))
        assertEquals(30.5, psi.snap(30.4)); assertEquals(50.0, psi.snap(80.0), "clamped to the range")
        // display range: 0..1 shown as 0..100
        val bias = v(unit = "%", min = 0.0, max = 1.0, minDis = 0.0, maxDis = 100.0, step = 1.0, value = 0.6)
        assertEquals("60 %", bias.format(0.6)); assertEquals(0.62, bias.fromDisplay(62.0), 1e-9)
        assertEquals(0.63, bias.snap(0.6312), 1e-9)
        // no unit: -100 % .. 100 % in 1 % steps
        val blank = v(unit = "", min = -0.04, max = 0.06, value = 0.01)
        assertEquals(-100.0, blank.displayMin); assertEquals("0 %", blank.format(0.01))
        assertEquals(0.06, blank.fromDisplay(100.0), 1e-12)
        // no step given: a round step from the range
        assertEquals(0.5, v(min = 0.0, max = 50.0).displayStep)
        assertEquals(2, v(min = 0.0, max = 1.0, step = 0.01).decimals)
        // pending edits and grouping
        val st = TuningState("pickup", listOf(psi, bias.copy(name = "\$b", category = "Brakes")), pending = mapOf("\$x" to 32.0))
        assertTrue(st.dirty); assertEquals(32.0, st.valueOf(psi))
        assertEquals(listOf("Wheels", "Brakes"), st.grouped().map { it.first })
        assertEquals(listOf("Brakes"), st.grouped("brakes").map { it.first })
        assertFalse(st.copy(pending = mapOf("\$x" to 30.0)).dirty, "an edit back to the applied value is not a change")
    }

    @Test
    fun tuningMessagesAndTheTuningPage() {
        val tuning = Protocol.parse("""{"v":1,"type":"beamng.tuning","vehicle":"pickup","list":[
            {"name":"${'$'}tirepressure_F","title":"Tire Pressure","category":"Wheels","subCategory":"Front","unit":"psi","min":0,"max":50,"default":30,"val":28},
            {"name":"${'$'}broken","title":"No range"},
            {"name":"${'$'}bias","category":"Brakes","unit":"","min":0,"max":1,"minDis":0,"maxDis":100,"stepDis":1,"default":0.6}]}""")
        assertTrue(tuning is Incoming.BeamNGTuning)
        tuning as Incoming.BeamNGTuning
        assertEquals(2, tuning.list.size, "settings without a range are dropped")
        assertEquals(28.0, tuning.list[0].value); assertEquals("Front", tuning.list[0].subCategory)
        assertEquals(0.6, tuning.list[1].value, "no val: the default"); assertEquals("bias", tuning.list[1].title)
        val empty = Protocol.parse("""{"v":1,"type":"beamng.tuning","vehicle":"","list":{}}""") as Incoming.BeamNGTuning
        assertTrue(empty.list.isEmpty(), "an empty Lua table arrives as {}")
        val res = Protocol.parse("""{"v":1,"type":"beamng.tuning.result","ok":true,"message":"Applied 1 setting."}""")
        assertEquals(Incoming.TuningResult(true, "Applied 1 setting."), res)
        val readings = Protocol.parse("""{"v":1,"type":"beamng.readings","vehicle":"pickup","list":[{"key":"oil.temp","group":"Engine","title":"Oil temperature","unit":"C"},{"group":"x"}]}""")
        assertEquals(listOf(ReadingInfo("oil.temp", "Engine", "Oil temperature", "C")), (readings as Incoming.BeamNGReadings).list)
        val apply = Protocol.tuningApply(mapOf("\$tirepressure_F" to 32.0))
        assertTrue("\"type\":\"tuning.apply\"" in apply && "\"\$tirepressure_F\":32.0" in apply)

        // the built-in BeamNG profile has a Gauges and a Tuning page; a tuning page needs schema 6
        val bng = BuiltInProfiles.beamng()
        assertNotNull(bng.pages.firstOrNull { it.auto == Page.AUTO_BEAMNG_TUNING })
        assertTrue(bng.pages.any { it.name == "Gauges" && it.controls.any { c -> c.feedbackKey == "coolant.temp" } })
        assertEquals(6, ProfileJson.schemaFor(bng))
        val back = ProfileJson.import(ProfileJson.export(bng))
        assertEquals(Page.AUTO_BEAMNG_TUNING, back.pages.last().auto)
    }
}
