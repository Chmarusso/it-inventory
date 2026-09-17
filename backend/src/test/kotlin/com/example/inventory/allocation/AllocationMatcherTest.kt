package com.example.inventory.allocation

import com.example.inventory.equipment.Equipment
import com.example.inventory.equipment.EquipmentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class AllocationMatcherTest {
    private val matcher = AllocationMatcher()
    private val today = LocalDate.of(2026, 1, 1)

    @Test
    fun `finds full assignment where a greedy choice would block a constrained slot`() {
        val flexible = PolicySlotRequest(EquipmentType.MONITOR, BigDecimal("0.70"), preferredBrand = "Dell")
        val constrained = PolicySlotRequest(EquipmentType.MONITOR, BigDecimal("0.90"))
        val high = equipment(1, "Dell", "0.95")
        val low = equipment(2, "LG", "0.80")

        val result = matcher.match(listOf(flexible, constrained), listOf(high, low), today)

        assertThat(result).isNotNull
        assertThat(result!!.first { it.slotIndex == 0 }.equipment.id).isEqualTo(2)
        assertThat(result.first { it.slotIndex == 1 }.equipment.id).isEqualTo(1)
    }

    @Test
    fun `returns null when every slot cannot be satisfied`() {
        val slots =
            listOf(
                PolicySlotRequest(EquipmentType.MONITOR),
                PolicySlotRequest(EquipmentType.MONITOR),
            )
        assertThat(matcher.match(slots, listOf(equipment(1, "Dell", "0.95")), today)).isNull()
    }

    @Test
    fun `uses preferred brand when all hard constraints are met`() {
        val slot = PolicySlotRequest(EquipmentType.MONITOR, preferredBrand = "LG")
        val result =
            matcher.match(
                listOf(slot),
                listOf(equipment(1, "Dell", "0.99"), equipment(2, "LG", "0.80")),
                today,
            )
        assertThat(result!!.single().equipment.brand).isEqualTo("LG")
    }

    private fun equipment(
        id: Long,
        brand: String,
        condition: String,
    ) = Equipment(
        id = id,
        type = EquipmentType.MONITOR,
        brand = brand,
        model = "Model $id",
        conditionScore = BigDecimal(condition),
        purchaseDate = LocalDate.of(2025, 1, id.toInt()),
    )
}
