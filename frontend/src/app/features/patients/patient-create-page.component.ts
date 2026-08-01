import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, switchMap } from 'rxjs';
import { ApiClientService } from '../../core/api/api-client.service';
import { ApiError } from '../../core/api/api-error';

interface PatientCreateResponse { ok: boolean; patientId: string; }

@Component({ selector: 'app-patient-create-page', imports: [ReactiveFormsModule, RouterLink], templateUrl: './patient-create-page.component.html', styleUrl: './patient-create-page.component.scss' })
export class PatientCreatePageComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly api = inject(ApiClientService);
  private readonly router = inject(Router);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = this.formBuilder.nonNullable.group({
    lastName: ['', [Validators.required, Validators.maxLength(120)]], firstName: ['', [Validators.required, Validators.maxLength(120)]], dni: ['', [Validators.required, Validators.maxLength(30)]], medicalRecord: ['', Validators.maxLength(80)], birthDate: [''], sex: [''], insurance: ['', Validators.maxLength(160)], affiliateNumber: ['', Validators.maxLength(80)], phone: ['', Validators.maxLength(80)], email: ['', [Validators.email, Validators.maxLength(160)]], address: ['', Validators.maxLength(300)]
  });
  save(): void {
    this.error.set(null);
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving.set(true);
    this.api.post<PatientCreateResponse>('/api/clinical/patients', this.form.getRawValue()).pipe(switchMap((created) => this.api.post<{ patientId?: string }>(`/api/clinical/patients/${created.patientId}/activate`)), finalize(() => this.saving.set(false))).subscribe({ next: (patient) => this.router.navigate(['/patients', patient.patientId]), error: (error: unknown) => this.error.set(ApiError.from(error).message) });
  }
  invalid(name: string): boolean { const field = this.form.get(name); return Boolean(field?.invalid && (field.dirty || field.touched)); }
}
