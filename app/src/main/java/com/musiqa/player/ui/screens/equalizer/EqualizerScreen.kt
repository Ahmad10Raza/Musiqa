package com.musiqa.player.ui.screens.equalizer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musiqa.player.audio.PlayerController
import com.musiqa.player.ui.theme.CrimsonPrimary
import com.musiqa.player.ui.theme.DarkSurfaceElevated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eqManager = PlayerController.audioEffectsManager
    var isEnabled by remember { mutableStateOf(eqManager.isEnabled) }
    var selectedPreset by remember { mutableIntStateOf(eqManager.currentPresetIndex.toInt()) }
    var bassBoostVal by remember { mutableFloatStateOf(eqManager.getBassBoostStrength().toFloat() / 1000f) }

    val presets = remember {
        val list = eqManager.getPresets()
        if (list.isNotEmpty()) list else listOf("Normal", "Classical", "Dance", "Flat", "Folk", "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock")
    }

    val numBands = eqManager.getNumberOfBands()
    val bandLevels = remember {
        mutableStateListOf<Short>().apply {
            for (i in 0 until numBands) {
                add(eqManager.getBandLevel(i.toShort()))
            }
        }
    }

    val (minRange, maxRange) = remember { eqManager.getBandLevelRange() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equalizer & Sound Effects", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            eqManager.setEnabled(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CrimsonPrimary)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Presets Horizontal Row
            Text(
                text = "Presets",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(presets) { index, presetName ->
                    FilterChip(
                        selected = selectedPreset == index,
                        onClick = {
                            selectedPreset = index
                            eqManager.usePreset(index.toShort())
                            // Refresh band values
                            for (i in 0 until numBands) {
                                bandLevels[i] = eqManager.getBandLevel(i.toShort())
                            }
                        },
                        label = { Text(presetName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CrimsonPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 5 / 10 Band Equalizer Sliders
            Text(
                text = "Frequency Bands",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    for (band in 0 until numBands) {
                        val freq = eqManager.getCenterFreq(band.toShort())
                        val freqLabel = if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz"
                        val currentLevel = bandLevels.getOrElse(band) { 0 }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = freqLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.width(68.dp)
                            )

                            Slider(
                                value = currentLevel.toFloat(),
                                onValueChange = { newVal ->
                                    val shortVal = newVal.toInt().toShort()
                                    bandLevels[band] = shortVal
                                    eqManager.setBandLevel(band.toShort(), shortVal)
                                },
                                valueRange = minRange.toFloat()..maxRange.toFloat(),
                                enabled = isEnabled,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = CrimsonPrimary,
                                    activeTrackColor = CrimsonPrimary
                                )
                            )

                            Text(
                                text = "${currentLevel / 100} dB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(48.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bass Booster Dial
            Text(
                text = "Bass Booster",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bass Level",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.width(90.dp)
                    )
                    Slider(
                        value = bassBoostVal,
                        onValueChange = {
                            bassBoostVal = it
                            eqManager.setBassBoost((it * 1000).toInt().toShort())
                        },
                        valueRange = 0f..1f,
                        enabled = isEnabled,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = CrimsonPrimary,
                            activeTrackColor = CrimsonPrimary
                        )
                    )
                    Text(
                        text = "${(bassBoostVal * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = CrimsonPrimary,
                        modifier = Modifier.width(44.dp)
                    )
                }
            }
        }
    }
}
