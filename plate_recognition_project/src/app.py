import numpy as np
import cv2
from flask import Flask, request, jsonify
from core.workflow import PlateRecognizer
from config.settings import YOLO_MODEL_PATH, OCR_MODEL_NAME


class PlateAPI:
    """
    Orchestrates the Flask web server and the License Plate Recognition pipeline.

    @pre: `YOLO_MODEL_PATH` and `OCR_MODEL_NAME` must be defined in config/settings.py.
    @post: An instance of PlateAPI is created with a loaded recognizer and registered routes.
    """
    def __init__(self):
        self.app = Flask(__name__)
        self.recognizer = PlateRecognizer(
            yolo_model_name=YOLO_MODEL_PATH,
            ocr_model_name=OCR_MODEL_NAME,
        )
        self._register_routes()

    def _register_routes(self):
        """
        Maps URL endpoints to their specific view functions.

        @pre: `self.app` must be an initialized Flask instance.
        @post: The '/detect-plate' route is bound to the `self.recognize` method.
        """
        self.app.add_url_rule("/detect-plate", view_func=self.recognize, methods=["POST"])

    @staticmethod
    def _normalize_text(text) -> str:
        """
        Cleans raw OCR output to ensure consistent alphanumeric formatting.

        @pre: `text` must be a string, list, or tuple.
        @post: Returns a string with spaces/underscores removed and character confusions (O/0, I/1) fixed.
        """
        if isinstance(text, (list, tuple)):
            raw = "".join(str(part) for part in text)
        else:
            raw = str(text)

        clean = raw.replace("_", "").replace(" ", "").replace("O", "0").replace("o", "0").replace("i", "1").replace("I", "1")
        return clean

    def recognize(self):
        """
        Handles the HTTP request to detect and read a license plate from an uploaded image.

        @pre: Request method must be POST.
        @pre: Request files must contain a key 'image' with valid image bytes.
        @post: Returns JSON `{"success": True, ...}` and status 200 if processed (even if no plate found).
        @post: Returns JSON `{"error": ...}` and status 400 if image is missing/invalid.
        @post: Returns JSON `{"error": "internal_error"}` and status 500 if an exception occurs.
        """
        try:
            img_bytes = request.files.get("image")
            if not img_bytes:
                return jsonify({"success": False, "error": "No image uploaded"}), 400

            # Decode image
            np_arr = np.frombuffer(img_bytes.read(), np.uint8)
            image = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
            if image is None:
                return jsonify({"success": False, "error": "Invalid image format"}), 400

            # Run recognition
            text, conf = self.recognizer.process(image)
            confidence = conf[0]

            if not text:
                return jsonify({"success": True, "plateFound": False}), 200

            if isinstance(text, str) and text in ["No text detected.", "________"]:
                return jsonify({"success": True, "plateFound": False}), 200

            if isinstance(text, (list, tuple)):
                joined = "".join(str(p) for p in text)
                if joined == "________":
                    return jsonify({"success": True, "plateFound": False}), 200

            clean_text = self._normalize_text(text)

            if not clean_text:
                return jsonify({"success": True, "plateFound": False}), 200

            return jsonify({
                "success": True,
                "plateFound": True,
                "licenseNumber": clean_text,
                "confidence": float(confidence),
            }), 200

        except Exception as e:
            import traceback
            traceback.print_exc()
            return jsonify({"success": False, "error": "internal_error"}), 500

    def run(self, host="0.0.0.0", port=9000):
        """
        Starts the Flask development server.

        @pre: `host` should be a valid string IP address.
        @pre: `port` should be an available integer port number.
        @post: The web server starts listening indefinitely until interrupted.
        """
        self.app.run(host=host, port=port)


if __name__ == "__main__":
    api = PlateAPI()
    api.run()
