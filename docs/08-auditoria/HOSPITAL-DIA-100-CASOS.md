# Matriz de 100 casos de Hospital de día

La matriz cubre el circuito completo desde cuatro miradas operativas. Los identificadores coinciden con `scripts/qa/hospital-day-100-cases.ps1`. El script aborta si no existen exactamente 25 casos por rol.

## Farmacia

| ID | Modo | Caso | Riesgo principal |
|---|---|---|---|
| FAR-01 | REAL | Abrir cola completa | Error 500 o contrato roto. |
| FAR-02 | REAL | Buscar por nombre | Paciente perdido en listados extensos. |
| FAR-03 | REAL | Buscar por DNI | Homónimos o identidad ambigua. |
| FAR-04 | REAL | Buscar por historia clínica | Error de columna o HC fuera de búsqueda. |
| FAR-05 | REAL | Buscar por esquema | Demanda difícil de agrupar. |
| FAR-06 | REAL | Buscar por diagnóstico | Filtro clínico incompleto. |
| FAR-07 | REAL | Buscar por droga | Medicación difícil de localizar. |
| FAR-08 | REAL | Buscar por ciclo y día | Confundir ciclo con aplicación. |
| FAR-09 | REAL | Buscar por fecha ISO | Fallo del formato interno. |
| FAR-10 | REAL | Buscar por fecha local | `dd/mm/aaaa` no encuentra la fila. |
| FAR-11 | REAL | Filtrar quién debe traer medicación | Custodia del paciente mezclada con stock. |
| FAR-12 | CONTRACT | Prioridad temporal | Lista anual sin foco operativo. |
| FAR-13 | CONTRACT | Filtros de estado | Pendientes, rechazados y reservas mezclados. |
| FAR-14 | CONTRACT | Agrupar por fecha | Organización diaria deficiente. |
| FAR-15 | CONTRACT | Rechazar orden pendiente | Orden incorrecta sin salida. |
| FAR-16 | CONTRACT | Validar/revalidar | Corrección sin retorno al circuito. |
| FAR-17 | CONTRACT | Procedencias inequívocas | Disponibilidad ficticia. |
| FAR-18 | CONTRACT | Reservar/liberar stock | Reserva manual sin cantidad/unidad o presentada como bloqueo atómico. |
| FAR-19 | REAL | Datos esenciales de la fila | Falta droga, fuente, estado o fecha. |
| FAR-20 | CONTRACT | Aprobación heredada sin traza | Migración tomada como validación humana. |
| FAR-21 | CONTRACT | Historial auditable | Cambio sin actor, hora o revisión. |
| FAR-22 | CONTRACT | Carga incremental | Tope silencioso o bloqueo con 2000 filas. |
| FAR-23 | CONTRACT | QR por aplicación | QR sin ciclo/día real. |
| FAR-24 | MANUAL | Buscar entre 2000 filas | Rendimiento y comprensión real. |
| FAR-25 | CONTRACT | Reserva concurrente automatizada | Sobre-reserva del mismo lote. |

Estos casos incorporan los hallazgos de Farmacia: búsqueda por HC rota, horizonte temporal excesivo, falta de rechazo inicial, aprobaciones migradas sin actor/fecha, filtros incompletos, tope de filas y ausencia de una prueba concurrente de stock.

## Enfermería

| ID | Modo | Caso | Riesgo principal |
|---|---|---|---|
| ENF-01 | REAL | Abrir triaje de hoy | Cola de otra fecha o indisponible. |
| ENF-02 | REAL | Buscar en triaje | Demora ante muchos turnos. |
| ENF-03 | REAL | Orden cronológico | Atender fuera de hora. |
| ENF-04 | REAL | Abrir preparación | Flujo estéril desconectado. |
| ENF-05 | REAL | Abrir administración | Sala sin lista operativa. |
| ENF-06 | CONTRACT | Buscador de sala | Paciente o sillón difícil de hallar. |
| ENF-07 | CONTRACT | Buscador de triaje | Listado diario poco usable. |
| ENF-08 | CONTRACT | Filtro de triaje | Aptos y pendientes mezclados. |
| ENF-09 | REAL | Turno listo para hoy | PASS sobre turno futuro/no confirmado. |
| ENF-10 | CONTRACT | Laboratorio | PASS sin analítica básica. |
| ENF-11 | CONTRACT | Signos vitales | Evaluación clínica incompleta. |
| ENF-12 | CONTRACT | Frecuencia cardíaca | Campo de servidor ausente en UI. |
| ENF-13 | CONTRACT | Saturación | Hipoxemia no registrada. |
| ENF-14 | CONTRACT | Toxicidad y ECOG | Tolerancia sin estructura. |
| ENF-15 | CONTRACT | Alertas clínicas | Valores críticos sin advertencia. |
| ENF-16 | CONTRACT | Override documentado | Excepción clínica sin fundamento. |
| ENF-17 | CONTRACT | FAIL con motivo/fecha | Postergación sin causa ni plan. |
| ENF-18 | CONTRACT | Revocar PASS | Error humano irreversible antes de preparar. |
| ENF-19 | CONTRACT | Trazabilidad de mezcla | Falta lote, vencimiento, concentración o TTL. |
| ENF-20 | CONTRACT | Segundo control de preparación | Autoverificación. |
| ENF-21 | CONTRACT | Reinicio por vencimiento | Circuito bloqueado o descarte perdido. |
| ENF-22 | CONTRACT | Doble chequeo a pie de cama | Error de paciente/etiqueta/profesional. |
| ENF-23 | CONTRACT | Inicio y cierre reales | Cierre sin dosis o tolerancia real. |
| ENF-24 | CONTRACT | QR de identidad | Escaneo sin aplicación concreta. |
| ENF-25 | CONTRACT | Interrumpir, reanudar y completar una aplicación multidroga | Reacción sin dosis parcial ni decisión clínica; pérdida del evento al cierre. |

Estos casos incorporan los hallazgos de Enfermería: triaje sobre turnos futuros, límites solo técnicos, frecuencia cardíaca ausente, PASS difícil de corregir, reinicio por TTL bloqueado y falta de manejo estructurado por droga ante una reacción.

## Oncología

| ID | Modo | Caso | Riesgo principal |
|---|---|---|---|
| ONC-01 | CONTRACT | Nuevo tratamiento primero | Punto de entrada confuso. |
| ONC-02 | CONTRACT | Contexto de paciente | Prescribir al paciente equivocado. |
| ONC-03 | CONTRACT | Diagnóstico obligatorio | Plan sin indicación vinculada. |
| ONC-04 | CONTRACT | Carácter terapéutico | Intención clínica ausente. |
| ONC-05 | CONTRACT | Tipo oncológico | Modalidad ambigua. |
| ONC-06 | CONTRACT | Selector de esquema | Estadio mostrado donde corresponde protocolo. |
| ONC-07 | REAL | Catálogo de protocolos | Protocolos faltantes o servicio caído. |
| ONC-08 | REAL | Esquemas prescribibles | Catálogo local inaccesible. |
| ONC-09 | CONTRACT | Opciones por paciente | Diagnóstico/protocolo fuera de contexto. |
| ONC-10 | CONTRACT | Requisitos por esquema | Datos previos omitidos. |
| ONC-11 | CONTRACT | Excepción diagnóstica | Indicación deliberada no documentada. |
| ONC-12 | CONTRACT | Cantidad de ciclos | Valor nulo o desproporcionado. |
| ONC-13 | CONTRACT | Ciclo inicial | Reanudación vuelve por error a ciclo 1. |
| ONC-14 | CONTRACT | Fecha inicial | Cronograma sin ancla. |
| ONC-15 | CONTRACT | Proyección previa | Intervalo erróneo detectado tarde. |
| ONC-16 | CONTRACT | Consentimiento | Estado documental ambiguo. |
| ONC-17 | CONTRACT | Confirmar requisitos | Guardado accidental incompleto. |
| ONC-18 | REAL | Drogas por día | Ciclo sin componentes. |
| ONC-19 | REAL | Ciclo, día y duración | Tratar un ciclo como una aplicación única. |
| ONC-20 | CONTRACT | Alta/listado en Swagger | API principal indocumentada. |
| ONC-21 | CONTRACT | Detalle ciclo-día-aplicación | Árbol clínico incompleto. |
| ONC-22 | CONTRACT | Suspensión | Tratamiento suspendido sigue operativo. |
| ONC-23 | CONTRACT | Reanudación | Nueva receta rompe continuidad. |
| ONC-24 | CONTRACT | Evolución automática | Prescripción fuera de la HC. |
| ONC-25 | CONTRACT | Documentos | Hoja, QR o consentimiento inaccesible. |

## Turnos

| ID | Modo | Caso | Riesgo principal |
|---|---|---|---|
| TUR-01 | REAL | Abrir candidatos | Lista de espera indisponible. |
| TUR-02 | REAL | Buscar candidato | Paciente difícil de encontrar. |
| TUR-03 | REAL | Excluir programados | Asignado continúa en espera. |
| TUR-04 | REAL | Mostrar bloqueados | No se explica por qué no agenda. |
| TUR-05 | REAL | Agenda del día | Turnos de fecha incorrecta. |
| TUR-06 | CONTRACT | Filtros operativos | Receta y medicación indistinguibles. |
| TUR-07 | CONTRACT | Prioridad cronológica | Próximo ciclo queda al fondo. |
| TUR-08 | CONTRACT | Fecha local y día | Interpretación equivocada. |
| TUR-09 | CONTRACT | Calendario/navegación | Cambio de día lento. |
| TUR-10 | REAL | Configuración activa | Grilla ignora PostgreSQL. |
| TUR-11 | CONTRACT | Fracciones 5/10/15/20/30 | Cambiar intervalo no cambia grilla. |
| TUR-12 | CONTRACT | Sillones/jornada | Capacidad rígida. |
| TUR-13 | CONTRACT | Zoom | Control presente pero inerte. |
| TUR-14 | CONTRACT | Navegar sillones | Más de seis inaccesibles. |
| TUR-15 | CONTRACT | Drag/drop coherente | Evento visual y destino separados. |
| TUR-16 | CONTRACT | Sombra válida | Feedback verde engañoso. |
| TUR-17 | CONTRACT | Sin superposición | Dos turnos ocupan la misma celda. |
| TUR-18 | CONTRACT | Duración completa | Bloque más corto que tratamiento. |
| TUR-19 | CONTRACT | Franja visible | Operador no ve inicio/fin. |
| TUR-20 | CONTRACT | Confirmación distinguible | Azul/rojo sin semántica. |
| TUR-21 | CONTRACT | Mover turno | Reprogramar duplica. |
| TUR-22 | CONTRACT | Quitar y devolver | Cancelado queda huérfano. |
| TUR-23 | CONTRACT | Modal completo | Decisión con datos parciales. |
| TUR-24 | CONTRACT | Alta en Swagger | API de agenda indocumentada. |
| TUR-25 | CONTRACT | Drop rápido, bordes y concurrencia automatizados | Delay, celda errónea o doble asignación. |

## Criterio de aprobación

La ronda queda cerrada cuando:

1. existen exactamente 100 resultados;
2. cada rol conserva exactamente 25;
3. no hay `FAIL`;
4. los `NO_DATA` se repiten luego de sembrar QA;
5. las pruebas de carga y concurrencia automatizadas están presentes;
6. ninguna ejecución apuntó al puerto `5180`.

Los controles `CONTRACT` verifican presencia y conexión básica y, cuando el
riesgo requiere modificar estado, exigen que exista una prueba automatizada
específica. La carga de 2000 filas se prueba contra la semilla QA; la
concurrencia de stock y agenda se valida en Java/PostgreSQL; la interrupción y
reacción multidroga se recorre en la prueba E2E separada.

La última ejecución reproducible obtuvo
[100 PASS, 0 FAIL, 0 NO_DATA y 0 MANUAL](resultados/hospital-dia-100-casos-20260730-100711.md).
