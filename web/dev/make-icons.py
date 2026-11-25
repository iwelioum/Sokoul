"""Draws the web client's icons from the app logo (app/src/main/resources/sokoul.png).

The logo is a dark rounded square on transparent corners; home screens apply their own mask, so every icon is drawn
on an opaque black square. Run from the repository root: python web/dev/make-icons.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app" / "src" / "main" / "resources" / "sokoul.png"
OUT = ROOT / "web" / "icons"

# name -> (size, share of the side the logo covers). The maskable icon keeps the logo inside the 80 % safe zone.
ICONS = {
    "apple-touch-icon.png": (180, 1.0),
    "icon-192.png": (192, 1.0),
    "icon-512.png": (512, 1.0),
    "icon-maskable-512.png": (512, 0.8),
}


def main() -> None:
    logo = Image.open(SOURCE).convert("RGBA")
    logo = logo.crop(logo.getbbox())
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (size, share) in ICONS.items():
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 255))
        side = round(size * share)
        mark = logo.resize((side, side), Image.LANCZOS)
        offset = (size - side) // 2
        canvas.alpha_composite(mark, (offset, offset))
        canvas.convert("RGB").save(OUT / name, optimize=True)
        print(f"{name} {size}x{size}")


if __name__ == "__main__":
    main()
