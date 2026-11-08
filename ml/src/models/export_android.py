"""Copia o modelo para o app Android e gera a referência do teste instrumentado.

O teste ClassificadorCedulasTest roda no aparelho e confere se o app dá as mesmas respostas
que o Python (src.models.predict) para as fotos de data/phone_test/. Rode depois de
src.models.ensemble, sempre que o modelo mudar.
"""

import csv
import shutil

import numpy as np

from src.data.prepare_own_photos import OWN_TEST_DIR
from src.models import ensemble
from src.models.ensemble import LABELS_PATH
from src.models.predict import load
from src.models.train_model import ROOT

# ROOT é a pasta ml/; o app fica ao lado dela, em android/.
ANDROID_APP = ROOT.parent / "android" / "app" / "src"
ASSETS_DIR = ANDROID_APP / "main" / "assets"
TEST_ASSETS_DIR = ANDROID_APP / "androidTest" / "assets"
TEST_PHOTOS_DIR = TEST_ASSETS_DIR / "fotos_teste"
REFERENCE_PATH = TEST_ASSETS_DIR / "referencia.csv"


def main() -> None:
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    for old in ASSETS_DIR.glob("*.tflite"):
        old.unlink()
    ensemble.export()
    for path in ensemble.tflite_paths():
        shutil.copy2(path, ASSETS_DIR / path.name)
    shutil.copy2(LABELS_PATH, ASSETS_DIR / LABELS_PATH.name)

    shutil.rmtree(TEST_PHOTOS_DIR, ignore_errors=True)
    TEST_PHOTOS_DIR.mkdir(parents=True)
    photos = sorted(OWN_TEST_DIR.glob("*/*.jpg"))
    labels = LABELS_PATH.read_text(encoding="utf-8").splitlines()
    probabilities = ensemble.predict(np.stack([load(photo) for photo in photos]))

    with REFERENCE_PATH.open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow(["arquivo", "real", "previsto", "confianca"])
        for photo, scores in zip(photos, probabilities):
            name = f"{photo.parent.name}_{photo.name}"
            shutil.copy2(photo, TEST_PHOTOS_DIR / name)
            writer.writerow([name, photo.parent.name, labels[int(np.argmax(scores))], f"{scores.max():.4f}"])

    print(f"Modelos {list(ensemble.MEMBERS)} e rótulos em {ASSETS_DIR}")
    print(f"{len(photos)} fotos e referência em {TEST_ASSETS_DIR}")


if __name__ == "__main__":
    main()
