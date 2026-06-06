package com.cappielloantonio.tempo.ui.adapter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.databinding.ItemHorizontalPlaylistFolderBinding
import com.cappielloantonio.tempo.interfaces.ClickCallback
import com.cappielloantonio.tempo.model.PlaylistFolder
import com.cappielloantonio.tempo.util.Constants

class PlaylistFolderAdapter(
    private val click: ClickCallback
) : RecyclerView.Adapter<PlaylistFolderAdapter.ViewHolder>() {

    private val folders = mutableListOf<PlaylistFolder>()
    private val subtitles = mutableMapOf<Long, String>()

    fun setItems(items: List<PlaylistFolder>) {
        folders.clear()
        folders.addAll(items)
        notifyDataSetChanged()
    }

    fun setSubtitle(folderId: Long, subtitle: String) {
        subtitles[folderId] = subtitle
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHorizontalPlaylistFolderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]
        holder.binding.folderNameTextView.text = folder.name
        holder.binding.folderSubtitleTextView.text = subtitles[folder.id]
            ?: holder.itemView.context.getString(R.string.playlist_folder_subtitle_empty)
        holder.binding.folderMoreButton.tag = "folder_${folder.id}"
    }

    override fun getItemCount(): Int = folders.size

    inner class ViewHolder(val binding: ItemHorizontalPlaylistFolderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.folderNameTextView.isSelected = true

            itemView.setOnClickListener {
                val bundle = Bundle().apply {
                    putLong(Constants.PLAYLIST_FOLDER_ID, folders[bindingAdapterPosition].id)
                    putString(Constants.PLAYLIST_FOLDER_OBJECT, folders[bindingAdapterPosition].name)
                }
                click.onPlaylistFolderClick(bundle)
            }

            itemView.setOnLongClickListener {
                emitLongClick()
                true
            }

            binding.folderMoreButton.setOnClickListener { emitLongClick() }
        }

        private fun emitLongClick() {
            val bundle = Bundle().apply {
                putLong(Constants.PLAYLIST_FOLDER_ID, folders[bindingAdapterPosition].id)
                putString(Constants.PLAYLIST_FOLDER_OBJECT, folders[bindingAdapterPosition].name)
            }
            click.onPlaylistFolderLongClick(bundle)
        }
    }
}
