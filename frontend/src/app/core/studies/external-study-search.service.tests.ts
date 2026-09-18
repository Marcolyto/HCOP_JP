import '@angular/compiler';
import { equal } from 'node:assert/strict';
import { Injector, runInInjectionContext } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subscriber } from 'rxjs';
import { ExternalStudySearchService } from './external-study-search.service';

interface Request { url: string; subscriber: Subscriber<unknown>; cancelled: boolean; }
const requests: Request[] = [];
const injector = Injector.create({ providers: [{ provide: HttpClient, useValue: {
  get: (url: string) => new Observable<unknown>(subscriber => {
    const request = { url, subscriber, cancelled: false };
    requests.push(request);
    return () => { request.cancelled = true; };
  })
} }] });
const search = runInInjectionContext(injector, () => new ExternalStudySearchService());
const response = (patientId: string, id: string) => ({ patientId, studies: [{ id, title: id }], sources: [], total: 1 });
search.setContext('42', '12.345.678');
equal(requests.length, 1);
equal(requests[0]!.url, '/api/patients/42/external-studies', 'DNI no se envía como parámetro editable');
equal(search.loading(), true);
search.setContext('42', '12345678');
equal(requests.length, 1, 'guardar la historia con mismo paciente/DNI no vuelve a consultar');

search.setContext('7', '87654321');
equal(requests[0]!.cancelled, true, 'cambiar paciente cancela HTTP anterior');
requests[0]!.subscriber.next(response('42', 'obsolete'));
equal(search.result(), null, 'respuesta anterior nunca aparece en otro paciente');
requests[1]!.subscriber.next(response('7', 'current'));
equal(search.result()?.studies[0]?.id, 'current');
equal(search.loading(), false);

search.refresh();
equal(search.result()?.studies[0]?.id, 'current', 'conserva resultados previos durante refresco mismo paciente');
search.refresh();
equal(requests[2]!.cancelled, true, 'otro refresco cancela el anterior');
requests[2]!.subscriber.next(response('7', 'stale-refresh'));
equal(search.result()?.studies[0]?.id, 'current');
requests[3]!.subscriber.error(new Error('source unavailable'));
equal(search.result()?.studies[0]?.id, 'current', 'un error de actualización no borra resultados existentes');
equal(Boolean(search.error()), true);

search.setContext('7', '12345678');
equal(search.result(), null, 'cambiar el DNI descarta resultados de identidad previa');
requests[4]!.subscriber.next(response('42', 'mismatched'));
equal(search.result(), null, 'rechaza envelope de otro paciente');
equal(Boolean(search.error()), true);
search.setContext('', '');
equal(search.loading(), false);
equal(search.result(), null);
search.ngOnDestroy();
injector.destroy();
console.log('external-study-search.service: cancelación, refresco, errores y pacientes obsoletos OK');
