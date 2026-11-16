# Guía de desarrollo de Fonaria

Cómo está hecha Fonaria por dentro y cómo trabajar en ella. Lo básico (requisitos, compilar, publicar) está en el [README](../README.md).

## Índice

- [Piezas](#piezas)
- [El motor nativo](#el-motor-nativo)
- [El modelo y las voces](#el-modelo-y-las-voces)
- [El servicio de voz](#el-servicio-de-voz)
- [El procesado del texto](#el-procesado-del-texto)
- [El audio](#el-audio)
- [Almacenamiento y Direct Boot](#almacenamiento-y-direct-boot)
- [Ajustes](#ajustes)
- [Descargas y actualizaciones](#descargas-y-actualizaciones)
- [La interfaz](#la-interfaz)
- [Pruebas](#pruebas)
- [Tareas frecuentes](#tareas-frecuentes)

## Piezas

| Pieza | Dónde | Qué hace |
|---|---|---|
| Motor nativo | `nativo/` (Rust) | Carga el modelo Phonon y genera audio. JNI para Kotlin. |
| Servicio de voz | `motor/ServicioVoz.kt` | El `TextToSpeechService` que usa Android. |
| Motor | `motor/Motor.kt` | Un solo modelo cargado para toda la app; cerrojo, voces y recarga. |
| Texto | `texto/` | Diccionario, deletreo, emojis, puntuación y frases. |
| Audio | `motor/Procesador.kt`, `Salida` | Silencios, Sonic, PCM y ritmo de entrega. |
| Voces | `voces/` | Catálogo, almacén en el móvil y descargas. |
| Interfaz | `ui/`, `accesibilidad/` | Pantallas, menú lateral, pestañas y ayudantes de accesibilidad. |

## El motor nativo

`nativo/` es un crate de Rust (`fonaria-nativo`, biblioteca `libfonaria.so`) con dos módulos:

- `motor.rs`: Rust puro. `Motor::abrir(carpeta, Carga)` construye un `ptts::synth::Synth` con el archivo de pesos elegido (`model.q8.gguf`, `model.q4k.gguf` o `model.safetensors`), los pasos del decodificador (`lsd_decode_steps`), el umbral de final de frase (`eos_threshold`) y el decodificador Mimi en q8 opcional. Arranca el grupo de hilos de `xn` una sola vez (`preparar_hilos`: hasta 4 en automático, porque los núcleos lentos de los móviles big.LITTLE frenan más que ayudan).
- Las voces se **registran** (nombre y ruta) y se leen del disco la primera vez que se usan (`asegurar_voz`), para no tener 102 voces en memoria.
- `hablar` aplica el ritmo como condicionante `duration_delta` (solo si cambia: `set_conditions` borra la caché de voces preparadas), normaliza el texto con `ptts::preprocess::Normalize` del idioma pedido y recorre el `SpeechStream`. Soltar el flujo detiene la generación.
- `puente.rs`: funciones `Java_com_xebolax_fonaria_motor_Nativo_*`. El manejador es un `Box<Motor>` guardado en un `Long`. Ningún pánico cruza a Java (`catch_unwind`); los errores se leen con `ultimoError()`. El audio se entrega llamando a `Receptor.audio(FloatArray, Int): Boolean`; si devuelve false se para.

Probarlo en el ordenador: `cargo run --release --example decir -- <carpeta modelo> <voz.safetensors> "texto" salida.wav [ritmo] [pesos]`. El ejemplo `entrega` (`entrega <modelo> <voz> <ritmo> <velocidad> <lote> "texto"...`) mide cómo llega el audio en el tiempo y simula la pista de Android: es el que dice si una voz en tiempo real se entrecortaría. Los dos se pueden compilar para `aarch64-linux-android` y ejecutar en un móvil desde `/data/local/tmp` con adb.

`nativo/vendor/ptts` es `ptts` 0.7.1 de crates.io (MIT o Apache-2.0) con un único cambio, marcado con «Fonaria:» en `src/synth.rs`; `Cargo.toml` lo usa con `[patch.crates-io]`. Se han quitado sus ejemplos y pruebas.

Compilar para Android: `herramientas/compilar_nativo.ps1`. Pone el enlazador del NDK por variables `CARGO_TARGET_<objetivo>_LINKER`, `-C link-arg=-Wl,-z,max-page-size=16384` (páginas de 16 KB, que exige Android 15 en Google Play) y `target-cpu=generic`. La variante `libfonaria_v82.so` añade `+dotprod,+fp16`: `xn` elige sus núcleos al compilar, así que esa biblioteca cerraría la app en un procesador sin esas instrucciones. `Nativo.cargar()` mira `asimddp` y `fphp`/`asimdhp` en `/proc/cpuinfo` antes de cargarla.

Actualizar `ptts`: copia la versión nueva a `nativo/vendor/ptts`, vuelve a aplicar el cambio de `set_decode_batch` (o quítalo si ya lo trae), cambia la versión en `nativo/Cargo.toml`, ejecuta `cargo update -p ptts`, `cargo test` y el ejemplo `decir`, y vuelve a compilar con `compilar_nativo.ps1`.

## El modelo y las voces

- Modelo: `gradium/phonon-40m-multilingual` (CC BY-NC 4.0, en Hugging Face tras aceptar la licencia). Se cuantizó con el ejemplo `quantize` de [xn-ptts](https://github.com/gradium-ai/xn-ptts):
  ```bash
  cargo run --release -p ptts --example quantize -- model.safetensors model.q8.gguf --quant q8_0 --no-mimi-encoder
  cargo run --release -p ptts --example quantize -- model.safetensors model.q4k.gguf --quant q4k --no-mimi-encoder
  ```
  `--no-mimi-encoder` quita el codificador de voz (solo hace falta para clonar desde audio): las voces de archivo siguen funcionando porque se conserva la proyección del hablante.
- Las huellas SHA-256 y los tamaños de los tres modelos están en `app/src/main/assets/modelos.json`.
- Voces: catálogo `gradium/phonon-voices` (Apache 2.0). `herramientas/generar_catalogo.py` crea `app/src/main/assets/catalogo_voces.json` a partir de `voices.jsonl` (con traducciones al español de las descripciones de las voces españolas) y copia a `modelo/apk/voces` las que van en el APK (`INCLUIDO = "es"`).
- `herramientas/preparar_modelo.ps1` deja `modelo/apk` listo: el modelo desde el servidor de Fonaria (o una carpeta con `-Desde`) y las voces desde Hugging Face, comprobando cada SHA-256.
- Gradle añade `../modelo/apk` como carpeta de assets (`sourceSets["main"].assets.srcDirs`) y no comprime los `.gguf`.

## El servicio de voz

`ServicioVoz` es el `TextToSpeechService`:

- **Idiomas**: Android pide en ISO de tres letras («spa», «ESP»); `Idiomas.codigo` y `Idiomas.region` los traducen. `disponibilidad` devuelve `LANG_COUNTRY_AVAILABLE` si hay voz de esa región, `LANG_AVAILABLE` si hay del idioma o se usa la voz de reserva, y `LANG_MISSING_DATA` si no.
- **Voces**: `onGetVoices` publica las instaladas con el nombre `es-ES-Celia` (las importadas llevan además su id).
- **Qué voz habla**: `Idiomas.elegirVoz`. La voz que pide el cliente solo se respeta si está activado «Usar la voz que pida cada app», porque un cliente (TalkBack) guarda la voz que recibió al conectarse y la sigue pidiendo aunque se cambie en Ajustes. Las pantallas de Fonaria fuerzan una voz con el parámetro `ServicioVoz.PARAM_VOZ` de `speak()`.
- **Parar**: `onStop` solo levanta un indicador; el receptor de audio devuelve false en el siguiente trozo y el motor deja de generar.
- `CHECK_TTS_DATA`, `GET_SAMPLE_TEXT`, `INSTALL_TTS_DATA` (abre Voces) y `CONFIGURE_ENGINE` (abre Ajustes) están en el manifiesto.

## El procesado del texto

`Preparador.preparar` aplica, en orden: marcas `TtsSpan` (lo literal se deletrea), diccionario, deletreo de un carácter suelto (`Simbolos`, con nombres de letras y signos en los cinco idiomas), emojis (`Emojis`, tablas CLDR en `assets/emojis/<idioma>.json`, buscando primero las secuencias más largas), puntuación por niveles y división en frases (`Trozos`, que respeta abreviaturas, iniciales y cifras con punto). El resultado es una lista de `Trozo.Habla` y `Trozo.Pausa`.

## El audio

- Limpieza (`Procesador.limpiar`): cada muestra se deja en [-1, 1] (y sin NaN) antes de Sonic. `Sonic.writeFloatToStream` hace `(short)(muestra * 32767f)`, y en Java pasar de float a short recorta a int y luego trunca los bits: 1,4 sale como -19663, un chasquido. Además los primeros 20 ms de cada frase se ponen a cero (arranque del decodificador) y la voz entra con un fundido *smoothstep* de 5 ms.
- `Procesador` (uno por frase): recorta el silencio del principio y guarda aparte el del final hasta saber si viene más voz (umbral 0,008 y margen de 20 ms). Si velocidad, tono o volumen no son 1, pasa por **Sonic** (`app/src/main/java/sonic/Sonic.java`, sin cambios) con calidad alta solo hasta 2×.
- Velocidad: la de Fonaria por la que manda el sistema. Esta se suaviza por defecto (`Ajustes.factorSistema`, exponente 0,6: 310 % → 1,97×), porque un 300 % pensado para un motor clásico es demasiado para una voz neuronal y además el móvil no la genera tan deprisa (un Snapdragon 845 llega a unas 2 veces el tiempo real con el modelo q8 y 4 hilos; q4k y Mimi en q8 van más lentos sin `dotprod`).
- Núcleos rápidos (`nativo/src/nucleos.rs`): los hilos de cálculo y el hilo que llama se fijan con `sched_setaffinity` a los núcleos de frecuencia máxima más alta (con un 25 % de margen). Sin esto, Android pone algún hilo en un núcleo lento y todos esperan por él. En un Snapdragon 845, el primer sonido de una palabra pasa de 127 a 57 ms. El número de hilos automático es el de núcleos rápidos (de 2 a 4).
- Números (`texto/Numeros.kt`): `ptts` deja en cifras los números de menos de mil y la voz a veces los trocea («105» como «cien y cinco»). Antes de enviarlos, los enteros sueltos de hasta seis cifras se escriben con letras con las reglas *spellout* de ICU (`android.icu.text.MessageFormat("{0,spellout}")`, Android 7+; la clase `RuleBasedNumberFormat` no es pública), y un «%» detrás pasa a «por ciento». Lo que va unido a otra cifra por `. , : / -` o pegado a letras se deja al motor.
- Texto entero: con «Hablar frase a frase» desactivado (por defecto), el texto va de una vez al motor, que lo trocea por dentro sin volver a preparar la voz; así no hay huecos entre frases y parar sigue siendo inmediato.
- Aceleración por el modelo (`motor/Acelerar.kt`): a partir de 1,15× la velocidad final se reparte entre el ritmo nativo (`duration_delta`, hasta -0,6, que acorta la frase un 25 % y se entiende igual) y Sonic. Generar menos audio da margen al procesador: en un Snapdragon 845, al 412 % de TalkBack, el motor pasa de ir justo a sobrarle un 20 %.
- Cola de Sonic: Sonic se guarda los últimos milisegundos de lo que recibe hasta tener más audio. Al empezar cada pausa, `Procesador` le pasa enseguida 60 ms del silencio (el «empujón») para que suelte el final de la palabra a su tiempo; el resto del silencio se aparta y se recorta.
- Generador (`motor/Generador.kt`): recibe todas las frases de una lectura y las genera seguidas en su propio hilo, dejando la voz en una cola de hasta 20 s (con una marca de fin por frase); el hilo de síntesis de Android solo procesa (Sonic) y entrega. Android bloquea al que entrega cuando hay medio segundo pendiente: si fuese el mismo que genera, el motor se pararía y perdería su ventaja. Y si cada frase esperase a que sonara la anterior, al empezar cada una (preparar texto y voz) no quedaría margen.
- Primer plano (`motor/PrimerPlano.kt`): en cada lectura se pide un servicio en primer plano de tipo `mediaPlayback`, que se para solo tras 2 minutos sin uso. Sin él Android pone el proceso en el cpuset de segundo plano (núcleos lentos) con la pantalla apagada o dentro de otra app: en otro proyecto con estas voces se midió 0,9× el tiempo real así frente a 3× en primer plano. Android 12+ lo niega desde segundo plano (también cuando el motor lo usa TalkBack: el proceso está en estado `IMPF`) salvo que la app esté exenta de la optimización de batería: Ajustes, Motor, «Uso de batería sin restricciones» abre esa petición. Si lo niega, se reintenta al cabo de un minuto. Durante la síntesis se mantiene además un `PARTIAL_WAKE_LOCK`.
- Prioridades (`motor/Hilos.kt`): busca en `/proc/self/task` los hilos por nombre, en segundo plano y como mucho cada 3 s (recorrer `/proc` cuesta 20 ms con el procesador en reposo): los que acaban en `PlaybackThread` (reproducción de Android; ART recorta los nombres largos por el principio) a `URGENT_AUDIO`, `SynthThread` a `AUDIO` y los `fonaria*` (también los nativos, que heredan el nombre de quien los crea) a `VIDEO`.
- Lotes del decodificador (`nativo/vendor/ptts`, `motor.rs` `LOTE`): `ptts` decodifica de una vez todas las tramas que tenga esperando, hasta 16 (`DECODE_BATCH`). En un móvil eso entrega la voz a golpes (80 ms, 80, 240, 320... hasta 1,28 s) con 250 a 450 ms entre uno y otro: la pista de Android se quedaba sin audio al principio de casi cada frase. La copia de `ptts` de `nativo/vendor` añade `ptts::synth::set_decode_batch`: las cuatro primeras llamadas de cada frase decodifican una trama y las demás, hasta `LOTE` (2). En un Snapdragon 845 llegan 160 ms cada 60 ms sin perder velocidad (con 1 se pierde un 10 %). Se mide con el ejemplo `entrega`.
- Audio por delante: `ptts` parte los textos largos en trozos de unos 50 tokens y al empezar cada uno se para un instante (150–400 ms en un Snapdragon 845). Por eso la salida deja ir hasta 1,5 s por delante de lo que suena (Ajustes, Motor, «Audio por adelantado»). Callar no empeora: al parar, Android descarta lo pendiente.
- Velocidad del motor: `Motor.velocidadGeneracion` es la media de los segundos de voz por segundo de cálculo medidos en cada frase (entre el primer trozo y el último, sin contar lo que el receptor tarda en aceptar el audio). Se guarda en las preferencias `estado` para que la primera frase tras arrancar ya la conozca, y se olvida al cambiar de modelo o de hilos.
- Tope de velocidad (`ServicioVoz.sintetizar`): Sonic no acelera más de `velocidadGeneracion / 1,15`. Si la velocidad pedida lo supera, primero se sube la parte del modelo (hasta ritmo -0,6) y después se recorta. Sustituye a la retención de audio de versiones anteriores, que llegaba a esperar 4 s antes de un texto largo. Ajuste `limitar_velocidad`.
- Arranque suave (`Procesador`, parámetro `arranque`): los primeros trozos de cada frase tardan unas 2,5 veces más que los siguientes (el modelo de lenguaje y el decodificador se reparten los núcleos hasta coger ritmo). Sonic empieza a `velocidadGeneracion / 2,5` (nunca menos de 1) durante 480 ms de voz y sube en línea recta hasta la pedida en otros 480.
- Memoria de frases (`motor/Memoria.kt`): el PCM final de las lecturas de hasta 160 caracteres y 8 s se guarda (8 MB, sale la menos usada) con una clave que incluye texto, voz, velocidad, ritmo, tono, volumen, `Ajustes.revision` y la fecha del diccionario. Solo con semilla fija. Al repetirse, sale de una vez sin tocar el motor.
- `Salida` (`motor/Salida.kt`): la pista de audio que Android crea para la voz (`BlockingAudioTrack`, búfer de 8192 bytes = 170 ms a 24 kHz) no empieza a sonar hasta tener el búfer lleno. Por eso al empezar se **ceba** con 180 ms de silencio, nada más llegar la petición y mientras el motor prepara la frase: la pista ya está en marcha cuando llega la voz. La voz se retiene hasta tener un colchón (`ServicioVoz.colchon`: 40 ms más lo que le falte al arranque), contando el silencio del cebado que aún queda por sonar, y después cada trozo se entrega según llega. Trocea al `maxBufferSize` y no se adelanta más de «Audio por adelantado» a lo que suena. Al guardar en archivo ni ceba ni espera. Lleva una simulación de la pista para el registro: «huecos N (M ms)» son las veces que se habría quedado sin audio.

## Almacenamiento y Direct Boot

Todo lo del motor vive en el almacenamiento **protegido del dispositivo** (`Ajustes.contextoProtegido`), que se puede leer antes de desbloquear el móvil tras reiniciar:

- `fonaria/modelo/` (copiado del APK la primera vez y tras cada actualización, con una marca del `versionCode`), `fonaria/voces/`, `fonaria/importadas/<id>/` y `fonaria/diccionario.json`.
- Los ajustes, en las preferencias `ajustes` de ese mismo almacenamiento (también las pantallas de Ajustes, con `setStorageDeviceProtected()`).
- El servicio y las actividades de datos de voz son `directBootAware`.

## Ajustes

`datos/Ajustes.kt` reúne todas las claves con sus valores por defecto. Los ajustes de sonido de cada voz son `voz.<id>.<clave>`; `Ajustes.parametros(voz)` toma el propio o, si no hay, el general. Cambiar modelo, hilos, pasos, umbral o decodificador cambia `claveCarga()` y el modelo se recarga en la siguiente frase.

## Descargas y actualizaciones

- `red/Descargador`: reanuda con `Range`, reintenta si avanza, comprueba tamaño y SHA-256 y solo deja el archivo en su sitio cuando está verificado.
- Voces: `<servidor>/voces/<archivo>` y, si falla, Hugging Face. Modelos opcionales: `<servidor>/modelos/<archivo>`.
- Actualizaciones de la app: `version.json` del servidor, la última release de GitHub (con su `version.json` adjunto) o los dos. Antes de instalar se comprueba paquete, versión y firma.

Carpeta del servidor (`SynologyDrive\web\Fonaria`):

```
version.json
Fonaria_v1.0.0.apk
voces/<id>.safetensors      (las 102, y catalogo_voces.json)
modelos/model.q4k.gguf, model.safetensors, LICENCIA.txt
modelo/config.json, tokenizer.json, model.q8.gguf, LICENCIA.txt   (para preparar_modelo.ps1)
```

## La interfaz

Views con Material Components 3. `PrincipalActivity` monta el menú lateral (`accesibilidad/MenuLateral.kt`) con las secciones como fragmentos; Voces y Ajustes usan pestañas. Los ayudantes de `accesibilidad/` vienen del agente «Android accesible»: `MemoriaFoco` (el foco vuelve al cerrar ventanas), `conEspera` (esperas con Cancelar), `DeslizadorAjuste` (valor con unidad y paso exacto), `accionesAccesibles`, diálogos y avisos. Las filas de lista (`ui/Filas.kt`) son de una sola parada con acciones de TalkBack.

## Pruebas

| Prueba | Dónde | Cómo |
|---|---|---|
| Texto y versiones | `app/src/test/.../TextoTest.kt` | `gradle testDebugUnitTest` |
| Motor del sistema | `app/src/androidTest/.../MotorTest.kt` | En el emulador; deja WAV en `files/pruebas` |
| Accesibilidad | `app/src/androidTest/.../AccesibilidadTest.kt` | En el emulador con TalkBack activo |
| Motor nativo | `nativo/src/motor.rs` | `cargo test` |

TalkBack solo se activa en el **emulador**, nunca en un móvil real conectado. Usa `ANDROID_SERIAL=emulator-5554` si hay varios dispositivos.

## Tareas frecuentes

- **Añadir un ajuste**: clave y valor por defecto en `Ajustes.kt`, la preferencia en el XML de su pestaña (`res/xml/ajustes_*.xml`), sus textos en `values/strings.xml` y `values-en/strings.xml`, y su uso donde haga falta. Si es un deslizador, su formato en `FonariaApp.formatosDeslizadores`.
- **Añadir un idioma de la interfaz**: ver [TRADUCIR.md](TRADUCIR.md) y añadirlo a `idioma_app_nombres`/`idioma_app_valores`.
- **Cambiar las voces incluidas**: `INCLUIDO` en `generar_catalogo.py`, ejecutarlo y `preparar_modelo.ps1`.
- **Nueva versión**: ver «Publicar una versión» en el README.
