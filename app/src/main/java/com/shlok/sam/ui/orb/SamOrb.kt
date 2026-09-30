package com.shlok.sam.ui.orb

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.shlok.sam.data.db.OrbSettingsEntity
import com.shlok.sam.domain.model.SamCoreState
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun SamOrb(
    state: SamCoreState,
    amplitude: Float,
    settings: OrbSettingsEntity,
    aura: Color,
    modifier: Modifier = Modifier
) {
    val speed = when (state) {
        SamCoreState.IDLE -> 8000
        SamCoreState.LISTENING -> 2800
        SamCoreState.THINKING -> 1400
        SamCoreState.EXECUTING -> 1100
        SamCoreState.SPEAKING -> 2000
        SamCoreState.SUCCESS -> 2400
        SamCoreState.ERROR -> 3200
    }
    val inf = rememberInfiniteTransition(label = "orb")
    val spin by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(speed, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )
    val breath by inf.animateFloat(
        0.92f, 1.08f,
        infiniteRepeatable(tween(if (state == SamCoreState.IDLE) 2400 else 900), RepeatMode.Reverse),
        label = "breath"
    )
    val pulse = when (state) {
        SamCoreState.LISTENING, SamCoreState.SPEAKING -> 1f + amplitude * 0.35f
        SamCoreState.SUCCESS -> 1.12f
        else -> breath
    }
    val ringColor = when (state) {
        SamCoreState.ERROR -> Color(0xFFFF8A8A)
        SamCoreState.SUCCESS -> Color(0xFF5CFFB0)
        else -> aura
    }
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f * 0.42f * settings.sizeSlider.coerceIn(0.25f, 1f) * pulse * 2.1f
        val c = center
        if (settings.glow) {
            drawCircle(
                brush = Brush.radialGradient(listOf(ringColor.copy(alpha = 0.35f), Color.Transparent), c, r * 1.85f),
                radius = r * 1.85f,
                center = c
            )
        }
        if (settings.rotatingRings) {
            rotate(spin, c) {
                drawCircle(Color.Transparent, r * 1.25f, c, style = Stroke(3f))
                drawArc(
                    color = ringColor.copy(alpha = 0.85f),
                    startAngle = 12f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 1.25f, c.y - r * 1.25f),
                    size = androidx.compose.ui.geometry.Size(r * 2.5f, r * 2.5f),
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }
            rotate(-spin * 1.3f, c) {
                drawArc(
                    color = Color(0xFF8B6CFF).copy(alpha = 0.7f),
                    startAngle = 200f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 1.45f, c.y - r * 1.45f),
                    size = androidx.compose.ui.geometry.Size(r * 2.9f, r * 2.9f),
                    style = Stroke(width = 2.4f, cap = StrokeCap.Round)
                )
            }
        }
        if (settings.auraBorder) {
            drawCircle(ringColor.copy(alpha = 0.45f), r * 1.08f, c, style = Stroke(2.2f))
        }
        val core = when (settings.orbType) {
            "MINIMAL" -> Brush.radialGradient(listOf(ringColor, Color(0xFF0A101C)), c, r)
            "NEON" -> Brush.radialGradient(listOf(Color.White, ringColor, Color(0xFF05070D)), c, r)
            "GALAXY" -> Brush.radialGradient(listOf(Color(0xFFE8D7FF), Color(0xFF4B7CFF), Color(0xFF12061C)), c, r)
            "ENERGY" -> Brush.radialGradient(listOf(Color.White, ringColor, Color(0xFF8B6CFF)), c, r)
            else -> Brush.radialGradient(listOf(Color(0xFFDFFFFA), ringColor, Color(0xFF081018)), c, r)
        }
        drawCircle(core, r, c)
        if (settings.particles) {
            for (i in 0 until 12) {
                val a = Math.toRadians((spin + i * 30).toDouble())
                val dist = r * 1.55f
                drawCircle(
                    ringColor.copy(alpha = 0.55f),
                    3.2f,
                    Offset(c.x + (cos(a) * dist).toFloat(), c.y + (sin(a) * dist).toFloat())
                )
            }
        }
        if (settings.voiceVisualizer && (state == SamCoreState.LISTENING || state == SamCoreState.SPEAKING)) {
            val bars = 16
            for (i in 0 until bars) {
                val ang = i / bars.toFloat() * 360f
                val h = 8f + amplitude * 28f * (0.4f + (i % 3) * 0.3f)
                rotate(ang, c) {
                    drawLine(
                        ringColor,
                        Offset(c.x, c.y - r - 6f),
                        Offset(c.x, c.y - r - 6f - h),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
fun MiniOrb(state: SamCoreState, amplitude: Float, modifier: Modifier = Modifier) {
    SamOrb(
        state = state,
        amplitude = amplitude,
        settings = OrbSettingsEntity(sizeSlider = 0.9f, particles = false, rotatingRings = true),
        aura = Color(0xFF2EE6FF),
        modifier = modifier
    )
}
