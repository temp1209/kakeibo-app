package work.temp1209.kakeibo.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import work.temp1209.kakeibo.data.notifications.NotificationHistory

@Composable
fun NotificationEventBadge(
    eventType: String,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val label = NotificationHistory.eventTypeLabel(eventType)
    val (containerColor, contentColor) = notificationEventColors(eventType)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = modifier,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = if (small) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = contentColor,
        )
    }
}

@Composable
private fun notificationEventColors(eventType: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (eventType) {
        NotificationHistory.TYPE_DONE ->
            scheme.tertiaryContainer to scheme.onTertiaryContainer
        NotificationHistory.TYPE_NEEDS_REVIEW, NotificationHistory.TYPE_FAILED ->
            scheme.errorContainer to scheme.onErrorContainer
        else ->
            scheme.surfaceVariant to scheme.onSurfaceVariant
    }
}
