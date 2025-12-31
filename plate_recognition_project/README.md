# 🚗 Project Pine — License Plate Recognition Subsystem

### YOLOv8 Detection + fast-plate-ocr Extraction + Flask REST API

This repository is a **subproject of Project Pine**, responsible for the **License Plate Recognition (LPR)** subsystem. It provides a complete detection + OCR pipeline used by Project Pine’s vehicle identification features.

This subsystem includes:

* YOLOv8 license plate detection
* Plate cropping, expansion, and resizing
* OCR using fast-plate-ocr (CCT-XS model)
* A production-ready Flask REST API
* Dataset and environment configs
* Developer utilities and debug tools

This module runs standalone or embedded in the main Project Pine backend.

---

## 📂 Subproject Structure
```
project-pine/
└── plate\_recognition/
  ├── Test\_Data/
  ├── datasets/
  ├── env/
  ├── core/
  │  ├── plate.py
  │  └── workflow.py
  ├── PlateProcessor/
  │  └── yolo\_detector.py
  ├── TextExtraction/
  │  └── ocr\_reader.py
  ├── utils/
  │  └── image\_utils.py
  ├── config/
  │  └── settings.py
  └── plate\_api.py
```
This separation keeps the subsystem modular, testable, and easily integrable.

---

## 🚀 Features

### YOLOv8 Detection

* High-speed bounding-box inference
* Expandable cropping region
* Configurable plate class ID

### fast-plate-ocr Extraction

* Trained models for license plate domains
* Clean text extraction with confidence scoring

### PlateRecognizer Pipeline

* YOLO → Crop → Resize → OCR
* Debug mode with visual outputs
* Configurable via settings.py

### Flask REST API

* POST /recognize endpoint
* Returns plate text + confidence in JSON

---

## 📦 Installation (Subproject Only)

From project-pine/plate\_recognition:

conda env create -f env/environment.yml
conda activate plate-recognition

Install Python dependencies:

pip install fast-plate-ocr ultralytics flask opencv-python numpy

---

## 🔧 Configuration (config/settings.py)

YOLO\_MODEL\_PATH = "models/best.pt"
OCR\_MODEL\_NAME = "cct-xs-v1-global-model"
YOLO\_EXPAND\_RATIO = 0.1
YOLO\_OUTPUT\_SIZE = (256, 128)
SHOW\_IMAGE\_DELAY\_MS = 2000

Adjust these for different deployment environments.

---

## 🌐 Running the API

python plate\_api.py

Send a request using curl:

curl -X POST [http://localhost:9000/recognize](http://localhost:9000/recognize) -F "image=@Test\_Data/N50.jpeg"

Example response:

{
"success": true,
"plateFound": true,
"plateNumber": "ABC123",
"confidence": 97.5
}

---

## 🧪 Running the Local Test Pipeline

python core/workflow.py

Pipeline steps:

1. Load image
2. YOLO detects plate
3. Crop + resize
4. OCR extracts text
5. Print text + confidence

---

## 📚 Code Modules Overview

PlateAPI – Flask server for /recognize
PlateRecognizer – Full YOLO → OCR pipeline
YOLODetector – YOLOv8 wrapper + cropping utilities
OCRReader – fast-plate-ocr wrapper
image\_utils – helper for loading, debugging

---

## 📂 YOLO Dataset (for Model Training)

datasets/ contains:

train/
valid/
test/
data.yaml

Train YOLO:

yolo train model=yolov8n.pt data=datasets/data.yaml epochs=100 imgsz=640

---

## 🔗 Integration With Project Pine

This module is designed to be:

* Self-contained
* Importable (Python module)
* Deployable (microservice)

Usage in main backend:

from plate\_recognition.core.workflow import PlateRecognizer

Or over the API endpoint:

POST http://:9000/recognize

---

## 🛠️ Contribution Guidelines

* Maintain pre/postcondition docstrings
* Keep YOLO & OCR modular
* Avoid blocking operations
* Provide sample images when adding modules

---

## 📄 License

This subproject is licensed under the MIT License. See the [LICENSE](./LICENSE) file for details.

---
