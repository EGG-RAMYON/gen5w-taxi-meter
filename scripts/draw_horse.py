#!/usr/bin/env python3
"""Draw eight original gait poses for the 46-column taxi meter display."""
from pathlib import Path
from PIL import Image, ImageDraw

# Each frame gives torso height and four legs as knee and hoof positions.
# The rear legs drive the stride, the front legs reach and absorb the landing.
POSES = [
    (0, ((15, 23, 14, 31), (22, 23, 21, 31), (31, 23, 31, 31), (37, 23, 37, 31))),
    (0, ((17, 23, 24, 24), (19, 24, 17, 30), (28, 22, 21, 25), (33, 24, 29, 31))),
    (-1, ((15, 22, 22, 22), (18, 21, 25, 24), (33, 22, 40, 26), (37, 23, 43, 29))),
    (-2, ((12, 20, 5, 18), (14, 21, 9, 23), (36, 19, 43, 22), (38, 22, 44, 25))),
    (-1, ((10, 21, 5, 24), (13, 22, 9, 27), (38, 22, 43, 25), (36, 21, 34, 26))),
    (0, ((12, 24, 11, 31), (16, 23, 14, 29), (33, 22, 34, 25), (29, 22, 24, 26))),
    (1, ((17, 25, 16, 31), (20, 24, 24, 27), (28, 23, 22, 25), (32, 24, 26, 29))),
    (0, ((20, 24, 25, 27), (17, 22, 21, 24), (29, 23, 25, 31), (33, 23, 30, 29))),
]

W, H, SCALE = 46, 33, 4
BODY = [(16, 12), (23, 13), (31, 12), (35, 13), (37, 16), (35, 19),
        (30, 20), (23, 19), (18, 20), (15, 18), (13, 15), (10, 12),
        (5, 12), (3, 10), (4, 8), (8, 6), (9, 2), (12, 5), (14, 4),
        (15, 8), (16, 12)]
ROOTS = [(17, 18), (20, 18), (31, 18), (34, 17)]
OUTPUT = Path(__file__).resolve().parents[1] / "app/res/drawable-nodpi/taxi_horse.png"


def draw_pose(index, lift, legs):
    frame = Image.new("RGBA", (W * SCALE, H * SCALE))
    draw = ImageDraw.Draw(frame)

    def line(points, alpha=255, width=7):
        draw.line([(x * SCALE + 2, y * SCALE + 2) for x, y in points],
                  fill=(95, 240, 255, alpha), width=width, joint="curve")

    for leg in (0, 2, 1, 3):
        x, y = ROOTS[leg]
        kx, ky, hx, hy = legs[leg]
        shade = 147 if leg in (0, 2) else 255
        line([(x, y + lift), (kx, ky), (hx, hy)], shade)
        line([(hx - 1, hy), (hx, hy)], shade, 6)

    line([(x, y + lift) for x, y in BODY])
    line([(12, 8 + lift), (16, 10 + lift), (19, 13 + lift)], 160, 6)
    line([(8, 8 + lift), (9, 8 + lift)], 255, 5)
    tail = (0, 1, 2, 1, 0, -1, -1, 0)[index]
    line([(36, 15 + lift), (39, 14 + lift), (43, 15 + tail), (44, 17 + tail)])
    return frame


if __name__ == "__main__":
    strip = Image.new("RGBA", (W * SCALE * len(POSES), H * SCALE))
    for index, (lift, legs) in enumerate(POSES):
        strip.paste(draw_pose(index, lift, legs), (index * W * SCALE, 0))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    strip.save(OUTPUT, optimize=True)
