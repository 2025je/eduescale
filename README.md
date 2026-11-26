# EduEscale

Repositorio con los proyectos de escritorio y móvil de EduEscale:

- [Aplicación de escritorio](./eduescale-desktop/README.md): aplicación Python
  para consultar el padrón ESCALE y exportar Excel y archivos `.eduescale`.
- [Aplicación móvil](./eduescale-mobile/README.md): aplicación Android nativa
  para importar `.eduescale` y explorar los colegios en un mapa OpenStreetMap.

La aplicación de escritorio mantiene su propio `pyproject.toml` y `uv.lock`.
Para instalarla y ejecutarla, sigue las instrucciones de su documentación.
El contrato compartido de los archivos `.eduescale` está en
[formato v1](./format/README.md).

## Pre-commit

Instala el hook desde la raíz del repositorio:

```powershell
uv tool install pre-commit
pre-commit install
```

Al modificar archivos Python o la configuración de dependencias de escritorio,
el hook ejecuta la sesión `lint` de Nox (Ruff y mypy) sobre el proyecto completo.
Para comprobar todos los archivos manualmente, ejecuta `pre-commit run --all-files`.
