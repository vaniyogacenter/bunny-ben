package com.example.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

// Represents Ben's currently visual animation state
enum class BenAnimation {
    IDLE,
    HAPPY,       // When petted, gains level, or wins game
    EATING,      // When feeding carrots or cupcakes
    TICKLED,     // When tummy or ears are tapped
    SLEEPING,    // Light sleep/heavy nap
    THINKING,    // Talking to AI
    TALKING      // When speaking a speech bubble response
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
    TOXIC_WEED
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
    }

    fun interactionBubble(msg: String, anim: BenAnimation = BenAnimation.TALKING, duration: Long = 4000) {
        _speechBubble.value = msg
        _animationState.value = anim
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
            _speechBubble.value = "Squeak! Good morning! I'm fully rested and ready to hop!"
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

            // Send to Gemini
            val reply = GeminiClient.chatWithBen(message)
            _isChatLoading.value = false
            _speechBubble.value = reply
            _animationState.value = BenAnimation.TALKING
        }
    }

    // --- MINI-GAME CONTROLLERS ---
    fun startMiniGame() {
        if (stateFlow.value.energy < 0.15f) {
            interactionBubble("Zzz... Ben is too tired to play games! Put me to sleep first!", BenAnimation.IDLE)
            return
        }

        _activeTargets.value = emptyList()
        _gameScore.value = 0
        _gameTimeLeft.value = 20
        _isPlayingGame.value = true
        _animationState.value = BenAnimation.IDLE
        _speechBubble.value = null

        gameLoopJob = viewModelScope.launch {
            var counter = 0
            while (_gameTimeLeft.value > 0) {
                delay(1000)
                _gameTimeLeft.value -= 1
                counter++

                // Spawn carrots dynamically
                if (counter % 1 == 0) {
                    spawnTarget()
                }

                // Autoclear expired targets (say 3 seconds old)
                val now = System.currentTimeMillis()
                _activeTargets.value = _activeTargets.value.filter { now - it.addedTime <= 2500 }
            }
            endMiniGame()
        }
    }

    private fun spawnTarget() {
        val count = randomGenerator.nextInt(1, 3)
        val newTargetsList = _activeTargets.value.toMutableList()
        for (i in 0 until count) {
            val isGolden = randomGenerator.nextFloat() < 0.15f // 15% gold carrot chance
            val isWeed = randomGenerator.nextFloat() < 0.20f   // 20% weed risk factor
            
            val type = when {
                isGolden -> TargetType.GOLDEN_CARROT
                isWeed -> TargetType.TOXIC_WEED
                else -> TargetType.REGULAR_CARROT
            }

            val points = when (type) {
                TargetType.REGULAR_CARROT -> 5
                TargetType.GOLDEN_CARROT -> 20
                TargetType.TOXIC_WEED -> -10
            }

            val target = GameTarget(
                id = randomGenerator.nextInt(100000),
                type = type,
                xOffset = randomGenerator.nextFloat() * 80f + 10f, // 10% to 90%
                yOffset = randomGenerator.nextFloat() * 60f + 15f  // 15% to 75%
                ,points = points
            )
            newTargetsList.add(target)
        }
        _activeTargets.value = newTargetsList
    }

    fun whackTarget(targetId: Int) {
        val target = _activeTargets.value.find { it.id == targetId } ?: return
        _activeTargets.value = _activeTargets.value - target
        
        _gameScore.value = (_gameScore.value + target.points).coerceAtLeast(0)
        
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
            val updated = repository.playMiniGame(coinsWon)
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
