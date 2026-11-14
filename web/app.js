// Oberfläche der Web-App: Startbildschirm, Runde, Ergebnis, Bestenliste, Wörter. Die Regeln stehen
// in lesen.js; gespeichert wird nur im localStorage dieses Browsers.

import {
  DEFAULT_LIST, GOOD, PRACTICE, NEW, Picker, Round, secondsOf, pairsOf,
  total, good, practice, compareRanking, ranked, bestExcept,
  wordStats, pileOf, recentAverage, piles,
  toJson, fromJson, merge, formatCountdown, formatSeconds, formatDuration, difference,
} from './lesen.js';

/** Wird beim Veröffentlichen durch den Commit ersetzt (.github/workflows/pages.yml). */
const BUILD = '__VERSION__';
const TICK_MS = 100;
/** So lange nach dem Aufdecken zählt kein Tipp, sonst bewertet ein Doppeltipp die nächste Karte. */
const MIN_SHOW_MS = 350;
const APK_URL = 'https://github.com/hansmaulwurf23/blitzlesen/releases/latest';
const INSTAGRAM_URL = 'https://www.instagram.com/die.lernheldin/';

const KEY_ROUNDS = 'lesen.runden';
const KEY_LIST = 'lesen.liste';
const KEY_THEME = 'lesen.design';

const app = document.getElementById('app');
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');

let lists = [];
let rounds = [];
let view = null;
let installPrompt = null;

// ------------------------------------------------------------------ Speicher

function storageGet(key) {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function storageSet(key, value) {
  localStorage.setItem(key, value);
}

function loadRounds() {
  const raw = storageGet(KEY_ROUNDS);
  if (raw === null) return [];
  try {
    return fromJson(JSON.parse(raw));
  } catch (e) {
    // Lieber leer weiter als unbenutzbar. Die kaputten Daten aber beiseitelegen, bevor die nächste
    // Runde sie überschreibt.
    console.error('Runden nicht lesbar', e);
    try {
      if (storageGet(KEY_ROUNDS + '.defekt') === null) storageSet(KEY_ROUNDS + '.defekt', raw);
    } catch { /* voll oder gesperrt */ }
    return [];
  }
}

function saveRounds() {
  storageSet(KEY_ROUNDS, JSON.stringify(toJson(rounds)));
}

/** Bitten, dass der Browser die Daten nicht von selbst aufräumt (vor allem Safari, Firefox). */
function askPersistence() {
  navigator.storage?.persist?.().catch(() => {});
}

function currentList() {
  const id = storageGet(KEY_LIST);
  return lists.find((l) => l.id === id) || lists.find((l) => l.id === DEFAULT_LIST) || lists[0];
}

async function loadLists() {
  const index = await fetchJson('listen/index.json');
  const loaded = await Promise.all(index.lists.map((id) => fetchJson(`listen/${id}.json`).catch((e) => {
    console.error('Liste nicht ladbar', id, e);
    return null;
  })));
  return loaded.filter((l) => l && Array.isArray(l.words) && l.words.length > 0);
}

async function fetchJson(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${url}: ${response.status}`);
  return response.json();
}

// ------------------------------------------------------------------ Navigation

// #/ Start, #/runde, #/ergebnis/<id>, #/bestenliste[/<id>], #/woerter/<stapel>
// Seiten innerhalb der App kommen mit state.inApp in den Verlauf; dann führt "Zurück" per
// history.back() wieder dorthin, sonst (direkt aufgerufen) per replace zum Start.

function navigate(hash) {
  history.pushState({ inApp: true }, '', hash);
  route();
}

function replace(hash) {
  history.replaceState(history.state, '', hash);
  route();
}

function goBack() {
  if (history.state?.inApp) {
    history.back();
  } else {
    replace('#/');
  }
}

function route() {
  const [name, arg] = location.hash.replace(/^#\/?/, '').split('/');
  if (view?.blocksLeaving?.(name)) return;
  view?.destroy?.();
  view = null;
  closeMenu();
  app.className = '';
  switch (name) {
    case 'runde': view = roundView(); break;
    case 'ergebnis': view = resultView(decodeURIComponent(arg || '')); break;
    case 'bestenliste': view = leaderboardView(decodeURIComponent(arg || '')); break;
    case 'woerter': view = wordsView(arg); break;
    default: view = startView();
  }
}

addEventListener('popstate', route);

// ------------------------------------------------------------------ Hilfen

const ICONS = {
  trophy: 'M19,5h-2V3H7v2H5C3.9,5 3,5.9 3,7v1c0,2.55 1.92,4.63 4.39,4.94c0.63,1.5 1.98,2.63 3.61,2.96V19H7v2h10v-2h-4v-3.1c1.63,-0.33 2.98,-1.46 3.61,-2.96C19.08,12.63 21,10.55 21,8V7C21,5.9 20.1,5 19,5zM5,8V7h2v3.82C5.84,10.4 5,9.3 5,8zM19,8c0,1.3 -0.84,2.4 -2,2.82V7h2V8z',
  replay: 'M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z',
  check: 'M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z',
  close: 'M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z',
  back: 'M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z',
  more: 'M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z',
  book: 'M18,2H6C4.9,2 4,2.9 4,4v16c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4C20,2.9 19.1,2 18,2zM6,4h5v8l-2.5,-1.5L6,12V4z',
  down: 'M7,10l5,5 5,-5z',
};

function icon(name) {
  return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${ICONS[name]}"/></svg>`;
}

function esc(text) {
  return String(text).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  })[c]);
}

function plural(n, one, other) {
  return `${n} ${n === 1 ? one : other}`;
}

function render(html, className = '') {
  app.innerHTML = html;
  app.className = className;
  app.scrollTop = 0;
}

function $(selector) {
  return app.querySelector(selector);
}

function animate(el, keyframes, options) {
  if (!el || !el.animate) return null;
  return el.animate(keyframes, reducedMotion.matches ? { ...options, duration: 1, delay: 0 } : options);
}

const OVERSHOOT = 'cubic-bezier(.34, 1.56, .64, 1)';

function pop(el, delay = 0) {
  animate(el, [{ transform: 'scale(.3)' }, { transform: 'scale(1)' }],
    { duration: 450, delay, easing: 'cubic-bezier(.3, 1.9, .5, 1)', fill: 'backwards' });
}

function bounce(el) {
  animate(el, [{ transform: 'scale(1.4)' }, { transform: 'scale(1)' }], { duration: 250, easing: OVERSHOOT });
}

let toastTimer = 0;
function toast(text) {
  const el = document.getElementById('toast');
  el.textContent = text;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 3500);
}

/** Einfacher Dialog; buttons: [{label, primary, action}]. onClose läuft bei jedem Schließen. */
function dialog(html, buttons, onClose) {
  const d = document.createElement('dialog');
  d.innerHTML = `${html}<div class="actions">${buttons.map((b, i) =>
    `<button type="button" data-i="${i}" class="${b.primary ? 'primary' : ''}">${esc(b.label)}</button>`).join('')}</div>`;
  d.addEventListener('click', (e) => {
    const button = e.target.closest('.actions button');
    if (button) {
      const b = buttons[+button.dataset.i];
      d.close();
      b.action?.();
    } else if (e.target === d) {
      d.close(); // Tipp neben den Dialog
    }
  });
  d.addEventListener('close', () => {
    d.remove();
    onClose?.();
  });
  document.body.append(d);
  d.showModal();
  return d;
}

function haptic() {
  navigator.vibrate?.(15);
}

function uuid() {
  if (crypto.randomUUID) return crypto.randomUUID();
  // Ohne sicheren Kontext (http im Heimnetz) fehlt randomUUID.
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

function setThemeColor() {
  const bg = getComputedStyle(document.documentElement).getPropertyValue('--bg').trim();
  document.querySelector('meta[name="theme-color"]').content = bg;
}

function isDark() {
  return document.documentElement.dataset.theme !== 'light';
}

function setDark(dark) {
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  try {
    storageSet(KEY_THEME, dark ? 'dunkel' : 'hell');
  } catch { /* bleibt dann nur für diesen Besuch */ }
  setThemeColor();
}

const dateFormat = new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium', timeStyle: 'short' });

function pileHtml(kind, count, clickable) {
  const label = kind === GOOD ? 'Kann ich' : 'Hakt noch';
  const tag = clickable ? 'button' : 'div';
  // Ein leerer Stapel ist nur eine Karte, mit jedem Wort wächst er bis zu drei.
  return `<div class="pile ${kind}">
    <div class="back2 ${count >= 10 ? '' : 'hidden'}"></div>
    <div class="back1 ${count >= 1 ? '' : 'hidden'}"></div>
    <${tag} class="front" ${clickable ? `type="button" data-pile="${kind}"` : ''}>
      <span class="count">${count}</span><span class="label">${label}</span>
    </${tag}>
  </div>`;
}

// ------------------------------------------------------------------ Start

function startView() {
  const list = currentList();
  const stats = wordStats(rounds, list.words);
  let nGood = 0, nPractice = 0, nNew = 0;
  for (const s of stats.values()) {
    const p = pileOf(s);
    if (p === GOOD) nGood++;
    else if (p === PRACTICE) nPractice++;
    else nNew++;
  }
  const best = ranked(rounds, list.id)[0];

  render(`
    <header class="toolbar">
      <h1>Lesen</h1>
      <button class="icon-button" id="to-leaderboard" aria-label="Bestenliste">${icon('trophy')}</button>
      <button class="icon-button" id="menu" aria-label="Menü" aria-haspopup="true">${icon('more')}</button>
    </header>
    <div class="scroll">
      <h2 class="headline">Blitzlesen</h2>
      <p class="tagline">Wie viele Wörter schaffst du in ${formatDuration(secondsOf(list))}?</p>
      <button class="list-pill" id="list" aria-label="Wortliste wechseln">
        ${icon('book')}<span>${esc(list.title)}</span>${lists.length > 1 ? icon('down') : ''}
      </button>
      <div class="piles">
        ${pileHtml(GOOD, nGood, true)}
        ${pileHtml(PRACTICE, nPractice, true)}
      </div>
      <button class="new-words" id="new-words">${nNew > 0
        ? `${plural(nNew, 'Wort hast', 'Wörter hast')} du noch nie gesehen`
        : `Alle ${list.words.length} Wörter schon einmal gelesen!`}</button>
      <button class="record-card" id="record">
        ${icon('trophy')}
        <div>${best
          ? `<b>Rekord: ${plural(total(best), 'Wort', 'Wörter')}</b><span>${good(best)} grün · ${practice(best)} gelb</span>`
          : '<b>Noch kein Rekord – leg los!</b>'}</div>
      </button>
    </div>
    <div class="bottom"><button class="big-button" id="start">Los geht's!</button></div>
  `);

  $('#start').onclick = () => navigate('#/runde');
  $('#record').onclick = () => navigate('#/bestenliste');
  $('#to-leaderboard').onclick = () => navigate('#/bestenliste');
  $('#new-words').onclick = () => navigate(`#/woerter/${NEW}`);
  $('#list').onclick = chooseList;
  $('#menu').onclick = (e) => openMenu(e.currentTarget);
  for (const b of app.querySelectorAll('[data-pile]')) {
    b.onclick = () => navigate(`#/woerter/${b.dataset.pile}`);
  }
  return {};
}

function chooseList() {
  const active = currentList();
  const d = dialog(`<h2>Wortliste</h2><ul class="lists">${lists.map((l) => `
    <li><button type="button" data-id="${esc(l.id)}" aria-pressed="${l.id === active.id}">
      <b>${esc(l.title)}</b><span>${esc(listDetail(l))}</span>
    </button></li>`).join('')}</ul>`, [{ label: 'Schließen' }]);
  for (const b of d.querySelectorAll('[data-id]')) {
    b.onclick = () => {
      try {
        storageSet(KEY_LIST, b.dataset.id);
      } catch { /* dann nur bis zum Neuladen */ }
      d.close();
      route();
    };
  }
}

/** Unter dem Titel in der Auswahl: Beschreibung, Anzahl Wörter oder Paare, abweichende Dauer. */
function listDetail(l) {
  const pairs = pairsOf(l).length;
  const seconds = secondsOf(l);
  return [
    l.description,
    pairs > 0 ? plural(pairs, 'Paar', 'Paare') : plural(l.words.length, 'Wort', 'Wörter'),
    seconds === 60 ? '' : seconds % 60 === 0 ? `${seconds / 60} Min.` : `${seconds} s`,
  ].filter(Boolean).join(' · ');
}

// ------------------------------------------------------------------ Menü

let menuEl = null;

function closeMenu() {
  menuEl?.remove();
  menuEl = null;
}

function openMenu(anchor) {
  closeMenu();
  const items = [
    ['Bestenliste', () => navigate('#/bestenliste')],
    ['Wörter', () => navigate(`#/woerter/${PRACTICE}`)],
    ...(lists.length > 1 ? [['Wortliste wechseln', chooseList]] : []),
    ['Dunkles Design', () => setDark(!isDark()), isDark()],
    null,
    ['Fortschritt exportieren', exportRounds],
    ['Fortschritt importieren', importRounds],
    ...(installPrompt ? [['Als App installieren', install]] : []),
    null,
    ['Über Lesen', showAbout],
  ];
  menuEl = document.createElement('div');
  menuEl.className = 'menu';
  menuEl.setAttribute('role', 'menu');
  menuEl.innerHTML = items.map((item, i) => item === null ? '<hr>'
    : `<button type="button" role="menuitem" data-i="${i}">${esc(item[0])}${item.length > 2
      ? `<span class="check ${item[2] ? 'on' : ''}">${item[2] ? icon('check') : ''}</span>` : ''}</button>`).join('');
  const rect = anchor.getBoundingClientRect();
  menuEl.style.top = `${rect.bottom + 4}px`;
  menuEl.style.right = `${Math.max(8, innerWidth - rect.right)}px`;
  menuEl.addEventListener('click', (e) => {
    const b = e.target.closest('button');
    if (!b) return;
    closeMenu();
    items[+b.dataset.i][1]();
  });
  document.body.append(menuEl);
  menuEl.querySelector('button').focus();
  // Erst nach diesem Klick auf Klicks daneben hören.
  setTimeout(() => document.addEventListener('pointerdown', outside, true));
  function outside(e) {
    if (menuEl && menuEl.contains(e.target)) return;
    document.removeEventListener('pointerdown', outside, true);
    closeMenu();
  }
}

addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && menuEl) closeMenu();
});

// ------------------------------------------------------------------ Export / Import

function exportRounds() {
  const json = JSON.stringify(toJson(rounds), null, 1);
  const date = new Date().toISOString().slice(0, 10);
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([json], { type: 'application/json' }));
  a.download = `lesen-fortschritt-${date}.json`;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 10000);
  toast('Fortschritt exportiert');
}

function importRounds() {
  const input = document.createElement('input');
  input.type = 'file';
  // Manche Dateimanager kennen .json nicht als application/json.
  input.accept = 'application/json,.json,text/plain';
  input.onchange = async () => {
    const file = input.files?.[0];
    if (!file) return;
    try {
      const imported = fromJson(JSON.parse(await file.text()));
      const added = merge(rounds, imported);
      if (added > 0) {
        saveRounds();
        askPersistence();
      }
      route();
      toast(`${plural(added, 'Runde', 'Runden')} importiert`);
    } catch (e) {
      toast(`Import fehlgeschlagen: ${e.message}`);
    }
  };
  input.click();
}

async function install() {
  const prompt = installPrompt;
  installPrompt = null;
  await prompt?.prompt();
}

addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  installPrompt = e;
});

function showAbout() {
  const version = BUILD.startsWith('__') ? 'lokal' : BUILD;
  dialog(`<div class="about">
    <img src="icons/favicon.svg" alt="">
    <div class="name">Lesen</div>
    <p class="built">Built by Martin and Claude with ❤️</p>
    <p class="based">Idee und Wortlisten von Die Lernheldin: die Wortkartei „Blitzlesen – 100 häufige Wörter“ und die „Minimalpaare zum Blitzlesen“.
<a href="${INSTAGRAM_URL}" target="_blank" rel="noopener" class="nowrap">@die.lernheldin</a> auf Instagram

Die 100 Wörter stammen aus dem Grundwortschatz für Berlin (Fassung 2025).

Alles bleibt in diesem Browser, ohne Anmeldung. Für Android gibt es Lesen auch als <a href="${APK_URL}" target="_blank" rel="noopener">App</a>.</p>
    <p class="version">Version: ${esc(version)}</p>
  </div>`, [{ label: 'OK', primary: true }]);
}

// ------------------------------------------------------------------ Runde

function roundView() {
  const list = currentList();
  const seconds = secondsOf(list);
  const picker = new Picker(list.words, piles(wordStats(rounds, list.words)), Math.random, pairsOf(list));
  const round = new Round(seconds * 1000, picker);
  // countdown, playing, animating, paused, done
  let phase = 'paused';
  let abortDialog = null;
  let cardShownAt = 0;
  let timers = [];
  let flight = null;
  let wakeLock = null;

  render(`
    <header class="toolbar">
      <button class="icon-button" id="abort" aria-label="Aufhören">${icon('close')}</button>
      <span class="spacer"></span>
      <span class="timer" id="timer" aria-hidden="true"></span>
    </header>
    <div class="time-bar"><div id="bar"></div></div>
    <div class="stage">
      <p class="time-up" id="time-up" hidden>Zeit um! Lies das Wort noch zu Ende.</p>
      <div class="word-card" id="card" style="visibility:hidden"><span id="word" lang="de"></span></div>
      <div class="countdown" id="countdown" hidden></div>
    </div>
    <div class="counts">
      <span class="count-chip good" id="count-good"></span>
      <span class="count-chip practice" id="count-practice"></span>
    </div>
    <div class="rate">
      <button class="good" id="rate-good">${icon('check')}Kann ich!</button>
      <button class="practice" id="rate-practice">${icon('replay')}Hakt noch</button>
    </div>
  `, 'round');

  const card = $('#card');
  const word = $('#word');
  const timer = $('#timer');
  const bar = $('#bar');
  const timeUp = $('#time-up');
  const countdown = $('#countdown');
  const countGood = $('#count-good');
  const countPractice = $('#count-practice');

  $('#abort').onclick = confirmAbort;
  $('#rate-good').onclick = () => rate(true);
  $('#rate-practice').onclick = () => rate(false);

  function later(fn, ms) {
    timers.push(setTimeout(fn, ms));
  }

  function clearTimers() {
    timers.forEach(clearTimeout);
    timers = [];
  }

  const ticker = setInterval(updateTimer, TICK_MS);

  function onKey(e) {
    if (abortDialog || e.repeat) return;
    if (e.key === 'ArrowLeft' || e.key === '1') rate(true);
    else if (e.key === 'ArrowRight' || e.key === '2') rate(false);
    else if (e.key === 'Escape') confirmAbort();
    else return;
    // Sonst schließt dasselbe Escape den eben geöffneten Dialog gleich wieder.
    e.preventDefault();
  }

  function onVisibility() {
    if (document.hidden) {
      pauseRound();
    } else {
      requestWakeLock();
      if (phase === 'paused' && !abortDialog) startCountdown();
    }
  }

  addEventListener('keydown', onKey);
  document.addEventListener('visibilitychange', onVisibility);

  async function requestWakeLock() {
    // Der Bildschirm soll während der Minute nicht ausgehen.
    try {
      wakeLock = await navigator.wakeLock?.request('screen');
    } catch { /* nicht erlaubt oder nicht unterstützt */ }
  }

  /** Uhr anhalten; weiter geht es danach immer mit einem Countdown. */
  function pauseRound() {
    clearTimers();
    if (phase === 'countdown') {
      countdown.hidden = true;
      phase = 'paused';
    } else if (phase === 'playing' || phase === 'animating') {
      flight?.cancel();
      flight = null;
      round.pause(performance.now());
      phase = 'paused';
    }
  }

  function startCountdown() {
    phase = 'countdown';
    card.style.visibility = 'hidden';
    timeUp.hidden = true;
    countdownStep(3);
  }

  function countdownStep(n) {
    if (phase !== 'countdown') return;
    countdown.hidden = false;
    countdown.textContent = n > 0 ? String(n) : 'Los!';
    animate(countdown, [{ transform: 'scale(1.6)', opacity: 0 }, { transform: 'scale(1)', opacity: 1 }],
      { duration: 350, easing: OVERSHOOT });
    later(() => {
      if (n > 0) {
        countdownStep(n - 1);
      } else {
        countdown.hidden = true;
        play();
      }
    }, n > 0 ? 800 : 450);
  }

  function play() {
    const now = performance.now();
    if (!round.started) {
      round.start(now);
    } else {
      round.resume(now);
      if (round.current === null && !round.next(now)) {
        finish();
        return;
      }
    }
    phase = 'playing';
    showCard();
  }

  /** Die aktuelle Karte; beim Gegenwort eines Paars ist der Unterschied zum ersten farbig. */
  function showCard() {
    cardShownAt = performance.now();
    const text = round.current;
    if (round.partner === null) {
      word.textContent = text;
    } else {
      const [from, to] = difference(text, round.partner);
      word.innerHTML = `${esc(text.slice(0, from))}<mark>${esc(text.slice(from, to))}</mark>${esc(text.slice(to))}`;
    }
    word.style.fontSize = '';
    card.style.visibility = 'visible';
    fitWord();
    const tilt = Math.random() * 6 - 3;
    card.style.transform = `rotate(${tilt}deg)`;
    animate(card, [
      { transform: `rotate(${tilt}deg) scale(.6)`, opacity: 0 },
      { transform: `rotate(${tilt}deg) scale(1)`, opacity: 1 },
    ], { duration: 200, easing: 'cubic-bezier(.34, 1.8, .64, 1)' });
  }

  /** Lange Wörter kleiner setzen, bis sie in eine Zeile passen (wie autoSize in der App). */
  function fitWord() {
    let size = 80;
    const room = card.clientWidth - 32;
    while (size > 32 && word.scrollWidth > room) {
      size -= 4;
      word.style.fontSize = `${size}px`;
    }
  }

  function rate(isGood) {
    const now = performance.now();
    if (phase !== 'playing' || round.current === null || now - cardShownAt < MIN_SHOW_MS) return;
    round.answer(isGood, now);
    haptic();
    updateCounts();
    bounce(isGood ? countGood : countPractice);

    // Grün fliegt nach links unten auf den grünen Stapel, Gelb nach rechts.
    phase = 'animating';
    const direction = isGood ? -1 : 1;
    const from = card.style.transform;
    flight = animate(card, [
      { transform: from, opacity: 1 },
      {
        transform: `translate(${direction * card.offsetWidth}px, ${card.offsetHeight * 0.8}px) rotate(${direction * 25}deg) scale(.4)`,
        opacity: 0,
      },
    ], { duration: 220, easing: 'cubic-bezier(.4, 0, 1, 1)', fill: 'forwards' });
    if (flight) {
      flight.onfinish = nextCard;
    } else {
      nextCard();
    }
  }

  function nextCard() {
    if (phase !== 'animating') return;
    flight?.cancel();
    flight = null;
    if (round.next(performance.now())) {
      phase = 'playing';
      showCard();
    } else {
      finish();
    }
  }

  function updateCounts() {
    countGood.textContent = `✓ ${round.goodCount()}`;
    countPractice.textContent = `↻ ${round.practiceCount()}`;
  }

  function updateTimer() {
    const now = performance.now();
    const remaining = round.started ? round.remaining(now) : round.durationMillis;
    timer.textContent = formatCountdown(remaining);
    bar.style.transform = `scaleX(${remaining / round.durationMillis})`;
    const up = round.started && round.isTimeUp(now);
    timer.classList.toggle('up', up);
    if (up && phase === 'playing' && timeUp.hidden) {
      timeUp.hidden = false;
      bounce(timeUp);
    }
  }

  function finish() {
    phase = 'done';
    clearTimers();
    const record = {
      id: uuid(),
      timestamp: Date.now(),
      seconds,
      list: list.id,
      attempts: [...round.attempts],
    };
    // Erst frisch laden: ein zweiter Tab könnte inzwischen gespeichert haben.
    rounds = loadRounds();
    rounds.push(record);
    try {
      saveRounds();
    } catch (e) {
      rounds.pop();
      toast(`Runde konnte nicht gespeichert werden: ${e.message}`);
      replace('#/');
      return;
    }
    askPersistence();
    replace(`#/ergebnis/${encodeURIComponent(record.id)}`);
  }

  function confirmAbort() {
    if (phase === 'done' || abortDialog) return;
    // Während der Rückfrage steht die Uhr; danach geht es mit Countdown weiter.
    pauseRound();
    card.style.visibility = 'hidden';
    let aborted = false;
    abortDialog = dialog(`<h2>Aufhören?</h2>
      <p>Die angefangene Runde zählt nicht und kommt nicht auf die Stapel.</p>`, [
      { label: 'Aufhören', action: () => { aborted = true; } },
      { label: 'Weiterlesen', primary: true },
    ], () => {
      abortDialog = null;
      if (aborted) {
        phase = 'done';
        goBack();
      } else if (phase === 'paused' && !document.hidden) {
        startCountdown();
      }
    });
  }

  updateCounts();
  updateTimer();
  requestWakeLock();
  startCountdown();

  return {
    /** Zurück-Geste mitten in der Runde: erst nachfragen, wie in der App. */
    blocksLeaving(name) {
      if (phase === 'done' || name === 'runde') return false;
      history.pushState({ inApp: true }, '', '#/runde');
      confirmAbort();
      return true;
    },
    destroy() {
      phase = 'done';
      clearTimers();
      clearInterval(ticker);
      flight?.cancel();
      abortDialog?.close();
      removeEventListener('keydown', onKey);
      document.removeEventListener('visibilitychange', onVisibility);
      wakeLock?.release().catch(() => {});
    },
  };
}

// ------------------------------------------------------------------ Ergebnis

function resultView(id) {
  const round = rounds.find((r) => r.id === id);
  if (!round) {
    history.replaceState(history.state, '', '#/');
    return startView();
  }
  const n = total(round);
  const share = n === 0 ? 0 : good(round) / n;
  const best = bestExcept(rounds, round);
  const newRecord = best !== null && compareRanking(round, best) < 0;
  const list = lists.find((l) => l.id === (round.list || DEFAULT_LIST));
  const rank = ranked(rounds, round.list || DEFAULT_LIST).indexOf(round) + 1;
  let fastest = null;
  for (const a of round.attempts) {
    if (a.good && (fastest === null || a.millis < fastest.millis)) fastest = a;
  }

  render(`
    <header class="toolbar">
      <button class="icon-button" id="close" aria-label="Schließen">${icon('close')}</button>
    </header>
    <div class="scroll result">
      <h2>${share >= 0.8 ? 'Super gemacht!' : share >= 0.5 ? 'Gut gemacht!' : 'Weiter üben!'}</h2>
      <div class="total" id="total">${n}</div>
      <div class="total-label">Wörter in ${formatDuration(round.seconds)}${list && lists.length > 1 ? ` · ${esc(list.title)}` : ''}</div>
      <div class="piles">
        ${pileHtml(GOOD, good(round), false)}
        ${pileHtml(PRACTICE, practice(round), false)}
      </div>
      ${newRecord ? '<div class="new-record" id="new-record">Neuer Rekord!</div>' : ''}
      <div class="rank">Platz ${rank} in der Bestenliste</div>
      ${fastest ? `<div class="fastest">Am schnellsten: ${esc(fastest.word)} (${formatSeconds(fastest.millis)})</div>` : ''}
      <div class="chips">${round.attempts.map((a) =>
        `<span class="chip ${a.good ? GOOD : PRACTICE}">${esc(a.word)}&nbsp; ${formatSeconds(a.millis)}</span>`).join('')}</div>
    </div>
    <div class="bottom">
      <button class="big-button small outlined" id="to-leaderboard">${icon('trophy')}Bestenliste</button>
      <button class="big-button small" id="again">${icon('replay')}Nochmal</button>
    </div>
  `);
  pop($('#total'));
  pop($('#new-record'), 300);
  $('#close').onclick = goBack;
  $('#to-leaderboard').onclick = () => replace(`#/bestenliste/${encodeURIComponent(round.id)}`);
  $('#again').onclick = () => replace('#/runde');
  return {};
}

// ------------------------------------------------------------------ Bestenliste

function leaderboardView(highlight) {
  const list = currentList();
  const listId = rounds.find((r) => r.id === highlight)?.list || list.id;
  const shown = lists.find((l) => l.id === listId) || list;
  const sorted = ranked(rounds, listId);
  const words = sorted.reduce((s, r) => s + total(r), 0);
  const goods = sorted.reduce((s, r) => s + good(r), 0);

  render(`
    <header class="toolbar">
      <button class="icon-button" id="back" aria-label="Zurück">${icon('back')}</button>
      <h1>Bestenliste${lists.length > 1 ? `<span class="sub">${esc(shown.title)}</span>` : ''}</h1>
    </header>
    <div class="scroll">
      ${sorted.length === 0 ? '<p class="empty">Noch keine Runden. Los geht\'s!</p>' : `
      <p class="summary">${plural(sorted.length, 'Runde', 'Runden')} · ${words} Wörter gelesen · ${words === 0 ? 0 : Math.round(100 * goods / words)} % grün</p>
      <ol class="rounds">${sorted.map((r, i) => `
        <li class="${r.id === highlight ? 'highlight' : ''}" ${r.id === highlight ? 'id="highlight"' : ''}>
          <span class="place ${i < 3 ? `p${i + 1}` : ''}">${i + 1}</span>
          <span class="what"><b>${plural(total(r), 'Wort', 'Wörter')}</b><span>${dateFormat.format(new Date(r.timestamp))}</span></span>
          <span class="split"><span class="g">✓ ${good(r)}</span><span class="p">↻ ${practice(r)}</span></span>
        </li>`).join('')}</ol>
      <div class="storage-note">
        <p>Die Runden liegen nur in diesem Browser. Werden die Browserdaten gelöscht, oder räumt der
        Browser selbst auf, ist die Bestenliste wieder leer.</p>
        <button type="button" id="backup">Fortschritt sichern</button>
      </div>`}
    </div>
  `);
  $('#back').onclick = goBack;
  const backup = $('#backup');
  if (backup) backup.onclick = exportRounds;
  $('#highlight')?.scrollIntoView({ block: 'center' });
  return {};
}

// ------------------------------------------------------------------ Wörter

const PILE_COLORS = {
  [GOOD]: ['var(--good)', '#fff'],
  [PRACTICE]: ['var(--practice)', 'var(--on-practice)'],
  [NEW]: ['var(--new)', '#fff'],
};

function wordsView(pile) {
  if (![GOOD, PRACTICE, NEW].includes(pile)) pile = PRACTICE;
  const list = currentList();
  const stats = [...wordStats(rounds, list.words).values()];
  const count = (p) => stats.filter((s) => pileOf(s) === p).length;
  const shown = stats.filter((s) => pileOf(s) === pile);
  const chip = (p, label) => `<button type="button" data-pile="${p}" aria-pressed="${p === pile}"
    style="--c:${PILE_COLORS[p][0]};--on-c:${PILE_COLORS[p][1]}">${label} (${count(p)})</button>`;

  render(`
    <header class="toolbar">
      <button class="icon-button" id="back" aria-label="Zurück">${icon('back')}</button>
      <h1>Wörter${lists.length > 1 ? `<span class="sub">${esc(list.title)}</span>` : ''}</h1>
    </header>
    <div class="scroll">
      <div class="filter">${chip(GOOD, 'Kann ich')}${chip(PRACTICE, 'Hakt noch')}${chip(NEW, 'Neu')}</div>
      ${shown.length === 0 ? '<p class="empty">Hier liegt noch kein Wort.</p>' : `
      <div class="grid">${shown.map((s, i) => `
        <button type="button" class="word-tile" data-i="${i}" style="--c:${PILE_COLORS[pile][0]}">
          <b lang="de">${esc(s.word)}</b>${s.millis.length > 0
            ? `<span>Ø ${formatSeconds(recentAverage(s))} · ${s.millis.length}×</span>` : ''}
        </button>`).join('')}</div>`}
    </div>
  `);
  $('#back').onclick = goBack;
  for (const b of app.querySelectorAll('.filter button')) {
    b.onclick = () => replace(`#/woerter/${b.dataset.pile}`);
  }
  for (const b of app.querySelectorAll('.word-tile')) {
    b.onclick = () => {
      const s = shown[+b.dataset.i];
      const text = s.millis.length === 0 ? 'Noch nie dran gewesen.'
        : `${s.millis.length}-mal gelesen, davon ${s.goodCount}-mal grün.\nDurchschnitt der letzten Male: ${formatSeconds(recentAverage(s))}`;
      dialog(`<h2 lang="de">${esc(s.word)}</h2><p>${esc(text)}</p>`, [{ label: 'OK', primary: true }]);
    };
  }
  return {};
}

// ------------------------------------------------------------------ Los

// Ein anderer Tab hat gespeichert: neu einlesen, nur mitten in der Runde nicht.
addEventListener('storage', (e) => {
  if (e.key !== KEY_ROUNDS && e.key !== KEY_LIST) return;
  rounds = loadRounds();
  if (!location.hash.startsWith('#/runde') && !document.querySelector('dialog')) route();
});

async function main() {
  setThemeColor();
  rounds = loadRounds();
  try {
    lists = await loadLists();
  } catch (e) {
    console.error(e);
  }
  if (lists.length === 0) {
    render('<p class="empty">Die Wortlisten ließen sich nicht laden. Bitte mit Internet neu öffnen.</p>');
    return;
  }
  // Nach dem Neuladen nicht mitten in eine neue Runde springen.
  if (location.hash.startsWith('#/runde')) {
    history.replaceState(null, '', '#/');
  }
  route();
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch((e) => console.error('Service Worker', e));
  }
}

main();
