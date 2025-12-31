# Parallax Parking Management System

**Parallax** is a full-stack system for vehicle registration, blacklist management, and license-plate recognition.  
The project consists of two major parts:

1. **Source Code Development**  
2. **Web Live Demo Deployment (Production Environment)**

## 1. Source Code Development

The codebase is fully handwritten without frameworks, designed for clarity, extensibility, and educational value.

### **Frontend (HTML/CSS/JavaScript SPA)**

- Custom Apple-style UI/UX with component-based sections (Sign-In, My Account, My Vehicles, Query).
- State management and navigation implemented purely in JavaScript.
- Full vehicle lifecycle UI: registration, listing, blacklist toggling, global queries.
- Integrated frontend logic for consuming backend REST APIs.
- Image-upload module for OCR integration.
- Highly modular structure allowing future migration to React.

### **Backend (Pure Java HTTP Server)**

- Custom HTTP server based on `com.sun.net.httpserver.HttpServer`.
- Clear handler separation: Auth, Account, Vehicles, Query, Image Recognition Proxy.
- Repository abstraction layer (`UserRepository`, `VehicleRepository`) with in-memory implementations and future SQLite drop-in replacements.
- Clean data models for request/response bodies.
- Config-driven architecture with admin account support and service endpoints.
- Backend functions already structured for multi-instance access via pooled connections once DB is deployed.

### **Database (SQLite)**

- Repository abstraction (`SQLiteUserRepository`, `SQLiteVehicleRepository`) separating data schema from application logic.
- Uses InMemory repositories during testing, switches seamlessly to **SQLite** in deployed instances, and includes a planned upgrade path to **PostgreSQL** in future releases.
- Consistent data normalization, indexing requirements, and future-ready DB transaction model.

### **Python OCR Microservice Integration**

- Implemented as a **Flask-based HTTP microservice**, exposing a clean REST contract for image inference.
- Powered by a YOLO detector and OCR pipeline trained on a sufficiently large dataset to achieve high recognition accuracy.
- Java backend issues controlled outbound requests, receives normalized plate strings and confidence metrics, and performs blacklist/ownership verification.
- Microservice is stateless and horizontally scalable: multiple instances can be deployed to increase throughput under load.
- Architecture explicitly reserves interfaces for **future hardware integration** (e.g., IP cameras, edge devices) enabling **real-time plate recognition** scenarios.

### **Testing**

- Comprehensive JUnit 5 test suite:
  - Repository behavior
  - Request/response validation
  - Handler routing and API correctness
  - Error handling and edge-case behavior

---

## 2. Web Live Demo Deployment

The production demo runs on a distributed, secure, Cloudflare-protected architecture designed to mimic real enterprise deployments.

### **Core Features**

- **End-to-end HTTPS** via Cloudflare secure proxy  
- **Static frontend delivery** on Nginx with HTTP/2 and global CDN caching  
- **Three-node distributed deployment**:
  - Frontend VPS
  - Java backend VPS
  - Python OCR microservice VPS  
- **Reverse-proxied backend API** — no direct public exposure  
- **Security-focused design** with WAF, DDoS protection, HSTS, and TLS origin certificates  
- **Operational reliability** through systemd supervision and isolated logging  

### **Architecture Characteristics**

- Clear separation of concerns improves scalability and maintainability.  
- Backend and OCR microservice communicate internally, never exposed to users directly.  
- Designed for future migration to:
  - Docker containerization  
  - Horizontal backend scaling  
  - Managed SQL database servers  
  - Kubernetes orchestration  

---

# Live Demo Website

1. **URL**: https://parallax.twilightfrosty.com  
2. **Admin Email**: admin@parallax.local  
3. **Admin Password**: Admin1234!

---

## Live Demo Deployment Features 

* **End-to-End HTTPS with Cloudflare Secure Proxy**

  * All browser-to-service traffic fully encrypted over HTTPS
  * Cloudflare acts as the single public entry point:
    routing, TLS termination, WAF, DDoS protection, and global CDN acceleration
  * Origin-to-edge encryption enforced using Cloudflare Origin Certificates

* **Production-Grade Frontend Delivery**

  * Static SPA hosted on a dedicated frontend VPS
  * Fully managed by Nginx with HTTP/2 enabled
  * Caching and content distribution optimized by Cloudflare global PoPs

* **Distributed Multi-Node Architecture**

  * **Frontend**, **Java Backend API**, and **Python OCR Microservice**
    run on **three isolated Debian VPS nodes**
  * Clear functional isolation improves scalability, resilience, and operational safety

* **Reverse-Proxied Backend API (Java)**

  * Backend API only accessible through Cloudflare-secured domain routing
  * No direct exposure of internal ports or private network topology
  * Ready for future horizontal scaling and load-balancer upstream integration

* **Python OCR Microservice (YOLO + OCR)**

  * Independent HTTP microservice optimized for image inference workloads
  * Can be replicated in parallel to improve recognition throughput
  * Consumed exclusively by backend via controlled internal channel

* **Security-Oriented Deployment Model**

  * Direct public access restricted to Cloudflare edge nodes
  * Internal services reachable only via local/private connections
  * Strict TLS configuration and HSTS to prevent downgrade/MITM attacks

* **Future-Ready System Design**

  * Architecture aligned toward container orchestration (Docker/Kubernetes)
  * Service-level independence enables progressive microservice separation
  * Database and state management ready for multi-instance backends

* **Operational Reliability**

  * All services run under systemd with automatic restart policies
  * Individual logging per service with isolation for debugging and auditing
  * Simple extensibility toward metrics collection and health monitoring




---

# Known Bugs
1. **OCR occasionally confuses the characters `i` and `1`.**  
   - This typically happens when the input image has low resolution or strong reflections.  
   - Potential improvement: apply character-level post-processing or use a custom-trained OCR model to reduce ambiguity.
2. **OCR occasionally failed to recognize the text**
   - This happens with weird **angels** of the car plate.
3. **YOLO occasionally failed to detext the plate in image full of text**
   - This is due to the lack of **training** data (trained on 8000 images).
---

# Installation Guide

This document explains how to install, configure, and launch the Parallax Backend API (Java) and the Python OCR Microservice on both **Windows** and **Debian Linux** environments.

---

## 1. Java Backend Installation & Startup

The Java backend exposes REST APIs for account, vehicle, and image-query operations.
It must be built using **Maven** before execution.

---

## 1.1 Prerequisites

### Windows

* Java 17+
* Maven 3.6+
* Git (optional)

### Debian

Install Java & Maven:

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk maven
```

Verify installation:

```bash
java -version
mvn -v
```

---

## 1.2 Build the Backend (Windows & Debian)

Navigate into the backend project directory:

```bash
cd project-pine/frontend_and_backend_project/backend
```

Run Maven build:

```bash
mvn clean package
```

This generates:

```
target/parallax-backend-1.0.0.jar
target/lib/
```

---

## 1.3 Run the Backend (Windows)

Command:

```bat
cd project-pine\frontend_and_backend_project\backend
java -cp "target\parallax-backend-1.0.0.jar;target\lib\*" parallax.backend.http.HttpServerApp
```

The server will start on the configured port (e.g., 8080).

---

## 1.4 Run the Backend (Debian)

```bash
cd project-pine/frontend_and_backend_project/backend
java -cp "target/parallax-backend-1.0.0.jar:target/lib/*" parallax.backend.http.HttpServerApp
```

---

## 1.5 Optional: Run Backend as a systemd Service (Debian)

Create a unit file `/etc/systemd/system/parallax-backend.service`:

```ini
[Unit]
Description=Parallax Backend Service
After=network.target parallax-plate.service
Wants=parallax-plate.service

[Service]
WorkingDirectory=project-pine/frontend_and_backend_project/backend
ExecStart=/usr/bin/java -cp target/parallax-backend-1.0.0.jar:target/lib/* parallax.backend.http.HttpServerApp
User=root
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
```

Activate:

```bash
sudo systemctl daemon-reload
sudo systemctl enable parallax-backend
sudo systemctl start parallax-backend
```

Check status:

```bash
systemctl status parallax-backend
```

---

## 2. Python OCR Microservice Installation & Startup

This component provides plate detection + OCR via YOLO + ONNX OCR models.
The Java backend communicates with it over HTTP.

---

## 2.1 Prerequisites

### Windows

Install Python 3.10+ and pip.
(Optional) Install Visual Studio Build Tools if building dependencies requires them.

### Debian

```bash
sudo apt update
sudo apt install -y python3 python3-venv python3-pip libgl1 libglib2.0-0
```

The `libgl1` and `libglib2.0-0` packages are required by OpenCV.

---

## 2.2 Create Conda Environment Setup

If you are using Conda, you can create environment with
```bash
cd project-pine/plate_recognition_project/env
conda env create -f environment_cross.yml
conda activate src
```
If you want to check the environment setup, you can simply run
```bash
python --version
pip list
```
After Conda setup, you can directly jump to 2.4.

## 2.3 Create Virtual Environment (Windows & Debian)

```bash
cd project-pine/plate_recognition_project/src
python -m venv .venv
```

Activate:

### Windows

```bat
.\.venv\Scripts\activate
```

### Debian

```bash
source .venv/bin/activate
```

---

## 2.3.1 Install Dependencies

Run inside the activated virtual environment:

```bash
pip install --upgrade pip
pip install flask numpy "opencv-python-headless<5.0.0.0" ultralytics fast-plate-ocr onnxruntime
```

If your workflow requires additional libraries (e.g., scikit-image, pydantic), install them similarly:

```bash
pip install scikit-image pydantic
```

---

## 2.4 Start OCR Microservice

### Windows

```bat
cd project-pine\plate_recognition_project\src
.\.venv\Scripts\activate
python app.py
```

### Debian

```bash
cd project-pine/plate_recognition_project/src
source .venv/bin/activate
python app.py
```

If everything is successful, you will see:

```
Running on http://0.0.0.0:9000
```

---

## 2.5 Optional: Run as a systemd Service (Debian)

Create `/etc/systemd/system/parallax-plate.service`:

```ini
[Unit]
Description=Parallax Plate Recognition Service
After=network.target

[Service]
WorkingDirectory=/etc/Parallax/project-pine/plate_recognition_project/src
ExecStart=/etc/Parallax/project-pine/plate_recognition_project/src/.venv/bin/python app.py
Environment=PYTHONUNBUFFERED=1
User=root
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable parallax-plate
sudo systemctl start parallax-plate
```

Check:

```bash
systemctl status parallax-plate
```

---

## 3. Integration Summary

* Java backend reads files uploaded from frontend.
* Java backend forwards the image to Python service:
  ```
  POST http://<PY_SERVICE_HOST>:9000/detect-plate
  Content-Type: multipart/form-data
  Field: image=<binary>
  ```
* Python returns JSON containing:
  ```json
  {
    "success": true/false,
    "plateFound": true/false,
    "plateNumber": "...",
    "confidence": 0.0-1.0
  }
  ```
* Java returns normalized results to the frontend.

---

## 4. Directory Layout (Example)

```
/etc/Parallax/
    └── project-pine/
        └── frontend_and_backend_project/
        	└── backend/
        		└── target/
        └── plate_recognition_project/
            └── src/
                ├── app.py
                ├── core/
                ├── PlateProcessor/
                ├── models/
                └── .venv/
```

---

# License
MIT License

Copyright (c) 2025 CPEN 221 Project-pine

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

---

# Contribution

**Hank Wu** – Implemented the SQLite persistence layer, including complete SQL-based repository implementations for users and vehicles, and integrated the database with the backend data flow.

**Sean** – Designed the database schema, created the table structure, and assisted with overall database architecture.

**Morant** – Developed the YOLO-based detection system, trained the YOLO model, implemented the car plate detection pipeline and subproject structure, and developed the backend logic in Python.

**Jeff** – Implemented the plate recognition pipeline, including OCR text extraction, and constructed the main processing workflow and project structure.

**Liam** – Designed and implemented the full front-end (UI/UX + state management), built a pure-Java backend with custom HTTP handlers and repository abstraction, integrated external Python plate-recognition services and web live demo construction.

---

# Acknowledgement

This began as a UBC CPEN 221 Course Project.

