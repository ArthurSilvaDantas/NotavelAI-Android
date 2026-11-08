"""Fotos reais de celular do dataset público "Cédulas do Real" (Kaggle).

Fonte: https://www.kaggle.com/datasets/karenalmeida340/cdulas-do-real (Karen Almeida e
colaboradores, licença MIT; artigo: https://doi.org/10.5335/rbca.v17i1.15934). São fotos de celular
em fundos variados (mão, mesa, chão, tecido), o tipo de imagem que o app recebe.

Duas etapas:
1. Importação (--source <pasta do zip extraído>): copia só as fotos originais, sem as versões
   aumentadas pelos autores (marcadas "synthetic" e "flip"), reduzidas a no máximo 1024 px, para
   data/external/cedulas_do_real/<split da fonte>/<valor>/.
2. Distribuição, respeitando os splits dos autores:
   - train      -> data/processed/train/<valor>/kaggle__*.jpg
   - test (273) -> metade em data/processed/valid/ (escolha da melhor época) e metade em
                   data/kaggle_test/ (teste independente)
   - validation (66, inclui imagens do Google) -> data/kaggle_extra/ (segundo teste independente;
                   é o conjunto em que o artigo relata 100%)

Uso:
    python -m src.data.prepare_kaggle --source ~/Downloads/dataset   # importa e distribui
    python -m src.data.prepare_kaggle                                # só distribui

Rode depois de prepare_crops.py, que recria as pastas das cédulas.
"""

import argparse
import hashlib
import shutil
from collections import Counter
from pathlib import Path

from PIL import Image, ImageOps

from src.data.dataset import CLASSES, PROCESSED_DIR, ROOT

SOURCE_DIR = ROOT / "data" / "external" / "cedulas_do_real"
KAGGLE_TEST_DIR = ROOT / "data" / "kaggle_test"
KAGGLE_EXTRA_DIR = ROOT / "data" / "kaggle_extra"
PREFIX = "kaggle__"
MAX_SIDE = 1024
JPEG_QUALITY = 92
AUGMENTED_MARKERS = ("synthetic", "flip")
SPLITS = ("train", "test", "validation")


def label_of(folder: str) -> str:
    """As pastas da fonte se chamam "nota-2" (train, test) ou "2" (validation)."""
    label = folder.removeprefix("nota-")
    assert label in CLASSES, folder
    return label


def import_source(source: Path) -> None:
    shutil.rmtree(SOURCE_DIR, ignore_errors=True)
    counts = Counter()
    for split in SPLITS:
        for path in sorted((source / split).glob("*/*")):
            if path.suffix.lower() not in (".jpg", ".jpeg"):
                continue
            if any(marker in path.name.lower() for marker in AUGMENTED_MARKERS):
                counts[(split, "aumentada")] += 1
                continue
            label = label_of(path.parent.name)
            destination = SOURCE_DIR / split / label / f"{path.stem}.jpg"
            destination.parent.mkdir(parents=True, exist_ok=True)
            with Image.open(path) as image:
                rgb = ImageOps.exif_transpose(image).convert("RGB")
            rgb.thumbnail((MAX_SIDE, MAX_SIDE))
            rgb.save(destination, quality=JPEG_QUALITY)
            counts[(split, "original")] += 1
    for split in SPLITS:
        print(f"{split}: {counts[(split, 'original')]} originais importadas, "
              f"{counts[(split, 'aumentada')]} aumentadas ignoradas")


def clean() -> None:
    for split in ("train", "valid"):
        for path in (PROCESSED_DIR / split).glob(f"*/{PREFIX}*"):
            path.unlink()
    for folder in (KAGGLE_TEST_DIR, KAGGLE_EXTRA_DIR):
        for subfolder in folder.glob("*/"):
            shutil.rmtree(subfolder)


def copy(path: Path, folder: Path, prefix: str = "") -> None:
    folder.mkdir(parents=True, exist_ok=True)
    shutil.copy2(path, folder / f"{prefix}{path.name}")


def distribute() -> None:
    clean()
    counts = Counter()
    for label in CLASSES:
        for path in sorted((SOURCE_DIR / "train" / label).glob("*.jpg")):
            copy(path, PROCESSED_DIR / "train" / label, PREFIX)
            counts[(label, "train")] += 1
        test = sorted((SOURCE_DIR / "test" / label).glob("*.jpg"),
                      key=lambda p: hashlib.sha256(p.name.encode()).hexdigest())
        for index, path in enumerate(test):
            if index % 2 == 0:
                copy(path, PROCESSED_DIR / "valid" / label, PREFIX)
                counts[(label, "valid")] += 1
            else:
                copy(path, KAGGLE_TEST_DIR / label)
                counts[(label, "test")] += 1
        for path in sorted((SOURCE_DIR / "validation" / label).glob("*.jpg")):
            copy(path, KAGGLE_EXTRA_DIR / label)
            counts[(label, "extra")] += 1

    for label in CLASSES:
        print(f"R$ {label:>3}: treino {counts[(label, 'train')]:>3}  validação {counts[(label, 'valid')]:>3}  "
              f"teste {counts[(label, 'test')]:>3}  teste extra {counts[(label, 'extra')]:>3}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Fotos do dataset Cédulas do Real (Kaggle)")
    parser.add_argument("--source", type=Path, help="pasta do zip extraído (com train/, test/, validation/)")
    parser.add_argument("--import-only", action="store_true", help="só importa, sem mexer em data/processed")
    args = parser.parse_args()
    if args.source:
        import_source(args.source.expanduser())
    if not args.import_only:
        distribute()


if __name__ == "__main__":
    main()
