package app.fieldwatch.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchSlider
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.BehavioralKind
import app.fieldwatch.domain.FilterLogic
import app.fieldwatch.domain.FilterPreset
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.ui.ClassGlyphs
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.component.FieldwatchFilterChip
import app.fieldwatch.ui.component.spectreSectionFill
import app.fieldwatch.ui.component.spectreTileEdge
import app.fieldwatch.ui.component.spectreTileFill
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.PhosphorActive
import app.fieldwatch.ui.theme.nightIf
import app.fieldwatch.ui.i18n.LocalAppStrings
import app.fieldwatch.ui.i18n.localizedLabel
import app.fieldwatch.ui.i18n.localizePresetName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersScreen(state: FieldwatchUi, vm: FieldwatchViewModel) {
    val strings = LocalAppStrings.current
    var presetName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<FilterPreset?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val filter = state.filter
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar(strings.filtersTitle) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(strings.filterPresets) {
            Text(
                strings.filterPresetsHelp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.presets.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row.forEach { preset ->
                            PresetChip(
                                name = localizePresetName(preset.name, strings.isEs),
                                selected = preset.filter == filter,
                                onApply = { vm.applyPreset(preset) },
                                onLongPress = { pendingDelete = preset },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldwatchOutlinedField(
                    presetName,
                    { presetName = it },
                    strings.filterSaveAsHint,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    if (presetName.isNotBlank()) {
                        vm.savePreset(presetName.trim())
                        presetName = ""
                    }
                }) { Text(strings.save) }
            }
            }

            SectionCard(strings.filterRadiosSection) {
            Text(
                if (filter.movingWithYou) {
                    strings.filterRadiosDescMoving
                } else {
                    strings.filterRadiosDescBoth
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = !filter.movingWithYou && filter.showWifi && filter.showBle,
                    onClick = { vm.updateFilter { it.copy(showWifi = true, showBle = true) } },
                    enabled = !filter.movingWithYou,
                    label = { Text(strings.filterBoth) },
                )
                FieldwatchFilterChip(
                    selected = !filter.movingWithYou && filter.showWifi && !filter.showBle,
                    onClick = { vm.updateFilter { it.copy(showWifi = true, showBle = false) } },
                    enabled = !filter.movingWithYou,
                    label = { Text(strings.filterPresetWifi) },
                )
                FieldwatchFilterChip(
                    selected = filter.movingWithYou || (filter.showBle && !filter.showWifi),
                    onClick = { vm.updateFilter { it.copy(showWifi = false, showBle = true) } },
                    label = { Text(strings.filterPresetBle) },
                )
            }
            }

            SectionCard(strings.filterMovingSection) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.filterMovingSection, Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.movingWithYou,
                    { on ->
                        vm.updateFilter { current ->
                            if (!on) current.copy(movingWithYou = false)
                            else {
                                // Follow test is BLE. Leftover Trackers / Show only hides
                                // unmatched rows; AirTags rotate, so Live looks empty.
                                val hiding = current.useClassFilter && current.excludeClasses
                                current.copy(
                                    movingWithYou = true,
                                    showWifi = false,
                                    showBle = true,
                                    namedOnly = false,
                                    customNamesOnly = false,
                                    watchedOnly = false,
                                    useClassFilter = hiding,
                                    excludeClasses = hiding,
                                    classes = if (hiding) current.classes else emptySet(),
                                    includeSignatures = false,
                                )
                            }
                        }
                    },
                )
            }
            Text(
                when {
                    !state.settings.tagLocation ->
                        strings.filterMovingHelpGps
                    state.operatorSpanM < 45.0 ->
                        if (strings.isEs) "Ruta GPS hasta ahora: ${state.operatorSpanM.toInt()} m. Sigue moviéndote (~50 m)."
                        else "GPS path so far ${state.operatorSpanM.toInt()} m. Keep moving (~50 m)."
                    else ->
                        if (strings.isEs) "Ruta GPS: ${state.operatorSpanM.toInt()} m. Detectando emisores BLE que te acompañan a un nivel estable."
                        else "GPS path ${state.operatorSpanM.toInt()} m. Loud BLE heard along that path at a fairly steady level."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(strings.filterArrivalsSection) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.arrivalsLearning) strings.filterArrivalsLearning else strings.filterArrivalsOnly,
                    Modifier.weight(1f),
                )
                FieldwatchSwitch(
                    filter.arrivalsOnly,
                    { on -> vm.updateFilter { it.copy(arrivalsOnly = on) } },
                )
            }
            Text(
                strings.filterArrivalsDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(strings.filterWhoStaysSection) {
            val namedImplied = filter.namedOnlyImplied()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    strings.filterSignaturesOnly,
                    Modifier.weight(1f),
                    color = if (namedImplied) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                FieldwatchSwitch(
                    checked = filter.namedOnly || namedImplied,
                    onCheckedChange = { on ->
                        if (!namedImplied) vm.updateFilter { it.copy(namedOnly = on) }
                    },
                    enabled = !namedImplied,
                )
            }
            if (namedImplied) {
                Text(
                    if (strings.isEs) "Mostrar solo ya oculta las radios no coincidentes."
                    else "Show only already hides unmatched radios. Turn Show only off to use this switch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.filterWatchedOnly, Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.watchedOnly,
                    { on -> vm.updateFilter { it.copy(watchedOnly = on) } },
                )
            }
            Text(
                strings.filterWatchedOnlyDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.filterNamedOnly, Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.customNamesOnly,
                    { on -> vm.updateFilter { it.copy(customNamesOnly = on) } },
                )
            }
            Text(
                strings.filterNamedOnlyDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.filterHideFastPair, Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.hideFastPairAccountKey,
                    { on -> vm.updateFilter { it.copy(hideFastPairAccountKey = on) } },
                )
            }
            Text(
                strings.filterHideFastPairDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(strings.filterClassesSection) {
            Text(
                strings.filterClassesDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = filter.useClassFilter && !filter.excludeClasses,
                    onClick = {
                        vm.updateFilter {
                            val on = !(it.useClassFilter && !it.excludeClasses)
                            it.copy(useClassFilter = on, excludeClasses = false)
                        }
                    },
                    label = { Text(strings.filterShowOnly) },
                )
                FieldwatchFilterChip(
                    selected = filter.useClassFilter && filter.excludeClasses,
                    onClick = {
                        vm.updateFilter {
                            val on = !(it.useClassFilter && it.excludeClasses)
                            it.copy(useClassFilter = on, excludeClasses = on)
                        }
                    },
                    label = { Text(strings.filterHideThese) },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SignatureClass.visible.sortedBy { it.label().lowercase() }.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row.forEach { kind ->
                            val on = kind in filter.classes
                            FieldwatchFilterChip(
                                selected = on,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    vm.updateFilter { current ->
                                        val next = current.classes.toMutableSet()
                                        if (on) next.remove(kind) else next.add(kind)
                                        current.copy(classes = next)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        ClassGlyphs.of(kind),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        kind.localizedLabel(strings.isEs),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            }

            // FASE 1: filtro por clase de comportamiento.
            SectionCard(
                if (strings.isEs) "Clases de comportamiento" else "Behavioral classes",
            ) {
            Text(
                if (strings.isEs) {
                    "Filtra por patrón de emisión (FASE 1), independiente de las firmas. Show only con Behavioral activo NO implica named-only: un match conductual puede no tener firma."
                } else {
                    "Filter by emission pattern (FASE 1), independent of signatures. Show only with Behavioral active does NOT imply named-only: a behavioral match can have no signature."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = filter.useBehavioralFilter && !filter.excludeBehavioral,
                    onClick = {
                        vm.updateFilter {
                            val on = !(it.useBehavioralFilter && !it.excludeBehavioral)
                            it.copy(useBehavioralFilter = on, excludeBehavioral = false)
                        }
                    },
                    label = { Text(strings.filterShowOnly) },
                )
                FieldwatchFilterChip(
                    selected = filter.useBehavioralFilter && filter.excludeBehavioral,
                    onClick = {
                        vm.updateFilter {
                            val on = !(it.useBehavioralFilter && it.excludeBehavioral)
                            it.copy(useBehavioralFilter = on, excludeBehavioral = on)
                        }
                    },
                    label = { Text(strings.filterHideThese) },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BehavioralKind.entries
                    .sortedBy { it.label.lowercase() }
                    .chunked(2)
                    .forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            row.forEach { kind ->
                                val on = kind in filter.behavioralKinds
                                FieldwatchFilterChip(
                                    selected = on,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        vm.updateFilter { current ->
                                            val next = current.behavioralKinds.toMutableSet()
                                            if (on) next.remove(kind) else next.add(kind)
                                            current.copy(behavioralKinds = next)
                                        }
                                    },
                                    label = {
                                        Text(
                                            kind.label,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
            }
            }

            SectionCard(strings.filterSelectedSignatures) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.filterShowSelectedSignatures, Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.includeSignatures,
                    { on -> vm.updateFilter { it.copy(includeSignatures = on) } },
                )
            }
            if (filter.includeSignatures) {
                SignaturePickList(
                    fleets = state.fleets,
                    selected = filter.includeFleetIds,
                    help = if (strings.isEs) "Toca una clase para abrir sus firmas. Solo las radios que coincidan con una firma que actives permanecerán en En vivo."
                    else "Tap a class to open its signatures. Only radios matching a signature you turn on below stay on Live.",
                    isEs = strings.isEs,
                    onToggle = { id, checked ->
                        vm.updateFilter { current ->
                            val next = current.includeFleetIds.toMutableSet()
                            if (checked) next.add(id) else next.remove(id)
                            current.copy(includeFleetIds = next)
                        }
                    },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.filterHideSelectedSignatures, Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.excludeSignatures,
                    { on -> vm.updateFilter { it.copy(excludeSignatures = on) } },
                )
            }
            if (filter.excludeSignatures) {
                SignaturePickList(
                    fleets = state.fleets,
                    selected = filter.fleetIds,
                    help = if (strings.isEs) "Toca una clase para abrir sus firmas. Los dispositivos que coincidan con una firma que actives se ocultarán de En vivo."
                    else "Tap a class to open its signatures. Devices matching a signature you turn on below stay off the Live list.",
                    isEs = strings.isEs,
                    onToggle = { id, checked ->
                        vm.updateFilter { current ->
                            val next = current.fleetIds.toMutableSet()
                            if (checked) next.add(id) else next.remove(id)
                            current.copy(fleetIds = next)
                        }
                    },
                )
            }
            }

            SectionCard(strings.filterFineSection) {
            var rssiDrag by remember { mutableIntStateOf(filter.rssiMin) }
            var rssiDragging by remember { mutableStateOf(false) }
            LaunchedEffect(filter.rssiMin) {
                if (!rssiDragging) rssiDrag = filter.rssiMin
            }
            Text("${strings.filterMinRssi}  $rssiDrag dBm", style = MaterialTheme.typography.labelLarge)
            FieldwatchSlider(
                value = rssiDrag.toFloat(),
                onValueChange = { v ->
                    rssiDragging = true
                    rssiDrag = v.toInt()
                },
                onValueChangeFinished = {
                    vm.updateFilter { it.copy(rssiMin = rssiDrag) }
                    rssiDragging = false
                },
                valueRange = -100f..-30f,
            )

            FieldwatchOutlinedField(
                filter.nameQuery,
                { value -> vm.updateFilter { it.copy(nameQuery = value) } },
                strings.filterNameQueryHint,
            )
            FieldwatchOutlinedField(
                filter.ouiQuery,
                { value -> vm.updateFilter { it.copy(ouiQuery = value) } },
                strings.filterOuiQueryHint,
            )

            Text(strings.filterExtraLogic, style = MaterialTheme.typography.labelLarge)
            Text(
                strings.filterExtraLogicDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = filter.logic == FilterLogic.AND,
                    onClick = { vm.updateFilter { it.copy(logic = FilterLogic.AND) } },
                    label = { Text(if (strings.isEs) "Y (AND)" else "AND") },
                )
                FieldwatchFilterChip(
                    selected = filter.logic == FilterLogic.OR,
                    onClick = { vm.updateFilter { it.copy(logic = FilterLogic.OR) } },
                    label = { Text(if (strings.isEs) "O (OR)" else "OR") },
                )
            }

            FieldwatchActionButton(onClick = { confirmReset = true }) {
                Text(strings.filterReset)
            }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(strings.filterResetConfirmTitle) },
            text = {
                Text(strings.filterResetConfirmMsg)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        vm.updateFilter { app.fieldwatch.domain.FilterState() }
                    },
                ) { Text(strings.reset) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(strings.cancel) }
            },
        )
    }
    pendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(strings.filterDeletePresetTitle) },
            text = {
                Text(
                    if (preset.isBuiltIn()) {
                        if (strings.isEs) "¿Quitar el preajuste de fábrica “${localizePresetName(preset.name, true)}”? Puedes recuperarlo desde Ajustes → Restablecer valores predeterminados."
                        else "Remove stock chip “${preset.name}” from this list? Catalog updates will not put it back. Settings → Restore default signatures & presets restores all stock chips."
                    } else {
                        if (strings.isEs) "¿Eliminar el preajuste “${preset.name}”? Esta acción no se puede deshacer."
                        else "Delete preset “${preset.name}”? This cannot be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePreset(preset.id)
                    pendingDelete = null
                }) { Text(strings.delete) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(strings.cancel) }
            },
        )
    }
}

@Composable
private fun SignaturePickList(
    fleets: List<Fleet>,
    selected: Set<String>,
    help: String,
    isEs: Boolean,
    onToggle: (id: String, checked: Boolean) -> Unit,
) {
    val groups = remember(fleets, isEs) {
        fleets.groupBy { it.kind.folded() }
            .toList()
            .sortedBy { it.first.localizedLabel(isEs).lowercase() }
            .map { (kind, rows) -> kind to rows.sortedBy { it.name.lowercase() } }
    }
    var open by remember {
        mutableStateOf(
            groups.filter { (_, rows) -> rows.any { it.id in selected } }
                .map { it.first.name }
                .toSet(),
        )
    }
    Column(
        modifier = Modifier.padding(start = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        groups.forEach { (kind, rows) ->
            val classId = kind.name
            val expanded = classId in open
            val picked = rows.count { it.id in selected }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        open = if (expanded) open - classId else open + classId
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    ClassGlyphs.of(kind),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    kind.localizedLabel(isEs),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(if (expanded) "▾  " else "▸  ")
                        if (picked > 0) append("$picked/")
                        append(rows.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (picked > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (expanded) {
                rows.forEach { fleet ->
                    val on = fleet.id in selected
                    Row(
                        modifier = Modifier.padding(start = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(fleet.name, Modifier.weight(1f))
                        FieldwatchSwitch(on, { checked -> onToggle(fleet.id, checked) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(
    name: String,
    selected: Boolean = false,
    onApply: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = FilterChipDefaults.shape,
            color = if (selected) spectreSectionFill() else spectreTileFill(),
            border = BorderStroke(
                1.dp,
                if (selected) PhosphorActive.nightIf(LocalNightMode.current) else spectreTileEdge(),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onApply,
                    onLongClick = onLongPress,
                ),
        ) {
            Text(
                name,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}