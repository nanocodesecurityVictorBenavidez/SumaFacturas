# SumaFacturas — Escáner de facturas por imagen (Android)

App Android **independiente**, que **no se conecta ni modifica el sistema de
facturación**. Todo el análisis se hace sobre una foto, una captura de
pantalla o una imagen guardada, usando OCR en el propio teléfono.

## Cómo abrir y compilar

1. Instala **Android Studio** (Koala o más reciente).
2. `File → Open` y selecciona la carpeta `SumaFacturas/`.
3. Android Studio descargará automáticamente el Gradle Wrapper y las
   dependencias (necesita internet la primera vez, igual que cualquier
   proyecto Android — esto **no** es una conexión al sistema de
   facturación, son las librerías del proyecto).
4. Conecta un celular o usa un emulador con Google Play Services (ML Kit lo
   requiere para el primer uso; después el reconocimiento funciona 100%
   offline).
5. Ejecuta `Run ▶`.

> Nota: este entorno de trabajo no tiene el Android SDK ni acceso a los
> repositorios de Google/Gradle, así que el proyecto no se compiló aquí.
> El código sigue las convenciones estándar de un proyecto Android/Kotlin
> con Gradle y debería compilar sin cambios en Android Studio. Si algo no
> compila, lo más probable es una versión de plugin/librería que Android
> Studio pedirá actualizar automáticamente (Gradle sync).

## Estructura del proyecto

```
app/src/main/java/com/sumafacturas/app/
├── ui/
│   ├── MainActivity.kt        → Tomar foto / Seleccionar imagen / Agregar otra / Ver resultados
│   ├── CropActivity.kt        → Recortar tabla + "Seleccionar columna Valor"
│   ├── CropOverlayView.kt     → Rectángulo dibujable a mano sobre la imagen
│   ├── ReviewActivity.kt      → Tabla editable de revisión + totales
│   ├── ReviewAdapter.kt       → Filas editables (resaltadas si hay que revisar)
│   └── ResultsActivity.kt     → Resultado final del escaneo
├── ocr/
│   ├── OcrProcessor.kt        → OCR local con ML Kit + agrupación en "filas visuales"
│   ├── InvoiceParser.kt       → Clasifica Fecha/Centro/Referencia/Valor y suma solo Valor
│   └── ScanMerger.kt          → Combina varias fotos y elimina facturas repetidas
├── model/
│   └── InvoiceRow.kt          → Fila de factura + ScanResult (totales, conteos)
└── util/
    ├── FileUtils.kt           → Fotos temporales locales (FileProvider)
    └── ScanSession.kt         → Estado en memoria de la sesión de escaneo
```

## Cómo cubre cada punto de la especificación

| Requisito | Dónde |
|---|---|
| No conexión al sistema de facturación | No hay dependencias de red salvo Gradle/ML Kit; `AndroidManifest.xml` no declara permiso de `INTERNET` |
| Tomar foto / Seleccionar imagen / Ingreso manual | `MainActivity.kt` |
| Recortar zona de la tabla | `CropActivity.kt` + `CropOverlayView.kt` |
| Detectar encabezados Referencia/Transacción/Valor | `InvoiceParser.detectColumns()` |
| Sumar solo lo que está bajo "Valor" | `InvoiceParser.parse()` — filtra por posición horizontal (`isInsideColumn`), no por posición en el texto |
| No confundir Fecha/Centro/Referencia con Valor | Expresiones regulares dedicadas (`REGEX_FECHA`, `REGEX_CENTRO`, `REGEX_REFERENCIA`, `REGEX_VALOR`) en `InvoiceParser.kt` |
| "Seleccionar columna Valor" manual | Botón dedicado en `CropActivity`, dibuja un segundo rectángulo (azul) con `CropOverlayView` |
| Conteo por palabra FACTURA vs. referencias únicas + advertencia | `ScanResult.facturasDetectadas()` / `referenciasUnicas()`, mensaje armado en `InvoiceParser.parse()` y `ScanMerger.merge()` |
| Agregar otra imagen / unir resultados sin duplicar | `ScanSession` + `ScanMerger.kt` |
| Comparar referencia + valor + fecha antes de descartar duplicado | `ScanMerger.merge()` |
| Revisión obligatoria con filas resaltadas y editables | `ReviewActivity.kt` + `ReviewAdapter.kt` (punto rojo/verde, campos editables) |
| Dos totales (automático y revisado) | `ScanResult.totalDetectadoAutomatico()` / `totalDespuesRevision()`, mostrados en `ReviewActivity` |
| Resultado final con formato solicitado | `ResultsActivity.kt` genera exactamente el bloque "RESULTADO DEL ESCANEO..." |

## Privacidad

- Sin permiso de `INTERNET` en el manifiesto.
- Las fotos se guardan solo en la caché temporal de la app
  (`context.cacheDir`) y se pueden borrar con `FileUtils.clearCache()`.
- El reconocimiento de texto (ML Kit Text Recognition) corre en el
  dispositivo; ninguna imagen se sube a un servidor.

## Limitaciones conocidas / próximos pasos sugeridos

- La detección de columnas por posición asume que la tabla no está rotada
  ni muy distorsionada; para fotos muy inclinadas conviene agregar
  corrección de perspectiva antes del OCR (por ejemplo con OpenCV).
- El emparejamiento de "línea visual" agrupa elementos por coordenada Y;
  en tablas muy comprimidas puede requerir ajustar `rowToleranceRatio`
  en `OcrProcessor.groupIntoVisualLines()`.
- No se implementó persistencia (historial de escaneos guardados); todo
  vive en memoria durante la sesión, tal como pedía la especificación
  original de "no modificar ni guardar nada del sistema de facturación".
