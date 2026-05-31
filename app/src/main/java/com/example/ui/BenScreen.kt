package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.BenAnimation
import com.example.viewmodel.BenViewModel
import com.example.viewmodel.GameTarget
import com.example.viewmodel.TargetType
import com.example.viewmodel.FoodItem
import com.example.viewmodel.Outfit
import com.example.viewmodel.Environment
import kotlinx.coroutines.delay

fun parseHexColor(hex: String): Color {
    return try {
        val cleanHex = hex.trim().removePrefix("#")
        if (cleanHex.length == 6) {
            Color((cleanHex.toLong(16) or 0xFF000000))
        } else if (cleanHex.length == 8) {
            Color(cleanHex.toLong(16))
        } else {
            Color.Gray
        }
    } catch (e: Throwable) {
        Color.Gray
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenScreen(
    viewModel: BenViewModel,
    modifier: Modifier = Modifier
) {
    val bState by viewModel.stateFlow.collectAsStateWithLifecycle()
    val speechBubble by viewModel.speechBubble.collectAsStateWithLifecycle()
    val animationState by viewModel.animationState.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()

    val isPlayingGame by viewModel.isPlayingGame.collectAsStateWithLifecycle()
    val gameScore by viewModel.gameScore.collectAsStateWithLifecycle()
    val gameTimeLeft by viewModel.gameTimeLeft.collectAsStateWithLifecycle()
    val activeTargets by viewModel.activeTargets.collectAsStateWithLifecycle()

    var activePanel by remember { mutableStateOf<String?>(null) } // "feed", "wardrobe", "chat", "environment"
    var chatInputText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // Background color parsing based on state
    val backgroundColor = when (bState.environmentId) {
        "cozy_room" -> Color(0xFFFBE9E7)   // Soft warm couch sunset color
        "cyber_studio" -> Color(0xFF120E16) // Cyberpunk dark violet
        else -> Color(0xFFE8F5E9)          // Green sunny meadow
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // 1. MAIN INTERACTIVE CAMPUS (Ben + Room decorators)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (isPlayingGame) 0.dp else 120.dp)
        ) {
            // Environment background decorations
            EnvironmentBackground(envId = bState.environmentId)

            // active family visitors floating on the left side of the room!
            if (!isPlayingGame) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp, top = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    viewModel.familyCatalog.forEach { member ->
                        val isUnlocked = bState.level >= member.introLevel
                        if (isUnlocked) {
                            Card(
                                shape = CircleShape,
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                                modifier = Modifier
                                    .size(54.dp)
                                    .shadow(4.dp, CircleShape)
                                    .border(2.5.dp, parseHexColor(member.color), CircleShape)
                                    .clickable { viewModel.talkToFamilyMember(member.id) }
                                    .testTag("visitor_${member.id}")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(member.emoji, fontSize = 20.sp)
                                        Text(member.name.split(" ").last(), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color.DarkGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Family Button on the right side of the screen!
            if (!isPlayingGame) {
                val unlockedCount = viewModel.familyCatalog.count { bState.level >= it.introLevel }
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp, top = 140.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier
                            .width(66.dp)
                            .shadow(6.dp, RoundedCornerShape(16.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                            .clickable { activePanel = "family" }
                            .testTag("family_panel_button")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("👪", fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Family", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            Text("$unlockedCount/4", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            // Dynamic header stats (Only if not playing Whack-a-Carrot)
            if (!isPlayingGame) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Level and XP section
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.shadow(2.dp, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFFF9800), CircleShape)
                                ) {
                                    Text(
                                        text = bState.level.toString(),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Ben the Rabbit",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val xpNeeded = bState.level * 100
                                    val progress = if (xpNeeded > 0) {
                                        (bState.xp.toFloat() / xpNeeded.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        color = Color(0xFFFF9800),
                                        trackColor = Color.DarkGray.copy(alpha = 0.2f),
                                        modifier = Modifier
                                            .width(86.dp)
                                            .height(6.dp)
                                            .clip(CircleShape)
                                    )
                                    Text(
                                        text = "${bState.xp}/${xpNeeded} XP",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Light
                                    )
                                }
                            }
                        }

                        // Gold Coins indicator
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFF9C4)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .border(1.5.dp, Color(0xFFFBC02D), RoundedCornerShape(20.dp))
                                .shadow(2.dp, RoundedCornerShape(20.dp))
                                .clickable { viewModel.claimDailyMission() }
                                .testTag("coin_wallet")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🪙",
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = bState.coins.toString(),
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bio status meters (Hunger, Fun, Energy)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatMeter(label = "😋 Hunger", value = bState.hunger, color = Color(0xFF4CAF50), tag = "hunger_bar")
                        StatMeter(label = "💖 Happiness", value = bState.happiness, color = Color(0xFFE91E63), tag = "happiness_bar")
                        StatMeter(label = "🔋 Energy", value = bState.energy, color = Color(0xFF2196F3), tag = "energy_bar")
                    }
                }
            }

            // Speech Bubble Overlay
            AnimatedVisibility(
                visible = speechBubble != null && !isPlayingGame,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 135.dp, start = 24.dp, end = 24.dp)
            ) {
                speechBubble?.let { text ->
                    SpeechBubbleView(text = text)
                }
            }

            // Ben the visual custom composed Bunny!
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.65f)
                    .align(Alignment.Center)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        viewModel.petBen()
                    }
                    .testTag("ben_pet_area"),
                contentAlignment = Alignment.BottomCenter
            ) {
                BenDrawing(
                    animation = animationState,
                    outfitId = bState.selectedOutfitId,
                    onTongueTap = { viewModel.tickleTummy() }
                )
            }
        }

        // 2. WHACK-A-CARROT ARCADE SCREEN OVERLAY
        AnimatedVisibility(
            visible = isPlayingGame,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Background game grid
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0D47A1), Color(0xFF1B5E20))
                    )
                    drawRect(brush = brush)
                    // Grid lines
                    for (i in 1..4) {
                        val x = size.width * (i / 5f)
                        drawLine(Color.White.copy(alpha = 0.15f), Offset(x, 0f), Offset(x, size.height), 2f)
                    }
                    for (j in 1..6) {
                        val y = size.height * (j / 7f)
                        drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, y), Offset(size.width, y), 2f)
                    }
                }

                // Arcade Header Panel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("🥕 Whack-A-Carrot!", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Tap carrots to buy outfits! Avoid toxic weeds!", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE65100))) {
                            Text("Score: $gameScore", color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = if (gameTimeLeft < 5) Color.Red else Color.DarkGray)) {
                            Text("⏳ ${gameTimeLeft}s", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                        }
                    }
                }

                // Bouncing Game Targets
                activeTargets.forEach { target ->
                    GameTargetBubble(
                        target = target,
                        areaWidth = maxWidth,
                        areaHeight = maxHeight,
                        onTap = { viewModel.whackTarget(target.id) }
                    )
                }
            }
        }

        // 3. BOTTOM UTILITY PANEL & NAVIGATION (Only if not in active Arcade Game)
        if (!isPlayingGame) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Secondary interactive contextual controls (Feed options, outfits list, environment switcher, etc.)
                AnimatedContent(
                    targetState = activePanel,
                    transitionSpec = {
                        slideInVertically(initialOffsetY = { it }) + fadeIn() togetherWith
                                slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    },
                    label = "UtilityPanel"
                ) { panel ->
                    when (panel) {
                        "feed" -> {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(8.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Feed Ben Rabbit 🥕", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        IconButton(onClick = { activePanel = null }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(viewModel.foodCatalog) { food ->
                                            FoodCard(
                                                item = food,
                                                playerCoins = bState.coins,
                                                onFeed = { viewModel.feedBen(food.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "wardrobe" -> {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(8.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Bunny Wardrobe 👑", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        IconButton(onClick = { activePanel = null }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(viewModel.outfitsCatalog) { outfit ->
                                            val isUnlocked = bState.unlockedOutfitsCsv.split(",").contains(outfit.id)
                                            val isSelected = bState.selectedOutfitId == outfit.id
                                            OutfitCard(
                                                outfit = outfit,
                                                isUnlocked = isUnlocked,
                                                isSelected = isSelected,
                                                playerCoins = bState.coins,
                                                onSelect = {
                                                    viewModel.tryUnlockOutfit(outfit.id, outfit.coinCost)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "chat" -> {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(8.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Chat with AI Ben 💬", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        IconButton(onClick = { activePanel = null }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close")
                                        }
                                    }
                                    Text("Ask Ben anything! Powered by server-side Gemini AI.", fontSize = 11.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextField(
                                            value = chatInputText,
                                            onValueChange = { chatInputText = it },
                                            placeholder = { Text("What are you doing today, Ben?") },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("chat_input_text"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = TextFieldDefaults.colors(
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent
                                            ),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                            keyboardActions = KeyboardActions(onSend = {
                                                if (chatInputText.isNotBlank()) {
                                                    viewModel.sendChatMessage(chatInputText)
                                                    chatInputText = ""
                                                    focusManager.clearFocus()
                                                }
                                            }),
                                            maxLines = 2
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                if (chatInputText.isNotBlank()) {
                                                    viewModel.sendChatMessage(chatInputText)
                                                    chatInputText = ""
                                                    focusManager.clearFocus()
                                                }
                                            },
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                .testTag("send_chat_button"),
                                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                        ) {
                                            if (isChatLoading) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Message to Ben")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "environment" -> {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(8.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Change Environment 🏡", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        IconButton(onClick = { activePanel = null }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        viewModel.environmentCatalog.forEach { env ->
                                            val isSelected = bState.environmentId == env.id
                                            val envBgColor = when (env.id) {
                                                "cozy_room" -> Color(0xFFFFB74D)
                                                "cyber_studio" -> Color(0xFF1F1A24)
                                                else -> Color(0xFF81C784)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1.5f)
                                                    .shadow(2.dp, RoundedCornerShape(16.dp))
                                                    .background(envBgColor, RoundedCornerShape(16.dp))
                                                    .border(
                                                        2.dp,
                                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                        RoundedCornerShape(16.dp)
                                                    )
                                                    .clickable { viewModel.trySetEnvironment(env.id) }
                                                    .padding(10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = env.name,
                                                    color = if (env.id == "cyber_studio") Color.White else Color.Black,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "family" -> {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(8.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Bunny Family 👪", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                            Text("Unlocked: ${viewModel.familyCatalog.count { bState.level >= it.introLevel }}/4 - Levels unlock family members!", fontSize = 11.sp, color = Color.Gray)
                                        }
                                        IconButton(onClick = { activePanel = null }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(viewModel.familyCatalog) { member ->
                                            val isUnlocked = bState.level >= member.introLevel
                                            FamilyCard(
                                                member = member,
                                                unlocked = isUnlocked,
                                                currentCoins = bState.coins,
                                                onTalk = { viewModel.talkToFamilyMember(member.id) },
                                                onGift = { viewModel.giftFamilyMember(member.id) },
                                                onInjectXp = { viewModel.injectQA_XP(50) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // PRIMARY NAVIGATION DECK
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp)
                        .background(MaterialTheme.colorScheme.surface),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionNavButton(
                            icon = "🍎",
                            label = "Feed",
                            isSelected = activePanel == "feed",
                            tag = "nav_feed",
                            onClick = { activePanel = if (activePanel == "feed") null else "feed" }
                        )

                        ActionNavButton(
                            icon = "👕",
                            label = "Wardrobe",
                            isSelected = activePanel == "wardrobe",
                            tag = "nav_wardrobe",
                            onClick = { activePanel = if (activePanel == "wardrobe") null else "wardrobe" }
                        )

                        // PLAY ARCADE CORE BUTTON (Whack-a-Carrot)
                        Box(
                            modifier = Modifier
                                .offset(y = (-14).dp)
                                .shadow(8.dp, CircleShape)
                                .background(Color(0xFFF57C00), CircleShape)
                                .clickable { viewModel.startMiniGame() }
                                .padding(12.dp)
                                .testTag("nav_play_game")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🎮", fontSize = 28.sp)
                                Text("PLAY", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp)
                            }
                        }

                        ActionNavButton(
                            icon = "💬",
                            label = "AI Chat",
                            isSelected = activePanel == "chat",
                            tag = "nav_chat",
                            onClick = { activePanel = if (activePanel == "chat") null else "chat" }
                        )

                        if (animationState == BenAnimation.SLEEPING) {
                            ActionNavButton(
                                icon = "⏰",
                                label = "Wake",
                                isSelected = false,
                                tag = "nav_wake",
                                onClick = { viewModel.wakeUp() }
                            )
                        } else {
                            ActionNavButton(
                                icon = "💤",
                                label = "Sleep",
                                isSelected = false,
                                tag = "nav_sleep",
                                onClick = { viewModel.putToSleep() }
                            )
                        }

                        ActionNavButton(
                            icon = "🏡",
                            label = "Room",
                            isSelected = activePanel == "environment",
                            tag = "nav_room",
                            onClick = { activePanel = if (activePanel == "environment") null else "environment" }
                        )
                    }
                }
            }
        }
    }
}

// Stats Progress Bar Model
@Composable
fun StatMeter(
    label: String,
    value: Float,
    color: Color,
    tag: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(108.dp)
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .testTag(tag)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            val safeProgress = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { safeProgress },
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
            )
        }
    }
}

// Secondary Navigation Item Composable
@Composable
fun ActionNavButton(
    icon: String,
    label: String,
    isSelected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 24.sp)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Speech Bubble Component
@Composable
fun SpeechBubbleView(text: String) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
        modifier = Modifier
            .widthIn(max = 280.dp)
            .border(2.dp, Color(0xFFFF9800), RoundedCornerShape(20.dp))
            .shadow(4.dp, RoundedCornerShape(20.dp))
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Bouncing Target for whacking!
@Composable
fun GameTargetBubble(
    target: GameTarget,
    areaWidth: androidx.compose.ui.unit.Dp,
    areaHeight: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "TargetShake")
    val sizePulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    val bubbleColor = when (target.type) {
        TargetType.REGULAR_CARROT -> Color(0xFFFF9800)
        TargetType.GOLDEN_CARROT -> Color(0xFFFFD54F)
        TargetType.TOXIC_WEED -> Color(0xFF9C27B0)
    }

    val bubbleSize = 64.dp
    val usableWidth = (areaWidth - bubbleSize).coerceAtLeast(0.dp)
    val usableHeight = (areaHeight - bubbleSize - 120.dp).coerceAtLeast(0.dp)

    val posX = ((target.xOffset / 100f) * usableWidth.value).dp
    val posY = 75.dp + ((target.yOffset / 100f) * usableHeight.value).dp

    Box(
        modifier = Modifier
            .offset(
                x = posX,
                y = posY
            )
            .size((64.dp * sizePulse))
            .background(bubbleColor, CircleShape)
            .shadow(6.dp, CircleShape)
            .border(3.dp, Color.White, CircleShape)
            .clickable { onTap() }
            .testTag("game_target_${target.id}"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (target.type) {
                    TargetType.REGULAR_CARROT -> "🥕"
                    TargetType.GOLDEN_CARROT -> "🏆🥕"
                    TargetType.TOXIC_WEED -> "💀"
                },
                fontSize = 24.sp
            )
            Text(
                text = if (target.points > 0) "+${target.points}" else "${target.points}",
                fontSize = 11.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Food Carousel Card
@Composable
fun FoodCard(
    item: FoodItem,
    playerCoins: Int,
    onFeed: () -> Unit
) {
    val canAfford = playerCoins >= item.coinCost
    Card(
        modifier = Modifier
            .width(132.dp)
            .border(
                1.5.dp,
                if (canAfford) Color(0xFF81C784) else Color(0xFFE57373),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (item.id) {
                    "carrot" -> "🥕"
                    "cookie" -> "🍪"
                    "cupcake" -> "🧁"
                    else -> "🍎"
                },
                fontSize = 32.sp
            )
            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            Text(item.statBenefit, color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onFeed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("feed_button_${item.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canAfford) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(2.dp)
            ) {
                Text("🪙 ${item.coinCost}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

// Outfit Purchase/Equip Card
@Composable
fun OutfitCard(
    outfit: Outfit,
    isUnlocked: Boolean,
    isSelected: Boolean,
    playerCoins: Int,
    onSelect: () -> Unit
) {
    val canAfford = playerCoins >= outfit.coinCost || isUnlocked
    Card(
        modifier = Modifier
            .width(140.dp)
            .border(
                1.5.dp,
                if (isSelected) Color(0xFFFF9800) else Color.Transparent,
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (outfit.id) {
                    "classic" -> "🐇"
                    "superhero" -> "🦸‍♂️🐇"
                    "gentleman" -> "🎩🐇"
                    "wizard" -> "🧙‍♂️🐇"
                    "cyber" -> "🕶️🐇"
                    else -> "🐇"
                },
                fontSize = 28.sp
            )
            Text(outfit.name, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
            Text(outfit.desc, fontSize = 9.sp, color = Color.Gray, maxLines = 1)
            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .testTag("equip_button_${outfit.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isSelected -> Color(0xFF4CAF50)
                        isUnlocked -> Color(0xFF2196F3)
                        canAfford -> Color(0xFFFF9800)
                        else -> Color(0xFF9E9E9E)
                    }
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(2.dp)
            ) {
                Text(
                    text = when {
                        isSelected -> "Equipped"
                        isUnlocked -> "Wear"
                        else -> "🪙 ${outfit.coinCost}"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// Composed background items based on custom static options
@Composable
fun EnvironmentBackground(envId: String) {
    val hillPath1 = remember { Path() }
    val hillPath2 = remember { Path() }
    when (envId) {
        "cozy_room" -> {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Drawing floor
                drawRect(
                    color = Color(0xFF795548),
                    topLeft = Offset(0f, size.height * 0.65f),
                    size = Size(size.width, size.height * 0.35f)
                )
                // Rug
                drawOval(
                    color = Color(0xFFEF9A9A),
                    topLeft = Offset(size.width * 0.15f, size.height * 0.72f),
                    size = Size(size.width * 0.7f, size.height * 0.16f)
                )
                // Window
                drawRoundRect(
                    color = Color(0xFFFFCC80),
                    topLeft = Offset(size.width * 0.65f, size.height * 0.12f),
                    size = Size(size.width * 0.25f, size.height * 0.28f),
                    cornerRadius = CornerRadius(16f, 16f)
                )
                drawRect(
                    color = Color(0xFF90CAF9),
                    topLeft = Offset(size.width * 0.67f, size.height * 0.14f),
                    size = Size(size.width * 0.21f, size.height * 0.24f)
                )
            }
        }
        "cyber_studio" -> {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw digital matrix layout
                val floorY = size.height * 0.65f
                drawRect(Color(0xFF0C0910))

                // Cyan grid floor
                drawLine(Color(0xFF00FFFF).copy(alpha = 0.4f), Offset(0f, floorY), Offset(size.width, floorY), 4f)
                for (i in 0..12) {
                    val xStart = size.width * (i / 12f)
                    val xEnd = size.width * -0.5f + (size.width * 2f * (i / 12f))
                    drawLine(Color(0xFF00FFFF).copy(alpha = 0.25f), Offset(xStart, floorY), Offset(xEnd, size.height), 2f)
                }
                // Horizontal lines with perspective density
                var cy = floorY
                var gap = 12f
                var lineCounter = 0
                while (cy < size.height && lineCounter < 100) {
                    drawLine(Color(0xFF00FFFF).copy(alpha = 0.25f), Offset(0f, cy), Offset(size.width, cy), 1.5f)
                    gap *= 1.35f
                    cy += gap
                    lineCounter++
                }

                // Cyber neon bunny polygon logo on background
                drawCircle(color = Color(0xFFFF007F).copy(alpha = 0.15f), radius = 250f, center = Offset(size.width / 2f, size.height / 2f - 100f))
            }
        }
        else -> { // classic_meadow
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Blue sky
                drawRect(Color(0xFFE3F2FD))
                // Rolling soft green hills
                hillPath1.reset()
                hillPath1.moveTo(0f, size.height * 0.58f)
                hillPath1.quadraticTo(size.width * 0.4f, size.height * 0.5f, size.width, size.height * 0.62f)
                hillPath1.lineTo(size.width, size.height)
                hillPath1.lineTo(0f, size.height)
                hillPath1.close()
                drawPath(hillPath1, Color(0xFFC8E6C9))

                hillPath2.reset()
                hillPath2.moveTo(0f, size.height * 0.7f)
                hillPath2.quadraticTo(size.width * 0.6f, size.height * 0.78f, size.width, size.height * 0.68f)
                hillPath2.lineTo(size.width, size.height)
                hillPath2.lineTo(0f, size.height)
                hillPath2.close()
                drawPath(hillPath2, Color(0xFF81C784))

                // Sun
                drawCircle(color = Color(0xFFFFF176), radius = 50f, center = Offset(size.width * 0.15f, size.height * 0.15f))
            }
        }
    }
}

// THE RESPONSIVE CORNERSTONE CANVAS DRAWING OF BEN
@Composable
fun BenDrawing(
    animation: BenAnimation,
    outfitId: String,
    onTongueTap: () -> Unit
) {
    // Preallocate all Path objects to avoid high GC pressure / Out Of Memory crashes in draw loop
    val capePath = remember { Path() }
    val nosePath = remember { Path() }
    val leftBowPath = remember { Path() }
    val rightBowPath = remember { Path() }
    val wizardHatPath = remember { Path() }

    // Basic loop variables for idle wiggle, blinking eyes, chewing
    val infiniteTransition = rememberInfiniteTransition(label = "BunnyIdleAnim")
    
    val earWiggle by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Ears"
    )

    val blinkState by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3500
                1.0f at 0
                1.0f at 3400
                0.0f at 3450
                1.0f at 3500
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Blink"
    )

    val chewState by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Chew"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onTongueTap()
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height * 0.6f

        // 1. BACK DECORATIONS (Superhero red cape)
        if (outfitId == "superhero") {
            capePath.reset()
            capePath.moveTo(cx - 110f, cy + 80f)
            capePath.lineTo(cx - 240f, size.height - 20f)
            capePath.lineTo(cx + 240f, size.height - 20f)
            capePath.lineTo(cx + 110f, cy + 80f)
            capePath.close()
            drawPath(capePath, Color(0xFFD32F2F)) // Royal Crimson Superhero Cape
        }

        // 2. RABBIT MAIN LEGS AND FEET
        drawCircle(color = Color(0xFFEFEFEF), radius = 45f, center = Offset(cx - 100f, cy + 220f)) // Left foot
        drawCircle(color = Color(0xFFEFEFEF), radius = 45f, center = Offset(cx + 100f, cy + 220f)) // Right foot
        drawCircle(color = Color(0xFFFFCDD2), radius = 25f, center = Offset(cx - 100f, cy + 220f)) // Left pink pad
        drawCircle(color = Color(0xFFFFCDD2), radius = 25f, center = Offset(cx + 100f, cy + 220f)) // Right pink pad

        // 3. FLUFFY BUNNY BODY
        drawOval(
            color = Color(0xFFF9F9F9),
            topLeft = Offset(cx - 140f, cy - 30f),
            size = Size(280f, 280f)
        )
        // Fluffy belly pad
        drawOval(
            color = Color(0xFFFFFFFF),
            topLeft = Offset(cx - 95f, cy + 20f),
            size = Size(190f, 190f)
        )

        // 4. RABBIT EARS (With selective rotate)
        val earOffsetL = if (animation == BenAnimation.HAPPY) earWiggle * 2.5f else earWiggle
        val earOffsetR = if (animation == BenAnimation.HAPPY) -earWiggle * 2.5f else -earWiggle

        // Left Ear
        drawRoundRect(
            color = Color(0xFFEFEFEF),
            topLeft = Offset(cx - 100f, cy - 350f),
            size = Size(65f, 220f),
            cornerRadius = CornerRadius(40f, 40f)
        )
        drawRoundRect(
            color = Color(0xFFFFCDD2),
            topLeft = Offset(cx - 85f, cy - 325f),
            size = Size(35f, 175f),
            cornerRadius = CornerRadius(20f, 20f)
        )

        // Right Ear
        drawRoundRect(
            color = Color(0xFFEFEFEF),
            topLeft = Offset(cx + 35f, cy - 350f),
            size = Size(65f, 220f),
            cornerRadius = CornerRadius(40f, 40f)
        )
        drawRoundRect(
            color = Color(0xFFFFCDD2),
            topLeft = Offset(cx + 50f, cy - 325f),
            size = Size(35f, 175f),
            cornerRadius = CornerRadius(20f, 20f)
        )

        // 5. RABBIT HEAD
        drawCircle(color = Color(0xFFFFFFFF), radius = 160f, center = Offset(cx, cy - 80f))

        // Cheek rosy circles
        drawCircle(color = Color(0xFFFFCDD2).copy(alpha = 0.55f), radius = 35f, center = Offset(cx - 105f, cy - 50f))
        drawCircle(color = Color(0xFFFFCDD2).copy(alpha = 0.55f), radius = 35f, center = Offset(cx + 105f, cy - 50f))

        // 6. BUNNY EYES (Animate blink, closed for sleep)
        val eyeRadius = 24f
        val eyeY = cy - 110f
        val isEyesClosed = (animation == BenAnimation.SLEEPING)

        if (isEyesClosed) {
            // Draw sleeping curved lines
            drawArc(
                color = Color(0xFF263238),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - 75f, eyeY - 12f),
                size = Size(40f, 24f),
                style = Stroke(width = 6f)
            )
            drawArc(
                color = Color(0xFF263238),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx + 35f, eyeY - 12f),
                size = Size(40f, 24f),
                style = Stroke(width = 6f)
            )
        } else if (animation == BenAnimation.HAPPY) {
            // Animated happy curves pointing upward
            drawArc(
                color = Color(0xFF1B5E20),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - 75f, eyeY - 10f),
                size = Size(40f, 30f),
                style = Stroke(width = 8f)
            )
            drawArc(
                color = Color(0xFF1B5E20),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx + 35f, eyeY - 10f),
                size = Size(40f, 30f),
                style = Stroke(width = 8f)
            )
        } else {
            // Standard blinking eyes
            if (blinkState > 0.15f) {
                // Outer eye
                drawCircle(color = Color(0xFF212121), radius = eyeRadius, center = Offset(cx - 55f, eyeY))
                drawCircle(color = Color(0xFF212121), radius = eyeRadius, center = Offset(cx + 55f, eyeY))
                // White highlight sparkles
                drawCircle(color = Color.White, radius = 7f, center = Offset(cx - 62f, eyeY - 8f))
                drawCircle(color = Color.White, radius = 7f, center = Offset(cx + 48f, eyeY - 8f))
            } else {
                // Blink closed flat line
                drawLine(Color(0xFF212121), Offset(cx - 75f, eyeY), Offset(cx - 35f, eyeY), 6f)
                drawLine(Color(0xFF212121), Offset(cx + 35f, eyeY), Offset(cx + 75f, eyeY), 6f)
            }
        }

        // 7. PINK NOSE (Wiggles on idle)
        val noseY = cy - 70f
        nosePath.reset()
        nosePath.moveTo(cx - 18f, noseY)
        nosePath.lineTo(cx + 18f, noseY)
        nosePath.lineTo(cx, noseY + 16f)
        nosePath.close()
        drawPath(nosePath, Color(0xFFE91E63))

        // 8. CUTE MOUTH (Dynamic chew scale)
        val currentChew = if (animation == BenAnimation.EATING) chewState else 0f
        drawArc(
            color = Color(0xFF263238),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - 30f, cy - 64f + (currentChew / 2f)),
            size = Size(30f, 25f + currentChew),
            style = Stroke(width = 5f)
        )
        drawArc(
            color = Color(0xFF263238),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx, cy - 64f + (currentChew / 2f)),
            size = Size(30f, 25f + currentChew),
            style = Stroke(width = 5f)
        )

        // Bunny cute buck teeth!
        drawRect(
            color = Color.White,
            topLeft = Offset(cx - 14f, cy - 48f + currentChew),
            size = Size(13f, 18f)
        )
        drawRect(
            color = Color.White,
            topLeft = Offset(cx + 1f, cy - 48f + currentChew),
            size = Size(13f, 18f)
        )
        drawRect(
            color = Color.LightGray,
            topLeft = Offset(cx - 14f, cy - 48f + currentChew),
            size = Size(13f, 18f),
            style = Stroke(width = 1.5f)
        )
        drawRect(
            color = Color.LightGray,
            topLeft = Offset(cx + 1f, cy - 48f + currentChew),
            size = Size(13f, 18f),
            style = Stroke(width = 1.5f)
        )

        // 9. WHISKERS
        drawLine(Color.Gray.copy(alpha = 0.5f), Offset(cx - 75f, cy - 50f), Offset(cx - 150f, cy - 60f), 3f)
        drawLine(Color.Gray.copy(alpha = 0.5f), Offset(cx - 75f, cy - 42f), Offset(cx - 160f, cy - 46f), 3f)
        
        drawLine(Color.Gray.copy(alpha = 0.5f), Offset(cx + 75f, cy - 50f), Offset(cx + 150f, cy - 60f), 3f)
        drawLine(Color.Gray.copy(alpha = 0.5f), Offset(cx + 75f, cy - 42f), Offset(cx + 160f, cy - 46f), 3f)

        // 10. FRONT ARMS/PAWS
        drawCircle(color = Color(0xFFFFFFFF), radius = 32f, center = Offset(cx - 110f, cy + 90f))
        drawCircle(color = Color(0xFFFFFFFF), radius = 32f, center = Offset(cx + 110f, cy + 90f))

        // 11. OUTFITS EXTRA GRAPHICS CHOSEN BY USER
        when (outfitId) {
            "superhero" -> {
                // Red mask over eyes
                val maskY = eyeY - 24f
                drawRoundRect(
                    color = Color(0xFFD32F2F),
                    topLeft = Offset(cx - 95f, maskY),
                    size = Size(190f, 50f),
                    cornerRadius = CornerRadius(24f, 24f)
                )
                // Re-draw pupils so superhero can see
                drawCircle(color = Color.White, radius = 15f, center = Offset(cx - 55f, eyeY))
                drawCircle(color = Color.White, radius = 15f, center = Offset(cx + 55f, eyeY))
                drawCircle(color = Color(0xFF212121), radius = 9f, center = Offset(cx - 55f, eyeY))
                drawCircle(color = Color(0xFF212121), radius = 9f, center = Offset(cx + 55f, eyeY))
            }
            "gentleman" -> {
                // Classy black top hat on head
                val hatBaseY = cy - 220f
                drawLine(Color(0xFF424242), Offset(cx - 120f, hatBaseY), Offset(cx + 120f, hatBaseY), 15f)
                drawRect(
                    color = Color(0xFF212121),
                    topLeft = Offset(cx - 65f, hatBaseY - 115f),
                    size = Size(130f, 115f)
                )
                // Red hat band ribbon
                drawRect(
                    color = Color(0xFFE53935),
                    topLeft = Offset(cx - 65f, hatBaseY - 18f),
                    size = Size(130f, 18f)
                )

                // Classic bow tie on Ben's chest neck
                val bowY = cy + 42f
                leftBowPath.reset()
                leftBowPath.moveTo(cx, bowY)
                leftBowPath.lineTo(cx - 45f, bowY - 20f)
                leftBowPath.lineTo(cx - 45f, bowY + 20f)
                leftBowPath.close()

                rightBowPath.reset()
                rightBowPath.moveTo(cx, bowY)
                rightBowPath.lineTo(cx + 45f, bowY - 20f)
                rightBowPath.lineTo(cx + 45f, bowY + 20f)
                rightBowPath.close()

                drawPath(leftBowPath, Color(0xFF212121))
                drawPath(rightBowPath, Color(0xFF212121))
                drawCircle(color = Color(0xFFD32F2F), radius = 12f, center = Offset(cx, bowY)) // Red node center
            }
            "wizard" -> {
                // Points tall starry hat
                val hatY = cy - 220f
                wizardHatPath.reset()
                wizardHatPath.moveTo(cx - 110f, hatY)
                wizardHatPath.lineTo(cx + 110f, hatY)
                wizardHatPath.lineTo(cx, hatY - 190f)
                wizardHatPath.close()
                drawPath(wizardHatPath, Color(0xFF5E35B1)) // Deep Starry Purple Hat
                
                // Gold Stars representation
                drawCircle(color = Color(0xFFFFD54F), radius = 7f, center = Offset(cx - 25f, hatY - 50f))
                drawCircle(color = Color(0xFFFFD54F), radius = 6f, center = Offset(cx + 25f, hatY - 75f))
                drawCircle(color = Color(0xFFFFD54F), radius = 8f, center = Offset(cx, hatY - 120f))
            }
            "cyber" -> {
                // Neon blue cyber holographic visor
                val visorY = eyeY - 26f
                drawRoundRect(
                    color = Color(0xAA00E5FF),
                    topLeft = Offset(cx - 100f, visorY),
                    size = Size(200f, 52f),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                // Visor grid horizontal lasers
                drawLine(Color(0xFFE040FB), Offset(cx - 95f, eyeY), Offset(cx + 95f, eyeY), 3f)
                drawLine(Color(0xFFE040FB), Offset(cx - 95f, eyeY - 10f), Offset(cx + 95f, eyeY - 10f), 2f)
            }
        }
    }
}

@Composable
fun FamilyCard(
    member: com.example.viewmodel.FamilyMember,
    unlocked: Boolean,
    currentCoins: Int,
    onTalk: () -> Unit,
    onGift: () -> Unit,
    onInjectXp: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(185.dp)
            .border(
                2.dp,
                if (unlocked) parseHexColor(member.color) else Color.Gray.copy(alpha = 0.5f),
                RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        if (unlocked) parseHexColor(member.color).copy(alpha = 0.15f) else Color.LightGray.copy(alpha = 0.2f),
                        CircleShape
                    )
            ) {
                Text(
                    text = if (unlocked) member.emoji else "🔒",
                    fontSize = 32.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(member.name, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(member.relation, fontWeight = FontWeight.Light, fontSize = 10.sp, color = Color.Gray)
            
            Spacer(modifier = Modifier.height(6.dp))
            if (unlocked) {
                Text(
                    text = member.desc,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.height(32.dp),
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onTalk,
                        modifier = Modifier.weight(1f).height(34.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E0E0), contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        Text("🗣️ Talk", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onGift,
                        modifier = Modifier.weight(1f).height(34.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(2.dp),
                        enabled = currentCoins >= member.giftCost
                    ) {
                        Text("🎁 🪙${member.giftCost}", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    text = "Arrives at Level ${member.introLevel}!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.Red.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().height(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onInjectXp,
                    modifier = Modifier.fillMaxWidth().height(34.dp).testTag("qa_unlock_button_${member.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    Text("🧪 QA Unlock (+50 XP)", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

