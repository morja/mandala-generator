package com.mathis.mandala.app.ui

import androidx.annotation.StringRes
import com.mathis.mandala.app.R
import com.mathis.mandala.core.MandalaStyle
import com.mathis.mandala.core.LayerBlendMode
import com.mathis.mandala.core.EmblemMotif
import com.mathis.mandala.core.EmblemFlowerVariant
import com.mathis.mandala.core.EmblemRendering
import com.mathis.mandala.core.EmblemFrame
import com.mathis.mandala.core.EmblemGlowMode
import com.mathis.mandala.core.ProFeature

// The localized names of core enums; core keeps the macOS names for files and the JVM tool (ADR-0019).

@get:StringRes
val MandalaStyle.labelRes: Int
    get() = when (this) {
        MandalaStyle.SPIROGRAPH -> R.string.style_spirograph
        MandalaStyle.ROSE_CURVES -> R.string.style_rose_curves
        MandalaStyle.FLORAL -> R.string.style_floral
        MandalaStyle.BUTTERFLY -> R.string.style_butterfly
        MandalaStyle.EPITROCHOID -> R.string.style_epitrochoid
        MandalaStyle.LISSAJOUS -> R.string.style_lissajous
        MandalaStyle.HYPOCYCLOID -> R.string.style_hypocycloid
        MandalaStyle.PHYLLOTAXIS -> R.string.style_phyllotaxis
        MandalaStyle.FRACTAL -> R.string.style_fractal
        MandalaStyle.SUPERFORMULA -> R.string.style_superformula
        MandalaStyle.WAVE_INTERFERENCE -> R.string.style_wave_interference
        MandalaStyle.GEOMETRIC -> R.string.style_geometric
        MandalaStyle.SACRED_GEOMETRY -> R.string.style_sacred_geometry
        MandalaStyle.SUNBURST -> R.string.style_sunburst
        MandalaStyle.STAR_BURST -> R.string.style_star_burst
        MandalaStyle.STRING_ART -> R.string.style_string_art
        MandalaStyle.SPIDER_WEB -> R.string.style_spider_web
        MandalaStyle.WEAVE -> R.string.style_weave
        MandalaStyle.RADIAL_MESH -> R.string.style_radial_mesh
        MandalaStyle.MOIRE -> R.string.style_moire
        MandalaStyle.FLOW_FIELD -> R.string.style_flow_field
        MandalaStyle.TENDRIL -> R.string.style_tendril
        MandalaStyle.VORONOI -> R.string.style_voronoi
        MandalaStyle.STRANGE_ATTRACTOR -> R.string.style_strange_attractor
        MandalaStyle.CONSTELLATION_WEB -> R.string.style_constellation_web
        MandalaStyle.DEEP_NEBULA -> R.string.style_deep_nebula
        MandalaStyle.INTERFERENCE_BLOOM -> R.string.style_interference_bloom
        MandalaStyle.HYPERBOLOID -> R.string.style_hyperboloid
        MandalaStyle.TORUS -> R.string.style_torus
        MandalaStyle.NAUTILUS -> R.string.style_nautilus
        MandalaStyle.SPHERE_GRID -> R.string.style_sphere_grid
        MandalaStyle.TORUS_KNOT -> R.string.style_torus_knot
        MandalaStyle.TESSERACT -> R.string.style_tesseract
        MandalaStyle.UNIVERSE -> R.string.style_universe
        MandalaStyle.SYMBOLS -> R.string.style_symbols
        MandalaStyle.MIXED -> R.string.style_mixed
    }

@get:StringRes
val LayerBlendMode.labelRes: Int
    get() = when (this) {
        LayerBlendMode.SCREEN -> R.string.blend_screen
        LayerBlendMode.ADD -> R.string.blend_add
        LayerBlendMode.NORMAL -> R.string.blend_normal
        LayerBlendMode.MULTIPLY -> R.string.blend_multiply
    }

@get:StringRes
val EmblemMotif.labelRes: Int
    get() = when (this) {
        EmblemMotif.OM -> R.string.motif_om
        EmblemMotif.TREE_OF_LIFE -> R.string.motif_tree_of_life
        EmblemMotif.FLOWER_OF_LIFE -> R.string.motif_flower_of_life
        EmblemMotif.LOTUS -> R.string.motif_lotus
        EmblemMotif.YIN_YANG -> R.string.motif_yin_yang
        EmblemMotif.DHARMA_WHEEL -> R.string.motif_dharma_wheel
        EmblemMotif.UNALOME -> R.string.motif_unalome
        EmblemMotif.TRIPLE_MOON -> R.string.motif_triple_moon
        EmblemMotif.SRI_YANTRA -> R.string.motif_sri_yantra
    }

@get:StringRes
val EmblemFlowerVariant.labelRes: Int
    get() = when (this) {
        EmblemFlowerVariant.SEED -> R.string.flower_seed
        EmblemFlowerVariant.FLOWER -> R.string.flower_flower
        EmblemFlowerVariant.FRUIT -> R.string.flower_fruit
        EmblemFlowerVariant.METATRON -> R.string.flower_metatron
    }

@get:StringRes
val EmblemRendering.labelRes: Int
    get() = when (this) {
        EmblemRendering.LINE -> R.string.rendering_line
        EmblemRendering.DOUBLE_LINE -> R.string.rendering_double_line
        EmblemRendering.FILLED -> R.string.rendering_filled
        EmblemRendering.BEADS -> R.string.rendering_beads
    }

@get:StringRes
val EmblemFrame.labelRes: Int
    get() = when (this) {
        EmblemFrame.NONE -> R.string.frame_none
        EmblemFrame.RING -> R.string.frame_ring
        EmblemFrame.DOUBLE_RING -> R.string.frame_double_ring
        EmblemFrame.LOTUS -> R.string.frame_lotus
        EmblemFrame.GATES -> R.string.frame_gates
        EmblemFrame.ENSO -> R.string.frame_enso
    }

@get:StringRes
val EmblemGlowMode.labelRes: Int
    get() = when (this) {
        EmblemGlowMode.LIGHT -> R.string.glow_light
        EmblemGlowMode.SHADOW -> R.string.glow_shadow
    }

@get:StringRes
val ProFeature.labelRes: Int
    get() = when (this) {
        ProFeature.MORE_LAYERS -> R.string.pro_feature_more_layers
        ProFeature.EMBLEM -> R.string.pro_feature_emblem
        ProFeature.TEXT -> R.string.pro_feature_text
        ProFeature.MOVED_LAYERS -> R.string.pro_feature_moved_layers
    }
