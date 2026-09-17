package com.example.inventory.allocation

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AllocationRepository : JpaRepository<AllocationRequestEntity, Long> {
    @EntityGraph(attributePaths = ["policy", "items", "items.equipment"])
    fun findOneById(id: Long): AllocationRequestEntity?

    @EntityGraph(attributePaths = ["items"])
    fun findAllByOrderByCreatedAtDesc(): List<AllocationRequestEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select allocation from AllocationRequestEntity allocation where allocation.id = :id")
    fun findForUpdateById(
        @Param("id") id: Long,
    ): AllocationRequestEntity?
}
