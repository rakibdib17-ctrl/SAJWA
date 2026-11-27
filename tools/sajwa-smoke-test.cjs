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
    const card = page.locator('.feature[onclick="openView(\'' + view + '\')"]').first();
    await card.click();
    const active = await page.locator('#' + view + 'View').evaluate(el => el.classList.contains('active'));
    if (!active) throw new Error('Navigation failed for view: ' + view);
    const lastError = await page.evaluate(() => window.__sajwaLastError || '');
    if (lastError) throw new Error('View initialization error in ' + view + ': ' + lastError);
  }

  await page.locator('#theme').click();
  await page.locator('#lang').selectOption('bn');
  await page.waitForTimeout(100);
  await page.locator('#lang').selectOption('en');
  await page.waitForTimeout(100);

  if (pageErrors.length) throw new Error('Browser runtime errors:\n' + pageErrors.join('\n---\n'));
  console.log('SAJWA smoke test passed: intro, 12 feature routes, theme and language controls.');
  await browser.close();
})().catch(error => {
  console.error(error);
  process.exit(1);
});
