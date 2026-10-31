package com.cappielloantonio.tempo.subsonic.api.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PlaylistServiceTest {
    private val service = Retrofit.Builder()
        .baseUrl("http://server/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(PlaylistService::class.java)

    private fun updateUrl(isPublic: Boolean?) =
        service.updatePlaylist(emptyMap(), "p1", "New", isPublic, null, null).request().url

    @Test
    fun leavesTheVisibilityOutWhenItIsNotChanging() {
        val url = updateUrl(null)

        assertNull(url.queryParameter("public"))
        assertEquals("New", url.queryParameter("name"))
    }

    @Test
    fun sendsTheVisibilityWhenItIsChosen() {
        assertEquals("false", updateUrl(false).queryParameter("public"))
        assertEquals("true", updateUrl(true).queryParameter("public"))
    }
}
