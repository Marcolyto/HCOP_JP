import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, Input, OnChanges, OnDestroy, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import {
  PasswordAction, RepositoryDraft, StudyRepository, repositoryChanged, repositoryDraft,
  repositoryUpdate, repositoryValidation
} from './study-repositories.models';
import { StudyRepositoriesService } from './study-repositories.service';

@Component({
  selector: 'app-study-repositories',
  imports: [CommonModule, FormsModule],
  templateUrl: './study-repositories.component.html',
  styleUrl: './study-repositories.component.scss'
})
export class StudyRepositoriesComponent implements OnChanges, OnDestroy {
  @Input() active = false;
  @Input() canManage = false;
  private readonly service = inject(StudyRepositoriesService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly subscriptions = new Subscription();
  private loaded = false;

  items: readonly StudyRepository[] = [];
  selected: StudyRepository | null = null;
  draft: RepositoryDraft | null = null;
  loading = false;
  saving = false;
  error = '';
  notice = '';

  ngOnChanges(): void {
    if (this.active && !this.loaded && !this.loading) this.reload(false);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    if (this.draft) this.draft.password = '';
  }

  reload(protectChanges = true): void {
    if (this.loading || this.saving || protectChanges && !this.confirmDiscardChanges('Actualizar descartará los cambios que todavía no guardó. ¿Desea continuar?')) return;
    this.loading = true;
    this.error = '';
    this.notice = '';
    this.changeDetector.markForCheck();
    this.subscriptions.add(this.service.load().subscribe({
      next: (items) => {
        this.loading = false;
        this.loaded = true;
        this.apply(items);
        this.changeDetector.markForCheck();
      },
      error: (failure) => {
        this.loading = false;
        this.error = this.message(failure, false);
        this.changeDetector.markForCheck();
      }
    }));
  }

  select(item: StudyRepository): void {
    if (this.loading || this.saving || item.id === this.selected?.id || !this.confirmDiscardChanges()) return;
    this.selected = item;
    this.draft = repositoryDraft(item);
    this.error = '';
    this.notice = '';
  }

  hasUnsavedChanges(): boolean {
    return Boolean(this.selected && this.draft && repositoryChanged(this.selected, this.draft));
  }

  confirmDiscardChanges(message = 'Hay cambios sin guardar en el repositorio. ¿Desea descartarlos y continuar?'): boolean {
    if (this.saving) return false;
    if (!this.hasUnsavedChanges()) return true;
    if (!globalThis.confirm(message)) return false;
    this.discard();
    return true;
  }

  discard(): void {
    if (this.saving || !this.selected) return;
    this.draft = repositoryDraft(this.selected);
    this.error = '';
    this.notice = '';
  }

  changePasswordAction(action: PasswordAction): void {
    if (!this.draft) return;
    this.draft.passwordAction = action;
    this.draft.password = '';
  }

  save(): void {
    if (!this.canManage || this.saving || this.loading || !this.selected || !this.draft || !this.hasUnsavedChanges()) return;
    this.error = repositoryValidation(this.draft);
    this.notice = '';
    if (this.error) return;
    this.saving = true;
    this.changeDetector.markForCheck();
    this.subscriptions.add(this.service.save(this.selected.id, repositoryUpdate(this.draft)).subscribe({
      next: (items) => {
        this.saving = false;
        this.apply(items);
        this.notice = 'Cambios guardados. Se usarán en las próximas consultas de estudios.';
        this.changeDetector.markForCheck();
      },
      error: (failure) => {
        this.saving = false;
        this.error = this.message(failure, true);
        this.changeDetector.markForCheck();
      }
    }));
  }

  credentialStatus(item: StudyRepository): string {
    return !item.requiresCredentials ? 'Sin credenciales' : item.username && item.hasPassword ? 'Acceso configurado' : 'Falta configurar acceso';
  }

  private apply(items: readonly StudyRepository[]): void {
    const selectedId = this.selected?.id;
    if (this.draft) this.draft.password = '';
    this.items = items;
    this.selected = items.find((item) => item.id === selectedId) ?? items[0] ?? null;
    this.draft = this.selected ? repositoryDraft(this.selected) : null;
  }

  private message(failure: unknown, saving: boolean): string {
    if (failure instanceof HttpErrorResponse) {
      if (failure.status === 403) return 'Su perfil no tiene permiso para realizar esta acción.';
      if (failure.status === 400) {
        const detail: unknown = failure.error?.error ?? failure.error?.message;
        return typeof detail === 'string' && detail.length < 400 ? detail : 'No se pudo guardar. Revise las direcciones y los datos de acceso.';
      }
      if (failure.status === 409) return 'La configuración cambió. Actualice la información antes de guardar.';
    }
    return saving ? 'No se pudieron guardar los cambios. Puede volver a intentarlo.' : 'No se pudo cargar el repositorio. Vuelva a intentar con Actualizar.';
  }
}
