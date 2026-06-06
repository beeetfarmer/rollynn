package com.cappielloantonio.tempo.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cappielloantonio.tempo.model.Download;

import java.util.List;

@Dao
public interface DownloadDao {
    @Query("SELECT * FROM download WHERE download_state = 1 ORDER BY artist, album, disc_number, track ASC")
    LiveData<List<Download>> getAll();

    @Query("SELECT * FROM download WHERE download_state = 1 ORDER BY artist, album, disc_number, track ASC")
    List<Download> getAllSync();

    @Query("SELECT * FROM download WHERE id = :id")
    Download getOne(String id);

    @Query("SELECT * FROM download WHERE cover_art_id = :coverArtId AND download_state = 1 LIMIT 1")
    Download getByCoverArtId(String coverArtId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Download download);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Download> downloads);

    @Query("UPDATE download SET download_state = 1 WHERE id = :id")
    void update(String id);

    @Query("DELETE FROM download WHERE id = :id")
    void delete(String id);

    @Query("DELETE FROM download WHERE id IN (:ids)")
    void deleteByIds(List<String> ids);

    @Query("DELETE FROM download")
    void deleteAll();

    @Query("SELECT * FROM download WHERE playlist_id = :playlistId AND download_state = 1 ORDER BY disc_number, track ASC")
    List<Download> getByPlaylistIdSync(String playlistId);

    @Query("SELECT playlist_id, playlist_name, COUNT(*) as song_count, MIN(cover_art_id) as cover_art_id FROM download WHERE playlist_id IS NOT NULL AND download_state = 1 GROUP BY playlist_id, playlist_name")
    List<DownloadedPlaylistInfo> getDownloadedPlaylistsSync();

    @Query("SELECT * FROM download WHERE download_state = 1 AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%') ORDER BY artist, album, disc_number, track ASC")
    List<Download> searchSync(String query);

    @Query("SELECT COUNT(*) FROM download WHERE playlist_id = :playlistId AND download_state = 1")
    int getDownloadCountForPlaylist(String playlistId);

    @Query("SELECT DISTINCT playlist_id FROM download WHERE playlist_id IS NOT NULL AND download_state = 1")
    List<String> getDownloadedPlaylistIds();
}