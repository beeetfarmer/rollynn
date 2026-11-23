package com.cappielloantonio.tempo.ui.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.cappielloantonio.tempo.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WhatsNewDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(getString(R.string.whats_new_dialog_title, args.getString(ARG_VERSION)))
            .setMessage(args.getString(ARG_NOTES))
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    companion object {
        const val TAG = "WhatsNewDialog"
        private const val ARG_VERSION = "version"
        private const val ARG_NOTES = "notes"

        @JvmStatic
        fun newInstance(version: String, notes: String) = WhatsNewDialog().apply {
            arguments = bundleOf(ARG_VERSION to version, ARG_NOTES to notes)
        }
    }
}
