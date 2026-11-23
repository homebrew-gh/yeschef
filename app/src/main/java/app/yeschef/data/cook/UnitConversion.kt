package app.yeschef.data.cook

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Kitchen unit conversion, all offline. Volumes are US customary (1 cup = 236.6 ml).
 * Weight and volume only convert into each other through an ingredient's density.
 */
enum class UnitKind { VOLUME, WEIGHT }

/** [toBase] is millilitres for a volume, grams for a weight. */
enum class KitchenUnit(val label: String, val kind: UnitKind, val toBase: Double, val metric: Boolean) {
    TSP("tsp", UnitKind.VOLUME, 4.92892, false),
    TBSP("tbsp", UnitKind.VOLUME, 14.7868, false),
    FL_OZ("fl oz", UnitKind.VOLUME, 29.5735, false),
    CUP("cup", UnitKind.VOLUME, 236.588, false),
    PINT("pint", UnitKind.VOLUME, 473.176, false),
    QUART("quart", UnitKind.VOLUME, 946.353, false),
    GALLON("gallon", UnitKind.VOLUME, 3785.41, false),
    ML("ml", UnitKind.VOLUME, 1.0, true),
    L("l", UnitKind.VOLUME, 1000.0, true),
    G("g", UnitKind.WEIGHT, 1.0, true),
    KG("kg", UnitKind.WEIGHT, 1000.0, true),
    OZ("oz", UnitKind.WEIGHT, 28.3495, false),
    LB("lb", UnitKind.WEIGHT, 453.592, false),
}

/** A common ingredient and how much a level US cup of it weighs. */
data class Density(val name: String, val gramsPerCup: Double) {
    val gramsPerMl: Double get() = gramsPerCup / KitchenUnit.CUP.toBase
}

/** Typical weights for a level US cup (spooned and levelled for flours). Good to a few percent. */
val Densities = listOf(
    Density("Water", 237.0),
    Density("Milk", 242.0),
    Density("Oil", 218.0),
    Density("Butter", 227.0),
    Density("Honey", 340.0),
    Density("All-purpose flour", 125.0),
    Density("Bread flour", 130.0),
    Density("Whole wheat flour", 120.0),
    Density("Sugar", 200.0),
    Density("Brown sugar, packed", 213.0),
    Density("Powdered sugar", 120.0),
    Density("Cocoa powder", 85.0),
    Density("Cornstarch", 120.0),
    Density("Rice, uncooked", 185.0),
    Density("Rolled oats", 90.0),
    Density("Table salt", 292.0),
)

/** [amount] of [from] in [to], or null when weight and volume meet without a density. */
fun convertUnit(amount: Double, from: KitchenUnit, to: KitchenUnit, density: Density? = null): Double? {
    val base = amount * from.toBase
    val targetBase = when {
        from.kind == to.kind -> base
        density == null -> return null
        from.kind == UnitKind.VOLUME -> base * density.gramsPerMl // ml -> g
        else -> base / density.gramsPerMl // g -> ml
    }
    return targetBase / to.toBase
}

/**
 * A converted amount the way a cook reads it: fractions for cups and spoons
 * ("1½ cup"), whole grams and millilitres, decimals for kg and litres.
 */
fun formatConverted(value: Double, unit: KitchenUnit): String {
    if (value <= 0) return "0"
    if (unit.metric) {
        return when {
            unit.toBase > 1 -> trimDecimals(value, if (value < 10) 2 else 1) // kg, l
            value < 10 -> trimDecimals(value, 1)
            else -> value.roundToLong().toString()
        }
    }
    // Fractions only when they are close (within 5%), else a short decimal.
    if (value >= 0.125 && value < 10) {
        val f = formatAmount(value)
        val back = parseAmount(f) ?: 0.0
        if (abs(back - value) / value <= 0.05) return f
    }
    return when {
        value >= 100 -> value.roundToLong().toString()
        value >= 10 -> trimDecimals(value, 1)
        else -> trimDecimals(value, 2)
    }
}

private fun trimDecimals(value: Double, places: Int): String =
    "%.${places}f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
