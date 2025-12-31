"""
Project-wide settings for license plate recognition.

Preconditions:
    - YOLO model must exist at YOLO_MODEL_PATH.
Postconditions:
    - Provides standardized paths and configuration constants for the project.
"""

import os

# --- Directory Paths ---
CONFIG_DIR = os.path.dirname(os.path.abspath(__file__))  # folder containing settings.py
SRC_DIR = os.path.dirname(CONFIG_DIR)                    # src/
PROJECT_ROOT = os.path.dirname(SRC_DIR)                 # project root

# --- Model Path ---
YOLO_MODEL_PATH = os.path.join(SRC_DIR, "models", "best.pt")

# --- Test Data Directory ---
TEST_DATA_DIR = os.path.join(PROJECT_ROOT, "Test_Data")

# --- Detection & OCR Settings ---
PLATE_CLASS_ID = 0                        # YOLO class for license plates
OCR_MODEL_NAME = "cct-xs-v1-global-model"  # fast-plate-ocr model name
OCR_LANGS = ["en"]                         # Languages for OCR (future use)

# YOLO detection/cropping parameters
YOLO_EXPAND_RATIO = 0.001                  # Bounding box expansion fraction
YOLO_OUTPUT_SIZE = (512, 256)             # (width, height) of cropped plates

# Image display settings
SHOW_IMAGE_DELAY_MS = 2000                 # milliseconds to show images in utils

# --- Preconditions Check ---
if not os.path.exists(YOLO_MODEL_PATH):
    raise FileNotFoundError(f"YOLO model not found: {YOLO_MODEL_PATH}")
