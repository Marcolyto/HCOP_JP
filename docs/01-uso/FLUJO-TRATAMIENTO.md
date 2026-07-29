# Flujo de tratamiento y Hospital de Día

## 1. Diagnóstico

El paciente debe tener un diagnóstico guardado. Puede incluir SNOMED, CIE-10,
AJCC, TNM y estadio. El tratamiento queda vinculado al identificador de ese
diagnóstico, no a un texto suelto.

## 2. Prescripción

**Nuevo tratamiento** solicita:

- diagnóstico;
- tipo e intención;
- protocolo/esquema;
- número y periodicidad de ciclos;
- fecha del primer ciclo;
- consentimiento;
- peso y talla en centímetros;
- requisitos particulares del esquema.

El sistema compara de forma conservadora el grupo clínico reconocible del
diagnóstico con el del protocolo. Si la discordancia es evidente, muestra una
advertencia y exige confirmar la excepción con un motivo clínico de al menos
diez caracteres. No impide usos excepcionales u off-label: los vuelve
explícitos y agrega el motivo a la evolución inmutable.

El estado **Firmado · documento pendiente** permite registrar que el
consentimiento fue informado como firmado sin afirmar que ya existe un archivo.
El icono de descarga sólo se habilita cuando el documento está realmente
guardado.

El selector usa el catálogo clínico completo, sin recortar los primeros
resultados. La misma fuente alimenta **Configuración → Protocolos**, por lo que
los esquemas por sitio —incluidos Mama y las categorías alfabéticamente
posteriores— deben aparecer en ambas pantallas. Los protocolos personalizados
activos se integran al catálogo inmediatamente.

Al confirmar se crean, en una sola transacción:

- el tratamiento;
- su detalle;
- cada ciclo con fecha planificada;
- la logística de farmacia/prescripción;
- una evolución clínica inmutable.

## 3. Farmacia

Cada ciclo puede marcarse como:

- medicación pendiente;
- medicación recibida;
- la tiene el paciente.

La prescripción puede estar confirmada, requerida, solicitada o rechazada. El
QR se genera con firma criptográfica y no confía en un identificador libre.

## 4. Turnero por sillón

Los ciclos no turnados aparecen ordenados por fecha planificada. Al asignar:

- la duración proviene del protocolo;
- el bloque ocupa todas las fracciones necesarias;
- PostgreSQL toma un bloqueo por sillón y fecha;
- una restricción rechaza cualquier superposición, incluso con dos usuarios al
  mismo tiempo.

Cancelar el turno libera las celdas y devuelve el ciclo a pendientes.

## 5. Administración

El QR abre exactamente el paciente, tratamiento, ciclo y turno firmados. El
escaneo se registra una sola vez por identificador de operación.

La finalización exige confirmación y observación, cambia los estados clínico y
de administración y agrega una evolución inmutable.

## 6. Suspensión y continuidad

- **Suspensión transitoria**: retira ciclos desde el ciclo efectivo y puede
  exigir una nueva prescripción antes de reanudar.
- **Suspensión definitiva**: cierra la continuidad y no se reanuda.
- **Solicitar prescripción/continuidad**: crea una tarea para un usuario con el
  permiso adecuado.
- **Reanudar**: define ciclo y nueva fecha, y documenta quién autorizó.

Toda decisión deja evento de flujo, auditoría y evolución.
