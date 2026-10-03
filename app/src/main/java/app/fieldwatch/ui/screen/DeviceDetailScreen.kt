package app.fieldwatch.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import app.fieldwatch.ui.component.DecodeGlyph
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.domain.BehaviorFeatures
import app.fieldwatch.domain.BehavioralClass
import app.fieldwatch.domain.BehavioralClassifier
import app.fieldwatch.domain.BehavioralKind
import app.fieldwatch.domain.CodDecoder
import app.fieldwatch.domain.FamilyVerdict
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.DeviceExplain
import app.fieldwatch.domain.Palette
import app.fieldwatch.domain.RadioDb
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.RotationDetector
import app.fieldwatch.domain.Rssi
import app.fieldwatch.domain.ServiceDataRecord
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureFamilyHint
import app.fieldwatch.domain.SignatureFieldDecoder
import app.fieldwatch.domain.TrainingLabel
import app.fieldwatch.domain.hexSpaced
import app.fieldwatch.domain.label
import app.fieldwatch.radio.BleAdParser
import app.fieldwatch.ui.RadioKindMark
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.i18n.LocalAppStrings
import app.fieldwatch.ui.theme.Amber
import app.fieldwatch.ui.theme.Cyan
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf
import app.fieldwatch.ui.component.PresenceTrack
import app.fieldwatch.ui.component.Sparkline
import app.fieldwatch.ui.component.StickyHeight
import app.fieldwatch.ui.component.rssiColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    device: Sighting,
    vm: FieldwatchViewModel,
    watched: Boolean,
    onBack: () -> Unit,
    onCreateFleet: () -> Unit,
    onHunt: () -> Unit,
    demoMode: Boolean = false,
) {
    val strings = LocalAppStrings.current
    val isEs = strings.isEs
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    val accent = (device.fleetIds.firstOrNull()
        ?.let { Color(Palette.color(vm.fleetColor(it))) }
        ?: rssiColor(device.rssi))
        .nightIf(LocalNightMode.current)
    val facts = device.facts
    val familyHint by vm.familyHint.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val custom = vm.watchLabelFor(device.key)
                    val title = custom?.takeIf { it.isNotBlank() }
                        ?: device.listTitle(device.fleetIds.map { vm.fleetName(it) })
                    Text(MacUtil.redactMacIn(title, device.mac, demoMode), maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            if (isEs) "Atrás" else "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.toggleWatchDevice(device) }) {
                        Icon(
                            if (watched) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            if (isEs) "Vigilar" else "Watch",
                        )
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                MacUtil.screenMac(device.mac, demoMode),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium,
            )
            if (device.gone) {
                Text(
                    if (isEs) "Fuera del aire. Este es el último detalle que escuchamos."
                    else "Not on the air. This is the last detail we heard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            var nameDraft by remember(device.key) {
                mutableStateOf(vm.watchLabelFor(device.key).orEmpty())
            }
            var lastSaved by remember(device.key) {
                mutableStateOf(vm.watchLabelFor(device.key).orEmpty())
            }
            var editingName by remember(device.key) { mutableStateOf(false) }
            val draftLabel = RadioBookmarks.clip(nameDraft)
            val nameIsSaved = lastSaved.isNotBlank() && draftLabel == lastSaved
            val canName = RadioBookmarks.canSetCustomName(device)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (lastSaved.isNotBlank()) {
                        Text(
                            if (isEs) "Nombre personalizado" else "Custom name",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(lastSaved, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (isEs) "Anunciado" else "Advertised",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            device.name.ifBlank { if (isEs) "Sin nombre anunciado" else "No advertised name" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (device.name.isNotBlank()) {
                        Text(
                            if (isEs) "Nombre anunciado" else "Advertised name",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(device.name, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            if (isEs) "Nombre" else "Name",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (isEs) "Sin nombre anunciado" else "No advertised name",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (canName) {
                    IconButton(onClick = { editingName = !editingName }) {
                        Icon(
                            Icons.Outlined.Edit,
                            if (editingName) {
                                if (isEs) "Ocultar nombre personalizado" else "Hide custom name"
                            } else {
                                if (isEs) "Nombre personalizado" else "Custom name"
                            },
                        )
                    }
                }
            }
            if (editingName && canName) {
                FieldwatchOutlinedField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(RadioBookmarks.MAX_NAME) },
                    label = if (isEs) "Nombre personalizado" else "Custom name",
                    supportingText = RadioBookmarks.customNameHint(device),
                )
                FieldwatchActionButton(
                    onClick = {
                        vm.saveRadioName(device, nameDraft)
                        nameDraft = draftLabel
                        lastSaved = draftLabel
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (isEs) "Guardado como $draftLabel" else "Saved as $draftLabel",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = nameDraft.isNotBlank() && !nameIsSaved,
                ) {
                    if (nameIsSaved) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.padding(4.dp))
                        Text(if (isEs) "Guardado" else "Saved")
                    } else {
                        Text(if (isEs) "Guardar nombre" else "Save name")
                    }
                }
            }

            var notesDraft by remember(device.key) {
                mutableStateOf(vm.watchObserverNoteFor(device.key).orEmpty())
            }
            var lastSavedNotes by remember(device.key) {
                mutableStateOf(vm.watchObserverNoteFor(device.key).orEmpty())
            }
            var editingNotes by remember(device.key) { mutableStateOf(false) }
            val draftNotes = RadioBookmarks.clipNotes(notesDraft)
            val notesIsSaved = draftNotes == lastSavedNotes
            if (canName || lastSavedNotes.isNotBlank()) {
                ObserverNotesCard(
                    notes = lastSavedNotes,
                    canEdit = canName,
                    editing = editingNotes && canName,
                    draft = notesDraft,
                    onToggleEdit = { editingNotes = !editingNotes },
                    onDraftChange = { notesDraft = it.take(RadioBookmarks.MAX_NOTES) },
                    onSave = {
                        vm.saveRadioNotes(device, notesDraft)
                        notesDraft = draftNotes
                        lastSavedNotes = draftNotes
                        if (lastSaved.isBlank() && draftNotes.isNotBlank()) {
                            val suggest = RadioBookmarks.suggestLabel(
                                device,
                                device.fleetIds.map { vm.fleetName(it) },
                            )
                            lastSaved = suggest
                            nameDraft = suggest
                        }
                        editingNotes = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (draftNotes.isBlank()) {
                                    if (isEs) "Notas del observador borradas" else "Observer notes cleared"
                                } else {
                                    if (isEs) "Notas del observador guardadas" else "Observer notes saved"
                                },
                            )
                        }
                    },
                    saveEnabled = !notesIsSaved,
                    saved = notesIsSaved && lastSavedNotes.isNotBlank(),
                )
            }

            val guess = DeviceExplain.guess(device, device.fleetIds.map { vm.fleetName(it) })
            StickyHeight(device.key to "guess") { GuessCard(guess) }

            // FASE 1: clasificador de comportamiento.
            val uiState by vm.ui.collectAsStateWithLifecycle()
            val behavior = remember(device.key, uiState.devices.size, device.rssiHistory.size) {
                val features = BehaviorFeatures.of(device)
                val rotation = RotationDetector.rotatingCount(device, uiState.devices)
                BehavioralClassifier().classify(device, features, rotation)
            }
            if (behavior.kind != BehavioralKind.UNKNOWN) {
                StickyHeight(device.key to "behavior") { BehaviorCard(behavior) }
            }

            // FASE 1.5: etiquetado para entrenamiento.
            var trainingLabel by remember(device.key) { mutableStateOf<TrainingLabel?>(null) }
            var pickLabel by remember { mutableStateOf(false) }
            LaunchedEffect(device.key) {
                trainingLabel = vm.trainingLabelFor(device)
            }
            StickyHeight(device.key to "training") {
                TrainingCard(
                    label = trainingLabel,
                    suggested = behavior.kind,
                    onPickLabel = { pickLabel = true },
                    onClear = {
                        vm.clearTrainingLabel(device)
                        trainingLabel = null
                    },
                    enabled = uiState.settings.trainingCollectionEnabled,
                )
            }
            if (pickLabel) {
                LabelPickerDialog(
                    onDismiss = { pickLabel = false },
                    onPick = { label ->
                        vm.labelDeviceForTraining(device, label)
                        trainingLabel = label
                        pickLabel = false
                    },
                )
            }

            val attention = vm.attentionNotesFor(device)
            if (attention.isNotEmpty()) {
                StickyHeight(device.key to "attention") { ExtraAttentionCard(attention) }
            }
            val notes = vm.signatureNotesFor(device)
            if (notes.isNotEmpty()) {
                StickyHeight(device.key to "notes") { SignatureNotesCard(notes) }
            }

            StickyHeight(device.key to "identity") {
                Section(if (isEs) "Identidad" else "Identity")
                Meta(
                    if (isEs) "Radio" else "Radio",
                    if (device.kind == RadioKind.WIFI) {
                        if (isEs) "Punto de acceso Wi-Fi (emitiendo una red)"
                        else "Wi-Fi access point (beaconing a network)"
                    } else {
                        if (isEs) "Emisor Bluetooth Low Energy"
                        else "Bluetooth Low Energy advertiser"
                    },
                )
                Meta(if (isEs) "Dirección" else "Address", DeviceExplain.addressExplain(device))
                // FASE 5 (Bloque 4): vendorLine ahora es @Composable y bilingüe.
                vendorLine(device)?.let { Meta(if (isEs) "Quién lo fabricó" else "Who made it", it) }
                    ?: Meta(
                        if (isEs) "OUI (prefijo de fabricante)" else "OUI (vendor prefix)",
                        if (isEs) "${device.oui} — sin coincidencia IEEE; las direcciones aleatorizadas suelen no tener ninguna"
                        else "${device.oui} — no IEEE match; randomized addresses usually have none",
                    )
                if (device.hiddenSsid) {
                    Meta(
                        if (isEs) "Nombre de red (SSID)" else "Network name (SSID)",
                        if (isEs) "Oculto — el AP emite pero no publica nombre"
                        else "Hidden — the AP is beaconing but not publishing a name",
                    )
                }
            }

            StickyHeight(device.key to "signal") {
                Section(if (isEs) "Señal" else "Signal")
                if (device.gone) {
                    Meta(
                        if (isEs) "Qué tan fuerte aquí (RSSI)" else "How loud here (RSSI)",
                        if (isEs) "No disponible" else "Not available",
                    )
                    Meta(
                        if (isEs) "Última vez escuchado" else "Last heard",
                        buildString {
                            append(fmt.format(Date(device.lastSeen)))
                            Rssi.lastMeasured(device.rssi, device.rssiHistory)?.let {
                                append(if (isEs) " a $it dBm" else " at $it dBm")
                            }
                        },
                    )
                } else {
                    Meta(
                        if (isEs) "Qué tan fuerte aquí (RSSI)" else "How loud here (RSSI)",
                        DeviceExplain.rssiExplain(device.rssi),
                    )
                    Text(
                        if (isEs) "Cerca de 0 dBm es más fuerte aquí, no es distancia."
                        else "Closer to 0 dBm is louder here, not a distance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Meta(
                    if (isEs) "Rango escuchado esta sesión" else "Heard range this session",
                    Rssi.sessionRange(device.rssiMin, device.rssiMax, device.rssiHistory),
                )
                facts.txPowerDbm?.let {
                    Meta(
                        if (isEs) "Potencia de transmisión declarada" else "Claimed transmit power",
                        if (isEs) "$it dBm — qué tan fuerte dice que transmite, no es distancia"
                        else "$it dBm — how loud it says it transmits, not a distance",
                    )
                }
                if (device.channel != 0 || device.frequencyMhz != 0) {
                    Meta(
                        if (isEs) "Canal / frecuencia" else "Channel / frequency",
                        buildString {
                            if (device.channel != 0) {
                                append(if (isEs) "canal ${device.channel}" else "channel ${device.channel}")
                            }
                            if (device.frequencyMhz != 0) {
                                if (isNotEmpty()) append("  ·  ")
                                append("${device.frequencyMhz} MHz")
                            }
                            facts.channelWidth?.let {
                                append(if (isEs) "  ·  ancho $it" else "  ·  $it wide")
                            }
                        },
                    )
                }
                facts.wifiStandard?.let { Meta(if (isEs) "Generación Wi-Fi" else "Wi-Fi generation", it) }
                if (facts.centerFreq0 != null || facts.centerFreq1 != null) {
                    Meta(
                        if (isEs) "Frecuencias centrales" else "Center frequencies",
                        listOfNotNull(
                            facts.centerFreq0?.let { "$it MHz" },
                            facts.centerFreq1?.let { "$it MHz" },
                        ).joinToString("  ·  "),
                    )
                }
            }

            if (device.kind == RadioKind.BLE) {
                StickyHeight(device.key to "ble") {
                Section(if (isEs) "Anuncio Bluetooth" else "Bluetooth advertisement")
                facts.primaryPhy?.let {
                    val phys = listOfNotNull(it, facts.secondaryPhy).distinct()
                    Meta(
                        if (isEs) "PHY de radio" else "Radio PHY",
                        phys.joinToString(" / ") { phy -> DeviceExplain.phyExplain(phy) },
                    )
                }
                facts.connectable?.let {
                    Meta(
                        if (isEs) "Conectable" else "Connectable",
                        if (it) {
                            if (isEs) "Sí — un teléfono podría abrir una conexión BLE"
                            else "Yes — a phone could open a BLE connection"
                        } else {
                            if (isEs) "No — solo difusión (lo puedes escuchar, no unir desde este escaneo)"
                            else "No — broadcast-only (you can hear it, not join it from this scan)"
                        },
                    )
                }
                facts.advertisingIntervalMs?.let {
                    Meta(
                        if (isEs) "Con qué frecuencia anuncia" else "How often it advertises",
                        if (isEs) "%.0f ms entre ráfagas (menor = más hablador en el aire)".format(it)
                        else "%.0f ms between bursts (smaller = chattier on the air)".format(it),
                    )
                }
                facts.periodicIntervalMs?.let {
                    Meta(if (isEs) "Anuncio periódico" else "Periodic advertising", "%.0f ms".format(it))
                }
                facts.advFlags?.let { flags ->
                    Meta(
                        if (isEs) "Descubribilidad" else "Discoverability",
                        DeviceExplain.flagsExplain(flags),
                    )
                    Meta(if (isEs) "Flags (crudo)" else "Flags (raw)", "0x%02X".format(flags), mono = true)
                }
                facts.appearance?.let { value ->
                    val name = RadioDb.appearance(value)
                    Meta(
                        if (isEs) "Qué dice ser (Appearance)" else "What it says it is (Appearance)",
                        name?.let {
                            if (isEs) "$it\nEl dispositivo publica este código GAP Appearance para describirse."
                            else "$it\nThe device publishes this GAP Appearance code to describe itself."
                        } ?: if (isEs) "Appearance no listado 0x%04X".format(value)
                        else "Unlisted Appearance 0x%04X".format(value),
                    )
                    Meta(
                        if (isEs) "Código de Appearance" else "Appearance code",
                        "0x%04X".format(value),
                        mono = true,
                    )
                }
                CodDecoder.decodeOrNull(facts.deviceClass)?.let { cod ->
                    Meta(
                        if (isEs) "Clase Bluetooth clásica" else "Classic Bluetooth class",
                        buildString {
                            append(cod.major)
                            if (cod.minor.isNotBlank()) append(" / ").append(cod.minor)
                            append(
                                if (isEs) "\nEste es el bitfield Class of Device que usa Bluetooth clásico."
                                else "\nThis is the Class of Device bitfield used by classic Bluetooth.",
                            )
                            if (cod.services.isNotEmpty()) {
                                append(if (isEs) "\nTambién ofrece: " else "\nAlso offers: ")
                                append(cod.services.joinToString(", "))
                            }
                        },
                    )
                }
                }
            }

            if (device.kind == RadioKind.WIFI) {
                StickyHeight(device.key to "wifi") {
                    Section(if (isEs) "Punto de acceso Wi-Fi" else "Wi-Fi access point")
                    facts.security?.let {
                        Meta(
                            if (isEs) "Cifrado / login" else "Encryption / login",
                            DeviceExplain.wifiSecurityExplain(it),
                        )
                        if (it.isNotBlank()) {
                            Meta(if (isEs) "Cadena de seguridad" else "Security string", it, mono = true)
                        }
                    }
                    facts.supportedRates?.let {
                        Meta(
                            if (isEs) "Velocidades soportadas" else "Supported rates",
                            if (isEs) "$it Mbps  (* = velocidad básica requerida)"
                            else "$it Mbps  (* = required basic rate)",
                        )
                    }
                    facts.capabilities?.takeIf { it.isNotBlank() && it != facts.security }?.let {
                        Meta(if (isEs) "Cadena de capacidades" else "Capability string", it, mono = true)
                    }
                }
            }

            if (device.serviceUuids.isNotEmpty() || facts.serviceData.isNotEmpty()) {
                StickyHeight(device.key to "services") {
                    if (device.serviceUuids.isNotEmpty()) {
                        Section(if (isEs) "Servicios que ofrece" else "Services it offers")
                        Meta(
                            if (isEs) "IDs de servicio" else "Service IDs",
                            device.serviceUuids.joinToString("\n") { uuid ->
                                DeviceExplain.uuidGloss(uuid)?.let { "$uuid  ·  $it" } ?: uuid
                            },
                            mono = true,
                        )
                    }
                    facts.serviceData.forEach { sd ->
                        val decoded = app.fieldwatch.domain.AdvPayloadDecoder.decodeService(sd)
                        decoded.forEach { field -> Meta(field.label, field.value) }
                        Meta(
                            serviceDataHeading(sd, isEs),
                            sd.dataHex.hexSpaced().ifBlank { if (isEs) "(vacío)" else "(empty)" },
                            mono = true,
                        )
                    }
                }
            }

            if (device.kind == RadioKind.BLE || device.kind == RadioKind.WIFI) {
                val fleets = vm.ui.value.fleets
                val decoded = remember(device.key, device.facts, device.fleetIds) {
                    SignatureFieldDecoder.decodeSighting(device, fleets)
                }
                val mapped = device.fleetIds.mapNotNull { id -> fleets.find { it.id == id && it.decode != null } }
                val hasPayload = device.facts.mfgRecords.isNotEmpty() ||
                    device.manufacturerDataHex.isNotBlank() ||
                    device.facts.serviceData.isNotEmpty()
                if (decoded.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DecodeGlyph(
                            tint = MaterialTheme.colorScheme.primary,
                            size = 16.dp,
                        )
                        Text(
                            if (isEs) "Campos decodificados" else "Decoded fields",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val multi = decoded.map { it.fleetId }.distinct().size > 1
                    decoded.forEach { row ->
                        Meta(if (multi) "${row.fleetName} · ${row.label}" else row.label, row.display)
                        if (row.note.isNotBlank()) {
                            Text(
                                row.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (mapped.isNotEmpty()) {
                    val govee = mapped.any { it.id == "fleet-govee" }
                    Text(
                        when {
                            hasPayload && govee -> if (isEs) {
                                "Los campos de decodificación no se aplicaron a este anuncio (payload corto, distinto company ID, o distinto layout). Las luces Govee normalmente solo envían un nombre; los higrómetros son H5074/H5075/H510x. Los bytes crudos están abajo."
                            } else {
                                "Decode fields did not apply to this advertisement (short payload, a different company ID, or a different layout). Govee lights usually only send a name; hygrometers are H5074/H5075/H510x. Raw bytes are below."
                            }
                            hasPayload -> if (isEs) {
                                "Los campos de decodificación no se aplicaron a este anuncio (payload corto, distinto company ID, o distinto layout). Los bytes crudos están abajo."
                            } else {
                                "Decode fields did not apply to this advertisement (short payload, a different company ID, or a different layout). Raw bytes are below."
                            }
                            govee -> if (isEs) {
                                "Esta firma tiene un mapa de decodificación, pero este anuncio no trae payload de fabricante o servicio que parsear. Muchas luces Govee solo emiten un nombre."
                            } else {
                                "This signature has a decode map, but this advertisement has no manufacturer or service payload to parse. Many Govee lights only broadcast a name."
                            }
                            else -> if (isEs) {
                                "Esta firma tiene un mapa de decodificación, pero este anuncio no trae payload de fabricante o servicio que parsear."
                            } else {
                                "This signature has a decode map, but this advertisement has no manufacturer or service payload to parse."
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val mfg = facts.mfgRecords.ifEmpty {
                device.manufacturerId?.let {
                    listOf(app.fieldwatch.domain.MfgRecord(it, device.manufacturerDataHex))
                } ?: emptyList()
            }
            if (mfg.isNotEmpty()) {
                StickyHeight(device.key to "mfg") {
                    Section(if (isEs) "Datos del fabricante dentro del anuncio" else "Maker data inside the ad")
                    mfg.forEach { rec ->
                        val company = RadioDb.company(rec.companyId)
                            ?: (if (isEs) "No está en la lista de compañías Bluetooth" else "Not in the Bluetooth company list")
                        Meta(
                            "Bluetooth company 0x%04X".format(rec.companyId),
                            if (isEs) {
                                "$company\nEste ID lo asigna el Bluetooth SIG y va dentro de datos específicos del fabricante."
                            } else {
                                "$company\nThis ID is assigned by the Bluetooth SIG and is carried in manufacturer-specific data."
                            },
                        )
                        val decoded = BleAdParser.mfgDecodedFields(rec)
                        decoded.forEach { (k, v) -> Meta(k, v) }
                        if (rec.dataHex.isNotBlank()) {
                            Meta(
                                if (isEs) "Payload crudo (${rec.dataHex.length / 2} bytes)"
                                else "Raw payload (${rec.dataHex.length / 2} bytes)",
                                rec.dataHex.hexSpaced(),
                                mono = true,
                            )
                        }
                    }
                }
            }

            if (facts.vendorIes.isNotEmpty() || device.vendorIeOuis.isNotEmpty()) {
                StickyHeight(device.key to "ies") {
                    Section(if (isEs) "Etiquetas de fabricante Wi-Fi" else "Wi-Fi vendor tags")
                    val rows = facts.vendorIes.ifEmpty {
                        device.vendorIeOuis.map { app.fieldwatch.domain.VendorIeRecord(it, -1, "") }
                    }
                    rows.forEach { ie ->
                        val org = RadioDb.vendorForOui24(ie.oui)
                        val type = if (ie.type >= 0) " type %d".format(ie.type) else ""
                        Meta(
                            "Vendor OUI ${ie.oui}$type",
                            buildString {
                                append(org ?: (if (isEs) "OUI IEEE desconocido" else "Unknown IEEE OUI"))
                                append(
                                    if (isEs) " — elemento de información extra del AP, no el SSID."
                                    else " — extra AP information element, not the SSID.",
                                )
                                if (ie.dataHex.isNotBlank()) {
                                    append("\n")
                                    append(ie.dataHex.hexSpaced())
                                }
                            },
                        )
                    }
                }
            }

            StickyHeight(device.key to "session") {
                Section(if (isEs) "Sesión" else "Session")
                Meta(if (isEs) "Visto por primera vez" else "First seen", fmt.format(Date(device.firstSeen)))
                Meta(if (isEs) "Visto por última vez" else "Last seen", fmt.format(Date(device.lastSeen)))
                Meta(if (isEs) "Impactos" else "Hits", device.hitCount.toString())
                Geo.screenCoord(device.latitude, device.longitude, demoMode)?.let {
                    Meta(if (isEs) "Última posición" else "Last fix", it)
                }
                if (device.fleetIds.isNotEmpty()) {
                    Meta(
                        if (isEs) "Firmas coincidentes" else "Matched signatures",
                        device.fleetIds.joinToString("\n") { id ->
                            val name = vm.fleetName(id)
                            if (vm.fleetHasDecode(id)) "$name  ⬡" else name
                        },
                    )
                }
                if (device.rawHex.isNotBlank() && device.kind == RadioKind.BLE) {
                    Meta(if (isEs) "Anuncio crudo" else "Raw advertisement", device.rawHex.hexSpaced())
                }
            }

            Text(if (isEs) "Tendencia de señal" else "Signal trend", style = MaterialTheme.typography.titleSmall)
            Sparkline(device.rssiHistory, accent, modifier = Modifier.fillMaxWidth().height(56.dp))
            Text(if (isEs) "Presencia (15 min)" else "Presence (15 min)", style = MaterialTheme.typography.titleSmall)
            PresenceTrack(device, System.currentTimeMillis(), 15 * 60 * 1000L, accent)
            if (device.kind == RadioKind.BLE) {
                FieldwatchActionButton(
                    onClick = onHunt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.NearMe, null)
                    Spacer(Modifier.padding(4.dp))
                    Text(if (isEs) "Búsqueda" else "Hunt")
                }
            } else {
                Text(
                    if (isEs) "La búsqueda es solo BLE. Los puntos de acceso Wi-Fi se actualizan demasiado lento en Android estándar para caminar hacia ellos."
                    else "Hunt is BLE only. Wi-Fi access points update too slowly on stock Android to walk toward.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            familyHint?.let { hint ->
                StickyHeight(device.key to "family") { FamilyCard(hint) }
            }
            FieldwatchActionButton(
                onClick = onCreateFleet,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.GroupAdd, null)
                Spacer(Modifier.padding(4.dp))
                Text(if (isEs) "Crear firma desde el dispositivo" else "Create signature from device")
            }
            FieldwatchActionButton(
                onClick = { vm.startDeviceDetailShare(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Share, null)
                Spacer(Modifier.padding(4.dp))
                Text(if (isEs) "Compartir como texto" else "Share as text")
            }
            FieldwatchActionButton(
                onClick = { vm.startDeviceDetailAiExport(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.padding(4.dp))
                Text(if (isEs) "Exportación IA" else "AI Export")
            }
            Text(
                if (isEs) {
                    "Abre un prompt listo para pegar en un chat: decodifica esta radio, busca OUI/company/UUIDs y di qué es más probable. Mismo descargo experimental que Ajustes → Exportación IA. Solo un dispositivo — no es identidad."
                } else {
                    "Opens a paste-ready prompt for a chat: decode this radio, look up OUI/company/UUIDs, and say what it most likely is. Same experimental disclaimer as Settings → AI Export. One device only — not identity."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrainingCard(
    label: TrainingLabel?,
    suggested: BehavioralKind,
    onPickLabel: () -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
) {
    val isEs = LocalAppStrings.current.isEs
    val scheme = MaterialTheme.colorScheme
    val container = if (label != null) scheme.primaryContainer else scheme.surfaceVariant.copy(alpha = 0.55f)
    val onContainer = if (label != null) scheme.onPrimaryContainer else scheme.onSurface
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = container,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (isEs) "Datos de entrenamiento" else "Training data",
                style = MaterialTheme.typography.labelSmall,
                color = onContainer.copy(alpha = 0.78f),
            )
            if (label != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isEs) "Etiquetada: ${label.name}" else "Labeled: ${label.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = onContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClear, enabled = enabled) {
                        Text(if (isEs) "Borrar" else "Clear")
                    }
                }
            } else if (!enabled) {
                Text(
                    if (isEs) "Recolección desactivada. Actívala en Informes → Datos de entrenamiento para registrar features."
                    else "Collection off. Turn on in Reports → Training data to log features.",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.78f),
                )
            } else {
                Text(
                    if (isEs) "Sin etiquetar. Sugerencia: ${suggested.label}"
                    else "Unlabeled. Suggest: ${suggested.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                )
                Text(
                    if (isEs) "Si reconoces este dispositivo, etiquétalo. Las etiquetas alimentan un futuro modelo ML."
                    else "If you recognize this device, label it. Labels feed a future ML model.",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.78f),
                )
                FieldwatchActionButton(
                    onClick = onPickLabel,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (isEs) "Etiquetar para entrenamiento" else "Label for training") }
            }
        }
    }
}

@Composable
private fun LabelPickerDialog(
    onDismiss: () -> Unit,
    onPick: (TrainingLabel) -> Unit,
) {
    val isEs = LocalAppStrings.current.isEs
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEs) "Etiquetar esta radio" else "Label this radio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (isEs) "Elige la clase que coincida con lo que ves. Elige UNSURE si no puedes determinar."
                    else "Pick the class that matches what you see. Choose UNSURE if you cannot tell.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TrainingLabel.entries.forEach { label ->
                    FieldwatchActionButton(
                        onClick = { onPick(label) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label.name.replace('_', ' ')) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (isEs) "Cancelar" else "Cancel") }
        },
    )
}

@Composable
private fun BehaviorCard(behavior: BehavioralClass) {
    val isEs = LocalAppStrings.current.isEs
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (isEs) "Clase de comportamiento" else "Behavioral class",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    behavior.kind.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(behavior.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            behavior.because.forEach { reason ->
                Text(
                    "· $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FamilyCard(hint: SignatureFamilyHint) {
    val isEs = LocalAppStrings.current.isEs
    val scheme = MaterialTheme.colorScheme
    val container = when (hint.verdict) {
        FamilyVerdict.STRONG -> scheme.primaryContainer
        FamilyVerdict.POSSIBLE -> Amber.nightIf(LocalNightMode.current).copy(alpha = 0.22f)
        FamilyVerdict.SINGLE, FamilyVerdict.TAGGED -> scheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val onContainer = when (hint.verdict) {
        FamilyVerdict.STRONG -> scheme.onPrimaryContainer
        FamilyVerdict.POSSIBLE, FamilyVerdict.SINGLE, FamilyVerdict.TAGGED -> scheme.onSurface
    }
    val muted = onContainer.copy(alpha = 0.78f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = container,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isEs) "Familia de firma" else "Signature family",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                    Text(hint.title, style = MaterialTheme.typography.titleMedium, color = onContainer)
                }
                if (hint.displayCount > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            hint.displayCount.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = onContainer,
                        )
                        RadioKindMark(hint.radioKind, size = 13.dp)
                    }
                }
            }
            hint.ruleLabel?.let { rule ->
                Text(
                    rule,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = scheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(hint.body, style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

@Composable
private fun SignatureNotesCard(notes: List<Pair<String, String>>) {
    if (notes.isEmpty()) return
    val isEs = LocalAppStrings.current.isEs
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (isEs) "Notas" else "Notes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            notes.forEach { (name, note) ->
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ObserverNotesCard(
    notes: String,
    canEdit: Boolean,
    editing: Boolean,
    draft: String,
    onToggleEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    saved: Boolean,
) {
    val isEs = LocalAppStrings.current.isEs
    val ink = Cyan.nightIf(LocalNightMode.current)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ink.copy(alpha = 0.18f),
        border = BorderStroke(1.5.dp, ink),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEs) "Notas del observador" else "Observer notes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    modifier = Modifier.weight(1f),
                )
                if (canEdit) {
                    IconButton(onClick = onToggleEdit) {
                        Icon(
                            Icons.Outlined.Edit,
                            if (editing) {
                                if (isEs) "Ocultar notas del observador" else "Hide observer notes"
                            } else {
                                if (isEs) "Notas del observador" else "Observer notes"
                            },
                        )
                    }
                }
            }
            if (!editing) {
                if (notes.isNotBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        if (isEs) "Sin notas del observador" else "No observer notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FieldwatchOutlinedField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = if (isEs) "Notas del observador" else "Observer notes",
                    singleLine = false,
                    minLines = 3,
                    supportingText = "${draft.trim().length}/${RadioBookmarks.MAX_NOTES}. ${RadioBookmarks.observerNotesHint()}",
                )
                FieldwatchActionButton(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = saveEnabled,
                ) {
                    if (saved) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.padding(4.dp))
                        Text(if (isEs) "Guardado" else "Saved")
                    } else {
                        Text(if (isEs) "Guardar notas" else "Save notes")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraAttentionCard(notes: List<Pair<String, String>>) {
    if (notes.isEmpty()) return
    val isEs = LocalAppStrings.current.isEs
    val warn = Amber.nightIf(LocalNightMode.current)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = warn.copy(alpha = 0.28f),
        border = BorderStroke(1.5.dp, warn),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = warn,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    if (isEs) "Atención especial" else "Extra attention",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = warn,
                )
            }
            notes.forEach { (name, note) ->
                Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                if (isEs) "Coincidencia de patrón, no identidad. No es un hallazgo de seguridad."
                else "Pattern match, not identity. Not a safety finding.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuessCard(guess: DeviceExplain.Guess) {
    val isEs = LocalAppStrings.current.isEs
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (isEs) "A qué se parece" else "What this looks like",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(guess.headline, style = MaterialTheme.typography.titleMedium)
            Text(
                guess.because,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Meta(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * FASE 5 (Bloque 4): ahora es @Composable para leer LocalAppStrings.
 * Los dos textos que genera se traducen; los nombres de compañía
 * (`RadioDb.company`) son data técnica y no se tocan.
 */
@Composable
private fun vendorLine(device: Sighting): String? {
    val isEs = LocalAppStrings.current.isEs
    val parts = ArrayList<String>(3)
    device.vendor?.let {
        parts += if (isEs) {
            "Fabricante IEEE de placa/chip: $it (${device.oui}). Esto es quién posee el prefijo MAC, no siempre la marca del producto."
        } else {
            "IEEE board/chip vendor: $it (${device.oui}). This is who owns the MAC prefix, not always the product brand."
        }
    }
    val mfgId = device.facts.mfgRecords.firstOrNull()?.companyId ?: device.manufacturerId
    if (mfgId != null) {
        val company = RadioDb.company(mfgId)
            ?: (if (isEs) "no listado" else "unlisted")
        parts += if (isEs) {
            "Compañía Bluetooth en el anuncio: $company (0x%04X).".format(mfgId)
        } else {
            "Bluetooth company in the ad: $company (0x%04X).".format(mfgId)
        }
    }
    return parts.joinToString("\n").ifBlank { null }
}

private fun uuidShort(uuid: String): String {
    val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
    return if (hex.length >= 8 && hex.startsWith("0000")) hex.substring(4, 8) else uuid.take(8)
}

private fun serviceDataHeading(sd: ServiceDataRecord, isEs: Boolean): String {
    val named = RadioDb.serviceUuid(sd.uuid)?.let { " ($it)" } ?: ""
    val frame = eddystoneFrameTag(sd)?.let { " · $it" } ?: ""
    val head = if (isEs) "Service data" else "Service data"
    return "$head ${uuidShort(sd.uuid)}$named$frame"
}

private fun eddystoneFrameTag(sd: ServiceDataRecord): String? {
    val hex = sd.uuid.filter { it.isLetterOrDigit() }.uppercase()
    val short = when {
        hex.length == 4 -> hex
        hex.length >= 8 && hex.startsWith("0000") -> hex.substring(4, 8)
        else -> return null
    }
    if (short != "FEAA") return null
    return when (sd.dataHex.filter { it.isLetterOrDigit() }.uppercase().take(2)) {
        "00" -> "UID"
        "10" -> "URL"
        "20" -> "TLM"
        "30" -> "EID"
        else -> null
    }
}