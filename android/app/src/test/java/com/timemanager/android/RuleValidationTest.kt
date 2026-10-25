package com.timemanager.android

import com.timemanager.android.domain.*
import org.junit.Assert.*
import org.junit.Test

class RuleValidationTest {

    @Test fun validAppRule() {
        val r = Rule(id=1, name="Discord", type=RuleType.APPLICATION, target="Discord", packageName="com.discord", dailyLimitSeconds=1800, action=Action.BLOCK)
        assertNotNull(r)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyNameFails() {
        Rule(id=1, name="", type=RuleType.APPLICATION, target="Discord", packageName="com.discord", dailyLimitSeconds=1800, action=Action.BLOCK)
    }

    @Test(expected = IllegalArgumentException::class)
    fun noLimitsFails() {
        Rule(id=1, name="X", type=RuleType.APPLICATION, target="X", packageName="com.x", dailyLimitSeconds=null, sessionLimitSeconds=null, action=Action.BLOCK)
    }

    @Test(expected = IllegalArgumentException::class)
    fun websiteNeedsDomain() {
        Rule(id=1, name="YT", type=RuleType.WEBSITE, target="YouTube", domain=null, dailyLimitSeconds=1800, action=Action.BLOCK)
    }

    @Test(expected = IllegalArgumentException::class)
    fun appMustNotHaveDomain() {
        Rule(id=1, name="Discord", type=RuleType.APPLICATION, target="Discord", packageName="com.discord", domain="example.com", dailyLimitSeconds=1800, action=Action.BLOCK)
    }

    @Test fun domainNormalizationMatches() {
        assertTrue(RuleMatcher.domainMatches("youtube.com", "m.youtube.com"))
        assertTrue(RuleMatcher.domainMatches("youtube.com", "youtube.com"))
        assertFalse(RuleMatcher.domainMatches("youtube.com", "notyoutube.com"))
        assertFalse(RuleMatcher.domainMatches("youtube.com", "youtube.com.evil.com"))
    }

    @Test fun packageMatchingExact() {
        assertTrue(RuleMatcher.packageMatches(listOf("com.discord"), "com.discord"))
        assertFalse(RuleMatcher.packageMatches(listOf("com.discord"), "com.discord.lite"))
    }

    @Test fun schedulePreserved() {
        val s = Schedule(days = setOf(java.time.DayOfWeek.MONDAY), windows = listOf(java.time.LocalTime.of(8,0) to java.time.LocalTime.of(22,0)))
        val r = Rule(id=1, name="Study", type=RuleType.APPLICATION, target="Games", packageName="com.test.game", dailyLimitSeconds=3600, action=Action.BLOCK, schedule=s)
        assertNotNull(r.schedule)
        assertEquals(1, r.schedule!!.windows.size)
    }
}
