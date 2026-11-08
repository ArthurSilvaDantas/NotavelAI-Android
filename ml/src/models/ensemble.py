"""Modelo final do NotavelAI: os modelos que o app roda e a média das probabilidades deles.

Hoje é um modelo só (notavelai): MobileNetV2 inteira ajustada, com corte central da imagem,
treinada com o BRL Dataset, a classe fundo, as fotos do celular, o banknoteBrazil, as composições e
as 1142 fotos originais de treino do dataset "Cédulas do Real" (Kaggle). Na validação, ele superou
as combinações de dois modelos usadas em versões anteriores. Com mais de um membro, a resposta seria
a média.

O app só anuncia um valor com confiança de pelo menos 70% (THRESHOLD), limiar escolhido na
validação, não no teste. O app faz a mesma conta em ClassificadorCedulas.java.

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
    "notavelai.tflite": MODELS_DIR / "notavelai.keras",
}
LABELS_PATH = MODELS_DIR / "labels.txt"
# Abaixo disso o app diz que não conseguiu identificar, em vez de falar um valor.
THRESHOLD = 0.7


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
