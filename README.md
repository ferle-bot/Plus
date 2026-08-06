# PulseDownloader - Android Downloader & Web Link Grabber

PulseDownloader es un gestor de descargas de alto rendimiento para Android con análisis inteligente de enlaces web (Instagram, Unsplash, directos), organización automática de archivos, sincronización con Google Drive y servidor de cola remota para extensión de navegador PC.

## 🚀 Generación de APK Automática mediante GitHub Actions

Este repositorio cuenta con un flujo de trabajo configurado en **GitHub Actions** (`.github/workflows/build-apk.yml`) que genera automáticamente el archivo `.apk` instalable cada vez que se sube código al repositorio.

### Pasos para obtener tu APK en GitHub:

1. **Sube o exporta este proyecto a GitHub** usando la opción **"Push to GitHub"** o **"Export to GitHub"** en la barra superior / menú de Google AI Studio.
2. Ve a la pestaña **Actions** en tu repositorio de GitHub.
3. Verás la ejecución del flujo **"Build Android APK"**.
4. Una vez finalizada (marcada con un check verde ✅), haz clic en la ejecución.
5. En la sección **Artifacts** (en la parte inferior de la página de la ejecución), descarga el archivo **`PulseDownloader-v1.0-debug.apk`**.
6. Instálalo en tu dispositivo Android.

---

## 🛠️ Compilación Local
Si prefieres compilar localmente con Gradle:
```bash
gradle :app:assembleDebug
```
El APK resultante se ubicará en: `app/build/outputs/apk/debug/app-debug.apk`.
