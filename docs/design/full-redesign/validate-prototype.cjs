/* Browser checks for the design prototype, not Android app tests. */
const { chromium } = require('playwright');
const { resolve, join } = require('node:path');
const { mkdirSync, writeFileSync } = require('node:fs');
const { pathToFileURL } = require('node:url');

(async () => {
  const out = join(__dirname, 'preview');
  mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  const checks = [];
  const check = (name, ok) => { checks.push({ name, passed: !!ok }); if (!ok) throw new Error(name); };
  const click = async (action, extra = '') => page.locator(`button[data-action="${action}"]${extra}`).first().click();
  const reload = async () => page.goto(pathToFileURL(resolve(__dirname, 'prototype.html')).href);
  try {
    await reload();
    check('Initial library has two ready apps and one draft', await page.locator('.appcard').count() === 2 && await page.locator('.listrow').count() === 1);
    await click('search');
    await page.locator('#app-search').fill('study');
    check('Search filters app list', await page.locator('.appcard').count() === 1);
    await click('clear-search');
    check('Clear search restores apps', await page.locator('.appcard').count() === 2);
    await click('edit', '[data-id="trip"]');
    await page.locator('#draft-project').fill('Keep this unsent idea');
    await click('tab-plan');
    await click('tab-chat');
    check('Project draft survives view changes', await page.locator('#draft-project').inputValue() === 'Keep this unsent idea');
    await page.locator('#draft-project').fill('Add an optional note to each expense');
    await page.locator('form[data-form="project"]').evaluate(f => f.requestSubmit());
    check('Change preparation starts without applying', await page.evaluate(() => state.job?.type === 'edit' && !project().note));
    await page.evaluate(() => openProject('routine'));
    await page.locator('#draft-project').fill('A different project request');
    await page.locator('form[data-form="project"]').evaluate(f => f.requestSubmit());
    check('Requests never queue into another project context', await page.evaluate(() => state.job.projectId === 'trip' && state.queue.length === 0 && project().draft === 'A different project request'));
    await page.evaluate(() => openProject('trip'));
    await page.locator('#draft-project').fill('Keep the old records');
    await page.locator('form[data-form="project"]').evaluate(f => f.requestSubmit());
    check('Follow-up can queue while preparing', await page.evaluate(() => state.queue[0] === 'Keep the old records'));
    await click('finish');
    check('Edit finishes in review state', await page.evaluate(() => state.review && !project().note));
    await click('review');
    await click('review-preview');
    await page.locator('#f-expense').fill('Preview test');
    await page.locator('#f-amount').fill('10');
    await page.locator('form[data-form="expense"]').evaluate(f => f.requestSubmit());
    check('Preview writes stay isolated', await page.evaluate(() => state.previewRecords.length === 3 && project().records.length === 2));
    await click('review');
    await click('apply');
    check('Apply keeps original app records', await page.evaluate(() => project().note && project().records.length === 2));
    await click('open-current');
    await page.locator('#f-expense').fill('Coffee');
    await page.locator('#f-amount').fill('5');
    await page.locator('form[data-form="expense"]').evaluate(f => f.requestSubmit());
    check('Standalone sample form saves an expense', await page.evaluate(() => project().records.length === 3 && project().records.at(-1).name === 'Coffee'));
    await click('back');
    await click('menu');
    await click('history');
    await click('restore-review');
    await click('restore');
    check('Explicit history restore keeps present records', await page.evaluate(() => !project().note && project().records.length === 3));
    await click('settings');
    await click('settings-ai');
    await click('settings-local');
    await click('remove-model');
    await click('remove-model-confirm');
    check('Removing local model never selects cloud', await page.evaluate(() => !state.localModel && state.connection === 'local'));
    await click('back');
    await click('settings-cloud');
    await page.locator('#demo-key').fill('sample-only');
    await page.locator('form[data-form="key"]').evaluate(f => f.requestSubmit());
    check('Demo key state saved and actual key text discarded', await page.evaluate(() => state.keySaved) && await page.locator('#demo-key').inputValue() === '');
    await click('use-cloud');
    await click('confirm-cloud');
    check('Cloud switch requires explicit action', await page.evaluate(() => state.connection === 'cloud'));
    await click('back');
    await click('settings-local');
    await click('catalog');
    check('Catalogue retains all five model choices', await page.locator('button[data-action="download"]').count() === 5);
    await click('download');
    await click('finish-operation');
    check('Installing a model keeps selected cloud connection', await page.evaluate(() => !!state.localModel && state.connection === 'cloud'));
    await click('back');
    await click('back');
    await click('back');
    check('Settings returns to its original workspace', await page.evaluate(() => state.route === 'workspace'));
    await click('apps');
    await click('create');
    await click('example');
    check('Example prefills without submitting', await page.evaluate(() => state.route === 'new') && !!await page.locator('#draft-new').inputValue());
    await page.locator('form[data-form="new"]').evaluate(f => f.requestSubmit());
    check('Create opens a stable project workspace', await page.evaluate(() => state.route === 'workspace' && project().status === 'draft'));
    await click('tab-plan');
    await click('feature');
    await page.locator('#f-workflow').fill('');
    await page.locator('form[data-form="feature"]').evaluate(f => f.requestSubmit());
    await click('build');
    check('Invalid draft is preserved while build blocked', await page.evaluate(() => project().features[0].workflow === '' && !state.job));
    await click('apps');
    await click('edit', '[data-id="notes"]');
    await click('tab-plan');
    await click('appearance');
    await page.locator('#palette').selectOption('Forest');
    await click('close-dialog');
    check('Cancelling appearance preserves saved style', await page.evaluate(() => project().appearance.palette === 'Ocean'));
    await click('appearance');
    await page.locator('#palette').selectOption('Forest');
    await page.locator('form[data-form="appearance"]').evaluate(f => f.requestSubmit());
    check('Appearance has explicit apply boundary', await page.evaluate(() => project().appearance.palette === 'Forest'));
    await click('menu');
    await click('share');
    check('All four existing HTML output actions available', await page.locator('.dialog button[data-action="share-html"],.dialog button[data-action="browser"],.dialog button[data-action="shortcut"],.dialog button[data-action="widget"]').count() === 4);
    await click('close-dialog');
    await click('apps');
    await click('create');
    await click('guided');
    await page.locator('#f-guided-name').fill('My custom form');
    await click('add-field','[data-type="Date"]');
    check('Manual draft survives adding a field', await page.locator('#f-guided-name').inputValue() === 'My custom form');
    await page.locator('form[data-form="guided"]').evaluate(f => f.requestSubmit());
    check('Manual route opens a clear simulated draft review', await page.locator('#dialog-title').textContent() === 'Manual draft preview');
    await click('close-dialog');
    await click('chats');
    await click('new-chat');
    await page.locator('#draft-general').fill('Think through an idea');
    await page.locator('form[data-form="general"]').evaluate(f => f.requestSubmit());
    check('General chat has its own context', await page.evaluate(() => state.route === 'general' && state.chats.find(c => c.id === state.chatId).messages.length === 2));
    await page.selectOption('#scenario', 'failure');
    await click('retry');
    check('Failed build offers retained-work retry', await page.evaluate(() => state.job.status === 'running'));
    await click('finish');
    check('Successful retry retains and publishes same project ID', await page.evaluate(() => state.projects.find(p => p.id === 'routine').status === 'ready'));

    await reload();
    const screens = [
      ['apps', () => page.evaluate(() => { state.route = 'apps'; render(); })],
      ['chats', () => page.evaluate(() => { state.route = 'chats'; render(); })],
      ['create', () => page.evaluate(() => { state.route = 'new'; render(); })],
      ['chat', () => page.evaluate(() => { state.route = 'workspace'; state.projectId = 'trip'; state.tab = 'chat'; render(); })],
      ['plan', () => page.evaluate(() => { state.route = 'workspace'; state.projectId = 'routine'; state.tab = 'plan'; render(); })],
      ['preview', () => page.evaluate(() => { state.route = 'workspace'; state.projectId = 'trip'; state.tab = 'preview'; state.previewRecords = null; render(); })],
      ['guided', () => page.evaluate(() => { state.route = 'guided'; render(); })],
      ['settings', () => page.evaluate(() => { state.route = 'settings'; state.settings = 'ai'; render(); })],
      ['local-ai', () => page.evaluate(() => { state.route = 'settings'; state.settings = 'local'; render(); })],
      ['cloud-ai', () => page.evaluate(() => { state.route = 'settings'; state.settings = 'cloud'; render(); })],
      ['activity', () => page.evaluate(() => { state.route = 'activity'; state.job = { projectId: 'routine', type: 'build', status: 'failed', connection: 'local' }; render(); })],
    ];
    for (const width of [320, 390, 1440]) {
      await page.setViewportSize({ width, height: width === 1440 ? 1000 : 844 });
      for (const large of [false, true]) {
        await page.evaluate(on => document.documentElement.classList.toggle('large-text', on), large);
        for (const [name, setup] of screens) {
          await page.evaluate(() => { state.job = null; state.review = false; });
          await setup();
          const fits = await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1);
          check(`${name} fits ${width}px${large ? ' at 150% text' : ''}`, fits);
          if (width === 390 && !large || width === 1440 && !large && ['apps', 'plan'].includes(name)) {
            await page.screenshot({ path: join(out, `${name}-${width}.png`), fullPage: true });
          }
        }
        await page.evaluate(() => { state.projectId = 'trip'; state.review = true; review(); });
        check(`Review sheet fits ${width}px${large ? ' at 150% text' : ''}`, await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
        await page.keyboard.press('Tab');
        check(`Review sheet traps keyboard focus at ${width}px`, await page.evaluate(() => !!document.activeElement.closest('.dialog')));
        await page.keyboard.press('Escape');
        check(`Escape closes sheet at ${width}px`, await page.locator('.dialog').count() === 0);
      }
    }
    check('No browser JavaScript errors', errors.length === 0);
    writeFileSync(join(out, 'validation.json'), JSON.stringify({ date: '2026-10-04', scope: 'Browser design prototype only', checks, errors, passed: checks.filter(c => c.passed).length }, null, 2));
    console.log(`${checks.length} prototype checks passed. Screenshots in ${out}`);
  } finally {
    await browser.close();
  }
})().catch(e => { console.error(e); process.exitCode = 1; });
