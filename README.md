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

## Integración continua

En pushes y pull requests, cada workflow se ejecuta cuando cambian archivos de
su proyecto o del contrato compartido. Mobile también se ejecuta cuando cambian
el generador de fixtures o el código de exportación de Desktop. Las
actualizaciones exclusivas de dependencias Python solo ejecutan Desktop, aunque
puedan afectar a la generación de archivos para Android; la compatibilidad con
esas dependencias se puede comprobar ejecutando Mobile manualmente. Los
workflows conservan su ejecución manual.

Si se configuran checks obligatorios en GitHub, los workflows omitidos por sus
filtros de rutas pueden quedar pendientes e impedir la fusión; ajusta las
reglas de protección antes de exigirlos.

## Pre-commit

Instala el hook desde la raíz del repositorio:

```powershell
uv tool install pre-commit
pre-commit install
```

Al modificar archivos Python o la configuración de dependencias de escritorio,
el hook ejecuta la sesión `lint` de Nox (Ruff y mypy) sobre el proyecto completo.
Para comprobar todos los archivos manualmente, ejecuta `pre-commit run --all-files`.
