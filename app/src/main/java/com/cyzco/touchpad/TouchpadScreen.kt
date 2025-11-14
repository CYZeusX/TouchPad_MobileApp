package com.cyzco.touchpad

import android.R
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun TouchpadScreen(viewModel: ServerConnection = viewModel())
{
    val serverIp by viewModel.serverIp.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    var pointerPositions by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val connectedColor = Color(0xFF388E3C) // Green
    val disconnectedColor = Color(0xFFD32F2F) // Red

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        // 1. Hide the system bars (status and navigation)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        // 2. Set behavior to show them temporarily with a swipe
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 3. Re-show the bars when the composable leaves the screen
        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- IP ADDRESS AND CONNECT BUTTON ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        )
        {
            Spacer(modifier = Modifier.height(60.dp))
            Spacer(modifier = Modifier.width(8.dp))

            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            Button(
                onClick = {
                    // 3. Get the activity from the context
                    val activity = context as? Activity ?: return@Button

                    // 4. Set the *opposite* orientation
                    activity.requestedOrientation =
                        if (isPortrait) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF646464))
            )
            {
                Text(if (isPortrait) "Landscape" else "Portrait", color = Color(0xFF000000))
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = serverIp,
                onValueChange = { viewModel.onIpChange(it) },
                placeholder = { Text("Server IP Address") },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(textAlign = TextAlign.Center, color = Color.White),
                singleLine = true,
                enabled = !isConnected,
                shape = RoundedCornerShape(50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    // === ENABLED colors (when !isConnected, i.e., disconnected) ===
                    focusedBorderColor = disconnectedColor,
                    unfocusedBorderColor = disconnectedColor,
                    focusedLabelColor = disconnectedColor,
                    unfocusedLabelColor = disconnectedColor,
                    cursorColor = disconnectedColor,

                    // === DISABLED colors (when isConnected, i.e., connected) ===
                    disabledBorderColor = connectedColor,
                    disabledLabelColor = connectedColor
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (isConnected) viewModel.disconnect()
                    else viewModel.connect()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) Color(0xFFD32F2F) else Color(0xFF388E3C)
                )
            )
            {
                Text(if (isConnected) "Disconnect" else "Connect", color = Color(0xFF000000))
            }

            Spacer(modifier = Modifier.width(8.dp))
        }

        // --- TOUCH AREA CONTAINER ---
        Box(modifier = Modifier.fillMaxSize())
        {
            // LAYER 1: RGB DOTS BACKGROUND
            // pass the pointer positions down to the background
            AnimatedDotsBackground(
                modifier = Modifier.fillMaxSize(),
                pointerPositions = pointerPositions // Pass the state down
            )

            // LAYER 2: GESTURES (Only active when connected)
            if (isConnected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .pointerInput(Unit) {
                            coroutineScope {
                                // 0. Animations
                                launch {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            pointerPositions = event.changes
                                                .filter { it.pressed }
                                                .map { it.position }
                                        }
                                    }
                                }

                                // 1. Taps
                                launch {
                                    detectTapGestures(
                                        onTap = {
                                            val cmd = "left_click"
                                            viewModel.sendCommand(cmd)
                                        },
                                        onLongPress = {
                                            val cmd = "right_click"
                                            viewModel.sendCommand(cmd)
                                        }
                                    )
                                }

                                // 2. Scroll
                                launch {
                                    detectTransformGestures(
                                        onGesture = { _, pan, _, _ ->
                                            val scrollAmount = (pan.y / 12).roundToInt()
                                            if (scrollAmount != 0) {
                                                val cmd = "scroll $scrollAmount"
                                                viewModel.sendCommand(cmd)
                                            }
                                        }
                                    )
                                }

                                // 3. 1-Finger Drag
                                launch {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val drag = awaitTouchSlopOrCancellation(
                                                down.id,
                                                onTouchSlopReached = { change, _ ->
                                                    if (currentEvent.changes.size == 1) {
                                                        change.consume()
                                                    }
                                                }
                                            )
                                            if (drag != null && currentEvent.changes.size == 1) {
                                                val initialDrag =
                                                    drag.position - drag.previousPosition
                                                val dx = initialDrag.x.roundToInt()
                                                val dy = initialDrag.y.roundToInt()
                                                if (dx != 0 || dy != 0) {
                                                    val cmd = "$dx,$dy"
                                                    viewModel.sendCommand(cmd)
                                                }
                                                drag(down.id) { change ->
                                                    if (currentEvent.changes.size == 1) {
                                                        change.consume()
                                                        val dragAmount =
                                                            change.position - change.previousPosition
                                                        val dxLoop = dragAmount.x.roundToInt()
                                                        val dyLoop = dragAmount.y.roundToInt()
                                                        if (dxLoop != 0 || dyLoop != 0) {
                                                            val cmd = "$dxLoop,$dyLoop"
                                                            viewModel.sendCommand(cmd)
                                                        }
                                                    } else {
                                                        change.consume()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                )
            }
        }
    }
}

// -----------------------------------------------------------------
// --- (AnimatedDotsBackground and other helper code is unchanged) ---
// -----------------------------------------------------------------

data class Dot(
    val originalOffset: Offset,
    val currentOffset: Animatable<Offset, *> = Animatable(originalOffset, Offset.VectorConverter)
)

fun lerp(start: Offset, stop: Offset, fraction: Float): Offset {
    return start + (stop - start) * fraction.coerceIn(0.05f, 0.5f)
}

@Composable
fun AnimatedDotsBackground(
    modifier: Modifier = Modifier,
    pointerPositions: List<Offset>
)
{
    val dots = remember { mutableStateListOf<Dot>() }
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    // --- Define Repulsion range ---
    val repulsionDistance = 150f
    val repulsionDistanceSq = repulsionDistance * repulsionDistance

    // This allows the loop to get the *current* finger positions
    val rememberedPointers = remember { mutableStateOf(pointerPositions) }
    rememberedPointers.value = pointerPositions

    // --- ANIMATION EFFECT ---
    LaunchedEffect(dots)
    {
        while (true) {
            // Get the latest finger positions
            val currentPointers = rememberedPointers.value

            coroutineScope.launch()
            {
                dots.forEach()
                { dot ->
                    var targetOffset = dot.originalOffset

                    // Find distance to closest pointer
                    val closestPointerSqDist = currentPointers
                        .map { pointer -> (pointer - dot.currentOffset.value).getDistanceSquared() }
                        .minByOrNull { it }

                    // If a pointer is too close...
                    if (closestPointerSqDist != null && closestPointerSqDist < repulsionDistanceSq) {

                        // Find the actual pointer
                        val closestPointer = currentPointers.minByOrNull { pointer ->
                            (pointer - dot.currentOffset.value).getDistanceSquared() }!!

                        // Calculate the "push"
                        val distance = sqrt(closestPointerSqDist)
                        val repelVector = dot.currentOffset.value - closestPointer
                        val repelDirection = repelVector / distance

                        // change the target to the repelled "safe" spot
                        targetOffset = closestPointer + (repelDirection * repulsionDistance)
                    }

                    // --- SIMPLE LERP ANIMATION ---
                    // Calculate the new position, 10% of the way to the target
                    val newOffset = lerp(dot.currentOffset.value, targetOffset, 0.15f)

                    // Immediately "snap" the dot to that new position
                    dot.currentOffset.snapTo(newOffset)
                }
            }
            // Run this loop ~60 times per second for a smooth animation
            delay(16)
        }
    }
    val baseColors = listOf(
        Color.Magenta,
        Color(0xFF5A00FF), // Violet
        Color.Cyan,
        Color.Green,
        Color.Yellow,
        Color(0xFFFF0008)
    )

    val seamlessColors = baseColors + baseColors.first()
    val colorStops = seamlessColors.mapIndexed { index, color ->
        (index.toFloat() / (seamlessColors.size - 1)) to color
    }.toTypedArray()

    val infiniteTransition = rememberInfiniteTransition("gradient_transition")
    val animatedFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient_animation"
    )

    var previousSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var previousIsPortrait by remember { mutableStateOf(isPortrait) }

    Canvas(modifier = modifier)
    {
        // 'this' is a DrawScope. get the *current* size here.
        val (currentCanvasWidth, currentCanvasHeight) = size

        // check if the keys have changed.
        if (size != previousSize || isPortrait != previousIsPortrait)
        {
            val numDotsX = if (isPortrait) 8 else 12
            val numDotsY = if (isPortrait) 12 else 8

            val spacingX = currentCanvasWidth / (numDotsX + 1)
            val spacingY = currentCanvasHeight / (numDotsY + 1)

            val newDots = buildList {
                for (i in 1..numDotsX) {
                    for (j in 1..numDotsY) {
                        val originalOffset = Offset(i * spacingX, j * spacingY)
                        add(Dot(originalOffset))
                    }
                }
            }

            // Atomically update the single source of truth
            dots.clear()
            dots.addAll(newDots)

            // --- Update our 'remembered' keys for the next frame ---
            previousSize = size
            previousIsPortrait = isPortrait
        }

        // --- Dots spec. logic ---
        val (startOffset, endOffset) = if (isPortrait)
        {
            // --- PORTRAIT --- nimate vertically along the Y-axis
            val animatedOffsetValue = animatedFraction * currentCanvasHeight
            Pair(
                Offset(0f, animatedOffsetValue),
                Offset(0f, animatedOffsetValue + currentCanvasHeight)
            )
        }

        else
        {
            // --- LANDSCAPE --- Animate horizontally along the X-axis
            val animatedOffsetValue = animatedFraction * currentCanvasWidth
            Pair(
                Offset(animatedOffsetValue, 0f),
                Offset(animatedOffsetValue + currentCanvasWidth, 0f)
            )
        }

        val movingBrush = Brush.linearGradient(
            colorStops = colorStops,
            start = startOffset,
            end = endOffset,
            tileMode = TileMode.Repeated
        )

        dots.forEach { dot ->
            drawCircle(
                brush = movingBrush,
                radius = 15f,
                center = dot.currentOffset.value
            )
        }
    }
}
@Preview(showBackground = true)
@Composable
fun TouchpadScreenPreview() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        TouchpadScreen(viewModel = ServerConnection())
    }
}