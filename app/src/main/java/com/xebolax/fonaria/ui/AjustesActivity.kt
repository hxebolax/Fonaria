package com.xebolax.fonaria.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.tabs.TabLayout
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

	private val pestanas = listOf(
		R.string.pestana_voces,
		R.string.pestana_sonido,
		R.string.pestana_texto,
		R.string.pestana_motor,
		R.string.pestana_actualizaciones,
		R.string.pestana_general,
	)

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		b = ActividadSimpleBinding.inflate(layoutInflater)
		setContentView(b.root)
		configurarBarra(b.barra, getString(R.string.ajustes))
		b.pestanas.visibility = View.VISIBLE
		b.pestanas.tabMode = TabLayout.MODE_SCROLLABLE
		pestanas.forEach { b.pestanas.addTab(b.pestanas.newTab().setText(it)) }
		val elegida = estado?.getInt("pestana") ?: intent.getIntExtra("pestana", 0)
		b.pestanas.getTabAt(elegida)?.select()
		if (supportFragmentManager.findFragmentById(R.id.contenedor) == null) mostrar(elegida)
		b.pestanas.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
			override fun onTabSelected(t: TabLayout.Tab) = mostrar(t.position)
			override fun onTabUnselected(t: TabLayout.Tab) = Unit
			override fun onTabReselected(t: TabLayout.Tab) = Unit
		})
	}

	override fun onSaveInstanceState(estado: Bundle) {
		super.onSaveInstanceState(estado)
		estado.putInt("pestana", b.pestanas.selectedTabPosition)
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
