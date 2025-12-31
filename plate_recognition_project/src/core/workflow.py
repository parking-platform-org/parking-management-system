"""
Pipeline: Coordinates YOLO detection and OCR extraction for license plates.

This module detects a license plate in an image using YOLO, optionally crops
it, and extracts text using OCR.

@pre: `image` passed to `process` must be a valid non-empty numpy.ndarray in BGR format.
@post: Returns OCR text and confidence as a tuple. Confidence is 0.0 if detection or OCR fails.
"""

import os
import numpy as np
from typing import Tuple

from PlateProcessor.yolo_detector import YOLODetector
from TextExtraction.ocr_reader import OCRReader
from utils.image_utils import load_image, show_image
from config.settings import YOLO_EXPAND_RATIO, YOLO_OUTPUT_SIZE,SHOW_IMAGE_DELAY_MS


class PlateRecognizer:
    """
    Pipeline for license plate detection and OCR extraction.

    @pre: YOLO model weights must exist at `self.yolo_model_path`.
    @post: `process` method returns detected text and confidence.
    """

    def __init__(self, yolo_model_name: str = "best.pt", ocr_model_name: str = "cct-xs-v1-global-model") -> None:
        """
        Initialize YOLO and OCR components for the PlateRecognizer pipeline.

        @param yolo_model_name: Filename of the YOLO model weights (default: "best.pt").
        @param ocr_model_name: Name of the OCR model (default: "cct-xs-v1-global-model").

        @pre: YOLO model file must exist in ../models relative to this file.
        @pre: OCR model must be compatible with OCRReader class.
        @post: Sets `self.yolo_model_path` to the absolute path of the YOLO model.
        @post: Initializes `self.ocr` as a ready-to-use OCRReader instance.
        """
        base_dir = os.path.dirname(os.path.abspath(__file__))
        self.yolo_model_path = os.path.join(base_dir, "..", "models", yolo_model_name)
        self.ocr = OCRReader(model_name=ocr_model_name)

    def process(self, image: np.ndarray, DEBUG: bool = False) -> Tuple[str, float]:
        """
        Detect and extract text from a license plate in an image.

        @param image: Input BGR image as a numpy array.
        @param DEBUG: If True, shows intermediate images.
        @pre: `image` must not be None and must have size > 0.
        @post: Returns tuple (text, confidence). If detection or OCR fails,return None.

        @return: Tuple[str, float]: OCR text and confidence percentage.
        """
        if image is None or image.size == 0:
            raise SystemExit("ERROR: Input image is invalid.")

        # Step 1 — YOLO detection + optional cropping
        cropped_plate, confidence = YOLODetector.detect_plate_yolo(
            image,
            self.yolo_model_path,
            expand_ratio=YOLO_EXPAND_RATIO,
            output_size=YOLO_OUTPUT_SIZE
        )
        if cropped_plate is not None:
             input_img = cropped_plate 
        else:
            return "No text detected.", 0.0

        # Step 2 — Show image if DEBUG
        if DEBUG:
            show_image(input_img, window_name="Detected Plate", delay_ms=SHOW_IMAGE_DELAY_MS)

        # Step 3 — OCR extraction
        result = self.ocr.extract_text(input_img)
        if result:
            text, _ = result
            if DEBUG:
                print(f"INFO: OCR result: {text} (confidence: {confidence:.2f}%)")
            return text, confidence

        if DEBUG:
            print("WARNING: OCR failed to extract text.")
        return None


def _main() -> None:  # pragma: no cover
    """
    Local test runner: loads an image, runs PlateRecognizer, prints result.

    @pre: Test image must exist at specified path.
    @post: Prints detected plate text and confidence.
    """
    base_dir = os.path.dirname(os.path.abspath(__file__))
    test_image_path = os.path.join(base_dir, "../../Test_Data", "BC_Plate_Standard_Front.jpg")

    image = load_image(test_image_path)
    if image is None:
        raise SystemExit("ERROR: Failed to read image.")

    recognizer = PlateRecognizer()
    text, confidence = recognizer.process(image)

    print("=" * 30)
    print(f"Detected Plate Text: {text}")
    print(f"Confidence: {confidence:.2f}%")
    print("=" * 30)


if __name__ == "__main__":  # pragma: no cover
    _main()
