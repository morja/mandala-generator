package com.mathis.mandala.core

/** One gradient stop. `color` holds Display P3 components, like the macOS palettes after conversion. */
data class PaletteStop(val position: Double, val color: Rgb)

class ColorPalette(val id: String, val name: String, val stops: List<PaletteStop>) {

    /** Writes the P3 colour at `t` (clamped to [0, 1]) into `out[0..2]`. */
    fun colorAt(t: Double, out: FloatArray) {
        if (stops.size < 2) {
            val c = stops.firstOrNull()?.color ?: Rgb(1.0, 1.0, 1.0)
            out[0] = c.r.toFloat(); out[1] = c.g.toFloat(); out[2] = c.b.toFloat()
            return
        }
        val clamped = t.coerceIn(0.0, 1.0)
        var lower = stops[0]
        var upper = stops[stops.size - 1]
        for (i in 0 until stops.size - 1) {
            if (clamped >= stops[i].position && clamped <= stops[i + 1].position) {
                lower = stops[i]
                upper = stops[i + 1]
                break
            }
        }
        val range = upper.position - lower.position
        val localT = if (range > 0) (clamped - lower.position) / range else 0.0
        val a = lower.color
        val b = upper.color
        out[0] = (a.r + (b.r - a.r) * localT).toFloat()
        out[1] = (a.g + (b.g - a.g) * localT).toFloat()
        out[2] = (a.b + (b.b - a.b) * localT).toFloat()
    }
}
