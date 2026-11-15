# Fonaria

**Voces neuronales naturales para todo Android, sin conexión y pensadas para TalkBack.**

Fonaria es un motor de texto a voz (TTS) para Android basado en **Phonon**, el modelo de voz neuronal de Gradium. Una vez elegido como motor del sistema, TalkBack y cualquier app que hable (lectores de libros, navegadores, GPS…) usan sus voces. Todo se genera en el móvil: no necesita internet ni envía nada a ningún sitio.

- Versión actual: **1.0.2** · [Descargar el APK](https://github.com/hxebolax/Fonaria/releases/latest)
- Android 5.0 (API 21) o posterior · procesadores de 64 bits (arm64) y de 32 bits (armv7).
- Trae **las 25 voces en español** (España y México). Las de inglés, francés, alemán y portugués se descargan desde la app.
- Interfaz en español (principal) e inglés, fácil de traducir.
- Autor: Héctor J. Benítez Corredera.
- Licencia: [GNU GPL versión 3 o posterior](LICENSE). El modelo de voz tiene su propia licencia (CC BY-NC 4.0, uso no comercial).

---

## Índice

- [Para quien usa la app](#para-quien-usa-la-app)
  - [Qué vas a encontrar](#qué-vas-a-encontrar)
  - [Las voces](#las-voces)
  - [Herramientas](#herramientas)
  - [Todo lo que se puede ajustar](#todo-lo-que-se-puede-ajustar)
  - [Accesibilidad](#accesibilidad)
  - [Instalar, activar y actualizar](#instalar-activar-y-actualizar)
  - [Móviles antiguos](#móviles-antiguos)
  - [Privacidad](#privacidad)
- [Para quien quiere compilarla o colaborar](#para-quien-quiere-compilarla-o-colaborar)
  - [Cómo funciona por dentro](#cómo-funciona-por-dentro)
  - [Requisitos](#requisitos)
  - [Compilar con compilar.bat (Windows)](#compilar-con-compilarbat-windows)
  - [Compilar a mano](#compilar-a-mano)
  - [Pruebas](#pruebas)
  - [Publicar una versión](#publicar-una-versión)
  - [Estructura del proyecto](#estructura-del-proyecto)
  - [Traducir la app](#traducir-la-app)
- [Licencia](#licencia)
- [Créditos](#créditos)
- [English summary](#english-summary)

---

## Para quien usa la app

### Qué vas a encontrar

Fonaria se organiza con un **menú lateral** y, en las secciones con varias partes, **pestañas abajo** con icono y nombre:

- **Inicio**: si Fonaria es el motor del sistema, qué voces tienes, una prueba rápida con cualquier voz y el botón para elegir Fonaria como motor de voz de Android.
- **Voces**, con tres pestañas:
  - **Instaladas**: escuchar cada voz, elegirla para su idioma, ajustarla o borrarla. Cada voz dice su perfil (por ejemplo, «Mujer joven adulta, Español (España)») y una descripción de cómo suena.
  - **Descargar**: las voces de otros idiomas, una a una o un idioma completo, con progreso y verificación SHA-256.
  - **Importadas**: tus propias voces diseñadas o clonadas en Gradium Studio (`.safetensors`).
- **Diccionario**: corrige cómo se pronuncian palabras, siglas y nombres. Compatible con los diccionarios de voz de **NVDA**.
- **Leer texto**: escribe o pega un texto, óyelo con cualquier voz y **guárdalo como audio WAV**.
- **Ajustes**, con seis pestañas abajo: General, Voces, Sonido, Texto, Motor y Actualizaciones.
- **Manual** completo dentro de la app, **Buscar actualizaciones** y **Acerca de** (con licencias y un diagnóstico que se puede copiar).

### Las voces

| Idioma | Voces | Viene con la app |
|---|---|---|
| Español de España | 13 (Noa, Lucía-Sol, Candela, Celia, Alba, Alicia, Nerea, Marcos, Borja, Iker, Nuno, Izan, Guillermo) | Sí |
| Español de México | 12 (Rebeca, Regina, Danae, Azucena, Jimena, Ximena, Lalo, Aldo, Yael, Ulises, Leonel, Nico) | Sí |
| Inglés del Reino Unido | 13 | Se descarga |
| Inglés de Estados Unidos | 12 | Se descarga |
| Inglés, voces de personaje | 12 (capitán pirata, hada madrina, ogro, dragón…) | Se descarga |
| Francés | 15 | Se descarga |
| Alemán | 13 | Se descarga |
| Portugués de Brasil | 12 | Se descarga |

En total, **102 voces**. Cada una ocupa unos 250 KB. Si una app pide un idioma del que no tienes voz, Fonaria lo lee con una voz española en lugar de quedarse callada (se puede desactivar).

### Herramientas

- **Diccionario de pronunciación**: palabra completa, en cualquier sitio o expresión regular; distinguir mayúsculas; por idioma; activar o desactivar; comentarios; escuchar cómo suena. Importa y exporta el formato `.dic` de NVDA y copias de Fonaria.
- **Deletreo**: una letra o un signo sueltos se dicen por su nombre en cada idioma («be», «uve doble», «almohadilla»), con la mayúscula en un tono más alto.
- **Emojis por su nombre** en los cinco idiomas (datos de Unicode CLDR).
- **Lectura de la puntuación** en cuatro niveles.
- **Normalización del texto** por el motor: números, fechas, horas, monedas, correos y direcciones web.
- **Guardar como audio** cualquier texto.
- **Medir la velocidad del motor** en tu móvil.
- **Exportar e importar toda la configuración** para pasarla a otro móvil.

### Todo lo que se puede ajustar

- **Por voz** (y en general): velocidad (25–400 %), tono, volumen, **ritmo natural** (lo cambia el propio modelo) y **variación de la entonación**.
- **Sonido**: usar o no la velocidad y el tono de TalkBack o de Android, misma lectura cada vez o tomas distintas, igualar el volumen, fidelidad a la voz (guía sin clasificador), recortar silencios, pausas entre frases y párrafos.
- **Voz de cada idioma y región**, voz de reserva y respetar la voz que pida cada app.
- **Velocidad de TalkBack**: suavizada para voz neuronal (300 % suena unas 2 veces más rápido), tal cual o sin usar.
- **Motor**: modelo integrado (q8), compacto (q4k) o de máxima calidad (f32), hilos de cálculo, pasos de decodificación, umbral de final de frase, tamaño de los trozos, audio por adelantado y decodificador acelerado.
- **General**: idioma de la app, tema claro u oscuro, actualizaciones desde el servidor, GitHub o los dos, y de dónde se descargan las voces.

### Accesibilidad

Fonaria está hecha para manejarse por completo con TalkBack:

- Cada pantalla se anuncia al entrar y tiene encabezados para navegar por ellos.
- El menú lateral pone el foco en la sección actual y solo esa se anuncia como seleccionada.
- Las filas de las listas se leen de una vez, con todas sus acciones en el menú de acciones de TalkBack y al mantener pulsado.
- Los deslizadores dicen su valor con su unidad («150 milisegundos», «120 por ciento») y avanzan un paso exacto.
- Las esperas largas tienen ventana con Cancelar, y el foco vuelve a su sitio al cerrar cualquier ventana.
- Objetivos táctiles de 48 dp, contraste suficiente en claro y oscuro, y texto que aguanta la letra grande.
- El motor funciona en la **pantalla de bloqueo nada más reiniciar** (Direct Boot).
- Se ha probado con una prueba automática que recorre la app con TalkBack activo y el Accessibility Test Framework de Google.

### Instalar, activar y actualizar

1. Descarga el APK de la [última versión](https://github.com/hxebolax/Fonaria/releases/latest) e instálalo (Android pedirá permitir la instalación desde esa app).
2. Abre Fonaria. La primera vez prepara el modelo (unos segundos) y te ofrece elegirla como motor.
3. En los ajustes de texto a voz de Android, en **Motor preferido**, elige **Fonaria**.

Fonaria se actualiza sola: busca versiones nuevas al abrirse (como mucho cada seis horas), muestra las novedades y, si aceptas, descarga y comprueba el APK (tamaño, SHA-256, paquete, versión y firma) antes de abrir el instalador de Android.

### Móviles antiguos

Las voces neuronales necesitan más procesador que las clásicas. Fonaria lleva dos versiones del motor para 64 bits (una optimizada para procesadores ARMv8.2, casi todos desde 2019, y otra para cualquiera) y elige sola; en 32 bits usa una versión compatible. «Medir la velocidad del motor» dice a qué ritmo genera tu móvil (un Snapdragon 845, unas 2 veces el tiempo real); si se pide hablar más rápido, Fonaria guarda un poco de audio antes de cada frase para que no se entrecorte.

### Privacidad

Lo que Fonaria lee se genera en tu móvil y no sale de él. Internet solo se usa para descargar voces, modelos y actualizaciones. Sin publicidad ni estadísticas.

---

## Para quien quiere compilarla o colaborar

### Cómo funciona por dentro

```
App que habla (TalkBack...)
   │  TextToSpeech de Android
   ▼
ServicioVoz (Kotlin)                 motor/ServicioVoz.kt
   │  Preparador: diccionario, deletreo, emojis, puntuación, frases     texto/
   │  Elegir voz según idioma y región                                 motor/Idiomas.kt
   ▼
Motor (Kotlin, un solo modelo cargado)   motor/Motor.kt
   │  JNI
   ▼
libfonaria.so (Rust)                 nativo/src/puente.rs, nativo/src/motor.rs
   │  ptts 0.7.1 de Gradium: tokenizador, normalización, LM de flujo y códec Mimi
   ▼
Audio 24 kHz flotante ──► Procesador (recorte de silencios, Sonic: velocidad, tono, volumen)
                         ──► Salida (PCM 16 bits, ritmo para no adelantarse a lo que suena)
```

- El **motor nativo** (`nativo/`) es una biblioteca de Rust que usa el crate [`ptts`](https://crates.io/crates/ptts) de Gradium y expone sus funciones a Kotlin por JNI: cargar el modelo, registrar voces (se leen del disco la primera vez que se usan), hablar con temperatura, semilla, ritmo (`duration_delta`), guía sin clasificador, tamaño de trozo y compresor-limitador opcional.
- Se compila para `arm64-v8a` (dos variantes: genérica y ARMv8.2 con `dotprod` y `fp16`; la app elige al arrancar mirando `/proc/cpuinfo`), `armeabi-v7a` y `x86_64` (emulador), con páginas de 16 KB.
- El **modelo** es `phonon-40m-multilingual` cuantizado a GGUF q8_0 (el integrado) y q4_K (el compacto) con el ejemplo `quantize` de `xn-ptts`. Va dentro del APK y se copia al almacenamiento protegido del dispositivo, para que el motor funcione antes de desbloquear el móvil.
- Las pantallas de la app hablan a través del propio servicio del sistema, así que el modelo se carga una sola vez.

Más detalles en [docs/DESARROLLO.md](docs/DESARROLLO.md).

### Requisitos

- **Windows** con PowerShell (para `compilar.bat`), o cualquier sistema con JDK 17, Gradle 8.11.1, Android SDK 35 y NDK r27.
- **Rust** (stable) para el motor nativo: `rustup`, con los objetivos `aarch64-linux-android`, `armv7-linux-androideabi` y `x86_64-linux-android`. `preparar_entorno.ps1 -ConRust` lo instala dentro de `entorno\rust`.
- Unos 8 GB libres para el entorno portátil (con emulador).

### Compilar con compilar.bat (Windows)

Doble clic en `compilar.bat`. Su menú accesible:

1. Compilar el APK (prepara lo que falte: entorno, modelo, motor nativo y firma).
2. Compilar e instalar en el móvil conectado por USB.
3. Instalar el APK ya compilado.
4. Ver móviles conectados.
5. Abrir la máquina virtual de pruebas (emulador).
6. Pasar las pruebas automáticas.
7. Compilar el motor nativo (Rust).
8. Compilar y publicar la versión (servidor y GitHub).
9. Preparar o reparar el entorno.

También sin menú: `compilar.bat compilar`, `instalar`, `todo`, `nativo`, `modelo`, `emulador`, `pruebas`, `publicar` o `entorno`.

Todo el entorno (JDK, SDK, NDK, Gradle, emulador y, si se pide, Rust) se descarga en la carpeta `entorno`, sin instalar nada en el sistema.

### Compilar a mano

```bash
# 1. El modelo y las voces españolas del APK (en modelo/apk, fuera de git)
powershell -File herramientas/preparar_modelo.ps1
# 2. El motor nativo (en app/src/main/jniLibs, fuera de git)
powershell -File herramientas/compilar_nativo.ps1
# 3. La app
gradle assembleDebug        # versión de pruebas (com.xebolax.fonaria.pruebas)
gradle assembleRelease      # firmada con firma/firma.properties si existe
```

El motor también se puede probar en el ordenador, sin Android:

```bash
cd nativo
cargo test
cargo run --release --example decir -- ../modelo/apk/modelo ../modelo/apk/voces/rmLBOdHrfkFym9MM.safetensors "Hola, soy Fonaria." prueba.wav
```

### Firma

La primera compilación crea `firma\fonaria.jks` y `firma\firma.properties`. **Guárdalos aparte**: sin ellos no se puede actualizar la app instalada. La carpeta `firma` no se sube nunca a git.

### Pruebas

- `gradle testDebugUnitTest`: procesado de texto (frases, abreviaturas, diccionario y formato NVDA, deletreo, puntuación) y comparación de versiones.
- `gradle connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.xebolax.fonaria.MotorTest`: hace hablar al motor del sistema en el emulador (español de España y México, voz de reserva para inglés, deletreo, párrafos con emojis) y deja los WAV en `/sdcard/Android/data/com.xebolax.fonaria.pruebas/files/pruebas`.
- `AccesibilidadTest`: recorre las pantallas con TalkBack activo **en el emulador** y pasa el Accessibility Test Framework. Lee el informe con `adb logcat -d -s Recorrido ATF Foco Menu`.
- `cargo test` en `nativo`.

### Publicar una versión

1. Sube `versionCode` y `versionName` en `app/build.gradle.kts`.
2. Añade la sección `== <versión>` en `NOVEDADES.txt`.
3. `compilar.bat publicar` (opción 8): compila, crea `dist\Fonaria_v<versión>.apk` y `dist\version.json` (tamaño, SHA-256 y novedades), los copia a la carpeta del servidor y crea la release `v<versión>` en GitHub con `gh`.
4. Con `herramientas\publicar_actualizacion.ps1 -ConRecursos` se copian además al servidor las voces de otros idiomas (`voces/`), los modelos opcionales (`modelos/`) y el modelo integrado (`modelo/`).

El servidor de actualizaciones es `https://xebolax.synology.me/Fonaria` (se puede cambiar al compilar con `-PurlActualizaciones=` o en Ajustes); el repositorio de GitHub, `hxebolax/Fonaria` (`-PrepoGithub=`).

### Estructura del proyecto

```
app/src/main/java/com/xebolax/fonaria/
  motor/            Nativo (JNI), Motor, ServicioVoz (TextToSpeechService), Idiomas, Procesador (audio), DatosVoz
  texto/            Preparador, Diccionario, Simbolos (letras y signos en 5 idiomas), Emojis, Trozos (frases)
  voces/            Catalogo, Almacen (modelo y voces en el móvil), Descargas
  datos/            Ajustes, Copias (exportar e importar), Fallos
  actualizaciones/  Actualizador (servidor y GitHub), GestorActualizaciones
  red/              Descargador (reanudación y SHA-256)
  ui/               Actividades y secciones; Locutor (la app habla a través del servicio)
  accesibilidad/    Ayudantes de accesibilidad (menú lateral, deslizadores, foco, esperas)
app/src/main/java/sonic/   Sonic de Bill Cox (Apache 2.0)
app/src/main/assets/       catálogo de voces, modelos.json, emojis (CLDR), manual y licencias
nativo/                    motor de Rust (JNI sobre ptts)
herramientas/              preparar_entorno, preparar_modelo, compilar_nativo, publicar_actualizacion, generar_catalogo
docs/                      guía de desarrollo, traducción y licencia del modelo
```

### Normas del proyecto

- Todo en español de España: interfaz, comentarios, nombres de código y documentación (la API de Android y de Rust, en su idioma).
- **La accesibilidad es un requisito de cada cambio**: cada control con nombre, filas de una sola parada, foco que vuelve, nada que solo se vea.
- Ningún error se pierde: `Log.e` con la excepción y `CancellationException` siempre relanzada.
- Antes de proponer un cambio, compila, pasa las pruebas y pruébalo con TalkBack en el emulador.

### Traducir la app

Los textos están en `app/src/main/res/values/strings.xml` (español) y `values-en/strings.xml` (inglés). Para otro idioma, copia esos archivos a `values-<código>/` y traduce el texto. El manual está en `app/src/main/assets/manual/manual_<código>.html`. Paso a paso en [docs/TRADUCIR.md](docs/TRADUCIR.md).

---

## Licencia

Fonaria es software libre: puedes usarlo, estudiarlo, compartirlo y modificarlo según los términos de la [Licencia Pública General de GNU, versión 3](LICENSE) o (a tu elección) cualquier versión posterior. Se distribuye sin ninguna garantía.

Copyright (C) 2026 Héctor J. Benítez Corredera

**El modelo de voz no es software y tiene su propia licencia.** El modelo Phonon 40m multilingüe de Gradium se distribuye bajo [CC BY-NC 4.0](docs/LICENCIA-MODELO.txt): se puede usar y compartir **sin fines comerciales** y citando a Gradium. Por eso, aunque el código de Fonaria es GPL, **el APK, que contiene el modelo, no se puede usar ni distribuir con fines comerciales**. El modelo no se guarda en este repositorio; `herramientas/preparar_modelo.ps1` lo descarga.

## Créditos

- [Gradium](https://gradium.ai/phonon): modelo Phonon, catálogo de voces (Apache 2.0) y motor [`ptts`/xn-ptts](https://github.com/gradium-ai/xn-ptts) (MIT o Apache 2.0), basados en [Pocket TTS](https://github.com/kyutai-labs/pocket-tts) y Mimi de Kyutai.
- [Sonic](https://github.com/waywardgeek/sonic), de Bill Cox (Apache 2.0): velocidad y tono.
- [Unicode CLDR](https://cldr.unicode.org/) (licencia de Unicode): nombres de los emojis.
- AndroidX, Material Components e iconos de Material Design (Apache 2.0).
- Ideas: [Phonon para NVDA](https://github.com/OnjLouis/phononNVDA), de Andre Louis, y [evvdroid](https://github.com/trypsynth/evvdroid). Su código no se ha copiado.
- Las bibliotecas de Rust del motor y sus licencias están en `app/src/main/assets/licencias/dependencias-nativas.txt`.

Fonaria es un proyecto independiente: no es un producto oficial de Gradium.

---

## English summary

**Fonaria** is an offline neural text-to-speech engine for Android built on Gradium's **Phonon** model and designed to be fully usable with TalkBack. It ships with the multilingual model and all **25 Spanish voices** (Spain and Mexico); the other 77 voices (British and American English, English character voices, French, German, Brazilian Portuguese) are downloaded in the app, and you can import your own Gradium Studio voices. It offers per-voice rate, pitch, volume, natural pace and intonation variation; a pronunciation dictionary compatible with NVDA `.dic` files; spelling, emoji names, punctuation levels and text normalisation; save-as-WAV; three model variants (arm64 with an ARMv8.2-optimised build, and armv7); Direct Boot support; and self-updates from the author's server or GitHub. The UI is in Spanish and English.

The native engine is a Rust JNI library over the [`ptts`](https://crates.io/crates/ptts) crate (`nativo/`). Build on Windows with `compilar.bat`, which sets up a portable JDK, Android SDK/NDK, Gradle and emulator in `entorno\`. Elsewhere: Rust + NDK r27 + JDK 17 + Gradle 8.11.1 + SDK 35; run `herramientas/preparar_modelo.ps1`, `herramientas/compilar_nativo.ps1`, then `gradle assembleDebug`. The code is GPL-3.0-or-later; the voice model is CC BY-NC 4.0 (non-commercial), so the APK must not be used commercially.
