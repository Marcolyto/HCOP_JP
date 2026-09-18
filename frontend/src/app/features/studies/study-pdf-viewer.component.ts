import { Component, ElementRef, OnDestroy, WritableSignal, effect, input, output, signal, untracked, viewChild, viewChildren } from '@angular/core';
import type { PDFDocumentLoadingTask, PDFDocumentProxy, PDFPageProxy, RenderTask } from 'pdfjs-dist';
import { safeStudyPdfUrl, studyPdfRasterSize } from './study-pdf-viewer.models';
import type { StudyPdfPageAction, StudyPdfPagePreview, StudyPdfPageVersion } from './study-pdf-page.models';

type PdfPageStatus = 'pending' | 'rendering' | 'ready' | 'error';
interface PdfPageView {
  readonly number: number;
  readonly width: number;
  readonly height: number;
  readonly status: WritableSignal<PdfPageStatus>;
}
interface PdfPageRuntime {
  readonly view: PdfPageView;
  canvas?: HTMLCanvasElement;
  near: boolean;
  revision: number;
  task?: RenderTask;
  showingSaved?: boolean;
}
interface PdfActionContext {
  readonly epoch: number; readonly revision: number; readonly patientId: string;
  readonly documentUrl: string; readonly pageNumber: number; readonly versionId: string;
}
interface PdfZoomView { readonly pageNumber: number; readonly versionId: string; readonly url: string; readonly loading: boolean; readonly error: string; }

@Component({
  selector: 'app-study-pdf-viewer',
  templateUrl: './study-pdf-viewer.component.html',
  styleUrl: './study-pdf-viewer.component.scss'
})
export class StudyPdfViewerComponent implements OnDestroy {
  readonly url = input('');
  readonly title = input('Documento PDF');
  readonly patientId = input('');
  readonly pageVersions = input<readonly StudyPdfPagePreview[]>([]);
  readonly canEdit = input(false);
  readonly canAddToEvolution = input(false);
  readonly editPage = output<StudyPdfPageAction>();
  readonly addPageToEvolution = output<StudyPdfPageAction>();
  readonly selections = signal<Readonly<Record<number, string>>>({});
  readonly busyPage = signal(0);
  readonly actionError = signal('');
  readonly zoom = signal<PdfZoomView | null>(null);
  readonly zoomScale = signal(1);
  readonly imageErrors = signal<Readonly<Record<number, string>>>({});
  readonly pages = signal<readonly PdfPageView[]>([]);
  readonly pageCount = signal(0);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly safeUrl = signal('');
  private readonly container = viewChild<ElementRef<HTMLElement>>('pagesContainer');
  private readonly pageHosts = viewChildren<ElementRef<HTMLElement>>('pageHost');
  private readonly zoomDialog = viewChild<ElementRef<HTMLDialogElement>>('zoomDialog');
  private readonly retryCount = signal(0);
  private readonly runtimes = new Map<number, PdfPageRuntime>();
  private loadingTask: PDFDocumentLoadingTask | null = null;
  private document: PDFDocumentProxy | null = null;
  private resizeObserver: ResizeObserver | null = null;
  private intersectionObserver: IntersectionObserver | null = null;
  private removeFallbackListener: (() => void) | null = null;
  private epoch = 0;
  private destroyed = false;
  private width = 0;
  private resizeFrame = 0;
  private renderingEpoch: number | null = null;
  private actionRevision = 0;
  private exportTask: RenderTask | null = null;
  private exportCanvas: HTMLCanvasElement | null = null;
  private zoomObjectUrl = '';
  private zoomReturnFocus: HTMLElement | null = null;
  private previousActiveVersions = new Map<number, string>();

  constructor() {
    effect(onCleanup => {
      const raw = this.url();
      const patient = this.patientId();
      const element = this.container()?.nativeElement;
      this.retryCount();
      if (!element) return;
      untracked(() => {
        this.disposeDocument();
        this.pages.set([]);
        this.pageCount.set(0);
        this.error.set('');
        const url = safeStudyPdfUrl(raw, window.location.origin);
        this.safeUrl.set(patient.trim() ? url : '');
        if (!patient.trim() || !url) {
          this.error.set('No se puede mostrar este PDF en el paciente actual.');
          this.loading.set(false);
          return;
        }
        this.width = Math.floor(element.clientWidth);
        const epoch = this.epoch;
        this.resizeObserver = new ResizeObserver(() => this.scheduleResize(element, epoch));
        this.resizeObserver.observe(element);
        this.loading.set(true);
        void this.loadDocument(url, epoch);
      });
      onCleanup(() => untracked(() => this.disposeDocument()));
    });
    effect(onCleanup => {
      this.pages();
      const hosts = this.pageHosts();
      untracked(() => {
        this.observePages(hosts.map(host => host.nativeElement));
      });
      const observer = this.intersectionObserver;
      const removeFallback = this.removeFallbackListener;
      onCleanup(() => { observer?.disconnect(); removeFallback?.(); });
    });
    effect(() => {
      const previews = this.pageVersions();
      untracked(() => {
        const selections = { ...this.selections() };
        let changed = false;
        for (const preview of previews) {
          const previous = this.previousActiveVersions.get(preview.pageNumber);
          if ((previous || 'original') !== preview.activeVersionId && selections[preview.pageNumber] !== undefined) {
            delete selections[preview.pageNumber];
            changed = true;
          }
        }
        this.previousActiveVersions = new Map(previews.map(preview => [preview.pageNumber, preview.activeVersionId]));
        if (changed) this.selections.set(selections);
      });
    });
    effect(() => {
      this.pageVersions(); this.selections(); this.pages();
      untracked(() => {
        const zoom = this.zoom();
        if (zoom && zoom.versionId !== (this.selectedVersion(zoom.pageNumber)?.id || 'original')) this.closeZoom();
        for (const runtime of this.runtimes.values()) {
          const saved = Boolean(this.selectedVersion(runtime.view.number));
          if (runtime.showingSaved !== saved) {
            runtime.showingSaved = saved;
            this.invalidatePage(runtime);
          }
        }
        this.queueRender();
      });
    });
    effect(() => {
      const dialog = this.zoomDialog()?.nativeElement;
      if (!dialog || !this.zoom()) return;
      untracked(() => {
        if (!dialog.open) dialog.showModal();
        dialog.querySelector<HTMLButtonElement>('[data-zoom-close]')?.focus();
      });
    });
  }

  ngOnDestroy(): void { this.destroyed = true; this.disposeDocument(); }

  retry(): void { this.retryCount.update(value => value + 1); }

  versionsFor(number: number): readonly StudyPdfPageVersion[] {
    const seen = new Set<string>();
    return (this.pageVersions().find(preview => preview.pageNumber === number)?.versions || []).filter(version => {
      if (!version.id || version.id === 'original' || seen.has(version.id) || !safeStudyPdfUrl(version.url, window.location.origin)) return false;
      seen.add(version.id);
      return true;
    });
  }

  selectedVersion(number: number): StudyPdfPageVersion | null {
    const id = this.selections()[number] ?? this.pageVersions().find(preview => preview.pageNumber === number)?.activeVersionId ?? 'original';
    return this.versionsFor(number).find(version => version.id === id) || null;
  }

  selectVersion(number: number, id: string): void {
    if (this.busyPage()) return;
    this.selections.update(value => ({ ...value, [number]: id }));
    this.actionError.set('');
  }

  imageFailed(number: number, versionId: string): void { this.imageErrors.update(value => ({ ...value, [number]: versionId })); }
  requestEdit(number: number): void { void this.emitPageAction(number, 'edit'); }
  requestEvolution(number: number): void { void this.emitPageAction(number, 'evolution'); }

  cancelPageAction(): void {
    this.actionRevision += 1;
    this.exportTask?.cancel();
    this.exportTask = null;
    if (this.exportCanvas) { this.exportCanvas.width = 0; this.exportCanvas.height = 0; }
    this.exportCanvas = null;
    this.busyPage.set(0);
  }

  async enlargePage(number: number): Promise<void> {
    if (this.busyPage() || this.zoom() || !this.document || !this.safeUrl()) return;
    this.actionError.set('');
    this.zoomReturnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    this.zoomScale.set(1);
    const version = this.selectedVersion(number);
    const context = this.beginAction(number, version?.id || 'original');
    this.zoom.set({ pageNumber: number, versionId: context.versionId, url: version?.url || '', loading: !version, error: '' });
    if (version) { this.busyPage.set(0); return; }
    try {
      const blob = await this.pageSnapshot(context);
      if (!this.isActionCurrent(context) || !this.zoom()) return;
      this.zoomObjectUrl = URL.createObjectURL(blob);
      this.zoom.set({ pageNumber: number, versionId: context.versionId, url: this.zoomObjectUrl, loading: false, error: '' });
    } catch {
      if (this.isActionCurrent(context) && this.zoom()) this.zoom.update(value => value ? { ...value, loading: false, error: 'No se pudo ampliar esta página. Cierre e intente nuevamente.' } : null);
    } finally {
      if (context.revision === this.actionRevision) this.busyPage.set(0);
    }
  }

  closeZoom(event?: Event, restoreFocus = true): void {
    event?.preventDefault();
    this.cancelPageAction();
    this.zoomDialog()?.nativeElement.close();
    this.zoom.set(null);
    if (this.zoomObjectUrl) URL.revokeObjectURL(this.zoomObjectUrl);
    this.zoomObjectUrl = '';
    const focus = this.zoomReturnFocus;
    this.zoomReturnFocus = null;
    if (restoreFocus && focus?.isConnected) queueMicrotask(() => focus.isConnected && focus.focus({ preventScroll: true }));
  }

  changeZoom(delta: number): void { this.zoomScale.update(value => Math.max(0.5, Math.min(3, Math.round((value + delta) * 100) / 100))); }
  zoomImageFailed(): void { this.zoom.update(value => value ? { ...value, error: 'No se pudo cargar la imagen de esta página.' } : null); }

  trapZoomFocus(event: KeyboardEvent): void {
    if (event.key === 'Escape') { event.stopPropagation(); this.closeZoom(event); return; }
    if (event.key !== 'Tab') return;
    const dialog = this.zoomDialog()?.nativeElement;
    const items = dialog ? Array.from(dialog.querySelectorAll<HTMLElement>('button:not([disabled]), a[href], [tabindex="0"]')).filter(item => item.getClientRects().length) : [];
    const first = items[0]; const last = items.at(-1);
    if (!first || !last) { event.preventDefault(); return; }
    if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog)) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }

  private beginAction(pageNumber: number, versionId: string): PdfActionContext {
    this.cancelPageAction();
    this.busyPage.set(pageNumber);
    return { epoch: this.epoch, revision: this.actionRevision, patientId: this.patientId(), documentUrl: this.safeUrl(), pageNumber, versionId };
  }

  private isActionCurrent(context: PdfActionContext): boolean {
    return this.isCurrent(context.epoch) && context.revision === this.actionRevision
      && context.patientId === this.patientId() && context.documentUrl === safeStudyPdfUrl(this.url(), window.location.origin)
      && context.versionId === (this.selectedVersion(context.pageNumber)?.id || 'original');
  }

  private async emitPageAction(number: number, kind: 'edit' | 'evolution'): Promise<void> {
    if (this.busyPage() || !this.document || !this.safeUrl() || !(kind === 'edit' ? this.canEdit() : this.canAddToEvolution())) return;
    this.actionError.set('');
    const version = this.selectedVersion(number);
    const context = this.beginAction(number, version?.id || 'original');
    try {
      const content = version ? { url: version.url } : { file: new File([await this.pageSnapshot(context)], `pagina-${number}.png`, { type: 'image/png' }) };
      if (!this.isActionCurrent(context) || !(kind === 'edit' ? this.canEdit() : this.canAddToEvolution())) return;
      const action: StudyPdfPageAction = { pageNumber: number, patientId: context.patientId, documentUrl: context.documentUrl, versionId: context.versionId, ...content };
      if (kind === 'edit') this.editPage.emit(action); else this.addPageToEvolution.emit(action);
    } catch {
      if (this.isActionCurrent(context)) this.actionError.set('No se pudo preparar la página. Intente nuevamente.');
    } finally {
      if (context.revision === this.actionRevision) this.busyPage.set(0);
    }
  }

  private async pageSnapshot(context: PdfActionContext): Promise<Blob> {
    const pdf = this.document;
    if (!pdf || !this.isActionCurrent(context)) throw new Error('Cancelled');
    const canvas = document.createElement('canvas');
    this.exportCanvas = canvas;
    let page: PDFPageProxy | undefined;
    let task: RenderTask | undefined;
    try {
      page = await pdf.getPage(context.pageNumber);
      if (!this.isActionCurrent(context)) throw new Error('Cancelled');
      const original = page.getViewport({ scale: 1 });
      const size = studyPdfRasterSize(1800, original.width, original.height, 1);
      canvas.width = size.pixelWidth; canvas.height = size.pixelHeight;
      task = page.render({ canvas, viewport: page.getViewport({ scale: size.cssWidth / original.width }), background: '#ffffff', transform: [size.outputScale, 0, 0, size.outputScale, 0, 0] });
      this.exportTask = task;
      await task.promise;
      if (!this.isActionCurrent(context)) throw new Error('Cancelled');
      const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(value => value ? resolve(value) : reject(new Error('Export failed')), 'image/png'));
      if (!this.isActionCurrent(context)) throw new Error('Cancelled');
      return blob;
    } finally {
      if (this.exportTask === task) this.exportTask = null;
      if (this.exportCanvas === canvas) this.exportCanvas = null;
      canvas.width = 0; canvas.height = 0;
      // PDF.js shares the proxy with the inline view; release only once both
      // render tasks have finished. Otherwise the inline completion owns it.
      if (!this.exportTask && !this.runtimes.get(context.pageNumber)?.task) {
        try { page?.cleanup(); } catch { /* Patient or document changed. */ }
      }
    }
  }

  retryPage(number: number): void {
    const runtime = this.runtimes.get(number);
    if (!runtime) return;
    runtime.view.status.set('pending');
    this.queueRender();
  }

  private async loadDocument(url: string, epoch: number): Promise<void> {
    try {
      const pdfjs = await import('pdfjs-dist');
      if (!this.isCurrent(epoch)) return;
      pdfjs.GlobalWorkerOptions.workerSrc = '/app/assets/pdfjs/pdf.worker.min.mjs';
      const options = {
        url, withCredentials: true, isEvalSupported: false, enableXfa: false,
        cMapUrl: '/app/assets/pdfjs/cmaps/', cMapPacked: true,
        standardFontDataUrl: '/app/assets/pdfjs/standard_fonts/',
        wasmUrl: '/app/assets/pdfjs/wasm/', iccUrl: '/app/assets/pdfjs/iccs/', verbosity: 0
      };
      const task = pdfjs.getDocument(options);
      this.loadingTask = task;
      const document = await task.promise;
      if (!this.isCurrent(epoch)) return;
      this.document = document;
      this.pageCount.set(document.numPages);
      const pages: PdfPageView[] = [];
      for (let number = 1; number <= document.numPages; number += 1) {
        if (!this.isCurrent(epoch)) return;
        let width = pages.at(-1)?.width || 595;
        let height = pages.at(-1)?.height || 842;
        let status: PdfPageStatus = 'pending';
        try {
          const page = await document.getPage(number);
          if (!this.isCurrent(epoch)) return;
          const viewport = page.getViewport({ scale: 1 });
          if (!(viewport.width > 0 && viewport.height > 0 && Number.isFinite(viewport.width) && Number.isFinite(viewport.height))) throw new Error('Invalid page size');
          width = viewport.width;
          height = viewport.height;
          page.cleanup();
        } catch {
          if (!this.isCurrent(epoch)) return;
          status = 'error';
        }
        const view: PdfPageView = { number, width, height, status: signal<PdfPageStatus>(status) };
        pages.push(view);
        this.runtimes.set(number, { view, near: false, revision: 0 });
        // Publish progressively, keeping the real aspect ratio for every page.
        if (number === 1 || number % 12 === 0 || number === document.numPages) this.pages.set([...pages]);
      }
    } catch (error: unknown) {
      if (!this.isCurrent(epoch)) return;
      const name = error && typeof error === 'object' && 'name' in error ? String(error.name) : '';
      this.error.set(name === 'PasswordException'
        ? 'Este PDF está protegido con contraseña. Use «Abrir PDF» para consultarlo.'
        : 'No se pudo mostrar el PDF completo. Puede reintentar o usar «Abrir PDF».');
      if (!this.document && this.loadingTask) {
        const task = this.loadingTask;
        this.loadingTask = null;
        void task.destroy().catch(() => undefined);
      }
    } finally {
      if (this.isCurrent(epoch)) this.loading.set(false);
    }
  }

  private observePages(hosts: readonly HTMLElement[]): void {
    this.intersectionObserver?.disconnect();
    this.removeFallbackListener?.();
    this.removeFallbackListener = null;
    const epoch = this.epoch;
    for (const host of hosts) {
      const runtime = this.runtimes.get(Number(host.dataset['pdfPage']));
      if (runtime) runtime.canvas = host.querySelector('canvas') || undefined;
    }
    const update = (host: HTMLElement, near: boolean): void => {
      if (!this.isCurrent(epoch)) return;
      const runtime = this.runtimes.get(Number(host.dataset['pdfPage']));
      if (!runtime || runtime.near === near) return;
      runtime.near = near;
      if (!near) this.invalidatePage(runtime);
    };
    if (typeof IntersectionObserver !== 'undefined') {
      this.intersectionObserver = new IntersectionObserver(entries => {
        for (const entry of entries) update(entry.target as HTMLElement, entry.isIntersecting);
        this.queueRender();
      }, { rootMargin: '900px 0px' });
      for (const host of hosts) this.intersectionObserver.observe(host);
    } else {
      const updateVisible = (): void => {
        for (const host of hosts) {
          const rect = host.getBoundingClientRect();
          update(host, rect.bottom >= -900 && rect.top <= window.innerHeight + 900);
        }
        this.queueRender();
      };
      window.addEventListener('scroll', updateVisible, { capture: true, passive: true });
      this.removeFallbackListener = () => window.removeEventListener('scroll', updateVisible, true);
      updateVisible();
    }
    this.queueRender();
  }

  private scheduleResize(element: HTMLElement, epoch: number): void {
    if (!this.isCurrent(epoch)) return;
    cancelAnimationFrame(this.resizeFrame);
    this.resizeFrame = requestAnimationFrame(() => {
      this.resizeFrame = 0;
      if (!this.isCurrent(epoch)) return;
      const width = Math.floor(element.clientWidth);
      if (width <= 0 || width === this.width) return;
      this.width = width;
      for (const runtime of this.runtimes.values()) this.invalidatePage(runtime);
      this.queueRender();
    });
  }

  private queueRender(): void {
    const epoch = this.epoch;
    if (!this.document || this.width <= 0 || this.destroyed || this.renderingEpoch === epoch) return;
    if (![...this.runtimes.values()].some(item => item.near && item.canvas && !this.selectedVersion(item.view.number) && item.view.status() === 'pending')) return;
    this.renderingEpoch = epoch;
    void this.renderVisiblePages(epoch).finally(() => {
      if (this.renderingEpoch === epoch) this.renderingEpoch = null;
    });
  }

  private async renderVisiblePages(epoch: number): Promise<void> {
    while (this.isCurrent(epoch)) {
      const runtime = [...this.runtimes.values()].find(item => item.near && item.canvas && !this.selectedVersion(item.view.number) && item.view.status() === 'pending');
      if (!runtime) return;
      await this.renderPage(runtime, epoch);
    }
  }

  private async renderPage(runtime: PdfPageRuntime, epoch: number): Promise<void> {
    const document = this.document;
    const canvas = runtime.canvas;
    if (!document || !canvas) return;
    const revision = runtime.revision;
    runtime.view.status.set('rendering');
    let page: PDFPageProxy | undefined;
    try {
      page = await document.getPage(runtime.view.number);
      if (!this.isCurrent(epoch) || revision !== runtime.revision || !runtime.near) return;
      const size = studyPdfRasterSize(this.width, runtime.view.width, runtime.view.height, window.devicePixelRatio);
      const viewport = page.getViewport({ scale: size.cssWidth / runtime.view.width });
      canvas.width = size.pixelWidth;
      canvas.height = size.pixelHeight;
      const task = page.render({
        canvas, viewport, background: '#ffffff',
        transform: [size.outputScale, 0, 0, size.outputScale, 0, 0]
      });
      runtime.task = task;
      await task.promise;
      if (this.isCurrent(epoch) && revision === runtime.revision && runtime.near) runtime.view.status.set('ready');
    } catch {
      if (this.isCurrent(epoch) && revision === runtime.revision && runtime.near) runtime.view.status.set('error');
    } finally {
      runtime.task = undefined;
      if (!this.exportTask) {
        try { page?.cleanup(); } catch { /* The document may already have been destroyed. */ }
      }
    }
  }

  private invalidatePage(runtime: PdfPageRuntime): void {
    runtime.revision += 1;
    runtime.task?.cancel();
    if (runtime.view.status() !== 'error') runtime.view.status.set('pending');
    if (runtime.canvas) { runtime.canvas.width = 0; runtime.canvas.height = 0; }
  }

  private isCurrent(epoch: number): boolean { return !this.destroyed && epoch === this.epoch; }

  private disposeDocument(): void {
    this.epoch += 1;
    this.closeZoom(undefined, false);
    this.selections.set({});
    this.imageErrors.set({});
    this.actionError.set('');
    this.previousActiveVersions.clear();
    this.renderingEpoch = null;
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    this.intersectionObserver?.disconnect();
    this.intersectionObserver = null;
    this.removeFallbackListener?.();
    this.removeFallbackListener = null;
    cancelAnimationFrame(this.resizeFrame);
    this.resizeFrame = 0;
    for (const runtime of this.runtimes.values()) this.invalidatePage(runtime);
    this.runtimes.clear();
    this.document = null;
    const task = this.loadingTask;
    this.loadingTask = null;
    if (task) void task.destroy().catch(() => undefined);
  }
}
