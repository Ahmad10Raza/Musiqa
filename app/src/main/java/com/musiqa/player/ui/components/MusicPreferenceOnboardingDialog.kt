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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
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
    initialLanguages: Set<String> = setOf("Hindi"),
    initialGenres: Set<String> = setOf("Bollywood", "Sufi & Ghazal", "Romantic"),
    initialArtists: Set<String> = setOf("Arijit Singh", "Nusrat Fateh Ali Khan", "KK"),
    isFirstRun: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (languages: Set<String>, genres: Set<String>, artists: Set<String>) -> Unit
) {
    var selectedLanguages by remember { mutableStateOf(initialLanguages) }
    var selectedGenres by remember { mutableStateOf(initialGenres) }
    var selectedArtists by remember { mutableStateOf(initialArtists) }

    val allLanguages = listOf(
        "Hindi" to "हिन्दी",
        "Punjabi" to "ਪੰਜਾਬੀ",
        "Urdu" to "اردو",
        "English" to "English",
        "Bhojpuri" to "भोजपुरी",
        "Haryanvi" to "हरियाणवी",
        "Tamil" to "தமிழ்",
        "Telugu" to "తెలుగు",
        "Bengali" to "বাংলা"
    )

    val allGenres = listOf(
        "Bollywood", "Sufi & Ghazal", "Romantic", "Lo-Fi & Chill",
        "Workout & Energy", "Party & Dance", "90s Nostalgia", "Devotional"
    )

    val allArtists = listOf(
        "Arijit Singh", "Nusrat Fateh Ali Khan", "KK", "Atif Aslam",
        "Shreya Ghoshal", "A.R. Rahman", "Diljit Dosanjh", "Kishore Kumar",
        "Mohd Rafi", "Sidhu Moosewala", "Jagjit Singh", "Anuv Jain", "AP Dhillon"
    )

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
                                items = allLanguages.map { "${it.first} (${it.second})" to it.first },
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

                    // 3. Favorite Artists
                    item {
                        Column {
                            Text(
                                text = "3. Favorite Artists",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(Modifier.height(10.dp))
                            FlowPills(
                                items = allArtists.map { it to it },
                                selectedKeys = selectedArtists,
                                onToggle = { key ->
                                    selectedArtists = if (selectedArtists.contains(key)) {
                                        selectedArtists - key
                                    } else {
                                        selectedArtists + key
                                    }
                                }
                            )
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
