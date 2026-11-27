const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
  const pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.stack || error.message));
  await page.goto('http://127.0.0.1:4173/index.html', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2400);

  const introState = await page.locator('#appIntro').evaluate(el => ({
    opacity: getComputedStyle(el).opacity,
    visibility: getComputedStyle(el).visibility,
    pointerEvents: getComputedStyle(el).pointerEvents
  }));
  if (introState.visibility !== 'hidden' && Number(introState.opacity) > 0.05) {
    throw new Error('Intro overlay is still covering the app: ' + JSON.stringify(introState));
  }

  const views = ['quran','prayer','qibla','dua','tasbeeh','tracker','hadith','learn','hajj','calendar','ramadan','settings'];
  for (const view of views) {
    await page.evaluate(() => window.openView('home'));
    const card = page.locator(`.feature[onclick*="openView('${view}')"]`).first();
    await card.click({ timeout: 7000 });
    const state = await page.locator('#' + view + 'View').evaluate(el => ({
      active: el.classList.contains('active'),
      hidden: el.hidden,
      display: getComputedStyle(el).display,
      rect: el.getBoundingClientRect().toJSON(),
      ariaHidden: el.getAttribute('aria-hidden')
    }));
    if (!state.active || state.hidden || state.display === 'none' || state.rect.height < 1 || state.ariaHidden === 'true') {
      throw new Error('Navigation target is not visibly rendered for ' + view + ': ' + JSON.stringify(state));
    }
    const visibleViews = await page.locator('.view').evaluateAll(els => els.filter(el => {
      const s = getComputedStyle(el);
      return s.display !== 'none' && !el.hidden && el.getBoundingClientRect().height > 0;
    }).map(el => el.id));
    if (visibleViews.length !== 1 || visibleViews[0] !== view + 'View') {
      throw new Error('Multiple or incorrect views visible after ' + view + ': ' + JSON.stringify(visibleViews));
    }
    const lastError = await page.evaluate(() => window.__sajwaLastError || '');
    if (lastError) throw new Error('View initialization error in ' + view + ': ' + lastError);
  }

  await page.locator('#theme').click();
  await page.locator('#lang').selectOption('bn');
  await page.waitForTimeout(100);
  await page.locator('#lang').selectOption('en');
  await page.waitForTimeout(100);

  if (pageErrors.length) throw new Error('Browser runtime errors:\\n' + pageErrors.join('\\n---\\n'));
  console.log('SAJWA smoke test passed: intro, 12 visibly exclusive feature routes, theme and language controls.');
  await browser.close();
})().catch(error => {
  console.error(error);
  process.exit(1);
});
