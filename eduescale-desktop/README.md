# Buscador de colegios ESCALE

Aplicación de escritorio en Python para extraer los servicios educativos publicados en
el [Padrón de IIEE de ESCALE](https://escale.minedu.gob.pe/padron-de-iiee).
La app usa Scrapy con `AsyncioSelectorReactor` para consultar la API del buscador
oficial y guardar los resultados temporalmente. Desde la interfaz puedes exportar
los datos a Excel, PDF o a un archivo `.eduescale` para la app Android. La
interfaz está hecha con QML, PySide6 Essentials y Qt WebEngine para renderizar el
mapa con MapLibre GL JS. La interfaz usa Roboto, incluida con
la licencia SIL Open Font License, y adapta a Qt Quick Controls los tokens
visuales de [Material Style](https://materialstyle.github.io/).

## Requisitos

- Python 3.10 o posterior
- [uv](https://docs.astral.sh/uv/)

## Instalación y ejecución

```powershell
uv sync
uv run eduescale-gui
```

Se abrirá una ventana Material para crear combinaciones de ubicación, gestión y
nivel educativo. El mapa muestra primero los departamentos; selecciona uno o
varios y pulsa «Ver provincias». Después selecciona provincias y pulsa
«Ver distritos» para elegir las zonas finales. Puedes volver a un nivel anterior
sin perder las selecciones que ya hiciste. Arrastra el mapa para moverlo y usa
la rueda del ratón para acercar o alejar. Si no seleccionas una ubicación, la
búsqueda cubre todo el país. Cada distrito
seleccionado se consulta por separado y los colegios repetidos se guardan una
sola vez. La extracción se ejecuta en
segundo plano para mantener la interfaz receptiva. Puedes cancelarla desde la
barra inferior; los archivos anteriores se conservan si la extracción se
cancela o falla.

También se puede iniciar con `uv run python app.py` por compatibilidad.

## Uso como librería

El paquete `eduescale` publica las funciones de consulta, filtros y parsing.
Pydantic valida los modelos de filtros y registros:

```python
from eduescale import SchoolFilters, build_search_params, fetch_regions

regions = fetch_regions()
params = build_search_params(
    SchoolFilters(
        region_code="22",
        management=("publica",),
        level=("primaria",),
    )
)
```

La GUI está separada en una vista QML, un controlador y servicios dedicados a
la carga de ubicaciones y al proceso Scrapy. `application.create_controller`
es el punto de composición: crea los servicios predeterminados y los inyecta
al controlador; también permite reemplazarlos por implementaciones propias.
El controlador depende de contratos de servicio, no de sus implementaciones
concretas. El comando `eduescale-gui` es el entry point instalado para iniciar
esa interfaz.

## Uso

Selecciona cero o más distritos directamente en el mapa; una o ambas opciones
de gestión pública/privada; y uno o varios niveles entre primaria, secundaria y
CETPRO. Pulsa **Agregar a mis búsquedas** para guardar los filtros y repite el
proceso para agregar otros criterios. Cada combinación debe tener al menos una
opción de Gestión y una de Nivel educativo. Puedes quitar combinaciones antes
de iniciar. Al pulsar
**Ejecutar búsqueda**, solo se consultan y guardan temporalmente los resultados;
no se crean archivos de exportación. Al finalizar, usa **Exportar Excel**,
**Exportar PDF** o **Exportar .eduescale** para abrir un diálogo y elegir la
carpeta y el nombre del archivo; se sugiere la última carpeta utilizada y un
nombre predeterminado. Al confirmar, se genera únicamente el formato elegido y
se abre con la aplicación predeterminada del sistema. El nombre sugerido incluye la fecha y hora en que
finalizó la descarga de datos, por ejemplo
`colegios_escale_2026-11-04_16-09-53.xlsx`; ambos formatos usan el mismo nombre
base. Los colegios repetidos se guardan una sola vez. El Excel crea una hoja por
distrito y, dentro de cada hoja, una tabla por cada nivel presente (Primaria,
Secundaria o CETPRO). Cada tabla indica el nivel y el distrito en su cabecera;
sus columnas incluyen nombre del colegio, dirección, localidad, centro poblado
(CP), turno, alumnos y secciones. La primera columna indica el orden de los
colegios dentro de cada tabla, empezando en 1. Departamento, provincia y distrito solo
aparecen en la cabecera de la tabla. No incluye coordenadas ni enlaces de Google
Maps. El Excel se prepara en orientación horizontal y ajustado al ancho de página.
El PDF conserva esta organización; cuando una tabla continúa en otra página,
repite sus encabezados y mantiene cada fila de colegio completa.

Las exportaciones Excel, PDF y `.eduescale` comparten el mismo nombre base y
solo cambia la extensión.
El `.eduescale` es un archivo JSON UTF-8 versionado con departamento, provincia,
distrito, los datos de cada colegio, el código del local y longitud/latitud, sin
la columna de orden del reporte ni el enlace de Maps. Por ejemplo,
`colegios_escale.xlsx` produce `colegios_escale.eduescale`:

```json
{
  "formato": "eduescale",
  "version": 1,
  "colegios": [
    {
      "departamento": "Loreto",
      "provincia": "Maynas",
      "distrito": "Iquitos",
      "nombre": "Colegio Ejemplo",
      "direccion": "Av. Principal",
      "localidad": "Iquitos",
      "cp": "Centro Poblado",
      "turno": "Mañana",
      "alumnos": 120,
      "secciones": 6,
      "gestion": "publica",
      "nivel": "secundaria",
      "codigo_local": "000123",
      "longitud": -73.253,
      "latitud": -3.749
    }
  ]
}
```

Puedes enviar el `.eduescale` como documento por WhatsApp; en Android, al tocar
el archivo, el sistema ofrecerá abrirlo con EduEscale si la app está instalada.
Android también sigue admitiendo archivos `.eduescale` antiguos en ZIP.

`gestion` identifica colegios públicos o privados y `nivel` clasifica el servicio como primaria, secundaria o CETPRO.
`codigo_local` identifica el local educativo.
La app móvil combina los registros con el mismo código de local, nombre,
gestión y ubicación para mostrar un solo marcador con todos sus niveles; los
alumnos y las secciones se suman entre esos niveles. El scraper omite los
registros sin una clasificación admitida.

También puedes ejecutar el spider directamente desde la terminal:

```powershell
uv run --project . --directory eduescale scrapy crawl colegios -a output=colegios_escale.xlsx -a region_code=22 -a management=publica,privada -a level=primaria,cetpro
```

Este comando también genera `colegios_escale.eduescale` en la misma carpeta.

### Límites del mapa

El mapa usa MapLibre GL JS 5.24.0 con recursos incluidos localmente; no descarga
mapas base ni depende de una conexión a Internet. MapLibre se distribuye bajo
BSD-3-Clause; su licencia está en `eduescale/gui/assets/maplibre/LICENSE.txt`.

La capa distrital descargable del INEI está actualizada al 2023 y contiene
1.890 UBIGEO únicos. La consulta en vivo de ESCALE usada para esta versión
devolvió 1.892 códigos; la cobertura exacta se conserva en
`tests/data/escale_district_codes.txt` y se valida automáticamente. Los dos
códigos ausentes en la capa del INEI son Alto Trujillo (`130112`) y Santa Rosa
de Loreto (`160405`). Para mostrarlos en el mapa se añadieron geometrías
aproximadas a partir de las leyes [31644](https://leyes.congreso.gob.pe/Documentos/2021_2026/ADLP/Texto_Consolidado/31644-TXM.pdf)
y [32403](https://leyes.congreso.gob.pe/Documentos/2021_2026/ADLP/Texto_Consolidado/32403-TXM.pdf);
aparecen en ocre y no representan límites oficiales ni deben usarse para fines
legales o catastrales.

El mapa se genera desde el GeoPackage distrital oficial extraído de
<https://ide.inei.gob.pe/files/Distrito.rar>:

```powershell
uv run python scripts/build_district_map.py .\DISTRITO.gpkg
```

El generador también crea `eduescale/gui/assets/districts_map_hierarchy.json`
con los límites disueltos por departamento y provincia que usa la navegación
por etapas del mapa.

Los filtros `region_code`, `province_code`, `district_code`, `management` y
`level` son opcionales. Los valores para gestión son `publica` y `privada`; los
valores para nivel son `primaria`, `secundaria` y `cetpro`. Para combinar
valores en la terminal, sepáralos con comas. Los registros de otros niveles
se omiten.

La app consulta los servicios activos, como en la configuración predeterminada
del buscador de ESCALE. La ubicación corresponde al local donde se brinda el
servicio educativo, según las consideraciones de la fuente oficial. Scrapy usa
el reactor asyncio y consulta el servicio en páginas de 50 registros.
La deduplicación entre filtros conserva la comparación de todos los campos del
registro y usa una base SQLite temporal para evitar que el conjunto de claves
crezca en memoria; se elimina al cerrar el spider.
El formulario se apila en una columna cuando la ventana mide menos de 720 px;
el contenido sigue siendo desplazable en ventanas pequeñas.
Las consultas directas de ubicación reintentan errores temporales de red y
respuestas HTTP recuperables con backoff acotado y respetan `Retry-After`.
La aplicación recuerda el tamaño de la ventana y la última carpeta de exportación
en las preferencias locales. En Windows, los logs rotativos se guardan en
`%LOCALAPPDATA%\EduScale\logs` (5 MiB por archivo y tres respaldos).

## Pruebas

```powershell
uv run --group nox nox
```

Nox crea el entorno de pruebas con `uv`, instala el grupo `test` desde el
`uv.lock` y ejecuta `pytest`. La suite incluye una prueba QML sin conexión que
verifica el diseño en 1280 × 720, 1366 × 768, 1600 × 900 y 1920 × 1080.

## Ejecutable para Windows

Genera los ejecutables con PyInstaller a través de Nox:

```powershell
uv run --group nox nox -s build
```

La carpeta `dist` contendrá `eduescale-gui.exe` y la carpeta
`eduescale-scraper`, que incluye el worker y sus dependencias. Mantén ambos
elementos juntos: la interfaz usa `QProcess` para iniciar el worker de Scrapy
como proceso separado y recibir su progreso. El worker se distribuye como una
carpeta para que, al cancelar una extracción, Windows cierre el proceso que
mantiene abierta la base de datos temporal.
Nox usa `eduescale-gui.spec` y `eduescale-scraper.spec` como configuración de
empaquetado. GitHub Actions ejecuta las pruebas, Ruff, mypy, ambos builds de
Windows y una comprobación del comando `list` del scraper, los recursos QML de Material,
los certificados, MapLibre y Qt WebEngine incluidos en la GUI en cada push y pull request.
También se puede ejecutar manualmente desde GitHub Actions; ese flujo permite
descargar la interfaz y la carpeta del worker como artefacto durante siete días.

Para comprobar lint, formato y tipos con Ruff y mypy:

```powershell
uv run --group nox nox -s lint
```

Para aplicar el formato Ruff:

```powershell
uv run --group nox nox -s format
```
