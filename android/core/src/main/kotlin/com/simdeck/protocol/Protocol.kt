package com.simdeck.protocol

import com.simdeck.model.Action
import com.simdeck.model.AxisRange
import com.simdeck.model.Capabilities
import com.simdeck.model.GameAction
import com.simdeck.model.VJoyInfo
import com.simdeck.model.VehicleFeature
import com.simdeck.model.VehicleFeatures
import com.simdeck.model.Control
import com.simdeck.model.Joystick
import com.simdeck.model.ControlType
import com.simdeck.model.Profile
import com.simdeck.model.ReadingInfo
import com.simdeck.model.TuningVar
import com.simdeck.model.Action as ModelAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Protocol v1 constants and message codec. See protocol/PROTOCOL.md. */
object Protocol {
    const val VERSION = 1
    const val WS_PORT = 47800
    const val UDP_PORT = 47801
    const val DISCOVERY_PORT = 47802
    const val SERVICE_TYPE = "_simdeck._tcp."
    const val WS_PATH = "/ws"

    /** Shared JSON config: actions use "type" as discriminator, unknown fields are ignored. */
    val json = Json {
        serializersModule = SerializersModule {
            // Unknown action types (from a newer app or a hand-edited file) decode as "none".
            polymorphic(ModelAction::class) {
                defaultDeserializer { ModelAction.None.serializer() }
            }
        }
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
        coerceInputValues = true
    }

    /** Pretty variant for profile export files. */
    val prettyJson = Json(json) { prettyPrint = true }

    fun hello(deviceId: String, deviceName: String, appVersion: String, pin: String?, token: String?): String =
        obj("hello") {
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            put("appVersion", appVersion)
            if (token != null) put("token", token) else if (pin != null) put("pin", pin)
        }

    fun ping(t: Long): String = obj("ping") { put("t", t) }

    fun discover(): String = obj("discover") {}

    /** WebSocket input event (discrete kinds, or fallback when UDP is unavailable). */
    fun wsInput(seq: Long, id: String, value: Float): String = obj("input") {
        put("s", seq)
        put("id", id)
        put("val", value.toDouble())
    }

    /** UDP datagram payload. */
    fun udpInput(session: String, seq: Long, id: String, value: Float): String = buildJsonObject {
        put("v", VERSION)
        put("k", session)
        put("s", seq)
        put("id", id)
        put("val", value.toDouble())
    }.toString()

    /**
     * profile.sync with one binding per control. [hidRouted] holds the control ids the phone sends
     * over Bluetooth HID; those are marked "route":"hid" so the companion does not inject them.
     */
    fun profileSync(profile: Profile, hidRouted: Set<String>): String = obj("profile.sync") {
        put("profileId", profile.id)
        put("game", profile.game.name.lowercase())
        put("bindings", buildJsonArray {
            // A joystick goes out as its two axes (Joystick): the companion only ever sees sliders.
            profile.pages.flatMap { Joystick.expand(it.controls) }.forEach { add(binding(it, it.id in hidRouted)) }
        })
    }

    fun binding(control: Control, hid: Boolean = false): JsonObject = buildJsonObject {
        put("id", control.id)
        put("kind", control.type.wire)
        when (control.type) {
            ControlType.SWITCH, ControlType.RADIO ->
                put("positions", JsonArray(control.positions.map { action(it.action) }))
            ControlType.ROTARY -> {
                put("cw", action(control.cw))
                put("ccw", action(control.ccw))
            }
            else -> put("action", action(effectiveAction(control)))
        }
        if (hid) put("route", "hid")
    }

    /** A slider that is unipolar on the control is reported unipolar on its axis action too (A7). */
    fun effectiveAction(control: Control): Action {
        val a = control.action
        // A slider's control range is the one truth for its axis (A25), both ways.
        return if (control.type == ControlType.SLIDER && a is Action.VJoyAxis) a.copy(range = control.range) else a
    }

    fun action(action: Action): JsonElement = json.encodeToJsonElement(Action.serializer(), action)

    /** The phone's profiles, for the PC companion's layout editor. */
    fun profilesList(profiles: List<com.simdeck.model.Profile>): String = obj("profiles.list") {
        put("list", buildJsonArray {
            for (p in profiles) add(buildJsonObject {
                put("id", p.id); put("name", p.name); put("game", p.game.name.lowercase()); put("builtIn", p.builtIn)
                put("pages", p.pages.size); p.theme?.let { put("theme", it) }
            })
        })
    }

    /** One profile in the profile file format, for the PC companion's layout editor. */
    fun profileData(p: com.simdeck.model.Profile): String = obj("profile.data") {
        put("profile", json.parseToJsonElement(com.simdeck.profiles.ProfileJson.export(p)))
    }

    fun profileSaved(id: String, ok: Boolean, message: String): String = obj("profile.saved") {
        put("id", id); put("ok", ok); put("message", message)
    }

    fun beamngBindingsApply(device: String, bindings: List<Pair<String, Int>>): String = obj("beamng.bindings.apply") {
        put("device", device)
        put("bindings", buildJsonArray {
            bindings.forEach { (name, button) ->
                add(buildJsonObject { put("beamngAction", name); put("button", button) })
            }
        })
    }

    // ---- readings and tuning (SimDeck 1.4) ----

    /** The readings this phone shows; [all] while the reading picker is open. */
    fun readingsWatch(keys: Collection<String>, all: Boolean): String = obj("readings.watch") {
        put("keys", buildJsonArray { keys.forEach { add(JsonPrimitive(it)) } })
        put("all", all)
    }

    fun tuningRequest(): String = obj("tuning.request") {}

    /** New values (game range) by variable name. */
    fun tuningApply(vars: Map<String, Double>): String = obj("tuning.apply") {
        put("vars", buildJsonObject { vars.forEach { (k, v) -> put(k, v) } })
    }

    private inline fun obj(type: String, body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        buildJsonObject {
            put("v", VERSION)
            put("type", type)
            body()
        }.toString()

    /** Parses one incoming message. Returns [Incoming.Unknown] for anything unrecognised (forward compatible). */
    fun parse(text: String): Incoming {
        val o = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return Incoming.Malformed(text)
        }
        val v = o["v"]?.jsonPrimitive?.intOrNull ?: return Incoming.Malformed(text)
        if (v > VERSION) return Incoming.VersionMismatch(v)
        val str = { k: String -> o[k]?.jsonPrimitive?.contentOrNull }
        return when (str("type")) {
            "welcome" -> Incoming.Welcome(
                serverName = str("serverName") ?: "PC",
                token = str("token") ?: "",
                session = str("session") ?: "",
                udpPort = o["udpPort"]?.jsonPrimitive?.intOrNull ?: UDP_PORT,
                capabilities = capabilities(o["capabilities"]),
            )
            "error" -> Incoming.Error(str("code") ?: "unknown", str("message") ?: "")
            "pong" -> Incoming.Pong(o["t"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L)
            "ping" -> Incoming.Ping(o["t"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L)
            "capabilities" -> Incoming.CapabilitiesChanged(capabilities(o["capabilities"]))
            "state" -> Incoming.State(
                source = str("source") ?: "companion",
                values = (o["values"] as? JsonObject)?.mapValues { (_, e) -> primitive(e) }
                    ?.filterValues { it != null }?.mapValues { it.value!! } ?: emptyMap(),
                full = o["full"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
            "profile.ack" -> Incoming.ProfileAck(str("profileId") ?: "", o["count"]?.jsonPrimitive?.intOrNull ?: 0)
            "beamng.commands" -> Incoming.BeamNGCommands(
                (o["list"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            )
            "bindings.result" -> Incoming.BindingsResult(
                ok = o["ok"]?.jsonPrimitive?.booleanOrNull ?: false,
                message = str("message") ?: "",
                written = o["written"]?.jsonPrimitive?.booleanOrNull ?: false,
                count = o["count"]?.jsonPrimitive?.intOrNull ?: 0,
                unknown = (o["unknown"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                device = str("device") ?: "",
                busy = o["busy"]?.jsonPrimitive?.booleanOrNull ?: false,
                alreadyBound = o["alreadyBound"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
            "beamng.features" -> Incoming.BeamNGFeatures(features(o))
            "beamng.actions" -> Incoming.BeamNGActions(
                (o["list"] as? JsonArray)?.mapNotNull { e ->
                    val a = e as? JsonObject ?: return@mapNotNull null
                    val name = a.s("name") ?: return@mapNotNull null
                    GameAction(name, a.s("title") ?: name, a.s("cat") ?: "", a.b("vehicle"), a.b("axis"))
                } ?: emptyList()
            )
            "beamng.readings" -> Incoming.BeamNGReadings(str("vehicle") ?: "", list(o).mapNotNull { e ->
                val r = e as? JsonObject ?: return@mapNotNull null
                val key = r.s("key")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ReadingInfo(key, r.s("group") ?: "Other", r.s("title") ?: key, r.s("unit") ?: "")
            })
            "beamng.tuning" -> Incoming.BeamNGTuning(str("vehicle") ?: "", list(o).mapNotNull { tuningVar(it) })
            "beamng.tuning.result" -> Incoming.TuningResult(o["ok"]?.jsonPrimitive?.booleanOrNull ?: false, str("message") ?: "")
            "notice" -> Incoming.Notice(str("level") ?: "info", str("code") ?: "", str("message") ?: "")
            "foreground" -> Incoming.Foreground(str("exe") ?: "")
            // Layout editing from the PC companion (SimDeck 1.3).
            "profiles.request" -> Incoming.ProfilesRequest
            "profile.get" -> Incoming.ProfileGet(str("id") ?: "")
            "profile.put" -> (o["profile"] as? JsonObject)?.let { Incoming.ProfilePut(it.toString()) } ?: Incoming.Malformed(text)
            "discover.reply" -> Incoming.DiscoverReply(
                str("name") ?: "PC",
                o["wsPort"]?.jsonPrimitive?.intOrNull ?: WS_PORT,
                o["udpPort"]?.jsonPrimitive?.intOrNull ?: UDP_PORT,
            )
            else -> Incoming.Unknown(str("type"))
        }
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.d(k: String) = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

    /** A message's "list"; an empty Lua table may arrive as {} instead of []. */
    private fun list(o: JsonObject): List<JsonElement> = (o["list"] as? JsonArray) ?: emptyList()

    private fun tuningVar(e: JsonElement): TuningVar? {
        val t = e as? JsonObject ?: return null
        val name = t.s("name")?.takeIf { it.isNotBlank() } ?: return null
        val min = t.d("min") ?: return null
        val max = t.d("max") ?: return null
        val def = t.d("default")
        return TuningVar(
            name = name, title = t.s("title")?.takeIf { it.isNotBlank() } ?: name.removePrefix("$"),
            category = t.s("category")?.takeIf { it.isNotBlank() } ?: "Other", subCategory = t.s("subCategory")?.takeIf { it.isNotBlank() },
            description = t.s("description")?.takeIf { it.isNotBlank() }, unit = t.s("unit") ?: "",
            min = min, max = max, minDis = t.d("minDis"), maxDis = t.d("maxDis"), stepDis = t.d("stepDis")?.takeIf { it > 0 },
            default = def, value = t.d("val") ?: def ?: min,
        )
    }
    private fun JsonObject.b(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false

    /** "beamng.features": the list (an empty Lua table may arrive as {} instead of []) and the diagnostics block. */
    fun features(o: JsonObject): VehicleFeatures {
        val list = (o["list"] as? JsonArray)?.mapNotNull { e ->
            val f = e as? JsonObject ?: return@mapNotNull null
            val id = f.s("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            VehicleFeature(id, f.s("title") ?: id, f.s("kind") ?: "button", f.b("centered"), f.s("source") ?: "", f.s("stateKey"))
        } ?: emptyList()
        val diag = (o["diag"] as? JsonObject)?.let { d -> flatten(d, "") } ?: emptyList()
        return VehicleFeatures(o.s("vehicle") ?: "", list, diag, o.s("category") ?: "")
    }

    private fun flatten(e: JsonElement, prefix: String): List<String> = when (e) {
        is JsonObject -> e.entries.sortedBy { it.key }.flatMap { (k, v) -> flatten(v, if (prefix.isEmpty()) k else "$prefix.$k") }
        is JsonArray -> if (e.isEmpty()) emptyList() else listOf("$prefix: " + e.joinToString(" | ") { (it as? JsonPrimitive)?.content ?: it.toString() })
        is JsonPrimitive -> listOf("$prefix: ${e.content}")
        else -> emptyList()
    }

    private fun capabilities(e: JsonElement?): Capabilities {
        val o = e as? JsonObject ?: return Capabilities()
        fun b(k: String) = o[k]?.jsonPrimitive?.booleanOrNull ?: false
        return Capabilities(
            keyboard = b("keyboard"),
            vjoy = b("vjoy"),
            vigem = b("vigem"),
            beamng = b("beamng"),
            outgauge = b("outgauge"),
            vjoyInfo = (o["vjoyInfo"] as? JsonArray)?.mapNotNull { x ->
                val d = x as? JsonObject ?: return@mapNotNull null
                VJoyInfo(
                    device = (d["device"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null,
                    buttons = (d["buttons"] as? JsonPrimitive)?.intOrNull ?: 0,
                    axes = (d["axes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList(),
                )
            } ?: emptyList(),
        )
    }

    /** Game state values are booleans, numbers or short strings. */
    private fun primitive(e: JsonElement): Any? {
        val p = e as? JsonPrimitive ?: return null
        if (p.isString) return p.content
        p.booleanOrNull?.let { return it }
        p.doubleOrNull?.let { return it }
        return null
    }
}

sealed interface Incoming {
    data class Welcome(
        val serverName: String,
        val token: String,
        val session: String,
        val udpPort: Int,
        val capabilities: Capabilities,
    ) : Incoming
    data class Error(val code: String, val message: String) : Incoming
    data class Ping(val t: Long) : Incoming
    data class Pong(val t: Long) : Incoming
    data class CapabilitiesChanged(val capabilities: Capabilities) : Incoming
    data class State(val source: String, val values: Map<String, Any>, val full: Boolean) : Incoming
    data class ProfileAck(val profileId: String, val count: Int) : Incoming
    data class BeamNGCommands(val list: List<String>) : Incoming
    data class BindingsResult(
        val ok: Boolean,
        val message: String,
        val written: Boolean = false,
        val count: Int = 0,
        val unknown: List<String> = emptyList(),
        val device: String = "",
        val busy: Boolean = false,
        val alreadyBound: Boolean = false,
    ) : Incoming
    data class DiscoverReply(val name: String, val wsPort: Int, val udpPort: Int) : Incoming
    data class BeamNGFeatures(val features: VehicleFeatures) : Incoming
    data class BeamNGActions(val list: List<GameAction>) : Incoming
    /** A message for the user, e.g. code "vjoy_config" when vJoy is set up with too few buttons for the profile. */
    data class Notice(val level: String, val code: String, val message: String) : Incoming
    data class Foreground(val exe: String) : Incoming
    /** What the BeamNG vehicle can report (SimDeck 1.4). */
    data class BeamNGReadings(val vehicle: String, val list: List<ReadingInfo>) : Incoming
    /** The BeamNG vehicle's tuning settings; an empty list when there is no vehicle or no game. */
    data class BeamNGTuning(val vehicle: String, val list: List<TuningVar>) : Incoming
    data class TuningResult(val ok: Boolean, val message: String) : Incoming
    /** The PC companion asks for the list of this phone's profiles. */
    data object ProfilesRequest : Incoming
    /** The PC companion asks for one profile, to edit it. */
    data class ProfileGet(val id: String) : Incoming
    /** The PC companion sends back an edited profile ([json] is the profile file object). */
    data class ProfilePut(val json: String) : Incoming
    data class VersionMismatch(val remoteVersion: Int) : Incoming
    data class Malformed(val text: String) : Incoming
    data class Unknown(val type: String?) : Incoming
}
