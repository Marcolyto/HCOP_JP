import { Component, HostListener, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { PatientWorkspaceService } from './core/patients/patient-workspace.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />'
})
export class AppComponent {
  private readonly workspace = inject(PatientWorkspaceService);
  @HostListener('window:beforeunload', ['$event'])
  protectPendingDraft(event: BeforeUnloadEvent): void {
    if (!this.workspace.hasPendingClinicalWork()) return;
    event.preventDefault();
    event.returnValue = '';
  }
}
