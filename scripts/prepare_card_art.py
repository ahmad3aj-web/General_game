from collections import deque
from pathlib import Path
from PIL import Image
import numpy as np

ROOT = Path("app/src/main/assets/ui")

def make_transparent(src_name, dst_name, min_luma=78, max_chroma=72):
    path = ROOT / src_name
    image = Image.open(path).convert("RGBA")
    pixels = np.array(image, dtype=np.uint8)
    rgb = pixels[:, :, :3].astype(np.int16)
    lo = rgb.min(axis=2)
    hi = rgb.max(axis=2)
    # The supplied cards use a pale neutral checkerboard. Include antialiased
    # edge pixels, but only remove regions connected to the image boundary.
    # Aircraft is mostly light grey/white; use a much stricter edge threshold
    # so the flood fill cannot leak through the plane and erase its body.
    neutral = ((hi - lo) <= max_chroma) & (lo >= min_luma)
    h, w = neutral.shape
    seen = np.zeros((h, w), dtype=np.bool_)
    q = deque()

    def add(y, x):
        if 0 <= y < h and 0 <= x < w and neutral[y, x] and not seen[y, x]:
            seen[y, x] = True
            q.append((y, x))

    for x in range(w):
        add(0, x)
        add(h - 1, x)
    for y in range(h):
        add(y, 0)
        add(y, w - 1)

    while q:
        y, x = q.popleft()
        add(y - 1, x)
        add(y + 1, x)
        add(y, x - 1)
        add(y, x + 1)
        add(y - 1, x - 1)
        add(y - 1, x + 1)
        add(y + 1, x - 1)
        add(y + 1, x + 1)

    pixels[seen, 3] = 0
    Image.fromarray(pixels, "RGBA").save(ROOT / dst_name, optimize=True)
    print(f"{src_name} -> {dst_name}: cleared {int(seen.sum())} edge-connected background pixels")

make_transparent("card_repair.jpg", "card_repair.png")
make_transparent("card_cyber.jpg", "card_cyber.png")
make_transparent("card_shield.png", "card_shield_transparent.png")

# The newly uploaded aircraft and mine are stored as base64 text so GitHub's
# text-only file uploader can preserve the original JPEG bytes. Decode them
# here, then run the same edge-connected background removal used above.
import base64
from io import BytesIO

def make_transparent_encoded(encoded_name, dst_name, min_luma=78, max_chroma=72):
    encoded_path = ROOT / encoded_name
    raw = base64.b64decode(encoded_path.read_text(encoding="ascii"))
    image = Image.open(BytesIO(raw)).convert("RGBA")
    pixels = np.array(image, dtype=np.uint8)
    rgb = pixels[:, :, :3].astype(np.int16)
    lo = rgb.min(axis=2)
    hi = rgb.max(axis=2)
    neutral = ((hi - lo) <= max_chroma) & (lo >= min_luma)
    h, w = neutral.shape
    seen = np.zeros((h, w), dtype=np.bool_)
    q = deque()

    def add(y, x):
        if 0 <= y < h and 0 <= x < w and neutral[y, x] and not seen[y, x]:
            seen[y, x] = True
            q.append((y, x))

    for x in range(w):
        add(0, x)
        add(h - 1, x)
    for y in range(h):
        add(y, 0)
        add(y, w - 1)

    while q:
        y, x = q.popleft()
        add(y - 1, x)
        add(y + 1, x)
        add(y, x - 1)
        add(y, x + 1)
        add(y - 1, x - 1)
        add(y - 1, x + 1)
        add(y + 1, x - 1)
        add(y + 1, x + 1)

    pixels[seen, 3] = 0
    Image.fromarray(pixels, "RGBA").save(ROOT / dst_name, optimize=True)
    print(f"{encoded_name} -> {dst_name}: cleared {int(seen.sum())} edge-connected background pixels")

make_transparent_encoded("airstrike_plane.jpg.b64", "airstrike_plane.png", min_luma=238, max_chroma=24)
make_transparent_encoded("land_mine.jpg.b64", "land_mine.png")
