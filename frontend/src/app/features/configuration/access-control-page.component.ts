import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { forkJoin, finalize } from 'rxjs';
import { ApiClientService } from '../../core/api/api-client.service';
import { ApiError } from '../../core/api/api-error';

interface RoleSummary { id: string; key: string; name: string; description: string; active: boolean; system: boolean; userCount?: number; permissions: string[]; }
interface User { id: string; username: string; email: string; displayName: string; specialty: string; licenseNumber: string; active: boolean; lastLoginAt: string; roles: RoleSummary[]; }
interface Permission { key: string; name: string; description: string; }
interface Security { loginRequired: boolean; sessionDurationMinutes: number; revision: number; }

@Component({
  selector: 'app-access-control-page',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './access-control-page.component.html',
  styleUrl: './access-control-page.component.scss'
})
export class AccessControlPageComponent {
  private readonly api = inject(ApiClientService);
  readonly users = signal<User[]>([]);
  readonly roles = signal<RoleSummary[]>([]);
  readonly permissions = signal<Permission[]>([]);
  readonly selectedUser = signal<User | null>(null);
  readonly selectedRole = signal<RoleSummary | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly tab = signal<'users' | 'roles' | 'security'>('users');
  readonly userForm = new FormGroup({ id: new FormControl('', { nonNullable: true }), username: new FormControl('', { nonNullable: true }), email: new FormControl('', { nonNullable: true }), displayName: new FormControl('', { nonNullable: true }), specialty: new FormControl('', { nonNullable: true }), licenseNumber: new FormControl('', { nonNullable: true }), password: new FormControl('', { nonNullable: true }), active: new FormControl(true, { nonNullable: true }), roleIds: new FormControl<string[]>([], { nonNullable: true }) });
  readonly roleForm = new FormGroup({ id: new FormControl('', { nonNullable: true }), key: new FormControl('', { nonNullable: true }), name: new FormControl('', { nonNullable: true }), description: new FormControl('', { nonNullable: true }), active: new FormControl(true, { nonNullable: true }), permissions: new FormControl<string[]>([], { nonNullable: true }) });
  readonly securityForm = new FormGroup({ sessionDurationMinutes: new FormControl(43200, { nonNullable: true }), revision: new FormControl(0, { nonNullable: true }) });

  constructor() { this.load(); }
  setTab(tab: 'users' | 'roles' | 'security') { this.tab.set(tab); this.error.set(null); this.notice.set(null); }

  load() {
    this.loading.set(true); this.error.set(null);
    forkJoin({ users: this.api.get<{ users: User[] }>('/api/admin/users'), roles: this.api.get<{ roles: RoleSummary[]; permissionCatalog: Permission[] }>('/api/admin/roles'), security: this.api.get<{ item: Security }>('/api/admin/security-settings') }).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => { this.users.set(response.users.users ?? []); this.roles.set(response.roles.roles ?? []); this.permissions.set(response.roles.permissionCatalog ?? []); this.securityForm.setValue({ sessionDurationMinutes: response.security.item.sessionDurationMinutes, revision: response.security.item.revision }); },
      error: (error) => this.error.set(ApiError.from(error).message)
    });
  }

  selectUser(user: User) { this.selectedUser.set(user); this.userForm.setValue({ id: user.id, username: user.username, email: user.email, displayName: user.displayName, specialty: user.specialty || '', licenseNumber: user.licenseNumber || '', password: '', active: user.active, roleIds: user.roles.map((role) => role.id) }); }
  newUser() { this.selectedUser.set(null); this.userForm.setValue({ id: '', username: '', email: '', displayName: '', specialty: '', licenseNumber: '', password: '', active: true, roleIds: [] }); }
  selectRole(role: RoleSummary) { this.selectedRole.set(role); this.roleForm.setValue({ id: role.id, key: role.key, name: role.name, description: role.description || '', active: role.active, permissions: role.permissions ?? [] }); }
  newRole() { this.selectedRole.set(null); this.roleForm.setValue({ id: '', key: '', name: '', description: '', active: true, permissions: [] }); }

  toggleUserRole(id: string, checked: boolean) { this.userForm.controls.roleIds.setValue(this.toggle(this.userForm.controls.roleIds.value, id, checked)); }
  togglePermission(key: string, checked: boolean) { this.roleForm.controls.permissions.setValue(this.toggle(this.roleForm.controls.permissions.value, key, checked)); }
  userRoleNames(user: User) { return user.roles.map((role) => role.name).join(', '); }

  saveUser() {
    const value = this.userForm.getRawValue();
    if (!value.username || !value.email || !value.displayName || !value.roleIds.length || (!value.id && !value.password)) { this.error.set('Usuario, correo, nombre, al menos un rol y contraseña para un alta son obligatorios.'); return; }
    const body = { ...value, roleIds: value.roleIds.map(Number) };
    this.saving.set(true); this.error.set(null); this.notice.set(null);
    const request = value.id ? this.api.put(`/api/admin/users/${encodeURIComponent(value.id)}`, body) : this.api.post('/api/admin/users', body);
    request.pipe(finalize(() => this.saving.set(false))).subscribe({ next: () => { this.notice.set('Usuario guardado. Si se cambió la clave o se desactivó, sus sesiones fueron revocadas.'); this.load(); }, error: (error) => this.error.set(ApiError.from(error).message) });
  }

  saveRole() {
    const value = this.roleForm.getRawValue();
    if (!value.name || (!value.id && !value.key)) { this.error.set('La clave y el nombre son obligatorios para crear un rol.'); return; }
    this.saving.set(true); this.error.set(null); this.notice.set(null);
    const request = value.id ? this.api.put(`/api/admin/roles/${encodeURIComponent(value.id)}`, value) : this.api.post('/api/admin/roles', value);
    request.pipe(finalize(() => this.saving.set(false))).subscribe({ next: () => { this.notice.set('Rol y permisos guardados.'); this.load(); }, error: (error) => this.error.set(ApiError.from(error).message) });
  }

  saveSecurity() {
    const value = this.securityForm.getRawValue();
    this.saving.set(true); this.error.set(null); this.notice.set(null);
    this.api.put('/api/admin/security-settings', { loginRequired: true, sessionDurationMinutes: Number(value.sessionDurationMinutes) }).pipe(finalize(() => this.saving.set(false))).subscribe({ next: () => { this.notice.set('Seguridad de sesión actualizada.'); this.load(); }, error: (error) => this.error.set(ApiError.from(error).message) });
  }

  private toggle(values: string[], value: string, checked: boolean) { return checked ? Array.from(new Set([...values, value])) : values.filter((item) => item !== value); }
}
