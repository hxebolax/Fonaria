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
 * Ajustes, con una pestaña por tema: Voces, Sonido, Texto, Motor, Actualizaciones y General.
 * Se abre solo desde el menú lateral.
 */
class AjustesActivity : AppCompatActivity() {
	private lateinit var b: ActividadSimpleBinding
	/** Para «Buscar actualizaciones» de la pestaña Actualizaciones. */
	val actualizaciones = GestorActualizaciones(this)

	private var pestanas: Pestanas? = null

	/** Las seis pestañas, abajo: nombre e icono. */
	private val lista get() = listOf(
		Pestanas.Pestana("0", getString(R.string.pestana_voces), R.drawable.ic_voces),
		Pestanas.Pestana("1", getString(R.string.pestana_sonido), R.drawable.ic_sonido),
		Pestanas.Pestana("2", getString(R.string.pestana_texto), R.drawable.ic_texto),
		Pestanas.Pestana("3", getString(R.string.pestana_motor), R.drawable.ic_motor),
		Pestanas.Pestana("4", getString(R.string.pestana_actualizaciones), R.drawable.ic_actualizar),
		Pestanas.Pestana("5", getString(R.string.pestana_general), R.drawable.ic_ajustes),
	)

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		b = ActividadSimpleBinding.inflate(layoutInflater)
		setContentView(b.root)
		configurarBarra(b.barra, getString(R.string.ajustes))
		val elegida = estado?.getInt("pestana") ?: intent.getIntExtra("pestana", 0)
		val p = Pestanas(this, abajo = true)
		b.pestanas.visibility = View.VISIBLE
		b.pestanas.addView(p, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
		p.poner(lista, elegida.toString()) { clave -> mostrar(clave.toInt()) }
		pestanas = p
		if (supportFragmentManager.findFragmentById(R.id.contenedor) == null) mostrar(elegida)
	}

	override fun onSaveInstanceState(estado: Bundle) {
		super.onSaveInstanceState(estado)
		estado.putInt("pestana", pestanas?.actual?.toIntOrNull() ?: 0)
	}

	private fun mostrar(i: Int) {
		val f: Fragment = when (i) {
			1 -> AjustesSonidoFragment()
			2 -> AjustesXml.nuevo(R.xml.ajustes_texto)
			3 -> AjustesMotorFragment()
			4 -> AjustesActualizacionesFragment()
			5 -> AjustesGeneralFragment()
			else -> AjustesVocesFragment()
		}
		supportFragmentManager.beginTransaction().replace(R.id.contenedor, f).commit()
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
