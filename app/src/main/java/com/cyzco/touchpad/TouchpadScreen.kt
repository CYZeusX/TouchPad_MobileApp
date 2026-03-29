package com.cyzco.touchpad

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.convertTo
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun TouchpadScreen(viewModel: IServerConnection = viewModel<ServerConnection>())
{
    // Connection
    val serverIp by viewModel.serverIp.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val connectedColor = Color(0xFF00FF0C)
    val disconnectedColor = Color(0xFFFF0000)

    // Setup
    var pointerPositions by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Create and remember our gesture processor
    val gestureProcessor = remember { TouchpadGestureProcessor(viewModel, coroutineScope) }

    DisposableEffect(Unit)
    {
        val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose()
        { insetsController.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Column(modifier = Modifier.fillMaxSize())
    {
        val canvasWidth = LocalConfiguration.current.screenWidthDp
        val surroundingSpace = 5.dp
        Spacer(modifier = Modifier.height(surroundingSpace))

        // --- TOUCHPAD AREA ---
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp))
        {
            // LAYER 1: RGB DOTS BACKGROUND
            // This now sits *under* the gesture view and just reads pointerPositions
            AnimatedDotsBackground(
                modifier = Modifier.fillMaxSize(),
                pointerPositions = pointerPositions,
                viewModel = viewModel,
            )

            // LAYER 2: GESTURES (PROGRAMMATIC)
            if (isConnected)
            {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        View(ctx).apply {
                            // Attach our programmatic listener
                            setOnTouchListener { _, event ->
                                val action = event.actionMasked

                                // --- 1. UPDATE ANIMATION ---
                                // --- BUG FIX: Check for gesture end to clear dots ---
                                pointerPositions =
                                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                                        emptyList() // Clear the dots

                                    else
                                    {
                                        // Update dots to follow fingers
                                        (0 until event.pointerCount).map { i ->
                                            Offset(event.getX(i), event.getY(i))
                                        }
                                    }

                                // --- PROCESS GESTURES ---
                                // Send the raw event to our processor for logic
                                gestureProcessor.onTouchEvent(event)
                            }
                        }
                    }
                )
            }
        }

        // --- BOTTOM BAR ---
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        )
        {
            val configuration = LocalConfiguration.current
            val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

            // element size variables
            val sideButtonWidth = (canvasWidth * if (isPortrait) 0.2f else 0.11f).dp
            val sideButtonHeight = (if (isPortrait) 50 else 40).dp
            val spaceBetweenButton = (canvasWidth * if (isPortrait) 0.03f else 0.03f).dp
            val ipAddressWidth = (canvasWidth * if (isPortrait) 0.45f else 0.3f).dp
            val ipAddressHeight = (if (isPortrait) 60 else 50).dp

            // element text settings
            val sideButtonFontSize = 15.sp
            val ipAddressFontSize = (if (isPortrait) 20 else 18).sp

            // element variables sync
            val sideButtonMod = Modifier.size(sideButtonWidth, sideButtonHeight).padding(0.dp)

            Button(
                modifier = sideButtonMod,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA0A0A0)),
                contentPadding = PaddingValues(0.dp),
                onClick =
                    {
                        val activity = context as? Activity ?: return@Button
                        activity.requestedOrientation =
                            if (isPortrait) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    },
            )
            {
                Text(if (isPortrait) "Landscape" else "Portrait",
                    color = Color(0xFF000000),
                    softWrap = false,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = sideButtonFontSize)
            }

            Spacer(modifier = Modifier.width(spaceBetweenButton))

            OutlinedTextField(
                value = serverIp,
                onValueChange = { viewModel.onIpChange(it) },
                modifier = Modifier.size(ipAddressWidth, ipAddressHeight),
                placeholder = { Text("Server IP", softWrap = false,maxLines = 1) },
                textStyle = TextStyle(
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    fontSize = ipAddressFontSize),
                singleLine = true,
                enabled = !isConnected,
                shape = RoundedCornerShape(50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = disconnectedColor,
                    unfocusedBorderColor = disconnectedColor,
                    focusedLabelColor = disconnectedColor,
                    unfocusedLabelColor = disconnectedColor,
                    cursorColor = disconnectedColor,
                    disabledBorderColor = connectedColor,
                    disabledLabelColor = connectedColor
                )
            )

            Spacer(modifier = Modifier.width(spaceBetweenButton))

            Button(
                modifier = sideButtonMod,
                colors = ButtonDefaults.buttonColors(containerColor =
                    if (isConnected) Color(0xFFFF6B6B)
                    else Color(0xFF82FF82)
                ),
                contentPadding = PaddingValues(0.dp),
                onClick =
                    {
                        if (isConnected) viewModel.disconnect()
                        else viewModel.connect()
                    },
            )
            {
                Text(if (isConnected) "Disconnect" else "Connect",
                    color = Color(
                        0xFF000000),
                    softWrap = false,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = sideButtonFontSize)
            }
        }
        Spacer(modifier = Modifier.height(surroundingSpace))
    }
}

// -----------------------------------------------------------------
//                  --- AnimatedDotsBackground ---
// -----------------------------------------------------------------

data class Dot(
    val originalOffset: Offset,
    val currentOffset: Animatable<Offset, *> = Animatable(originalOffset, Offset.VectorConverter)
)

fun lerp(start: Offset, stop: Offset, fraction: Float): Offset
{ return start + (stop - start) * fraction.coerceIn(0.05f, 0.5f) }

@Composable
fun AnimatedDotsBackground(
    modifier: Modifier = Modifier,
    pointerPositions: List<Offset>,
    viewModel: IServerConnection
)
{
    // dots config
    val dots = remember { mutableStateListOf<Dot>() }
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current

    // animation config
    val fps = 120
    val runningSpeed : Long = (1000 / fps).toLong()

    // --- State for dot repulsion logic ---
    val repulsionDistance = 150f
    val repulsionDistanceSq = repulsionDistance * repulsionDistance
    val rememberedPointers = remember { mutableStateOf(pointerPositions) }
    rememberedPointers.value = pointerPositions

    // --- State for canvas size, updated safely ---
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // --- State for dot generation ---
    var previousIsPortrait by remember { mutableStateOf(configuration.orientation == Configuration.ORIENTATION_PORTRAIT) }

    // --- ANIMATION EFFECT ---
    // This effect now correctly depends on `dots.size`
    LaunchedEffect(dots.size, canvasSize)
    {
        val (canvasWidth, canvasHeight) = canvasSize

        // Guard: Wait until the canvas has reported its size AND dots exist
        if (canvasWidth == 0f || canvasHeight == 0f || dots.isEmpty()) {
            return@LaunchedEffect
        }

        while (true) {
            val currentPointers = rememberedPointers.value
            coroutineScope.launch()
            {
                dots.forEach()
                { dot ->
                    var targetOffset = dot.originalOffset

                    val closestPointerSqDist = currentPointers
                        .map { pointer -> (pointer - dot.currentOffset.value).getDistanceSquared() }
                        .minByOrNull { it }

                    if (closestPointerSqDist != null && closestPointerSqDist < repulsionDistanceSq) {
                        val closestPointer = currentPointers.minByOrNull { pointer ->
                            (pointer - dot.currentOffset.value).getDistanceSquared()
                        }!!

                        val distance = sqrt(closestPointerSqDist)
                        val repelVector = dot.currentOffset.value - closestPointer
                        val repelDirection = if (distance > 0) repelVector / distance else Offset.Zero

                        // Calculate the target, which *can* be out of bounds
                        targetOffset = closestPointer + (repelDirection * repulsionDistance)
                    }

                    // Calculate the new interpolated offset
                    var newOffset = lerp(dot.currentOffset.value, targetOffset, 0.15f)

                    // This ensures the dot never snaps to a position outside the canvas.
                    newOffset = Offset(
                        x = newOffset.x.coerceIn(0f, canvasWidth),
                        y = newOffset.y.coerceIn(0f, canvasHeight)
                    )

                    dot.currentOffset.snapTo(newOffset)
                }
            }
            delay(runningSpeed)
        }
    }

    // --- DOT GENERATION EFFECT ---
    // This effect runs when size or orientation changes
    LaunchedEffect(canvasSize, configuration.orientation)
    {
        val (currentCanvasWidth, currentCanvasHeight) = canvasSize
        val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        // Guard: Don't generate dots until we have a size
        if (currentCanvasWidth == 0f || currentCanvasHeight == 0f) {
            return@LaunchedEffect
        }

        // Only regenerate if orientation changed or dots are empty
        if (isPortrait != previousIsPortrait || dots.isEmpty()) {
            val dotCountWidth = 6
            val dotNumberX: Int
            val dotNumberY: Int
            val canvasAspectRatio = currentCanvasWidth / currentCanvasHeight

            if (isPortrait) {
                dotNumberX = dotCountWidth
                dotNumberY = (dotCountWidth / canvasAspectRatio).roundToInt()
            } else {
                dotNumberY = dotCountWidth
                dotNumberX = (dotCountWidth * canvasAspectRatio).roundToInt()
            }

            val paddingRatio = 0.6f
            val spacingX = currentCanvasWidth / (dotNumberX - 1 + 2 * paddingRatio)
            val spacingY = currentCanvasHeight / (dotNumberY - 1 + 2 * paddingRatio)
            val paddingX = spacingX * paddingRatio
            val paddingY = spacingY * paddingRatio

            val newDots = buildList {
                for (i in 0 until dotNumberX) {
                    for (j in 0 until dotNumberY) {
                        val x = paddingX + (i * spacingX)
                        val y = paddingY + (j * spacingY)
                        add(Dot(Offset(x, y)))
                    }
                }
            }
            dots.clear()
            dots.addAll(newDots)
            previousIsPortrait = isPortrait
        }
    }


    // --- Color/Gradient logic (Unchanged) ---
    val baseColors = listOf(
        Color.Magenta,
        Color(0xFF5A00FF), // Violet
        Color.Cyan,
        Color.Green,
        Color.Yellow,
        Color(0xFFFF0008)
    )
    val seamlessColors = baseColors + baseColors.first()
    val colorStops = seamlessColors.mapIndexed()
    {
            index, color ->
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
    val isConnected by viewModel.isConnected.collectAsState()

    // --- CANVAS ---
    Canvas(
        modifier = modifier
            .border(
                2.dp,
                if (isConnected) Color(0xF09F9F9F) else Color(0xF0410003),
                shape = RoundedCornerShape(15.dp)
            )
            .background(
                Color(0xFF141414),
                shape = RoundedCornerShape(15.dp)
            )
            .onSizeChanged()
            {
                    newSize ->
                canvasSize = newSize.toSize()
            }
    )
    {
        // --- Drawing logic ---
        val (currentCanvasWidth, currentCanvasHeight) = canvasSize
        if (currentCanvasWidth == 0f) return@Canvas // Don't draw if size is unknown

        val (startOffset, endOffset) =
            if (previousIsPortrait) { // Use the state variable
                val animatedOffsetValue = animatedFraction * currentCanvasHeight
                Pair(
                    Offset(0f, animatedOffsetValue),
                    Offset(0f, animatedOffsetValue + currentCanvasHeight)
                )
            } else {
                val animatedOffsetValue = animatedFraction * currentCanvasWidth
                Pair(
                    Offset(animatedOffsetValue, 0f),
                    Offset(animatedOffsetValue + currentCanvasWidth, 0f)
                )
            }

        val rgb = Brush.linearGradient(
            colorStops = colorStops,
            start = startOffset,
            end = endOffset,
            tileMode = TileMode.Repeated
        )

        val darkGray = Color(0xFF3A3A3A)

        dots.forEach()
        { dot ->
            if (isConnected) {
                drawCircle(
                    brush = rgb,
                    center = dot.currentOffset.value,
                    radius = 6f
                )
            } else {
                drawCircle(
                    color = darkGray,
                    center = dot.currentOffset.value,
                    radius = 6f
                )
            }
        }
    }
}

// -----------------------------------------------------------------
//                       --- PREVIEWS ---
@Preview(name = "Landscape Preview", device = "spec:orientation=landscape,width=411dp,height=891dp")
@Composable
fun TouchpadScreenPreview_landscape()
{
    Surface(
        modifier = Modifier.fillMaxSize(),
        color =  Color.Black
    )
    { TouchpadScreen(viewModel = VirtualServerConnection()) }
}

@Preview(name = "Portrait Preview")
@Composable
fun TouchpadScreenPreview_portrait()
{
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    )
    { TouchpadScreen(viewModel = VirtualServerConnection()) }
}