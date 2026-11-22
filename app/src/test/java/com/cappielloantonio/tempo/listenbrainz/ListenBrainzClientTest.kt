package com.cappielloantonio.tempo.listenbrainz

import com.cappielloantonio.tempo.BuildConfig
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListenBrainzClientTest {
    /** Runs a request through the real client, answering it before it reaches the network. */
    private fun sentUserAgents(request: Request): List<String> {
        var sent = emptyList<String>()
        val client = ListenBrainz.client.newBuilder().addInterceptor { chain ->
            sent = chain.request().headers("User-Agent")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("".toResponseBody()).build()
        }.build()
        client.newCall(request).execute().close()
        return sent
    }

    private fun request() = Request.Builder().url("https://api.listenbrainz.org/1/anything")

    @Test
    fun requestsNameTheAppItsVersionAndWhereToReachIt() {
        val version = BuildConfig.VERSION_NAME.removePrefix("v")

        assertEquals(
            listOf("Rollynn/$version ( https://github.com/beeetfarmer/rollynn )"),
            sentUserAgents(request().build()),
        )
    }

    @Test
    fun itReplacesAnyOtherUserAgentRatherThanAddingToIt() {
        assertEquals(1, sentUserAgents(request().header("User-Agent", "other").build()).size)
    }

    @Test
    fun itHasTheFormatListenBrainzAsksFor() {
        assertTrue(Regex("""Rollynn/\d+\.\d+\S* \( https://\S+ \)""").matches(ListenBrainz.userAgent))
    }
}
