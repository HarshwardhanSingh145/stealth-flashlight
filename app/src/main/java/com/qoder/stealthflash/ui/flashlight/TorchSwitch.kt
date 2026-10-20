package com.qoder.stealthflash.ui.flashlight

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.qoder.stealthflash.gesture.HoldGestureTracker
import com.qoder.stealthflash.gesture.HoldOutcome
import com.qoder.stealthflash.ui.theme.NeonBloom
import com.qoder.stealthflash.ui.theme.NeonCore
import com.qoder.stealthflash.ui.theme.NeonMid
import com.qoder.stealthflash.ui.theme.NeonOuter
import com.qoder.stealthflash.ui.theme.OffBackground
import com.qoder.stealthflash.ui.theme.OffCaption
import com.qoder.stealthflash.ui.theme.OffDotIdle
import com.qoder.stealthflash.ui.theme.OffDotLit
import com.qoder.stealthflash.ui.theme.OffHighlight
import com.qoder.stealthflash.ui.theme.OffKnob
import com.qoder.stealthflash.ui.theme.OffKnobRim
import com.qoder.stealthflash.ui.theme.OffLabelActive
import com.qoder.stealthflash.ui.theme.OffLabelInactive
import com.qoder.stealthflash.ui.theme.OffShadow
import com.qoder.stealthflash.ui.theme.OffTrack
import com.qoder.stealthflash.ui.theme.OnBackground
import com.qoder.stealthflash.ui.theme.OnBloom
import com.qoder.stealthflash.ui.theme.OnCaption
import com.qoder.stealthflash.ui.theme.OnDotIdle
import com.qoder.stealthflash.ui.theme.OnDotLit
import com.qoder.stealthflash.ui.theme.OnKnob
import com.qoder.stealthflash.ui.theme.OnLabelActive
import com.qoder.stealthflash.ui.theme.OnLabelInactive
import com.qoder.stealthflash.ui.theme.OnTrack
import com.qoder.stealthflash.ui.theme.TorchType
import kotlin.math.max
import kotlin.math.roundToInt
// Fractions measured off the reference frame. See reference/DESIGN_SPEC.md.
private const val PILL_HEIGHT_FRACTION = 0.74f
private const val PILL_WIDTH_FRACTION = 0.468f
private const val PILL_ASPECT = 0.3874f
private const val DOT_COLUMN_FRACTION = 0.736f
private const val DOT_GAP_FRACTION = 0.09f
private const val DOT_RADIUS_OVER_PILL = 0.0208f
private const val LABEL_SIZE_OVER_PILL = 0.24f
private const val CAPTION_SIZE_OVER_PILL = 0.115f
private const val PILL_CENTER_Y_FRACTION = 0.508f

/**
 * The torch switch, drawn to the geometry measured out of the reference clip: a vertical
 * capsule 46.8 % of screen width, aspect 0.387, with an 18-dot meter in the right gutter.
 *
 * The knob is the half-capsule that slides. It rests at the bottom when the torch is off and
 * rises to the top when it is on, and it always carries the label of the half it occupies, so
 * the word under your thumb reads as the action you are about to take.
 */
@Composable
fun TorchSwitch(
    isOn: Boolean,
    enabled: Boolean,
    tracker: HoldGestureTracker,
    onOutcome: (HoldOutcome) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val currentOutcome by rememberUpdatedState(onOutcome)
    val currentTracker by rememberUpdatedState(tracker)

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val layout = remember(canvasSize, tracker.dotCount) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            TorchLayout.of(canvasSize.width.toFloat(), canvasSize.height.toFloat(), tracker.dotCount)
        } else {
            null
        }
    }
    val layoutState by rememberUpdatedState(layout)

    val knobT by animateFloatAsState(
        targetValue = if (isOn) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "knob",
    )
    val glow by animateFloatAsState(
        targetValue = if (isOn && enabled) 1f else 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "glow",
    )

    // Written by the frame ticker, read by the draw pass. A stationary finger emits no pointer
    // events, so the meter can only advance on the clock.
    val meter = remember { HoldMeter() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val slopPx = 28.dp.toPx()
                runHoldGesture(
                    tracker = { currentTracker },
                    layout = { layoutState },
                    slopPx = slopPx,
                    meter = meter,
                    onCompletedWithHaptic = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onOutcome = { currentOutcome(it) },
                )
            },
    ) {
        val geometry = layout ?: return@Canvas
        meter.sample().let { (progress, lit) ->
            drawTorchScene(
                geometry = geometry,
                textMeasurer = textMeasurer,
                density = density,
                glow = glow,
                knobT = knobT,
                litDots = lit,
                progress = progress,
                enabled = enabled,
            )
        }
    }
}

private class HoldMeter {
    @Volatile
    var progress: Float = 0f

    @Volatile
    var litDots: Int = 0

    fun set(progress: Float, litDots: Int) {
        this.progress = progress
        this.litDots = litDots
    }

    fun reset() = set(0f, 0)

    fun sample(): Pair<Float, Int> = progress to litDots
}

/**
 * Touch handling for the switch.
 *
 * Leaving the pill's slop-expanded bounds interrupts the hold instead of restarting it, so an
 * interrupted touch can never accumulate five seconds. A release inside the slop and inside
 * the tap window toggles the torch; a release after that but before the threshold does nothing,
 * which is what stops a failed unlock attempt from also switching the light on.
 */
private suspend fun PointerInputScope.runHoldGesture(
    tracker: () -> HoldGestureTracker,
    layout: () -> TorchLayout?,
    slopPx: Float,
    meter: HoldMeter,
    onCompletedWithHaptic: () -> Unit,
    onOutcome: (HoldOutcome) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val active = tracker()
        val geometry = layout() ?: return@awaitEachGesture

        if (!geometry.holdBounds(slopPx).contains(down.position)) return@awaitEachGesture

        active.onPointerDown(down.uptimeMillis)
        val origin = down.position
        val moveTolerance = max(slopPx, geometry.pillWidth * 0.35f)

        var finished = HoldOutcome.None
        var slopExceeded = false

        while (true) {
            // Poll the timer on the frame clock, then take any pointer events that arrived.
            withFrameMillis { now ->
                val outcome = active.onTick(now)
                meter.set(active.progress, active.litDots)
                if (outcome != HoldOutcome.None) {
                    if (outcome == HoldOutcome.HoldCompleted) onCompletedWithHaptic()
                    onOutcome(outcome)
                }
            }
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                active.onInterrupted()
                meter.reset()
                break
            }
            change.consume()
            if (!change.pressed) {
                finished = if (slopExceeded) {
                    active.onInterrupted()
                    HoldOutcome.None
                } else {
                    active.onPointerUp(change.uptimeMillis).also { meter.reset() }
                }
                break
            }
            if (!slopExceeded && (change.position - origin).getDistance() > moveTolerance) {
                slopExceeded = true
                active.onInterrupted()
                meter.reset()
            }
        }
        if (finished != HoldOutcome.None) onOutcome(finished)
    }
}

private fun DrawScope.drawTorchScene(
    geometry: TorchLayout,
    textMeasurer: TextMeasurer,
    density: Density,
    glow: Float,
    knobT: Float,
    litDots: Int,
    progress: Float,
    enabled: Boolean,
) {
    drawRect(lerp(OffBackground, OnBackground, glow))
    if (glow > 0.001f) {
        drawRadialGlow(
            center = Offset(geometry.pillCenterX, geometry.pillCenterY),
            radius = geometry.pillHeight * 0.85f,
            color = OnBloom,
            alpha = glow * 0.9f,
        )
    }
    drawNeonBloom(glow, geometry)
    drawPillTrack(glow, geometry, enabled)
    drawKnob(glow, knobT, geometry, enabled)
    drawStateLabels(glow, geometry, textMeasurer, density, enabled)
    drawDotMeter(glow, geometry, textMeasurer, density, litDots, progress, enabled)
}

private fun DrawScope.drawRadialGlow(center: Offset, radius: Float, color: Color, alpha: Float) {
    val steps = 14
    for (i in steps downTo 1) {
        val t = i.toFloat() / steps
        drawCircle(
            color = color.copy(alpha = alpha * (1f - t) * 0.35f),
            radius = radius * t,
            center = center,
        )
    }
}

/** Concentric strokes stand in for a blur, which RenderEffect only offers from API 31. */
private fun DrawScope.drawNeonBloom(glow: Float, geometry: TorchLayout) {
    if (glow <= 0.001f) return
    val rect = geometry.pillRect
    val passes = listOf(
        26f to NeonBloom.copy(alpha = 0.10f * glow),
        18f to NeonOuter.copy(alpha = 0.18f * glow),
        12f to NeonMid.copy(alpha = 0.30f * glow),
        7f to NeonMid.copy(alpha = 0.55f * glow),
        3.5f to NeonCore.copy(alpha = 0.95f * glow),
    )
    passes.forEach { (width, color) ->
        drawRoundRect(
            color = color,
            topLeft = rect.topLeft - Offset(width / 2f, width / 2f),
            size = Size(rect.width + width, rect.height + width),
            cornerRadius = CornerRadius((rect.width + width) / 2f, (rect.width + width) / 2f),
            style = Stroke(width = width),
        )
    }
}

private fun DrawScope.drawPillTrack(glow: Float, geometry: TorchLayout, enabled: Boolean) {
    val rect = geometry.pillRect
    if (glow < 0.999f) {
        drawRoundRect(
            color = OffShadow.copy(alpha = (1f - glow) * 0.55f),
            topLeft = Offset(rect.left + geometry.pillWidth * 0.06f, rect.top + 10f),
            size = Size(rect.width * 0.94f, rect.height * 1.01f),
            cornerRadius = CornerRadius(rect.width / 2f, rect.width / 2f),
        )
    }
    val trackColor = if (enabled) lerp(OffTrack, OnTrack, glow) else OffTrack.copy(alpha = 0.55f)
    drawRoundRect(
        color = trackColor,
        topLeft = rect.topLeft,
        size = Size(rect.width, rect.height),
        cornerRadius = CornerRadius(rect.width / 2f, rect.width / 2f),
        style = Fill,
    )
}

private fun DrawScope.drawKnob(glow: Float, knobT: Float, geometry: TorchLayout, enabled: Boolean) {
    val rect = geometry.pillRect
    val half = geometry.pillHeight / 2f
    val top = rect.top + knobT * half
    val knob = Path().apply {
        addRoundRect(
            RoundRect(
                topLeft = Offset(rect.left, top),
                size = Size(rect.width, half),
                cornerRadius = CornerRadius(rect.width / 2f, rect.width / 2f),
            ),
        )
    }
    clipPath(Path().apply { addRoundRect(geometry.pillRoundRect) }) {
        drawPath(
            path = knob,
            color = if (enabled) lerp(OffKnob, OnKnob, glow) else OffKnob.copy(alpha = 0.6f),
            style = Fill,
        )
        drawPath(
            path = knob,
            color = lerp(OffKnobRim, NeonCore, glow)
                .copy(alpha = if (enabled) 0.85f else 0.25f),
            style = Stroke(width = geometry.dotRadius * 0.5f),
        )
        drawLine(
            color = lerp(OffHighlight, NeonOuter, glow),
            start = Offset(rect.left + geometry.pillWidth * 0.18f, top + 1.5f),
            end = Offset(rect.right - geometry.pillWidth * 0.18f, top + 1.5f),
            strokeWidth = 3f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawStateLabels(
    glow: Float,
    geometry: TorchLayout,
    measurer: TextMeasurer,
    density: Density,
    enabled: Boolean,
) {
    val rect = geometry.pillRect
    val half = geometry.pillHeight / 2f
    val style = TextStyle(
        fontFamily = TorchType.family,
        fontWeight = TorchType.stateWeight,
        letterSpacing = TorchType.stateTracking,
        fontSize = geometry.labelSizePx.toSp(density),
    )
    val dim = if (enabled) 1f else 0.5f
    drawCenteredText(measurer, "OFF", style, rect.center.x, rect.top + half / 2f, density) {
        lerp(OffLabelActive, OnLabelInactive, glow).copy(alpha = dim)
    }
    drawCenteredText(measurer, "ON", style, rect.center.x, rect.bottom - half / 2f, density) {
        lerp(OffLabelInactive, OnLabelActive, glow).copy(alpha = dim)
    }
}

private fun DrawScope.drawDotMeter(
    glow: Float,
    geometry: TorchLayout,
    measurer: TextMeasurer,
    density: Density,
    litDots: Int,
    progress: Float,
    enabled: Boolean,
) {
    val idleColor = lerp(OffDotIdle, OnDotIdle, glow)
    val litColor = lerp(OffDotLit, OnDotLit, glow)
    val firstLitIndex = geometry.dotCount - litDots
    for (i in 0 until geometry.dotCount) {
        val isLit = enabled && i >= firstLitIndex
        drawCircle(
            color = if (isLit) litColor else idleColor,
            radius = if (isLit) geometry.dotRadius * 1.12f else geometry.dotRadius,
            center = Offset(geometry.dotCenterX, geometry.dotCenterY(i)),
        )
    }

    val captionStyle = TextStyle(
        fontFamily = TorchType.family,
        fontWeight = TorchType.captionWeight,
        letterSpacing = TorchType.captionTracking,
        fontSize = geometry.captionSizePx.toSp(density),
    )
    val captionColor = lerp(OffCaption, OnCaption, glow)
    drawCenteredText(
        measurer, "HOLD", captionStyle,
        geometry.dotCenterX, geometry.dotColumnTop - geometry.dotPitch,
        density,
    ) { captionColor }

    val countdown = if (progress > 0.001f) {
        ((1f - progress) * 5f).roundToInt().coerceAtLeast(1).toString() + "s"
    } else {
        "5s"
    }
    drawCenteredText(
        measurer, countdown, captionStyle,
        geometry.dotCenterX, geometry.dotColumnBottom + geometry.dotPitch,
        density,
    ) { if (progress > 0.001f) litColor else captionColor }
}

private fun DrawScope.drawCenteredText(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    centerX: Float,
    centerY: Float,
    density: Density,
    colorOf: () -> Color,
) {
    val measured = measurer.measure(text, style)
    drawText(
        textLayoutResult = measured,
        color = colorOf(),
        topLeft = Offset(
            centerX - measured.size.width / 2f,
            centerY - measured.size.height / 2f,
        ),
    )
}

private class TorchLayout(
    val pillWidth: Float,
    val pillHeight: Float,
    val pillCenterX: Float,
    val pillCenterY: Float,
    val dotCenterX: Float,
    val dotCount: Int,
    val dotRadius: Float,
    val dotPitch: Float,
    val dotColumnTop: Float,
    val dotColumnBottom: Float,
    val labelSizePx: Float,
    val captionSizePx: Float,
) {
    val pillRect: Rect
        get() = Rect(
            pillCenterX - pillWidth / 2f,
            pillCenterY - pillHeight / 2f,
            pillCenterX + pillWidth / 2f,
            pillCenterY + pillHeight / 2f,
        )

    val pillRoundRect: RoundRect
        get() = RoundRect(
            topLeft = Offset(pillCenterX - pillWidth / 2f, pillCenterY - pillHeight / 2f),
            size = Size(pillWidth, pillHeight),
            cornerRadius = CornerRadius(pillWidth / 2f, pillWidth / 2f),
        )

    fun dotCenterY(index: Int): Float = dotColumnTop + index * dotPitch

    /** The pill plus a margin; the dot column is deliberately outside the touch target. */
    fun holdBounds(slopPx: Float): Rect = pillRect.inflate(slopPx)

    companion object {
        fun of(widthPx: Float, heightPx: Float, dotCount: Int): TorchLayout {
            var pillHeight = heightPx * PILL_HEIGHT_FRACTION
            var pillWidth = pillHeight * PILL_ASPECT
            val widthBudget = widthPx * PILL_WIDTH_FRACTION
            if (pillWidth > widthBudget) {
                pillWidth = widthBudget
                pillHeight = pillWidth / PILL_ASPECT
            }
            val heightBudget = heightPx * 0.82f
            if (pillHeight > heightBudget) {
                pillHeight = heightBudget
                pillWidth = pillHeight * PILL_ASPECT
            }

            val dotRadius = pillWidth * DOT_RADIUS_OVER_PILL
            val dotGap = widthPx * DOT_GAP_FRACTION
            val groupWidth = pillWidth + dotGap + dotRadius
            val groupLeft = (widthPx - groupWidth) / 2f

            val pillCenterX = groupLeft + pillWidth / 2f
            val pillCenterY = heightPx * PILL_CENTER_Y_FRACTION
            val dotCenterX = groupLeft + pillWidth + dotGap

            val columnHeight = pillHeight * DOT_COLUMN_FRACTION
            val dotPitch = columnHeight / (dotCount - 1).coerceAtLeast(1)

            return TorchLayout(
                pillWidth = pillWidth,
                pillHeight = pillHeight,
                pillCenterX = pillCenterX,
                pillCenterY = pillCenterY,
                dotCenterX = dotCenterX,
                dotCount = dotCount,
                dotRadius = dotRadius,
                dotPitch = dotPitch,
                dotColumnTop = pillCenterY - columnHeight / 2f,
                dotColumnBottom = pillCenterY + columnHeight / 2f,
                labelSizePx = pillWidth * LABEL_SIZE_OVER_PILL,
                captionSizePx = pillWidth * CAPTION_SIZE_OVER_PILL,
            )
        }
    }
}

private fun Float.toSp(density: Density): TextUnit = with(density) { this@toSp.toSp() }
