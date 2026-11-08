"""Gera composições de treino: cédulas coladas sobre cenas sem cédula, em escala e ângulo variados.

Os recortes justos e o `fundo` em cena inteira ensinam o modelo a separar pelo enquadramento, e ele
passa a chamar de `fundo` cédulas pequenas em fotos reais. Aqui a cédula aparece dentro de uma cena.
Para o modelo não aprender que "retângulo colado = cédula", o `fundo` recebe a mesma quantidade de
composições com pedaços sem cédula.

Só usa dados de treino. Rode depois de prepare_crops.py e build_background.py.
"""

import random
from pathlib import Path

from PIL import Image

from src.data.dataset import CLASSES, PROCESSED_DIR

PREFIX = "composite__"
PER_CLASS = 60
MIN_SCALE, MAX_SCALE = 0.25, 0.7  # maior lado colado, como fração do maior lado da cena
JPEG_QUALITY = 95
SEED = 42

TRAIN_DIR = PROCESSED_DIR / "train"


def paste(scene_path: Path, item_path: Path, rng: random.Random) -> Image.Image:
    with Image.open(scene_path) as image:
        scene = image.convert("RGB")
    with Image.open(item_path) as image:
        item = image.convert("RGBA")

    longest = int(max(scene.size) * rng.uniform(MIN_SCALE, MAX_SCALE))
    scale = longest / max(item.size)
    item = item.resize((max(1, int(item.width * scale)), max(1, int(item.height * scale))))
    item = item.rotate(rng.uniform(0, 360), expand=True, resample=Image.Resampling.BILINEAR)

    x = rng.randint(min(0, scene.width - item.width), max(0, scene.width - item.width))
    y = rng.randint(min(0, scene.height - item.height), max(0, scene.height - item.height))
    scene.paste(item, (x, y), item)
    return scene


def build(label: str, items: list[Path], scenes: list[Path], rng: random.Random) -> None:
    for index in range(PER_CLASS):
        image = paste(rng.choice(scenes), rng.choice(items), rng)
        image.save(TRAIN_DIR / label / f"{PREFIX}{index:03d}.jpg", quality=JPEG_QUALITY)


def main() -> None:
    rng = random.Random(SEED)
    for path in TRAIN_DIR.glob(f"*/{PREFIX}*"):
        path.unlink()

    backgrounds = sorted((TRAIN_DIR / "fundo").glob("*.jpg"))
    for label in CLASSES:
        # Fotos inteiras (do celular ou de dataset externo) já têm cena própria e não servem como recorte.
        crops = sorted(p for p in (TRAIN_DIR / label).glob("*.jpg") if not p.name.startswith(("phone__", "external__", "kaggle__")))
        build(label, crops, backgrounds, rng)
    build("fundo", backgrounds, backgrounds, rng)

    print(f"{PER_CLASS} composições por classe em {TRAIN_DIR}, {PER_CLASS * (len(CLASSES) + 1)} no total")


if __name__ == "__main__":
    main()
