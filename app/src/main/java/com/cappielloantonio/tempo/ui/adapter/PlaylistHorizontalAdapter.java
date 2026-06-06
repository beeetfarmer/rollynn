package com.cappielloantonio.tempo.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.DownloadDao;
import com.cappielloantonio.tempo.databinding.ItemHorizontalPlaylistBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlaylistHorizontalAdapter extends RecyclerView.Adapter<PlaylistHorizontalAdapter.ViewHolder> implements Filterable {
    private final ClickCallback click;

    private List<Playlist> playlists;
    private List<Playlist> playlistsFull;
    private Set<String> downloadedPlaylistIds = new HashSet<>();

    private final Filter filtering = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Playlist> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                synchronized (playlistsFull) {
                    filteredList.addAll(playlistsFull);
                }
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();

                synchronized (playlistsFull) {
                    for (Playlist item : playlistsFull) {
                        if (item.getName() != null && item.getName().toLowerCase().contains(filterPattern)) {
                            filteredList.add(item);
                        }
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = filteredList;
            results.count = filteredList.size();

            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            playlists.clear();
            if (results.count > 0) playlists.addAll((List<Playlist>) results.values);
            notifyDataSetChanged();
        }
    };

    public PlaylistHorizontalAdapter(ClickCallback click) {
        this.click = click;
        this.playlists = new ArrayList<>();
        this.playlistsFull = new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHorizontalPlaylistBinding view = ItemHorizontalPlaylistBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);

        holder.item.playlistMoreButton.setTag(playlist.getId());
        holder.item.playlistTitleTextView.setText(playlist.getName());
        holder.item.playlistSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.playlist_counted_tracks, playlist.getSongCount(), MusicUtil.getReadableDurationString(playlist.getDuration(), false)));

        holder.item.playlistPinnedIcon.setVisibility(playlist.isPinned() ? android.view.View.VISIBLE : android.view.View.GONE);
        holder.item.playlistDownloadIcon.setVisibility(downloadedPlaylistIds.contains(playlist.getId()) ? android.view.View.VISIBLE : android.view.View.GONE);

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), playlist.getCoverArtId(), CustomGlideRequest.ResourceType.Playlist)
                .build()
                .into(holder.item.playlistCoverImageView);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    public Playlist getItem(int id) {
        return playlists.get(id);
    }

    @androidx.media3.common.util.UnstableApi
    public void setItems(List<Playlist> playlists) {
        if (playlists == null) return;
        synchronized (playlistsFull) {
            this.playlistsFull.clear();
            this.playlistsFull.addAll(playlists);
        }
        this.playlists.clear();
        this.playlists.addAll(this.playlistsFull);
        refreshDownloadedIds();
        sort(null);
        notifyDataSetChanged();
    }

    @androidx.media3.common.util.UnstableApi
    private void refreshDownloadedIds() {
        new Thread(() -> {
            DownloadDao downloadDao = AppDatabase.getInstance().downloadDao();
            List<String> ids = downloadDao.getDownloadedPlaylistIds();
            downloadedPlaylistIds = new HashSet<>(ids);
        }).start();
    }

    public void setPinnedIds(List<String> pinnedIds) {
        if (pinnedIds == null) return;
        synchronized (playlistsFull) {
            for (Playlist playlist : playlistsFull) {
                playlist.setPinned(pinnedIds.contains(playlist.getId()));
            }
        }
        this.playlists.clear();
        this.playlists.addAll(this.playlistsFull);
        sort(null);
        notifyDataSetChanged();
    }

    @Override
    public Filter getFilter() {
        return filtering;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHorizontalPlaylistBinding item;

        ViewHolder(ItemHorizontalPlaylistBinding item) {
            super(item.getRoot());

            this.item = item;
            item.playlistTitleTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());

            item.playlistMoreButton.setOnClickListener(v -> onLongClick());
        }

        public void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.PLAYLIST_OBJECT, playlists.get(getBindingAdapterPosition()));

            click.onPlaylistClick(bundle);
        }

        public boolean onLongClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.PLAYLIST_OBJECT, playlists.get(getBindingAdapterPosition()));

            click.onPlaylistLongClick(bundle);

            return true;
        }
    }

    public void sort(String order) {
        Comparator<Playlist> comparator = (p1, p2) -> Boolean.compare(p2.isPinned(), p1.isPinned());

        if (order != null) {
            switch (order) {
                case Constants.PLAYLIST_ORDER_BY_NAME:
                    comparator = comparator.thenComparing(Playlist::getName);
                    break;
                case Constants.PLAYLIST_ORDER_BY_RANDOM:
                    Collections.shuffle(playlists);
                    return;
            }
        } else {
            comparator = comparator.thenComparing(Playlist::getName);
        }

        playlists.sort(comparator);
        notifyDataSetChanged();
    }
}
