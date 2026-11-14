// node --test web/test
// Dieselben Fälle wie RoundTest, PickerTest und HistoryTest der Android-App, dazu Wortlisten und
// das, was es nur im Web gibt (mehrere Listen).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import {
  DEFAULT_LIST, GOOD, PRACTICE, NEW, Picker, Round, secondsOf, pairsOf,
  ranked, bestExcept, wordStats, pileOf, recentAverage, piles,
  toJson, fromJson, merge, formatCountdown, formatSeconds, formatDuration, difference,
} from '../lesen.js';

const here = (p) => fileURLToPath(new URL(p, import.meta.url));
const readJson = (p) => JSON.parse(readFileSync(here(p), 'utf8'));
const WORDS = readJson('../listen/grundwortschatz-100.json').words;

/** Wie java.util.Random mit festem Startwert: reproduzierbar, aber gut verteilt. */
function seeded(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const newRound = (words) => new Round(60_000, new Picker(words, new Map(), seeded(1)));
const record = (id, timestamp, attempts, list) => ({
  id, timestamp, seconds: 60, attempts: attempts.map(([word, millis, good]) => ({ word, millis, good })),
  ...(list ? { list } : {}),
});

// ------------------------------------------------------------------ Runde

test('misst die Zeit je Wort ohne Pausen', () => {
  const r = newRound(WORDS);
  r.start(1_000);
  const first = r.current;
  assert.ok(first);
  r.pause(2_000);
  r.resume(10_000); // 8 s Pause zählen nicht
  const a = r.answer(true, 10_500);
  assert.equal(a.word, first);
  assert.equal(a.millis, 1_500);
  assert.equal(r.current, null);

  assert.ok(r.next(11_000)); // Zeit zwischen den Karten zählt zur Runde, nicht zum Wort
  const b = r.answer(false, 13_000);
  assert.equal(b.millis, 2_000);
  assert.equal(r.goodCount(), 1);
  assert.equal(r.practiceCount(), 1);
  assert.equal(r.remaining(13_000), 60_000 - 4_000);
});

test('offene Karte darf nach Ablauf noch bewertet werden', () => {
  const r = newRound(WORDS);
  r.start(0);
  r.answer(true, 59_000);
  assert.ok(r.next(59_500));
  assert.ok(r.isTimeUp(61_000));
  assert.ok(r.current);
  r.answer(true, 62_000); // zählt noch
  assert.equal(r.next(62_200), false);
  assert.ok(r.finished);
  assert.equal(r.attempts.length, 2);
});

test('keine Wiederholung, bis alle dran waren, und nie zweimal hintereinander', () => {
  const r = newRound(['a', 'b', 'c']);
  r.start(0);
  const seen = new Set();
  let last = null;
  for (let i = 0; i < 30; i++) {
    const w = r.current;
    if (i < 3) {
      assert.ok(!seen.has(w), `Wiederholung vor Ende des Durchgangs: ${w}`);
      seen.add(w);
    }
    assert.notEqual(w, last);
    last = w;
    r.answer(true, i * 100 + 50);
    assert.ok(r.next(i * 100 + 100));
  }
});

// ------------------------------------------------------------------ Karten ziehen

test('gelbe und neue Wörter kommen öfter dran als grüne', () => {
  const picker = new Picker(['gut', 'gelb', 'neu'], new Map([['gut', GOOD], ['gelb', PRACTICE]]), seeded(42));
  const counts = { gut: 0, gelb: 0, neu: 0 };
  for (let i = 0; i < 7000; i++) counts[picker.next(new Set(), null)]++;
  // Gewichte 1 : 3 : 3
  assert.ok(counts.gut < 1300, JSON.stringify(counts));
  assert.ok(counts.gelb > 2700, JSON.stringify(counts));
  assert.ok(counts.neu > 2700, JSON.stringify(counts));
});

// ------------------------------------------------------------------ Paare

const PAIRS = [['Haus', 'Maus'], ['Maus', 'Laus'], ['Ball', 'Fall'], ['Kuh', 'Schuh']];
const PAIR_WORDS = ['Haus', 'Maus', 'Laus', 'Ball', 'Fall', 'Kuh', 'Schuh'];
const pairRound = (seed) => new Round(60_000, new Picker(PAIR_WORDS, new Map(), seeded(seed), PAIRS));
const isPair = (a, b) => PAIRS.some(([x, y]) => (x === a && y === b) || (x === b && y === a));

test('Paare: das Gegenwort kommt direkt danach, mit Verweis auf das erste', () => {
  for (let seed = 1; seed <= 20; seed++) {
    const r = pairRound(seed);
    r.start(0);
    for (let i = 0; i < 40; i++) {
      const first = r.current;
      assert.equal(r.partner, null, `erstes Wort ${first} ohne Partner`);
      r.answer(true, i * 200 + 50);
      assert.ok(r.next(i * 200 + 100));
      const second = r.current;
      assert.equal(r.partner, first);
      assert.ok(isPair(first, second), `${first} – ${second} ist kein Paar`);
      r.answer(false, i * 200 + 150);
      assert.ok(r.next(i * 200 + 200));
      assert.notEqual(r.current, second, 'nie zweimal hintereinander');
    }
  }
});

test('Paare: keins doppelt, solange es noch welche mit ungezeigten Wörtern gibt', () => {
  for (let seed = 1; seed <= 50; seed++) {
    const picker = new Picker(PAIR_WORDS, new Map(), seeded(seed), PAIRS);
    const shown = new Set();
    const seen = [];
    let last = null;
    // Solange es ein Paar mit einem ungezeigten Wort gibt, kommt nur so eins.
    while (PAIRS.some(([a, b]) => !shown.has(a) || !shown.has(b))) {
      const a = picker.next(shown, last);
      shown.add(a);
      const b = picker.next(shown, a);
      shown.add(b);
      last = b;
      const key = [a, b].sort().join('–');
      assert.ok(!seen.includes(key), `${key} doppelt bei Startwert ${seed}`);
      seen.push(key);
    }
    assert.ok(seen.length <= PAIRS.length);
  }
});

test('Paare: das schwächere Wort bestimmt, wie oft ein Paar drankommt', () => {
  const piles = new Map([['Haus', GOOD], ['Maus', GOOD], ['Ball', GOOD], ['Fall', PRACTICE]]);
  const picker = new Picker(['Haus', 'Maus', 'Ball', 'Fall'], piles, seeded(7), [['Haus', 'Maus'], ['Ball', 'Fall']]);
  let ball = 0;
  for (let i = 0; i < 4000; i++) {
    const w = picker.next(new Set(), null);
    picker.next(new Set(), w);
    if (w === 'Ball' || w === 'Fall') ball++;
  }
  // Gewichte 1 : 3
  assert.ok(ball > 2700 && ball < 3300, String(ball));
});

test('Paare aus der Liste: nur zwei verschiedene Wörter, die in der Liste stehen', () => {
  const list = { words: ['Haus', 'Maus', 'Laus'], pairs: [['Haus', 'Maus'], ['Maus', 'Maus'], ['Maus', 'Hut'], ['Laus'], 'x'] };
  assert.deepEqual(pairsOf(list), [['Haus', 'Maus']]);
  assert.deepEqual(pairsOf({ words: ['a'] }), []);
});

test('Rundendauer aus der Liste, sonst eine Minute', () => {
  assert.equal(secondsOf({ seconds: 120 }), 120);
  assert.equal(secondsOf({}), 60);
  assert.equal(secondsOf({ seconds: 0 }), 60);
  assert.equal(secondsOf({ seconds: '90' }), 60);
  assert.equal(formatDuration(60), 'einer Minute');
  assert.equal(formatDuration(120), 'zwei Minuten');
  assert.equal(formatDuration(600), '10 Minuten');
  assert.equal(formatDuration(90), '90 Sekunden');
});

test('Unterschied im Gegenwort', () => {
  const part = (a, b) => { const [s, e] = difference(a, b); return a.slice(s, e); };
  assert.equal(part('Maus', 'Haus'), 'M');
  assert.equal(part('Schuh', 'Kuh'), 'Sch');
  assert.equal(part('Kuh', 'Schuh'), 'K');
  assert.equal(part('Glas', 'Gras'), 'l');
  assert.equal(part('Wanne', 'Tanne'), 'W');
  assert.equal(part('Rolle', 'Wolle'), 'R');
  assert.equal(part('See', 'Fee'), 'S');
  assert.equal(part('Haus', 'Haus'), '');
  assert.deepEqual(difference('aa', 'aaa'), [2, 2]);
});

// ------------------------------------------------------------------ Verlauf

test('die letzte Bewertung entscheidet den Stapel', () => {
  // absichtlich nicht chronologisch
  const rounds = [
    record('2', 2000, [['und', 900, true], ['aber', 3000, false]]),
    record('1', 1000, [['und', 2500, false], ['aber', 1200, true]]),
  ];
  const stats = wordStats(rounds, WORDS);
  assert.equal(pileOf(stats.get('und')), GOOD);
  assert.equal(pileOf(stats.get('aber')), PRACTICE);
  assert.equal(pileOf(stats.get('Zeit')), NEW);
  assert.equal(recentAverage(stats.get('und')), 1700);
  assert.equal(stats.get('und').millis.length, 2);
  assert.equal(stats.get('und').goodCount, 1);
  assert.equal(stats.size, 100);
  assert.equal(piles(stats).get('und'), GOOD);
});

test('Durchschnitt nur über die letzten fünf Male', () => {
  const rounds = [1, 2, 3, 4, 5, 6].map((i) => record(String(i), i, [['und', i * 1000, true]]));
  assert.equal(recentAverage(wordStats(rounds, ['und']).get('und')), 4000);
});

test('Bestenliste: mehr Wörter, dann mehr grüne, dann die frühere', () => {
  const a = record('a', 1, [['am', 1, true], ['an', 1, false]]);
  const b = record('b', 2, [['am', 1, true], ['an', 1, true]]);
  const c = record('c', 3, [['am', 1, true], ['an', 1, true]]);
  const d = record('d', 4, [['am', 1, true]]);
  const rounds = [d, c, a, b];
  assert.deepEqual(ranked(rounds, DEFAULT_LIST), [b, c, a, d]);
  assert.equal(bestExcept(rounds, b), c);
  assert.equal(bestExcept([a], a), null);
});

test('Bestenliste je Wortliste, Stapel über alle Listen', () => {
  const alt = record('alt', 1, [['und', 900, true], ['am', 900, true], ['an', 900, true]]); // ohne list: Android
  const own = record('neu', 2, [['und', 1200, false]], 'andere-liste');
  const rounds = [alt, own];
  assert.deepEqual(ranked(rounds, DEFAULT_LIST), [alt]);
  assert.deepEqual(ranked(rounds, 'andere-liste'), [own]);
  // Rekord zählt nur in derselben Liste: die Runde mit 3 Wörtern schlägt die andere Liste nicht.
  assert.equal(bestExcept(rounds, own), null);
  assert.equal(pileOf(wordStats(rounds, WORDS).get('und')), PRACTICE);
});

// ------------------------------------------------------------------ JSON

test('JSON hin und zurück, Import überspringt bekannte Runden', () => {
  const rounds = [record('x', 5, [['für', 1234, true], ['über', 800, false]])];
  const copy = fromJson(JSON.parse(JSON.stringify(toJson(rounds))));
  assert.equal(copy[0].timestamp, 5);
  assert.equal(copy[0].attempts[0].word, 'für');
  assert.equal(copy[0].attempts[0].millis, 1234);
  assert.equal(copy[0].attempts[1].good, false);
  assert.equal(copy[0].list, undefined);

  copy.push(record('y', 6, [['so', 1, true]], 'andere-liste'));
  assert.equal(merge(rounds, copy), 1);
  assert.equal(merge(rounds, copy), 0);
  assert.equal(rounds.length, 2);
  assert.equal(fromJson(toJson(rounds))[1].list, 'andere-liste');
});

test('liest einen Export der Android-App', () => {
  const android = {
    format: 'lesen-runden', version: 1, rounds: [{
      id: 'b0fa3f98', timestamp: 1794126000000, seconds: 60,
      attempts: [{ word: 'und', millis: 840, good: true }, { word: 'welche', millis: 2310, good: false }],
    }],
  };
  const rounds = fromJson(android);
  assert.equal(rounds.length, 1);
  assert.deepEqual(ranked(rounds, DEFAULT_LIST), rounds);
});

test('fremde und kaputte Dateien werden abgelehnt', () => {
  assert.throws(() => fromJson({ format: 'andere-app', games: [] }));
  assert.throws(() => fromJson({ format: 'lesen-runden', version: 2, rounds: [] }));
  assert.throws(() => fromJson({ format: 'lesen-runden', version: 1, rounds: [{ id: 'x' }] }));
  assert.throws(() => fromJson(null));
});

// ------------------------------------------------------------------ Anzeige

test('Restzeit und Lesezeit wie in der App', () => {
  assert.equal(formatCountdown(60_000), '1:00');
  assert.equal(formatCountdown(59_001), '1:00');
  assert.equal(formatCountdown(1), '0:01');
  assert.equal(formatCountdown(0), '0:00');
  assert.equal(formatSeconds(1449), '1,4 s');
  assert.equal(formatSeconds(600), '0,6 s');
});

// ------------------------------------------------------------------ Wortlisten

test('jede Liste im Index ist gültig und ohne doppelte Wörter', () => {
  const index = readJson('../listen/index.json');
  assert.ok(index.lists.includes(DEFAULT_LIST));
  assert.equal(new Set(index.lists).size, index.lists.length);
  for (const id of index.lists) {
    // Die Android-App (words/WordLists) lädt nur ids aus Kleinbuchstaben, Ziffern und Bindestrichen.
    assert.match(id, /^[a-z0-9-]+$/, `id ${id}`);
    const list = readJson(`../listen/${id}.json`);
    assert.equal(list.id, id, `id in ${id}.json`);
    assert.ok(list.title, `Titel in ${id}.json`);
    assert.ok(list.words.length > 0, `Wörter in ${id}.json`);
    assert.equal(new Set(list.words).size, list.words.length, `doppelte Wörter in ${id}.json`);
    for (const w of list.words) {
      assert.equal(typeof w, 'string');
      assert.equal(w, w.trim(), `Leerzeichen um "${w}" in ${id}.json`);
      assert.ok(w.length > 0);
    }
    if ('seconds' in list) {
      assert.ok(Number.isInteger(list.seconds) && list.seconds > 0, `seconds in ${id}.json`);
    }
    if ('pairs' in list) {
      // Ungültige Paare würden in der App stillschweigend wegfallen.
      assert.equal(pairsOf(list).length, list.pairs.length, `ungültiges Paar in ${id}.json`);
      const keys = list.pairs.map((p) => [...p].sort().join('–'));
      assert.equal(new Set(keys).size, keys.length, `doppeltes Paar in ${id}.json`);
      const inPairs = new Set(list.pairs.flat());
      for (const w of list.words) assert.ok(inPairs.has(w), `"${w}" in keinem Paar in ${id}.json`);
    }
  }
  // Keine Datei, die im Index fehlt (sonst erscheint sie nie).
  for (const file of readdirSync(here('../listen/'))) {
    if (file !== 'index.json') assert.ok(index.lists.includes(file.replace(/\.json$/, '')), `${file} fehlt im Index`);
  }
});

test('die 100 Wörter stimmen mit der Android-App überein', () => {
  const java = readFileSync(here('../../app/src/main/java/de/noisebleed/lesen/words/Words.java'), 'utf8');
  const body = java.slice(java.indexOf('Arrays.asList('), java.indexOf('));'));
  const androidWords = [...body.matchAll(/"([^"]+)"/g)].map((m) => m[1]);
  assert.equal(WORDS.length, 100);
  assert.deepEqual(WORDS, androidWords);
});
