import { timingSafeEqual } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { PROVIDERS, ProviderError, callProvider } from './providers.js';
import { buildMessages } from './prompt.js';

export function loadConfig(env = process.env) {
  const num = (k, d) => (env[k] && Number.isFinite(Number(env[k])) ? Number(env[k]) : d);
  const providers = {};
  for (const [name, p] of Object.entries(PROVIDERS)) {
    const key = env[p.keyEnv];
    const model = env[p.modelEnv];
    if (key && model) providers[name] = { name, key, model, url: env[p.urlEnv] || p.baseUrl };
  }
  return {
    appToken: env.APP_TOKEN || '',
    port: num('PORT', 8787),
    defaultProvider: env.DEFAULT_PROVIDER || 'gemini',
    providers,
    ratePerMinute: num('RATE_LIMIT_PER_MINUTE', 10),
    dailyRequests: num('DAILY_REQUEST_LIMIT', 200),
    dailyTokens: num('DAILY_TOKEN_LIMIT', 200000),
    maxOutputTokens: num('MAX_OUTPUT_TOKENS', 800),
    timeoutMs: num('REQUEST_TIMEOUT_MS', 25000),
    usageFile: env.USAGE_FILE || null,
  };
}

/** Daily usage counters, optionally persisted to a JSON file so restarts don't reset the budget. */
export class UsageStore {
  constructor(file, now) {
    this.file = file;
    this.now = now;
    this.data = { day: '', requests: 0, tokens: 0 };
    if (file) try { this.data = JSON.parse(readFileSync(file, 'utf8')); } catch { /* first run */ }
  }
  today() { return new Date(this.now()).toISOString().slice(0, 10); }
  current() {
    if (this.data.day !== this.today()) this.data = { day: this.today(), requests: 0, tokens: 0 };
    return this.data;
  }
  add(requests, tokens) {
    const d = this.current();
    d.requests += requests;
    d.tokens += tokens;
    if (this.file) try { writeFileSync(this.file, JSON.stringify(d)); } catch { /* non-fatal */ }
  }
}

const MAX_BODY = 64 * 1024;
const IDEMPOTENCY_TTL_MS = 10 * 60 * 1000;

function tokenMatches(header, expected) {
  const given = Buffer.from((header || '').replace(/^Bearer\s+/i, ''));
  const want = Buffer.from(expected);
  return given.length === want.length && timingSafeEqual(given, want);
}

export function validateBody(b, config) {
  if (!b || typeof b !== 'object') return 'body must be a JSON object';
  if (typeof b.requestId !== 'string' || b.requestId.length < 1 || b.requestId.length > 100) return 'requestId required';
  if (!Array.isArray(b.messages) || b.messages.length < 1 || b.messages.length > 30) return 'messages must have 1-30 items';
  for (const m of b.messages) {
    if (!m || (m.role !== 'user' && m.role !== 'assistant') || typeof m.content !== 'string' || m.content.length > 4000) return 'invalid message';
  }
  if (b.messages[b.messages.length - 1].role !== 'user') return 'last message must be from the user';
  if (b.context != null && typeof b.context !== 'object') return 'context must be an object';
  if (b.provider != null && typeof b.provider !== 'string') return 'provider must be a string';
  return null;
}

/** Checks the model output is a JSON object the app can parse. The app re-validates the details. */
export function parseModelOutput(text) {
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start < 0 || end <= start) return null;
  let obj;
  try { obj = JSON.parse(text.slice(start, end + 1)); } catch { return null; }
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return null;
  if (typeof obj.message !== 'string' || !obj.message.trim() || obj.message.length > 4000) return null;
  if (obj.workout != null && (typeof obj.workout !== 'object' || !Array.isArray(obj.workout.exercises))) return null;
  return obj;
}

/**
 * Creates the request handler. Dependencies are injectable for tests.
 * Logs contain only metadata — never message content, tokens or keys.
 */
export function createHandler(config, { fetchImpl = fetch, now = Date.now, log = (o) => console.log(JSON.stringify(o)) } = {}) {
  const usage = new UsageStore(config.usageFile, now);
  const window = []; // request timestamps for the per-minute limit
  const idempotency = new Map(); // requestId -> { expires, status, payload } | { pending: Promise }

  const send = (res, status, payload, headers = {}) => {
    res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', ...headers });
    res.end(JSON.stringify(payload));
  };

  async function handleCoach(body) {
    const providerName = body.provider || config.defaultProvider;
    const provider = config.providers[providerName];
    if (!provider) return { status: 400, payload: { error: 'provider_unavailable', configured: Object.keys(config.providers) } };

    const t = now();
    while (window.length && window[0] <= t - 60_000) window.shift();
    if (window.length >= config.ratePerMinute) return { status: 429, payload: { error: 'rate_limited' }, headers: { 'Retry-After': '60' } };
    const u = usage.current();
    if (u.requests >= config.dailyRequests || u.tokens >= config.dailyTokens) return { status: 429, payload: { error: 'budget_exceeded' } };
    window.push(t);

    try {
      const result = await callProvider({ fetchImpl, provider, messages: buildMessages(body), maxTokens: config.maxOutputTokens, timeoutMs: config.timeoutMs });
      const tokens = (result.usage.promptTokens || 0) + (result.usage.completionTokens || 0);
      usage.add(1, tokens);
      const reply = parseModelOutput(result.text);
      if (!reply) return { status: 502, payload: { error: 'malformed_model_output' }, tokens };
      return { status: 200, payload: { reply, provider: providerName, model: provider.model, usage: result.usage }, tokens };
    } catch (e) {
      if (!(e instanceof ProviderError)) throw e;
      usage.add(1, 0); // a request that reached the provider may still be billed
      if (e.code === 'timeout') return { status: 504, payload: { error: 'timeout' } };
      if (e.code === 'rate_limited') return { status: 429, payload: { error: 'rate_limited' }, headers: e.retryAfter ? { 'Retry-After': String(e.retryAfter) } : {} };
      return { status: 502, payload: { error: e.code, upstreamStatus: e.status ?? null } };
    }
  }

  return async function handler(req, res) {
    const started = now();
    if (req.method === 'GET' && req.url === '/health') {
      return send(res, 200, { ok: true, providers: Object.keys(config.providers), usage: usage.current() });
    }
    if (req.method !== 'POST' || req.url !== '/v1/coach') return send(res, 404, { error: 'not_found' });
    if (!tokenMatches(req.headers.authorization, config.appToken)) return send(res, 401, { error: 'unauthorized' });

    let raw = '';
    let tooBig = false;
    for await (const chunk of req) {
      raw += chunk;
      if (raw.length > MAX_BODY) { tooBig = true; break; }
    }
    if (tooBig) return send(res, 413, { error: 'body_too_large' });
    let body;
    try { body = JSON.parse(raw); } catch { return send(res, 400, { error: 'invalid_json' }); }
    const invalid = validateBody(body, config);
    if (invalid) return send(res, 400, { error: 'invalid_request', detail: invalid });

    // Idempotency: a retried request id gets the stored result instead of a second billable call.
    for (const [k, v] of idempotency) if (v.expires && v.expires < now()) idempotency.delete(k);
    let entry = idempotency.get(body.requestId);
    let replayed = true;
    if (!entry) {
      replayed = false;
      entry = { pending: handleCoach(body) };
      idempotency.set(body.requestId, entry);
    }
    let result;
    try {
      result = entry.pending ? await entry.pending : entry;
    } catch (e) {
      idempotency.delete(body.requestId);
      log({ ts: new Date(now()).toISOString(), requestId: body.requestId.slice(0, 40), status: 500, error: 'internal' });
      return send(res, 500, { error: 'internal' });
    }
    if (!replayed) {
      // Only successful answers are cached; failures may be retried for real.
      if (result.status === 200) idempotency.set(body.requestId, { ...result, expires: now() + IDEMPOTENCY_TTL_MS });
      else idempotency.delete(body.requestId);
    }
    log({ ts: new Date(now()).toISOString(), requestId: body.requestId.slice(0, 40), provider: body.provider || config.defaultProvider, status: result.status, ms: now() - started, tokens: result.tokens ?? 0, replayed });
    return send(res, result.status, result.payload, result.headers);
  };
}
