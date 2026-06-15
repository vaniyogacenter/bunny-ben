package com.example.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

// Represents Ben's currently visual animation state
enum class BenAnimation {
    IDLE,
    HAPPY,       // When petted, gains level, or wins game
    EATING,      // When feeding carrots or cupcakes
    TICKLED,     // When tummy or ears are tapped
    SLEEPING,    // Light sleep/heavy nap
    THINKING,    // Talking to AI
    TALKING,     // When speaking a speech bubble response
    DIZZY,       // When swiped or spun
    WAVING,      // Friendly arm wave greeting
    DANCING      // Happy dancing state
}

// Data class for a Whack-A-Carrot item
data class GameTarget(
    val id: Int,
    val type: TargetType,
    val xOffset: Float, // percentage offset 0% to 100%
    val yOffset: Float, // percentage offset 0% to 100%
    val points: Int,
    val addedTime: Long = System.currentTimeMillis()
)

enum class TargetType {
    REGULAR_CARROT,
    GOLDEN_CARROT,
    TOXIC_WEED,
    FREEZE_CLOCK,
    DOUBLE_STAR,
    TNT_BOMB
}

class BenViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BenDatabase.getDatabase(application)
    private val repository = BenRepository(database.benDao())

    // Observe player/pet state from Room DB
    val stateFlow: StateFlow<BenStateEntity> = repository.benStateFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BenStateEntity()
        )

    // Speech bubble spoken by Ben
    private val _speechBubble = MutableStateFlow<String?>("Squeak! Tap me to play, or type in the box to chat with me!")
    val speechBubble: StateFlow<String?> = _speechBubble.asStateFlow()

    // Current visual action/animation state
    private val _animationState = MutableStateFlow(BenAnimation.IDLE)
    val animationState: StateFlow<BenAnimation> = _animationState.asStateFlow()

    // TTS engine states
    private var tts: TextToSpeech? = null
    private val _isSpeakingTts = MutableStateFlow(false)
    val isSpeakingTts: StateFlow<Boolean> = _isSpeakingTts.asStateFlow()
    private val _ttsMuted = MutableStateFlow(false)
    val ttsMuted: StateFlow<Boolean> = _ttsMuted.asStateFlow()

    // Loading indicator for AI network calls
    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    // --- MINI-GAME STATES ---
    private val _isPlayingGame = MutableStateFlow(false)
    val isPlayingGame: StateFlow<Boolean> = _isPlayingGame.asStateFlow()

    private val _gameScore = MutableStateFlow(0)
    val gameScore: StateFlow<Int> = _gameScore.asStateFlow()

    private val _gameTimeLeft = MutableStateFlow(20) // 20 seconds of gameplay
    val gameTimeLeft: StateFlow<Int> = _gameTimeLeft.asStateFlow()

    private val _activeTargets = MutableStateFlow<List<GameTarget>>(emptyList())
    val activeTargets: StateFlow<List<GameTarget>> = _activeTargets.asStateFlow()
    private var gameLoopJob: Job? = null
    private var randomGenerator = Random(System.currentTimeMillis())

    // Outfits catalog
    val outfitsCatalog = listOf(
        Outfit("classic", "Classic White Fur", "Standard soft bunny look.", 0),
        Outfit("superhero", "Superhero Cape", "Grants Ben mega carrot jumps!", 80),
        Outfit("gentleman", "Fancy Gentleman", "Royal Blue top-hat & black bowtie.", 150),
        Outfit("wizard", "Wizard Scholar", "A magic starry cap and wand outline.", 250),
        Outfit("cyber", "Cyber Rabbit", "Sleek glowing futuristic neon visor.", 400)
    )

    // Environment backgrounds
    val environmentCatalog = listOf(
        Environment("classic_meadow", "Sunny Meadow", "#81C784"),
        Environment("cozy_room", "Cozy Living Room", "#FFB74D"),
        Environment("cyber_studio", "Neon Cyber Grid", "#1F1A24")
    )

    // Items catalogs and prices
    val foodCatalog = listOf(
        FoodItem("carrot", "Orange Carrot", "A healthy, crispy snack.", 10, "+15% Hunger"),
        FoodItem("cookie", "Chocolate Cookie", "Extremely sweet treat.", 25, "+30% Hunger"),
        FoodItem("cupcake", "Strawberry Cupcake", "Ben's absolute dream dessert!", 45, "+45% Hunger")
    )

    init {
        // Decrease stats over time for engaging pet mechanic (only if active)
        viewModelScope.launch {
            while (true) {
                delay(60000) // Every 1 minute, reduce biological metrics slightly
                val current = repository.getBenState()
                val updatedHunger = (current.hunger - 0.02f).coerceAtLeast(0.0f)
                val updatedHappiness = (current.happiness - 0.01f).coerceAtLeast(0.1f)
                val updatedEnergy = (current.energy - 0.01f).coerceAtLeast(0.0f)
                
                repository.saveBenState(current.copy(
                    hunger = updatedHunger,
                    happiness = updatedHappiness,
                    energy = updatedEnergy
                ))
            }
        }

        // Initialize TextToSpeech engine
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setPitch(1.4f)
                tts?.setSpeechRate(1.15f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeakingTts.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeakingTts.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeakingTts.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeakingTts.value = false
                    }
                })
            }
        }

        // Collect stateFlow to auto-unlock achievements
        viewModelScope.launch {
            stateFlow.collect { state ->
                if (state.totalFeeds >= 1 && !state.achievementsCsv.contains("first_feed")) {
                    repository.unlockAchievement("first_feed")
                }
                if (state.level >= 5 && !state.achievementsCsv.contains("level_5")) {
                    repository.unlockAchievement("level_5")
                }
                if (state.highScore >= 50 && !state.achievementsCsv.contains("arcade_master")) {
                    repository.unlockAchievement("arcade_master")
                }
                val outfitCount = state.unlockedOutfitsCsv.split(",").filter { it.isNotBlank() }.size
                if (outfitCount > 1 && !state.achievementsCsv.contains("outfit_unlock")) {
                    repository.unlockAchievement("outfit_unlock")
                }
                if (state.unlockedOutfitsCsv.contains("superhero") && !state.achievementsCsv.contains("super_bunny")) {
                    repository.unlockAchievement("super_bunny")
                }
                if (state.environmentId == "cyber_studio" && !state.achievementsCsv.contains("cyber_bunny")) {
                    repository.unlockAchievement("cyber_bunny")
                }
            }
        }
    }

    fun speakText(text: String) {
        if (_ttsMuted.value) return
        val cleanText = text.replace(Regex("\\*.*?\\*"), "").trim()
        if (cleanText.isEmpty()) return
        
        val params = android.os.Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ben_speech_bubble")
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, "ben_speech_bubble")
    }

    fun toggleTtsMute() {
        _ttsMuted.value = !_ttsMuted.value
        if (_ttsMuted.value) {
            stopSpeaking()
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeakingTts.value = false
    }

    fun triggerDizzy() {
        if (_isPlayingGame.value || _animationState.value == BenAnimation.SLEEPING) return
        interactionBubble("Whoa! Everything is spinning around... Squeak! *holds head*", BenAnimation.DIZZY, 3000)
    }

    fun triggerWaving() {
        if (_isPlayingGame.value || _animationState.value == BenAnimation.SLEEPING) return
        interactionBubble("Hi there! Squeak! I'm waving to my favorite owner! *waves paw*", BenAnimation.WAVING, 3000)
    }

    fun triggerDancing() {
        if (_isPlayingGame.value || _animationState.value == BenAnimation.SLEEPING) return
        interactionBubble("Boing boing! Look at my happy dance! *dances*", BenAnimation.DANCING, 4000)
    }

    fun triggerJump() {
        if (_isPlayingGame.value || _animationState.value == BenAnimation.SLEEPING) return
        interactionBubble("Boing! Higher and higher! *hops high*", BenAnimation.HAPPY, 2500)
    }

    fun interactionBubble(msg: String, anim: BenAnimation = BenAnimation.TALKING, duration: Long = 4000) {
        _speechBubble.value = msg
        _animationState.value = anim
        speakText(msg)
        viewModelScope.launch {
            delay(duration)
            if (_speechBubble.value == msg) {
                _speechBubble.value = null
            }
            if (_animationState.value == anim) {
                _animationState.value = BenAnimation.IDLE
            }
        }
    }

    fun petBen() {
        if (_isPlayingGame.value) return
        viewModelScope.launch {
            val updated = repository.petBen()
            
            val reactions = listOf(
                "Ahaha! That tickles my long ears! Squeak!",
                "Purrrr... you are so warm! *flaps nose*",
                "Boing! I love petting! Do it more!",
                "Squeak! I'm the happiest rabbit around!"
            )
            interactionBubble(reactions.random(), BenAnimation.HAPPY)
        }
    }

    fun tickleTummy() {
        if (_isPlayingGame.value) return
        viewModelScope.launch {
            repository.petBen()
            interactionBubble("Squeak! Hehe, my tummy is super ticklish! *thumps paw*", BenAnimation.TICKLED, 3500)
        }
    }

    fun feedBen(itemType: String) {
        if (_isPlayingGame.value) return
        val item = foodCatalog.firstOrNull { it.id == itemType } ?: return
        
        viewModelScope.launch {
            val result = repository.feedBen(itemType)
            if (result.isSuccess) {
                val updated = result.getOrThrow()
                val eatPhases = listOf(
                    "Munch munch, crunch...!",
                    "Chomp! This ${item.name} is incredibly delicious!",
                    "Om nom nom! Squeak! I feel so nourished!"
                )
                interactionBubble(eatPhases.random(), BenAnimation.EATING, 3000)
            } else {
                interactionBubble("Oops! I need more coins to buy a ${item.name}! Play a game to earn coins!", BenAnimation.IDLE)
            }
        }
    }

    fun putToSleep() {
        if (_isPlayingGame.value) return
        stopSpeaking()
        viewModelScope.launch {
            repository.putToSleep()
            _animationState.value = BenAnimation.SLEEPING
            _speechBubble.value = "Zzz... Ben is dreaming about floating giant carrots... Zzz..."
        }
    }

    fun wakeUp() {
        if (_isPlayingGame.value) return
        if (_animationState.value == BenAnimation.SLEEPING) {
            _animationState.value = BenAnimation.IDLE
            val wakeMsg = "Squeak! Good morning! I'm fully rested and ready to hop!"
            _speechBubble.value = wakeMsg
            speakText(wakeMsg)
        }
    }

    fun tryUnlockOutfit(outfitId: String, cost: Int) {
        viewModelScope.launch {
            val result = repository.purchaseOutfit(outfitId, cost)
            if (result.isSuccess) {
                val updated = result.getOrThrow()
                interactionBubble("Wow! Look at my new style! I look absolute premium!", BenAnimation.HAPPY, 3500)
            } else {
                interactionBubble("Oh, I can't afford that yet! I need ${cost - stateFlow.value.coins} more coins! Squeak!", BenAnimation.IDLE)
            }
        }
    }

    fun trySetEnvironment(envId: String) {
        viewModelScope.launch {
            repository.changeEnvironment(envId)
            val desc = environmentCatalog.firstOrNull { it.id == envId }?.name ?: "room"
            interactionBubble("Squeak! I love this new ${desc} background!", BenAnimation.HAPPY, 3000)
        }
    }

    fun claimDailyMission() {
        viewModelScope.launch {
            repository.claimDailyReward(50)
            interactionBubble("Happy Daily Reward! +50 shiny gold coins added to our collection! Boing!", BenAnimation.HAPPY, 4000)
        }
    }

    // --- GEMINI TALK LOGIC ---
    fun sendChatMessage(message: String) {
        if (message.isBlank()) return
        if (_isPlayingGame.value) return

        viewModelScope.launch {
            _isChatLoading.value = true
            _animationState.value = BenAnimation.THINKING
            _speechBubble.value = "Thinking..."

            // Format current state context
            val state = stateFlow.value
            val petContext = "Level=${state.level}, Outfit=${state.selectedOutfitId}, Hunger=${(state.hunger * 100).toInt()}% (100% is full, 0% is starving), Happiness=${(state.happiness * 100).toInt()}% (100% is extremely happy), Energy=${(state.energy * 100).toInt()}% (100% is wide awake, 0% is exhausted)."

            // Send to Gemini
            val reply = GeminiClient.chatWithBen(message, petContext)
            _isChatLoading.value = false
            _speechBubble.value = reply
            _animationState.value = BenAnimation.TALKING
            speakText(reply)
        }
    }

    // --- MINI-GAME CONTROLLERS ---
    private val _isDoublePointsActive = MutableStateFlow(false)
    val isDoublePointsActive: StateFlow<Boolean> = _isDoublePointsActive.asStateFlow()

    private val _isTimeFrozenActive = MutableStateFlow(false)
    val isTimeFrozenActive: StateFlow<Boolean> = _isTimeFrozenActive.asStateFlow()

    fun startMiniGame() {
        if (stateFlow.value.energy < 0.15f) {
            interactionBubble("Zzz... Ben is too tired to play games! Put me to sleep first!", BenAnimation.IDLE)
            return
        }

        stopSpeaking()
        _activeTargets.value = emptyList()
        _gameScore.value = 0
        _gameTimeLeft.value = 20
        _isDoublePointsActive.value = false
        _isTimeFrozenActive.value = false
        _isPlayingGame.value = true
        _animationState.value = BenAnimation.IDLE
        _speechBubble.value = null

        gameLoopJob = viewModelScope.launch {
            var counter = 0
            while (_gameTimeLeft.value > 0) {
                delay(1000)
                if (_isTimeFrozenActive.value) {
                    // Time is frozen, do not decrement time left
                } else {
                    _gameTimeLeft.value -= 1
                }
                counter++

                // Spawn carrots dynamically
                if (counter % 1 == 0) {
                    spawnTarget()
                }

                // Autoclear expired targets (if time is frozen, targets don't expire to let the player whack them!)
                if (!_isTimeFrozenActive.value) {
                    val now = System.currentTimeMillis()
                    _activeTargets.value = _activeTargets.value.filter { now - it.addedTime <= 2500 }
                }
            }
            endMiniGame()
        }
    }

    private fun spawnTarget() {
        val count = randomGenerator.nextInt(1, 3)
        val newTargetsList = _activeTargets.value.toMutableList()
        val score = _gameScore.value
        val isBonusRound = score >= 50

        for (i in 0 until count) {
            val type = if (isBonusRound) {
                // Bonus Round: 85% Golden Carrots, 15% Weed hazards (no other powerups spawn)
                if (randomGenerator.nextFloat() < 0.85f) TargetType.GOLDEN_CARROT else TargetType.TOXIC_WEED
            } else {
                val rand = randomGenerator.nextFloat()
                when {
                    rand < 0.06f -> TargetType.FREEZE_CLOCK
                    rand < 0.12f -> TargetType.DOUBLE_STAR
                    rand < 0.18f -> TargetType.TNT_BOMB
                    rand < 0.30f -> TargetType.GOLDEN_CARROT
                    rand < 0.48f -> TargetType.TOXIC_WEED
                    else -> TargetType.REGULAR_CARROT
                }
            }

            val points = when (type) {
                TargetType.REGULAR_CARROT -> 5
                TargetType.GOLDEN_CARROT -> 20
                TargetType.TOXIC_WEED -> -10
                TargetType.FREEZE_CLOCK -> 10
                TargetType.DOUBLE_STAR -> 15
                TargetType.TNT_BOMB -> 25
            }

            val target = GameTarget(
                id = randomGenerator.nextInt(100000),
                type = type,
                xOffset = randomGenerator.nextFloat() * 80f + 10f, // 10% to 90%
                yOffset = randomGenerator.nextFloat() * 60f + 15f,  // 15% to 75%
                points = points
            )
            newTargetsList.add(target)
        }
        _activeTargets.value = newTargetsList
    }

    fun whackTarget(targetId: Int) {
        val target = _activeTargets.value.find { it.id == targetId } ?: return
        _activeTargets.value = _activeTargets.value - target

        val multiplier = if (_isDoublePointsActive.value) 2 else 1
        val pointsGained = target.points * multiplier
        if (pointsGained > 0) {
            _gameScore.value += pointsGained
        } else {
            _gameScore.value = (_gameScore.value + pointsGained).coerceAtLeast(0)
        }

        // Process special powerup triggers
        when (target.type) {
            TargetType.FREEZE_CLOCK -> {
                _isTimeFrozenActive.value = true
                viewModelScope.launch {
                    delay(4000) // freeze time countdown for 4 seconds
                    _isTimeFrozenActive.value = false
                }
            }
            TargetType.DOUBLE_STAR -> {
                _isDoublePointsActive.value = true
                viewModelScope.launch {
                    delay(6000) // double points active for 6 seconds
                    _isDoublePointsActive.value = false
                }
            }
            TargetType.TNT_BOMB -> {
                // Instantly whack all active regular/golden targets on screen, giving points
                val targetsToWhack = _activeTargets.value.filter { it.type != TargetType.TNT_BOMB && it.type != TargetType.TOXIC_WEED }
                targetsToWhack.forEach { t ->
                    val mult = if (_isDoublePointsActive.value) 2 else 1
                    _gameScore.value += t.points * mult
                }
                // Clear the board of whacked items
                _activeTargets.value = _activeTargets.value.filter { it.type == TargetType.TOXIC_WEED }
            }
            else -> { /* carrots/weeds handled by default point additions */ }
        }

        // Flash interactive feedback on Ben
        _animationState.value = if (target.type == TargetType.TOXIC_WEED) BenAnimation.TICKLED else BenAnimation.HAPPY
        viewModelScope.launch {
            delay(500)
            if (_isPlayingGame.value && _animationState.value != BenAnimation.SLEEPING) {
                _animationState.value = BenAnimation.IDLE
            }
        }
    }

    private fun endMiniGame() {
        _isPlayingGame.value = false
        gameLoopJob?.cancel()
        _activeTargets.value = emptyList()

        val scoreValue = _gameScore.value
        // 1 score point = 0.5 coin, let's round up
        val coinsWon = (scoreValue / 2).coerceAtLeast(5)

        viewModelScope.launch {
            val updated = repository.playMiniGame(coinsWon, scoreValue)
            interactionBubble("Game Over! Score: $scoreValue! Squeak! You won $coinsWon coins!", BenAnimation.HAPPY, 4500)
        }
    }

    // Family members catalog in Bunny Ben's life
    val familyCatalog = listOf(
        FamilyMember("mom", "Ben Mom", "Mother", 2, "👩", "#FF4081", "Loving Mom who cooks sweet organic carrot stews for Ben.", 20, "Oh my sweet bunny! Make sure you chew those carrots 40 times!", "Aww, thank you! Here's some freshly squeezed carrot juice for you! (+50 XP)"),
        FamilyMember("dad", "Ben Dad", "Father", 3, "👨", "#1E88E5", "Hardworking Dad who teaches Ben to hop higher and jump far.", 35, "Work hard, play hard, son! Let me see those level up numbers!", "Proud of you, sport! Keep up the hard hopping! (+100 XP)"),
        FamilyMember("sis", "Ben Sis", "Sister", 4, "👧", "#AB47BC", "Mischievous sister who loves giving Ben high-fives.", 50, "Hi little bro! Bet you can't beat my carrot whacking record!", "Yay! Let's share some chocolate chip cookies! (+150 XP)"),
        FamilyMember("bro", "Ben Bro", "Brother", 5, "👦", "#4CAF50", "Cool older brother who listens to high-intensity synthwave beats.", 80, "Sup lil bro! You're looking absolute premium today!", "Sweet! Keep rockin' those retro cyber shades, bro! (+200 XP)")
    )

    fun giftFamilyMember(memberId: String) {
        val member = familyCatalog.firstOrNull { it.id == memberId } ?: return
        if (stateFlow.value.level < member.introLevel) {
            interactionBubble("Squeak! This family member hasn't arrived in our life yet!", BenAnimation.IDLE)
            return
        }
        if (stateFlow.value.coins < member.giftCost) {
            interactionBubble("Oops! We need ${member.giftCost} coins to gift ${member.name}!", BenAnimation.IDLE)
            return
        }

        viewModelScope.launch {
            val current = repository.getBenState()
            val newCoins = current.coins - member.giftCost
            val rewardXp = when(member.id) {
                "mom" -> 50
                "dad" -> 100
                "sis" -> 150
                "bro" -> 200
                else -> 50
            }
            val newXp = current.xp + rewardXp
            
            // Calculate new level safely
            var level = current.level.coerceAtLeast(1)
            var xp = newXp.coerceAtLeast(0)
            var xpNeeded = level * 100
            var iterations = 0
            while (xp >= xpNeeded && iterations < 500) {
                xp -= xpNeeded
                level++
                xpNeeded = level * 100
                iterations++
            }

            val updated = current.copy(
                coins = newCoins,
                xp = xp,
                level = level,
                happiness = 1.0f
            )
            repository.saveBenState(updated)

            // Show family dialogue
            interactionBubble("${member.name} says: \"${member.giftSuccessDialogue}\"", BenAnimation.HAPPY, 5000)
        }
    }

    fun talkToFamilyMember(memberId: String) {
        val member = familyCatalog.firstOrNull { it.id == memberId } ?: return
        if (stateFlow.value.level < member.introLevel) return
        interactionBubble("${member.name} says: \"${member.dialogue}\"", BenAnimation.HAPPY, 4500)
    }

    fun injectQA_XP(amount: Int) {
        viewModelScope.launch {
            val current = repository.getBenState()
            val newXp = current.xp + amount
            var lvl = current.level.coerceAtLeast(1)
            var xp = newXp.coerceAtLeast(0)
            var xpNeeded = lvl * 100
            var iterations = 0
            while (xp >= xpNeeded && iterations < 500) {
                xp -= xpNeeded
                lvl++
                xpNeeded = lvl * 100
                iterations++
            }
            val updated = current.copy(level = lvl, xp = xp)
            repository.saveBenState(updated)
            interactionBubble("Magic QA XP injection! Level is now $lvl!", BenAnimation.HAPPY)
        }
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
        tts?.stop()
        tts?.shutdown()
    }

    // Achievements calculation
    fun getAchievementsList(state: BenStateEntity): List<Achievement> {
        return listOf(
            Achievement("first_feed", "Carrot Feast 🥕", "Feed Ben at least once", "🥕", state.totalFeeds >= 1),
            Achievement("level_5", "Growing Up 🎂", "Reach Level 5", "🎂", state.level >= 5),
            Achievement("arcade_master", "Arcade Master 🎮", "Score 50+ in Whack-A-Carrot", "🎮", state.highScore >= 50),
            Achievement("outfit_unlock", "Fancy Dresser 🎩", "Unlock a custom outfit", "🎩", state.unlockedOutfitsCsv.split(",").filter { it.isNotBlank() }.size > 1),
            Achievement("super_bunny", "Super Rabbit 🦸", "Own the Superhero Cape", "🦸", state.unlockedOutfitsCsv.split(",").contains("superhero")),
            Achievement("cyber_bunny", "Cosmic Explorer 🌃", "Set Neon Cyber Grid environment", "🌃", state.environmentId == "cyber_studio")
        )
    }

    // Leaderboard calculation
    fun getLeaderboard(state: BenStateEntity): List<LeaderboardEntry> {
        val playerEntry = LeaderboardEntry(0, "You (Ben)", state.level, state.highScore, true)
        val botEntries = listOf(
            LeaderboardEntry(0, "Talking Tom 🐱", 15, 80, false),
            LeaderboardEntry(0, "Angela 🐈", 12, 65, false),
            LeaderboardEntry(0, "Ginger 🐕", 5, 30, false),
            LeaderboardEntry(0, "Hank 🐶", 3, 15, false)
        )
        val allEntries = (botEntries + playerEntry).sortedByDescending { it.score * 1000 + it.level }
        return allEntries.mapIndexed { index, entry ->
            entry.copy(rank = index + 1)
        }
    }

    // Friends visit & gift actions
    val friendsCatalog = listOf(
        Friend("tom", "Talking Tom", 15, "Fancy Gentleman", "🐱", "Meow! My bunny is resting. Let's trade carrots!", 30),
        Friend("angela", "Angela", 12, "Classic White Fur", "🐈", "Bonjour! How is your Bunny Ben doing today?", 40),
        Friend("ginger", "Ginger", 5, "Classic White Fur", "🐕", "Haha! Play some Whack-A-Carrot with me!", 20),
        Friend("hank", "Hank", 3, "Classic White Fur", "🐶", "Whoa, look at your cool outfit! Squeak!", 15)
    )

    fun visitFriend(friendId: String) {
        val friend = friendsCatalog.firstOrNull { it.id == friendId } ?: return
        viewModelScope.launch {
            val current = repository.getBenState()
            val newCoins = current.coins + friend.giftRewardCoins
            val newXp = current.xp + 25
            
            // Calculate new level progress
            var level = current.level.coerceAtLeast(1)
            var xp = newXp.coerceAtLeast(0)
            var xpNeeded = level * 100
            var iterations = 0
            while (xp >= xpNeeded && iterations < 500) {
                xp -= xpNeeded
                level++
                xpNeeded = level * 100
                iterations++
            }

            val updated = current.copy(coins = newCoins, xp = xp, level = level)
            repository.saveBenState(updated)
            interactionBubble("Visited ${friend.name}! ${friend.dialog} You received +${friend.giftRewardCoins} coins and +25 XP!", BenAnimation.HAPPY, 5000)
        }
    }

    fun giftFriend(friendId: String) {
        val friend = friendsCatalog.firstOrNull { it.id == friendId } ?: return
        viewModelScope.launch {
            val current = repository.getBenState()
            if (current.coins < 25) {
                interactionBubble("Oops! You need 25 coins to gift ${friend.name}!", BenAnimation.IDLE)
                return@launch
            }
            val newCoins = current.coins - 25
            val newXp = current.xp + 60

            // Calculate new level progress
            var level = current.level.coerceAtLeast(1)
            var xp = newXp.coerceAtLeast(0)
            var xpNeeded = level * 100
            var iterations = 0
            while (xp >= xpNeeded && iterations < 500) {
                xp -= xpNeeded
                level++
                xpNeeded = level * 100
                iterations++
            }

            val updated = current.copy(coins = newCoins, xp = xp, level = level)
            repository.saveBenState(updated)
            interactionBubble("Gave a gift to ${friend.name}! They love it! You earned +60 XP!", BenAnimation.HAPPY, 5000)
        }
    }

    fun shareBenStats(context: android.content.Context) {
        val state = stateFlow.value
        val text = "🐰 Check out my virtual pet rabbit Bunny Ben! He is Level ${state.level}, has ${state.coins} coins, and unlocked ${state.selectedOutfitId} outfit! Can you beat my high score of ${state.highScore} in Whack-A-Carrot? Join the fun! ✨"
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share Bunny Ben Stats"))
    }
}

// Data models for metadata arrays
data class FamilyMember(
    val id: String,
    val name: String,
    val relation: String,
    val introLevel: Int,
    val emoji: String,
    val color: String,
    val desc: String,
    val giftCost: Int,
    val dialogue: String,
    val giftSuccessDialogue: String
)

data class Outfit(
    val id: String,
    val name: String,
    val desc: String,
    val coinCost: Int
)

data class Environment(
    val id: String,
    val name: String,
    val hexColor: String
)

data class FoodItem(
    val id: String,
    val name: String,
    val desc: String,
    val coinCost: Int,
    val statBenefit: String
)

data class Achievement(
    val id: String,
    val title: String,
    val desc: String,
    val icon: String,
    val isUnlocked: Boolean
)

data class Friend(
    val id: String,
    val name: String,
    val level: Int,
    val outfit: String,
    val emoji: String,
    val dialog: String,
    val giftRewardCoins: Int
)

data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val level: Int,
    val score: Int,
    val isPlayer: Boolean
)
