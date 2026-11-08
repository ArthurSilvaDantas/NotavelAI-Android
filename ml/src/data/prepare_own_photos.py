"""Separa as fotos tiradas no celular segundo config/phone_photo_labels.csv.

As fotos ficam em data/raw/phone_photos/. Os rótulos foram lidos à mão: uma foto só é usada se o
valor for legível e não houver outra nota relevante junto. Fotos da mesma cena nunca ficam em
splits diferentes, porque muitas são quase idênticas.

Cédulas estrangeiras (dólar) entram como `fundo`: para o app, não são cédula de Real e ele não deve
anunciar valor.

- train: data/processed/train/<classe>/phone__*.jpg
- test: data/phone_test/<classe>/, nunca usado no treino

Rode depois de prepare_crops.py, que recria as pastas das cédulas.
"""

import csv
import shutil
from collections import Counter

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from PIL import Image, ImageOps

from src.data.dataset import CLASSES, FIGURES_DIR, PROCESSED_DIR, ROOT, print_table

LABELS_PATH = ROOT / "config" / "phone_photo_labels.csv"
RAW_DIR = ROOT / "data" / "raw" / "phone_photos"
OWN_TEST_DIR = ROOT / "data" / "phone_test"
PREFIX = "phone__"
MAX_SIDE = 1024
JPEG_QUALITY = 95
SHEET_COLUMNS = 9
LABELS = (*CLASSES, "fundo")


def load_labels() -> list[dict[str, str]]:
    with LABELS_PATH.open(encoding="utf-8", newline="") as file:
        return list(csv.DictReader(file))


def save_photo(row: dict[str, str]) -> None:
    with Image.open(RAW_DIR / row["file"]) as image:
        rgb = ImageOps.exif_transpose(image).convert("RGB")
    rgb.thumbnail((MAX_SIDE, MAX_SIDE))
    if row["split"] == "train":
        rgb.save(PROCESSED_DIR / "train" / row["label"] / f"{PREFIX}{row['file']}", quality=JPEG_QUALITY)
    else:
        folder = OWN_TEST_DIR / row["label"]
        folder.mkdir(parents=True, exist_ok=True)
        rgb.save(folder / row["file"], quality=JPEG_QUALITY)


def plot_sheet(used: list[dict[str, str]]) -> None:
    rows = (len(used) + SHEET_COLUMNS - 1) // SHEET_COLUMNS
    figure, axes = plt.subplots(rows, SHEET_COLUMNS, figsize=(SHEET_COLUMNS * 1.7, rows * 2.2), squeeze=False)
    for axis in axes.ravel():
        axis.axis("off")
    ordered = sorted(used, key=lambda r: (LABELS.index(r["label"]), r["scene"], r["file"]))
    for axis, row in zip(axes.ravel(), ordered):
        with Image.open(RAW_DIR / row["file"]) as image:
            thumb = ImageOps.exif_transpose(image).convert("RGB")
        thumb.thumbnail((300, 300))
        axis.imshow(thumb)
        name = row["label"] if row["label"] == "fundo" else f"R$ {row['label']}"
        axis.set_title(f"{name} · {row['split']}\n{row['scene']}", fontsize=7)
    figure.tight_layout()
    FIGURES_DIR.mkdir(parents=True, exist_ok=True)
    figure.savefig(FIGURES_DIR / "phone_photos.png", dpi=110)


def main() -> None:
    labels = load_labels()
    # Só as pastas das classes: o .gitkeep de data/phone_test/ é versionado.
    for folder in OWN_TEST_DIR.glob("*/"):
        shutil.rmtree(folder)
    for path in (PROCESSED_DIR / "train").glob(f"*/{PREFIX}*"):
        path.unlink()

    used = [row for row in labels if row["split"] != "discard"]
    for row in used:
        save_photo(row)
    plot_sheet(used)

    counts = Counter((row["label"], row["split"]) for row in used)
    table = [[label, counts[(label, "train")], counts[(label, "test")]] for label in LABELS]
    print_table(table, ["classe", "treino", "teste"])
    print(f"\nDescartadas: {Counter(r['discard_reason'] for r in labels if r['split'] == 'discard')}")


if __name__ == "__main__":
    main()
