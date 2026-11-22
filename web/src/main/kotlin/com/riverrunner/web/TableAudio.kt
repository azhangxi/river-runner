package com.riverrunner.web

import kotlin.js.Promise

/** WebKit allows sounds after the user has unlocked the audio context with a tap. */
internal class TableAudio {
    private var context: dynamic = null
    private val buffers = mutableMapOf<String, Promise<dynamic>>()

    fun unlock() {
        runCatching {
            if (context == null) context = js("new (window.AudioContext || window.webkitAudioContext)()")
            if (context.state == "suspended") (context.resume() as Promise<dynamic>).catch { null }
        }
    }

    fun play(name: String) {
        val audio = context ?: return
        val buffer = buffers.getOrPut(name) {
            val request = js("fetch") as (String) -> Promise<dynamic>
            request("./audio/sfx_$name.wav").then<dynamic> { response ->
                if (response.ok != true) error("Could not load sound")
                response.arrayBuffer()
            }.then<dynamic> { bytes -> audio.decodeAudioData(bytes) }
        }
        buffer.then<Unit> { decoded: dynamic ->
            val source = audio.createBufferSource()
            source.buffer = decoded
            source.connect(audio.destination)
            source.start()
        }.catch {
            buffers.remove(name)
            null
        }
    }
}
