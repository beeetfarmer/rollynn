package com.cappielloantonio.tempo.viewmodel

import android.app.Dialog
import androidx.lifecycle.LifecycleOwner
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.testutil.thenAnswerOk
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaylistChooserViewModelTest {
    private lateinit var viewModel: PlaylistChooserViewModel
    private val client get() = AppTestEnv.subsonic.playlistClient

    @Before
    fun setUp() {
        AppTestEnv.install()
        AppTestEnv.setOnline()
        AppTestEnv.setPreference("allow_playlist_duplicates", true)
        `when`(client.updatePlaylist(any(), any(), any(), any(), any())).thenAnswerOk()
        viewModel = PlaylistChooserViewModel(RuntimeEnvironment.getApplication())
        viewModel.setSongsToAdd(arrayListOf(Child("s1")))
    }

    @After
    fun tearDown() = AppTestEnv.uninstall()

    private fun add() = viewModel.addSongsToPlaylists(
        mock(LifecycleOwner::class.java),
        mock(Dialog::class.java),
        listOf("p1"),
    )

    @Test
    fun addingLeavesTheVisibilityAloneByDefault() {
        add()

        verify(client).updatePlaylist("p1", null, null, arrayListOf("s1"), null)
    }

    @Test
    fun turningTheSwitchOnMarksThePlaylistPublic() {
        viewModel.setIsPlaylistPublic(true)

        add()

        verify(client).updatePlaylist("p1", null, true, arrayListOf("s1"), null)
    }

    @Test
    fun turningTheSwitchBackOffLeavesTheVisibilityAlone() {
        viewModel.setIsPlaylistPublic(true)
        viewModel.setIsPlaylistPublic(false)

        add()

        verify(client).updatePlaylist("p1", null, null, arrayListOf("s1"), null)
    }
}
