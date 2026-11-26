# EduEscale

Aplicación Android nativa en Kotlin para leer las exportaciones `.eduescale` de
[EduEscale Desktop](../eduescale-desktop/README.md) y ubicar los colegios en un
mapa interactivo.
La interfaz usa la familia `sans-serif` de Android, basada en Roboto.

## Funcionalidad

- Importa el archivo mediante el selector de documentos de Android; no requiere
  permisos de almacenamiento.
- Lee el JSON versionado del archivo `.eduescale` en streaming, valida los
  metadatos si están presentes y sigue admitiendo JSON anterior y ZIP.
- Muestra la versión del formato y la versión de Desktop que generó el archivo;
  en archivos anteriores sin metadatos indica que esta última no está disponible.
- Importa archivos `.eduescale` protegidos con contraseña, verifica el cifrado
  autenticado y guarda la copia descifrada solo en el almacenamiento privado de
  la app para restaurar la sesión.
- Puede abrir directamente los archivos `.eduescale` recibidos como documentos
  por WhatsApp u otras apps compatibles.
- Conserva el último archivo importado en el almacenamiento privado de la app
  (`files/selected.eduescale`) y lo restaura al volver a abrirla, incluso si el
  permiso temporal de WhatsApp para leer el archivo ya expiró.
- Al abrir uno desde otra app, EduEscale queda en una tarea separada. El botón
  Atrás regresa a la app de origen y deja EduEscale disponible en **Recientes**
  para volver al mapa con los datos cargados.
- Agrupa los registros por código de local, nombre, gestión y ubicación. En el
  mapa, los locales cercanos forman grupos con su cantidad; al tocarlos, el mapa
  se acerca y muestra marcadores individuales al aumentar el zoom.
- Permite buscar colegios por nombre, sin distinguir mayúsculas ni tildes.
- Ordena **Colegios** por la distancia de la ruta en automóvil calculada por
  Valhalla; identifica los colegios sin coordenadas o sin ruta y permite abrir
  sus detalles. El cálculo puede tardar para listas grandes.
- Al seleccionar un colegio con ubicación disponible, dibuja en el mapa la ruta
  local en automóvil y muestra distancia y tiempo estimado. Los tramos punteados
  conectan las coordenadas con la carretera en línea recta: son aproximaciones,
  no caminos comprobados ni parte de la distancia o tiempo de la ruta. Se usa
  la ubicación obtenida al seleccionarlo; no es navegación en tiempo real. La
  ruta permanece al cerrar la ficha hasta seleccionar otro colegio.
- En **Archivos**, descarga por separado la red vial de Perú (aproximadamente
  425 MiB) para calcular rutas sin conexión. No es el mismo archivo que los
  mapas visuales: se necesita ubicación y espacio libre para la descarga; al
  actualizarla se conserva la versión anterior hasta verificar la nueva. El
  avance aparece en **Archivos** y **Colegios**, con porcentaje cuando se
  conoce el tamaño total.
- En **Archivos**, distingue los registros originales del archivo, los locales
  agrupados con ubicación y los registros sin coordenadas utilizables.
- Filtra los resultados por departamento, provincia, distrito, gestión y nivel
  educativo desde **Filtros avanzados**.
- Permite guardar búsquedas con nombre en el dispositivo, aplicarlas desde
  **Colegios** e importarlas o exportarlas con los selectores de documentos de
  Android.
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
- **Archivos** informa el total descargado para los mapas sin conexión y el
  espacio disponible en el almacenamiento interno del dispositivo.
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
  alarma, incluso después de dispararse o cancelarse. Si Android recrea el
  proceso mientras se solicitan permisos, conserva el recordatorio ya fechado
  y espera el resultado del permiso antes de programarlo.
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
**Colegios**, **Historial**, **Archivos** y **Ajustes**. En **Ajustes** se puede
elegir **Sistema**, **Claro** u **Oscuro**; Sistema es la opción inicial y la
preferencia se guarda en el dispositivo. La búsqueda y los filtros se aplican localmente a
los registros importados; la ficha del colegio se presenta en una hoja inferior
con opciones para abrir rutas en Google Maps o Waze y compartir la ubicación.
El borrador de filtros y la confirmación de eliminación de un mapa se conservan
al girar la pantalla.

Las rutas usan datos de [OpenStreetMap](https://www.openstreetmap.org/copyright)
© sus colaboradores, obtenidos de [Geofabrik](https://download.geofabrik.de/south-america/peru.html).
La cobertura y los resultados dependen de esos datos; pueden faltar carreteras,
conexiones o restricciones, especialmente cerca de las fronteras. La distancia
corresponde a la ruta calculada, no necesariamente a la ruta más corta.
En **Archivos** puedes consultar el espacio ocupado y eliminar las carreteras
sin conexión previa confirmación; también puedes volver a descargarlas.

Las búsquedas guardadas solo contienen sus filtros, nunca datos de colegios ni
secretos. Su documento portátil usa el formato `eduescale.saved-searches`,
versión `1`, con una lista `searches` y los campos `name`, `department`,
`province`, `district`, `department_code`, `province_code`, `district_code`,
`management` y `levels`. Los códigos se exportan como `null` cuando no están
disponibles en los registros móviles. La app valida formato, versión, tipos,
valores permitidos y nombres al importar.

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
  `SavedSearchJson` (serialización validada de búsquedas), `SchoolFileImportStore`
  (importar/restaurar el archivo) y `ActivityBackupFile` (acceso a copias JSON).
- `activity/`: `SchoolActivityViewModel` (estado, búsqueda y filtros del historial
  y operaciones de comentarios), `ActivityBackupViewModel` (importación y exportación de copias) y
  modelos y contratos de persistencia independientes de las entidades Room.
- `ui/schools/`: `SavedSearchViewModel` gestiona las búsquedas y su
  importación/exportación mediante contratos; los adaptadores están en `data/`.
- `ui/files/`: `SchoolFileImportViewModel` conserva el progreso de importación y
  publica la solicitud de contraseña y el resultado para la actividad;
  `StorageSpaceViewModel` consulta el espacio disponible mediante `StorageSpace`.
- `map/`: adaptadores de MapLibre para cámara, marcadores y conteos de grupos,
  además de `OfflineMapStore`
  y su adaptador `MapLibreOfflineMapStore`. `OfflineMapsViewModel` gestiona las
  descargas, sus límites, su estado y los errores sin depender de una actividad.
- `location/`: `LocationController` (permisos y ubicación) y el adaptador
  `MapLibreLocationDisplay` para mostrarla en el mapa.
- `navigation/`: enlaces de navegación y `SchoolNavigator` (cómo llegar y compartir).
- `reminders/`: `ReminderViewModel` coordina persistencia y alarmas mediante
  `ReminderStore` y `ReminderAlarmPort`; `ReminderController` y
  `ReminderDialogs` adaptan permisos y selectores de Android.
- `ui/`: `EduEscaleViewModel` prepara el listado ordenado y las opciones y el
  borrador de filtros; `AppEventCoordinator` coordina importaciones y restauración.
  `EduEscaleApp` gestiona la navegación y las pantallas en `map`, `schools`,
  `history`, `files`, `components` y `theme`; `ThemeViewModel` gestiona la
  preferencia de apariencia mediante `ThemeSettingsStore`. `SchoolMapCoordinator`
  sincroniza la selección, el archivo y el estado del mapa.

La inyección de dependencias usa Hilt (`MainActivity`, receivers y
`EduEscaleApplication`).

## Abrir y ejecutar

Abre esta carpeta (`eduescale-mobile`) en Android Studio y sincroniza Gradle.
Se requiere JDK 21 y Android SDK Platform 36. Ejecuta la configuración `app` en
un dispositivo o emulador con Android 8.0 (API 26) o posterior y conexión a
Internet.

Desde PowerShell, también puedes compilar e instalar las pruebas unitarias con
el Gradle Wrapper incluido:

```powershell
.\gradlew.bat :app:testCompleteDebugUnitTest :app:testLiteDebugUnitTest
.\gradlew.bat :app:assembleCompleteDebug :app:assembleLiteDebug
```

Las dependencias de prueba incluyen Turbine para `Flow` y Robolectric para
pruebas locales. Room Testing está disponible para pruebas instrumentadas de
la base de datos y sus migraciones; Turbine también puede usarse en ellas.
Navigation Testing y Hilt Testing están disponibles para pruebas locales e
instrumentadas. Las pruebas instrumentadas usan un runner con
`HiltTestApplication`; en Robolectric se puede especificar con `@Config`.
Consulta la [guía de pruebas de Hilt](https://developer.android.com/training/dependency-injection/hilt-testing).

El proyecto ofrece dos variantes: **EduEscale Completa**, con Valhalla y rutas
por carretera, conserva el identificador `pe.eduescale.mobile`; **EduEscale
Lite** calcula distancias en línea recta y usa `pe.eduescale.mobile.lite`.
Pueden instalarse juntas, pero mantienen datos locales independientes.

## Compilar con Docker

Desde esta carpeta, ejecuta:

```powershell
docker buildx build --file Dockerfile --target apk --output type=local,dest=dist ..
```

Docker genera los APK debug y de pruebas instrumentadas de ambas variantes:
`EduEscale-completa-debug.apk`, `EduEscale-lite-debug.apk` y los dos archivos
`*-debug-androidTest.apk`. La variante Lite no incluye las dependencias nativas
de Valhalla ni la descarga de carreteras; las distancias se calculan y etiquetan
en línea recta. La imagen instala JDK 21, Android SDK Platform 36 y Build Tools
36.0.0, ejecuta las pruebas unitarias de ambas variantes y compila las pruebas
instrumentadas. Para controlar el uso de memoria, Docker ejecuta las tareas
debug y cada release por separado, con hasta dos workers y 4 GiB de heap para
Gradle; además se necesita memoria para Kotlin y otros procesos. Se requiere
Docker BuildKit y conexión a Internet durante la primera compilación.

Para ejecutar las pruebas instrumentadas, conecta un dispositivo o inicia un
emulador y, desde esta carpeta, ejecuta:

```powershell
adb install -r dist\EduEscale-completa-debug.apk
adb install -r dist\EduEscale-completa-debug-androidTest.apk
adb shell am instrument -w pe.eduescale.mobile.test/pe.eduescale.mobile.HiltTestRunner

adb install -r dist\EduEscale-lite-debug.apk
adb install -r dist\EduEscale-lite-debug-androidTest.apk
adb shell am instrument -w pe.eduescale.mobile.lite.test/pe.eduescale.mobile.HiltTestRunner
```

El workflow móvil de GitHub Actions usa este mismo `docker-compose.yml` para
ejecutar las pruebas y generar el APK en pushes y pull requests que afecten a
Mobile o al contrato compartido. Para firmar builds desde una
ejecución manual del workflow, configura el environment de GitHub Actions
`android-release` con estos secrets:

- `ANDROID_RELEASE_KEYSTORE_BASE64`: contenido de `release.jks` codificado en
  Base64. En PowerShell puedes obtenerlo con
  `[Convert]::ToBase64String([IO.File]::ReadAllBytes("android-secrets/release.jks"))`.
- `ANDROID_KEYSTORE_PROPERTIES`: contenido completo de
  `android-secrets/keystore.properties`.

El workflow reconstruye esos archivos ignorados localmente y ejecuta el mismo
Compose para probar y compilar. En CI sin firma genera el APK debug y releases
sin firmar; en la ejecución manual genera los APK/AAB firmados. Los artefactos
se publican por siete días y los archivos temporales de firma se eliminan al
terminar.

Para compilar con Docker Compose y firmar los artefactos de release con el
keystore local, desde esta carpeta ejecuta:

```powershell
docker compose run --build --rm android
```

Compose monta `../android-secrets/release.jks` y
`../android-secrets/keystore.properties` como secretos BuildKit durante la
compilación; no los copia a la imagen. Se generan los APK debug, los APK release
y los AAB de ambas variantes en `dist/`. Con el keystore local disponible, los
artefactos release se firman con esa clave.

Para generar el bundle Android de release:

```powershell
docker buildx build --file Dockerfile --target release --output type=local,dest=dist ..
```

Docker genera `dist/EduEscale-completa-release-unsigned.aab` y
`dist/EduEscale-lite-release-unsigned.aab`. Los bundles se compilan sin firma de
publicación; antes de subirlos a Google Play, deben firmarse con la clave de
carga configurada para cada aplicación.

Para generar un APK de release:

```powershell
docker buildx build --file Dockerfile --target release-apk --output type=local,dest=dist ..
```

Docker genera `dist/EduEscale-completa-release-unsigned.apk` y
`dist/EduEscale-lite-release-unsigned.apk`. Los APK no están firmados; antes de
instalarlos o distribuirlos, deben firmarse con una clave de release.

En la aplicación, pulsa **Abrir archivo**, selecciona el archivo `.eduescale` y
acerca o aleja el mapa. Toca un punto para consultar sus datos. Los archivos
pueden tener cualquier nombre; el contenido debe respetar el esquema versionado
que documenta Desktop.

Las pruebas unitarias del lector están en `app/src/test`.
