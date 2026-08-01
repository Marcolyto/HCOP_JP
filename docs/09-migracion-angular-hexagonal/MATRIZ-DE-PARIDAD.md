# Matriz de paridad funcional

Esta matriz es el contrato de aceptación de la migración. Una capacidad sólo
puede marcarse como completada cuando conserva comportamiento, permisos,
persistencia, respuesta ante errores y apariencia clínica.

Estados posibles: `Pendiente`, `En convivencia`, `Validada` y `Retirada` para
la implementación anterior.

| Capacidad | Entrada vigente | Autoridad / API principal | Criterio de paridad | Angular | Backend hexagonal |
|---|---|---|---|---|---|
| Login y sesión | Pantalla de acceso | `/api/auth/**`, usuarios y sesiones | Login, cierre, expiración, cookie segura, recuperación de contexto y errores equivalentes | En convivencia | En convivencia (caso de uso, puertos y adaptadores de hash/sesión) |
| Usuario y permisos | Cabecera y Configuración | `/api/admin/**`, RBAC | Misma visibilidad y prohibición efectiva por rol; el servidor continúa siendo la barrera final | En convivencia (usuarios, roles, permisos y sesiones) | Pendiente |
| Paciente activo | Abrir, nuevo y cerrar paciente | `/api/clinical/patients/**`, `/api/auth/active-patient` | Mantener paciente al navegar/recargar y liberarlo sólo mediante cierre explícito | En convivencia | En convivencia (caso de uso, puertos y adaptador PostgreSQL) |
| Hoja clínica | Panel izquierdo | `/api/hc`, documento JSON versionado | Orden, formularios, evoluciones, edición, impresión y conflicto `409` sin pérdida | En convivencia (lectura y edición narrativa base) | Pendiente |
| Diagnóstico | Modal desde la hoja | Diagnósticos, SNOMED, CIE-10 y AJCC | Selección obligatoria, TNM/estadio, varios diagnósticos y evolución resultante | En convivencia (alta con TNM y tres clasificaciones) | Pendiente |
| Estudios | Solapa Estudios | `/api/media/**`, `clinical_files`, `/api/study-templates` | Carga múltiple, pegado, plantillas, orden, edición, dibujo, descarga y eliminación de sesión | En convivencia (lista, filtros, carga múltiple, pegado, plantillas y eliminación de carga propia) | Pendiente |
| Prescripción general | Solapa Prescripción | APIs de prescripción y hoja | Medicamentos, prácticas, estudios y documentos con datos de cobertura completos | Pendiente | Pendiente |
| Protocolos | Solapa y Configuración | `/api/clinical/protocols/**` | Búsqueda, detalle, drogas, dosis, preparación, tiempos, edición y versionado | En convivencia (catálogo, detalle y editor local versionado) | En convivencia |
| Nuevo tratamiento | Hospital de Día | `/api/clinical/patients/{id}/treatments` | Diagnóstico existente, protocolo, antropometría, dosis, requisitos, ciclos y evolución atómica | Pendiente | Pendiente |
| Tratamiento | Lista y detalle | APIs de tratamientos | Tarjetas, documentos, estado, consentimiento, drogas y árbol ciclo–día–aplicación | En convivencia (lista, filtro, estados, drogas y árbol de lectura) | Pendiente |
| Farmacia | Hospital de Día | Cola `pharmacy` y comandos farmacéuticos | Búsqueda, filtros, auditoría, procedencia, recepción, reserva, liberación, rechazo y QR | En convivencia (cola y comandos operativos) | Pendiente |
| Agenda | Turnos y sala | `/api/clinical/infusions/**` | Fecha, sillones, zoom, arrastrar, mover, quitar, duración, colores y cero superposiciones | En convivencia (lista de espera, grilla y arrastre) | Pendiente |
| Sala de hoy | Turnos y sala | Cola `administration` | Orden por hora/sillón, búsqueda, estados, doble control y apertura de la aplicación exacta | En convivencia (cola y acciones de administración) | Pendiente |
| Triaje | Hospital de Día | `clinical-authorization` | Laboratorio, signos, toxicidad, PASS/FAIL, justificación, postergación y liberación asociada | En convivencia (cola diaria y decisión PASS/FAIL) | Pendiente |
| Preparación | Hospital de Día | Comandos `preparation/**` | Inicio, componentes, lotes, TTL, etiqueta, liberación, vencimiento y repetición trazable | En convivencia (cola y trazabilidad operativa) | Pendiente |
| Administración | QR y Sala | Comandos `administration/**` | Escaneo, identidad, doble chequeo, inicio, dosis real, reacción, interrupción, reanudación y cierre | En convivencia (cola, doble chequeo y cierre) | Pendiente |
| Suspensión y continuidad | Tratamiento y espera | `/api/clinical/treatments/**/workflow/**` | Solicitudes, responsables, motivos, suspensión temporal/definitiva, nueva prescripción y evolución | Pendiente | Pendiente |
| Configuración H. de Día | Configuración | Ajustes de sillones | Sillones, fracción 5/10/15/20/30, jornada y efecto inmediato controlado sobre agenda | En convivencia (edición e historial versionado) | En convivencia |
| Guías | Configuración y Herramientas | `/api/guides/**` | Carga, metadatos, activación, búsqueda, apertura y conservación del archivo | En convivencia (biblioteca y edición de ficha) | En convivencia |
| Calculadoras | Configuración y Herramientas | Configuración versionada | Crear fórmula/score sin programar, variables, reglas, rangos, vista previa, activar y ejecutar | Pendiente | En convivencia |
| Investigación | Configuración e Investigación | Formularios versionados y hoja | Constructor, orden, etiquetas, tipos, obligatoriedad, versión aplicada y recuperación de respuestas | Pendiente | En convivencia |
| Plantillas anatómicas | Configuración y Estudios | `/api/study-templates/**` | Catálogo, miniaturas, derechos, alta, baja, selección, marcado e incorporación al estudio | En convivencia (consulta, búsqueda, miniaturas e incorporación desde Estudios) | Pendiente |
| Agente y línea temporal | Solapas derechas | `/api/llm/**` y hoja | Configuración local/remota, errores claros, paciente activo, resaltado y ausencia de pérdida de foco | En convivencia (configuración LLM y prueba de conexión) | Pendiente |
| Ayuda y documentación | Cabecera y páginas de ayuda | Recursos estáticos | Índice, manuales, diagramas, videos y enlaces accesibles desde la interfaz final | Pendiente | No aplica |
| Instalación y actualización | Lanzador y Docker | GHCR, Compose, Flyway | Primer inicio, actualización conservando datos, respaldo, restauración, healthcheck y un solo comando | Pendiente | Pendiente |

## Corte verificado: alta de tratamientos Angular

La capacidad **Nuevo tratamiento** queda en `En convivencia`: la ruta
`#/patients/{id}/treatments/new` recupera diagnósticos y protocolos desde
`GET /api/clinical/patients/{id}/treatment-options`, consulta los requisitos
dinámicos con `GET /api/clinical/patients/{id}/treatment-requirements/{schemeId}`
y crea la prescripción mediante `POST /api/clinical/patients/{id}/treatments`.
El formulario exige los datos de dosis que correspondan al esquema, documenta
una excepción diagnóstico–protocolo y conserva la evolución atómica generada
por el backend. El árbol de ciclos y drogas se verifica en la pantalla de
tratamientos inmediatamente después del alta.

## Evidencia requerida para marcar `Validada`

1. Prueba automática de la regla o del contrato.
2. Prueba E2E del recorrido principal y al menos un error relevante.
3. Comparación visual en las resoluciones admitidas.
4. Comprobación con usuario autorizado y usuario sin permiso.
5. Verificación de persistencia y recuperación tras reiniciar.
6. OpenAPI y documentación actualizados.
7. Resultado satisfactorio en la instalación Docker aislada.
