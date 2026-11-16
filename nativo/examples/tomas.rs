//! Dice el mismo texto con varias semillas y muestra cuánto dura cada toma: las tomas que salen
//! mal (palabras comidas o repetidas) suelen durar bastante menos o más que las demás.
//!
//!     tomas <carpeta modelo> <voz.safetensors> <ritmo> <temperatura> <tomas> "texto"...
use fonaria::motor::{Carga, Frase, Motor};
use std::path::Path;

fn main() {
	let a: Vec<String> = std::env::args().collect();
	let ritmo: f32 = a[3].parse().expect("ritmo");
	let temperatura: f32 = a[4].parse().expect("temperatura");
	let tomas: u64 = a[5].parse().expect("tomas");
	let mut m = Motor::abrir(Path::new(&a[1]), &Carga { umbral_fin: f32::NAN, ..Default::default() }).expect("abrir");
	m.registrar("voz", Path::new(&a[2])).expect("registrar");
	for texto in &a[6..] {
		let mut duraciones = Vec::new();
		for semilla in 0..tomas {
			let frase = Frase { voz: "voz".into(), idioma: "es".into(), ritmo, temperatura, semilla: if semilla == 0 { 4242424242424242 } else { semilla * 7919 }, ..Default::default() };
			let mut n = 0usize;
			m.hablar(texto, &frase, &mut |p| { n += p.iter().filter(|v| v.abs() > 0.02).count(); true }).expect("hablar");
			duraciones.push(n as f64 * 1e3 / m.frecuencia() as f64);
		}
		let mut o = duraciones.clone();
		o.sort_by(|x, y| x.partial_cmp(y).unwrap());
		let mediana = o[o.len() / 2];
		let raras = duraciones.iter().filter(|d| (**d - mediana).abs() > mediana * 0.25).count();
		println!("{texto:?} ritmo {ritmo} temp {temperatura}: mediana {mediana:.0} ms de voz, raras {raras}/{tomas} | {}",
			duraciones.iter().map(|d| format!("{d:.0}")).collect::<Vec<_>>().join(" "));
	}
}
