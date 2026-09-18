package com.example.inventory.equipment

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/equipments")
class EquipmentController(
    private val repository: EquipmentRepository,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateEquipmentRequest,
    ): EquipmentResponse =
        repository
            .save(
                Equipment(
                    type = request.type,
                    brand = request.brand.trim(),
                    model = request.model.trim(),
                    conditionScore = request.conditionScore,
                    purchaseDate = request.purchaseDate,
                ),
            ).toResponse()

    /** Distinct brands already present in stock, used to populate the brand picker. */
    @GetMapping("/brands")
    fun brands(): List<String> = repository.findDistinctBrands()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): EquipmentResponse =
        repository
            .findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found") }
            .toResponse()

    @GetMapping
    fun list(
        @RequestParam(required = false) state: EquipmentState?,
        @RequestParam(required = false) type: EquipmentType?,
    ): List<EquipmentResponse> {
        val equipment =
            when {
                state != null && type != null -> repository.findAllByStateAndTypeOrderByIdAsc(state, type)
                state != null -> repository.findAllByStateOrderByIdAsc(state)
                type != null -> repository.findAllByTypeOrderByIdAsc(type)
                else -> repository.findAll().sortedBy { it.id }
            }
        return equipment.map(Equipment::toResponse)
    }
}
