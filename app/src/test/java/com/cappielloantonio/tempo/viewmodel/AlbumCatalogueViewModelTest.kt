package com.cappielloantonio.tempo.viewmodel

import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.subsonic.base.ApiResponse
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.testutil.thenAnswerHttpError
import com.cappielloantonio.tempo.testutil.thenAnswerNetworkFailure
import com.cappielloantonio.tempo.testutil.thenAnswerOk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
class AlbumCatalogueViewModelTest {
    private lateinit var model: AlbumCatalogueViewModel

    @Before
    fun setUp() {
        AppTestEnv.install()
        model = AlbumCatalogueViewModel(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() = AppTestEnv.uninstall()

    private fun request() = `when`(
        App.getSubsonicClientInstance(false).albumSongListClient
            .getAlbumList2(anyString(), anyInt(), anyInt(), any(), any()),
    )

    private fun page(count: Int, prefix: String = "album") = (1..count).joinToString(
        ",",
        """{"status":"ok","albumList2":{"album":[""",
        "]}}",
    ) { """{"id":"$prefix$it","name":"$prefix $it"}""" }

    private fun loaded() = model.albumList.value!!.map { it.name }

    /** Requests that only finish when the test says so. */
    @Suppress("UNCHECKED_CAST")
    private fun heldRequests(): List<Callback<ApiResponse>> {
        val held = mutableListOf<Callback<ApiResponse>>()
        request().thenAnswer {
            val call = mock(Call::class.java) as Call<ApiResponse>
            org.mockito.Mockito.doAnswer {
                held.add(it.getArgument(0) as Callback<ApiResponse>)
                null
            }.`when`(call).enqueue(any())
            call
        }
        return held
    }

    private fun Callback<ApiResponse>.answer(json: String) =
        onResponse(mock(Call::class.java) as Call<ApiResponse>, Response.success(AppTestEnv.apiResponse(json)))

    @Test
    fun aShortPageEndsLoading() {
        request().thenAnswerOk(page(3))

        model.loadAlbums()

        assertEquals(3, loaded().size)
        assertEquals(false, model.loadingStatus.value)
    }

    @Test
    fun fullPagesAreFollowedByTheNext() {
        request().thenAnswerOk(page(500, "first")).thenAnswerOk(page(2, "second"))

        model.loadAlbums()

        assertEquals(502, loaded().size)
        assertEquals("second 2", loaded().last())
        assertEquals(false, model.loadingStatus.value)
    }

    @Test
    fun aLibraryOfExactlyOnePageFinishes() {
        request().thenAnswerOk(page(500)).thenAnswerOk("""{"status":"ok","albumList2":{}}""")

        model.loadAlbums()

        assertEquals(500, loaded().size)
        assertEquals(false, model.loadingStatus.value)
    }

    @Test
    fun anEmptyLibraryFinishes() {
        request().thenAnswerOk("""{"status":"ok","albumList2":{}}""")

        model.loadAlbums()

        assertTrue(loaded().isEmpty())
        assertEquals(false, model.loadingStatus.value)
    }

    @Test
    fun anHttpErrorEndsLoading() {
        request().thenAnswerHttpError()

        model.loadAlbums()

        assertEquals(false, model.loadingStatus.value)
    }

    @Test
    fun aNetworkFailureEndsLoading() {
        request().thenAnswerNetworkFailure()

        model.loadAlbums()

        assertEquals(false, model.loadingStatus.value)
    }

    @Test
    fun aServerErrorEndsLoading() {
        request().thenAnswerOk("""{"status":"failed"}""")

        model.loadAlbums()

        assertEquals(false, model.loadingStatus.value)
    }

    @Test
    fun aFailedLaterPageKeepsWhatLoaded() {
        request().thenAnswerOk(page(500)).thenAnswerHttpError()

        model.loadAlbums()

        assertEquals(500, loaded().size)
        assertEquals(false, model.loadingStatus.value)
    }

    @Test
    fun reloadingShowsThatItIsLoading() {
        request().thenAnswerOk(page(1))
        model.loadAlbums()
        heldRequests()

        model.loadAlbums()

        assertEquals(true, model.loadingStatus.value)
    }

    @Test
    fun aReplacedLoadCannotAddToTheNewOne() {
        val held = heldRequests()
        model.loadAlbums()
        model.loadAlbums()

        held[0].answer(page(2, "old"))
        held[1].answer(page(1, "new"))

        assertEquals(listOf("new 1"), loaded())
        assertEquals(false, model.loadingStatus.value)
    }

    @Test
    fun aStoppedLoadIgnoresItsResponse() {
        val held = heldRequests()
        model.loadAlbums()

        model.stopLoading()
        held[0].answer(page(2))

        assertTrue(loaded().isEmpty())
        assertEquals(false, model.loadingStatus.value)
    }
}
