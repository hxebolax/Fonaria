package com.xebolax.fonaria

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xebolax.fonaria.motor.ServicioVoz
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Prueba el motor como lo usaría cualquier app: TextToSpeech con el paquete de Fonaria.
 * Escribe los WAV en la carpeta externa de la app (Android/data/<paquete>/files/pruebas) para
 * poder escucharlos o transcribirlos en el ordenador:
 *
 *     adb pull /sdcard/Android/data/com.xebolax.fonaria.pruebas/files/pruebas
 */
@RunWith(AndroidJUnit4::class)
class MotorTest {
	private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
	private lateinit var tts: TextToSpeech
	private val carpeta by lazy { File(contexto.getExternalFilesDir(null), "pruebas").apply { mkdirs() } }

	@Before
	fun conectar() {
		val listo = CountDownLatch(1)
		var estado = TextToSpeech.ERROR
		InstrumentationRegistry.getInstrumentation().runOnMainSync {
			tts = TextToSpeech(contexto, { estado = it; listo.countDown() }, contexto.packageName)
		}
		assertTrue("El motor no respondió", listo.await(30, TimeUnit.SECONDS))
		assertEquals(TextToSpeech.SUCCESS, estado)
	}

	@After
	fun cerrar() = tts.shutdown()

	/** Genera [texto] en un WAV y devuelve los segundos de audio. */
	private fun generar(nombre: String, texto: String, locale: Locale, voz: String? = null): Double {
		val archivo = File(carpeta, "$nombre.wav").apply { delete() }
		val fin = CountDownLatch(1)
		var bien = false
		tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
			override fun onStart(id: String?) = Unit
			override fun onDone(id: String?) { bien = true; fin.countDown() }
			@Deprecated("API 21") override fun onError(id: String?) = fin.countDown()
			override fun onError(id: String?, codigo: Int) = fin.countDown()
		})
		tts.language = locale
		val params = Bundle().apply { if (voz != null) putString(ServicioVoz.PARAM_VOZ, voz) }
		val inicio = System.currentTimeMillis()
		assertEquals(TextToSpeech.SUCCESS, tts.synthesizeToFile(texto, params, archivo, nombre))
		assertTrue("Tardó demasiado: $nombre", fin.await(180, TimeUnit.SECONDS))
		assertTrue("Falló la síntesis: $nombre", bien)
		val segundos = (archivo.length() - 44) / 2.0 / 24000.0
		Log.i("MotorTest", "$nombre: %.2f s de audio en %d ms".format(segundos, System.currentTimeMillis() - inicio))
		return segundos
	}

	@Test
	fun hablaEspanol() {
		assertTrue(generar("es_frase", "Hola, soy Fonaria. Hoy es 15 de noviembre de 2026 y son las 21:30.", Locale("es", "ES")) > 2.0)
	}

	@Test
	fun hablaConVozMexicana() {
		// Leonel, español de México
		assertTrue(generar("es_mx", "Buenos días. ¿Qué tal estás? Te leo las noticias.", Locale("es", "MX"), "BkuASiOJRrJEB5qA") > 1.5)
	}

	@Test
	fun inglesConVozDeReserva() {
		assertEquals(TextToSpeech.LANG_AVAILABLE, tts.isLanguageAvailable(Locale.US))
		assertTrue(generar("en_reserva", "This is English read with a Spanish voice.", Locale.US) > 1.0)
	}

	@Test
	fun deletreaUnaLetra() {
		assertTrue(generar("letra_b", "b", Locale("es", "ES")) > 0.15)
	}

	@Test
	fun textoLargoEnVariasFrases() {
		val texto = "Primera frase del texto. Segunda frase, con una coma. ¿Tercera pregunta? ¡Cuarta exclamación!\nY un párrafo nuevo con un emoji 😀."
		assertTrue(generar("parrafos", texto, Locale("es", "ES")) > 5.0)
	}

	@Test
	fun idiomaNoSoportado() {
		assertEquals(TextToSpeech.LANG_NOT_SUPPORTED, tts.isLanguageAvailable(Locale("ja", "JP")))
	}
}
