package com.cappielloantonio.tempo.repository

import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.model.Download
import com.cappielloantonio.tempo.model.Scrobble
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.testutil.AppTestEnv.db
import com.cappielloantonio.tempo.testutil.AppTestEnv.settle
import com.cappielloantonio.tempo.testutil.thenAnswerHttpError
import com.cappielloantonio.tempo.testutil.thenAnswerNetworkFailure
import com.cappielloantonio.tempo.testutil.thenAnswerOk
import com.cappielloantonio.tempo.util.Constants.SeedType
import com.cappielloantonio.tempo.util.NetworkUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import retrofit2.mock.Calls
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class SongRepositoryTest {
    private lateinit var repository: SongRepository
    private val annotation get() = AppTestEnv.subsonic.mediaAnnotationClient
    private val browsing get() = AppTestEnv.subsonic.browsingClient
    private val songLists get() = AppTestEnv.subsonic.albumSongListClient

    private fun songs(vararg ids: String) = ids.joinToString(",", "[", "]") { """{"id":"$it"}""" }

    @Before
    fun setUp() {
        AppTestEnv.install()
        AppTestEnv.setOnline()
        AppTestEnv.setPreference("server_id", "server1")
        repository = SongRepository()
    }

    @After
    fun tearDown() = AppTestEnv.uninstall()

    private fun download(id: String = "s1", configure: Download.() -> Unit = {}) {
        db.downloadDao().insert(Download(id).apply(configure))
    }

    private fun pending(server: String = "server1") = db.scrobbleDao().getPendingScrobbles(server)

    // scrobble

    @Test
    fun aSuccessfulScrobbleSavesNothing() {
        `when`(annotation.scrobble(anyString(), anyBoolean(), any())).thenAnswerOk()

        repository.scrobble("s1", true, 100L)
        settle()

        verify(annotation).scrobble("s1", true, 100L)
        assertTrue(pending().isEmpty())
    }

    @Test
    fun aSuccessfulScrobbleSendsWhatWasWaiting() {
        db.scrobbleDao().insert(Scrobble(0, "waiting", 50L, true, "server1"))
        `when`(annotation.scrobble(anyString(), anyBoolean(), any())).thenAnswerOk()

        repository.scrobble("s1", true, 100L)
        settle()

        verify(annotation).scrobble("waiting", true, 50L)
        assertTrue(pending().isEmpty())
    }

    @Test
    fun aRejectedScrobbleIsKeptForLater() {
        `when`(annotation.scrobble(anyString(), anyBoolean(), any())).thenAnswerHttpError()

        repository.scrobble("s1", true, 100L)
        settle()

        val saved = pending().single()
        assertEquals("s1", saved.id)
        assertEquals(100L, saved.timestamp)
        assertEquals(true, saved.submission)
        assertEquals("server1", saved.server)
    }

    @Test
    fun anUnsentScrobbleIsKeptForLater() {
        `when`(annotation.scrobble(anyString(), anyBoolean(), any())).thenAnswerNetworkFailure()

        repository.scrobble("s1", false, 100L)
        settle()

        assertEquals(false, pending().single().submission)
    }

    @Test
    fun aScrobbleWithoutATimeIsKeptWithTheTimeItHappened() {
        `when`(annotation.scrobble(anyString(), anyBoolean(), isNull())).thenAnswerNetworkFailure()
        val before = System.currentTimeMillis()

        repository.scrobble("s1", true)
        settle()

        assertTrue(pending().single().timestamp >= before)
    }

    @Test
    fun aScrobbleWithNoServerIsNotKept() {
        App.getInstance().preferences.edit().remove("server_id").commit()
        `when`(annotation.scrobble(anyString(), anyBoolean(), any())).thenAnswerNetworkFailure()

        repository.scrobble("s1", true, 100L)
        settle()

        assertTrue(pending().isEmpty())
    }

    @Test
    fun aSubmissionCountsAsAPlayOfTheDownload() {
        download { playCount = 4 }
        `when`(annotation.scrobble(anyString(), anyBoolean(), any())).thenAnswerOk()

        repository.scrobble("s1", true, 100L)
        settle()

        assertEquals(5L, db.downloadDao().getOne("s1").playCount)
    }

    @Test
    fun theFirstSubmissionCountsAsOnePlay() {
        download()
        `when`(annotation.scrobble(anyString(), anyBoolean(), any())).thenAnswerOk()

        repository.scrobble("s1", true, 100L)
        settle()

        assertEquals(1L, db.downloadDao().getOne("s1").playCount)
    }

    @Test
    fun nowPlayingDoesNotCountAsAPlay() {
        download { playCount = 4 }
        `when`(annotation.scrobble(anyString(), anyBoolean(), any())).thenAnswerOk()

        repository.scrobble("s1", false, 100L)
        settle()

        assertEquals(4L, db.downloadDao().getOne("s1").playCount)
    }

    // submitPendingScrobbles

    @Test
    fun pendingScrobblesAreSentOldestFirst() {
        db.scrobbleDao().insert(Scrobble(0, "new", 200L, true, "server1"))
        db.scrobbleDao().insert(Scrobble(0, "old", 100L, true, "server1"))
        val sent = mutableListOf<String>()
        `when`(annotation.scrobble(anyString(), anyBoolean(), anyLong())).thenAnswer {
            sent += it.getArgument<String>(0)
            Calls.response(AppTestEnv.apiResponse("{}"))
        }

        repository.submitPendingScrobbles()
        settle()

        assertEquals(listOf("old", "new"), sent)
        assertTrue(pending().isEmpty())
    }

    @Test
    fun onlyThisServersScrobblesAreSent() {
        db.scrobbleDao().insert(Scrobble(0, "other", 100L, true, "server2"))
        `when`(annotation.scrobble(anyString(), anyBoolean(), anyLong())).thenAnswerOk()

        repository.submitPendingScrobbles()
        settle()

        verify(annotation, never()).scrobble(anyString(), anyBoolean(), anyLong())
        assertEquals(1, pending("server2").size)
    }

    @Test
    fun sendingStopsAtTheFirstRejection() {
        db.scrobbleDao().insert(Scrobble(0, "first", 100L, true, "server1"))
        db.scrobbleDao().insert(Scrobble(0, "second", 200L, true, "server1"))
        `when`(annotation.scrobble(anyString(), anyBoolean(), anyLong())).thenAnswerHttpError()

        repository.submitPendingScrobbles()
        settle()

        verify(annotation, times(1)).scrobble(anyString(), anyBoolean(), anyLong())
        assertEquals(2, pending().size)
    }

    @Test
    fun sendingStopsAtTheFirstNetworkFailureAndCanRunAgain() {
        db.scrobbleDao().insert(Scrobble(0, "first", 100L, true, "server1"))
        `when`(annotation.scrobble(anyString(), anyBoolean(), anyLong()))
            .thenAnswerNetworkFailure()
            .thenAnswerOk()

        repository.submitPendingScrobbles()
        settle()
        assertEquals(1, pending().size)

        repository.submitPendingScrobbles()
        settle()
        assertTrue(pending().isEmpty())
    }

    // Instant mix

    @Test
    fun anAlbumMixIsCappedAtTheCount() {
        `when`(browsing.getAlbum("a")).thenAnswerOk("""{"album":{"id":"a","song":${songs("1", "2", "3")}}}""")

        val mix = repository.getInstantMix("a", SeedType.ALBUM, 2)
        settle()

        assertEquals(listOf("1", "2"), mix.value!!.map { it.id })
    }

    @Test
    fun aMixDropsDuplicates() {
        `when`(browsing.getSimilarSongs2("a", 2)).thenAnswerOk("""{"similarSongs2":{"song":${songs("1", "1")}}}""")

        val mix = repository.getInstantMix("a", SeedType.ARTIST, 2)
        settle()

        assertEquals(listOf("1"), mix.value!!.map { it.id })
    }

    @Test
    fun aShortMixIsToppedUpWithSimilarSongs() {
        `when`(browsing.getSong("t")).thenAnswerOk("""{"song":{"id":"t"}}""")
        `when`(browsing.getSimilarSongs("t", 20)).thenAnswerOk("""{"similarSongs":{"song":${songs("t", "x", "y")}}}""")

        val mix = repository.getInstantMix("t", SeedType.TRACK, 20)
        settle()

        assertEquals(listOf("t", "x", "y"), mix.value!!.map { it.id })
    }

    @Test
    fun aLongEnoughMixIsNotToppedUp() {
        `when`(browsing.getAlbum("a")).thenAnswerOk("""{"album":{"id":"a","song":${songs("1", "2")}}}""")

        repository.getInstantMix("a", SeedType.ALBUM, 4)
        settle()

        verify(browsing, never()).getSimilarSongs(anyString(), anyInt())
    }

    @Test
    fun aFailedMixStaysEmpty() {
        `when`(browsing.getAlbum("a")).thenAnswerNetworkFailure()

        val mix = repository.getInstantMix("a", SeedType.ALBUM, 2)
        settle()

        assertTrue(mix.value!!.isEmpty())
    }

    @Test
    fun theMixCallbackGetsAFullMix() {
        `when`(browsing.getAlbum("a")).thenAnswerOk("""{"album":{"id":"a","song":${songs("1", "2", "3")}}}""")
        var received: List<Child>? = null

        repository.getInstantMix("a", SeedType.ALBUM, 2) { received = it }

        assertEquals(listOf("1", "2"), received!!.map { it.id })
    }

    @Test
    fun theMixCallbackGetsAShortMixToo() {
        `when`(browsing.getAlbum("a")).thenAnswerOk("""{"album":{"id":"a","song":${songs("1", "2")}}}""")
        var received: List<Child>? = null

        repository.getInstantMix("a", SeedType.ALBUM, 25) { received = it }

        assertEquals(listOf("1", "2"), received?.map { it.id })
    }

    @Test
    fun continuousMixReturnsSimilarSongs() {
        `when`(browsing.getSimilarSongs("t", 5)).thenAnswerOk("""{"similarSongs":{"song":${songs("1")}}}""")

        assertEquals(listOf("1"), repository.getContinuousMix("t", 5).value!!.map { it.id })
    }

    @Test
    fun continuousMixIsNullOnFailure() {
        `when`(browsing.getSimilarSongs("t", 5)).thenAnswerNetworkFailure()

        assertNull(repository.getContinuousMix("t", 5).value)
    }

    // Lists

    @Test
    fun starredSongsComeBackInServerOrder() {
        `when`(songLists.getStarred2()).thenAnswerOk("""{"starred2":{"song":${songs("1", "2", "3")}}}""")

        assertEquals(listOf("1", "2", "3"), repository.getStarredSongs(false, 0).value!!.map { it.id })
    }

    @Test
    fun aRandomStarredPickIsLimited() {
        `when`(songLists.getStarred2()).thenAnswerOk("""{"starred2":{"song":${songs("1", "2", "3")}}}""")

        assertEquals(2, repository.getStarredSongs(true, 2).value!!.size)
    }

    @Test
    fun aRandomSampleIsEmptyWhenTheServerHasNone() {
        `when`(songLists.getRandomSongs(5, null, null)).thenAnswerOk()

        assertTrue(repository.getRandomSample(5, null, null).value!!.isEmpty())
    }

    @Test
    fun aRandomSampleFiltersByGenre() {
        `when`(songLists.getRandomSongs(5, 1990, 2000, "Rock")).thenAnswerOk("""{"randomSongs":{"song":${songs("1")}}}""")

        assertEquals(listOf("1"), repository.getRandomSampleWithGenre(5, 1990, 2000, "Rock").value!!.map { it.id })
    }

    @Test
    fun genrePagesAreAHundredSongsLong() {
        `when`(songLists.getSongsByGenre("Rock", 100, 300)).thenAnswerOk("""{"songsByGenre":{"song":${songs("1")}}}""")

        assertEquals(listOf("1"), repository.getSongsByGenre("Rock", 3).value!!.map { it.id })
    }

    // getSong

    @Test
    fun unreachableServerServesTheDownload() {
        download { title = "Local" }
        NetworkUtil.setServerReachable(false)

        val song = repository.getSong("s1")
        settle()

        assertEquals("Local", song.value!!.title)
        verify(browsing, never()).getSong(anyString())
    }

    @Test
    fun theServersSongRefreshesTheDownloadRecord() {
        download { title = "Local"; playCount = 1 }
        `when`(browsing.getSong("s1")).thenAnswerOk("""{"song":{"id":"s1","playCount":9,"userRating":4}}""")

        val song = repository.getSong("s1")
        settle()

        assertEquals(9L, song.value!!.playCount)
        val saved = db.downloadDao().getOne("s1")
        assertEquals(9L, saved.playCount)
        assertEquals(4, saved.userRating)
        assertEquals("Local", saved.title)
    }

    @Test
    fun aServerErrorServesTheDownload() {
        download { title = "Local" }
        `when`(browsing.getSong("s1")).thenAnswerHttpError()

        val song = repository.getSong("s1")
        settle()

        assertEquals("Local", song.value!!.title)
    }

    @Test
    fun aNetworkFailureServesTheDownload() {
        download { title = "Local" }
        `when`(browsing.getSong("s1")).thenAnswerNetworkFailure()

        val song = repository.getSong("s1")
        settle()

        assertEquals("Local", song.value!!.title)
    }

    @Test
    fun aSongThatIsNotDownloadedStaysNullOffline() {
        NetworkUtil.setServerReachable(false)

        val song = repository.getSong("missing")
        settle()

        assertNull(song.value)
    }

    // Download records

    @Test
    fun ratingUpdatesTheDownload() {
        download()

        repository.setRating("s1", 3)
        settle()

        verify(annotation).setRating("s1", 3)
        assertEquals(3, db.downloadDao().getOne("s1").userRating)
    }

    @Test
    fun starringUpdatesTheDownload() {
        download()
        val starred = Date(1000)

        repository.updateDownloadStarred("s1", starred)
        settle()

        assertEquals(starred, db.downloadDao().getOne("s1").starred)
    }

    @Test
    fun lyricsComeFromTheServer() {
        `when`(AppTestEnv.subsonic.mediaRetrievalClient.getLyrics("Artist", "Title"))
            .thenAnswerOk("""{"lyrics":{"value":"la la"}}""")

        assertEquals("la la", repository.getSongLyrics(Child("s1", artist = "Artist", title = "Title")).value)
    }
}
