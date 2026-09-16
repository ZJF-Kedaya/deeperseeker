package com.deeperseeker.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader

/**
 * Incremental reader for the bridge's `text/event-stream` output.
 *
 * The bridge follows the OpenAI streaming shape: each event is a `data:` line
 * holding a JSON chunk, terminated by `data: [DONE]`. Chunks may arrive split
 * across reads, so this class accumulates a line buffer and only emits a
 * parsed frame once a newline is seen.
 *
 * Both `content` and `reasoning_content` deltas are surfaced; tool-call
 * fragments are accumulated by index because the bridge (like OpenAI) streams
 * the JSON arguments of a tool call across several chunks.
 */
class SseParser(
    private val json: Json,
    private val onDelta: (content: String?, reasoning: String?) -> Unit,
    private val onToolCall: (id: String?, name: String?, argumentsFragment: String?) -> Unit,
    private val onDone: () -> Unit,
    private val onError: (String) -> Unit,
) {

    private val toolAccumulator = linkedMapOf<Int, ToolAccum>()

    private data class ToolAccum(
        var id: String? = null,
        var name: String? = null,
        val args: StringBuilder = StringBuilder(),
    )

    /**
     * Consumes the whole body. Returns when the stream ends or `[DONE]` is hit.
     * Never throws for a malformed chunk — a single bad frame is reported and
     * skipped so the rest of the answer still renders.
     */
    fun consume(reader: BufferedReader) {
        reader.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                if (!line.startsWith(DATA_PREFIX)) continue
                val payload = line.removePrefix(DATA_PREFIX).trim()
                if (payload == DONE_SENTINEL) {
                    flushToolCalls()
                    onDone()
                    return
                }
                handleChunk(payload)
            }
        }
        // Stream closed without an explicit [DONE] — still flush whatever the
        // model produced so a truncated upstream response is not lost.
        flushToolCalls()
        onDone()
    }

    private fun handleChunk(payload: String) {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }
            .getOrElse {
                onError("无法解析流式数据块")
                return
            }

        // Errors can be delivered mid-stream as a normal JSON body.
        root["error"]?.let { err ->
            onError(err.toString().trim('"'))
            return
        }

        val choices = root["choices"] as? kotlinx.serialization.json.JsonArray ?: return
        val first = choices.firstOrNull()?.jsonObject ?: return
        val delta = first["delta"]?.jsonObject

        if (delta != null) {
            val content = delta["content"]?.jsonPrimitive?.contentOrNull
            val reasoning = delta["reasoning_content"]?.jsonPrimitive?.contentOrNull
            if (!content.isNullOrEmpty() || !reasoning.isNullOrEmpty()) {
                onDelta(content, reasoning)
            }
            accumulateToolCalls(delta["tool_calls"])
        }
    }

    private fun accumulateToolCalls(node: kotlinx.serialization.json.JsonElement?) {
        val array = node as? kotlinx.serialization.json.JsonArray ?: return
        for (element in array) {
            val obj: JsonObject = element.jsonObject
            val index = obj["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val accum = toolAccumulator.getOrPut(index) { ToolAccum() }
            obj["id"]?.jsonPrimitive?.contentOrNull?.let { accum.id = it }
            obj["function"]?.jsonObject?.let { fn ->
                fn["name"]?.jsonPrimitive?.contentOrNull?.let { accum.name = it }
                fn["arguments"]?.jsonPrimitive?.contentOrNull?.let { accum.args.append(it) }
            }
        }
    }

    private fun flushToolCalls() {
        for (accum in toolAccumulator.values) {
            if (accum.name != null || accum.args.isNotEmpty()) {
                onToolCall(accum.id, accum.name, accum.args.toString())
            }
        }
        toolAccumulator.clear()
    }

    private companion object {
        const val DATA_PREFIX = "data:"
        const val DONE_SENTINEL = "[DONE]"
    }
}