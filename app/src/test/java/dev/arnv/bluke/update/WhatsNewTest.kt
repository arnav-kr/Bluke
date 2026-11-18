package dev.arnv.bluke.update

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WhatsNewTest {
    private lateinit var prefs: SharedPreferences

    private val changelog = """
        # Changelog

        Intro text that is not a release.

        ## [Unreleased]

        ### Added
        - Not out yet

        ## [1.2.0] - 2026-12-01

        ### Added
        - **New thing:** does stuff
          and wraps onto a second line.

        ## [1.1.9] - 2026-11-16

        ### Added
        - Themes
        ### Improved & Fixed
        - A fix

        ## [1.1.8] - 2026-11-15

        ### Added
        - Trackpad
    """.trimIndent()

    private val releases = parseChangelog(changelog)

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun parsesReleasesAndSkipsUnreleased() {
        assertEquals(listOf("1.2.0", "1.1.9", "1.1.8"), releases.map { it.version })
        assertEquals("2026-11-16", releases[1].date)
        assertEquals(listOf("Added", "Improved & Fixed"), releases[1].groups.map { it.title })
        assertEquals(listOf("**New thing:** does stuff and wraps onto a second line."), releases[0].groups[0].items)
    }

    @Test
    fun freshInstallShowsNothingAndRemembersVersion() {
        assertTrue(pendingWhatsNew(prefs, releases, "1.1.9", isFreshInstall = true).isEmpty())
        assertEquals("1.1.9", prefs.getString(LAST_SEEN_CHANGELOG_VERSION_PREFERENCE, null))
    }

    @Test
    fun updateFromBeforeThisFeatureShowsOnlyCurrentVersion() {
        val shown = pendingWhatsNew(prefs, releases, "1.1.9", isFreshInstall = false)
        assertEquals(listOf("1.1.9"), shown.map { it.version })
    }

    @Test
    fun updateShowsEverythingSinceLastSeen() {
        prefs.markChangelogSeen("1.1.8")
        val shown = pendingWhatsNew(prefs, releases, "1.2.0", isFreshInstall = false)
        assertEquals(listOf("1.2.0", "1.1.9"), shown.map { it.version })
    }

    @Test
    fun sameVersionShowsNothing() {
        prefs.markChangelogSeen("1.1.9")
        assertTrue(pendingWhatsNew(prefs, releases, "1.1.9", isFreshInstall = false).isEmpty())
    }

    @Test
    fun versionWithoutEntryShowsNothingAndIsMarkedSeen() {
        prefs.markChangelogSeen("1.2.0")
        assertTrue(pendingWhatsNew(prefs, releases, "1.2.1", isFreshInstall = false).isEmpty())
        assertEquals("1.2.1", prefs.getString(LAST_SEEN_CHANGELOG_VERSION_PREFERENCE, null))
    }

    @Test
    fun realChangelogHasAnEntryForEveryForkRelease() {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { it.resolve("CHANGELOG.md") }
            .first { it.exists() }
        val parsed = parseChangelog(file.readText())
        assertTrue(parsed.any { it.version == "1.1.9" })
        assertTrue(parsed.all { it.groups.isNotEmpty() })
    }
}
