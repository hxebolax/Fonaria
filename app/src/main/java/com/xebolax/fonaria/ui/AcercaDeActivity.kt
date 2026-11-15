package com.xebolax.fonaria.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.xebolax.fonaria.BuildConfig
import com.xebolax.fonaria.R
import com.xebolax.fonaria.accesibilidad.configurarBarra
import com.xebolax.fonaria.accesibilidad.copiar
import com.xebolax.fonaria.accesibilidad.dp
import com.xebolax.fonaria.accesibilidad.encabezado
import com.xebolax.fonaria.databinding.ActividadSimpleBinding
import com.xebolax.fonaria.motor.Motor
import com.xebolax.fonaria.motor.Nativo

/** Acerca de: versión, autoría, licencias y el diagnóstico del motor (copiable para informar de un problema). */
class AcercaDeActivity : AppCompatActivity() {
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		val b = ActividadSimpleBinding.inflate(layoutInflater)
		setContentView(b.root)
		configurarBarra(b.barra, getString(R.string.acerca_de))
		val m = dp(this, 16)
		val lista = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(m, m, m, m)
		}
		fun titulo(texto: String) = TextView(this).apply {
			text = texto
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
			setPadding(0, m, 0, m / 2)
			encabezado()
		}
		fun parrafo(texto: String) = TextView(this).apply {
			text = texto
			setTextIsSelectable(true)
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
		}
		fun boton(texto: String, accion: () -> Unit) = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
			text = texto
			minHeight = dp(this@AcercaDeActivity, 48)
			setOnClickListener { accion() }
		}
		lista.addView(titulo(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME))
		lista.addView(parrafo(getString(R.string.acerca_texto)))
		lista.addView(boton(getString(R.string.licencias)) {
			startActivity(Intent(this, ManualActivity::class.java).putExtra(ManualActivity.EXTRA_PAGINA, "licencias"))
		})
		lista.addView(boton(getString(R.string.codigo_fuente)) {
			startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.REPO_GITHUB}")))
		})
		lista.addView(titulo(getString(R.string.diagnostico)))
		val diagnostico = buildString {
			append("Fonaria ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
			append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
			append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
			append("ABI: ").append(Build.SUPPORTED_ABIS.joinToString()).append("\n")
			append("ARMv8.2: ").append(if (Nativo.admiteArmV82()) "sí" else "no").append("\n")
			append(Motor.diagnostico())
		}
		lista.addView(parrafo(diagnostico))
		lista.addView(boton(getString(R.string.copiar_diagnostico)) { copiar(diagnostico) })
		b.contenedor.addView(ScrollView(this).apply {
			addView(lista)
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
		})
	}
}
