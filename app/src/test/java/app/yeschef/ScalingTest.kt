package app.yeschef

import app.yeschef.data.cook.baseServings
import app.yeschef.data.cook.formatAmount
import app.yeschef.data.cook.scaleIngredient
import org.junit.Assert.assertEquals
import org.junit.Test

class ScalingTest {
    @Test fun scalesLeadingAmounts() {
        assertEquals("2 lb ground beef", scaleIngredient("1 lb ground beef", 2.0))
        assertEquals("3 cups flour", scaleIngredient("1 1/2 cups flour", 2.0))
        assertEquals("¾ tsp salt", scaleIngredient("1½ tsp salt", 0.5))
        assertEquals("1 tsp vanilla", scaleIngredient("1/2 tsp vanilla", 2.0))
        assertEquals("4-6 cloves garlic", scaleIngredient("2-3 cloves garlic", 2.0))
        assertEquals("3 to 4½ cups stock", scaleIngredient("2 to 3 cups stock", 1.5))
        assertEquals("2¼ lb chicken", scaleIngredient("1.5 lb chicken", 1.5))
        assertEquals("2 (14 oz) cans tomatoes", scaleIngredient("1 (14 oz) cans tomatoes", 2.0))
        assertEquals("36 lasagna noodles", scaleIngredient("12 lasagna noodles", 3.0))
    }

    @Test fun leavesTextWithoutAmountAlone() {
        assertEquals("Salt to taste", scaleIngredient("Salt to taste", 2.0))
        assertEquals("1 lb beef", scaleIngredient("1 lb beef", 1.0))
        assertEquals("9x13 pan", scaleIngredient("9x13 pan", 2.0))
    }

    @Test fun formatsFriendlyFractions() {
        assertEquals("⅓", formatAmount(1.0 / 3))
        assertEquals("1½", formatAmount(1.49))
        assertEquals("2", formatAmount(1.97))
        assertEquals("⅛", formatAmount(0.05))
        assertEquals("13", formatAmount(12.6))
    }

    @Test fun servings() {
        assertEquals(4.0, baseServings(4.0, "8 servings")!!, 0.0)
        assertEquals(8.0, baseServings(null, "8 servings")!!, 0.0)
        assertEquals(null, baseServings(0.0, "One loaf"))
    }

    // Mealie's display text: Griddle steak fajitas, 6 servings up to 8.
    @Test fun scalesMealieSuperscriptFractions() {
        assertEquals("2⅓ lb flank steak", scaleIngredient("1 ³/₄ lb flank steak", 8.0 / 6))
        assertEquals("1 cup beef broth", scaleIngredient("¹/₂ cup beef broth", 2.0))
        assertEquals("1 ³/₄ lb flank steak", scaleIngredient("1 ³/₄ lb flank steak", 1.0))
    }
}
