// Spiellogik, Stapel und Bestenliste ohne DOM — dieselben Regeln wie in der Android-App
// (game/Round, words/Picker, stats/History). Getestet in web/test/lesen.test.mjs.

export const FORMAT = 'lesen-runden';
export const VERSION = 1;
/** Runden ohne "list" stammen aus der Android-App oder von vor den Wortlisten. */
export const DEFAULT_LIST = 'grundwortschatz-100';

export const GOOD = 'good';
export const PRACTICE = 'practice';
export const NEW = 'new';

export const WEIGHT_GOOD = 1;
export const WEIGHT_PRACTICE = 3;
export const WEIGHT_NEW = 3;
/** Für den Durchschnitt zählen nur die letzten paar Male, damit Fortschritt sichtbar wird. */
export const RECENT = 5;

// ------------------------------------------------------------------ Karten ziehen

/**
 * Zieht die nächste Karte zufällig, aber gewichtet: gelbe und neue Wörter kommen häufiger dran
 * als grüne. Innerhalb einer Runde wiederholt sich kein Wort, solange es noch ungezeigte gibt,
 * und nie zweimal direkt hintereinander.
 *
 * piles: Map Wort → Stapel zu Beginn der Runde; fehlende Wörter gelten als neu.
 * random: () => [0, 1)
 */
export class Picker {
  constructor(words, piles, random = Math.random) {
    this.words = words;
    this.piles = piles;
    this.random = random;
  }

  next(shown, last) {
    let candidates = this.words.filter((w) => !shown.has(w));
    if (candidates.length === 0) {
      candidates = this.words.filter((w) => w !== last);
    }
    if (candidates.length === 0) {
      return this.words[0];
    }
    const total = candidates.reduce((sum, w) => sum + this.weight(w), 0);
    let r = Math.floor(this.random() * total);
    for (const w of candidates) {
      r -= this.weight(w);
      if (r < 0) return w;
    }
    return candidates[candidates.length - 1];
  }

  weight(word) {
    const pile = this.piles.get(word);
    if (pile === undefined || pile === NEW) return WEIGHT_NEW;
    return pile === GOOD ? WEIGHT_GOOD : WEIGHT_PRACTICE;
  }
}

// ------------------------------------------------------------------ Runde

/**
 * Eine Runde: zufällige Karten, bis die Zeit um ist. Eine Karte, die beim Ablauf noch offen ist,
 * darf noch bewertet werden. Die Uhr läuft nur, solange nicht pausiert ist; alle Zeitpunkte
 * kommen von außen (performance.now()), damit sich das testen lässt.
 */
export class Round {
  constructor(durationMillis, picker) {
    this.durationMillis = durationMillis;
    this.picker = picker;
    this.attempts = [];
    this.shown = new Set();
    this.banked = 0;
    this.runningSince = -1;
    this.current = null;
    this.last = null;
    this.shownAt = 0;
    this.started = false;
    this.finished = false;
  }

  start(now) {
    if (this.started) return;
    this.started = true;
    this.runningSince = now;
    this.next(now);
  }

  pause(now) {
    if (this.runningSince >= 0) {
      this.banked += now - this.runningSince;
      this.runningSince = -1;
    }
  }

  resume(now) {
    if (this.started && !this.finished && this.runningSince < 0) {
      this.runningSince = now;
    }
  }

  elapsed(now) {
    return this.banked + (this.runningSince >= 0 ? now - this.runningSince : 0);
  }

  remaining(now) {
    return Math.max(0, this.durationMillis - this.elapsed(now));
  }

  isTimeUp(now) {
    return this.elapsed(now) >= this.durationMillis;
  }

  /** Aktuelle Karte grün (good) oder gelb bewerten; danach ist bis next() keine offen. */
  answer(good, now) {
    if (this.current === null || this.finished) {
      throw new Error('Keine offene Karte');
    }
    const attempt = { word: this.current, millis: Math.round(this.elapsed(now) - this.shownAt), good };
    this.attempts.push(attempt);
    this.last = this.current;
    this.current = null;
    return attempt;
  }

  /** Nächste Karte aufdecken. False, wenn die Zeit um ist — dann ist die Runde beendet. */
  next(now) {
    if (this.finished) return false;
    if (this.isTimeUp(now)) {
      this.current = null;
      this.finished = true;
      this.pause(now);
      return false;
    }
    this.current = this.picker.next(this.shown, this.last);
    this.shown.add(this.current);
    this.shownAt = this.elapsed(now);
    return true;
  }

  goodCount() {
    return this.attempts.filter((a) => a.good).length;
  }

  practiceCount() {
    return this.attempts.length - this.goodCount();
  }
}

// ------------------------------------------------------------------ Runden auswerten

export const total = (round) => round.attempts.length;
export const good = (round) => round.attempts.filter((a) => a.good).length;
export const practice = (round) => total(round) - good(round);
export const listOf = (round) => round.list || DEFAULT_LIST;

/** Mehr Wörter zuerst, bei Gleichstand mehr grüne, dann die frühere Runde. */
export function compareRanking(a, b) {
  return total(b) - total(a) || good(b) - good(a) || a.timestamp - b.timestamp;
}

/** Bestenliste einer Wortliste. */
export function ranked(rounds, listId) {
  return rounds.filter((r) => listOf(r) === listId).sort(compareRanking);
}

/** Beste Runde derselben Liste außer dieser — null, wenn es die erste ist. Für "Neuer Rekord!". */
export function bestExcept(rounds, round) {
  let best = null;
  for (const r of rounds) {
    if (r !== round && listOf(r) === listOf(round) && (best === null || compareRanking(r, best) < 0)) {
      best = r;
    }
  }
  return best;
}

/**
 * Verlauf je Wort für jedes Wort aus words, in dieser Reihenfolge. Das Wort ist die Kennung,
 * gezählt wird über alle Listen: Wer "und" in einer Liste kann, kann es auch in der nächsten.
 * Wörter, die keine Liste mehr kennt, bleiben gespeichert, zählen aber für keinen Stapel.
 */
export function wordStats(rounds, words) {
  const stats = new Map();
  for (const w of words) {
    stats.set(w, { word: w, millis: [], goodCount: 0, lastGood: null });
  }
  const chronological = [...rounds].sort((a, b) => a.timestamp - b.timestamp);
  for (const r of chronological) {
    for (const a of r.attempts) {
      const s = stats.get(a.word);
      if (s) {
        s.millis.push(a.millis);
        if (a.good) s.goodCount++;
        s.lastGood = a.good;
      }
    }
  }
  return stats;
}

export function pileOf(stat) {
  if (stat.lastGood === null) return NEW;
  return stat.lastGood ? GOOD : PRACTICE;
}

/** Durchschnitt der letzten RECENT Lesezeiten, 0 wenn nie gelesen. */
export function recentAverage(stat) {
  const recent = stat.millis.slice(-RECENT);
  if (recent.length === 0) return 0;
  return Math.floor(recent.reduce((s, m) => s + m, 0) / recent.length);
}

export function piles(stats) {
  const result = new Map();
  for (const s of stats.values()) result.set(s.word, pileOf(s));
  return result;
}

// ------------------------------------------------------------------ JSON

/** Gleiches Format wie der Export der Android-App; "list" ist neu und dort ohne Bedeutung. */
export function toJson(rounds) {
  return {
    format: FORMAT,
    version: VERSION,
    rounds: rounds.map((r) => ({
      id: r.id,
      timestamp: r.timestamp,
      seconds: r.seconds,
      ...(r.list ? { list: r.list } : {}),
      attempts: r.attempts.map((a) => ({ word: a.word, millis: a.millis, good: a.good })),
    })),
  };
}

export function fromJson(json) {
  if (!json || json.format !== FORMAT) {
    throw new Error('Keine Lesen-Datei');
  }
  if ((json.version | 0) > VERSION) {
    throw new Error('Datei von einer neueren Version');
  }
  if (!Array.isArray(json.rounds)) {
    throw new Error('Keine Runden in der Datei');
  }
  return json.rounds.map((r) => {
    if (typeof r.id !== 'string' || typeof r.timestamp !== 'number' || !Array.isArray(r.attempts)) {
      throw new Error('Runde unvollständig');
    }
    const round = {
      id: r.id,
      timestamp: r.timestamp,
      seconds: typeof r.seconds === 'number' ? r.seconds : 60,
      attempts: r.attempts.map((a) => {
        if (typeof a.word !== 'string' || typeof a.millis !== 'number' || typeof a.good !== 'boolean') {
          throw new Error('Karte unvollständig');
        }
        return { word: a.word, millis: a.millis, good: a.good };
      }),
    };
    if (typeof r.list === 'string' && r.list) round.list = r.list;
    return round;
  });
}

/** Ergänzt um Runden mit unbekannter id; gibt die Anzahl neuer Runden zurück. */
export function merge(rounds, other) {
  const known = new Set(rounds.map((r) => r.id));
  let added = 0;
  for (const r of other) {
    if (!known.has(r.id)) {
      known.add(r.id);
      rounds.push(r);
      added++;
    }
  }
  return added;
}

// ------------------------------------------------------------------ Anzeige

/** Restzeit als m:ss, aufgerundet — "0:01" bis zur letzten Sekunde, dann "0:00". */
export function formatCountdown(millis) {
  const seconds = Math.ceil(millis / 1000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

/** Lesezeit eines Wortes, z. B. "1,4 s". */
export function formatSeconds(millis) {
  return `${(millis / 1000).toFixed(1).replace('.', ',')} s`;
}
