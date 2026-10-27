package com.mathis.mandala.app.ui

import androidx.annotation.DrawableRes
import com.mathis.mandala.app.R
import com.mathis.mandala.core.MandalaStyle

/** Stable examples from the Mac renderer, shipped as small bitmaps instead of rendered on opening. */
@DrawableRes
internal fun stylePreviewResource(style: MandalaStyle): Int = when (style) {
    MandalaStyle.SPIROGRAPH -> R.drawable.style_preview_spirograph
    // Android first: rendered by the core (cli, the Mac preview settings), until the Mac app has them.
    MandalaStyle.PETAL_RINGS -> R.drawable.style_preview_petal_rings
    MandalaStyle.LOOP_RINGS -> R.drawable.style_preview_loop_rings
    MandalaStyle.ROSE_CURVES -> R.drawable.style_preview_rose_curves
    MandalaStyle.FLORAL -> R.drawable.style_preview_floral
    MandalaStyle.BUTTERFLY -> R.drawable.style_preview_butterfly
    MandalaStyle.EPITROCHOID -> R.drawable.style_preview_epitrochoid
    MandalaStyle.LISSAJOUS -> R.drawable.style_preview_lissajous
    MandalaStyle.HYPOCYCLOID -> R.drawable.style_preview_hypocycloid
    MandalaStyle.PHYLLOTAXIS -> R.drawable.style_preview_phyllotaxis
    MandalaStyle.FRACTAL -> R.drawable.style_preview_fractal
    MandalaStyle.SUPERFORMULA -> R.drawable.style_preview_superformula
    MandalaStyle.WAVE_INTERFERENCE -> R.drawable.style_preview_wave_interference
    MandalaStyle.GEOMETRIC -> R.drawable.style_preview_geometric
    MandalaStyle.SACRED_GEOMETRY -> R.drawable.style_preview_sacred_geometry
    MandalaStyle.SUNBURST -> R.drawable.style_preview_sunburst
    MandalaStyle.STAR_BURST -> R.drawable.style_preview_star_burst
    MandalaStyle.STRING_ART -> R.drawable.style_preview_string_art
    MandalaStyle.SPIDER_WEB -> R.drawable.style_preview_spider_web
    MandalaStyle.WEAVE -> R.drawable.style_preview_weave
    MandalaStyle.RADIAL_MESH -> R.drawable.style_preview_radial_mesh
    MandalaStyle.MOIRE -> R.drawable.style_preview_moire
    MandalaStyle.FLOW_FIELD -> R.drawable.style_preview_flow_field
    MandalaStyle.TENDRIL -> R.drawable.style_preview_tendril
    MandalaStyle.VORONOI -> R.drawable.style_preview_voronoi
    MandalaStyle.STRANGE_ATTRACTOR -> R.drawable.style_preview_strange_attractor
    MandalaStyle.CONSTELLATION_WEB -> R.drawable.style_preview_constellation_web
    MandalaStyle.DEEP_NEBULA -> R.drawable.style_preview_deep_nebula
    MandalaStyle.INTERFERENCE_BLOOM -> R.drawable.style_preview_interference_bloom
    MandalaStyle.HYPERBOLOID -> R.drawable.style_preview_hyperboloid
    MandalaStyle.TORUS -> R.drawable.style_preview_torus
    MandalaStyle.NAUTILUS -> R.drawable.style_preview_nautilus
    MandalaStyle.SPHERE_GRID -> R.drawable.style_preview_sphere_grid
    MandalaStyle.TORUS_KNOT -> R.drawable.style_preview_torus_knot
    MandalaStyle.TESSERACT -> R.drawable.style_preview_tesseract
    MandalaStyle.UNIVERSE -> R.drawable.style_preview_universe
    MandalaStyle.SYMBOLS -> R.drawable.style_preview_symbols
    MandalaStyle.MIXED -> R.drawable.style_preview_mixed
}
