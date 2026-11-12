import Foundation
@preconcurrency import MandalaCore
import UIKit

// Texts of the app. The keys are Android's (strings*.xml) or those of ios/Dahlia/Strings; the catalogue
// Localizable.xcstrings is generated from both by ios/scripts/gen-strings, and ios/scripts/check-sources
// fails when a key below is missing from it. No English text belongs in the screens.
enum Texts {
    static let layers = text("controls_tab_layers")
    static let style = text("controls_style")
    static let palette = text("controls_palette")
    static let shape = text("controls_shape")
    static let lightAndColour = text("controls_light_and_colour")
    static let favourites = text("editor_favourites")
    static let addCurrent = text("editor_favourites_add")
    static let favouritesEmpty = text("editor_favourites_empty") // iOS variant: no "Open file"
    static let favouritesHint = text("editor_favourites_hint") // iOS variant: no selecting yet
    static let favouriteAdded = text("message_favourite_added")
    static let saveImage = text("editor_save_image")
    static let saveToPhotos = text("ios_save_to_photos")
    static let creatingImage = text("export_creating_image")
    static let addingToPhotos = text("export_saving_to_pictures") // iOS variant: Photos
    static let saveFailed = text("message_image_failed") // iOS variant: without the cause
    static let photosDenied = text("ios_photos_denied")
    static let openSettings = text("ios_open_settings")
    static let preview = text("editor_preview")
    static let close = text("controls_close")

    static let layersAdd = text("controls_layers_add")
    static let layersDuplicate = text("controls_layers_duplicate")
    static let delete = text("controls_delete")
    static let cancel = text("controls_cancel")
    static let edit = text("controls_edit")
    static let proNoteLayers = text("controls_pro_note_layers")
    static let blending = text("controls_blending")
    static let opacity = text("controls_opacity")
    static let position = text("controls_position")
    static let positionCentred = text("controls_position_centred")
    static let positionMoved = text("controls_position_moved")
    static let proNotePosition = text("controls_pro_note_position")
    static let across = text("controls_across")
    static let upAndDown = text("controls_up_and_down")
    static let centre = text("controls_centre")
    static let positionHiddenNote = text("controls_position_hidden_note")
    static let groupChanged = text("controls_group_changed")
    static let variation = text("menu_die_variation")
    static let variationDetail = text("controls_variation_detail")
    static let variationDescription = text("controls_variation_description")
    static let seedDetail = text("controls_seed_detail")
    static let seedDescription = text("controls_seed_description")
    static let inThisPicture = text("controls_in_this_picture")
    static let paletteKeep = text("controls_palette_keep")
    static let myPalettes = text("controls_my_palettes")
    static let paletteNew = text("controls_palette_new")
    static let paletteEdit = text("controls_palette_edit")
    static let paletteName = text("controls_palette_name")
    static let paletteFromPicture = text("controls_palette_from_picture")
    static let paletteAddColour = text("controls_palette_add_colour")
    static let paletteRemoveColour = text("controls_palette_remove_colour")
    static let paletteHint = text("controls_palette_hint")
    static let paletteSave = text("controls_palette_save")
    static let hue = text("controls_hue")
    static let saturation = text("save_saturation")
    static let brightness = text("save_brightness")
    static let tabScene = text("controls_tab_scene")
    static let tabEmblem = text("controls_tab_emblem")
    static let tabText = text("controls_tab_text")
    static let tabDraw = text("controls_tab_draw")
    static let showEffects = text("controls_show_effects")
    static let randomEffects = text("controls_random_effects")
    static let reset = text("save_reset")
    static let effectsOffNote = text("controls_effects_off_note")
    static let glowAndLight = text("glow_light")
    static let contrast = text("save_contrast")
    static let vignette = text("controls_vignette")
    static let bloom = text("controls_bloom")
    static let tone = text("controls_tone")
    static let sepia = text("controls_sepia")
    static let fade = text("controls_fade")
    static let texture = text("controls_texture")
    static let clarity = text("controls_clarity")
    static let grain = text("controls_background_grain")
    static let smoother = text("controls_smoother")
    static let sparkle = text("controls_sparkle")
    static let sparkleArrangementDetail = text("controls_sparkle_arrangement_detail")
    static let sparkleArrangementDescription = text("controls_sparkle_arrangement_description")
    static let stars = text("controls_stars")
    static let glitter = text("controls_glitter")
    static let more = text("editor_more")
    static let lightArrangementDetail = text("controls_light_arrangement_detail")
    static let lightArrangementDescription = text("controls_light_arrangement_description")
    static let highlights = text("controls_highlights")
    static let dimming = text("controls_dimming")
    static let erasure = text("controls_erasure")
    static let relief = text("controls_relief")
    static let reliefAngle = text("controls_relief_angle")
    static let chromatic = text("controls_chromatic")
    static let oldFade = text("controls_old_fade")
    static let appliesTo = text("controls_applies_to")
    static let reachMandala = text("controls_reach_mandala")
    static let background = text("controls_background")
    static let backgroundAutoNote = text("controls_background_auto_note")
    static let choosePicture = text("controls_choose_picture")
    static let strength = text("controls_strength")
    static let colour = text("controls_colour")
    static let colour1 = text("controls_colour_1")
    static let colour2 = text("controls_colour_2")
    static let radial = text("controls_radial")
    static let angle = text("controls_angle")
    static let rays = text("controls_rays")
    static let sharpness = text("controls_sharpness")
    static let grainAmount = text("controls_grain")
    static let colouredGrain = text("controls_coloured_grain")
    static let fastPreview = text("menu_fast_preview")
    static let fastPreviewDetail = text("menu_fast_preview_detail")
    static let chipPreviews = text("menu_chip_previews")
    static let chipPreviewsDetail = text("menu_chip_previews_detail")
    static let menu = text("menu_more")
    static let photos = text("ios_picture_from_photos")
    static let files = text("ios_picture_from_files")


    // ── Emblem, Text and Draw tabs, moving on the picture (WP-6) ──
    static let emblemShow = text("controls_emblem_show")
    static let emblemOffNote = text("controls_emblem_off_note")
    static let randomEmblem = text("controls_random_emblem")
    static let proNoteEmblem = text("controls_pro_note_emblem")
    static let motif = text("controls_motif")
    static let variant = text("controls_variant")
    static let petals = text("controls_petals")
    static let organic = text("controls_organic")
    static let newShape = text("controls_new_shape")
    static let branches = text("controls_branches")
    static let roots = text("controls_roots")
    static let styleAndFrame = text("controls_style_and_frame")
    static let frame = text("controls_frame")
    static let size = text("controls_size")
    static let weight = text("controls_weight")
    static let rotate = text("controls_rotate")
    static let spread = text("controls_spread")
    static let glow = text("controls_glow")
    static let textShow = text("controls_text_show")
    static let textOffNote = text("controls_text_off_note")
    static let randomTextLook = text("controls_random_text_look")
    static let proNoteText = text("controls_pro_note_text")
    static let words = text("controls_words")
    static let collection = text("controls_collection")
    static let newQuote = text("controls_new_quote")
    static let keep = text("controls_keep")
    static let kept = text("controls_kept")
    static let textField = text("controls_text_field")
    static let author = text("controls_author")
    static let showAuthor = text("controls_show_author")
    static let quotes = text("controls_quotes")
    static let quotesCollection = text("controls_quotes_collection")
    static let quotesPublicDomain = text("controls_quotes_public_domain")
    static let yourQuotes = text("controls_your_quotes")
    static let removeQuote = text("controls_remove_quote")
    static let font = text("controls_font")
    static let sizeAndPlace = text("controls_size_and_place")
    static let width = text("controls_width")
    static let tracking = text("controls_tracking")
    static let height = text("controls_height")
    static let light = text("controls_light")
    static let cloud = text("controls_cloud")
    static let cloudSize = text("controls_cloud_size")
    static let cloudColour = text("controls_cloud_colour")
    static let shadow = text("controls_shadow")
    static let shadowColour = text("controls_shadow_colour")
    static let moveOnImage = text("controls_move_on_image")
    static let done = text("controls_done")
    static let drawOnImage = text("controls_draw_on_image")
    static let drawShowLines = text("controls_draw_show_lines")
    static let drawShowSpray = text("controls_draw_show_spray")
    static let undo = text("controls_undo")
    static let clear = text("controls_clear")
    static let symmetry = text("controls_symmetry")
    static let lineColour = text("controls_line_colour")
    static let lineAndLight = text("controls_line_and_light")
    static let brushSize = text("controls_brush_size")
    static let paint = text("controls_paint")
    static let sprayColour = text("controls_spray_colour")
    static let softnessAndBlending = text("controls_softness_and_blending")
    static let softness = text("controls_softness")
    static let drawHint = text("editor_draw_hint")
    static let moveEmblemHint = text("editor_move_emblem_hint")
    static let moveTextHint = text("editor_move_text_hint")
    static let proMarkDescription = text("editor_pro_mark_description")
    static let withoutEffects = text("editor_without_effects")
    /// The Pro mark on the picture: the brand word, the same in every language (as on Android).
    static let proMark = "Pro"

    static func moveLayerHint(_ n: Int) -> String { format("editor_move_layer_hint", n) }
    static func emblemShapeSeed(_ seed: String) -> String { format("controls_emblem_shape_seed", seed) }
    static func drawTool(_ tool: DrawTool, count: Int) -> String { "\(drawTool(tool)) (\(count))" }

    static func drawTool(_ tool: DrawTool) -> String {
        switch tool {
        case .lines: text("draw_tool_lines")
        case .spray: text("draw_tool_spray")
        default: tool.name
        }
    }

    static func motif(_ m: EmblemMotif) -> String {
        switch m {
        case .om: text("motif_om")
        case .treeOfLife: text("motif_tree_of_life")
        case .flowerOfLife: text("motif_flower_of_life")
        case .lotus: text("motif_lotus")
        case .yinYang: text("motif_yin_yang")
        case .dharmaWheel: text("motif_dharma_wheel")
        case .unalome: text("motif_unalome")
        case .tripleMoon: text("motif_triple_moon")
        case .sriYantra: text("motif_sri_yantra")
        default: m.displayName
        }
    }

    static func flowerVariant(_ v: EmblemFlowerVariant) -> String {
        switch v {
        case .seed: text("flower_seed")
        case .flower: text("flower_flower")
        case .fruit: text("flower_fruit")
        case .metatron: text("flower_metatron")
        default: v.displayName
        }
    }

    static func rendering(_ r: EmblemRendering) -> String {
        switch r {
        case .line: text("rendering_line")
        case .doubleLine: text("rendering_double_line")
        case .filled: text("rendering_filled")
        case .beads: text("rendering_beads")
        default: r.displayName
        }
    }

    static func frame(_ f: EmblemFrame) -> String {
        switch f {
        case .none: text("frame_none")
        case .ring: text("frame_ring")
        case .doubleRing: text("frame_double_ring")
        case .lotus: text("frame_lotus")
        case .gates: text("frame_gates")
        case .enso: text("frame_enso")
        default: f.displayName
        }
    }

    static func glowMode(_ m: EmblemGlowMode) -> String {
        switch m {
        case .light: text("glow_light")
        case .shadow: text("glow_shadow")
        default: m.displayName
        }
    }

    static func slider(_ s: EmblemSlider) -> String {
        switch s {
        case .organic: organic
        case .branches: branches
        case .size: size
        case .weight: weight
        case .rotate: rotate
        case .offsetX: across
        case .offsetY: upAndDown
        case .colourPosition: position
        case .spread: spread
        case .glow: glow
        case .opacity: opacity
        default: s.name
        }
    }

    static func slider(_ s: TextSlider) -> String {
        switch s {
        case .size: size
        case .width: width
        case .tracking: tracking
        case .height: height
        case .glow: glow
        case .cloud: cloud
        case .cloudSize: cloudSize
        case .shadow: shadow
        case .hue, .cloudHue, .shadowHue: hue
        case .saturation, .cloudSaturation, .shadowSaturation: saturation
        case .brightness, .cloudBrightness, .shadowBrightness: brightness
        default: s.name
        }
    }

    static func slider(_ s: DrawSlider) -> String {
        switch s {
        case .lineSymmetry, .spraySymmetry: symmetry
        case .lineWeight: weight
        case .lineGlow: glow
        case .lineOpacity, .sprayOpacity: opacity
        case .brushSize: brushSize
        case .paint: paint
        case .softness: softness
        case .lineHue, .sprayHue: hue
        case .lineSaturation, .spraySaturation: saturation
        case .lineBrightness, .sprayBrightness: brightness
        default: s.name
        }
    }

    static func saveSquare(_ size: Int) -> String { format("ios_save_square", size, size) }
    static func savedToPhotos(_ size: Int) -> String { format("message_saved_to_pictures", size, size) } // iOS variant: Photos
    static func layerHide(_ n: Int) -> String { format("controls_layer_hide", n) }
    static func layerShow(_ n: Int) -> String { format("controls_layer_show", n) }
    static func sliderReset(_ label: String) -> String { format("controls_slider_reset", label) }
    static func favouriteLabel(_ styles: String) -> String { format("ios_favourite_label", styles) }
    static func percent(_ value: Int) -> String { format("editor_percent", value) }

    static func layerRandomize(_ n: Int) -> String { format("controls_layers_randomize", n) }
    static func layerMoveUp(_ n: Int) -> String { format("controls_layer_move_up", n) }
    static func layerMoveDown(_ n: Int) -> String { format("controls_layer_move_down", n) }
    static func withPro(_ label: String) -> String { format("controls_with_pro", label) }
    static func blendSummary(_ mode: LayerBlendMode, _ opacity: Double) -> String { format("controls_blend_summary", blend(mode), Int((opacity * 100).rounded())) }
    /// The blend summary cut after the mode's name, so a row can shorten the name and keep the percentage
    /// whole; the cut follows the language's own format.
    static func blendSummaryParts(_ mode: LayerBlendMode, _ opacity: Double) -> (head: String, tail: String) {
        let full = blendSummary(mode, opacity)
        guard let range = full.range(of: blend(mode)) else { return (full, "") }
        return (String(full[..<range.upperBound]), String(full[range.upperBound...]))
    }
    static func groupOpen(_ title: String) -> String { format("controls_group_open", title) }
    static func groupClose(_ title: String) -> String { format("controls_group_close", title) }
    static let groupStateOpen = text("controls_group_state_open")
    static let groupStateClosed = text("controls_group_state_closed")
    static func paletteDefaultName(_ n: Int) -> String { format("controls_palette_default_name", n) }
    static func paletteColour(_ n: Int) -> String { format("controls_palette_colour", n) }
    static func pictureFailed(_ why: String) -> String { format("message_picture_failed", why) }

    static func blend(_ mode: LayerBlendMode) -> String {
        switch mode {
        case .screen: text("blend_screen")
        case .add: text("blend_add")
        case .normal: text("blend_normal")
        case .multiply: text("blend_multiply")
        default: mode.name
        }
    }

    static func background(_ type: BaseLayerType) -> String {
        switch type {
        case .auto_: text("controls_background_auto")
        case .image: text("controls_background_picture")
        case .color: text("controls_background_colour")
        case .gradient: text("controls_background_gradient")
        case .sunburst: text("controls_background_sunburst")
        case .grain: text("controls_background_grain")
        default: type.name
        }
    }

    static func slider(_ s: BackgroundSlider, twoColours: Bool = false) -> String {
        switch s {
        case .opacity: opacity
        case .pictureStrength: strength
        case .hue, .hue2: hue
        case .saturation, .saturation2: saturation
        case .brightness, .brightness2: brightness
        case .angle: angle
        case .rays: rays
        case .sharpness: sharpness
        case .grainAmount: grainAmount
        default: s.name
        }
    }

    static func slider(_ s: EffectSlider) -> String {
        switch s {
        case .brightness: brightness
        case .contrast: contrast
        case .vignette: vignette
        case .bloom: bloom
        case .sepia: sepia
        case .fade: fade
        case .clarity: clarity
        case .grain: grain
        case .smoother: smoother
        case .stars: stars
        case .glitter: glitter
        case .highlights: highlights
        case .dimming: dimming
        case .erasure: erasure
        case .relief: relief
        case .reliefAngle: reliefAngle
        case .chromatic: chromatic
        case .oldFade: oldFade
        default: s.name
        }
    }

    static func slider(_ s: LayerSlider) -> String {
        switch s {
        case .symmetry: text("controls_symmetry")
        case .scale: text("controls_scale")
        case .complexity: text("controls_complexity")
        case .spokes: text("controls_spokes")
        case .density: text("controls_density")
        case .ripple: text("controls_ripple")
        case .glow: text("controls_glow")
        case .colourDrift: text("controls_colour_drift")
        case .painted: text("controls_painted")
        case .saturation: text("controls_saturation")
        case .brightness: text("controls_brightness")
        case .opacity: opacity
        case .offsetX: across
        case .offsetY: upAndDown
        default: s.name
        }
    }

    /// Android's short style labels (strings_labels.xml): the key is `style_` plus the Kotlin enum name in lower case.
    static func style(_ s: MandalaStyle) -> String {
        let key = "style_" + s.name.lowercased()
        let label = text(key)
        return label == key ? s.displayName : label
    }

    private static func text(_ key: String) -> String {
        String(localized: String.LocalizationValue(key))
    }

    /// A catalogue text with Android's positional placeholders (`%1$lld`, `%1$@`).
    private static func format(_ key: String, _ args: CVarArg...) -> String {
        String(format: text(key), locale: nil, arguments: args)
    }
}

enum Links {
    @MainActor static func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    }
}
