//! Fonaria nativo: el motor de voz Phonon para Android.
//!
//! - `motor.rs`: el motor en Rust puro (cargar el modelo, registrar voces, hablar). Se puede
//!   probar en el ordenador con `cargo test` y `cargo run --release --example decir`.
//! - `puente.rs`: las funciones JNI que llama `com.xebolax.fonaria.motor.Nativo`.
//!
//! Copyright (C) 2026 Fonaria. Licencia GPL-3.0-or-later.

pub mod motor;
pub mod nucleos;
mod puente;

/// Versión de esta biblioteca, para el diagnóstico de la app.
pub const VERSION: &str = env!("CARGO_PKG_VERSION");
