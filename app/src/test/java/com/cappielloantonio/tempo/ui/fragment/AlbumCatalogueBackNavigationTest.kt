package com.cappielloantonio.tempo.ui.fragment

import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.Filter
import androidx.recyclerview.widget.RecyclerView
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.testutil.thenAnswerOk
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.cappielloantonio.tempo.ui.adapter.AlbumCatalogueAdapter
import com.cappielloantonio.tempo.util.Constants
import com.cappielloantonio.tempo.util.Preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * Robolectric's own Filter publishes at once. On a phone the result is posted to the main thread,
 * so it lands after whatever that thread was in the middle of.
 */
@Implements(Filter::class)
class PostingFilterShadow {
    @RealObject
    private lateinit var filter: Filter

    @Implementation
    fun filter(constraint: CharSequence?) {
        val text = ClassParameter.from(CharSequence::class.java, constraint?.toString())
        val results: Any = ReflectionHelpers.callInstanceMethod(filter, "performFiltering", text)
        val resultsClass = Class.forName("android.widget.Filter\$FilterResults")
        Handler(Looper.getMainLooper()).post {
            ReflectionHelpers.callInstanceMethod<Any?>(
                filter, "publishResults", text, ClassParameter(resultsClass, results),
            )
        }
    }
}

/** Issue #6: the album list came back blank after opening an album and going back. */
// Dynamic colours crash Robolectric's theme code, and only exist from SDK 31.
@Config(sdk = [30], shadows = [PostingFilterShadow::class])
@RunWith(RobolectricTestRunner::class)
class AlbumCatalogueBackNavigationTest {
    private lateinit var controller: ActivityController<MainActivity>
    private val activity get() = controller.get()

    @Before
    fun setUp() {
        AppTestEnv.install()
        `when`(
            App.getSubsonicClientInstance(false).albumSongListClient
                .getAlbumList2(anyString(), anyInt(), anyInt(), any(), any()),
        ).thenAnswerOk(
            """{"status":"ok","albumList2":{"album":[
                {"id":"1","name":"Alpha","artist":"C","year":2001},
                {"id":"2","name":"Bravo","artist":"B","year":1999},
                {"id":"3","name":"Charlie","artist":"A","year":2010}]}}""",
        )
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, MediaService::class.java))
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        AppTestEnv.uninstall()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun openList(order: String = Constants.ALBUM_ORDER_BY_NAME) {
        Preferences.setAlbumSortOrder(order)
        activity.navController.navigate(R.id.albumCatalogueFragment)
        idle()
    }

    private fun leaveAndComeBack() {
        activity.navController.navigate(R.id.settingsFragment)
        idle()
        activity.navController.popBackStack()
        idle()
    }

    private fun type(text: String) {
        activity.findViewById<EditText>(R.id.album_filter_edit_text).setText(text)
        idle()
    }

    private fun shown(): List<String?> {
        val adapter = activity.findViewById<RecyclerView>(R.id.album_catalogue_recycler_view).adapter as AlbumCatalogueAdapter
        return (0 until adapter.itemCount).map { adapter.getItem(it).name }
    }

    @Test
    fun theListIsThereOnFirstOpen() {
        openList()

        assertEquals(listOf("Alpha", "Bravo", "Charlie"), shown())
    }

    @Test
    fun theListIsStillThereAfterComingBack() {
        openList()

        leaveAndComeBack()

        assertEquals(listOf("Alpha", "Bravo", "Charlie"), shown())
    }

    @Test
    fun theListSurvivesSeveralTrips() {
        openList()

        repeat(5) {
            leaveAndComeBack()
            assertEquals("trip $it", 3, shown().size)
        }
    }

    @Test
    fun theListSurvivesInListMode() {
        Preferences.setAlbumCatalogueListMode(true)
        openList()

        leaveAndComeBack()

        assertEquals(3, shown().size)
    }

    @Test
    fun theSortOrderSurvivesComingBack() {
        openList(Constants.ALBUM_ORDER_BY_ARTIST)

        leaveAndComeBack()

        assertEquals(listOf("Charlie", "Bravo", "Alpha"), shown())
    }

    @Test
    fun theFilterSurvivesComingBack() {
        openList()
        type("brav")

        leaveAndComeBack()

        assertEquals(listOf("Bravo"), shown())
    }

    @Test
    fun clearingTheFilterKeepsTheSortOrder() {
        openList(Constants.ALBUM_ORDER_BY_ARTIST)
        type("brav")

        type("")

        assertEquals(listOf("Charlie", "Bravo", "Alpha"), shown())
    }

    @Test
    fun randomOrderIsTheSameAfterComingBack() {
        openList(Constants.ALBUM_ORDER_BY_RANDOM)
        val before = shown()

        repeat(6) {
            leaveAndComeBack()
            assertEquals(before, shown())
        }
    }
}
