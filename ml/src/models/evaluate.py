"""Mede o modelo final na validação e em fotos reais, do mesmo jeito que o app.

Conjuntos: validação (recortes do BRL Dataset e metade do teste do Kaggle), fotos do celular do
grupo (data/phone_test/), a outra metade do teste do Kaggle (data/kaggle_test/) e o teste extra
dos autores do Kaggle (data/kaggle_extra/): fotos reais fora do treino, a métrica principal. O split test do BRL Dataset fica de fora. A latência é a do computador que roda o script, não a de um
celular.

Saídas:
- reports/evaluation.csv: acertos e valores anunciados por modelo e conjunto.
- reports/phone_predictions.csv: resposta do modelo final para cada foto do celular.
"""

import csv
import json

import keras
import numpy as np

from src.data.dataset import PROCESSED_DIR, REPORTS_DIR
from src.data.prepare_kaggle import KAGGLE_EXTRA_DIR, KAGGLE_TEST_DIR
from src.data.prepare_own_photos import OWN_TEST_DIR
from src.models import ensemble
from src.models.predict import expand, load
from src.models.train_model import CLASS_NAMES_PATH

SETS = {
    "validation": PROCESSED_DIR / "valid",
    "phone": OWN_TEST_DIR,
    "kaggle": KAGGLE_TEST_DIR,
    "kaggle_extra": KAGGLE_EXTRA_DIR,
}
SUMMARY_PATH = REPORTS_DIR / "evaluation.csv"
PHOTOS_PATH = REPORTS_DIR / "phone_predictions.csv"


def spoken(scores: np.ndarray, names: list[str]) -> str | None:
    """Valor que o app fala, ou None quando não anuncia (sem cédula ou confiança abaixo do limiar)."""
    best = int(np.argmax(scores))
    return names[best] if names[best] != "fundo" and scores[best] >= ensemble.THRESHOLD else None


def main() -> None:
    names = json.loads(CLASS_NAMES_PATH.read_text(encoding="utf-8"))
    data = {}
    for set_name, directory in SETS.items():
        files = expand([directory])
        data[set_name] = (files, np.stack([load(file) for file in files]), [file.parent.name for file in files])

    probabilities = {}  # modelo -> conjunto -> probabilidades
    MEMBER_NAMES = [name.removeprefix("notavelai_").removesuffix(".tflite") for name in ensemble.MEMBERS]
    for name, path in zip(MEMBER_NAMES, ensemble.tflite_paths()):
        probabilities[name] = {set_name: ensemble.run_tflite(path, images)[0] for set_name, (_, images, _) in data.items()}
    if len(MEMBER_NAMES) > 1:
        probabilities["final"] = {
            set_name: np.mean([probabilities[name][set_name] for name in MEMBER_NAMES], axis=0) for set_name in SETS
        }
    final = "final" if "final" in probabilities else MEMBER_NAMES[0]

    # O .tflite precisa dar as mesmas respostas do Keras de origem.
    for (name, path), source in zip(zip(MEMBER_NAMES, ensemble.tflite_paths()), ensemble.MEMBERS.values()):
        model = keras.models.load_model(source)
        differences = sum(
            int((model.predict(images, verbose=0).argmax(1) != probabilities[name][set_name].argmax(1)).sum())
            for set_name, (_, images, _) in data.items()
        )
        print(f"{path.name}: {differences} previsões diferentes do Keras")

    rows = []
    for name, by_set in probabilities.items():
        row = {"model": name}
        for set_name, (_, _, real) in data.items():
            scores = by_set[set_name]
            said = [spoken(s, names) for s in scores]
            row[f"{set_name}_correct"] = f"{sum(names[int(np.argmax(s))] == r for s, r in zip(scores, real))}/{len(real)}"
            row[f"{set_name}_spoken_right"] = sum(value is not None and value == r for value, r in zip(said, real))
            row[f"{set_name}_spoken_wrong"] = sum(value is not None and value != r for value, r in zip(said, real))
        rows.append(row)
        print(row)

    all_images = np.concatenate([images for _, images, _ in data.values()])
    latency = sum(ensemble.run_tflite(path, all_images[:50])[1] for path in ensemble.tflite_paths())
    size = sum(path.stat().st_size for path in ensemble.tflite_paths()) / 1e6
    print(f"Modelo final: {size:.1f} MB, {latency:.1f} ms por foto (1 thread, neste computador)")

    with SUMMARY_PATH.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)

    files, _, real = data["phone"]
    with PHOTOS_PATH.open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow(["file", "true_label", "predicted", "confidence", "app_says"])
        for path, truth, scores in zip(files, real, probabilities[final]["phone"]):
            value = spoken(scores, names)
            answer = f"R$ {value}" if value else ("no banknote" if names[int(np.argmax(scores))] == "fundo" else "not identified")
            writer.writerow([path.name, truth, names[int(np.argmax(scores))], f"{scores.max():.3f}", answer])


if __name__ == "__main__":
    main()
