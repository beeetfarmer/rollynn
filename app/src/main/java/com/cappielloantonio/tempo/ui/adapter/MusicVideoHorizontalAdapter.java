package com.cappielloantonio.tempo.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.databinding.ItemHorizontalMusicVideoBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.popinn.PopinnVideo;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicVideoUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MusicVideoHorizontalAdapter extends RecyclerView.Adapter<MusicVideoHorizontalAdapter.ViewHolder> {
    private final ClickCallback click;
    private List<PopinnVideo> videos;

    public MusicVideoHorizontalAdapter(ClickCallback click) {
        this.click = click;
        this.videos = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHorizontalMusicVideoBinding view = ItemHorizontalMusicVideoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PopinnVideo video = videos.get(position);

        holder.item.musicVideoTitleLabel.setText(video.getTitle());
        MusicVideoUtil.bindSubtitle(holder.item.musicVideoSubtitleLabel, video, true);
        bindDuration(holder.item.musicVideoDurationLabel, video);
        MusicVideoUtil.loadThumbnail(holder.item.musicVideoThumbnailImageView, video);
    }

    private void bindDuration(TextView label, PopinnVideo video) {
        String duration = MusicVideoUtil.formatDuration(video);
        label.setText(duration);
        label.setVisibility(duration != null ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    public void setItems(List<PopinnVideo> videos) {
        this.videos = videos != null ? videos : Collections.emptyList();
        notifyDataSetChanged();
    }

    /** Appends the next page, keeping the scroll position stable. */
    public void addItems(List<PopinnVideo> more) {
        if (more == null || more.isEmpty()) return;

        List<PopinnVideo> combined = new ArrayList<>(videos);
        int insertedAt = combined.size();
        combined.addAll(more);
        this.videos = combined;
        notifyItemRangeInserted(insertedAt, more.size());
    }

    /** Drops everything, for when the sort changes and paging restarts. */
    public void clear() {
        int previousCount = videos.size();
        this.videos = Collections.emptyList();
        notifyItemRangeRemoved(0, previousCount);
    }

    public int getLoadedCount() {
        return videos.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHorizontalMusicVideoBinding item;

        ViewHolder(ItemHorizontalMusicVideoBinding item) {
            super(item.getRoot());
            this.item = item;

            itemView.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.MUSIC_VIDEO_OBJECT, videos.get(getBindingAdapterPosition()));
                click.onMusicVideoClick(bundle);
            });
        }
    }
}
