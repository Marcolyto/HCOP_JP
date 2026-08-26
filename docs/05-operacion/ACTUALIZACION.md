# Actualización

Use **Actualizar HCOP JP.bat** en la carpeta de instalación. El acceso directo
principal **HCOP JP** sólo inicia la versión estable y no descarga cambios.

## Flujo seguro

La actualización:

1. comprueba WSL 2, Docker Engine y Docker Compose;
2. comprueba el puerto configurado;
3. descarga el commit actual de `main` en una carpeta candidata;
4. intenta la imagen inmutable `sha-<commit>` publicada por GitHub;
5. si no existe, construye el código de ese mismo commit;
6. inicia la candidata sin modificar `current.txt`;
7. verifica `/actuator/health`, la interfaz y `/api/runtime/status`;
8. sólo entonces mueve la candidata a `current.txt`;
9. conserva la versión anterior en `previous.txt`;
10. elimina versiones temporales que ya no hacen falta.

La etiqueta `latest` no se usa para decidir qué versión ejecutar, porque podría
estar atrasada respecto del código descargado.

## Fallo y rollback

Si la candidata no arranca o falla una comprobación:

- `current.txt` permanece sin cambios;
- se muestran el estado y los últimos registros de Docker;
- el instalador vuelve a iniciar la versión estable anterior;
- el error completo queda en `logs`.

**Reparar HCOP JP.bat** intenta primero la estable, luego `previous.txt` y sólo
descarga otra copia cuando ninguna versión local es utilizable.

El rollback automático recupera la aplicación. Flyway no ejecuta migraciones
hacia atrás; por eso, antes de una actualización clínica importante, mantenga
además una copia de seguridad reciente de PostgreSQL y de los archivos.

## Inicio sin conexión

**Iniciar HCOP JP.bat** usa exclusivamente la versión indicada por
`current.txt`. Puede iniciar sin conexión a GitHub siempre que Docker Desktop y
las imágenes necesarias ya estén en el equipo.

## Actualización desde un checkout de desarrollo

Un solo script por plataforma en la raíz del repositorio, con el mismo
diagnóstico centralizado (Windows delega en
`scripts\instalar-desde-github.ps1`; macOS/Linux usa `docker compose`
directo — ver [Docker](DOCKER.md)):

```powershell
# Windows
.\iniciar.bat
.\iniciar.bat reiniciar
.\iniciar.bat detener
```

```bash
# macOS / Linux
./iniciar.sh
./iniciar.sh reiniciar
./iniciar.sh detener
```

Ambos construyen/levantan `compose.yaml`, respetan el puerto de `.env`,
esperan la salud del producto y muestran diagnóstico ante fallos.

No edite archivos dentro de un contenedor. Los cambios se pierden al reemplazar
la imagen.
