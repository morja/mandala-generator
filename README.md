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
- **Per-layer controls** — symmetry, seed, scale, complexity, density, glow, colour drift, ripple, wash, abstract level, saturation, brightness, rotation, blend mode
- **Drag-to-reorder layers** — drag handle on each layer card
- **Background layer** — solid colour, gradient (radial or linear), pattern (checkerboard, stripes, diagonal, crosshatch), grain, or image
- **Effects layer** — brightness, contrast, vignette, chromatic aberration, 3D relief, dimming, erasure, highlights, star sparkles — each spatial effect with independent seed dice button
- **18 colour palettes** — Aurora, Nebula, Neon City, Sunset, Prism, Bioluminescence, Dragon, Synthwave, Lava, Gold, Toxic, and more
- **Persistent history** — back/forward navigation (⌘[ / ⌘]) through every render, survives app restarts
- **Randomize All** — generates a completely new random mandala in one click; app also starts with a random mandala on launch
- **Save/Load settings** — save full parameter state as JSON (⌘S), load it back (⌘O)
- **Export** — PNG, JPG, or WebP at 512 / 800 / 1024 / 1400 / 2048 px, with square / circle / squircle / rounded output shapes (PNG and WebP support transparency)
- **Batch export** — render many seed variations to a folder in parallel (⌘⇧E)
- **Pan & zoom** — scroll to zoom, drag to pan the canvas preview

## Building

Requires Xcode command-line tools and Swift 5.9+.

```bash
swift build -c release
./build_app.sh
open "Mandala Generator.app"
```

## Interface

```
┌──────────────────────────────────────────────────────────────────┐
│  Generate   Randomize All   Save Settings   Load   ←  →          │  ← toolbar
├────────────────┬─────────────────────────┬───────────────────────┤
│  BACKGROUND    │                         │  LAYERS               │
│  ┌──────────┐  │                         │  ⠿ ○ Layer 1  ⌄  ×   │
│  │ Base     │  │       Canvas            │  ┌─────────────────┐  │
│  │ Layer    │  │   (pan + zoom)          │  │  style / blend  │  │
│  └──────────┘  │                         │  │  palette grid   │  │
│  EFFECTS       │                         │  │  sliders…       │  │
│  ┌──────────┐  │                         │  └─────────────────┘  │
│  │ Effects  │  │                         │  ⠿ ○ Layer 2  ⌄  ×   │
│  │ Layer    │  │                         │  └─────────────────┘  │
│  └──────────┘  │                         │                       │
│  EXPORT        │                         │                       │
│  ┌──────────┐  │                         │                       │
│  │ Size/Fmt │  │                         │                       │
│  │ Save Img │  │                         │                       │
│  └──────────┘  │                         │                       │
└────────────────┴─────────────────────────┴───────────────────────┘
```

### Toolbar

| Control | Description |
|---|---|
| **Generate** (⌘R) | Re-render with current settings |
| **Randomize All** | Randomise all layers and settings |
| **Save Settings** (⌘S) | Save all parameters as a JSON file |
| **Load Settings** (⌘O) | Restore parameters from a JSON file |
| **← / →** (⌘[ / ⌘]) | Navigate render history |

### Background Panel (left)

Controls a dedicated background layer rendered before the mandala layers.

| Setting | Description |
|---|---|
| **Type** | Solid colour, gradient, pattern, grain, or image |
| **Primary / Secondary** | HSB colour pickers for each type |
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
| **Size** | Output resolution: 512 / 800 / 1024 / 1400 / 2048 px |
| **Format** | PNG, JPG, or WebP |
| **Shape** | Square, Circle, Squircle, or Rounded (PNG/WebP only — uses transparency) |
| **Save Image** (⌘⇧S) | Save the rendered image |

### Layer Card (right)

Each layer is an independent drawing pass composited on top of previous layers.

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
| **Rotation** | 2D rotation of the rendered layer (0–360°) |

## Architecture

| File | Purpose |
|---|---|
| `MandalaParameters.swift` | `StyleLayer`, `BaseLayerSettings`, `EffectsLayerSettings`, `MandalaParameters` model structs — all `Codable` |
| `MandalaRenderer.swift` | Core renderer — 25 curve styles including 3D (torus knot, sphere grid, tesseract), base/effects layers, CIFilter post-processing |
| `PixelBuffer.swift` | Float32 additive pixel buffer with Wu anti-aliased line drawing |
| `ColorPalettes.swift` | 18 named colour palettes |
| `AppState.swift` | `@MainActor ObservableObject` — debounced auto-generate, persistent history, save/load settings, batch export |
| `ContentView.swift` | Root layout (3-column HSplitView: scene panel + canvas + layers panel) |
| `CanvasView.swift` | Canvas with pan/zoom, toolbar, context menu |
| `ScenePanel.swift` | Left panel — Background, Effects, and Export cards |
| `PalettePanel.swift` | Right panel — draggable `LayerCard` views with drag-handle reordering |
| `ParameterPanel.swift` | Shared UI components (`PaletteSwatch`, etc.) |

### Rendering pipeline

1. **Base layer** — solid colour / gradient / pattern / grain / image (or default gradient + grass fibers)
2. **Per mandala layer** — curves collected into `CurveDrawTask` structs, drawn in parallel via `DispatchQueue.concurrentPerform` into per-thread sub-buffers, merged with `vDSP_vadd`, then glow → wash → abstract → colour grade applied; 3D styles use perspective projection with depth-shaded weights
3. **Blend composite** — each layer blended onto the running composite using the chosen blend mode (Screen, Add, Normal/Lighten, or Multiply); optional 2D rotation via `CIAffineTransform`
4. **Effects layer** — brightness/contrast, 3D relief (hard-light emboss), vignette, chromatic aberration, dimming, erasure, highlights, stars applied as CIFilter passes
5. **Downscale** — Lanczos downscale from 2× render buffer to output size
