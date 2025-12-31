import os
import sys
import types
import numpy as np
import pytest

# Ensure project src is importable when running tests directly.
PROJECT_SRC = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "src"))
if PROJECT_SRC not in sys.path:
    sys.path.insert(0, PROJECT_SRC)

# Provide a stub ultralytics module if the real package is unavailable.
if "ultralytics" not in sys.modules:
    ultralytics_stub = types.ModuleType("ultralytics")
    ultralytics_stub.YOLO = lambda *args, **kwargs: None
    sys.modules["ultralytics"] = ultralytics_stub

from PlateProcessor.yolo_detector import YOLODetector, Box


class _FakeBoxes:
    """Mimics ultralytics result boxes with cpu()->numpy() chain."""

    def __init__(self, xyxy_array, cls_array):
        self.xyxy = xyxy_array
        self.cls = cls_array

    def cpu(self):
        return self

    def numpy(self):
        return self


class _FakeResult:
    def __init__(self, xyxy_array, cls_array):
        self.boxes = _FakeBoxes(xyxy_array, cls_array)


class _FakeModel:
    """Stub YOLO model returning a single box."""

    def __call__(self, image, classes=None):
        xyxy = np.array([[10, 20, 50, 60]], dtype=float)
        cls = np.array([2], dtype=float)
        return [_FakeResult(xyxy, cls)]


def test_box_wraps_xyxy_and_cls():
    box = Box((1, 2, 3, 4), 7)
    assert box.xyxy.shape == (1, 4)
    assert box.cls == [7]
    np.testing.assert_array_equal(box.xyxy[0], np.array([1, 2, 3, 4]))


def test_detect_plate_returns_boxes(monkeypatch):
    monkeypatch.setattr("PlateProcessor.yolo_detector.YOLO", lambda path: _FakeModel())
    detector = YOLODetector(model_path="dummy", plate_class_id=2)
    image = np.zeros((100, 100, 3), dtype=np.uint8)

    boxes = detector.detect_plate(image)

    assert len(boxes) == 1
    assert isinstance(boxes[0], Box)
    np.testing.assert_array_equal(boxes[0].xyxy[0], np.array([10, 20, 50, 60]))
    assert boxes[0].cls == [2]


def test_detect_plate_empty_results(monkeypatch):
    class _EmptyModel:
        def __call__(self, image, classes=None):
            return []

    monkeypatch.setattr("PlateProcessor.yolo_detector.YOLO", lambda path: _EmptyModel())
    detector = YOLODetector(model_path="dummy", plate_class_id=2)
    image = np.zeros((50, 50, 3), dtype=np.uint8)

    boxes = detector.detect_plate(image)

    assert boxes == []


def test_crop_plate_extracts_region():
    image = np.arange(10 * 10 * 3, dtype=np.uint8).reshape((10, 10, 3))
    cropped = YOLODetector.crop_plate(image, (2, 2, 5, 6))
    assert cropped.shape == (4, 3, 3)
    np.testing.assert_array_equal(cropped[0, 0], image[2, 2])
    np.testing.assert_array_equal(cropped[-1, -1], image[5, 4])


def test_detect_plate_yolo_returns_none_when_model_missing(monkeypatch):
    monkeypatch.setattr(os.path, "exists", lambda _: False)
    image = np.zeros((20, 20, 3), dtype=np.uint8)

    result = YOLODetector.detect_plate_yolo(image, "missing.pt")

    assert result is None


def test_detect_plate_yolo_expands_and_resizes(monkeypatch):
    monkeypatch.setattr(YOLODetector, "__init__", lambda self, model_path, plate_class_id: None)
    monkeypatch.setattr(
        YOLODetector,
        "detect_plate",
        lambda self, image: [Box((5, 5, 15, 15), 0)],
    )
    monkeypatch.setattr(os.path, "exists", lambda _: True)

    image = np.zeros((40, 80, 3), dtype=np.uint8)
    result = YOLODetector.detect_plate_yolo(
        image,
        model_path="dummy.pt",
        expand_ratio=0.1,
        output_size=(64, 32),
    )

    assert result is not None
    assert result.shape == (32, 64, 3)


def test_detect_plate_yolo_no_boxes(monkeypatch):
    monkeypatch.setattr(os.path, "exists", lambda _: True)
    monkeypatch.setattr(YOLODetector, "__init__", lambda self, model_path, plate_class_id: None)
    monkeypatch.setattr(YOLODetector, "detect_plate", lambda self, image: [])

    image = np.zeros((10, 10, 3), dtype=np.uint8)
    result = YOLODetector.detect_plate_yolo(image, "dummy.pt")

    assert result is None


def test_detect_plate_yolo_handles_init_failure(monkeypatch):
    class _Boom(Exception):
        pass

    def boom_init(self, model_path, plate_class_id):
        raise _Boom("init failed")

    monkeypatch.setattr(os.path, "exists", lambda _: True)
    monkeypatch.setattr(YOLODetector, "__init__", boom_init)

    image = np.zeros((10, 10, 3), dtype=np.uint8)
    assert YOLODetector.detect_plate_yolo(image, "dummy.pt") is None
