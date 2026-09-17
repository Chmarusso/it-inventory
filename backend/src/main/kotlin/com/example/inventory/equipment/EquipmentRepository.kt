package com.example.inventory.equipment

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EquipmentRepository : JpaRepository<Equipment, Long> {
    fun findAllByStateOrderByIdAsc(state: EquipmentState): List<Equipment>

    fun findAllByTypeOrderByIdAsc(type: EquipmentType): List<Equipment>

    fun findAllByStateAndTypeOrderByIdAsc(
        state: EquipmentState,
        type: EquipmentType,
    ): List<Equipment>

    @Query("select distinct e.brand from Equipment e order by e.brand")
    fun findDistinctBrands(): List<String>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.state = :state and e.type in :types order by e.id")
    fun findAndLockByStateAndTypes(
        @Param("state") state: EquipmentState,
        @Param("types") types: Set<EquipmentType>,
    ): List<Equipment>
}
