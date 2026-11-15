# Traducir Fonaria

Fonaria está en español (idioma principal) e inglés. Añadir otro idioma no necesita tocar código.

## 1. Los textos de la app

1. Copia `app/src/main/res/values-en/strings.xml` y `app/src/main/res/values-en/arrays.xml` a una carpeta nueva `app/src/main/res/values-<código>/`, donde `<código>` es el código ISO del idioma: `values-fr`, `values-de`, `values-pt`, `values-it`, `values-ca`…
   Para una variante de país: `values-pt-rBR`.
2. Traduce **solo el texto** que hay entre las etiquetas. No cambies los atributos `name`.
3. Respeta los marcadores: `%1$s` (un texto), `%1$d` (un número entero) y `%1$.1f` (un número con un decimal) deben seguir en la frase, aunque cambien de sitio.
4. El apóstrofo se escribe `\'` y el salto de línea `\n`.
5. Los `plurals` llevan las formas de tu idioma (`one`, `other` y, según el idioma, `few` o `many`).
6. Las cadenas marcadas `translatable="false"` (el nombre de la app y las frases de muestra de cada voz) no se traducen.

Añade el idioma a la lista de Ajustes, General, Idioma de la app: en `app/src/main/res/values/arrays.xml`, un elemento en `idioma_app_nombres` (escrito en ese idioma, por ejemplo «Français») y su código en `idioma_app_valores` (`fr`). En Android 13 y posteriores aparece además en los ajustes del sistema, Idioma de la app, sin hacer nada más.

## 2. El manual

Copia `app/src/main/assets/manual/manual_en.html` a `manual_<código>.html` y tradúcelo. Haz lo mismo con `licencias_en.html`. Si falta, se muestra el español.

## 3. Comprobar

1. `compilar.bat compilar`. Si falta un marcador o una etiqueta está mal cerrada, la compilación lo dice.
2. Instala la app, elige tu idioma en Ajustes, General, y recorre las pantallas con TalkBack: que nada se corte, que cada botón diga lo que hace y que no haya dos botones con el mismo nombre.

## 4. Enviarlo

Abre una solicitud de cambios (pull request) en GitHub con la carpeta `values-<código>` y el manual. ¡Gracias!
