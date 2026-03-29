package com.cyzco.touchpad

import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.roundToInt

// --- GESTURE STATE ---
enum class GestureState { Idle, Down, Move, Scroll, MultiSwipe, LongPress }

// --- STATE MACHINE ---
class TouchpadGestureProcessor(
    private val viewModel: IServerConnection,
    private val scope: CoroutineScope
)
{
    // --- SETTINGS ---
    private val dragSensitivity = 12
    private val scrollSensitivity = 0.06f
    private val panThreshold = 10f * 10f // n*n pixels
    private val tapTimeoutMs = 150L
    private val longPressTimeoutMs = 300L
    private val swipeThresholdPixels = 10
    private val sensitivityMultiplier = 1.6f // 1.0 = 1:1 movement. 1.5 = Cursor moves 50% faster than finger.

    // --- ACCUMULATORS ---
    // These store the fractional "leftover" movement that hasn't been sent yet.
    private var residueX = 0f
    private var residueY = 0f

    // --- STATE ---
    private var state = GestureState.Idle
    private var maxPointerCount = 0
    private var totalPan = Offset.Zero
    private var isClickable = true
    private val velocityTracker = VelocityTracker()

    // --- TIMERS ---
    private var tapTimeoutJob: Job? = null
    private var longPressJob: Job? = null

    // --- TRACKING ---
    private var lastCentroid = Offset.Zero

    /**
     * This is the main entry point.
     * We will call this from our View's onTouchEvent.
     */
    fun onTouchEvent(event: MotionEvent): Boolean
    {
        val pointerCount = event.pointerCount
        val action = event.actionMasked

        // Calculate the "centroid" (average position) of *all* fingers for this event
        var sumX = 0f
        var sumY = 0f
        for (i in 0 until pointerCount)
        {
            sumX += event.getX(i)
            sumY += event.getY(i)
        }
        val centroid = if (pointerCount > 0) Offset(sumX / pointerCount, sumY / pointerCount) else Offset.Zero

        when (action)
        {
            MotionEvent.ACTION_DOWN ->
            {
                // --- GESTURE START ---
                tapTimeoutJob?.cancel()
                longPressJob?.cancel()

                state = GestureState.Down
                maxPointerCount = 1
                totalPan = Offset.Zero
                isClickable = true
                velocityTracker.resetTracking()

                residueX = 0f
                residueY = 0f

                val pos = Offset(event.getX(0), event.getY(0))
                lastCentroid = pos
                velocityTracker.addPosition(event.eventTime, pos)

                // --- START TIMERS ---
                tapTimeoutJob = scope.launch()
                {
                    delay(tapTimeoutMs)
                    isClickable = false
                }

                longPressJob = scope.launch()
                {
                    delay(longPressTimeoutMs)
                    if (state == GestureState.Down)
                    {
                        isClickable = false
                        state = GestureState.LongPress
                        viewModel.sendCommand("mouse_down")
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN ->
            {
                maxPointerCount = maxPointerCount.coerceAtLeast(pointerCount)
                if (maxPointerCount > 1)
                    longPressJob?.cancel()

                lastCentroid = centroid
            }

            MotionEvent.ACTION_MOVE ->
            {
                val pan = centroid - lastCentroid
                lastCentroid = centroid

                totalPan += pan
                velocityTracker.addPosition(event.eventTime, centroid)

                // --- STATE TRANSITION ---
                if (state != GestureState.LongPress)
                {
                    val hasMoved = totalPan.getDistanceSquared() > panThreshold
                    if (hasMoved)
                    {
                        if (isClickable)
                        {
                            isClickable = false
                            tapTimeoutJob?.cancel()
                            longPressJob?.cancel()
                        }

                        // to "Scroll" if you lift a finger mid-swipe.
                        when (pointerCount)
                        {
                            1 -> if (state == GestureState.Down) state = GestureState.Move
                            2 -> if (state == GestureState.Down || state == GestureState.Move) state = GestureState.Scroll
                            else -> state = GestureState.MultiSwipe // Always upgrade to MultiSwipe
                        }
                    }
                }

                // --- SEND COMMANDS ---
                when (state)
                {
                    GestureState.Move, GestureState.LongPress ->
                    {
                        when (pointerCount)
                        {
                            1 ->
                            {
                                val dx = ((pan.x * dragSensitivity)/12).toInt()
                                val dy = ((pan.y * dragSensitivity)/12).toInt()
                                if (dx != 0 || dy != 0)
                                    viewModel.sendCommand("drag $dx,$dy")
                            }
                        }
                    }

                    // --- DOMINANT-AXIS SCROLL ---
                    GestureState.Scroll ->
                    {
                        if (pointerCount == 2)
                        {
                            // Check which axis has more movement
                            if (abs(pan.x) > abs(pan.y))
                            {
                                // Horizontal is dominant
                                val scrollX =  (pan.x * scrollSensitivity).roundToInt()
                                if (scrollX != 0) viewModel.sendCommand("scrollX $scrollX")
                            }

                            else
                            {
                                // Vertical is dominant
                                val scrollY = (pan.y * scrollSensitivity).roundToInt()
                                if (scrollY != 0) viewModel.sendCommand("scrollY $scrollY")
                            }
                        }
                    }
                    else -> {}
                }
            }

            MotionEvent.ACTION_POINTER_UP ->
            {
                // Recalculate centroid for the remaining fingers to prevent "teleport"
                val index = event.actionIndex
                var newSumX = 0f
                var newSumY = 0f
                val remainingPointers = pointerCount - 1

                if (remainingPointers > 0)
                {
                    for (i in 0 until pointerCount)
                    {
                        if (i != index)
                        {
                            newSumX += event.getX(i)
                            newSumY += event.getY(i)
                        }
                    }
                    lastCentroid = Offset(newSumX / remainingPointers, newSumY / remainingPointers)
                }
                else lastCentroid = Offset.Zero
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
            {
                // --- GESTURE END ---
                tapTimeoutJob?.cancel()
                longPressJob?.cancel()

                if (isClickable && state == GestureState.Down)
                {
                    when (maxPointerCount)
                    {
                        1 -> viewModel.sendCommand("left_click")
                        2 -> viewModel.sendCommand("right_click")
                        3 -> viewModel.sendCommand("ctrl_c")
                        4 -> viewModel.sendCommand("alt_tab")
                        5 -> viewModel.sendCommand("ctrl_t")
                    }
                }

                else if (state == GestureState.LongPress)
                {
                    when(maxPointerCount)
                    {
                        1 -> viewModel.sendCommand("mouse_up")
                        2 -> viewModel.sendCommand("ctrl_v")
                    }
                }

                else if (state == GestureState.MultiSwipe)
                {
                    // This block will now be correctly reached
                    val (velX, velY) = velocityTracker.calculateVelocity()
                    val horizontalSwipe = abs(totalPan.x) > swipeThresholdPixels && abs(velX) > abs(velY)
                    val verticalSwipe = abs(totalPan.y) > swipeThresholdPixels && abs(velY) > abs(velX)

                    when (maxPointerCount)
                    {
                        3 ->
                        {
                            if (horizontalSwipe)
                            {
                                if (totalPan.x > 0) viewModel.sendCommand("ctrl_tab") // Swipe Right
                                else viewModel.sendCommand("ctrl_shift_tab") // Swipe Left
                            }
                        }

                        4 ->
                        {
                            if (horizontalSwipe)
                            {
                                viewModel.sendCommand("alt_tab")
                            }
                        }

                        5 ->
                        {
                            if (horizontalSwipe)
                            {
                                viewModel.sendCommand("ctrl_w")
                            }
                        }
                    }
                }
                state = GestureState.Idle
                lastCentroid = Offset.Zero
            }
        }
        return true
    }
}