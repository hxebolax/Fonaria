package com.xebolax.fonaria.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import com.xebolax.fonaria.R
import com.xebolax.fonaria.accesibilidad.configurarBarra
import com.xebolax.fonaria.actualizaciones.GestorActualizaciones
import com.xebolax.fonaria.databinding.ActividadSimpleBinding
import com.xebolax.fonaria.datos.Ajustes

/**
 * Ajustes, con una pestaña por tema, abajo y siempre en este orden: General, Voces, Sonido,
 * Texto, Motor y Actualizaciones (de lo más general a lo más técnico). Se abre solo desde el
 * menú lateral o desde la rueda de Fonaria en los ajustes de texto a voz de Android.
 */
class AjustesActivity : AppCompatActivity() {
	private lateinit var b: ActividadSimpleBinding
	/** Para «Buscar actualizaciones» de la pestaña Actualizaciones. */
	val actualizaciones = GestorActualizaciones(this)

	private var pestanas: Pestanas? = null

	/** Las seis pestañas, en su orden: clave, nombre e icono. */
	private val lista get() = listOf(
		Pestanas.Pestana(GENERAL, getString(R.string.pestana_general), R.drawable.ic_ajustes),
		Pestanas.Pestana(VOCES, getString(R.string.pestana_voces), R.drawable.ic_voces),
		Pestanas.Pestana(SONIDO, getString(R.string.pestana_sonido), R.drawable.ic_sonido),
		Pestanas.Pestana(TEXTO, getString(R.string.pestana_texto), R.drawable.ic_texto),
		Pestanas.Pestana(MOTOR, getString(R.string.pestana_motor), R.drawable.ic_motor),
		Pestanas.Pestana(ACTUALIZACIONES, getString(R.string.pestana_actualizaciones), R.drawable.ic_actualizar),
	)

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		b = ActividadSimpleBinding.inflate(layoutInflater)
		setContentView(b.root)
		configurarBarra(b.barra, getString(R.string.ajustes))
		val elegida = estado?.getString(EXTRA_PESTANA) ?: intent.getStringExtra(EXTRA_PESTANA) ?: GENERAL
		val p = Pestanas(this, abajo = true)
		b.pestanas.visibility = View.VISIBLE
		b.pestanas.addView(p, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
		p.poner(lista, elegida) { clave -> mostrar(clave) }
		pestanas = p
		onBackPressedDispatcher.addCallback(this, volverAGeneral)
		volverAGeneral.isEnabled = elegida != GENERAL
		if (supportFragmentManager.findFragmentById(R.id.contenedor) == null) mostrar(p.actual)
	}

	override fun onSaveInstanceState(estado: Bundle) {
		super.onSaveInstanceState(estado)
		estado.putString(EXTRA_PESTANA, pestanas?.actual ?: GENERAL)
	}

	/** Atrás (y la flecha de arriba) desde otra pestaña vuelve a General; desde General, sale. */
	private val volverAGeneral = object : androidx.activity.OnBackPressedCallback(false) {
		override fun handleOnBackPressed() {
			pestanas?.elegir(GENERAL)
			mostrar(GENERAL)
		}
	}

	private fun mostrar(clave: String) {
		volverAGeneral.isEnabled = clave != GENERAL
		val f: Fragment = when (clave) {
			VOCES -> AjustesVocesFragment()
			SONIDO -> AjustesSonidoFragment()
			TEXTO -> AjustesXml.nuevo(R.xml.ajustes_texto)
			MOTOR -> AjustesMotorFragment()
			ACTUALIZACIONES -> AjustesActualizacionesFragment()
			else -> AjustesGeneralFragment()
		}
		supportFragmentManager.beginTransaction().replace(R.id.contenedor, f).commit()
	}

	companion object {
		/** Extra con la pestaña que se abre (una de las claves de abajo). */
		const val EXTRA_PESTANA = "pestana"
		const val GENERAL = "general"
		const val VOCES = "voces"
		const val SONIDO = "sonido"
		const val TEXTO = "texto"
		const val MOTOR = "motor"
		const val ACTUALIZACIONES = "actualizaciones"
	}
}

/** Base de las pestañas de Ajustes: todas guardan en el archivo de ajustes del almacenamiento protegido. */
abstract class AjustesBase : PreferenceFragmentCompat() {
	protected val ajustes by lazy { Ajustes(requireContext()) }

	protected fun prepararAlmacen() {
		preferenceManager.sharedPreferencesName = Ajustes.ARCHIVO
		if (Build.VERSION.SDK_INT >= 24) preferenceManager.setStorageDeviceProtected()
		// Que el escuchador de revisiones esté registrado sobre el mismo archivo
		Ajustes.preferencias(requireContext())
	}
}

/** Pestaña hecha solo con un XML. */
class AjustesXml : AjustesBase() {
	override fun onCreatePreferences(estado: Bundle?, clave: String?) {
		prepararAlmacen()
		setPreferencesFromResource(requireArguments().getInt("xml"), clave)
	}

	companion object {
		fun nuevo(xml: Int) = AjustesXml().apply { arguments = Bundle().apply { putInt("xml", xml) } }
	}
}

class AjustesSonidoFragment : AjustesBase() {
	override fun onCreatePreferences(estado: Bundle?, clave: String?) {
		prepararAlmacen()
		setPreferencesFromResource(R.xml.ajustes_sonido, clave)
	}
}
