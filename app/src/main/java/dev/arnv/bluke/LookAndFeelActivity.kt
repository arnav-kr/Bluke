package dev.arnv.bluke

import android.content.Intent
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.ui.SettingsCardGroup
import dev.arnv.bluke.ui.SettingsItemData
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import dev.arnv.bluke.ui.theme.AccentColors
import dev.arnv.bluke.ui.theme.CUSTOM_THEME_ACCENT
import dev.arnv.bluke.ui.theme.CUSTOM_THEME_BACKGROUND
import dev.arnv.bluke.ui.theme.CUSTOM_THEME_ENABLED
import dev.arnv.bluke.ui.theme.CUSTOM_THEME_SURFACE
import dev.arnv.bluke.ui.theme.DEFAULT_CUSTOM_ACCENT
import dev.arnv.bluke.ui.theme.DEFAULT_CUSTOM_BACKGROUND
import dev.arnv.bluke.ui.theme.DEFAULT_CUSTOM_SURFACE
import dev.arnv.bluke.ui.theme.getCookieShape
import kotlinx.coroutines.launch

import androidx.core.content.edit

class LookAndFeelActivity : ComponentActivity() {
    private val themeModeState = mutableIntStateOf(0)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isDynamicColorDefault = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        themeModeState.intValue = sharedPrefs.getInt("theme_mode", 0)
        
        setContent {
            MyApplicationTheme {
                var dynamicColor by remember { mutableStateOf(sharedPrefs.getBoolean("dynamic_color", isDynamicColorDefault)) }
                var accentColorIndex by remember { mutableIntStateOf(sharedPrefs.getInt("accent_color_index", 0)) }
                var customThemeEnabled by remember { mutableStateOf(sharedPrefs.getBoolean(CUSTOM_THEME_ENABLED, false)) }
                var paletteStyleState by remember { mutableStateOf(sharedPrefs.getString("palette_style", "Tonal Spot") ?: "Tonal Spot") }
                
                var hapticsEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("haptics_enabled", true)) }
                var keySoundEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("key_sound_enabled", true)) }
                
                var showPaletteDialog by remember { mutableStateOf(false) }
                var showCustomColorDialog by remember { mutableStateOf(false) }
                val themeMode by themeModeState
                
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
                
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("Look & Feel") },
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
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))
                        
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
                                                val isSelected = !customThemeEnabled && accentColorIndex == index
                                                Box(
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                        .clickable {
                                                            accentColorIndex = index
                                                            customThemeEnabled = false
                                                            sharedPrefs.edit {
                                                                putInt("accent_color_index", index)
                                                                putBoolean(CUSTOM_THEME_ENABLED, false)
                                                            }
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
                                
                                val cookieShape7 = getCookieShape(7)
                                val pebbleShape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 35)
                                val archShapeRound = androidx.compose.foundation.shape.RoundedCornerShape(
                                    topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp
                                )
                                val cookieShape5 = getCookieShape(5)

                                val paginationShapes = listOf(cookieShape7, pebbleShape, archShapeRound, cookieShape5)
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
                                            paginationShapes.getOrElse(iteration) { cookieShape7 }
                                        } else {
                                            CircleShape
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

                                Spacer(modifier = Modifier.height(16.dp))

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .clickable { showCustomColorDialog = true },
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        CustomPalettePreview(
                                            background = Color(sharedPrefs.getInt(CUSTOM_THEME_BACKGROUND, DEFAULT_CUSTOM_BACKGROUND)),
                                            surface = Color(sharedPrefs.getInt(CUSTOM_THEME_SURFACE, DEFAULT_CUSTOM_SURFACE)),
                                            accent = Color(sharedPrefs.getInt(CUSTOM_THEME_ACCENT, DEFAULT_CUSTOM_ACCENT)),
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Custom colors", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Background, keys/surfaces, and accent/text",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        RadioButton(
                                            selected = customThemeEnabled,
                                            onClick = { showCustomColorDialog = true },
                                        )
                                    }
                                }
                            }
                        }

                        if (showCustomColorDialog) {
                            CustomColorDialog(
                                initialBackground = sharedPrefs.getInt(CUSTOM_THEME_BACKGROUND, DEFAULT_CUSTOM_BACKGROUND),
                                initialSurface = sharedPrefs.getInt(CUSTOM_THEME_SURFACE, DEFAULT_CUSTOM_SURFACE),
                                initialAccent = sharedPrefs.getInt(CUSTOM_THEME_ACCENT, DEFAULT_CUSTOM_ACCENT),
                                onDismiss = { showCustomColorDialog = false },
                                onSave = { background, surface, accent ->
                                    customThemeEnabled = true
                                    sharedPrefs.edit {
                                        putInt(CUSTOM_THEME_BACKGROUND, background)
                                        putInt(CUSTOM_THEME_SURFACE, surface)
                                        putInt(CUSTOM_THEME_ACCENT, accent)
                                        putBoolean(CUSTOM_THEME_ENABLED, true)
                                        putBoolean("dynamic_color", false)
                                    }
                                    dynamicColor = false
                                    showCustomColorDialog = false
                                },
                            )
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
                                                sharedPrefs.edit { putBoolean("dynamic_color", it) }
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
                                                    sharedPrefs.edit { putString("palette_style", option) }
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
                                    onClick = { 
                                        startActivity(Intent(this@LookAndFeelActivity, DarkThemeActivity::class.java))
                                    }
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
                                                sharedPrefs.edit { putBoolean("haptics_enabled", it) }
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
                                                sharedPrefs.edit {
                                                    putBoolean("key_sound_enabled", it)
                                                    putBoolean("sound_toggle", it)
                                                }
                                            }
                                        )
                                    }
                                ),
                                SettingsItemData(
                                    title = "Custom key sounds",
                                    subtitle = "Import and select Mechvibes sound packs",
                                    icon = { Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        startActivity(Intent(this@LookAndFeelActivity, SoundPacksActivity::class.java))
                                    }
                                )
                            )
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        themeModeState.intValue = sharedPrefs.getInt("theme_mode", 0)
    }
}
