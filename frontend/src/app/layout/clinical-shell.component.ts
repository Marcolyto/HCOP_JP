import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';

@Component({
  selector: 'app-clinical-shell',
  imports: [RouterLink, RouterOutlet],
  templateUrl: './clinical-shell.component.html',
  styleUrl: './clinical-shell.component.scss'
})
export class ClinicalShellComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  logout(): void {
    this.auth.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
