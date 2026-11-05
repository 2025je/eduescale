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
- Al abrir uno desde otra app, EduEscale queda en una tarea separada. El botón
  Atrás regresa a la app de origen y deja EduEscale disponible en **Recientes**
  para volver al mapa con los datos cargados.
- Muestra un marcador por local educativo con coordenadas válidas y agrupa sus
  registros por código de local, nombre, gestión y ubicación.
- Permite buscar colegios por nombre, sin distinguir mayúsculas ni tildes.
- Filtra los resultados por departamento, provincia, distrito, gestión y nivel
  educativo desde **Filtros avanzados**.
- Distingue colegios públicos (verde) y particulares (naranja); el tamaño del
  marcador representa el nivel más avanzado que se ofrece en ese local.
- Muestra tu ubicación actual con un indicador azul pulsante al tocar el botón
  de ubicación; la primera vez, Android solicita permiso aproximado o preciso.
- Al tocar un marcador, muestra nombre, dirección, localidad, distrito,
  provincia, departamento, CP, turno, gestión, todos los niveles educativos y
  los totales de alumnos y secciones, con opciones para abrir rutas en auto con
  Google Maps o Waze y compartir el enlace.
- Presenta la información en una ficha desplazable con niveles destacados,
  métricas de alumnos/secciones y acciones para abrir o compartir la ubicación.
- Permite minimizar o cerrar la ficha; si un archivo antiguo no contiene nivel
  o gestión, muestra un aviso en lugar de ocultar esos datos silenciosamente.
- Informa cuántos registros no tienen coordenadas utilizables.

El mapa usa MapLibre Native Android y los mosaicos raster estándar de
OpenStreetMap. La atribución «© OpenStreetMap contributors» permanece visible y
las solicitudes identifican la aplicación. MapLibre conserva en una caché HTTP
local, limitada a 128 MiB, los mosaicos que solicita al navegar y respeta las
cabeceras de caché del servidor. Las zonas visitadas recientemente pueden
seguir visibles sin conexión mientras sus mosaicos permanezcan en caché; esto
no garantiza disponibilidad offline completa ni descarga áreas por adelantado.
El servidor público de mosaicos de OpenStreetMap tiene capacidad limitada y no
ofrece SLA; no se deben precargar ni descargar mosaicos en bloque. Para un uso
de producción con mucho tráfico o descargas offline completas, configura un
proveedor de mosaicos con capacidad y condiciones adecuadas y respeta la
[política de uso de mosaicos de OpenStreetMap](https://operations.osmfoundation.org/policies/tiles/).

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

En la aplicación, pulsa **Abrir archivo**, selecciona el archivo `.eduescale` y
acerca o aleja el mapa. Toca un punto para consultar sus datos. Los archivos
pueden tener cualquier nombre; el contenido debe respetar el esquema versionado
que documenta Desktop.

Las pruebas unitarias del lector están en `app/src/test`.
