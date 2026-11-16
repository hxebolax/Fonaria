//! El motor de Fonaria: un modelo Phonon cargado una vez y las voces que se le van pidiendo.
//!
//! Todo lo que hay aquí es Rust puro, sin nada de Java, para poder probarlo en el ordenador
//! (`cargo test`, `cargo run --example decir`). El puente con Android está en `jni.rs`.
//!
//! Reglas del motor (las mismas que las de `ptts`):
//! - El modelo se carga una vez y se reutiliza: cargar es lo lento.
//! - Cada llamada bloquea; se hace fuera del hilo principal y desde un solo hilo a la vez.
//! - El audio sale a 24 kHz, mono, en coma flotante entre -1 y 1, a trozos según se genera.

use ptts::checkpoint::read_config;
use ptts::comp::ModuleConfig;
use ptts::preprocess::Normalize;
use ptts::synth::{DeviceKind, Quant, SpeechOptions, Synth, SynthBuilder};
use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::time::Instant;

/// Nombre del condicionante que cambia el ritmo natural del modelo.
const RITMO: &str = "duration_delta";

/// Archivos de pesos que se buscan, en este orden, si no se pide uno concreto.
pub const PESOS: [&str; 3] = ["model.q8.gguf", "model.q4k.gguf", "model.safetensors"];

/// Cómo se carga el modelo. Cambiar cualquiera de estos valores obliga a recargarlo.
#[derive(Clone, Debug, Default)]
pub struct Carga {
    /// Archivo de pesos dentro de la carpeta del modelo; vacío para el primero que exista.
    pub pesos: String,
    /// Pasos del decodificador de flujo. 0 deja los del modelo (1). Más pasos, más calidad y
    /// más lentitud.
    pub pasos: usize,
    /// Umbral de fin de frase. No finito deja el del modelo. Más bajo deja hablar más tiempo
    /// antes de dar la frase por terminada.
    pub umbral_fin: f32,
    /// Decodificador Mimi en q8 (experimental): más rápido en algunos procesadores.
    pub mimi_q8: bool,
    /// Hilos de cálculo. 0 elige según los núcleos del móvil.
    pub hilos: usize,
}

/// Lo que se puede cambiar en cada frase sin recargar el modelo.
#[derive(Clone, Debug)]
pub struct Frase {
    /// Nombre de una voz registrada.
    pub voz: String,
    /// Idioma con el que se normaliza el texto (es, en, fr, de, pt) o `none` para leerlo tal cual.
    pub idioma: String,
    /// Temperatura del muestreo. Negativa deja la del motor (0,3). 0 quita la variación.
    pub temperatura: f32,
    /// Semilla del muestreo: la misma semilla da la misma toma de la misma frase.
    pub semilla: u64,
    /// Ritmo natural del modelo: negativo habla más rápido, positivo más despacio. 0 normal.
    pub ritmo: f32,
    /// Guía sin clasificador. 1 o menos la desactiva; más alta, más fiel y el doble de lenta.
    pub guia: f32,
    /// Tokens de texto por trozo. 0 deja el valor del motor.
    pub tokens_trozo: usize,
    /// Compresor y limitador sobre el audio, para que todas las voces suenen parecido de alto.
    pub igualar_volumen: bool,
}

impl Default for Frase {
    fn default() -> Self {
        Self {
            voz: String::new(),
            idioma: "es".into(),
            temperatura: -1.0,
            semilla: 4242424242424242,
            ritmo: 0.0,
            guia: 1.0,
            tokens_trozo: 0,
            igualar_volumen: false,
        }
    }
}

/// Lo que ha tardado una frase.
#[derive(Clone, Copy, Debug, Default)]
pub struct Medida {
    pub muestras: usize,
    /// Desde la llamada hasta el primer audio.
    pub primer_audio_ms: f64,
    pub total_ms: f64,
}

pub struct Motor {
    synth: Synth,
    idiomas: Vec<String>,
    /// Voces conocidas que todavía no se han leído del disco: se cargan la primera vez que
    /// se usan, para no gastar memoria en las que nunca se oyen.
    pendientes: HashMap<String, PathBuf>,
    cargadas: HashSet<String>,
    /// Ritmo aplicado ahora mismo al modelo.
    ritmo: f32,
    tiene_ritmo: bool,
    frecuencia: u32,
}

fn texto<E: std::fmt::Display>(e: E) -> String {
    e.to_string()
}

/// El formato de los pesos según el nombre del archivo.
fn formato(nombre: &str) -> Quant {
    let n = nombre.to_ascii_lowercase();
    if n.ends_with(".safetensors") {
        Quant::F32
    } else if n.contains("q4k") {
        Quant::Q4k
    } else if n.contains("q4") {
        Quant::Q40
    } else if n.contains("q6k") {
        Quant::Q6k
    } else if n.contains("q5k") {
        Quant::Q5k
    } else {
        Quant::Q80
    }
}

/// Tramas (de 80 ms) que el decodificador agrupa como mucho en una llamada. `ptts` agrupa todas
/// las que tenga esperando, hasta 16: es lo más barato, pero en un móvil el audio llega entonces a
/// golpes (80 ms, 80, 240, 320... hasta 1,28 s) con huecos de cientos de milisegundos entre uno y
/// otro, y una voz que suena en tiempo real se queda sin audio al principio de cada frase. Con 2,
/// en un Snapdragon 845 llegan 160 ms cada 60 ms sin perder velocidad (con 1 se pierde un 10 %).
pub const LOTE: usize = 2;

/// Arranca el grupo de hilos de cálculo una sola vez. Las siguientes llamadas no hacen nada:
/// el grupo dura lo que dura el proceso.
pub fn preparar_hilos(hilos: usize) -> usize {
    let nucleos = std::thread::available_parallelism().map_or(1, |n| n.get());
    // Los hilos de cálculo, solo en los núcleos rápidos: los heredan de este hilo al crearse
    let rapidos = crate::nucleos::rapidos();
    crate::nucleos::fijar(&rapidos);
    // Automático: uno por núcleo rápido (de 2 a 4); si no se distinguen, hasta 4
    let ancho = if hilos == 0 {
        if rapidos.is_empty() { nucleos.clamp(1, 4) } else { rapidos.len().clamp(2, 4) }
    } else {
        hilos.clamp(1, nucleos.max(1))
    };
    xn::threadpool::start_pool_with(
        xn::threadpool::PoolConfig { workers: ancho - 1, spin_budget: None },
        |trabajo| {
            std::thread::Builder::new()
                .name("fonaria-calculo".into())
                .spawn(trabajo)
                .expect("no se pudo crear un hilo de cálculo");
        },
    )
}

/// Fija el hilo actual a los núcleos rápidos (se calcula una vez).
fn fijar_a_rapidos() {
    static RAPIDOS: std::sync::OnceLock<Vec<usize>> = std::sync::OnceLock::new();
    crate::nucleos::fijar(RAPIDOS.get_or_init(crate::nucleos::rapidos));
}

impl Motor {
    /// Carga el modelo de `carpeta`: `config.json`, `tokenizer.json` y los pesos.
    pub fn abrir(carpeta: &Path, carga: &Carga) -> Result<Self, String> {
        preparar_hilos(carga.hilos);
        // El decodificador entrega la voz cada una o dos tramas, no a golpes de hasta 1,3 s (ver LOTE)
        ptts::synth::set_decode_batch(LOTE);
        let mut cfg = read_config(carpeta.join("config.json")).map_err(texto)?;
        if carga.pasos > 0 {
            cfg.lsd_decode_steps = carga.pasos.min(8);
        }
        let tiene_ritmo = cfg.conditioners.iter().any(|c| c.name() == RITMO);
        let pesos = if carga.pesos.is_empty() {
            PESOS
                .iter()
                .map(|n| carpeta.join(n))
                .find(|p| p.is_file())
                .ok_or_else(|| format!("no hay pesos en {}", carpeta.display()))?
        } else {
            let p = carpeta.join(&carga.pesos);
            if !p.is_file() {
                return Err(format!("no existe el archivo de pesos {}", p.display()));
            }
            p
        };
        let nombre = pesos.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default();
        let mut constructor = SynthBuilder::new(cfg, &pesos, Normalize::OFF)
            .tokenizer_file(carpeta.join("tokenizer.json"))
            .device(DeviceKind::Cpu)
            .quant(formato(&nombre))
            .quantize_mimi(carga.mimi_q8);
        if carga.umbral_fin.is_finite() {
            constructor = constructor.eos_threshold(carga.umbral_fin);
        }
        let synth = constructor.build().map_err(texto)?;
        Ok(Self {
            idiomas: synth.languages().to_vec(),
            frecuencia: synth.sample_rate(),
            synth,
            pendientes: HashMap::new(),
            cargadas: HashSet::new(),
            ritmo: 0.0,
            tiene_ritmo,
        })
    }

    pub fn frecuencia(&self) -> u32 {
        self.frecuencia
    }

    pub fn idiomas(&self) -> &[String] {
        &self.idiomas
    }

    /// Las voces que el motor conoce, cargadas o no.
    pub fn voces(&self) -> Vec<String> {
        let mut v: Vec<String> =
            self.pendientes.keys().chain(self.cargadas.iter()).cloned().collect();
        v.sort();
        v.dedup();
        v
    }

    /// Da a conocer una voz sin leerla todavía. Si ya estaba cargada con otro archivo, se
    /// volverá a leer la próxima vez que se use.
    pub fn registrar(&mut self, nombre: &str, ruta: &Path) -> Result<(), String> {
        if !ruta.is_file() {
            return Err(format!("no existe el archivo de voz {}", ruta.display()));
        }
        self.cargadas.remove(nombre);
        self.pendientes.insert(nombre.to_string(), ruta.to_path_buf());
        Ok(())
    }

    /// Olvida una voz (por ejemplo, porque se ha borrado). Si estaba en memoria se queda allí
    /// hasta que se recargue el modelo, pero ya no se puede pedir.
    pub fn olvidar(&mut self, nombre: &str) {
        self.pendientes.remove(nombre);
        self.cargadas.remove(nombre);
    }

    /// Lee una voz del disco ahora mismo. Sirve también para comprobar que un archivo
    /// importado es una voz válida para este modelo.
    pub fn cargar(&mut self, nombre: &str, ruta: &Path) -> Result<(), String> {
        self.synth.add_voice_file(nombre, ruta).map_err(texto)?;
        self.pendientes.remove(nombre);
        self.cargadas.insert(nombre.to_string());
        Ok(())
    }

    fn asegurar_voz(&mut self, nombre: &str) -> Result<(), String> {
        if self.cargadas.contains(nombre) {
            return Ok(());
        }
        let ruta = self
            .pendientes
            .get(nombre)
            .cloned()
            .ok_or_else(|| format!("la voz {nombre:?} no está instalada"))?;
        self.cargar(nombre, &ruta)
    }

    fn aplicar_ritmo(&mut self, ritmo: f32) -> Result<(), String> {
        if !self.tiene_ritmo {
            return Ok(());
        }
        let ritmo = if ritmo.is_finite() { ritmo.clamp(-1.0, 1.0) } else { 0.0 };
        if (ritmo - self.ritmo).abs() < 1e-4 {
            return Ok(());
        }
        let mut valores = HashMap::new();
        valores.insert(RITMO.to_string(), format!("{ritmo}"));
        self.synth.set_conditions(valores).map_err(texto)?;
        self.ritmo = ritmo;
        Ok(())
    }

    /// Habla `texto` y va entregando el audio a `salida` según se genera. Si `salida` devuelve
    /// false, la generación se corta ahí y la función vuelve normalmente.
    pub fn hablar(
        &mut self,
        texto_entrada: &str,
        frase: &Frase,
        salida: &mut dyn FnMut(&[f32]) -> bool,
    ) -> Result<Medida, String> {
        let inicio = Instant::now();
        // El hilo que llama (el de síntesis de Android) también calcula y crea el del decodificador
        fijar_a_rapidos();
        if texto_entrada.trim().is_empty() {
            return Ok(Medida::default());
        }
        self.asegurar_voz(&frase.voz)?;
        self.aplicar_ritmo(frase.ritmo)?;
        let normalizar = frase.idioma.parse::<Normalize>().unwrap_or(Normalize::OFF);
        let texto_listo = normalizar.apply(texto_entrada);
        let mut opciones = SpeechOptions {
            voice: Some(frase.voz.clone()),
            seed: Some(frase.semilla),
            ..Default::default()
        };
        if frase.temperatura.is_finite() && frase.temperatura >= 0.0 {
            opciones.temperature = Some(frase.temperatura.min(2.0));
        }
        if frase.guia.is_finite() && frase.guia > 1.001 {
            opciones.cfg_coef = Some(frase.guia.min(4.0));
        }
        if frase.tokens_trozo > 0 {
            opciones.max_tokens_per_chunk = Some(frase.tokens_trozo);
        }
        if frase.igualar_volumen {
            opciones.post_process = Some(cadena_volumen()?);
        }
        let flujo = self.synth.stream_with(&texto_listo, &opciones).map_err(texto)?;
        let mut medida = Medida::default();
        let mut primero = None;
        // Soltar el flujo al salir antes de tiempo detiene la generación.
        for trozo in flujo {
            let pcm = trozo.map_err(texto)?;
            if pcm.is_empty() {
                continue;
            }
            primero.get_or_insert_with(|| inicio.elapsed());
            medida.muestras += pcm.len();
            if !salida(&pcm) {
                break;
            }
        }
        medida.primer_audio_ms = primero.unwrap_or_default().as_secs_f64() * 1e3;
        medida.total_ms = inicio.elapsed().as_secs_f64() * 1e3;
        Ok(medida)
    }
}

/// Compresor suave y limitador: sube lo bajo, frena lo alto y nunca satura.
fn cadena_volumen() -> Result<Vec<ModuleConfig>, String> {
    let json = r#"[
        {"type": "compressor", "threshold_db": -20.0, "ratio": 3.0, "attack_ms": 5.0, "decay_ms": 120.0},
        {"type": "gain", "gain_db": 4.0},
        {"type": "limiter", "lookahead_ms": 0.0, "headroom_db": 1.0, "release_ms": 60.0}
    ]"#;
    serde_json::from_str(json).map_err(texto)
}

#[cfg(test)]
mod pruebas {
    use super::*;

    #[test]
    fn formato_por_nombre() {
        assert_eq!(formato("model.q8.gguf"), Quant::Q80);
        assert_eq!(formato("model.q4k.gguf"), Quant::Q4k);
        assert_eq!(formato("model.safetensors"), Quant::F32);
    }

    #[test]
    fn cadena_de_volumen_valida() {
        assert_eq!(cadena_volumen().unwrap().len(), 3);
    }

    #[test]
    fn sin_modelo_falla_con_mensaje() {
        let e = Motor::abrir(Path::new("no-existe"), &Carga::default()).err().unwrap();
        assert!(e.contains("config.json") || e.contains("no-existe"), "{e}");
    }
}
