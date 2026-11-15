package com.xebolax.fonaria.motor

import android.os.Build
import android.util.Log
import java.io.File

/**
 * Las funciones de la biblioteca nativa (`nativo/src/puente.rs`, en Rust).
 *
 * En los móviles de 64 bits hay dos bibliotecas: `libfonaria_v82.so`, compilada para
 * procesadores ARMv8.2 con instrucciones de producto escalar y coma flotante de 16 bits
 * (casi todos desde 2019, bastante más rápida), y `libfonaria.so`, que funciona en cualquiera.
 * Usar la optimizada en un procesador que no tiene esas instrucciones cerraría la app, así
 * que se mira antes en /proc/cpuinfo.
 *
 * Ninguna función es segura entre hilos: [Motor] las llama siempre con su cerrojo.
 */
object Nativo {
	private const val ETIQUETA = "Fonaria.Nativo"

	/** La biblioteca que se ha cargado («fonaria_v82» o «fonaria»), o null si no se pudo. */
	@Volatile
	var cargada: String? = null
		private set

	/** Por qué no se pudo cargar, para el diagnóstico. */
	@Volatile
	var errorCarga: String? = null
		private set

	@Synchronized
	fun cargar(): Boolean {
		if (cargada != null) return true
		val candidatas = buildList {
			if (admiteArmV82()) add("fonaria_v82")
			add("fonaria")
		}
		for (nombre in candidatas) {
			try {
				System.loadLibrary(nombre)
				cargada = nombre
				errorCarga = null
				Log.i(ETIQUETA, "Biblioteca nativa $nombre (${version()})")
				return true
			} catch (e: UnsatisfiedLinkError) {
				Log.w(ETIQUETA, "No se pudo cargar $nombre", e)
				errorCarga = e.message
			}
		}
		return false
	}

	/** ¿Tiene el procesador las instrucciones ARMv8.2 (asimddp y fphp) que usa la biblioteca optimizada? */
	fun admiteArmV82(): Boolean {
		if (!Build.SUPPORTED_ABIS.firstOrNull().orEmpty().startsWith("arm64")) return false
		return try {
			val cpu = File("/proc/cpuinfo").readText()
			val linea = cpu.lineSequence().firstOrNull { it.startsWith("Features") }.orEmpty()
			val rasgos = linea.substringAfter(':').trim().split(Regex("\\s+")).toSet()
			"asimddp" in rasgos && ("fphp" in rasgos || "asimdhp" in rasgos)
		} catch (e: Exception) {
			Log.w(ETIQUETA, "No se pudo leer /proc/cpuinfo; se usa la biblioteca genérica", e)
			false
		}
	}

	/** Recibe el audio según se genera: 24 kHz, mono, flotantes entre -1 y 1. False para parar. */
	fun interface Receptor {
		fun audio(muestras: FloatArray, n: Int): Boolean
	}

	@JvmStatic external fun abrir(carpeta: String, pesos: String, pasos: Int, umbralFin: Float, mimiQ8: Boolean, hilos: Int): Long
	@JvmStatic external fun liberar(motor: Long)
	@JvmStatic external fun ultimoError(): String?
	@JvmStatic external fun version(): String
	@JvmStatic external fun frecuencia(motor: Long): Int
	@JvmStatic external fun idiomas(motor: Long): Array<String>
	@JvmStatic external fun voces(motor: Long): Array<String>
	@JvmStatic external fun registrar(motor: Long, nombre: String, ruta: String): Boolean
	@JvmStatic external fun cargar(motor: Long, nombre: String, ruta: String): Boolean
	@JvmStatic external fun olvidar(motor: Long, nombre: String)

	@JvmStatic external fun hablar(
		motor: Long,
		texto: String,
		voz: String,
		idioma: String,
		temperatura: Float,
		semilla: Long,
		ritmo: Float,
		guia: Float,
		tokensTrozo: Int,
		igualarVolumen: Boolean,
		receptor: Receptor,
	): Boolean
}
