package com.example.inventory.allocation

import com.example.inventory.allocation.AllocationFixtures.equipment
import com.example.inventory.allocation.AllocationFixtures.slot
import com.example.inventory.allocation.AllocationFixtures.today
import com.example.inventory.equipment.Equipment
import com.example.inventory.equipment.EquipmentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Edge cases for allocation, grouped by the surface they probe. Each test carries a one-line
 * reason: the situation that would break and why it matters to an operator or to correctness.
 * These deliberately avoid restating the tier and diagnosis tests; they sit at the boundaries
 * those tests assume.
 */
class AllocationEdgeCasesTest {
    private val matcher = AllocationMatcher()

    private fun assignedIds(
        slots: List<PolicySlotRequest>,
        items: List<Equipment>,
    ): List<Long> =
        matcher
            .match(slots, items, today)!!
            .sortedBy { it.slotIndex }
            .map { it.equipment.id!! }

    @Nested
    inner class Matcher {
        @Test
        fun `empty policy yields an empty assignment, not a failure`() {
            // A request with no slots is trivially satisfied; returning null would mark it FAILED.
            assertThat(matcher.match(emptyList(), listOf(equipment(1)), today)).isEmpty()
        }

        @Test
        fun `no candidates at all fails cleanly`() {
            // An empty catalogue must produce null, not an index error from an empty graph.
            assertThat(matcher.match(listOf(slot()), emptyList(), today)).isNull()
        }

        @Test
        fun `item exactly at the minimum is accepted`() {
            // The hard constraint is "at or above"; a boundary item must qualify.
            assertThat(assignedIds(listOf(slot(minimum = "0.85")), listOf(equipment(1, condition = "0.85"))))
                .containsExactly(1L)
        }

        @Test
        fun `item one hundredth below the minimum is rejected`() {
            // The column stores two decimals, so 0.84 is the nearest possible miss.
            assertThat(matcher.match(listOf(slot(minimum = "0.85")), listOf(equipment(1, condition = "0.84")), today)).isNull()
        }

        @Test
        fun `brand preference matches regardless of case`() {
            // Operators type brands by hand; "apple" must still prefer the Apple item.
            val apple = equipment(1, condition = "0.50", brand = "Apple")
            val dell = equipment(2, condition = "0.99", brand = "Dell")
            assertThat(assignedIds(listOf(slot(brand = "apple")), listOf(dell, apple))).containsExactly(apple.id)
        }

        @Test
        fun `preferred brand that nobody stocks still allocates`() {
            // A preference is soft; an unavailable brand must not turn a feasible request into a failure.
            assertThat(assignedIds(listOf(slot(brand = "Framework")), listOf(equipment(1, brand = "Dell")))).containsExactly(1L)
        }

        @Test
        fun `future purchase date counts as newest, not as negative age`() {
            // A mistyped date in the future must clamp to "today", never crash or invert the recency tier.
            val future = equipment(1, condition = "0.50", ageDays = -30)
            val todayItem = equipment(2, condition = "0.99", ageDays = 0)
            // Both clamp to age 0, so recency ties and condition decides.
            assertThat(assignedIds(listOf(slot()), listOf(future, todayItem))).containsExactly(todayItem.id)
        }

        @Test
        fun `ages beyond the recency cap tie instead of overflowing`() {
            // Anything older than ~274 years clamps; two ancient items must fall through to condition.
            val ancientWorse = equipment(1, condition = "0.50", ageDays = 200_000)
            val ancientBetter = equipment(2, condition = "0.60", ageDays = 300_000)
            assertThat(assignedIds(listOf(slot()), listOf(ancientWorse, ancientBetter))).containsExactly(ancientBetter.id)
        }

        @Test
        fun `equipment without an id still scores`() {
            // Unsaved entities have a null id; the tie-break must treat that as zero, not throw.
            val unsaved = equipment(1).also { it.id = null }
            val result = matcher.match(listOf(slot()), listOf(unsaved), today)
            assertThat(result).hasSize(1)
            assertThat(result!!.single().equipment).isSameAs(unsaved)
        }

        @Test
        fun `condition outside zero to one is clamped`() {
            // The schema forbids it, but a corrupt row must rank as the maximum rather than overflow a tier.
            val outOfRange = equipment(1, condition = "1.50")
            val perfect = equipment(2, condition = "1.00")
            assertThat(matcher.score(slot(), outOfRange, today)).isEqualTo(matcher.score(slot(), perfect, today) + 1)
        }

        @Test
        fun `three identical slots receive three distinct items`() {
            // Distinctness comes from the unit capacity on item->sink; losing it would hand one item out three times.
            val slots = List(3) { slot() }
            val items = (1L..3L).map { equipment(it) }
            assertThat(assignedIds(slots, items)).containsExactlyInAnyOrder(1L, 2L, 3L)
        }

        @Test
        fun `only one slot can receive the single preferred item`() {
            // Two slots prefer LG and one LG exists: both must be filled and LG must be given out once.
            val slots = listOf(slot(brand = "LG"), slot(brand = "LG"))
            val items = listOf(equipment(1, brand = "LG"), equipment(2, brand = "Dell"))
            assertThat(assignedIds(slots, items)).containsExactlyInAnyOrder(1L, 2L)
        }

        @Test
        fun `a preference chain two levels deep is re-routed`() {
            // Greedy by preference takes I2 for C, then I1 for B, leaving A nothing. Flow must
            // undo two choices, not one: A=I1, B=I2, C=I3 is the only full assignment.
            val i1 = equipment(1, condition = "0.95", brand = "X")
            val i2 = equipment(2, condition = "0.85", brand = "Y")
            val i3 = equipment(3, condition = "0.75", brand = "Z")
            val a = slot(minimum = "0.90")
            val b = slot(minimum = "0.80", brand = "X")
            val c = slot(minimum = "0.70", brand = "Y")
            assertThat(assignedIds(listOf(c, b, a), listOf(i1, i2, i3))).containsExactly(i3.id, i2.id, i1.id)
        }

        @Test
        fun `a better item of the wrong type is never chosen`() {
            // Type is a hard constraint; a pristine keyboard must not fill a monitor slot.
            val keyboard = equipment(1, EquipmentType.KEYBOARD, condition = "1.00")
            val monitor = equipment(2, EquipmentType.MONITOR, condition = "0.50")
            assertThat(assignedIds(listOf(slot(EquipmentType.MONITOR)), listOf(keyboard, monitor))).containsExactly(monitor.id)
        }

        @Test
        fun `matching leaves its inputs untouched and is repeatable`() {
            // The service reuses the candidate list after matching; a mutated list or a
            // different second answer would corrupt the reservation it then writes.
            val slots = listOf(slot(minimum = "0.85"), slot(brand = "LG"))
            val items =
                listOf(equipment(1, condition = "0.90", brand = "LG"), equipment(2, condition = "0.80"), equipment(3, condition = "0.70"))
            val snapshot = items.map { Triple(it.id, it.conditionScore, it.state) }

            val first = assignedIds(slots, items)
            val second = assignedIds(slots, items)

            assertThat(second).isEqualTo(first)
            assertThat(items.map { Triple(it.id, it.conditionScore, it.state) }).isEqualTo(snapshot)
        }

        @Test
        fun `ids past the tie-break range stay deterministic`() {
            // Above ten million every id ties at zero; the result must still be the same on every call.
            val items = listOf(equipment(30_000_000), equipment(20_000_000))
            assertThat(assignedIds(listOf(slot()), items)).isEqualTo(assignedIds(listOf(slot()), items))
        }

        @Test
        fun `policy at the API maximum with exactly enough stock fills every slot`() {
            // The overflow margin is sized for MAX_POLICY_SLOTS; the largest legal request must succeed.
            val slots = List(MAX_POLICY_SLOTS) { slot() }
            val items = (1L..MAX_POLICY_SLOTS.toLong()).map { equipment(it) }
            assertThat(assignedIds(slots, items)).hasSize(MAX_POLICY_SLOTS).doesNotHaveDuplicates()
        }
    }

    @Nested
    inner class Diagnosis {
        @Test
        fun `supply counts only the slot's own type`() {
            // Five keyboards must not make a monitor shortfall read "5 available".
            val slots = listOf(slot(EquipmentType.MONITOR, minimum = "0.50"))
            val stock = (1L..5L).map { equipment(it, EquipmentType.KEYBOARD, condition = "0.99") }
            assertThat(AllocationShortfalls.explain(slots, stock))
                .isEqualTo("No valid equipment set is available (monitor: 1 slot needs condition >= 0.50, 0 available).")
        }

        @Test
        fun `every violated threshold is reported, tightest first`() {
            // An operator fixing the 0.90 gap must also learn the 0.70 pool is short by three.
            val slots = listOf(slot(minimum = "0.70"), slot(minimum = "0.90"), slot(minimum = "0.70"), slot(minimum = "0.70"))
            val stock = listOf(equipment(1, condition = "0.75"))
            assertThat(AllocationShortfalls.find(slots, stock)).containsExactly(
                AllocationShortfall(EquipmentType.MONITOR, BigDecimal("0.90"), required = 1, available = 0),
                AllocationShortfall(EquipmentType.MONITOR, BigDecimal("0.70"), required = 4, available = 1),
            )
        }

        @Test
        fun `exactly enough supply is not a shortfall`() {
            // Demand equal to supply is the boundary of Hall's condition and must pass.
            val slots = listOf(slot(minimum = "0.80"), slot(minimum = "0.80"))
            val stock = listOf(equipment(1, condition = "0.80"), equipment(2, condition = "0.80"))
            assertThat(AllocationShortfalls.find(slots, stock)).isEmpty()
        }

        @Test
        fun `threshold is rendered with two decimals however the request spelled it`() {
            // A request written as 0.9 must not read differently from one written as 0.90.
            assertThat(AllocationShortfalls.explain(listOf(slot(minimum = "0.9")), emptyList()))
                .contains("condition >= 0.90")
        }

        @Test
        fun `minimum of one is satisfiable only by a perfect item`() {
            // The top of the range is a legal minimum; a 1.00 item meets it and 0.99 does not.
            assertThat(AllocationShortfalls.find(listOf(slot(minimum = "1.00")), listOf(equipment(1, condition = "1.00")))).isEmpty()
            assertThat(AllocationShortfalls.find(listOf(slot(minimum = "1.00")), listOf(equipment(1, condition = "0.99"))))
                .containsExactly(AllocationShortfall(EquipmentType.MONITOR, BigDecimal("1.00"), required = 1, available = 0))
        }

        @Test
        fun `empty policy explains nothing`() {
            // No slots means no shortfall; explain() must fall back rather than emit "available ()".
            assertThat(AllocationShortfalls.find(emptyList(), listOf(equipment(1)))).isEmpty()
            assertThat(AllocationShortfalls.explain(emptyList(), emptyList()))
                .isEqualTo("No valid non-overlapping equipment set satisfies every policy slot.")
        }
    }

    @Nested
    inner class Request {
        @Test
        fun `blank brand normalises to no preference`() {
            // Whitespace-only input from a text field must not become a brand nobody stocks.
            assertThat(slot(brand = "   ").normalised().preferredBrand).isNull()
        }

        @Test
        fun `padded brand normalises to its trimmed form`() {
            // The matcher compares exact strings; " Dell " must reach it as "Dell".
            assertThat(slot(brand = " Dell ").normalised().preferredBrand).isEqualTo("Dell")
        }

        @Test
        fun `normalisation keeps every other field`() {
            // Trimming the brand must not reset the type, minimum or recency flag.
            val original = slot(EquipmentType.MOUSE, minimum = "0.60", brand = " Logitech", preferRecent = false)
            assertThat(original.normalised()).isEqualTo(original.copy(preferredBrand = "Logitech"))
        }

        @Test
        fun `accepts is false for the wrong type even when condition qualifies`() {
            // The shared predicate is the single source of truth for hard constraints; type must be part of it.
            assertThat(slot(EquipmentType.MONITOR).accepts(equipment(1, EquipmentType.KEYBOARD, condition = "1.00"))).isFalse()
        }
    }
}
