import os
import sys
import types
import numpy as np
import pytest

# Ensure project src is importable when running tests directly.
PROJECT_SRC = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "src"))
if PROJECT_SRC not in sys.path:
    sys.path.insert(0, PROJECT_SRC)


# Provide a stub fast_plate_ocr module if the real package is unavailable.
if "fast_plate_ocr" not in sys.modules:
    fast_plate_ocr_stub = types.ModuleType("fast_plate_ocr")
    fast_plate_ocr_stub.LicensePlateRecognizer = object  # placeholder, monkeypatched in tests
    sys.modules["fast_plate_ocr"] = fast_plate_ocr_stub

from TextExtraction.ocr_reader import OCRReader


class _FakeRecognizer:
    def __init__(self, model_name):
        self.model_name = model_name
        self.run_calls = 0
        self.raise_error = False
        self.return_text = "ABC123"

    def run(self, img):
        self.run_calls += 1
        if self.raise_error:
            raise RuntimeError("fail")
        return self.return_text


def test_extract_text_success(monkeypatch):
    fake_recognizer = _FakeRecognizer("cct-xs-v1-global-model")
    monkeypatch.setattr("TextExtraction.ocr_reader.LicensePlateRecognizer", lambda name: fake_recognizer)
    reader = OCRReader()
    img = np.ones((5, 5, 3), dtype=np.uint8)

    result = reader.extract_text(img)

    assert result == ("ABC123", 100.0)
    assert fake_recognizer.run_calls == 1


def test_extract_text_returns_none_on_empty_input(monkeypatch):
    monkeypatch.setattr("TextExtraction.ocr_reader.LicensePlateRecognizer", lambda name: _FakeRecognizer(name))
    reader = OCRReader()

    assert reader.extract_text(None) is None
    assert reader.extract_text(np.zeros((0, 0, 3), dtype=np.uint8)) is None


def test_extract_text_handles_recognizer_error(monkeypatch):
    fake_recognizer = _FakeRecognizer("cct-xs-v1-global-model")
    fake_recognizer.raise_error = True
    monkeypatch.setattr("TextExtraction.ocr_reader.LicensePlateRecognizer", lambda name: fake_recognizer)
    reader = OCRReader()
    img = np.ones((5, 5, 3), dtype=np.uint8)

    assert reader.extract_text(img) is None
    assert fake_recognizer.run_calls == 1


def test_extract_text_handles_empty_string(monkeypatch):
    fake_recognizer = _FakeRecognizer("cct-xs-v1-global-model")
    fake_recognizer.return_text = ""
    monkeypatch.setattr("TextExtraction.ocr_reader.LicensePlateRecognizer", lambda name: fake_recognizer)
    reader = OCRReader()
    img = np.ones((2, 2, 3), dtype=np.uint8)

    assert reader.extract_text(img) is None
    assert fake_recognizer.run_calls == 1
