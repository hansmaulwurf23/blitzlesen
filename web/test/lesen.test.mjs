// node --test web/test
// Dieselben Fälle wie RoundTest, PickerTest und HistoryTest der Android-App, dazu Wortlisten und
// das, was es nur im Web gibt (mehrere Listen).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import {
  DEFAULT_LIST, GOOD, PRACTICE, NEW, Picker, Round,
  ranked, bestExcept, wordStats, pileOf, recentAverage, piles,
  toJson, fromJson, merge, formatCountdown, formatSeconds,
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
