package app.yeschef.data.cook

import kotlin.math.roundToInt

/**
 * Static kitchen reference tables shown in the Kitchen tools sheet. Plain data, no network:
 * edit the lists below to change what the app shows.
 *
 * Safe temperatures, storage times and thawing come from USDA guidance (FoodSafety.gov).
 * Doneness, oven, pan and grain values are the usual cookbook figures.
 */
data class RefTable(
    val title: String,
    val columns: List<String>,
    val rows: List<List<String>>,
    val note: String? = null,
    /** Relative column widths; the last column usually holds the longest text. */
    val weights: List<Float> = List(columns.size) { 1f },
)

enum class RefTopic(val label: String) {
    TEMPS("Meat temps"),
    OVEN("Oven"),
    SWAPS("Swaps"),
    MEASURES("Measures"),
    PANS("Pans"),
    GRAINS("Eggs & grains"),
    SAFETY("Food safety"),
}

/** Celsius for a Fahrenheit temperature, so the two columns never drift apart. */
fun fahrenheitToCelsius(f: Int): Int = ((f - 32) * 5 / 9.0).roundToInt()

private fun temp(f: Int) = "$f°F"
private fun tempC(f: Int) = "${fahrenheitToCelsius(f)}°C"
private fun meat(food: String, f: Int, rest: String = "") = listOf(food, temp(f), tempC(f), rest)
private fun doneness(level: String, f: Int) = listOf(level, temp(f), tempC(f))
private fun range(fLow: Int, fHigh: Int) =
    listOf("$fLow–${fHigh}°F", "${fahrenheitToCelsius(fLow)}–${fahrenheitToCelsius(fHigh)}°C")

val KitchenReference: Map<RefTopic, List<RefTable>> = mapOf(
    RefTopic.TEMPS to listOf(
        RefTable(
            "Safe internal temperatures",
            listOf("Food", "°F", "°C", "Rest"),
            listOf(
                meat("Beef, pork, veal, lamb: steaks, chops, roasts", 145, "3 min"),
                meat("Ground beef, pork, veal, lamb, sausage", 160),
                meat("Chicken and turkey: whole, pieces, ground", 165),
                meat("Stuffing, casseroles, leftovers", 165),
                meat("Fresh or raw ham", 145, "3 min"),
                meat("Precooked ham, reheated", 140),
                meat("Fish and shellfish", 145),
                meat("Egg dishes", 160),
            ),
            note = "USDA minimums. Measure in the thickest part, away from bone. Fish is done when it flakes and turns opaque.",
            weights = listOf(2.6f, 0.9f, 0.9f, 0.9f),
        ),
        RefTable(
            "Steak and roast doneness",
            listOf("Doneness", "°F", "°C"),
            listOf(
                doneness("Rare", 125),
                doneness("Medium rare", 135),
                doneness("Medium", 145),
                doneness("Medium well", 150),
                doneness("Well done", 160),
            ),
            note = "Final temperature after resting. Take it off the heat about 5°F (3°C) early; it keeps rising as it rests. USDA's safe minimum for whole cuts is 145°F.",
            weights = listOf(2f, 1f, 1f),
        ),
        RefTable(
            "Cook past safe for tenderness",
            listOf("Cut", "°F", "°C"),
            listOf(
                listOf("Chicken thighs and drumsticks") + range(175, 185),
                listOf("Pulled pork, brisket, short ribs") + range(195, 205),
            ),
            note = "Safe at the minimums above, but these cuts get tender only once their connective tissue breaks down.",
            weights = listOf(2f, 1.2f, 1.2f),
        ),
    ),
    RefTopic.OVEN to listOf(
        RefTable(
            "Oven temperatures",
            listOf("°F", "°C", "Gas", "Heat"),
            listOf(
                listOf("250", "120", "½", "Very low"),
                listOf("275", "140", "1", "Low"),
                listOf("300", "150", "2", "Low"),
                listOf("325", "160", "3", "Moderately low"),
                listOf("350", "180", "4", "Moderate"),
                listOf("375", "190", "5", "Moderately hot"),
                listOf("400", "200", "6", "Hot"),
                listOf("425", "220", "7", "Hot"),
                listOf("450", "230", "8", "Very hot"),
                listOf("475", "240", "9", "Very hot"),
            ),
            note = "Fan or convection oven: set it about 25°F (15–20°C) lower, or check 5–10 minutes early.",
            weights = listOf(1f, 1f, 0.8f, 2f),
        ),
    ),
    RefTopic.SWAPS to listOf(
        RefTable(
            "Baking",
            listOf("Instead of", "Use"),
            listOf(
                listOf("1 cup buttermilk", "1 tbsp lemon juice or white vinegar, plus milk to make 1 cup. Let stand 5 minutes."),
                listOf("1 egg", "1 tbsp ground flaxseed + 3 tbsp water, rested 5 minutes; or ¼ cup applesauce."),
                listOf("1 tsp baking powder", "¼ tsp baking soda + ½ tsp cream of tartar."),
                listOf("1 cup self-rising flour", "1 cup all-purpose flour + 1½ tsp baking powder + ¼ tsp salt."),
                listOf("1 cup cake flour", "1 cup all-purpose flour, take out 2 tbsp, add 2 tbsp cornstarch."),
                listOf("1 cup brown sugar", "1 cup white sugar + 1 tbsp molasses (2 tbsp for dark)."),
                listOf("1 cup powdered sugar", "1 cup sugar + 1 tbsp cornstarch, blended fine."),
                listOf("1 cup honey", "1¼ cups sugar + ¼ cup more of the recipe's liquid."),
            ),
            weights = listOf(1.2f, 2f),
        ),
        RefTable(
            "Dairy",
            listOf("Instead of", "Use"),
            listOf(
                listOf("1 cup sour cream", "1 cup plain Greek yogurt."),
                listOf("1 cup heavy cream", "¾ cup milk + ¼ cup melted butter (won't whip)."),
                listOf("1 cup half-and-half", "½ cup whole milk + ½ cup heavy cream."),
                listOf("Unsalted butter", "Salted butter; cut the recipe's salt by ½ tsp per cup of butter."),
            ),
            weights = listOf(1.2f, 2f),
        ),
        RefTable(
            "Savory",
            listOf("Instead of", "Use"),
            listOf(
                listOf("1 tbsp cornstarch (to thicken)", "2 tbsp all-purpose flour."),
                listOf("1 tbsp fresh herbs", "1 tsp dried herbs."),
                listOf("1 garlic clove", "⅛ tsp garlic powder or ½ tsp jarred minced garlic."),
                listOf("1 small onion", "1 tsp onion powder or 1 tbsp dried minced onion."),
                listOf("1 tsp table salt", "1¼ tsp Morton kosher salt or 2 tsp Diamond Crystal."),
                listOf("1 tbsp lemon juice", "1 tbsp white or white wine vinegar."),
                listOf("1 cup wine (cooking)", "1 cup broth + 1 tsp vinegar or lemon juice."),
                listOf("1 cup tomato sauce", "½ cup tomato paste + ½ cup water."),
                listOf("1 cup bread crumbs", "1 cup crushed crackers or rolled oats."),
            ),
            weights = listOf(1.2f, 2f),
        ),
    ),
    RefTopic.MEASURES to listOf(
        RefTable(
            "US measures",
            listOf("Amount", "Equals"),
            listOf(
                listOf("1 tbsp", "3 tsp · 15 ml"),
                listOf("⅛ cup", "2 tbsp · 1 fl oz · 30 ml"),
                listOf("¼ cup", "4 tbsp · 60 ml"),
                listOf("⅓ cup", "5 tbsp + 1 tsp · 80 ml"),
                listOf("½ cup", "8 tbsp · 4 fl oz · 120 ml"),
                listOf("¾ cup", "12 tbsp · 180 ml"),
                listOf("1 cup", "16 tbsp · 8 fl oz · 240 ml"),
                listOf("1 pint", "2 cups · 16 fl oz · 475 ml"),
                listOf("1 quart", "2 pints · 4 cups · 950 ml"),
                listOf("1 gallon", "4 quarts · 16 cups · 3.8 l"),
            ),
            note = "Millilitres rounded the way recipes print them. A US cup is 236.6 ml; a metric cup is 250 ml.",
            weights = listOf(1f, 2.4f),
        ),
        RefTable(
            "Handy amounts",
            listOf("Item", "Equals"),
            listOf(
                listOf("1 stick butter", "½ cup · 8 tbsp · 4 oz · 113 g"),
                listOf("1 lb", "16 oz · 454 g"),
                listOf("1 oz", "28 g"),
                listOf("Pinch", "About ⅛ tsp or less"),
                listOf("1 lemon", "2–3 tbsp juice, 1 tbsp zest"),
                listOf("1 lime", "About 2 tbsp juice"),
            ),
            weights = listOf(1f, 2.4f),
        ),
    ),
    RefTopic.PANS to listOf(
        RefTable(
            "Pan sizes",
            listOf("Pan", "Metric", "Holds"),
            listOf(
                listOf("8 × 8 in square", "20 × 20 cm", "8 cups · 2 l"),
                listOf("9 × 9 in square", "23 × 23 cm", "10 cups · 2.5 l"),
                listOf("9 × 13 in", "23 × 33 cm", "14 cups · 3.5 l"),
                listOf("8 in round", "20 cm", "6 cups · 1.5 l"),
                listOf("9 in round", "23 cm", "8 cups · 2 l"),
                listOf("9 × 5 in loaf", "23 × 13 cm", "8 cups · 2 l"),
                listOf("8½ × 4½ in loaf", "22 × 11 cm", "6 cups · 1.5 l"),
                listOf("9 in pie plate", "23 cm", "4 cups · 1 l"),
                listOf("10 in Bundt", "25 cm", "12 cups · 3 l"),
            ),
            note = "Swap pans that hold about the same. A 9 × 13 takes the batter for two 9 in rounds. In a shallower pan it bakes faster, so check early.",
            weights = listOf(1.6f, 1.3f, 1.4f),
        ),
    ),
    RefTopic.GRAINS to listOf(
        RefTable(
            "Boiled eggs",
            listOf("Yolk", "Minutes"),
            listOf(
                listOf("Runny", "6"),
                listOf("Jammy", "7"),
                listOf("Just set", "9"),
                listOf("Hard", "11–12"),
            ),
            note = "Large eggs straight from the fridge, lowered into boiling water, then into ice water. Poached eggs take 3–4 minutes.",
            weights = listOf(2f, 1f),
        ),
        RefTable(
            "Grains on the stove",
            listOf("Grain (1 cup)", "Water", "Simmer"),
            listOf(
                listOf("White long-grain rice", "1¾ cups", "15–18 min"),
                listOf("Jasmine or basmati rice", "1½ cups", "15 min"),
                listOf("Brown rice", "2¼ cups", "40–45 min"),
                listOf("Quinoa, rinsed", "2 cups", "15 min"),
                listOf("Couscous", "1¼ cups", "5 min off heat"),
                listOf("Rolled oats", "2 cups", "5 min"),
                listOf("Steel-cut oats", "3–4 cups", "20–30 min"),
                listOf("Brown or green lentils", "3 cups", "20–30 min"),
                listOf("Red lentils", "2½ cups", "15–20 min"),
            ),
            note = "Covered on low heat. Let rice rest covered 5–10 minutes before fluffing. Pasta: about 4 quarts (4 l) of water and 1 tbsp salt per pound.",
            weights = listOf(2f, 1.1f, 1.3f),
        ),
    ),
    RefTopic.SAFETY to listOf(
        RefTable(
            "Temperatures",
            listOf("Where", "°F", "°C"),
            listOf(
                listOf("Fridge", "40°F or below", "4°C or below"),
                listOf("Freezer", "0°F", "−18°C"),
                listOf("Danger zone", "40–140°F", "4–60°C"),
            ),
            note = "Don't leave food out more than 2 hours, or 1 hour above 90°F (32°C).",
            weights = listOf(1.4f, 1.3f, 1.3f),
        ),
        RefTable(
            "In the fridge",
            listOf("Food", "Keeps"),
            listOf(
                listOf("Raw chicken, turkey, ground meat", "1–2 days"),
                listOf("Raw fish and shellfish", "1–2 days"),
                listOf("Raw steaks, chops, roasts", "3–5 days"),
                listOf("Cooked leftovers", "3–4 days"),
                listOf("Hard-boiled eggs", "1 week"),
                listOf("Raw eggs in the shell", "3–5 weeks"),
            ),
            note = "Frozen at 0°F, food stays safe indefinitely; quality is best within a few months.",
            weights = listOf(2.2f, 1f),
        ),
        RefTable(
            "Thawing",
            listOf("Method", "Time"),
            listOf(
                listOf("In the fridge", "About 1 day per 4–5 lb (2 kg)"),
                listOf("In cold water", "About 30 min per lb, changing the water every 30 min"),
                listOf("In the microwave", "Cook right away"),
            ),
            note = "Never thaw on the counter. Food thawed in the fridge can go back in the freezer.",
            weights = listOf(1.2f, 2f),
        ),
    ),
)
