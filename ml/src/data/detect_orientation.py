"""Gera config/orientation.csv: a rotação de cada arquivo que alinha a foto às suas caixas.

As caixas do COCO se referem à foto orientada, mas os JPEGs exportados não têm tag EXIF e podem
estar girados: 90°/270° (dimensões trocadas em relação ao JSON) ou 180° (dimensões iguais, o que
nenhuma checagem de tamanho detecta).

Só as fotos de celular (`AAAAMMDD_HHMMSS*`) perderam a orientação. As demais famílias (webcam,
WhatsApp, imagens 640x640 com padding) ficam em 0°: nelas o critério abaixo erra por ter pouco
sinal, como mostrou a conferência visual.

Critério: as caixas cobrem objetos com muito detalhe, então a razão entre a energia de borda
dentro e fora das caixas é maior na rotação certa. A tabela é versionada; casos de margem baixa
são listados para conferência visual.
"""

import csv
import re

import numpy as np
from PIL import Image

from src.data.dataset import ORIENTATION_TABLE, Source, candidate_rotations, load_all, rotate

MAX_THUMB_SIDE = 500  # o critério não precisa de resolução total
LOW_MARGIN = 1.15  # melhor/segundo melhor abaixo disso vai para conferência visual
CAMERA_PHOTO = re.compile(r"^\d{8}_\d{6}_")


def box_contrast(gray: np.ndarray, source: Source, scale: float) -> float:
    edges = np.abs(np.diff(gray, axis=0))[:, :-1] + np.abs(np.diff(gray, axis=1))[:-1, :]
    mask = np.zeros(edges.shape, dtype=bool)
    for box in source.boxes:
        x0, y0 = int(box.x * scale), int(box.y * scale)
        x1, y1 = int((box.x + box.w) * scale), int((box.y + box.h) * scale)
        mask[max(y0, 0) : y1, max(x0, 0) : x1] = True
    if mask.all() or not mask.any():
        return 1.0
    return float(edges[mask].mean() / (edges[~mask].mean() + 1e-6))


def score_rotations(source: Source) -> dict[int, float]:
    with Image.open(source.path) as image:
        candidates = candidate_rotations(image.size, source)
        scale = min(1.0, MAX_THUMB_SIDE / max(image.size))
        thumb = image.convert("L").resize((round(image.width * scale), round(image.height * scale)))
    return {
        degrees: box_contrast(np.asarray(rotate(thumb, degrees), dtype=np.float32), source, scale)
        for degrees in candidates
    }


def main() -> None:
    rows = []
    for source in load_all():
        scores = score_rotations(source)
        ranked = sorted(scores, key=scores.get, reverse=True)
        best, second = ranked[0], ranked[1]
        margin = scores[best] / max(scores[second], 1e-6)
        is_camera = CAMERA_PHOTO.match(source.path.name) is not None
        chosen = best if is_camera else 0
        rows.append([source.split, source.path.name, chosen, round(scores[chosen], 3), round(margin, 3), is_camera])

    ORIENTATION_TABLE.parent.mkdir(exist_ok=True)
    with ORIENTATION_TABLE.open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow(["split", "file", "rotation_degrees", "contrast", "margin", "phone_photo"])
        writer.writerows(sorted(rows, key=lambda row: row[1]))

    print(f"Imagens: {len(rows)}")
    for degrees in (0, 90, 180, 270):
        print(f"  {degrees:>3}°: {sum(row[2] == degrees for row in rows)}")
    low = [row for row in rows if row[5] and row[4] < LOW_MARGIN]
    print(f"Margem < {LOW_MARGIN} (conferir visualmente): {len(low)}")


if __name__ == "__main__":
    main()
