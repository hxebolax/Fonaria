package com.xebolax.fonaria

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Habla de verdad por el altavoz, como TalkBack: frases cortas seguidas, interrupciones y un texto
 * largo, a la velocidad que se pase (por defecto 412 %, la de un usuario rápido de TalkBack).
 * No comprueba nada por sí sola: sirve para contar después los cortes en el registro
 *
 *     adb logcat -d | grep -E "underrun|Fonaria.Servicio"
 *
 * Argumentos: -e velocidad 412 -e caso cortas|largo|interrumpe
 */
@RunWith(AndroidJUnit4::class)
class ReproduccionTest {
	private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
	private val argumentos = InstrumentationRegistry.getArguments()
	private lateinit var tts: TextToSpeech
	@Volatile
	private var fin = CountDownLatch(1)

	@Before
	fun conectar() {
		val listo = CountDownLatch(1)
		var estado = TextToSpeech.ERROR
		InstrumentationRegistry.getInstrumentation().runOnMainSync {
			tts = TextToSpeech(contexto, { estado = it; listo.countDown() }, contexto.packageName)
		}
		assertTrue("El motor no respondió", listo.await(30, TimeUnit.SECONDS))
		assertEquals(TextToSpeech.SUCCESS, estado)
		tts.language = Locale("es", "ES")
		tts.setSpeechRate((argumentos.getString("velocidad") ?: "412").toFloat() / 100f)
		tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
			override fun onStart(id: String?) = Unit
			override fun onDone(id: String?) = fin.countDown()
			@Deprecated("API 21") override fun onError(id: String?) = fin.countDown()
			override fun onError(id: String?, codigo: Int) = fin.countDown()
			override fun onStop(id: String?, interrumpida: Boolean) = fin.countDown()
		})
	}

	@After
	fun cerrar() = tts.shutdown()

	private fun decir(texto: String, esperar: Boolean = true) {
		fin = CountDownLatch(1)
		val mio = fin
		tts.speak(texto, TextToSpeech.QUEUE_FLUSH, Bundle(), "p${System.nanoTime()}")
		if (esperar) mio.await(120, TimeUnit.SECONDS)
	}

	@Test
	fun frasesCortas() {
		for (ronda in 1..2) for (f in CORTAS) {
			decir(f)
			Thread.sleep(250)
		}
		Log.i("ReproduccionTest", "fin frasesCortas")
	}

	@Test
	fun textoLargo() {
		decir(LARGO)
		Log.i("ReproduccionTest", "fin textoLargo")
	}

	@Test
	fun interrupciones() {
		// Como al pasar el dedo deprisa: cada frase corta a la anterior al poco de empezar
		for (ronda in 1..2) for ((i, f) in CORTAS.withIndex()) {
			decir(f, esperar = false)
			Thread.sleep(if (i % 3 == 2) 1800L else 350L)
		}
		decir("Fin de la prueba de interrupciones.")
		Log.i("ReproduccionTest", "fin interrupciones")
	}

	/** Lo mismo que se oiría, pero a archivos WAV (para analizar chasquidos en el ordenador). */
	@Test
	fun aArchivos() {
		val carpeta = java.io.File(contexto.getExternalFilesDir(null), "pruebas").apply { mkdirs() }
		val v = argumentos.getString("velocidad") ?: "412"
		for ((i, t) in (CORTAS + LARGO).withIndex()) {
			fin = CountDownLatch(1)
			val mio = fin
			tts.synthesizeToFile(t, Bundle(), java.io.File(carpeta, "v${v}_$i.wav"), "a$i")
			mio.await(180, TimeUnit.SECONDS)
		}
	}

	companion object {
		val CORTAS = listOf(
			"Ajustes, botón",
			"Mensajes, tres sin leer",
			"Llamada perdida de María, hace cinco minutos",
			"Casilla de verificación, no marcada, recordar la contraseña",
			"Wifi, activado, conectado a la red de casa",
			"Batería al ochenta y dos por ciento, cargando",
			"Lista, mostrando elementos del uno al doce de cuarenta",
			"Escribir un mensaje, cuadro de edición",
			"Enviar",
			"Lunes dieciséis de noviembre, nueve y cuarto de la mañana",
			"Atrás, botón",
			"El tiempo en Madrid: parcialmente nublado, catorce grados, con probabilidad de lluvia por la tarde",
		)
		const val LARGO = "La tarde caía sobre el pueblo cuando los niños volvieron de la escuela. Nadie hablaba, pero todos sabían que algo había cambiado para siempre. El viejo molino seguía girando junto al río, como cada día desde hacía cien años, y el panadero, que nunca cerraba antes de las ocho, había bajado ya la persiana. En la plaza, las palomas se disputaban las migas que alguien había dejado junto a la fuente. Doña Carmen miraba desde su balcón, con las manos apoyadas en la barandilla de hierro, y contaba en silencio a los que pasaban. Cuando llegó el último, respiró hondo, entró en casa y cerró la ventana. Aquella noche llovió sin parar hasta el amanecer, y por la mañana el río bajaba tan crecido que el puente de piedra apenas asomaba sobre el agua. Los vecinos se reunieron en la iglesia para decidir qué hacer, y fue entonces cuando el maestro, que llevaba poco tiempo en el pueblo, propuso algo que nadie esperaba."
	}
}
