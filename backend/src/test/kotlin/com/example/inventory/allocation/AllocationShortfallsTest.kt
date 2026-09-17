package com.example.inventory.allocation

import com.example.inventory.allocation.AllocationFixtures.equipment
import com.example.inventory.allocation.AllocationFixtures.slot
import com.example.inventory.allocation.AllocationFixtures.today
import com.example.inventory.allocation.AllocationShortfalls.displayName
import com.example.inventory.equipment.EquipmentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Random

class AllocationShortfallsTest {
    private val matcher = AllocationMatcher()

    @Test
    fun `names the tight threshold that the old per-type count could not see`() {
        // Two monitors exist and two are wanted, so a per-type count says "no shortage".
        // The real problem: the slot needing 0.90 has zero candidates.
        val slots = listOf(slot(minimum = "0.90"), slot())
        val stock = listOf(equipment(1, condition = "0.50"), equipment(2, condition = "0.60"))

        assertThat(matcher.match(slots, stock, today)).isNull()
        assertThat(AllocationShortfalls.find(slots, stock)).containsExactly(
            AllocationShortfall(EquipmentType.MONITOR, BigDecimal("0.90"), required = 1, available = 0),
        )
        assertThat(AllocationShortfalls.explain(slots, stock))
            .isEqualTo("No valid equipment set is available (monitor: 1 slot needs condition >= 0.90, 0 available).")
    }

    @Test
    fun `reports competition between slots at the same threshold`() {
        val slots = listOf(slot(minimum = "0.85"), slot(minimum = "0.85"))
        val stock = listOf(equipment(1, condition = "0.90"), equipment(2, condition = "0.80"), equipment(3, condition = "0.70"))

        assertThat(matcher.match(slots, stock, today)).isNull()
        assertThat(AllocationShortfalls.explain(slots, stock))
            .isEqualTo("No valid equipment set is available (monitor: 2 slots need condition >= 0.85, 1 available).")
    }

    @Test
    fun `accumulates demand across thresholds within one type`() {
        // One slot at 0.90 is satisfiable alone; adding two slots at 0.70 over-subscribes the
        // looser pool, which the 0.90 slot also draws from.
        val slots = listOf(slot(minimum = "0.90"), slot(minimum = "0.70"), slot(minimum = "0.70"))
        val stock = listOf(equipment(1, condition = "0.95"), equipment(2, condition = "0.75"))

        assertThat(matcher.match(slots, stock, today)).isNull()
        assertThat(AllocationShortfalls.find(slots, stock)).containsExactly(
            AllocationShortfall(EquipmentType.MONITOR, BigDecimal("0.70"), required = 3, available = 2),
        )
    }

    @Test
    fun `reports each type independently in one sentence`() {
        val slots = listOf(slot(EquipmentType.MAIN_COMPUTER, minimum = "0.80"), slot(EquipmentType.KEYBOARD))
        val stock = listOf(equipment(1, EquipmentType.MAIN_COMPUTER, condition = "0.70"))

        assertThat(AllocationShortfalls.explain(slots, stock))
            .isEqualTo(
                "No valid equipment set is available " +
                    "(main computer: 1 slot needs condition >= 0.80, 0 available; keyboard: 1 slot needs any condition, 0 available).",
            )
    }

    @Test
    fun `feasible input yields no shortfalls and the generic fallback`() {
        // The README's greedy counterexample: feasible, so the diagnosis must stay silent.
        val slots = listOf(slot(minimum = "0.85"), slot())
        val stock = listOf(equipment(1, condition = "0.90"), equipment(2, condition = "0.80"), equipment(3, condition = "0.70"))

        assertThat(matcher.match(slots, stock, today)).isNotNull
        assertThat(AllocationShortfalls.find(slots, stock)).isEmpty()
        assertThat(AllocationShortfalls.explain(slots, stock))
            .isEqualTo("No valid non-overlapping equipment set satisfies every policy slot.")
    }

    @Test
    fun `thresholds that differ only in scale are one threshold`() {
        val slots = listOf(slot(minimum = "0.8"), slot(minimum = "0.80"))
        val stock = listOf(equipment(1, condition = "0.85"))

        val shortfalls = AllocationShortfalls.find(slots, stock)
        assertThat(shortfalls).hasSize(1)
        assertThat(shortfalls.single().required).isEqualTo(2)
    }

    @Test
    fun `explicit zero minimum is no minimum`() {
        val slots = listOf(slot(EquipmentType.MOUSE, minimum = "0.00"), slot(EquipmentType.MOUSE))

        assertThat(AllocationShortfalls.find(slots, emptyList())).containsExactly(
            AllocationShortfall(EquipmentType.MOUSE, minimumCondition = null, required = 2, available = 0),
        )
        assertThat(AllocationShortfalls.explain(slots, emptyList())).contains("mouse: 2 slots need any condition, 0 available")
    }

    @Test
    fun `largest allowed policy is explained in full`() {
        // MAX_POLICY_SLOTS slots, every one unsatisfiable, spread across all types and several
        // thresholds. failure_reason is TEXT, so nothing is dropped and every type is named.
        val slots =
            (1..MAX_POLICY_SLOTS).map { index ->
                slot(EquipmentType.entries[index % EquipmentType.entries.size], minimum = "0.${50 + index % 5}")
            }

        val message = AllocationShortfalls.explain(slots, emptyList())

        EquipmentType.entries.forEach { type -> assertThat(message).contains("${type.displayName()}:") }
        assertThat(message).contains("condition >= 0.54").contains("condition >= 0.50")
    }

    @Test
    fun `diagnosis agrees with the matcher on random instances`() {
        // Hall's condition on nested threshold sets is exact, so on every instance the finder
        // must report a shortfall exactly when the matcher returns null.
        val random = Random(42)
        repeat(500) {
            val slots = (1..random.nextInt(1, 6)).map { slot(minimum = if (random.nextBoolean()) "0.${50 + random.nextInt(50)}" else null) }
            val stock = (1..random.nextInt(0, 6)).map { id -> equipment(id.toLong(), condition = "0.${50 + random.nextInt(50)}") }

            val feasible = matcher.match(slots, stock, today) != null
            val diagnosed = AllocationShortfalls.find(slots, stock).isEmpty()
            assertThat(diagnosed).`as`("slots=$slots stock=${stock.map { it.conditionScore }}").isEqualTo(feasible)
        }
    }
}
