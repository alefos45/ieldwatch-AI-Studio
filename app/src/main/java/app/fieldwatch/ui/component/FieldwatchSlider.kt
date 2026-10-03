package app.fieldwatch.ui.component

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.fieldwatch.ui.i18n.LocalAppStrings
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.PhosphorActive
import app.fieldwatch.ui.theme.nightIf

/**
 * FASE 5 (Bloque 6): slider con `stateDescription` para TalkBack. Sin esto,
 * el lector anuncia "slider" sin el valor. El operador con TalkBack no oye
 * el RSSI mínimo, la ventana de promedio, el tamaño de rotación, etc.
 *
 * [label] es opcional. Si no se pasa, TalkBack lee solo el valor.
 */
@Composable
fun FieldwatchSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    label: String? = null,
    stateFormatter: ((Float) -> String)? = null,
) {
    val strings = LocalAppStrings.current
    val stateText = stateFormatter?.invoke(value) ?: value.toString()
    val stateLabel = label
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics {
            if (stateLabel != null) {
                contentDescription = stateLabel
            }
            stateDescription = stateText
        },
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = PhosphorActive.nightIf(LocalNightMode.current),
            activeTrackColor = PhosphorActive.nightIf(LocalNightMode.current),
            activeTickColor = PhosphorActive.nightIf(LocalNightMode.current),
        ),
    )
}