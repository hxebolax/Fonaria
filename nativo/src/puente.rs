//! Funciones JNI de `com.xebolax.fonaria.motor.Nativo` (un `object` de Kotlin con métodos
//! `@JvmStatic external`).
//!
//! Un motor es un puntero a `Motor` guardado en un `Long` de Java. Java lo usa desde un solo
//! hilo a la vez (lo garantiza `Motor.kt` con un cerrojo) y lo libera con `liberar`.
//! Ningún pánico cruza a Java: se convierte en un error que se lee con `ultimoError`.

use crate::motor::{Carga, Frase, Motor};
use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString, JValue};
use jni::sys::{jboolean, jfloat, jint, jlong, jobjectArray, jstring, JNI_FALSE, JNI_TRUE};
use std::cell::RefCell;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::path::Path;

thread_local! {
    /// El último error de este hilo: un `abrir` fallido o cualquier llamada que devolvió false.
    static ERROR: RefCell<String> = const { RefCell::new(String::new()) };
}

fn poner_error(e: impl Into<String>) {
    let e = e.into();
    ERROR.with(|x| *x.borrow_mut() = e);
}

/// Ejecuta `f` convirtiendo un pánico en un error: un pánico que llegase a Java cerraría la app.
fn protegido<T>(f: impl FnOnce() -> Result<T, String>) -> Result<T, String> {
    catch_unwind(AssertUnwindSafe(f)).unwrap_or_else(|p| {
        let m = p
            .downcast_ref::<&str>()
            .map(|s| s.to_string())
            .or_else(|| p.downcast_ref::<String>().cloned())
            .unwrap_or_else(|| "desconocido".into());
        Err(format!("error interno del motor: {m}"))
    })
}

fn cadena(env: &mut JNIEnv, s: &JString) -> String {
    if s.is_null() {
        return String::new();
    }
    env.get_string(s).map(|s| s.into()).unwrap_or_default()
}

fn motor<'a>(h: jlong) -> Option<&'a mut Motor> {
    if h == 0 { None } else { Some(unsafe { &mut *(h as *mut Motor) }) }
}

fn booleano(r: Result<(), String>) -> jboolean {
    match r {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            poner_error(e);
            JNI_FALSE
        }
    }
}

fn lista(env: &mut JNIEnv, valores: &[String]) -> jobjectArray {
    let Ok(clase) = env.find_class("java/lang/String") else { return std::ptr::null_mut() };
    let Ok(arr) = env.new_object_array(valores.len() as i32, clase, JObject::null()) else {
        return std::ptr::null_mut();
    };
    for (i, v) in valores.iter().enumerate() {
        if let Ok(s) = env.new_string(v) {
            let _ = env.set_object_array_element(&arr, i as i32, &s);
            let _ = env.delete_local_ref(s);
        }
    }
    arr.into_raw()
}

/// `abrir(carpeta, pesos, pasos, umbralFin, mimiQ8, hilos): Long` — 0 si falla.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_xebolax_fonaria_motor_Nativo_abrir(
    mut env: JNIEnv,
    _clase: JClass,
    carpeta: JString,
    pesos: JString,
    pasos: jint,
    umbral_fin: jfloat,
    mimi_q8: jboolean,
    hilos: jint,
) -> jlong {
    let carpeta = cadena(&mut env, &carpeta);
    let carga = Carga {
        pesos: cadena(&mut env, &pesos),
        pasos: pasos.max(0) as usize,
        umbral_fin,
        mimi_q8: mimi_q8 != JNI_FALSE,
        hilos: hilos.max(0) as usize,
    };
    match protegido(|| Motor::abrir(Path::new(&carpeta), &carga)) {
        Ok(m) => Box::into_raw(Box::new(m)) as jlong,
        Err(e) => {
            poner_error(e);
            0
        }
    }
}

/// `liberar(motor)`: después no se puede volver a usar.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_xebolax_fonaria_motor_Nativo_liberar(
    _env: JNIEnv,
    _clase: JClass,
    h: jlong,
) {
    if h != 0 {
        let _ = protegido(|| {
            drop(unsafe { Box::from_raw(h as *mut Motor) });
            Ok(())
        });
    }
}

/// `ultimoError(): String` — el último error de este hilo.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_xebolax_fonaria_motor_Nativo_ultimoError(
    env: JNIEnv,
    _clase: JClass,
) -> jstring {
    let e = ERROR.with(|x| x.borrow().clone());
    env.new_string(e).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

/// `version(): String`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_xebolax_fonaria_motor_Nativo_version(
    env: JNIEnv,
    _clase: JClass,
) -> jstring {
    let v = format!("{} (ptts 0.7.1, {})", crate::VERSION, std::env::consts::ARCH);
    env.new_string(v).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

/// `frecuencia(motor): Int` — muestras por segundo del audio (24000).
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_xebolax_fonaria_motor_Nativo_frecuencia(
    _env: JNIEnv,
    _clase: JClass,
    h: jlong,
) -> jint {
    motor(h).map_or(24000, |m| m.frecuencia() as jint)
}

/// `idiomas(motor): Array<String>` — los idiomas para los que se entrenó el modelo.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_xebolax_fonaria_motor_Nativo_idiomas(
    mut env: JNIEnv,
    _clase: JClass,
    h: jlong,
) -> jobjectArray {
    let v = motor(h).map(|m| m.idiomas().to_vec()).unwrap_or_default();
    lista(&mut env, &v)
}

/// `voces(motor): Array<String>` — las voces registradas.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_xebolax_fonaria_motor_Nativo_voces(
    mut env: JNIEnv,
    _clase: JClass,
    h: jlong,
) -> jobjectArray {
    let v = motor(h).map(|m| m.voces()).unwrap_or_default();
    lista(&mut env, &v)
}

/// `registrar(motor, nombre, ruta): Boolean` — la voz se lee del disco la primera vez que se usa.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_xebolax_fonaria_motor_Nativo_registrar(
    mut env: JNIEnv,
    _clase: JClass,
    h: jlong,
    nombre: JString,
    ruta: JString,
) -> jboolean {
    let nombre = cadena(&mut env, &nombre);
    let ruta = cadena(&mut env, &ruta);
    let Some(m) = motor(h) else { return booleano(Err("motor cerrado".into())) };
    booleano(protegido(|| m.registrar(&nombre, Path::new(&ruta))))
}

/// `cargar(motor, nombre, ruta): Boolean` — lee la voz ahora; sirve para validar una importación.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_xebolax_fonaria_motor_Nativo_cargar(
    mut env: JNIEnv,
    _clase: JClass,
    h: jlong,
    nombre: JString,
    ruta: JString,
) -> jboolean {
    let nombre = cadena(&mut env, &nombre);
    let ruta = cadena(&mut env, &ruta);
    let Some(m) = motor(h) else { return booleano(Err("motor cerrado".into())) };
    booleano(protegido(|| m.cargar(&nombre, Path::new(&ruta))))
}

/// `olvidar(motor, nombre)`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_xebolax_fonaria_motor_Nativo_olvidar(
    mut env: JNIEnv,
    _clase: JClass,
    h: jlong,
    nombre: JString,
) {
    let nombre = cadena(&mut env, &nombre);
    if let Some(m) = motor(h) {
        m.olvidar(&nombre);
    }
}

/// `hablar(motor, texto, voz, idioma, temperatura, semilla, ritmo, guia, tokensTrozo,
/// igualarVolumen, receptor): Boolean`.
///
/// `receptor` tiene el método `audio(FloatArray, Int): Boolean`; si devuelve false, se deja de
/// generar. Si lanza una excepción, se para y la excepción sigue su curso en Java.
#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_xebolax_fonaria_motor_Nativo_hablar(
    mut env: JNIEnv,
    _clase: JClass,
    h: jlong,
    texto: JString,
    voz: JString,
    idioma: JString,
    temperatura: jfloat,
    semilla: jlong,
    ritmo: jfloat,
    guia: jfloat,
    tokens_trozo: jint,
    igualar_volumen: jboolean,
    receptor: JObject,
) -> jboolean {
    let texto = cadena(&mut env, &texto);
    let frase = Frase {
        voz: cadena(&mut env, &voz),
        idioma: cadena(&mut env, &idioma),
        temperatura,
        semilla: semilla as u64,
        ritmo,
        guia,
        tokens_trozo: tokens_trozo.max(0) as usize,
        igualar_volumen: igualar_volumen != JNI_FALSE,
    };
    let Some(m) = motor(h) else { return booleano(Err("motor cerrado".into())) };
    let mut fallo_java = false;
    let resultado = protegido(|| {
        let mut salida = |pcm: &[f32]| -> bool {
            if fallo_java {
                return false;
            }
            let seguir = (|| -> jni::errors::Result<bool> {
                let arr = env.new_float_array(pcm.len() as i32)?;
                env.set_float_array_region(&arr, 0, pcm)?;
                let r = env.call_method(
                    &receptor,
                    "audio",
                    "([FI)Z",
                    &[JValue::Object(&arr), JValue::Int(pcm.len() as i32)],
                )?;
                env.delete_local_ref(arr)?;
                r.z()
            })();
            match seguir {
                Ok(s) => s,
                Err(_) => {
                    // Excepción de Java pendiente: se deja para que la vea quien llamó.
                    fallo_java = true;
                    false
                }
            }
        };
        m.hablar(&texto, &frase, &mut salida).map(|_| ())
    });
    if fallo_java {
        poner_error("el receptor de audio falló");
        return JNI_FALSE;
    }
    booleano(resultado)
}
