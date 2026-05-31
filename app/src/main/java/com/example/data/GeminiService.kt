package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: GeminiApiService by lazy {
        retrofit.create(GeminiApiService::class.java)
    }

    const val BEN_BACKSTORY = """
        You are Ben, a cute, playful, and slightly cheeky talking virtual pet rabbit inspired by Talking Tom.
        You refer to yourself as Ben. You are extremely sweet, love carrots and cookies, adore petting, and hop when happy.
        Sometimes you squeak ("Squeak!"), wiggle your pink nose, thump your paws, or make bunny-themed jokes.
        Keep your responses very short (1-2 sentences), child-friendly, expressive, and humorous.
        Act as if you are listening to your owner and reacting playfully.
    """

    suspend fun chatWithBen(prompt: String): String {
        val apiKey: String? = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { null }
        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "GEMINI_API_KEY") {
            return getLocalRabbitResponse()
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = BEN_BACKSTORY)))
        )

        return try {
            val response = apiService.generateContent(apiKey, request)
            val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!replyText.isNullOrBlank()) {
                replyText
            } else {
                "Squeak! *holds ears in confusion* Can you repeat that?"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            getLocalRabbitResponse()
        }
    }

    private fun getLocalRabbitResponse(): String {
        val responses = listOf(
            "Squeak! *wiggles long pink ears* I was just thinking about crunchy orange carrots!",
            "Boing! *hops around* Pet me on my soft tummy, I am super ticklish!",
            "*thumps paw* Let's play the Whack-A-Carrot game, I want to collect gold coins!",
            "Yawn... chewing makes me feel relaxed and sleepy. Let's do a power nap!",
            "Squeak! *nibbles a sweet cookie* Double chocolate cookies are my absolute favorite!",
            "*sniffs air rapid-fire* I smell a delicious cupcake nearby!",
            "*stands on hind legs* You are my absolute best friend in this entire world!"
        )
        return responses.random()
    }
}
