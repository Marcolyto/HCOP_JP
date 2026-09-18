import { Component, ElementRef, HostListener, OnDestroy, ViewChild, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { fitRasterSize, isDrawableShape, normalizeCanvasPoint, normalizedSearch, safePngName, shapeGeometry } from './study-template-editor.geometry';
import { ShapeAnnotation, StrokeAnnotation, StudyAnnotation, StudyAnnotationFill, StudyAnnotationTool, StudyTemplateCatalogItem, StudyTemplateEditorSource } from './study-template-editor.models';
import { StudyTemplateEditorService } from './study-template-editor.service';
import { StudyTemplateImageSession } from './study-template-editor.state';

@Component({
  selector: 'app-study-template-editor',
  templateUrl: './study-template-editor.component.html',
  styleUrl: './study-template-editor.component.scss'
})
export class StudyTemplateEditorComponent implements OnDestroy {
  private readonly api = inject(StudyTemplateEditorService);
  private readonly auth = inject(AuthService);
  private baseImage: HTMLImageElement | null = null;
  private baseObjectUrl = '';
  private activePointerId: number | null = null;
  private readonly imageSession = new StudyTemplateImageSession();
  private canvasRef?: ElementRef<HTMLCanvasElement>;
  private destroyed = false;
  private exportRevision = 0;

  readonly open = input(false);
  readonly source = input<StudyTemplateEditorSource | null>(null);
  readonly canEdit = input(true);
  readonly canAddToEvolution = input(false);
  readonly closed = output<void>();
  readonly imageReady = output<File>();
  readonly evolutionReady = output<File>();

  @ViewChild('canvas')
  private set canvas(value: ElementRef<HTMLCanvasElement> | undefined) {
    this.canvasRef = value;
    this.canvasMounted.set(Boolean(value));
    this.redraw();
  }

  readonly editable = computed(() => this.canEdit() && this.auth.hasPermission('section.studies.edit'));
  readonly templates = signal<readonly StudyTemplateCatalogItem[]>([]);
  readonly query = signal('');
  readonly category = signal('');
  readonly catalogLoading = signal(false);
  readonly catalogError = signal('');
  readonly imageLoading = signal(false);
  readonly imageLoaded = signal(false);
  readonly exporting = signal(false);
  private readonly canvasMounted = signal(false);
  readonly loading = computed(() => this.catalogLoading() || this.imageLoading());
  readonly ready = computed(() => this.open() && this.imageLoaded() && this.canvasMounted() && !this.imageLoading() && !this.exporting());
  readonly error = signal('');
  readonly status = signal('Seleccione una plantilla anatómica para comenzar.');
  readonly selected = signal<StudyTemplateCatalogItem | null>(null);
  readonly tool = signal<StudyAnnotationTool>('draw');
  readonly fill = signal<StudyAnnotationFill>('none');
  readonly color = signal('#1587c9');
  readonly width = signal(7);
  readonly textValue = signal('');
  readonly commands = signal<readonly StudyAnnotation[]>([]);
  readonly redoCommands = signal<readonly StudyAnnotation[]>([]);
  readonly draft = signal<StrokeAnnotation | ShapeAnnotation | null>(null);
  readonly imageTitle = signal('Plantilla anatómica');
  readonly sourceName = signal('');

  readonly categories = computed(() => [...new Set(this.templates().map((item) => item.category).filter(Boolean))]
    .sort((left, right) => left.localeCompare(right, 'es-AR')));
  readonly filteredTemplates = computed(() => {
    const query = normalizedSearch(this.query());
    const category = this.category();
    return this.templates().filter((item) => {
      if (category && item.category !== category) return false;
      const haystack = normalizedSearch([item.title, item.category, item.description, item.tags.join(' ')].join(' '));
      return !query || haystack.includes(query);
    });
  });
  readonly shapeTool = computed(() => ['circle', 'rectangle', 'arrow'].includes(this.tool()));

  constructor() {
    effect(() => {
      const visible = this.open();
      const source = this.source();
      untracked(() => {
        if (!visible) { this.cancelPendingWork(); return; }
        if (this.imageSession.setSource(source)) {
          this.resetImage();
          this.selected.set(null);
        }
        if (!source && !this.templates().length && !this.catalogLoading()) void this.loadTemplates();
        if (source && !this.baseImage && !this.imageLoading()) void this.loadSource(source);
        else if (!source && this.selected() && !this.baseImage && !this.imageLoading()) void this.selectTemplate(this.selected()!);
      });
    });
  }

  ngOnDestroy(): void { this.destroyed = true; this.cancelPendingWork(); this.revokeBaseUrl(); }

  async loadTemplates(): Promise<void> {
    if (this.catalogLoading() || this.destroyed) return;
    this.catalogLoading.set(true); this.catalogError.set('');
    try {
      const templates = await firstValueFrom(this.api.templates());
      if (!this.destroyed) this.templates.set(templates);
    } catch { if (!this.destroyed) this.catalogError.set('No se pudo abrir la biblioteca anatómica.'); }
    finally { if (!this.destroyed) this.catalogLoading.set(false); }
  }

  async selectTemplate(item: StudyTemplateCatalogItem): Promise<void> {
    if (!this.open() || !item.available || !item.imageUrl || this.exporting()) return;
    if (this.selected()?.id === item.id && this.baseImage) return;
    this.selected.set(item);
    await this.loadSource({ url: item.imageUrl, name: item.title, title: item.title });
  }

  setTool(tool: StudyAnnotationTool): void { if (this.editable()) this.tool.set(tool); }
  setFill(fill: StudyAnnotationFill): void { this.fill.set(fill); }
  setColor(color: string): void { if (/^#[0-9a-f]{6}$/i.test(color)) this.color.set(color.toLowerCase()); }
  setWidth(width: number): void { if ([3, 7, 14].includes(width)) this.width.set(width); }

  pointerDown(event: PointerEvent): void {
    if (!this.editable() || !this.ready() || !this.baseImage || event.button !== 0) return;
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;
    event.preventDefault();
    const point = normalizeCanvasPoint(event.clientX, event.clientY, canvas.getBoundingClientRect(), canvas.width, canvas.height);
    if (this.tool() === 'text') {
      const text = this.textValue().trim();
      if (!text) { this.status.set('Escriba el texto y luego haga clic sobre la imagen.'); return; }
      const scale = canvas.width / Math.max(canvas.getBoundingClientRect().width, 1);
      this.commit({ type: 'text', point, text, color: this.color(), fontSize: Math.max(18, 28 * scale) });
      return;
    }
    this.activePointerId = event.pointerId;
    canvas.setPointerCapture(event.pointerId);
    const scale = canvas.width / Math.max(canvas.getBoundingClientRect().width, 1);
    const width = this.width() * scale * (this.tool() === 'eraser' ? 3.2 : 1);
    if (this.shapeTool()) {
      this.draft.set({ type: 'shape', shape: this.tool() as ShapeAnnotation['shape'], start: point, end: point, color: this.color(), width, filled: this.fill() === 'solid' });
    } else {
      this.draft.set({ type: this.tool() === 'eraser' ? 'eraser' : 'stroke', points: [point], color: this.color(), width });
    }
    this.redraw();
  }

  pointerMove(event: PointerEvent): void {
    if (this.activePointerId !== event.pointerId) return;
    const canvas = this.canvasRef?.nativeElement;
    const draft = this.draft();
    if (!canvas || !draft) return;
    event.preventDefault();
    const point = normalizeCanvasPoint(event.clientX, event.clientY, canvas.getBoundingClientRect(), canvas.width, canvas.height);
    this.draft.set(draft.type === 'shape' ? { ...draft, end: point } : { ...draft, points: [...draft.points, point] });
    this.redraw();
  }

  pointerUp(event: PointerEvent): void {
    if (this.activePointerId !== event.pointerId) return;
    event.preventDefault();
    const canvas = this.canvasRef?.nativeElement;
    const draft = this.draft();
    this.activePointerId = null; this.draft.set(null);
    try { canvas?.releasePointerCapture(event.pointerId); } catch { /* browser already released it */ }
    if (!draft) return;
    if (draft.type === 'shape') {
      if (isDrawableShape(draft)) this.commit(draft); else this.redraw();
      return;
    }
    const points = draft.points.length === 1 ? [...draft.points, { x: draft.points[0]!.x + 1, y: draft.points[0]!.y }] : draft.points;
    this.commit({ ...draft, points });
  }

  pointerCancel(event: PointerEvent): void {
    if (this.activePointerId !== event.pointerId) return;
    const canvas = this.canvasRef?.nativeElement;
    this.activePointerId = null; this.draft.set(null);
    try { canvas?.releasePointerCapture(event.pointerId); } catch { /* browser already released it */ }
    this.redraw();
  }

  undo(): void {
    const commands = [...this.commands()];
    const command = commands.pop();
    if (!command) return;
    this.commands.set(commands); this.redoCommands.update((items) => [...items, command]); this.redraw();
  }

  redo(): void {
    const redo = [...this.redoCommands()];
    const command = redo.pop();
    if (!command) return;
    this.redoCommands.set(redo); this.commands.update((items) => [...items, command]); this.redraw();
  }

  clearAnnotations(): void { this.commands.set([]); this.redoCommands.set([]); this.draft.set(null); this.redraw(); }

  async useImage(): Promise<void> {
    if (!this.editable()) { this.status.set('Su perfil no permite agregar imágenes a Estudios.'); return; }
    const file = await this.rasterFile();
    if (file) this.imageReady.emit(file);
  }

  async useInEvolution(): Promise<void> {
    if (!this.editable() || !this.canAddToEvolution()) return;
    const file = await this.rasterFile();
    if (file && this.canAddToEvolution()) this.evolutionReady.emit(file);
  }

  async download(): Promise<void> {
    if (!this.editable()) return;
    const file = await this.rasterFile();
    if (!file) return;
    const url = URL.createObjectURL(file);
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = file.name; anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }

  requestClose(): void { this.cancelPendingWork(); this.closed.emit(); }

  @HostListener('document:keydown', ['$event'])
  keyboard(event: KeyboardEvent): void {
    if (!this.ready() || !this.editable() || !(event.ctrlKey || event.metaKey)) return;
    if (event.key.toLocaleLowerCase('en') === 'z') { event.preventDefault(); event.shiftKey ? this.redo() : this.undo(); }
    if (event.key.toLocaleLowerCase('en') === 'y') { event.preventDefault(); this.redo(); }
  }

  private async loadSource(source: StudyTemplateEditorSource): Promise<void> {
    const revision = this.imageSession.beginLoad();
    this.resetImage();
    this.imageLoading.set(true); this.error.set('');
    this.imageTitle.set(source.title || source.name || 'Imagen clínica');
    try {
      const blob = source.file ?? (source.url ? await firstValueFrom(this.api.image(source.url)) : null);
      if (!this.isCurrentImage(revision)) return;
      if (!blob) throw new Error('missing source');
      await this.loadImageBlob(blob, source.name || 'imagen-clinica', source.title || source.name || 'Imagen clínica', revision);
    } catch { if (this.isCurrentImage(revision)) this.error.set('No se pudo preparar la imagen seleccionada.'); }
    finally { if (this.isCurrentImage(revision)) this.imageLoading.set(false); }
  }

  private async loadImageBlob(blob: Blob, name: string, title: string, revision: number): Promise<void> {
    if (!blob.type.startsWith('image/')) throw new Error('invalid image');
    const objectUrl = URL.createObjectURL(blob);
    try {
      const image = new Image(); image.decoding = 'async'; image.src = objectUrl;
      await image.decode();
      if (!this.isCurrentImage(revision)) return;
      this.revokeBaseUrl();
      this.baseObjectUrl = objectUrl;
      this.baseImage = image;
      this.sourceName.set(name); this.imageTitle.set(title); this.imageLoaded.set(true);
      this.status.set('Imagen lista. Puede marcarla y guardar una copia; el original se conserva.');
      this.redraw();
    } finally {
      if (this.baseObjectUrl !== objectUrl) URL.revokeObjectURL(objectUrl);
    }
  }

  private isCurrentImage(revision: number): boolean {
    return !this.destroyed && this.open() && this.imageSession.isCurrent(revision);
  }

  private cancelPendingWork(): void {
    this.imageSession.cancelLoad();
    this.exportRevision++;
    this.exporting.set(false); this.imageLoading.set(false);
    const pointerId = this.activePointerId;
    this.activePointerId = null; this.draft.set(null);
    if (pointerId !== null) {
      try { this.canvasRef?.nativeElement.releasePointerCapture(pointerId); } catch { /* browser already released it */ }
    }
    this.redraw();
  }

  private resetImage(): void {
    this.exportRevision++;
    this.exporting.set(false); this.imageLoading.set(false);
    this.baseImage = null; this.revokeBaseUrl();
    this.imageLoaded.set(false); this.sourceName.set(''); this.imageTitle.set('Plantilla anatómica');
    this.commands.set([]); this.redoCommands.set([]); this.draft.set(null); this.activePointerId = null;
    this.error.set(''); this.status.set('Seleccione una plantilla anatómica para comenzar.');
    const canvas = this.canvasRef?.nativeElement;
    if (canvas) canvas.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height);
  }

  private commit(command: StudyAnnotation): void {
    this.commands.update((items) => [...items, command]); this.redoCommands.set([]); this.draft.set(null); this.redraw();
  }

  private redraw(): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas || !this.baseImage) return;
    // Angular recreates the canvas after closing the modal; restore its intrinsic
    // dimensions before repainting the preserved image and annotation commands.
    const size = fitRasterSize(this.baseImage.naturalWidth, this.baseImage.naturalHeight);
    if (canvas.width !== size.x) canvas.width = size.x;
    if (canvas.height !== size.y) canvas.height = size.y;
    const context = canvas.getContext('2d');
    if (!context) return;
    context.clearRect(0, 0, canvas.width, canvas.height);
    context.drawImage(this.baseImage, 0, 0, canvas.width, canvas.height);
    const layer = document.createElement('canvas'); layer.width = canvas.width; layer.height = canvas.height;
    const layerContext = layer.getContext('2d');
    if (!layerContext) return;
    [...this.commands(), ...(this.draft() ? [this.draft()!] : [])].forEach((command) => this.drawCommand(layerContext, command));
    context.drawImage(layer, 0, 0);
  }

  private drawCommand(context: CanvasRenderingContext2D, command: StudyAnnotation): void {
    context.save();
    switch (command.type) {
      case 'stroke':
      case 'eraser':
        if (command.type === 'eraser') context.globalCompositeOperation = 'destination-out';
        context.strokeStyle = command.color; context.lineWidth = command.width; context.lineCap = 'round'; context.lineJoin = 'round';
        context.beginPath(); command.points.forEach((point, index) => index ? context.lineTo(point.x, point.y) : context.moveTo(point.x, point.y)); context.stroke();
        break;
      case 'text':
        context.font = `700 ${command.fontSize}px Arial, sans-serif`; context.lineWidth = Math.max(3, command.fontSize / 9);
        context.strokeStyle = 'rgba(255,255,255,.95)'; context.strokeText(command.text, command.point.x, command.point.y);
        context.fillStyle = command.color; context.fillText(command.text, command.point.x, command.point.y);
        break;
      case 'shape': {
        const geometry = shapeGeometry(command); context.strokeStyle = command.color; context.fillStyle = command.color;
        context.lineWidth = command.width; context.lineCap = 'round'; context.lineJoin = 'round'; context.beginPath();
        if (geometry.kind === 'circle') context.arc(geometry.centerX, geometry.centerY, geometry.radius, 0, Math.PI * 2);
        else if (geometry.kind === 'rectangle') context.rect(geometry.x, geometry.y, geometry.width, geometry.height);
        else geometry.points.forEach((point, index) => index ? context.lineTo(point.x, point.y) : context.moveTo(point.x, point.y));
        if (geometry.kind === 'arrow') context.closePath();
        if (command.filled) { context.globalAlpha = geometry.kind === 'arrow' ? .42 : .22; context.fill(); context.globalAlpha = 1; }
        context.stroke();
        break;
      }
    }
    context.restore();
  }

  private async rasterFile(): Promise<File | null> {
    const canvas = this.canvasRef?.nativeElement;
    if (!this.ready() || !canvas || !this.baseImage) { this.status.set('Espere a que la imagen esté lista para guardarla.'); return null; }
    const revision = ++this.exportRevision;
    const name = safePngName(this.sourceName() || this.imageTitle());
    this.exporting.set(true);
    try {
      this.redraw();
      const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'));
      if (revision !== this.exportRevision || this.destroyed || !this.open()) return null;
      if (!blob) throw new Error('empty raster');
      return new File([blob], name, { type: 'image/png', lastModified: Date.now() });
    } catch {
      if (revision === this.exportRevision) this.error.set('No se pudo preparar la copia de la imagen. Vuelva a intentar.');
      return null;
    } finally { if (revision === this.exportRevision) this.exporting.set(false); }
  }

  private revokeBaseUrl(): void { if (this.baseObjectUrl) URL.revokeObjectURL(this.baseObjectUrl); this.baseObjectUrl = ''; }
}
