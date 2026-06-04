package dev.arnv.bluke.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class SettingsItemData(
    val title: String,
    val subtitle: String? = null,
    val icon: @Composable (() -> Unit)? = null,
    val action: @Composable (() -> Unit)? = null,
    val onClick: (() -> Unit)? = null
)

@Composable
fun SettingsCardGroup(
    title: String? = null,
    items: List<SettingsItemData>
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 24.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { index, item ->
                val topRadius = if (index == 0) 28.dp else 4.dp
                val bottomRadius = if (index == items.size - 1) 28.dp else 4.dp
                Surface(
                    shape = RoundedCornerShape(
                        topStart = topRadius,
                        topEnd = topRadius,
                        bottomStart = bottomRadius,
                        bottomEnd = bottomRadius
                    ),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val modifier = if (item.onClick != null) {
                        Modifier.clickable { item.onClick.invoke() }
                    } else Modifier
                    
                    val paddingVertical = if (item.subtitle == null && item.icon == null) 12.dp else 20.dp
                    Row(
                        modifier = modifier.padding(horizontal = 24.dp, vertical = paddingVertical),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.icon != null) {
                            Box(
                                modifier = Modifier.padding(end = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                item.icon.invoke()
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            if (item.subtitle != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (item.action != null) {
                            Box(modifier = Modifier.padding(start = 16.dp)) {
                                item.action.invoke()
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp, top = 24.dp)
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                content = content
            )
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: @Composable() (() -> Unit)? = null,
    action: @Composable() (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.padding(end = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (action != null) {
            Box(modifier = Modifier.padding(start = 16.dp)) {
                action()
            }
        }
    }
}
