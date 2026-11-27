package com.musiqa.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musiqa.player.ui.theme.CrimsonPrimary
import com.musiqa.player.ui.theme.DarkBackground
import com.musiqa.player.ui.theme.DarkSurface
import com.musiqa.player.ui.theme.DarkSurfaceBorder

@Composable
fun MusicPreferenceOnboardingDialog(
    initialLanguages: Set<String> = setOf("English", "Hindi"),
    initialGenres: Set<String> = emptySet(),
    initialArtists: Set<String> = emptySet(),
    isFirstRun: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (languages: Set<String>, genres: Set<String>, artists: Set<String>) -> Unit
) {
    var selectedLanguages by remember { mutableStateOf(initialLanguages.ifEmpty { setOf("English", "Hindi") }) }
    var selectedGenres by remember { mutableStateOf(initialGenres) }
    var selectedArtists by remember { mutableStateOf(initialArtists) }
    var customArtistInput by remember { mutableStateOf("") }
    var artistFilterQuery by remember { mutableStateOf("") }

    val primaryLanguages = listOf(
        "English (US)" to "English (US)",
        "English (India)" to "English (India)",
        "Hindi" to "हिन्दी",
        "Punjabi" to "ਪੰਜਾਬੀ",
        "Urdu" to "اردو"
    )
    val regionalLanguages = listOf(
        "Tamil" to "தமிழ்",
        "Telugu" to "తెలుగు",
        "Bengali" to "বাংলা",
        "Marathi" to "मराठी",
        "Gujarati" to "ગુજરાતી",
        "Kannada" to "ಕನ್ನಡ",
        "Malayalam" to "മലയാളം"
    )
    val allLanguages = primaryLanguages + regionalLanguages

    val allGenres = listOf(
        "Pop", "Rock", "Hip-Hop & Rap", "R&B & Soul",
        "Electronic & EDM", "Lo-Fi & Chill", "Indie & Alt", "Jazz & Blues",
        "Bollywood", "Sufi & Ghazal", "Punjabi Beats", "Romantic",
        "Sad & Heartbreak", "Workout & Energy", "Party & Dance",
        "90s & 2000s Nostalgia", "Acoustic & Unplugged", "Classical & Instrumental", "Devotional"
    )

    val globalArtists = listOf(
        "The Weeknd", "Taylor Swift", "Drake", "Ed Sheeran", "Eminem",
        "Coldplay", "Billie Eilish", "Bruno Mars", "Dua Lipa", "Imagine Dragons",
        "Linkin Park", "Post Malone", "Kendrick Lamar", "Ariana Grande",
        "Justin Bieber", "Travis Scott", "Olivia Rodrigo", "SZA",
        "Arctic Monkeys", "Radiohead", "Queen", "Michael Jackson", "Pink Floyd"
    )

    val indianArtists = listOf(
        "Arijit Singh", "A.R. Rahman", "Nusrat Fateh Ali Khan", "KK", "Atif Aslam",
        "Shreya Ghoshal", "Diljit Dosanjh", "Karan Aujla", "AP Dhillon", "Anuv Jain",
        "Sidhu Moosewala", "Kishore Kumar", "Mohd Rafi", "Pritam", "Sonu Nigam",
        "Sunidhi Chauhan", "Jubin Nautiyal", "Rahat Fateh Ali Khan", "Jagjit Singh",
        "Lucky Ali", "Badshah", "Honey Singh", "Shubh", "Mohit Chauhan"
    )

    val customAddedArtists = remember(selectedArtists) {
        selectedArtists.filter { it !in globalArtists && it !in indianArtists }.sorted()
    }

    Dialog(
        onDismissRequest = { if (!isFirstRun) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isFirstRun) "Set Up Your Music" else "Music Preferences",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Tailor your personalized YouTube Music feed & categories",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFFAAAAAA)
                            )
                        )
                    }
                    if (!isFirstRun) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. Languages Section
                    item {
                        Column {
                            Text(
                                text = "1. Languages you listen to",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(Modifier.height(10.dp))
                            FlowPills(
                                items = allLanguages.map {
                                    val label = if (it.first == it.second) it.first else "${it.first} (${it.second})"
                                    label to it.first
                                },
                                selectedKeys = selectedLanguages,
                                onToggle = { key ->
                                    selectedLanguages = if (selectedLanguages.contains(key)) {
                                        if (selectedLanguages.size > 1) selectedLanguages - key else selectedLanguages
                                    } else {
                                        selectedLanguages + key
                                    }
                                }
                            )
                        }
                    }

                    // 2. Vibes & Genres
                    item {
                        Column {
                            Text(
                                text = "2. Vibes & Genres",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(Modifier.height(10.dp))
                            FlowPills(
                                items = allGenres.map { it to it },
                                selectedKeys = selectedGenres,
                                onToggle = { key ->
                                    selectedGenres = if (selectedGenres.contains(key)) {
                                        selectedGenres - key
                                    } else {
                                        selectedGenres + key
                                    }
                                }
                            )
                        }
                    }

                    // 3. Favorite Artists (Global + India + Custom)
                    item {
                        Column {
                            Text(
                                text = "3. Favorite Singers & Artists (Global & India)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(Modifier.height(8.dp))

                            // Add any custom artist or filter list
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customArtistInput,
                                    onValueChange = {
                                        customArtistInput = it
                                        artistFilterQuery = it
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    placeholder = { Text("Filter or add any singer (e.g. Weeknd, Talha Anjum)", fontSize = 12.sp) },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        val clean = customArtistInput.trim()
                                        if (clean.isNotBlank()) {
                                            selectedArtists = selectedArtists + clean
                                            customArtistInput = ""
                                            artistFilterQuery = ""
                                        }
                                    }),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF161616),
                                        unfocusedContainerColor = Color(0xFF161616),
                                        focusedBorderColor = CrimsonPrimary,
                                        unfocusedBorderColor = Color(0xFF333333),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                IconButton(
                                    onClick = {
                                        val clean = customArtistInput.trim()
                                        if (clean.isNotBlank()) {
                                            selectedArtists = selectedArtists + clean
                                            customArtistInput = ""
                                            artistFilterQuery = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(CrimsonPrimary)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add artist", tint = Color.White)
                                }
                            }

                            if (customAddedArtists.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Text("Your Added Artists", color = Color(0xFFBBBBBB), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                FlowPills(
                                    items = customAddedArtists.map { it to it },
                                    selectedKeys = selectedArtists,
                                    onToggle = { key -> selectedArtists = selectedArtists - key }
                                )
                            }

                            val q = artistFilterQuery.trim()
                            val filteredGlobal = if (q.isBlank()) globalArtists else globalArtists.filter { it.contains(q, ignoreCase = true) }
                            val filteredIndian = if (q.isBlank()) indianArtists else indianArtists.filter { it.contains(q, ignoreCase = true) }

                            if (filteredGlobal.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("Global Icons", color = Color(0xFFBBBBBB), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                FlowPills(
                                    items = filteredGlobal.map { it to it },
                                    selectedKeys = selectedArtists,
                                    onToggle = { key ->
                                        selectedArtists = if (selectedArtists.contains(key)) selectedArtists - key else selectedArtists + key
                                    }
                                )
                            }

                            if (filteredIndian.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("Indian & South Asian", color = Color(0xFFBBBBBB), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                FlowPills(
                                    items = filteredIndian.map { it to it },
                                    selectedKeys = selectedArtists,
                                    onToggle = { key ->
                                        selectedArtists = if (selectedArtists.contains(key)) selectedArtists - key else selectedArtists + key
                                    }
                                )
                            }
                        }
                    }

                    // 4. Privacy badge
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF161616),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262626)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = CrimsonPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "100% Offline & Private • Preferences stay on your device without server tracking or account login required.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF888888)),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Save / Continue Button
                Button(
                    onClick = { onSave(selectedLanguages, selectedGenres, selectedArtists) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        text = if (isFirstRun) "Start Listening" else "Save Preferences",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                if (isFirstRun) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            val randomGlobal = globalArtists.shuffled().take(2)
                            val randomIndian = indianArtists.shuffled().take(2)
                            val randomGenres = allGenres.shuffled().take(3).toSet()
                            onSave(
                                setOf("English (US)", "English (India)", "Hindi"),
                                randomGenres,
                                (randomGlobal + randomIndian).toSet()
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Skip for now (Surprise me)",
                            color = Color(0xFFAAAAAA),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowPills(
    items: List<Pair<String, String>>,
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit
) {
    // Flow row using chunked items for clean wrap
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val rows = items.chunked(3)
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((label, key) in row) {
                    val isSelected = selectedKeys.contains(key)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onToggle(key) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) CrimsonPrimary else Color(0xFF1A1A1A),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) CrimsonPrimary else Color(0xFF333333)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color.White else Color(0xDDFFFFFF)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                // Fill blank weights if row has fewer than 3
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
