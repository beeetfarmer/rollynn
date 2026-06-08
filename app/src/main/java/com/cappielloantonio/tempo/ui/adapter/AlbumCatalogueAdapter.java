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
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class AlbumCatalogueAdapter extends RecyclerView.Adapter<AlbumCatalogueAdapter.ViewHolder> implements Filterable {
    private static final int TYPE_GRID = 0;
    private static final int TYPE_LIST = 1;

    private final ClickCallback click;
    private String currentFilter;
    private boolean showArtist;
    private boolean listMode;

    private final Filter filtering = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<AlbumID3> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(albumsFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                currentFilter = filterPattern;

                for (AlbumID3 item : albumsFull) {
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
            albums = (List<AlbumID3>) results.values;
            notifyDataSetChanged();
        }
    };

    private List<AlbumID3> albums;
    private List<AlbumID3> albumsFull;

    public AlbumCatalogueAdapter(ClickCallback click, boolean showArtist) {
        this.click = click;
        this.albums = Collections.emptyList();
        this.albumsFull = Collections.emptyList();
        this.currentFilter = "";
        this.showArtist = showArtist;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_LIST ? R.layout.item_library_catalogue_album_list : R.layout.item_library_catalogue_album;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        AlbumID3 album = albums.get(position);

        holder.albumNameLabel.setText(album.getName());
        holder.artistNameLabel.setText(album.getArtist());
        holder.artistNameLabel.setVisibility(showArtist ? View.VISIBLE : View.GONE);

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), album.getCoverArtId(), CustomGlideRequest.ResourceType.Album)
                .build()
                .into(holder.albumCatalogueCoverImageView);
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    public AlbumID3 getItem(int position) {
        return albums.get(position);
    }

    public void setItems(List<AlbumID3> albums) {
        this.albumsFull = new ArrayList<>(albums);
        filtering.filter(currentFilter);
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

    public void appendItems(List<AlbumID3> newItems) {
        if (newItems == null || newItems.isEmpty()) return;
        if (!(albums instanceof ArrayList)) albums = new ArrayList<>(albums);
        if (!(albumsFull instanceof ArrayList)) albumsFull = new ArrayList<>(albumsFull);
        int start = albums.size();
        albums.addAll(newItems);
        albumsFull.addAll(newItems);
        notifyItemRangeInserted(start, newItems.size());
    }

    @Override
    public Filter getFilter() {
        return filtering;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView albumCatalogueCoverImageView;
        TextView albumNameLabel;
        TextView artistNameLabel;

        ViewHolder(View itemView) {
            super(itemView);

            albumCatalogueCoverImageView = itemView.findViewById(R.id.album_catalogue_cover_image_view);
            albumNameLabel = itemView.findViewById(R.id.album_name_label);
            artistNameLabel = itemView.findViewById(R.id.artist_name_label);

            albumNameLabel.setSelected(true);
            artistNameLabel.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());
        }

        private void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.ALBUM_OBJECT, albums.get(getBindingAdapterPosition()));

            click.onAlbumClick(bundle);
        }

        private boolean onLongClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.ALBUM_OBJECT, albums.get(getBindingAdapterPosition()));

            click.onAlbumLongClick(bundle);

            return true;
        }
    }

    public void setItemsWithoutFilter(List<AlbumID3> albums) {
        this.albumsFull = new ArrayList<>(albums);
        this.albums = new ArrayList<>(albums);
        notifyDataSetChanged();
    }

    public void sort(String order) {
        if (albums == null) return;
        
        switch (order) {
            case Constants.ALBUM_ORDER_BY_NAME:
                albums.sort(Comparator.comparing(
                    album -> album.getName() != null ? album.getName() : "",
                    String.CASE_INSENSITIVE_ORDER
                ));
                break;
            case Constants.ALBUM_ORDER_BY_ARTIST:
                albums.sort(Comparator.comparing(
                    album -> album.getArtist() != null ? album.getArtist() : "",
                    String.CASE_INSENSITIVE_ORDER
                ));
                break;
            case Constants.ALBUM_ORDER_BY_YEAR:
                albums.sort(Comparator.comparing(AlbumID3::getYear));
                break;
            case Constants.ALBUM_ORDER_BY_RANDOM:
                Collections.shuffle(albums);
                break;
            case Constants.ALBUM_ORDER_BY_RECENTLY_ADDED:
                albums.sort(Comparator.comparing(
                    album -> album.getCreated() != null ? album.getCreated() : new Date(0),
                    Comparator.nullsLast(Date::compareTo)
                ));
                Collections.reverse(albums);
                break;
            case Constants.ALBUM_ORDER_BY_RECENTLY_PLAYED:
                albums.sort(Comparator.comparing(
                    album -> album.getPlayed() != null ? album.getPlayed() : new Date(0),
                    Comparator.nullsLast(Date::compareTo)
                ));
                Collections.reverse(albums);
                break;
            case Constants.ALBUM_ORDER_BY_MOST_PLAYED:
                albums.sort(Comparator.comparing(
                    album -> album.getPlayCount() != null ? album.getPlayCount() : 0L
                ));
                Collections.reverse(albums);
                break;
        }

        notifyDataSetChanged();
    }
}