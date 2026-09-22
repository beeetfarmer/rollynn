package com.cappielloantonio.tempo.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.databinding.ItemMusicVideoCarouselBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.popinn.PopinnVideo;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicVideoUtil;

import java.util.Collections;
import java.util.List;

public class MusicVideoCarouselAdapter extends RecyclerView.Adapter<MusicVideoCarouselAdapter.ViewHolder> {
    private final ClickCallback click;
    private final boolean showArtist;
    private List<PopinnVideo> videos;

    public MusicVideoCarouselAdapter(ClickCallback click, boolean showArtist) {
        this.click = click;
        this.showArtist = showArtist;
        this.videos = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMusicVideoCarouselBinding view = ItemMusicVideoCarouselBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PopinnVideo video = videos.get(position);

        holder.item.musicVideoTitleLabel.setText(video.getTitle());
        MusicVideoUtil.bindSubtitle(holder.item.musicVideoSubtitleLabel, video, showArtist);
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

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemMusicVideoCarouselBinding item;

        ViewHolder(ItemMusicVideoCarouselBinding item) {
            super(item.getRoot());
            this.item = item;

            itemView.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.MUSIC_VIDEO_OBJECT, videos.get(getBindingAdapterPosition()));
                click.onMusicVideoClick(bundle);
            });

            item.musicVideoTitleLabel.setSelected(true);
            item.musicVideoSubtitleLabel.setSelected(true);
        }
    }
}
