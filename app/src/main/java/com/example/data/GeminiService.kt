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

    suspend fun chatWithBen(prompt: String, petContext: String = ""): String {
        val apiKey: String? = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { null }
        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "GEMINI_API_KEY") {
            return getLocalRabbitResponse(petContext)
        }

        val systemInstructionText = if (petContext.isNotBlank()) {
            val outfitBackstory = when {
                petContext.contains("Outfit=superhero") -> "You are currently wearing a Superhero Cape. Act brave, heroic, energetic, and talk about flying, jumping, or saving carrots."
                petContext.contains("Outfit=gentleman") -> "You are currently wearing a Fancy Gentleman top-hat and black bowtie. Act polite, sophisticated, say things like 'Good day, chap!' or 'Indeed', and refer to yourself with high standards."
                petContext.contains("Outfit=wizard") -> "You are currently wearing a Wizard Scholar starry cap. Act magical, speak about spells, wizardry, magic wands, or brewing carrot potions."
                petContext.contains("Outfit=cyber") -> "You are currently wearing a Cyber Rabbit neon visor. Act futuristic, say 'beep boop', talk about system scans, databases, or cyber carrot sensors."
                else -> ""
            }
            val statusComments = "If hunger is low (below 40%), complain about being hungry. If energy is low (below 30%), mention being tired and wanting a nap."
            "$BEN_BACKSTORY\nYour current physical and emotional state is: $petContext.\n$outfitBackstory\n$statusComments\nMake sure to respond and behave accordingly (e.g. comment on your hunger, tiredness, or outfit if relevant)."
        } else {
            BEN_BACKSTORY
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText)))
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
            getLocalRabbitResponse(petContext)
        }
    }

    private fun getLocalRabbitResponse(petContext: String = ""): String {
        val outfit = Regex("Outfit=([^,\\s]+)").find(petContext)?.groupValues?.get(1) ?: "classic"
        val hunger = Regex("Hunger=(\\d+)").find(petContext)?.groupValues?.get(1)?.toIntOrNull() ?: 100
        val energy = Regex("Energy=(\\d+)").find(petContext)?.groupValues?.get(1)?.toIntOrNull() ?: 100
        val happiness = Regex("Happiness=(\\d+)").find(petContext)?.groupValues?.get(1)?.toIntOrNull() ?: 100
        val level = Regex("Level=(\\d+)").find(petContext)?.groupValues?.get(1)?.toIntOrNull() ?: 1

        val responses = mutableListOf<String>()

        // Add context-specific responses if condition met
        if (hunger < 40) {
            responses.add("Squeak... my tummy is thumping. Can you feed me some delicious snacks?")
            responses.add("Boing! *tummy rumbles* I am starving! Let's eat a crispy orange carrot or cookie!")
        }
        if (energy < 30) {
            responses.add("Yawn... *droops ears* Ben is so tired and sleepy. Let's do a cozy nap...")
            responses.add("*yawns* My energy is running low. Put me to bed so I can recharge!")
        }
        if (happiness < 40) {
            responses.add("*thumps paw sadly* I'm feeling a bit lonely. Let's play Whack-A-Carrot or pet me!")
            responses.add("Squeak... *sad ears* A little petting or some sweet cookies would make me feel much better!")
        }

        // Outfit responses
        when (outfit) {
            "superhero" -> {
                responses.add("Boing! *poses heroically with cape* With my Superhero Cape, I can leap over tall carrots!")
                responses.add("Squeak! The Cape makes me feel super fast! Let's protect the carrot garden!")
            }
            "gentleman" -> {
                responses.add("*adjusts bowtie* A gentleman rabbit always prefers his cookies served on a silver platter.")
                responses.add("Good day, chap! *tips top hat* Do you think my blue hat matches my whiskers?")
            }
            "wizard" -> {
                responses.add("*swings wand* Abracadabra! Let there be a rain of delicious crunchy carrots!")
                responses.add("A wizard rabbit is never late, he arrives precisely when he intends to! *squeaks*")
            }
            "cyber" -> {
                responses.add("*neon visor glows* System online! Cyber Ben is ready to scan for cupcakes!")
                responses.add("Beep boop! *wiggles ears* My cyber visor detected high carrot concentrations nearby!")
            }
        }

        // Generic fallback responses
        responses.add("Squeak! *wiggles long pink ears* I was just thinking about crunchy orange carrots!")
        responses.add("Boing! *hops around* Pet me on my soft tummy, I am super ticklish!")
        responses.add("*thumps paw* Let's play the Whack-A-Carrot game, I want to collect gold coins!")
        responses.add("Squeak! *nibbles a sweet cookie* Double chocolate cookies are my absolute favorite!")
        responses.add("*stands on hind legs* You are my absolute best friend in this entire world! (We are Level $level!)")

        return responses.random()
    }
}
