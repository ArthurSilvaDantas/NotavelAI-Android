"""Testes do preparo de dados, cobrindo os erros que o dataset real já provocou."""

import random
from collections import defaultdict
from pathlib import Path

import pytest
from PIL import Image

from src.data.build_background import sample_patch
from src.data.dataset import CLASSES, Box, Source, candidate_rotations, normalize_category, rotate
from src.data.prepare_crops import crop_bounds, other_note_fraction
from src.data.prepare_own_photos import LABELS, load_labels


def make_source(width=100, height=200, boxes=()):
    return Source(split="train", path=Path("foto.jpg"), width=width, height=height, boxes=tuple(boxes))


def make_box(x, y, w, h, category="2 reais", id=1):
    return Box(id=id, category=category, x=x, y=y, w=w, h=h)


def test_same_scene_is_never_in_train_and_test():
    destinations = defaultdict(set)
    for row in load_labels():
        if row["split"] != "discard":
            destinations[row["scene"]].add(row["split"])
    assert destinations
    assert all(len(found) == 1 for found in destinations.values())


def test_used_photos_have_a_known_class():
    used = [row for row in load_labels() if row["split"] != "discard"]
    assert all(row["label"] in LABELS for row in used)


def test_normalize_category_ignores_case_and_spaces():
    assert normalize_category("  2   Reais ") == "2 reais"


def test_crop_adds_ten_percent_margin_on_each_side():
    assert crop_bounds(make_box(100, 100, 100, 50), 1000, 1000) == (90, 95, 210, 155)


def test_crop_margin_is_limited_to_image_borders():
    assert crop_bounds(make_box(0, 0, 100, 100), 105, 105) == (0, 0, 105, 105)


def test_crop_handles_box_slightly_outside_image():
    # Caixa real do dataset: x = -0.01.
    x0, _, _, _ = crop_bounds(make_box(-0.01, 10, 50, 50), 100, 100)
    assert x0 == 0


@pytest.mark.parametrize(
    ("file_size", "expected"),
    [
        ((100, 200), (0, 180)),  # 180° não muda as dimensões
        ((200, 100), (90, 270)),  # deitada em relação ao JSON
    ],
)
def test_candidate_rotations_follow_dimensions(file_size, expected):
    assert candidate_rotations(file_size, make_source()) == expected


def test_candidate_rotations_square_allows_all():
    assert candidate_rotations((50, 50), make_source(50, 50)) == (0, 90, 180, 270)


def test_candidate_rotations_rejects_incompatible_size():
    with pytest.raises(ValueError):
        candidate_rotations((123, 456), make_source())


def test_rotate_90_is_clockwise():
    image = Image.new("L", (2, 1))
    image.putpixel((0, 0), 255)  # pixel marcado à esquerda
    rotated = rotate(image, 90)
    assert rotated.size == (1, 2)
    assert rotated.getpixel((0, 0)) == 255  # no sentido horário, a esquerda vai para o topo


def test_rotate_zero_keeps_image():
    image = Image.new("L", (2, 1))
    assert rotate(image, 0) is image


def test_other_note_fraction_ignores_same_value_and_coins():
    box = make_box(0, 0, 100, 100, "2 reais", id=1)
    others = (box, make_box(0, 0, 100, 100, "2 reais", id=2), make_box(0, 0, 100, 100, "1 real", id=3))
    assert other_note_fraction(box, (0, 0, 100, 100), others) == 0


def test_other_note_fraction_measures_different_value():
    box = make_box(0, 0, 100, 100, "2 reais", id=1)
    other = make_box(50, 0, 100, 100, "5 reais", id=2)
    assert other_note_fraction(box, (0, 0, 100, 100), (box, other)) == pytest.approx(0.5)


def test_background_patch_never_touches_a_box():
    box = make_box(400, 400, 200, 200)
    source = make_source(1000, 1000, [box])
    rng = random.Random(0)
    for _ in range(200):
        x0, y0, x1, y1 = sample_patch(source, 1000, 1000, rng)
        assert x1 <= box.x or box.x + box.w <= x0 or y1 <= box.y or box.y + box.h <= y0


def test_background_patch_returns_none_when_box_fills_image():
    source = make_source(100, 100, [make_box(0, 0, 100, 100)])
    assert sample_patch(source, 100, 100, random.Random(0)) is None
