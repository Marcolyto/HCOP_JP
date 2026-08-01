# Evidencia de corte: Catalogo de Protocolos Angular

Fecha: 2026-07-31

La ruta `/protocols` consulta `GET /api/clinical/protocols?includeCatalog=1`
y abre el detalle estructurado de cada esquema. Expone categoria, ciclo,
duracion y componentes/drogas para que la misma informacion que habilita la
prescripcion sea visible en Angular.

El catalogo COIR permanece de solo lectura. La creacion, edicion versionada y
archivo de protocolos locales se mantienen como el siguiente corte de
Configuracion; no se habilitan mutaciones incompletas desde esta pantalla.

Verificacion:

```powershell
docker build --target frontend-build --file Dockerfile --tag hcop-jp:protocols-check .
```

Resultado: compilacion Angular correcta y carga diferida del modulo.
