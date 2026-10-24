package tv.own.owntv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Lightweight indeterminate spinner drawn with Canvas (no Material dependency). */
@Composable
fun OwnTVSpinner(
    modifier: Modifier = Modifier,
    sizeDp: Int = 44,
    color: Color = tv.own.owntv.ui.theme.stageAccent.accent,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spinnerAngle",
    )
    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val d = size.minDimension
        val stroke = d * 0.11f
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(d - stroke, d - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

