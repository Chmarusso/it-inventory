package com.example.inventory.allocation

import com.example.inventory.equipment.CONDITION_SCORE_SCALE
import com.example.inventory.equipment.Equipment
import com.example.inventory.equipment.EquipmentType
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.max
import kotlin.random.Random
import kotlin.system.measureNanoTime

/**
 * Latency benchmark for [AllocationMatcher] over a 5 000-item catalogue.
 *
 * Tagged `benchmark` and excluded from the normal `test` task, so the Docker image build stays
 * fast. Run it with `./gradlew :backend:benchmark`.
 *
 * This is a wall-clock harness, not JMH: it measures the matcher in isolation with a fixed seed,
 * reports percentiles over many iterations after a warmup, and makes no attempt to defeat JIT
 * loop optimisation beyond consuming the result. Good enough to characterise the shape of the
 * curve and catch regressions; not a substitute for JMH if single-digit-microsecond precision
 * ever matters.
 */
@Tag("benchmark")
class AllocationMatcherBenchmark {
    private val matcher = AllocationMatcher()
    private val today = LocalDate.of(2026, 1, 1)

    private companion object {
        const val CATALOGUE_SIZE = 5_000
        const val WARMUP_ITERATIONS = 50
        const val MEASURED_ITERATIONS = 300
        const val SEED = 20260916L
    }

    @Test
    fun `report latency percentiles across policy sizes`() {
        val catalogue = buildCatalogue(CATALOGUE_SIZE)

        println()
        println("AllocationMatcher benchmark — catalogue of $CATALOGUE_SIZE equipment items")
        println("warmup=$WARMUP_ITERATIONS  measured=$MEASURED_ITERATIONS  seed=$SEED")
        println("JVM: ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
        println("cores: ${Runtime.getRuntime().availableProcessors()}")
        println()
        println(
            "%-38s %9s %9s %9s %9s %9s".format(
                "scenario",
                "p50",
                "p90",
                "p99",
                "max",
                "mean",
            ),
        )
        println("-".repeat(88))

        scenarios().forEach { scenario ->
            val candidates = catalogue.filter { item -> scenario.policy.any { it.type == item.type } }
            val samples = measure(scenario.policy, candidates)
            println(
                "%-38s %9s %9s %9s %9s %9s".format(
                    "${scenario.name} (${candidates.size} candidates)",
                    samples.percentile(50).ms(),
                    samples.percentile(90).ms(),
                    samples.percentile(99).ms(),
                    samples.max().ms(),
                    (samples.average().toLong()).ms(),
                ),
            )
        }
        println()
    }

    private fun measure(
        policy: List<PolicySlotRequest>,
        candidates: List<Equipment>,
    ): LongArray {
        repeat(WARMUP_ITERATIONS) { consume(matcher.match(policy, candidates, today)) }

        val samples = LongArray(MEASURED_ITERATIONS)
        repeat(MEASURED_ITERATIONS) { index ->
            // The matcher mutates Equipment.state only via the service, never here, so the same
            // candidate list is safe to reuse across iterations.
            samples[index] = measureNanoTime { consume(matcher.match(policy, candidates, today)) }
        }
        return samples
    }

    /** Keeps the JIT from eliminating the call whose cost we are measuring. */
    private fun consume(matches: List<AllocationMatcher.Match>?) {
        if (matches != null && matches.size == Int.MAX_VALUE) error("unreachable")
    }

    private fun scenarios() =
        listOf(
            Scenario(
                "1 laptop >=0.8 prefer Apple",
                listOf(slot(EquipmentType.MAIN_COMPUTER, minimum = "0.8", brand = "Apple")),
            ),
            Scenario(
                "1 laptop + 2 monitors (the brief)",
                listOf(
                    slot(EquipmentType.MAIN_COMPUTER, minimum = "0.8", brand = "Apple"),
                    slot(EquipmentType.MONITOR),
                    slot(EquipmentType.MONITOR),
                ),
            ),
            Scenario(
                "full desk setup (4 slots)",
                listOf(
                    slot(EquipmentType.MAIN_COMPUTER, minimum = "0.7"),
                    slot(EquipmentType.MONITOR, minimum = "0.6"),
                    slot(EquipmentType.KEYBOARD),
                    slot(EquipmentType.MOUSE),
                ),
            ),
            Scenario(
                "contended: 10 monitors, tight minimums",
                (1..10).map { slot(EquipmentType.MONITOR, minimum = "0.85", brand = "Dell") },
            ),
            Scenario(
                "worst case: 50 slots (API maximum)",
                (1..50).map { index ->
                    slot(
                        EquipmentType.entries[index % EquipmentType.entries.size],
                        minimum = "0.5",
                        brand = if (index % 3 == 0) "Dell" else null,
                    )
                },
            ),
            Scenario(
                "unsatisfiable: minimum above every item",
                listOf(slot(EquipmentType.MAIN_COMPUTER, minimum = "0.999")),
            ),
        )

    private data class Scenario(
        val name: String,
        val policy: List<PolicySlotRequest>,
    )

    private fun slot(
        type: EquipmentType,
        minimum: String? = null,
        brand: String? = null,
    ) = PolicySlotRequest(
        type = type,
        minimumCondition = minimum?.let(::BigDecimal),
        preferredBrand = brand,
        preferRecent = true,
    )

    private fun buildCatalogue(size: Int): List<Equipment> {
        val random = Random(SEED)
        val brands = listOf("Apple", "Dell", "Lenovo", "LG", "Logitech", "HP", "Samsung", "Keychron")
        return (1..size).map { id ->
            Equipment(
                id = id.toLong(),
                type = EquipmentType.entries[random.nextInt(EquipmentType.entries.size)],
                brand = brands[random.nextInt(brands.size)],
                model = "model-$id",
                // Skewed towards healthy stock, with a long tail of worn items, so minimum
                // thresholds actually exclude a meaningful slice.
                conditionScore =
                    BigDecimal
                        .valueOf(0.50 + random.nextDouble() * 0.49)
                        .setScale(CONDITION_SCORE_SCALE, java.math.RoundingMode.HALF_UP),
                purchaseDate = today.minusDays(random.nextLong(1, 1_800)),
            )
        }
    }

    private fun LongArray.percentile(percentile: Int): Long {
        val sorted = this.sortedArray()
        val rank = max(0, Math.ceil(percentile / 100.0 * sorted.size).toInt() - 1)
        return sorted[rank]
    }

    private fun Long.ms(): String = "%.3f ms".format(this / 1_000_000.0)
}
