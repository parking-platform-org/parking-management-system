"""
YOLO Detector Module
--------------------

Provides utilities for loading a YOLOv8 model, detecting license plates,
and cropping regions of interest from images.

Classes:
    Box: Simple container for bounding box and class ID.
    YOLODetector: Handles YOLO model inference and plate cropping utilities.

This module uses JavaDoc-style docstrings with @param, @return, and @raises.
"""

from ultralytics import YOLO
from typing import List, Tuple, Optional
import numpy as np
import os
import cv2


class Box:
    """Container for a single YOLO bounding box detection.

    Attributes:
        xyxy (np.ndarray): Bounding box coordinates shaped (1, 4) formatted as [x1, y1, x2, y2].
        cls (List[int]): The class ID associated with this detection.
    """

    def __init__(self, xyxy: Tuple[float, float, float, float], cls: int, conf: float):
        """
        Initializes a detected bounding box.

        @param xyxy: Tuple of coordinates (x1, y1, x2, y2)
        @param cls: Integer class ID of the detected object
        @param conf: Float confidence of the detected plate
        @raises TypeError: If xyxy is not a tuple of four numbers or cls is not int
        @postcondition: Creates a Box object with xyxy reshaped to (1, 4) and cls stored as a list
        """
        self.xyxy = np.array(xyxy).reshape(1, 4)
        self.cls = [cls]
        self.conf = [conf]


class YOLODetector:
    """YOLOv8-based object detector specialized for license plates.

    Attributes:
        model (YOLO): Loaded YOLOv8 model instance.
        plate_class_id (int): ID of the class representing license plates.
    """

    def __init__(self, model_path: str = "./src/models/yolov8n.pt", plate_class_id: int = 2):
        """
        Initializes the YOLO detector.

        @param model_path: Path to YOLOv8 weights file
        @param plate_class_id: Class ID used to filter license plate detections
        @raises FileNotFoundError: If model_path does not exist
        @postcondition: YOLO model is loaded and ready for inference
        """
        self.model = YOLO(model_path)
        self.plate_class_id = plate_class_id

    def detect_plate(self, image: np.ndarray) -> List[Box]:
        """
        Detects license plates in a given image.

        @param image: BGR image as a NumPy ndarray
        @return: List of Box objects representing detected plates
        @raises ValueError: If image is None or not a valid ndarray
        @postcondition: Returns empty list if no plates are detected
        """
        results = self.model(image, classes=[self.plate_class_id])
        boxes: List[Box] = []

        for r in results:
            xyxy_array = r.boxes.xyxy.cpu().numpy()
            cls_array = r.boxes.cls.cpu().numpy()
            conf_array = r.boxes.conf.cpu().numpy()
            for xyxy, cls_id, conf in zip(xyxy_array, cls_array, conf_array):
                boxes.append(Box(xyxy, int(cls_id), float(conf)))

        return boxes

    @staticmethod
    def crop_plate(image: np.ndarray, box: Tuple[int, int, int, int]) -> np.ndarray:
        """
        Crops a subregion from an image given a bounding box.

        @param image: Original BGR image
        @param box: Tuple of coordinates (x1, y1, x2, y2)
        @return: Cropped image region
        @raises ValueError: If box coordinates are out of bounds
        """
        x1, y1, x2, y2 = map(int, box)
        return image[y1:y2, x1:x2]

    @staticmethod
    def detect_plate_yolo(
        image: cv2.Mat,
        model_path: str,
        expand_ratio: float = 0.1,
        output_size: Tuple[int, int] = (256, 128),
    ) -> Optional[cv2.Mat]:
        """
        Detects and crops a license plate with optional expansion and resizing.

        @param image: BGR input image
        @param model_path: Path to YOLO model weights
        @param expand_ratio: Fraction to expand the bounding box (default 0.1)
        @param output_size: Output dimensions (width, height)
        @return: Cropped and resized plate image and its confidence, or None if detection fails
        @raises FileNotFoundError: If model_path does not exist
        @raises Exception: If YOLO detector cannot be initialized
        """
        try:
            from PlateProcessor.yolo_detector import YOLODetector  # type: ignore

            if not os.path.exists(model_path):
                print(f"WARNING: YOLO model not found at {model_path}")
                return None

            detector = YOLODetector(model_path=model_path, plate_class_id=0)
            boxes = detector.detect_plate(image)

            if not boxes:
                print("WARNING: YOLO: no plate detected.")
                return None

            x1, y1, x2, y2 = boxes[0].xyxy[0]
            h, w = image.shape[:2]
            conf = boxes[0].conf

            dx = (x2 - x1) * expand_ratio
            dy = (y2 - y1) * expand_ratio
            x1_new = max(0, int(x1 - dx))
            y1_new = max(0, int(y1 - dy))
            x2_new = min(w, int(x2 + dx))
            y2_new = min(h, int(y2 + dy))

            cropped = YOLODetector.crop_plate(image, (x1_new, y1_new, x2_new, y2_new))
            cropped_resized = cv2.resize(cropped, output_size, interpolation=cv2.INTER_LINEAR)
            print(f"INFO: YOLO: plate cropped and resized to {output_size}")
            return (cropped_resized, conf)

        except Exception as e:
            print(f"DEBUG: YOLO detector not available: {e}")
            return None


def _main():  # pragma: no cover
    """
    Simple test runner.

    @precondition: Test image exists at the specified path
    @precondition: YOLO model weights exist at ../models/best.pt
    @postcondition: Displays the cropped plate in a window for 2 seconds
    """
    folder = "/Users"
    file_name = "BC_Plate_Standard_Front.jpg"
    image_path = os.path.join(folder, file_name)

    if not os.path.exists(image_path):
        raise FileNotFoundError(f"Image file not found: {image_path}")

    image = cv2.imread(image_path)
    detector = YOLODetector(model_path="../models/best.pt", plate_class_id=0)
    boxes = detector.detect_plate(image)

    if not boxes:
        print("No plate detected.")
        return

    cropped = detector.crop_plate(image, boxes[0].xyxy[0])
    cv2.imshow("Cropped Plate", cropped)
    cv2.waitKey(2000)
    cv2.destroyAllWindows()


if __name__ == "__main__":  # pragma: no cover
    _main()
