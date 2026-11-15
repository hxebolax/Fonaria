# Carpeta modelo

Aquí se prepara lo que pesa demasiado para guardarlo en git (por eso solo se sube este archivo):

- `apk/modelo/`: `config.json`, `tokenizer.json` y `model.q8.gguf`, que van dentro del APK.
- `apk/voces/`: las 25 voces en español que van dentro del APK.
- `voces/`: las 102 voces del catálogo (para publicarlas en el servidor).
- `model.q4k.gguf` y `model.safetensors`: los modelos opcionales que se descargan desde la app.

Para prepararla: `compilar.bat modelo` o `powershell -File herramientas\preparar_modelo.ps1` (con `-TodasLasVoces` baja también las de otros idiomas).

El modelo Phonon 40m multilingüe es de Gradium y tiene licencia CC BY-NC 4.0 (uso no comercial): ver `docs/LICENCIA-MODELO.txt`.
