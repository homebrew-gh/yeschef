package app.yeschef

import app.yeschef.data.cook.KitchenReference
import app.yeschef.data.cook.RefTopic
import app.yeschef.data.cook.fahrenheitToCelsius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KitchenReferenceTest {
    @Test fun everyTopicHasTables() {
        RefTopic.entries.forEach { assertTrue("$it has no tables", KitchenReference[it].orEmpty().isNotEmpty()) }
    }

    @Test fun rowsMatchTheirColumns() {
        KitchenReference.values.flatten().forEach { t ->
            assertEquals("${t.title} weights", t.columns.size, t.weights.size)
            t.rows.forEach { row -> assertEquals("${t.title}: $row", t.columns.size, row.size) }
        }
    }

    @Test fun celsiusFromFahrenheit() {
        assertEquals(63, fahrenheitToCelsius(145))
        assertEquals(71, fahrenheitToCelsius(160))
        assertEquals(74, fahrenheitToCelsius(165))
        assertEquals(52, fahrenheitToCelsius(125))
    }

    @Test fun safeTemperaturesAreUsdaValues() {
        val safe = KitchenReference.getValue(RefTopic.TEMPS).first().rows.associate { it[0] to it[1] }
        assertEquals("165°F", safe["Chicken and turkey: whole, pieces, ground"])
        assertEquals("160°F", safe["Ground beef, pork, veal, lamb, sausage"])
        assertEquals("145°F", safe["Beef, pork, veal, lamb: steaks, chops, roasts"])
    }

    /** Each oven row's Celsius is the Fahrenheit rounded to the nearest 10, as recipes print it. */
    @Test fun ovenCelsiusIsCloseToFahrenheit() {
        KitchenReference.getValue(RefTopic.OVEN).first().rows.forEach { row ->
            val exact = fahrenheitToCelsius(row[0].toInt())
            assertTrue("$row", kotlin.math.abs(exact - row[1].toInt()) <= 6)
        }
    }
}
