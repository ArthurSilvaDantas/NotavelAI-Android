"""Acrescenta ao treino fotos de cédulas de um dataset público: banknoteBrazil.

Fonte: https://github.com/joaofracasso/banknoteBrazil (código sob licença MIT; o autor declara o
projeto "for academic research use only"). São 1010 fotos de celular, 256x256, de R$ 2, 5, 10, 20
e 50, frente e verso, todas sobre a mesma mesa de madeira. Parecem quadros de vídeo, então fotos
vizinhas são quase iguais.

Por isso: entram só no treino (nunca na validação nem no teste) e, de cada classe, só uma amostra
fixa por lado, para não desequilibrar as classes nem repetir a mesma cena demais.

Rode depois de prepare_crops.py, que recria as pastas das cédulas.
"""

import random
import shutil
import urllib.request
import zipfile

from src.data.dataset import CLASSES, PROCESSED_DIR, ROOT

URL = "https://www.dropbox.com/s/est8xcd9blthj44/validation.zip?dl=1"
DOWNLOAD_DIR = ROOT / "data" / "external" / "banknote_brazil"
ZIP_PATH = DOWNLOAD_DIR / "validation.zip"
EXTRACTED_DIR = DOWNLOAD_DIR / "extracted"
PREFIX = "external__"
PER_SIDE = 50
SEED = 42


def download() -> None:
    DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)
    if not ZIP_PATH.exists():
        print(f"Baixando {URL}")
        urllib.request.urlretrieve(URL, ZIP_PATH)
    if not EXTRACTED_DIR.exists():
        with zipfile.ZipFile(ZIP_PATH) as archive:
            archive.extractall(EXTRACTED_DIR)


def main() -> None:
    download()
    rng = random.Random(SEED)
    for path in (PROCESSED_DIR / "train").glob(f"*/{PREFIX}*"):
        path.unlink()

    # Pastas do zip: "<valor>reais<Frente|Verso>", por exemplo "50reaisVerso".
    counts = {}
    for folder in sorted(EXTRACTED_DIR.rglob("*reais*")):
        label, side = folder.name.split("reais")
        if not folder.is_dir() or label not in CLASSES:
            continue
        photos = sorted(folder.glob("*.jp*g"))
        for photo in rng.sample(photos, min(PER_SIDE, len(photos))):
            shutil.copy2(photo, PROCESSED_DIR / "train" / label / f"{PREFIX}{side.lower()}_{photo.stem}.jpg")
        counts[label] = counts.get(label, 0) + min(PER_SIDE, len(photos))

    print(f"Fotos externas no treino: {counts}")


if __name__ == "__main__":
    main()
