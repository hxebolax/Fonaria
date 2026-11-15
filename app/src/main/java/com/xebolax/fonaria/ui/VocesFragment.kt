package com.xebolax.fonaria.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.xebolax.fonaria.R
import com.xebolax.fonaria.databinding.SeccionPestanasBinding

/** Voces: pestañas abajo, Instaladas, Descargar e Importadas. */
class VocesFragment : Fragment() {
	private var _b: SeccionPestanasBinding? = null
	private val b get() = _b!!
	private var pestanas: Pestanas? = null

	override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
		_b = SeccionPestanasBinding.inflate(inflater, contenedor, false)
		return b.root
	}

	override fun onViewCreated(vista: View, estado: Bundle?) {
		b.paginas.adapter = object : FragmentStateAdapter(this) {
			override fun getItemCount() = 3
			override fun createFragment(posicion: Int) = ListaVocesFragment.nuevo(posicion)
		}
		// Deslizar entre páginas choca con los gestos de TalkBack: se cambia solo con las pestañas
		b.paginas.isUserInputEnabled = false
		val inicial = estado?.getInt(ARG_PESTANA) ?: arguments?.getInt(ARG_PESTANA) ?: 0
		b.paginas.setCurrentItem(inicial, false)
		val p = Pestanas(requireContext(), abajo = true)
		b.pestanas.addView(p, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
		p.poner(
			listOf(
				Pestanas.Pestana("0", getString(R.string.pestana_instaladas), R.drawable.ic_instaladas),
				Pestanas.Pestana("1", getString(R.string.pestana_descargar), R.drawable.ic_descargar),
				Pestanas.Pestana("2", getString(R.string.pestana_importadas), R.drawable.ic_importar),
			),
			inicial.toString(),
		) { clave -> b.paginas.setCurrentItem(clave.toInt(), false) }
		pestanas = p
	}

	override fun onSaveInstanceState(estado: Bundle) {
		super.onSaveInstanceState(estado)
		_b?.let { estado.putInt(ARG_PESTANA, it.paginas.currentItem) }
	}

	fun irA(pestana: Int) {
		_b?.paginas?.setCurrentItem(pestana, false)
		pestanas?.elegir(pestana.toString())
	}

	override fun onDestroyView() {
		_b = null
		pestanas = null
		super.onDestroyView()
	}

	companion object {
		const val PESTANA_INSTALADAS = 0
		const val PESTANA_DESCARGAR = 1
		const val PESTANA_IMPORTADAS = 2
		private const val ARG_PESTANA = "pestana"

		fun nuevo(pestana: Int) = VocesFragment().apply { arguments = Bundle().apply { putInt(ARG_PESTANA, pestana) } }
	}
}
