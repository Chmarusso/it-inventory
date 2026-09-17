package com.example.inventory.allocation

import com.example.inventory.equipment.EquipmentRepository
import com.example.inventory.equipment.EquipmentState
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class AllocationService(
    private val allocationRepository: AllocationRepository,
    private val equipmentRepository: EquipmentRepository,
) {
    private val matcher = AllocationMatcher()

    @Transactional
    fun create(request: CreateAllocationRequest): AllocationDetailResponse {
        val allocation =
            AllocationRequestEntity(
                employeeId = request.employeeId.trim(),
                state = AllocationState.RESERVED,
            )
        // Normalise once so the persisted policy, the matcher and the failure diagnosis all see
        // the same values. Matching against the raw request while storing a trimmed copy let a
        // padded brand silently disable the preference the stored policy claimed to apply.
        val slots = request.policy.map { it.normalised() }
        slots.forEachIndexed { index, slot ->
            allocation.policy.add(
                PolicySlot(
                    allocation = allocation,
                    position = index,
                    type = slot.type,
                    minimumCondition = slot.minimumCondition,
                    preferredBrand = slot.preferredBrand,
                    preferRecent = slot.preferRecent,
                ),
            )
        }

        val candidates =
            equipmentRepository.findAndLockByStateAndTypes(
                EquipmentState.AVAILABLE,
                slots.map { it.type }.toSet(),
            )
        val matches = matcher.match(slots, candidates)
        if (matches == null) {
            allocation.state = AllocationState.FAILED
            allocation.failureReason = AllocationShortfalls.explain(slots, candidates)
        } else {
            matches.sortedBy { it.slotIndex }.forEach { match ->
                match.equipment.state = EquipmentState.RESERVED
                allocation.items.add(AllocationItem(allocation = allocation, equipment = match.equipment))
            }
        }
        return allocationRepository.save(allocation).toDetail()
    }

    @Transactional
    fun confirm(id: Long): AllocationDetailResponse {
        val allocation = findForUpdate(id)
        requireState(allocation, AllocationState.RESERVED, "confirm")
        allocation.items.forEach { it.equipment.state = EquipmentState.ASSIGNED }
        allocation.state = AllocationState.CONFIRMED
        return allocation.toDetail()
    }

    @Transactional
    fun cancel(id: Long): AllocationDetailResponse {
        val allocation = findForUpdate(id)
        requireState(allocation, AllocationState.RESERVED, "cancel")
        allocation.items.forEach { it.equipment.state = EquipmentState.AVAILABLE }
        allocation.state = AllocationState.CANCELLED
        return allocation.toDetail()
    }

    @Transactional
    fun get(id: Long): AllocationDetailResponse = find(id).toDetail()

    @Transactional
    fun list(): List<AllocationSummaryResponse> = allocationRepository.findAllByOrderByCreatedAtDesc().map { it.toSummary() }

    private fun find(id: Long) = allocationRepository.findOneById(id) ?: throw NotFoundException("Allocation $id was not found")

    private fun findForUpdate(id: Long) =
        allocationRepository.findForUpdateById(id)
            ?: throw NotFoundException("Allocation $id was not found")

    private fun requireState(
        allocation: AllocationRequestEntity,
        expected: AllocationState,
        action: String,
    ) {
        if (allocation.state != expected) {
            throw ConflictException("Allocation ${allocation.id} cannot be $action in state ${allocation.state}")
        }
    }
}

class NotFoundException(
    message: String,
) : RuntimeException(message)

class ConflictException(
    message: String,
) : RuntimeException(message)
