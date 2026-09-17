package com.example.inventory.equipment

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PastOrPresent
import java.math.BigDecimal
import java.time.LocalDate

data class CreateEquipmentRequest(
    val type: EquipmentType,
    @field:NotBlank @field:jakarta.validation.constraints.Size(max = 100) val brand: String,
    @field:NotBlank @field:jakarta.validation.constraints.Size(max = 100) val model: String,
    @field:DecimalMin("0.0") @field:DecimalMax("1.0") @field:jakarta.validation.constraints.Digits(integer = 1, fraction = 2)
    val conditionScore: BigDecimal,
    @field:PastOrPresent val purchaseDate: LocalDate,
)

data class EquipmentResponse(
    val id: Long,
    val type: EquipmentType,
    val brand: String,
    val model: String,
    val state: EquipmentState,
    val conditionScore: BigDecimal,
    val purchaseDate: LocalDate,
)

fun Equipment.toResponse() =
    EquipmentResponse(
        requireNotNull(id),
        type,
        brand,
        model,
        state,
        conditionScore,
        purchaseDate,
    )
