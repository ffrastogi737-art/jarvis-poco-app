import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createHandler, loadConfig, parseModelOutput } from '../src/app.js';
import { SYSTEM_PROMPT } from '../src/prompt.js';

const TOKEN = 'test-app-token-0123456789abcdef';
const KEY = 'provider-secret-key-should-never-leak';

function config(over = {}) {
  return {
    ...loadConfig({ APP_TOKEN: TOKEN, GEMINI_API_KEY: KEY, GEMINI_MODEL: 'test-model', GROQ_API_KEY: KEY, GROQ_MODEL: 'groq-test-model' }),
    ...over,
  };
}

function okFetch(content = '{"type":"CHAT","message":"Namaste!"}', calls = []) {
  return async (url, init) => {
    calls.push({ url, init });
    return new Response(JSON.stringify({ choices: [{ message: { content } }], usage: { prompt_tokens: 50, completion_tokens: 10 } }), { status: 200 });
  };
}

async function withServer(handler, fn) {
  const server = createServer(handler);
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  try { await fn(base); } finally { server.close(); }
}

const body = (over = {}) => JSON.stringify({ provider: 'gemini', requestId: 'r1', language: 'ENGLISH', context: { language: 'ENGLISH' }, messages: [{ role: 'user', content: 'hi' }], ...over });
const post = (base, b = body(), token = TOKEN) => fetch(`${base}/v1/coach`, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: b });

test('happy path returns validated reply and usage; logs carry no content or secrets', async () => {
  const logs = [];
  const calls = [];
  await withServer(createHandler(config(), { fetchImpl: okFetch(undefined, calls), log: (o) => logs.push(JSON.stringify(o)) }), async (base) => {
    const res = await post(base);
    assert.equal(res.status, 200);
    const json = await res.json();
    assert.equal(json.reply.message, 'Namaste!');
    assert.equal(json.model, 'test-model');
    assert.equal(json.usage.completionTokens, 10);
    assert.match(calls[0].url, /generativelanguage\.googleapis\.com/);
    const sent = JSON.parse(calls[0].init.body);
    assert.deepEqual(sent.response_format, { type: 'json_object' });
    assert.equal(sent.messages[0].content, SYSTEM_PROMPT);
    assert.equal(calls[0].init.headers.Authorization, `Bearer ${KEY}`);
  });
  const all = logs.join('\n');
  assert.ok(!all.includes(KEY) && !all.includes(TOKEN) && !all.includes('hi"') && !all.includes('Namaste'));
});

test('rejects bad token', async () => {
  await withServer(createHandler(config(), { fetchImpl: okFetch(), log: () => {} }), async (base) => {
    assert.equal((await post(base, body(), 'wrong-token-wrong-token-wrong')).status, 401);
    assert.equal((await fetch(`${base}/v1/coach`, { method: 'POST', body: body() })).status, 401);
  });
});

test('validates requests', async () => {
  await withServer(createHandler(config(), { fetchImpl: okFetch(), log: () => {} }), async (base) => {
    assert.equal((await post(base, '{nope')).status, 400);
    assert.equal((await post(base, body({ messages: [] }))).status, 400);
    assert.equal((await post(base, body({ messages: [{ role: 'system', content: 'x' }] }))).status, 400);
    assert.equal((await post(base, body({ messages: [{ role: 'user', content: 'x'.repeat(5000) }] }))).status, 400);
    assert.equal((await post(base, 'x'.repeat(70 * 1024))).status, 413);
  });
});

test('unconfigured provider is reported, configured one can be switched', async () => {
  const calls = [];
  await withServer(createHandler(config(), { fetchImpl: okFetch(undefined, calls), log: () => {} }), async (base) => {
    const res = await post(base, body({ provider: 'openrouter' }));
    assert.equal(res.status, 400);
    assert.equal((await res.json()).error, 'provider_unavailable');
    const ok = await post(base, body({ provider: 'groq', requestId: 'g1' }));
    assert.equal(ok.status, 200);
    assert.match(calls[0].url, /api\.groq\.com/);
    assert.equal((await ok.json()).model, 'groq-test-model');
  });
});

test('malformed model output -> 502 malformed_model_output', async () => {
  await withServer(createHandler(config(), { fetchImpl: okFetch('Sure! Here is a plan without JSON'), log: () => {} }), async (base) => {
    const res = await post(base);
    assert.equal(res.status, 502);
    assert.equal((await res.json()).error, 'malformed_model_output');
  });
  assert.equal(parseModelOutput('```json\n{"message":"ok"}\n```').message, 'ok');
  assert.equal(parseModelOutput('{"message":""}'), null);
  assert.equal(parseModelOutput('{"message":"x","workout":{"exercises":"no"}}'), null);
});

test('per-minute rate limit and daily budget', async () => {
  let t = Date.parse('2026-10-21T10:00:00Z');
  const h = createHandler(config({ ratePerMinute: 2, dailyRequests: 3 }), { fetchImpl: okFetch(), now: () => t, log: () => {} });
  await withServer(h, async (base) => {
    assert.equal((await post(base, body({ requestId: 'a' }))).status, 200);
    assert.equal((await post(base, body({ requestId: 'b' }))).status, 200);
    const limited = await post(base, body({ requestId: 'c' }));
    assert.equal(limited.status, 429);
    assert.equal((await limited.json()).error, 'rate_limited');
    t += 61_000;
    assert.equal((await post(base, body({ requestId: 'd' }))).status, 200);
    t += 61_000;
    const budget = await post(base, body({ requestId: 'e' }));
    assert.equal((await budget.json()).error, 'budget_exceeded');
    t += 86_400_000; // next day resets
    assert.equal((await post(base, body({ requestId: 'f' }))).status, 200);
  });
});

test('token budget', async () => {
  await withServer(createHandler(config({ dailyTokens: 60 }), { fetchImpl: okFetch(), log: () => {} }), async (base) => {
    assert.equal((await post(base, body({ requestId: 'a' }))).status, 200); // 60 tokens used
    assert.equal((await (await post(base, body({ requestId: 'b' }))).json()).error, 'budget_exceeded');
  });
});

test('same requestId is not billed twice (idempotent retry)', async () => {
  const calls = [];
  await withServer(createHandler(config(), { fetchImpl: okFetch(undefined, calls), log: () => {} }), async (base) => {
    const [a, b] = await Promise.all([post(base), post(base)]);
    assert.equal(a.status, 200); assert.equal(b.status, 200);
    assert.equal((await post(base)).status, 200);
    assert.equal(calls.length, 1);
  });
});

test('provider errors are mapped, never retried automatically', async () => {
  const cases = [
    [async () => new Response('{}', { status: 429, headers: { 'retry-after': '7' } }), 429, 'rate_limited'],
    [async () => new Response('{}', { status: 401 }), 502, 'provider_auth'],
    [async () => new Response('oops', { status: 500 }), 502, 'provider_error'],
    [async () => { throw new TypeError('fetch failed'); }, 502, 'network'],
  ];
  for (const [impl, status, code] of cases) {
    let n = 0;
    await withServer(createHandler(config(), { fetchImpl: async (...a) => { n++; return impl(...a); }, log: () => {} }), async (base) => {
      const res = await post(base);
      assert.equal(res.status, status);
      assert.equal((await res.json()).error, code);
      if (code === 'rate_limited') assert.equal(res.headers.get('retry-after'), '7');
    });
    assert.equal(n, 1);
  }
});

test('timeout -> 504', async () => {
  const slow = (url, init) => new Promise((_, reject) => init.signal.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' }))));
  await withServer(createHandler(config({ timeoutMs: 50 }), { fetchImpl: slow, log: () => {} }), async (base) => {
    const res = await post(base);
    assert.equal(res.status, 504);
  });
});

test('health endpoint does not expose keys', async () => {
  await withServer(createHandler(config(), { fetchImpl: okFetch(), log: () => {} }), async (base) => {
    const text = await (await fetch(`${base}/health`)).text();
    assert.ok(!text.includes(KEY) && !text.includes(TOKEN));
    assert.deepEqual(JSON.parse(text).providers.sort(), ['gemini', 'groq']);
  });
});
