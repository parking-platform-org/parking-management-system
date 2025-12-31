import os
import sys
import types
import numpy as np
import pytest

# Ensure project src is importable.
PROJECT_SRC = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "src"))
if PROJECT_SRC not in sys.path:
    sys.path.insert(0, PROJECT_SRC)

# Stub config.settings to avoid FileNotFoundError on import.
config_module = types.ModuleType("config")
settings_stub = types.ModuleType("config.settings")
settings_stub.YOLO_EXPAND_RATIO = 0.1
settings_stub.YOLO_OUTPUT_SIZE = (10, 5)
settings_stub.SHOW_IMAGE_DELAY_MS = 50
sys.modules["config"] = config_module
sys.modules["config.settings"] = settings_stub

# Stub ultralytics if missing.
if "ultralytics" not in sys.modules:
    ultralytics_stub = types.ModuleType("ultralytics")
    ultralytics_stub.YOLO = lambda *args, **kwargs: None
    sys.modules["ultralytics"] = ultralytics_stub

# Stub fast_plate_ocr if missing.
if "fast_plate_ocr" not in sys.modules:
    fpo_stub = types.ModuleType("fast_plate_ocr")
    fpo_stub.LicensePlateRecognizer = object
    sys.modules["fast_plate_ocr"] = fpo_stub

# Stub cv2 if missing.
if "cv2" not in sys.modules:
    cv2_stub = types.ModuleType("cv2")
    cv2_stub.imshow = lambda *args, **kwargs: None
    cv2_stub.waitKey = lambda *args, **kwargs: 0
    cv2_stub.destroyAllWindows = lambda *args, **kwargs: None
    sys.modules["cv2"] = cv2_stub

from core.workflow import PlateRecognizer


class _FakeOCR:
    def __init__(self, model_name):
        self.model_name = model_name
        self.calls = 0
        self.return_value = ("ABC123", 99.0)

    def extract_text(self, img):
        self.calls += 1
        return self.return_value


def test_process_happy_path(monkeypatch):
    fake_ocr = _FakeOCR("model")
    monkeypatch.setattr("core.workflow.OCRReader", lambda model_name: fake_ocr)
    monkeypatch.setattr(
        "core.workflow.YOLODetector.detect_plate_yolo",
        lambda image, model_path, expand_ratio, output_size: np.ones((5, 10, 3), dtype=np.uint8),
    )

    recognizer = PlateRecognizer(yolo_model_name="dummy.pt", ocr_model_name="model")
    image = np.zeros((20, 20, 3), dtype=np.uint8)

    text, confidence = recognizer.process(image, DEBUG=False)

    assert (text, confidence) == ("ABC123", 99.0)
    assert fake_ocr.calls == 1


def test_process_no_detection_then_ocr_none(monkeypatch, capsys):
    fake_ocr = _FakeOCR("model")
    fake_ocr.return_value = None
    monkeypatch.setattr("core.workflow.OCRReader", lambda model_name: fake_ocr)
    monkeypatch.setattr(
        "core.workflow.YOLODetector.detect_plate_yolo",
        lambda image, model_path, expand_ratio, output_size: None,
    )
    monkeypatch.setattr("core.workflow.show_image", lambda *args, **kwargs: None)

    recognizer = PlateRecognizer(yolo_model_name="dummy.pt", ocr_model_name="model")
    image = np.zeros((10, 10, 3), dtype=np.uint8)

    assert recognizer.process(image, DEBUG=True) is None
    out = capsys.readouterr().out
    assert "OCR failed" in out or "WARNING" in out or out == ""


def test_process_invalid_input_raises_system_exit():
    recognizer = PlateRecognizer(yolo_model_name="dummy.pt", ocr_model_name="model")
    with pytest.raises(SystemExit):
        recognizer.process(None)
