import { CommonModule } from '@angular/common';
import {
  AfterViewInit,
  Component,
  HostListener,
  OnDestroy,
  ViewChild,
  computed,
  inject,
  signal
} from '@angular/core';
import { ActivatedRoute, CanDeactivateFn, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfigurationCatalogSection } from '../catalogs/configuration-catalogs.models';
import { ConfigurationCatalogsComponent } from '../catalogs/configuration-catalogs.component';
import { ConfigurationOperationsComponent } from '../operations/configuration-operations.component';
import { OperationsSection } from '../operations/configuration-operations.models';
import { ProtocolConfigurationComponent } from '../protocols/protocol-configuration.component';

export type ConfigurationHubTab =
  | 'protocols'
  | 'diagnoses'
  | 'guides'
  | 'templates'
  | 'calculators'
  | 'research'
  | 'day-hospital'
  | 'llm'
  | 'access';

export const pendingConfigurationChangesGuard: CanDeactivateFn<ConfigurationHubComponent> = (component) =>
  component.canDeactivate();

interface HubTabDescriptor {
  readonly id: ConfigurationHubTab;
  readonly label: string;
  readonly shortLabel: string;
  readonly description: string;
  readonly icon: string;
}

const HUB_TABS: readonly HubTabDescriptor[] = [
  { id: 'protocols', label: 'Protocolos', shortLabel: 'Protocolos', description: 'Esquemas, drogas y tiempos', icon: 'protocol' },
  { id: 'diagnoses', label: 'Diagnósticos', shortLabel: 'Diagnósticos', description: 'SNOMED, CIE-10 y AJCC', icon: 'diagnosis' },
  { id: 'guides', label: 'Guías', shortLabel: 'Guías', description: 'Documentos clínicos', icon: 'guides' },
  { id: 'templates', label: 'Plantillas', shortLabel: 'Plantillas', description: 'Imágenes anatómicas', icon: 'templates' },
  { id: 'calculators', label: 'Calculadoras y scores', shortLabel: 'Calculadoras', description: 'Herramientas configurables', icon: 'calculator' },
  { id: 'research', label: 'Investigación', shortLabel: 'Investigación', description: 'Formularios personalizados', icon: 'research' },
  { id: 'day-hospital', label: 'Hospital de día', shortLabel: 'H. de día', description: 'Sillones, jornada y turnos', icon: 'day-hospital' },
  { id: 'llm', label: 'IA', shortLabel: 'IA', description: 'Conexión con el modelo', icon: 'llm' },
  { id: 'access', label: 'Usuarios y permisos', shortLabel: 'Usuarios', description: 'Accesos, roles y seguridad', icon: 'access' }
];

const CATALOG_TABS: Readonly<Partial<Record<ConfigurationHubTab, ConfigurationCatalogSection>>> = {
  diagnoses: 'diagnoses',
  guides: 'guides',
  templates: 'templates'
};

const OPERATIONS_TABS: Readonly<Partial<Record<ConfigurationHubTab, OperationsSection>>> = {
  calculators: 'calculators',
  research: 'research',
  'day-hospital': 'day-hospital',
  llm: 'llm',
  access: 'access'
};

@Component({
  selector: 'app-configuration-hub',
  imports: [
    CommonModule,
    RouterLink,
    ProtocolConfigurationComponent,
    ConfigurationCatalogsComponent,
    ConfigurationOperationsComponent
  ],
  host: { class: 'configuration-hub-host' },
  templateUrl: './configuration-hub.component.html',
  styleUrl: './configuration-hub.component.scss'
})
export class ConfigurationHubComponent implements AfterViewInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly subscriptions = new Subscription();
  private viewReady = false;

  @ViewChild(ProtocolConfigurationComponent) private protocols?: ProtocolConfigurationComponent;
  @ViewChild(ConfigurationCatalogsComponent) private catalogs?: ConfigurationCatalogsComponent;
  @ViewChild(ConfigurationOperationsComponent) private operations?: ConfigurationOperationsComponent;

  readonly tabs = HUB_TABS;
  readonly activeTab = signal<ConfigurationHubTab>('protocols');
  readonly canView = computed(() => this.auth.hasPermission('section.configuration.view'));
  readonly canManage = computed(() => this.auth.hasPermission('section.configuration.manage'));
  readonly canEditProtocols = computed(() =>
    this.canManage() && this.auth.hasPermission('section.protocols.edit'));
  readonly activeDescriptor = computed(() =>
    this.tabs.find((tab) => tab.id === this.activeTab()) ?? this.tabs[0]!);
  readonly catalogSection = computed<ConfigurationCatalogSection>(() =>
    CATALOG_TABS[this.activeTab()] ?? 'diagnoses');
  readonly operationsSection = computed<OperationsSection>(() =>
    OPERATIONS_TABS[this.activeTab()] ?? 'calculators');

  constructor() {
    this.subscriptions.add(this.route.queryParamMap.subscribe((parameters) => {
      const requested = this.asTab(parameters.get('tab'));
      if (!requested || requested === this.activeTab()) return;
      if (!this.viewReady) {
        this.activeTab.set(requested);
        return;
      }
      if (!this.activateChild(requested, true)) return void this.writeTabToUrl(this.activeTab(), true);
      this.activeTab.set(requested);
    }));
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.activateChild(this.activeTab());
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  selectTab(tab: ConfigurationHubTab): void {
    if (tab === this.activeTab() || !this.canView()) return;
    if (!this.activateChild(tab, true)) return;
    this.activeTab.set(tab);
    void this.writeTabToUrl(tab, false);
  }

  refreshActive(): void {
    const tab = this.activeTab();
    if (tab === 'protocols') {
      this.protocols?.reload();
      return;
    }
    const catalog = CATALOG_TABS[tab];
    if (catalog) {
      if (!this.catalogs?.confirmDiscardChanges('Actualizar descartará los cambios que todavía no guardó. ¿Desea continuar?')) return;
      if (catalog === 'diagnoses') void this.catalogs?.loadDiagnoses(this.catalogs.selectedDiagnosis()?.id ?? '');
      if (catalog === 'guides') void this.catalogs?.loadGuides(this.catalogs.selectedGuide()?.name ?? '');
      if (catalog === 'templates') void this.catalogs?.loadTemplates(this.catalogs.selectedTemplate()?.id ?? '');
      return;
    }
    this.operations?.reload();
  }

  canDeactivate(): boolean {
    return this.canLeaveCurrentTab();
  }

  panelId(tab: ConfigurationHubTab): string {
    if (tab === 'protocols') return 'configuration-panel-protocols';
    if (CATALOG_TABS[tab]) return 'configuration-panel-catalogs';
    return 'configuration-panel-operations';
  }

  @HostListener('window:beforeunload', ['$event'])
  protectBrowserClose(event: BeforeUnloadEvent): void {
    if (!this.hasUnsavedChangesInCurrentTab()) return;
    event.preventDefault();
  }

  private activateChild(tab: ConfigurationHubTab, protectDirty = false): boolean {
    if (protectDirty && !this.canLeaveCurrentTab()) return false;
    const catalog = CATALOG_TABS[tab];
    if (catalog) {
      this.catalogs?.activate(catalog, false);
      return !this.catalogs || this.catalogs.activeSection() === catalog;
    }
    const operation = OPERATIONS_TABS[tab];
    if (operation && this.operations && !this.operations.selectSection(operation)) return false;
    return true;
  }

  private canLeaveCurrentTab(): boolean {
    const tab = this.activeTab();
    if (tab === 'protocols') return this.protocols?.confirmDiscardAndRestore() ?? true;
    const catalog = CATALOG_TABS[tab];
    if (catalog) return this.catalogs?.confirmDiscardChanges() ?? true;
    const operation = OPERATIONS_TABS[tab];
    return operation ? this.operations?.confirmDiscardChanges(operation) ?? true : true;
  }

  private hasUnsavedChangesInCurrentTab(): boolean {
    const tab = this.activeTab();
    if (tab === 'protocols') return this.protocols?.hasUnsavedChanges() ?? false;
    const catalog = CATALOG_TABS[tab];
    if (catalog) return this.catalogs?.hasUnsavedChanges() ?? false;
    const operation = OPERATIONS_TABS[tab];
    return operation ? this.operations?.hasUnsavedChanges(operation) ?? false : false;
  }

  private asTab(value: string | null): ConfigurationHubTab | null {
    if (!value) return null;
    return this.tabs.some((tab) => tab.id === value) ? value as ConfigurationHubTab : null;
  }

  private writeTabToUrl(tab: ConfigurationHubTab, replaceUrl: boolean): Promise<boolean> {
    return this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab },
      queryParamsHandling: 'merge',
      replaceUrl
    });
  }
}
