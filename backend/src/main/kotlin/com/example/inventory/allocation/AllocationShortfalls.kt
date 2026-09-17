package com.example.inventory.allocation

import com.example.inventory.equipment.CONDITION_SCORE_SCALE
import com.example.inventory.equipment.Equipment
import com.example.inventory.equipment.EquipmentType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.TreeMap

/**
 * One violated hard constraint: [required] slots of [type] need condition at or above
 * [minimumCondition] (null meaning any condition), but only [available] candidates qualify.
 */
data class AllocationShortfall(
    val type: EquipmentType,
    val minimumCondition: BigDecimal?,
    val required: Int,
    val available: Int,
)

/**
 * Explains why no complete assignment exists, in terms an operator can act on.
 *
 * The hard constraints are only type and minimum condition ([PolicySlotRequest.accepts]).
 * Within one type, each slot's candidate set is therefore a threshold set -- every item at or
 * above its minimum -- and threshold sets are nested: a tighter slot's candidates are a subset
 * of a looser slot's. For nested sets, Hall's condition collapses to one check per distinct
 * threshold: the number of slots demanding at least that minimum must not exceed the number
 * of items meeting it. Types never share candidates, so the checks are independent across types.
 *
 * That makes this diagnosis exact rather than heuristic. It finds a shortfall if and only if
 * the matcher cannot produce a full assignment, and it names the tightest threshold at fault
 * instead of a per-type total that hides competition between slots of the same type.
 */
object AllocationShortfalls {
    fun find(
        slots: List<PolicySlotRequest>,
        candidates: List<Equipment>,
    ): List<AllocationShortfall> =
        slots.groupBy { it.type }.flatMap { (type, typeSlots) ->
            // A minimum of zero is no minimum, so it merges with null up front rather than being
            // decoded on output. TreeMap orders keys with compareTo, so 0.8 and 0.80 share a
            // bucket; groupBy would use equals, which is scale-sensitive on BigDecimal.
            val slotsPerThreshold = TreeMap<BigDecimal?, MutableList<PolicySlotRequest>>(nullsLast(reverseOrder()))
            typeSlots.forEach { slot ->
                val threshold = slot.minimumCondition?.takeIf { it.signum() > 0 }
                slotsPerThreshold.getOrPut(threshold) { mutableListOf() } += slot
            }

            // Walk thresholds from tightest to loosest. Every slot at a tighter threshold also
            // competes for the looser pool, so demand accumulates while supply widens. Any slot
            // at the threshold can stand in for it when counting supply: same type, same minimum.
            var demand = 0
            slotsPerThreshold.entries.mapNotNull { (threshold, slotsHere) ->
                demand += slotsHere.size
                val supply = candidates.count { slotsHere.first().accepts(it) }
                if (demand > supply) AllocationShortfall(type, threshold, demand, supply) else null
            }
        }

    /**
     * The sentence stored in `failure_reason` and shown by the UI. Called only after the matcher
     * has failed. The generic fallback is unreachable while hard constraints are exactly type and
     * minimum condition (the theorem above), and exists so a future constraint the diagnosis does
     * not model degrades to a generic sentence rather than a null reason.
     */
    fun explain(
        slots: List<PolicySlotRequest>,
        candidates: List<Equipment>,
    ): String {
        val shortfalls = find(slots, candidates)
        if (shortfalls.isEmpty()) return "No valid non-overlapping equipment set satisfies every policy slot."
        return "No valid equipment set is available (${shortfalls.joinToString("; ", transform = ::render)})."
    }

    internal fun EquipmentType.displayName(): String = name.lowercase().replace('_', ' ')

    private fun render(shortfall: AllocationShortfall): String {
        val slots = if (shortfall.required == 1) "1 slot needs" else "${shortfall.required} slots need"
        // Always two decimals, so a request written as 0.9 reads the same as one written as 0.90.
        val condition =
            shortfall.minimumCondition
                ?.let { "condition >= ${it.setScale(CONDITION_SCORE_SCALE, RoundingMode.HALF_UP).toPlainString()}" }
                ?: "any condition"
        return "${shortfall.type.displayName()}: $slots $condition, ${shortfall.available} available"
    }
}
