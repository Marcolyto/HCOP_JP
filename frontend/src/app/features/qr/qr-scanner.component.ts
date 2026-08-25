import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, ElementRef, OnChanges, OnDestroy, SimpleChanges, ViewChild, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

type JsonObject = Record<string, unknown>;
interface BarcodeResult { rawValue?: string; }
interface BarcodeDetectorLike { detect(source: HTMLCanvasElement): Promise<BarcodeResult[]>; }
type QrDecoder = typeof import('jsqr').default;
type QrBrowserWindow = Window & typeof globalThis & {
  BarcodeDetector?: new (options: { formats: string[] }) => BarcodeDetectorLike;
};

@Component({
  selector: 'app-qr-scanner',
  imports: [CommonModule, FormsModule],
  templateUrl: './qr-scanner.component.html',
  styleUrl: './qr-scanner.component.scss'
})
export class QrScannerComponent implements OnChanges, OnDestroy {
  readonly open = input(false);
  readonly closed = output<void>();
  readonly administrationRequested = output<JsonObject>();
  private readonly http = inject(HttpClient);
  @ViewChild('cameraVideo') private videoRef?: ElementRef<HTMLVideoElement>;
  @ViewChild('scannerCanvas') private canvasRef?: ElementRef<HTMLCanvasElement>;

  readonly manualCode = signal('');
  readonly busy = signal(false);
  readonly cameraActive = signal(false);
  readonly statusTone = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly statusTitle = signal('Listo para escanear');
  readonly statusDetail = signal('El código se valida en el sistema local antes de abrir la ficha.');
  readonly resolved = signal<JsonObject | null>(null);
  private stream: MediaStream | null = null;
  private frame = 0;
  private lastFrameAt = 0;
  private decoding = false;
  private detector: BarcodeDetectorLike | null = null;
  private decoder: QrDecoder | null = null;
  private decoderLoading: Promise<boolean> | null = null;
  private operationId = '';
  private lastCode = '';
  private requestVersion = 0;

  ngOnChanges(changes: SimpleChanges): void { if (changes['open']?.currentValue) this.reset(); else if (changes['open']) this.stopCamera(false); }
  ngOnDestroy(): void { this.stopCamera(false); }
  close(): void { if (this.busy()) return; this.stopCamera(false); this.closed.emit(); }
  reset(): void {
    this.stopCamera(false); this.requestVersion += 1; this.manualCode.set(''); this.resolved.set(null);
    this.lastCode = ''; this.operationId = ''; this.busy.set(false);
    this.setStatus('idle', 'Listo para escanear', 'El código se valida en el sistema local antes de abrir la ficha.');
  }
  async startCamera(): Promise<void> {
    if (this.stream || this.busy()) return;
    if (!navigator.mediaDevices?.getUserMedia) { this.setStatus('error', 'La cámara no está disponible', 'Use una imagen del QR o pegue su contenido.'); return; }
    this.setStatus('loading', 'Preparando el lector', 'Comprobando la cámara y el decodificador local.');
    if (!await this.ensureDecoder()) { this.setStatus('error', 'No hay un decodificador QR disponible', 'Puede pegar el contenido y continuar sin cámara.'); return; }
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
      const video = this.videoRef?.nativeElement; if (!video) throw new Error('No se encontró la vista de cámara.');
      video.srcObject = this.stream; await video.play(); this.cameraActive.set(true);
      this.setStatus('loading', 'Buscando el código', 'Centre el QR dentro del recuadro. La lectura es automática.');
      this.frame = requestAnimationFrame(timestamp => void this.scanFrame(timestamp));
    } catch {
      this.stopCamera(false); this.setStatus('error', 'No se pudo abrir la cámara', 'Revise el permiso del navegador o use una imagen del QR.');
    }
  }
  stopCamera(announce = true): void {
    if (this.frame) cancelAnimationFrame(this.frame);
    this.frame = 0; this.lastFrameAt = 0; this.decoding = false;
    this.stream?.getTracks().forEach(track => track.stop()); this.stream = null;
    if (this.videoRef?.nativeElement) this.videoRef.nativeElement.srcObject = null;
    this.cameraActive.set(false);
    if (announce) this.setStatus('idle', 'Cámara detenida', 'Puede reiniciarla, elegir una imagen o pegar el contenido del código.');
  }
  async readImage(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement; const file = input.files?.[0];
    if (!file || this.busy()) return;
    this.stopCamera(false); this.setStatus('loading', 'Leyendo la imagen', 'Buscando un código QR legible.');
    try {
      if (!await this.ensureDecoder()) throw new Error('No hay un decodificador QR disponible para imágenes.');
      const bitmap = await createImageBitmap(file); const drawing = this.draw(bitmap, bitmap.width, bitmap.height); bitmap.close();
      const value = drawing ? await this.decode(drawing.canvas, drawing.context) : '';
      if (!value) throw new Error('No se encontró un QR legible en la imagen seleccionada.');
      this.resolve(value);
    } catch (error) {
      this.setStatus('error', 'No se pudo leer la imagen', error instanceof Error ? error.message : 'Pruebe con una imagen más nítida.');
    } finally { input.value = ''; }
  }
  identify(): void { this.resolve(this.manualCode()); }
  requestAdministration(): void { const payload = this.resolved(); if (payload && !this.busy()) this.administrationRequested.emit(payload); }
  patient(): JsonObject { return this.object(this.resolved()?.['patient']); }
  treatment(): JsonObject { return this.object(this.resolved()?.['treatment']); }
  infusion(): JsonObject { return this.object(this.resolved()?.['infusion']); }
  appointmentLabel(): string {
    const item = this.infusion(); const scheduled = new Date(String(item['scheduledAt'] || ''));
    if (Number.isNaN(scheduled.getTime())) return 'Turno todavía no asignado';
    return `${scheduled.toLocaleString('es-AR', { dateStyle: 'short', timeStyle: 'short' })} · Sillón ${item['chair'] || 'sin asignar'}`;
  }

  private async scanFrame(timestamp: number): Promise<void> {
    if (!this.stream || !this.open()) return;
    if (timestamp - this.lastFrameAt < 120) { this.frame = requestAnimationFrame(next => void this.scanFrame(next)); return; }
    this.lastFrameAt = timestamp;
    const video = this.videoRef?.nativeElement;
    if (!this.decoding && video && video.readyState >= 2 && video.videoWidth && video.videoHeight) {
      this.decoding = true;
      try {
        const drawing = this.draw(video, video.videoWidth, video.videoHeight);
        const value = drawing ? await this.decode(drawing.canvas, drawing.context) : '';
        if (value) { this.stopCamera(false); this.resolve(value); return; }
      } catch { /* Los fotogramas ilegibles son normales mientras se encuadra. */ }
      finally { this.decoding = false; }
    }
    if (this.stream) this.frame = requestAnimationFrame(next => void this.scanFrame(next));
  }
  private resolve(rawCode: string): void {
    const code = String(rawCode || '').trim(); if (this.busy()) return;
    if (!code) { this.setStatus('error', 'Ingrese un código', 'Pegue el contenido completo del QR para identificarlo.'); return; }
    this.stopCamera(false);
    if (code !== this.lastCode || !this.operationId) { this.lastCode = code; this.operationId = `qr-scan-${crypto.randomUUID()}`; this.resolved.set(null); }
    const version = ++this.requestVersion; this.busy.set(true);
    this.setStatus('loading', 'Validando el QR', 'Buscando paciente, tratamiento, ciclo y aplicación en la base local.');
    this.http.post<JsonObject>('/api/clinical/qr-scans', { code, operationId: this.operationId }, { withCredentials: true }).subscribe({
      next: payload => {
        if (version !== this.requestVersion) return;
        const patient = this.object(payload['patient']); const treatment = this.object(payload['treatment']); const infusion = this.object(payload['infusion']);
        if (!patient['id'] || !treatment['id'] || !infusion['id'] || String(infusion['patientId']) !== String(patient['id']) || String(infusion['treatmentId']) !== String(treatment['id']) || Number(infusion['cycleNumber'] || 0) < 1) {
          this.resolved.set(null); this.setStatus('error', 'No se pudo identificar el QR', 'La identidad clínica devuelta no es consistente.');
        } else {
          this.resolved.set(payload); this.setStatus('success', 'QR reconocido', 'Verifique nombre, documento, ciclo y día antes de abrir Administración.');
        }
        this.busy.set(false);
      },
      error: response => { if (version !== this.requestVersion) return; this.busy.set(false); this.setStatus('error', 'No se pudo identificar el QR', response?.error?.error || 'Compruebe que corresponda a una aplicación local con turno.'); }
    });
  }
  private async ensureDecoder(): Promise<boolean> {
    const browser = window as QrBrowserWindow;
    if (browser.BarcodeDetector) { try { this.detector ||= new browser.BarcodeDetector({ formats: ['qr_code'] }); return true; } catch { this.detector = null; } }
    if (this.decoder) return true;
    if (!this.decoderLoading) this.decoderLoading = import('jsqr')
      .then((module) => { this.decoder = module.default; return true; })
      .catch(() => false)
      .finally(() => { this.decoderLoading = null; });
    return this.decoderLoading;
  }
  private draw(source: CanvasImageSource, width: number, height: number): { canvas: HTMLCanvasElement; context: CanvasRenderingContext2D } | null {
    const canvas = this.canvasRef?.nativeElement; const context = canvas?.getContext('2d', { willReadFrequently: true });
    if (!canvas || !context || !width || !height) return null;
    const scale = Math.min(1, 1600 / Math.max(width, height)); canvas.width = Math.max(1, Math.round(width * scale)); canvas.height = Math.max(1, Math.round(height * scale));
    context.drawImage(source, 0, 0, canvas.width, canvas.height); return { canvas, context };
  }
  private async decode(canvas: HTMLCanvasElement, context: CanvasRenderingContext2D): Promise<string> {
    if (this.detector) { const values = await this.detector.detect(canvas); const value = String(values[0]?.rawValue || '').trim(); if (value) return value; }
    const decoder = this.decoder; if (!decoder) return '';
    const image = context.getImageData(0, 0, canvas.width, canvas.height);
    return String(decoder(image.data, image.width, image.height, { inversionAttempts: 'attemptBoth' })?.data || '').trim();
  }
  private setStatus(tone: 'idle' | 'loading' | 'success' | 'error', title: string, detail: string): void { this.statusTone.set(tone); this.statusTitle.set(title); this.statusDetail.set(detail); }
  private object(value: unknown): JsonObject { return value && typeof value === 'object' && !Array.isArray(value) ? value as JsonObject : {}; }
}
