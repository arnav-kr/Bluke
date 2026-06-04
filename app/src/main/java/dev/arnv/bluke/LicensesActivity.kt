package dev.arnv.bluke

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arnv.bluke.ui.theme.MyApplicationTheme

data class LibraryData(
    val name: String,
    val author: String,
    val version: String,
    val license: String,
    val artifactId: String,
    val githubUrl: String
)

class LicensesActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                
                var searchQuery by remember { mutableStateOf("") }
                var expandedLibraries by remember { mutableStateOf(setOf<String>()) }
                
                // Define the full list of real/detected project dependencies + KBSim core
                val allLibraries = remember {
                    listOf(
                        LibraryData(
                            name = "KBSim",
                            author = "tplai",
                            version = "1.0.0-master",
                            license = "AGPL-3.0",
                            artifactId = "project:kbsim",
                            githubUrl = "https://github.com/tplai/kbsim"
                        ),
                        LibraryData(
                            name = "AboutLibraries Core Android",
                            author = "Mike Penz",
                            version = "14.2.0",
                            license = "Apache-2.0",
                            artifactId = "com.mikepenz:aboutlibraries-core-android:14.2.0",
                            githubUrl = "https://github.com/mikepenz/AboutLibraries"
                        ),
                        LibraryData(
                            name = "Activity Compose",
                            author = "Android Open Source Project",
                            version = "1.7.0",
                            license = "Apache-2.0",
                            artifactId = "androidx.activity:activity-compose:1.7.0",
                            githubUrl = "https://developer.android.com/jetpack/androidx/releases/activity"
                        ),
                        LibraryData(
                            name = "Compose Material 3",
                            author = "Android Open Source Project",
                            version = "1.1.2",
                            license = "Apache-2.0",
                            artifactId = "androidx.compose.material3:material3:1.1.2",
                            githubUrl = "https://developer.android.com/jetpack/androidx/releases/compose-material3"
                        ),
                        LibraryData(
                            name = "Kotlinx Serialization JSON",
                            author = "JetBrains",
                            version = "1.6.0",
                            license = "Apache-2.0",
                            artifactId = "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0",
                            githubUrl = "https://github.com/Kotlin/kotlinx.serialization"
                        ),
                        LibraryData(
                            name = "Retrofit Networking Core",
                            author = "Square",
                            version = "2.9.0",
                            license = "Apache-2.0",
                            artifactId = "com.squareup.retrofit2:retrofit:2.9.0",
                            githubUrl = "https://github.com/square/retrofit"
                        ),
                        LibraryData(
                            name = "OkHttp Client Engine",
                            author = "Square",
                            version = "4.11.0",
                            license = "Apache-2.0",
                            artifactId = "com.squareup.okhttp3:okhttp:4.11.0",
                            githubUrl = "https://github.com/square/okhttp"
                        ),
                        LibraryData(
                            name = "Room Database Architecture",
                            author = "Android Open Source Project",
                            version = "2.6.1",
                            license = "Apache-2.0",
                            artifactId = "androidx.room:room-runtime:2.6.1",
                            githubUrl = "https://developer.android.com/training/data-storage/room"
                        )
                    )
                }
                
                // Filter libraries based on search query
                val filteredLibraries = remember(searchQuery) {
                    if (searchQuery.isBlank()) {
                        allLibraries
                    } else {
                        allLibraries.filter { lib ->
                            lib.name.contains(searchQuery, ignoreCase = true) ||
                            lib.author.contains(searchQuery, ignoreCase = true) ||
                            lib.license.contains(searchQuery, ignoreCase = true) ||
                            lib.artifactId.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Licenses") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {


                        // Scrollable content area
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // App License Card (AGPL-3.0)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(24.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                text = "Bluke",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text("This application", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("AGPL-3.0", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Text(
                                        text = "This is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                        Text(
                                            text = "View on GitHub", 
                                            color = MaterialTheme.colorScheme.primary, 
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/arnav-kr/Bluke")))
                                            }
                                        )
                                        Text(
                                            text = "Full license text", 
                                            color = MaterialTheme.colorScheme.primary, 
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://raw.githubusercontent.com/arnav-kr/Bluke/main/LICENSE")))
                                            }
                                        )
                                    }
                                }
                            }

                            // Third-party libraries header
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text("Third-party libraries", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${filteredLibraries.size} libraries detected", 
                                        style = MaterialTheme.typography.bodyMedium, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Search bar below libraries detected text
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                placeholder = { Text("Search libraries, authors, licenses...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                shape = RoundedCornerShape(24.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            )

                            // Dynamic, expandable Library Cards list
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            ) {
                                filteredLibraries.forEachIndexed { index, lib ->
                                    val isExpanded = expandedLibraries.contains(lib.name)
                                    val topRadius = if (index == 0) 28.dp else 4.dp
                                    val bottomRadius = if (index == filteredLibraries.size - 1) 28.dp else 4.dp
                                    val shape = RoundedCornerShape(
                                        topStart = topRadius,
                                        topEnd = topRadius,
                                        bottomStart = bottomRadius,
                                        bottomEnd = bottomRadius
                                    )
                                    Card(
                                        onClick = {
                                            expandedLibraries = if (isExpanded) {
                                                expandedLibraries - lib.name
                                            } else {
                                                expandedLibraries + lib.name
                                            }
                                        },
                                        shape = shape,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = lib.name.first().toString(), 
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer, 
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(lib.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                                    Text("${lib.version} • ${lib.license}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            
                                            if (isExpanded) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                                                Spacer(modifier = Modifier.height(12.dp))
                                                
                                                Text(text = "ARTIFACT ID", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Text(text = lib.artifactId, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                
                                                Text(text = "DEVELOPER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Text(text = lib.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                
                                                Text(text = "LICENSE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Text(text = lib.license, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    TextButton(onClick = {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lib.githubUrl)))
                                                    }) {
                                                        Text("View on GitHub")
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    TextButton(onClick = {
                                                        val licenseUrl = if (lib.name == "KBSim") {
                                                            "https://raw.githubusercontent.com/tplai/kbsim/master/LICNSE.md"
                                                        } else {
                                                            when (lib.license) {
                                                                "AGPL-3.0" -> "https://raw.githubusercontent.com/arnav-kr/Bluke/main/LICENSE"
                                                                else -> "https://www.apache.org/licenses/LICENSE-2.0.txt"
                                                            }
                                                        }
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(licenseUrl)))
                                                    }) {
                                                        Text("Full license text")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}
