package app.yeschef.ui.cook

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.yeschef.data.cook.Densities
import app.yeschef.data.cook.Density
import app.yeschef.data.cook.KitchenUnit
import app.yeschef.data.cook.UnitKind
import app.yeschef.data.cook.convertUnit
import app.yeschef.data.cook.formatConverted
import app.yeschef.data.cook.parseAmount
import app.yeschef.ui.SectionLabel

/** Stateless converter: an amount and unit in, that amount in every other unit out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnitConverterContent(
    amount: String,
    onAmount: (String) -> Unit,
    from: KitchenUnit,
    onFrom: (KitchenUnit) -> Unit,
    density: Density?,
    onDensity: (Density?) -> Unit,
) {
    val value = parseAmount(amount)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(
            "US cups and spoons. Pick an ingredient to go between weight and volume.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        )
        OutlinedTextField(
            value = amount,
            onValueChange = onAmount,
            label = { Text("Amount") },
            singleLine = true,
            isError = amount.isNotBlank() && value == null,
            textStyle = MaterialTheme.typography.titleLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 240.dp),
        )
        SectionLabel("From")
        UnitChips(KitchenUnit.entries.filter { it.kind == UnitKind.VOLUME }, from, onFrom)
        UnitChips(KitchenUnit.entries.filter { it.kind == UnitKind.WEIGHT }, from, onFrom)
        SectionLabel("Ingredient")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("None", density == null, tertiary = true) { onDensity(null) }
            Densities.forEach { d -> Chip(d.name, d == density, tertiary = true) { onDensity(d) } }
        }
        val other = if (from.kind == UnitKind.VOLUME) UnitKind.WEIGHT else UnitKind.VOLUME
        listOf(from.kind, other).forEach { kind ->
            SectionLabel(if (kind == UnitKind.VOLUME) "Volume" else "Weight")
            val units = KitchenUnit.entries.filter { it.kind == kind && it != from }
            if (kind != from.kind && density == null) {
                Text(
                    "Pick an ingredient to see the ${if (kind == UnitKind.WEIGHT) "weight" else "volume"}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            } else {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    units.forEach { u ->
                        val out = value?.let { convertUnit(it, from, u, density) }
                        ResultTile(out?.let { formatConverted(it, u) } ?: "–", u.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitChips(units: List<KitchenUnit>, selected: KitchenUnit, onSelect: (KitchenUnit) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        units.forEach { u -> Chip(u.label, u == selected) { onSelect(u) } }
    }
}

@Composable
internal fun Chip(label: String, selected: Boolean, tertiary: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = if (tertiary) colors.tertiaryContainer else colors.primaryContainer,
            selectedLabelColor = if (tertiary) colors.onTertiaryContainer else colors.onPrimaryContainer,
        ),
    )
}

@Composable
private fun ResultTile(value: String, unit: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.widthIn(min = 92.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
