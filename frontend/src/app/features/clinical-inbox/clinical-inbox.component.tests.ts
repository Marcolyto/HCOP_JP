import '@angular/compiler';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Injector, runInInjectionContext, signal,
  ɵChangeDetectionScheduler, ɵEffectScheduler, ɵINJECTOR_SCOPE
} from '@angular/core';
import { Observable, Subject, of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import type { AuthSession } from '../../core/auth/auth.models';
import { ClinicalInboxComponent } from './clinical-inbox.component';
import type { ClinicalInboxItem, ClinicalInboxPage } from './clinical-inbox.models';
import { ClinicalInboxService } from './clinical-inbox.service';

interface TestCase { readonly name: string; readonly run: () => Promise<void>; }
const tests: TestCase[] = [];
let assertions = 0;

function test(name: string, run: () => Promise<void>): void { tests.push({ name, run }); }
function equal(actual: unknown, expected: unknown, message: string): void {
  assertions++;
  if (!Object.is(actual, expected)) throw new Error(`${message}: esperado ${String(expected)}, recibido ${String(actual)}.`);
}
function truthy(value: unknown, message: string): asserts value {
  assertions++;
  if (!value) throw new Error(message);
}

function session(id = 'inbox-user-a', permissions = ['workflow.resolve-prescription']): AuthSession {
  return { ok: true, authenticated: true, loginRequired: false, activePatientId: null,
    user: { id, username: id, roles: ['clinician'], permissions } };
}

class FakeAuth {
  readonly session = signal<AuthSession | null>(session());
  hasPermission(permission: string): boolean { return Boolean(this.session()?.user?.permissions.includes(permission)); }
  expireSession(): void {
    this.session.set({ ok: false, authenticated: false, loginRequired: true, activePatientId: null });
  }
}

class FakeInbox {
  readonly requests: Subject<ClinicalInboxPage>[] = [];
  load(): Observable<ClinicalInboxPage> {
    const response = new Subject<ClinicalInboxPage>();
    this.requests.push(response);
    return response.asObservable();
  }
  markSeen(): Observable<{ ok: boolean }> { return of({ ok: true }); }
  resolve(): Observable<never> { throw new Error('Estas pruebas no deben registrar decisiones clínicas.'); }
  succeed(index: number, items: ClinicalInboxItem[] = []): void {
    this.request(index).next({ ok: true, items, total: items.length });
    this.request(index).complete();
  }
  fail(index: number, status = 503): void {
    this.request(index).error(new HttpErrorResponse({ status, statusText: 'Test failure',
      url: '/api/clinical/treatment-workflow-requests/inbox', error: { error: 'Consulta temporalmente no disponible.' } }));
  }
  private request(index: number): Subject<ClinicalInboxPage> {
    const request = this.requests[index];
    if (!request) throw new Error(`No existe la consulta ${index}.`);
    return request;
  }
}

interface Timer { readonly callback: () => void; readonly interval: number; due: number; }
class FakeClock {
  private nextId = 0;
  private now = 0;
  readonly timers = new Map<number, Timer>();
  readonly window = {
    setInterval: (callback: () => void, interval: number): number => this.add(callback, interval, interval),
    clearInterval: (id: number): void => { this.timers.delete(id); },
    setTimeout: (callback: () => void, delay = 0): number => this.add(callback, delay, 0),
    clearTimeout: (id: number): void => { this.timers.delete(id); }
  };
  advance(milliseconds: number): void {
    const until = this.now + milliseconds;
    let calls = 0;
    for (;;) {
      const next = [...this.timers.entries()].filter(([, timer]) => timer.due <= until)
        .sort((left, right) => left[1].due - right[1].due || left[0] - right[0])[0];
      if (!next) break;
      if (++calls > 100) throw new Error('El buzón programó un ciclo de temporizadores sin límite.');
      const [id, timer] = next;
      this.now = timer.due;
      if (timer.interval) timer.due += timer.interval;
      else this.timers.delete(id);
      timer.callback();
    }
    this.now = until;
  }
  private add(callback: () => void, delay: number, interval: number): number {
    const id = ++this.nextId;
    this.timers.set(id, { callback, interval, due: this.now + delay });
    return id;
  }
}

function item(id: string): ClinicalInboxItem {
  return { id, type: 'prescription_request', status: 'pending', patientId: 'synthetic-patient',
    treatmentId: 'synthetic-treatment', cycleNumber: 1, message: '', context: {}, resolution: '',
    resolutionReason: '', resumeDate: null, seen: true, seenAt: null, createdAt: null,
    patientName: 'Paciente de prueba', patientDni: '', scheme: 'Tratamiento de prueba', diagnosis: '',
    requestedByDisplayName: 'Equipo de prueba', assignedToDisplayName: '' };
}

class Harness {
  readonly api = new FakeInbox();
  readonly auth = new FakeAuth();
  readonly clock = new FakeClock();
  readonly notifications: string[] = [];
  expired = 0;
  private destroyed = false;
  private readonly globals = ['window', 'document', 'HTMLElement'].map(name => ({ name, descriptor: Object.getOwnPropertyDescriptor(globalThis, name) }));
  readonly injector: ReturnType<typeof Injector.create>;
  readonly scheduler: ɵEffectScheduler;
  readonly component: ClinicalInboxComponent;

  constructor() {
    Object.defineProperty(globalThis, 'window', { configurable: true, value: this.clock.window });
    Object.defineProperty(globalThis, 'document', { configurable: true, value: { activeElement: null } });
    Object.defineProperty(globalThis, 'HTMLElement', { configurable: true, value: class {} });
    this.injector = Injector.create({ providers: [
      { provide: ɵINJECTOR_SCOPE, useValue: 'root' },
      { provide: ɵChangeDetectionScheduler, useValue: { notify(): void {} } },
      { provide: AuthService, useValue: this.auth },
      { provide: ClinicalInboxService, useValue: this.api }
    ] });
    this.component = runInInjectionContext(this.injector, () => new ClinicalInboxComponent());
    this.component.notification.subscribe(message => this.notifications.push(message));
    this.component.sessionExpired.subscribe(() => { this.expired++; });
    this.scheduler = this.injector.get(ɵEffectScheduler);
    this.scheduler.flush();
  }

  async settle(): Promise<void> {
    // Process actual Angular effects after promise continuations, without advancing the polling clock.
    for (let index = 0; index < 4; index++) {
      await Promise.resolve();
      if (!this.destroyed) this.scheduler.flush();
    }
  }
  destroy(): void {
    if (this.destroyed) return;
    this.destroyed = true;
    this.component.ngOnDestroy();
    this.injector.destroy();
  }
  dispose(): void {
    this.destroy();
    for (const { name, descriptor } of this.globals) {
      if (descriptor) Object.defineProperty(globalThis, name, descriptor);
      else Reflect.deleteProperty(globalThis, name);
    }
  }
}

test('finalizar una carga no crea polling reactivo; el intervalo real sí actualiza', async () => {
  const h = new Harness();
  try {
    equal(h.api.requests.length, 1, 'la sesión inicia una sola consulta');
    equal(h.component.loading(), true, 'la consulta inicial está pendiente');
    h.api.succeed(0, [item('initial')]);
    await h.settle();
    equal(h.component.loading(), false, 'la respuesta libera el estado de carga');
    equal(h.api.requests.length, 1, 'loading=false no dispara otra consulta');
    h.component.resolving.set(true);
    await h.settle();
    h.component.resolving.set(false);
    await h.settle();
    equal(h.api.requests.length, 1, 'cambiar resolving no reinicia el efecto de sesión');
    h.clock.advance(29_999);
    await h.settle();
    equal(h.api.requests.length, 1, 'no consulta antes de los 30 segundos');
    h.clock.advance(1);
    await h.settle();
    equal(h.api.requests.length, 2, 'el intervalo programado inicia la consulta siguiente');
    h.clock.advance(60_000);
    await h.settle();
    equal(h.api.requests.length, 2, 'no solapa consultas mientras una sigue pendiente');
    h.api.succeed(1, [item('updated')]);
    await h.settle();
    equal(h.component.items()[0]?.id, 'updated', 'aplica la actualización programada');
    equal(h.api.requests.length, 2, 'terminar el refresco tampoco provoca otro ciclo');
  } finally { h.dispose(); }
});

test('un fallo automático conserva los pendientes y no emite avisos; luego reintenta', async () => {
  const h = new Harness();
  try {
    h.api.succeed(0, [item('retained')]);
    await h.settle();
    h.clock.advance(30_000);
    h.api.fail(1);
    await h.settle();
    equal(h.component.items()[0]?.id, 'retained', 'conserva los últimos pendientes confirmados');
    equal(h.component.pendingCount(), 1, 'conserva el contador anterior');
    equal(h.notifications.length, 0, 'el fallo automático no genera notificaciones');
    equal(h.component.loading(), false, 'permite un nuevo intento tras el error');
    equal(h.api.requests.length, 2, 'el error no genera reintentos inmediatos en bucle');
    h.clock.advance(30_000);
    equal(h.api.requests.length, 3, 'el siguiente intervalo reintenta');
    h.api.succeed(2, [item('recovered')]);
    await h.settle();
    equal(h.component.items()[0]?.id, 'recovered', 'recupera normalmente después del fallo');
    equal(h.notifications.length, 0, 'la recuperación automática tampoco genera avisos');
  } finally { h.dispose(); }
});

test('abrir tras una consulta fallida informa el fallo y nunca afirma que no hay pendientes', async () => {
  const h = new Harness();
  try {
    h.api.fail(0);
    await h.settle();
    equal(h.notifications.length, 0, 'el primer error automático permanece silencioso');
    const opening = h.component.openInbox(true);
    equal(h.api.requests.length, 2, 'la acción explícita reintenta consultar');
    h.api.fail(1);
    await opening;
    await h.settle();
    equal(h.notifications.length, 1, 'la acción explícita explica el fallo una sola vez');
    truthy(h.notifications[0]?.trim(), 'la explicación explícita no está vacía');
    truthy(!h.notifications.some(message => /no hay|sin solicitudes/i.test(message)), 'un fallo no se presenta como bandeja vacía');
    equal(h.component.open(), false, 'no abre una solicitud inexistente');
  } finally { h.dispose(); }
});

test('abrir durante la primera carga no duplica la consulta ni anuncia una bandeja vacía prematuramente', async () => {
  const h = new Harness();
  try {
    const opening = h.component.openInbox(true);
    await h.settle();
    equal(h.api.requests.length, 1, 'reutiliza o espera la consulta que ya está pendiente');
    truthy(!h.notifications.some(message => /no hay|sin solicitudes/i.test(message)), 'no anuncia ausencia antes de recibir la respuesta');
    h.api.succeed(0);
    await opening;
    await h.settle();
    equal(h.component.loading(), false, 'finaliza la carga inicial');
  } finally { h.dispose(); }
});

test('401 limpia loading y detiene consultas hasta que aparece una sesión nueva', async () => {
  const h = new Harness();
  try {
    h.api.fail(0, 401);
    await h.settle();
    equal(h.expired, 1, 'informa la expiración una vez');
    equal(h.component.loading(), false, 'no deja loading bloqueado tras invalidar la petición');
    equal(h.component.items().length, 0, 'elimina pendientes de la sesión vencida');
    h.clock.advance(90_000);
    h.component.onWindowFocus();
    await h.settle();
    equal(h.api.requests.length, 1, 'ni reloj ni foco consultan una sesión ya vencida');
    equal(h.expired, 1, 'no repite avisos de sesión vencida');
    h.auth.session.set(session('inbox-user-b'));
    await h.settle();
    equal(h.api.requests.length, 2, 'la nueva sesión inicia una consulta');
    h.api.succeed(1, [item('new-session')]);
    await h.settle();
    equal(h.component.items()[0]?.id, 'new-session', 'la sesión nueva puede cargar normalmente');
    equal(h.component.loading(), false, 'libera carga en la nueva sesión');
  } finally { h.dispose(); }
});

test('cambiar usuario invalida respuestas previas aun cuando ambos conservan permisos', async () => {
  const h = new Harness();
  try {
    h.auth.session.set(session('inbox-user-b'));
    await h.settle();
    equal(h.api.requests.length, 2, 'otra identidad con iguales permisos recibe su propia consulta');
    h.api.succeed(0, [item('old-user')]);
    await h.settle();
    equal(h.component.items().length, 0, 'una respuesta vieja no aparece en la nueva sesión');
    equal(h.component.loading(), true, 'la respuesta vieja no libera la carga actual');
    h.api.succeed(1, [item('current-user')]);
    await h.settle();
    equal(h.component.items()[0]?.id, 'current-user', 'aplica solamente la respuesta del usuario vigente');
  } finally { h.dispose(); }
});

test('perder permisos invalida la consulta en curso y limpia el estado de carga', async () => {
  const h = new Harness();
  try {
    h.auth.session.set(session('inbox-user-a', []));
    await h.settle();
    equal(h.component.loading(), false, 'limpia carga al perder permiso');
    h.api.succeed(0, [item('unauthorized-old-result')]);
    await h.settle();
    equal(h.component.items().length, 0, 'ignora datos tardíos después de perder permiso');
    h.clock.advance(60_000);
    equal(h.api.requests.length, 1, 'no consulta sin permisos');
  } finally { h.dispose(); }
});

test('destruir el buzón descarta respuestas tardías y cancela temporizadores', async () => {
  const h = new Harness();
  try {
    h.destroy();
    equal(h.clock.timers.size, 0, 'destruir cancela el polling');
    h.api.succeed(0, [item('after-destroy')]);
    await h.settle();
    equal(h.component.items().length, 0, 'una respuesta posterior no modifica el componente destruido');
    equal(h.notifications.length, 0, 'no emite notificaciones después de destruir');
    equal(h.expired, 0, 'no emite eventos de sesión después de destruir');
    h.clock.advance(90_000);
    equal(h.api.requests.length, 1, 'no genera nuevas consultas tras destruir');
  } finally { h.dispose(); }
});

async function run(): Promise<void> {
  const failures: string[] = [];
  for (const entry of tests) {
    try { await entry.run(); }
    catch (failure) { failures.push(`${entry.name}: ${failure instanceof Error ? failure.stack || failure.message : String(failure)}`); }
  }
  if (failures.length) throw new Error(`Fallaron ${failures.length}/${tests.length} pruebas del buzón:\n${failures.join('\n')}`);
  console.log(`clinical-inbox.component: ${tests.length} pruebas, ${assertions} aserciones OK`);
}

void run().catch(failure => { queueMicrotask(() => { throw failure; }); });
