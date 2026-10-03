package app.fieldwatch.ui.screen

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import app.fieldwatch.ui.component.FieldwatchFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchDropdownField
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchSlider
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fieldwatch.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.domain.AlertVoiceWhat
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.ContextState
import app.fieldwatch.domain.IntensityMode
import app.fieldwatch.domain.PlaceKind
import app.fieldwatch.domain.ScanIntensity
import app.fieldwatch.domain.ScanProfile
import app.fieldwatch.domain.TakDefaults
import app.fieldwatch.domain.TakFeedStatus
import app.fieldwatch.domain.TakPublish
import app.fieldwatch.domain.TakUdpPreset
import app.fieldwatch.radio.WifiRadio
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.component.StableCaption
import app.fieldwatch.ui.component.StickyHeight
import app.fieldwatch.ui.i18n.AppLanguage
import app.fieldwatch.ui.i18n.LocalAppStrings
import java.net.Inet4Address
import java.net.NetworkInterface

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onRadioBookmarks: () -> Unit,
    onShowLiveTour: () -> Unit = {},
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val isEs = strings.isEs
    val settings = state.settings
    val saveSignatures = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::saveSignaturesToUri) }
    val importSignatures = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importSignaturesFromUri) }
    val saveSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::saveSettingsToUri) }
    val importSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importSettingsFromUri) }
    var confirmRestore by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar(strings.settingsTitle) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(strings.languageSection) {
                Text(
                    strings.languageDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppLanguage.entries.forEach { lang ->
                        FieldwatchFilterChip(
                            selected = settings.language == lang.code,
                            onClick = { vm.updateSettings { it.copy(language = lang.code) } },
                            label = {
                                Text(
                                    when (lang) {
                                        AppLanguage.AUTO -> strings.languageAuto
                                        AppLanguage.ENGLISH -> "English"
                                        AppLanguage.SPANISH -> "Español"
                                    }
                                )
                            },
                        )
                    }
                }
            }

            SectionCard(strings.appearanceSection) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.nightMode, Modifier.weight(1f))
                FieldwatchSwitch(settings.nightMode, { on -> vm.updateSettings { it.copy(nightMode = on) } })
            }
            Text(
                strings.nightModeDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEs) "Alto contraste" else "High contrast",
                    Modifier.weight(1f),
                )
                FieldwatchSwitch(
                    settings.a11yHighContrast,
                    { on -> vm.updateSettings { it.copy(a11yHighContrast = on) } },
                )
            }
            Text(
                if (isEs) {
                    "Refuerza el contraste de texto y bordes para uso bajo sol directo. " +
                        "Cambia el fondo a negro puro, sube la luminancia de los textos " +
                        "secundarios y oscurece los bordes para que se vean. Independiente " +
                        "del modo nocturno."
                } else {
                    "Reinforces text and border contrast for direct-sunlight use. " +
                        "Shifts the background to pure black, raises secondary-text " +
                        "luminance, and darkens borders so they read. Independent of " +
                        "night mode."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.keepScreenOn, Modifier.weight(1f))
                FieldwatchSwitch(settings.keepScreenOn, { on -> vm.updateSettings { it.copy(keepScreenOn = on) } })
            }
            Text(
                strings.keepScreenOnDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.privacyMode, Modifier.weight(1f))
                FieldwatchSwitch(settings.demoMode, { on -> vm.updateSettings { it.copy(demoMode = on) } })
            }
            Text(
                strings.privacyModeDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(strings.scanningSection) {
            val label = when (settings.intensity) {
                ScanIntensity.SAVER -> strings.intensitySaver
                ScanIntensity.BALANCED -> strings.intensityBalanced
                ScanIntensity.PERFORMANCE -> strings.intensityPerformance
            }
            Text("${strings.scanIntensity}  ·  $label")
            FieldwatchSlider(
                value = settings.intensity.ordinal.toFloat(),
                onValueChange = { v ->
                    val next = ScanIntensity.entries[v.toInt().coerceIn(0, 2)]
                    vm.updateSettings { it.copy(intensity = next) }
                },
                valueRange = 0f..2f,
                steps = 1,
            )
            Text(
                if (isEs) {
                    "Wi-Fi se escanea por lotes: el teléfono detecta todos los AP a la vez y luego espera. Alto rendimiento consulta cada ~30s, manteniéndose bajo el límite de Android de 4 escaneos cada 2 minutos. BLE sigue transmitiendo continuamente."
                } else {
                    "Wi-Fi is a batch radio: the phone grabs every AP at once, then must wait. High performance asks about every 30s — that is the fastest cadence that stays under the OS limit of four scans per two minutes. BLE still streams in between."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StableCaption(
                state.throttleHint.ifBlank { " " },
                if (isEs) "Wi-Fi esperando al SO" else "Wi-Fi waiting on OS",
                if (isEs) "Wi-Fi escaneando" else "Wi-Fi scanning",
                if (isEs) "Wi-Fi próximo en 99s" else "Wi-Fi next 99s",
                " ",
            )

            val lifecycleOwner = LocalLifecycleOwner.current
            var osThrottled by remember { mutableStateOf(WifiRadio.osScanThrottled(context)) }
            var backgroundAllowed by remember { mutableStateOf(isBackgroundUsageAllowed(context)) }
            var unrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
            var needDevOptions by remember { mutableStateOf(false) }
            var batteryGate by remember { mutableStateOf<BatteryAndroidGate?>(null) }
            DisposableEffect(lifecycleOwner) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        osThrottled = WifiRadio.osScanThrottled(context)
                        backgroundAllowed = isBackgroundUsageAllowed(context)
                        unrestricted = isIgnoringBatteryOptimizations(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }
            val fastActive = settings.wifiFastScan && !osThrottled
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEs) "Escaneos Wi-Fi más rápidos" else "Faster Wi-Fi AP scans", Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = settings.wifiFastScan,
                    onCheckedChange = { on ->
                        if (!on) {
                            vm.updateSettings { it.copy(wifiFastScan = false) }
                        } else if (!osThrottled) {
                            vm.updateSettings { it.copy(wifiFastScan = true) }
                        } else {
                            needDevOptions = true
                        }
                    },
                )
            }
            StableCaption(
                when {
                    Build.VERSION.SDK_INT < 30 ->
                        if (isEs) "Requiere Android 11+ para que Fieldwatch pueda leer si el SO sigue limitando los escaneos. Se mantiene desactivado."
                        else "Needs Android 11+ so Fieldwatch can read whether the OS is still throttling scans. This phone cannot confirm that, so the switch stays off."
                    fastActive ->
                        if (isEs) "Activado. Fieldwatch solicita nueva lista de AP cada ~8s. Usa más batería."
                        else "On. Fieldwatch asks for a new AP list about every 8 seconds. Uses more battery and heat. If the OS starts refusing scans, it backs off."
                    settings.wifiFastScan && osThrottled ->
                        if (isEs) "Guardado, pero no activo — la limitación de escaneo Wi-Fi de Android sigue activa. Desactívala en Opciones de desarrollador."
                        else "Saved on, but not in effect — Android Wi-Fi scan throttling is still on. Turn that off in Developer options, then return here."
                    else ->
                        if (isEs) "Android estándar permite unos 4 escaneos de AP por cada dos minutos. Los escaneos más rápidos requieren desactivar la limitación en Opciones de desarrollador."
                        else "Stock Android allows about four AP scans per two minutes. Faster scans only run after you turn off Wi-Fi scan throttling in Developer options. Fieldwatch checks that OS switch before turning this on, and cannot change it for you."
                },
                "Needs Android 11+ so Fieldwatch can read whether the OS is still throttling scans. This phone cannot confirm that, so the switch stays off.",
                "On. Fieldwatch asks for a new AP list about every 8 seconds. Uses more battery and heat. If the OS starts refusing scans, it backs off.",
                "Saved on, but not in effect — Android Wi-Fi scan throttling is still on. Turn that off in Developer options, then return here.",
                "Stock Android allows about four AP scans per two minutes. Faster scans only run after you turn off Wi-Fi scan throttling in Developer options. Fieldwatch checks that OS switch before turning this on, and cannot change it for you.",
            )
            if (needDevOptions) {
                AlertDialog(
                    onDismissRequest = { needDevOptions = false },
                    title = { Text(if (isEs) "Opciones de desarrollador requeridas" else "Developer options required") },
                    text = {
                        Text(
                            if (Build.VERSION.SDK_INT < 30) {
                                if (isEs) "Este teléfono es anterior a Android 11, por lo que Fieldwatch no puede leer la limitación de escaneo Wi-Fi del SO."
                                else "This phone is older than Android 11, so Fieldwatch cannot read the OS Wi-Fi scan-throttle switch. Faster AP scanning stays off."
                            } else {
                                if (isEs) "Android aún está limitando los escaneos Wi-Fi. Activa las Opciones de desarrollador (toca Número de compilación 7 veces en Acerca del teléfono), luego ve a Ajustes → Opciones de desarrollador → Limitación de escaneo Wi-Fi → Desactivar."
                                else "Android is still throttling Wi-Fi scans (about four per two minutes). Fieldwatch will not turn Faster Wi-Fi AP scans on until that is off.\n\n" +
                                    "Enable Developer options (tap Build number seven times in About phone), then Settings → Developer options → Wi-Fi scan throttling → Off. Come back and flip this switch again."
                            },
                        )
                    },
                    confirmButton = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            TextButton(
                                onClick = {
                                    needDevOptions = false
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                    }
                                },
                            ) { Text(if (isEs) "Abrir opciones de desarrollador" else "Open developer options") }
                        } else {
                            TextButton(onClick = { needDevOptions = false }) { Text(strings.ok) }
                        }
                    },
                    dismissButton = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            TextButton(onClick = { needDevOptions = false }) { Text(if (isEs) "Ahora no" else "Not now") }
                        }
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEs) "Permitir uso en segundo plano" else "Allow background usage", Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = backgroundAllowed,
                    onCheckedChange = { batteryGate = BatteryAndroidGate.BACKGROUND },
                )
            }
            Text(
                if (isEs) "Refleja el permiso de uso en segundo plano de Android. Toca para abrir la página de batería de Fieldwatch y usar ese interruptor."
                else "Mirrors Android Allow background usage. Tap to open Fieldwatch’s Battery page and use that switch. Fieldwatch updates when you return. Off: the OS can kill the scan as soon as you leave. Not Keep screen on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEs) "Batería sin restricciones" else "Unrestricted battery", Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = unrestricted,
                    onCheckedChange = { batteryGate = BatteryAndroidGate.UNRESTRICTED },
                )
            }
            Text(
                if (isEs) "Refleja Batería sin restricciones de Android (no optimizada). Permite escanear sin que el sistema suspenda la app."
                else "Mirrors Android Unrestricted (not Optimized). Some phones (Samsung among them) do not open onto that choice. If you only see Allow background usage, tap that row to click through and select Unrestricted. Fieldwatch updates when you return.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (batteryGate != null) {
                val background = batteryGate == BatteryAndroidGate.BACKGROUND
                AlertDialog(
                    onDismissRequest = { batteryGate = null },
                    title = {
                        Text(
                            if (background) (if (isEs) "Permitir uso en segundo plano" else "Allow background usage")
                            else (if (isEs) "Batería sin restricciones" else "Unrestricted battery"),
                        )
                    },
                    text = {
                        Text(
                            if (background) {
                                if (isEs) "La siguiente pantalla es la página de batería de Fieldwatch. Activa el interruptor 'Permitir uso en segundo plano'."
                                else "The next screen is Fieldwatch’s Battery page. Use the Allow background usage switch. Fieldwatch will match that setting when you return."
                            } else {
                                if (isEs) "En la pantalla de ajustes de batería, selecciona 'Sin restricciones' para evitar interrupciones."
                                else "Some phones (Samsung among them) do not open onto Unrestricted / Optimized / Restricted. If you only see Allow background usage, tap that row to click through, then select Unrestricted. Fieldwatch will match that when you return."
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val gate = batteryGate
                                batteryGate = null
                                openAppBatteryPage(
                                    context,
                                    highlightBackground = gate == BatteryAndroidGate.BACKGROUND,
                                )
                            },
                        ) { Text(if (isEs) "Abrir ajustes de Android" else "Open Android settings") }
                    },
                    dismissButton = {
                        TextButton(onClick = { batteryGate = null }) { Text(if (isEs) "Ahora no" else "Not now") }
                    },
                )
            }
            }

            SectionCard(if (isEs) "Escaneo adaptativo" else "Adaptive scanning") {
            val ctx by vm.contextState.collectAsStateWithLifecycle()
            val effective by vm.effectiveScanProfile.collectAsStateWithLifecycle()
            var floorOpen by remember { mutableStateOf(false) }
            var addPlaceError by remember { mutableStateOf<String?>(null) }
            Text(
                if (isEs) {
                    "Cómo Fieldwatch elige la cadencia de escaneo según tu contexto. Manual usa el deslizador de arriba tal como está. Adaptativo baja la cadencia cuando estás quieto, con batería, en Casa o Trabajo, o con la pantalla apagada; y la sube cuando te mueves o la pantalla está encendida."
                } else {
                    "How Fieldwatch picks the scan cadence from your context. Manual keeps the slider above exactly as set. Adaptive lowers the cadence when you are still, on battery, at Home or Work, or the screen is off — and raises it when you are moving or the screen is on."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (isEs) "Modo" else "Mode",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FieldwatchFilterChip(
                    selected = settings.intensityMode == IntensityMode.MANUAL,
                    onClick = { vm.setIntensityMode(IntensityMode.MANUAL) },
                    label = { Text(if (isEs) "Manual" else "Manual") },
                )
                FieldwatchFilterChip(
                    selected = settings.intensityMode == IntensityMode.ADAPTIVE,
                    onClick = { vm.setIntensityMode(IntensityMode.ADAPTIVE) },
                    label = { Text(if (isEs) "Adaptativo" else "Adaptive") },
                )
            }
            Text(
                if (settings.intensityMode == IntensityMode.MANUAL) {
                    if (isEs) "Manual: se usa el deslizador de Intensidad de escaneo tal cual está configurado."
                    else "Manual: the Scan intensity slider above is used exactly as set."
                } else {
                    if (isEs) "Adaptativo: el deslizador de Intensidad de escaneo pasa a ser el respaldo manual; el contexto decide el perfil real, nunca por debajo del mínimo."
                    else "Adaptive: the Scan intensity slider above becomes the manual fallback; the context decides the actual profile, never below the floor."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                if (isEs) "Perfil mínimo" else "Minimum profile",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            val floorOptions = listOf(
                ScanProfile.SAVER,
                ScanProfile.BALANCED,
                ScanProfile.PERFORMANCE,
            )
            ExposedDropdownMenuBox(
                expanded = floorOpen,
                onExpandedChange = { floorOpen = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                FieldwatchDropdownField(
                    label = if (isEs) "Mínimo" else "Floor",
                    value = scanProfileLabel(settings.adaptiveFloor, isEs),
                    expanded = floorOpen,
                )
                ExposedDropdownMenu(floorOpen, { floorOpen = false }) {
                    floorOptions.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(scanProfileLabel(profile, isEs)) },
                            onClick = {
                                vm.setAdaptiveFloor(profile)
                                floorOpen = false
                            },
                        )
                    }
                }
            }
            Text(
                if (isEs) {
                    "El modo adaptativo nunca baja de este perfil. Ahorro es el mínimo; Rendimiento el máximo. Agresivo solo se usa durante una búsqueda."
                } else {
                    "Adaptive never drops below this profile. Saver is the lowest, Performance is the highest. Aggressive is only used while hunting."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                if (isEs) "Lugares guardados" else "Saved places",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (settings.knownPlaces.isEmpty()) {
                Text(
                    if (isEs) "Aún no hay lugares guardados. Añade Casa o Trabajo para que Adaptativo baje al mínimo cuando estés allí."
                    else "No saved places yet. Add Home or Work so Adaptive can drop to the floor when you are there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                settings.knownPlaces.forEach { place ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(place.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${place.kind.label()}  ·  %.5f, %.5f".format(place.lat, place.lon),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.removeKnownPlace(place.id) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = if (isEs) "Eliminar ${place.label}" else "Remove ${place.label}",
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FieldwatchActionButton(
                    onClick = {
                        addPlaceError = null
                        if (!vm.addCurrentPlaceAs(PlaceKind.HOME, "Home")) {
                            addPlaceError = if (isEs) "Aún no hay fix GPS. Espera a tener señal o activa Etiquetar detecciones con GPS."
                            else "No GPS fix yet. Wait for a lock or enable Tag detections with GPS."
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (isEs) "Añadir Casa" else "Add Home") }
                FieldwatchActionButton(
                    onClick = {
                        addPlaceError = null
                        if (!vm.addCurrentPlaceAs(PlaceKind.WORK, "Work")) {
                            addPlaceError = if (isEs) "Aún no hay fix GPS. Espera a tener señal o activa Etiquetar detecciones con GPS."
                            else "No GPS fix yet. Wait for a lock or enable Tag detections with GPS."
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (isEs) "Añadir Trabajo" else "Add Work") }
            }
            if (!settings.tagLocation) {
                Text(
                    if (isEs) "Debe estar activo Etiquetar detecciones con GPS para guardar un lugar (sección Ubicación abajo)."
                    else "Tag detections with GPS must be on to save a place (Location section below).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (addPlaceError != null) {
                Text(
                    addPlaceError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                if (isEs) "Contexto actual" else "Current context",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            ContextStatusCard(ctx, effective, settings.intensityMode, isEs)
            }

            SectionCard(if (isEs) "Lista de vigilancia" else "Watchlist") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEs) "Alertas de la lista de vigilancia" else "Watchlist alerts",
                    Modifier.weight(1f),
                )
                FieldwatchSwitch(settings.alertsEnabled, { on -> vm.updateSettings { it.copy(alertsEnabled = on) } })
            }
            Text(
                if (isEs) {
                    "Activado por defecto. Interruptor maestro para las firmas marcadas y radios con nombre. Desactivado: sin pitido, vibración, destello, salto a la radio ni tarjeta en la sombra de notificaciones. Marcar sigue funcionando — simplemente no se te avisará cuando esa radio aparezca."
                } else {
                    "On by default. Master switch for bookmarked signatures and devices. Off: no beep, vibration, flash, jump, or shade card. Bookmarking still works — you just will not be told when that radio appears."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val radioWatchN = state.watchlist.count { it.deviceKey != null }
            FieldwatchActionButton(
                onClick = onRadioBookmarks,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (isEs) "Radios con nombre ($radioWatchN)"
                    else "Named radios ($radioWatchN)",
                )
            }
            Text(
                if (isEs) {
                    "Nombres personalizados para una MAC. La alerta es opcional. Filtros → Solo radios con nombre las muestra en En vivo. Las firmas vigiladas se gestionan en Firmas."
                } else {
                    "Custom names for one MAC. Alert is optional. Filters → Named radios only shows them on Live. Signature watches stay on Signatures."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEs) "Pitido en firma vigilada" else "Beep on watched signature",
                    Modifier.weight(1f),
                )
                FieldwatchSwitch(
                    settings.alertBeep,
                    { on -> vm.updateSettings { it.copy(alertBeep = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                if (isEs) {
                    "El doble pip en el volumen multimedia cuando una firma marcada o radio aparece por primera vez, o vuelve tras desaparecer. Las detecciones ya presentes no vuelven a pitar. Independiente de Voz — usa pitido, voz o ambos. Sube el volumen multimedia si no oyes nada, luego toca Probar alerta."
                } else {
                    "The double pip on media volume when a bookmarked signature or device first appears, or returns after leaving. Sitting detections do not beep again. Independent of Voice — use beep, voice, or both. Raise media volume if you hear nothing, then tap Test alert."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEs) "Voz en firma vigilada" else "Voice on watched signature",
                    Modifier.weight(1f),
                )
                FieldwatchSwitch(
                    settings.alertVoice,
                    { on -> vm.updateSettings { it.copy(alertVoice = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                if (isEs) {
                    "Activado por defecto. Habla en el mismo volumen multimedia que el pip. Independiente de Pitido: con Pitido activado la voz sigue al pip; con Pitido desactivado, solo voz. No en Búsqueda. Si ya está hablando una frase, el siguiente impacto se salta. Teléfonos sin texto-a-voz seguirán pitando si Pitido está activado."
                } else {
                    "On by default. Speaks on the same media volume as the pip. Independent of Beep: with Beep on, voice follows the pip; with Beep off, voice only. Not Hunt. If a phrase is already being spoken, a second hit is skipped. Phones with no text-to-speech still beep if Beep is on."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (isEs) "Qué decir" else "What to say",
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertVoiceWhat.entries.forEach { item ->
                    FieldwatchFilterChip(
                        selected = settings.alertVoiceWhat == item,
                        onClick = { vm.updateSettings { it.copy(alertVoiceWhat = item) } },
                        enabled = settings.alertsEnabled && settings.alertVoice,
                        label = { Text(item.localizedLabel(isEs)) },
                    )
                }
            }
            Text(
                if (isEs) {
                    "Para firmas vigiladas: Clase es el grupo del icono en En vivo (rastreadores, audio, …). Firma es la fila del catálogo (Apple AirTags, Axon, …). Clase + firma (por defecto) dice ambas. Una radio con nombre y Alerta activada siempre dice su nombre personalizado, aunque no tenga clase. Probar alerta reproduce la mezcla que tengas activa."
                } else {
                    "For signature watches: Class is the Live glyph bucket (finder tags, audio, …). Signature is the catalog row (Apple AirTags, Axon, …). Class + signature (default) says both. A named radio with Alert on always says its custom name, even if it has no class. Test alert plays the signature mix you have on."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::testWatchBeep,
                modifier = Modifier.fillMaxWidth(),
                enabled = settings.alertsEnabled && (settings.alertBeep || settings.alertVoice),
            ) { Text(if (isEs) "Probar alerta" else "Test alert") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEs) "Saltar a nueva detección vigilada" else "Jump to new watched detection",
                    Modifier.weight(1f),
                )
                FieldwatchSwitch(
                    settings.snapToBeep,
                    { on -> vm.updateSettings { it.copy(snapToBeep = on) } },
                    enabled = settings.alertsEnabled && (settings.alertBeep || settings.alertVoice),
                )
            }
            Text(
                if (isEs) {
                    "Cuando aparece una firma o radio vigilada nueva, En vivo salta a esa fila para que veas el destello. Funciona con pitido, voz o ambos. Los impactos débiles van al final en una lista ordenada por intensidad. Desactívalo si no quieres que la lista se mueva."
                } else {
                    "When a new watched signature or device appears, Live scrolls to that row so you can see the flash. Works with beep, voice, or both. Weak hits sit at the bottom of a strength-ranked list. Turn this off if you do not want the list to move."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEs) "Notificación del sistema" else "System notification",
                    Modifier.weight(1f),
                )
                FieldwatchSwitch(
                    settings.alertShade,
                    { on -> vm.updateSettings { it.copy(alertShade = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                if (isEs) {
                    "Opcional. Publica una tarjeta silenciosa en la sombra de notificaciones cuando aparece una radio vigilada. Desactivado por defecto — el pitido y el destello son suficientes, y prescindir de la tarjeta mantiene el bucle de escaneo más ligero."
                } else {
                    "Optional. Posts a silent shade card when a watched radio appears. Off by default — the beep and flash are enough, and skipping the card keeps the scan loop lighter."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(if (isEs) "Ubicación" else "Location") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEs) "Etiquetar detecciones con GPS" else "Tag detections with GPS",
                    Modifier.weight(1f),
                )
                FieldwatchSwitch(settings.tagLocation, { on -> vm.updateSettings { it.copy(tagLocation = on) } })
            }
            Text(
                if (isEs) {
                    "Activado por defecto. Solicita actualizaciones GPS/red en vivo y marca cada detección (detalle En vivo, En movimiento contigo, Informe de misión, y lat/lon en filas nuevas del registro). Se ignora si la última posición conocida tiene más de 30 s. Ese es tu GPS en el momento de la detección, no una posición independiente de la otra radio. Usa Ubicación de alta precisión o la trayectoria queda en 0. Desactívalo si no quieres coordenadas del operador en los registros. Los pines TAK 'escuchado aquí' también lo necesitan; las coordenadas publicadas en el payload (Remote ID) no."
                } else {
                    "On by default. Requests live GPS/network updates and stamps each hear (Live detail, Moving with you, " +
                        "Debrief, and lat/lon on new log rows). Last-known-only is ignored if older than 30 s. " +
                        "That is your GPS at hear-time, not an independent fix on the other radio. " +
                        "Use high-accuracy Location or the path stays 0. Turn off if you do not want operator coordinates on logs. " +
                        "Heard-here TAK pins also need this; advertised payload coordinates (Remote ID) do not."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEs) "Nombres de lugares y mapas en línea" else "Online place names and maps",
                    Modifier.weight(1f),
                )
                FieldwatchSwitch(settings.onlineLookup, { on -> vm.updateSettings { it.copy(onlineLookup = on) } })
            }
            Text(
                if (isEs) {
                    "Activado por defecto. Cuando el teléfono tiene internet, el Informe de misión / Exportación IA geocodifican inversamente las marcas GPS a calle/ciudad, y en Informes → Trayectoria se cargan teselas de OpenStreetMap bajo el trazo. Sin nube de Fieldwatch, sin clave de API. Sin conexión o sin geocodificador: el informe usa solo coordenadas y la trayectoria queda en el trazo actual con norte arriba — sin diálogo de error. Desactívalo para mantener calles y teselas fuera de informes y trayectoria. Informe de misión, Exportar situación, Exportar registro y Restablecer / borrar registro están en la pestaña Informes."
                } else {
                    "On by default. When the phone has internet, Debrief / AI Export reverse-geocode GPS stamps " +
                        "to street/city, and Reports → Path loads OpenStreetMap tiles under the trace. " +
                        "No Fieldwatch cloud, no API key. Offline or no geocoder: Debrief uses coordinates only and Path stays the current north-up trace — no error dialog. " +
                        "Turn off to keep streets and map tiles out of reports and Path. " +
                        "Debrief, Sit export, Log export, and Reset / clear log are on the Reports tab."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(if (isEs) "TAK / CoT" else "TAK / CoT") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEs) "Feed TAK / CoT" else "TAK / CoT feed",
                    Modifier.weight(1f),
                )
                FieldwatchSwitch(settings.takEnabled, { on -> vm.updateSettings { it.copy(takEnabled = on) } })
            }
            Text(
                if (isEs) {
                    "Desactivado por defecto. Envía marcadores Cursor-on-Target por UDP a ATAK, WinTAK o iTAK. Este teléfono (${TakDefaults.LOOPBACK}:${TakDefaults.PORT}) es ATAK CIV en este dispositivo. El multicast LAN es ${TakDefaults.SA_HOST}:${TakDefaults.SA_PORT}. Personalizado es una IPv4 unicast o nombre de host. Solo UDP — el TCP 8087 de un servidor TAK no es este feed. Los pines 'escuchado aquí' se sitúan en el GPS de este teléfono en la detección más fuerte (aproximación más cercana) y se etiquetan (here). Alejarse no arrastra el pin; una detección más fuerte lo mueve. Los keep-alive refrescan el mismo lat/lon cada ~10 s para que ATAK no lo descarte. Lat/lon publicados (Remote ID stock) van sobre la aeronave; el mismo Remote ID mantiene un marcador que se mueve (UAS ID, no la MAC BLE rotativa). Una ubicación de piloto decodificada es un segundo pin. Las radios desaparecidas se descartan en ATAK en lugar de quedarse 120 s. Toca un marcador en ATAK para ver las notas (nombre, MAC, RSSI, firmas). No es dirección por radio. No es un plugin de Remote ID. El modo de privacidad pausa el feed."
                } else {
                    "Off by default. Sends Cursor-on-Target UDP markers to ATAK, WinTAK, or iTAK. " +
                        "This phone (${TakDefaults.LOOPBACK}:${TakDefaults.PORT}) is ATAK CIV on this handset. " +
                        "LAN multicast is ${TakDefaults.SA_HOST}:${TakDefaults.SA_PORT}. " +
                        "Custom is a unicast IPv4 or hostname. UDP only — a TAK server’s TCP 8087 is not this feed. " +
                        "Heard-here pins sit at this phone’s GPS at the loudest hear (closest approach) and are labeled (here). " +
                        "Walking away does not drag the pin; a louder hear moves it. Keep-alives refresh the same lat/lon every ~10 s so ATAK does not drop it. " +
                        "Advertised lat/lon (stock Remote ID) sit on the aircraft; the same Remote ID " +
                        "keeps one marker that moves (UAS ID, not the rotating BLE MAC). " +
                        "A decoded pilot location is a second pin. Gone radios are dropped on ATAK instead of sitting 120 s. " +
                        "Tap a marker in ATAK for remarks (name, MAC, RSSI, signatures). " +
                        "Not direction-finding. Not a Remote ID plugin. Privacy mode pauses the feed."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.takEnabled && settings.demoMode) {
                Text(
                    if (isEs) "El modo de privacidad está activo — el feed está pausado para no enviar MACs completas ni coordenadas. Desactiva el modo de privacidad para publicar."
                    else "Privacy mode is on — the feed is paused so full MACs and coordinates are not sent. Turn Privacy mode off to publish.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (settings.takEnabled) {
                TakFeedSettings(settings, vm, state.takStatus, isEs)
            }
            }

            SectionCard(if (isEs) "Registro" else "Logging") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEs) "Escribir detecciones a disco" else "Write detections to disk",
                    Modifier.weight(1f),
                )
                FieldwatchSwitch(settings.loggingEnabled, { on -> vm.updateSettings { it.copy(loggingEnabled = on) } })
            }
            StableCaption(
                if (settings.loggingEnabled) {
                    if (isEs) "Registro activado. Las nuevas detecciones se añaden al archivo rotativo."
                    else "Logging is on. New detections are appended to the rotating file."
                } else {
                    if (isEs) "Registro desactivado. El escaneo sigue funcionando; no se escribe nada nuevo hasta que lo vuelvas a activar."
                    else "Logging is off. Scanning still runs; nothing new is written until you turn this back on."
                },
                "Logging is on. New detections are appended to the rotating file.",
                "Logging is off. Scanning still runs; nothing new is written until you turn this back on.",
            )
            Text(
                if (isEs) {
                    "El archivo rotativo es JSON lines (una detección por línea). Informes → Registro → Formato escribe CSV, JSON lines, GPX, KML o WiGLE cuando usas Compartir o Guardar."
                } else {
                    "The rotating file is JSON lines (one hear per line). Reports → Log → Format writes CSV, JSON lines, GPX, KML, or WiGLE when you Share or Save."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var rotateDrag by remember { mutableIntStateOf(settings.logRotateKb) }
            var rotateDragging by remember { mutableStateOf(false) }
            LaunchedEffect(settings.logRotateKb) {
                if (!rotateDragging) rotateDrag = settings.logRotateKb
            }
            Text(
                if (isEs) "Rotar a $rotateDrag KB" else "Rotate at $rotateDrag KB",
            )
            FieldwatchSlider(
                value = rotateDrag.toFloat(),
                onValueChange = {
                    rotateDragging = true
                    rotateDrag = it.toInt().coerceIn(128, 4096)
                },
                onValueChangeFinished = {
                    vm.updateSettings { s -> s.copy(logRotateKb = rotateDrag) }
                    rotateDragging = false
                },
                valueRange = 128f..4096f,
                label = if (isEs) "Rotar registro" else "Rotate log",
                stateFormatter = { v -> "${v.toInt()} KB" },
            )
            var staleDrag by remember { mutableIntStateOf(settings.staleSec) }
            var staleDragging by remember { mutableStateOf(false) }
            LaunchedEffect(settings.staleSec) {
                if (!staleDragging) staleDrag = settings.staleSec
            }
            Text(
                if (isEs) "Caducar tras ${staleDrag}s" else "Stale after ${staleDrag}s",
            )
            FieldwatchSlider(
                value = staleDrag.toFloat(),
                onValueChange = {
                    staleDragging = true
                    staleDrag = it.toInt().coerceIn(15, 180)
                },
                onValueChangeFinished = {
                    vm.updateSettings { s -> s.copy(staleSec = staleDrag) }
                    staleDragging = false
                },
                valueRange = 15f..180f,
                label = if (isEs) "Caducidad" else "Stale after",
                stateFormatter = { v -> "${v.toInt()} s" },
            )
            StickyHeight("log-stats") {
                Text(
                    if (isEs) {
                        "${state.logLines} líneas esta sesión  ·  ${vm.logBytes() / 1024} KB en disco. Compartir, Guardar y Restablecer / borrar registro están en la pestaña Informes."
                    } else {
                        "${state.logLines} lines this session  ·  ${vm.logBytes() / 1024} KB on disk. " +
                            "Share, Save, and Reset / clear log are on the Reports tab."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            }

            SectionCard(if (isEs) "Firmas" else "Signatures") {
            Text(
                if (isEs) {
                    "Exporta el catálogo (stock más los que añadiste o editaste) para compartirlo con otro Fieldwatch o como copia de seguridad. Importar añade filas nuevas y reglas extra; no borra nada. Se saltan los que tengan el mismo id o las mismas reglas, así que un paquete se puede importar dos veces. Actualizar catálogo stock desde GitHub reemplaza las filas stock (incluida Atención especial) desde el paquete v2 del repositorio; los marcadores, los Ajustes y las firmas que añadiste se conservan. Necesita internet. Sin conexión: Importar firmas desde archivo. Restablecer valores predeterminados abajo sí elimina las personalizaciones."
                } else {
                    "Export the catalog (stock plus any you added or edited) to share with another Fieldwatch or as a backup. Import adds new rows and extra rules; it does not delete anything. Same id or the same match rules are skipped so a pack can be imported twice. Update stock catalog from GitHub replaces stock rows (including Extra attention) from the v2 pack on the repo; bookmarks, Settings, and signatures you added stay. Needs internet. Offline: Import signatures from a file. Restore defaults below still wipes customs."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startSignatureShare,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isEs) "Exportar firmas" else "Export signatures") }
            FieldwatchActionButton(
                onClick = { saveSignatures.launch(vm.suggestedSignaturesName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isEs) "Guardar firmas en almacenamiento / SD…" else "Save signatures to SD card / storage…") }
            FieldwatchActionButton(
                onClick = {
                    importSignatures.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isEs) "Importar firmas…" else "Import signatures…") }
            FieldwatchActionButton(
                onClick = vm::updateStockCatalogFromGitHub,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isEs) "Actualizar catálogo stock desde GitHub" else "Update stock catalog from GitHub") }

            FieldwatchActionButton(
                onClick = { confirmRestore = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isEs) "Restablecer firmas y presets predeterminados" else "Restore default signatures & presets")
            }
            }

            SectionCard(if (isEs) "Copia de ajustes" else "Settings backup") {
            Text(
                if (isEs) {
                    "Interruptores de ajustes, el filtro actual, presets de filtro, radios con nombre y firmas vigiladas. No el catálogo — para eso Exportar firmas. No registros ni GPS. Importar reemplaza eso en este teléfono; el catálogo se conserva. Úsalo tras un restablecimiento de fábrica o en un teléfono nuevo."
                } else {
                    "Settings switches, the current filter, filter presets, named radios, and signature watches. " +
                        "Not the catalog — that is Export signatures. Not logs or GPS. " +
                        "Import replaces those on this phone; the catalog stays. " +
                        "Use this after a factory reset or on a new phone."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startSettingsShare,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isEs) "Exportar ajustes" else "Export settings") }
            FieldwatchActionButton(
                onClick = { saveSettings.launch(vm.suggestedSettingsName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isEs) "Guardar ajustes en almacenamiento / SD…" else "Save settings to SD card / storage…") }
            FieldwatchActionButton(
                onClick = {
                    importSettings.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isEs) "Importar ajustes…" else "Import settings…") }
            }

            FieldwatchActionButton(
                onClick = onShowLiveTour,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isEs) "Mostrar tour de En vivo" else "Show Live tour") }
            Text(
                if (isEs) {
                    "Superposición sobre En vivo: Ajustar es Visualización (Radar, lista, Por clase), Pausar, Filtros, Firmas, Informes, Ajustes. Primera vez tras la licencia; este botón lo vuelve a mostrar."
                } else {
                    "Chrome overlay on Live: Tune is Display (Radar, list, By class), Pause, Filters, Signatures, Reports, Settings. First-run after the license; this button shows it again."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "Fieldwatch ${app.fieldwatch.BuildConfig.VERSION_NAME}  ·  " +
                    (if (isEs) "Catálogo" else "Catalog") + " ${state.catalogVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (isEs) {
                    "Solo Wi-Fi + BLE pasivo. Android estándar no captura promiscuamente estaciones Wi-Fi; los puntos de acceso y emisores BLE son lo que exponen las radios."
                } else {
                    "Passive Wi-Fi + BLE only. " +
                        "Stock Android cannot promiscuously capture Wi-Fi stations; access points and BLE advertisers are what the radios expose."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val footerLifecycle = LocalLifecycleOwner.current
            var ipv4 by remember { mutableStateOf(localIpv4Addresses()) }
            DisposableEffect(footerLifecycle) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) ipv4 = localIpv4Addresses()
                }
                footerLifecycle.lifecycle.addObserver(obs)
                onDispose { footerLifecycle.lifecycle.removeObserver(obs) }
            }
            Text(
                if (ipv4.isEmpty()) {
                    if (isEs) "IPv4 de este teléfono  ·  ninguna" else "This phone’s IPv4  ·  none"
                } else {
                    if (isEs) "IPv4 de este teléfono  ·  ${ipv4.joinToString("  ·  ")}"
                    else "This phone’s IPv4  ·  ${ipv4.joinToString("  ·  ")}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            CreditFooter()
        }
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = {
                Text(if (isEs) "¿Restablecer valores predeterminados?" else "Restore defaults?")
            },
            text = {
                Text(
                    if (isEs) {
                        "Reescribe el catálogo (filas stock, colores de clase, campos Decode), marcadores stock, presets stock y valores predeterminados de Ajustes. Las firmas personalizadas y los presets que guardaste se eliminan. Exporta firmas y Exporta ajustes primero si quieres una copia de seguridad. Esta acción no se puede deshacer."
                    } else {
                        "Rewrites the catalog (stock rows, class colors, Decode fields), stock bookmarks, " +
                            "stock filter chips, and default Settings switches. Custom signatures and chips you " +
                            "saved are wiped. Export signatures and Export settings first if you want a backup. " +
                            "This cannot be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        vm.restoreDefaults()
                    },
                ) { Text(if (isEs) "Restablecer" else "Restore") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(strings.cancel) }
            },
        )
    }
}

// FASE 4 (Bloque 4): helpers para la sección Adaptive scanning.

private fun scanProfileLabel(profile: ScanProfile, isEs: Boolean): String = when (profile) {
    ScanProfile.SAVER -> if (isEs) "Ahorro" else "Saver"
    ScanProfile.BALANCED -> if (isEs) "Equilibrado" else "Balanced"
    ScanProfile.PERFORMANCE -> if (isEs) "Rendimiento" else "Performance"
    ScanProfile.AGGRESSIVE -> if (isEs) "Agresivo (buscando)" else "Aggressive (hunting)"
}

@Composable
private fun ContextStatusCard(
    ctx: ContextState,
    effective: ScanProfile,
    mode: IntensityMode,
    isEs: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ContextRow(
            if (isEs) "Actividad" else "Activity",
            "${ctx.activity.label()} (${ctx.activityConfidence}%)",
        )
        ContextRow(if (isEs) "Lugar" else "Place", ctx.place.label())
        ContextRow(
            if (isEs) "Pantalla" else "Screen",
            if (ctx.screenOn) (if (isEs) "Encendida" else "On")
            else (if (isEs) "Apagada" else "Off"),
        )
        ContextRow(
            if (isEs) "Batería" else "Battery",
            "${ctx.batteryLevel}%" + if (ctx.batteryCharging) (if (isEs) "  ·  cargando" else "  ·  charging") else "",
        )
        ContextRow(
            if (isEs) "Perfil efectivo" else "Effective profile",
            scanProfileLabel(effective, isEs) +
                if (mode == IntensityMode.MANUAL) (if (isEs) "  ·  Manual" else "  ·  Manual")
                else (if (isEs) "  ·  Adaptativo" else "  ·  Adaptive"),
        )
    }
}

@Composable
private fun ContextRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CreditFooter() {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Copyright (c) 2026 Off Grid Pete LLC. All rights reserved.",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialChip(
                icon = R.drawable.ic_instagram,
                label = "@OffGridPete",
                tint = muted,
                onClick = { openUrl(context, "https://instagram.com/OffGridPete") },
            )
            SocialChip(
                icon = R.drawable.ic_x,
                label = "@OGridPete",
                tint = muted,
                onClick = { openUrl(context, "https://x.com/OGridPete") },
            )
        }
    }
}

@Composable
private fun SocialChip(
    icon: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TakFeedSettings(
    settings: AppSettings,
    vm: FieldwatchViewModel,
    status: TakFeedStatus,
    isEs: Boolean,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var hostText by remember { mutableStateOf(settings.takHost) }
    var portText by remember { mutableStateOf(settings.takPort.toString()) }
    LaunchedEffect(settings.takHost) { hostText = settings.takHost }
    LaunchedEffect(settings.takPort) { portText = settings.takPort.toString() }
    val preset = TakPublish.udpPreset(settings.takHost, settings.takPort)
    Text(
        if (isEs) "Destino" else "Destination",
        style = MaterialTheme.typography.labelLarge,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.THIS_PHONE,
            onClick = {
                val (host, port) = TakPublish.applyPreset(TakUdpPreset.THIS_PHONE)
                vm.updateSettings { it.copy(takHost = host, takPort = port) }
            },
            enabled = !settings.demoMode,
            label = { Text(if (isEs) "Este teléfono" else "This phone") },
        )
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.LAN_MULTICAST,
            onClick = {
                val (host, port) = TakPublish.applyPreset(TakUdpPreset.LAN_MULTICAST)
                vm.updateSettings { it.copy(takHost = host, takPort = port) }
            },
            enabled = !settings.demoMode,
            label = { Text(if (isEs) "Multicast LAN" else "LAN multicast") },
        )
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.CUSTOM,
            onClick = {
                if (preset != TakUdpPreset.CUSTOM) {
                    val (host, port) = TakPublish.applyPreset(TakUdpPreset.CUSTOM)
                    vm.updateSettings { it.copy(takHost = host, takPort = port) }
                }
            },
            enabled = !settings.demoMode,
            label = { Text(if (isEs) "Personalizado" else "Custom") },
        )
    }
    Text(
        if (isEs) {
            "Este teléfono: ${TakDefaults.LOOPBACK}:${TakDefaults.PORT} (ATAK CIV en este dispositivo). Multicast LAN: ${TakDefaults.SA_HOST}:${TakDefaults.SA_PORT} (otros ATAK en esta Wi-Fi). Personalizado: escribe una IPv4 unicast o nombre de host. Solo UDP. El TCP 8087 de un servidor TAK no es este feed. Si Este teléfono no aparece en el mapa, usa Personalizado con la IPv4 Wi-Fi de este teléfono (pie de página) y el puerto ${TakDefaults.PORT}."
        } else {
            "This phone: ${TakDefaults.LOOPBACK}:${TakDefaults.PORT} (ATAK CIV on this handset). " +
                "LAN multicast: ${TakDefaults.SA_HOST}:${TakDefaults.SA_PORT} (other ATAKs on this Wi-Fi). " +
                "Custom: type a unicast IPv4 or hostname. UDP only. A TAK server’s TCP 8087 is not this feed. " +
                "If This phone does not plot, use Custom with this phone’s Wi-Fi IPv4 from the footer and port ${TakDefaults.PORT}."
        },
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    FieldwatchOutlinedField(
        value = hostText,
        onValueChange = { value ->
            hostText = value
            val trimmed = value.trim()
            if (trimmed.isNotEmpty()) {
                vm.updateSettings { it.copy(takHost = trimmed) }
            }
        },
        label = if (isEs) "Host" else "Host",
        placeholder = TakDefaults.HOST,
        enabled = !settings.demoMode,
    )
    FieldwatchOutlinedField(
        value = portText,
        onValueChange = { value ->
            val filtered = value.filter { it.isDigit() }.take(5)
            portText = filtered
            filtered.toIntOrNull()?.let { n ->
                if (n in 1..65_535) {
                    vm.updateSettings { it.copy(takPort = n) }
                }
            }
        },
        label = if (isEs) "Puerto" else "Port",
        placeholder = TakDefaults.PORT.toString(),
        supportingText = if (isEs) {
            "UDP. ATAK CIV ${TakDefaults.PORT}. Multicast SA ${TakDefaults.SA_PORT}. No TCP 8087."
        } else {
            "UDP. ATAK CIV ${TakDefaults.PORT}. SA multicast ${TakDefaults.SA_PORT}. Not TCP 8087."
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !settings.demoMode,
    )
    Text(takStatusLine(status, isEs), style = MaterialTheme.typography.bodySmall, color = muted)
    Text(
        if (isEs) "Qué enviar" else "What to send",
        style = MaterialTheme.typography.labelLarge,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchFilterChip(
            selected = settings.takAttention,
            onClick = { vm.updateSettings { it.copy(takAttention = !it.takAttention) } },
            enabled = !settings.demoMode,
            label = { Text(if (isEs) "Atención especial" else "Extra attention") },
        )
        FieldwatchFilterChip(
            selected = settings.takPayloadFix,
            onClick = { vm.updateSettings { it.copy(takPayloadFix = !it.takPayloadFix) } },
            enabled = !settings.demoMode,
            label = { Text(if (isEs) "Ubicación del payload" else "Payload location") },
        )
        FieldwatchFilterChip(
            selected = settings.takWatchlist,
            onClick = { vm.updateSettings { it.copy(takWatchlist = !it.takWatchlist) } },
            enabled = !settings.demoMode,
            label = { Text(if (isEs) "Lista de vigilancia" else "Watchlist") },
        )
        FieldwatchFilterChip(
            selected = settings.takAllSignatures,
            onClick = { vm.updateSettings { it.copy(takAllSignatures = !it.takAllSignatures) } },
            enabled = !settings.demoMode,
            label = { Text(if (isEs) "Todas las firmas" else "All signatures") },
        )
    }
    Text(
        if (isEs) {
            "Chips independientes. Atención especial (activado): cámaras corporales, gafas, wearables que graban, pentest, AP de seguridad pública. Ubicación del payload (activado): lat/lon publicados desde un mapa de decodificación — necesario para Remote ID stock, que no tiene marca de atención especial. Lista de vigilancia (desactivado): firmas marcadas y radios con nombre con Alerta activada. Todas las firmas (desactivado): cada radio etiquetada — ruidoso en una plaza. Las radios sin coincidencia nunca se envían. Un pin necesita coordenadas igualmente: payload publicado, o etiquetado GPS con fix en vivo. Escuchado aquí guarda la detección más fuerte, no la última, y los distintivos terminan en (here). Remote ID mantiene un marcador de aeronave (UAS ID) más un pin de piloto cuando esa ubicación se decodifica."
        } else {
            "Independent chips. Extra attention (on): body-cam, glasses, recording wearables, pentest, public-safety APs. " +
                "Payload location (on): advertised lat/lon from a decode map — required for stock Remote ID, which has no Extra attention mark. " +
                "Watchlist (off): bookmarked signatures and named radios with Alert on. " +
                "All signatures (off): every labeled radio — noisy in a plaza. Unmatched radios never go. " +
                "A pin still needs coordinates: advertised payload, or GPS tagging with a live fix. " +
                "Heard-here holds the loudest hear, not the last, and callsigns end in (here). " +
                "Remote ID keeps one aircraft marker (UAS ID) plus a pilot pin when that location decoded."
        },
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
}

private fun takStatusLine(status: TakFeedStatus, isEs: Boolean): String {
    if (status.paused) return if (isEs) "Estado del feed  ·  pausado (modo de privacidad)" else "Feed status  ·  paused (Privacy mode)"
    if (status.error != null) {
        val whenAt = takStatusWhen(status.at)
        return (if (isEs) "Estado del feed  ·  error: ${status.error}" else "Feed status  ·  error: ${status.error}") +
            if (whenAt.isNotEmpty()) "  ·  $whenAt" else ""
    }
    if (status.at <= 0L) {
        return if (isEs) "Estado del feed  ·  sin envíos esta sesión" else "Feed status  ·  no send yet this session"
    }
    val bits = ArrayList<String>(5)
    bits += if (isEs) "en el feed ${status.onFeed}" else "on the feed ${status.onFeed}"
    bits += if (isEs) "enviadas ${status.sent}" else "sent ${status.sent}"
    if (status.gone > 0) {
        bits += if (isEs) {
            if (status.gone == 1) "1 descartada" else "${status.gone} descartadas"
        } else {
            if (status.gone == 1) "1 gone" else "${status.gone} gone"
        }
    }
    if (status.dest.isNotBlank()) bits += status.dest
    val whenAt = takStatusWhen(status.at)
    if (whenAt.isNotEmpty()) bits += whenAt
    val head = (if (isEs) "Estado del feed  ·  " else "Feed status  ·  ") + bits.joinToString("  ·  ")
    return if (status.detail.isNotBlank() && status.sent == 0 && status.gone == 0) {
        "$head  ·  ${status.detail}"
    } else {
        head
    }
}

private fun takStatusWhen(at: Long): String {
    if (at <= 0L) return ""
    return java.time.Instant.ofEpochMilli(at)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
}

private fun localIpv4Addresses(): List<String> {
    val found = LinkedHashSet<String>()
    val nifs = runCatching {
        java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
    }.getOrDefault(emptyList())
    for (nif in nifs) {
        if (!nif.isUp || nif.isLoopback) continue
        for (addr in java.util.Collections.list(nif.inetAddresses)) {
            if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                addr.hostAddress?.let { found += it }
            }
        }
    }
    return found.toList()
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

private fun isBackgroundUsageAllowed(context: Context): Boolean =
    context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted != true

private enum class BatteryAndroidGate { BACKGROUND, UNRESTRICTED }

/**
 * Fieldwatch’s per-app Battery page. Samsung keeps Allow background usage and
 * Unrestricted on this same screen. [highlightBackground] asks Settings to
 * focus the background-usage switch when the OEM supports it.
 */
private fun openAppBatteryPage(context: Context, highlightBackground: Boolean) {
    val pkgUri = Uri.fromParts("package", context.packageName, null)
    val attempts = listOf(
        Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL").apply {
            data = pkgUri
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("request_ignore_background_restriction", highlightBackground)
            if (!highlightBackground) {
                putExtra(":settings:fragment_args_key", "unrestricted_pref")
            }
        },
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = pkgUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
    for (intent in attempts) {
        if (intent.resolveActivity(context.packageManager) == null) continue
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}