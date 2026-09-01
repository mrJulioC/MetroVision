# MetroVision

## MetroVision 3.0 — IA local

La selección precisa de objetos usa MediaPipe MagicTouch directamente en el teléfono. El modelo se incluye dentro del APK: las imágenes no se envían a servidores. El contorno obtenido se combina con profundidad ARCore y los parámetros ópticos de la cámara para calcular medidas; el GPS se reserva para recorridos exteriores.

Metro y medidor de áreas para Android con realidad aumentada, calibración y recorrido GPS.

## Modos

- Distancia A–B mediante ARCore.
- Rectángulo: ancho, alto, área y perímetro.
- Área libre mediante tres o más vértices.
- Calibración con una distancia real conocida.
- Recorrido GPS A–B con distancia acumulada y precisión de señal.
- Unidades seleccionables: metros, centímetros, pies y pulgadas.
- Interfaz de cámara compacta con guía de proximidad.
- Calibración persistente aplicada a todas las mediciones por cámara.
- Detección automática de objetos mediante ML Kit.
- Estimación del ancho combinando profundidad AR y óptica de cámara.
- Validación de calibración y restablecimiento desde la pantalla principal.

## Construcción

Requiere JDK 17, Android SDK 37 y Gradle 9.1:

```bash
gradle assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. También se incluye un workflow de GitHub Actions.

## Precisión

ARCore necesita buena iluminación, superficies con textura y movimientos lentos. El GPS está pensado para exteriores y filtra lecturas con precisión peor de 20 metros.
