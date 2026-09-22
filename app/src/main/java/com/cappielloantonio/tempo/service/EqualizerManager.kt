package com.cappielloantonio.tempo.service

import android.media.audiofx.Equalizer
import com.cappielloantonio.tempo.model.EqualizerPreset
import com.cappielloantonio.tempo.util.Preferences
import com.google.gson.Gson

class EqualizerManager {

    private var equalizer: Equalizer? = null

    fun attachToSession(audioSessionId: Int): Boolean {
        release()
        if (audioSessionId != 0 && audioSessionId != -1) {
            try {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = true
                }
                return true
            } catch (e: Exception) {
                // Some devices may not support Equalizer or audio session may be invalid
                equalizer = null
            }
        }
        return false
    }

    fun setBandLevel(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
    }

    fun getNumberOfBands(): Short = equalizer?.numberOfBands ?: 0

    fun getBandLevelRange(): ShortArray? = equalizer?.bandLevelRange

    fun getCenterFreq(band: Short): Int? =
        equalizer?.getCenterFreq(band)?.div(1000)

    fun getBandLevel(band: Short): Short? =
        equalizer?.getBandLevel(band)

    /** Applies a preset to the live audio session: a device preset via usePreset, a custom one band by band. */
    fun applyPreset(preset: EqualizerPreset) {
        val eq = equalizer ?: return
        val deviceIndex = preset.devicePresetIndex
        if (deviceIndex != null) {
            try { eq.usePreset(deviceIndex) } catch (e: Exception) { /* device rejected the preset */ }
        } else preset.bandLevels?.let { levels ->
            val bands = eq.numberOfBands.toInt()
            for (i in 0 until minOf(bands, levels.size)) {
                try { eq.setBandLevel(i.toShort(), levels[i]) } catch (e: Exception) { /* skip bad band */ }
            }
        }
    }

    /**
     * Reads the live session's band layout and device presets and caches them, so the settings
     * editor and player picker can work later without a playing session (querying a global session
     * 0 equalizer fails on some devices).
     */
    fun captureAndCacheCapabilities() {
        val eq = equalizer ?: return
        // Skip only when a full cache (including each device preset's curve) already exists.
        Preferences.getEqualizerCapabilitiesJson()?.let { json ->
            try {
                val cached = Gson().fromJson(json, EqualizerCapabilities::class.java)
                if (cached != null && cached.bands > 0 && cached.devicePresets.all { it.bandLevels != null }) return
            } catch (e: Exception) { /* re-capture */ }
        }
        try {
            val bands = eq.numberOfBands.toInt()
            if (bands == 0) return
            val range = eq.bandLevelRange ?: shortArrayOf(-1500, 1500)
            val centerFreqs = IntArray(bands) { i -> eq.getCenterFreq(i.toShort()) / 1000 }
            val presetCount = eq.numberOfPresets.toInt()
            val presets = ArrayList<EqualizerPreset>(presetCount)
            for (i in 0 until presetCount) {
                val name = try { eq.getPresetName(i.toShort()) } catch (e: Exception) { null } ?: continue
                if (name.isBlank()) continue
                // Read the preset's actual band curve so it can be forked into a custom preset.
                val levels = try {
                    eq.usePreset(i.toShort())
                    ShortArray(bands) { b -> eq.getBandLevel(b.toShort()) }
                } catch (e: Exception) {
                    ShortArray(bands)
                }
                presets.add(EqualizerPreset.device(i.toShort(), name, levels))
            }
            // Neutralise the temporary preset cycling; attach applies the real active preset next.
            for (b in 0 until bands) try { eq.setBandLevel(b.toShort(), 0) } catch (e: Exception) { }
            Preferences.setEqualizerCapabilitiesJson(
                Gson().toJson(EqualizerCapabilities(bands, range, centerFreqs, presets))
            )
        } catch (e: Exception) {
            // leave any previously cached capabilities in place
        }
    }

    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
    }

    fun release() {
        equalizer?.release()
        equalizer = null
    }

    companion object {
        /**
         * Reads equalizer capabilities without a playing session, for the settings editor.
         * Uses a temporary, disabled global-output equalizer purely as a computation engine:
         * it never outputs audio, but it can report the band layout and compute the curve of
         * each device preset. Returns null when the device has no usable equalizer.
         */
        fun queryCapabilities(): EqualizerCapabilities? {
            // Prefer capabilities cached from a live playback session: querying a global
            // session-0 equalizer is unreliable and fails on some devices (e.g. Pixel).
            Preferences.getEqualizerCapabilitiesJson()?.let { json ->
                try {
                    val cached = Gson().fromJson(json, EqualizerCapabilities::class.java)
                    if (cached != null && cached.bands > 0) return cached
                } catch (e: Exception) { /* fall through to a live query */ }
            }

            var eq: Equalizer? = null
            try {
                eq = Equalizer(0, 0).apply { enabled = false }
                val bands = eq.numberOfBands.toInt()
                if (bands == 0) return null
                val range = eq.bandLevelRange ?: shortArrayOf(-1500, 1500)
                val centerFreqs = IntArray(bands) { i -> eq.getCenterFreq(i.toShort()) / 1000 }

                val presetCount = eq.numberOfPresets.toInt()
                val presets = ArrayList<EqualizerPreset>(presetCount)
                for (i in 0 until presetCount) {
                    val name = try { eq.getPresetName(i.toShort()) } catch (e: Exception) { null }
                    if (!name.isNullOrBlank()) presets.add(EqualizerPreset.device(i.toShort(), name))
                }
                val caps = EqualizerCapabilities(bands, range, centerFreqs, presets)
                Preferences.setEqualizerCapabilitiesJson(Gson().toJson(caps))
                return caps
            } catch (e: Exception) {
                return null
            } finally {
                try { eq?.release() } catch (e: Exception) { /* ignore */ }
            }
        }

        /** Resolves the persisted active preset id into a preset, or null when none/invalid. */
        fun resolveActivePreset(): EqualizerPreset? {
            val id = Preferences.getActiveEqualizerPresetId() ?: return null
            return when {
                id.startsWith(EqualizerPreset.CUSTOM_PREFIX) -> {
                    val name = id.removePrefix(EqualizerPreset.CUSTOM_PREFIX)
                    Preferences.getCustomEqualizerPresets().find { it.name == name }
                }
                id.startsWith(EqualizerPreset.DEVICE_PREFIX) ->
                    id.removePrefix(EqualizerPreset.DEVICE_PREFIX).toShortOrNull()
                        ?.let { EqualizerPreset.device(it, "") }
                else -> null
            }
        }

        /** All presets available for selection: the device presets plus the user's custom ones. */
        fun allPresets(): List<EqualizerPreset> {
            val caps = queryCapabilities()
            val device = caps?.devicePresets ?: emptyList()
            return device + Preferences.getCustomEqualizerPresets()
        }

        /**
         * Chooses a preset for a track's genre: the first preset whose name matches the track's
         * first genre value (case-insensitive), else the configured default preset, else null.
         */
        fun presetForGenre(genre: String?): EqualizerPreset? {
            val presets = allPresets()
            val firstGenre = genre?.split(';', '/', ',')?.firstOrNull()?.trim()
            if (!firstGenre.isNullOrEmpty()) {
                presets.firstOrNull { it.name.equals(firstGenre, ignoreCase = true) }?.let { return it }
            }
            val defaultId = Preferences.getDefaultEqualizerPresetId() ?: return null
            return presets.firstOrNull { it.id() == defaultId }
        }

        /** Computes the band-level curve of a device preset, so it can be shown or saved as a custom preset. */
        fun computeDevicePresetLevels(presetIndex: Short, bands: Int): ShortArray {
            var eq: Equalizer? = null
            return try {
                eq = Equalizer(0, 0).apply { enabled = false }
                eq.usePreset(presetIndex)
                ShortArray(bands) { i -> eq.getBandLevel(i.toShort()) }
            } catch (e: Exception) {
                ShortArray(bands)
            } finally {
                try { eq?.release() } catch (e: Exception) { /* ignore */ }
            }
        }
    }
}

/** Static equalizer capabilities used to build the settings editor without a playing session. */
data class EqualizerCapabilities(
    val bands: Int,
    val levelRange: ShortArray,
    val centerFreqs: IntArray,
    val devicePresets: List<EqualizerPreset>,
)
