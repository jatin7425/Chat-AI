/**
 * Prefixes every console.log/warn/error call with an ISO timestamp. Imported first thing in
 * index.ts so every log line -- including ones from deep inside library stack traces printed via
 * console.error -- is actually timestamped, which matters a lot for a long-running local process
 * where "is this error from just now or from an hour ago" is otherwise unanswerable from the log
 * file alone.
 */
const original = {
  log: console.log.bind(console),
  warn: console.warn.bind(console),
  error: console.error.bind(console),
};

function timestamp(): string {
  return new Date().toISOString();
}

console.log = (...args: unknown[]) => original.log(`[${timestamp()}]`, ...args);
console.warn = (...args: unknown[]) => original.warn(`[${timestamp()}]`, ...args);
console.error = (...args: unknown[]) => original.error(`[${timestamp()}]`, ...args);
