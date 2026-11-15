# Fonaria

Motor de voz (TTS) para Android con las voces neuronales Phonon de Gradium, accesible con TalkBack. Todo en español. Cómo se compila, se prueba y se publica está en `README.md` y `docs/DESARROLLO.md`.

## Repositorio (regla permanente)

- Remoto: `https://github.com/hxebolax/Fonaria`. Se sube solo lo necesario del proyecto (ver `.gitignore`): nunca `entorno`, `firma`, `adjuntos`, `modelo` (salvo `LEEME.md`), `app/src/main/jniLibs`, compilaciones ni APK.
- Al terminar cada encargo se hace commit de lo cambiado, con el mensaje en español. Cada versión publicada lleva su etiqueta `v1.0.0`, `v1.0.1`...
- Antes de empezar se mira `git status`: si hay cambios de antes sin guardar, van primero en su propio commit.
- Nada de `reset --hard`, `push --force` ni borrar etiquetas o historial sin que se pida.

La firma no se puede recuperar desde aquí: `firma\fonaria.jks` y `firma\firma.properties` hay que guardarlos aparte.

## Antes de publicar

- `compilar.bat pruebas` y `compilar.bat compilar` sin errores.
- Comprobar el cambio en el emulador. TalkBack se activa solo en el emulador, nunca en un móvil real (`ANDROID_SERIAL=emulator-5554` si hay un móvil conectado).
- Primero commit y `git push`: la release de GitHub se crea sobre el commit actual y el script no la crea si no está subido. Después, `compilar.bat publicar` copia la versión a `C:\Users\xebol\SynologyDrive\web\Fonaria` y crea la release y su etiqueta en GitHub.

## Agentes

Para Android: `E:\Antigravity\Agentes\agentes\android-accesible` y `entorno-android-portable`.
