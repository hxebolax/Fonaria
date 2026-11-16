//! Muestra cómo deja el motor un texto antes de decirlo: `normaliza <idioma> "texto"...`
use ptts::preprocess::Normalize;

fn main() {
	let a: Vec<String> = std::env::args().collect();
	let n = a[1].parse::<Normalize>().expect("idioma");
	for t in &a[2..] {
		println!("{t} -> {}", n.apply(t));
	}
}
