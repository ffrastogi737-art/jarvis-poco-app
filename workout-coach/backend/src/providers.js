// Provider adapters. All three expose OpenAI-compatible chat-completions endpoints, so one
// adapter covers them; only base URL, key and model differ. Base URLs can be overridden via env
// in case a provider moves its endpoint.

export const PROVIDERS = {
  gemini: { baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai/chat/completions', keyEnv: 'GEMINI_API_KEY', modelEnv: 'GEMINI_MODEL', urlEnv: 'GEMINI_BASE_URL' },
  groq: { baseUrl: 'https://api.groq.com/openai/v1/chat/completions', keyEnv: 'GROQ_API_KEY', modelEnv: 'GROQ_MODEL', urlEnv: 'GROQ_BASE_URL' },
  openrouter: { baseUrl: 'https://openrouter.ai/api/v1/chat/completions', keyEnv: 'OPENROUTER_API_KEY', modelEnv: 'OPENROUTER_MODEL', urlEnv: 'OPENROUTER_BASE_URL' },
};

export class ProviderError extends Error {
  constructor(code, status, retryAfter) {
    super(code);
    this.code = code; // timeout | rate_limited | provider_auth | provider_error | network
    this.status = status;
    this.retryAfter = retryAfter;
  }
}

/** Calls the provider once (no automatic retries: a repeated call could be billed twice). */
export async function callProvider({ fetchImpl, provider, messages, maxTokens, timeoutMs }) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  let res;
  try {
    res = await fetchImpl(provider.url, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${provider.key}`,
      },
      body: JSON.stringify({
        model: provider.model,
        messages,
        temperature: 0.6,
        max_tokens: maxTokens,
        response_format: { type: 'json_object' },
      }),
    });
  } catch (e) {
    throw new ProviderError(e.name === 'AbortError' ? 'timeout' : 'network');
  } finally {
    clearTimeout(timer);
  }
  if (res.status === 429) throw new ProviderError('rate_limited', 429, res.headers.get('retry-after'));
  if (res.status === 401 || res.status === 403) throw new ProviderError('provider_auth', res.status);
  if (!res.ok) throw new ProviderError('provider_error', res.status);
  let data;
  try {
    data = await res.json();
  } catch {
    throw new ProviderError('provider_error', res.status);
  }
  const text = data?.choices?.[0]?.message?.content;
  if (typeof text !== 'string') throw new ProviderError('provider_error', res.status);
  return {
    text,
    usage: {
      promptTokens: data?.usage?.prompt_tokens ?? null,
      completionTokens: data?.usage?.completion_tokens ?? null,
    },
  };
}
