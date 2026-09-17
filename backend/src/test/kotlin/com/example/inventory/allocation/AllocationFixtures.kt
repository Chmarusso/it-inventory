package com.example.inventory.allocation

import com.example.inventory.equipment.Equipment
import com.example.inventory.equipment.EquipmentType
import java.math.BigDecimal
import java.time.LocalDate

/** Shared builders for allocation tests, so a new required column is one edit rather than one per file. */
object AllocationFixtures {
    val today: LocalDate = LocalDate.of(2026, 1, 1)

    fun equipment(
        id: Long,
        type: EquipmentType = EquipmentType.MONITOR,
        condition: String = "0.90",
        brand: String = "Dell",
        ageDays: Long = 0,
    ) = Equipment(
        id = id,
        type = type,
        brand = brand,
        model = "m$id",
        conditionScore = BigDecimal(condition),
        purchaseDate = today.minusDays(ageDays),
    )

    fun slot(
        type: EquipmentType = EquipmentType.MONITOR,
        minimum: String? = null,
        brand: String? = null,
        preferRecent: Boolean = true,
    ) = PolicySlotRequest(
        type = type,
        minimumCondition = minimum?.let(::BigDecimal),
        preferredBrand = brand,
        preferRecent = preferRecent,
    )
}
