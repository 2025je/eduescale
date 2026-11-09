# EduEscale

Aplicación Android nativa en Kotlin para leer las exportaciones `.eduescale` de
[EduScale Desktop](../eduescale-desktop/README.md) y ubicar los colegios en un
mapa interactivo.
La interfaz usa la familia `sans-serif` de Android, basada en Roboto.

## Funcionalidad

- Importa el archivo mediante el selector de documentos de Android; no requiere
  permisos de almacenamiento.
- Lee el JSON versionado del archivo `.eduescale` en streaming y sigue
  admitiendo archivos ZIP de versiones anteriores.
- Puede abrir directamente los archivos `.eduescale` recibidos como documentos
  por WhatsApp u otras apps compatibles.
- Conserva el último archivo importado en el almacenamiento privado de la app
  (`files/selected.eduescale`) y lo restaura al volver a abrirla, incluso si el
  permiso temporal de WhatsApp para leer el archivo ya expiró.
- Al abrir uno desde otra app, EduEscale queda en una tarea separada. El botón
  Atrás regresa a la app de origen y deja EduEscale disponible en **Recientes**
  para volver al mapa con los datos cargados.
- Muestra un marcador por local educativo con coordenadas válidas y agrupa sus
  registros por código de local, nombre, gestión y ubicación.
- Permite buscar colegios por nombre, sin distinguir mayúsculas ni tildes.
- Ordena la pestaña **Colegios** por cercanía y muestra la distancia aproximada en
  kilómetros; identifica los colegios sin ubicación, permite abrir sus detalles
  y muestra cuántos hay entre los resultados.
- En **Archivos**, distingue los registros originales del archivo, los locales
  agrupados con ubicación y los registros sin coordenadas utilizables.
- Filtra los resultados por departamento, provincia, distrito, gestión y nivel
  educativo desde **Filtros avanzados**.
- Distingue colegios públicos (verde) y particulares (naranja); el tamaño del
  marcador representa el nivel más avanzado que se ofrece en ese local.
- Resalta el colegio seleccionado con un aro celeste de borde blanco y ajusta
  el mapa para mantenerlo visible por encima de la ficha.
- Muestra tu ubicación actual con un indicador azul pulsante al tocar el botón
  de ubicación; la primera vez, Android solicita permiso aproximado o preciso.
- Si los servicios de ubicación están desactivados, ofrece abrir la configuración
  del sistema; al volver, detecta el cambio y actualiza la ubicación del mapa.
- Conserva el centro, nivel de zoom, inclinación y rotación del mapa al cerrar y
  volver a abrir la aplicación.
- Permite descargar para uso sin conexión el área visible del mapa y el rango
  de zoom elegido; muestra el progreso y permite pausar, reanudar o eliminar
  cada área guardada.
- Al tocar un marcador, muestra nombre, dirección, localidad, distrito,
  provincia, departamento, CP, turno, gestión, todos los niveles educativos y
  los totales de alumnos y secciones, la distancia desde tu ubicación y
  opciones para abrir rutas en auto con Google Maps o Waze y compartir el enlace.
- Permite guardar comentarios en un historial por colegio, buscarlos por texto
  y editar comentarios con su fecha de modificación. Antes de borrar un
  comentario o cancelar una alarma, pide confirmación.
- Incluye una sección **Historial** global para buscar y filtrar comentarios,
  alarmas pendientes, disparadas, canceladas y vencidas. Desde el historial se
  pueden editar comentarios y alarmas; al guardar una alarma, queda pendiente
  y se programa con la nueva fecha.
- Programa recordatorios de una sola vez. Room conserva comentarios y
  recordatorios en el dispositivo y migra los datos locales de la versión
  anterior. Antes de pedir permisos, la app explica por qué necesita
  notificaciones y acceso a alarmas exactas; las alarmas pendientes se
  restauran al abrir la app o después de reiniciar el dispositivo, siempre que
  ambos permisos sigan habilitados. El historial conserva el estado de cada
  alarma, incluso después de dispararse o cancelarse.
- Exporta comentarios y recordatorios como un archivo JSON desde **Archivos** y
  permite restaurarlo en otro dispositivo. La restauración combina la copia con
  los datos existentes y omite registros duplicados.
- Presenta la información en una ficha desplazable con niveles destacados,
  métricas de alumnos/secciones y acciones para abrir o compartir la ubicación.
- Permite minimizar o cerrar la ficha; si un archivo antiguo no contiene nivel
  o gestión, muestra un aviso en lugar de ocultar esos datos silenciosamente.
- Informa cuántos registros no tienen coordenadas utilizables.

## Interfaz

La app usa Jetpack Compose y Material 3 con navegación inferior entre **Mapa**,
**Colegios** y **Archivos**. La búsqueda y los filtros se aplican localmente a
los registros importados; la ficha del colegio se presenta en una hoja inferior
con opciones para abrir rutas en Google Maps o Waze y compartir la ubicación.
El tema claro u oscuro sigue la preferencia del sistema.

El mapa usa el estilo Liberty de OpenFreeMap con MapLibre Native Android y
mantiene visible la atribución «OpenFreeMap © OpenMapTiles · Data from OpenStreetMap».
MapLibre conserva además una caché HTTP local de hasta 128 MiB. Desde **Mapa**,
desplaza y acerca el mapa al área que quieras guardar, pulsa **Descargar área
visible** y elige el rango de zoom. La app confirma al instante que inició la
preparación y muestra el progreso en el mapa y en **Archivos > Mapas sin
conexión**, donde también puedes pausar, reanudar o eliminar áreas descargadas.

Las condiciones de OpenFreeMap limitan cada descarga a 3.000 teselas o 50 MB y
a un máximo de cuatro solicitudes simultáneas. La app estima las teselas antes
de empezar y elimina una descarga si el tamaño real supera esos límites. El
servicio se ofrece «tal cual» y no tiene SLA; revisa los
[términos de OpenFreeMap](https://openfreemap.org/terms) antes de distribuir la
app o realizar descargas.

## Estructura del código

Paquete base `pe.eduescale.mobile`:

- `di/`: módulo Hilt (`AppModule`) con OkHttp, almacenamiento y OfflineManager.
- `data/`: modelos, Room (`SchoolActivityRepository`), lectura de archivos,
  `SchoolImportController` (importar/restaurar el archivo) y
  `ActivityBackupController` (copia JSON).
- `activity/`: `SchoolActivityController` (comentarios e historial).
- `map/`: estilo, cámara, marcadores y `OfflineMapController`.
- `location/`: `LocationController` (permisos y ubicación).
- `navigation/`: enlaces de navegación.
- `reminders/`: alarmas, `ReminderController` (flujo de recordatorios y permisos)
  y `ReminderDialogs` (selector de fecha y diálogos de permisos).
- `ui/`: `EduEscaleApp` (navegación) y pantallas en `map`, `schools`,
  `history`, `files`, `components` y `theme`.

La inyección de dependencias usa Hilt (`MainActivity`, receivers y
`EduEscaleApplication`).

## Abrir y ejecutar

Abre esta carpeta (`eduescale-mobile`) en Android Studio y sincroniza Gradle.
Se requiere JDK 17 y Android SDK Platform 36. Ejecuta la configuración `app` en
un dispositivo o emulador con Android 7.0 (API 24) o posterior y conexión a
Internet.

Desde PowerShell, también puedes compilar e instalar las pruebas unitarias con
el Gradle Wrapper incluido:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

## Compilar con Docker

Desde esta carpeta, ejecuta:

```powershell
docker buildx build --target apk --output type=local,dest=dist .
```

Docker genera `dist/EduEscale-debug.apk`. La imagen instala JDK 17, Android
SDK Platform 36 y Build Tools 36.0.0, ejecuta las pruebas unitarias y empaqueta
la aplicación como APK de depuración. Se requiere Docker BuildKit y conexión a
Internet durante la primera compilación.

Para compilar con Docker Compose y firmar los artefactos de release con el
keystore local, desde esta carpeta ejecuta:

```powershell
docker compose run --build --rm android
```

Compose monta `../android-secrets/release.jks` y
`../android-secrets/keystore.properties` como secretos BuildKit durante la
compilación; no los copia a la imagen. Se generan el APK debug, el APK release
firmado y el AAB firmado en `dist/`.

Para generar el bundle Android de release:

```powershell
docker buildx build --target release --output type=local,dest=dist .
```

Docker genera `dist/EduEscale-release.aab`. El bundle se compila sin firma de
publicación; antes de subirlo a Google Play, debe firmarse con la clave de carga
configurada para la aplicación.

Para generar un APK de release:

```powershell
docker buildx build --target release-apk --output type=local,dest=dist .
```

Docker genera `dist/EduEscale-release-unsigned.apk`. El APK no está firmado;
antes de instalarlo o distribuirlo, debe firmarse con una clave de release.

En la aplicación, pulsa **Abrir archivo**, selecciona el archivo `.eduescale` y
acerca o aleja el mapa. Toca un punto para consultar sus datos. Los archivos
pueden tener cualquier nombre; el contenido debe respetar el esquema versionado
que documenta Desktop.

Las pruebas unitarias del lector están en `app/src/test`.
