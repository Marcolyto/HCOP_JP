INSERT INTO local_permissions (permission_key, display_name, description) VALUES
  ('section.history.view', 'Ver historia clinica', 'Acceder a la hoja clinica del paciente.'),
  ('section.history.edit', 'Editar historia clinica', 'Agregar registros clinicos al documento.'),
  ('section.studies.view', 'Ver estudios', 'Consultar estudios y adjuntos.'),
  ('section.studies.edit', 'Gestionar estudios', 'Agregar y administrar estudios.'),
  ('section.day-hospital.view', 'Ver Hospital de dia', 'Consultar tratamientos, farmacia y turnos.'),
  ('section.day-hospital.edit', 'Gestionar Hospital de dia', 'Modificar el circuito operativo.'),
  ('section.prescriptions.view', 'Ver prescripciones', 'Consultar prescripciones.'),
  ('section.prescriptions.edit', 'Gestionar prescripciones', 'Crear y confirmar prescripciones.'),
  ('section.agent.view', 'Usar Agente', 'Acceder al asistente clinico.'),
  ('section.research.view', 'Ver investigacion', 'Consultar formularios de investigacion.'),
  ('section.research.edit', 'Gestionar investigacion', 'Completar formularios de investigacion.'),
  ('section.timeline.view', 'Ver linea del tiempo', 'Consultar la cronologia clinica.'),
  ('section.protocols.view', 'Ver protocolos', 'Consultar protocolos y esquemas.'),
  ('section.protocols.edit', 'Gestionar protocolos', 'Crear y modificar protocolos.'),
  ('section.tools.view', 'Ver herramientas', 'Acceder a calculadoras y herramientas.'),
  ('section.tools.use', 'Usar herramientas', 'Ejecutar calculadoras y scores.'),
  ('section.configuration.view', 'Ver configuracion', 'Consultar la configuracion del sistema.'),
  ('section.configuration.manage', 'Gestionar configuracion', 'Modificar la configuracion del sistema.'),
  ('workflow.suspend', 'Suspender tratamiento', 'Suspender transitoria o definitivamente un tratamiento.'),
  ('workflow.resume', 'Reactivar tratamiento', 'Reactivar un tratamiento suspendido.'),
  ('workflow.request-prescription', 'Solicitar prescripcion', 'Enviar una solicitud de prescripcion.'),
  ('workflow.request-continuity', 'Solicitar continuidad', 'Enviar una solicitud de continuidad.'),
  ('workflow.resolve-prescription', 'Resolver prescripcion', 'Responder solicitudes de prescripcion.'),
  ('workflow.resolve-continuity', 'Resolver continuidad', 'Responder solicitudes de continuidad.'),
  ('admin.manage-users', 'Administrar usuarios', 'Crear, modificar y desactivar usuarios.'),
  ('admin.manage-roles', 'Administrar roles', 'Crear roles y definir sus permisos.'),
  ('admin.manage-security', 'Administrar acceso', 'Configurar login y acceso automatico.')
ON CONFLICT (permission_key) DO UPDATE SET
  display_name = EXCLUDED.display_name,
  description = EXCLUDED.description;

INSERT INTO local_roles (role_key, display_name, description, system_role) VALUES
  ('administrator', 'Administrador', 'Acceso completo al sistema.', true),
  ('oncologist', 'Medico oncologo', 'Atencion oncologica, prescripcion y continuidad.', true),
  ('nursing', 'Enfermeria', 'Historia clinica y operacion de Hospital de dia.', true),
  ('pharmacy', 'Farmacia', 'Recepcion, preparacion y entrega de medicacion.', true),
  ('admissions', 'Admision', 'Admision, turnos y consulta clinica.', true)
ON CONFLICT (role_key) DO UPDATE SET
  display_name = EXCLUDED.display_name,
  description = EXCLUDED.description,
  system_role = true;

INSERT INTO local_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM local_roles r CROSS JOIN local_permissions p
WHERE r.role_key = 'administrator'
ON CONFLICT DO NOTHING;

INSERT INTO local_security_settings (id, login_required, session_duration_minutes)
VALUES (1, true, 43200)
ON CONFLICT (id) DO NOTHING;
