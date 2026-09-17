package com.example.inventory.allocation

import com.example.inventory.equipment.Equipment
import com.example.inventory.equipment.EquipmentResponse
import com.example.inventory.equipment.EquipmentType
import com.example.inventory.equipment.toResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant

/**
 * Upper bound on slots per request. Also bounds the longest residual path the matcher can
 * build, which is what keeps its edge scores inside Long -- see AllocationMatcher.
 */
const val MAX_POLICY_SLOTS = 50

data class PolicySlotRequest(
    val type: EquipmentType,
    // Digits keeps the request at the column's NUMERIC(3,2) scale, so the threshold the matcher
    // enforces is the one Postgres stores and the failure reason reports.
    @field:DecimalMin("0.0") @field:DecimalMax("1.0") @field:Digits(integer = 1, fraction = 2)
    val minimumCondition: BigDecimal? = null,
    @field:Size(max = 100) val preferredBrand: String? = null,
    val preferRecent: Boolean = true,
) {
    /**
     * The hard constraints, in one place. The matcher uses this to decide whether an edge
     * exists; the failure diagnosis uses it to count supply. Both must agree, so both call this.
     */
    fun accepts(item: Equipment): Boolean = item.type == type && (minimumCondition == null || item.conditionScore >= minimumCondition)

    /** Blank brand means no preference; surrounding whitespace must not defeat a real one. */
    fun normalised(): PolicySlotRequest = copy(preferredBrand = preferredBrand?.trim()?.takeIf(String::isNotEmpty))
}

data class CreateAllocationRequest(
    @field:NotBlank @field:Size(max = 100) val employeeId: String,
    @field:NotEmpty @field:Size(max = MAX_POLICY_SLOTS) @field:Valid val policy: List<PolicySlotRequest>,
)

data class PolicySlotResponse(
    val position: Int,
    val type: EquipmentType,
    val minimumCondition: BigDecimal?,
    val preferredBrand: String?,
    val preferRecent: Boolean,
)

data class AllocationSummaryResponse(
    val id: Long,
    val employeeId: String,
    val state: AllocationState,
    val itemCount: Int,
    val failureReason: String?,
    val createdAt: Instant,
)

data class AllocationDetailResponse(
    val id: Long,
    val employeeId: String,
    val state: AllocationState,
    val policy: List<PolicySlotResponse>,
    val allocatedEquipments: List<EquipmentResponse>,
    val failureReason: String?,
    val createdAt: Instant,
)

fun AllocationRequestEntity.toSummary() =
    AllocationSummaryResponse(
        requireNotNull(id),
        employeeId,
        state,
        items.size,
        failureReason,
        createdAt,
    )

fun AllocationRequestEntity.toDetail() =
    AllocationDetailResponse(
        id = requireNotNull(id),
        employeeId = employeeId,
        state = state,
        policy = policy.map { PolicySlotResponse(it.position, it.type, it.minimumCondition, it.preferredBrand, it.preferRecent) },
        allocatedEquipments = items.sortedBy { it.id }.map { it.equipment.toResponse() },
        failureReason = failureReason,
        createdAt = createdAt,
    )
