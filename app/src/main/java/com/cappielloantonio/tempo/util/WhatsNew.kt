package com.cappielloantonio.tempo.util

import android.content.Context
import com.cappielloantonio.tempo.BuildConfig
import java.io.IOException

/** Shows each version's notes once, from app/src/main/assets/changelog/<versionName>.txt. */
object WhatsNew {
    /** The notes to show on this launch, or null. Marks the version as seen either way. */
    @JvmStatic
    fun notesToShow(context: Context): String? =
        notesToShow(BuildConfig.VERSION_NAME, isFreshInstall(context)) { read(context, it) }

    internal fun notesToShow(version: String, freshInstall: Boolean, notesFor: (String) -> String?): String? {
        val seen = Preferences.getWhatsNewSeenVersion()
        if (seen == version) return null
        Preferences.setWhatsNewSeenVersion(version)
        // Nothing stored is a fresh install, or an update from before this existed.
        if (seen == null && freshInstall) return null
        return notesFor(version)
    }

    internal fun read(context: Context, version: String): String? = try {
        context.assets.open("changelog/$version.txt").bufferedReader().use { it.readText() }.trim().ifEmpty { null }
    } catch (e: IOException) {
        null
    }

    private fun isFreshInstall(context: Context): Boolean {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.firstInstallTime == info.lastUpdateTime
    }
}
