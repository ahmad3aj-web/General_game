from collections import deque
from pathlib import Path
from PIL import Image
import numpy as np

ROOT = Path("app/src/main/assets/ui")

def make_transparent(src_name, dst_name):
    path = ROOT / src_name
    image = Image.open(path).convert("RGBA")
    pixels = np.array(image, dtype=np.uint8)
    rgb = pixels[:, :, :3].astype(np.int16)
    lo = rgb.min(axis=2)
    hi = rgb.max(axis=2)
    # The supplied cards use a pale neutral checkerboard. Include antialiased
    # edge pixels, but only remove regions connected to the image boundary.
    neutral = ((hi - lo) <= 72) & (lo >= 78)
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
