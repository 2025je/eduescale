# Formato `.eduescale` v1

El contenido es un documento JSON UTF-8 conforme a
[eduescale-v1.schema.json](./eduescale-v1.schema.json). Los campos adicionales
son compatibles y los lectores deben ignorarlos. `metadatos` es opcional para
archivos antiguos; si existe, `cantidad_colegios` debe coincidir con el número
de registros de `colegios`. Los registros pueden omitir campos o tener valores
nulos. Las coordenadas no utilizables no impiden importar el archivo; Android
las trata como ausentes. El nivel inicial no está admitido.

El documento puede almacenarse directamente, en un ZIP con una única entrada
`colegios.json`, o en un contenedor cifrado. El contenedor v1 consta de:

1. Cabecera de 41 bytes en orden big-endian: `EDUSCENC` (8 bytes), versión
   (1 byte, valor 1), iteraciones PBKDF2 (4 bytes), salt (16 bytes) y nonce
   (12 bytes).
2. JSON comprimido con zlib y cifrado con AES-256-GCM; la cabecera completa es
   el dato autenticado adicional. La etiqueta GCM de 16 bytes sigue al cifrado.
3. La clave se deriva de la contraseña UTF-8 mediante PBKDF2-HMAC-SHA-256;
   Desktop utiliza 600.000 iteraciones y Android acepta entre 100.000 y
   2.000.000. La contraseña de exportación tiene al menos 8 caracteres.

El esquema no expresa la igualdad del conteo ni las reglas del contenedor: las
pruebas deben verificarlas por separado. Los cambios incompatibles requieren
una versión nueva sin eliminar la lectura de las versiones anteriores.

Las pruebas comparten archivos de referencia en `test-fixtures`. En CI se
regeneran con el exportador actual de Desktop antes de que Android los lea en
sus dos variantes. Para regenerarlos localmente, desde `eduescale-desktop`:

```powershell
uv run --locked python scripts/generate_format_contract_fixtures.py
```

Para impedir fusiones incompatibles, configura como comprobaciones obligatorias
los trabajos **Desktop CI / windows** y **Mobile CI / android** en la protección
de la rama principal; el hook local de pre-commit no sustituye esta protección.
