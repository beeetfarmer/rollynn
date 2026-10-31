package com.cappielloantonio.tempo.ui.fragment

import android.content.ComponentName
import android.os.Looper
import android.view.View
import androidx.navigation.fragment.NavHostFragment
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.cappielloantonio.tempo.util.NetworkUtil
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

// Dynamic colours crash Robolectric's theme code, and only exist from SDK 31.
@Config(sdk = [30])
@RunWith(RobolectricTestRunner::class)
class SearchBackNavigationTest {

    private lateinit var controller: ActivityController<MainActivity>
    private val activity get() = controller.get()

    @Before
    fun setUp() {
        AppTestEnv.install()
        // No media service here; left to connect, Media3 fails on Robolectric's null component.
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, MediaService::class.java))
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
        // Starting up without a server marks it unreachable, which searches downloads instead.
        AppTestEnv.setOnline()
        NetworkUtil.setServerReachable(true)
        activity.navController.navigate(R.id.searchFragment)
        idle()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        AppTestEnv.uninstall()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun searchFragment(): SearchFragment {
        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return host.childFragmentManager.primaryNavigationFragment as SearchFragment
    }

    private fun openAResultAndComeBack() {
        searchFragment().search("abba")
        idle()
        activity.navController.navigate(R.id.settingsFragment)
        idle()
        activity.navController.popBackStack()
        idle()
    }

    private fun searchView() = activity.findViewById<SearchView>(R.id.search_view)

    @Test
    fun aNewSearchTabOpensTheKeyboardView() {
        assertTrue(searchView().isShowing)
    }

    @Test
    fun comingBackFromAResultShowsTheSameSearch() {
        openAResultAndComeBack()

        assertEquals(R.id.searchFragment, activity.navController.currentDestination?.id)
        assertEquals("abba", activity.findViewById<SearchBar>(R.id.search_bar).text.toString())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.search_result_layout).visibility)
    }

    @Test
    fun comingBackFromAResultFetchesTheResultsAgain() {
        openAResultAndComeBack()

        // The rebuilt view starts with empty lists.
        verify(AppTestEnv.subsonic.searchingClient, times(2)).search3("abba", 20, 20, 20)
    }

    @Test
    fun comingBackFromAResultDoesNotReopenAnEmptySearch() {
        openAResultAndComeBack()

        assertFalse(searchView().isShowing)
    }

    @Test
    fun leavingSearchAndOpeningItAgainStartsFresh() {
        searchFragment().search("abba")
        idle()
        activity.navController.popBackStack()
        idle()

        activity.navController.navigate(R.id.searchFragment)
        idle()

        assertTrue(searchView().isShowing)
    }
}
