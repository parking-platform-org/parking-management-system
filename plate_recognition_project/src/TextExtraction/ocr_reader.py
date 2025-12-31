"""
Minimal OCR module for YOLO plates + fast-plate-ocr with confidence and stats.

Pipeline:
  1. Optional YOLO plate detection
  2. Feed cropped plate (or full image) directly to fast-plate-ocr
  3. Return detected text with confidence (100% if text detected)
  4. Show raw image for 2 seconds
"""

import sys, os
sys.path.append(os.path.dirname(os.path.dirname(__file__)))

from typing import Optional, Tuple

import cv2
from fast_plate_ocr import LicensePlateRecognizer  # pip install fast-plate-ocr
from PlateProcessor.yolo_detector import YOLODetector
from utils.image_utils import show_image, load_image


class OCRReader:
    """
    Wrapper for fast-plate-ocr LicensePlateRecognizer with confidence.

    @pre: img passed to extract_text must be a valid cv2 image (numpy array) in BGR format.
    @pre: img should not be empty (size > 0).
    @post: Returns a tuple of (detected_text, confidence).
    @post: Confidence is 100.0 if text is detected, else 0.0.
    """

    def __init__(self, model_name: str = "cct-xs-v1-global-model") -> None:
        """
        Initializes the OCR reader with a pretrained fast-plate-ocr model.

        @param model_name: Name of the fast-plate-ocr model.
        @post: OCR model is loaded and ready for inference.
        """
        print(f"INFO: Initializing OCR model: {model_name}")
        self.ocr = LicensePlateRecognizer(model_name)

    def extract_text(self, img: cv2.Mat) -> Optional[Tuple[str, float]]:
        """
        Run OCR on the provided image.

        @param img: cv2 image in BGR format.
        @return: Tuple of (detected_text, confidence) if OCR succeeds, else None.
        @pre: img is not None.
        @pre: img.size > 0.
        @post: Returns None if img is invalid or OCR fails.
        @post: Confidence = 100% if text detected, else 0%.
        """
        if img is None or img.size == 0:
            return None

        try:
            text = self.ocr.run(img)
        except Exception as e:
            print(f"ERROR: fast-plate-ocr failed: {e}")
            return None

        confidence = 100.0 if text else 0.0
        return (text, confidence) if text else None


def main() -> None:  # pragma: no cover
    """
    Main execution pipeline.

    @post: Displays raw/cropped image for 2 seconds.
    @post: Prints OCR result and confidence if successful.
    """
    base_dir = os.path.dirname(os.path.abspath(__file__))
    test_image_path = os.path.join(base_dir, "../../Test_Data", "BC_Plate_Standard_Front.jpg")
    yolo_model_path = os.path.join(base_dir, "..", "models", "best.pt")

    image = load_image(test_image_path)
    if image is None:
        raise SystemExit(1)

    # Optional YOLO plate detection
    cropped_plate = YOLODetector.detect_plate_yolo(
        image,
        yolo_model_path,
        expand_ratio=0.001,
        output_size=(512, 256)
    )
    input_img = cropped_plate if cropped_plate is not None else image

    # Show raw image for 2 seconds
    show_image(cropped_plate, window_name="Raw Plate Image", delay_ms=2000)

    # Run OCR
    reader = OCRReader(model_name="cct-xs-v1-global-model")
    result = reader.extract_text(input_img)

    if result:
        text, confidence = result
        print(f"INFO: OCR result: {text} (confidence: {confidence:.2f}%)")
        print("\n" + "=" * 30)
        print(f"  RESULT: {text}")
        print(f"  CONFIDENCE: {confidence:.2f}%")
        print("=" * 30 + "\n")
    else:
        print("\nOCR failed to extract text.\n")


if __name__ == "__main__":  # pragma: no cover
    main()
