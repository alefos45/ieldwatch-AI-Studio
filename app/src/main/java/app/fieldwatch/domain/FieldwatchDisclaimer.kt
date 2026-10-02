package app.fieldwatch.domain

/** Operator-facing disclaimer. First-run, Debrief, and AI Export share the same core. */
object FieldwatchDisclaimer {
    const val HOBBY =
        "This is a hobby project, provided as-is under the MIT License. Use at your own risk."

    const val HYPOTHESES =
        "Detections, pattern matches, “Moving with you” / “possible tail,” Debrief text, " +
            "and AI Export are hypotheses — not identity, not a legal finding, and not a " +
            "complete RF capture. Radios that are off, asleep, randomized, cellular-only, " +
            "or hidden by the OS will not appear."

    const val LIABILITY =
        "You are solely responsible for how you use this app and for following local law. " +
            "To the maximum extent permitted by law, Off Grid Pete LLC is not liable for " +
            "indirect, incidental, special, consequential, or punitive damages arising from its use."

    const val LOCATION =
        "GPS stamps are this phone at hear-time, not the other radio, unless a decode map " +
            "advertises its own latitude/longitude (Remote ID Location). Sharing a Debrief, " +
            "sit compare, AI Export (sit or one radio), radio-detail Share as text, or log can take that " +
            "path off the device. A TAK/CoT feed, when you turn it on, sends markers onto " +
            "the LAN; that is on the operator."

    const val ACCEPT =
        "By checking the box and continuing, you accept these terms and the MIT License."

    const val HOBBY_ES =
        "Este es un proyecto personal, proporcionado tal cual bajo la Licencia MIT. Úselo bajo su propio riesgo."

    const val HYPOTHESES_ES =
        "Las detecciones, coincidencias de patrones, 'En movimiento contigo' / 'posible seguimiento', informes de misión y exportaciones para IA son hipótesis — no constituyen una identificación legalmente vinculante ni una captura de radiofrecuencia exhaustiva. Los radios apagados, en reposo, con direcciones aleatorias o no expuestos por el sistema operativo no aparecerán."

    const val LIABILITY_ES =
        "Usted es el único responsable del uso de esta aplicación y del cumplimiento de la legislación local aplicable. En la medida máxima permitida por la ley, los autores no se hacen responsables de daños directos o indirectos derivados de su uso."

    const val LOCATION_ES =
        "Las marcas GPS corresponden a este teléfono al momento de la escucha, no al radio remoto, a menos que un mapa de decodificación transmita su propia latitud/longitud (Remote ID Location)."

    const val ACCEPT_ES =
        "Al marcar la casilla y continuar, acepta estos términos y la Licencia MIT."

    const val LICENSE_TITLE = "MIT License"

    /** Body of LICENSE in the repository, without the title line. */
    const val LICENSE_BODY =
        "Copyright (c) 2026 Off Grid Pete LLC\n" +
            "\n" +
            "Permission is hereby granted, free of charge, to any person obtaining a copy " +
            "of this software and associated documentation files (the \"Software\"), to deal " +
            "in the Software without restriction, including without limitation the rights " +
            "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell " +
            "copies of the Software, and to permit persons to whom the Software is " +
            "furnished to do so, subject to the following conditions:\n" +
            "\n" +
            "The above copyright notice and this permission notice shall be included in all " +
            "copies or substantial portions of the Software.\n" +
            "\n" +
            "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR " +
            "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, " +
            "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE " +
            "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER " +
            "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, " +
            "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE " +
            "SOFTWARE."

    val LICENSE_TEXT = "$LICENSE_TITLE\n\n$LICENSE_BODY"

    val firstRunDisclaimer: String = "$HOBBY\n\n$HYPOTHESES\n\n$LIABILITY"

    fun firstRunDisclaimer(isEs: Boolean): String =
        if (isEs) "$HOBBY_ES\n\n$HYPOTHESES_ES\n\n$LIABILITY_ES"
        else firstRunDisclaimer

    fun acceptText(isEs: Boolean): String =
        if (isEs) ACCEPT_ES else ACCEPT

    val firstRun: String =
        "$firstRunDisclaimer\n\n$LICENSE_TEXT\n\n$ACCEPT"

    /** Debrief text / PDF. Same core as first-run, without the click-through line. */
    fun report(window: DebriefWindow? = null): String {
        val source = if (window?.sitName != null) {
            "named sit “${window.sitName}” (radios heard in that window; Live list cap still applied while watching). "
        } else {
            "the in-memory live set (last 15 minutes, cap about 400). "
        }
        return "$HOBBY\n\n$HYPOTHESES\n\n$LIABILITY\n\n$LOCATION\n\n" +
            "This sit report is from $source" +
            "Do not use it in any situation where safety is in question."
    }

    fun compare(): String =
        "$HOBBY\n\n$HYPOTHESES\n\n$LIABILITY\n\n$LOCATION\n\n" +
            "This compare is two windows of radios this phone heard (kind + MAC). " +
            "BLE rotation is a new row. Do not use it in any situation where safety is in question."

    fun experimentalMarkdown(): String = buildString {
        appendLine("## Disclaimer (repeat this in your answer)")
        appendLine(HOBBY)
        appendLine(HYPOTHESES)
        appendLine(LIABILITY)
        appendLine(LOCATION)
        appendLine("Do not use Fieldwatch, this paste, or your analysis in any situation where safety is in question.")
        appendLine("Begin your reply with this disclaimer. Do not give safety advice.")
    }
}
