#!/usr/bin/env python3
"""Dibuja los gráficos PROVISIONALES de 8K PRO (icono, banner de Android TV, logo y splash).

Sustitúyelos por los definitivos de tu diseñador manteniendo nombres y tamaños:
  res/mipmap-*/brand_icon.png          48/72/96/144/192 px (cuadrado)
  res/drawable-xhdpi/brand_banner.png  320x180 px (banner del launcher de Android TV)
  res/drawable-nodpi/brand_mark.png    512x512 px (logo dentro de la app, fondo transparente)
  res/drawable-nodpi/brand_splash.png  432x432 px (icono de la pantalla de arranque)

Uso: python3 tools/brand/render_placeholder_brand.py /ruta/a/Poppins-Bold.ttf
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app" / "src" / "main" / "res"
FONT = sys.argv[1] if len(sys.argv) > 1 else "Poppins-Bold.ttf"
TOP = (20, 24, 48)
BOTTOM = (64, 22, 92)
GOLD = (255, 196, 64)
WHITE = (255, 255, 255)


def gradient(w, h):
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3)) + (255,)
        for x in range(w):
            px[x, y] = c
    return img


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1], radius, fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def text_center(draw, cx, cy, text, size, fill):
    font = ImageFont.truetype(FONT, size)
    l, t, r, b = draw.textbbox((0, 0), text, font=font)
    draw.text((cx - (r - l) / 2 - l, cy - (b - t) / 2 - t), text, font=font, fill=fill)


def mark(size):
    s = 4  # supersampling
    img = rounded(gradient(size * s, size * s), int(size * s * 0.22))
    d = ImageDraw.Draw(img)
    text_center(d, size * s / 2, size * s * 0.42, "8K", int(size * s * 0.46), WHITE)
    text_center(d, size * s / 2, size * s * 0.76, "PRO", int(size * s * 0.2), GOLD)
    return img.resize((size, size), Image.LANCZOS)


def banner():
    w, h, s = 320, 180, 4
    img = gradient(w * s, h * s)
    d = ImageDraw.Draw(img)
    text_center(d, w * s * 0.34, h * s * 0.50, "8K", int(h * s * 0.55), WHITE)
    text_center(d, w * s * 0.75, h * s * 0.53, "PRO", int(h * s * 0.30), GOLD)
    return img.resize((w, h), Image.LANCZOS)


def splash():
    canvas = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    m = mark(240)
    canvas.paste(m, ((432 - 240) // 2, (432 - 240) // 2), m)
    return canvas


def save(img, rel):
    p = RES / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p, optimize=True)
    print(rel, img.size)


for q, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    save(mark(px), f"mipmap-{q}/brand_icon.png")
save(banner(), "drawable-xhdpi/brand_banner.png")
save(mark(512), "drawable-nodpi/brand_mark.png")
save(splash(), "drawable-nodpi/brand_splash.png")
