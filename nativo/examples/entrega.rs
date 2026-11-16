//! Mide cómo llega el audio en el tiempo (no solo cuánto tarda), que es lo que decide si una voz
//! en tiempo real se entrecorta:
//!
//!     entrega <carpeta modelo> <voz.safetensors> <ritmo> <velocidad> <lote> "texto" ["texto"...]
//!
//! - velocidad: a cuántas veces el tiempo real se consume el audio del modelo (lo que acelera Sonic).
//! - lote: tramas que el decodificador puede agrupar por llamada (16 es lo de ptts sin tocar).
//!
//! Simula la pista de audio de Android: empieza a sonar con 170 ms en el búfer y cuenta un hueco
//! cada vez que se queda sin audio. La primera frase solo calienta.
use fonaria::motor::{Carga, Frase, Motor};
use std::path::Path;
use std::time::Instant;

fn main() {
	let a: Vec<String> = std::env::args().collect();
	if a.len() < 7 {
		eprintln!("uso: entrega <modelo> <voz> <ritmo> <velocidad> <lote> <texto>...");
		std::process::exit(2);
	}
	let ritmo: f32 = a[3].parse().expect("ritmo");
	let velocidad: f64 = a[4].parse().expect("velocidad");
	let lote: usize = a[5].parse().expect("lote");
	ptts::synth::set_decode_batch(lote);
	let hilos: usize = std::env::var("HILOS").ok().and_then(|v| v.parse().ok()).unwrap_or(0);
	let carga = Carga { umbral_fin: f32::NAN, hilos, ..Default::default() };
	let mut m = Motor::abrir(Path::new(&a[1]), &carga).expect("abrir");
	m.registrar("voz", Path::new(&a[2])).expect("registrar");
	let frase = Frase { voz: "voz".into(), idioma: "es".into(), ritmo, ..Default::default() };
	let f = m.frecuencia() as f64;
	m.hablar("Hola, esto es para calentar el motor.", &frase, &mut |_| true).expect("calentar");
	let (mut suma_primero, mut suma_huecos, mut suma_ms, mut suma_g, mut n) = (0.0, 0, 0.0, 0.0, 0);
	for texto in &a[6..] {
		// PAUSA: silencio entre frases (ms). CALENTAR: ms de trabajo inútil en 4 hilos justo antes,
		// para ver cuánto del arranque lento es la frecuencia baja del procesador en reposo.
		let pausa: u64 = std::env::var("PAUSA").ok().and_then(|v| v.parse().ok()).unwrap_or(250);
		let calentar: u64 = std::env::var("CALENTAR").ok().and_then(|v| v.parse().ok()).unwrap_or(0);
		std::thread::sleep(std::time::Duration::from_millis(pausa));
		if calentar > 0 {
			let hilos: Vec<_> = (0..4).map(|_| std::thread::spawn(move || {
				let t = Instant::now();
				let mut x = 1u64;
				while t.elapsed().as_millis() < calentar as u128 { x = x.wrapping_mul(6364136223846793005).wrapping_add(1); }
				std::hint::black_box(x);
			})).collect();
			for h in hilos { let _ = h.join(); }
		}
		let t0 = Instant::now();
		// (instante en ms, ms de audio del modelo) de cada entrega, ya sin el silencio inicial
		let mut entregas: Vec<(f64, f64)> = Vec::new();
		let mut empezado = false;
		m.hablar(texto, &frase, &mut |p| {
			let t = t0.elapsed().as_secs_f64() * 1e3;
			let mut desde = 0;
			if !empezado {
				while desde < p.len() && p[desde].abs() < 0.008 { desde += 1; }
				if desde == p.len() { return true; }
				empezado = true;
			}
			entregas.push((t, (p.len() - desde) as f64 * 1e3 / f));
			true
		}).expect("hablar");
		if entregas.is_empty() { continue; }
		// Simulación: se consume a `velocidad`; arranca con 170 ms de salida en el búfer
		let (mut inicio, mut entregado, mut perdido, mut huecos, mut huecos_ms) = (None::<f64>, 0.0, 0.0, 0, 0.0);
		for (t, ms) in &entregas {
			let antes: f64 = entregado;
			entregado += ms / velocidad;
			match inicio {
				None => if entregado >= 170.0 { inicio = Some(*t) },
				Some(i) => {
					let falta = (t - i) - perdido - antes;
					if falta > 2.0 { huecos += 1; huecos_ms += falta; perdido += falta; }
				}
			}
		}
		let total: f64 = entregas.iter().map(|e| e.1).sum();
		let dur = entregas.last().unwrap().0 - entregas[0].0;
		let g = if dur > 0.0 { (total - entregas[0].1) / dur } else { 0.0 };
		let suena = inicio.unwrap_or(entregas.last().unwrap().0);
		let traza: Vec<String> = entregas.iter().take(8).map(|(t, ms)| format!("{t:.0}:{ms:.0}")).collect();
		println!("{:3} car.: primera voz {:4.0} ms, suena a {:4.0} ms, huecos {} ({:.0} ms), motor x{:.2} | {}",
			texto.chars().count(), entregas[0].0, suena, huecos, huecos_ms, g, traza.join(" "));
		suma_primero += suena; suma_huecos += huecos; suma_ms += huecos_ms; suma_g += g; n += 1;
	}
	println!("LOTE {lote} velocidad {velocidad}: suena a {:.0} ms de media, {} huecos ({:.0} ms), motor x{:.2}",
		suma_primero / n as f64, suma_huecos, suma_ms, suma_g / n as f64);
}
