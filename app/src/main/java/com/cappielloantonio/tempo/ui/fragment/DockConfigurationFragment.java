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

public class DockConfigurationFragment extends Fragment {

    private RecyclerView recyclerView;
    private DockAdapter adapter;
    private MainActivity activity;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        View view = inflater.inflate(R.layout.fragment_dock_configuration, container, false);

        androidx.appcompat.widget.Toolbar toolbar = view.findViewById(R.id.toolbar);
        activity.setSupportActionBar(toolbar);
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> activity.navController.navigateUp());

        recyclerView = view.findViewById(R.id.dock_items_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        setupAdapter();

        return view;
    }

    private void setupAdapter() {
        List<String> currentItems = Preferences.getDockItems();
        List<String> allItems = new ArrayList<>(Arrays.asList(
                Constants.DOCK_ITEM_HOME,
                Constants.DOCK_ITEM_SEARCH,
                Constants.DOCK_ITEM_SETTINGS,
                Constants.DOCK_ITEM_LIBRARY,
                Constants.DOCK_ITEM_ALBUMS,
                Constants.DOCK_ITEM_ARTISTS,
                Constants.DOCK_ITEM_DOWNLOADS,
                Constants.DOCK_ITEM_PLAYLISTS
        ));
        if (Preferences.isRewindTabEnabled()) {
            allItems.add(Constants.DOCK_ITEM_REWIND);
        }

        // Reorder allItems to have current items first in their saved order
        List<String> orderedAllItems = new ArrayList<>(currentItems);
        for (String item : allItems) {
            if (!orderedAllItems.contains(item)) {
                orderedAllItems.add(item);
            }
        }

        adapter = new DockAdapter(orderedAllItems, currentItems);
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
        Preferences.setDockItems(adapter.getSelectedItems());
        // Refresh dock in MainActivity
        if (activity != null) {
            activity.init(); // Re-init navigation logic
        }
    }

    private class DockAdapter extends RecyclerView.Adapter<DockViewHolder> {
        private final List<String> items;
        private final List<String> selectedItems;

        public DockAdapter(List<String> items, List<String> selectedItems) {
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

        /** How many items would appear in the dock: mandatory (Home/Search/Settings) plus selected. */
        private int selectedDockCount() {
            int count = 0;
            for (String item : items) {
                boolean mandatory = item.equals(Constants.DOCK_ITEM_HOME)
                        || item.equals(Constants.DOCK_ITEM_SEARCH);
                if (mandatory || selectedItems.contains(item)) count++;
            }
            return count;
        }

        @NonNull
        @Override
        public DockViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dock_config, parent, false);
            return new DockViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DockViewHolder holder, int position) {
            String item = items.get(position);
            holder.name.setText(getItemDisplayName(item));
            holder.icon.setImageResource(getDockIcon(item));
            
            boolean isMandatory = item.equals(Constants.DOCK_ITEM_HOME) ||
                                 item.equals(Constants.DOCK_ITEM_SEARCH);
            
            holder.checkBox.setEnabled(!isMandatory);
            holder.checkBox.setChecked(selectedItems.contains(item) || isMandatory);
            
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!selectedItems.contains(item)) {
                        if (selectedDockCount() >= 4) {
                            buttonView.setChecked(false); // cap: at most 4 items besides More
                            android.widget.Toast.makeText(buttonView.getContext(), R.string.dock_max_items, android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        selectedItems.add(item);
                    }
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
                case Constants.DOCK_ITEM_LIBRARY: return "Library";
                case Constants.DOCK_ITEM_ALBUMS: return "Albums";
                case Constants.DOCK_ITEM_ARTISTS: return "Artists";
                case Constants.DOCK_ITEM_REWIND: return "Rewind";
                case Constants.DOCK_ITEM_DOWNLOADS: return "Downloads";
                case Constants.DOCK_ITEM_PLAYLISTS: return "Playlists";
                case Constants.DOCK_ITEM_SEARCH: return "Search";
                case Constants.DOCK_ITEM_SETTINGS: return "Settings";
                default: return "Home";
            }
        }

        private int getDockIcon(String item) {
            switch (item) {
                case Constants.DOCK_ITEM_LIBRARY: return R.drawable.ic_graphic_eq;
                case Constants.DOCK_ITEM_ALBUMS: return R.drawable.ic_album;
                case Constants.DOCK_ITEM_ARTISTS: return R.drawable.ic_artist;
                case Constants.DOCK_ITEM_REWIND: return R.drawable.ic_history;
                case Constants.DOCK_ITEM_DOWNLOADS: return R.drawable.ic_file_download;
                case Constants.DOCK_ITEM_PLAYLISTS: return R.drawable.ic_placeholder_playlist;
                case Constants.DOCK_ITEM_SEARCH: return R.drawable.ic_search;
                case Constants.DOCK_ITEM_SETTINGS: return R.drawable.ic_settings;
                default: return R.drawable.ic_home;
            }
        }
    }

    private static class DockViewHolder extends RecyclerView.ViewHolder {
        ImageView dragHandle, icon;
        TextView name;
        CheckBox checkBox;

        public DockViewHolder(@NonNull View itemView) {
            super(itemView);
            dragHandle = itemView.findViewById(R.id.item_drag_handle);
            icon = itemView.findViewById(R.id.item_icon);
            name = itemView.findViewById(R.id.item_name);
            checkBox = itemView.findViewById(R.id.item_checkbox);
        }
    }
}
