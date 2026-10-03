package app.fieldwatch.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import app.fieldwatch.ui.component.DecodeGlyph
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import app.fieldwatch.ui.component.FieldwatchFilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchDropdownField
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.Palette
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SignatureListSort
import app.fieldwatch.domain.groupedByClass
import app.fieldwatch.domain.sortedForCatalog
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.i18n.LocalAppStrings
import app.fieldwatch.ui.i18n.localizedLabel
import app.fieldwatch.ui.RadioClassBadge
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.SectionCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetsScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onCandidateDraftClosed: () -> Unit = {},
) {
    val draft = state.draftFleet
    if (draft != null) {
        val isNew = state.fleets.none { it.id == draft.id }
        var working by remember(draft.id) { mutableStateOf(draft) }
        var editingDecode by remember(draft.id) { mutableStateOf(false) }
        if (editingDecode) {
            val preview = state.devices.firstOrNull { working.id in it.fleetIds }
                ?: state.selected?.takeIf { working.id in it.fleetIds }
            DecodeFieldsScreen(
                fleet = working,
                previewDevice = preview,
                onSave = { decode ->
                    val next = working.copy(decode = decode)
                    working = next
                    vm.saveFleetKeepDraft(next)
                    editingDecode = false
                },
                onBack = { editingDecode = false },
            )
            return
        }
        FleetEditor(
            working,
            isNew = isNew,
            onSave = { fleet ->
                val bounce = vm.takeDraftFromCandidates()
                vm.upsertFleet(fleet) {
                    if (bounce) {
                        vm.startSignatureCandidates()
                        onCandidateDraftClosed()
                    }
                }
            },
            onCancel = {
                val bounce = vm.takeDraftFromCandidates()
                vm.cancelDraft()
                if (bounce) onCandidateDraftClosed()
            },
            onDelete = if (isNew) null else ({ vm.deleteFleet(draft.id) }),
            onOpenDecode = { current ->
                working = current
                editingDecode = true
            },
        )
        return
    }
    val strings = LocalAppStrings.current
    val isEs = strings.isEs
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar("${strings.signaturesTitle} (${state.fleets.size})") },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::beginNewFleet) {
                Icon(
                    Icons.Outlined.Add,
                    if (isEs) "Nueva firma" else "New signature",
                )
            }
        },
    ) { pad ->
        val sort = state.settings.signatureListSort
        val openClasses by vm.catalogOpenClasses.collectAsStateWithLifecycle()
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    if (sort == SignatureListSort.CLASS) {
                        if (isEs) {
                            "Toca una clase para abrir sus firmas. Marcador = pitido. Oculta una familia en Filtros, no aquí."
                        } else {
                            "Tap a class to open its signatures. Bookmark = beep. Hide a family on Filters, not here."
                        }
                    } else {
                        if (isEs) {
                            "Toca para editar. Marcador = pitido cuando esa familia aparece. Oculta una familia en Filtros, no aquí."
                        } else {
                            "Tap to edit. Bookmark = beep when that family appears. Hide a family on Filters, not here."
                        }
                    },
                    style = compactLine(12.sp, 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    FieldwatchFilterChip(
                        selected = sort == SignatureListSort.NAME,
                        onClick = { vm.setSignatureListSort(SignatureListSort.NAME) },
                        label = { Text(if (isEs) "Nombre A–Z" else "Name A–Z") },
                    )
                    FieldwatchFilterChip(
                        selected = sort == SignatureListSort.CLASS,
                        onClick = { vm.setSignatureListSort(SignatureListSort.CLASS) },
                        label = { Text(if (isEs) "Clase A–Z" else "Class A–Z") },
                    )
                }
            }
            if (sort == SignatureListSort.CLASS) {
                state.fleets.groupedByClass().forEach { (kind, rows) ->
                    val classId = kind.name
                    val expanded = classId in openClasses
                    item(key = "class-$classId") {
                        SignatureClassHeader(
                            kind = kind,
                            count = rows.size,
                            colorIndex = rows.firstOrNull()?.colorIndex ?: 0,
                            expanded = expanded,
                            onToggle = { vm.toggleCatalogClass(classId) },
                        )
                    }
                    if (expanded) {
                        items(rows, key = { it.id }) { fleet ->
                            Box(Modifier.padding(start = 16.dp)) {
                                SignatureRow(fleet, state, vm)
                            }
                        }
                    }
                }
            } else {
                items(state.fleets.sortedForCatalog(sort), key = { it.id }) { fleet ->
                    SignatureRow(fleet, state, vm)
                }
            }
        }
    }
}

@Composable
private fun SignatureClassHeader(
    kind: SignatureClass,
    count: Int,
    colorIndex: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val isEs = LocalAppStrings.current.isEs
    val accent = Color(Palette.color(colorIndex)).nightIf(LocalNightMode.current)
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioClassBadge(classKind = kind, accent = accent)
            Spacer(Modifier.width(10.dp))
            Text(
                // FASE 5 (Bloque 4): bugfix — antes usaba kind.label() (inglés)
                // incluso en español. Ahora respeta el idioma.
                kind.localizedLabel(isEs),
                style = compactLine(16.sp, 18.sp, FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (expanded) "▾  $count" else "▸  $count",
                style = compactLine(14.sp, 16.sp, FontWeight.Bold).copy(fontFamily = FontFamily.Monospace),
                color = accent,
            )
        }
    }
}

@Composable
private fun SignatureRow(fleet: Fleet, state: FieldwatchUi, vm: FieldwatchViewModel) {
    val isEs = LocalAppStrings.current.isEs
    val color = Color(Palette.color(fleet.colorIndex)).nightIf(LocalNightMode.current)
    val liveHits = state.devices.count { fleet.id in it.fleetIds && !it.gone }
    Surface(
        onClick = { vm.editFleet(fleet) },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioClassBadge(
                classKind = fleet.kind,
                accent = color,
            )
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        fleet.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = compactLine(16.sp, 18.sp, FontWeight.SemiBold),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (fleet.decode != null) {
                        DecodeGlyph(
                            tint = color,
                            size = 14.dp,
                        )
                    }
                }
                val rulesWord = if (isEs) "reglas" else "rules"
                val liveWord = if (isEs) "en vivo" else "live"
                Text(
                    // FASE 5 (Bloque 4): kind.label() → localizedLabel(isEs).
                    "${fleet.kind.localizedLabel(isEs)} · ${fleet.rules.size} $rulesWord · $liveHits $liveWord · ${if (fleet.matchAny) "OR" else "AND"}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = compactLine(11.sp, 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // FASE 5 (Bloque 3): quitado .size(36.dp). M3 expande el touch
            // del IconButton a 48dp automáticamente. Visual 40dp (antes 36).
            IconButton(
                onClick = { vm.toggleWatchFleet(fleet) },
            ) {
                Icon(
                    if (vm.isFleetWatched(fleet.id)) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    if (isEs) "Pitar cuando esta firma aparezca" else "Beep when this signature appears",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetEditor(
    initial: Fleet,
    isNew: Boolean = false,
    onSave: (Fleet) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onOpenDecode: (Fleet) -> Unit = {},
) {
    val strings = LocalAppStrings.current
    val isEs = strings.isEs
    var fleet by remember(initial.id) { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(initial.decode) {
        fleet = fleet.copy(decode = initial.decode)
    }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = if (isNew) {
                    if (isEs) "Nueva firma" else "New signature"
                } else {
                    if (isEs) "Editar firma" else "Edit signature"
                },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text(if (isEs) "Cancelar" else "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(fleet) }) {
                        Text(if (isEs) "Guardar" else "Save")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(if (isEs) "Identidad" else "Identity") {
            FieldwatchOutlinedField(
                fleet.name,
                { fleet = fleet.copy(name = it) },
                if (isEs) "Nombre" else "Name",
            )
            FieldwatchOutlinedField(
                fleet.notes,
                { fleet = fleet.copy(notes = it) },
                if (isEs) "Notas" else "Notes",
                supportingText = if (isEs) {
                    "Aparece en el detalle de radios coincidentes, y en Compartir / Exportación IA. No es Atención especial — no hay “!” en En vivo ni tarjeta ámbar."
                } else {
                    "Shows on radio detail for matching radios, and in Share / AI Export. Not Extra attention — no Live “!” and not the amber card."
                },
                singleLine = false,
                minLines = 2,
            )
            FieldwatchOutlinedField(
                fleet.attentionNote,
                { fleet = fleet.copy(attentionNote = it) },
                if (isEs) "Atención especial" else "Extra attention",
                supportingText = if (isEs) {
                    "Opcional. Si no está vacío, las radios coincidentes reciben un “!” en En vivo, esta tarjeta ámbar en el detalle, y una línea en el Informe. Separado de Notas arriba."
                } else {
                    "Optional. If this is not empty, matching radios get a “!” on Live, this amber card on detail, and a line in Debrief. Separate from Notes above."
                },
                singleLine = false,
                minLines = 3,
            )
            }

            SectionCard(if (isEs) "Coincidencia" else "Matching") {
            var classMenu by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(classMenu, { classMenu = it }) {
                FieldwatchDropdownField(
                    if (isEs) "Clase" else "Class",
                    fleet.kind.localizedLabel(isEs),
                    classMenu,
                )
                ExposedDropdownMenu(classMenu, { classMenu = false }) {
                    SignatureClass.visible.sortedBy { it.label().lowercase() }.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(kind.localizedLabel(isEs)) },
                            onClick = {
                                fleet = fleet.copy(kind = kind)
                                classMenu = false
                            },
                        )
                    }
                }
            }
            Text(
                if (isEs) {
                    "Filtros → Mostrar solo / Ocultar estas. Las clases (Rastreadores, Cámaras, …) son esos chips — Guardar actual como… si quieres un preset."
                } else {
                    "Filters → Show only / Hide these. Class sits (Finder tags, Cameras, …) are those chips — Save current as… if you want a preset."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEs) "Coincidir cualquier regla (OR)" else "Match any rule (OR)", Modifier.weight(1f))
                FieldwatchSwitch(fleet.matchAny, { fleet = fleet.copy(matchAny = it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEs) "Agrupar por OUI" else "Cluster by OUI", Modifier.weight(1f))
                FieldwatchSwitch(fleet.clusterByOui, { fleet = fleet.copy(clusterByOui = it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEs) "MACs secuenciales" else "Sequential MACs", Modifier.weight(1f))
                FieldwatchSwitch(fleet.sequentialMac, { fleet = fleet.copy(sequentialMac = it) })
            }
            FieldwatchOutlinedField(
                fleet.minPeers.toString(),
                { fleet = fleet.copy(minPeers = it.toIntOrNull() ?: 0) },
                if (isEs) "Peers mínimos (0 = off)" else "Min peers (0 = off)",
            )
            FieldwatchOutlinedField(
                fleet.peerWindowSec.toString(),
                { fleet = fleet.copy(peerWindowSec = it.toIntOrNull() ?: 60) },
                if (isEs) "Ventana de peers (segundos)" else "Peer window (seconds)",
            )
            }

            SectionCard(if (isEs) "Color" else "Color") {
            ColorPicker(fleet.colorIndex) { fleet = fleet.copy(colorIndex = it) }
            Text(
                if (isEs) {
                    "Los colores stock son por clase (rojo pentest, ámbar cámaras/ALPR, púrpura teléfonos/tags, cian wearables, verde mesh, naranja audio/gafas, verde azulado en coche/vehículo). Puedes cambiar cualquiera."
                } else {
                    "Stock colors are by class (red pentest, amber cameras/ALPR, purple phones/tags, cyan wearables, green mesh, orange audio/glasses, teal in-car/vehicle). Change any row."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(if (isEs) "Reglas" else "Rules") {
            Text(
                if (isEs) {
                    "Cada regla tiene su propio interruptor. Desactivada la regla se conserva pero no coincide. " +
                        "Úsalo para silenciar OUIs o nombres ruidosos en una firma sin borrarlos."
                } else {
                    "Each rule has its own switch. Off keeps the rule but it does not match. " +
                        "Use that to mute noisy OUIs or names on one signature without deleting them."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            fleet.rules.forEachIndexed { index, rule ->
                RuleEditor(
                    rule = rule,
                    onChange = { next ->
                        val rules = fleet.rules.toMutableList()
                        rules[index] = next
                        fleet = fleet.copy(rules = rules)
                    },
                    onDelete = {
                        fleet = fleet.copy(rules = fleet.rules.filterIndexed { i, _ -> i != index })
                    },
                )
            }
            FieldwatchActionButton(
                onClick = {
                    fleet = fleet.copy(rules = fleet.rules + MatchRule(RuleKind.OUI, text = ""))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isEs) "Añadir regla" else "Add rule") }
            }

            if (fleet.canHaveBleDecode()) {
                val decodeCount = fleet.decode?.fields?.size ?: 0
                SectionCard(if (isEs) "Campos de decodificación" else "Decode fields") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDecode(fleet) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (decodeCount == 0) {
                            if (isEs) "Ninguno" else "None"
                        } else {
                            if (isEs) "$decodeCount campos" else "$decodeCount fields"
                        },
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "›",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (isEs) {
                        "Opcional. Después de que esta firma coincida, mapea bytes BLE en claro a etiquetas. Los payloads cifrados quedan en hex."
                    } else {
                        "Optional. After this signature matches, map cleartext BLE bytes to labels. Encrypted payloads stay hex."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                }
            }
            if (onDelete != null) {
                FieldwatchActionButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Delete, null)
                    Spacer(Modifier.padding(4.dp))
                    Text(if (isEs) "Eliminar firma" else "Delete signature")
                }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (isEs) "¿Eliminar esta firma?" else "Delete this signature?") },
            text = {
                Text(
                    if (initial.builtIn) {
                        if (isEs) {
                            "“${fleet.name}” es una firma de fábrica. Al eliminarla se pierden las coincidencias, su marcador y los chips de filtro. Restablecer firmas predeterminadas en Ajustes recupera el catálogo stock."
                        } else {
                            "“${fleet.name}” is a built-in signature. Deleting it removes matching, its bookmark, and filter chips. Restore default signatures in Settings will bring the stock set back."
                        }
                    } else {
                        if (isEs) {
                            "“${fleet.name}” será eliminada. Las coincidencias, su marcador y los chips de filtro se van con ella. Esta acción no se puede deshacer."
                        } else {
                            "“${fleet.name}” will be removed. Matching, its bookmark, and filter chips go with it. This cannot be undone."
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(if (isEs) "Eliminar" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(if (isEs) "Cancelar" else "Cancel")
                }
            },
        )
    }
}

@Composable
private fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    // FASE 5 (Bloque 3): touch 48dp, visual 36dp. El click va en el Box
    // externo; la Surface interna solo dibuja. Look idéntico al original.
    val isEs = LocalAppStrings.current.isEs
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Palette.fleet.forEachIndexed { index, argb ->
            val on = index == selected
            val fill = Color(argb).nightIf(LocalNightMode.current)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = fill,
                    border = BorderStroke(
                        width = if (on) 2.dp else 1.dp,
                        color = if (on) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (on) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = if (isEs) "Color seleccionado" else "Selected color",
                                tint = if (fill.luminance() > 0.45f) {
                                    Color(0xFF12171C)
                                } else {
                                    Color.White
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditor(rule: MatchRule, onChange: (MatchRule) -> Unit, onDelete: () -> Unit) {
    val isEs = LocalAppStrings.current.isEs
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldwatchSwitch(
                rule.enabled,
                { onChange(rule.copy(enabled = it)) },
            )
            ExposedDropdownMenuBox(
                expanded,
                { expanded = it },
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 4.dp),
            ) {
                FieldwatchDropdownField(
                    if (isEs) "Tipo" else "Kind",
                    ruleKindLabel(rule.kind, isEs),
                    expanded,
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    RuleKind.entries.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(ruleKindLabel(kind, isEs)) },
                            onClick = {
                                onChange(rule.copy(kind = kind))
                                expanded = false
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    if (isEs) "Eliminar regla" else "Delete rule",
                )
            }
        }
        Column(
            Modifier.padding(top = 12.dp, start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        when (rule.kind) {
            RuleKind.OUI, RuleKind.MAC_PREFIX, RuleKind.NAME_CONTAINS, RuleKind.NAME_GLOB, RuleKind.SERVICE_UUID, RuleKind.VENDOR_IE_OUI -> {
                FieldwatchOutlinedField(
                    rule.text,
                    { onChange(rule.copy(text = it)) },
                    if (isEs) "Valor" else "Value",
                )
            }
            RuleKind.MANUFACTURER_ID -> {
                FieldwatchOutlinedField(
                    if (rule.companyId == 0) "" else "0x%04X".format(rule.companyId),
                    {
                        val parsed = it.removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: 0
                        onChange(rule.copy(companyId = parsed))
                    },
                    if (isEs) "Company ID hex" else "Company ID hex",
                )
            }
            RuleKind.MANUFACTURER_DATA -> {
                FieldwatchOutlinedField(
                    if (rule.companyId == 0) "" else "0x%04X".format(rule.companyId),
                    {
                        val parsed = it.removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: 0
                        onChange(rule.copy(companyId = parsed))
                    },
                    if (isEs) "Company ID hex" else "Company ID hex",
                )
                FieldwatchOutlinedField(
                    rule.dataPrefixHex,
                    { onChange(rule.copy(dataPrefixHex = it)) },
                    if (isEs) "Prefijo de datos hex" else "Data prefix hex",
                )
            }
            RuleKind.SERVICE_DATA -> {
                FieldwatchOutlinedField(
                    rule.text,
                    { onChange(rule.copy(text = it)) },
                    if (isEs) "Service UUID (vacío = cualquiera, contiene)"
                    else "Service UUID (empty = any, contains)",
                )
                FieldwatchOutlinedField(
                    rule.dataPrefixHex,
                    { onChange(rule.copy(dataPrefixHex = it)) },
                    if (rule.text.isBlank()) {
                        if (isEs) "Contiene hex" else "Contains hex"
                    } else {
                        if (isEs) "Prefijo de datos hex" else "Data prefix hex"
                    },
                )
            }
            RuleKind.RADIO_KIND -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wi-Fi", Modifier.padding(end = 8.dp))
                    FieldwatchSwitch(
                        rule.radio != RadioKind.BLE,
                        { onChange(rule.copy(radio = if (it) RadioKind.WIFI else RadioKind.BLE)) },
                    )
                }
            }
            RuleKind.HIDDEN_SSID -> Text(
                if (isEs) "Coincide con SSIDs ocultos" else "Matches hidden SSIDs",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        }
    }
}

/**
 * FASE 5 (Bloque 4): antes devolvía siempre inglés. Ahora recibe [isEs]
 * para poder traducir las etiquetas de cada tipo de regla.
 */
private fun ruleKindLabel(kind: RuleKind, isEs: Boolean): String = if (!isEs) {
    when (kind) {
        RuleKind.OUI -> "OUI"
        RuleKind.MAC_PREFIX -> "MAC prefix"
        RuleKind.NAME_CONTAINS -> "Name contains"
        RuleKind.NAME_GLOB -> "Name glob"
        RuleKind.SERVICE_UUID -> "Service UUID"
        RuleKind.SERVICE_DATA -> "Service data"
        RuleKind.MANUFACTURER_ID -> "Manufacturer ID"
        RuleKind.MANUFACTURER_DATA -> "Manufacturer data"
        RuleKind.RADIO_KIND -> "Radio kind"
        RuleKind.HIDDEN_SSID -> "Hidden SSID"
        RuleKind.VENDOR_IE_OUI -> "Vendor IE OUI"
    }
} else {
    when (kind) {
        RuleKind.OUI -> "OUI"
        RuleKind.MAC_PREFIX -> "Prefijo MAC"
        RuleKind.NAME_CONTAINS -> "Nombre contiene"
        RuleKind.NAME_GLOB -> "Nombre glob"
        RuleKind.SERVICE_UUID -> "Service UUID"
        RuleKind.SERVICE_DATA -> "Service data"
        RuleKind.MANUFACTURER_ID -> "ID de fabricante"
        RuleKind.MANUFACTURER_DATA -> "Datos de fabricante"
        RuleKind.RADIO_KIND -> "Tipo de radio"
        RuleKind.HIDDEN_SSID -> "SSID oculto"
        RuleKind.VENDOR_IE_OUI -> "Vendor IE OUI"
    }
}

private fun compactLine(
    size: androidx.compose.ui.unit.TextUnit,
    line: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontSize = size,
    lineHeight = line,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)