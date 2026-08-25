import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { ClinicalConflictComparison } from '../core/patients/clinical-conflict-comparison';
import { PatientWorkspaceService } from '../core/patients/patient-workspace.service';
import { ClinicalDraftRegistryService } from '../core/patients/clinical-draft-registry.service';
import { ClinicalWorkspaceComponent } from '../features/clinical-workspace/clinical-workspace.component';
import { StudyPanelComponent, StudyPanelRequest } from '../features/studies/study-panel.component';
import { TimelinePanelComponent } from '../features/timeline/timeline-panel.component';
import { NewPatientModalComponent } from '../features/patients/new-patient-modal.component';
import { DayHospitalComponent } from '../features/day-hospital/day-hospital.component';
import { CareSchedulerComponent } from '../features/scheduler/care-scheduler.component';
import { PrescriptionComponent } from '../features/prescription/prescription.component';
import { AgentComponent } from '../features/agent/agent.component';
import { ProtocolExplorerComponent } from '../features/protocols/protocol-explorer.component';
import { ToolsComponent } from '../features/tools/tools.component';
import { ClinicalInboxComponent } from '../features/clinical-inbox/clinical-inbox.component';
import { ResearchComponent } from '../features/research/research.component';
import { ClinicalHighlightActionDirective } from '../core/highlighting/clinical-highlight-action.directive';
import { ClinicalHighlightFeedbackComponent } from '../features/highlighting/clinical-highlight-feedback.component';

type RightPane = 'studies' | 'care' | 'prescription' | 'agent' | 'research' | 'timeline' | 'protocols' | 'tools';

@Component({
  selector: 'app-clinical-shell',
  imports: [RouterLink, ClinicalWorkspaceComponent, StudyPanelComponent, TimelinePanelComponent, DayHospitalComponent, NewPatientModalComponent, CareSchedulerComponent, PrescriptionComponent, AgentComponent, ProtocolExplorerComponent, ToolsComponent, ClinicalInboxComponent, ResearchComponent, ClinicalHighlightActionDirective, ClinicalHighlightFeedbackComponent],
  templateUrl: './clinical-shell.component.html',
  styleUrl: './clinical-shell.component.scss'
})
export class ClinicalShellComponent implements OnInit, AfterViewInit, OnDestroy {
  readonly initialPane = input<RightPane | ''>('');
  readonly auth = inject(AuthService);
  readonly patientWorkspace = inject(PatientWorkspaceService);
  private readonly clinicalDrafts = inject(ClinicalDraftRegistryService);
  private readonly router = inject(Router);
  readonly selectedPane = signal<RightPane>('studies');
  readonly rightTabsIconOnly = signal(false);
  readonly studyPanelRequest = signal<StudyPanelRequest | null>(null);
  readonly searchExpanded = signal(false);
  readonly historySearchQuery = signal('');
  readonly historySearchMatches = signal(0);
  readonly newPatientOpen = signal(false);
  readonly careSchedulerOpen = signal(false);
  readonly printTimestamp = signal('');
  readonly conflictReviewOpen = signal(false);
  readonly clinicalNotification = signal('');
  readonly splitPercent = signal(58);
  readonly splitLeftPixels = signal(0);
  readonly splitPosition = computed<'studies' | 'balanced' | 'history' | 'custom'>(() => {
    const percent = this.splitPercent();
    if (percent <= 0.01) return 'studies';
    if (percent >= 99.99) return 'history';
    if (Math.abs(percent - 50) < 0.6) return 'balanced';
    return 'custom';
  });
  readonly clinicalCollapsed = computed(() => this.splitPosition() === 'studies' && !this.splitWorkspaceStacked());
  readonly supportCollapsed = computed(() => this.splitPosition() === 'history' && !this.splitWorkspaceStacked());
  @ViewChild('splitWorkspace') private splitWorkspace?: ElementRef<HTMLElement>;
  @ViewChild('splitterControl') private splitterControl?: ElementRef<HTMLElement>;
  @ViewChild('clinicalSearchInput') private clinicalSearchInput?: ElementRef<HTMLInputElement>;
  @ViewChild('clinicalSaveConflictBanner') private clinicalSaveConflictBanner?: ElementRef<HTMLElement>;
  @ViewChild('rightPanelTabs') private rightPanelTabs?: ElementRef<HTMLElement>;
  @ViewChild('conflictReviewClose') private conflictReviewClose?: ElementRef<HTMLButtonElement>;
  private conflictReviewReturnFocus: HTMLElement | null = null;
  private focusedConflictId = '';
  private studyRequestSequence = 0;
  private clinicalNotificationTimer: number | null = null;
  private splitDragging = false;
  private splitDragOffset = 0;
  private historySearchTimer: number | null = null;

  constructor() {
    effect(() => {
      const conflictId = this.patientWorkspace.activeSaveConflict()?.conflictId || '';
      if (!conflictId) {
        this.focusedConflictId = '';
        return;
      }
      if (conflictId === this.focusedConflictId) return;
      this.focusedConflictId = conflictId;
      window.setTimeout(() => this.clinicalSaveConflictBanner?.nativeElement.focus(), 0);
    });
  }

  ngOnInit(): void {
    const requestedPane = this.initialPane();
    if (requestedPane) this.selectedPane.set(requestedPane);
    this.auth.load().subscribe({
      next: (session) => {
        if (session.activePatientId) this.patientWorkspace.load(session.activePatientId);
        if (!this.canOpen(this.selectedPane())) this.selectedPane.set(this.defaultPane());
        window.setTimeout(() => this.updateRightTabLabels());
      },
      error: () => this.auth.session.set({ ok: false, authenticated: false, loginRequired: true, activePatientId: null })
    });
  }
  ngAfterViewInit(): void {
    this.applyStoredSplit();
    window.setTimeout(() => this.updateRightTabLabels());
  }
  ngOnDestroy(): void {
    if (this.clinicalNotificationTimer !== null) window.clearTimeout(this.clinicalNotificationTimer);
    if (this.historySearchTimer !== null) window.clearTimeout(this.historySearchTimer);
    this.clearHistorySearchMarks();
    document.body.classList.remove('resizing');
  }

  selectPane(pane: RightPane): void {
    if (pane !== this.selectedPane() && this.hasPendingConflict()) return;
    this.selectedPane.set(this.canOpen(pane) ? pane : this.defaultPane());
  }
  openStudies(request: Omit<StudyPanelRequest, 'id'>): void {
    if (this.hasPendingConflict() || !this.auth.hasPermission('section.studies.view')) return;
    this.selectedPane.set('studies');
    this.studyPanelRequest.set({ ...request, id: ++this.studyRequestSequence });
  }
  openLogin(): void { this.router.navigateByUrl('/login'); }
  openConfiguration(): void { if (!this.hasPendingConflict()) void this.router.navigateByUrl('/configuration'); }
  openPatient(): void { if (!this.hasPendingConflict()) this.patientWorkspace.openPicker(); }
  openNewPatient(): void { if (!this.hasPendingConflict()) this.newPatientOpen.set(true); }
  closePatient(): void { if (!this.hasPendingConflict()) this.patientWorkspace.close(); }
  refreshActivePatient(): void {
    const patientId = this.patientWorkspace.workspace()?.patientId;
    if (!patientId || this.patientWorkspace.loading() || this.hasPendingConflict()) return;
    this.patientWorkspace.load(patientId);
    this.showClinicalNotification('Actualizando la historia clínica');
  }
  logout(): void {
    if (this.hasPendingConflict()) return;
    this.auth.logout().subscribe({ next: () => this.router.navigateByUrl('/login') });
  }
  inboxBlocked(): boolean {
    return this.hasPendingConflict()
      || this.newPatientOpen()
      || this.careSchedulerOpen()
      || this.conflictReviewOpen()
      || this.patientWorkspace.pickerOpen();
  }
  refreshPatientAfterWorkflow(patientId: string): void {
    if (!patientId || this.hasPendingConflict()) return;
    if (this.patientWorkspace.workspace()?.patientId === patientId) this.patientWorkspace.load(patientId);
  }
  showClinicalNotification(message: string): void {
    const clean = message.trim();
    if (!clean) return;
    this.clinicalNotification.set(clean);
    if (this.clinicalNotificationTimer !== null) window.clearTimeout(this.clinicalNotificationTimer);
    this.clinicalNotificationTimer = window.setTimeout(() => {
      this.clinicalNotification.set('');
      this.clinicalNotificationTimer = null;
    }, 4_500);
  }
  toggleHistorySearch(): void {
    const opening = !this.searchExpanded();
    this.searchExpanded.set(opening);
    if (opening) {
      window.setTimeout(() => {
        const input = this.clinicalSearchInput?.nativeElement;
        input?.focus();
        input?.setSelectionRange(input.value.length, input.value.length);
      });
    }
  }
  updateHistorySearch(value: string): void {
    this.historySearchQuery.set(value);
    if (this.historySearchTimer !== null) window.clearTimeout(this.historySearchTimer);
    this.historySearchTimer = window.setTimeout(() => {
      this.historySearchTimer = null;
      this.applyHistorySearch(true);
    }, 180);
  }
  private applyHistorySearch(scrollToFirst: boolean): void {
    const root = this.splitWorkspace?.nativeElement.querySelector<HTMLElement>('#clinicalDocument');
    if (!root) {
      this.historySearchMatches.set(0);
      return;
    }
    this.clearHistorySearchMarks(root);
    const term = this.normalizeHistorySearch(this.historySearchQuery()).trim();
    if (term.length < 2) {
      this.historySearchMatches.set(0);
      return;
    }
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodes: Text[] = [];
    while (walker.nextNode()) {
      const node = walker.currentNode as Text;
      const parent = node.parentElement;
      if (!parent || parent.closest('input, button, svg, style, script, mark.timeline-search-mark, mark.clinical-text-highlight')) continue;
      if (this.normalizeHistorySearch(node.nodeValue || '').includes(term)) nodes.push(node);
    }
    let first: HTMLElement | null = null;
    let count = 0;
    for (const node of nodes) {
      const original = node.nodeValue || '';
      const normalized = this.normalizeHistorySearch(original);
      const fragment = document.createDocumentFragment();
      let cursor = 0;
      let start = normalized.indexOf(term);
      while (start >= 0) {
        if (start > cursor) fragment.appendChild(document.createTextNode(original.slice(cursor, start)));
        const mark = document.createElement('mark');
        mark.className = 'timeline-search-mark';
        mark.textContent = original.slice(start, start + term.length);
        fragment.appendChild(mark);
        first ||= mark;
        count += 1;
        cursor = start + term.length;
        start = normalized.indexOf(term, cursor);
      }
      if (cursor < original.length) fragment.appendChild(document.createTextNode(original.slice(cursor)));
      node.replaceWith(fragment);
    }
    this.historySearchMatches.set(count);
    if (scrollToFirst && first) {
      first.scrollIntoView({ behavior: 'auto', block: 'center' });
      first.classList.add('timeline-search-mark--focus');
      window.setTimeout(() => first?.classList.remove('timeline-search-mark--focus'), 1_800);
    }
  }
  private clearHistorySearchMarks(root?: ParentNode): void {
    const searchRoot = root || this.splitWorkspace?.nativeElement;
    if (!searchRoot) return;
    const parents = new Set<Node>();
    searchRoot.querySelectorAll('mark.timeline-search-mark').forEach((mark) => {
      if (mark.parentNode) parents.add(mark.parentNode);
      mark.replaceWith(document.createTextNode(mark.textContent || ''));
    });
    parents.forEach((parent) => parent.normalize());
  }
  private normalizeHistorySearch(value: string): string {
    return Array.from(value).map((character) => character.normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase('es-AR')).join('');
  }
  startSplitDrag(event: PointerEvent): void {
    if (event.button !== 0 || this.splitWorkspaceStacked()) return;
    const target = event.currentTarget instanceof HTMLElement ? event.currentTarget : this.splitterControl?.nativeElement;
    if (!target) return;
    event.preventDefault();
    target.setPointerCapture(event.pointerId);
    const rect = target.getBoundingClientRect();
    this.splitDragOffset = event.clientX - (rect.left + rect.width / 2);
    this.splitDragging = true;
    target.classList.add('dragging');
    document.body.classList.add('resizing');
  }
  moveSplitDrag(event: PointerEvent): void {
    if (!this.splitDragging) return;
    event.preventDefault();
    const metrics = this.splitMetrics();
    const dividerCenter = event.clientX - this.splitDragOffset;
    const leftPixels = dividerCenter - metrics.contentLeft - metrics.railWidth / 2;
    this.setSplitPercent((leftPixels / metrics.available) * 100, true);
  }
  endSplitDrag(event?: PointerEvent): void {
    if (!this.splitDragging) return;
    const target = event?.currentTarget instanceof HTMLElement ? event.currentTarget : this.splitterControl?.nativeElement;
    if (event && target?.hasPointerCapture(event.pointerId)) target.releasePointerCapture(event.pointerId);
    this.splitDragging = false;
    this.splitDragOffset = 0;
    target?.classList.remove('dragging');
    document.body.classList.remove('resizing');
  }
  setSplitPreset(preset: 'studies' | 'balanced' | 'history'): void {
    const percent = preset === 'studies' ? 0 : preset === 'history' ? 100 : 50;
    this.setSplitPercent(percent, true);
    this.showClinicalNotification(preset === 'studies'
      ? 'Historia colapsada: sólo Estudios'
      : preset === 'history'
        ? 'Estudios colapsados: sólo Historia'
        : 'Historia y Estudios a la mitad');
  }
  onSplitKeydown(event: KeyboardEvent): void {
    const step = event.shiftKey ? 10 : 2;
    let next: number | null = null;
    if (event.key === 'ArrowLeft') next = this.splitPercent() - step;
    if (event.key === 'ArrowRight') next = this.splitPercent() + step;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = 100;
    if (next === null) return;
    event.preventDefault();
    this.setSplitPercent(next, true);
  }
  splitValueText(): string {
    const percent = Math.round(this.splitPercent());
    if (percent <= 0) return 'Sólo Estudios; Historia colapsada';
    if (percent >= 100) return 'Sólo Historia; Estudios colapsados';
    return `Historia ${percent}%, estudios ${100 - percent}%`;
  }
  @HostListener('window:resize')
  syncSplitAfterResize(): void {
    this.setSplitPercent(this.splitPercent(), false);
    this.updateRightTabLabels();
  }
  private updateRightTabLabels(): void {
    const container = this.rightPanelTabs?.nativeElement;
    if (!container) return;
    const tabs = Array.from(container.querySelectorAll<HTMLElement>('.right-tab'));
    const context = document.createElement('canvas').getContext('2d');
    const requiredWidth = tabs.reduce((total, tab) => {
      const label = tab.querySelector<HTMLElement>('.right-tab-label');
      if (!label) return total;
      const style = window.getComputedStyle(tab);
      const padding = (Number.parseFloat(style.paddingLeft) || 0) + (Number.parseFloat(style.paddingRight) || 0);
      if (context) context.font = `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`;
      const labelWidth = context?.measureText(label.textContent?.trim() || '').width || label.scrollWidth;
      return total + labelWidth + padding + 4;
    }, 0);
    this.rightTabsIconOnly.set(requiredWidth > container.clientWidth);
  }
  private applyStoredSplit(): void {
    let stored = 58;
    try {
      const value = Number(localStorage.getItem('hc-oncologica-left-width-v2'));
      if (Number.isFinite(value)) stored = value;
    } catch { /* almacenamiento no disponible */ }
    this.setSplitPercent(stored, false);
  }
  private setSplitPercent(value: number, persist: boolean): void {
    const percent = Math.max(0, Math.min(100, Number.isFinite(value) ? value : 58));
    const metrics = this.splitMetrics();
    this.splitPercent.set(Math.round(percent * 10) / 10);
    this.splitLeftPixels.set(metrics.available * (percent / 100));
    window.requestAnimationFrame(() => this.updateRightTabLabels());
    if (persist) {
      try { localStorage.setItem('hc-oncologica-left-width-v2', String(this.splitPercent())); } catch { /* almacenamiento no disponible */ }
    }
  }
  private splitMetrics(): { available: number; contentLeft: number; railWidth: number } {
    const workspace = this.splitWorkspace?.nativeElement;
    const splitter = this.splitterControl?.nativeElement;
    if (!workspace) return { available: 1, contentLeft: 0, railWidth: 0 };
    const rect = workspace.getBoundingClientRect();
    const style = window.getComputedStyle(workspace);
    const paddingLeft = Number.parseFloat(style.paddingLeft) || 0;
    const paddingRight = Number.parseFloat(style.paddingRight) || 0;
    const borderLeft = Number.parseFloat(style.borderLeftWidth) || 0;
    const railWidth = splitter?.parentElement?.getBoundingClientRect().width
      || Number.parseFloat(style.getPropertyValue('--splitter-width'))
      || 36;
    const contentWidth = Math.max(workspace.clientWidth - paddingLeft - paddingRight, 1);
    return {
      available: Math.max(contentWidth - railWidth, 1),
      contentLeft: rect.left + borderLeft + paddingLeft,
      railWidth
    };
  }
  private splitWorkspaceStacked(): boolean {
    return Boolean(window.matchMedia?.('(max-width: 980px)').matches);
  }
  hasPendingConflict(): boolean {
    return this.patientWorkspace.hasPendingClinicalWork();
  }
  canPrint(): boolean {
    return Boolean(
      this.patientWorkspace.workspace()
      && !this.patientWorkspace.loading()
      && !this.hasPendingConflict()
      && this.auth.hasPermission('section.history.view')
    );
  }
  print(): void {
    if (!this.canPrint()) return;
    this.preparePrint();
    window.setTimeout(() => window.print(), 0);
  }
  @HostListener('window:beforeprint')
  preparePrint(): void {
    if (this.canPrint() && !this.printTimestamp()) this.printTimestamp.set(new Date().toISOString());
  }
  @HostListener('window:afterprint')
  restoreAfterPrint(): void { this.printTimestamp.set(''); }
  resolvePendingConflict(): void {
    const conflict = this.patientWorkspace.activeSaveConflict();
    if (!conflict) return;
    if (!window.confirm('¿Descartar este borrador no guardado y recuperar la última versión confirmada?')) return;
    this.conflictReviewOpen.set(false);
    this.clinicalDrafts.clearPatient(conflict.patientId);
    this.patientWorkspace.discardConflictAndReload();
  }
  openConflictReview(): void {
    const conflict = this.patientWorkspace.activeSaveConflict();
    if (!conflict || conflict.code !== 'VERSION_CONFLICT' || !this.auth.hasPermission('section.history.view')) return;
    this.conflictReviewReturnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    this.conflictReviewOpen.set(true);
    this.patientWorkspace.refreshConflictLatest();
    window.setTimeout(() => this.conflictReviewClose?.nativeElement.focus(), 0);
  }
  closeConflictReview(): void {
    this.conflictReviewOpen.set(false);
    const returnFocus = this.conflictReviewReturnFocus;
    this.conflictReviewReturnFocus = null;
    window.setTimeout(() => returnFocus?.focus(), 0);
  }
  conflictComparison(): ClinicalConflictComparison | null {
    return this.patientWorkspace.activeConflictComparison();
  }
  initial(): string { return (this.auth.session()?.user?.displayName || this.auth.session()?.user?.username || 'U').slice(0, 1).toUpperCase(); }
  canViewAnySupport(): boolean {
    const panes: readonly RightPane[] = ['studies', 'care', 'prescription', 'agent', 'research', 'timeline', 'protocols', 'tools'];
    return panes.some((pane) => this.canOpen(pane));
  }
  private canOpen(pane: RightPane): boolean {
    if (pane === 'studies') return this.auth.hasPermission('section.studies.view');
    if (pane === 'care') return this.auth.hasPermission('section.day-hospital.view');
    if (pane === 'prescription') return this.auth.hasPermission('section.prescriptions.view');
    if (pane === 'agent') return this.auth.hasPermission('section.agent.view');
    if (pane === 'research') return this.auth.hasPermission('section.research.view');
    if (pane === 'protocols') return this.auth.hasPermission('section.protocols.view');
    if (pane === 'tools') return this.auth.hasPermission('section.tools.view');
    if (pane === 'timeline') return this.auth.hasPermission('section.timeline.view');
    return false;
  }
  private defaultPane(): RightPane {
    const panes: readonly RightPane[] = ['studies', 'care', 'prescription', 'agent', 'research', 'timeline', 'protocols', 'tools'];
    return panes.find((pane) => this.canOpen(pane)) || 'studies';
  }
}
