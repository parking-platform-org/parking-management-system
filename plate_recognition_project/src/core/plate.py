"""
Plate object: Stores cropped plate image and OCR results.
"""

import numpy as np

class Plate:
    """
    Represents a detected license plate with cropped image, OCR text, and confidence.

    @pre: `crop` must be a valid non-empty numpy.ndarray representing a BGR image.
    @pre: `text` should be a string (can be empty if OCR fails).
    @pre: `confidence` should be a float, typically in 0-100.
    @post: Plate instance stores the cropped image, text, and confidence for later use.
    """

    def __init__(self, crop: np.ndarray, text: str, confidence: float):
        """
        Initializes a Plate object.

        @param crop: Cropped plate image as a numpy array (BGR format).
        @param text: OCR-extracted text.
        @param confidence: OCR confidence (0-100 or 0-1 depending on OCR output).
        @post: Stores the crop, text, and confidence in the object.
        """
        self.crop = crop
        self.text = text
        self.confidence = confidence

    def set_text(self, text: str, confidence: float) -> None:
        """
        Updates the OCR text and confidence for this plate.

        @param text: OCR-extracted text.
        @param confidence: OCR confidence.
        @pre: text should be a string.
        @pre: confidence should be a float.
        @post: Updates the Plate instance's text and confidence fields.
        """
        self.text = text
        self.confidence = confidence
