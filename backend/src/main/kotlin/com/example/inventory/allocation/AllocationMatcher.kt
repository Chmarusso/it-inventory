package com.example.inventory.allocation

import com.example.inventory.equipment.CONDITION_SCORE_SCALE
import com.example.inventory.equipment.Equipment
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.ArrayDeque
import kotlin.math.max

/** Maximum-weight full matching of policy slots to distinct equipment via min-cost max-flow. */
class AllocationMatcher {
    data class Match(
        val slotIndex: Int,
        val equipment: Equipment,
    )

    private data class Edge(
        var to: Int,
        var capacity: Int,
        var cost: Long,
        var reverse: Int,
    )

    internal companion object {
        /*
         * Preference weights, in strict priority tiers: brand > recency > condition > id.
         *
         * Each tier is a multiplicative unit chosen so that unit(tier) exceeds the maximum
         * combined contribution of every tier below it. A lower-priority signal can therefore
         * never outvote a higher-priority one, no matter how it accumulates.
         * [tierInvariantsHold] states the rule; AllocationScoringTest asserts it.
         *
         * Magnitudes are also bounded from above: SPFA sums edge costs along a residual path
         * that can cross every slot the API allows (MAX_POLICY_SLOTS), so that many times
         * MAX_EDGE_SCORE must stay well inside Long. It does: about 5.5e17 against 9.2e18.
         */

        /** Ids below this get a distinct tie-break value; larger ids all tie at zero. */
        const val TIE_BREAK_RANGE = 10_000_000L
        const val TIE_BREAK_MAX = TIE_BREAK_RANGE - 1

        /** 0..1 with CONDITION_SCORE_SCALE decimals gives 0..100 whole units. */
        const val CONDITION_MAX_UNITS = 100L
        const val CONDITION_UNIT = TIE_BREAK_RANGE
        const val CONDITION_MAX = CONDITION_MAX_UNITS * CONDITION_UNIT

        const val RECENCY_MAX_DAYS = 100_000L
        const val RECENCY_UNIT = 10_000_000_000L
        const val RECENCY_MAX = RECENCY_MAX_DAYS * RECENCY_UNIT

        const val BRAND_UNIT = 10_000_000_000_000_000L

        /** Highest score a single edge can carry; bounds residual-path sums. */
        const val MAX_EDGE_SCORE = BRAND_UNIT + RECENCY_MAX + CONDITION_MAX + TIE_BREAK_MAX

        fun tierInvariantsHold(): Boolean =
            CONDITION_UNIT > TIE_BREAK_MAX &&
                RECENCY_UNIT > CONDITION_MAX + TIE_BREAK_MAX &&
                BRAND_UNIT > RECENCY_MAX + CONDITION_MAX + TIE_BREAK_MAX
    }

    /**
     * Edge weight for assigning [item] to [slot]; higher is better. Hard constraints are the
     * caller's responsibility -- this only ranks items that already qualify.
     */
    internal fun score(
        slot: PolicySlotRequest,
        item: Equipment,
        today: LocalDate,
    ): Long {
        val brand =
            if (!slot.preferredBrand.isNullOrBlank() && item.brand.equals(slot.preferredBrand, ignoreCase = true)) {
                BRAND_UNIT
            } else {
                0L
            }
        val ageDays = max(0, ChronoUnit.DAYS.between(item.purchaseDate, today)).coerceAtMost(RECENCY_MAX_DAYS)
        val recency = if (slot.preferRecent) RECENCY_MAX_DAYS - ageDays else 0L
        val condition =
            item.conditionScore
                .movePointRight(CONDITION_SCORE_SCALE)
                .toLong()
                .coerceIn(0, CONDITION_MAX_UNITS)
        // Lower id wins: a 1-based id maps to TIE_BREAK_RANGE - id, clamped at zero above the range.
        val tieBreak = (TIE_BREAK_RANGE - (item.id ?: TIE_BREAK_RANGE)).coerceIn(0, TIE_BREAK_RANGE - 1)
        return brand + recency * RECENCY_UNIT + condition * CONDITION_UNIT + tieBreak
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun match(
        slots: List<PolicySlotRequest>,
        equipment: List<Equipment>,
        today: LocalDate = LocalDate.now(),
    ): List<Match>? {
        if (slots.isEmpty()) return emptyList()
        val source = 0
        val slotStart = 1
        val equipmentStart = slotStart + slots.size
        val sink = equipmentStart + equipment.size
        val graph = Array(sink + 1) { mutableListOf<Edge>() }

        fun addEdge(
            from: Int,
            to: Int,
            capacity: Int,
            cost: Long,
        ) {
            val forward = Edge(to, capacity, cost, graph[to].size)
            val reverse = Edge(from, 0, -cost, graph[from].size)
            graph[from].add(forward)
            graph[to].add(reverse)
        }

        slots.indices.forEach { addEdge(source, slotStart + it, 1, 0) }
        equipment.indices.forEach { addEdge(equipmentStart + it, sink, 1, 0) }

        // Group once so each slot scans only items of its own type instead of the whole catalogue.
        val itemsByType = equipment.withIndex().groupBy { it.value.type }
        slots.forEachIndexed { slotIndex, slot ->
            itemsByType[slot.type].orEmpty().forEach { (equipmentIndex, item) ->
                if (slot.accepts(item)) {
                    // Costs are negated because the solver minimises cost while we maximise score.
                    addEdge(slotStart + slotIndex, equipmentStart + equipmentIndex, 1, -score(slot, item, today))
                }
            }
        }

        var flow = 0
        while (flow < slots.size) {
            val distance = LongArray(graph.size) { Long.MAX_VALUE }
            val previousNode = IntArray(graph.size) { -1 }
            val previousEdge = IntArray(graph.size) { -1 }
            distance[source] = 0
            val queue = ArrayDeque<Int>()
            val queued = BooleanArray(graph.size)
            queue.add(source)
            queued[source] = true
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                queued[node] = false
                graph[node].forEachIndexed { edgeIndex, edge ->
                    if (edge.capacity > 0 && distance[node] != Long.MAX_VALUE && distance[edge.to] > distance[node] + edge.cost) {
                        distance[edge.to] = distance[node] + edge.cost
                        previousNode[edge.to] = node
                        previousEdge[edge.to] = edgeIndex
                        if (!queued[edge.to]) {
                            queue.addLast(edge.to)
                            queued[edge.to] = true
                        }
                    }
                }
            }
            if (previousNode[sink] == -1) return null
            var node = sink
            while (node != source) {
                val parent = previousNode[node]
                val edge = graph[parent][previousEdge[node]]
                edge.capacity--
                graph[node][edge.reverse].capacity++
                node = parent
            }
            flow++
        }

        return slots.indices.map { slotIndex ->
            val usedEdge =
                graph[slotStart + slotIndex].first { edge ->
                    edge.to in equipmentStart until sink && edge.capacity == 0
                }
            Match(slotIndex, equipment[usedEdge.to - equipmentStart])
        }
    }
}
