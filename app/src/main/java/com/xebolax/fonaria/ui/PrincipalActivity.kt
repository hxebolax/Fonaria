package com.xebolax.fonaria.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xebolax.fonaria.R
import com.xebolax.fonaria.accesibilidad.configurarBarra
import com.xebolax.fonaria.accesibilidad.ponerMenuLateral
import com.xebolax.fonaria.actualizaciones.GestorActualizaciones
import com.xebolax.fonaria.databinding.ActividadPrincipalBinding
import com.xebolax.fonaria.datos.Ajustes
import com.xebolax.fonaria.voces.Almacen
import com.xebolax.fonaria.voces.Catalogo
import com.xebolax.fonaria.voces.idiomaInterfaz

/**
 * La pantalla principal: barra superior, menú lateral con las secciones (Inicio, Voces,
 * Diccionario, Leer texto) y, tras un divisor, las acciones generales (Ajustes, Manual, Buscar
 * actualizaciones, Acerca de). Ajustes solo se abre desde aquí: una sola puerta.
 */
class PrincipalActivity : AppCompatActivity() {

	private lateinit var b: ActividadPrincipalBinding
	private val actualizaciones = GestorActualizaciones(this)
	private var destino = R.id.destino_inicio

	private val volverAInicio = object : OnBackPressedCallback(false) {
		override fun handleOnBackPressed() = mostrar(R.id.destino_inicio)
	}

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		b = ActividadPrincipalBinding.inflate(layoutInflater)
		destino = estado?.getInt("destino") ?: destinoDeIntent(intent) ?: R.id.destino_inicio
		montar()
		onBackPressedDispatcher.addCallback(this, volverAInicio)
		if (estado == null) {
			mostrar(destino, intent.getIntExtra(EXTRA_PESTANA, 0))
			actualizaciones.alIniciar()
			primeraVez()
		} else {
			volverAInicio.isEnabled = destino != R.id.destino_inicio
		}
	}

	/** El menú lateral se vuelve a montar al cambiar de sección para que marque la actual. */
	private fun montar() {
		(b.root.parent as? android.view.ViewGroup)?.removeView(b.root)
		configurarBarra(b.barra, titulo(destino), conArriba = false)
		setContentView(ponerMenuLateral(b.root, b.barra, R.menu.menu_lateral, destino, R.drawable.ic_menu) { id -> elegir(id) })
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		destinoDeIntent(intent)?.let { mostrar(it, intent.getIntExtra(EXTRA_PESTANA, 0)) }
	}

	override fun onSaveInstanceState(estado: Bundle) {
		super.onSaveInstanceState(estado)
		estado.putInt("destino", destino)
	}

	private fun destinoDeIntent(i: Intent?): Int? = when {
		i?.action == TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA -> R.id.destino_voces
		else -> i?.getIntExtra(EXTRA_DESTINO, 0)?.takeIf { it != 0 }
	}

	private fun titulo(id: Int) = getString(
		when (id) {
			R.id.destino_voces -> R.string.voces
			R.id.destino_diccionario -> R.string.diccionario
			R.id.destino_leer -> R.string.leer_texto
			else -> R.string.app_name
		},
	)

	private fun elegir(id: Int) {
		when (id) {
			R.id.accion_ajustes -> startActivity(Intent(this, AjustesActivity::class.java))
			R.id.accion_manual -> startActivity(Intent(this, ManualActivity::class.java))
			R.id.accion_actualizaciones -> actualizaciones.comprobarAMano()
			R.id.accion_acerca -> startActivity(Intent(this, AcercaDeActivity::class.java))
			else -> mostrar(id)
		}
	}

	/** Cambia de sección (y, en Voces, a la pestaña [pestana]). */
	fun mostrar(id: Int, pestana: Int = 0) {
		val cambio = id != destino || supportFragmentManager.findFragmentById(R.id.contenedor) == null
		destino = id
		volverAInicio.isEnabled = id != R.id.destino_inicio
		if (cambio) {
			val f: Fragment = when (id) {
				R.id.destino_voces -> VocesFragment.nuevo(pestana)
				R.id.destino_diccionario -> DiccionarioFragment()
				R.id.destino_leer -> LeerFragment()
				else -> InicioFragment()
			}
			supportFragmentManager.beginTransaction().replace(R.id.contenedor, f).commit()
			montar()
		} else if (id == R.id.destino_voces) {
			(supportFragmentManager.findFragmentById(R.id.contenedor) as? VocesFragment)?.irA(pestana)
		}
		title = titulo(id)
		supportActionBar?.title = title
	}

	/**
	 * La primera vez: qué es Fonaria y cómo activarla. Y si el móvil está en un idioma que no es
	 * el español, aviso de que hay que descargar las voces de ese idioma.
	 */
	private fun primeraVez() {
		val ajustes = Ajustes(this)
		if (!ajustes.bienvenidaVista) {
			MaterialAlertDialogBuilder(this)
				.setTitle(R.string.bienvenida_titulo)
				.setMessage(R.string.bienvenida_texto)
				.setPositiveButton(R.string.elegir_motor_sistema) { _, _ -> abrirAjustesVoz(this) }
				.setNegativeButton(R.string.ahora_no, null)
				.setOnDismissListener {
					ajustes.bienvenidaVista = true
					avisoIdioma()
				}
				.show()
		} else {
			avisoIdioma()
		}
	}

	private fun avisoIdioma() {
		val ajustes = Ajustes(this)
		val idioma = idiomaInterfaz(this).language
		if (idioma == "es" || ajustes.avisoIdiomaVisto == idioma) return
		if (Catalogo.voces(this).none { it.idioma == idioma }) return
		if (Almacen.instaladas(this).any { it.idioma == idioma }) return
		val nombre = Catalogo.nombreIdioma(this, idioma)
		MaterialAlertDialogBuilder(this)
			.setTitle(getString(R.string.aviso_idioma_titulo, nombre))
			.setMessage(getString(R.string.aviso_idioma_texto, nombre))
			.setPositiveButton(R.string.descargar_voces) { _, _ -> mostrar(R.id.destino_voces, VocesFragment.PESTANA_DESCARGAR) }
			.setNegativeButton(R.string.ahora_no, null)
			.setOnDismissListener { ajustes.avisoIdiomaVisto = idioma }
			.show()
	}

	companion object {
		const val EXTRA_DESTINO = "destino"
		const val EXTRA_PESTANA = "pestana"

		/** Abre los ajustes de texto a voz del sistema (para elegir Fonaria como motor). */
		fun abrirAjustesVoz(contexto: Context) {
			val intentos = listOf(
				Intent("com.android.settings.TTS_SETTINGS"),
				Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
				Intent(Settings.ACTION_SETTINGS),
			)
			for (i in intentos) {
				try {
					contexto.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
					return
				} catch (e: ActivityNotFoundException) {
					continue
				}
			}
		}
	}
}
