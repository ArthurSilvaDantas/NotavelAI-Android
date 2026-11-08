"""Classifica fotos com os modelos .tflite do app (média do ensemble), do mesmo jeito que o app.

Uso: python -m src.models.predict foto.jpg [outra.jpg | pasta ...]
"""

import argparse
from pathlib import Path

import numpy as np
import tensorflow as tf
from PIL import Image, ImageOps

from src.models import ensemble
from src.models.ensemble import LABELS_PATH
from src.models.train_model import prepare_image

EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
TOP = 3


def load(path: Path) -> np.ndarray:
    """Endireita pela tag EXIF, como a câmera do app entrega, e prepara como no treino."""
    with Image.open(path) as image:
        image = ImageOps.exif_transpose(image).convert("RGB")
    # O app reduz a foto a no máximo 1024 px antes do modelo (FotoUtil.LADO_MODELO).
    image.thumbnail((1024, 1024))
    rgb = np.asarray(image, dtype="float32")
    # Mesma função do treino (prepare_image) e do app (PreProcessamento.java): outro
    # redimensionamento, mesmo parecido, muda previsões.
    return np.asarray(prepare_image(tf.convert_to_tensor(rgb)))


def expand(paths: list[Path]) -> list[Path]:
    files = []
    for path in paths:
        found = sorted(path.rglob("*")) if path.is_dir() else [path]
        files += [file for file in found if file.suffix.lower() in EXTENSIONS]
    return files


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("caminhos", nargs="+", type=Path, help="fotos ou pastas")
    files = expand(parser.parse_args().caminhos)
    if not files:
        parser.error("nenhuma imagem encontrada")

    labels = LABELS_PATH.read_text(encoding="utf-8").splitlines()
    probabilities = ensemble.predict(np.stack([load(file) for file in files]))
    for file, scores in zip(files, probabilities):
        ranking = np.argsort(scores)[::-1][:TOP]
        answer = "sem cédula" if labels[ranking[0]] == "fundo" else f"R$ {labels[ranking[0]]}"
        others = ", ".join(f"{labels[i]} {scores[i]:.0%}" for i in ranking[1:])
        print(f"{file.name}: {answer} ({scores[ranking[0]]:.0%})  depois: {others}")


if __name__ == "__main__":
    main()
