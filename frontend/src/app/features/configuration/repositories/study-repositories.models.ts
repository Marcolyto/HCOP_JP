export interface StudyRepositoryEndpoint {
  readonly key: string;
  readonly label: string;
  readonly url: string;
}

export interface StudyRepository {
  readonly id: string;
  readonly name: string;
  readonly requiresCredentials: boolean;
  readonly username: string;
  readonly hasPassword: boolean;
  readonly endpoints: readonly StudyRepositoryEndpoint[];
}

export type PasswordAction = 'keep' | 'replace' | 'remove';

export interface RepositoryDraft {
  username: string;
  passwordAction: PasswordAction;
  password: string;
  endpoints: { key: string; label: string; url: string }[];
}

export interface RepositoryUpdate {
  readonly username: string;
  readonly passwordAction: PasswordAction;
  readonly password?: string;
  readonly endpoints: readonly { key: string; url: string }[];
}

function record(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

export function normalizeRepositories(payload: unknown): readonly StudyRepository[] {
  const items = record(payload)['items'];
  if (!Array.isArray(items)) throw new Error('Invalid repository response');
  return items.map((value): StudyRepository => {
    const item = record(value);
    const endpoints = item['endpoints'];
    if (typeof item['id'] !== 'string' || !item['id'] || typeof item['name'] !== 'string' || !Array.isArray(endpoints)) {
      throw new Error('Invalid repository');
    }
    return {
      id: item['id'], name: item['name'], requiresCredentials: item['requiresCredentials'] === true,
      username: typeof item['username'] === 'string' ? item['username'] : '', hasPassword: item['hasPassword'] === true,
      endpoints: endpoints.map((value) => {
        const endpoint = record(value);
        if (typeof endpoint['key'] !== 'string' || typeof endpoint['label'] !== 'string' || typeof endpoint['url'] !== 'string') {
          throw new Error('Invalid repository endpoint');
        }
        return { key: endpoint['key'], label: endpoint['label'], url: endpoint['url'] };
      })
    };
  });
}

export function repositoryDraft(item: StudyRepository): RepositoryDraft {
  return { username: item.username, passwordAction: 'keep', password: '', endpoints: item.endpoints.map((endpoint) => ({ ...endpoint })) };
}

export function repositoryUpdate(draft: RepositoryDraft): RepositoryUpdate {
  return {
    username: draft.username.trim(), passwordAction: draft.passwordAction,
    ...(draft.passwordAction === 'replace' ? { password: draft.password } : {}),
    endpoints: draft.endpoints.map(({ key, url }) => ({ key, url: url.trim() }))
  };
}

export function repositoryChanged(item: StudyRepository, draft: RepositoryDraft): boolean {
  return JSON.stringify(repositoryUpdate(draft)) !== JSON.stringify(repositoryUpdate(repositoryDraft(item)));
}

export function repositoryValidation(draft: RepositoryDraft): string {
  if (draft.passwordAction === 'replace' && !draft.password.trim()) return 'Ingrese la nueva contraseña.';
  for (const endpoint of draft.endpoints) {
    const address = endpoint.url.trim();
    if (!address) continue;
    try {
      const parsed = new URL(address);
      const protocols = endpoint.key === 'SOCKET_URL' ? ['ws:', 'wss:'] : ['http:', 'https:'];
      if (!protocols.includes(parsed.protocol) || !parsed.hostname || parsed.username || parsed.password || parsed.hash || /[\s<>"'\\]/.test(address)) {
        throw new Error('Invalid endpoint');
      }
    } catch {
      return `Revise la dirección de «${endpoint.label}».`;
    }
  }
  return '';
}
