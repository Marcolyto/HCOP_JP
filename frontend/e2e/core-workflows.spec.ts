import { expect, test, type Locator, type Page } from '@playwright/test';

const username = process.env['HCOP_E2E_USERNAME'] || 'qa_browser';
const password = process.env['HCOP_E2E_PASSWORD'] || '';
const examplePatient = 'Test Savatierra, Tomas Alejandro';

async function loginThroughUi(page: Page): Promise<void> {
  expect(password, 'HCOP_E2E_PASSWORD debe estar definido por el lanzador descartable.').not.toBe('');
  await page.goto('./#/login');
  await expect(page.getByRole('heading', { name: 'HCOP Centro Oncologico' })).toBeVisible();
  await page.getByLabel('Usuario').fill(username);
  await page.getByLabel(/Contrase/).fill(password);

  const loginResponse = page.waitForResponse((response) =>
    response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/auth/login');
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  expect((await loginResponse).status()).toBe(200);

  await expect(page).toHaveURL(/\/app\/#\/?$/);
  await expect(page.locator('.app-header')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Abrir paciente', exact: true })).toBeVisible();

  const balancedPanels = page.getByRole('button', { name: 'Dejar Historia y Estudios a la mitad' });
  if (await balancedPanels.getAttribute('aria-pressed') !== 'true') await balancedPanels.click();
  await expect(page.locator('#clinicalPanel')).toBeVisible();
}

async function openExamplePatient(page: Page): Promise<void> {
  await page.locator('.app-header').getByRole('button', { name: 'Abrir paciente', exact: true }).click();
  const picker = page.getByRole('dialog', { name: 'Abrir historia de paciente' });
  await expect(picker).toBeVisible();

  const searchResponse = page.waitForResponse((response) =>
    response.request().method() === 'GET'
      && new URL(response.url()).pathname === '/api/clinical/patients'
      && new URL(response.url()).searchParams.get('q') === 'Test Savatierra');
  await picker.getByLabel('Buscar paciente').fill('Test Savatierra');
  expect((await searchResponse).status()).toBe(200);

  const result = picker.getByRole('option').filter({ hasText: examplePatient });
  await expect(result).toHaveCount(1);
  const activation = page.waitForResponse((response) =>
    response.request().method() === 'POST'
      && /\/api\/clinical\/patients\/[^/]+\/activate$/.test(new URL(response.url()).pathname));
  await result.click();
  expect((await activation).status()).toBe(200);
  await expect(picker).toHaveCount(0);
  await expect(page.locator('#clinicalDocument')).toContainText(examplePatient);
}

async function gridPosition(locator: Locator): Promise<{ row: string; column: string }> {
  return locator.evaluate((element) => {
    const style = getComputedStyle(element);
    return { row: style.gridRowStart, column: style.gridColumnStart };
  });
}

test.describe('circuitos esenciales del frontend Angular', () => {
  test.use({ viewport: { width: 1600, height: 1000 } });

  test('inicia sesión por la interfaz y abre la historia sintética sin datos reales', async ({ page }) => {
    await loginThroughUi(page);
    await expect(page.locator('#clinicalDocument')).toHaveCount(0);
    await expect(page.getByText('No hay un paciente abierto.', { exact: true })).toBeVisible();

    await openExamplePatient(page);
    const document = page.locator('#clinicalDocument');
    await expect(document.getByRole('heading', { name: examplePatient })).toBeVisible();
    await expect(document).toContainText('DNI 99000002');
    await expect(document.getByRole('heading', { name: 'Diagnóstico oncológico' })).toBeVisible();
    await expect(document.getByRole('heading', { name: 'Actividad clínica cronológica' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cerrar paciente' })).toBeEnabled();
  });

  test('navega por Configuración y conserva el paciente abierto al regresar', async ({ page }) => {
    await loginThroughUi(page);
    await openExamplePatient(page);

    await page.locator('.configuration-button').click();
    await expect(page).toHaveURL(/\/app\/#\/configuration/);
    await expect(page.locator('.configuration-title strong')).toHaveText('Configuración');

    const tablist = page.getByRole('tablist', { name: /Secciones de configuraci/ });
    await expect(tablist.getByRole('tab')).toHaveCount(9);
    const protocols = tablist.getByRole('tab', { name: /^Protocolos/ });
    await expect(protocols).toHaveAttribute('aria-selected', 'true');

    const diagnoses = tablist.getByRole('tab', { name: /^Diagn/ });
    await diagnoses.click();
    await expect(diagnoses).toHaveAttribute('aria-selected', 'true');
    await expect(page).toHaveURL(/(?:\?|&)tab=diagnoses(?:&|$)/);
    await expect(page.locator('#configuration-panel-catalogs')).toHaveAttribute('aria-hidden', 'false');

    const dayHospital = tablist.getByRole('tab', { name: /^H\. de d/ });
    await dayHospital.click();
    await expect(dayHospital).toHaveAttribute('aria-selected', 'true');
    await expect(page).toHaveURL(/(?:\?|&)tab=day-hospital(?:&|$)/);
    await expect(page.locator('#configuration-panel-operations')).toHaveAttribute('aria-hidden', 'false');
    await expect(page.getByRole('heading', { name: 'Capacidad y jornada de Hospital de día' })).toBeVisible();
    await expect(
      page.getByRole('combobox', { name: 'Fracción mínima' }).locator('option:checked'),
    ).toHaveText('Cada 10 minutos');
    await expect(page.getByText(/48 casilleros de 10 minutos por sillón.*2 filas × 3 columnas/)).toBeVisible();

    await page.getByRole('link', { name: /Volver a la historia cl/ }).click();
    await expect(page).toHaveURL(/\/app\/#\/?$/);
    await expect(page.locator('#clinicalDocument')).toContainText(examplePatient);
  });

  test('mantiene la matriz compacta de 10 minutos y sus controles accesibles', async ({ page }) => {
    await loginThroughUi(page);
    await page.getByRole('button', { name: 'Hospital de dia', exact: true }).click();

    const dialog = page.getByRole('dialog', { name: 'Hospital de dia' });
    await expect(dialog).toBeVisible();
    const chairsTab = dialog.getByRole('tab', { name: 'Turnos y sala', exact: true });
    await expect(chairsTab).toHaveAttribute('aria-selected', 'true');
    await expect(dialog.getByRole('tab', { name: 'Agenda', exact: true })).toHaveAttribute('aria-selected', 'true');
    await expect(dialog.getByRole('searchbox', { name: 'Buscar en la espera y en los sillones' })).toBeVisible();

    const grid = dialog.getByRole('grid', { name: 'Agenda de sillones' });
    await expect(grid).toBeVisible();
    await expect(grid).toHaveAttribute('data-slot-minutes', '10');
    await expect(grid).toHaveAttribute('data-columns-per-chair', '3');
    await expect(grid.locator('.angular-chair-header')).toHaveCount(6);
    await expect(grid.getByRole('gridcell')).toHaveCount(48 * 6);

    const chairOne = (slot: number) => grid.locator(`.angular-empty-slot[data-chair="1"][data-slot-index="${slot}"]`);
    const chairTwoFirst = grid.locator('.angular-empty-slot[data-chair="2"][data-slot-index="0"]');
    await expect(chairOne(0)).toHaveAttribute('data-time', '08:00');
    await expect(chairOne(5)).toHaveAttribute('data-time', '08:50');
    await expect(chairOne(6)).toHaveAttribute('data-time', '09:00');
    await expect(chairOne(0)).toHaveAttribute('aria-label', 'Sillón 1, 08:00');

    expect(await gridPosition(chairOne(0))).toEqual({ row: '2', column: '1' });
    expect(await gridPosition(chairOne(1))).toEqual({ row: '2', column: '2' });
    expect(await gridPosition(chairOne(2))).toEqual({ row: '2', column: '3' });
    expect(await gridPosition(chairOne(3))).toEqual({ row: '3', column: '1' });
    expect(await gridPosition(chairTwoFirst)).toEqual({ row: '2', column: '4' });

    const cells = await Promise.all([chairOne(0), chairOne(1), chairOne(2), chairOne(3)].map((cell) => cell.boundingBox()));
    expect(cells.every(Boolean)).toBe(true);
    expect(Math.abs(cells[0]!.y - cells[1]!.y)).toBeLessThan(1);
    expect(Math.abs(cells[1]!.y - cells[2]!.y)).toBeLessThan(1);
    expect(cells[3]!.y).toBeGreaterThan(cells[0]!.y);
    expect(cells[1]!.x).toBeGreaterThan(cells[0]!.x);
    expect(cells[2]!.x).toBeGreaterThan(cells[1]!.x);

    const chairControls = dialog.getByRole('navigation', { name: 'Sillones visibles' });
    await expect(chairControls.getByText(/Sillones 1/)).toBeVisible();
    await chairControls.getByRole('button', { name: 'Acercar' }).click();
    await expect(grid.locator('.angular-chair-header')).toHaveCount(5);
    await expect(chairControls.getByText(/Sillones 1.*5/)).toBeVisible();
    await chairControls.getByRole('button', { name: 'Alejar' }).click();
    await expect(grid.locator('.angular-chair-header')).toHaveCount(6);

    const dateInput = dialog.getByLabel('Elegir fecha del turnero');
    const initialDate = await dateInput.inputValue();
    await dialog.getByRole('button', { name: 'Dia siguiente' }).click();
    await expect(dateInput).not.toHaveValue(initialDate);
    await dialog.getByRole('button', { name: 'Dia anterior' }).click();
    await expect(dateInput).toHaveValue(initialDate);
    await expect(dialog.getByRole('button', { name: 'Cerrar Hospital de dia' })).toBeVisible();
  });
});
