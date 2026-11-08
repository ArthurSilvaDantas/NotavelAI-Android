"""Treina o classificador de cédulas (MobileNetV2) em duas fases e salva o melhor modelo.

Fase 1: base congelada, treina só a cabeça. Fase 2: fine-tuning das últimas camadas da base.
Usa apenas train e valid; o split de teste fica reservado para a Etapa 3.

Grava em models/notavelai.keras. O app usa a média de dois treinos (src/models/ensemble.py): para
trocar um deles, renomeie o arquivo para o nome do membro e rode src.models.ensemble.

A normalização faz parte do grafo do modelo: ele recebe 224x224 RGB em float com valores 0-255.
As augmentations rodam no tf.data, fora do modelo, para não entrarem no .tflite exportado.
"""

import json

import keras
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import tensorflow as tf

from src.data.dataset import CLASSES, FIGURES_DIR, PROCESSED_DIR, REPORTS_DIR, ROOT

MODELS_DIR = ROOT / "models"
MODEL_PATH = MODELS_DIR / "notavelai.keras"
CLASS_NAMES_PATH = MODELS_DIR / "classes.json"

IMAGE_SIZE = (224, 224)
BATCH_SIZE = 32
SEED = 42

HEAD_EPOCHS = 30
HEAD_LEARNING_RATE = 1e-3
FINE_EPOCHS = 30
FINE_LEARNING_RATE = 1e-5
FINE_TUNE_LAYERS = 30
PATIENCE = 6
DROPOUT = 0.3
MAX_IMBALANCE = 1.5  # maior/menor classe acima disso liga o class_weight


def class_names() -> list[str]:
    """Ordem numérica, fixada aqui: a ordem alfabética do Keras poria "100" antes de "2"."""
    has_background = any((PROCESSED_DIR / "train" / "fundo").glob("*"))
    return [*CLASSES, "fundo"] if has_background else list(CLASSES)


def load_split(split: str, names: list[str]) -> tf.data.Dataset:
    # pad_to_aspect_ratio preserva a proporção: há recortes em pé e deitados, e esticá-los para
    # um quadrado distorceria cada grupo de um jeito diferente.
    return keras.utils.image_dataset_from_directory(
        PROCESSED_DIR / split,
        labels="inferred",
        label_mode="int",
        class_names=names,
        batch_size=None,
        image_size=IMAGE_SIZE,
        pad_to_aspect_ratio=True,
        shuffle=False,
    ).cache()


def build_augmentation() -> keras.Sequential:
    return keras.Sequential(
        [
            # Rotação de até 180°: a pessoa pode apontar a câmera para a nota em qualquer ângulo.
            keras.layers.RandomRotation(0.5, fill_mode="constant", seed=SEED),
            keras.layers.RandomZoom(0.2, fill_mode="constant", seed=SEED),
            keras.layers.RandomFlip("horizontal", seed=SEED),
            keras.layers.RandomBrightness(0.2, value_range=(0, 255), seed=SEED),
            keras.layers.RandomContrast(0.2, value_range=(0, 255), seed=SEED),
        ],
        name="augmentation",
    )


def build_model(num_classes: int) -> tuple[keras.Model, keras.Model]:
    base = keras.applications.MobileNetV2(
        input_shape=(*IMAGE_SIZE, 3), include_top=False, weights="imagenet"
    )
    base.trainable = False

    inputs = keras.Input(shape=(*IMAGE_SIZE, 3), name="imagem_0_255")
    x = keras.layers.Rescaling(1 / 127.5, offset=-1, name="normalizacao")(inputs)
    # training=False mantém o BatchNorm em modo de inferência também no fine-tuning.
    x = base(x, training=False)
    x = keras.layers.GlobalAveragePooling2D()(x)
    x = keras.layers.Dropout(DROPOUT)(x)
    outputs = keras.layers.Dense(num_classes, activation="softmax", name="classes")(x)
    return keras.Model(inputs, outputs, name="notavelai"), base


def compute_class_weight(train: tf.data.Dataset, num_classes: int) -> dict[int, float] | None:
    labels = np.array([int(label) for _, label in train.as_numpy_iterator()])
    counts = np.bincount(labels, minlength=num_classes)
    ratio = counts.max() / counts.min()
    print(f"Treino: {len(labels)} recortes; maior/menor classe = {ratio:.2f}")
    if ratio <= MAX_IMBALANCE:
        print("class_weight desligado")
        return None
    print("class_weight ligado")
    return {i: len(labels) / (num_classes * count) for i, count in enumerate(counts)}


def fit_phase(model, train, valid, epochs, learning_rate, class_weight, best_so_far=None):
    model.compile(
        optimizer=keras.optimizers.Adam(learning_rate),
        loss=keras.losses.SparseCategoricalCrossentropy(),
        metrics=["accuracy"],
    )
    callbacks = [
        keras.callbacks.EarlyStopping(monitor="val_loss", patience=PATIENCE, restore_best_weights=True),
        # O limiar impede a fase 2 de sobrescrever a fase 1 com um modelo pior.
        keras.callbacks.ModelCheckpoint(
            MODEL_PATH, monitor="val_loss", save_best_only=True, initial_value_threshold=best_so_far
        ),
    ]
    history = model.fit(
        train, validation_data=valid, epochs=epochs, class_weight=class_weight, callbacks=callbacks, verbose=2
    )
    return history.history


def plot_history(head: dict, fine: dict) -> None:
    train_color, valid_color = "#2a78d6", "#eb6834"
    surface, ink, muted, grid = "#fcfcfb", "#0b0b0b", "#52514e", "#e8e7e3"
    split = len(head["loss"])
    epochs = np.arange(1, split + len(fine["loss"]) + 1)

    figure, axes = plt.subplots(1, 2, figsize=(11, 4.2), facecolor=surface)
    for axis, metric, title in ((axes[0], "loss", "Perda"), (axes[1], "accuracy", "Acurácia")):
        axis.set_facecolor(surface)
        for key, label, color in ((metric, "treino", train_color), (f"val_{metric}", "validação", valid_color)):
            values = head[key] + fine[key]
            axis.plot(epochs, values, color=color, linewidth=2, label=label)
            axis.annotate(label, (epochs[-1], values[-1]), xytext=(6, 0), textcoords="offset points",
                          color=ink, fontsize=9, va="center")
        axis.axvline(split + 0.5, color=muted, linewidth=1, linestyle=(0, (3, 3)))
        axis.text(split + 0.7, 1.02, "fine-tuning", transform=axis.get_xaxis_transform(), color=muted, fontsize=9)
        axis.set_title(title, loc="left", color=ink, fontsize=12, pad=16)
        axis.set_xlabel("Época", color=muted, fontsize=9)
        axis.set_xmargin(0.02)
        axis.grid(axis="y", color=grid, linewidth=1)
        axis.tick_params(colors=muted, labelsize=9, length=0)
        axis.spines[["top", "right", "left"]].set_visible(False)
        axis.spines["bottom"].set_color(grid)
        axis.legend(frameon=False, fontsize=9, labelcolor=ink, loc="best")
    axes[1].set_ylim(0, 1.0)
    figure.tight_layout()
    FIGURES_DIR.mkdir(parents=True, exist_ok=True)
    figure.savefig(FIGURES_DIR / "training_curves.png", dpi=130)


def main() -> None:
    keras.utils.set_random_seed(SEED)
    MODELS_DIR.mkdir(exist_ok=True)

    names = class_names()
    print(f"Classes ({len(names)}): {names}")
    if "fundo" not in names:
        print("AVISO: sem data/processed/train/fundo; o modelo não saberá responder 'sem cédula'.")

    train_samples = load_split("train", names)
    valid = load_split("valid", names).batch(BATCH_SIZE).prefetch(tf.data.AUTOTUNE)
    class_weight = compute_class_weight(train_samples, len(names))

    augmentation = build_augmentation()
    train = (
        train_samples.shuffle(2048, seed=SEED, reshuffle_each_iteration=True)
        .batch(BATCH_SIZE)
        .map(lambda images, labels: (augmentation(images, training=True), labels), num_parallel_calls=tf.data.AUTOTUNE)
        .prefetch(tf.data.AUTOTUNE)
    )

    model, base = build_model(len(names))
    model.summary()

    print("\n=== Fase 1: cabeça ===")
    head = fit_phase(model, train, valid, HEAD_EPOCHS, HEAD_LEARNING_RATE, class_weight)
    best_head = min(head["val_loss"])

    print(f"\n=== Fase 2: fine-tuning das últimas {FINE_TUNE_LAYERS} camadas ===")
    base.trainable = True
    for layer in base.layers[:-FINE_TUNE_LAYERS]:
        layer.trainable = False
    # BatchNorm só treina pesos se estiver em modo de treino; congelado por clareza e segurança.
    for layer in base.layers:
        if isinstance(layer, keras.layers.BatchNormalization):
            layer.trainable = False
    fine = fit_phase(model, train, valid, FINE_EPOCHS, FINE_LEARNING_RATE, class_weight, best_head)

    CLASS_NAMES_PATH.write_text(json.dumps(names), encoding="utf-8")
    REPORTS_DIR.mkdir(exist_ok=True)
    (REPORTS_DIR / "training_history.json").write_text(json.dumps({"head": head, "fine_tuning": fine}, indent=2))
    plot_history(head, fine)

    best = keras.models.load_model(MODEL_PATH)
    val_loss, val_accuracy = best.evaluate(valid, verbose=0)
    print(f"\nMelhor modelo: {MODEL_PATH}")
    print(f"Validação: loss={val_loss:.4f} acurácia={val_accuracy:.4f}")
    print(f"Melhor val_loss: fase 1={best_head:.4f}, fase 2={min(fine['val_loss']):.4f}")


if __name__ == "__main__":
    main()
