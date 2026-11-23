package com.cappielloantonio.tempo.ui.fragment

import android.content.ComponentName
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.model.HomeSector
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.cappielloantonio.tempo.util.Constants
import com.cappielloantonio.tempo.util.Preferences
import com.cappielloantonio.tempo.util.SettingsBackupUtil
import com.cappielloantonio.tempo.viewmodel.HomeViewModel
import com.google.gson.Gson
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.ByteArrayInputStream

// Dynamic colours crash Robolectric's theme code, and only exist from SDK 31.
@Config(sdk = [30])
@RunWith(RobolectricTestRunner::class)
class HomeOrderRestoreTest {
    private lateinit var controller: ActivityController<MainActivity>
    private val activity get() = controller.get()
    private val uri = Uri.parse("content://test/backup.json")

    private fun sectors(vararg ids: String) = ids.mapIndexed { i, id -> HomeSector(id, id, true, i) }

    @Before
    fun setUp() {
        AppTestEnv.install()
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, MediaService::class.java))
        Preferences.setHomeSectorList(sectors(Constants.HOME_SECTOR_DISCOVERY, Constants.HOME_SECTOR_PINNED_PLAYLISTS))
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        AppTestEnv.uninstall()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun homeOrder() =
        ViewModelProvider(activity)[HomeViewModel::class.java].homeSectorList.map { it.id }.take(2)

    @Test
    fun theHomeScreenShowsARestoredOrderStraightAway() {
        assertEquals(listOf(Constants.HOME_SECTOR_DISCOVERY, Constants.HOME_SECTOR_PINNED_PLAYLISTS), homeOrder())
        val restoredOrder = Gson().toJson(sectors(Constants.HOME_SECTOR_PINNED_PLAYLISTS, Constants.HOME_SECTOR_DISCOVERY))
        val backup = JSONObject().put("format", 1).put(
            "settings",
            JSONObject().put("home_sector_list", JSONObject().put("type", "string").put("value", restoredOrder)),
        )
        shadowOf(activity.contentResolver).registerInputStream(uri, ByteArrayInputStream(backup.toString().toByteArray()))
        val restored = SettingsBackupUtil.restore(activity, uri, null)

        activity.navController.navigate(R.id.settingsFragment)
        idle()
        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val settings = host.childFragmentManager.primaryNavigationFragment as SettingsFragment
        ReflectionHelpers.callInstanceMethod<Any?>(
            settings, "onSettingsRestored",
            ClassParameter.from(Uri::class.java, uri), ClassParameter.from(Int::class.javaPrimitiveType, restored),
        )
        idle()

        assertEquals(listOf(Constants.HOME_SECTOR_PINNED_PLAYLISTS, Constants.HOME_SECTOR_DISCOVERY), homeOrder())
    }
}
