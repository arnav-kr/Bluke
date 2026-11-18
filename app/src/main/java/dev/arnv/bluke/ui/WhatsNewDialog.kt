package dev.arnv.bluke.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.update.ChangelogRelease

/**
 * Shown once after an update with the changelog entries the user hasn't seen yet,
 * or from About → Changelog with the whole history ([title] differs).
 */
@Composable
fun WhatsNewDialog(
    releases: List<ChangelogRelease>,
    onDismiss: () -> Unit,
    title: String = "What's new",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.NewReleases, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                releases.forEach { ChangelogReleaseBlock(it) }
                if (releases.isEmpty()) Text("No changelog is included in this build.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}

@Composable
private fun ChangelogReleaseBlock(release: ChangelogRelease) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = listOfNotNull("Version ${release.version}", release.date).joinToString(" · "),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        release.groups.forEach { group ->
            group.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            group.items.forEach { item ->
                Row {
                    Text("•  ", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = inlineMarkdown(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Handles the bits the changelog uses: **bold**, `code` and [links](url) (shown as their text). */
internal fun inlineMarkdown(source: String): AnnotatedString {
    val text = source.replace(Regex("""\[([^]]*)]\([^)]*\)"""), "$1")
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end < 0) { append(text.substring(i)); break }
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) { append(text.substring(i)); break }
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
