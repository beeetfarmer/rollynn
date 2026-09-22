package com.cappielloantonio.tempo.ui.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.model.EqualizerPreset
import com.cappielloantonio.tempo.service.BaseMediaService
import com.cappielloantonio.tempo.service.EqualizerCapabilities
import com.cappielloantonio.tempo.service.EqualizerManager
import com.cappielloantonio.tempo.util.Preferences

class EqualizerFragment : Fragment() {

    private var capabilities: EqualizerCapabilities? = null

    private lateinit var eqSwitch: Switch
    private lateinit var defaultPresetRow: View
    private lateinit var defaultPresetValue: TextView
    private lateinit var presetSection: View
    private lateinit var presetListContainer: LinearLayout
    private lateinit var createPresetButton: Button
    private lateinit var editorContainer: View
    private lateinit var nameInput: EditText
    private lateinit var bandsContainer: LinearLayout
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    // The preset currently being edited (null while creating a new one), and its in-progress band levels.
    private var editingPreset: EqualizerPreset? = null
    private var editorLevels: ShortArray = ShortArray(0)

    /** Tells the media service to re-apply the persisted equalizer state to the live audio session. */
    private fun notifyService() {
        val intent = Intent(BaseMediaService.ACTION_APPLY_EQUALIZER).setPackage(requireContext().packageName)
        requireContext().sendBroadcast(intent)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_equalizer, container, false)

    override fun onResume() {
        super.onResume()
        // The player picker can change the enable flag / active preset while this screen sits in the
        // back stack, so re-sync from preferences whenever it returns to the foreground.
        if (capabilities == null || editorContainer.visibility == View.VISIBLE) return
        if (eqSwitch.isChecked != Preferences.isEqualizerEnabled()) {
            eqSwitch.isChecked = Preferences.isEqualizerEnabled()
        }
        renderPresetList()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        eqSwitch = view.findViewById(R.id.equalizer_switch)
        defaultPresetRow = view.findViewById(R.id.eq_default_preset_row)
        defaultPresetValue = view.findViewById(R.id.eq_default_preset_value)
        presetSection = view.findViewById(R.id.eq_preset_section)
        presetListContainer = view.findViewById(R.id.eq_preset_list_container)
        createPresetButton = view.findViewById(R.id.eq_create_preset_button)
        editorContainer = view.findViewById(R.id.eq_editor_container)
        nameInput = view.findViewById(R.id.eq_preset_name_input)
        bandsContainer = view.findViewById(R.id.eq_bands_container)
        saveButton = view.findViewById(R.id.eq_editor_save)
        cancelButton = view.findViewById(R.id.eq_editor_cancel)

        capabilities = EqualizerManager.queryCapabilities()
        if (capabilities == null) {
            view.findViewById<View>(R.id.equalizer_switch_row).visibility = View.GONE
            presetSection.visibility = View.GONE
            view.findViewById<View>(R.id.equalizer_not_supported_container).visibility = View.VISIBLE
            return
        }

        eqSwitch.isChecked = Preferences.isEqualizerEnabled()
        eqSwitch.setOnCheckedChangeListener { _, isChecked ->
            Preferences.setEqualizerEnabled(isChecked)
            notifyService()
        }

        updateDefaultPresetRow()
        defaultPresetRow.setOnClickListener { showDefaultPresetChooser() }

        createPresetButton.setOnClickListener { openEditor(null) }
        cancelButton.setOnClickListener { closeEditor() }
        saveButton.setOnClickListener { saveEditedPreset() }

        renderPresetList()
    }

    private fun allPresets(): List<EqualizerPreset> {
        val caps = capabilities ?: return emptyList()
        return caps.devicePresets + Preferences.getCustomEqualizerPresets()
    }

    private fun updateDefaultPresetRow() {
        defaultPresetRow.visibility = if (Preferences.isEqualizerAutoByGenre()) View.VISIBLE else View.GONE
        val defaultId = Preferences.getDefaultEqualizerPresetId()
        val name = allPresets().firstOrNull { it.id() == defaultId }?.name
        defaultPresetValue.text = name ?: getString(R.string.equalizer_default_preset_none)
    }

    private fun showDefaultPresetChooser() {
        val presets = allPresets()
        if (presets.isEmpty()) return
        val names = presets.map { it.name }.toTypedArray()
        val currentId = Preferences.getDefaultEqualizerPresetId()
        val checked = presets.indexOfFirst { it.id() == currentId }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.equalizer_default_preset_title)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                Preferences.setDefaultEqualizerPresetId(presets[which].id())
                updateDefaultPresetRow()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.equalizer_editor_cancel) { dialog, _ -> dialog.cancel() }
            .show()
    }

    private fun renderPresetList() {
        presetListContainer.removeAllViews()
        val autoActive = Preferences.isEqualizerAutoByGenre()
        val activeId = Preferences.getActiveEqualizerPresetId()
        updateDefaultPresetRow()

        // "Auto (by genre)" is the first selectable item: choosing it picks the preset per track.
        presetListContainer.addView(
            presetRow(getString(R.string.equalizer_preset_auto), autoActive, null) { selectAuto() }
        )

        for (preset in allPresets()) {
            val isActive = !autoActive && preset.id() == activeId
            presetListContainer.addView(presetRow(preset.name, isActive, preset) { selectPreset(preset) })
        }
    }

    private fun presetRow(name: String, isActive: Boolean, preset: EqualizerPreset?, onSelect: () -> Unit): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12.dpToPx(context), 0, 12.dpToPx(context))
            isClickable = true
            setOnClickListener { onSelect() }
        }
        row.addView(TextView(requireContext(), null, 0, R.style.LabelMedium).apply {
            text = if (isActive) "●  " else "○  "
        })
        row.addView(TextView(requireContext(), null, 0, R.style.LabelMedium).apply {
            text = name
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        // Presets can be edited (editing a device preset forks it into a custom one); only custom ones deleted.
        if (preset != null) {
            row.addView(iconButton(R.drawable.ic_settings) { openEditor(preset) })
            if (preset.isCustom) row.addView(iconButton(R.drawable.ic_close) { deletePreset(preset) })
        }
        return row
    }

    private fun iconButton(iconRes: Int, onClick: () -> Unit): ImageButton =
        ImageButton(requireContext()).apply {
            setImageResource(iconRes)
            background = null
            setPadding(16.dpToPx(context), 0, 16.dpToPx(context), 0)
            setOnClickListener { onClick() }
        }

    private fun ensureEnabled() {
        if (!Preferences.isEqualizerEnabled()) {
            Preferences.setEqualizerEnabled(true)
            eqSwitch.isChecked = true
        }
    }

    private fun selectAuto() {
        ensureEnabled()
        Preferences.setEqualizerAutoByGenre(true)
        notifyService()
        renderPresetList()
    }

    private fun selectPreset(preset: EqualizerPreset) {
        // Choosing a specific preset implies manual mode with the equalizer on.
        ensureEnabled()
        Preferences.setEqualizerAutoByGenre(false)
        Preferences.setActiveEqualizerPresetId(preset.id())
        notifyService()
        renderPresetList()
    }

    private fun deletePreset(preset: EqualizerPreset) {
        val remaining = Preferences.getCustomEqualizerPresets().filterNot { it.name == preset.name }
        Preferences.setCustomEqualizerPresets(remaining)
        if (Preferences.getActiveEqualizerPresetId() == preset.id()) {
            Preferences.setActiveEqualizerPresetId(null)
        }
        renderPresetList()
    }

    // Editing a custom preset edits it in place; editing a device preset (or creating one) starts
    // a new custom preset seeded from that preset's curve, or from whatever is active, else flat.
    private fun openEditor(preset: EqualizerPreset?) {
        val caps = capabilities ?: return
        editingPreset = if (preset != null && preset.isCustom) preset else null
        editorLevels = when {
            preset?.bandLevels != null -> preset.bandLevels.copyOf(caps.bands)
            else -> currentCurve(caps.bands)
        }
        nameInput.setText(when {
            preset == null -> ""
            preset.isCustom -> preset.name
            else -> getString(R.string.equalizer_preset_fork_name, preset.name)
        })
        buildBandSliders(caps)
        presetSection.visibility = View.GONE
        editorContainer.visibility = View.VISIBLE
    }

    private fun closeEditor() {
        editorContainer.visibility = View.GONE
        presetSection.visibility = View.VISIBLE
        renderPresetList()
    }

    private fun saveEditedPreset() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), R.string.equalizer_preset_name_required, Toast.LENGTH_SHORT).show()
            return
        }
        val presets = Preferences.getCustomEqualizerPresets()
        // Replace a preset of the same name (or the one being renamed), otherwise append.
        val oldName = editingPreset?.name
        val filtered = presets.filterNot { it.name == name || it.name == oldName }.toMutableList()
        val saved = EqualizerPreset.custom(name, editorLevels.copyOf())
        filtered.add(saved)
        Preferences.setCustomEqualizerPresets(filtered)
        selectPreset(saved)
        closeEditor()
    }

    private fun currentCurve(bands: Int): ShortArray {
        val active = EqualizerManager.resolveActivePreset() ?: return ShortArray(bands)
        active.bandLevels?.let { return it.copyOf(bands) }
        active.devicePresetIndex?.let { return EqualizerManager.computeDevicePresetLevels(it, bands) }
        return ShortArray(bands)
    }

    private fun formatDb(value: Int): String = if (value > 0) "+$value dB" else "$value dB"

    private fun buildBandSliders(caps: EqualizerCapabilities) {
        bandsContainer.removeAllViews()
        val minLevelDb = caps.levelRange[0] / 100
        val maxLevelDb = caps.levelRange[1] / 100

        for (i in 0 until caps.bands) {
            val freq = caps.centerFreqs.getOrElse(i) { 0 }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 16.dpToPx(context)
                    bottomMargin = 16.dpToPx(context)
                }
                setPadding(0, 8, 0, 8)
            }

            val freqLabel = TextView(requireContext(), null, 0, R.style.LabelSmall).apply {
                text = if (freq >= 1000) {
                    if (freq % 1000 == 0) "${freq / 1000} kHz" else String.format("%.1f kHz", freq / 1000f)
                } else "$freq Hz"
                gravity = Gravity.START
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            }
            row.addView(freqLabel)

            val initialDb = editorLevels.getOrElse(i) { 0 } / 100
            val dbLabel = TextView(requireContext(), null, 0, R.style.LabelSmall).apply {
                text = formatDb(initialDb)
                setPadding(12, 0, 0, 0)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            }

            val seekBar = SeekBar(requireContext()).apply {
                max = maxLevelDb - minLevelDb
                progress = initialDb - minLevelDb
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 6f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        val levelDb = progress + minLevelDb
                        if (fromUser && i < editorLevels.size) editorLevels[i] = (levelDb * 100).toShort()
                        dbLabel.text = formatDb(levelDb)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {}
                })
            }
            row.addView(seekBar)
            row.addView(dbLabel)
            bandsContainer.addView(row)
        }
    }
}

private fun Int.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()
