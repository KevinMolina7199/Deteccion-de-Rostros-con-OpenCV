from flask import Flask, request, jsonify, render_template, Response
import cv2
import numpy as np
from flask_socketio import SocketIO
import base64
import io
from PIL import Image

app = Flask(__name__)
socketio = SocketIO(app, cors_allowed_origins="*")  # Configura CORS si es necesario

# Buffer para almacenar el frame más reciente
current_frame = None

# Cargar la imagen de las gafas
glasses = cv2.imread('D:\\SEPTIMO_CICLO\\VISION_POR_COMPUTADOR\\SERVIDOR\\.venv\\1.png', -1)  # Asegúrate de tener la imagen de las gafas en el mismo directorio
if glasses.shape[2] == 3:
    # Si la imagen no tiene un canal alfa, agrega uno
    b, g, r = cv2.split(glasses)
    alpha = np.ones(b.shape, dtype=b.dtype) * 255
    glasses = cv2.merge((b, g, r, alpha))

# Cargar el clasificador de ojos
eye_cascade = cv2.CascadeClassifier('D:\\SEPTIMO_CICLO\\VISION_POR_COMPUTADOR\\SERVIDOR\\.venv\\haarcascade_eye.xml')

def overlay_image(background, overlay, x, y, w, h):
    overlay_resized = cv2.resize(overlay, (w, h))
    overlay_rgb = overlay_resized[:, :, :3]
    mask = overlay_resized[:, :, 3]

    roi = background[y:y+h, x:x+w]

    img1_bg = cv2.bitwise_and(roi, roi, mask=cv2.bitwise_not(mask))
    img2_fg = cv2.bitwise_and(overlay_rgb, overlay_rgb, mask=mask)

    dst = cv2.add(img1_bg, img2_fg)
    background[y:y+h, x:x+w] = dst

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/upload_detection', methods=['POST'])
def upload_detection():
    global current_frame
    data = request.json
    base64_image = data.get('image', '')

    # Decodificar la imagen
    image_data = base64.b64decode(base64_image)
    image = Image.open(io.BytesIO(image_data))
    image_np = np.array(image)

    # Convertir a BGR (OpenCV usa BGR en lugar de RGB)
    image_bgr = cv2.cvtColor(image_np, cv2.COLOR_RGB2BGR)

    # Voltear la imagen si es necesario (rotar 90 grados)
    image_bgr = cv2.rotate(image_bgr, cv2.ROTATE_90_CLOCKWISE)

    # Detección de ojos y superposición de gafas
    gray = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY)
    eyes = eye_cascade.detectMultiScale(gray, scaleFactor=1.2, minNeighbors=15, minSize=(10, 10))

    for (x, y, w, h) in eyes:
        overlay_image(image_bgr, glasses, x, y, w, h)

    current_frame = image_bgr

    return jsonify({"status": "success"}), 200

def generate_frames():
    global current_frame
    while True:
        if current_frame is not None:
            _, buffer = cv2.imencode('.jpg', current_frame)
            frame = buffer.tobytes()
            yield (b'--frame\r\n'
                   b'Content-Type: image/jpeg\r\n\r\n' + frame + b'\r\n')

@app.route('/video_feed')
def video_feed():
    return Response(generate_frames(),
                    mimetype='multipart/x-mixed-replace; boundary=frame')

@app.route('/detection_data')
def get_detection_data():
    return jsonify({})  # Ajusta según tus necesidades

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
