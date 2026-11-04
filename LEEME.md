# Pasitos para Android: cómo obtener el APK (sin instalar nada)

Este proyecto usa el micrófono y la voz nativos de Android, así que funciona sin conexión a Chrome.
El APK se compila gratis en GitHub.

1. Crea una cuenta en https://github.com (si no tienes).
2. Pulsa "+" → "New repository". Nombre: pasitos. Público o privado. "Create repository".
3. En el repositorio, pulsa "uploading an existing file" y arrastra TODO el contenido de esta carpeta
   (incluida la carpeta oculta `.github`). Pulsa "Commit changes".
   - Si tu computador oculta `.github`, en Windows activa "Ver → Elementos ocultos"; en Mac pulsa Cmd+Shift+punto.
4. Ve a la pestaña "Actions". Verás "Construir APK" en marcha (tarda unos 4 minutos).
5. Cuando tenga el visto verde, ábrelo y abajo, en "Artifacts", descarga "Pasitos-APK".
   Es un .zip que trae app-debug.apk.
6. Pasa el .apk al celular, ábrelo y permite "instalar apps de origen desconocido".
7. Al abrir la app, acepta el permiso del micrófono.

## Requisitos del celular
- Android 7 o superior.
- Reconocimiento de voz de Google instalado (viene en casi todos los Android).
  Para usarlo sin internet: Ajustes → Google → Voz → Reconocimiento sin conexión → descargar Español.
- Voz en español para leer en voz alta: Ajustes → Idioma → Salida de texto a voz → Español.
