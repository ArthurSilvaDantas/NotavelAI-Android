"""Constantes e leitura do export COCO do Roboflow, compartilhadas pelos scripts da Etapa 1."""

import csv
import json
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SPLITS = ("train", "valid", "test")
PROCESSED_DIR = ROOT / "data" / "processed"
REPORTS_DIR = ROOT / "reports"
FIGURES_DIR = REPORTS_DIR / "figures"
ORIENTATION_TABLE = ROOT / "config" / "orientation.csv"

SOURCE_DIR = ROOT / "data" / "raw" / "BRL.v1i.coco"

# Nome normalizado no COCO -> pasta da classe. A ordem define a ordem das tabelas.
BANKNOTES = {
    "2 reais": "2",
    "5 reais": "5",
    "10 reais": "10",
    "20 reais": "20",
    "50 reais": "50",
    "100 reais": "100",
    "200 reais": "200",
}
COINS = {"1 real", "10 cents", "25 cents", "5 cents", "50 cents"}
# Classe "pai" que o Roboflow adiciona com id 0; não tem caixas.
PARENT_CATEGORY = "objetos-brazilian-currency"
CLASSES = tuple(BANKNOTES.values())

# Transpose do Pillow gira no sentido anti-horário, por isso 90° horário é ROTATE_270.
_TRANSPOSE = {
    90: Image.Transpose.ROTATE_270,
    180: Image.Transpose.ROTATE_180,
    270: Image.Transpose.ROTATE_90,
}

# Fotos que são a mesma imagem de outra em split diferente, com pixels não idênticos (o hash
# exato não as pega). Confirmadas visualmente; ver find_near_duplicates.py.
CONFIRMED_NEAR_DUPLICATES: tuple[str, ...] = (
    "60-200_",  # mesma imagem de test/91-200, em resolução menor
)


@dataclass(frozen=True)
class Box:
    id: int
    category: str  # nome normalizado
    x: float
    y: float
    w: float
    h: float


@dataclass(frozen=True)
class Source:
    split: str
    path: Path
    width: int  # largura segundo o JSON (sistema de coordenadas das caixas)
    height: int
    boxes: tuple[Box, ...]


def normalize_category(name: str) -> str:
    return " ".join(name.lower().split())


def load_split(split: str, source_dir: Path = SOURCE_DIR) -> list[Source]:
    """Lê um split e falha se aparecer categoria desconhecida, em vez de descartá-la em silêncio."""
    split_dir = source_dir / split
    coco = json.loads((split_dir / "_annotations.coco.json").read_text(encoding="utf-8"))

    categories = {c["id"]: normalize_category(c["name"]) for c in coco["categories"]}
    unknown = set(categories.values()) - set(BANKNOTES) - COINS - {PARENT_CATEGORY}
    if unknown:
        raise ValueError(f"{split}: categorias desconhecidas no JSON: {sorted(unknown)}")

    boxes_by_image: dict[int, list[Box]] = {}
    for ann in coco["annotations"]:
        x, y, w, h = ann["bbox"]
        box = Box(ann["id"], categories[ann["category_id"]], x, y, w, h)
        boxes_by_image.setdefault(ann["image_id"], []).append(box)

    return [
        Source(
            split=split,
            path=split_dir / image["file_name"],
            width=image["width"],
            height=image["height"],
            boxes=tuple(boxes_by_image.get(image["id"], ())),
        )
        for image in coco["images"]
    ]


def load_all(source_dir: Path = SOURCE_DIR) -> list[Source]:
    return [source for split in SPLITS for source in load_split(split, source_dir)]


def candidate_rotations(file_size: tuple[int, int], source: Source) -> tuple[int, ...]:
    """Rotações (graus, sentido horário) que levam o arquivo às dimensões do JSON."""
    width, height = file_size
    if (width, height) not in ((source.width, source.height), (source.height, source.width)):
        raise ValueError(
            f"{source.path.name}: arquivo {file_size} incompatível com JSON "
            f"{(source.width, source.height)}"
        )
    if width == height:
        return (0, 90, 180, 270)
    return (0, 180) if (width, height) == (source.width, source.height) else (90, 270)


def rotate(image: Image.Image, degrees_clockwise: int) -> Image.Image:
    if degrees_clockwise == 0:
        return image
    return image.transpose(_TRANSPOSE[degrees_clockwise])


def load_orientations() -> dict[str, int]:
    """Tabela arquivo -> rotação, gerada e conferida por detect_orientation.py."""
    with ORIENTATION_TABLE.open(encoding="utf-8", newline="") as file:
        return {row["file"]: int(row["rotation_degrees"]) for row in csv.DictReader(file)}


def open_canonical(source: Source, orientations: dict[str, int]) -> Image.Image:
    """Abre a imagem em RGB, no mesmo sistema de coordenadas das caixas.

    O export não tem tag EXIF: o Roboflow anotou as fotos já orientadas e removeu a tag, então
    cada arquivo pode estar girado em relação às caixas. Ver detect_orientation.py.
    """
    with Image.open(source.path) as image:
        return rotate(image.convert("RGB"), orientations[source.path.name])


def print_table(rows: list[list], header: list[str]) -> None:
    table = [[str(cell) for cell in row] for row in [header, *rows]]
    widths = [max(len(row[i]) for row in table) for i in range(len(header))]
    for index, row in enumerate(table):
        print("  ".join(cell.rjust(width) for cell, width in zip(row, widths)))
        if index == 0:
            print("  ".join("-" * width for width in widths))
