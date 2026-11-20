// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.model

import android.content.Context
import com.emitloom.app.protocol.ControllerProfile
import org.json.JSONArray
import org.json.JSONObject

data class SavedController(val device: ControllerDevice, val name: String)
data class SavedLook(
    val id: String, val name: String, val address: String, val channel: Int, val state: LightState
)

class LocalLibrary(context: Context) {
    private val prefs = context.getSharedPreferences("emitloom_library", Context.MODE_PRIVATE)

    fun controllers() = read("controllers") {
        val profile = ControllerProfile.valueOf(it.getString("profile"))
        val device = ControllerDevice(it.getString("address"), it.getString("model"), profile)
        SavedController(device, it.getString("name"))
    }

    fun storeControllers(devices: List<SavedController>) = write("controllers", devices.map { saved ->
        JSONObject().put("address", saved.device.address).put("model", saved.device.model)
            .put("profile", saved.device.profile.name).put("name", saved.name)
    })

    fun looks() = read("looks") {
        val state = LightState(
            power = true, brightness = it.getInt("brightness").coerceIn(0, 255),
            color = requireNotNull(ColorRgb.fromHex(it.getString("color"))),
            mode = LightMode.valueOf(it.getString("mode")), effect = it.getInt("effect"),
            speed = it.getInt("speed"), sensitivity = it.getInt("sensitivity"),
            lightType = it.optInt("lightType"),
            white = it.optInt("white"), cold = it.optInt("cold", 255), warm = it.optInt("warm")
        )
        SavedLook(it.getString("id"), it.getString("name"), it.getString("address"), it.getInt("channel"), state)
    }

    fun storeLooks(looks: List<SavedLook>) = write("looks", looks.map { look ->
        val state = look.state
        JSONObject().put("id", look.id).put("name", look.name).put("address", look.address).put("channel", look.channel)
            .put("color", state.color.toHex()).put("mode", state.mode.name).put("effect", state.effect)
            .put("brightness", state.brightness).put("speed", state.speed).put("sensitivity", state.sensitivity)
            .put("lightType", state.lightType)
            .put("white", state.white).put("cold", state.cold).put("warm", state.warm)
    })

    private fun <T> read(key: String, decode: (JSONObject) -> T): List<T> {
        val array = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { runCatching { decode(array.getJSONObject(it)) }.getOrNull() }
    }

    fun sequences() = read("sequences") { item ->
        val steps = item.getJSONArray("steps")
        ColorSequence(item.getString("id"), item.getString("name"), List(steps.length()) { index ->
            val step = steps.getJSONObject(index)
            requireNotNull(ColorStep.parse(step.getString("color"), step.getString("seconds")))
        }, item.getBoolean("loop"))
    }

    fun storeSequences(sequences: List<ColorSequence>) = write("sequences", sequences.map { sequence ->
        val steps = sequence.steps.map { step ->
            JSONObject().put("color", step.color.toHex()).put("seconds", step.seconds)
        }
        JSONObject().put("id", sequence.id).put("name", sequence.name)
            .put("steps", JSONArray(steps)).put("loop", sequence.loop)
    })

    private fun write(key: String, values: List<JSONObject>) {
        prefs.edit().putString(key, JSONArray(values).toString()).apply()
    }
}
