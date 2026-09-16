package com.deeperseeker.app.net

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * The admin dashboard surface.
 *
 * The dashboard is server-rendered HTML, so these calls return raw bodies.
 * [ManageRepository] parses the fragments it needs (token rows, status
 * counters) out of the HTML, which keeps the app working against the same
 * endpoints the web UI uses without duplicating server logic.
 */
interface ManageApi {

    @FormUrlEncoded
    @POST("login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
    ): Response<ResponseBody>

    @GET("dashboard")
    suspend fun dashboard(): Response<ResponseBody>

    @FormUrlEncoded
    @POST("tokens/add")
    suspend fun addToken(
        @Field("alias") alias: String,
        @Field("auth_token") authToken: String,
    ): Response<ResponseBody>

    @POST("tokens/{id}/delete")
    suspend fun deleteToken(@Path("id") id: Int): Response<ResponseBody>

    @POST("accounts/reload")
    suspend fun reloadAccounts(): Response<ResponseBody>

    @POST("tokens/{id}/activate")
    suspend fun activateToken(@Path("id") id: Int): Response<ResponseBody>

    @POST("tokens/{id}/deactivate")
    suspend fun deactivateToken(@Path("id") id: Int): Response<ResponseBody>

    @GET("health")
    suspend fun health(): Response<ResponseBody>
}