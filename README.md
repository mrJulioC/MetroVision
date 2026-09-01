# MetroVision

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

## Construcción

Requiere JDK 17, Android SDK 37 y Gradle 9.1:

```bash
gradle assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. También se incluye un workflow de GitHub Actions.

## Precisión

ARCore necesita buena iluminación, superficies con textura y movimientos lentos. El GPS está pensado para exteriores y filtra lecturas con precisión peor de 20 metros.
