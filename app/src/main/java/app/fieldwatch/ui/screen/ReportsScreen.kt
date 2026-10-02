package app.fieldwatch.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.ui.RadioClassBadge
import app.fieldwatch.ui.RadioKindMark
import app.fieldwatch.domain.LogExportKind
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.Sit
import app.fieldwatch.domain.SitDiff
import app.fieldwatch.domain.SitPathPlot
import app.fieldwatch.ui.component.AircraftAmber
import app.fieldwatch.ui.component.FieldwatchDropdownField
import app.fieldwatch.ui.component.SitPathCanvas
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.i18n.LocalAppStrings
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.FieldwatchSwitch
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.theme.Cyan
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    exporting: Boolean,
    onSaveToStorage: () -> Unit,
    onSaveSitToStorage: () -> Unit,
    onSignatureCandidates: () -> Unit,
    onOpenPathRadio: (String) -> Unit = {},
) {
    val settings = state.settings
    var confirmClear by remember { mutableStateOf(false) }
    var startSit by remember { mutableStateOf(false) }
    var sitNameDraft by remember { mutableStateOf("") }
    var renameSitId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleteSitId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmClearTraining by remember { mutableStateOf(false) }
    val strings = LocalAppStrings.current
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar(strings.reportsTitle) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (settings.demoMode) {
                Text(
                    if (strings.isEs) {
                        "Modo de privacidad activado. Las terminaciones MAC en informes, comparaciones de situación, exportación IA y compartir están como **:**:**. Las coordenadas GPS están ocultas. El archivo de registro, exportación de situación y archivos GPX / KML / WiGLE conservan direcciones completas y coordenadas."
                    } else {
                        "Privacy mode is on. MAC tails in Debrief, sit compare, AI Export (sit or compare), and detail Share are **:**:**. GPS coordinates are masked. The log file, sit export, and GPX / KML / WiGLE files still have full addresses and lat/lon."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            SectionCard(strings.reportsSitsSection) {
                Text(
                    strings.reportsSitsDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val open = state.sit.open
                if (open != null) {
                    val dur = Sit.fmtDuration(open.durationMs())
                    Text(
                        if (strings.isEs) "Esta situación: ${open.name} · $dur · ${state.sit.radioCount} radios"
                        else "This sit: ${open.name} · $dur · ${state.sit.radioCount} radios",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FieldwatchActionButton(
                        onClick = vm::endSit,
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(strings.reportsEndSit) }
                } else {
                    FieldwatchActionButton(
                        onClick = {
                            sitNameDraft = vm.defaultSitName()
                            startSit = true
                        },
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(strings.reportsStartSit) }
                    Text(
                        if (state.sit.closed.isEmpty()) {
                            strings.reportsNoSitRunning
                        } else {
                            if (strings.isEs) "No hay ninguna situación activa. Inicia una aquí. La trayectoria e informes usarán la situación seleccionada."
                            else "No sit running. Start sit here. Path and Debrief use the selected sit."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.sit.closed.isEmpty() && open == null) {
                    Text(
                        strings.reportsNoSavedSits,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.sit.closed.isNotEmpty()) {
                    val pickEnabled = open == null && !exporting
                    SitChoiceRow(
                        selected = state.sit.selectedId == null,
                        enabled = pickEnabled,
                        title = strings.reportsLast15Min,
                        subtitle = strings.reportsLast15MinSub,
                        onSelect = { vm.selectSit(null) },
                    )
                    state.sit.closed.forEach { row ->
                        val dur = Sit.fmtDuration(row.durationMs())
                        val extra = if (row.extraAttentionCount > 0) {
                            if (strings.isEs) " · Atención especial ${row.extraAttentionCount}"
                            else " · Extra attention ${row.extraAttentionCount}"
                        } else {
                            ""
                        }
                        SitChoiceRow(
                            selected = state.sit.selectedId == row.id,
                            enabled = pickEnabled,
                            title = row.name,
                            subtitle = "${Sit.defaultName(row.startAt)} · $dur · ${row.radioCount} radios$extra",
                            onSelect = { vm.selectSit(row.id) },
                        )
                    }
                    if (open != null) {
                        Text(
                            if (strings.isEs) "Finaliza la situación actual para seleccionar una guardada para Trayectoria e Informes."
                            else "End sit to pick a saved one for Path and Debrief.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val picked = state.sit.closed.firstOrNull { it.id == state.sit.selectedId }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FieldwatchActionButton(
                            onClick = {
                                if (picked != null) {
                                    renameSitId = picked.id
                                    renameDraft = picked.name
                                }
                            },
                            enabled = !exporting && picked != null,
                            modifier = Modifier.weight(1f),
                        ) { Text(strings.rename) }
                        FieldwatchActionButton(
                            onClick = { if (picked != null) deleteSitId = picked.id },
                            enabled = !exporting && picked != null,
                            modifier = Modifier.weight(1f),
                        ) { Text(strings.delete) }
                    }
                    FieldwatchActionButton(
                        onClick = { confirmDeleteAll = true },
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(strings.reportsDeleteAll) }
                }
            }

            val pathModel by vm.sitPath.collectAsStateWithLifecycle()
            LaunchedEffect(state.sit.selectedId, state.sit.open?.id) {
                while (true) {
                    vm.refreshSitPath()
                    kotlinx.coroutines.delay(3_000L)
                }
            }
            SectionCard(strings.reportsPathSection) {
                Text(
                    strings.reportsPathDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val model = pathModel
                val showWalk = model != null && model.emptyHint == null
                val showAircraft = model != null && model.aircraftCards.isNotEmpty()
                val emptyHintDefault = if (strings.isEs) {
                    "Etiqueta detecciones con GPS y camina, o abre una situación que haya registrado una ruta."
                } else {
                    "Tag detections with GPS and walk, or open a sit that recorded a path."
                }
                if (model == null || (!showWalk && !showAircraft)) {
                    Text(
                        model?.emptyHint ?: emptyHintDefault,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val pathTiles by vm.pathTiles.collectAsStateWithLifecycle()
                    val aircraftTiles by vm.pathAircraftTiles.collectAsStateWithLifecycle()
                    if (!showWalk) {
                        Text(
                            model.emptyHint ?: emptyHintDefault,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showWalk) {
                    val stopN = model.dots.size
                    Text(
                        buildString {
                            if (strings.isEs) {
                                append("${model.title} · ${model.lengthM.toInt()} m de ruta · ${model.spanM.toInt()} m de alcance")
                                if (stopN > 0) {
                                    append(" · $stopN alerta")
                                    if (stopN != 1) append("s")
                                }
                            } else {
                                append("${model.title} · ${model.lengthM.toInt()} m path · ${model.spanM.toInt()} m span")
                                if (stopN > 0) {
                                    append(" · $stopN alert")
                                    if (stopN != 1) append("s")
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SitPathCanvas(model, tiles = pathTiles, onOpenRadio = onOpenPathRadio)
                    Text(
                        if (strings.isEs) "Toca un número para ver las radios en ese punto. Toca un icono para ver esa radio. Toca de nuevo para cerrar. Toca una fila para abrir la radio."
                        else "Tap a count for the radios there. Tap a single icon for that one radio. Tap again to close. Tap a row in that list, or a row below, to open that radio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (model.craft.isNotEmpty() || model.pilots.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (model.craft.isNotEmpty()) {
                                val multi = model.craft.any { it.samples.size >= 2 }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (multi) AdvertisedTrackSwatch() else AdvertisedRingSwatch()
                                    Text(
                                        if (strings.isEs) {
                                            if (multi) "= trayectoria transmitida a menos de 2 km de esta ruta"
                                            else "= una posición transmitida a menos de 2 km de esta ruta"
                                        } else {
                                            if (multi) "= advertised track within 2 km of this path"
                                            else "= one advertised position within 2 km of this path"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            if (model.pilots.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    PilotSwatch()
                                    Text(
                                        if (strings.isEs) "= piloto" else "= pilot",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    val alertsOnACard = model.aircraftCards.any { it.dots.isNotEmpty() }
                    if (model.dots.isEmpty() && !alertsOnACard) {
                        Text(
                            if (strings.isEs) "No hay alertas de MAC o firmas con marca GPS en esta ruta."
                            else "No MAC or signature alerts with a GPS stamp on this path.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (model.dots.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            model.dots.forEachIndexed { i, dot ->
                                PathRadioRow(
                                    index = i + 1,
                                    dot = dot,
                                    demoMode = settings.demoMode,
                                    onOpen = { onOpenPathRadio(dot.key) },
                                )
                            }
                        }
                    }
                    }
                    model.aircraftCards.forEachIndexed { index, card ->
                        val fixes = card.craft.sumOf { it.samples.size }
                        Text(
                            card.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = AircraftAmber,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            buildString {
                                if (strings.isEs) {
                                    append(if (fixes == 1) "1 posición transmitida" else "$fixes posiciones transmitidas")
                                    if (card.lengthM >= 1.0) append(" · ${card.lengthM.toInt()} m")
                                } else {
                                    append(if (fixes == 1) "1 advertised fix" else "$fixes advertised fixes")
                                    if (card.lengthM >= 1.0) append(" · ${card.lengthM.toInt()} m")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (card.dots.isNotEmpty()) {
                            Text(
                                if (strings.isEs) {
                                    if (card.dots.size == 1) "1 alerta" else "${card.dots.size} alertas"
                                } else {
                                    if (card.dots.size == 1) "1 alert" else "${card.dots.size} alerts"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SitPathCanvas(
                            card,
                            tiles = aircraftTiles.getOrElse(index) { emptyList() },
                            onOpenRadio = onOpenPathRadio,
                        )
                        if (card.dots.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                card.dots.forEachIndexed { i, dot ->
                                    PathRadioRow(
                                        index = i + 1,
                                        dot = dot,
                                        demoMode = settings.demoMode,
                                        onOpen = { onOpenPathRadio(dot.key) },
                                    )
                                }
                            }
                        }
                        if (card.caption.isNotBlank()) {
                            Text(
                                card.caption,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (model.looseAdvertised > 0) {
                        Text(
                            if (strings.isEs) {
                                if (model.looseAdvertised == 1) "Una posición transmitida sin ID de UAS está en el informe de situación."
                                else "Posiciones transmitidas sin ID de UAS están en el informe de situación."
                            } else {
                                if (model.looseAdvertised == 1) "An advertised position with no UAS id is in the sit report."
                                else "Advertised positions with no UAS id are in the sit report."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionCard(strings.reportsSitReportSection) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FieldwatchActionButton(
                    onClick = vm::startFieldDebrief,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) { Text(strings.reportsDebriefText) }
                FieldwatchActionButton(
                    onClick = vm::startFieldDebriefPdf,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) { Text(strings.reportsDebriefPdf) }
            }
            Text(
                sitReportCaption(state, strings.isEs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    strings.reportsShowUnmatchedBle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FieldwatchSwitch(
                    settings.debriefShowUnmatchedRandomBle,
                    { on -> vm.updateSettings { it.copy(debriefShowUnmatchedRandomBle = on) } },
                )
            }
            Text(
                strings.reportsShowUnmatchedBleDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startAiExport,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(strings.reportsAiExport) }
            Text(
                strings.reportsAiExportDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(strings.reportsSitExportSection) {
            val sitKind by vm.sitExportKind.collectAsStateWithLifecycle()
            val sitRadios by vm.sitExportRadios.collectAsStateWithLifecycle()
            ExportFormatBlock(
                kind = sitKind,
                radios = sitRadios,
                exporting = exporting,
                isEs = strings.isEs,
                onKind = vm::setSitExportKind,
                onRadios = vm::setSitExportRadios,
                onShare = vm::startSitExport,
                onSave = onSaveSitToStorage,
                hint = if (strings.isEs) {
                    "Una fila por cada radio única en esta situación (o últimos 15 min). Las líneas CSV / JSON incluyen firmas reconocidas y familias de atención especial. No es el registro rotativo. GPX / KML incluyen la trayectoria de este teléfono como ruta y puntos de detección. Fieldwatch no sube datos. El modo de privacidad no oculta este archivo."
                } else {
                    "One row per unique radio in this sit (or last 15 minutes). CSV / JSON lines include matched signatures and Extra attention families. Not the rotating log. GPX / KML include this phone’s path as a track plus hear-points. Fieldwatch does not upload. Privacy mode does not mask this file."
                },
            )
            }

            SectionCard(strings.reportsCompareSection) {
                Text(
                    compareThisCaption(state, strings.isEs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val thisSaved = SitDiff.thisSavedId(state.sit.open, state.sit.selectedId)
                val choices = SitDiff.secondSitChoices(state.sit.closed, thisSaved)
                if (choices.isEmpty()) {
                    Text(
                        if (strings.isEs) "Guarda una segunda situación para comparar. Inicia situación y luego finalízala. Los últimos 15 minutos pueden usarse como la situación actual."
                        else "Save a second sit to compare. Start sit, then End sit. Last 15 minutes can be this sit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        if (strings.isEs) "Segunda situación" else "Second sit",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    choices.forEach { row ->
                        val dur = Sit.fmtDuration(row.durationMs())
                        SitChoiceRow(
                            selected = state.sit.compareId == row.id,
                            enabled = !exporting,
                            title = row.name,
                            subtitle = "${Sit.defaultName(row.startAt)} · $dur · ${row.radioCount} radios",
                            onSelect = { vm.selectCompareSit(row.id) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FieldwatchActionButton(
                        onClick = vm::startSitCompare,
                        enabled = !exporting && state.sit.compareId != null,
                        modifier = Modifier.weight(1f),
                    ) { Text(strings.reportsCompareText) }
                    FieldwatchActionButton(
                        onClick = vm::startSitComparePdf,
                        enabled = !exporting && state.sit.compareId != null,
                        modifier = Modifier.weight(1f),
                    ) { Text(strings.reportsComparePdf) }
                }
                Text(
                    if (strings.isEs) "Mismo informe, dos formatos. Solo presencia — solo en esta situación, solo en la segunda o en ambas. Tipo + MAC. Radios con atención especial y con nombre marcadas. No es una posición de radio."
                    else "Same report, two formats. Presence only — only in this sit, only in the second, in both. Kind + MAC. Extra attention and Named radios are marked. Not a radio fix.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FieldwatchActionButton(
                    onClick = vm::startSitCompareAiExport,
                    enabled = !exporting && state.sit.compareId != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(strings.reportsAiExport) }
                Text(
                    if (strings.isEs) "Apéndice listo para pegar: superposición, atención especial / radios con nombre exclusivas, qué reduciría otra situación. No reimprime las listas de comparación. La exportación IA del informe permanece en esta ventana."
                    else "Paste-ready addendum: overlap, exclusive Extra attention / Named radios, what another sit would shrink. Does not reprint the compare lists. Sit report AI Export stays this window only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(strings.reportsCatalogSection) {
            FieldwatchActionButton(
                onClick = onSignatureCandidates,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(strings.reportsSignatureCandidates) }
            Text(
                strings.reportsSignatureCandidatesDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            // FASE 1.5: recolección y exportación de datos para entrenar ML.
            SectionCard("Training data") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Collect behavioral features",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FieldwatchSwitch(
                    settings.trainingCollectionEnabled,
                    { on -> vm.updateSettings { it.copy(trainingCollectionEnabled = on) } },
                )
            }
            Text(
                if (settings.trainingCollectionEnabled) {
                    "On. One JSONL row per new radio: features + heuristic guess + MAC hash (no raw MAC). Stays on this phone. Long-term dataset for FASE 1.5."
                } else {
                    "Off. When on, one JSONL row per new radio (features only, MAC hashed). Feeds a future ML model. Requires 5-6 months of daily use + manual labeling."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val trainingCounts by vm.trainingCounts.collectAsStateWithLifecycle()
            val total = trainingCounts.first
            val labeled = trainingCounts.second
            Text(
                "$total samples · $labeled labeled",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (labeled in 1 until 50) {
                Text(
                    "Aim for 50+ labeled per class before training. Keep labeling on detail pages when you recognize a device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FieldwatchActionButton(
                    onClick = vm::startExportTrainingData,
                    enabled = !exporting && total > 0,
                    modifier = Modifier.weight(1f),
                ) { Text(if (strings.isEs) "Exportar conjunto" else "Export dataset") }
                FieldwatchActionButton(
                    onClick = { confirmClearTraining = true },
                    enabled = !exporting && total > 0,
                    modifier = Modifier.weight(1f),
                ) { Text(if (strings.isEs) "Borrar" else "Clear") }
            }
            if (confirmClearTraining) {
                AlertDialog(
                    onDismissRequest = { confirmClearTraining = false },
                    title = { Text(if (strings.isEs) "¿Borrar datos de entrenamiento?" else "Clear training data?") },
                    text = {
                        Text(if (strings.isEs) "Elimina todas las muestras recopiladas en este teléfono. No se puede deshacer. Exporta primero si deseas una copia de seguridad." else "Deletes all collected feature samples from this phone. This cannot be undone. Export first if you want a backup.")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmClearTraining = false
                            vm.clearTrainingData()
                        }) { Text(if (strings.isEs) "Borrar" else "Clear") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClearTraining = false }) { Text(strings.cancel) }
                    },
                )
            }
            }

            SectionCard(strings.reportsLogExportSection) {
            Text(
                if (strings.isEs) {
                    "${state.logLines} líneas esta sesión  ·  ${vm.logBytes() / 1024} KB en disco" +
                        if (settings.loggingEnabled) "" else "  ·  registro desactivado"
                } else {
                    "${state.logLines} lines this session  ·  ${vm.logBytes() / 1024} KB on disk" +
                        if (settings.loggingEnabled) "" else "  ·  logging off"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            val logKind by vm.logExportKind.collectAsStateWithLifecycle()
            val logRadios by vm.logExportRadios.collectAsStateWithLifecycle()
            ExportFormatBlock(
                kind = logKind,
                radios = logRadios,
                exporting = exporting,
                isEs = strings.isEs,
                onKind = vm::setLogExportKind,
                onRadios = vm::setLogExportRadios,
                onShare = vm::startExport,
                onSave = onSaveToStorage,
                hint = if (strings.isEs) {
                    "El archivo rotativo es JSON lines. CSV contiene las mismas filas en formato de tabla. GPX (GPS Exchange), KML (Google Earth) y WiGLE CSV (wigle.net) son puntos de escucha: dónde estaba este teléfono al detectar cada radio, no una posición calculada. Requiere etiquetar con GPS y registro activo. Compartir usa el menú estándar de Android — Fieldwatch no sube nada a internet."
                } else {
                    "The rotating file is JSON lines. CSV is the same rows as a spreadsheet. GPX — GPS Exchange, KML — Google Earth, and WiGLE CSV — wigle.net are hear-points: where this phone was when it heard each radio, not a radio fix. Tag detections with GPS and logging on. Share uses the Android share sheet — Fieldwatch does not upload."
                },
            )
            FieldwatchActionButton(
                onClick = { confirmClear = true },
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.reportsResetClearLog)
            }
            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text(strings.reportsClearLogConfirmTitle) },
                    text = {
                        Text(strings.reportsClearLogConfirmMsg)
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmClear = false
                            vm.clearLogs()
                        }) { Text(if (strings.isEs) "Borrar registro" else "Clear log") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClear = false }) { Text(strings.cancel) }
                    },
                )
            }
            }
        }
    }
    if (startSit) {
        AlertDialog(
            onDismissRequest = { startSit = false },
            title = { Text(if (strings.isEs) "Iniciar situación" else "Start sit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldwatchOutlinedField(
                        value = sitNameDraft,
                        onValueChange = { sitNameDraft = it.take(Sit.NAME_MAX) },
                        label = if (strings.isEs) "Nombre" else "Name",
                    )
                    Text(
                        if (strings.isEs) "El informe de misión y la exportación IA usarán esta ventana hasta que la finalices. La lista En vivo no cambia."
                        else "Debrief and AI Export use this window until you end it. The Live list is unchanged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Sit.dropWarning(state.sit.closed)?.let { warn ->
                        Text(
                            warn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    startSit = false
                    vm.startSit(sitNameDraft)
                }) { Text(if (strings.isEs) "Iniciar" else "Start") }
            },
            dismissButton = {
                TextButton(onClick = { startSit = false }) { Text(strings.cancel) }
            },
        )
    }
    val renaming = renameSitId
    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { renameSitId = null },
            title = { Text(if (strings.isEs) "Renombrar situación" else "Rename sit") },
            text = {
                FieldwatchOutlinedField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(Sit.NAME_MAX) },
                    label = if (strings.isEs) "Nombre" else "Name",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameSitId = null
                    vm.renameSit(renaming, renameDraft)
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { renameSitId = null }) { Text(strings.cancel) }
            },
        )
    }
    val deleting = deleteSitId
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteSitId = null },
            title = { Text(if (strings.isEs) "¿Eliminar esta situación?" else "Delete this sit?") },
            text = { Text(if (strings.isEs) "Elimina la situación guardada de este teléfono. El registro no cambia." else "Removes the saved sit from this phone. The log is unchanged.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteSitId = null
                    vm.deleteSit(deleting)
                }) { Text(strings.delete) }
            },
            dismissButton = {
                TextButton(onClick = { deleteSitId = null }) { Text(strings.cancel) }
            },
        )
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(strings.reportsDeleteAllConfirmTitle) },
            text = { Text(strings.reportsDeleteAllConfirmMsg) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    vm.deleteAllSits()
                }) { Text(if (strings.isEs) "Eliminar todo" else "Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(strings.cancel) }
            },
        )
    }
}

@Composable
private fun SitChoiceRow(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Column(Modifier.padding(start = 8.dp).fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected && enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdvertisedTrackSwatch() {
    Canvas(Modifier.width(28.dp).height(10.dp)) {
        val dash = 3.dp.toPx()
        val gap = 4.5.dp.toPx()
        drawLine(
            Color.White,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), 0f),
        )
    }
}

@Composable
private fun AdvertisedRingSwatch() {
    Canvas(Modifier.size(12.dp)) {
        drawCircle(
            Color.White,
            radius = size.minDimension / 2f - 1.dp.toPx(),
            style = Stroke(width = 1.6.dp.toPx()),
        )
    }
}

@Composable
private fun PilotSwatch() {
    val painter = rememberVectorPainter(Icons.Outlined.Person)
    Canvas(Modifier.size(18.dp)) {
        val radius = size.minDimension / 2f
        val disc = radius * 0.86f
        drawCircle(Color.White, radius = radius)
        drawCircle(Color(0xFFF4F7FB), radius = disc)
        drawCircle(Color(0xFF3D4A55), radius = disc, style = Stroke(width = 1.2.dp.toPx()))
        val icon = disc * 1.35f
        translate((size.width - icon) / 2f, (size.height - icon) / 2f) {
            with(painter) {
                draw(Size(icon, icon), colorFilter = ColorFilter.tint(Color(0xFF3D4A55)))
            }
        }
    }
}

@Composable
private fun PathRadioRow(
    index: Int,
    dot: SitPathPlot.Dot,
    demoMode: Boolean,
    onOpen: () -> Unit,
) {
    val mac = MacUtil.screenMac(dot.mac, demoMode)
    val named = dot.label.isNotBlank() && !dot.label.equals(mac, ignoreCase = true)
    val fleets = dot.fleetNames.joinToString(" · ")
    val note = dot.observerNotes.trim()
    val accent = (if (dot.accentArgb != 0) Color(dot.accentArgb) else MaterialTheme.colorScheme.onSurfaceVariant)
        .nightIf(LocalNightMode.current)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$index",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        RadioClassBadge(dot.classKind, accent, compact = true)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            if (named) {
                Text(
                    dot.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (mac.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioKindMark(dot.kind, size = 13.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        mac,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (fleets.isNotEmpty()) {
                Text(
                    fleets,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.isNotEmpty()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Cyan.nightIf(LocalNightMode.current),
                )
            }
        }
    }
}

private fun compareThisCaption(state: FieldwatchUi, isEs: Boolean): String {
    val open = state.sit.open
    if (open != null) {
        return if (isEs) "Esta situación: ${open.name} — ventana con nombre (hasta ${Sit.RADIO_CAP}). Igual que en el informe."
        else "This sit: ${open.name} — named window (up to ${Sit.RADIO_CAP}). Same as Debrief."
    }
    val selected = state.sit.closed.firstOrNull { it.id == state.sit.selectedId }
    if (selected != null) {
        return if (isEs) "Esta situación: ${selected.name} — ventana con nombre (hasta ${Sit.RADIO_CAP}). Igual que en el informe."
        else "This sit: ${selected.name} — named window (up to ${Sit.RADIO_CAP}). Same as Debrief."
    }
    return if (isEs) "Esta situación: últimos 15 minutos en memoria (aprox. 400 radios). Igual que en el informe."
    else "This sit: last 15 minutes in memory (about 400 radios). Same as Debrief."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportFormatBlock(
    kind: LogExportKind,
    radios: LogExportRadios,
    exporting: Boolean,
    isEs: Boolean,
    onKind: (LogExportKind) -> Unit,
    onRadios: (LogExportRadios) -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    hint: String,
) {
    val strings = LocalAppStrings.current
    var openFormat by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = openFormat,
        onExpandedChange = { openFormat = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        val formatTitle = if (isEs) "Formato" else "Format"
        val kindLabel = if (isEs) {
            when (kind) {
                LogExportKind.LOG_CSV -> "Archivo de registro — CSV"
                LogExportKind.LOG_JSONL -> "Archivo de registro — JSON lines"
                LogExportKind.GPX -> "GPX — GPS Exchange"
                LogExportKind.KML -> "KML — Google Earth"
                LogExportKind.WIGLE -> "WiGLE CSV — wigle.net"
            }
        } else kind.label
        FieldwatchDropdownField(formatTitle, kindLabel, openFormat)
        ExposedDropdownMenu(openFormat, { openFormat = false }) {
            LogExportKind.entries.forEach { item ->
                val itemLabel = if (isEs) {
                    when (item) {
                        LogExportKind.LOG_CSV -> "Archivo de registro — CSV"
                        LogExportKind.LOG_JSONL -> "Archivo de registro — JSON lines"
                        LogExportKind.GPX -> "GPX — GPS Exchange"
                        LogExportKind.KML -> "KML — Google Earth"
                        LogExportKind.WIGLE -> "WiGLE CSV — wigle.net"
                    }
                } else item.label
                DropdownMenuItem(
                    text = { Text(itemLabel) },
                    onClick = {
                        onKind(item)
                        openFormat = false
                    },
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LogExportRadios.entries.forEach { item ->
            val radioLabel = if (isEs) {
                when (item) {
                    LogExportRadios.BOTH -> "Ambas"
                    LogExportRadios.WIFI -> "Solo Wi-Fi"
                    LogExportRadios.BLE -> "Solo BLE"
                }
            } else item.label
            Row(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = radios == item,
                        onClick = { onRadios(item) },
                        role = Role.RadioButton,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = radios == item,
                    onClick = { onRadios(item) },
                    enabled = !exporting,
                )
                Text(radioLabel, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    FieldwatchActionButton(
        onClick = onShare,
        enabled = !exporting,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(strings.share) }
    FieldwatchActionButton(
        onClick = onSave,
        enabled = !exporting,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(strings.saveToStorage) }
    Text(
        hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun sitReportCaption(state: FieldwatchUi, isEs: Boolean): String {
    val open = state.sit.open
    if (open != null) {
        return if (isEs) "Esta situación (${open.name}) — misma ventana que la Trayectoria. Prueba de seguimiento por GPS cuando el etiquetado está activo y te has movido. No es una determinación legal."
        else "This sit (${open.name}) — same window as Path. GPS following test when tagging is on and you have moved. Not a legal finding."
    }
    val selected = state.sit.closed.firstOrNull { it.id == state.sit.selectedId }
    if (selected != null) {
        return if (isEs) "Situación: ${selected.name} — misma ventana que la Trayectoria. Prueba de seguimiento por GPS cuando el etiquetado está activo y te has movido. No es una determinación legal."
        else "Sit: ${selected.name} — same window as Path. GPS following test when tagging is on and you have moved. Not a legal finding."
    }
    return if (isEs) "Últimos 15 minutos en memoria — misma ventana que la Trayectoria. Dos formatos. Prueba de seguimiento por GPS cuando el etiquetado está activo y te has movido. No es una determinación legal."
    else "Last 15 minutes in memory — same window as Path. Two formats. GPS following test when tagging is on and you have moved. Not a legal finding."
}