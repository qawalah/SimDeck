package com.simdeck.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Two profiles played at once (1.6): one above the other in portrait, side by side in landscape. Kept on the
 * phone only, apart from the profiles, so the profile file format is unchanged.
 */
@Serializable
data class ProfileStack(
    val id: String,
    val name: String,
    /** Profile id shown on top (portrait) or on the left (landscape). */
    val top: String,
    /** Profile id shown below (portrait) or on the right (landscape). */
    val bottom: String,
) {
    fun swapped() = copy(top = bottom, bottom = top)
}

/**
 * A stack ready to play. The input engine, feedback and the PC companion see one profile, [merged]: the top
 * profile's pages followed by the bottom profile's, every bottom control id prefixed with [Stacks.BOTTOM] so
 * the two never share an id. So profile.sync, the companion and the protocol need nothing new.
 *
 * [top] is the top profile as it is; [bottom] is the bottom profile with its ids prefixed, as drawn.
 */
data class StackedProfiles(val stack: ProfileStack, val top: Profile, val bottom: Profile, val merged: Profile) {
    /** The output setting of the half [control] belongs to: each profile keeps its own Bluetooth / PC choice. */
    fun outputOf(control: Control): OutputRoute = if (Stacks.isBottom(control.id)) bottom.output else top.output
}

object Stacks {
    /** Prefix of every bottom-half control id. A joystick's axes and a rocker's halves carry it too ("b~stick.x"). */
    const val BOTTOM = "b~"
    /** Prefix of the merged profile's id, as the companion sees it in profile.sync. */
    const val MERGED_ID_PREFIX = "stack-"

    private val json = Json { ignoreUnknownKeys = true }

    fun newId(): String = "s-" + UUID.randomUUID().toString().take(8)

    fun isBottom(id: String) = id.startsWith(BOTTOM)

    /** The id a control has in its own profile. */
    fun originalId(id: String) = id.removePrefix(BOTTOM)

    /** [top] and [bottom] (both already filled for the vehicle) as one playable profile. */
    fun combine(stack: ProfileStack, top: Profile, bottom: Profile): StackedProfiles {
        val b = bottom.copy(pages = bottom.pages.map { pg -> pg.copy(controls = pg.controls.map { it.copy(id = BOTTOM + it.id) }) })
        val merged = Profile(
            id = MERGED_ID_PREFIX + stack.id,
            name = stack.name,
            game = top.game,
            output = top.output,
            pages = top.pages + b.pages,
        )
        return StackedProfiles(stack, top, b, merged)
    }

    fun encode(list: List<ProfileStack>): String = json.encodeToString(ListSerializer(ProfileStack.serializer()), list)

    /** Stacks as saved in the settings; unreadable text gives none. */
    fun decode(text: String?): List<ProfileStack> =
        text?.let { runCatching { json.decodeFromString(ListSerializer(ProfileStack.serializer()), it) }.getOrNull() } ?: emptyList()
}
