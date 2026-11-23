package app.yeschef

import app.yeschef.data.cook.Densities
import app.yeschef.data.cook.KitchenUnit
import app.yeschef.data.cook.convertUnit
import app.yeschef.data.cook.formatConverted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConversionTest {
    private fun show(amount: Double, from: KitchenUnit, to: KitchenUnit, ingredient: String? = null): String? {
        val d = ingredient?.let { n -> Densities.first { it.name == n } }
        return convertUnit(amount, from, to, d)?.let { formatConverted(it, to) }
    }

    @Test fun convertsVolumes() {
        assertEquals("16", show(1.0, KitchenUnit.CUP, KitchenUnit.TBSP))
        assertEquals("3", show(1.0, KitchenUnit.TBSP, KitchenUnit.TSP))
        assertEquals("237", show(1.0, KitchenUnit.CUP, KitchenUnit.ML))
        assertEquals("2", show(1.0, KitchenUnit.PINT, KitchenUnit.CUP))
        assertEquals("4", show(1.0, KitchenUnit.GALLON, KitchenUnit.QUART))
        assertEquals("8", show(1.0, KitchenUnit.CUP, KitchenUnit.FL_OZ))
        assertEquals("4¼", show(1.0, KitchenUnit.L, KitchenUnit.CUP))
        assertEquals("½", show(4.0, KitchenUnit.FL_OZ, KitchenUnit.CUP))
    }

    @Test fun convertsWeights() {
        assertEquals("454", show(1.0, KitchenUnit.LB, KitchenUnit.G))
        assertEquals("16", show(1.0, KitchenUnit.LB, KitchenUnit.OZ))
        assertEquals("1⅛", show(500.0, KitchenUnit.G, KitchenUnit.LB))
        assertEquals("0.25", show(250.0, KitchenUnit.G, KitchenUnit.KG))
    }

    @Test fun weightAndVolumeNeedAnIngredient() {
        assertNull(show(1.0, KitchenUnit.CUP, KitchenUnit.G))
        assertEquals("125", show(1.0, KitchenUnit.CUP, KitchenUnit.G, "All-purpose flour"))
        assertEquals("200", show(1.0, KitchenUnit.CUP, KitchenUnit.G, "Sugar"))
        assertEquals("½", show(113.5, KitchenUnit.G, KitchenUnit.CUP, "Butter"))
        assertEquals("1", show(125.0, KitchenUnit.G, KitchenUnit.CUP, "All-purpose flour"))
    }

    @Test fun formatsForTheKitchen() {
        assertEquals("1½", formatConverted(1.5, KitchenUnit.CUP))
        assertEquals("⅓", formatConverted(1.0 / 3, KitchenUnit.CUP))
        assertEquals("0.06", formatConverted(0.0625, KitchenUnit.CUP)) // too small for a fraction
        assertEquals("0.15", formatConverted(0.15, KitchenUnit.CUP)) // no fraction close enough
        assertEquals("4.9", formatConverted(4.92892, KitchenUnit.ML))
        assertEquals("1.5", formatConverted(1.5, KitchenUnit.L))
        assertEquals("12.5", formatConverted(12.5, KitchenUnit.OZ))
    }
}
