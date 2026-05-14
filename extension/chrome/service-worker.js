const DEFAULT_SETTINGS = {
  enabled: true,
  sensitivity: 60,
  categories: {
    abuse: true,
    hate: true,
    insult: true,
    spam: true
  },
  interventionMode: "mask",
  customBlockWords: "",
  customAllowWords: "",
  blockedDomains: "",
  warnDomains: "",
  showReason: true,
  siteProtectionEnabled: true,
  backendApiBaseUrl: "http://127.0.0.1:8000",
  requestTimeoutMs: 10000,
  stats: {
    blockedCount: 0,
    falsePositiveCount: 0,
    byCategory: {
      abuse: 0,
      hate: 0,
      insult: 0,
      spam: 0
    },
    averageLatencyMs: 0,
    totalAnalyzedCount: 0
  }
};

const BACKEND_HEALTH_TIMEOUT_MS = 2500;
const RESPONSE_CACHE_LIMIT = 2000;
const SAFE_RESPONSE_CACHE_TTL_MS = 5000;
const OFFENSIVE_RESPONSE_CACHE_TTL_MS = 90000;
const RESPONSE_CACHE_SCHEMA_VERSION = "sw-v10";
const SMALL_ANALYZE_BATCH_CHUNK_SIZE = 2;
const MEDIUM_ANALYZE_BATCH_CHUNK_SIZE = 4;
const LARGE_ANALYZE_BATCH_CHUNK_SIZE = 6;
const XL_ANALYZE_BATCH_CHUNK_SIZE = 12;
const FOREGROUND_ANALYZE_MIN_TIMEOUT_MS = 900;
const RECONCILE_ANALYZE_TIMEOUT_CAP_MS = 1400;
const BACKGROUND_ANALYZE_TIMEOUT_CAP_MS = 1000;
const SELF_TEST_ANALYZE_TIMEOUT_CAP_MS = 5000;
const FOREGROUND_ACTIVE_PREEMPT_AFTER_MS = 820;
const FULL_ANALYSIS_RESPONSE_CACHE = new Map();
const FULL_ANALYSIS_IN_FLIGHT_REQUESTS = new Map();
const SITE_POLICY_CACHE = new Map();
const SITE_POLICY_IN_FLIGHT = new Map();
const SITE_POLICY_BY_TAB = new Map();
const SITE_POLICY_CACHE_TTL_MS = 10 * 60 * 1000;
const SITE_POLICY_SCHEMA_VERSION = "site-policy-v1";
const BACKEND_QUEUE_LIMIT_BY_MODE = new Map([
  ["foreground", 2],
  ["reconcile", 1],
  ["background-validation", 1],
  ["self-test", 1]
]);
const BACKEND_REQUEST_QUEUES = new Map([
  ["foreground", []],
  ["reconcile", []],
  ["background-validation", []],
  ["self-test", []]
]);
const BACKEND_REQUEST_PRIORITY = [
  "foreground",
  "reconcile",
  "background-validation",
  "self-test"
];
let isBackendRequestRunning = false;
let activeBackendRequest = null;

function normalizeAnalyzeBatchMode(value) {
  const normalized = String(value || "").trim().toLowerCase();
  if (normalized === "reconcile" || normalized === "background-validation" || normalized === "self-test") {
    return normalized;
  }
  return "foreground";
}

function dequeueNextBackendRequest() {
  for (const mode of BACKEND_REQUEST_PRIORITY) {
    const queue = BACKEND_REQUEST_QUEUES.get(mode);
    if (queue?.length) {
      return queue.shift();
    }
  }

  return null;
}

function getBackendQueuedRequestCount() {
  let count = 0;
  for (const queue of BACKEND_REQUEST_QUEUES.values()) {
    count += queue.length;
  }
  return count;
}

function dropQueuedBackendRequests(queue, mode, reasonCode = "QUEUE_DROPPED") {
  while (queue?.length) {
    const dropped = queue.shift();
    if (typeof dropped?.reject === "function") {
      dropped.reject(new BackendRequestError(reasonCode, "오래된 백엔드 분석 요청을 건너뛰었습니다.", {
        retryable: true,
        detail: {
          mode,
          queueAgeMs: Math.max(0, Date.now() - Number(dropped.queuedAt || Date.now()))
        }
      }));
    }
  }
}

function drainBackendRequestQueue() {
  if (isBackendRequestRunning) {
    return;
  }

  const nextRequest = dequeueNextBackendRequest();
  if (!nextRequest) {
    return;
  }

  const abortController = new AbortController();
  isBackendRequestRunning = true;
  activeBackendRequest = {
    mode: nextRequest.mode,
    startedAt: Date.now(),
    abortController
  };
  Promise.resolve()
    .then(() => nextRequest.operation({
      mode: nextRequest.mode,
      queueWaitMs: Date.now() - nextRequest.queuedAt,
      queueDepthAtEnqueue: nextRequest.queueDepthAtEnqueue,
      queueDepthAtStart: getBackendQueuedRequestCount() + 1,
      abortSignal: abortController.signal
    }))
    .then(nextRequest.resolve, nextRequest.reject)
    .finally(() => {
      isBackendRequestRunning = false;
      if (activeBackendRequest?.abortController === abortController) {
        activeBackendRequest = null;
      }
      drainBackendRequestQueue();
    });
}

function enqueueBackendRequest(mode, operation) {
  const normalizedMode = normalizeAnalyzeBatchMode(mode);
  const queue = BACKEND_REQUEST_QUEUES.get(normalizedMode) || BACKEND_REQUEST_QUEUES.get("foreground");
  const queuedAt = Date.now();

  return new Promise((resolve, reject) => {
    if (
      normalizedMode === "foreground" &&
      isBackendRequestRunning &&
      activeBackendRequest?.mode &&
      activeBackendRequest.mode !== "foreground" &&
      activeBackendRequest.abortController instanceof AbortController
    ) {
      activeBackendRequest.abortController.abort("PREEMPTED_BY_FOREGROUND");
    }

    if (
      normalizedMode === "foreground" &&
      isBackendRequestRunning &&
      activeBackendRequest?.mode === "foreground" &&
      activeBackendRequest.abortController instanceof AbortController &&
      Date.now() - Number(activeBackendRequest.startedAt || Date.now()) >= FOREGROUND_ACTIVE_PREEMPT_AFTER_MS
    ) {
      activeBackendRequest.abortController.abort("PREEMPTED_BY_FOREGROUND");
    }

    if (normalizedMode === "foreground") {
      dropQueuedBackendRequests(queue, normalizedMode);
    }

    const queueLimit = Math.max(1, Number(BACKEND_QUEUE_LIMIT_BY_MODE.get(normalizedMode) || 1));
    while (queue.length >= queueLimit) {
      dropQueuedBackendRequests(queue, normalizedMode);
    }

    queue.push({
      mode: normalizedMode,
      queuedAt,
      queueDepthAtEnqueue: getBackendQueuedRequestCount() + (isBackendRequestRunning ? 1 : 0),
      operation,
      resolve,
      reject
    });
    drainBackendRequestQueue();
  });
}

class BackendRequestError extends Error {
  constructor(code, message, options = {}) {
    super(message);
    this.name = "BackendRequestError";
    this.code = code;
    this.retryable = Boolean(options.retryable);
    this.status = options.status ?? null;
    this.detail = options.detail ?? null;
  }
}

function sanitizeApiBaseUrl(value) {
  const normalized = String(value || DEFAULT_SETTINGS.backendApiBaseUrl).trim();
  if (!normalized) return DEFAULT_SETTINGS.backendApiBaseUrl;
  return normalized.replace(/\/+$/, "");
}

function normalizeRequestTimeoutMs(value) {
  const numberValue = Number(value);
  if (Number.isNaN(numberValue)) return DEFAULT_SETTINGS.requestTimeoutMs;
  return Math.max(1000, Math.min(30000, Math.round(numberValue)));
}

function normalizeForegroundRequestTimeoutMs(value, fallbackMs) {
  const numberValue = Number(value);
  if (Number.isNaN(numberValue)) return fallbackMs;
  return Math.max(150, Math.min(5000, Math.round(numberValue)));
}

function chunkArray(items, chunkSize) {
  const nextChunkSize = Math.max(1, Number(chunkSize) || 1);
  const chunks = [];

  for (let index = 0; index < items.length; index += nextChunkSize) {
    chunks.push(items.slice(index, index + nextChunkSize));
  }

  return chunks;
}

function getAnalyzeBatchChunkSize(requestTimeoutMs, textCount, mode = "foreground") {
  if (textCount <= 1) {
    return 1;
  }

  const normalizedMode = normalizeAnalyzeBatchMode(mode);
  if (normalizedMode === "background-validation") {
    return Math.min(SMALL_ANALYZE_BATCH_CHUNK_SIZE, textCount);
  }

  if (normalizedMode === "reconcile") {
    return Math.min(SMALL_ANALYZE_BATCH_CHUNK_SIZE, textCount);
  }

  if (requestTimeoutMs <= 450) {
    return Math.min(SMALL_ANALYZE_BATCH_CHUNK_SIZE, textCount);
  }

  if (requestTimeoutMs <= 1200) {
    return Math.min(MEDIUM_ANALYZE_BATCH_CHUNK_SIZE, textCount);
  }

  if (requestTimeoutMs <= 2500) {
    return Math.min(LARGE_ANALYZE_BATCH_CHUNK_SIZE, textCount);
  }

  return Math.min(XL_ANALYZE_BATCH_CHUNK_SIZE, textCount);
}

function shouldSplitAnalyzeBatchRequest(error, chunkLength, mode = "foreground") {
  if (!(error instanceof BackendRequestError) || chunkLength <= 1) {
    return false;
  }

  if (normalizeAnalyzeBatchMode(mode) === "foreground") {
    return false;
  }

  return (
    error.code === "TIMEOUT" ||
    error.code === "HTTP_503" ||
    error.code === "HTTP_504"
  );
}

function shouldTolerateAnalyzeBatchChunkFailure(error, mode) {
  const normalizedMode = normalizeAnalyzeBatchMode(mode);
  if (
    normalizedMode !== "foreground" &&
    normalizedMode !== "background-validation" &&
    normalizedMode !== "reconcile"
  ) {
    return false;
  }

  if (!(error instanceof BackendRequestError)) {
    return false;
  }

  return Boolean(
    error.retryable ||
      error.code === "TIMEOUT" ||
      error.code === "NETWORK_UNREACHABLE" ||
      error.code === "HTTP_503" ||
      error.code === "HTTP_504"
  );
}

function isBenignAnalyzeSkipCode(errorCode) {
  const code = String(errorCode || "");
  return code === "PREEMPTED_BY_FOREGROUND" || code === "QUEUE_DROPPED";
}

function getAnalyzeBatchBackendStatus(skippedChunkCount, errorCode) {
  if (Number(skippedChunkCount || 0) <= 0) {
    return "ready";
  }

  return isBenignAnalyzeSkipCode(errorCode) ? "ready" : "degraded";
}

function createSkippedAnalyzeBatchResults(texts) {
  return texts.map((text) => ({
    __shieldtextSkipped: true,
    original: text,
    is_offensive: false,
    is_profane: false,
    is_toxic: false,
    is_hate: false,
    scores: {
      profanity: 0,
      toxicity: 0,
      hate: 0
    },
    evidence_spans: []
  }));
}

function mergeSettings(stored) {
  return {
    ...DEFAULT_SETTINGS,
    ...(stored || {}),
    siteProtectionEnabled: stored?.siteProtectionEnabled !== false,
    backendApiBaseUrl: sanitizeApiBaseUrl(stored?.backendApiBaseUrl),
    requestTimeoutMs: normalizeRequestTimeoutMs(stored?.requestTimeoutMs),
    categories: {
      ...DEFAULT_SETTINGS.categories,
      ...(stored?.categories || {})
    },
    stats: {
      ...DEFAULT_SETTINGS.stats,
      ...(stored?.stats || {}),
      byCategory: {
        ...DEFAULT_SETTINGS.stats.byCategory,
        ...(stored?.stats?.byCategory || {})
      }
    }
  };
}

async function ensureSettings() {
  const { settings } = await chrome.storage.sync.get("settings");
  const merged = mergeSettings(settings || {});
  await chrome.storage.sync.set({ settings: merged });
}

async function getSettings() {
  const { settings } = await chrome.storage.sync.get("settings");
  return mergeSettings(settings || {});
}

function isUnsupportedTabUrl(url) {
  const value = String(url || "").toLowerCase();
  return (
    value.startsWith("chrome://") ||
    value.startsWith("chrome-extension://") ||
    value.startsWith("edge://") ||
    value.startsWith("about:") ||
    value.startsWith("view-source:")
  );
}

async function getActiveTab() {
  const tabs = await chrome.tabs.query({ active: true, currentWindow: true });
  return tabs?.[0] || null;
}

function isHttpPageUrl(url) {
  const value = String(url || "").trim().toLowerCase();
  return value.startsWith("http://") || value.startsWith("https://");
}

function normalizeDomain(value) {
  const domain = String(value || "").trim().toLowerCase();
  return domain.startsWith("www.") ? domain.slice(4) : domain;
}

function domainFromUrl(value) {
  try {
    const url = new URL(String(value || ""));
    return normalizeDomain(url.hostname);
  } catch {
    return "";
  }
}

function normalizeSitePolicyCacheKey(url) {
  try {
    const parsed = new URL(String(url || ""));
    return [
      SITE_POLICY_SCHEMA_VERSION,
      normalizeDomain(parsed.hostname),
      parsed.pathname || "/"
    ].join("::");
  } catch {
    return "";
  }
}

function getCachedSitePolicy(url) {
  const key = normalizeSitePolicyCacheKey(url);
  if (!key) return null;
  const cached = SITE_POLICY_CACHE.get(key);
  if (!cached) return null;
  if (Number(cached.expiresAt || 0) <= Date.now()) {
    SITE_POLICY_CACHE.delete(key);
    return null;
  }
  return cached.value || null;
}

function setCachedSitePolicy(url, value) {
  const key = normalizeSitePolicyCacheKey(url);
  if (!key) return;
  SITE_POLICY_CACHE.set(key, {
    value,
    expiresAt: Date.now() + SITE_POLICY_CACHE_TTL_MS
  });
}

function getInFlightSitePolicy(url) {
  const key = normalizeSitePolicyCacheKey(url);
  if (!key) return null;
  return SITE_POLICY_IN_FLIGHT.get(key) || null;
}

function createInFlightSitePolicyEntry(url) {
  const key = normalizeSitePolicyCacheKey(url);
  let resolveEntry;
  let rejectEntry;
  const promise = new Promise((resolve, reject) => {
    resolveEntry = resolve;
    rejectEntry = reject;
  });
  if (key) {
    SITE_POLICY_IN_FLIGHT.set(key, promise);
  }
  return { key, promise, resolve: resolveEntry, reject: rejectEntry };
}

function clearInFlightSitePolicyEntry(entry) {
  if (!entry?.key) return;
  if (SITE_POLICY_IN_FLIGHT.get(entry.key) === entry.promise) {
    SITE_POLICY_IN_FLIGHT.delete(entry.key);
  }
}

function parseDomainList(rawValue) {
  return String(rawValue || "")
    .split(/[\n,]/)
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);
}

function matchDomainRule(domain, rawValue) {
  const normalizedDomain = normalizeDomain(domain);
  const rules = parseDomainList(rawValue);
  for (const rule of rules) {
    const normalizedRule = normalizeDomain(rule);
    if (!normalizedRule) continue;
    if (normalizedDomain === normalizedRule || normalizedDomain.endsWith(`.${normalizedRule}`)) {
      return normalizedRule;
    }
  }
  return "";
}

function buildOverrideSitePolicy(url, verdict, matchedRule) {
  const domain = domainFromUrl(url);
  const isBlock = verdict === "block";
  return {
    url,
    domain,
    verdict,
    risk_score: isBlock ? 0.99 : 0.72,
    site_category: "manual-policy",
    security_threat: isBlock,
    harmful_content: !isBlock,
    reasons: [
      `사용자 설정의 ${isBlock ? "차단" : "경고"} 도메인 목록에 '${matchedRule}' 규칙이 등록되어 있다.`
    ],
    matched_entries: [],
    exact_match: null,
    retrieval_ms: 0,
    llm_timing_ms: 0,
    timing_ms: 0,
    agent: {
      mode: "override",
      model: null,
      reason: null,
      response: isBlock
        ? "1. 판정\n수동 차단 도메인으로 등록된 사이트입니다.\n2. 근거\n사용자 정책 목록과 직접 일치했습니다.\n3. 사용자 안내\n계속 접속 전 출처와 안전성을 다시 확인하세요."
        : "1. 판정\n수동 경고 도메인으로 등록된 사이트입니다.\n2. 근거\n사용자 정책 목록과 직접 일치했습니다.\n3. 사용자 안내\n사이트 신뢰성과 목적을 확인한 뒤 필요한 경우에만 계속 접속하세요.",
      sub_agents: null
    }
  };
}

async function ensureTabContentScript(tabId) {
  await chrome.scripting.insertCSS({
    target: { tabId },
    files: ["content-style.css"]
  });

  await chrome.scripting.executeScript({
    target: { tabId },
    files: [
      "content-runtime-status.js",
      "content-editable-overlay.js",
      "content-self-test.js",
      "content-script.js"
    ]
  });
}

async function sendMessageToTabWithInjection(tabId, message) {
  try {
    return await chrome.tabs.sendMessage(tabId, message);
  } catch (sendError) {
    const missingReceiver = String(sendError || "").includes("Receiving end does not exist");
    if (!missingReceiver) {
      throw sendError;
    }

    await ensureTabContentScript(tabId);
    return chrome.tabs.sendMessage(tabId, message);
  }
}

function normalizeSensitivity(value) {
  const numberValue = Number(value);
  if (Number.isNaN(numberValue)) return DEFAULT_SETTINGS.sensitivity;
  return Math.max(0, Math.min(100, Math.round(numberValue)));
}

function normalizeCacheKey(
  value,
  sensitivity = DEFAULT_SETTINGS.sensitivity,
  apiBaseUrl = DEFAULT_SETTINGS.backendApiBaseUrl,
  mode = "foreground"
) {
  const backendKey = sanitizeApiBaseUrl(apiBaseUrl || DEFAULT_SETTINGS.backendApiBaseUrl);
  return [
    RESPONSE_CACHE_SCHEMA_VERSION,
    backendKey,
    normalizeAnalyzeBatchMode(mode),
    normalizeSensitivity(sensitivity),
    String(value || "").replace(/\s+/g, " ").trim()
  ].join("::");
}

function normalizeInFlightCacheKey(
  value,
  sensitivity = DEFAULT_SETTINGS.sensitivity,
  apiBaseUrl = DEFAULT_SETTINGS.backendApiBaseUrl,
  mode = "foreground"
) {
  return normalizeCacheKey(value, sensitivity, apiBaseUrl, mode);
}

function getCachedResponse(cache, text, sensitivity, apiBaseUrl, mode) {
  const key = normalizeCacheKey(text, sensitivity, apiBaseUrl, mode);
  if (!key || !cache.has(key)) return null;

  const cached = cache.get(key);
  if (!cached || typeof cached !== "object") {
    cache.delete(key);
    return null;
  }

  if ("expiresAt" in cached && Number(cached.expiresAt || 0) <= Date.now()) {
    cache.delete(key);
    return null;
  }

  const value = "value" in cached ? cached.value : cached;
  cache.delete(key);
  cache.set(key, {
    value,
    expiresAt: "expiresAt" in cached
      ? cached.expiresAt
      : Date.now() + (value?.is_offensive ? OFFENSIVE_RESPONSE_CACHE_TTL_MS : SAFE_RESPONSE_CACHE_TTL_MS)
  });
  return value;
}

function getInFlightAnalysisResponse(text, sensitivity, apiBaseUrl, mode) {
  const key = normalizeInFlightCacheKey(text, sensitivity, apiBaseUrl, mode);
  if (!key) return null;
  return FULL_ANALYSIS_IN_FLIGHT_REQUESTS.get(key) || null;
}

function createInFlightAnalysisEntry(text, sensitivity, apiBaseUrl, mode) {
  const key = normalizeInFlightCacheKey(text, sensitivity, apiBaseUrl, mode);
  let resolveEntry;
  const promise = new Promise((resolve) => {
    resolveEntry = resolve;
  });

  if (key) {
    FULL_ANALYSIS_IN_FLIGHT_REQUESTS.set(key, promise);
  }

  return {
    key,
    promise,
    resolve: resolveEntry
  };
}

function clearInFlightAnalysisEntry(entry) {
  if (!entry?.key) return;
  if (FULL_ANALYSIS_IN_FLIGHT_REQUESTS.get(entry.key) === entry.promise) {
    FULL_ANALYSIS_IN_FLIGHT_REQUESTS.delete(entry.key);
  }
}

function shouldCacheAnalyzeBatchResult(value) {
  if (!value || typeof value !== "object") {
    return false;
  }

  if (value.__shieldtextSkipped === true) {
    return false;
  }

  return Boolean(
    "is_offensive" in value &&
    "is_profane" in value &&
    "is_toxic" in value &&
    "is_hate" in value
  );
}

function setCachedResponse(cache, text, value, sensitivity, apiBaseUrl, mode) {
  const key = normalizeCacheKey(text, sensitivity, apiBaseUrl, mode);
  if (!key) return;

  if (!shouldCacheAnalyzeBatchResult(value)) {
    cache.delete(key);
    return;
  }

  if (cache.has(key)) {
    cache.delete(key);
  }
  cache.set(key, {
    value,
    expiresAt: Date.now() + (value?.is_offensive ? OFFENSIVE_RESPONSE_CACHE_TTL_MS : SAFE_RESPONSE_CACHE_TTL_MS)
  });

  while (cache.size > RESPONSE_CACHE_LIMIT) {
    const oldestKey = cache.keys().next().value;
    cache.delete(oldestKey);
  }
}

function normalizeBackendError(error, fallbackCode = "UNKNOWN_BACKEND_ERROR") {
  if (error instanceof BackendRequestError) {
    return {
      errorCode: error.code,
      reason: error.message,
      retryable: Boolean(error.retryable),
      status: error.status ?? null,
      detail: error.detail ?? null
    };
  }

  if (error?.name === "AbortError") {
    return {
      errorCode: "ABORTED",
      reason: "요청이 취소되었습니다.",
      retryable: true,
      status: null,
      detail: null
    };
  }

  const message = String(error?.message || error || "");
  if (message.includes("Failed to fetch")) {
    return {
      errorCode: "NETWORK_UNREACHABLE",
      reason: "백엔드 서버에 연결할 수 없습니다.",
      retryable: true,
      status: null,
      detail: message
    };
  }

  return {
    errorCode: fallbackCode,
    reason: message || fallbackCode,
    retryable: false,
    status: null,
    detail: null
  };
}

function summarizeBackendRequestError(error, fallbackCode = "REQUEST_FAILED") {
  const normalized = normalizeBackendError(error, fallbackCode);
  return {
    errorCode: normalized.errorCode,
    reason: normalized.reason,
    retryable: Boolean(normalized.retryable),
    status: normalized.status ?? null
  };
}

function createAnalyzeBatchTiming({
  mode,
  textCount,
  effectiveTimeoutMs,
  durationMs,
  queueWaitMs,
  queueDepthAtEnqueue,
  queueDepthAtStart,
  ok,
  error
}) {
  const timing = {
    mode: normalizeAnalyzeBatchMode(mode),
    textCount: Math.max(0, Number(textCount || 0)),
    effectiveTimeoutMs: Math.max(0, Number(effectiveTimeoutMs || 0)),
    durationMs: Math.max(0, Number(durationMs || 0)),
    queueWaitMs: Math.max(0, Number(queueWaitMs || 0)),
    queueDepthAtEnqueue: Math.max(0, Number(queueDepthAtEnqueue || 0)),
    queueDepthAtStart: Math.max(0, Number(queueDepthAtStart || 0)),
    ok: Boolean(ok)
  };

  if (error) {
    Object.assign(timing, summarizeBackendRequestError(error));
  }

  return timing;
}

function summarizeAnalyzeBatchTimings(requestTimings) {
  const timings = Array.isArray(requestTimings) ? requestTimings : [];
  return timings.reduce(
    (summary, timing) => ({
      maxQueueWaitMs: Math.max(summary.maxQueueWaitMs, Number(timing?.queueWaitMs || 0)),
      maxQueueDepthAtEnqueue: Math.max(
        summary.maxQueueDepthAtEnqueue,
        Number(timing?.queueDepthAtEnqueue || 0)
      ),
      maxQueueDepthAtStart: Math.max(
        summary.maxQueueDepthAtStart,
        Number(timing?.queueDepthAtStart || 0)
      )
    }),
    {
      maxQueueWaitMs: 0,
      maxQueueDepthAtEnqueue: 0,
      maxQueueDepthAtStart: 0
    }
  );
}

async function fetchJsonWithTimeout(
  url,
  options = {},
  timeoutMs = DEFAULT_SETTINGS.requestTimeoutMs,
  externalAbortSignal = null
) {
  const controller = new AbortController();
  let didTimeout = false;
  let didExternalAbort = false;
  const timerId = setTimeout(() => {
    didTimeout = true;
    controller.abort();
  }, timeoutMs);
  const abortFromExternalSignal = () => {
    didExternalAbort = true;
    controller.abort();
  };

  if (externalAbortSignal?.aborted) {
    didExternalAbort = true;
    controller.abort();
  } else if (externalAbortSignal?.addEventListener) {
    externalAbortSignal.addEventListener("abort", abortFromExternalSignal, { once: true });
  }

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers: {
        "Content-Type": "application/json",
        ...(options.headers || {})
      }
    });

    const rawText = await response.text();
    let body = null;

    if (rawText) {
      try {
        body = JSON.parse(rawText);
      } catch {
        body = rawText;
      }
    }

    if (!response.ok) {
      const detailMessage =
        typeof body === "string"
          ? body
          : body?.detail?.message || body?.detail || response.statusText;
      throw new BackendRequestError(`HTTP_${response.status}`, `HTTP_${response.status}: ${detailMessage}`, {
        retryable: response.status >= 500,
        status: response.status,
        detail: body
      });
    }

    return body;
  } catch (error) {
    if (error?.name === "AbortError" && didTimeout) {
      throw new BackendRequestError("TIMEOUT", "요청 시간이 초과되었습니다.", {
        retryable: true
      });
    }

    if (error?.name === "AbortError" && didExternalAbort) {
      throw new BackendRequestError(
        "PREEMPTED_BY_FOREGROUND",
        "foreground 분석을 위해 낮은 우선순위 요청을 중단했습니다.",
        { retryable: true }
      );
    }

    if (error?.name === "AbortError") {
      throw new BackendRequestError("ABORTED", "요청이 취소되었습니다.", {
        retryable: true
      });
    }

    if (error instanceof BackendRequestError) {
      throw error;
    }

    const message = String(error?.message || error || "");
    if (message.includes("Failed to fetch")) {
      throw new BackendRequestError("NETWORK_UNREACHABLE", "백엔드 서버에 연결할 수 없습니다.", {
        retryable: true,
        detail: message
      });
    }

    throw new BackendRequestError("REQUEST_FAILED", message || "백엔드 요청에 실패했습니다.", {
      retryable: false,
      detail: message
    });
  } finally {
    clearTimeout(timerId);
    if (externalAbortSignal?.removeEventListener) {
      externalAbortSignal.removeEventListener("abort", abortFromExternalSignal);
    }
  }
}

function validateAnalyzeBatchResponse(body, texts) {
  const results = Array.isArray(body?.results) ? body.results : null;
  if (!results) {
    throw new BackendRequestError(
      "INVALID_RESPONSE",
      "배치 분석 응답 형식이 올바르지 않습니다.",
      { retryable: false, detail: body }
    );
  }

  if (results.length !== texts.length) {
    throw new BackendRequestError(
      "INVALID_RESPONSE",
      `RESULT_COUNT_MISMATCH:${results.length}/${texts.length}`,
      { retryable: false, detail: body }
    );
  }

  return results;
}

async function performAnalyzeBatchRequest(apiBaseUrl, texts, requestTimeoutMs, sensitivity, mode = "foreground") {
  let queueDiagnostics = {
    queueWaitMs: 0,
    queueDepthAtEnqueue: 0,
    queueDepthAtStart: 0
  };

  try {
    const body = await enqueueBackendRequest(
      mode,
      (diagnostics = {}) => {
        queueDiagnostics = {
          queueWaitMs: Math.max(0, Number(diagnostics.queueWaitMs || 0)),
          queueDepthAtEnqueue: Math.max(0, Number(diagnostics.queueDepthAtEnqueue || 0)),
          queueDepthAtStart: Math.max(0, Number(diagnostics.queueDepthAtStart || 0))
        };

        return fetchJsonWithTimeout(
          `${apiBaseUrl}/analyze_batch`,
          {
            method: "POST",
            body: JSON.stringify({
              texts,
              sensitivity: normalizeSensitivity(sensitivity)
            })
          },
          requestTimeoutMs,
          diagnostics.abortSignal
        );
      }
    );

    return {
      results: validateAnalyzeBatchResponse(body, texts),
      queueDiagnostics
    };
  } catch (error) {
    error.queueDiagnostics = queueDiagnostics;
    throw error;
  }
}

function getAnalyzeBatchRequestTimeoutMs(requestTimeoutMs, mode = "foreground") {
  const normalizedMode = normalizeAnalyzeBatchMode(mode);
  const requestedTimeoutMs = Math.max(0, Number(requestTimeoutMs || 0));

  if (normalizedMode === "background-validation") {
    return Math.max(
      FOREGROUND_ANALYZE_MIN_TIMEOUT_MS,
      Math.min(
        BACKGROUND_ANALYZE_TIMEOUT_CAP_MS,
        requestedTimeoutMs || BACKGROUND_ANALYZE_TIMEOUT_CAP_MS
      )
    );
  }

  if (normalizedMode === "reconcile") {
    return Math.max(
      FOREGROUND_ANALYZE_MIN_TIMEOUT_MS,
      Math.min(
        RECONCILE_ANALYZE_TIMEOUT_CAP_MS,
        requestedTimeoutMs || RECONCILE_ANALYZE_TIMEOUT_CAP_MS
      )
    );
  }

  if (normalizedMode === "self-test") {
    return Math.max(
      1200,
      Math.min(
        SELF_TEST_ANALYZE_TIMEOUT_CAP_MS,
        requestedTimeoutMs || SELF_TEST_ANALYZE_TIMEOUT_CAP_MS
      )
    );
  }

  return Math.max(FOREGROUND_ANALYZE_MIN_TIMEOUT_MS, requestedTimeoutMs);
}

async function performAnalyzeBatchRequestWithSplits(
  apiBaseUrl,
  texts,
  requestTimeoutMs,
  sensitivity,
  mode = "foreground"
) {
  const effectiveTimeoutMs = getAnalyzeBatchRequestTimeoutMs(requestTimeoutMs, mode);
  const requestStartedAt = Date.now();

  try {
    const requestResult = await performAnalyzeBatchRequest(
      apiBaseUrl,
      texts,
      effectiveTimeoutMs,
      sensitivity,
      mode
    );

    return {
      results: requestResult.results,
      requestCount: 1,
      splitRetryCount: 0,
      requestTimings: [
        createAnalyzeBatchTiming({
          mode,
          textCount: texts.length,
          effectiveTimeoutMs,
          durationMs: Date.now() - requestStartedAt,
          queueWaitMs: requestResult.queueDiagnostics?.queueWaitMs,
          queueDepthAtEnqueue: requestResult.queueDiagnostics?.queueDepthAtEnqueue,
          queueDepthAtStart: requestResult.queueDiagnostics?.queueDepthAtStart,
          ok: true
        })
      ]
    };
  } catch (error) {
    const failedTiming = createAnalyzeBatchTiming({
      mode,
      textCount: texts.length,
      effectiveTimeoutMs,
      durationMs: Date.now() - requestStartedAt,
      queueWaitMs: error?.queueDiagnostics?.queueWaitMs,
      queueDepthAtEnqueue: error?.queueDiagnostics?.queueDepthAtEnqueue,
      queueDepthAtStart: error?.queueDiagnostics?.queueDepthAtStart,
      ok: false,
      error
    });

    if (!shouldSplitAnalyzeBatchRequest(error, texts.length, mode)) {
      error.requestTimings = [
        ...(Array.isArray(error.requestTimings) ? error.requestTimings : []),
        failedTiming
      ];
      throw error;
    }

    const midpoint = Math.ceil(texts.length / 2);
    let left;
    let right;
    try {
      left = await performAnalyzeBatchRequestWithSplits(
        apiBaseUrl,
        texts.slice(0, midpoint),
        requestTimeoutMs,
        sensitivity,
        mode
      );
      right = await performAnalyzeBatchRequestWithSplits(
        apiBaseUrl,
        texts.slice(midpoint),
        requestTimeoutMs,
        sensitivity,
        mode
      );
    } catch (splitError) {
      splitError.requestTimings = [
        failedTiming,
        ...(Array.isArray(splitError.requestTimings) ? splitError.requestTimings : [])
      ];
      throw splitError;
    }

    return {
      results: [...left.results, ...right.results],
      requestCount: 1 + left.requestCount + right.requestCount,
      splitRetryCount: 1 + left.splitRetryCount + right.splitRetryCount,
      requestTimings: [
        failedTiming,
        ...(Array.isArray(left.requestTimings) ? left.requestTimings : []),
        ...(Array.isArray(right.requestTimings) ? right.requestTimings : [])
      ]
    };
  }
}

async function performAnalyzeBatchRequests(apiBaseUrl, texts, requestTimeoutMs, sensitivity, mode = "foreground") {
  const chunkSize = getAnalyzeBatchChunkSize(requestTimeoutMs, texts.length, mode);
  const chunks = chunkArray(texts, chunkSize);
  const results = [];
  const requestTimings = [];
  let requestCount = 0;
  let splitRetryCount = 0;
  let skippedChunkCount = 0;
  let failedTextCount = 0;
  let lastBackendError = null;

  for (const chunk of chunks) {
    let chunkResult;
    try {
      chunkResult = await performAnalyzeBatchRequestWithSplits(
        apiBaseUrl,
        chunk,
        requestTimeoutMs,
        sensitivity,
        mode
      );
    } catch (error) {
      const errorTimings = Array.isArray(error.requestTimings) ? error.requestTimings : [];
      requestTimings.push(...errorTimings);
      lastBackendError = summarizeBackendRequestError(error, "ANALYZE_BATCH_FAILED");

      if (!shouldTolerateAnalyzeBatchChunkFailure(error, mode)) {
        error.analysisDiagnostics = {
          mode: normalizeAnalyzeBatchMode(mode),
          chunkSize,
          failedTextCount: chunk.length,
          lastBackendError,
          requestTimings: requestTimings.slice(-12)
        };
        throw error;
      }

      results.push(...createSkippedAnalyzeBatchResults(chunk));
      requestCount += Math.max(1, errorTimings.length);
      skippedChunkCount += 1;
      failedTextCount += chunk.length;
      continue;
    }

    results.push(...chunkResult.results);
    requestCount += chunkResult.requestCount;
    splitRetryCount += chunkResult.splitRetryCount;
    requestTimings.push(...(Array.isArray(chunkResult.requestTimings) ? chunkResult.requestTimings : []));
  }

  return {
    results,
    requestCount,
    splitRetryCount,
    skippedChunkCount,
    failedTextCount,
    chunkSize,
    lastBackendError,
    lastBackendErrorCode: lastBackendError?.errorCode || "",
    requestTimings: requestTimings.slice(-12)
  };
}

async function checkApiHealthInternal(apiBaseUrl, requestTimeoutMs, options = {}) {
  try {
    const body = await fetchJsonWithTimeout(
      `${apiBaseUrl}/health`,
      { method: "GET" },
      Math.min(requestTimeoutMs, BACKEND_HEALTH_TIMEOUT_MS)
    );

    return {
      ok: true,
      apiBaseUrl,
      backendStatus: body?.model_ready === false ? "degraded" : "ready",
      ...(body || {})
    };
  } catch (error) {
    const normalized = normalizeBackendError(error, "HEALTH_CHECK_FAILED");
    if (!options.suppressErrorLog) {
      console.error("[청마루] checkApiHealth failed", error);
    }

    return {
      ok: false,
      apiBaseUrl,
      backendStatus: "degraded",
      ...normalized
    };
  }
}

async function analyzeTextBatch(message) {
  const settings = await getSettings();
  const apiBaseUrl = sanitizeApiBaseUrl(settings.backendApiBaseUrl);
  const sensitivity = normalizeSensitivity(message?.sensitivity ?? settings.sensitivity);
  const requestTimeoutMs = normalizeForegroundRequestTimeoutMs(
    message?.requestTimeoutMsOverride,
    normalizeRequestTimeoutMs(settings.requestTimeoutMs)
  );
  const analysisMode = normalizeAnalyzeBatchMode(message?.analysisMode);
  const startedAt = Date.now();
  const texts = Array.isArray(message?.texts)
    ? message.texts.map((item) => String(item || "").trim()).filter(Boolean)
    : [];

  if (texts.length === 0) {
    return {
      ok: false,
      reason: "EMPTY_TEXTS",
      errorCode: "EMPTY_TEXTS",
      retryable: false,
      backendStatus: "degraded",
      apiBaseUrl,
      durationMs: 0
    };
  }

  try {
    const resultsByText = new Map();
    const pendingTexts = [];
    const pendingTextSet = new Set();
    const inFlightResultPromises = [];
    let cacheHitCount = 0;
    let inFlightHitCount = 0;

    for (const text of texts) {
      const cached = getCachedResponse(
        FULL_ANALYSIS_RESPONSE_CACHE,
        text,
        sensitivity,
        apiBaseUrl,
        analysisMode
      );
      if (cached) {
        resultsByText.set(text, cached);
        cacheHitCount += 1;
        continue;
      }

      const inFlight = getInFlightAnalysisResponse(text, sensitivity, apiBaseUrl, analysisMode);
      if (inFlight) {
        inFlightHitCount += 1;
        inFlightResultPromises.push(
          inFlight
            .then((result) => {
              resultsByText.set(text, result || null);
            })
            .catch(() => {
              resultsByText.set(text, createSkippedAnalyzeBatchResults([text])[0]);
            })
        );
        continue;
      }

      if (!pendingTextSet.has(text)) {
        pendingTextSet.add(text);
        pendingTexts.push(text);
      }
    }

    if (pendingTexts.length > 0) {
      const inFlightEntries = pendingTexts.map((text) => ({
        text,
        entry: createInFlightAnalysisEntry(text, sensitivity, apiBaseUrl, analysisMode)
      }));
      let batchResponse;
      try {
        batchResponse = await performAnalyzeBatchRequests(
          apiBaseUrl,
          pendingTexts,
          requestTimeoutMs,
          sensitivity,
          analysisMode
        );
        batchResponse.results.forEach((result, index) => {
          const text = pendingTexts[index];
          const value = result || null;
          resultsByText.set(text, value);
          setCachedResponse(
            FULL_ANALYSIS_RESPONSE_CACHE,
            text,
            value,
            sensitivity,
            apiBaseUrl,
            analysisMode
          );
          inFlightEntries[index]?.entry?.resolve(value);
        });
      } catch (error) {
        const skippedResults = createSkippedAnalyzeBatchResults(pendingTexts);
        skippedResults.forEach((result, index) => {
          inFlightEntries[index]?.entry?.resolve(result);
        });
        throw error;
      } finally {
        for (const { entry } of inFlightEntries) {
          clearInFlightAnalysisEntry(entry);
        }
      }

      if (inFlightResultPromises.length > 0) {
        await Promise.all(inFlightResultPromises);
      }

      const skippedChunkCount = Number(batchResponse.skippedChunkCount || 0);
      const failedTextCount = Number(batchResponse.failedTextCount || 0);
      const lastBackendErrorCode = String(batchResponse.lastBackendErrorCode || "");
      const timingSummary = summarizeAnalyzeBatchTimings(batchResponse.requestTimings);

      return {
        ok: true,
        apiBaseUrl,
        durationMs: Date.now() - startedAt,
        backendStatus: getAnalyzeBatchBackendStatus(skippedChunkCount, lastBackendErrorCode),
        analysisMode,
        requestedCount: pendingTexts.length,
        cacheHitCount,
        inFlightHitCount,
        requestCount: Number(batchResponse.requestCount || 0),
        splitRetryCount: Number(batchResponse.splitRetryCount || 0),
        chunkSize: Number(batchResponse.chunkSize || 0),
        skippedChunkCount,
        failedTextCount,
        lastBackendErrorCode,
        requestTimeoutMs,
        requestTimings: Array.isArray(batchResponse.requestTimings)
          ? batchResponse.requestTimings
          : [],
        backendQueueWaitMs: timingSummary.maxQueueWaitMs,
        backendQueueDepthAtEnqueue: timingSummary.maxQueueDepthAtEnqueue,
        backendQueueDepthAtStart: timingSummary.maxQueueDepthAtStart,
        results: texts.map((text) => resultsByText.get(text) || null)
      };
    }

    if (inFlightResultPromises.length > 0) {
      await Promise.all(inFlightResultPromises);
    }

    return {
      ok: true,
      apiBaseUrl,
      durationMs: Date.now() - startedAt,
      backendStatus: "ready",
      analysisMode,
      requestedCount: pendingTexts.length,
      cacheHitCount,
      inFlightHitCount,
      requestCount: 0,
      splitRetryCount: 0,
      chunkSize: 0,
      skippedChunkCount: 0,
      failedTextCount: 0,
      lastBackendErrorCode: "",
      requestTimeoutMs,
      requestTimings: [],
      backendQueueWaitMs: 0,
      backendQueueDepthAtEnqueue: 0,
      backendQueueDepthAtStart: 0,
      results: texts.map((text) => resultsByText.get(text) || null)
    };
  } catch (error) {
    const normalized = normalizeBackendError(error, "ANALYZE_BATCH_FAILED");
    const analysisDiagnostics = error?.analysisDiagnostics || null;
    const isRuntimeAnalysisMode =
      analysisMode === "foreground" ||
      analysisMode === "background-validation" ||
      analysisMode === "reconcile";
    const canDegradeWithoutFailing =
      isRuntimeAnalysisMode &&
      normalized.errorCode !== "INVALID_RESPONSE";

    if (canDegradeWithoutFailing) {
      const timingSummary = summarizeAnalyzeBatchTimings(analysisDiagnostics?.requestTimings);
      const lastBackendErrorCode = String(
        analysisDiagnostics?.lastBackendError?.errorCode || normalized.errorCode || ""
      );
      return {
        ok: true,
        apiBaseUrl,
        durationMs: Date.now() - startedAt,
        backendStatus: getAnalyzeBatchBackendStatus(1, lastBackendErrorCode),
        analysisMode,
        requestedCount: texts.length,
        cacheHitCount: 0,
        inFlightHitCount: 0,
        requestCount: Math.max(1, Number(analysisDiagnostics?.requestTimings?.length || 0)),
        splitRetryCount: 0,
        chunkSize: Number(analysisDiagnostics?.chunkSize || texts.length),
        skippedChunkCount: 1,
        failedTextCount: Number(analysisDiagnostics?.failedTextCount || texts.length),
        lastBackendErrorCode,
        requestTimeoutMs,
        requestTimings: Array.isArray(analysisDiagnostics?.requestTimings)
          ? analysisDiagnostics.requestTimings
          : [],
        backendQueueWaitMs: timingSummary.maxQueueWaitMs,
        backendQueueDepthAtEnqueue: timingSummary.maxQueueDepthAtEnqueue,
        backendQueueDepthAtStart: timingSummary.maxQueueDepthAtStart,
        results: createSkippedAnalyzeBatchResults(texts)
      };
    }

    const timingSummary = summarizeAnalyzeBatchTimings(analysisDiagnostics?.requestTimings);

    if (analysisMode === "foreground" && !normalized.retryable) {
      console.error("[청마루] analyzeTextBatch failed", error);
    } else {
      console.warn("[청마루] analyzeTextBatch degraded", {
        analysisMode,
        errorCode: normalized.errorCode,
        reason: normalized.reason,
        requestedCount: texts.length,
        durationMs: Date.now() - startedAt
      });
    }
    return {
      ok: false,
      reason: normalized.reason,
      errorCode: normalized.errorCode,
      retryable: normalized.retryable,
      backendStatus: "degraded",
      analysisMode,
      apiBaseUrl,
      durationMs: Date.now() - startedAt,
      requestedCount: texts.length,
      requestTimeoutMs,
      chunkSize: Number(analysisDiagnostics?.chunkSize || 0),
      failedTextCount: Number(analysisDiagnostics?.failedTextCount || texts.length),
      lastBackendErrorCode:
        String(analysisDiagnostics?.lastBackendError?.errorCode || normalized.errorCode || ""),
      requestTimings: Array.isArray(analysisDiagnostics?.requestTimings)
        ? analysisDiagnostics.requestTimings
        : [],
      backendQueueWaitMs: timingSummary.maxQueueWaitMs,
      backendQueueDepthAtEnqueue: timingSummary.maxQueueDepthAtEnqueue,
      backendQueueDepthAtStart: timingSummary.maxQueueDepthAtStart,
      detail: normalized.detail || undefined
    };
  }
}

async function checkApiHealth() {
  const settings = await getSettings();
  const apiBaseUrl = sanitizeApiBaseUrl(settings.backendApiBaseUrl);
  const requestTimeoutMs = normalizeRequestTimeoutMs(settings.requestTimeoutMs);
  const startedAt = Date.now();
  const result = await checkApiHealthInternal(apiBaseUrl, requestTimeoutMs);
  return {
    ...result,
    durationMs: Date.now() - startedAt
  };
}

async function fetchSitePolicyFromBackend(url, settings, options = {}) {
  const apiBaseUrl = sanitizeApiBaseUrl(settings.backendApiBaseUrl);
  const requestTimeoutMs = normalizeRequestTimeoutMs(settings.requestTimeoutMs);
  const payload = {
    url,
    title: String(options.title || ""),
    snippet: String(options.snippet || ""),
    force_refresh: Boolean(options.forceRefresh)
  };
  return fetchJsonWithTimeout(
    `${apiBaseUrl}/site/check`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    },
    Math.max(1200, Math.min(requestTimeoutMs, 12000))
  );
}

async function getSitePolicyForUrl(url, options = {}) {
  const settings = await getSettings();
  if (settings.siteProtectionEnabled === false) {
    return {
      ok: true,
      skipped: true,
      reason: "SITE_PROTECTION_DISABLED",
      policy: null
    };
  }

  if (!isHttpPageUrl(url) || isUnsupportedTabUrl(url)) {
    return {
      ok: true,
      skipped: true,
      reason: "UNSUPPORTED_URL",
      policy: null
    };
  }

  const domain = domainFromUrl(url);
  const blockedRule = matchDomainRule(domain, settings.blockedDomains);
  if (blockedRule) {
    const policy = buildOverrideSitePolicy(url, "block", blockedRule);
    setCachedSitePolicy(url, policy);
    return { ok: true, source: "manual-override", policy };
  }

  const warnedRule = matchDomainRule(domain, settings.warnDomains);
  if (warnedRule) {
    const policy = buildOverrideSitePolicy(url, "warning", warnedRule);
    setCachedSitePolicy(url, policy);
    return { ok: true, source: "manual-override", policy };
  }

  if (!options.forceRefresh) {
    const cached = getCachedSitePolicy(url);
    if (cached) {
      return { ok: true, source: "cache", policy: cached };
    }

    const inflight = getInFlightSitePolicy(url);
    if (inflight) {
      const policy = await inflight;
      return { ok: true, source: "inflight", policy };
    }
  }

  const inflightEntry = createInFlightSitePolicyEntry(url);
  try {
    const policy = await fetchSitePolicyFromBackend(url, settings, options);
    setCachedSitePolicy(url, policy);
    inflightEntry.resolve(policy);
    return { ok: true, source: "backend", policy };
  } catch (error) {
    const normalized = normalizeBackendError(error, "SITE_CHECK_FAILED");
    const fallback = {
      url,
      domain,
      verdict: "allow",
      risk_score: 0.0,
      site_category: "unknown",
      security_threat: false,
      harmful_content: false,
      reasons: [
        "백엔드 사이트 판별기에 일시적으로 연결하지 못했다.",
        `상세 원인: ${normalized.errorCode || normalized.reason || "unknown"}`
      ],
      matched_entries: [],
      exact_match: null,
      retrieval_ms: 0,
      llm_timing_ms: 0,
      timing_ms: 0,
      agent: {
        mode: "fallback",
        model: null,
        reason: normalized.errorCode || normalized.reason || "SITE_CHECK_FAILED",
        response:
          "1. 판정\n현재 사이트 위험도는 확정하지 못했습니다.\n2. 근거\n백엔드 사이트 판별기에 연결하지 못했습니다.\n3. 사용자 안내\n로그인, 결제, 파일 다운로드가 필요한 사이트라면 주소와 출처를 다시 확인한 뒤 진행하세요.",
        sub_agents: null
      }
    };
    inflightEntry.resolve(fallback);
    return {
      ok: false,
      source: "fallback",
      reason: normalized.reason,
      errorCode: normalized.errorCode,
      policy: fallback
    };
  } finally {
    clearInFlightSitePolicyEntry(inflightEntry);
  }
}

async function prefetchSitePolicyForTab(tabId, url) {
  if (!tabId || !isHttpPageUrl(url) || isUnsupportedTabUrl(url)) {
    return;
  }
  const result = await getSitePolicyForUrl(url);
  SITE_POLICY_BY_TAB.set(tabId, {
    url,
    policy: result?.policy || null,
    updatedAt: Date.now(),
    source: result?.source || "unknown",
    dismissed: false
  });
}

async function runPipelineOnActiveTab() {
  const tab = await getActiveTab();
  if (!tab?.id) {
    return { ok: false, reason: "ACTIVE_TAB_NOT_FOUND" };
  }

  if (isUnsupportedTabUrl(tab.url)) {
    return { ok: false, reason: "UNSUPPORTED_TAB" };
  }

  try {
    const contentResult = await sendMessageToTabWithInjection(tab.id, {
      type: "RUN_PIPELINE",
      reason: "manual-request"
    });

    const lastState = await chrome.storage.local.get([
      "lastPayload",
      "lastDecision",
      "lastRunAt",
      "lastStats",
      "lastPipelineError",
      "sessionStats",
      "lastSelfTest",
      "lastSelfTestHistory"
    ]);

    return {
      ok: true,
      tabId: tab.id,
      tabUrl: tab.url,
      contentResult: contentResult || null,
      ...lastState
    };
  } catch (error) {
    const normalized = normalizeBackendError(error, "RUN_PIPELINE_ON_TAB_FAILED");
    return {
      ok: false,
      reason: normalized.reason,
      errorCode: normalized.errorCode,
      retryable: normalized.retryable
    };
  }
}

async function runSelfTestOnActiveTab() {
  const tab = await getActiveTab();
  if (!tab?.id) {
    return { ok: false, reason: "ACTIVE_TAB_NOT_FOUND", errorCode: "ACTIVE_TAB_NOT_FOUND" };
  }

  if (isUnsupportedTabUrl(tab.url)) {
    return { ok: false, reason: "UNSUPPORTED_TAB", errorCode: "UNSUPPORTED_TAB" };
  }

  try {
    const contentResult = await sendMessageToTabWithInjection(tab.id, {
      type: "RUN_SELF_TEST"
    });

    const state = await chrome.storage.local.get([
      "lastPayload",
      "lastDecision",
      "lastRunAt",
      "lastStats",
      "lastPipelineError",
      "sessionStats",
      "lastSelfTest",
      "lastSelfTestHistory"
    ]);

    return {
      ok: true,
      tabId: tab.id,
      tabUrl: tab.url,
      contentResult: contentResult || null,
      ...state
    };
  } catch (error) {
    const normalized = normalizeBackendError(error, "RUN_SELF_TEST_ON_TAB_FAILED");
    return {
      ok: false,
      reason: normalized.reason,
      errorCode: normalized.errorCode,
      retryable: normalized.retryable
    };
  }
}

async function getLastPipelineState() {
  const state = await chrome.storage.local.get([
    "lastPayload",
    "lastDecision",
    "lastRunAt",
    "lastStats",
    "lastPipelineError",
    "sessionStats",
    "lastSelfTest",
    "lastSelfTestHistory"
  ]);

  return {
    ok: true,
    ...state
  };
}

chrome.runtime.onInstalled.addListener(() => {
  FULL_ANALYSIS_RESPONSE_CACHE.clear();
  FULL_ANALYSIS_IN_FLIGHT_REQUESTS.clear();
  SITE_POLICY_CACHE.clear();
  SITE_POLICY_IN_FLIGHT.clear();
  SITE_POLICY_BY_TAB.clear();
  ensureSettings().catch((error) => {
    console.error("[청마루] ensureSettings(onInstalled) failed", error);
  });
});

chrome.runtime.onStartup.addListener(() => {
  FULL_ANALYSIS_RESPONSE_CACHE.clear();
  FULL_ANALYSIS_IN_FLIGHT_REQUESTS.clear();
  SITE_POLICY_CACHE.clear();
  SITE_POLICY_IN_FLIGHT.clear();
  SITE_POLICY_BY_TAB.clear();
  ensureSettings().catch((error) => {
    console.error("[청마루] ensureSettings(onStartup) failed", error);
  });
});

chrome.storage.onChanged.addListener((changes, areaName) => {
  if (areaName !== "sync" || !changes?.settings) {
    return;
  }

  FULL_ANALYSIS_RESPONSE_CACHE.clear();
  FULL_ANALYSIS_IN_FLIGHT_REQUESTS.clear();
  SITE_POLICY_CACHE.clear();
  SITE_POLICY_IN_FLIGHT.clear();
  SITE_POLICY_BY_TAB.clear();
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  const nextUrl = String(changeInfo.url || tab?.url || "");
  if (!nextUrl || !isHttpPageUrl(nextUrl) || isUnsupportedTabUrl(nextUrl)) {
    return;
  }
  if (changeInfo.url || changeInfo.status === "loading") {
    prefetchSitePolicyForTab(tabId, nextUrl).catch((error) => {
      console.warn("[청마루] prefetchSitePolicyForTab failed", error);
    });
  }
});

chrome.tabs.onActivated.addListener(async ({ tabId }) => {
  try {
    const tab = await chrome.tabs.get(tabId);
    if (tab?.url && isHttpPageUrl(tab.url) && !isUnsupportedTabUrl(tab.url)) {
      await prefetchSitePolicyForTab(tabId, tab.url);
    }
  } catch (error) {
    console.warn("[청마루] tabs.onActivated site policy prefetch failed", error);
  }
});

chrome.tabs.onRemoved.addListener((tabId) => {
  SITE_POLICY_BY_TAB.delete(tabId);
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type === "GET_DEFAULT_SETTINGS") {
    sendResponse({ ok: true, defaults: DEFAULT_SETTINGS });
    return true;
  }

  if (message?.type === "RUN_PIPELINE_ON_ACTIVE_TAB" || message?.type === "APPLY_FILTER_TO_ACTIVE_TAB") {
    runPipelineOnActiveTab().then(sendResponse);
    return true;
  }

  if (message?.type === "RUN_SELF_TEST_ON_ACTIVE_TAB") {
    runSelfTestOnActiveTab().then(sendResponse);
    return true;
  }

  if (message?.type === "GET_LAST_PIPELINE_STATE") {
    getLastPipelineState().then(sendResponse);
    return true;
  }

  if (message?.type === "ANALYZE_TEXT_BATCH") {
    analyzeTextBatch(message).then(sendResponse);
    return true;
  }

  if (message?.type === "CHECK_API_HEALTH") {
    checkApiHealth().then(sendResponse);
    return true;
  }

  if (message?.type === "GET_SITE_POLICY_FOR_URL") {
    const senderTabId = sender?.tab?.id;
    const requestedUrl = String(message?.url || sender?.tab?.url || "");
    if (senderTabId) {
      const current = SITE_POLICY_BY_TAB.get(senderTabId);
      if (current?.url === requestedUrl && current.policy) {
        sendResponse({
          ok: true,
          source: current.source || "tab-cache",
          dismissed: Boolean(current.dismissed),
          policy: current.policy
        });
        return true;
      }
    }
    getSitePolicyForUrl(requestedUrl, {
      title: message?.title || "",
      snippet: message?.snippet || "",
      forceRefresh: Boolean(message?.forceRefresh)
    }).then((result) => {
      if (senderTabId) {
        SITE_POLICY_BY_TAB.set(senderTabId, {
          url: requestedUrl,
          policy: result?.policy || null,
          updatedAt: Date.now(),
          source: result?.source || "direct",
          dismissed: false
        });
      }
      sendResponse({
        ok: Boolean(result?.ok),
        source: result?.source || "unknown",
        reason: result?.reason || null,
        errorCode: result?.errorCode || null,
        dismissed: false,
        policy: result?.policy || null
      });
    });
    return true;
  }

  if (message?.type === "DISMISS_SITE_POLICY") {
    const senderTabId = sender?.tab?.id;
    const requestedUrl = String(message?.url || sender?.tab?.url || "");
    if (senderTabId) {
      const current = SITE_POLICY_BY_TAB.get(senderTabId);
      if (current && current.url === requestedUrl) {
        current.dismissed = true;
        SITE_POLICY_BY_TAB.set(senderTabId, current);
      }
    }
    sendResponse({ ok: true });
    return true;
  }

  return false;
});
