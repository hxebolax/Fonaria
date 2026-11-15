//! Prueba en el ordenador: `cargo run --release --example decir -- <carpeta modelo> <voz.safetensors> "texto" salida.wav [ritmo]`
use fonaria::motor::{Carga, Frase, Motor};
use std::path::Path;

fn main() {
    let a: Vec<String> = std::env::args().collect();
    if a.len() < 5 {
        eprintln!("uso: decir <carpeta modelo> <voz.safetensors> <texto> <salida.wav> [ritmo] [pesos]");
        std::process::exit(2);
    }
    let carga = Carga { pesos: a.get(6).cloned().unwrap_or_default(), umbral_fin: f32::NAN, ..Default::default() };
    let t = std::time::Instant::now();
    let mut m = Motor::abrir(Path::new(&a[1]), &carga).expect("abrir");
    println!("carga: {:.0} ms, idiomas {:?}", t.elapsed().as_secs_f64() * 1e3, m.idiomas());
    m.registrar("voz", Path::new(&a[2])).expect("registrar");
    let ritmo: f32 = a.get(5).and_then(|r| r.parse().ok()).unwrap_or(0.0);
    let frase = Frase { voz: "voz".into(), idioma: "es".into(), ritmo, ..Default::default() };
    let mut pcm = Vec::new();
    let r = m.hablar(&a[3], &frase, &mut |p| { pcm.extend_from_slice(p); true }).expect("hablar");
    let seg = pcm.len() as f64 / m.frecuencia() as f64;
    println!("audio {:.2} s; primer audio {:.0} ms; total {:.0} ms; x{:.1} tiempo real", seg, r.primer_audio_ms, r.total_ms, seg * 1e3 / r.total_ms);
    // WAV de 16 bits
    let mut w = Vec::new();
    let n = pcm.len() as u32 * 2;
    w.extend_from_slice(b"RIFF"); w.extend_from_slice(&(36 + n).to_le_bytes()); w.extend_from_slice(b"WAVEfmt ");
    w.extend_from_slice(&16u32.to_le_bytes()); w.extend_from_slice(&1u16.to_le_bytes()); w.extend_from_slice(&1u16.to_le_bytes());
    w.extend_from_slice(&m.frecuencia().to_le_bytes()); w.extend_from_slice(&(m.frecuencia() * 2).to_le_bytes());
    w.extend_from_slice(&2u16.to_le_bytes()); w.extend_from_slice(&16u16.to_le_bytes()); w.extend_from_slice(b"data"); w.extend_from_slice(&n.to_le_bytes());
    for s in pcm { w.extend_from_slice(&((s.clamp(-1.0, 1.0) * 32767.0) as i16).to_le_bytes()); }
    std::fs::write(&a[4], w).expect("escribir wav");
}
