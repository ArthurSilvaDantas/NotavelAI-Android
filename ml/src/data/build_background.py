"""Monta a classe `fundo` (imagens sem cédula) em data/processed/<split>/fundo/.

Três fontes, em partes iguais:
- coin: fotos do dataset que só têm moedas, inteiras (a confusão mais provável no app);
- patch: regiões sem nenhuma caixa de fotos com cédulas, do mesmo split da foto de origem;
- coco: objetos do dia a dia do COCO val2017, só com licenças CC BY e CC BY-SA.

As duas primeiras herdam o split da foto de origem, para não criar vazamento. Tudo fica
registrado em reports/background_manifest.csv.
"""

import csv
import json
import random
import shutil
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor

from PIL import Image

from src.data.dataset import (
    BANKNOTES,
    PROCESSED_DIR,
    REPORTS_DIR,
    ROOT,
    SPLITS,
    Source,
    load_all,
    load_orientations,
    open_canonical,
)

PER_SOURCE = {"train": 40, "valid": 5, "test": 5}
MAX_SIDE = 1024
JPEG_QUALITY = 95
SEED = 42

COCO_DIR = ROOT / "data" / "external" / "coco_val2017"
COCO_ANNOTATIONS_URL = "http://images.cocodataset.org/annotations/annotations_trainval2017.zip"
COCO_IMAGE_URL = "http://images.cocodataset.org/val2017/{}"
COCO_OPEN_LICENSES = {4, 5}  # CC BY e CC BY-SA
COCO_CATEGORIES = {
    "book", "cell phone", "keyboard", "laptop", "mouse", "remote", "dining table", "handbag",
    "backpack", "tv", "couch", "bed", "chair", "bottle", "cup", "scissors", "clock", "vase",
}

PATCH_MIN_FRACTION, PATCH_MAX_FRACTION = 0.15, 0.5
PATCH_ATTEMPTS = 200
BOX_CLEARANCE = 0.03  # folga em volta das caixas, como fração do maior lado da imagem


def is_padded(source: Source) -> bool:
    # A família *REAIS* tem bordas pretas de padding, que não existem numa câmera.
    return "REAIS" in source.path.name


def has_banknote(source: Source) -> bool:
    return any(box.category in BANKNOTES for box in source.boxes)


def save(image: Image.Image, split: str, name: str) -> None:
    image = image.copy()
    image.thumbnail((MAX_SIDE, MAX_SIDE))
    image.save(PROCESSED_DIR / split / "fundo" / name, quality=JPEG_QUALITY)


def sample_patch(source: Source, width: int, height: int, rng: random.Random) -> tuple[int, int, int, int] | None:
    clearance = BOX_CLEARANCE * max(width, height)
    for _ in range(PATCH_ATTEMPTS):
        crop_w = int(width * rng.uniform(PATCH_MIN_FRACTION, PATCH_MAX_FRACTION))
        crop_h = int(height * rng.uniform(PATCH_MIN_FRACTION, PATCH_MAX_FRACTION))
        x0, y0 = rng.randint(0, width - crop_w), rng.randint(0, height - crop_h)
        x1, y1 = x0 + crop_w, y0 + crop_h
        if all(
            x1 + clearance <= box.x or box.x + box.w + clearance <= x0
            or y1 + clearance <= box.y or box.y + box.h + clearance <= y0
            for box in source.boxes
        ):
            return x0, y0, x1, y1
    return None


def build_from_dataset(sources: list[Source], orientations: dict[str, int], rng: random.Random) -> list[list]:
    rows = []
    for split in SPLITS:
        candidates = [s for s in sources if s.split == split and not is_padded(s)]

        coin_photos = [s for s in candidates if not has_banknote(s)]
        for source in rng.sample(coin_photos, PER_SOURCE[split]):
            name = f"coin__{source.path.stem}.jpg"
            save(open_canonical(source, orientations), split, name)
            rows.append([split, name, "coin", source.path.name])

        # Só fotos com uma única caixa: nas fotos com várias notas, as caixas nem sempre cobrem
        # tudo, e um pedaço "livre" pode conter a ponta de uma cédula.
        note_photos = [s for s in candidates if has_banknote(s) and len(s.boxes) == 1]
        rng.shuffle(note_photos)
        found = 0
        for source in note_photos:
            if found == PER_SOURCE[split]:
                break
            image = open_canonical(source, orientations)
            bounds = sample_patch(source, *image.size, rng)
            if bounds:
                name = f"patch__{source.path.stem}.jpg"
                save(image.crop(bounds), split, name)
                rows.append([split, name, "patch", source.path.name])
                found += 1
        if found < PER_SOURCE[split]:
            raise RuntimeError(f"{split}: só {found} pedaços sem caixa encontrados")
    return rows


def load_coco_candidates() -> list[str]:
    COCO_DIR.mkdir(parents=True, exist_ok=True)
    annotations = COCO_DIR / "instances_val2017.json"
    if not annotations.exists():
        archive = COCO_DIR / "annotations.zip"
        print("Baixando anotações do COCO...")
        urllib.request.urlretrieve(COCO_ANNOTATIONS_URL, archive)
        with zipfile.ZipFile(archive) as file, file.open("annotations/instances_val2017.json") as member:
            annotations.write_bytes(member.read())
        archive.unlink()
    coco = json.loads(annotations.read_text(encoding="utf-8"))
    wanted = {c["id"] for c in coco["categories"] if c["name"] in COCO_CATEGORIES}
    with_objects = {a["image_id"] for a in coco["annotations"] if a["category_id"] in wanted}
    return sorted(
        image["file_name"]
        for image in coco["images"]
        if image["id"] in with_objects and image["license"] in COCO_OPEN_LICENSES
    )


def download_coco_image(file_name: str) -> None:
    path = COCO_DIR / file_name
    if not path.exists():
        urllib.request.urlretrieve(COCO_IMAGE_URL.format(file_name), path)


def build_from_coco(rng: random.Random) -> list[list]:
    candidates = load_coco_candidates()
    print(f"Imagens do COCO com objetos do dia a dia e licença aberta: {len(candidates)}")
    chosen = rng.sample(candidates, sum(PER_SOURCE.values()))
    with ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(download_coco_image, chosen))

    rows = []
    start = 0
    for split in SPLITS:
        for file_name in chosen[start : start + PER_SOURCE[split]]:
            with Image.open(COCO_DIR / file_name) as image:
                name = f"coco__{file_name}"
                save(image.convert("RGB"), split, name)
            rows.append([split, name, "coco", file_name])
        start += PER_SOURCE[split]
    return rows


def main() -> None:
    rng = random.Random(SEED)
    for split in SPLITS:
        shutil.rmtree(PROCESSED_DIR / split / "fundo", ignore_errors=True)
        (PROCESSED_DIR / split / "fundo").mkdir(parents=True)

    rows = build_from_dataset(load_all(), load_orientations(), rng)
    rows += build_from_coco(rng)

    with (REPORTS_DIR / "background_manifest.csv").open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow(["split", "file", "source", "origin"])
        writer.writerows(rows)
    for split in SPLITS:
        print(f"{split}: {sum(row[0] == split for row in rows)} imagens em fundo/")


if __name__ == "__main__":
    main()
