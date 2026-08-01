export interface HcopUser {
  id: string;
  username: string;
  email?: string;
  displayName?: string;
  specialty?: string;
  licenseNumber?: string;
  active: boolean;
  roles: string[];
  permissions: string[];
}

export interface SessionResponse {
  ok: boolean;
  authenticated: boolean;
  loginRequired: boolean;
  autoLoginEnabled: boolean;
  user?: HcopUser;
  activePatientId?: string | null;
}

export interface LoginCommand {
  username: string;
  password: string;
}
