package dev.ihorshevchuk.piper.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version-aware staging decisions for the bundled espeak-ng data.
 *
 * espeak-ng's Android port re-extracts its data when the bundled version
 * hash changes; a stage-once marker never picks up data upgrades. These
 * tests pin the decision logic: the installer must re-stage when the
 * bundled version differs from what is on disk.
 */
class EspeakDataInstallerTest {

    @Test
    fun `missing destination triggers staging`() {
        assertTrue(EspeakDataInstaller.shouldRestage(
            destIsDirectory = false,
            stagedVersion = null,
            assetVersion = "1.59.12"
        ))
    }

    @Test
    fun `matching version needs no restage`() {
        assertFalse(EspeakDataInstaller.shouldRestage(
            destIsDirectory = true,
            stagedVersion = "1.59.12",
            assetVersion = "1.59.12"
        ))
    }

    @Test
    fun `version bump triggers restage`() {
        assertTrue(EspeakDataInstaller.shouldRestage(
            destIsDirectory = true,
            stagedVersion = "1.59.12",
            assetVersion = "1.60.01"
        ))
    }

    @Test
    fun `legacy unversioned marker triggers one restage`() {
        // Installs staged by the old stage-once marker carry "1"; the first
        // run with versioned data must refresh them exactly once.
        assertTrue(EspeakDataInstaller.shouldRestage(
            destIsDirectory = true,
            stagedVersion = "1",
            assetVersion = "1.59.12"
        ))
    }

    @Test
    fun `missing staged marker triggers restage`() {
        assertTrue(EspeakDataInstaller.shouldRestage(
            destIsDirectory = true,
            stagedVersion = null,
            assetVersion = "1.59.12"
        ))
    }
}
