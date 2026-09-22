package com.cappielloantonio.tempo.ui.dialog

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.model.EqualizerPreset
import com.cappielloantonio.tempo.service.BaseMediaService
import com.cappielloantonio.tempo.service.EqualizerManager
import com.cappielloantonio.tempo.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Lightweight preset switcher shown from the player. Picking a preset applies it to the
 * currently playing audio immediately; the full editor lives in Settings.
 */
class EqualizerPresetPickerDialog : DialogFragment() {

    fun interface OnEditPresetsListener {
        fun onEditPresets()
    }

    var editListener: OnEditPresetsListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val capabilities = EqualizerManager.queryCapabilities()
        val presets: List<EqualizerPreset> =
            if (capabilities != null) capabilities.devicePresets + Preferences.getCustomEqualizerPresets()
            else emptyList()

        // List: [Off, Auto (by genre), <presets...>]
        val labels = ArrayList<String>()
        labels.add(getString(R.string.equalizer_preset_off))
        labels.add(getString(R.string.equalizer_preset_auto))
        presets.forEach { labels.add(it.name) }

        val selected = when {
            !Preferences.isEqualizerEnabled() -> 0
            Preferences.isEqualizerAutoByGenre() -> 1
            else -> {
                val idx = presets.indexOfFirst { it.id() == Preferences.getActiveEqualizerPresetId() }
                if (idx >= 0) idx + 2 else 0
            }
        }

        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.equalizer_preset_dialog_title)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                when (which) {
                    0 -> Preferences.setEqualizerEnabled(false)
                    1 -> {
                        Preferences.setEqualizerEnabled(true)
                        Preferences.setEqualizerAutoByGenre(true)
                    }
                    else -> {
                        Preferences.setEqualizerEnabled(true)
                        Preferences.setEqualizerAutoByGenre(false)
                        Preferences.setActiveEqualizerPresetId(presets[which - 2].id())
                    }
                }
                notifyService()
                dialog.dismiss()
            }
            .setNeutralButton(R.string.equalizer_preset_dialog_edit) { _, _ -> editListener?.onEditPresets() }
            .setNegativeButton(R.string.playback_speed_dialog_negative_button) { dialog, _ -> dialog.cancel() }
            .create()
    }

    private fun notifyService() {
        val intent = Intent(BaseMediaService.ACTION_APPLY_EQUALIZER).setPackage(requireContext().packageName)
        requireContext().sendBroadcast(intent)
    }
}
