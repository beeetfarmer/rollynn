package com.cappielloantonio.tempo.ui.fragment

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.appcompat.view.menu.MenuBuilder
import androidx.navigation.fragment.NavHostFragment
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.model.Server
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.cappielloantonio.tempo.ui.dialog.ServerSignupDialog
import com.cappielloantonio.tempo.util.Preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Restoring a backup from the login screen of a fresh install. */
// Dynamic colours crash Robolectric's theme code, and only exist from SDK 31.
@Config(sdk = [30])
@RunWith(RobolectricTestRunner::class)
class LoginRestoreTest {
    private lateinit var controller: ActivityController<MainActivity>
    private val activity get() = controller.get()

    // Not reachable, so the login it starts never gets an answer during the test.
    private val home = Server("home-id", "Home", "neil", "http://10.255.255.1", null, 1, false)
    private val other = Server("other-id", "Other", "neil", "http://10.255.255.2", null, 2, false)

    @Before
    fun setUp() {
        AppTestEnv.install()
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, MediaService::class.java))
        AppTestEnv.setStatic(App::class.java, "encryptedPreferences", app.getSharedPreferences("fake_encrypted", Context.MODE_PRIVATE))
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        settle()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        AppTestEnv.setStatic(App::class.java, "encryptedPreferences", null)
        AppTestEnv.uninstall()
    }

    private fun settle() {
        AppTestEnv.settle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun login(): LoginFragment {
        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return host.childFragmentManager.primaryNavigationFragment as LoginFragment
    }

    /** What a restore leaves behind: the servers it added, then the login screen told it finished. */
    private fun restored(loginServerId: String?, vararg added: Server) {
        for (server in added) {
            AppTestEnv.db.serverDao().insert(server)
            App.getInstance().encryptedPreferences.edit().putString("password_${server.serverId}", "pass").commit()
        }
        ReflectionHelpers.callInstanceMethod<Any?>(
            login(), "onBackupRestored",
            ClassParameter.from(Int::class.javaPrimitiveType, 5), ClassParameter.from(String::class.java, loginServerId),
        )
        settle()
    }

    private fun signupDialogs() = activity.supportFragmentManager.fragments.count { it is ServerSignupDialog }

    private fun addServer(server: Server) {
        AppTestEnv.db.serverDao().insert(server)
        App.getInstance().encryptedPreferences.edit().putString("password_${server.serverId}", "pass").commit()
        settle()
    }

    /** Room reports a change to the list from its own thread, which [settle] doesn't wait for. */
    private fun waitFor(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    private fun loggedInto(server: Server) = waitFor { Preferences.getServerId() == server.serverId }

    @Test
    fun itStartsOnTheLoginScreen() {
        assertTrue(login().isAdded)
        assertNull(Preferences.getServerId())
    }

    @Test
    fun theMenuOffersARestore() {
        val menu = MenuBuilder(activity)
        activity.menuInflater.inflate(R.menu.login_page_menu, menu)

        assertEquals("Restore from backup", menu.findItem(R.id.action_restore).title)
    }

    @Test
    fun choosingRestoreOpensTheFilePicker() {
        login().onOptionsItemSelected(RoboMenuItem(R.id.action_restore))

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, shadowOf(activity).nextStartedActivityForResult.intent.action)
    }

    @Test
    fun withTheLoginInTheBackupItLogsStraightIn() {
        restored(home.serverId, home, other)

        assertEquals(home.serverId, Preferences.getServerId())
        assertEquals(home.address, Preferences.getServer())
        assertEquals("pass", Preferences.getPassword())
        assertEquals(0, signupDialogs())
    }

    @Test
    fun withoutALoginItAsksForTheServer() {
        restored(null)

        assertEquals(1, signupDialogs())
        assertEquals("Add your server to finish restoring", ShadowToast.getTextOfLatestToast())
        assertNull(Preferences.getServerId())
    }

    @Test
    fun thenItLogsIntoTheServerAdded() {
        restored(null)

        addServer(home)

        assertTrue(loggedInto(home))
    }

    @Test
    fun aServerThatWasAlreadyListedIsNotTakenForTheNewOne() {
        addServer(other)
        restored(null)
        assertNull(Preferences.getServerId())

        addServer(home)

        assertTrue(loggedInto(home))
    }

    @Test
    fun turningThePhoneWhileAskingDoesNotAskTwice() {
        restored(null)

        controller.recreate()
        settle()

        assertEquals(1, signupDialogs())
        addServer(home)

        assertTrue(loggedInto(home))
    }

    @Test
    fun aLoginInTheBackupForAServerThatIsMissingFallsBackToAsking() {
        restored("gone-id", home)

        assertEquals(1, signupDialogs())
    }

    @Test
    fun withoutARestoreNothingHappensByItself() {
        addServer(home)

        assertFalse("not logged in", waitFor(1_000) { Preferences.getServerId() != null })
        assertEquals(0, signupDialogs())
    }
}
