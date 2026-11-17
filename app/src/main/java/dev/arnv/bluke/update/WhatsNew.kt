package dev.arnv.bluke.update

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.io.IOException

/** The last version whose changes the user has been shown (or that was freshly installed). */
const val LAST_SEEN_CHANGELOG_VERSION_PREFERENCE = "last_seen_changelog_version"

/** CHANGELOG.md is copied into the APK's assets at build time (see app/build.gradle.kts). */
const val CHANGELOG_ASSET = "CHANGELOG.md"

/** Never show more than this many releases at once, e.g. after skipping many updates. */
private const val MAX_WHATS_NEW_RELEASES = 5

/** One `### Added` / `### Improved & Fixed` group of bullet points. */
data class ChangelogGroup(val title: String?, val items: List<String>)

/** One `## [1.1.9] - 2026-11-16` section of the changelog. */
data class ChangelogRelease(val version: String, val date: String?, val groups: List<ChangelogGroup>)

private val releaseHeading = Regex("""^##\s+\[?([^\]\s]+)]?\s*(?:-\s*(.+))?$""")

/**
 * Splits a Keep-a-Changelog style file into releases, newest first as written.
 * Sections whose heading isn't a version (like "Unreleased") are left out.
 * Wrapped bullet lines are joined onto their bullet; plain paragraphs become their own item.
 */
fun parseChangelog(markdown: String): List<ChangelogRelease> {
    val releases = mutableListOf<ChangelogRelease>()
    var version: String? = null
    var date: String? = null
    val groups = mutableListOf<ChangelogGroup>()
    var groupTitle: String? = null
    var items = mutableListOf<String>()

    fun closeGroup() {
        if (items.isNotEmpty()) groups += ChangelogGroup(groupTitle, items)
        items = mutableListOf()
        groupTitle = null
    }

    fun closeRelease() {
        closeGroup()
        val v = version
        if (v != null && groups.isNotEmpty()) releases += ChangelogRelease(v, date, groups.toList())
        groups.clear()
        version = null
        date = null
    }

    for (raw in markdown.lineSequence()) {
        val line = raw.trim()
        when {
            line.startsWith("## ") -> {
                closeRelease()
                val match = releaseHeading.matchEntire(line)
                val candidate = match?.groupValues?.get(1)
                if (candidate != null && parseVersion(candidate) != null) {
                    version = displayVersion(candidate)
                    date = match.groupValues[2].trim().ifEmpty { null }
                }
            }
            version == null -> Unit
            line.startsWith("### ") -> {
                closeGroup()
                groupTitle = line.removePrefix("### ").trim()
            }
            line.startsWith("- ") || line.startsWith("* ") -> items += line.drop(2).trim()
            line.isEmpty() -> Unit
            items.isNotEmpty() && raw.startsWith(" ") -> items[items.lastIndex] = items.last() + " " + line
            else -> items += line
        }
    }
    closeRelease()
    return releases
}

/**
 * Releases newer than [lastSeen] up to and including [current], newest first.
 * With no [lastSeen] (updated from a version before this feature), only [current] is shown.
 */
fun releasesToShow(
    releases: List<ChangelogRelease>,
    lastSeen: String?,
    current: String,
): List<ChangelogRelease> =
    releases
        .filter { !isNewerVersion(it.version, current) }
        .filter { release ->
            if (lastSeen == null) !isNewerVersion(current, release.version) else isNewerVersion(release.version, lastSeen)
        }
        .take(MAX_WHATS_NEW_RELEASES)

/**
 * What to show on this launch, or an empty list. A fresh install records the current
 * version without showing anything; so does a version whose changelog has no entry.
 */
fun pendingWhatsNew(
    prefs: SharedPreferences,
    releases: List<ChangelogRelease>,
    currentVersion: String,
    isFreshInstall: Boolean,
): List<ChangelogRelease> {
    val lastSeen = prefs.getString(LAST_SEEN_CHANGELOG_VERSION_PREFERENCE, null)
    if (lastSeen == null && isFreshInstall) {
        prefs.markChangelogSeen(currentVersion)
        return emptyList()
    }
    if (lastSeen != null && !isNewerVersion(currentVersion, lastSeen)) return emptyList()
    return releasesToShow(releases, lastSeen, currentVersion).also {
        if (it.isEmpty()) prefs.markChangelogSeen(currentVersion)
    }
}

fun SharedPreferences.markChangelogSeen(version: String) {
    edit { putString(LAST_SEEN_CHANGELOG_VERSION_PREFERENCE, version) }
}

/** The bundled changelog, or an empty list if it is missing from this build. */
fun Context.loadBundledChangelog(): List<ChangelogRelease> =
    try {
        assets.open(CHANGELOG_ASSET).bufferedReader().use { parseChangelog(it.readText()) }
    } catch (_: IOException) {
        emptyList()
    }

/** True when this launch is the first since installing, not since an update. */
fun Context.isFreshInstall(): Boolean =
    try {
        val info = packageManager.getPackageInfo(packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    } catch (_: Exception) {
        false
    }
