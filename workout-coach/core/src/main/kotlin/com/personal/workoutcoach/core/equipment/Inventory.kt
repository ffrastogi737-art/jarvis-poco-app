package com.personal.workoutcoach.core.equipment

import com.personal.workoutcoach.core.catalog.Exercise
import com.personal.workoutcoach.core.catalog.LoadKind

sealed interface Compatibility {
    data class Ok(val loadOptionsKg: List<Double>) : Compatibility
    data class Unavailable(val reason: String) : Compatibility
}

/**
 * The enabled part of the user's equipment, translated into capabilities and load options.
 * Pure and AI-independent: everything that recommends an exercise goes through [check].
 */
class Inventory(items: List<EquipmentItem>) {
    val active: List<EquipmentItem> = items.filter { it.enabled }

    val capabilities: Set<Capability> = buildSet {
        for (item in active) when (item.type) {
            EquipmentType.FIXED_DUMBBELL, EquipmentType.ADJUSTABLE_DUMBBELL -> add(Capability.DUMBBELL)
            EquipmentType.KETTLEBELL -> add(Capability.KETTLEBELL)
            EquipmentType.RESISTANCE_BAND -> {
                add(Capability.BAND)
                if (item.anchor == BandAnchor.DOOR_ANCHOR || item.anchor == BandAnchor.SECURE_POST) add(Capability.BAND_ANCHORED)
            }
            EquipmentType.BARBELL -> add(Capability.BARBELL)
            EquipmentType.PULL_UP_BAR -> add(Capability.PULL_UP_BAR)
            EquipmentType.PUSH_UP_BARS -> add(Capability.PUSH_UP_HANDLES)
            EquipmentType.BENCH -> {
                if (BenchPosition.FLAT in item.benchPositions) add(Capability.BENCH_FLAT)
                if (BenchPosition.INCLINE in item.benchPositions) add(Capability.BENCH_INCLINE)
            }
            EquipmentType.DIP_BARS -> add(Capability.DIP_STATION)
            EquipmentType.ANKLE_WEIGHTS -> add(Capability.ANKLE_WEIGHT)
            EquipmentType.YOGA_MAT -> add(Capability.MAT)
            EquipmentType.CUSTOM -> addAll(item.customCapabilities)
            EquipmentType.WEIGHT_PLATE, EquipmentType.BODYWEIGHT -> Unit
        }
        if (dumbbellPairLoads().isNotEmpty()) add(Capability.DUMBBELL_PAIR)
    }

    private fun adjustableRange(item: EquipmentItem): List<Double> {
        val min = item.minWeightKg ?: return emptyList()
        val max = item.maxWeightKg ?: return emptyList()
        val inc = item.incrementKg?.takeIf { it > 0 } ?: return emptyList()
        return generateSequence(min) { it + inc }.takeWhile { it <= max + 1e-6 }.map { round2(it) }.toList()
    }

    fun singleDumbbellLoads(): List<Double> = active.flatMap {
        when (it.type) {
            EquipmentType.FIXED_DUMBBELL -> listOfNotNull(it.weightKg)
            EquipmentType.ADJUSTABLE_DUMBBELL -> adjustableRange(it)
            else -> emptyList()
        }
    }.distinct().sorted()

    fun dumbbellPairLoads(): List<Double> {
        val fixedCounts = active.filter { it.type == EquipmentType.FIXED_DUMBBELL && it.weightKg != null }
            .groupBy { it.weightKg!! }.mapValues { (_, v) -> v.sumOf { it.quantity } }
        val fixed = fixedCounts.filterValues { it >= 2 }.keys
        val adjustableItems = active.filter { it.type == EquipmentType.ADJUSTABLE_DUMBBELL }
        val adjustable = if (adjustableItems.sumOf { it.quantity } >= 2) adjustableItems.flatMap(::adjustableRange) else emptyList()
        return (fixed + adjustable).distinct().sorted()
    }

    fun kettlebellLoads(): List<Double> = active.filter { it.type == EquipmentType.KETTLEBELL }.mapNotNull { it.weightKg }.distinct().sorted()

    fun ankleWeightLoads(): List<Double> = active.filter { it.type == EquipmentType.ANKLE_WEIGHTS }.mapNotNull { it.weightKg }.distinct().sorted()

    /** Bar plus any symmetric combination of available plates (pairs only). */
    fun barbellLoads(): List<Double> {
        val bars = active.filter { it.type == EquipmentType.BARBELL }.mapNotNull { it.weightKg }
        if (bars.isEmpty()) return emptyList()
        var sums = setOf(0.0)
        for (plate in active.filter { it.type == EquipmentType.WEIGHT_PLATE && it.weightKg != null }) {
            repeat(plate.quantity / 2) { sums = sums + sums.map { round2(it + plate.weightKg!! * 2) } }
            if (sums.size > 400) break // shortcut: caps combinatorics for huge plate sets; fine for home gyms
        }
        return bars.flatMap { bar -> sums.map { round2(bar + it) } }.distinct().sorted()
    }

    fun loadsFor(kind: LoadKind): List<Double> = when (kind) {
        LoadKind.BODYWEIGHT, LoadKind.BAND -> emptyList()
        LoadKind.DUMBBELL_SINGLE -> singleDumbbellLoads()
        LoadKind.DUMBBELL_PAIR -> dumbbellPairLoads()
        LoadKind.KETTLEBELL -> kettlebellLoads()
        LoadKind.BARBELL -> barbellLoads()
        LoadKind.ANKLE_WEIGHT -> ankleWeightLoads()
    }

    fun check(ex: Exercise): Compatibility {
        val missing = ex.requires - capabilities
        if (missing.isNotEmpty()) return Compatibility.Unavailable("Needs ${missing.joinToString { it.name.lowercase().replace('_', ' ') }}")
        if (ex.loadKind == LoadKind.BODYWEIGHT || ex.loadKind == LoadKind.BAND) return Compatibility.Ok(emptyList())
        val loads = loadsFor(ex.loadKind).filter { ex.maxLoadKg == null || it <= ex.maxLoadKg + 1e-6 }
        if (loads.isEmpty()) return Compatibility.Unavailable(
            "Available weights are too heavy for controlled technique (max ${fmt(ex.maxLoadKg)} kg)"
        )
        return Compatibility.Ok(loads)
    }

    fun isCompatible(ex: Exercise) = check(ex) is Compatibility.Ok

    fun summary(): List<String> = active.filter { it.type != EquipmentType.BODYWEIGHT }.map { it.label() } + "Bodyweight"
}

internal fun round2(v: Double) = Math.round(v * 100) / 100.0
