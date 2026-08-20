package com.dhruw.autoflow.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dhruw.autoflow.automation.engine.AutomationHealth
import com.dhruw.autoflow.automation.engine.HealthStatus

/**
 * The one-line status strip on an automation card. Colours follow the theme's
 * semantic roles rather than raw values so both themes stay legible, and the
 * text always says what is actually wrong — never a score.
 */
@Composable
fun HealthBadge(
    health: AutomationHealth,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val container = when (health.status) {
        HealthStatus.HEALTHY -> MaterialTheme.colorScheme.secondaryContainer
        HealthStatus.NEEDS_PERMISSION -> MaterialTheme.colorScheme.tertiaryContainer
        HealthStatus.CONFIGURATION_ISSUE, HealthStatus.FAILING ->
            MaterialTheme.colorScheme.errorContainer
        HealthStatus.DISABLED -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when (health.status) {
        HealthStatus.HEALTHY -> MaterialTheme.colorScheme.onSecondaryContainer
        HealthStatus.NEEDS_PERMISSION -> MaterialTheme.colorScheme.onTertiaryContainer
        HealthStatus.CONFIGURATION_ISSUE, HealthStatus.FAILING ->
            MaterialTheme.colorScheme.onErrorContainer
        HealthStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = health.status.icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = health.detail.ifBlank { health.status.label },
                style = MaterialTheme.typography.bodySmall,
                color = content,
                modifier = Modifier.weight(1f)
            )
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

val HealthStatus.label: String
    get() = when (this) {
        HealthStatus.HEALTHY -> "Healthy"
        HealthStatus.NEEDS_PERMISSION -> "Needs permission"
        HealthStatus.CONFIGURATION_ISSUE -> "Configuration issue"
        HealthStatus.FAILING -> "Frequently failing"
        HealthStatus.DISABLED -> "Disabled"
    }

val HealthStatus.icon: ImageVector
    get() = when (this) {
        HealthStatus.HEALTHY -> Icons.Outlined.CheckCircle
        HealthStatus.NEEDS_PERMISSION -> Icons.Outlined.Lock
        HealthStatus.CONFIGURATION_ISSUE -> Icons.Outlined.ReportProblem
        HealthStatus.FAILING -> Icons.Outlined.ErrorOutline
        HealthStatus.DISABLED -> Icons.Outlined.PauseCircle
    }
