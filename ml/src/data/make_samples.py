"""Gera reports/figures/samples.png: recortes aleatórios de cada classe, para conferência visual."""

import random

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from PIL import Image

from src.data.dataset import CLASSES, FIGURES_DIR, PROCESSED_DIR, SPLITS

SAMPLES_PER_CLASS = 8
SEED = 42


def main() -> None:
    rng = random.Random(SEED)
    figure, axes = plt.subplots(
        len(CLASSES), SAMPLES_PER_CLASS, figsize=(SAMPLES_PER_CLASS * 1.8, len(CLASSES) * 1.9), squeeze=False
    )
    for row, label in enumerate(CLASSES):
        paths = sorted(p for split in SPLITS for p in (PROCESSED_DIR / split / label).glob("*.jpg"))
        chosen = rng.sample(paths, min(SAMPLES_PER_CLASS, len(paths)))
        for column in range(SAMPLES_PER_CLASS):
            axis = axes[row][column]
            axis.axis("off")
            if column < len(chosen):
                with Image.open(chosen[column]) as image:
                    image.thumbnail((300, 300))
                    axis.imshow(image)
                axis.set_title(chosen[column].parent.parent.name, fontsize=7)
        axes[row][0].text(-0.15, 0.5, f"R$ {label}", transform=axes[row][0].transAxes,
                          ha="right", va="center", fontsize=11, fontweight="bold")
    figure.tight_layout(rect=(0.06, 0, 1, 1))
    FIGURES_DIR.mkdir(parents=True, exist_ok=True)
    output = FIGURES_DIR / "samples.png"
    figure.savefig(output, dpi=110)
    print(output)


if __name__ == "__main__":
    main()
