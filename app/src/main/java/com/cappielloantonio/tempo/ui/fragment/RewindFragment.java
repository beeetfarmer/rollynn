package com.cappielloantonio.tempo.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.koito.KoitoClient;
import com.cappielloantonio.tempo.koito.KoitoEntity;
import com.cappielloantonio.tempo.koito.KoitoRankedItem;
import com.cappielloantonio.tempo.koito.KoitoRepository;
import com.cappielloantonio.tempo.koito.KoitoSummary;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Koito-powered "Rewind": top artists/albums/tracks and totals for a chosen month or year. */
public class RewindFragment extends Fragment {

    private final KoitoRepository koitoRepository = new KoitoRepository();
    private View content;
    private TextView message;
    private Spinner yearSpinner, monthSpinner;
    private TextView periodTitle, minutes, statsPlays, statsUnique, statsNew;
    private android.widget.CheckBox minutesToggle;
    private long lastMinutes = 0;
    private LinearLayout artistsContainer, albumsContainer, tracksContainer;

    private final List<Integer> years = new ArrayList<>();
    private String[] monthLabels;
    private int currentRequest = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rewind, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        content = view.findViewById(R.id.rewind_scroll);
        message = view.findViewById(R.id.rewind_message);
        yearSpinner = view.findViewById(R.id.rewind_year_spinner);
        monthSpinner = view.findViewById(R.id.rewind_month_spinner);
        periodTitle = view.findViewById(R.id.rewind_period_title);
        minutes = view.findViewById(R.id.rewind_minutes);
        minutesToggle = view.findViewById(R.id.rewind_minutes_toggle);
        minutesToggle.setOnCheckedChangeListener((b, checked) -> updateMinutesText());
        statsPlays = view.findViewById(R.id.rewind_stats_plays);
        statsUnique = view.findViewById(R.id.rewind_stats_unique);
        statsNew = view.findViewById(R.id.rewind_stats_new);
        artistsContainer = view.findViewById(R.id.rewind_artists_container);
        albumsContainer = view.findViewById(R.id.rewind_albums_container);
        tracksContainer = view.findViewById(R.id.rewind_tracks_container);

        if (!KoitoClient.isConfigured()) {
            content.setVisibility(View.GONE);
            message.setText(R.string.rewind_not_configured);
            message.setVisibility(View.VISIBLE);
            return;
        }

        setupSpinners();
    }

    private void setupSpinners() {
        int thisYear = Calendar.getInstance().get(Calendar.YEAR);
        years.clear();
        for (int y = thisYear; y >= thisYear - 8; y--) years.add(y);
        List<String> yearLabels = new ArrayList<>();
        for (int y : years) yearLabels.add(String.valueOf(y));

        // Index 0 = whole year, 1..12 = months.
        String[] months = new DateFormatSymbols().getMonths();
        monthLabels = new String[13];
        monthLabels[0] = getString(R.string.rewind_month_all);
        for (int i = 0; i < 12; i++) monthLabels[i + 1] = months[i];

        yearSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, yearLabels));
        monthSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, monthLabels));

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                reload();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        };
        yearSpinner.setOnItemSelectedListener(listener);
        monthSpinner.setOnItemSelectedListener(listener);
        reload();
    }

    private void reload() {
        if (years.isEmpty()) return;
        int year = years.get(yearSpinner.getSelectedItemPosition());
        int month = monthSpinner.getSelectedItemPosition(); // 0 = whole year
        int token = year * 100 + month;
        currentRequest = token;

        periodTitle.setText(month == 0
                ? getString(R.string.rewind_period_year, year)
                : getString(R.string.rewind_period_month, monthLabels[month], year));

        koitoRepository.getSummary(year, month).observe(getViewLifecycleOwner(), summary -> {
            if (getView() == null || token != currentRequest) return; // ignore stale responses
            render(summary);
        });
    }

    private void render(KoitoSummary summary) {
        if (summary == null || summary.getPlays() <= 0) {
            lastMinutes = 0;
            minutes.setText("");
            statsPlays.setText(R.string.rewind_no_data);
            statsUnique.setText("");
            statsNew.setText("");
            artistsContainer.removeAllViews();
            albumsContainer.removeAllViews();
            tracksContainer.removeAllViews();
            return;
        }

        lastMinutes = summary.getMinutesListened();
        updateMinutesText();

        statsPlays.setText(getString(R.string.rewind_plays_stat, summary.getPlays()));
        statsUnique.setText(getString(R.string.rewind_unique_stat, summary.getUniqueArtists(), summary.getUniqueAlbums(), summary.getUniqueTracks()));
        statsNew.setText(getString(R.string.rewind_new_stat, summary.getNewArtists(), summary.getNewAlbums(), summary.getNewTracks()));

        renderTop(artistsContainer, summary.getTopArtists(), false);
        renderTop(albumsContainer, summary.getTopAlbums(), false);
        renderTop(tracksContainer, summary.getTopTracks(), true);
    }

    private void updateMinutesText() {
        if (lastMinutes <= 0) {
            minutes.setText("");
            return;
        }
        if (minutesToggle.isChecked()) {
            minutes.setText(getString(R.string.rewind_minutes_only, lastMinutes));
        } else {
            long h = lastMinutes / 60, m = lastMinutes % 60;
            minutes.setText(h > 0 ? getString(R.string.rewind_minutes_format, h, m) : getString(R.string.rewind_minutes_format_short, m));
        }
    }

    private void renderTop(LinearLayout container, List<KoitoRankedItem> items, boolean showArtist) {
        container.removeAllViews();
        if (items == null) return;
        for (KoitoRankedItem ranked : items) {
            KoitoEntity item = ranked.getItem();
            if (item == null) continue;
            View row = getLayoutInflater().inflate(R.layout.item_rewind_top, container, false);
            ((TextView) row.findViewById(R.id.rewind_item_rank)).setText(String.valueOf(ranked.getRank()));
            ((TextView) row.findViewById(R.id.rewind_item_title)).setText(item.label());
            ((TextView) row.findViewById(R.id.rewind_item_count)).setText(getString(R.string.rewind_plays_count, item.getListenCount()));

            TextView subtitle = row.findViewById(R.id.rewind_item_subtitle);
            if (showArtist && item.artistNames() != null) {
                subtitle.setText(item.artistNames());
                subtitle.setVisibility(View.VISIBLE);
            }

            ImageView image = row.findViewById(R.id.rewind_item_image);
            String url = KoitoClient.imageUrl(item.getImage() != null ? item.getImage().getSmall() : null);
            Glide.with(this)
                    .load(url)
                    .placeholder(R.drawable.ic_placeholder_album)
                    .error(R.drawable.ic_placeholder_album)
                    .transform(new CenterCrop(), new RoundedCorners(16))
                    .into(image);

            container.addView(row);
        }
    }
}
