package dev.arnv.bluke

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.ui.SettingsCardGroup
import dev.arnv.bluke.ui.SettingsItemData
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import dev.arnv.bluke.ui.theme.AccentColors
import dev.arnv.bluke.ui.theme.getCookieShape
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch

enum class SettingsScreenState { HOME, LOOK_AND_FEEL, DARK_THEME }

class SettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isDynamicColorDefault = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        
        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf(SettingsScreenState.HOME) }

                BackHandler(enabled = currentScreen != SettingsScreenState.HOME) {
                    if (currentScreen == SettingsScreenState.LOOK_AND_FEEL) {
                        currentScreen = SettingsScreenState.HOME
                    } else if (currentScreen == SettingsScreenState.DARK_THEME) {
                        currentScreen = SettingsScreenState.LOOK_AND_FEEL
                    }
                }

                var hideUnknownDevices by remember { mutableStateOf(sharedPrefs.getBoolean("hide_unknown", false)) }
                var showMacAddress by remember { mutableStateOf(sharedPrefs.getBoolean("show_mac", false)) }

                var dynamicColor by remember { mutableStateOf(sharedPrefs.getBoolean("dynamic_color", isDynamicColorDefault)) }
                var accentColorIndex by remember { mutableStateOf(sharedPrefs.getInt("accent_color_index", 0)) }
                var paletteStyleState by remember { mutableStateOf(sharedPrefs.getString("palette_style", "Tonal Spot") ?: "Tonal Spot") }

                var themeMode by remember { mutableStateOf(sharedPrefs.getInt("theme_mode", 0)) } // 0: System, 1: Off, 2: On
                var highContrastMode by remember { mutableStateOf(sharedPrefs.getBoolean("high_contrast_mode", false)) }
                var hapticsEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("haptics_enabled", true)) }
                var keySoundEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("key_sound_enabled", true)) }

                Scaffold(
                    topBar = {
                        LargeTopAppBar(
                            title = { 
                                val title = when(currentScreen) {
                                    SettingsScreenState.HOME -> "Settings"
                                    SettingsScreenState.LOOK_AND_FEEL -> "Look & Feel"
                                    SettingsScreenState.DARK_THEME -> "Dark theme"
                                }
                                Text(title) 
                            },
                            navigationIcon = {
                                IconButton(onClick = { 
                                    if (currentScreen == SettingsScreenState.LOOK_AND_FEEL) {
                                        currentScreen = SettingsScreenState.HOME
                                    } else if (currentScreen == SettingsScreenState.DARK_THEME) {
                                        currentScreen = SettingsScreenState.LOOK_AND_FEEL
                                    } else {
                                        finish() 
                                    }
                                }) {
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
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))
                        if (currentScreen == SettingsScreenState.HOME) {
                            SettingsCardGroup(
                                items = listOf(
                                    SettingsItemData(
                                        title = "Look & Feel",
                                        subtitle = "Dynamic colors, Dark theme, Haptics",
                                        icon = { Icon(Icons.Default.ColorLens, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { currentScreen = SettingsScreenState.LOOK_AND_FEEL }
                                    ),
                                    SettingsItemData(
                                        title = "Behavior",
                                        subtitle = "Modify certain behavior of the app",
                                        icon = { Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { 
                                            startActivity(android.content.Intent(this@SettingsActivity, BehaviorActivity::class.java))
                                        }
                                    ),
                                    SettingsItemData(
                                        title = "About",
                                        subtitle = "Contributors and support",
                                        icon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { 
                                            startActivity(android.content.Intent(this@SettingsActivity, AboutActivity::class.java))
                                        }
                                    )
                                )
                            )

                        } else if (currentScreen == SettingsScreenState.LOOK_AND_FEEL) {
                            var showPaletteDialog by remember { mutableStateOf(false) }
                            
                            // Top illustration mock
                            Icon(
                                Icons.Default.FormatPaint, 
                                contentDescription = null, 
                                modifier = Modifier.size(100.dp).align(Alignment.CenterHorizontally), 
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            
                            if (!dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                val pageCount = (AccentColors.size + 3) / 4
                                val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pageCount })
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    androidx.compose.foundation.pager.HorizontalPager(
                                        state = pagerState,
                                        contentPadding = PaddingValues(horizontal = 24.dp),
                                        pageSpacing = 16.dp
                                    ) { page ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            val startIndex = page * 4
                                            for (i in 0 until 4) {
                                                val index = startIndex + i
                                                if (index < AccentColors.size) {
                                                    val color = AccentColors[index]
                                                    val isSelected = accentColorIndex == index
                                                    Box(
                                                        modifier = Modifier
                                                            .size(64.dp)
                                                            .clip(CircleShape)
                                                            .background(color)
                                                            .clickable {
                                                                accentColorIndex = index
                                                                sharedPrefs.edit().putInt("accent_color_index", index).apply()
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isSelected) {
                                                            Surface(
                                                                shape = CircleShape,
                                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(6.dp), tint = MaterialTheme.colorScheme.onSurface)
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.size(64.dp))
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    val CookieShape7 = getCookieShape(7)
                                    val PebbleShape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 35)
                                    val ArchShapeRound = androidx.compose.foundation.shape.RoundedCornerShape(
                                        topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp
                                    )
                                    val CookieShape5 = getCookieShape(5)

                                    val paginationShapes = listOf(CookieShape7, PebbleShape, ArchShapeRound, CookieShape5)
                                    val coroutineScope = rememberCoroutineScope()

                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        repeat(pageCount) { iteration ->
                                            val isSelected = pagerState.currentPage == iteration
                                            val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                            val shape = if (isSelected) {
                                                paginationShapes.getOrElse(iteration) { CookieShape7 }
                                            } else {
                                                androidx.compose.foundation.shape.CircleShape
                                            }
                                            val size = if (isSelected) 14.dp else 10.dp
                                            Box(
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .size(size)
                                                    .clip(shape)
                                                    .background(color)
                                                    .clickable {
                                                        coroutineScope.launch {
                                                            pagerState.animateScrollToPage(iteration)
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }
                            }

                            SettingsCardGroup(
                                title = "Color Palette",
                                items = listOf(
                                    SettingsItemData(
                                        title = "Dynamic colors",
                                        subtitle = "Automatically set the app theme according to the device wallpaper",
                                        icon = { Icon(Icons.Default.FormatPaint, null, tint = MaterialTheme.colorScheme.primary) },
                                        action = {
                                            Switch(
                                                checked = dynamicColor,
                                                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                                onCheckedChange = { 
                                                    dynamicColor = it
                                                    sharedPrefs.edit().putBoolean("dynamic_color", it).apply()
                                                }
                                            )
                                        }
                                    ),
                                    SettingsItemData(
                                        title = "Palette style",
                                        subtitle = paletteStyleState,
                                        icon = { Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { showPaletteDialog = true }
                                    )
                                )
                            )
                            if (showPaletteDialog) {
                                AlertDialog(
                                    onDismissRequest = { showPaletteDialog = false },
                                    title = { Text("Palette style") },
                                    text = {
                                        Column {
                                            listOf("Tonal Spot", "Vibrant", "Expressive", "Rainbow", "Fruit Salad", "Fidelity", "Content", "Neutral", "Monochrome").forEach { option ->
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth().clickable { 
                                                        paletteStyleState = option
                                                        sharedPrefs.edit().putString("palette_style", option).apply()
                                                        showPaletteDialog = false 
                                                    }.padding(vertical = 8.dp)
                                                ) {
                                                    RadioButton(selected = option == paletteStyleState, onClick = null)
                                                    Spacer(Modifier.width(16.dp))
                                                    Text(option)
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { showPaletteDialog = false }) { Text("Confirm") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showPaletteDialog = false }) { Text("Cancel") }
                                    }
                                )
                            }
                            
                            SettingsCardGroup(
                                title = "Additional settings",
                                items = listOf(
                                    SettingsItemData(
                                        title = "Dark theme",
                                        subtitle = when(themeMode) {
                                            0 -> "System"
                                            1 -> "Off"
                                            else -> "On"
                                        },
                                        icon = { Icon(Icons.Default.DarkMode, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { currentScreen = SettingsScreenState.DARK_THEME }
                                    ),
                                    SettingsItemData(
                                        title = "Haptics & Vibration",
                                        subtitle = "Interactive haptics for touch feedback",
                                        icon = { Icon(Icons.Default.Vibration, null, tint = MaterialTheme.colorScheme.primary) },
                                        action = {
                                            Switch(
                                                checked = hapticsEnabled,
                                                onCheckedChange = { 
                                                    hapticsEnabled = it
                                                    sharedPrefs.edit().putBoolean("haptics_enabled", it).apply()
                                                }
                                            )
                                        }
                                    ),
                                    SettingsItemData(
                                        title = "Key Press Sound",
                                        subtitle = "Play sound effect on key press",
                                        icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = MaterialTheme.colorScheme.primary) },
                                        action = {
                                            Switch(
                                                checked = keySoundEnabled,
                                                onCheckedChange = { 
                                                    keySoundEnabled = it
                                                    sharedPrefs.edit().putBoolean("key_sound_enabled", it).apply()
                                                    sharedPrefs.edit().putBoolean("sound_toggle", it).apply() 
                                                }
                                            )
                                        }
                                    )
                                )
                            )

                        } else if (currentScreen == SettingsScreenState.DARK_THEME) {
                            SettingsCardGroup(
                                items = listOf(
                                    SettingsItemData(
                                        title = "System",
                                        action = {
                                            RadioButton(
                                                selected = themeMode == 0,
                                                onClick = {
                                                    themeMode = 0
                                                    sharedPrefs.edit().putInt("theme_mode", 0).apply()
                                                }
                                            )
                                        },
                                        onClick = {
                                            themeMode = 0
                                            sharedPrefs.edit().putInt("theme_mode", 0).apply()
                                        }
                                    ),
                                    SettingsItemData(
                                        title = "Off",
                                        action = {
                                            RadioButton(
                                                selected = themeMode == 1,
                                                onClick = {
                                                    themeMode = 1
                                                    sharedPrefs.edit().putInt("theme_mode", 1).apply()
                                                }
                                            )
                                        },
                                        onClick = {
                                            themeMode = 1
                                            sharedPrefs.edit().putInt("theme_mode", 1).apply()
                                        }
                                    ),
                                    SettingsItemData(
                                        title = "On",
                                        action = {
                                            RadioButton(
                                                selected = themeMode == 2,
                                                onClick = {
                                                    themeMode = 2
                                                    sharedPrefs.edit().putInt("theme_mode", 2).apply()
                                                }
                                            )
                                        },
                                        onClick = {
                                            themeMode = 2
                                            sharedPrefs.edit().putInt("theme_mode", 2).apply()
                                        }
                                    )
                                )
                            )

                            SettingsCardGroup(
                                title = "Additional settings",
                                items = listOf(
                                    SettingsItemData(
                                        title = "High contrast dark mode",
                                        subtitle = "Pitch black dark theme for devices with OLED display",
                                        icon = { Icon(Icons.Default.Contrast, null, tint = MaterialTheme.colorScheme.primary) },
                                        action = {
                                            Switch(
                                                checked = highContrastMode,
                                                onCheckedChange = { 
                                                    highContrastMode = it
                                                    sharedPrefs.edit().putBoolean("high_contrast_mode", it).apply()
                                                }
                                            )
                                        }
                                    )
                                )
                            )
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
