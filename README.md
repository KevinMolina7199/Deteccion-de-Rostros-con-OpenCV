Aquí tienes un README para tu proyecto en GitHub:

---

# Final App

Este proyecto es una aplicación Android que utiliza OpenCV y cascadas de Haar para detectar las posiciones de los distintos componentes del rostro, como ojos, nariz y boca. La información de la detección se envía en tiempo real a un servidor, donde se superpone una imagen sobre los ojos detectados.

![image](https://github.com/user-attachments/assets/5886a84f-9d1c-4407-b641-014b51c2bbff)



## Estructura del Proyecto

### Aplicación Android

- **Tecnologías utilizadas:**
  - **Android Studio:** Entorno de desarrollo.
  - **OpenCV:** Biblioteca para el procesamiento de imágenes.
  - **Cascadas de Haar:** Para la detección de características faciales.

- **Descripción:**
  La aplicación Android captura imágenes en tiempo real utilizando la cámara del dispositivo, detecta los componentes del rostro utilizando cascadas de Haar y OpenCV, y envía la información de la detección a un servidor. En el servidor, se superpone una imagen a los ojos detectados y se muestra el resultado final.

- **Archivos principales:**
  - `MainActivity.java`: Actividad principal que maneja la captura de la imagen, la detección facial y el envío de datos al servidor.
  - `opencv_module`: Módulo de OpenCV integrado en el proyecto.
  - `AndroidManifest.xml`: Archivo de configuración de la aplicación.
  - `activity_main.xml`: Diseño de la interfaz de usuario.
  - `haarcascade_eye.xml`, `haarcascade_frontalface.xml`, etc.: Archivos de cascadas de Haar utilizados para la detección.

### Servidor

- **Tecnologías utilizadas:**
  - **Python Flask:** Framework para construir la aplicación web.
  - **OpenCV:** Para el procesamiento de imágenes en el servidor.

- **Descripción:**
  El servidor Flask recibe la información de detección facial desde la aplicación Android, añade una imagen sobre los ojos detectados y muestra el resultado final.

- **Archivos principales:**
  - `app.py`: Archivo principal que define las rutas y la lógica del servidor.
  - `requirements.txt`: Archivo con las dependencias necesarias para el servidor.

## Instalación y Configuración

### En el Cliente (Android Studio)

1. **Clona el repositorio** en tu máquina local:
    ```bash
    git clone https://github.com/KevinMolina7199/Final_App.git
    ```

2. **Importa el proyecto en Android Studio**:
    - Abre Android Studio.
    - Selecciona `File` -> `Open` y elige el directorio del proyecto.

3. **Configura las dependencias de OpenCV:**
    - Asegúrate de seguir las instrucciones en la [documentación de OpenCV para Android](https://docs.opencv.org/master/d5/df8/tutorial_py_sift_intro.html).

4. **Añade los archivos de cascadas de Haar** a la carpeta `assets` de tu proyecto.

5. **Ejecuta la aplicación** en un dispositivo o emulador Android.

### En el Servidor (Python Flask)

1. **Clona el repositorio** en tu máquina local:
    ```bash
    git clone https://github.com/KevinMolina7199/Final_App.git
    ```

2. **Instala las dependencias**:
    - Crea un entorno virtual:
      ```bash
      python -m venv venv
      ```
    - Activa el entorno virtual:
      ```bash
      source venv/bin/activate  # En Windows: venv\Scripts\activate
      ```
    - Instala las dependencias:
      ```bash
      pip install -r requirements.txt
      ```

3. **Ejecuta el servidor Flask**:
    ```bash
    python app.py
    ```

4. **Accede al servidor** en `http://localhost:5000` para ver el resultado.

## Uso

1. **Captura una Imagen:**
   - Abre la aplicación y usa la funcionalidad de la cámara para capturar una imagen del rostro.

2. **Detección y Superposición:**
   - La aplicación detectará los componentes del rostro y enviará la información al servidor.
   - El servidor añadirá una imagen a los ojos detectados y mostrará el resultado.

## Contribuciones

Las contribuciones son bienvenidas. Si tienes ideas para mejorar el proyecto o encontrar errores, no dudes en hacer un fork del repositorio y enviar un pull request.

## Autor

- **Kevin Ismael Molina Arpi** - [GitHub](https://github.com/KevinMolina7199)

## Licencia

Este proyecto está bajo la Licencia MIT. Ver el archivo `LICENSE` para más detalles.

---

Asegúrate de tener el archivo `LICENSE` en tu repositorio para completar la información sobre la licencia. ¡Espero que esto te sea útil!
