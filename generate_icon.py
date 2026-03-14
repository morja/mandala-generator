"""
Generate a 1024x1024 app icon for a Mandala Generator app.
Uses additive blending, multi-pass gaussian blur for glow, and a cosmic color palette.
"""

import numpy as np
from PIL import Image, ImageFilter, ImageDraw
import math

SIZE = 1024
cx, cy = SIZE // 2, SIZE // 2

# --- 1. Base float32 buffer ---
buf = np.zeros((SIZE, SIZE, 3), dtype=np.float32)

# --- 2. Dark textured background (near-black deep blue/purple) ---
rng = np.random.default_rng(42)
noise = rng.uniform(0, 0.015, (SIZE, SIZE, 3)).astype(np.float32)
bg_color = np.array([0.012, 0.008, 0.025], dtype=np.float32)  # deep blue-purple
buf += bg_color + noise

# --- 3. Cosmic color palette: violet → blue → cyan → magenta ---
def palette(t):
    """t in [0,1] → RGB float color"""
    t = t % 1.0
    if t < 0.25:
        u = t / 0.25
        return np.array([0.55 + 0.1*u, 0.0 + 0.15*u, 0.85 + 0.1*u])  # violet
    elif t < 0.5:
        u = (t - 0.25) / 0.25
        return np.array([0.15 - 0.05*u, 0.25 + 0.45*u, 0.9 + 0.05*u])  # blue→cyan
    elif t < 0.75:
        u = (t - 0.5) / 0.25
        return np.array([0.1 + 0.8*u, 0.7 - 0.5*u, 0.95 - 0.45*u])   # cyan→magenta
    else:
        u = (t - 0.75) / 0.25
        return np.array([0.9 - 0.35*u, 0.2 - 0.2*u, 0.5 + 0.35*u])  # magenta→violet

# --- 4. Draw epitrochoid/spirograph curves ---
R = 400.0  # outer circle radius

# (r, d, color_offset, weight, steps) tuples
# epitrochoid: x = (R-r)*cos(t) + d*cos((R-r)/r * t)
#              y = (R-r)*sin(t) - d*sin((R-r)/r * t)
curves = [
    # r=R/3 → 3 petals
    (R/3,   R*0.28, 0.0,  1.6, 60000),
    # r=R/4 → 4 petals
    (R/4,   R*0.22, 0.12, 1.4, 80000),
    # r=R/5 → 5 petals
    (R/5,   R*0.18, 0.24, 1.2, 100000),
    # r=R/6 → 6 petals
    (R/6,   R*0.16, 0.36, 1.3, 120000),
    # r=R/7 → 7 petals
    (R/7,   R*0.14, 0.48, 1.1, 140000),
    # r=R/8 → 8 petals
    (R/8,   R*0.12, 0.6,  1.0, 160000),
    # r=R/9 → 9 petals
    (R/9,   R*0.10, 0.72, 1.0, 180000),
    # r=R/10 → 10 petals
    (R/10,  R*0.09, 0.84, 0.9, 200000),
    # r=R/11 → 11 petals
    (R/11,  R*0.08, 0.93, 0.85, 220000),
    # r=R/12 → 12 petals
    (R/12,  R*0.07, 0.05, 0.8, 240000),
    # inner dense ring - r=R/5, small d
    (R/5,   R*0.05, 0.3,  1.8, 100000),
    # very inner tight pattern
    (R/7,   R*0.04, 0.55, 1.5, 140000),
]

print("Drawing epitrochoid curves...")
for idx, (r, d, col_off, weight, steps) in enumerate(curves):
    ratio = (R - r) / r
    # Full period for epitrochoid: LCM-based, approximate with 2π * denominator
    denom = round(R / r)
    period = 2 * math.pi * denom

    t_vals = np.linspace(0, period, steps, dtype=np.float64)
    px = (R - r) * np.cos(t_vals) + d * np.cos(ratio * t_vals)
    py = (R - r) * np.sin(t_vals) - d * np.sin(ratio * t_vals)

    # Scale to fill ~85% of frame
    scale = (SIZE * 0.425) / R
    px = px * scale + cx
    py = py * scale + cy

    # Parameterize color along curve by distance from center
    dist = np.sqrt(px**2 + py**2 - 2*px*cx - 2*py*cy + cx**2 + cy**2)
    max_dist = R * scale
    color_t = (dist / max_dist + col_off) % 1.0

    # Convert to pixel indices
    xi = np.round(px).astype(np.int32)
    yi = np.round(py).astype(np.int32)

    mask = (xi >= 0) & (xi < SIZE) & (yi >= 0) & (yi < SIZE)
    xi, yi, color_t = xi[mask], yi[mask], color_t[mask]

    # Batch color assignment
    colors = np.array([palette(ct) for ct in color_t[::max(1, len(color_t)//5000)]])
    # Use vectorized approach for speed
    for i in range(0, len(xi), max(1, len(xi)//5000)):
        end = min(i + max(1, len(xi)//5000), len(xi))
        chunk_xi = xi[i:end]
        chunk_yi = yi[i:end]
        chunk_ct = color_t[i:end]
        for j in range(len(chunk_xi)):
            c = palette(chunk_ct[j])
            buf[chunk_yi[j], chunk_xi[j]] += c * weight * 0.003

    print(f"  Curve {idx+1}/{len(curves)} done")

# --- 5. Radial "grass" fiber lines for organic texture ---
print("Drawing fiber lines...")
fiber_count = 15000
angles = rng.uniform(0, 2*math.pi, fiber_count)
radii_start = rng.uniform(0.05, 0.9, fiber_count) * (SIZE * 0.425)
fiber_len = rng.uniform(3, 18, fiber_count)
fiber_brightness = rng.uniform(0.3, 1.2, fiber_count)

for i in range(fiber_count):
    angle = angles[i]
    r_s = radii_start[i]
    r_e = r_s + fiber_len[i]
    x0 = cx + r_s * math.cos(angle)
    y0 = cy + r_s * math.sin(angle)
    x1 = cx + r_e * math.cos(angle)
    y1 = cy + r_e * math.sin(angle)

    # Color based on radius
    col_t = (r_s / (SIZE * 0.425) + angle / (2*math.pi)) % 1.0
    color = palette(col_t) * fiber_brightness[i] * 0.25

    steps = max(2, int(fiber_len[i]))
    for s in range(steps):
        fx = x0 + (x1 - x0) * s / steps
        fy = y0 + (y1 - y0) * s / steps
        xi, yi = int(round(fx)), int(round(fy))
        if 0 <= xi < SIZE and 0 <= yi < SIZE:
            buf[yi, xi] += color

# --- 6. Multi-pass Gaussian blur for neon glow ---
print("Applying glow effect...")
img_linear = Image.fromarray(np.clip(buf, 0, None).astype(np.float32).view(np.uint8).reshape(SIZE, SIZE, 12)[:,:,:3] if False else (np.clip(buf * 255, 0, 255).astype(np.uint8)), mode='RGB')

# Work in float for blending
blur_passes = [(3, 1.0), (10, 0.5), (25, 0.2)]
buf_pil = Image.fromarray(np.clip(buf * 255, 0, 255).astype(np.uint8), mode='RGB')

glow_accum = np.zeros_like(buf)
for radius, strength in blur_passes:
    blurred = buf_pil.filter(ImageFilter.GaussianBlur(radius=radius))
    glow_accum += np.array(blurred, dtype=np.float32) / 255.0 * strength

buf = buf + glow_accum

# --- 7. Subtle radial vignette ---
print("Applying vignette...")
yy, xx = np.mgrid[0:SIZE, 0:SIZE]
dist_from_center = np.sqrt((xx - cx)**2 + (yy - cy)**2) / (SIZE * 0.5)
vignette = np.clip(1.0 - dist_from_center * 0.35, 0.65, 1.0).astype(np.float32)
buf *= vignette[:, :, np.newaxis]

# --- 8. Tone mapping: Reinhard x/(x+1)*255 ---
print("Tone mapping...")
buf_tm = buf / (buf + 1.0) * 255.0
buf_tm = np.clip(buf_tm, 0, 255).astype(np.uint8)

# --- 9. Rounded corners mask (radius=180) ---
print("Applying rounded corners...")
CORNER_RADIUS = 180

# Create RGBA image
img_rgba = Image.fromarray(buf_tm, mode='RGB').convert('RGBA')

# Create mask with rounded rectangle
mask = Image.new('L', (SIZE, SIZE), 0)
draw = ImageDraw.Draw(mask)
draw.rounded_rectangle([0, 0, SIZE-1, SIZE-1], radius=CORNER_RADIUS, fill=255)

# Apply mask
img_rgba.putalpha(mask)

# --- 10. Save ---
out_path = '/Users/mathis/Pictures/images/ai-icons/MandalaGenerator/icon_1024.png'
img_rgba.save(out_path)
print(f"Saved: {out_path}")
