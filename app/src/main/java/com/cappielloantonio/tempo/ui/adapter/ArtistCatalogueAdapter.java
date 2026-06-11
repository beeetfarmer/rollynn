package com.cappielloantonio.tempo.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ArtistCatalogueAdapter extends RecyclerView.Adapter<ArtistCatalogueAdapter.ViewHolder> implements Filterable {
    private static final int TYPE_GRID = 0;
    private static final int TYPE_LIST = 1;

    private final ClickCallback click;
    private boolean listMode;

    private final Filter filtering = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<ArtistID3> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(artistFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();

                for (ArtistID3 item : artistFull) {
                    if (item.getName().toLowerCase().contains(filterPattern)) {
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
            artists.clear();
            if (results.count > 0) artists.addAll((List) results.values);
            notifyDataSetChanged();
        }
    };

    private List<ArtistID3> artists;
    private List<ArtistID3> artistFull;

    public ArtistCatalogueAdapter(ClickCallback click) {
        this.click = click;
        this.artists = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_LIST ? R.layout.item_library_catalogue_artist_list : R.layout.item_library_catalogue_artist;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ArtistID3 artist = artists.get(position);

        holder.artistNameLabel.setText(artist.getName());

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), artist.getCoverArtId(), CustomGlideRequest.ResourceType.Artist)
                .build()
                .into(holder.artistCatalogueCoverImageView);
    }

    @Override
    public int getItemCount() {
        return artists.size();
    }

    public ArtistID3 getItem(int position) {
        return artists.get(position);
    }

    public void setItems(List<ArtistID3> artists) {
        this.artists = artists;
        this.artistFull = new ArrayList<>(artists);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return listMode ? TYPE_LIST : TYPE_GRID;
    }

    public void setListMode(boolean listMode) {
        if (this.listMode != listMode) {
            this.listMode = listMode;
            notifyDataSetChanged();
        }
    }

    @Override
    public Filter getFilter() {
        return filtering;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView artistCatalogueCoverImageView;
        TextView artistNameLabel;

        ViewHolder(View itemView) {
            super(itemView);

            artistCatalogueCoverImageView = itemView.findViewById(R.id.artist_catalogue_cover_image_view);
            artistNameLabel = itemView.findViewById(R.id.artist_name_label);

            artistNameLabel.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());
        }

        public void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.ARTIST_OBJECT, artists.get(getBindingAdapterPosition()));

            click.onArtistClick(bundle);
        }

        public boolean onLongClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.ARTIST_OBJECT, artists.get(getBindingAdapterPosition()));

            click.onArtistLongClick(bundle);

            return true;
        }
    }

    public void sort(String order) {
        switch (order) {
            case Constants.ARTIST_ORDER_BY_NAME:
                artists.sort(Comparator.comparing(ArtistID3::getName));
                break;
            case Constants.ARTIST_ORDER_BY_RANDOM:
                Collections.shuffle(artists);
                break;
            case Constants.ARTIST_ORDER_BY_ALBUM_COUNT:
                artists.sort(Comparator.comparing(ArtistID3::getAlbumCount).reversed());
                break;
        }

        notifyDataSetChanged();
    }
}
