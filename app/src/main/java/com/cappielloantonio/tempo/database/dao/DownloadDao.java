package com.cappielloantonio.tempo.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.model.DownloadPlaylistEntry;

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

    @Query("SELECT d.* FROM download d JOIN download_playlist p ON p.download_id = d.id WHERE p.playlist_id = :playlistId AND d.download_state = 1 ORDER BY CASE WHEN p.position < 0 THEN 1 ELSE 0 END, p.position ASC, d.disc_number, d.track ASC")
    List<Download> getByPlaylistIdSync(String playlistId);

    /** Downloads in the playlist that no other downloaded playlist holds. */
    @Query("SELECT d.* FROM download d JOIN download_playlist p ON p.download_id = d.id WHERE p.playlist_id = :playlistId AND NOT EXISTS (SELECT 1 FROM download_playlist o WHERE o.download_id = d.id AND o.playlist_id != :playlistId)")
    List<Download> getOnlyInPlaylistSync(String playlistId);

    @Query("SELECT p.playlist_id, MAX(p.playlist_name) as playlist_name, COUNT(*) as song_count, MIN(d.cover_art_id) as cover_art_id, COALESCE(SUM(d.duration), 0) as total_duration FROM download_playlist p JOIN download d ON d.id = p.download_id WHERE d.download_state = 1 GROUP BY p.playlist_id")
    List<DownloadedPlaylistInfo> getDownloadedPlaylistsSync();

    @Query("SELECT * FROM download WHERE download_state = 1 AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%') ORDER BY artist, album, disc_number, track ASC")
    List<Download> searchSync(String query);

    @Query("SELECT COUNT(*) FROM download_playlist p JOIN download d ON d.id = p.download_id WHERE p.playlist_id = :playlistId AND d.download_state = 1")
    int getDownloadCountForPlaylist(String playlistId);

    @Query("SELECT DISTINCT p.playlist_id FROM download_playlist p JOIN download d ON d.id = p.download_id WHERE d.download_state = 1")
    List<String> getDownloadedPlaylistIds();

    @Query("SELECT playlist_id FROM download_playlist WHERE download_id = :downloadId")
    List<String> getPlaylistIdsFor(String downloadId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPlaylistEntries(List<DownloadPlaylistEntry> entries);

    @Query("DELETE FROM download_playlist WHERE playlist_id = :playlistId")
    void deletePlaylistEntries(String playlistId);

    @Query("DELETE FROM download_playlist WHERE download_id = :downloadId")
    void deletePlaylistEntriesForDownload(String downloadId);

    @Query("DELETE FROM download_playlist WHERE download_id IN (:downloadIds)")
    void deletePlaylistEntriesForDownloads(List<String> downloadIds);

    @Query("DELETE FROM download_playlist")
    void deleteAllPlaylistEntries();

    /** Makes the playlist's downloaded tracks exactly {@code entries}. */
    @Transaction
    default void replacePlaylistEntries(String playlistId, List<DownloadPlaylistEntry> entries) {
        deletePlaylistEntries(playlistId);
        insertPlaylistEntries(entries);
    }
}
