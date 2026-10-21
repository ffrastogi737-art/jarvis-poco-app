package com.personal.workoutcoach.core

import com.personal.workoutcoach.core.catalog.ExerciseCatalog
import com.personal.workoutcoach.core.equipment.*
import kotlin.test.*

class EquipmentTest {
    private val defaultInv = Inventory(defaultInventory())

    @Test fun defaultInventoryIsPairOf17_5() {
        assertEquals(listOf(17.5), defaultInv.dumbbellPairLoads())
        assertEquals(setOf(Capability.DUMBBELL, Capability.DUMBBELL_PAIR), defaultInv.capabilities)
    }

    @Test fun lightIsolationExcludedWith17_5Dumbbells() {
        val lateral = ExerciseCatalog.get("db_lateral_raise")!!
        assertIs<Compatibility.Unavailable>(defaultInv.check(lateral))
        assertTrue(defaultInv.isCompatible(ExerciseCatalog.get("goblet_squat")!!))
    }

    @Test fun unavailableEquipmentFilteredOut() {
        for (id in listOf("pull_up", "bench_db_press", "band_row", "kb_swing", "barbell_rdl", "dips")) {
            assertFalse(defaultInv.isCompatible(ExerciseCatalog.get(id)!!), id)
        }
    }

    @Test fun updatingDumbbellWeightChangesLoads() {
        val items = defaultInventory().toMutableList()
        items[0] = items[0].copy(weightKg = 10.0)
        val inv = Inventory(items)
        assertEquals(listOf(10.0), inv.dumbbellPairLoads())
        assertTrue(inv.isCompatible(ExerciseCatalog.get("db_lateral_raise")!!))
    }

    @Test fun singleDumbbellIsNotAPair() {
        val inv = Inventory(listOf(EquipmentItem(type = EquipmentType.FIXED_DUMBBELL, name = "DB", quantity = 1, weightKg = 12.0)))
        assertFalse(Capability.DUMBBELL_PAIR in inv.capabilities)
        assertTrue(inv.isCompatible(ExerciseCatalog.get("goblet_squat")!!))
        assertFalse(inv.isCompatible(ExerciseCatalog.get("db_rdl")!!))
    }

    @Test fun twoSeparateFixedItemsOfSameWeightFormAPair() {
        val inv = Inventory(listOf(
            EquipmentItem(type = EquipmentType.FIXED_DUMBBELL, name = "A", quantity = 1, weightKg = 12.0),
            EquipmentItem(type = EquipmentType.FIXED_DUMBBELL, name = "B", quantity = 1, weightKg = 12.0),
        ))
        assertEquals(listOf(12.0), inv.dumbbellPairLoads())
    }

    @Test fun adjustableDumbbellRange() {
        val inv = Inventory(listOf(EquipmentItem(type = EquipmentType.ADJUSTABLE_DUMBBELL, name = "Adj", quantity = 2, minWeightKg = 2.5, maxWeightKg = 10.0, incrementKg = 2.5)))
        assertEquals(listOf(2.5, 5.0, 7.5, 10.0), inv.dumbbellPairLoads())
    }

    @Test fun bandsWithDifferentResistanceAndAnchorSafety() {
        val light = EquipmentItem(type = EquipmentType.RESISTANCE_BAND, name = "Yellow", resistanceLabel = "Light", anchor = BandAnchor.NONE)
        val heavy = EquipmentItem(type = EquipmentType.RESISTANCE_BAND, name = "Black", resistanceKg = 30.0, anchor = BandAnchor.OTHER)
        val inv = Inventory(listOf(light, heavy))
        assertTrue(Capability.BAND in inv.capabilities)
        assertFalse(Capability.BAND_ANCHORED in inv.capabilities, "improvised anchors must not unlock anchored exercises")
        assertFalse(inv.isCompatible(ExerciseCatalog.get("band_row")!!))
        assertTrue(inv.isCompatible(ExerciseCatalog.get("band_pull_apart")!!))

        val anchored = Inventory(listOf(light.copy(anchor = BandAnchor.DOOR_ANCHOR)))
        assertTrue(anchored.isCompatible(ExerciseCatalog.get("band_row")!!))
    }

    @Test fun disabledEquipmentIgnored() {
        val inv = Inventory(listOf(defaultInventory()[0].copy(enabled = false)))
        assertTrue(inv.capabilities.isEmpty())
    }

    @Test fun customEquipmentDeclaresCapabilities() {
        val inv = Inventory(listOf(EquipmentItem(type = EquipmentType.CUSTOM, name = "Sturdy door bar", customCapabilities = setOf(Capability.PULL_UP_BAR))))
        assertTrue(inv.isCompatible(ExerciseCatalog.get("negative_pull_up")!!))
    }

    @Test fun barbellLoadsUsePlatePairs() {
        val inv = Inventory(listOf(
            EquipmentItem(type = EquipmentType.BARBELL, name = "Bar", weightKg = 10.0),
            EquipmentItem(type = EquipmentType.WEIGHT_PLATE, name = "5s", quantity = 2, weightKg = 5.0),
            EquipmentItem(type = EquipmentType.WEIGHT_PLATE, name = "2.5s", quantity = 3, weightKg = 2.5),
        ))
        assertEquals(listOf(10.0, 15.0, 20.0, 25.0), inv.barbellLoads())
    }

    @Test fun validation() {
        assertTrue(EquipmentItem(type = EquipmentType.FIXED_DUMBBELL, name = "x", weightKg = null).validate().isNotEmpty())
        assertTrue(EquipmentItem(type = EquipmentType.RESISTANCE_BAND, name = "b").validate().isNotEmpty())
        assertTrue(EquipmentItem(type = EquipmentType.ADJUSTABLE_DUMBBELL, name = "a", minWeightKg = 10.0, maxWeightKg = 5.0, incrementKg = 1.0).validate().isNotEmpty())
        assertTrue(EquipmentItem(type = EquipmentType.BENCH, name = "b").validate().isNotEmpty())
        assertTrue(defaultInventory().all { it.validate().isEmpty() })
    }

    @Test fun catalogueIdsUniqueAndReferencesValid() {
        assertEquals(ExerciseCatalog.all.size, ExerciseCatalog.all.map { it.id }.toSet().size)
        for (ex in ExerciseCatalog.all) for (ref in ex.easier + ex.harder) assertNotNull(ExerciseCatalog.get(ref), "${ex.id} -> $ref")
        for (ex in ExerciseCatalog.all) assertTrue(ex.instructions.isNotEmpty() && ex.cues.isNotEmpty() && ex.commonErrors.isNotEmpty(), ex.id)
    }
}
