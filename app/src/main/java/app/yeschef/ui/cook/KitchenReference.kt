package app.yeschef.ui.cook

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yeschef.data.cook.KitchenReference
import app.yeschef.data.cook.RefTable
import app.yeschef.data.cook.RefTopic
import app.yeschef.ui.isWide
import app.yeschef.ui.theme.OverlineStyle

/** Stateless reference pages: a row of topics, then that topic's tables. Two columns on a tablet. */
@Composable
fun KitchenReferenceContent(topic: RefTopic, onTopic: (RefTopic) -> Unit, wide: Boolean = isWide()) {
    val tables = KitchenReference[topic].orEmpty()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RefTopic.entries.forEach { t -> Chip(t.label, t == topic) { onTopic(t) } }
        }
        if (wide) {
            tables.chunked(2).forEach { pair ->
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    pair.forEach { RefTableView(it, Modifier.weight(1f)) }
                    if (pair.size == 1 && tables.size > 1) Column(Modifier.weight(1f)) {}
                }
            }
        } else {
            tables.forEach { RefTableView(it, Modifier.padding(horizontal = 16.dp)) }
        }
    }
}

@Composable
private fun RefTableView(table: RefTable, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(
            table.title.uppercase(),
            style = OverlineStyle,
            color = colors.tertiary,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
        )
        Surface(color = colors.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                RefRow(table.columns, table.weights, header = true)
                table.rows.forEach { row ->
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                    RefRow(row, table.weights, header = false)
                }
            }
        }
        table.note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun RefRow(cells: List<String>, weights: List<Float>, header: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = if (header) 6.dp else 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                // The first column names the thing; short values after it are what you came for.
                fontWeight = if (!header && i > 0 && cell.length <= 14) FontWeight.SemiBold else null,
                color = if (header) colors.tertiary else colors.onSurface,
                modifier = Modifier.weight(weights[i]),
            )
        }
    }
}
