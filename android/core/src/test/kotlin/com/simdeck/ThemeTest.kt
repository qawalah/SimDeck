package com.simdeck

import com.simdeck.model.AxisRange
import com.simdeck.model.Band
import com.simdeck.model.Control
import com.simdeck.model.ControlType
import com.simdeck.model.OutputRoute
import com.simdeck.model.Page
import com.simdeck.model.PanelGroup
import com.simdeck.model.Profile
import com.simdeck.model.Skins
import com.simdeck.model.Station
import com.simdeck.model.Themes
import com.simdeck.profiles.BuiltInProfiles
import com.simdeck.profiles.LayoutOps
import com.simdeck.profiles.ProfileJson
import com.simdeck.protocol.Incoming
import com.simdeck.protocol.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SimDeck 1.2: equipment themes (docs/THEMES.md). */
class ThemeTest {
    private val lever = Control("l", ControlType.SLIDER, "Thrust", stations = listOf(Station(0f, "IDLE"), Station(1f, "TOGA")))

    private fun profile(c: Control, theme: String? = null) =
        Profile("t", "T", pages = listOf(Page("p", "P", controls = listOf(c))), output = OutputRoute.COMPANION, theme = theme)

    @Test fun themeFieldsNeedSchema4AndRoundTrip() {
        assertEquals(4, ProfileJson.schemaFor(profile(Control("b", ControlType.MOMENTARY, "B"), theme = Themes.HEAVY)))
        assertEquals(4, ProfileJson.schemaFor(profile(lever)))
        assertEquals(1, ProfileJson.schemaFor(profile(Control("b", ControlType.MOMENTARY, "B"))), "no theme fields: schema stays low")
        val gauge = Control("g", ControlType.GAUGE, "Oil", feedbackKey = "oil", skin = "steam", min = 0.0, max = 150.0,
            bands = listOf(Band(0.0, 120.0, "green"), Band(120.0, 150.0, "red")))
        val p = Profile("t", "T", theme = Themes.AIRLINER, themeFollowsVehicle = true, pages = listOf(
            Page("p", "P", theme = Themes.GA, panels = listOf(PanelGroup("ELEC", 0, 0, 2, 1)),
                controls = listOf(lever, gauge, Control("k", ControlType.LATCHING, "Pump", legends = listOf("FAULT", "OFF"), ends = listOf("A", "B")))),
        ))
        val back = ProfileJson.import(ProfileJson.export(p))
        assertEquals(Themes.AIRLINER, back.theme)
        assertTrue(back.themeFollowsVehicle)
        assertEquals(Themes.GA, back.pages[0].theme)
        assertEquals("ELEC", back.pages[0].panels.single().title)
        assertEquals(listOf("IDLE", "TOGA"), back.pages[0].controls[0].stations.map { it.label })
        assertEquals(150.0, back.pages[0].controls[1].max)
        assertEquals("red", back.pages[0].controls[1].bands[1].color)
        assertEquals(listOf("FAULT", "OFF"), back.pages[0].controls[2].legends)
    }

    @Test fun sanitizeKeepsPanelsAndStationsInBounds() {
        val p = Profile("t", "T", pages = listOf(Page("p", "P", columns = 4, rows = 2,
            panels = listOf(PanelGroup("WIDE", 3, 1, 9, 9)),
            controls = listOf(Control("s", ControlType.SLIDER, "S", h = 2, stations = listOf(Station(-1f), Station(2f)), legends = listOf("a", "b", "c"))))))
        val s = LayoutOps.sanitize(p).pages[0]
        val g = s.panels.single()
        assertTrue(g.x + g.w <= 4 && g.y + g.h <= 2)
        assertEquals(listOf(0f, 1f), s.controls[0].stations.map { it.at })
        assertEquals(2, s.controls[0].legends.size)
    }

    @Test fun skinsResolveFromThemeControlAndMeaning() {
        val button = Control("b", ControlType.MOMENTARY, "B")
        assertEquals("cap", Skins.resolve(button, Themes.RACE))
        assertEquals("lit-push", Skins.resolve(button, Themes.AIRLINER))
        assertEquals("rubber-key", Skins.resolve(button, Themes.HEAVY))
        assertEquals("ring-push", Skins.resolve(button.copy(skin = "ring-push"), Themes.HEAVY), "a control's own skin wins")
        assertEquals("rubber-key", Skins.resolve(button.copy(skin = "nonsense"), Themes.HEAVY), "unknown skin falls back")
        val valve = Control("v", ControlType.SLIDER, "Boom", range = AxisRange.BIPOLAR, springReturn = true)
        assertEquals("hydraulic-lever", Skins.resolve(valve, Themes.RACE))
        assertEquals("fader", Skins.resolve(valve, Themes.AIRLINER), "no thrust lever for a centred axis")
        assertEquals("ring-push", Skins.resolve(Control("h", ControlType.MOMENTARY, "Hazard", icon = "hazard"), Themes.ROADCAR),
            "road car keeps hazard on a physical button")
        for ((theme, map) in Skins.defaults) for ((type, skin) in map) {
            assertTrue(skin in Skins.byType.getValue(type), "$theme default $skin is valid for $type")
        }
        for (type in ControlType.entries) assertTrue(Skins.byType.containsKey(type), "$type has skins")
    }

    @Test fun themeFollowsTheVehicle() {
        val p = BuiltInProfiles.beamng()
        assertTrue(p.themeFollowsVehicle)
        assertEquals(Themes.COMMERCIAL, Themes.resolve(p, p.pages[0], "truck"))
        assertEquals(Themes.HEAVY, Themes.resolve(p, p.pages[0], "machine"))
        assertEquals(Themes.RACE, Themes.resolve(p, p.pages[0], "trailer"), "no theme for a trailer: the profile's own")
        assertEquals(Themes.GA, Themes.resolve(p, p.pages[0].copy(theme = Themes.GA), "truck"), "a page's theme wins")
        val f = Protocol.parse("""{"v":1,"type":"beamng.features","vehicle":"us_semi","category":"truck","list":[]}""")
        assertIs<Incoming.BeamNGFeatures>(f)
        assertEquals("truck", f.features.category)
    }

    @Test fun builtInProfilesHaveThemes() {
        val byGame = BuiltInProfiles.all.associateBy { it.id }
        assertEquals(Themes.AIRLINER, byGame.getValue(BuiltInProfiles.MSFS_ID).theme)
        assertEquals(Themes.HEAVY, byGame.getValue(BuiltInProfiles.UTILITY_ID).theme)
        assertEquals(Themes.HEAVY, byGame.getValue(BuiltInProfiles.BULLDOZER_ID).theme)
        assertEquals(Themes.AGRICULTURE, byGame.getValue(BuiltInProfiles.FS25_ID).theme)
        assertEquals(Themes.COMMERCIAL, byGame.getValue(BuiltInProfiles.ETS2_ID).theme)
        assertEquals(Themes.OVERLAND, byGame.getValue(BuiltInProfiles.SNOWRUNNER_ID).theme)
        assertEquals(Themes.MOTORSPORT, byGame.getValue(BuiltInProfiles.ACC_ID).theme)
        assertNull(BuiltInProfiles.gamepad().theme)
        for (p in BuiltInProfiles.all) for (pg in p.pages) for (g in pg.panels) {
            assertTrue(g.x + g.w <= pg.columns && g.y + g.h <= pg.rows, "${p.name}/${pg.name}: panel ${g.title} inside the grid")
        }
    }
}
