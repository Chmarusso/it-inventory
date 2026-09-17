package com.example.inventory.allocation

import com.example.inventory.equipment.CONDITION_SCORE_PRECISION
import com.example.inventory.equipment.CONDITION_SCORE_SCALE
import com.example.inventory.equipment.Equipment
import com.example.inventory.equipment.EquipmentType
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

enum class AllocationState { RESERVED, CONFIRMED, CANCELLED, FAILED }

@Entity
@Table(name = "allocation_request")
class AllocationRequestEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "employee_id", nullable = false, length = 100)
    var employeeId: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: AllocationState,
    @Column(name = "failure_reason", columnDefinition = "text")
    var failureReason: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @OneToMany(mappedBy = "allocation", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position ASC")
    var policy: MutableList<PolicySlot> = mutableListOf(),
    @OneToMany(mappedBy = "allocation", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableSet<AllocationItem> = linkedSetOf(),
)

@Entity
@Table(name = "allocation_policy_slot")
class PolicySlot(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_request_id", nullable = false)
    var allocation: AllocationRequestEntity,
    @Column(nullable = false)
    var position: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: EquipmentType,
    @Column(name = "minimum_condition", precision = CONDITION_SCORE_PRECISION, scale = CONDITION_SCORE_SCALE)
    var minimumCondition: BigDecimal? = null,
    @Column(name = "preferred_brand", length = 100)
    var preferredBrand: String? = null,
    @Column(name = "prefer_recent", nullable = false)
    var preferRecent: Boolean = true,
)

@Entity
@Table(name = "allocation_item")
class AllocationItem(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_request_id", nullable = false)
    var allocation: AllocationRequestEntity,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    var equipment: Equipment,
)
