"""Lista recortes visualmente quase iguais em splits diferentes (rodar após prepare_crops.py).

O hash exato não pega a mesma foto recodificada. Aqui comparamos um dHash de 256 bits e geramos
uma folha de contato para conferência humana: o hash é deliberadamente folgado, e os pares
confirmados entram em dataset.CONFIRMED_NEAR_DUPLICATES.
"""

import csv
from itertools import combinations

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from PIL import Image

from src.data.dataset import FIGURES_DIR, PROCESSED_DIR, REPORTS_DIR, SPLITS

HASH_SIZE = 16
MAX_DISTANCE = 45  # de 256 bits
MAX_SHOWN = 30


def dhash(image: Image.Image) -> np.ndarray:
    small = image.convert("L").resize((HASH_SIZE + 1, HASH_SIZE), Image.Resampling.BILINEAR)
    pixels = np.asarray(small, dtype=np.int16)
    return (pixels[:, 1:] > pixels[:, :-1]).ravel()


def main() -> None:
    entries = []
    for split in SPLITS:
        for path in sorted((PROCESSED_DIR / split).glob("*/*.jpg")):
            with Image.open(path) as image:
                entries.append((split, path, dhash(image)))

    pairs = []
    for (split_a, path_a, hash_a), (split_b, path_b, hash_b) in combinations(entries, 2):
        if split_a != split_b:
            distance = int(np.count_nonzero(hash_a != hash_b))
            if distance <= MAX_DISTANCE:
                pairs.append((distance, split_a, path_a, split_b, path_b))
    pairs.sort(key=lambda pair: pair[0])

    with (REPORTS_DIR / "near_duplicates.csv").open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow(["distance", "split_a", "class_a", "file_a", "split_b", "class_b", "file_b"])
        for distance, split_a, path_a, split_b, path_b in pairs:
            writer.writerow([distance, split_a, path_a.parent.name, path_a.name, split_b, path_b.parent.name, path_b.name])
    print(f"Recortes analisados: {len(entries)}")
    print(f"Pares entre splits com distância <= {MAX_DISTANCE}/256: {len(pairs)}")

    output = FIGURES_DIR / "near_duplicates.png"
    output.unlink(missing_ok=True)
    if not pairs:
        return
    shown = pairs[:MAX_SHOWN]
    columns = 3
    rows = (len(shown) + columns - 1) // columns
    figure, axes = plt.subplots(rows, columns * 2, figsize=(columns * 5, rows * 2.5), squeeze=False)
    for axis in axes.ravel():
        axis.axis("off")
    for index, (distance, split_a, path_a, split_b, path_b) in enumerate(shown):
        for offset, (split, path) in enumerate(((split_a, path_a), (split_b, path_b))):
            axis = axes[index // columns][(index % columns) * 2 + offset]
            with Image.open(path) as image:
                image.thumbnail((400, 400))
                axis.imshow(image)
            axis.set_title(f"d={distance} {split}/{path.parent.name}\n{path.name[:28]}", fontsize=6)
    figure.tight_layout()
    figure.savefig(output, dpi=110)
    print(f"Folha de contato: {output}")


if __name__ == "__main__":
    main()
