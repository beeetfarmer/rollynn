package com.cappielloantonio.tempo.testutil

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.room.Room
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.database.AppDatabase
import com.cappielloantonio.tempo.subsonic.Subsonic
import com.cappielloantonio.tempo.subsonic.base.ApiResponse
import com.cappielloantonio.tempo.subsonic.utils.EmptyDateTypeAdapter
import com.cappielloantonio.tempo.util.NetworkUtil
import com.google.gson.GsonBuilder
import okhttp3.ResponseBody.Companion.toResponseBody
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.stubbing.OngoingStubbing
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities
import retrofit2.Call
import retrofit2.Response
import retrofit2.mock.Calls
import java.io.IOException
import java.util.Date

/**
 * Puts a real in-memory Room database and a mocked Subsonic client behind the app's singletons.
 * Fields are swapped rather than mocked statically because Mockito's static mocks only apply to
 * the calling thread, and the repositories do their work on threads of their own.
 */
object AppTestEnv {
    lateinit var db: AppDatabase
    lateinit var subsonic: Subsonic

    private val gson = GsonBuilder().registerTypeAdapter(Date::class.java, EmptyDateTypeAdapter()).create()
    private val bareThread = Regex("Thread-\\d+")

    fun install() {
        db = Room.inMemoryDatabaseBuilder(App.getContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        setStatic(AppDatabase::class.java, "instance", db)

        subsonic = mock(Subsonic::class.java, RETURNS_DEEP_STUBS)
        `when`(subsonic.url).thenReturn("http://server/")
        setStatic(App::class.java, "subsonic", subsonic)

        NetworkUtil.setServerReachable(true)
    }

    fun uninstall() {
        settle()
        db.close()
        setStatic(AppDatabase::class.java, "instance", null)
        setStatic(App::class.java, "subsonic", null)
    }

    /** Makes [NetworkUtil.isOffline] false; Robolectric reports no validated network by default. */
    fun setOnline() {
        val manager = App.getContext().getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
    }

    fun setPreference(key: String, value: String) {
        App.getInstance().preferences.edit().putString(key, value).commit()
    }

    fun setPreference(key: String, value: Boolean) {
        App.getInstance().preferences.edit().putBoolean(key, value).commit()
    }

    /** Waits for the repositories' `new Thread` work and everything it posts to the main looper. */
    fun settle() {
        val deadline = System.currentTimeMillis() + 10_000
        do {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "Background work did not finish" }
            while (backgroundThreads().isNotEmpty()) {
                check(System.currentTimeMillis() < deadline) { "Background work did not finish" }
                Thread.sleep(2)
            }
            shadowOf(Looper.getMainLooper()).idle()
        } while (backgroundThreads().isNotEmpty())
    }

    private fun backgroundThreads() =
        Thread.getAllStackTraces().keys.filter { it.isAlive && bareThread.matches(it.name) }

    /** Parses the body of a `subsonic-response` the way Retrofit does in the app. */
    fun apiResponse(json: String): ApiResponse =
        gson.fromJson("""{"subsonic-response":$json}""", ApiResponse::class.java)

    fun setStatic(owner: Class<*>, field: String, value: Any?) {
        owner.getDeclaredField(field).apply { isAccessible = true }.set(null, value)
    }
}

/** Calls can only be run once, so each stubbed call gets a fresh one. They run synchronously. */
fun OngoingStubbing<Call<ApiResponse>>.thenAnswerOk(json: String = "{}"): OngoingStubbing<Call<ApiResponse>> =
    thenAnswer { Calls.response(AppTestEnv.apiResponse(json)) }

fun OngoingStubbing<Call<ApiResponse>>.thenAnswerHttpError(): OngoingStubbing<Call<ApiResponse>> =
    thenAnswer { Calls.response(Response.error<ApiResponse>(500, "".toResponseBody())) }

fun OngoingStubbing<Call<ApiResponse>>.thenAnswerNetworkFailure(): OngoingStubbing<Call<ApiResponse>> =
    thenAnswer { Calls.failure<ApiResponse>(IOException("network down")) }
