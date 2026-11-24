package app.yeschef.ui.cook

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yeschef.data.cook.Densities
import app.yeschef.data.cook.KitchenUnit
import app.yeschef.data.cook.RefTopic
import app.yeschef.ui.isWide

enum class KitchenTool(val label: String) { CONVERT("Converter"), REFERENCE("Reference") }

/**
 * What the Kitchen tools sheet shows. It lives with the screen, so reopening the sheet
 * keeps the last conversion and the last reference page.
 */
class KitchenToolsState(open: Boolean, tool: KitchenTool, amount: String, from: KitchenUnit, density: String?, topic: RefTopic) {
    var open by mutableStateOf(open)
    var tool by mutableStateOf(tool)
    var amount by mutableStateOf(amount)
    var from by mutableStateOf(from)
    var density by mutableStateOf(density)
    var topic by mutableStateOf(topic)

    fun show(tool: KitchenTool) {
        this.tool = tool
        open = true
    }

    companion object {
        val Saver = listSaver<KitchenToolsState, Any?>(
            save = { listOf(it.open, it.tool.name, it.amount, it.from.name, it.density, it.topic.name) },
            restore = {
                KitchenToolsState(
                    it[0] as Boolean, KitchenTool.valueOf(it[1] as String), it[2] as String,
                    KitchenUnit.valueOf(it[3] as String), it[4] as String?, RefTopic.valueOf(it[5] as String),
                )
            },
        )
    }
}

@Composable
fun rememberKitchenToolsState(): KitchenToolsState = rememberSaveable(saver = KitchenToolsState.Saver) {
    KitchenToolsState(false, KitchenTool.CONVERT, "1", KitchenUnit.CUP, null, RefTopic.TEMPS)
}

/** The converter and reference tables in a bottom sheet over whatever is on screen, so cook mode stays put. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenToolsSheet(state: KitchenToolsState) {
    if (!state.open) return
    ModalBottomSheet(
        onDismissRequest = { state.open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Tablets get a wider sheet so the reference tables sit side by side.
        sheetMaxWidth = if (isWide()) 960.dp else BottomSheetDefaults.SheetMaxWidth,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text("Kitchen tools", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            SecondaryTabRow(selectedTabIndex = state.tool.ordinal, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                KitchenTool.entries.forEach { t ->
                    Tab(selected = state.tool == t, onClick = { state.tool = t }, text = { Text(t.label) })
                }
            }
            when (state.tool) {
                KitchenTool.CONVERT -> UnitConverterContent(
                    amount = state.amount, onAmount = { state.amount = it },
                    from = state.from, onFrom = { state.from = it },
                    density = Densities.firstOrNull { it.name == state.density }, onDensity = { state.density = it?.name },
                )
                KitchenTool.REFERENCE -> KitchenReferenceContent(state.topic, onTopic = { state.topic = it })
            }
        }
    }
}
