package com.example.inventory.allocation

import com.example.inventory.allocation.AllocationFixtures.equipment
import com.example.inventory.allocation.AllocationFixtures.slot
import com.example.inventory.allocation.AllocationFixtures.today
import com.example.inventory.equipment.Equipment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins the documented preference order: brand > recency > condition > id, each tier strictly
 * dominating everything below it. Every case sets the higher tier against the *maximum*
 * possible swing of all lower tiers combined, so a regression in the unit sizes fails here
 * rather than showing up as an odd allocation in production.
 */
class AllocationScoringTest {
    private val matcher = AllocationMatcher()

    private fun chosen(
        slot: PolicySlotRequest,
        vararg items: Equipment,
    ): Long =
        matcher
            .match(listOf(slot), items.toList(), today)!!
            .single()
            .equipment.id!!

    @Test
    fun `tier units are strictly ordered`() {
        assertThat(AllocationMatcher.tierInvariantsHold()).isTrue()
    }

    @Test
    fun `longest residual path cannot overflow Long`() {
        // SPFA sums costs along a path that can traverse one slot-to-item edge per slot.
        val worstPathSum = Math.multiplyExact(AllocationMatcher.MAX_EDGE_SCORE, MAX_POLICY_SLOTS.toLong())
        assertThat(worstPathSum).isLessThan(Long.MAX_VALUE / 10)
    }

    @Test
    fun `brand match beats maximum recency, condition and id advantage`() {
        val preferred = equipment(id = 9_999_999, condition = "0.00", brand = "LG", ageDays = 100_000)
        val other = equipment(id = 1, condition = "1.00", brand = "Dell", ageDays = 0)
        assertThat(chosen(slot(brand = "LG"), preferred, other)).isEqualTo(preferred.id)
    }

    @Test
    fun `one day of recency beats maximum condition and id advantage`() {
        val newer = equipment(id = 9_999_999, condition = "0.00", ageDays = 0)
        val older = equipment(id = 1, condition = "1.00", ageDays = 1)
        assertThat(chosen(slot(), newer, older)).isEqualTo(newer.id)
    }

    @Test
    fun `recency is ignored when the slot does not prefer recent`() {
        val newer = equipment(id = 2, condition = "0.50", ageDays = 0)
        val older = equipment(id = 1, condition = "0.90", ageDays = 1_000)
        assertThat(chosen(slot(preferRecent = false), newer, older)).isEqualTo(older.id)
    }

    @Test
    fun `condition beats the id tie-break -- the original probe case`() {
        val better = equipment(id = 9_500, condition = "0.99")
        val worse = equipment(id = 5, condition = "0.90")
        assertThat(chosen(slot(), better, worse)).isEqualTo(better.id)
    }

    @Test
    fun `one hundredth of condition beats the maximum id advantage`() {
        val better = equipment(id = 9_999_999, condition = "0.51")
        val worse = equipment(id = 1, condition = "0.50")
        assertThat(chosen(slot(), better, worse)).isEqualTo(better.id)
    }

    @Test
    fun `lower id wins when everything else is equal`() {
        val low = equipment(id = 20)
        val high = equipment(id = 30)
        assertThat(chosen(slot(), high, low)).isEqualTo(low.id)
    }

    @Test
    fun `tie-break still discriminates up to ten million ids`() {
        val low = equipment(id = 9_999_998)
        val high = equipment(id = 9_999_999)
        assertThat(chosen(slot(), high, low)).isEqualTo(low.id)
    }

    @Test
    fun `score rises with each signal`() {
        // Unit-level counterpart of the tier tests above: those pin dominance through match();
        // this pins direction through score() so a sign error is caught at the source.
        val preferLg = slot(brand = "LG")
        val baseline = matcher.score(preferLg, equipment(id = 100, condition = "0.50", ageDays = 10), today)
        assertThat(matcher.score(preferLg, equipment(id = 100, condition = "0.50", brand = "LG", ageDays = 10), today))
            .isGreaterThan(baseline)
        assertThat(matcher.score(preferLg, equipment(id = 100, condition = "0.50", ageDays = 9), today)).isGreaterThan(baseline)
        assertThat(matcher.score(preferLg, equipment(id = 100, condition = "0.51", ageDays = 10), today)).isGreaterThan(baseline)
        assertThat(matcher.score(preferLg, equipment(id = 99, condition = "0.50", ageDays = 10), today)).isGreaterThan(baseline)
    }

    @Test
    fun `the README competing-slots instance gives the constrained slot the only qualifying item`() {
        // Slot A needs >= 0.85; slot B merely prefers LG. M1 is both the only item clearing A's
        // minimum and the only LG. Greedy-by-preference gives M1 to B and fails A; flow gives
        // M1 to A and M2 to B.
        val slotA = slot(minimum = "0.85")
        val slotB = slot(brand = "LG")
        val m1 = equipment(id = 1, condition = "0.90", brand = "LG")
        val m2 = equipment(id = 2, condition = "0.80", brand = "Dell")
        val m3 = equipment(id = 3, condition = "0.70", brand = "Dell")

        val result = matcher.match(listOf(slotA, slotB), listOf(m1, m2, m3), today)!!

        assertThat(result.single { it.slotIndex == 0 }.equipment.id).isEqualTo(m1.id)
        assertThat(result.single { it.slotIndex == 1 }.equipment.id).isEqualTo(m2.id)
    }
}
