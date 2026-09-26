import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  ViewChild,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import {
  ClinicalInboxItem,
  ClinicalInboxResolution,
  ClinicalInboxResolutionRequest,
  ClinicalInboxResolvedEvent,
  clinicalInboxPermission,
  clinicalInboxResolutionNeedsReason,
  clinicalInboxResolutionOptions
} from './clinical-inbox.models';
import {
  ClinicalInboxService,
  clinicalInboxApiMessage,
  clinicalInboxIsUnauthorized,
  clinicalInboxRefreshMessage
} from './clinical-inbox.service';

@Component({
  selector: 'app-clinical-inbox',
  imports: [CommonModule, FormsModule],
  templateUrl: './clinical-inbox.component.html',
  styleUrl: './clinical-inbox.component.scss'
})
export class ClinicalInboxComponent implements OnDestroy {
  readonly modalBlocked = input(false);
  readonly autoOpen = input(true);
  readonly pollIntervalMs = input(30_000);
  readonly resolved = output<ClinicalInboxResolvedEvent>();
  readonly patientRefreshRequested = output<string>();
  readonly sessionExpired = output<void>();
  readonly pendingCountChanged = output<number>();
  readonly notification = output<string>();

  readonly auth = inject(AuthService);
  private readonly inbox = inject(ClinicalInboxService);

  readonly items = signal<ClinicalInboxItem[]>([]);
  readonly activeId = signal('');
  readonly open = signal(false);
  readonly loading = signal(false);
  readonly resolving = signal(false);
  readonly error = signal('');
  readonly refreshError = signal('');
  readonly resolution = signal<ClinicalInboxResolution | ''>('');
  readonly reason = signal('');
  readonly resumeDate = signal('');

  readonly activeItem = computed(() =>
    this.items().find((item) => item.id === this.activeId()) ?? null
  );
  readonly pendingCount = computed(() => this.items().length);
  readonly buttonLabel = computed(() => {
    const count = this.pendingCount();
    if (this.refreshError()) return count
      ? `Solicitudes clínicas: ${count} pendientes; actualización pendiente. Abrir y reintentar.`
      : 'Solicitudes clínicas: actualización pendiente. Reintentar.';
    if (this.loading() && !count) return 'Consultando solicitudes clínicas';
    return count
      ? `Abrir ${count} ${count === 1 ? 'solicitud clínica pendiente' : 'solicitudes clínicas pendientes'}`
      : 'Sin solicitudes clínicas pendientes';
  });
  private readonly sessionUserId = computed(() => this.auth.session()?.user?.id ?? '');
  readonly unseenCount = computed(() => this.items().filter((item) => !item.seen).length);
  readonly canReceiveTasks = computed(() => {
    const session = this.auth.session();
    return Boolean(session?.authenticated && (
      this.auth.hasPermission('workflow.resolve-prescription')
      || this.auth.hasPermission('workflow.resolve-continuity')
    ));
  });
  readonly resolutionOptions = computed(() => {
    const item = this.activeItem();
    return item ? clinicalInboxResolutionOptions(item.type) : [];
  });
  readonly needsReason = computed(() => clinicalInboxResolutionNeedsReason(this.resolution()));
  readonly canSubmit = computed(() => Boolean(
    this.resolution()
    && (!this.needsReason() || this.reason().trim().length >= 3)
    && !this.resolving()
  ));
  readonly position = computed(() => {
    const index = this.items().findIndex((item) => item.id === this.activeId());
    return `${Math.max(0, index) + 1} de ${this.items().length}`;
  });

  @ViewChild('inboxDialog') private dialog?: ElementRef<HTMLElement>;
  @ViewChild('resolutionSelect') private resolutionSelect?: ElementRef<HTMLSelectElement>;
  private pollTimer: number | null = null;
  private loadVersion = 0;
  private autoOpenPending = false;
  private returnFocus: HTMLElement | null = null;

  constructor() {
    effect(() => {
      const allowed = this.canReceiveTasks();
      const interval = Math.max(5_000, this.pollIntervalMs());
      const autoOpen = this.autoOpen();
      this.sessionUserId();
      // Loading and inbox contents must not become dependencies of the polling setup.
      untracked(() => {
        this.stopPolling();
        this.resetForClosedSession();
        if (!allowed) return;
        this.autoOpenPending = autoOpen;
        void this.refresh({ openFirst: autoOpen });
        this.pollTimer = window.setInterval(
          () => void this.refresh({ openFirst: autoOpen }),
          interval
        );
      });
    });

    effect(() => this.pendingCountChanged.emit(this.pendingCount()));

    effect(() => {
      if (!this.modalBlocked() && this.autoOpenPending && this.autoOpen()) this.tryAutoOpen();
    });
  }

  ngOnDestroy(): void {
    this.stopPolling();
    this.loadVersion += 1;
  }

  @HostListener('window:focus')
  onWindowFocus(): void {
    if (this.canReceiveTasks()) void this.refresh({ openFirst: this.autoOpen() });
  }

  async openInbox(explicit = true, itemId = ''): Promise<void> {
    if (!this.canReceiveTasks() || this.resolving() || this.modalBlocked()) return;
    if (this.loading() && !this.items().length) return;
    if (explicit) this.autoOpenPending = false;
    if (explicit && (!this.items().length || this.refreshError())) {
      const updated = await this.refresh({ openFirst: false });
      if (!this.canReceiveTasks() || this.modalBlocked()) return;
      if (!updated && !this.items().length) {
        if (this.refreshError()) this.notification.emit(this.refreshError());
        return;
      }
    }
    const item = this.items().find((entry) => entry.id === itemId) ?? this.items()[0];
    if (!item) {
      if (explicit) this.notification.emit('No hay solicitudes clínicas pendientes');
      return;
    }
    this.returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    this.activeId.set(item.id);
    this.resetDecision();
    this.open.set(true);
    this.autoOpenPending = false;
    window.setTimeout(() => this.resolutionSelect?.nativeElement.focus(), 0);
    if (!item.seen) this.markActiveSeen(item);
  }

  async retryRefresh(): Promise<void> {
    await this.refresh({ openFirst: false });
  }

  close(): void {
    if (this.resolving()) return;
    this.open.set(false);
    this.activeId.set('');
    this.resetDecision();
    const focus = this.returnFocus;
    this.returnFocus = null;
    window.setTimeout(() => focus?.focus(), 0);
  }

  onResolutionChanged(value: string): void {
    this.resolution.set(value as ClinicalInboxResolution | '');
    this.error.set('');
    if (value !== 'temporary_hold') this.resumeDate.set('');
  }

  async submit(): Promise<void> {
    const item = this.activeItem();
    const resolution = this.resolution();
    const reason = this.reason().trim();
    if (!item || !resolution || this.resolving()) return;
    if (this.needsReason() && reason.length < 3) {
      this.error.set('La causa debe tener al menos 3 caracteres para esta decisión.');
      return;
    }
    if (!this.auth.hasPermission(clinicalInboxPermission(item.type))) {
      this.error.set('No tiene permiso para resolver este tipo de solicitud.');
      return;
    }

    const request: ClinicalInboxResolutionRequest = {
      resolution,
      reason,
      ...(resolution === 'temporary_hold' && this.resumeDate()
        ? { resumeDate: this.resumeDate() }
        : {})
    };
    this.resolving.set(true);
    this.loadVersion += 1;
    this.loading.set(false);
    this.error.set('');
    try {
      const response = await firstValueFrom(this.inbox.resolve(item.id, request));
      this.items.update((items) => items.filter((entry) => entry.id !== item.id));
      this.resolved.emit({ request: item, decision: request, response });
      this.patientRefreshRequested.emit(item.patientId);
      this.notification.emit(resolution === 'prescription_confirmed'
        ? 'Prescripción confirmada para el tratamiento y ciclo actuales'
        : 'Decisión registrada en la historia clínica');
      this.closeAfterResolution();
      if (this.items().length) {
        window.setTimeout(() => {
          if (!this.modalBlocked()) void this.openInbox(false);
          else this.autoOpenPending = true;
        }, 250);
      }
    } catch (error) {
      if (clinicalInboxIsUnauthorized(error)) this.expireSession();
      this.error.set(clinicalInboxApiMessage(error, 'No se pudo registrar la decisión.'));
    } finally {
      this.resolving.set(false);
    }
  }

  onDialogKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Tab') return;
    const root = this.dialog?.nativeElement;
    if (!root) return;
    const focusable = Array.from(root.querySelectorAll<HTMLElement>(
      'button:not([disabled]), select:not([disabled]), textarea:not([disabled]), input:not([disabled]), [href], [tabindex]:not([tabindex="-1"])'
    )).filter((element) => !element.hidden && element.offsetParent !== null);
    if (!focusable.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  patientName(item: ClinicalInboxItem): string {
    return item.patientName || `Paciente ${item.patientId}`;
  }

  requestedBy(item: ClinicalInboxItem): string {
    return item.requestedByDisplayName || 'Equipo asistencial';
  }

  treatmentName(item: ClinicalInboxItem): string {
    return item.scheme || 'Tratamiento';
  }

  submitLabel(): string {
    return this.activeItem()?.type === 'prescription_request'
      && this.resolution() === 'prescription_confirmed'
      ? 'Confirmar prescripción'
      : 'Registrar decisión';
  }

  createdAtLabel(value: string | null): string {
    if (!value) return 'Fecha no informada';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return 'Fecha no informada';
    return `Solicitado ${new Intl.DateTimeFormat('es-AR', {
      dateStyle: 'short',
      timeStyle: 'short'
    }).format(date)}`;
  }

  private async refresh({ openFirst }: { openFirst: boolean }): Promise<boolean> {
    if (!this.canReceiveTasks() || this.loading() || this.resolving()) return false;
    const version = ++this.loadVersion;
    this.loading.set(true);
    try {
      const page = await firstValueFrom(this.inbox.load());
      if (version !== this.loadVersion) return false;
      this.refreshError.set('');
      const items = page.items.filter((item) =>
        item.status === 'pending' && this.auth.hasPermission(clinicalInboxPermission(item.type))
      );
      this.items.set(items);
      if (this.open() && !items.some((item) => item.id === this.activeId())) this.close();
      this.autoOpenPending ||= openFirst;
      this.tryAutoOpen();
      return true;
    } catch (error) {
      if (version !== this.loadVersion) return false;
      if (clinicalInboxIsUnauthorized(error)) {
        this.expireSession();
      } else {
        this.refreshError.set(clinicalInboxRefreshMessage(error));
      }
      return false;
    } finally {
      if (version === this.loadVersion) this.loading.set(false);
    }
  }

  private tryAutoOpen(): void {
    if (!this.autoOpenPending || this.modalBlocked() || this.open()) return;
    const unseen = this.items().find((item) => !item.seen);
    if (unseen) {
      this.autoOpenPending = false;
      void this.openInbox(false, unseen.id);
    } else if (!this.items().length) {
      this.autoOpenPending = false;
    }
  }

  private markActiveSeen(item: ClinicalInboxItem): void {
    this.items.update((items) => items.map((entry) =>
      entry.id === item.id
        ? { ...entry, seen: true, seenAt: new Date().toISOString() }
        : entry
    ));
    this.inbox.markSeen(item.id).subscribe({
      error: (error) => {
        if (clinicalInboxIsUnauthorized(error)) this.expireSession();
      }
    });
  }

  private closeAfterResolution(): void {
    this.open.set(false);
    this.activeId.set('');
    this.resetDecision();
    this.returnFocus = null;
  }

  private resetDecision(): void {
    this.resolution.set('');
    this.reason.set('');
    this.resumeDate.set('');
    this.error.set('');
  }

  private resetForClosedSession(): void {
    this.loadVersion += 1;
    this.loading.set(false);
    this.items.set([]);
    this.open.set(false);
    this.activeId.set('');
    this.autoOpenPending = false;
    this.refreshError.set('');
    this.resetDecision();
  }

  private expireSession(): void {
    this.stopPolling();
    this.resetForClosedSession();
    this.auth.expireSession();
    this.sessionExpired.emit();
  }

  private stopPolling(): void {
    if (this.pollTimer !== null) window.clearInterval(this.pollTimer);
    this.pollTimer = null;
  }
}
