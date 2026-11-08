"""Testes do contrato do modelo com o app Android."""

import json

import keras
import numpy as np
import pytest

from src.data.dataset import CLASSES
from src.models import ensemble
from src.models.ensemble import LABELS_PATH, MEMBERS, run_tflite
from src.models.train_model import CLASS_NAMES_PATH, build_augmentation, class_names

KERAS_MEMBERS = [path for path in MEMBERS.values() if path.exists()]
EXPORTED = [(ensemble.MODELS_DIR / name, source) for name, source in MEMBERS.items() if (ensemble.MODELS_DIR / name).exists()]


def test_output_order_is_numeric_not_alphabetical():
    assert class_names()[: len(CLASSES)] == ["2", "5", "10", "20", "50", "100", "200"]


def test_augmentation_is_inactive_outside_training():
    images = np.random.default_rng(0).uniform(0, 255, (2, 224, 224, 3)).astype("float32")
    output = build_augmentation()(images, training=False)
    assert np.allclose(keras.ops.convert_to_numpy(output), images)


@pytest.mark.parametrize("path", KERAS_MEMBERS, ids=lambda path: path.stem)
def test_saved_model_contract(path):
    model = keras.models.load_model(path)
    names = [layer.name for layer in model.layers]
    assert model.input_shape == (None, 224, 224, 3)
    assert "normalizacao" in names
    assert not any("random" in name or "augmentation" in name for name in names)

    normalization = model.get_layer("normalizacao")
    limits = keras.ops.convert_to_numpy(normalization(np.array([[0.0, 255.0]])))
    assert np.allclose(limits, [[-1.0, 1.0]])

    assert model.output_shape[-1] == len(json.loads(CLASS_NAMES_PATH.read_text()))


@pytest.mark.skipif(not LABELS_PATH.exists(), reason="modelo ainda não exportado")
def test_labels_follow_output_order():
    assert LABELS_PATH.read_text(encoding="utf-8").splitlines() == json.loads(CLASS_NAMES_PATH.read_text())


@pytest.mark.parametrize("tflite, source", EXPORTED, ids=lambda value: value.stem)
def test_tflite_matches_keras(tflite, source):
    images = np.random.default_rng(0).uniform(0, 255, (3, 224, 224, 3)).astype("float32")
    expected = keras.models.load_model(source).predict(images, verbose=0)
    probabilities, _ = run_tflite(tflite, images)
    assert np.allclose(probabilities, expected, atol=1e-3)


@pytest.mark.skipif(len(EXPORTED) < len(MEMBERS), reason="modelo ainda não exportado")
def test_final_model_is_mean_of_members():
    images = np.random.default_rng(1).uniform(0, 255, (2, 224, 224, 3)).astype("float32")
    members = [run_tflite(path, images)[0] for path, _ in EXPORTED]
    final = ensemble.predict(images)
    assert np.allclose(final, np.mean(members, axis=0))
    assert final.sum(axis=1) == pytest.approx([1.0, 1.0], abs=1e-4)
