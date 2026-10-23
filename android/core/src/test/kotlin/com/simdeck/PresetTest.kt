package com.simdeck

import com.simdeck.model.Action
import com.simdeck.model.Control
import com.simdeck.model.ControlType
import com.simdeck.model.GameAction
import com.simdeck.model.GameId
import com.simdeck.model.Page
import com.simdeck.model.Profile
import com.simdeck.profiles.BuiltInProfiles
import com.simdeck.profiles.Presets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SimDeck 1.3: function presets, placement, copy and paste. */
class PresetTest {
    private fun profile(vararg c: Control, cols: Int = 8, rows: Int = 4) =
        Profile("t", "T", pages = listOf(Page("p", "P", columns = cols, rows = rows, controls = c.toList())))

    @Test fun libraryCoversEveryBuiltInGame() {
        val lib = Presets.builtIn
        assertEquals(lib.size, lib.map { it.id }.toSet().size, "preset ids are unique")
        for (g in GameId.entries.filter { it != GameId.CUSTOM }) assertTrue(lib.any { g in it.games }, "presets for $g")
        assertTrue(lib.count { GameId.BEAMNG in it.games } > 80, "BeamNG has its full function set")
        assertTrue(Presets.search(lib, "hazard").isNotEmpty())
    }

    @Test fun placingFillsInFreeGamepadNumbers() {
        val used = Control("u", ControlType.MOMENTARY, "U", action = Action.VJoyButton(1, 1))
        var p = profile(used)
        val rocker = Presets.generic.first { it.id == "generic.gp_rocker" }.control
        val (p2, id) = Presets.place(p, 0, rocker)!!
        val placed = p2.pages[0].controls.first { it.id == id }
        assertEquals(Action.VJoyButton(1, 2), placed.cw)
        assertEquals(Action.VJoyButton(1, 3), placed.ccw)
        p = p2
        val stick = Presets.generic.first { it.id == "generic.gp_stick" }.control
        val (p3, sid) = Presets.place(p, 0, stick)!!
        val s = p3.pages[0].controls.first { it.id == sid }
        assertEquals("X", (s.action as Action.VJoyAxis).axis)
        assertEquals("Y", (s.actionY as Action.VJoyAxis).axis)
        val cells = p3.pages[0].controls.flatMap { c -> (c.x until c.x + c.w).flatMap { x -> (c.y until c.y + c.h).map { y -> x to y } } }
        assertEquals(cells.size, cells.toSet().size, "placed without overlap")
    }

    @Test fun placingShrinksThenGivesUp() {
        // A 4x2 page with only a 2x1 hole: a 4-wide shift-light strip shrinks to fit, a joystick (2x2 minimum) does not.
        val filler = (0 until 4).flatMap { x -> listOf(Control("a$x", ControlType.MOMENTARY, "A", x = x, y = 0)) } +
            listOf(Control("b0", ControlType.MOMENTARY, "B", x = 0, y = 1), Control("b1", ControlType.MOMENTARY, "B", x = 1, y = 1))
        val p = profile(*filler.toTypedArray(), cols = 4, rows = 2)
        val strip = Presets.generic.first { it.id == "generic.g_shift" }.control
        val (p2, id) = Presets.place(p, 0, strip)!!
        val c = p2.pages[0].controls.first { it.id == id }
        assertTrue(c.w <= 2 && c.x == 2 && c.y == 1)
        assertNull(Presets.place(p, 0, Presets.generic.first { it.id == "generic.gp_stick" }.control))
    }

    @Test fun copiesGetNewIdsAndPagesPasteWhole() {
        val src = BuiltInProfiles.beamng()
        val c = src.pages[0].controls.first()
        val target = profile()
        val (p2, id) = Presets.place(target, 0, c)!!
        assertNotEquals(c.id, id)
        assertEquals(c.action, p2.pages[0].controls.single().action)
        val p3 = Presets.pastePage(p2, src.pages[0])
        assertEquals(2, p3.pages.size)
        assertEquals(src.pages[0].controls.size, p3.pages[1].controls.size)
        assertTrue(p3.pages[1].controls.none { pc -> src.pages[0].controls.any { it.id == pc.id } }, "pasted controls get fresh ids")
        assertEquals("t", p3.id)
    }

    @Test fun gameActionsBecomeButtonsOrSliders() {
        val b = Presets.gameAction(GameAction("toggle_tailgate", "Tailgate", vehicle = true))
        assertEquals(ControlType.MOMENTARY, b.control.type)
        assertEquals(Action.BeamNG("action.toggle_tailgate"), b.control.action)
        assertEquals("This vehicle", b.category)
        val a = Presets.gameAction(GameAction("steering", "Steering", axis = true))
        assertEquals(ControlType.SLIDER, a.control.type)
    }

    @Test fun backlightNeedsSchema5AndRoundTrips() {
        val c = Control("r", ControlType.LATCHING, "Winch", skin = "rocker-lamp", backlight = "blue")
        val p = profile(c)
        assertEquals(5, com.simdeck.profiles.ProfileJson.schemaFor(p))
        assertEquals("blue", com.simdeck.profiles.ProfileJson.import(com.simdeck.profiles.ProfileJson.export(p)).pages[0].controls[0].backlight)
        assertTrue(Presets.generic.any { it.control.skin == "inclinometer" } && Presets.generic.any { it.control.skin == "hat" })
    }

    @Test fun pcLayoutEditingMessages() {
        val bng = BuiltInProfiles.beamng()
        val list = com.simdeck.protocol.Protocol.profilesList(listOf(bng))
        assertTrue("\"type\":\"profiles.list\"" in list && bng.id in list)
        val data = com.simdeck.protocol.Protocol.json.parseToJsonElement(com.simdeck.protocol.Protocol.profileData(bng)) as kotlinx.serialization.json.JsonObject
        val profileJson = data["profile"].toString()
        val back = com.simdeck.profiles.ProfileJson.import(profileJson, newId = bng.id)
        assertEquals(bng.pages.size, back.pages.size)
        assertEquals(com.simdeck.protocol.Incoming.ProfilesRequest, com.simdeck.protocol.Protocol.parse("""{"v":1,"type":"profiles.request"}"""))
        assertEquals(com.simdeck.protocol.Incoming.ProfileGet("x"), com.simdeck.protocol.Protocol.parse("""{"v":1,"type":"profile.get","id":"x"}"""))
        val put = com.simdeck.protocol.Protocol.parse("""{"v":1,"type":"profile.put","profile":$profileJson}""")
        assertTrue(put is com.simdeck.protocol.Incoming.ProfilePut && bng.id in put.json)
    }

    @Test fun profilePresetsListRepeatsOnce() {
        val c = Control("x", ControlType.MOMENTARY, "Horn", action = Action.Key(listOf("H")))
        val p = Profile("t", "T", pages = listOf(Page("p1", "One", controls = listOf(c)), Page("p2", "Two", controls = listOf(c.copy(id = "y")))))
        assertEquals(1, Presets.fromProfile(p).size)
    }
}
