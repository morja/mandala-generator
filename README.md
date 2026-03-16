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

- **22 drawing styles** — Spirograph, Rose Curves, String Art, Sunburst, Epitrochoid, Floral, Lissajous, Butterfly, Geometric, Fractal, Phyllotaxis, Hypocycloid, Wave Interference, Spider Web, Weave, Sacred Geometry, Radial Mesh, Flow Field, Tendril, Moiré, Voronoi, Mixed
- **Multi-layer compositing** — stack up to 5 independent layers, each with its own style, palette, and settings
- **Per-layer controls** — symmetry, seed, scale, complexity, density, glow, colour drift, ripple, wash, abstract level, saturation, brightness
- **Background layer** — solid colour, gradient (radial or linear), pattern (checkerboard, stripes, diagonal, crosshatch), grain, or image
- **Effects layer** — vignette, chromatic aberration, dimming, erasure, highlights, and star sparkles — each with independent seed dice buttons
- **18 colour palettes** — Aurora, Nebula, Neon City, Sunset, Prism, Bioluminescence, Dragon, Synthwave, Lava, and more
- **Randomize All** — generates a completely new random mandala in one click
- **Export** — PNG or JPG at 512 / 800 / 1024 / 1400 / 2048 px, with circle / squircle / rounded output shapes
- **Batch export** — render many variations to a folder in parallel
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
│  Generate   Randomize All   Save   800 px   PNG   ◯ ▣ ⬜         │  ← toolbar
├────────────────┬─────────────────────────┬───────────────────────┤
│  BACKGROUND    │                         │  LAYERS               │
│  ┌──────────┐  │                         │  ┌─────────────────┐  │
│  │ Base     │  │       Canvas            │  │ Layer 1         │  │
│  │ Layer    │  │   (pan + zoom)          │  │  style/palette  │  │
│  └──────────┘  │                         │  │  sliders…       │  │
│  EFFECTS       │                         │  └─────────────────┘  │
│  ┌──────────┐  │                         │  ┌─────────────────┐  │
│  │ Effects  │  │                         │  │ Layer 2         │  │
│  │ Layer    │  │                         │  └─────────────────┘  │
│  └──────────┘  │                         │                       │
└────────────────┴─────────────────────────┴───────────────────────┘
```

### Toolbar

| Control | Description |
|---|---|
| **Generate** (⌘R) | Re-render with current settings |
| **Randomize All** | Randomise all layers and settings |
| **Save** | Save the current image |
| **Size picker** | Output resolution (512–2048 px) |
| **Format picker** | PNG or JPG |

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

Post-processing applied to the final composite.

| Effect | Description |
|---|---|
| **Vignette** | Darkens the edges |
| **Chromatic** | RGB channel separation (chromatic aberration) |
| **Dimming** | Random dark blotches (multiply blend) |
| **Erasure** | Burn-through holes in the image |
| **Highlights** | Additive glowing radial spots |
| **Stars** | Sharp cross-flare sparkle points |

Each spatially-random effect has a dice button to reshuffle its position independently.

### Layer Card (right)

Each layer is an independent drawing pass composited on top of previous layers using screen blending.

| Parameter | Effect |
|---|---|
| **Style** | One of 22 drawing algorithms |
| **Symmetry** | Rotational repeat count (1–8) |
| **Seed** | RNG seed — change for a different curve arrangement |
| **Scale** | Radius of the pattern (0.1–1.0) |
| **Complexity** | Number of curves drawn |
| **Density** | Stroke weight and line count |
| **Glow** | Bloom/glow halo intensity |
| **Color Drift** | How far colours shift along the palette per curve |
| **Ripple** | Radial displacement noise |
| **Wash** | Watercolour bleed overlay |
| **Abstract** | Turbulence distortion + painted blur |
| **Saturation** | Colour vividness for this layer |
| **Brightness** | Luminance boost for this layer |

## Architecture

| File | Purpose |
|---|---|
| `MandalaParameters.swift` | `StyleLayer`, `BaseLayerSettings`, `EffectsLayerSettings`, `MandalaParameters` model structs |
| `MandalaRenderer.swift` | Core renderer — 22 curve styles, base/effects layers, CIFilter post-processing |
| `PixelBuffer.swift` | Float32 additive pixel buffer with Wu anti-aliased line drawing |
| `ColorPalettes.swift` | 18 named colour palettes |
| `AppState.swift` | `@MainActor ObservableObject` — debounced auto-generate, save, batch export |
| `ContentView.swift` | Root layout (3-column HSplitView: scene panel + canvas + layers panel) |
| `CanvasView.swift` | Canvas with pan/zoom, toolbar, context menu |
| `ScenePanel.swift` | Left panel — Background and Effects layer cards |
| `PalettePanel.swift` | Right panel — expandable `LayerCard` views |
| `ParameterPanel.swift` | Shared UI components (`PaletteSwatch`, etc.) |

### Rendering pipeline

1. **Base layer** — solid colour / gradient / pattern / grain / image (or default gradient + grass fibers)
2. **Per mandala layer** — curves collected into `CurveDrawTask` structs, drawn in parallel via `DispatchQueue.concurrentPerform` into per-thread sub-buffers, merged with `vDSP_vadd`, then glow → wash → abstract → colour grade applied
3. **Screen composite** — each layer blended onto the running composite using `CIScreenBlendMode`
4. **Effects layer** — vignette, chromatic aberration, dimming, erasure, highlights, stars applied as CIFilter passes
5. **Downscale** — Lanczos downscale from 2× render buffer to output size
