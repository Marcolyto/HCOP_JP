# Corte Angular: configuracion de Inteligencia Artificial

## Alcance

Se incorpora una portada Angular de Configuracion y la ruta
`/configuration/llm`. Esta pantalla permite:

- leer el estado actual del servicio LLM;
- activar o desactivar la integracion;
- configurar proveedor, modelo, endpoint, temperatura, limite de tokens y
  tiempo de espera;
- aplicar configuraciones rapidas para Ollama, LM Studio y Gemini;
- conservar, reemplazar o quitar una clave privada sin mostrar su valor;
- probar un borrador antes de guardarlo.

La configuracion se delega en el servidor. El navegador no guarda claves en
estado persistente ni recibe la clave existente desde el endpoint.

## Contratos

| Operacion | Endpoint | Permiso |
| --- | --- | --- |
| Leer configuracion | `GET /api/config` | `section.configuration.view` |
| Guardar configuracion | `PUT /api/config` | `section.configuration.manage` |
| Probar borrador | `POST /api/llm/test` | `section.configuration.manage` |

## Verificacion

El frontend Angular compiló correctamente como chunk diferido
`llm-settings-page-component`.

En PostgreSQL aislado se inició sesión como administrador, se leyó la
configuracion, se guardó Ollama local (`http://127.0.0.1:11434`,
`llama3.2`) con el servicio desactivado y se verificó:

- proveedor y modelo persistieron;
- `enabled` continuó en `false`;
- la respuesta no expuso la propiedad `apiKey`.

No se contactó a ningún proveedor externo y la base temporal fue eliminada
después de la prueba.
