//! Núcleos rápidos del procesador.
//!
//! Casi todos los móviles mezclan núcleos rápidos y lentos (big.LITTLE). El motor reparte cada
//! paso del cálculo entre varios hilos y espera a que acaben todos: si Android pone uno en un
//! núcleo lento, todos esperan por él y la voz se genera a la mitad de velocidad o se
//! entrecorta. Por eso los hilos de cálculo se fijan a los núcleos rápidos.
//!
//! Los rápidos son los que tienen la frecuencia máxima más alta (con un margen del 25 %, para
//! contar juntos los «prime» y los grandes). Si no se puede leer, no se fija nada.

use std::fs;

/// Los números de los núcleos rápidos, o vacío si todos son iguales o no se sabe.
pub fn rapidos() -> Vec<usize> {
	let total = std::thread::available_parallelism().map_or(1, |n| n.get()).max(8);
	let mut frecuencias = Vec::new();
	for cpu in 0..total.min(64) {
		let ruta = format!("/sys/devices/system/cpu/cpu{cpu}/cpufreq/cpuinfo_max_freq");
		if let Ok(texto) = fs::read_to_string(&ruta)
			&& let Ok(f) = texto.trim().parse::<u64>()
		{
			frecuencias.push((cpu, f));
		}
	}
	let Some(maxima) = frecuencias.iter().map(|(_, f)| *f).max() else { return Vec::new() };
	let rapidos: Vec<usize> = frecuencias.iter().filter(|(_, f)| *f * 4 >= maxima * 3).map(|(c, _)| *c).collect();
	// Todos iguales: no hay nada que elegir
	if rapidos.len() == frecuencias.len() { Vec::new() } else { rapidos }
}

/// Fija el hilo actual a [nucleos]. Los hilos que cree después lo heredan. No hace nada si la
/// lista está vacía o el sistema no lo permite.
#[cfg(any(target_os = "android", target_os = "linux"))]
pub fn fijar(nucleos: &[usize]) -> bool {
	if nucleos.is_empty() {
		return false;
	}
	// SAFETY: cpu_set_t es una máscara de bits; se rellena con CPU_SET y se pasa su tamaño.
	unsafe {
		let mut conjunto: libc::cpu_set_t = std::mem::zeroed();
		for &n in nucleos {
			libc::CPU_SET(n, &mut conjunto);
		}
		libc::sched_setaffinity(0, std::mem::size_of::<libc::cpu_set_t>(), &conjunto) == 0
	}
}

#[cfg(not(any(target_os = "android", target_os = "linux")))]
pub fn fijar(_nucleos: &[usize]) -> bool {
	false
}
