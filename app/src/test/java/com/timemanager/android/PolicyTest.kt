package com.timemanager.android

import com.timemanager.android.domain.*
import org.junit.Assert.*
import org.junit.Test

class PolicyTest {

    private fun rule(mode: Mode, action: Action) = Rule(
        id=1, name="Test", type=RuleType.APPLICATION, target="Discord",
        packageName="com.discord", dailyLimitSeconds=60, action=action, mode=mode
    )

    @Test fun normalBlockGrandfathersFirst() {
        val p = KillPolicy()
        val r = rule(Mode.NORMAL, Action.BLOCK)
        val d = p.decide(r, "PREVENT_LAUNCH", setOf("com.discord"), firstEnforcement=true)
        assertTrue(d.packagesToBlock.isEmpty())
        assertEquals(listOf("com.discord"), d.grandfathered)
    }

    @Test fun normalBlockSecondBlocksNew() {
        val p = KillPolicy()
        val r = rule(Mode.NORMAL, Action.BLOCK)
        val first = p.decide(r, "PREVENT_LAUNCH", setOf("com.discord"), firstEnforcement=true)
        val second = p.decide(r, "PREVENT_LAUNCH", setOf("com.discord", "com.discord.lite"), grandfathered=first.grandfathered.toSet())
        assertEquals(listOf("com.discord.lite"), second.packagesToBlock)
        assertEquals(listOf("com.discord"), second.grandfathered)
    }

    @Test fun strictNeverGrandfathers() {
        val p = KillPolicy()
        val r = rule(Mode.STRICT, Action.BLOCK)
        val d = p.decide(r, "PREVENT_LAUNCH", setOf("com.discord"), firstEnforcement=true)
        assertEquals(listOf("com.discord"), d.packagesToBlock)
    }

    @Test fun closeAlwaysBlocks() {
        val p = KillPolicy()
        val r = rule(Mode.NORMAL, Action.CLOSE)
        val d = p.decide(r, "CLOSE_APP", setOf("com.discord"), firstEnforcement=true)
        assertEquals(listOf("com.discord"), d.packagesToBlock)
    }

    @Test fun protectedNeverBlocked() {
        val p = KillPolicy()
        val r = rule(Mode.STRICT, Action.CLOSE)
        val d = p.decide(r, "CLOSE_APP", setOf("com.timemanager.android", "com.android.systemui", "com.discord"))
        assertEquals(listOf("com.discord"), d.packagesToBlock)
        assertEquals(2, d.skipped.size)
    }
}
