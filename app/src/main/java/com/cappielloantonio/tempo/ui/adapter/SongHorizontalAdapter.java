package com.cappielloantonio.tempo.ui.adapter;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.ItemHorizontalTrackBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.service.DownloaderManager;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.DiscTitle;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.ExternalAudioReader;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@UnstableApi
public class SongHorizontalAdapter extends RecyclerView.Adapter<SongHorizontalAdapter.ViewHolder> implements Filterable {
    private final ClickCallback click;
    private final boolean showCoverArt;
    private final boolean showAlbum;
    private final AlbumID3 album;

    private List<Child> songsFull;
    private List<Child> songs;
    private String currentFilter;

    private String currentPlayingId;
    private boolean isPlaying;
    private List<Integer> currentPlayingPositions = Collections.emptyList();
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private Drawable starDrawable;
    private Drawable starOutlinedDrawable;
    private DownloaderManager downloadTracker;
    private Map<String, Float> downloadProgressMap = new HashMap<>();

    private final Filter filtering = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Child> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(songsFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                currentFilter = filterPattern;

                for (Child item : songsFull) {
                    if (item.getTitle().toLowerCase().contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = filteredList;

            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            List<Child> newSongs = (List<Child>) results.values;
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new SongDiffCallback(songs, newSongs));
            songs = newSongs;
            diffResult.dispatchUpdatesTo(SongHorizontalAdapter.this);

            for (int pos : currentPlayingPositions) {
                if (pos >= 0 && pos < songs.size()) {
                    notifyItemChanged(pos, "payload_playback");
                }
            }
        }
    };

    public SongHorizontalAdapter(LifecycleOwner lifecycleOwner, ClickCallback click, boolean showCoverArt, boolean showAlbum, AlbumID3 album) {
        this.click = click;
        this.showCoverArt = showCoverArt;
        this.showAlbum = showAlbum;
        this.songs = Collections.emptyList();
        this.songsFull = Collections.emptyList();
        this.currentFilter = "";
        this.album = album;
        setHasStableIds(false);

        if (lifecycleOwner != null) {
            MappingUtil.observeExternalAudioRefresh(lifecycleOwner, this::handleExternalAudioRefresh);

            MediaManager.getFavoriteEvent().observe(lifecycleOwner, event -> {
                if (event == null) return;
                String songId = (String) event[0];
                Date starred = (Date) event[1];
                updateFavoriteInList(songId, starred);
            });

            MediaManager.getRatingEvent().observe(lifecycleOwner, event -> {
                if (event == null) return;
                String songId = (String) event[0];
                int rating = (Integer) event[1];
                updateRatingInList(songId, rating);
            });
        }
    }

    private void updateFavoriteInList(String songId, Date starred) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).getId().equals(songId)) {
                songs.get(i).setStarred(starred);
                notifyItemChanged(i);
            }
        }
        for (Child c : songsFull) {
            if (c.getId().equals(songId)) {
                c.setStarred(starred);
            }
        }
    }

    private void updateRatingInList(String songId, int rating) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).getId().equals(songId)) {
                songs.get(i).setUserRating(rating);
                notifyItemChanged(i);
            }
        }
        for (Child c : songsFull) {
            if (c.getId().equals(songId)) {
                c.setUserRating(rating);
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (starDrawable == null) {
            starDrawable = AppCompatResources.getDrawable(parent.getContext(), R.drawable.ic_star);
            starOutlinedDrawable = AppCompatResources.getDrawable(parent.getContext(), R.drawable.ic_star_outlined);
            downloadTracker = DownloadUtil.getDownloadTracker(parent.getContext());
        }
        ItemHorizontalTrackBinding view = ItemHorizontalTrackBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains("payload_playback")) {
            bindPlaybackState(holder, songs.get(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Child song = songs.get(position);

        holder.item.searchResultSongTitleTextView.setText(song.getTitle());

        holder.item.searchResultSongSubtitleTextView.setText(
                holder.itemView.getContext().getString(
                        R.string.song_subtitle_formatter,
                        this.showAlbum ?
                                song.getAlbum() :
                                song.getArtist(),
                        MusicUtil.getReadableDurationString(song.getDuration(), false),
                        MusicUtil.getReadableAudioQualityString(song)
                )
        );

        holder.item.trackNumberTextView.setText(MusicUtil.getReadableTrackNumber(holder.itemView.getContext(), song.getTrack()));

        Float progress = downloadProgressMap.get(song.getId());
        if (progress != null && progress < 1.0f) {
            holder.item.downloadProgressIndicator.setVisibility(View.VISIBLE);
            holder.item.downloadProgressIndicator.setMax(100);
            holder.item.downloadProgressIndicator.setProgress((int) (progress * 100));
            holder.item.searchResultDownloadIndicatorImageView.setVisibility(View.GONE);
        } else {
            holder.item.downloadProgressIndicator.setVisibility(View.GONE);
            if (Preferences.getDownloadDirectoryUri() == null) {
                if (downloadTracker != null && downloadTracker.isDownloaded(song.getId())) {
                    holder.item.searchResultDownloadIndicatorImageView.setVisibility(View.VISIBLE);
                } else {
                    holder.item.searchResultDownloadIndicatorImageView.setVisibility(View.GONE);
                }
            } else {
                if (ExternalAudioReader.getUri(song) != null) {
                    holder.item.searchResultDownloadIndicatorImageView.setVisibility(View.VISIBLE);
                } else {
                    holder.item.searchResultDownloadIndicatorImageView.setVisibility(View.GONE);
                }
            }
        }

        if (showCoverArt) CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                .build()
                .into(holder.item.songCoverImageView);

        holder.item.trackNumberTextView.setVisibility(showCoverArt ? View.INVISIBLE : View.VISIBLE);
        holder.item.songCoverImageView.setVisibility(showCoverArt ? View.VISIBLE : View.INVISIBLE);

        if (!showCoverArt &&
                (position == 0 ||
                        (position > 0 && songs.get(position - 1) != null &&
                                songs.get(position - 1).getDiscNumber() != null &&
                                songs.get(position).getDiscNumber() != null &&
                                songs.get(position - 1).getDiscNumber() < songs.get(position).getDiscNumber()
                        )
                )
        ) {

            if (songs.get(position).getDiscNumber() != null && !Objects.requireNonNull(songs.get(position).getDiscNumber()).toString().isBlank()) {
                holder.item.discTitleTextView.setText(holder.itemView.getContext().getString(R.string.disc_titleless, songs.get(position).getDiscNumber().toString()));
                holder.item.differentDiskDividerSector.setVisibility(View.VISIBLE);
            } else {
                holder.item.differentDiskDividerSector.setVisibility(View.GONE);
            }

            if (album.getDiscTitles() != null) {
                Optional<DiscTitle> discTitle = album.getDiscTitles().stream().filter(title -> Objects.equals(title.getDisc(), songs.get(position).getDiscNumber())).findFirst();

                if (discTitle.isPresent() && discTitle.get().getDisc() != null && discTitle.get().getTitle() != null && !discTitle.get().getTitle().isEmpty()) {
                    holder.item.discTitleTextView.setText(holder.itemView.getContext().getString(R.string.disc_titlefull, discTitle.get().getDisc().toString() , discTitle.get().getTitle()));
                }
            }
        }

        if (song.getStarred() == null && (song.getUserRating() == null || song.getUserRating() == 0)) {
            holder.item.ratingIndicatorImageView.setVisibility(View.GONE);
        } else {
            holder.item.ratingIndicatorImageView.setVisibility(View.VISIBLE);
            holder.item.preferredIcon.setVisibility(song.getStarred() != null ? View.VISIBLE : View.GONE);
            holder.item.ratingBarLayout.setVisibility(song.getUserRating() != null && song.getUserRating() > 0 ? View.VISIBLE : View.GONE);

            if (song.getUserRating() != null && song.getUserRating() > 0) {
                int rating = song.getUserRating();
                holder.item.oneStarIcon.setImageDrawable(rating >= 1 ? starDrawable : starOutlinedDrawable);
                holder.item.twoStarIcon.setImageDrawable(rating >= 2 ? starDrawable : starOutlinedDrawable);
                holder.item.threeStarIcon.setImageDrawable(rating >= 3 ? starDrawable : starOutlinedDrawable);
                holder.item.fourStarIcon.setImageDrawable(rating >= 4 ? starDrawable : starOutlinedDrawable);
                holder.item.fiveStarIcon.setImageDrawable(rating >= 5 ? starDrawable : starOutlinedDrawable);
            }
        }

        bindPlaybackState(holder, song);
    }

    private void handleExternalAudioRefresh() {
        if (Preferences.getDownloadDirectoryUri() != null) {
            notifyDataSetChanged();
        }
    }

    private void bindPlaybackState(@NonNull ViewHolder holder, @NonNull Child song) {
        boolean isCurrent = currentPlayingId != null && currentPlayingId.equals(song.getId());

        if (isCurrent) {
            holder.item.playPauseIcon.setVisibility(View.VISIBLE);
            if (isPlaying) {
                holder.item.playPauseIcon.setImageResource(R.drawable.ic_pause);
            } else {
                holder.item.playPauseIcon.setImageResource(R.drawable.ic_play);
            }
            if (!showCoverArt) {
                holder.item.trackNumberTextView.setVisibility(View.INVISIBLE);
            } else {
                holder.item.coverArtOverlay.setVisibility(View.VISIBLE);
            }
        } else {
            holder.item.playPauseIcon.setVisibility(View.INVISIBLE);
            if (!showCoverArt) {
                holder.item.trackNumberTextView.setVisibility(View.VISIBLE);
            } else {
                holder.item.coverArtOverlay.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    public void setItems(List<Child> songs) {
        this.songsFull = songs != null ? songs : Collections.emptyList();
        filtering.filter(currentFilter);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void setPlaybackState(String mediaId, boolean playing) {
        String oldId = this.currentPlayingId;
        boolean oldPlaying = this.isPlaying;
        List<Integer> oldPositions = currentPlayingPositions;

        this.currentPlayingId = mediaId;
        this.isPlaying = playing;

        if (Objects.equals(oldId, mediaId) && oldPlaying == playing) {
            List<Integer> newPositionsCheck = mediaId != null ? findPositionsById(mediaId) : Collections.emptyList();
            if (oldPositions.equals(newPositionsCheck)) {
                return;
            }
        }

        currentPlayingPositions = mediaId != null ? findPositionsById(mediaId) : Collections.emptyList();

        for (int pos : oldPositions) {
            if (pos >= 0 && pos < songs.size()) {
                notifyItemChanged(pos, "payload_playback");
            }
        }
        for (int pos : currentPlayingPositions) {
            if (!oldPositions.contains(pos) && pos >= 0 && pos < songs.size()) {
                notifyItemChanged(pos, "payload_playback");
            }
        }
    }

    private List<Integer> findPositionsById(String id) {
        if (id == null) return Collections.emptyList();
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < songs.size(); i++) {
            if (id.equals(songs.get(i).getId())) {
                positions.add(i);
            }
        }
        return positions;
    }

    @Override
    public Filter getFilter() {
        return filtering;
    }

    public Child getItem(int id) {
        return songs.get(id);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHorizontalTrackBinding item;

        ViewHolder(ItemHorizontalTrackBinding item) {
            super(item.getRoot());

            this.item = item;

            item.searchResultSongTitleTextView.setSelected(true);
            item.searchResultSongSubtitleTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());

            item.searchResultSongMoreButton.setOnClickListener(v -> onLongClick());
        }

        public void onClick() {
            int pos = getBindingAdapterPosition();
            Child tappedSong = songs.get(pos);

            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(MusicUtil.limitPlayableMedia(songs, getBindingAdapterPosition())));
            bundle.putInt(Constants.ITEM_POSITION, MusicUtil.getPlayableMediaPosition(songs, getBindingAdapterPosition()));

            if (tappedSong.getId().equals(currentPlayingId)) {
                try {
                    MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                    if (isPlaying) {
                        mediaBrowser.pause();
                    } else {
                        mediaBrowser.play();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                click.onMediaClick(bundle);
            }
        }

        private boolean onLongClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.TRACK_OBJECT, songs.get(getBindingAdapterPosition()));
            bundle.putInt(Constants.ITEM_POSITION, getBindingAdapterPosition());

            click.onMediaLongClick(bundle);

            return true;
        }
    }

    public void sort(String order) {
        List<Child> sorted = new ArrayList<>(songs);
        switch (order) {
            case Constants.MEDIA_BY_TITLE:
                sorted.sort(Comparator.comparing(Child::getTitle, String.CASE_INSENSITIVE_ORDER));
                break;
            case Constants.MEDIA_BY_ARTIST:
                sorted.sort(Comparator.comparing(
                        song -> song.getArtist() != null ? song.getArtist().split("[,/;&\u2022]")[0].trim() : "",
                        String.CASE_INSENSITIVE_ORDER
                ));
                break;
            case Constants.MEDIA_DEFAULT_ORDER:
                sorted = new ArrayList<>(songsFull);
                break;
            case Constants.MEDIA_MOST_RECENTLY_STARRED:
                sorted.sort(Comparator.comparing(Child::getStarred, Comparator.nullsLast(Comparator.reverseOrder())));
                break;
            case Constants.MEDIA_LEAST_RECENTLY_STARRED:
                sorted.sort(Comparator.comparing(Child::getStarred, Comparator.nullsLast(Comparator.naturalOrder())));
                break;
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new SongDiffCallback(songs, sorted));
        songs = sorted;
        diffResult.dispatchUpdatesTo(this);
    }

    public void setMediaBrowserListenableFuture(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        this.mediaBrowserListenableFuture = mediaBrowserListenableFuture;
    }

    public void updateDownloadProgress(Map<String, Float> progressMap) {
        this.downloadProgressMap = progressMap != null ? progressMap : new HashMap<>();
        notifyDataSetChanged();
    }

    private static class SongDiffCallback extends DiffUtil.Callback {
        private final List<Child> oldList;
        private final List<Child> newList;

        SongDiffCallback(List<Child> oldList, List<Child> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() { return oldList.size(); }

        @Override
        public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            return oldList.get(oldPos).getId().equals(newList.get(newPos).getId());
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            Child o = oldList.get(oldPos);
            Child n = newList.get(newPos);
            return Objects.equals(o.getStarred(), n.getStarred())
                    && Objects.equals(o.getUserRating(), n.getUserRating())
                    && Objects.equals(o.getTitle(), n.getTitle())
                    && Objects.equals(o.getArtist(), n.getArtist())
                    && Objects.equals(o.getAlbum(), n.getAlbum());
        }
    }
}
