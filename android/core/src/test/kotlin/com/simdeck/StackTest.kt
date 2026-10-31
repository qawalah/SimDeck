package com.simdeck

import com.simdeck.engine.Feedback
import com.simdeck.engine.InputEngine
import com.simdeck.engine.Route
import com.simdeck.model.Action
import com.simdeck.model.Control
import com.simdeck.model.ControlType
import com.simdeck.model.Joystick
import com.simdeck.model.OutputRoute
import com.simdeck.model.Page
import com.simdeck.model.Profile
import com.simdeck.model.ProfileStack
import com.simdeck.model.Stacks
import com.simdeck.model.VehicleFeature
import com.simdeck.model.VehicleFeaturePages
import com.simdeck.model.VehicleFeatures
import com.simdeck.profiles.BuiltInProfiles
import com.simdeck.profiles.LayoutOps
import com.simdeck.protocol.Protocol
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stacks: two profiles played at once (1.6). */
@OptIn(ExperimentalCoroutinesApi::class)
class StackTest {
    private val stack = ProfileStack(id = "s-1", name = "Truck + Lights", top = "a", bottom = "b")

    private fun button(id: String, action: Action = Action.VJoyButton(1, 1)) = Control(id, ControlType.MOMENTARY, id, action = action)
    private fun profile(id: String, vararg controls: Control, output: OutputRoute = OutputRoute.AUTO, pages: Int = 1) =
        Profile(id, id.uppercase(), output = output, pages = (1..pages).map { n ->
            Page("p$n", "Page $n", controls = if (n == 1) controls.toList() else emptyList())
        })

    @Test fun bottomControlIdsArePrefixedSoTheTwoNeverShareAnId() {
        val top = profile("a", button("horn"), button("lights"))
        val bottom = profile("b", button("horn"), button("wipers"))
        val s = Stacks.combine(stack, top, bottom)
        val ids = s.merged.pages.flatMap { it.controls }.map { it.id }
        assertEquals(listOf("horn", "lights", "b~horn", "b~wipers"), ids)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(top, s.top, "the top profile is played as it is")
        assertEquals(listOf("b~horn", "b~wipers"), s.bottom.pages[0].controls.map { it.id })
        assertEquals("horn", Stacks.originalId("b~horn"))
        assertTrue(Stacks.isBottom("b~horn"))
        assertFalse(Stacks.isBottom("horn"))
    }

    @Test fun mergedProfileHasBothProfilesPagesInOrder() {
        val s = Stacks.combine(stack, profile("a", pages = 2), profile("b", pages = 3))
        assertEquals(5, s.merged.pages.size)
        assertEquals("stack-s-1", s.merged.id)
        assertEquals("Truck + Lights", s.merged.name)
        assertEquals(s.top.pages + s.bottom.pages, s.merged.pages)
    }

    @Test fun builtInsStackedWithEachOtherHaveUniqueIds() {
        val all = BuiltInProfiles.all
        for (a in all) for (b in all) {
            val merged = Stacks.combine(stack, a, b).merged
            val ids = merged.pages.flatMap { Joystick.expand(it.controls) }.map { it.id }
            assertEquals(ids.size, ids.toSet().size, "${a.name} over ${b.name}")
        }
    }

    @Test fun profileSyncCarriesBothHalvesWithJoystickAxesPrefixed() {
        val stick = Control("stick", ControlType.JOYSTICK, "Boom", action = Action.VJoyAxis(1, "X"), actionY = Action.VJoyAxis(1, "Y"))
        val s = Stacks.combine(stack, profile("a", button("horn")), profile("b", stick))
        val sync = Protocol.json.parseToJsonElement(Protocol.profileSync(s.merged, emptySet())).jsonObject
        val ids = sync["bindings"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertEquals(listOf("horn", "b~stick.x", "b~stick.y"), ids)
        assertEquals("stack-s-1", sync["profileId"]!!.jsonPrimitive.content)
    }

    @Test fun eachHalfKeepsItsOwnOutputRoute() = runTest {
        val links = FakeLinks(hidConnected = true, companionConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        val s = Stacks.combine(stack, profile("a", button("horn"), output = OutputRoute.HID), profile("b", button("horn"), output = OutputRoute.COMPANION))
        e.outputFor = s::outputOf
        e.profile = s.merged
        val top = s.merged.pages[0].controls[0]
        val bottom = s.merged.pages[1].controls[0]
        assertEquals(Route.HID, e.routeFor(top))
        assertEquals(Route.COMPANION, e.routeFor(bottom))
        assertEquals(setOf("horn"), e.hidRoutedIds())

        links.companion.clear()
        e.onValue(bottom, 1f)
        e.onValue(bottom, 0f)
        assertEquals(listOf("b~horn", "b~horn"), links.companion.take(2).map { it.id })
        advanceUntilIdle()
    }

    @Test fun withoutOutputForTheProfileSettingStillDecides() = runTest {
        val links = FakeLinks(hidConnected = true, companionConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = profile("a", button("horn"), output = OutputRoute.COMPANION)
        assertEquals(Route.COMPANION, e.routeFor(e.profile!!.pages[0].controls[0]))
    }

    @Test fun feedbackResolvesBothHalvesIndependently() {
        val lamp = { id: String -> Control(id, ControlType.LATCHING, id, action = Action.VJoyButton(1, 2)) }
        val s = Stacks.combine(stack, profile("a", lamp("beacon")), profile("b", lamp("beacon")))
        val r = Feedback.resolve(s.merged, mapOf("b~beacon" to 1f), emptyMap(), emptyMap(), 0L)
        assertEquals(false, r.indicators["beacon"]?.lit)
        assertEquals(true, r.indicators["b~beacon"]?.lit)
    }

    @Test fun vehiclePagesFillBeforeTheBottomIsPrefixed() {
        val auto = Profile("b", "B", pages = listOf(Page("v", "Vehicle", auto = Page.AUTO_BEAMNG_VEHICLE)))
        val features = VehicleFeatures("pickup", listOf(VehicleFeature(id = "tailgate", title = "Tailgate")))
        val s = Stacks.combine(stack, profile("a"), VehicleFeaturePages.fill(auto, features))
        val id = s.bottom.pages[0].controls.single().id
        assertEquals("b~" + VehicleFeaturePages.ID_PREFIX + "tailgate", id)
    }

    @Test fun pageControlsMoveWithinTheirOwnPages() {
        val pages = listOf(Page("drive", "Drive"), Page("cab", "Cab"), Page("crane", "Crane"))
        assertEquals(1, LayoutOps.pageAfter(pages, 0, null))
        assertEquals(1, LayoutOps.pageAfter(pages, 0, "next"))
        assertEquals(0, LayoutOps.pageAfter(pages, 2, "next"))
        assertEquals(2, LayoutOps.pageAfter(pages, 0, "prev"))
        assertEquals(2, LayoutOps.pageAfter(pages, 0, "crane"))
        assertEquals(1, LayoutOps.pageAfter(pages, 0, "2"))
        assertEquals(2, LayoutOps.pageAfter(pages, 0, "99"))
        assertEquals(1, LayoutOps.pageAfter(pages, 1, "nowhere"))
        assertEquals(0, LayoutOps.pageAfter(emptyList(), 0, "next"))
    }

    @Test fun stacksRoundTripAndBadTextGivesNone() {
        val list = listOf(stack, ProfileStack("s-2", "Two", "x", "y"))
        assertEquals(list, Stacks.decode(Stacks.encode(list)))
        assertEquals(emptyList(), Stacks.decode(null))
        assertEquals(emptyList(), Stacks.decode("not json"))
    }

    @Test fun swapPutsTheBottomOnTop() {
        assertEquals(ProfileStack("s-1", "Truck + Lights", top = "b", bottom = "a"), stack.swapped())
    }
}
