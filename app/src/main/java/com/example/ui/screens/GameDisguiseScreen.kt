package com.example.ui.screens

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class GameMode {
    VS_AI,
    TWO_PLAYERS
}

enum class PlayerSymbol {
    X, O
}

@Composable
fun GameDisguiseScreen(
    onUnlockApp: () -> Unit,
    configuredPin: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val haptic = LocalHapticFeedback.current

    // Game State
    var gameMode by remember { mutableStateOf(GameMode.VS_AI) }
    var currentTurn by remember { mutableStateOf(PlayerSymbol.X) }
    var isMuted by remember { mutableStateOf(false) }

    // Board: 9 cells, null = empty, "X" or "O"
    val board = remember { mutableStateListOf<String?>().apply { repeat(9) { add(null) } } }
    var winningIndices by remember { mutableStateOf<List<Int>?>(null) }
    var winner by remember { mutableStateOf<String?>(null) } // "X", "O", "DRAW", or null
    var isAiThinking by remember { mutableStateOf(false) }

    // Scoreboard
    var scoreX by remember { mutableIntStateOf(0) }
    var scoreO by remember { mutableIntStateOf(0) }
    var scoreDraws by remember { mutableIntStateOf(0) }

    // Secret Authorization Dialog State
    var showPinDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // Back handling: Minimize app to prevent exposing document scanner
    BackHandler {
        if (showPinDialog) {
            showPinDialog = false
        } else {
            activity?.moveTaskToBack(true)
        }
    }

    // Helper functions
    fun checkWinner(b: List<String?>): Pair<String?, List<Int>?> {
        val winCombos = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // Rows
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // Columns
            listOf(0, 4, 8), listOf(2, 4, 6)             // Diagonals
        )
        for (combo in winCombos) {
            val a = b[combo[0]]
            val c = b[combo[1]]
            val d = b[combo[2]]
            if (a != null && a == c && c == d) {
                return Pair(a, combo)
            }
        }
        if (b.all { it != null }) {
            return Pair("DRAW", null)
        }
        return Pair(null, null)
    }

    fun startNewRound() {
        for (i in 0 until 9) {
            board[i] = null
        }
        winner = null
        winningIndices = null
        currentTurn = PlayerSymbol.X
        isAiThinking = false
    }

    fun resetAllScores() {
        startNewRound()
        scoreX = 0
        scoreO = 0
        scoreDraws = 0
    }

    // Smart AI Move
    fun makeAiMove() {
        val emptyIndices = board.indices.filter { board[it] == null }
        if (emptyIndices.isEmpty() || winner != null) return

        // 1. Check if AI can win this move
        for (i in emptyIndices) {
            val simulated = board.toMutableList()
            simulated[i] = "O"
            if (checkWinner(simulated).first == "O") {
                board[i] = "O"
                return
            }
        }

        // 2. Check if player X could win next turn and block them
        for (i in emptyIndices) {
            val simulated = board.toMutableList()
            simulated[i] = "X"
            if (checkWinner(simulated).first == "X") {
                board[i] = "O"
                return
            }
        }

        // 3. Take center if available
        if (board[4] == null) {
            board[4] = "O"
            return
        }

        // 4. Take corners
        val corners = listOf(0, 2, 6, 8).filter { board[it] == null }
        if (corners.isNotEmpty()) {
            board[corners.random()] = "O"
            return
        }

        // 5. Take any empty spot
        board[emptyIndices.random()] = "O"
    }

    // AI Turn Trigger with slight human-like delay
    LaunchedEffect(currentTurn, gameMode, winner) {
        if (gameMode == GameMode.VS_AI && currentTurn == PlayerSymbol.O && winner == null) {
            isAiThinking = true
            delay(400)
            makeAiMove()
            isAiThinking = false

            // Check outcome after AI move
            val (win, combo) = checkWinner(board)
            if (win != null) {
                winner = win
                winningIndices = combo
                if (win == "O") scoreO++
                else if (win == "DRAW") scoreDraws++
                if (!isMuted) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } else {
                currentTurn = PlayerSymbol.X
            }
        }
    }

    // Player Cell Tap
    fun handleCellClick(index: Int) {
        if (board[index] != null || winner != null || isAiThinking) return

        if (!isMuted) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        board[index] = currentTurn.name
        val (win, combo) = checkWinner(board)
        if (win != null) {
            winner = win
            winningIndices = combo
            if (win == "X") scoreX++
            else if (win == "O") scoreO++
            else if (win == "DRAW") scoreDraws++
            if (!isMuted) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            currentTurn = if (currentTurn == PlayerSymbol.X) PlayerSymbol.O else PlayerSymbol.X
        }
    }

    // Secret icon click handler
    val handleSecretIconClick = {
        if (configuredPin.isNotBlank()) {
            enteredPin = ""
            pinError = false
            showPinDialog = true
        } else {
            // Instant 1-tap open!
            onUnlockApp()
        }
    }

    // Pulsing transition for winning line / active turn
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // MAIN CONTAINER (FULL SCREEN GAME DESIGN)
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("game_disguise_screen"),
        color = Color(0xFF0A0E1A) // Deep Arcade Obsidian
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. TOP GAME ARCADE HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Game Title & Edition
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "OX",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "OX DUEL PRO",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Championship Classic",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Controls & THE SECRET MINI ICON
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Audio SFX toggle
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = "Sound Toggle",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Reset Round
                    IconButton(
                        onClick = { startNewRound() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Board",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // ==========================================
                    // ⭐ THE DISCREET SECRET MINI ICON ⭐
                    // Looks like a subtle game rank badge / shield emblem
                    // Clicking it instantly unlocks Scannivo document app!
                    // ==========================================
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B).copy(alpha = 0.9f))
                            .border(1.dp, Color(0xFF334155), CircleShape)
                            .clickable(onClick = handleSecretIconClick)
                            .testTag("secret_app_icon"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "Game Badge",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 2. GAME MODE SWITCHER (VS AI vs 2 PLAYERS)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131B2E),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // VS AI Mode Button
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (gameMode != GameMode.VS_AI) {
                                    gameMode = GameMode.VS_AI
                                    resetAllScores()
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (gameMode == GameMode.VS_AI) Color(0xFF00E5FF).copy(alpha = 0.18f) else Color.Transparent,
                        border = if (gameMode == GameMode.VS_AI) BorderStroke(1.dp, Color(0xFF00E5FF)) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = if (gameMode == GameMode.VS_AI) Color(0xFF00E5FF) else Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "vs Computer AI",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (gameMode == GameMode.VS_AI) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                            )
                        }
                    }

                    // 2 Players Mode Button
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (gameMode != GameMode.TWO_PLAYERS) {
                                    gameMode = GameMode.TWO_PLAYERS
                                    resetAllScores()
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (gameMode == GameMode.TWO_PLAYERS) Color(0xFFFF2A85).copy(alpha = 0.18f) else Color.Transparent,
                        border = if (gameMode == GameMode.TWO_PLAYERS) BorderStroke(1.dp, Color(0xFFFF2A85)) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (gameMode == GameMode.TWO_PLAYERS) Color(0xFFFF2A85) else Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "2 Players (Pass)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (gameMode == GameMode.TWO_PLAYERS) Color(0xFFFF2A85) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            // 3. SCOREBOARD CARDS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Player X Score
                ScoreBadge(
                    label = "PLAYER X",
                    score = scoreX,
                    color = Color(0xFF00E5FF),
                    isActiveTurn = currentTurn == PlayerSymbol.X && winner == null,
                    modifier = Modifier.weight(1f)
                )

                // Draws Score
                ScoreBadge(
                    label = "TIES",
                    score = scoreDraws,
                    color = Color(0xFF94A3B8),
                    isActiveTurn = false,
                    modifier = Modifier.weight(0.8f)
                )

                // Player O / AI Score
                ScoreBadge(
                    label = if (gameMode == GameMode.VS_AI) "AI BOT (O)" else "PLAYER O",
                    score = scoreO,
                    color = Color(0xFFFF2A85),
                    isActiveTurn = currentTurn == PlayerSymbol.O && winner == null,
                    modifier = Modifier.weight(1f)
                )
            }

            // 4. TURN & STATUS BANNER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    winner != null -> {
                        val bannerText = when (winner) {
                            "X" -> "🏆 PLAYER X WON!"
                            "O" -> if (gameMode == GameMode.VS_AI) "🤖 AI BOT WON!" else "🏆 PLAYER O WON!"
                            else -> "🤝 IT'S A DRAW!"
                        }
                        val bannerColor = when (winner) {
                            "X" -> Color(0xFF00E5FF)
                            "O" -> Color(0xFFFF2A85)
                            else -> Color(0xFFFFB800)
                        }
                        Text(
                            text = bannerText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = bannerColor,
                            modifier = Modifier.scale(pulseScale)
                        )
                    }
                    isAiThinking -> {
                        Text(
                            text = "🤖 AI is thinking...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF2A85)
                        )
                    }
                    else -> {
                        val turnName = if (currentTurn == PlayerSymbol.X) "Player X's Turn" else if (gameMode == GameMode.VS_AI) "AI's Turn" else "Player O's Turn"
                        val turnColor = if (currentTurn == PlayerSymbol.X) Color(0xFF00E5FF) else Color(0xFFFF2A85)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(turnColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = turnName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = turnColor
                            )
                        }
                    }
                }
            }

            // 5. 3x3 INTERACTIVE TIC-TAC-TOE BOARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF111827))
                    .border(2.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (row in 0 until 3) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (col in 0 until 3) {
                                val index = row * 3 + col
                                val cellValue = board[index]
                                val isWinningCell = winningIndices?.contains(index) == true

                                val animatedBgColor by animateColorAsState(
                                    targetValue = when {
                                        isWinningCell && cellValue == "X" -> Color(0xFF00E5FF).copy(alpha = 0.35f)
                                        isWinningCell && cellValue == "O" -> Color(0xFFFF2A85).copy(alpha = 0.35f)
                                        cellValue != null -> Color(0xFF1E293B).copy(alpha = 0.7f)
                                        else -> Color(0xFF182234)
                                    },
                                    label = "cellBg"
                                )

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .clickable(
                                            enabled = cellValue == null && winner == null && !isAiThinking,
                                            onClick = { handleCellClick(index) }
                                        )
                                        .testTag("cell_$index"),
                                    shape = RoundedCornerShape(16.dp),
                                    color = animatedBgColor,
                                    border = BorderStroke(
                                        width = if (isWinningCell) 2.5.dp else 1.dp,
                                        color = if (isWinningCell) {
                                            if (cellValue == "X") Color(0xFF00E5FF) else Color(0xFFFF2A85)
                                        } else {
                                            Color(0xFF334155).copy(alpha = 0.5f)
                                        }
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (cellValue != null) {
                                            Text(
                                                text = cellValue,
                                                fontSize = 44.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (cellValue == "X") Color(0xFF00E5FF) else Color(0xFFFF2A85),
                                                modifier = Modifier.scale(if (isWinningCell) pulseScale else 1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. BOTTOM ACTION BUTTON (PLAY NEXT ROUND / RESTART)
            Button(
                onClick = { startNewRound() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("game_action_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (winner != null) Color(0xFF00E5FF) else Color(0xFF1E293B)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = if (winner != null) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (winner != null) "Play Next Round" else "Restart Board",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (winner != null) Color.Black else Color.White
                )
            }
        }

        // SECRET PIN UNLOCK DIALOG (IF MASTER PIN IS CONFIGURED)
        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Secret Access",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "Secret Authorization",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Enter master PIN to reveal Scannivo Document Scanner:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = enteredPin,
                            onValueChange = {
                                enteredPin = it.take(8)
                                pinError = false
                            },
                            label = { Text("4-Digit PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            isError = pinError,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (pinError) {
                            Text(
                                text = "Incorrect PIN. Try again.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (enteredPin == configuredPin) {
                                showPinDialog = false
                                onUnlockApp()
                            } else {
                                pinError = true
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Unlock App")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ScoreBadge(
    label: String,
    score: Int,
    color: Color,
    isActiveTurn: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isActiveTurn) color.copy(alpha = 0.15f) else Color(0xFF131B2E),
        border = BorderStroke(
            width = if (isActiveTurn) 1.5.dp else 1.dp,
            color = if (isActiveTurn) color else Color(0xFF1E293B)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isActiveTurn) color else Color(0xFF94A3B8),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = score.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
