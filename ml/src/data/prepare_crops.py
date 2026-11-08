"""Recorta as cédulas do export COCO em data/processed/<split>/<classe>/.

Remove duplicatas entre splits pelo hash dos pixels, das imagens de origem e dos recortes. Quando
há conflito, mantém a cópia do split mais protegido (test > valid > train), para que a avaliação
não perca amostras e o treino não contenha nada que será testado.

Todo recorte, mantido ou descartado, fica registrado em reports/crop_manifest.csv.
"""

import csv
import hashlib
import math
import shutil
from collections import Counter

from PIL import Image

from src.data.dataset import (
    BANKNOTES,
    CLASSES,
    CONFIRMED_NEAR_DUPLICATES,
    PROCESSED_DIR,
    REPORTS_DIR,
    SOURCE_DIR,
    SPLITS,
    Box,
    Source,
    load_all,
    load_orientations,
    open_canonical,
    print_table,
)

MARGIN = 0.10  # fração da largura/altura da caixa, em cada lado
MIN_SIDE = 32
MIN_TRAIN_EXAMPLES = 30
JPEG_QUALITY = 95
SPLIT_PRIORITY = ("test", "valid", "train")

KEPT = "kept"
TOO_SMALL = "too_small"
DUPLICATE_SOURCE = "duplicate_source"
DUPLICATE_CROP = "duplicate_crop"
NEAR_DUPLICATE = "confirmed_near_duplicate"


def pixel_hash(image: Image.Image) -> str:
    # Hash dos pixels, não do arquivo: ignora metadados e, nos recortes, a recompressão JPEG.
    return hashlib.sha256(image.tobytes() + repr(image.size).encode()).hexdigest()


def crop_bounds(box: Box, width: int, height: int) -> tuple[int, int, int, int]:
    margin_x, margin_y = box.w * MARGIN, box.h * MARGIN
    return (
        max(0, math.floor(box.x - margin_x)),
        max(0, math.floor(box.y - margin_y)),
        min(width, math.ceil(box.x + box.w + margin_x)),
        min(height, math.ceil(box.y + box.h + margin_y)),
    )


def other_note_fraction(box: Box, bounds: tuple[int, int, int, int], boxes: tuple[Box, ...]) -> float:
    """Fração do recorte coberta pela maior cédula de OUTRO valor (rótulo potencialmente ambíguo)."""
    x0, y0, x1, y1 = bounds
    largest = 0.0
    for other in boxes:
        if other.category in BANKNOTES and other.category != box.category:
            overlap_w = min(x1, other.x + other.w) - max(x0, other.x)
            overlap_h = min(y1, other.y + other.h) - max(y0, other.y)
            if overlap_w > 0 and overlap_h > 0:
                largest = max(largest, overlap_w * overlap_h)
    return largest / ((x1 - x0) * (y1 - y0))


def clean_output() -> None:
    # Só as pastas das cédulas: data/processed/<split>/fundo (a gravar) não é gerada aqui.
    for split in SPLITS:
        for label in CLASSES:
            shutil.rmtree(PROCESSED_DIR / split / label, ignore_errors=True)
            (PROCESSED_DIR / split / label).mkdir(parents=True)


def main() -> None:
    print(f"Origem: {SOURCE_DIR}")
    clean_output()
    orientations = load_orientations()

    sources = sorted(load_all(), key=lambda s: (SPLIT_PRIORITY.index(s.split), s.path.name))

    # Primeira cópia vista por hash; a ordem de `sources` garante a prioridade entre splits.
    first_source: dict[str, Source] = {}
    first_crop: dict[str, str] = {}
    dropped_sources: list[tuple[Source, Source]] = []
    same_split_sources = same_split_crops = 0
    manifest = []
    status_counts = Counter()
    kept = Counter()

    for source in sources:
        image = open_canonical(source, orientations)
        digest = pixel_hash(image)
        original = first_source.setdefault(digest, source)
        is_cross_split_duplicate = original.split != source.split
        if is_cross_split_duplicate:
            dropped_sources.append((source, original))
        elif original is not source:
            same_split_sources += 1
        is_near_duplicate = source.path.name.startswith(CONFIRMED_NEAR_DUPLICATES)

        for box in source.boxes:
            if box.category not in BANKNOTES:
                continue
            label = BANKNOTES[box.category]
            bounds = crop_bounds(box, *image.size)
            x0, y0, x1, y1 = bounds
            width, height = x1 - x0, y1 - y0
            crop = image.crop(bounds)
            crop_digest = pixel_hash(crop)
            file_name = f"{source.path.stem}__ann{box.id}.jpg"

            if is_cross_split_duplicate:
                status = DUPLICATE_SOURCE
            elif is_near_duplicate:
                status = NEAR_DUPLICATE
            elif min(width, height) < MIN_SIDE:
                status = TOO_SMALL
            else:
                first_split = first_crop.setdefault(crop_digest, source.split)
                if first_split != source.split:
                    status = DUPLICATE_CROP
                else:
                    status = KEPT
                    if (source.split, label, crop_digest) in kept:
                        same_split_crops += 1
                    kept[(source.split, label, crop_digest)] += 1
                    crop.save(PROCESSED_DIR / source.split / label / file_name, quality=JPEG_QUALITY)

            status_counts[status] += 1
            manifest.append(
                [
                    source.split,
                    label,
                    file_name,
                    source.path.name,
                    box.id,
                    x0,
                    y0,
                    x1,
                    y1,
                    round(other_note_fraction(box, bounds, source.boxes), 3),
                    crop_digest,
                    status,
                ]
            )

    REPORTS_DIR.mkdir(exist_ok=True)
    with (REPORTS_DIR / "crop_manifest.csv").open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow(
            ["split", "classe", "arquivo", "origem", "ann_id", "x0", "y0", "x1", "y1",
             "other_banknote_fraction", "crop_sha256", "status"]
        )
        writer.writerows(sorted(manifest, key=lambda r: (SPLITS.index(r[0]), CLASSES.index(r[1]), r[2])))

    counts = Counter()
    for (split, label, _), amount in kept.items():
        counts[(split, label)] += amount
    rows = [[label, *(counts[(split, label)] for split in SPLITS)] for label in CLASSES]
    for row in rows:
        row.append(sum(row[1:]))
    rows.append(["TOTAL", *(sum(row[i] for row in rows) for i in range(1, len(SPLITS) + 2))])
    header = ["classe", *SPLITS, "total"]
    with (REPORTS_DIR / "crop_counts.csv").open("w", newline="", encoding="utf-8") as file:
        csv.writer(file).writerows([header, *rows])

    print(f"\nImagens de origem analisadas: {len(sources)}")
    print(f"Origens duplicadas entre splits (hash exato): {len(dropped_sources)}")
    for source, original in dropped_sources:
        print(f"  removida {source.split}/{source.path.name} (mantida em {original.split})")
    print(f"Origens duplicadas dentro do mesmo split (não removidas): {same_split_sources}")
    print(f"Recortes descartados por menor lado < {MIN_SIDE} px: {status_counts[TOO_SMALL]}")
    print(f"Recortes de origens duplicadas: {status_counts[DUPLICATE_SOURCE]}")
    print(f"Recortes duplicados entre splits (hash exato): {status_counts[DUPLICATE_CROP]}")
    print(f"Recortes de quase duplicatas confirmadas: {status_counts[NEAR_DUPLICATE]}")
    print(f"Recortes duplicados dentro do mesmo split (não removidos): {same_split_crops}")

    print("\nRecortes por classe e split:\n")
    print_table(rows, header)

    few = [label for label in CLASSES if counts[("train", label)] < MIN_TRAIN_EXAMPLES]
    print(f"\nClasses com menos de {MIN_TRAIN_EXAMPLES} recortes no treino: {few or 'nenhuma'}")

    ambiguous = [row for row in manifest if row[-1] == KEPT and row[9] >= 0.25]
    print(f"Recortes mantidos com >= 25% de área coberta por cédula de outro valor: {len(ambiguous)}")


if __name__ == "__main__":
    main()
