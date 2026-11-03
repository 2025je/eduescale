# Buscador de colegios ESCALE

Aplicación de escritorio en Python para extraer los servicios educativos publicados en
el [Padrón de IIEE de ESCALE](https://escale.minedu.gob.pe/padron-de-iiee).
La app usa Scrapy con `AsyncioSelectorReactor` para consultar la API del buscador
oficial y crear un Excel local. La interfaz está hecha con QML y PySide6, con
estilo Material.

## Requisitos

- Python 3.10 o posterior
- [uv](https://docs.astral.sh/uv/)

## Instalación y ejecución

```powershell
uv sync
uv run eduescale-gui
```

Se abrirá una ventana Material para seleccionar la ubicación, la gestión y el
nivel educativo. La extracción se ejecuta en segundo plano para mantener la
interfaz receptiva. Durante el proceso se muestra el total de registros, cuántos
se han recibido y cuántas páginas se han descargado.

También se puede iniciar con `uv run python app.py` por compatibilidad.

## Uso como librería

El paquete `eduescale` publica las funciones de consulta, filtros y parsing:

```python
from eduescale import SchoolFilters, build_search_params, fetch_regions

regions = fetch_regions()
params = build_search_params(
    SchoolFilters(region_code="22", management="publica", level="inicial")
)
```

La GUI está separada en una vista QML, un controlador y servicios dedicados a
la carga de ubicaciones y al proceso Scrapy. El comando `eduescale-gui` es el
entry point instalado para iniciar esa interfaz.

## Uso

Selecciona, si lo deseas, departamento, provincia y distrito; gestión pública o
privada; y nivel inicial, primaria o secundaria. Pulsa **Scrapear y generar
Excel**. El archivo incluye nombre del colegio, dirección, localidad, centro
poblado (CP), turno, alumnos, secciones, longitud y latitud del local educativo,
y un enlace para abrir las coordenadas en Google Maps.

También puedes ejecutar el spider directamente desde la terminal:

```powershell
uv run --project . --directory eduescale scrapy crawl colegios -a output=colegios_escale.xlsx -a region_code=22 -a management=publica -a level=inicial
```

Los filtros `region_code`, `province_code`, `district_code`, `management` y
`level` son opcionales. Los valores para gestión son `publica` y `privada`; los
valores para nivel son `inicial`, `primaria` y `secundaria`.

La app consulta los servicios activos, como en la configuración predeterminada
del buscador de ESCALE. La ubicación corresponde al local donde se brinda el
servicio educativo, según las consideraciones de la fuente oficial. Scrapy usa
el reactor asyncio y consulta el servicio en páginas de 50 registros.

## Pruebas

```powershell
uv run python -m unittest discover -s tests
```
