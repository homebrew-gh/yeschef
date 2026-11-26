package app.yeschef

import app.yeschef.data.Cookbook
import app.yeschef.data.Cookbook.Sort
import app.yeschef.data.local.RecipeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CookbookTest {
    private fun r(
        name: String, cats: String = "", tags: String = "", time: String? = null,
        lastMade: String? = null, rating: Double? = null, added: String? = null,
    ) = RecipeEntity(name, name.lowercase().replace(' ', '-'), name, null, time, null, null, cats, tags, rating, lastMade, added)

    private val box = listOf(
        r("Roast chicken", "Main course", "Chicken\nSunday", "1 hour 30 minutes", "2026-10-01T18:00:00Z", 5.0, "2026-01-01"),
        r("Garlic green beans", "Side dish", "Vegetarian\nQuick", "15 minutes", null, 4.0, "2026-11-01"),
        r("Lasagna", "Mains", "Beef", "PT1H30M", "2026-11-20T19:00:00-05:00", 4.5, "2026-03-01"),
        r("Banana bread", "Bread", "", "1 hour 10 minutes", "2025-06-01T10:00:00Z", null, "2025-05-01"),
        r("Grandma's chutney", "Preserves"),
        r("Mystery stew"),
    )

    @Test fun chaptersFromCategories() {
        assertEquals(listOf("Mains", "Sides", "Mains", "Baking", "Preserves", Cookbook.UNFILED), box.map(Cookbook::chapterOf))
        assertEquals(listOf("Mains", "Sides", "Baking", "Preserves", Cookbook.UNFILED), Cookbook.chapterOrder(box))
    }

    @Test fun sectionsInBookOrder() {
        val sections = Cookbook.sections(box, Sort.Chapters)
        assertEquals("Mains", sections[0].title)
        assertEquals(listOf("Lasagna", "Roast chicken"), sections[0].recipes.map { it.name })
    }

    @Test fun proteinFilter() {
        assertEquals(listOf("Garlic green beans"), Cookbook.filter(box, "", null, "Vegetarian").map { it.name })
        assertEquals(listOf("Lasagna"), Cookbook.filter(box, "", "Mains", "Beef").map { it.name })
        assertEquals(listOf("Garlic green beans"), Cookbook.filter(box, "quick", null, null).map { it.name })
        assertEquals(listOf("Garlic green beans"), Cookbook.filter(box, "", null, null, style = "Quick").map { it.name })
        assertEquals(setOf("Quick"), Cookbook.stylesOf(box[1]))
    }

    @Test fun sorts() {
        assertEquals(
            listOf("Banana bread", "Roast chicken", "Lasagna", "Garlic green beans", "Grandma's chutney", "Mystery stew"),
            Cookbook.sorted(box, Sort.NotLately).map { it.name },
        )
        assertEquals("Garlic green beans", Cookbook.sorted(box, Sort.Quickest).first().name)
        assertEquals("Roast chicken", Cookbook.sorted(box, Sort.TopRated).first().name)
        assertEquals("Garlic green beans", Cookbook.sorted(box, Sort.Newest).first().name)
    }

    @Test fun minutes() {
        assertEquals(70L, Cookbook.minutes("1 hour 10 minutes"))
        assertEquals(90L, Cookbook.minutes("PT1H30M"))
        assertEquals(45L, Cookbook.minutes("45 min"))
        assertNull(Cookbook.minutes("overnight"))
    }

    @Test fun lastCooked() {
        val today = LocalDate.of(2026, 11, 22)
        assertEquals("Cooked today", Cookbook.lastCookedText("2026-11-22T08:00:00Z", today))
        assertEquals("Cooked yesterday", Cookbook.lastCookedText("2026-11-21", today))
        assertEquals("Cooked 5 days ago", Cookbook.lastCookedText("2026-11-17T08:00:00Z", today))
        assertEquals("Cooked 7 weeks ago", Cookbook.lastCookedText("2026-10-01T18:00:00Z", today))
        assertEquals("Cooked Jun 2025", Cookbook.lastCookedText("2025-06-01T10:00:00Z", today))
        assertNull(Cookbook.lastCookedText(null, today))
    }

    @Test fun tagEditorRoundTrip() {
        val sel = Cookbook.selectionOf(box[0]) // Roast chicken: "Main course", tags Chicken + Sunday
        assertEquals(Cookbook.TagSelection("Mains", setOf("Chicken"), setOf("Sunday")), sel)
        val edited = sel.copy(chapter = "Sides", proteins = setOf("Vegetarian", "Chicken"), tags = setOf("Quick", "Sunday"))
        assertEquals(listOf("Sides"), Cookbook.categoriesFor(edited))
        assertEquals(listOf("Chicken", "Vegetarian", "Quick", "Sunday"), Cookbook.tagsFor(edited))
        assertEquals(Cookbook.TagSelection(null, emptySet(), emptySet()), Cookbook.selectionOf(box[5]))
    }

    @Test fun tagEditorChoices() {
        assertEquals("Preserves", Cookbook.chapterChoices(box).last())
        val tags = Cookbook.tagChoices(box)
        assertEquals(Cookbook.styleTags, tags.take(Cookbook.styleTags.size))
        assertEquals(listOf("Sunday"), tags.drop(Cookbook.styleTags.size)) // "Quick" not repeated, proteins left out
    }

    @Test fun diceRollsFromPickedChapter() {
        val today = LocalDate.parse("2026-11-24")
        // Lasagna was cooked 4 days ago, so a Mains roll gives the roast chicken every time.
        repeat(20) { seed ->
            assertEquals("Roast chicken", Cookbook.surprise(box, "", "Mains", null, today, kotlin.random.Random(seed))?.name)
        }
        assertEquals("Garlic green beans", Cookbook.surprise(box, "", "Sides", null, today)?.name)
        // Only recently cooked ones left: still roll one of them.
        assertEquals("Lasagna", Cookbook.surprise(box, "", "Mains", "Beef", today)?.name)
        assertNull(Cookbook.surprise(box, "", "Drinks", null, today))
        val anything = (0 until 200).mapNotNull { Cookbook.surprise(box, "", null, null, today, kotlin.random.Random(it))?.name }.toSet()
        assertEquals(box.map { it.name }.toSet() - "Lasagna", anything)
    }
}
