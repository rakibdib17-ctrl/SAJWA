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


  // Focused regression checks for the reported language, Quran theme and prayer issues.
  await page.evaluate(() => { localStorage.setItem('sajwa-theme','royal'); applySajwaTheme('royal'); setTheme(true); });
  await page.locator('#lang').selectOption('bn');
  await page.evaluate(() => window.openView('quran'));
  await page.waitForTimeout(250);
  const quranBn = await page.evaluate(() => ({
    notice: document.querySelector('#quranView .notice')?.textContent || '',
    surahMode: document.getElementById('surahModeBtn')?.textContent || '',
    mushafMode: document.getElementById('mushafModeBtn')?.textContent || '',
    theme: getComputedStyle(document.querySelector('#quranView .readingpanel')).backgroundColor,
    royal: document.body.dataset.theme
  }));
  if (!quranBn.notice.includes('কুরআনের আরবি পাঠ') || !quranBn.surahMode.includes('সূরা') || !quranBn.mushafMode.includes('মুসহাফ')) {
    throw new Error('Quran Bengali localization regression: ' + JSON.stringify(quranBn));
  }
  if (quranBn.royal !== 'royal') throw new Error('Royal theme was not retained on Quran view.');
  await page.locator('#lang').selectOption('en');
  await page.waitForTimeout(100);
  const quranEn = await page.evaluate(() => ({
    notice: document.querySelector('#quranView .notice')?.textContent || '',
    surahMode: document.getElementById('surahModeBtn')?.textContent || '',
    mushafMode: document.getElementById('mushafModeBtn')?.textContent || ''
  }));
  if (!quranEn.notice.includes('Quran text and translations') || !quranEn.surahMode.toLowerCase().includes('surah') || !quranEn.mushafMode.toLowerCase().includes('mushaf')) {
    throw new Error('Quran English localization regression: ' + JSON.stringify(quranEn));
  }

  await page.locator('#lang').selectOption('bn');
  await page.evaluate(() => window.openView('qibla'));
  await page.waitForTimeout(100);
  const qiblaBn = await page.locator('#qiblaView').innerText();
  if (!qiblaBn.includes('কিবলার দিক') || !qiblaBn.includes('কম্পাস চালু করুন')) throw new Error('Qibla Bengali localization regression.');

  await page.evaluate(() => window.openView('hadith'));
  await page.waitForTimeout(100);
  const hadithBn = await page.locator('#hadithNotice').innerText();
  if (!hadithBn.includes('বহুভাষিক Hadislam')) throw new Error('Hadith notice Bengali localization regression.');

  await page.evaluate(() => window.openView('learn'));
  await page.evaluate(() => showLearnTab('daily'));
  await page.waitForTimeout(100);
  await page.locator('#lang').selectOption('en');
  await page.evaluate(() => showLearnTab('daily'));
  const learnSource = await page.locator('#learnContent .learn-source').first().innerText();
  if (!learnSource.startsWith('Source:') || !learnSource.includes('Sahih al-Bukhari')) {
    throw new Error('Daily dua English source/reference regression: ' + learnSource);
  }

  await page.evaluate(() => {
    prayerData = { times: [
      {name:'Fajr',time:'05:23 AM'}, {name:'Sunrise',time:'06:42 AM'},
      {name:'Dhuhr',time:'12:10 PM'}, {name:'Asr',time:'03:12 PM'},
      {name:'Maghrib',time:'05:32 PM'}, {name:'Isha',time:'06:50 PM'}
    ]};
    renderPrayerDashboard();
  });
  const prayerCards = await page.locator('#prayerGrid .prayer-cell').count();
  const prayerLabels = await page.locator('#prayerGrid .prayer-cell-name').allTextContents();
  if (prayerCards !== 6 || prayerLabels.some(x => !x.trim())) {
    throw new Error('Prayer schedule must render six named cards: ' + JSON.stringify({prayerCards,prayerLabels}));
  }
  await page.locator('#lang').selectOption('bn');
  await page.waitForTimeout(100);
  const prayerBn = await page.locator('#prayerGrid .prayer-cell-name').allTextContents();
  if (prayerBn[0] !== 'ফজর' || prayerBn[4] !== 'মাগরিব') {
    throw new Error('Prayer schedule Bengali labels regression: ' + JSON.stringify(prayerBn));
  }

  if (pageErrors.length) throw new Error('Browser runtime errors:\\n' + pageErrors.join('\\n---\\n'));
  console.log('SAJWA smoke test passed: intro, 12 visibly exclusive feature routes, theme and language controls.');
  await browser.close();
})().catch(error => {
  console.error(error);
  process.exit(1);
});
