package dev.arnv.bluke

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.core.content.edit
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.arnv.bluke.ui.SettingsGroup
import dev.arnv.bluke.ui.SettingsCardGroup
import dev.arnv.bluke.ui.SettingsItemData
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import dev.arnv.bluke.ui.theme.getCookieShape

private const val ORIGINAL_REPO_URL = "https://github.com/arnav-kr/Bluke"
private const val ORIGINAL_RELEASES_URL = "https://github.com/arnav-kr/Bluke/releases/latest"
private const val ORIGINAL_DEVELOPER_URL = "https://github.com/arnav-kr"
private const val FORK_RELEASES_URL = "https://github.com/wylify/Bluke_Simplified/releases"

class AboutActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val versionName = try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "1.0.1"
        } catch (_: Exception) {
            "1.0.1"
        }
        
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                fun openUrl(url: String) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
                
                // Compute shapes separately to avoid sharing a stateful Shape instance across different sizes, which causes layout/shrinking bugs on activity resume.
                val logoShape = getCookieShape(7)
                val developerShape = getCookieShape(7)
                
                var clickCount by remember { mutableIntStateOf(0) }
                val sharedPrefs = context.getSharedPreferences("app_prefs", MODE_PRIVATE)

                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                            ),
                            scrollBehavior = scrollBehavior
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
                            color = Color.Black,
                            modifier = Modifier
                                .size(100.dp)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null // Hide ripple for secret click
                                ) {
                                    clickCount++
                                    if (clickCount == 5) {
                                        sharedPrefs.edit { putBoolean("is_developer_mode", true) }
                                        android.widget.Toast.makeText(context, "You are now a developer!", android.widget.Toast.LENGTH_SHORT).show()
                                    } else if (clickCount > 5 && clickCount % 3 == 0) {
                                        android.widget.Toast.makeText(context, "No need, you are already a developer.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = "App Logo",
                                modifier = Modifier.fillMaxSize(),
                                tint = Color.White
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

                        // Fork badge
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Unofficial fork of Bluke",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        SettingsGroup(title = "About this fork") {
                            Text(
                                text = "Simply Bluke is a simplified, unofficial fork of Bluke. " +
                                    "All the core Bluetooth keyboard, touchpad and remote work comes from the original project by Arnav Kumar.\n\n" +
                                    "This fork is not affiliated with or supported by the original developer, " +
                                    "so please don't send issues about Simply Bluke to the Bluke project.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }

                        // Credit to the original developer
                        SettingsGroup(title = "Original developer") {
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
                                            text = "@arnav-kr · creator of Bluke",
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
                                        onClick = { openUrl(ORIGINAL_DEVELOPER_URL) },
                                        label = "Profile",
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
                                        onClick = { openUrl(ORIGINAL_REPO_URL) },
                                        label = "Bluke repo",
                                        icon = {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_github),
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

                        // How to get the original app
                        SettingsGroup(title = "Get the original Bluke") {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val steps = listOf(
                                    "Open the Bluke releases page on GitHub.",
                                    "Under the latest release, download the .apk file from Assets.",
                                    "You can keep Simply Bluke installed. The two are separate apps, but only one should be connected to a host at a time.",
                                    "Open the downloaded APK and allow installs from your browser or file manager if Android asks."
                                )
                                steps.forEachIndexed { index, step ->
                                    Row {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.width(24.dp)
                                        )
                                        Text(
                                            text = step,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { openUrl(ORIGINAL_RELEASES_URL) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open Bluke releases")
                                }
                            }
                        }

                        SettingsCardGroup(
                            title = "App",
                            items = listOf(
                                SettingsItemData(
                                    title = "Changelog",
                                    subtitle = "Release history of Simply Bluke",
                                    icon = { Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { openUrl(FORK_RELEASES_URL) }
                                ),
                                SettingsItemData(
                                    title = "Licenses",
                                    subtitle = "View the licenses that the app and libraries are using",
                                    icon = { Icon(Icons.Default.Policy, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { startActivity(Intent(this@AboutActivity, LicensesActivity::class.java)) }
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
