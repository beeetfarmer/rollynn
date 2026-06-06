package com.cappielloantonio.tempo.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import com.cappielloantonio.tempo.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PlaylistFolderEditorDialog(
    private val currentName: String? = null,
    private val onConfirm: Callback
) : DialogFragment() {

    fun interface Callback {
        fun onConfirm(name: String)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val isRename = currentName != null
        val title = if (isRename) R.string.playlist_folder_rename else R.string.playlist_folder_create

        val input = EditText(requireContext()).apply {
            hint = getString(R.string.playlist_folder_name_hint)
            if (isRename) setText(currentName)
            setSingleLine()
        }

        val container = FrameLayout(requireContext()).apply {
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) onConfirm.onConfirm(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }
}
