"""
Image utility functions.
"""
import os
from typing import Optional

import cv2

# The original code imported 'logger' from TextExtraction.ocr_reader,
# which is removed here to comply with the request to eliminate logging.
# from TextExtraction.ocr_reader import logger

def show_image(image: cv2.Mat, window_name: str = "Image", delay_ms: int = 2000) -> None:
    """
    Display an image for a specified duration.

    Args:
        image: cv2 image in BGR format.
        window_name: Title of the display window.
        delay_ms: Duration in milliseconds to display the image.

    Preconditions:
        - image is a valid cv2 image (numpy array).
        - delay_ms >= 0.
    Postconditions:
        - The image window will automatically close after delay_ms.
    """
    cv2.imshow(window_name, image)
    cv2.waitKey(delay_ms)
    cv2.destroyAllWindows()


def load_image(path: str) -> Optional[cv2.Mat]:
    """
    Load an image from disk.

    Args:
        path: Path to the image file.

    Returns:
        cv2 image if successfully loaded, else None.

    Preconditions:
        - path must be a valid file path string.
    Postconditions:
        - Returns None if the file does not exist or loading fails.
        - Returns a BGR cv2 image (numpy array) if successful.
    """
    if not os.path.isfile(path):
        # logger.error("Image not found at: %s", path)
        print(f"ERROR: Image not found at: {path}")
        return None
    image = cv2.imread(path)
    if image is None:
        # logger.error("Failed to load image from: %s", path)
        print(f"ERROR: Failed to load image from: {path}")
    return image