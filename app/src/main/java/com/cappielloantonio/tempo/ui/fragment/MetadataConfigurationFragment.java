package com.cappielloantonio.tempo.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.Preferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MetadataConfigurationFragment extends Fragment {

    private RecyclerView recyclerView;
    private MetadataAdapter adapter;
    private MainActivity activity;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        View view = inflater.inflate(R.layout.fragment_metadata_configuration, container, false);

        androidx.appcompat.widget.Toolbar toolbar = view.findViewById(R.id.toolbar);
        activity.setSupportActionBar(toolbar);
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> activity.navController.navigateUp());

        recyclerView = view.findViewById(R.id.metadata_items_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        setupAdapter();

        return view;
    }

    private void setupAdapter() {
        List<String> currentItems = Preferences.getNowPlayingMetadata();
        List<String> allItems = new ArrayList<>(Arrays.asList(
                Constants.METADATA_TITLE,
                Constants.METADATA_ARTIST,
                Constants.METADATA_ALBUM,
                Constants.METADATA_YEAR,
                Constants.METADATA_GENRE,
                Constants.METADATA_BITRATE,
                Constants.METADATA_PLAY_COUNT,
                Constants.METADATA_SCROBBLES
        ));

        // Reorder allItems to have current items first in their saved order
        List<String> orderedAllItems = new ArrayList<>(currentItems);
        for (String item : allItems) {
            if (!orderedAllItems.contains(item)) {
                orderedAllItems.add(item);
            }
        }

        adapter = new MetadataAdapter(orderedAllItems, currentItems);
        recyclerView.setAdapter(adapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getBindingAdapterPosition();
                int toPos = target.getBindingAdapterPosition();
                adapter.moveItem(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        touchHelper.attachToRecyclerView(recyclerView);
    }

    @Override
    public void onPause() {
        super.onPause();
        Preferences.setNowPlayingMetadata(adapter.getSelectedItems());
    }

    private class MetadataAdapter extends RecyclerView.Adapter<MetadataViewHolder> {
        private final List<String> items;
        private final List<String> selectedItems;

        public MetadataAdapter(List<String> items, List<String> selectedItems) {
            this.items = items;
            this.selectedItems = new ArrayList<>(selectedItems);
        }

        public void moveItem(int fromPos, int toPos) {
            Collections.swap(items, fromPos, toPos);
            notifyItemMoved(fromPos, toPos);
        }

        public List<String> getSelectedItems() {
            List<String> result = new ArrayList<>();
            for (String item : items) {
                if (selectedItems.contains(item)) {
                    result.add(item);
                }
            }
            return result;
        }

        @NonNull
        @Override
        public MetadataViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dock_config, parent, false);
            return new MetadataViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MetadataViewHolder holder, int position) {
            String item = items.get(position);
            holder.name.setText(getItemDisplayName(item));
            holder.icon.setImageResource(getMetadataIcon(item));
            
            holder.checkBox.setChecked(selectedItems.contains(item));
            
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!selectedItems.contains(item)) selectedItems.add(item);
                } else {
                    selectedItems.remove(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private String getItemDisplayName(String item) {
            switch (item) {
                case Constants.METADATA_TITLE: return getString(R.string.track_info_title);
                case Constants.METADATA_ARTIST: return getString(R.string.track_info_artist);
                case Constants.METADATA_ALBUM: return getString(R.string.track_info_album);
                case Constants.METADATA_YEAR: return getString(R.string.track_info_year);
                case Constants.METADATA_GENRE: return getString(R.string.track_info_genre);
                case Constants.METADATA_BITRATE: return getString(R.string.track_info_bitrate);
                case Constants.METADATA_PLAY_COUNT: return getString(R.string.track_info_play_count);
                case Constants.METADATA_SCROBBLES: return getString(R.string.metadata_scrobbles);
                default: return item;
            }
        }

        private int getMetadataIcon(String item) {
            switch (item) {
                case Constants.METADATA_TITLE: return R.drawable.ic_check_circle;
                case Constants.METADATA_ARTIST: return R.drawable.ic_placeholder_artist;
                case Constants.METADATA_ALBUM: return R.drawable.ic_placeholder_album;
                case Constants.METADATA_YEAR: return R.drawable.ic_history;
                case Constants.METADATA_GENRE: return R.drawable.ic_eq;
                case Constants.METADATA_BITRATE: return R.drawable.ic_graphic_eq;
                case Constants.METADATA_PLAY_COUNT: return R.drawable.ic_repeat;
                case Constants.METADATA_SCROBBLES: return R.drawable.ic_refresh;
                default: return R.drawable.ic_info_stream;
            }
        }
    }

    private static class MetadataViewHolder extends RecyclerView.ViewHolder {
        ImageView dragHandle, icon;
        TextView name;
        CheckBox checkBox;

        public MetadataViewHolder(@NonNull View itemView) {
            super(itemView);
            dragHandle = itemView.findViewById(R.id.item_drag_handle);
            icon = itemView.findViewById(R.id.item_icon);
            name = itemView.findViewById(R.id.item_name);
            checkBox = itemView.findViewById(R.id.item_checkbox);
        }
    }
}