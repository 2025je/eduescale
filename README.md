# Buscador de colegios ESCALE

Aplicación de escritorio en Python para extraer los servicios educativos publicados en
el [Padrón de IIEE de ESCALE](https://escale.minedu.gob.pe/padron-de-iiee).
La app usa Scrapy con `AsyncioSelectorReactor` para consultar la API del buscador
oficial y crear un Excel local junto con un archivo complementario `.eduescale`
para una app Android. La interfaz está hecha con QML y PySide6
Essentials, que incluye los componentes Qt Quick usados por la app sin empaquetar
los módulos opcionales de WebEngine y PDF. Su paleta y componentes adaptan los
tokens visuales de [Material Style](https://materialstyle.github.io/) a Qt Quick
Controls.

## Requisitos

- Python 3.10 o posterior
- [uv](https://docs.astral.sh/uv/)

## Instalación y ejecución

```powershell
uv sync
uv run eduescale-gui
```

Se abrirá una ventana Material para crear combinaciones de ubicación, gestión y
nivel educativo. Puedes agregar varias combinaciones antes de iniciar la
extracción; se descargan y reúnen en una sola hoja Excel. Los colegios repetidos
entre combinaciones se exportan una sola vez. La extracción se ejecuta en
segundo plano para mantener la interfaz receptiva. Puedes cancelarla desde la
barra inferior; los archivos anteriores se conservan si la extracción se
cancela o falla. El Excel se valida antes de reemplazar los destinos.

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
        level=("inicial",),
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

Selecciona, si lo deseas, departamento, provincia y distrito; una o ambas
opciones de gestión pública/privada; y uno o varios niveles entre inicial,
primaria y secundaria. Pulsa **Agregar combinación de filtros** para guardar
esa búsqueda y repite el proceso para agregar otras ubicaciones o criterios.
Cada combinación debe tener al menos una opción de Gestión y una de Nivel
educativo. Puedes quitar combinaciones antes de iniciar. Al pulsar
**Descargar filtros y generar Excel**, se reúnen los resultados, se eliminan
colegios duplicados entre las búsquedas y se organizan tablas de Excel por
distrito dentro de una hoja. El Excel contiene nombre del colegio, dirección,
localidad, centro poblado (CP), turno, alumnos y secciones; no incluye
coordenadas ni enlaces de Google Maps.

Junto al Excel se genera un archivo del mismo nombre con extensión `.eduescale`.
Su contenido es JSON UTF-8 versionado e incluye departamento, provincia,
distrito, los campos del Excel y longitud/latitud, sin el enlace de Maps. Por
ejemplo, `colegios_escale.xlsx` produce `colegios_escale.eduescale`:

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
      "longitud": -73.253,
      "latitud": -3.749
    }
  ]
}
```

También puedes ejecutar el spider directamente desde la terminal:

```powershell
uv run --project . --directory eduescale scrapy crawl colegios -a output=colegios_escale.xlsx -a region_code=22 -a management=publica,privada -a level=inicial,primaria
```

Este comando también genera `colegios_escale.eduescale` en la misma carpeta.

Los filtros `region_code`, `province_code`, `district_code`, `management` y
`level` son opcionales. Los valores para gestión son `publica` y `privada`; los
valores para nivel son `inicial`, `primaria` y `secundaria`. Para combinar
valores en la terminal, sepáralos con comas.

La app consulta los servicios activos, como en la configuración predeterminada
del buscador de ESCALE. La ubicación corresponde al local donde se brinda el
servicio educativo, según las consideraciones de la fuente oficial. Scrapy usa
el reactor asyncio y consulta el servicio en páginas de 50 registros.
La deduplicación entre filtros conserva la comparación de todos los campos del
registro y usa una base SQLite temporal para evitar que el conjunto de claves
crezca en memoria; se elimina al cerrar el spider.
El formulario se apila en una columna cuando la ventana mide menos de 720 px;
el contenido sigue siendo desplazable en ventanas pequeñas.
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

La carpeta `dist` contendrá `eduescale-gui.exe` y `eduescale-scraper.exe`.
Mantén ambos archivos juntos: la interfaz usa `QProcess` para iniciar el worker
de Scrapy como proceso separado y recibir su progreso. Distribuye los dos
ejecutables en la misma carpeta.
Nox usa `eduescale-gui.spec` y `eduescale-scraper.spec` como configuración de
empaquetado. GitHub Actions ejecuta las pruebas, Ruff, ambos builds de Windows
y una comprobación del comando `list` del scraper, los recursos QML de Material,
los certificados incluidos y la ausencia de WebEngine innecesario en cada push
y pull request.
También se puede ejecutar manualmente desde GitHub Actions; ese flujo permite
descargar ambos ejecutables como artefacto durante siete días.

Para comprobar lint y formato con Ruff:

```powershell
uv run --group nox nox -s lint
```

Para aplicar el formato Ruff:

```powershell
uv run --group nox nox -s format
```
