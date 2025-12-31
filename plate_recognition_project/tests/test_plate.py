import os
import sys
import numpy as np

# Ensure project src is importable.
PROJECT_SRC = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "src"))
if PROJECT_SRC not in sys.path:
    sys.path.insert(0, PROJECT_SRC)

from core.plate import Plate


def test_plate_initialization_and_setters():
    crop = np.zeros((2, 2, 3), dtype=np.uint8)
    plate = Plate(crop, "ABC123", 88.5)

    assert plate.crop is crop
    assert plate.text == "ABC123"
    assert plate.confidence == 88.5

    plate.set_text("XYZ789", 42.0)
    assert plate.text == "XYZ789"
    assert plate.confidence == 42.0
