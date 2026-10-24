package com.simdeck

import com.simdeck.engine.Feedback
import com.simdeck.engine.InputEngine
import com.simdeck.model.Accent
import com.simdeck.model.Action
import com.simdeck.model.AxisRange
import com.simdeck.model.Control
import com.simdeck.model.ControlType
import com.simdeck.model.GameId
import com.simdeck.model.Joystick
import com.simdeck.model.OutputRoute
import com.simdeck.model.Page
import com.simdeck.model.Profile
import com.simdeck.model.VehicleFeature
import com.simdeck.model.VehicleFeaturePages
import com.simdeck.model.VehicleFeatures
import com.simdeck.profiles.BuiltInProfiles
import com.simdeck.profiles.ProfileJson
import com.simdeck.protocol.Incoming
import com.simdeck.protocol.Protocol
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** SimDeck 1.1: rockers, display controls, vehicle feature pages, new messages, new profiles. */
@OptIn(ExperimentalCoroutinesApi::class)
class SimDeckTest {
    private val rocker = Control("zoom", ControlType.ROCKER, "Zoom", cw = Action.Key(listOf("PAGEUP")), ccw = Action.Key(listOf("PAGEDOWN")))
    private val gauge = Control("spd", ControlType.GAUGE, "Speed", feedbackKey = "speed", scale = 3.6, unit = "km/h")
    private val lamp = Control("pb", ControlType.INDICATOR, "Park", feedbackKey = "parkingbrake")
    private val pageBtn = Control("next", ControlType.PAGE, "Next", pageTarget = "next")

    private fun profile(vararg c: Control) = Profile("t", "T", pages = listOf(Page("p", "P", controls = c.toList())), output = OutputRoute.COMPANION)

    @Test fun rockerGoesOnTheWireAsTwoButtonsAndDisplayControlsNotAtAll() {
        val p = profile(rocker, gauge, lamp, pageBtn)
        val sync = Protocol.json.parseToJsonElement(Protocol.profileSync(p, emptySet())).jsonObject
        val ids = sync["bindings"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertEquals(listOf("zoom.up", "zoom.down"), ids)
        val up = Joystick.find(p.pages[0].controls, "zoom.down")!!
        assertEquals(ControlType.MOMENTARY, up.type)
        assertEquals(Action.Key(listOf("PAGEDOWN")), up.action)
    }

    @Test fun rockerHalvesDriveTheCompanion() = runTest {
        val links = FakeLinks(companionConnected = true)
        val e = InputEngine(this, links)
        e.profile = profile(rocker)
        val down = Joystick.find(e.profile!!.pages[0].controls, "zoom.down")!!
        e.onValue(down, 1f)
        e.onValue(down, 0f)
        assertEquals("zoom.down", links.companion.first().id)
        assertEquals(1f, links.companion.first().value)
        assertTrue(links.companion.any { it.value == 0f })
        e.releaseAll()
        advanceUntilIdle()
    }

    @Test fun gaugeAndLampFollowTheGame() {
        val p = profile(gauge, lamp, rocker)
        val r = Feedback.resolve(p, mapOf("zoom.up" to 1f), mapOf("speed" to 10.0, "parkingbrake" to true), emptyMap(), 0L)
        assertEquals("36", r.indicators["spd"]!!.text)
        assertTrue(r.indicators["pb"]!!.lit)
        assertTrue(r.indicators["zoom"]!!.lit, "a pressed rocker half lights the rocker")
        assertEquals("--", Feedback.resolve(p, emptyMap(), emptyMap(), emptyMap(), 0L).indicators["spd"]!!.text)
        assertEquals("12.3", Feedback.gauge(gauge.copy(scale = 1.0, decimals = 1), 12.34))
    }

    @Test fun vehicleFeaturesFillTheAutoPage() {
        val features = VehicleFeatures("pickup", listOf(
            VehicleFeature("action.toggle_tailgate", "Tailgate", "button", source = "input_actions"),
            VehicleFeature("hydro.dump_bed", "Dump bed", "axis", true, "hydro", "hydro.dump_bed"),
            VehicleFeature("coupler.tailgateCoupler.toggle", "Tailgate latch", "button", source = "coupler"),
        ))
        val p = Profile("b", "B", game = GameId.BEAMNG, pages = listOf(Page("v", "Vehicle", columns = 4, rows = 2, auto = Page.AUTO_BEAMNG_VEHICLE)))
        val filled = VehicleFeaturePages.fill(p, features)
        val c = filled.pages[0].controls
        assertEquals(3, c.size)
        val bed = c.first { it.id == "auto-hydro.dump_bed" }
        assertEquals(ControlType.SLIDER, bed.type)
        assertEquals(AxisRange.BIPOLAR, bed.range)
        assertTrue(bed.springReturn)
        assertEquals(2, bed.w)
        assertEquals(Action.BeamNG("hydro.dump_bed"), bed.action)
        assertEquals("hydro.dump_bed", bed.feedbackKey)
        assertEquals(Accent.AMBER, c.first { it.id.startsWith("auto-coupler") }.accent)
        // no overlaps, inside the grid
        val cells = c.flatMap { x -> (x.x until x.x + x.w).flatMap { i -> (x.y until x.y + x.h).map { j -> i to j } } }
        assertEquals(cells.size, cells.toSet().size)
        assertTrue(c.all { it.x + it.w <= filled.pages[0].columns && it.y + it.h <= filled.pages[0].rows })
        // the companion sees the filled controls
        val sync = Protocol.profileSync(filled, emptySet())
        assertTrue("hydro.dump_bed" in sync && "action.toggle_tailgate" in sync)
    }

    @Test fun manyFeaturesGrowTheGrid() {
        val many = (1..40).map { VehicleFeature("action.a$it", "A$it") }
        val (page, dropped) = VehicleFeaturePages.controls(Page("v", "V", columns = 8, rows = 4, auto = Page.AUTO_BEAMNG_VEHICLE), many)
        assertEquals(0, dropped)
        assertEquals(40, page.controls.size)
        assertTrue(page.columns * page.rows >= 40)
    }

    @Test fun vehicleFunctionsAreGroupedAndNoneIsDropped() {
        fun f(id: String, title: String, kind: String = "button", source: String = "input_actions") = VehicleFeature(id, title, kind, kind == "axis", source)
        assertEquals("Doors & body", VehicleFeaturePages.groupFor(f("action.toggle_tailgate", "Tailgate")))
        assertEquals("Doors & body", VehicleFeaturePages.groupFor(f("action.door_RL", "Rear left door", source = "interaction")))
        assertEquals("Couplers & towing", VehicleFeaturePages.groupFor(f("coupler.tailgateCoupler.toggle", "Tailgate latch", source = "coupler")))
        assertEquals("Machinery", VehicleFeaturePages.groupFor(f("hydro.dump_bed", "Dump bed", "axis", "hydro")))
        assertEquals("Machinery", VehicleFeaturePages.groupFor(f("action.crane_boom", "Crane boom", "axis")))
        assertEquals("Lights & sound", VehicleFeaturePages.groupFor(f("action.toggle_beacon", "Beacon")))
        assertEquals("Suspension & drivetrain", VehicleFeaturePages.groupFor(f("action.lift_axle_toggle", "Lift axle")))
        assertEquals("Suspension & drivetrain", VehicleFeaturePages.groupFor(f("action.ride_height_up", "Ride height up")))
        assertEquals("Other", VehicleFeaturePages.groupFor(f("action.radio_toggle", "Radio")))
        assertEquals("Hydraulic lever", VehicleFeaturePages.kindLabel(f("hydro.x", "X", "axis", "hydro")))
        assertTrue(VehicleFeaturePages.kindLabel(f("coupler.x.toggle", "X", source = "coupler")).startsWith("Latch"))
        assertEquals("Press", VehicleFeaturePages.kindLabel(f("action.door", "Door", source = "interaction")))
        // the Vehicle page keeps every function, however many
        val many = (1..150).map { f("action.a$it", "A$it") }
        val p = BuiltInProfiles.beamng()
        val filled = VehicleFeaturePages.fill(p, VehicleFeatures("truck", many))
        assertEquals(150, filled.pages.first { it.auto == Page.AUTO_BEAMNG_VEHICLE }.controls.size)
        assertEquals("pickup|action.toggle_tailgate", VehicleFeaturePages.favouriteKey("pickup", "action.toggle_tailgate"))
    }

    @Test fun newMessagesParse() {
        val f = Protocol.parse("""{"v":1,"type":"beamng.features","vehicle":"pickup","list":[{"id":"hydro.bed","title":"Bed","kind":"axis","centered":true,"source":"hydro","stateKey":"hydro.bed"}],"diag":{"vehicle":"pickup","sources":{"input_actions":2},"errors":[]}}""")
        assertIs<Incoming.BeamNGFeatures>(f)
        assertEquals("hydro.bed", f.features.list.single().id)
        assertTrue(f.features.diagnostics.any { it == "sources.input_actions: 2" })
        // an empty Lua table arrives as {}
        val empty = Protocol.parse("""{"v":1,"type":"beamng.features","vehicle":"","list":{}}""")
        assertIs<Incoming.BeamNGFeatures>(empty)
        assertTrue(empty.features.list.isEmpty())
        val a = Protocol.parse("""{"v":1,"type":"beamng.actions","list":[{"name":"toggle_tailgate","title":"Tailgate","vehicle":true}]}""")
        assertIs<Incoming.BeamNGActions>(a)
        assertTrue(a.list.single().vehicle)
        val n = Protocol.parse("""{"v":1,"type":"notice","level":"warn","code":"vjoy_config","message":"vJoy device 1 has 8 buttons"}""")
        assertIs<Incoming.Notice>(n)
        assertEquals("vjoy_config", n.code)
        val fg = Protocol.parse("""{"v":1,"type":"foreground","exe":"BeamNG.drive.x64.exe"}""")
        assertIs<Incoming.Foreground>(fg)
        val w = Protocol.parse("""{"v":1,"type":"welcome","capabilities":{"vjoy":true,"vjoyInfo":[{"device":1,"buttons":8,"axes":["X","Y"]}]}}""")
        assertIs<Incoming.Welcome>(w)
        assertEquals(8, w.capabilities.vjoyInfo.single().buttons)
    }

    @Test fun schema3OnlyWhenNeeded() {
        assertEquals(1, ProfileJson.schemaFor(BuiltInProfiles.keyboard()))
        assertEquals(3, ProfileJson.schemaFor(profile(rocker)))
        assertEquals(3, ProfileJson.schemaFor(profile(Control("b", ControlType.MOMENTARY, "B", guard = true))))
        val back = ProfileJson.import(ProfileJson.export(profile(rocker, gauge.copy(decimals = 1), pageBtn)))
        assertEquals(listOf(ControlType.ROCKER, ControlType.GAUGE, ControlType.PAGE), back.pages[0].controls.map { it.type })
        assertEquals(1, back.pages[0].controls[1].decimals)
    }

    @Test fun everyGameProfileHasProcessNamesAndUniqueIds() {
        for (p in BuiltInProfiles.all) {
            val ids = p.pages.flatMap { pg -> pg.controls.map { it.id } }
            assertEquals(ids.size, ids.toSet().size, "${p.name} repeats a control id")
            if (p.game != GameId.CUSTOM) assertTrue(p.processNames.isNotEmpty(), "${p.name} has no game executable")
        }
        assertTrue(BuiltInProfiles.all.map { it.game }.containsAll(listOf(GameId.SNOWRUNNER, GameId.ETS2, GameId.ATS, GameId.FS25, GameId.ACC, GameId.MSFS, GameId.EXPEDITIONS)))
        assertTrue(BuiltInProfiles.beamng().pages.any { it.auto == Page.AUTO_BEAMNG_VEHICLE }, "BeamNG has the vehicle page")
    }
}
