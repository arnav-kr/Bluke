package dev.arnv.bluke

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.R
import dev.arnv.bluke.ui.SettingsGroup
import dev.arnv.bluke.ui.SettingsCardGroup
import dev.arnv.bluke.ui.SettingsItemData
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import dev.arnv.bluke.ui.theme.getCookieShape

class AboutActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val versionName = try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "1.0.1"
        } catch (e: Exception) {
            "1.0.1"
        }
        
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                fun openUrl(url: String) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                
                // Compute shapes separately to avoid sharing a stateful Shape instance across different sizes, which causes layout/shrinking bugs on activity resume.
                val logoShape = getCookieShape(7)
                val developerShape = getCookieShape(7)

                Scaffold(
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("About") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Surface(
                            shape = logoShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(100.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = "App Logo",
                                modifier = Modifier.fillMaxSize(),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        
                        Text(
                            text = getString(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Text(
                            text = "Version $versionName",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Staged chips below version number
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // GitHub Repository Link Chip
                            Surface(
                                onClick = { openUrl("https://github.com/arnav-kr/Bluke") },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_github),
                                        contentDescription = "GitHub Repository",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "GitHub",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Email Link Chip
                            Surface(
                                onClick = { openUrl("mailto:bluke@arnv.dev") },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = "Contact Email",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Email",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        // Developer Heading and Card content wrapped in SettingsGroup for visual consistency
                        SettingsGroup(title = "Developer") {
                            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                     Surface(
                                         shape = developerShape,
                                         color = MaterialTheme.colorScheme.primary,
                                         modifier = Modifier
                                             .padding(end = 16.dp)
                                             .size(44.dp)
                                     ) {
                                         Box(
                                             contentAlignment = Alignment.Center,
                                             modifier = Modifier.fillMaxSize()
                                         ) {
                                             Icon(
                                                 painter = painterResource(id = R.drawable.ic_wordmark),
                                                 contentDescription = "Wordmark logo",
                                                 tint = MaterialTheme.colorScheme.onPrimary,
                                                 modifier = Modifier.size(24.dp)
                                             )
                                         }
                                     }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Arnav Kumar",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "@arnav-kr",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PlayfulSocialButton(
                                        onClick = { openUrl("https://github.com/arnav-kr") },
                                        label = "GitHub",
                                        icon = {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_github),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    
                                    PlayfulSocialButton(
                                        onClick = { openUrl("https://buymeacoffee.com/arnavkr") },
                                        label = "Buy Me a Coffee",
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Coffee,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }

                        SettingsCardGroup(
                            title = "App",
                            items = listOf(
                                SettingsItemData(
                                    title = "Changelogs",
                                    subtitle = "History of all the changes made to the app",
                                    icon = { Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { openUrl("https://github.com/arnav-kr/Bluke/releases") }
                                ),
                                SettingsItemData(
                                    title = "Licenses",
                                    subtitle = "View the licenses that the app and libraries are using",
                                    icon = { Icon(Icons.Default.Policy, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { startActivity(android.content.Intent(this@AboutActivity, LicensesActivity::class.java)) }
                                ),
                                SettingsItemData(
                                    title = "Report issue",
                                    subtitle = "Report any issue or bug you have encountered while using the app",
                                    icon = { Icon(Icons.Default.BugReport, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { openUrl("https://github.com/arnav-kr/Bluke/issues/new?template=bug_report.md") }
                                ),
                                SettingsItemData(
                                    title = "Feature request",
                                    subtitle = "If you have any ideas or suggestions for the app, let us know",
                                    icon = { Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { openUrl("https://github.com/arnav-kr/Bluke/issues/new?template=feature_request.md") }
                                )
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PlayfulSocialButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
