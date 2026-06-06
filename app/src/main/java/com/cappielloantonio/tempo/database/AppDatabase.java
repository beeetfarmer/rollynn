package com.cappielloantonio.tempo.database;

import androidx.media3.common.util.UnstableApi;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.converter.DateConverters;
import com.cappielloantonio.tempo.database.dao.ChronologyDao;
import com.cappielloantonio.tempo.database.dao.DownloadDao;
import com.cappielloantonio.tempo.database.dao.FavoriteDao;
import com.cappielloantonio.tempo.database.dao.LyricsDao;
import com.cappielloantonio.tempo.database.dao.PlaylistDao;
import com.cappielloantonio.tempo.database.dao.PlaylistFolderDao;
import com.cappielloantonio.tempo.database.dao.QueueDao;
import com.cappielloantonio.tempo.database.dao.RecentSearchDao;
import com.cappielloantonio.tempo.database.dao.ScrobbleDao;
import com.cappielloantonio.tempo.database.dao.ServerDao;
import com.cappielloantonio.tempo.database.dao.SessionMediaItemDao;
import com.cappielloantonio.tempo.model.Chronology;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.model.Favorite;
import com.cappielloantonio.tempo.model.LyricsCache;
import com.cappielloantonio.tempo.model.Queue;
import com.cappielloantonio.tempo.model.RecentSearch;
import com.cappielloantonio.tempo.model.PlaylistFolder;
import com.cappielloantonio.tempo.model.PlaylistFolderEntry;
import com.cappielloantonio.tempo.model.Scrobble;
import com.cappielloantonio.tempo.model.Server;
import com.cappielloantonio.tempo.model.SessionMediaItem;
import com.cappielloantonio.tempo.subsonic.models.Playlist;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@UnstableApi
@Database(
        version = 17,
        entities = {Queue.class, Server.class, RecentSearch.class, Download.class, Chronology.class, Favorite.class, SessionMediaItem.class, Playlist.class, LyricsCache.class, Scrobble.class, PlaylistFolder.class, PlaylistFolderEntry.class},
        autoMigrations = {@AutoMigration(from = 10, to = 11), @AutoMigration(from = 11, to = 12)}
)
@TypeConverters({DateConverters.class})
public abstract class AppDatabase extends RoomDatabase {
    private final static String DB_NAME = "tempo_db";
    private static AppDatabase instance;

    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_folder` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`parent_id` INTEGER, " +
                    "`sort_order` INTEGER NOT NULL DEFAULT 0, " +
                    "`created_at` INTEGER NOT NULL DEFAULT 0, " +
                    "FOREIGN KEY(`parent_id`) REFERENCES `playlist_folder`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_folder_parent_id` ON `playlist_folder` (`parent_id`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_folder_entry` (" +
                    "`playlist_id` TEXT NOT NULL, " +
                    "`folder_id` INTEGER, " +
                    "`sort_order` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`playlist_id`), " +
                    "FOREIGN KEY(`folder_id`) REFERENCES `playlist_folder`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_folder_entry_folder_id` ON `playlist_folder_entry` (`folder_id`)");
        }
    };

    public static synchronized AppDatabase getInstance() {
        if (instance == null) {
            instance = Room.databaseBuilder(App.getContext(), AppDatabase.class, DB_NAME)
                    .addMigrations(MIGRATION_16_17)
                    .fallbackToDestructiveMigration()
                    .build();
        }

        return instance;
    }

    public abstract QueueDao queueDao();

    public abstract ServerDao serverDao();

    public abstract RecentSearchDao recentSearchDao();

    public abstract DownloadDao downloadDao();

    public abstract ChronologyDao chronologyDao();

    public abstract FavoriteDao favoriteDao();

    public abstract SessionMediaItemDao sessionMediaItemDao();

    public abstract PlaylistDao playlistDao();

    public abstract LyricsDao lyricsDao();

    public abstract ScrobbleDao scrobbleDao();

    public abstract PlaylistFolderDao playlistFolderDao();
}
