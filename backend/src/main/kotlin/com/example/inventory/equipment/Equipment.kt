package com.example.inventory.equipment

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

enum class EquipmentType { MAIN_COMPUTER, MONITOR, KEYBOARD, MOUSE }

/**
 * condition_score and minimum_condition are NUMERIC(3,2) in the schema: a 0..1 value with two
 * decimal places. Both entity columns, the matcher and the benchmark derive from these so the
 * schema fact is stated once in Kotlin.
 */
const val CONDITION_SCORE_PRECISION = 3
const val CONDITION_SCORE_SCALE = 2

enum class EquipmentState { AVAILABLE, RESERVED, ASSIGNED }

@Entity
@Table(name = "equipment")
class Equipment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: EquipmentType,
    @Column(nullable = false, length = 100)
    var brand: String,
    @Column(nullable = false, length = 100)
    var model: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: EquipmentState = EquipmentState.AVAILABLE,
    @Column(name = "condition_score", nullable = false, precision = CONDITION_SCORE_PRECISION, scale = CONDITION_SCORE_SCALE)
    var conditionScore: BigDecimal,
    @Column(name = "purchase_date", nullable = false)
    var purchaseDate: LocalDate,
)
