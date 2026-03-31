package yangfentuozi.batteryrecorder.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.ui.model.PredictionConfidenceLevel

private val PredictionLowColor = Color(0xFFF44336)
private val PredictionMediumColor = Color(0xFFFF9800)
private val PredictionHighColor = Color(0xFF4CAF50)

@Composable
fun StatusIndicator(confidenceLevel: PredictionConfidenceLevel?) {
    val color = when (confidenceLevel) {
        PredictionConfidenceLevel.Low -> PredictionLowColor
        PredictionConfidenceLevel.Medium -> PredictionMediumColor
        PredictionConfidenceLevel.High -> PredictionHighColor
        null -> return
    }
    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        // 光晕层：径向渐变模拟发光
        Box(
            modifier = Modifier
                .size(20.dp)
                .drawBehind {
                    drawCircle(
                        color = color.copy(alpha = 0.3f),
                        radius = size.minDimension / 2f
                    )
                    drawCircle(
                        color = color.copy(alpha = 0.15f),
                        radius = size.minDimension / 1.5f
                    )
                }
        )
        // 实心圆点
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
    }
}
