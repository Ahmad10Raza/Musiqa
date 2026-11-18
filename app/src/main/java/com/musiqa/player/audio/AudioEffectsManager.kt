package com.musiqa.player.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import com.musiqa.player.data.repository.UserPreferencesRepository

class AudioEffectsManager {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var prefsRepo: UserPreferencesRepository? = null

    var isEnabled: Boolean = false
        private set

    var currentPresetIndex: Short = 0
        private set

    fun attach(audioSessionId: Int, repository: UserPreferencesRepository? = null) {
        if (audioSessionId <= 0) return
        this.prefsRepo = repository
        try {
            release()

            val savedEnabled = repository?.getEqEnabled() ?: true
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = savedEnabled
            }

            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = savedEnabled
            }

            isEnabled = savedEnabled

            // Restore saved preset
            val savedPreset = repository?.getEqPreset() ?: 0
            if (savedPreset in 0 until (equalizer?.numberOfPresets ?: 0)) {
                equalizer?.usePreset(savedPreset)
                currentPresetIndex = savedPreset
            }

            // Restore saved bass boost
            val savedBass = repository?.getBassBoost() ?: 0
            if (savedBass > 0) {
                bassBoost?.setStrength(savedBass)
            }

            Log.d("AudioEffects", "Attached AudioEffects to session: $audioSessionId with ${equalizer?.numberOfBands} bands (preset=$savedPreset, bass=$savedBass)")
        } catch (e: Exception) {
            Log.e("AudioEffects", "Failed to initialize Equalizer/BassBoost", e)
            isEnabled = false
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        prefsRepo?.setEqEnabled(enabled)
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
        } catch (e: Exception) {
            Log.e("AudioEffects", "Failed to toggle enabled state", e)
        }
    }

    fun getNumberOfBands(): Short = equalizer?.numberOfBands ?: 5

    fun getBandLevelRange(): Pair<Short, Short> {
        val range = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
        return Pair(range[0], range[1])
    }

    fun getCenterFreq(band: Short): Int {
        return (equalizer?.getCenterFreq(band) ?: 0) / 1000 // In Hz or kHz
    }

    fun getBandLevel(band: Short): Short {
        return equalizer?.getBandLevel(band) ?: 0
    }

    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
        } catch (e: Exception) {
            Log.e("AudioEffects", "Error setting band $band to $level", e)
        }
    }

    fun getPresets(): List<String> {
        val eq = equalizer ?: return emptyList()
        val numPresets = eq.numberOfPresets
        val list = mutableListOf<String>()
        for (i in 0 until numPresets) {
            list.add(eq.getPresetName(i.toShort()))
        }
        return list
    }

    fun usePreset(presetIndex: Short) {
        try {
            equalizer?.usePreset(presetIndex)
            currentPresetIndex = presetIndex
            prefsRepo?.setEqPreset(presetIndex)
        } catch (e: Exception) {
            Log.e("AudioEffects", "Error applying preset $presetIndex", e)
        }
    }

    fun setBassBoost(strength: Short) { // 0 to 1000
        try {
            bassBoost?.setStrength(strength)
            prefsRepo?.setBassBoost(strength)
        } catch (e: Exception) {
            Log.e("AudioEffects", "Error setting BassBoost to $strength", e)
        }
    }

    fun getBassBoostStrength(): Short {
        return bassBoost?.roundedStrength ?: 0
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
        } catch (e: Exception) {
            Log.e("AudioEffects", "Error releasing audio effects", e)
        } finally {
            equalizer = null
            bassBoost = null
            isEnabled = false
        }
    }
}
