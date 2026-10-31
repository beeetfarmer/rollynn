package com.cappielloantonio.tempo.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.testutil.AppTestEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
class LogExportUtilTest {

    @Before
    fun setUp() {
        // The Android keystore is missing under Robolectric.
        val encrypted = App.getContext().getSharedPreferences("fake_encrypted", Context.MODE_PRIVATE)
        AppTestEnv.setStatic(App::class.java, "encryptedPreferences", encrypted)
    }

    private fun redact(log: String, vararg secrets: String) = LogExportUtil.redact(log, secrets.toList())

    @Test
    fun streamUrlsLoseTheirLoginAndHostButKeepThePath() {
        val log = "Source error: https://music.example.org:4533/rest/stream?u=neil&t=abc123&s=salty&v=1.16.1&id=42"

        assertEquals("Source error: https://<server>/rest/stream", redact(log))
    }

    @Test
    fun loginsInsideAUrlAreRemoved() {
        assertEquals("GET http://<server>/rest", redact("GET http://neil:hunter2@10.0.0.5:8080/rest"))
    }

    @Test
    fun aQueryWithoutItsHostStillLosesTheLogin() {
        val redacted = redact("/rest/ping?u=neil&t=abc123&s=salty&p=enc:6869&v=1.16.1")

        assertEquals("/rest/ping?u=<redacted>&t=<redacted>&s=<redacted>&p=<redacted>&v=1.16.1", redacted)
    }

    @Test
    fun lastFmParametersAreRemoved() {
        val redacted = redact("/2.0/?method=track.scrobble&api_key=key123&sk=session1&api_sig=signed")

        assertEquals("/2.0/?method=track.scrobble&api_key=<redacted>&sk=<redacted>&api_sig=<redacted>", redacted)
    }

    @Test
    fun namedSecretsAreRemovedInAnyCommonForm() {
        assertEquals("password=<redacted> next", redact("password=hunter2 next"))
        assertEquals("apiKey: <redacted>", redact("apiKey: sk-or-123"))
        assertEquals("{\"token\":\"<redacted>\",\"id\":1}", redact("{\"token\":\"abc\",\"id\":1}"))
    }

    @Test
    fun authorizationHeadersAreRemovedWhole() {
        assertEquals("Authorization: <redacted>", redact("Authorization: Basic dXNlcjpwYXNz"))
        assertEquals("x-nd-authorization: <redacted>", redact("x-nd-authorization: Bearer abc.def"))
    }

    @Test
    fun bareBearerTokensAreRemoved() {
        assertEquals("sent Bearer <redacted> ok", redact("sent Bearer abc.def ok"))
    }

    @Test
    fun storedValuesAreRemovedWhereverTheyAppear() {
        val log = "java.net.UnknownHostException: Unable to resolve host \"music.example.org\""

        assertEquals(
            "java.net.UnknownHostException: Unable to resolve host \"<redacted>\"",
            redact(log, "music.example.org"),
        )
    }

    @Test
    fun aValueContainingAnotherIsRemovedWhole() {
        assertEquals("<redacted>", redact("abcdef", "abcd", "abcdef"))
    }

    @Test
    fun valuesTooShortToBeSafelyMatchedLeaveTheLogAlone() {
        assertEquals("Player started", redact("Player started", "a", "ay"))
    }

    @Test
    fun ordinaryLogLinesAreUnchanged() {
        val log = "10-31 10:57:01.123  1234  1250 E AndroidRuntime: FATAL EXCEPTION: main\n" +
            "java.lang.NullPointerException: state was null\n" +
            "\tat com.cappielloantonio.tempo.App.onCreate(App.java:30)\n"

        assertEquals(log, redact(log))
    }

    @Test
    fun filePathsAreKept() {
        assertEquals("Reading file:///storage/music/a.mp3", redact("Reading file:///storage/music/a.mp3"))
    }

    @Test
    fun theReportRedactsWhatIsStoredInSettings() {
        Preferences.setServer("https://music.example.org:4533")
        Preferences.setUser("listener")
        Preferences.setToken("storedtoken")
        Preferences.setKoitoApiKey("storedkoitokey")
        Preferences.setTranslationApiKey("storedtranslationkey")

        val report = LogExportUtil.report(
            "host music.example.org user listener storedtoken storedkoitokey storedtranslationkey",
        )

        for (secret in listOf("music.example.org", "listener", "storedtoken", "storedkoitokey", "storedtranslationkey")) {
            assertFalse("$secret leaked", report.contains(secret))
        }
    }

    @Test
    fun theReportStartsWithTheAppAndDevice() {
        val report = LogExportUtil.report("line")

        assertTrue(report.startsWith("App: "))
        assertTrue(report.contains("\nDevice: "))
        assertTrue(report.contains("\nAndroid: "))
        assertTrue(report.endsWith("\n\nline"))
    }

    @Test
    fun aSmallReportIsCopiedWhole() {
        assertEquals("App: 1\n\nline", LogExportUtil.forClipboard("App: 1\n\nline"))
    }

    @Test
    fun aLargeReportKeepsTheDetailsAndTheNewestLines() {
        val log = "o".repeat(LogExportUtil.CLIPBOARD_LIMIT) + "the crash"

        val copied = LogExportUtil.forClipboard("App: 1\n\n$log")

        assertTrue(copied.startsWith("App: 1\n\n[older lines removed]\n"))
        assertTrue(copied.endsWith("the crash"))
        assertTrue(copied.length < LogExportUtil.CLIPBOARD_LIMIT + 64)
    }

    @Test
    fun savingWritesTheReport() {
        val file = File.createTempFile("logs", ".txt", App.getContext().cacheDir)

        assertTrue(LogExportUtil.save(App.getContext(), Uri.fromFile(file), "report"))

        assertEquals("report", file.readText())
    }

    @Test
    fun savingToAnUnusableLocationReportsFailure() {
        val destination = Uri.parse("content://files/logs.txt")
        val resolver = mock(ContentResolver::class.java)
        `when`(resolver.openOutputStream(destination)).thenThrow(FileNotFoundException())
        val context = mock(Context::class.java)
        `when`(context.contentResolver).thenReturn(resolver)

        assertFalse(LogExportUtil.save(context, destination, "report"))
    }

    @Test
    fun theFileNameIsAPlainTextFile() {
        assertTrue(LogExportUtil.suggestedFileName().matches(Regex("rollynn-logs-\\d{4}-\\d{2}-\\d{2}\\.txt")))
    }
}
