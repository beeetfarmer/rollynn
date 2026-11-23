package com.cappielloantonio.tempo.github.utils

import com.cappielloantonio.tempo.github.Github
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateUtilTest {
    @Test
    fun aHigherVersionIsNewerDespiteTheVPrefix() {
        // The app's own version is "v0.6.1", which used to make every check fail.
        assertTrue(UpdateUtil.isNewer("v0.6.2", "v0.6.1"))
        assertTrue(UpdateUtil.isNewer("v0.7.0", "v0.6.1"))
        assertTrue(UpdateUtil.isNewer("v1.0.0", "v0.6.1"))
    }

    @Test
    fun theSameVersionIsNotNewer() {
        assertFalse(UpdateUtil.isNewer("v0.6.1", "v0.6.1"))
        assertFalse(UpdateUtil.isNewer("0.6.1", "v0.6.1"))
    }

    @Test
    fun anOlderVersionIsNotNewer() {
        assertFalse(UpdateUtil.isNewer("v0.6.0", "v0.6.1"))
        assertFalse(UpdateUtil.isNewer("v0.5.9", "v0.6.1"))
    }

    @Test
    fun numbersAreComparedAsNumbersNotText() {
        assertTrue(UpdateUtil.isNewer("v0.6.10", "v0.6.9"))
        assertFalse(UpdateUtil.isNewer("v0.6.9", "v0.6.10"))
    }

    @Test
    fun differentLengthsCompareAsIfPaddedWithZeros() {
        assertFalse(UpdateUtil.isNewer("v0.7", "v0.7.0"))
        assertFalse(UpdateUtil.isNewer("v0.7.0", "v0.7"))
        assertTrue(UpdateUtil.isNewer("v0.7.1", "v0.7"))
        assertTrue(UpdateUtil.isNewer("v0.8", "v0.7.5"))
    }

    @Test
    fun suffixesAreIgnored() {
        assertTrue(UpdateUtil.isNewer("v0.7.0-beta", "v0.6.1"))
        assertFalse(UpdateUtil.isNewer("v0.6.1-hotfix", "v0.6.1"))
    }

    @Test
    fun aTagWithoutNumbersIsNeverNewer() {
        assertFalse(UpdateUtil.isNewer("latest", "v0.6.1"))
        assertFalse(UpdateUtil.isNewer("", "v0.6.1"))
        assertFalse(UpdateUtil.isNewer("v0.7.0", "dev"))
    }

    @Test
    fun aHugeNumberDoesNotCrash() {
        assertFalse(UpdateUtil.isNewer("v99999999999.0", "v0.6.1"))
    }

    @Test
    fun itChecksThisAppsRepository() {
        assertEquals("beeetfarmer", Github.getOwner())
        assertEquals("rollynn", Github.getRepo())
    }
}
