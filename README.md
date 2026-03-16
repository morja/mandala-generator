# Mandala Generator

A native macOS app for generating neon light-painting mandala images. Every parameter is controllable in real time, with instant auto-generation on change.

![Mandala Generator](sample-image.jpg)

![Mandala Generator](samples/sample-image-2.png)
![Mandala Generator](samples/sample-image-3.png)
![Mandala Generator](samples/sample-image-4.png)
![Mandala Generator](samples/sample-image-5.png)
![Mandala Generator](samples/sample-image-6.png)
![Mandala Generator](samples/sample-image-7.png)
![Mandala Generator](samples/sample-image-8.png)
![Mandala Generator](samples/sample-image-9.png)
![Mandala Generator](samples/sample-image-10.png)

## Features

- **25 drawing styles** — Spirograph, Rose Curves, String Art, Sunburst, Epitrochoid, Floral, Lissajous, Butterfly, Geometric, Fractal, Phyllotaxis, Hypocycloid, Wave Interference, Spider Web, Weave, Sacred Geometry, Radial Mesh, Flow Field, Tendril, Moiré, Voronoi, Torus Knot, Sphere Grid, Tesseract, Mixed
- **3D styles** — Torus Knot (full tube surface with Frenet frame), Sphere Grid (tilted great circles + pole spirals), Tesseract (4D hypercube with nested shells) — all with perspective depth shading, symmetry, and ripple
- **Multi-layer compositing** — stack up to 5 independent layers, each with its own style, palette, blend mode, and settings
- **Per-layer controls** — symmetry, seed, scale, complexity, density, glow, colour drift, ripple, wash, abstract level, saturation, brightness, rotation, opacity, blend mode
- **Layer header actions** — dice (randomize), duplicate, copy/paste via context menu; drag handle for reordering
- **Layer mini-previews** — each layer card shows a live thumbnail that updates after every render
- **Drag-to-reorder layers** — drag handle on each layer card; sticky "Add Layer" button always visible at the top
- **Background layer** — solid colour, gradient (radial or linear), pattern (checkerboard, stripes, diagonal, crosshatch), grain, image, or **Auto** (palette-derived dark ambient gradient)
- **Hue colour sliders** — background hue controls show a rainbow gradient track so you can see what colour you're picking
- **Effects layer** — brightness, contrast, vignette, chromatic aberration, 3D relief, dimming, erasure, highlights, star sparkles — each spatial effect with independent seed dice button
- **18 colour palettes** — Aurora, Nebula, Neon City, Sunset, Prism, Bioluminescence, Blue/Red, Synthwave, Lava, Gold, Toxic, and more
- **Custom palette editor** — create, name, and save your own palettes with a gradient stop editor (HSB sliders per stop); custom palettes are marked with a star and persist across sessions
- **Persistent history** — back/forward navigation (⌘[ / ⌘]) through every render, survives app restarts
- **Randomize All** (⌘⇧R) — generates a completely new random mandala; app starts with a random mandala on first launch
- **Save/Load settings** — save full parameter state as JSON (⌘S), load it back (⌘O); backward-compatible with older saves
- **Export** — PNG, JPG, or WebP at 512 / 800 / 1024 / 1400 / 2048 px or a custom size, with square / circle / squircle / rounded output shapes (PNG and WebP support transparency)
- **Batch export** (⌘⇧E) — render many seed/palette variations in parallel to a folder
- **Animated export** (⌘⌥E) — export a looping MOV (HEVC) or GIF with rotating layers; configurable frame count, FPS, and format; non-blocking with a live progress bar; background expanded so corners never appear during rotation
- **Pan & zoom** — scroll to zoom, drag to pan the canvas preview
- **Experimental drawing layer** — draw symmetrical shapes directly on the canvas with mouse/trackpad (enable via Experimental menu)

## Building

Requires Xcode command-line tools and Swift 5.9+.

```bash
swift build -c release
./build_app.sh
open "Mandala Generator.app"
```

### WebP Support

WebP export is not guaranteed to be available via macOS system frameworks (ImageIO WebP *read* support does not necessarily imply WebP *write* support).

To ensure reliable WebP export, install the `cwebp` tool via Homebrew:

```bash
brew install webp
```

The app detects WebP export support at runtime and only shows the WebP option when it can actually export WebP (either via ImageIO encoding support when available, or via `cwebp`). If WebP was previously selected but isn't supported on the current system, it automatically falls back to PNG.

## Interface

```
┌──────────────────────────────────────────────────────────────────────┐
│  Generate   Randomize All   Save Settings   Load   ←  →              │  ← toolbar
├────────────────┬───────────────────────────┬─────────────────────────┤
│  BACKGROUND    │                           │  LAYERS           [ + ] │  ← sticky
│  ┌──────────┐  │                           │  ⠿ ○ [img] Name  🎲 ⌄ × │
│  │ Base     │  │         Canvas            │  ┌───────────────────┐  │
│  │ Layer    │  │     (pan + zoom)          │  │  style / blend    │  │
│  └──────────┘  │                           │  │  palette grid     │  │
│  EFFECTS       │                           │  │  sliders…         │  │
│  ┌──────────┐  │                           │  └───────────────────┘  │
│  │ Effects  │  │                           │  ⠿ ○ [img] Name  🎲 ⌄ × │
│  └──────────┘  │                           │  └───────────────────┘  │
│  EXPORT        │                           │                         │
│  ┌──────────┐  │                           │                         │
│  │ Size/Fmt │  │                           │                         │
│  │ Save Img │  │                           │                         │
│  │ Animate  │  │                           │                         │
│  └──────────┘  │                           │                         │
└────────────────┴───────────────────────────┴─────────────────────────┘
```

### Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| ⌘R | Generate / re-render |
| ⌘⇧R | Randomize all |
| ⌘S | Save settings |
| ⌘O | Load settings |
| ⌘⇧S | Save image |
| ⌘⇧E | Batch export |
| ⌘⌥E | Export animation |
| ⌘[ / ⌘] | Navigate history back / forward |

### Background Panel (left)

Controls a dedicated background layer rendered before the mandala layers.

| Setting | Description |
|---|---|
| **Type** | Solid colour, gradient, pattern, grain, image, or Auto |
| **Auto** | Derives a dark ambient background from the first layer's palette |
| **Primary / Secondary** | Hue (colour gradient slider), saturation, brightness |
| **Gradient style** | Radial or linear (with angle control) |
| **Pattern type** | Checkerboard, stripes, diagonal, or crosshatch |
| **Opacity** | Overall background opacity |

### Effects Panel (left, below Background)

Post-processing applied to the final composite. Reset button restores all to defaults.

| Effect | Description |
|---|---|
| **Brightness** | Global brightness (0.5 = neutral) |
| **Contrast** | Global contrast (0.5 = neutral) |
| **Vignette** | Darkens the edges |
| **Chromatic** | RGB channel separation (chromatic aberration) |
| **Relief** | 3D emboss depth — hard-light blend of a directional height map |
| **Light** | Relief light direction (0–360°, shown when Relief > 0) |
| **Dimming** | Random dark blotches (multiply blend) |
| **Erasure** | Burn-through holes in the image |
| **Highlights** | Additive glowing radial spots |
| **Stars** | Sharp cross-flare diffraction spike sparkles |

Each spatially-random effect has a dice button to reshuffle its position independently.

### Export Card (left, below Effects)

| Control | Description |
|---|---|
| **Size** | 512 / 800 / 1024 / 1400 / 2048 px, or Custom (any value 64–8192) |
| **Format** | PNG, JPG, or WebP (WebP only shown when supported) |
| **Shape** | Square, Circle, Squircle, or Rounded (PNG/WebP only — uses transparency) |
| **Save Image** (⌘⇧S) | Save the rendered image |
| **Export Animation…** (⌘⌥E) | Opens a dialog to choose format (MOV/GIF), frame count, and FPS, then exports a looping animation with a live progress bar |

### Layer Card (right)

Each layer is an independent drawing pass composited on top of previous layers. The sticky header at the top of the panel contains an **Add Layer** button.

#### Header actions

| Control | Description |
|---|---|
| **Drag handle** | Reorder layers by dragging |
| **Toggle** | Enable / disable the layer |
| **Thumbnail** | Live mini-preview, updates after each render |
| **Dice** (🎲) | Randomize all settings for this layer |
| **Duplicate** | Clone the layer with a new seed |
| **▲▼** | Collapse / expand the layer body |
| **×** | Delete the layer |
| **Right-click** | Copy / paste layer settings |

#### Layer parameters

| Parameter | Effect |
|---|---|
| **Style** | One of 25 drawing algorithms |
| **Blend Mode** | Screen (default), Add, Normal, or Multiply |
| **Symmetry** | Rotational repeat count (1–8) |
| **Seed** | RNG seed — change for a different arrangement |
| **Scale** | Radius of the pattern (0.1–1.0) |
| **Complexity** | Number / intricacy of curves |
| **Density** | Stroke weight and line count |
| **Glow** | Bloom/glow halo intensity |
| **Color Drift** | How far colours shift along the palette per curve |
| **Ripple** | Radial displacement noise |
| **Wash** | Watercolour bleed overlay |
| **Abstract** | Turbulence distortion + painted blur |
| **Saturation** | Colour vividness for this layer |
| **Brightness** | Luminance boost for this layer |
| **Rotation** | 2D rotation of the rendered layer (0–1 = 0–360°) |
| **Opacity** | Layer transparency (0–1) |

#### Palette

Each layer has its own palette grid. You can select from the 18 built-in palettes or any custom palettes you have created (marked ★). Use **New Palette** to open the palette editor, or **Edit** to modify a selected custom palette.

## Architecture

| File | Purpose |
|---|---|
| `MandalaParameters.swift` | All model structs (`StyleLayer`, `BaseLayerSettings`, `EffectsLayerSettings`, `DrawingLayerSettings`, `MandalaParameters`) — fully `Codable` with backward-compatible `decodeSafe` fallbacks |
| `MandalaRenderer.swift` | Core renderer — 25 curve styles including 3D (torus knot, sphere grid, tesseract), base/effects layers, CIFilter post-processing, layer preview thumbnails |
| `PixelBuffer.swift` | Float32 additive pixel buffer with Wu anti-aliased line drawing and tone mapping |
| `ColorPalettes.swift` | 18 built-in named colour palettes |
| `PaletteEditor.swift` | Custom palette editor — `PaletteStop`, `CustomPalette` (Codable), `PaletteEditorSheet` with gradient preview and HSB stop editor |
| `AppState.swift` | `@MainActor ObservableObject` — debounced auto-generate, persistent history, save/load, batch/animation export, custom palette persistence |
| `ContentView.swift` | Root layout (3-column HSplitView: scene panel + canvas + layers panel) |
| `CanvasView.swift` | Canvas with pan/zoom, toolbar, drawing overlay (experimental) |
| `ScenePanel.swift` | Left panel — Background, Effects, and Export cards; animation options sheet |
| `PalettePanel.swift` | Right panel — sticky add-layer header, draggable `LayerCard` views |
| `ParameterPanel.swift` | Shared UI components (`PaletteSwatch`, etc.) |

### Rendering pipeline

1. **Base layer** — solid colour / gradient / pattern / grain / image / auto (palette-derived gradient + grass fibers)
2. **Per mandala layer** — curves collected into `CurveDrawTask` structs, drawn in parallel via `DispatchQueue.concurrentPerform` into per-thread sub-buffers, merged with `vDSP_vadd`, then glow → wash → abstract → colour grade applied; 3D styles use perspective projection with depth-shaded weights
3. **Blend composite** — each layer blended onto the running composite using the chosen blend mode (Screen, Add, Normal/Lighten, or Multiply); optional 2D rotation via `CIAffineTransform`; optional opacity via `CIColorMatrix`
4. **Drawing layer** (experimental) — user strokes composited with a chosen blend mode and symmetry
5. **Effects layer** — brightness/contrast, 3D relief (hard-light emboss), vignette, chromatic aberration, dimming, erasure, highlights, stars applied as CIFilter passes
6. **Downscale** — Lanczos downscale from 2× render buffer to output size

### Animation export

Frames are rendered in parallel using Swift's structured concurrency (`withTaskGroup` with per-frame `Task.detached` to keep the main actor free). The render canvas is expanded by √2 and each layer's scale is reduced proportionally, so after a center-crop to the target size the mandala appears identical to the static render — but the background fills all corner area that would otherwise be visible during rotation. MOV uses AVFoundation (HEVC), GIF uses `CGImageDestination`.
