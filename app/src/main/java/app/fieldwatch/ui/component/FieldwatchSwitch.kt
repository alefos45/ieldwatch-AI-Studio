package app.fieldwatch.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.PhosphorActive
import app.fieldwatch.ui.theme.nightIf

/**
 * FASE 5 (Bloque 3): el switch visual sigue siendo compacto (40×24 con
 * escala 0.72), pero el área táctil se expande a 48×48 para cumplir el
 * mínimo Material. El toque lo captura un Box transparente encima del
 * visual, con Role.Switch para TalkBack. El Switch interno tiene
 * onCheckedChange = null y no captura eventos.
 */
@Composable
fun FieldwatchSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fill = spectreTileFill()
    val edge = spectreTileEdge()
    val active = PhosphorActive.nightIf(LocalNightMode.current)
    val callback = onCheckedChange
    Box(
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Visual: mismo tamaño y forma que antes.
        Box(
            Modifier
                .requiredSize(width = 40.dp, height = 24.dp)
                .clip(RectangleShape),
            contentAlignment = Alignment.Center,
        ) {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.scale(0.72f),
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = active,
                    checkedBorderColor = edge,
                    uncheckedTrackColor = fill,
                    uncheckedBorderColor = edge,
                    disabledCheckedTrackColor = active.copy(alpha = 0.38f),
                    disabledCheckedBorderColor = edge.copy(alpha = 0.4f),
                    disabledUncheckedTrackColor = fill.copy(alpha = 0.4f),
                    disabledUncheckedBorderColor = edge.copy(alpha = 0.4f),
                ),
            )
        }
        // Touch: cubre 48×48 sin dibujar nada.
        if (enabled && callback != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = callback,
                        indication = null,
                    ),
            )
        }
    }
}