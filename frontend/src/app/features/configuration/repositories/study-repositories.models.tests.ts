import {
  normalizeRepositories, repositoryChanged, repositoryDraft, repositoryUpdate, repositoryValidation
} from './study-repositories.models';

let assertions = 0;
function equal(actual: unknown, expected: unknown, message: string): void {
  assertions += 1;
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(message);
}

const repositories = normalizeRepositories({ ok: true, items: [{
  id: 'synthetic', name: 'Sitio de prueba', requiresCredentials: true, username: 'usuario-prueba', hasPassword: true,
  password: 'server-must-not-send-this',
  endpoints: [{ key: 'BASE_URL', label: 'Sitio principal', url: 'https://institution.example/viewer' },
    { key: 'SOCKET_URL', label: 'Canal de consulta', url: 'wss://institution.example/live/websocket' }]
}] });
const repository = repositories[0]!;
equal(JSON.stringify(repository).includes('server-must-not-send-this'), false, 'no conserva campos de contraseña recibidos por error');
const draft = repositoryDraft(repository);
equal(draft.password, '', 'editar comienza sin una contraseña recuperada');
equal(repositoryChanged(repository, draft), false, 'abrir un sitio no crea cambios');
equal(repositoryValidation(draft), '', 'acepta endpoints HTTPS y canal WSS');
draft.password = 'unsubmitted-draft';
equal(Object.hasOwn(repositoryUpdate(draft), 'password'), false, 'conservar la contraseña no envía secretos residuales');
equal(repositoryChanged(repository, draft), false, 'un campo no enviado no cambia el estado persistido');
draft.passwordAction = 'remove';
equal(repositoryChanged(repository, draft), true, 'eliminar la contraseña es un cambio explícito');
equal(Object.hasOwn(repositoryUpdate(draft), 'password'), false, 'eliminar no envía una contraseña');
draft.passwordAction = 'replace';
draft.password = '';
equal(repositoryValidation(draft), 'Ingrese la nueva contraseña.', 'impide reemplazar por una contraseña vacía');
draft.password = 'synthetic-new-secret';
equal(repositoryUpdate(draft).password, 'synthetic-new-secret', 'envía sólo la nueva contraseña solicitada');
draft.endpoints[0]!.url = 'https://changed.example/viewer';
equal(repository.endpoints[0]!.url, 'https://institution.example/viewer', 'editar no modifica la configuración cargada');
equal(repositoryUpdate(draft).endpoints[0], { key: 'BASE_URL', url: 'https://changed.example/viewer' }, 'el payload usa claves y URLs sin etiquetas de presentación');
for (const invalid of ['javascript:alert(1)', 'https://user:password@institution.example', 'https://institution.example/#secret', 'https://institution.example/path with spaces']) {
  draft.endpoints[0]!.url = invalid;
  equal(Boolean(repositoryValidation(draft)), true, 'rechaza direcciones inseguras o ambiguas');
}
draft.endpoints[0]!.url = 'https://institution.example/viewer';
draft.endpoints[1]!.url = 'https://institution.example/live';
equal(Boolean(repositoryValidation(draft)), true, 'el canal requiere un endpoint WebSocket');
equal(repositoryDraft(repository).passwordAction, 'keep', 'descartar vuelve a conservar la contraseña guardada');

console.log(`Repositorio de estudios: ${assertions} aserciones aprobadas.`);
