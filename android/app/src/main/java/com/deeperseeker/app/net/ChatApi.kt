package com.deeperseeker.app.net

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * The OpenAI/Anthropic-compatible surface exposed by the DeeperSeeker bridge.
 *
 * Every route requires the API key, which [ApiKeyInterceptor] attaches.
 */
interface ChatApi {

    @POST("v1/chat/completions")
    suspend fun chatCompletions(@Body body: ChatCompletionRequest): ChatCompletionResponse

    /**
     * Streaming variant. The response body is consumed as raw SSE lines by
     * [com.deeperseeker.app.data.ChatRepository], since the bridge emits
     * `text/event-stream` frames rather than a single JSON document.
     */
    @Streaming
    @POST("v1/chat/completions")
    suspend fun chatCompletionsStream(@Body body: ChatCompletionRequest): Response<ResponseBody>

    @GET("v1/models")
    suspend fun models(): ModelListResponse

    @GET("health")
    suspend fun health(): Response<ResponseBody>
}