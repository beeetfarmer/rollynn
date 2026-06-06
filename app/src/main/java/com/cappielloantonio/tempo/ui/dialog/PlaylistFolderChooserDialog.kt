package com.cappielloantonio.tempo.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.model.PlaylistFolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PlaylistFolderChooserDialog(
    private val folders: List<PlaylistFolder>,
    private val currentFolderId: Long?,
    private val onSelect: Callback
) : DialogFragment() {

    fun interface Callback {
        fun onSelect(folderId: Long?)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_playlist_folder_chooser, null)

        val recyclerView = view.findViewById<RecyclerView>(R.id.folder_chooser_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val items = buildFlatTree(folders, null, 0)
        recyclerView.adapter = FolderTreeAdapter(items, currentFolderId) { selectedId ->
            onSelect.onSelect(selectedId)
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.playlist_move_to_folder)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private data class FolderItem(
        val id: Long?,
        val name: String,
        val depth: Int
    )

    private fun buildFlatTree(
        allFolders: List<PlaylistFolder>,
        parentId: Long?,
        depth: Int
    ): List<FolderItem> {
        val result = mutableListOf<FolderItem>()
        if (depth == 0) {
            result.add(FolderItem(null, getString(R.string.playlist_folder_root), 0))
        }
        val children = allFolders.filter { it.parentId == parentId }.sortedBy { it.name }
        for (child in children) {
            result.add(FolderItem(child.id, child.name, depth + 1))
            result.addAll(buildFlatTree(allFolders, child.id, depth + 1))
        }
        return result
    }

    private class FolderTreeAdapter(
        private val items: List<FolderItem>,
        private val currentFolderId: Long?,
        private val onSelect: (Long?) -> Unit
    ) : RecyclerView.Adapter<FolderTreeAdapter.ViewHolder>() {

        inner class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(parent.context).apply {
                val dp16 = (16 * resources.displayMetrics.density).toInt()
                val dp8 = (8 * resources.displayMetrics.density).toInt()
                setPadding(dp16, dp8, dp16, dp8)
                textSize = 16f
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                val attrs = intArrayOf(android.R.attr.selectableItemBackground)
                val ta = parent.context.obtainStyledAttributes(attrs)
                val bgRes = ta.getResourceId(0, 0)
                ta.recycle()
                setBackgroundResource(bgRes)
            }
            return ViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val indent = "    ".repeat(item.depth)
            holder.textView.text = "$indent${item.name}"

            val isCurrentFolder = item.id == currentFolderId
            holder.textView.alpha = if (isCurrentFolder) 0.5f else 1.0f

            holder.itemView.setOnClickListener {
                if (!isCurrentFolder) onSelect(item.id)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
