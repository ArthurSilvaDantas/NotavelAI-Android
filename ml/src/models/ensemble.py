"""Modelo final do NotavelAI: a média das probabilidades de dois treinos da mesma MobileNetV2.

- base: recortes do BRL Dataset, classe fundo, fotos do celular e composições.
- external: o mesmo, mais o segundo lote de fotos do celular (com dólar como fundo) e 500 fotos do
  dataset banknoteBrazil.

Com o limiar de 50% do app, a média anuncia menos valores errados que cada treino sozinho (ver
README). O app faz a mesma conta em ClassificadorCedulas.java.

Uso: python -m src.models.ensemble   # converte os membros para .tflite e gera labels.txt
"""

import json
import time
from pathlib import Path

import numpy as np
import tensorflow as tf

from src.models.train_model import CLASS_NAMES_PATH, MODELS_DIR

# Nome do .tflite no app -> modelo Keras de origem
MEMBERS = {
    "notavelai_base.tflite": MODELS_DIR / "notavelai_base.keras",
    "notavelai_external.tflite": MODELS_DIR / "notavelai_external.keras",
}
LABELS_PATH = MODELS_DIR / "labels.txt"
# Abaixo disso o app diz que não conseguiu identificar, em vez de falar um valor.
THRESHOLD = 0.5


def tflite_paths() -> list[Path]:
    return [MODELS_DIR / name for name in MEMBERS]


def convert_float32(model) -> bytes:
    return tf.lite.TFLiteConverter.from_keras_model(model).convert()


def export() -> None:
    """Converte cada membro para .tflite float32 e grava labels.txt na ordem das saídas."""
    import keras

    for name, source in MEMBERS.items():
        (MODELS_DIR / name).write_bytes(convert_float32(keras.models.load_model(source)))
    names = json.loads(CLASS_NAMES_PATH.read_text(encoding="utf-8"))
    LABELS_PATH.write_text("\n".join(names) + "\n", encoding="utf-8")


def run_tflite(path: Path, images: np.ndarray, num_threads: int = 1) -> tuple[np.ndarray, float]:
    """Devolve as probabilidades e a mediana em ms por imagem, ignorando a primeira execução."""
    interpreter = tf.lite.Interpreter(model_path=str(path), num_threads=num_threads)
    interpreter.allocate_tensors()
    input_index = interpreter.get_input_details()[0]["index"]
    output_index = interpreter.get_output_details()[0]["index"]

    probabilities, times = [], []
    for image in images:
        interpreter.set_tensor(input_index, image[None].astype("float32"))
        start = time.perf_counter()
        interpreter.invoke()
        times.append(time.perf_counter() - start)
        probabilities.append(interpreter.get_tensor(output_index)[0].copy())
    return np.stack(probabilities), float(np.median(times[1:]) * 1000) if len(times) > 1 else 0.0


def predict(images: np.ndarray) -> np.ndarray:
    """Média das probabilidades dos membros, como no app."""
    return np.mean([run_tflite(path, images)[0] for path in tflite_paths()], axis=0)


def main() -> None:
    export()
    for path in [*tflite_paths(), LABELS_PATH]:
        print(f"{path}: {path.stat().st_size / 1e6:.2f} MB")


if __name__ == "__main__":
    main()
