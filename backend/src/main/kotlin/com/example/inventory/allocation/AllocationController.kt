package com.example.inventory.allocation

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/allocations")
class AllocationController(
    private val service: AllocationService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateAllocationRequest,
    ) = service.create(request)

    @GetMapping
    fun list() = service.list()

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ) = service.get(id)

    @PostMapping("/{id}/confirm")
    fun confirm(
        @PathVariable id: Long,
    ) = service.confirm(id)

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Long,
    ) = service.cancel(id)

    @PostMapping("/{id}/items/{equipmentId}/return")
    fun returnEquipment(
        @PathVariable id: Long,
        @PathVariable equipmentId: Long,
    ) = service.returnEquipment(id, equipmentId)
}
