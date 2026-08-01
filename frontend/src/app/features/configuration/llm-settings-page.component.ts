import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, finalize } from 'rxjs';
import { ApiClientService } from '../../core/api/api-client.service';
import { ApiError } from '../../core/api/api-error';

interface LlmSettings { enabled: boolean; provider: string; baseUrl: string; model: string; temperature: number; maxTokens: number; timeoutMs: number; hasApiKey: boolean; lockedFields: string[]; }
interface LlmTestResponse { model?: string; response?: string; message?: string; }
interface LlmConfigResponse { llm: LlmSettings; }

@Component({
  selector: 'app-llm-settings-page',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './llm-settings-page.component.html',
  styleUrl: './llm-settings-page.component.scss'
})
export class LlmSettingsPageComponent {
  private readonly api = inject(ApiClientService);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly testing = signal(false);
  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly locked = signal<string[]>([]);
  readonly keyConfigured = signal(false);
  readonly form = new FormGroup({
    enabled: new FormControl(false, { nonNullable: true }),
    provider: new FormControl('openai-compatible', { nonNullable: true }),
    baseUrl: new FormControl('', { nonNullable: true }),
    model: new FormControl('', { nonNullable: true }),
    temperature: new FormControl(0.2, { nonNullable: true }),
    maxTokens: new FormControl(1200, { nonNullable: true }),
    timeoutMs: new FormControl(60000, { nonNullable: true }),
    apiKeyAction: new FormControl('keep', { nonNullable: true }),
    apiKey: new FormControl('', { nonNullable: true })
  });

  constructor() { this.load(); }

  get showingApiKey() { return this.form.controls.apiKeyAction.value === 'replace'; }
  isLocked(field: string) { return this.locked().includes(field); }

  load() {
    this.loading.set(true); this.error.set(null); this.message.set(null);
    this.api.get<{ llm: LlmSettings }>('/api/config').pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => {
        const value = response.llm;
        this.locked.set(value.lockedFields ?? []);
        this.keyConfigured.set(Boolean(value.hasApiKey));
        this.form.patchValue({ enabled: value.enabled, provider: value.provider, baseUrl: value.baseUrl, model: value.model, temperature: value.temperature, maxTokens: value.maxTokens, timeoutMs: value.timeoutMs, apiKeyAction: 'keep', apiKey: '' });
      }, error: (error) => this.error.set(ApiError.from(error).message)
    });
  }

  preset(name: 'ollama' | 'lm-studio' | 'gemini') {
    const presets = {
      ollama: { provider: 'ollama', baseUrl: 'http://127.0.0.1:11434', model: 'llama3.2' },
      'lm-studio': { provider: 'lm-studio', baseUrl: 'http://127.0.0.1:1234/v1', model: 'local-model' },
      gemini: { provider: 'gemini', baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai', model: 'gemini-3.5-flash' }
    };
    if (this.isLocked('baseUrl') || this.isLocked('model')) return;
    this.form.patchValue(presets[name]);
    this.message.set('Configuracion rapida aplicada. Revise y guarde para confirmarla.');
  }

  test() { this.send(true); }
  save() { this.send(false); }

  private send(testOnly: boolean) {
    const value = this.form.getRawValue();
    if (!this.valid(value.baseUrl, value.model)) return;
    const llm = { ...value, apiKey: value.apiKeyAction === 'replace' ? value.apiKey : undefined };
    this.error.set(null); this.message.set(null);
    const busy = testOnly ? this.testing : this.saving;
    busy.set(true);
    const request: Observable<LlmTestResponse | LlmConfigResponse> = testOnly
      ? this.api.post<LlmTestResponse>('/api/llm/test', { llm })
      : this.api.put<LlmConfigResponse>('/api/config', { llm });
    request.pipe(finalize(() => busy.set(false))).subscribe({
      next: (response) => {
        this.form.controls.apiKey.setValue('');
        if (testOnly && 'model' in response) this.message.set(`Conexion correcta${response.model ? ` · ${response.model}` : ''}${response.response ? ` · ${response.response}` : ''}`);
        else { this.message.set('Configuracion guardada.'); this.load(); }
      }, error: (error) => this.error.set(ApiError.from(error).message)
    });
  }

  private valid(baseUrl: string, model: string) {
    try { const url = new URL(baseUrl); if (!['http:', 'https:'].includes(url.protocol) || !model.trim()) throw new Error(); return true; }
    catch { this.error.set('Complete un endpoint HTTP o HTTPS valido y el nombre del modelo.'); return false; }
  }
}
