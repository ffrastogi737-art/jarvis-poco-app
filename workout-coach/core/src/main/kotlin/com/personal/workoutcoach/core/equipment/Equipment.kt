package com.personal.workoutcoach.core.equipment

enum class EquipmentType {
    FIXED_DUMBBELL, ADJUSTABLE_DUMBBELL, RESISTANCE_BAND, BARBELL, WEIGHT_PLATE, KETTLEBELL,
    PULL_UP_BAR, PUSH_UP_BARS, BENCH, DIP_BARS, ANKLE_WEIGHTS, YOGA_MAT, BODYWEIGHT, CUSTOM
}

/** What an exercise can ask for. Inventory items are translated into capabilities. */
enum class Capability {
    DUMBBELL, DUMBBELL_PAIR, KETTLEBELL, BAND, BAND_ANCHORED, BARBELL, PULL_UP_BAR,
    PUSH_UP_HANDLES, BENCH_FLAT, BENCH_INCLINE, DIP_STATION, ANKLE_WEIGHT, MAT
}

enum class BandType { LOOP, TUBE_WITH_HANDLES, MINI_LOOP, FLAT_THERAPY }

/** Only purpose-made door anchors and secure fixed posts count as safe anchors. */
enum class BandAnchor { NONE, DOOR_ANCHOR, SECURE_POST, OTHER }

enum class MountType { DOORWAY_PRESSURE, DOORFRAME_LEVERAGE, WALL_MOUNTED, CEILING_MOUNTED, FREE_STANDING }

enum class BenchPosition { FLAT, INCLINE, DECLINE }

data class EquipmentItem(
    val id: Long = 0,
    val type: EquipmentType,
    val name: String,
    val enabled: Boolean = true,
    val quantity: Int = 1,
    /** Fixed dumbbell, kettlebell, plate, ankle weight: weight per item. Barbell: bar weight. */
    val weightKg: Double? = null,
    val minWeightKg: Double? = null,
    val maxWeightKg: Double? = null,
    val incrementKg: Double? = null,
    val bandType: BandType? = null,
    /** Manufacturer label such as "Red / Medium"; bands differ, so we never assume a value. */
    val resistanceLabel: String? = null,
    val resistanceKg: Double? = null,
    val anchor: BandAnchor? = null,
    val benchPositions: Set<BenchPosition> = emptySet(),
    val mountType: MountType? = null,
    /** CUSTOM items declare which capabilities they provide (may be empty). */
    val customCapabilities: Set<Capability> = emptySet(),
    val notes: String = "",
) {
    fun validate(): List<String> {
        val e = mutableListOf<String>()
        if (name.isBlank()) e += "Name is required"
        if (quantity !in 1..50) e += "Quantity must be 1–50"
        fun weight(label: String, w: Double?) {
            if (w == null || w <= 0.0 || w > 300.0) e += "$label must be between 0 and 300 kg"
        }
        when (type) {
            EquipmentType.FIXED_DUMBBELL, EquipmentType.KETTLEBELL, EquipmentType.WEIGHT_PLATE,
            EquipmentType.ANKLE_WEIGHTS -> weight("Weight", weightKg)
            EquipmentType.BARBELL -> weight("Bar weight", weightKg)
            EquipmentType.ADJUSTABLE_DUMBBELL -> {
                weight("Minimum weight", minWeightKg); weight("Maximum weight", maxWeightKg)
                if (minWeightKg != null && maxWeightKg != null && minWeightKg >= maxWeightKg) e += "Maximum must be greater than minimum"
                if (incrementKg == null || incrementKg <= 0.0) e += "Increment must be greater than 0"
            }
            EquipmentType.RESISTANCE_BAND -> {
                if (resistanceLabel.isNullOrBlank() && resistanceKg == null) e += "Enter the band's resistance level or rating"
                if (resistanceKg != null && resistanceKg <= 0.0) e += "Resistance must be positive"
            }
            EquipmentType.BENCH -> if (benchPositions.isEmpty()) e += "Select at least one bench position"
            EquipmentType.PULL_UP_BAR -> if (mountType == null) e += "Select how the bar is mounted"
            else -> Unit
        }
        return e
    }

    /** Short label used for history snapshots and AI context. */
    fun label(): String = when (type) {
        EquipmentType.FIXED_DUMBBELL -> "$name (${quantity}× ${fmt(weightKg)} kg fixed)"
        EquipmentType.ADJUSTABLE_DUMBBELL -> "$name (${quantity}× adjustable ${fmt(minWeightKg)}–${fmt(maxWeightKg)} kg)"
        EquipmentType.RESISTANCE_BAND -> "$name (band ${resistanceLabel ?: ""}${resistanceKg?.let { " ~${fmt(it)} kg" } ?: ""}, anchor ${anchor ?: BandAnchor.NONE})"
        EquipmentType.KETTLEBELL, EquipmentType.WEIGHT_PLATE, EquipmentType.ANKLE_WEIGHTS -> "$name (${quantity}× ${fmt(weightKg)} kg)"
        EquipmentType.BARBELL -> "$name (bar ${fmt(weightKg)} kg)"
        else -> name
    }
}

fun fmt(v: Double?): String = when {
    v == null -> "?"
    v % 1.0 == 0.0 -> v.toInt().toString()
    else -> v.toString()
}

/** Default inventory for the primary user: a pair of fixed 17.5 kg dumbbells plus bodyweight. */
fun defaultInventory(): List<EquipmentItem> = listOf(
    EquipmentItem(type = EquipmentType.FIXED_DUMBBELL, name = "Dumbbells", quantity = 2, weightKg = 17.5),
    EquipmentItem(type = EquipmentType.BODYWEIGHT, name = "Bodyweight"),
)
