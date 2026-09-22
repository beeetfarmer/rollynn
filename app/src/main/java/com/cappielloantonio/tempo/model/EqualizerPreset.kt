package com.cappielloantonio.tempo.model

import androidx.annotation.Keep

/**
 * An equalizer preset the user can switch between. It is either a built-in
 * device preset (identified by its index in the Android [android.media.audiofx.Equalizer]),
 * or a custom preset carrying a saved band-level curve in millibels.
 */
@Keep
data class EqualizerPreset(
    val name: String,
    val devicePresetIndex: Short? = null,
    // For a custom preset this is its curve; for a device preset it is the cached curve of that
    // built-in preset, kept only so it can be forked into an editable custom preset.
    val bandLevels: ShortArray? = null,
) {
    val isCustom: Boolean get() = devicePresetIndex == null && bandLevels != null

    /** Stable id used to remember which preset is active across sessions. */
    fun id(): String = if (devicePresetIndex != null) "$DEVICE_PREFIX$devicePresetIndex" else "$CUSTOM_PREFIX$name"

    companion object {
        const val DEVICE_PREFIX = "device:"
        const val CUSTOM_PREFIX = "custom:"

        fun device(index: Short, name: String, levels: ShortArray? = null) =
            EqualizerPreset(name = name, devicePresetIndex = index, bandLevels = levels)

        fun custom(name: String, levels: ShortArray) = EqualizerPreset(name = name, bandLevels = levels)
    }
}
