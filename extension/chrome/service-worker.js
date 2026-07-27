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
  textMaskingEnabled: true,
  customBlockWords: "",
  customAllowWords: "",
  blockedDomains: "",
  warnDomains: "",
  showReason: true,
  siteProtectionEnabled: true,
  siteNavigationWarningEnabled: true,
  searchResultProtectionEnabled: true,
  mediaSafetyEnabled: false,
  mediaSafetyInterventionMode: "auto",
  mediaSafetyStartupGateEnabled: false,
  showWellbeingWidget: true,
  wellbeingWidgetStyle: "soft",
  wellbeingAvatarImages: "",
  wellbeingAgeStageCount: 5,
  wellbeingAgeMinutesPerStage: 30,
  wellbeingAngerStageCount: 5,
  wellbeingAngerDetectionsPerStage: 3,
  backendEnabled: false,
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
const BACKEND_HEALTH_RETRY_TIMEOUT_MS = 8500;
const BACKEND_HEALTH_FAST_TIMEOUT_MS = 1000;
const BACKEND_HEALTH_CACHE_TTL_MS = 30 * 1000;
const BACKEND_HEALTH_FAILURE_CACHE_TTL_MS = 5 * 1000;
const BACKEND_HEALTH_STALE_TTL_MS = 5 * 60 * 1000;
const BACKEND_WARMUP_TIMEOUT_MS = 8000;
const SETTINGS_CACHE_TTL_MS = 5 * 1000;
const RESPONSE_CACHE_LIMIT = 2000;
const SAFE_RESPONSE_CACHE_TTL_MS = 5000;
const OFFENSIVE_RESPONSE_CACHE_TTL_MS = 90000;
const RESPONSE_CACHE_SCHEMA_VERSION = "sw-v10";
const SMALL_ANALYZE_BATCH_CHUNK_SIZE = 2;
const BACKGROUND_ANALYZE_BATCH_CHUNK_SIZE = 4;
const MEDIUM_ANALYZE_BATCH_CHUNK_SIZE = 4;
const LARGE_ANALYZE_BATCH_CHUNK_SIZE = 6;
const XL_ANALYZE_BATCH_CHUNK_SIZE = 12;
const FOREGROUND_ANALYZE_MIN_TIMEOUT_MS = 650;
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
const SITE_WARNING_PAYLOAD_STORAGE_PREFIX = "siteWarningPayload:";
const SITE_WARNING_PAYLOAD_TTL_MS = 30 * 60 * 1000;
const SITE_WARNING_ALLOWED_TTL_MS = 5 * 60 * 1000;
const SITE_WARNING_FORCE_BACK_RISK_SCORE = 0.9;
const SITE_WARNING_FORCE_BACK_SENSITIVITY = 50;
const SITE_WARNING_ALLOWED_NAVIGATIONS = new Map();
const WELLBEING_STATE_STORAGE_KEY = "wellbeingState";
const WELLBEING_DEBUG_OVERRIDE_STORAGE_KEY = "wellbeingDebugOverride";
const RUNTIME_EVENT_LOG_STORAGE_KEY = "runtimeEventLog";
const DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY = "developerRuntimeLogEnabled";
const WELLBEING_SCHEMA_VERSION = "wellbeing-v1";
const WELLBEING_MAX_HEARTBEAT_DELTA_MS = 30 * 1000;
const WELLBEING_IDLE_GAP_MS = 90 * 1000;
const WELLBEING_RECENT_DETECTION_WINDOW_MS = 10 * 60 * 1000;
const WELLBEING_RECENT_DETECTION_LIMIT = 160;
const WELLBEING_SITE_LIMIT = 60;
const RUNTIME_EVENT_LOG_LIMIT = 140;
const NSFW_OFFSCREEN_DOCUMENT_PATH = "offscreen.html";
const NSFW_OFFSCREEN_TARGET = "chungmaru-nsfw-offscreen";
const NSFW_CLASSIFIER_BATCH_LIMIT = 4;
const NSFW_CLASSIFIER_MAX_DATA_URL_CHARS = 1024 * 1024;
const NSFW_CLASSIFIER_TEST_MODES = new Set(["normal", "off", "fixture", "cpu"]);
const WELLBEING_USAGE_ALARM_NAME = "wellbeing-active-usage-sample";
const WELLBEING_USAGE_ALARM_PERIOD_MINUTES = 1;
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
let backendHealthCache = null;
let backendHealthInFlight = null;
let settingsCache = null;
let settingsCacheExpiresAt = 0;
let settingsInFlight = null;
let developerRuntimeLogEnabledCache = false;
let developerRuntimeLogStateLoaded = false;
let developerRuntimeLogStateInFlight = null;
let nsfwOffscreenCreatePromise = null;
let nsfwClassifierTestOverride = "normal";
let nsfwClassifierReadyLogSignature = "";
const BACKEND_REQUEST_PRIORITY = [
  "foreground",
  "reconcile",
  "background-validation",
  "self-test"
];
const CURATED_SITE_POLICY_FALLBACKS = Object.freeze([
  {
    domain: "google-account-verify.com",
    verdict: "block",
    site_category: "phishing",
    risk_score: 0.96,
    security_threat: true,
    harmful_content: false,
    reason: "청마루 기본 피싱 테스트 seed와 일치합니다."
  },
  {
    domain: "adult-webtoon-plus.kr",
    verdict: "block",
    site_category: "adult",
    risk_score: 0.9,
    security_threat: false,
    harmful_content: true,
    reason: "청마루 기본 성인 콘텐츠 테스트 seed와 일치합니다."
  },
  {
    domain: "jusoguide1.com",
    verdict: "block",
    site_category: "adult-gambling-link-guide",
    risk_score: 0.94,
    security_threat: false,
    harmful_content: true,
    reason: "성인/카지노/도박 주소를 모아 노출하는 주소가이드형 사이트입니다."
  },
  {
    domain: "jusowhy1.com",
    verdict: "block",
    site_category: "adult-gambling-link-guide",
    risk_score: 0.94,
    security_threat: false,
    harmful_content: true,
    reason: "성인/카지노/도박 주소를 모아 노출하는 주소가이드형 사이트입니다."
  },
  {
    domain: "dcinside.com",
    verdict: "warning",
    site_category: "community",
    risk_score: 0.38,
    security_threat: false,
    harmful_content: false,
    reason: "커뮤니티형 사이트로 게시판별 유해성 편차가 커 사전 주의가 필요합니다."
  }
]);
let isBackendRequestRunning = false;
let activeBackendRequest = null;
let wellbeingUpdateChain = Promise.resolve();

function truncateRuntimeLogValue(value, limit = 220) {
  const text = String(value ?? "");
  if (text.length <= limit) {
    return text;
  }
  return `${text.slice(0, limit - 1)}…`;
}

function normalizeRuntimeLogEvent(event) {
  const ts = Number(event?.ts || Date.now());
  const type = truncateRuntimeLogValue(event?.type || "event", 48);
  return {
    ts,
    isoTime: new Date(ts).toISOString(),
    type,
    ok: event?.ok === undefined ? null : Boolean(event.ok),
    status: truncateRuntimeLogValue(event?.status || "", 64),
    source: truncateRuntimeLogValue(event?.source || "", 64),
    domain: truncateRuntimeLogValue(event?.domain || "", 120),
    title: truncateRuntimeLogValue(event?.title || "", 160),
    url: truncateRuntimeLogValue(event?.url || "", 220),
    verdict: truncateRuntimeLogValue(event?.verdict || "", 40),
    profile: truncateRuntimeLogValue(event?.profile || "", 64),
    action: truncateRuntimeLogValue(event?.action || "", 40),
    modelVersion: truncateRuntimeLogValue(event?.modelVersion || "", 80),
    backend: truncateRuntimeLogValue(event?.backend || "", 40),
    apiBaseUrl: truncateRuntimeLogValue(event?.apiBaseUrl || "", 180),
    durationMs: Math.max(0, Math.round(Number(event?.durationMs || 0))),
    count: Math.max(0, Math.round(Number(event?.count || 0))),
    candidateCount: Math.max(0, Math.round(Number(event?.candidateCount || 0))),
    visibleTileCount: Math.max(0, Math.round(Number(event?.visibleTileCount || 0))),
    cheapFilterHitCount: Math.max(0, Math.round(Number(event?.cheapFilterHitCount || 0))),
    actionCount: Math.max(0, Math.round(Number(event?.actionCount || 0))),
    removedCount: Math.max(0, Math.round(Number(event?.removedCount || 0))),
    placeholderCount: Math.max(0, Math.round(Number(event?.placeholderCount || 0))),
    mergedTargetCount: Math.max(0, Math.round(Number(event?.mergedTargetCount || 0))),
    collapsedGroupCount: Math.max(0, Math.round(Number(event?.collapsedGroupCount || 0))),
    hiddenAreaPx: Math.max(0, Math.round(Number(event?.hiddenAreaPx || 0))),
    viewportCoveragePct: Math.min(100, Math.max(0, Math.round(Number(event?.viewportCoveragePct || 0) * 10) / 10)),
    remainingVisibleTileCount: Math.max(0, Math.round(Number(event?.remainingVisibleTileCount || 0))),
    falseHiddenCount: Math.max(0, Math.round(Number(event?.falseHiddenCount || 0))),
    domAddedToActionMs: Math.max(0, Math.round(Number(event?.domAddedToActionMs || 0))),
    collectMs: Math.max(0, Math.round(Number(event?.collectMs || 0))),
    cheapFilterMs: Math.max(0, Math.round(Number(event?.cheapFilterMs || 0))),
    classifierMs: Math.max(0, Math.round(Number(event?.classifierMs || 0))),
    modelLoadMs: Math.max(0, Math.round(Number(event?.modelLoadMs || 0))),
    warmupMs: Math.max(0, Math.round(Number(event?.warmupMs || 0))),
    classifierCandidateCount: Math.max(0, Math.round(Number(event?.classifierCandidateCount || 0))),
    classifierQueuedCount: Math.max(0, Math.round(Number(event?.classifierQueuedCount || 0))),
    classifierAppliedCount: Math.max(0, Math.round(Number(event?.classifierAppliedCount || 0))),
    cacheHitCount: Math.max(0, Math.round(Number(event?.cacheHitCount || 0))),
    blockedCount: Math.max(0, Math.round(Number(event?.blockedCount || 0))),
    benignCount: Math.max(0, Math.round(Number(event?.benignCount || 0))),
    ambiguousCount: Math.max(0, Math.round(Number(event?.ambiguousCount || 0))),
    errorCount: Math.max(0, Math.round(Number(event?.errorCount || 0))),
    fetchMs: Math.max(0, Math.round(Number(event?.fetchMs || 0))),
    decodeMs: Math.max(0, Math.round(Number(event?.decodeMs || 0))),
    inferenceMs: Math.max(0, Math.round(Number(event?.inferenceMs || 0))),
    queueWaitMs: Math.max(0, Math.round(Number(event?.queueWaitMs || 0))),
    classifierDecisionMs: Math.max(0, Math.round(Number(event?.classifierDecisionMs || 0))),
    modelLoadCount: Math.max(0, Math.round(Number(event?.modelLoadCount || 0))),
    tensorCount: Math.max(0, Math.round(Number(event?.tensorCount || 0))),
    tensorBytes: Math.max(0, Math.round(Number(event?.tensorBytes || 0))),
    staleDropCount: Math.max(0, Math.round(Number(event?.staleDropCount || 0))),
    ocrMs: Math.max(0, Math.round(Number(event?.ocrMs || 0))),
    applyMs: Math.max(0, Math.round(Number(event?.applyMs || 0))),
    mediaSafetyScanRequestCount: Math.max(0, Math.round(Number(event?.mediaSafetyScanRequestCount || 0))),
    mediaSafetyCoalescedScanRequestCount: Math.max(
      0,
      Math.round(Number(event?.mediaSafetyCoalescedScanRequestCount || 0))
    ),
    mediaSafetyMediaLoadEventCount: Math.max(0, Math.round(Number(event?.mediaSafetyMediaLoadEventCount || 0))),
    mediaSafetyMutationBatchCount: Math.max(0, Math.round(Number(event?.mediaSafetyMutationBatchCount || 0))),
    mediaSafetyMutationAddedNodeCount: Math.max(0, Math.round(Number(event?.mediaSafetyMutationAddedNodeCount || 0))),
    mediaSafetyPotentialMutationBatchCount: Math.max(
      0,
      Math.round(Number(event?.mediaSafetyPotentialMutationBatchCount || 0))
    ),
    mediaSafetyFastPath: event?.mediaSafetyFastPath === undefined ? null : Boolean(event.mediaSafetyFastPath),
    mediaSafetyFastPathSeedCount: Math.max(0, Math.round(Number(event?.mediaSafetyFastPathSeedCount || 0))),
    mediaSafetyFastPathRequestCount: Math.max(
      0,
      Math.round(Number(event?.mediaSafetyFastPathRequestCount || 0))
    ),
    mediaSafetyFastPathRunCount: Math.max(0, Math.round(Number(event?.mediaSafetyFastPathRunCount || 0))),
    mediaSafetyFastPathCandidateCount: Math.max(
      0,
      Math.round(Number(event?.mediaSafetyFastPathCandidateCount || 0))
    ),
    mediaSafetyFastPathActionCount: Math.max(
      0,
      Math.round(Number(event?.mediaSafetyFastPathActionCount || 0))
    ),
    pipelineScheduleCount: Math.max(0, Math.round(Number(event?.pipelineScheduleCount || 0))),
    pipelineRunCount: Math.max(0, Math.round(Number(event?.pipelineRunCount || 0))),
    pipelineQueuedCount: Math.max(0, Math.round(Number(event?.pipelineQueuedCount || 0))),
    pipelineSuppressedCount: Math.max(0, Math.round(Number(event?.pipelineSuppressedCount || 0))),
    pipelineDurationTotalMs: Math.max(0, Math.round(Number(event?.pipelineDurationTotalMs || 0))),
    pipelineDurationMaxMs: Math.max(0, Math.round(Number(event?.pipelineDurationMaxMs || 0))),
    searchResultScheduleCount: Math.max(0, Math.round(Number(event?.searchResultScheduleCount || 0))),
    googleLightScheduleCount: Math.max(0, Math.round(Number(event?.googleLightScheduleCount || 0))),
    mediaSafetyScheduleCount: Math.max(0, Math.round(Number(event?.mediaSafetyScheduleCount || 0))),
    targetedMediaSafetyScheduleCount: Math.max(
      0,
      Math.round(Number(event?.targetedMediaSafetyScheduleCount || 0))
    ),
    runtimeMessageCount: Math.max(0, Math.round(Number(event?.runtimeMessageCount || 0))),
    backendMessageCount: Math.max(0, Math.round(Number(event?.backendMessageCount || 0))),
    mutationBatchCount: Math.max(0, Math.round(Number(event?.mutationBatchCount || 0))),
    mutationRecordCount: Math.max(0, Math.round(Number(event?.mutationRecordCount || 0))),
    mutationAddedNodeCount: Math.max(0, Math.round(Number(event?.mutationAddedNodeCount || 0))),
    mutationPotentialMediaBatchCount: Math.max(
      0,
      Math.round(Number(event?.mutationPotentialMediaBatchCount || 0))
    ),
    mutationGoogleBatchCount: Math.max(0, Math.round(Number(event?.mutationGoogleBatchCount || 0))),
    longTaskCount: Math.max(0, Math.round(Number(event?.longTaskCount || 0))),
    longTaskMaxMs: Math.max(0, Math.round(Number(event?.longTaskMaxMs || 0))),
    eventLoopLagCount: Math.max(0, Math.round(Number(event?.eventLoopLagCount || 0))),
    eventLoopLagMaxMs: Math.max(0, Math.round(Number(event?.eventLoopLagMaxMs || 0))),
    performanceGuardActive: event?.performanceGuardActive === undefined ? null : Boolean(event.performanceGuardActive),
    performanceGuardReason: truncateRuntimeLogValue(event?.performanceGuardReason || "", 80),
    performanceGuardRemainingMs: Math.max(0, Math.round(Number(event?.performanceGuardRemainingMs || 0))),
    missedVisibleTileCount: Math.max(0, Math.round(Number(event?.missedVisibleTileCount || 0))),
    positiveCount: Math.max(0, Math.round(Number(event?.positiveCount || 0))),
    skippedCount: Math.max(0, Math.round(Number(event?.skippedCount || 0))),
    errorCode: truncateRuntimeLogValue(event?.errorCode || "", 80),
    message: truncateRuntimeLogValue(event?.message || event?.note || "", 260),
    reason: truncateRuntimeLogValue(event?.reason || "", 220)
  };
}

async function isDeveloperRuntimeLogEnabled(options = {}) {
  if (!options.force && developerRuntimeLogStateLoaded) {
    return developerRuntimeLogEnabledCache === true;
  }

  if (!options.force && developerRuntimeLogStateInFlight) {
    return developerRuntimeLogStateInFlight;
  }

  developerRuntimeLogStateInFlight = chrome.storage.local
    .get(DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY)
    .then((result) => {
      developerRuntimeLogEnabledCache =
        result?.[DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY] === true;
      developerRuntimeLogStateLoaded = true;
      return developerRuntimeLogEnabledCache;
    })
    .finally(() => {
      developerRuntimeLogStateInFlight = null;
    });

  return developerRuntimeLogStateInFlight;
}

async function appendRuntimeLogEvent(event) {
  const normalized = normalizeRuntimeLogEvent(event);
  const result = await chrome.storage.local.get(RUNTIME_EVENT_LOG_STORAGE_KEY);
  const previous = Array.isArray(result?.[RUNTIME_EVENT_LOG_STORAGE_KEY])
    ? result[RUNTIME_EVENT_LOG_STORAGE_KEY]
    : [];
  await chrome.storage.local.set({
    [RUNTIME_EVENT_LOG_STORAGE_KEY]: [normalized, ...previous].slice(0, RUNTIME_EVENT_LOG_LIMIT)
  });
}

async function writeRuntimeLogEvent(event) {
  const enabled = await isDeveloperRuntimeLogEnabled();
  if (!enabled) {
    return {
      ok: true,
      skipped: true,
      reason: "DEVELOPER_RUNTIME_LOG_DISABLED"
    };
  }
  await appendRuntimeLogEvent(event);
  return { ok: true, skipped: false };
}

function recordRuntimeLogEvent(event) {
  writeRuntimeLogEvent(event).catch((error) => {
    console.warn("[청마루] runtime log write failed", error);
  });
}

const SETTINGS_RUNTIME_LOG_KEYS = Object.freeze([
  "enabled",
  "sensitivity",
  "categories",
  "interventionMode",
  "textMaskingEnabled",
  "customBlockWords",
  "customAllowWords",
  "blockedDomains",
  "warnDomains",
  "showReason",
  "siteProtectionEnabled",
  "siteNavigationWarningEnabled",
  "searchResultProtectionEnabled",
  "mediaSafetyEnabled",
  "mediaSafetyInterventionMode",
  "mediaSafetyStartupGateEnabled",
  "showWellbeingWidget",
  "wellbeingWidgetStyle",
  "wellbeingAvatarImages",
  "wellbeingAgeStageCount",
  "wellbeingAgeMinutesPerStage",
  "wellbeingAngerStageCount",
  "wellbeingAngerDetectionsPerStage",
  "backendEnabled",
  "backendApiBaseUrl",
  "requestTimeoutMs"
]);

function stableRuntimeLogComparable(value) {
  if (!value || typeof value !== "object") {
    return JSON.stringify(value ?? null);
  }
  if (Array.isArray(value)) {
    return JSON.stringify(value.map((item) => JSON.parse(stableRuntimeLogComparable(item))));
  }
  const sorted = {};
  for (const key of Object.keys(value).sort()) {
    sorted[key] = JSON.parse(stableRuntimeLogComparable(value[key]));
  }
  return JSON.stringify(sorted);
}

function getChangedSettingsKeys(previous = {}, next = {}) {
  return SETTINGS_RUNTIME_LOG_KEYS.filter((key) => (
    stableRuntimeLogComparable(previous?.[key]) !== stableRuntimeLogComparable(next?.[key])
  ));
}

function summarizeSettingsForRuntimeLog(settings = {}) {
  return [
    `enabled=${settings?.enabled !== false}`,
    `text=${settings?.textMaskingEnabled !== false}`,
    `sensitivity=${normalizeSensitivity(settings?.sensitivity)}`,
    `site=${settings?.siteProtectionEnabled !== false}`,
    `navWarning=${settings?.siteNavigationWarningEnabled !== false}`,
    `search=${settings?.searchResultProtectionEnabled !== false}`,
    `media=${settings?.mediaSafetyEnabled === true}`,
    `mediaMode=${settings?.mediaSafetyInterventionMode || DEFAULT_SETTINGS.mediaSafetyInterventionMode}`,
    `mediaStartupGate=${settings?.mediaSafetyStartupGateEnabled === true}`,
    `widget=${settings?.showWellbeingWidget !== false}`,
    `mode=${settings?.interventionMode || DEFAULT_SETTINGS.interventionMode}`
  ].join("; ");
}

async function getRuntimeEventLogs(limit = RUNTIME_EVENT_LOG_LIMIT) {
  const result = await chrome.storage.local.get(RUNTIME_EVENT_LOG_STORAGE_KEY);
  const logs = Array.isArray(result?.[RUNTIME_EVENT_LOG_STORAGE_KEY])
    ? result[RUNTIME_EVENT_LOG_STORAGE_KEY]
    : [];
  const normalizedLimit = Math.max(1, Math.min(RUNTIME_EVENT_LOG_LIMIT, Math.round(Number(limit || 80))));
  return logs.slice(0, normalizedLimit);
}

async function clearRuntimeEventLogs() {
  await chrome.storage.local.remove(RUNTIME_EVENT_LOG_STORAGE_KEY);
}

async function addRuntimeEventLogFromMessage(message) {
  return writeRuntimeLogEvent({
    ...(message?.event || {}),
    type: message?.event?.type || "manual-note",
    source: message?.event?.source || "options"
  });
}

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

function isBackendEnabled(settings = {}) {
  return settings.backendEnabled === true;
}

function normalizeRequestTimeoutMs(value) {
  const numberValue = Number(value);
  if (Number.isNaN(numberValue)) return DEFAULT_SETTINGS.requestTimeoutMs;
  return Math.max(1000, Math.min(30000, Math.round(numberValue)));
}

function normalizeHealthRequestTimeoutMs(value, fallbackMs = BACKEND_HEALTH_TIMEOUT_MS) {
  const numberValue = Number(value);
  if (Number.isNaN(numberValue)) return fallbackMs;
  return Math.max(250, Math.min(30000, Math.round(numberValue)));
}

function normalizeInterventionMode(value) {
  const mode = String(value || DEFAULT_SETTINGS.interventionMode).trim();
  return ["mask", "blur", "hide", "remove"].includes(mode) ? mode : DEFAULT_SETTINGS.interventionMode;
}

function normalizeMediaSafetyInterventionMode(value) {
  const mode = String(value || DEFAULT_SETTINGS.mediaSafetyInterventionMode).trim();
  return ["auto", "placeholder", "remove"].includes(mode)
    ? mode
    : DEFAULT_SETTINGS.mediaSafetyInterventionMode;
}

function normalizeWellbeingStageCount(value, fallback = 5) {
  const numberValue = Number(value);
  if (Number.isNaN(numberValue)) return fallback;
  return Math.max(1, Math.min(10, Math.round(numberValue)));
}

function normalizeWellbeingStageStep(value, fallback, min, max) {
  const numberValue = Number(value);
  if (Number.isNaN(numberValue)) return fallback;
  return Math.max(min, Math.min(max, Math.round(numberValue)));
}

function normalizeWellbeingStageSettings(settings = {}) {
  return {
    ageStageCount: normalizeWellbeingStageCount(
      settings.wellbeingAgeStageCount,
      DEFAULT_SETTINGS.wellbeingAgeStageCount
    ),
    ageMinutesPerStage: normalizeWellbeingStageStep(
      settings.wellbeingAgeMinutesPerStage,
      DEFAULT_SETTINGS.wellbeingAgeMinutesPerStage,
      5,
      240
    ),
    angerStageCount: normalizeWellbeingStageCount(
      settings.wellbeingAngerStageCount,
      DEFAULT_SETTINGS.wellbeingAngerStageCount
    ),
    angerDetectionsPerStage: normalizeWellbeingStageStep(
      settings.wellbeingAngerDetectionsPerStage,
      DEFAULT_SETTINGS.wellbeingAngerDetectionsPerStage,
      1,
      50
    )
  };
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
    return Math.min(BACKGROUND_ANALYZE_BATCH_CHUNK_SIZE, textCount);
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

function summarizeAnalyzeRuntimeResults(results) {
  const entries = Array.isArray(results) ? results : [];
  let positiveCount = 0;
  let profaneCount = 0;
  let toxicCount = 0;
  let hateCount = 0;
  for (const result of entries) {
    if (result?.is_offensive) positiveCount += 1;
    if (result?.is_profane) profaneCount += 1;
    if (result?.is_toxic) toxicCount += 1;
    if (result?.is_hate) hateCount += 1;
  }
  return {
    totalCount: entries.length,
    positiveCount,
    profaneCount,
    toxicCount,
    hateCount
  };
}

function recordAnalyzeBatchRuntimeLog(response) {
  const summary = summarizeAnalyzeRuntimeResults(response?.results);
  const backendStatus = String(response?.backendStatus || "");
  const skippedCount = Number(response?.skippedChunkCount || 0);
  const shouldRecord =
    backendStatus !== "ready" ||
    skippedCount > 0 ||
    summary.positiveCount > 0 ||
    String(response?.lastBackendErrorCode || "");
  if (!shouldRecord) {
    return;
  }

  recordRuntimeLogEvent({
    type: "analyze-batch",
    ok: Boolean(response?.ok !== false && backendStatus !== "degraded"),
    status: backendStatus || "unknown",
    source: response?.analysisMode || "",
    apiBaseUrl: response?.apiBaseUrl || "",
    durationMs: response?.durationMs || 0,
    count: summary.totalCount,
    positiveCount: summary.positiveCount,
    skippedCount,
    errorCode: response?.lastBackendErrorCode || "",
    reason: [
      summary.profaneCount ? `profanity=${summary.profaneCount}` : "",
      summary.toxicCount ? `toxicity=${summary.toxicCount}` : "",
      summary.hateCount ? `hate=${summary.hateCount}` : ""
    ].filter(Boolean).join(", ")
  });
}

function deriveBackendModelReady(body) {
  if (!body || typeof body !== "object") {
    return false;
  }

  if (typeof body.model_ready === "boolean") {
    return body.model_ready;
  }

  const hasPipelineSignal =
    "text_pipeline_ready" in body ||
    "pipeline_loaded" in body ||
    Boolean(body.models && typeof body.models === "object");
  if (!hasPipelineSignal) {
    return false;
  }

  if (body.text_pipeline_ready === false || body.pipeline_loaded === false) {
    return false;
  }

  if (body.text_pipeline_error || body.pipeline_error) {
    return false;
  }

  const models = body.models && typeof body.models === "object" ? body.models : null;
  if (models?.model_files_ready === false) {
    return false;
  }

  return true;
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
    interventionMode: normalizeInterventionMode(stored?.interventionMode),
    textMaskingEnabled: stored?.textMaskingEnabled !== false,
    siteProtectionEnabled: stored?.siteProtectionEnabled !== false,
    siteNavigationWarningEnabled: stored?.siteNavigationWarningEnabled !== false,
    searchResultProtectionEnabled: stored?.searchResultProtectionEnabled !== false,
    mediaSafetyEnabled: stored?.mediaSafetyEnabled === true,
    mediaSafetyInterventionMode: normalizeMediaSafetyInterventionMode(stored?.mediaSafetyInterventionMode),
    mediaSafetyStartupGateEnabled: stored?.mediaSafetyStartupGateEnabled === true,
    wellbeingAvatarImages: String(stored?.wellbeingAvatarImages || ""),
    wellbeingAgeStageCount: normalizeWellbeingStageCount(
      stored?.wellbeingAgeStageCount,
      DEFAULT_SETTINGS.wellbeingAgeStageCount
    ),
    wellbeingAgeMinutesPerStage: normalizeWellbeingStageStep(
      stored?.wellbeingAgeMinutesPerStage,
      DEFAULT_SETTINGS.wellbeingAgeMinutesPerStage,
      5,
      240
    ),
    wellbeingAngerStageCount: normalizeWellbeingStageCount(
      stored?.wellbeingAngerStageCount,
      DEFAULT_SETTINGS.wellbeingAngerStageCount
    ),
    wellbeingAngerDetectionsPerStage: normalizeWellbeingStageStep(
      stored?.wellbeingAngerDetectionsPerStage,
      DEFAULT_SETTINGS.wellbeingAngerDetectionsPerStage,
      1,
      50
    ),
    backendEnabled: stored?.backendEnabled === true,
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
  settingsCache = merged;
  settingsCacheExpiresAt = Date.now() + SETTINGS_CACHE_TTL_MS;
}

async function getSettings(options = {}) {
  const now = Date.now();
  if (!options.force && settingsCache && settingsCacheExpiresAt > now) {
    return settingsCache;
  }

  if (!options.force && settingsInFlight) {
    return settingsInFlight;
  }

  settingsInFlight = chrome.storage.sync.get("settings")
    .then(({ settings }) => {
      const merged = mergeSettings(settings || {});
      settingsCache = merged;
      settingsCacheExpiresAt = Date.now() + SETTINGS_CACHE_TTL_MS;
      return merged;
    })
    .finally(() => {
      settingsInFlight = null;
    });

  return settingsInFlight;
}

function isUnsupportedTabUrl(url) {
  const value = String(url || "").toLowerCase();
  let hostname = "";
  try {
    hostname = new URL(value).hostname;
  } catch {
    hostname = "";
  }
  return (
    value.startsWith("chrome://") ||
    value.startsWith("chrome-extension://") ||
    value.startsWith("edge://") ||
    value.startsWith("about:") ||
    value.startsWith("view-source:") ||
    hostname === "chrome.google.com" ||
    hostname === "chromewebstore.google.com" ||
    hostname.endsWith(".chromewebstore.google.com")
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

function isGlobalProtectionEnabled(settings) {
  return settings?.enabled !== false && normalizeSensitivity(settings?.sensitivity) > 0;
}

function isManualSitePolicy(policy, source = "") {
  return (
    source === "manual-override" ||
    policy?.site_category === "manual-policy" ||
    policy?.agent?.mode === "override"
  );
}

function hasExactSitePolicyMatch(policy) {
  return Boolean(policy?.exact_match?.domain);
}

function shouldInterruptNavigationForPolicy(policy, source = "") {
  if (!policy || policy.verdict === "allow") {
    return false;
  }
  return isManualSitePolicy(policy, source) || hasExactSitePolicyMatch(policy);
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

function getCuratedFallbackSitePolicy(url) {
  const domain = domainFromUrl(url);
  if (!domain) {
    return null;
  }

  const match = CURATED_SITE_POLICY_FALLBACKS.find((entry) => {
    const fallbackDomain = normalizeDomain(entry.domain);
    return domain === fallbackDomain || domain.endsWith(`.${fallbackDomain}`);
  });
  if (!match) {
    return null;
  }

  return {
    url,
    domain,
    verdict: match.verdict,
    risk_score: match.risk_score,
    site_category: match.site_category,
    security_threat: Boolean(match.security_threat),
    harmful_content: Boolean(match.harmful_content),
    reasons: [match.reason],
    matched_entries: [],
    exact_match: {
      domain: normalizeDomain(match.domain),
      title: match.domain,
      summary: match.reason,
      category: match.site_category,
      risk_level: match.verdict,
      security_threat: Boolean(match.security_threat),
      harmful_content: Boolean(match.harmful_content),
      source: "extension-curated-fallback"
    },
    retrieval_ms: 0,
    llm_timing_ms: 0,
    timing_ms: 0,
    agent: {
      mode: "extension-curated-fallback",
      model: null,
      reason: "extension-curated-fallback",
      response: match.reason,
      sub_agents: null
    }
  };
}

function getSiteWarningStorageKey(warningId) {
  return `${SITE_WARNING_PAYLOAD_STORAGE_PREFIX}${String(warningId || "").trim()}`;
}

function createSiteWarningId() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function getNavigationAllowKey(tabId, url) {
  return `${tabId}:${normalizeWellbeingPageKey(url) || String(url || "")}`;
}

function getNavigationAllowOriginKey(tabId, url) {
  try {
    const parsed = new URL(String(url || ""));
    if (!/^https?:$/i.test(parsed.protocol)) {
      return "";
    }
    return `${tabId}:origin:${parsed.origin}`;
  } catch {
    return "";
  }
}

function clearExpiredSiteWarningAllows(now = Date.now()) {
  for (const [key, expiresAt] of SITE_WARNING_ALLOWED_NAVIGATIONS.entries()) {
    if (Number(expiresAt || 0) <= now) {
      SITE_WARNING_ALLOWED_NAVIGATIONS.delete(key);
    }
  }
}

function markSiteNavigationAllowed(tabId, url) {
  if (!tabId || !url) {
    return;
  }
  clearExpiredSiteWarningAllows();
  SITE_WARNING_ALLOWED_NAVIGATIONS.set(
    getNavigationAllowKey(tabId, url),
    Date.now() + SITE_WARNING_ALLOWED_TTL_MS
  );
  const originKey = getNavigationAllowOriginKey(tabId, url);
  if (originKey) {
    SITE_WARNING_ALLOWED_NAVIGATIONS.set(originKey, Date.now() + SITE_WARNING_ALLOWED_TTL_MS);
  }
  const current = SITE_POLICY_BY_TAB.get(tabId);
  if (current?.url === url) {
    SITE_POLICY_BY_TAB.set(tabId, {
      ...current,
      dismissed: true
    });
  }
}

function isSiteNavigationAllowed(tabId, url) {
  if (!tabId || !url) {
    return false;
  }
  clearExpiredSiteWarningAllows();
  const now = Date.now();
  return (
    Number(SITE_WARNING_ALLOWED_NAVIGATIONS.get(getNavigationAllowKey(tabId, url)) || 0) > now ||
    Number(SITE_WARNING_ALLOWED_NAVIGATIONS.get(getNavigationAllowOriginKey(tabId, url)) || 0) > now
  );
}

function buildSiteWarningUrl(warningId) {
  return chrome.runtime.getURL(`site-warning.html?id=${encodeURIComponent(warningId)}`);
}

function getSiteWarningContinuePolicy(policy, settings) {
  const verdict = String(policy?.verdict || "warning");
  const riskScore = Number(policy?.risk_score || 0);
  const sensitivity = normalizeSensitivity(settings?.sensitivity);
  const forceBackOnly =
    verdict === "block" &&
    riskScore >= SITE_WARNING_FORCE_BACK_RISK_SCORE &&
    sensitivity >= SITE_WARNING_FORCE_BACK_SENSITIVITY;

  return {
    can_continue: !forceBackOnly,
    continue_block_reason: forceBackOnly ? "HIGH_RISK_BLOCK_SITE" : "",
    sensitivity
  };
}

async function createSiteWarningPayload(tabId, url, policy, source, settings = DEFAULT_SETTINGS) {
  const warningId = createSiteWarningId();
  const continuePolicy = getSiteWarningContinuePolicy(policy, settings);
  const payload = {
    id: warningId,
    tabId,
    targetUrl: String(url || ""),
    createdAt: Date.now(),
    expiresAt: Date.now() + SITE_WARNING_PAYLOAD_TTL_MS,
    source: String(source || "unknown"),
    can_continue: continuePolicy.can_continue,
    continue_block_reason: continuePolicy.continue_block_reason,
    sensitivity: continuePolicy.sensitivity,
    policy: {
      url: String(policy?.url || url || ""),
      domain: String(policy?.domain || domainFromUrl(url) || ""),
      verdict: String(policy?.verdict || "warning"),
      risk_score: Number(policy?.risk_score || 0),
      site_category: String(policy?.site_category || "unknown"),
      security_threat: Boolean(policy?.security_threat),
      harmful_content: Boolean(policy?.harmful_content),
      reasons: Array.isArray(policy?.reasons) ? policy.reasons.slice(0, 6).map(String) : [],
      agent: {
        mode: policy?.agent?.mode || null,
        response: String(policy?.agent?.response || "")
      }
    }
  };
  await chrome.storage.local.set({
    [getSiteWarningStorageKey(warningId)]: payload
  });
  return payload;
}

async function readSiteWarningPayload(warningId) {
  const key = getSiteWarningStorageKey(warningId);
  if (!warningId || key === SITE_WARNING_PAYLOAD_STORAGE_PREFIX) {
    return null;
  }
  const result = await chrome.storage.local.get(key);
  const payload = result?.[key] || null;
  if (!payload) {
    return null;
  }
  if (Number(payload.expiresAt || 0) <= Date.now()) {
    await chrome.storage.local.remove(key);
    return null;
  }
  return payload;
}

async function removeSiteWarningPayload(warningId) {
  const key = getSiteWarningStorageKey(warningId);
  if (!warningId || key === SITE_WARNING_PAYLOAD_STORAGE_PREFIX) {
    return;
  }
  await chrome.storage.local.remove(key);
}

async function handleSiteNavigationWarning(details) {
  const tabId = Number(details?.tabId || 0);
  const url = String(details?.url || "");
  if (!tabId || details?.frameId !== 0 || !isHttpPageUrl(url) || isUnsupportedTabUrl(url)) {
    return;
  }

  const settings = await getSettings();
  if (
    !isGlobalProtectionEnabled(settings) ||
    settings.siteProtectionEnabled === false ||
    settings.siteNavigationWarningEnabled === false
  ) {
    return;
  }

  if (isSiteNavigationAllowed(tabId, url)) {
    return;
  }

  const result = await getSitePolicyForUrl(url, { settings });
  const policy = result?.policy || null;
  if (!shouldInterruptNavigationForPolicy(policy, result?.source || "")) {
    return;
  }

  const payload = await createSiteWarningPayload(tabId, url, policy, result?.source || "unknown", settings);
  SITE_POLICY_BY_TAB.set(tabId, {
    url,
    policy,
    updatedAt: Date.now(),
    source: result?.source || "unknown",
    dismissed: true
  });
  await chrome.tabs.update(tabId, {
    url: buildSiteWarningUrl(payload.id)
  });
}

async function allowSiteWarningAndContinue(message, sender) {
  const payload = await readSiteWarningPayload(message?.warningId);
  if (!payload?.targetUrl) {
    return { ok: false, reason: "SITE_WARNING_NOT_FOUND" };
  }
  const tabId = Number(sender?.tab?.id || payload.tabId || 0);
  if (!tabId) {
    return { ok: false, reason: "SITE_WARNING_TAB_NOT_FOUND" };
  }
  if (payload.can_continue === false) {
    return {
      ok: false,
      reason: payload.continue_block_reason || "SITE_WARNING_CONTINUE_BLOCKED"
    };
  }

  markSiteNavigationAllowed(tabId, payload.targetUrl);
  await removeSiteWarningPayload(payload.id || message?.warningId);
  await chrome.tabs.update(tabId, {
    url: payload.targetUrl
  });
  return {
    ok: true,
    targetUrl: payload.targetUrl
  };
}

function getLocalDayKey(timestamp = Date.now()) {
  const date = new Date(Number(timestamp || Date.now()));
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function normalizeWellbeingPageKey(url) {
  try {
    const parsed = new URL(String(url || ""));
    if (!/^https?:$/i.test(parsed.protocol)) {
      return "";
    }
    parsed.hash = "";
    return parsed.href;
  } catch {
    return "";
  }
}

function createEmptyWellbeingState(now = Date.now()) {
  return {
    schemaVersion: WELLBEING_SCHEMA_VERSION,
    dayKey: getLocalDayKey(now),
    startedAt: now,
    updatedAt: now,
    totalActiveMs: 0,
    domains: {},
    recentDetections: [],
    lastActiveSample: null,
    lastHeartbeatByTab: {}
  };
}

function normalizeWellbeingState(value, now = Date.now()) {
  const state = value && typeof value === "object" ? value : null;
  if (!state || state.schemaVersion !== WELLBEING_SCHEMA_VERSION || state.dayKey !== getLocalDayKey(now)) {
    return createEmptyWellbeingState(now);
  }

  return {
    ...state,
    updatedAt: Number(state.updatedAt || now),
    totalActiveMs: Math.max(0, Number(state.totalActiveMs || 0)),
    domains: state.domains && typeof state.domains === "object" ? state.domains : {},
    recentDetections: Array.isArray(state.recentDetections) ? state.recentDetections : [],
    lastActiveSample:
      state.lastActiveSample && typeof state.lastActiveSample === "object"
        ? state.lastActiveSample
        : null,
    lastHeartbeatByTab:
      state.lastHeartbeatByTab && typeof state.lastHeartbeatByTab === "object"
        ? state.lastHeartbeatByTab
        : {}
  };
}

function getWellbeingDomainState(state, domain, now = Date.now()) {
  const normalizedDomain = normalizeDomain(domain);
  if (!normalizedDomain) return null;

  const current = state.domains?.[normalizedDomain] || {};
  const next = {
    domain: normalizedDomain,
    activeMs: Math.max(0, Number(current.activeMs || 0)),
    detectionWeight: Math.max(0, Number(current.detectionWeight || 0)),
    blockedNodeCount: Math.max(0, Number(current.blockedNodeCount || 0)),
    maskedSpanCount: Math.max(0, Number(current.maskedSpanCount || 0)),
    profanityNodeCount: Math.max(0, Number(current.profanityNodeCount || 0)),
    toxicNodeCount: Math.max(0, Number(current.toxicNodeCount || 0)),
    hateNodeCount: Math.max(0, Number(current.hateNodeCount || 0)),
    lastSeenAt: Number(current.lastSeenAt || now),
    lastDetectionAt: Number(current.lastDetectionAt || 0)
  };
  state.domains[normalizedDomain] = next;
  return next;
}

function getWellbeingDetectionDedupeKey(entry) {
  const key = String(entry?.key || "");
  const pageKey = String(entry?.pageKey || "");
  const oldKeyMatch = key.match(/^(\d+):(\d+):[^:]+:(.+)$/);
  if (oldKeyMatch) {
    return [`tab:${oldKeyMatch[1]}`, `seq:${oldKeyMatch[2]}`, pageKey || oldKeyMatch[3]].join(":");
  }
  const currentKeyMatch = key.match(/^(\d+):seq:(\d+):(.+)$/);
  if (currentKeyMatch) {
    return [`tab:${currentKeyMatch[1]}`, `seq:${currentKeyMatch[2]}`, pageKey || currentKeyMatch[3]].join(":");
  }
  return key || [entry?.domain || "", pageKey, entry?.source || "", entry?.ts || ""].join(":");
}

function trimWellbeingState(state, now = Date.now()) {
  const latestDetectionByKey = new Map();
  for (const entry of state.recentDetections || []) {
    if (now - Number(entry?.ts || 0) > WELLBEING_RECENT_DETECTION_WINDOW_MS) {
      continue;
    }
    const fallbackKey = [
      entry?.domain || "",
      entry?.pageKey || "",
      entry?.source || "",
      entry?.ts || ""
    ].join(":");
    latestDetectionByKey.set(getWellbeingDetectionDedupeKey(entry) || fallbackKey, entry);
  }
  state.recentDetections = Array.from(latestDetectionByKey.values())
    .sort((left, right) => Number(left?.ts || 0) - Number(right?.ts || 0))
    .slice(-WELLBEING_RECENT_DETECTION_LIMIT);

  for (const domainState of Object.values(state.domains || {})) {
    if (!domainState || typeof domainState !== "object") {
      continue;
    }
    domainState.detectionWeight = 0;
    domainState.blockedNodeCount = 0;
    domainState.maskedSpanCount = 0;
    domainState.profanityNodeCount = 0;
    domainState.toxicNodeCount = 0;
    domainState.hateNodeCount = 0;
    domainState.lastDetectionAt = 0;
  }
  for (const entry of state.recentDetections) {
    const domainState = getWellbeingDomainState(state, entry?.domain, now);
    if (!domainState) {
      continue;
    }
    applyWellbeingDetectionCounters(domainState, getWellbeingDetectionCounters(entry), 1);
    domainState.lastDetectionAt = Math.max(
      Number(domainState.lastDetectionAt || 0),
      Number(entry?.ts || now)
    );
    domainState.lastSeenAt = Math.max(Number(domainState.lastSeenAt || 0), Number(entry?.ts || now));
  }

  const domainEntries = Object.entries(state.domains || {})
    .sort(([, left], [, right]) => Number(right.lastSeenAt || 0) - Number(left.lastSeenAt || 0))
    .slice(0, WELLBEING_SITE_LIMIT);
  state.domains = Object.fromEntries(domainEntries);

  for (const [tabId, heartbeat] of Object.entries(state.lastHeartbeatByTab || {})) {
    if (now - Number(heartbeat?.at || 0) > WELLBEING_IDLE_GAP_MS * 4) {
      delete state.lastHeartbeatByTab[tabId];
    }
  }

  if (
    state.lastActiveSample &&
    now - Number(state.lastActiveSample.at || 0) > WELLBEING_IDLE_GAP_MS * 4
  ) {
    state.lastActiveSample = null;
  }

  state.updatedAt = now;
  return state;
}

async function readWellbeingState() {
  const result = await chrome.storage.local.get(WELLBEING_STATE_STORAGE_KEY);
  return normalizeWellbeingState(result?.[WELLBEING_STATE_STORAGE_KEY]);
}

async function writeWellbeingState(state) {
  await chrome.storage.local.set({
    [WELLBEING_STATE_STORAGE_KEY]: trimWellbeingState(state)
  });
}

function applyWellbeingActiveSample(state, now, sample) {
  const tabId = sample?.tabId;
  const url = String(sample?.url || "");
  const domain = normalizeDomain(sample?.domain || domainFromUrl(url));
  const pageKey = sample?.pageKey || normalizeWellbeingPageKey(url);
  if (!tabId || !domain) {
    return 0;
  }

  const tabKey = String(tabId);
  const previousActiveSample = state.lastActiveSample;
  const isActiveSample = sample?.active !== false && sample?.visible !== false;
  let activeMs = 0;
  if (isActiveSample && previousActiveSample?.active !== false && previousActiveSample?.at) {
    const deltaMs = now - Number(previousActiveSample.at || 0);
    if (deltaMs > 0 && deltaMs <= WELLBEING_IDLE_GAP_MS) {
      activeMs = Math.min(deltaMs, WELLBEING_MAX_HEARTBEAT_DELTA_MS);
    }
  }

  state.lastHeartbeatByTab[tabKey] = {
    at: now,
    url,
    domain,
    pageKey
  };
  state.lastActiveSample = {
    at: now,
    tabId,
    url,
    domain,
    pageKey,
    active: isActiveSample
  };

  if (activeMs > 0) {
    state.totalActiveMs = Math.max(0, Number(state.totalActiveMs || 0)) + activeMs;
    const activeDomain = normalizeDomain(previousActiveSample?.domain || domain);
    const domainState = getWellbeingDomainState(state, activeDomain, now);
    if (domainState) {
      domainState.activeMs += activeMs;
      domainState.lastSeenAt = now;
    }
  }

  return activeMs;
}

function cloneWellbeingState(state, now = Date.now()) {
  const normalized = normalizeWellbeingState(state, now);
  const domains = {};
  for (const [domain, value] of Object.entries(normalized.domains || {})) {
    domains[domain] = { ...value };
  }
  const lastHeartbeatByTab = {};
  for (const [tabId, value] of Object.entries(normalized.lastHeartbeatByTab || {})) {
    lastHeartbeatByTab[tabId] = { ...value };
  }
  return {
    ...normalized,
    domains,
    recentDetections: [...(normalized.recentDetections || [])],
    lastActiveSample: normalized.lastActiveSample ? { ...normalized.lastActiveSample } : null,
    lastHeartbeatByTab
  };
}

function projectActiveWellbeingState(state, tab, now = Date.now()) {
  const projected = cloneWellbeingState(state, now);
  const tabId = tab?.id;
  const url = String(tab?.url || "");
  const domain = domainFromUrl(url);
  if (!tabId || !domain) {
    return projected;
  }

  const previous = projected.lastActiveSample;
  if (!previous?.at) {
    return projected;
  }

  const deltaMs = now - Number(previous.at || 0);
  if (previous.active === false || deltaMs <= 0 || deltaMs > WELLBEING_IDLE_GAP_MS) {
    return projected;
  }

  const activeMs = Math.min(deltaMs, WELLBEING_MAX_HEARTBEAT_DELTA_MS);
  projected.totalActiveMs = Math.max(0, Number(projected.totalActiveMs || 0)) + activeMs;
  const activeDomain = normalizeDomain(previous.domain || domain);
  const domainState = getWellbeingDomainState(projected, activeDomain, now);
  if (domainState) {
    domainState.activeMs += activeMs;
    domainState.lastSeenAt = now;
  }
  projected.updatedAt = now;
  return projected;
}

function updateWellbeingState(mutator) {
  const nextUpdate = wellbeingUpdateChain.then(async () => {
    const now = Date.now();
    const state = normalizeWellbeingState(await readWellbeingState(), now);
    const result = (await mutator(state, now)) || {};
    await writeWellbeingState(state);
    return {
      ...result,
      state
    };
  });

  wellbeingUpdateChain = nextUpdate.catch(() => {});
  return nextUpdate;
}

async function isSenderTabCurrentlyActive(sender) {
  const tabId = sender?.tab?.id;
  if (!tabId) return false;

  try {
    const tab = await chrome.tabs.get(tabId);
    if (!tab?.active) return false;
    const windowInfo = await chrome.windows.get(tab.windowId);
    return windowInfo?.focused !== false;
  } catch {
    return Boolean(sender?.tab?.active);
  }
}

function normalizeDetectionSummary(value) {
  const summary = value && typeof value === "object" ? value : {};
  const categoryHits = summary.categoryHits && typeof summary.categoryHits === "object"
    ? summary.categoryHits
    : {};
  const blockedNodeCount = Math.max(0, Number(summary.blockedNodeCount || 0));
  const maskedSpanCount = Math.max(0, Number(summary.maskedSpanCount || 0));
  const profanityNodeCount = Math.max(
    0,
    Number(summary.profanityNodeCount ?? categoryHits.insult ?? 0)
  );
  const toxicNodeCount = Math.max(
    0,
    Number(summary.toxicNodeCount ?? categoryHits.abuse ?? 0)
  );
  const hateNodeCount = Math.max(
    0,
    Number(summary.hateNodeCount ?? categoryHits.hate ?? 0)
  );
  const maxScores = summary.maxScores && typeof summary.maxScores === "object"
    ? summary.maxScores
    : {};
  const scoreWeight =
    Math.max(
      Number(maxScores.profanity || 0),
      Number(maxScores.toxicity || 0),
      Number(maxScores.hate || 0)
    ) * 4;
  const detectionWeight = Math.min(
    48,
    maskedSpanCount +
      blockedNodeCount * 2 +
      profanityNodeCount * 3 +
      toxicNodeCount * 2 +
      hateNodeCount * 3 +
      scoreWeight
  );

  return {
    pipelineSequence: Number(summary.pipelineSequence || 0),
    blockedNodeCount,
    maskedSpanCount,
    profanityNodeCount,
    toxicNodeCount,
    hateNodeCount,
    detectionWeight: Math.max(0, detectionWeight),
    maxScores: {
      profanity: Number(maxScores.profanity || 0),
      toxicity: Number(maxScores.toxicity || 0),
      hate: Number(maxScores.hate || 0)
    }
  };
}

function buildWellbeingDetectionEventKey(tabId, summary, pageKey, domain) {
  const pipelineSequence = Number(summary?.pipelineSequence || 0);
  const sequenceKey = pipelineSequence > 0 ? `seq:${pipelineSequence}` : `at:${Date.now()}`;
  return [tabId || 0, sequenceKey, pageKey || domain].join(":");
}

function getWellbeingDetectionCounters(value) {
  const entry = value && typeof value === "object" ? value : {};
  return {
    detectionWeight: Math.max(0, Number(entry.detectionWeight || 0)),
    blockedNodeCount: Math.max(0, Number(entry.blockedNodeCount || 0)),
    maskedSpanCount: Math.max(0, Number(entry.maskedSpanCount || 0)),
    profanityNodeCount: Math.max(0, Number(entry.profanityNodeCount || 0)),
    toxicNodeCount: Math.max(0, Number(entry.toxicNodeCount || 0)),
    hateNodeCount: Math.max(0, Number(entry.hateNodeCount || 0))
  };
}

function sumWellbeingDetectionCounters(entries) {
  const total = {
    detectionWeight: 0,
    blockedNodeCount: 0,
    maskedSpanCount: 0,
    profanityNodeCount: 0,
    toxicNodeCount: 0,
    hateNodeCount: 0
  };

  for (const entry of Array.isArray(entries) ? entries : []) {
    const counters = getWellbeingDetectionCounters(entry);
    for (const key of Object.keys(total)) {
      total[key] += Number(counters[key] || 0);
    }
  }

  return total;
}

function applyWellbeingDetectionCounters(domainState, counters, direction = 1) {
  if (!domainState) {
    return;
  }
  const sign = direction < 0 ? -1 : 1;
  for (const key of [
    "detectionWeight",
    "blockedNodeCount",
    "maskedSpanCount",
    "profanityNodeCount",
    "toxicNodeCount",
    "hateNodeCount"
  ]) {
    domainState[key] = Math.max(
      0,
      Number(domainState[key] || 0) + Number(counters?.[key] || 0) * sign
    );
  }
}

async function recordWellbeingHeartbeat(message, sender) {
  const settings = await getSettings();
  const active = await isSenderTabCurrentlyActive(sender);
  const tabId = sender?.tab?.id;
  const url = String(message?.url || sender?.tab?.url || "");
  const domain = domainFromUrl(url);
  const pageKey = normalizeWellbeingPageKey(url);

  if (!tabId || !domain) {
    return { ok: false, reason: "WELLBEING_UNSUPPORTED_TAB" };
  }

  if (!isGlobalProtectionEnabled(settings)) {
    const state = await readWellbeingState();
    return {
      ok: true,
      skipped: true,
      reason: "PROTECTION_DISABLED",
      view: await buildWellbeingViewWithDebug(state, url, sender?.tab?.id, settings)
    };
  }

  const result = await updateWellbeingState((state, now) => {
    const activeMs = applyWellbeingActiveSample(state, now, {
      tabId,
      url,
      domain,
      pageKey,
      active,
      visible: message?.visible !== false
    });

    return { activeMs };
  });

  return {
    ok: true,
    activeMs: Number(result.activeMs || 0),
    view: await buildWellbeingViewWithDebug(result.state, url, sender?.tab?.id, settings)
  };
}

function ensureWellbeingUsageAlarm() {
  if (!chrome.alarms?.create) {
    return;
  }
  chrome.alarms.create(WELLBEING_USAGE_ALARM_NAME, {
    periodInMinutes: WELLBEING_USAGE_ALARM_PERIOD_MINUTES
  });
}

async function getFocusedActiveHttpTab() {
  const tabs = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
  const tab = tabs?.[0] || null;
  if (!tab?.id || !tab?.url || !isHttpPageUrl(tab.url) || isUnsupportedTabUrl(tab.url)) {
    return null;
  }

  try {
    const windowInfo = await chrome.windows.get(tab.windowId);
    if (windowInfo?.focused === false) {
      return null;
    }
  } catch {
    // Some browser contexts do not expose focus state reliably; active tab is still the best sample.
  }

  return tab;
}

async function recordActiveBrowsingSample(tab = null) {
  const settings = await getSettings();
  if (!isGlobalProtectionEnabled(settings)) {
    return { ok: false, reason: "PROTECTION_DISABLED" };
  }

  const targetTab = tab?.id ? tab : await getFocusedActiveHttpTab();
  if (
    !targetTab?.id ||
    !targetTab?.url ||
    !isHttpPageUrl(targetTab.url) ||
    isUnsupportedTabUrl(targetTab.url)
  ) {
    return { ok: false, reason: "NO_ACTIVE_HTTP_TAB" };
  }

  const url = String(targetTab.url || "");
  const domain = domainFromUrl(url);
  if (!domain) {
    return { ok: false, reason: "NO_ACTIVE_DOMAIN" };
  }

  const result = await updateWellbeingState((state, now) => ({
    activeMs: applyWellbeingActiveSample(state, now, {
      tabId: targetTab.id,
      url,
      domain,
      pageKey: normalizeWellbeingPageKey(url),
      active: true,
      visible: true
    })
  }));

  return {
    ok: true,
    activeMs: Number(result.activeMs || 0)
  };
}

async function broadcastWellbeingWidgetLayout(layout, sourceTabId = 0) {
  const normalizedLayout = {
    size: String(layout?.size || ""),
    x: Number.isFinite(Number(layout?.x)) ? Math.round(Number(layout.x)) : null,
    y: Number.isFinite(Number(layout?.y)) ? Math.round(Number(layout.y)) : null
  };
  const tabs = await chrome.tabs.query({});
  await Promise.all(
    tabs.map(async (tab) => {
      if (
        !tab?.id ||
        tab.id === sourceTabId ||
        !tab.url ||
        !isHttpPageUrl(tab.url) ||
        isUnsupportedTabUrl(tab.url)
      ) {
        return;
      }
      try {
        await chrome.tabs.sendMessage(tab.id, {
          type: "APPLY_WELLBEING_WIDGET_LAYOUT",
          layout: normalizedLayout
        });
      } catch {
        // Some tabs may not have the content script yet or may be restricted.
      }
    })
  );
}

async function broadcastWellbeingStateReset() {
  const tabs = await chrome.tabs.query({});
  await Promise.all(
    tabs.map(async (tab) => {
      if (!tab?.id || !tab.url || !isHttpPageUrl(tab.url) || isUnsupportedTabUrl(tab.url)) {
        return;
      }
      try {
        await chrome.tabs.sendMessage(tab.id, { type: "WELLBEING_STATE_RESET" });
      } catch {
        // Some tabs may not have the widget content script active yet.
      }
    })
  );
}

async function recordWellbeingDetection(message, sender) {
  const tabId = sender?.tab?.id || 0;
  const url = String(message?.url || sender?.tab?.url || "");
  const domain = domainFromUrl(url);
  const pageKey = normalizeWellbeingPageKey(url);
  if (!domain) {
    return { ok: false, reason: "WELLBEING_UNSUPPORTED_URL" };
  }

  const settings = await getSettings();
  if (!isGlobalProtectionEnabled(settings)) {
    const state = await readWellbeingState();
    return {
      ok: true,
      skipped: true,
      reason: "PROTECTION_DISABLED",
      view: await buildWellbeingViewWithDebug(state, url, sender?.tab?.id, settings)
    };
  }

  const summary = normalizeDetectionSummary(message?.summary);
  if (summary.detectionWeight <= 0) {
    return { ok: true, skipped: true, reason: "NO_DETECTION_WEIGHT" };
  }

  const source = String(message?.source || "backend-foreground");
  const eventKey = buildWellbeingDetectionEventKey(tabId, summary, pageKey, domain);

  const result = await updateWellbeingState((state, now) => {
    const previousIndex = state.recentDetections.findIndex((entry) => entry.key === eventKey);
    if (previousIndex >= 0) {
      const previous = state.recentDetections[previousIndex];
      applyWellbeingDetectionCounters(
        state.domains?.[normalizeDomain(previous.domain)],
        getWellbeingDetectionCounters(previous),
        -1
      );
      state.recentDetections.splice(previousIndex, 1);
    }

    const domainState = getWellbeingDomainState(state, domain, now);
    if (domainState) {
      applyWellbeingDetectionCounters(domainState, getWellbeingDetectionCounters(summary), 1);
      domainState.lastDetectionAt = now;
      domainState.lastSeenAt = now;
    }

    state.recentDetections.push({
      key: eventKey,
      ts: now,
      url,
      domain,
      pageKey,
      source,
      blockedNodeCount: summary.blockedNodeCount,
      maskedSpanCount: summary.maskedSpanCount,
      profanityNodeCount: summary.profanityNodeCount,
      toxicNodeCount: summary.toxicNodeCount,
      hateNodeCount: summary.hateNodeCount,
      detectionWeight: summary.detectionWeight,
      maxScores: summary.maxScores
    });

    return { replaced: previousIndex >= 0 };
  });
  return {
    ok: true,
    replaced: Boolean(result.replaced),
    view: await buildWellbeingViewWithDebug(result.state, url, sender?.tab?.id, settings)
  };
}

function getTabSitePolicyForWellbeing(url, tabId) {
  const current = tabId ? SITE_POLICY_BY_TAB.get(tabId) : null;
  if (current?.url === url && current.policy) {
    return shouldInterruptNavigationForPolicy(current.policy, current.source || "") ? current.policy : null;
  }
  const cached = getCachedSitePolicy(url);
  return shouldInterruptNavigationForPolicy(cached, "cache") ? cached : null;
}

function getCurrentPageExpressionCount(page = {}) {
  const profanityCount = Number(page.profanityNodeCount || 0);
  const blockedCount = Number(page.blockedNodeCount || 0);
  const maskedCount = Number(page.maskedSpanCount || 0);
  const toxicCount = Number(page.toxicNodeCount || 0);
  const hateCount = Number(page.hateNodeCount || 0);
  if (profanityCount > 0) {
    return Math.max(0, Math.round(profanityCount));
  }
  return Math.max(0, Math.round(Math.max(maskedCount, blockedCount, toxicCount, hateCount)));
}

function getAngerLevel(currentPage, policy, stageSettings) {
  const stageCount = normalizeWellbeingStageCount(stageSettings?.angerStageCount);
  const detectionsPerStage = normalizeWellbeingStageStep(
    stageSettings?.angerDetectionsPerStage,
    DEFAULT_SETTINGS.wellbeingAngerDetectionsPerStage,
    1,
    50
  );
  const expressionCount = getCurrentPageExpressionCount(currentPage);
  let level = expressionCount > 0 ? Math.ceil(expressionCount / detectionsPerStage) : 0;
  if (policy?.verdict === "block") {
    level = Math.max(level, stageCount);
  } else if (policy?.verdict === "warning") {
    level = Math.max(level, Math.max(1, Math.ceil(stageCount * 0.4)));
  }

  return Math.max(0, Math.min(stageCount, level));
}

function getAgeLevel(totalActiveMs, stageSettings) {
  const minutes = Number(totalActiveMs || 0) / 60000;
  const stageCount = normalizeWellbeingStageCount(stageSettings?.ageStageCount);
  const minutesPerStage = normalizeWellbeingStageStep(
    stageSettings?.ageMinutesPerStage,
    DEFAULT_SETTINGS.wellbeingAgeMinutesPerStage,
    5,
    240
  );
  return Math.max(0, Math.min(stageCount, Math.floor(minutes / minutesPerStage)));
}

function getAngerMood(level, stageCount) {
  if (level <= 0) {
    return "";
  }
  const ratio = Math.min(1, Number(level || 0) / Math.max(1, Number(stageCount || 1)));
  if (ratio >= 0.9) return "furious";
  if (ratio >= 0.7) return "angry";
  if (ratio >= 0.5) return "mad";
  if (ratio >= 0.25) return "annoyed";
  return "uneasy";
}

function getAgeMood(level, stageCount) {
  if (level <= 0) {
    return "calm";
  }
  const ratio = Math.min(1, Number(level || 0) / Math.max(1, Number(stageCount || 1)));
  if (ratio >= 0.8) return "old";
  if (ratio >= 0.45) return "tired";
  if (ratio >= 0.2) return "focused";
  return "calm";
}

function buildWellbeingView(state, url, tabId, settings = DEFAULT_SETTINGS, policyOverride = null) {
  const now = Date.now();
  const normalizedState = trimWellbeingState(normalizeWellbeingState(state, now), now);
  const stageSettings = normalizeWellbeingStageSettings(settings);
  const domain = domainFromUrl(url);
  const pageKey = normalizeWellbeingPageKey(url);
  const policy = isGlobalProtectionEnabled(settings)
    ? policyOverride || getTabSitePolicyForWellbeing(url, tabId)
    : null;
  const recentForPage = normalizedState.recentDetections.filter(
    (entry) =>
      entry.pageKey &&
      entry.pageKey === pageKey &&
      now - Number(entry.ts || 0) <= WELLBEING_RECENT_DETECTION_WINDOW_MS
  );
  const pageCounters = sumWellbeingDetectionCounters(recentForPage);
  const currentPage = {
    url: String(url || ""),
    pageKey,
    domain,
    activeMs: Math.round(Number(normalizedState.domains?.[domain]?.activeMs || 0)),
    detectionWeight: Math.round(Number(pageCounters.detectionWeight || 0)),
    maskedSpanCount: Math.round(Number(pageCounters.maskedSpanCount || 0)),
    blockedNodeCount: Math.round(Number(pageCounters.blockedNodeCount || 0)),
    profanityNodeCount: Math.round(Number(pageCounters.profanityNodeCount || 0)),
    toxicNodeCount: Math.round(Number(pageCounters.toxicNodeCount || 0)),
    hateNodeCount: Math.round(Number(pageCounters.hateNodeCount || 0)),
    recentEventCount: recentForPage.length
  };
  currentPage.expressionCount = getCurrentPageExpressionCount(currentPage);
  const domainState = normalizedState.domains?.[domain] || {};
  const ageLevel = getAgeLevel(normalizedState.totalActiveMs, stageSettings);
  const angerLevel = getAngerLevel(currentPage, policy, stageSettings);
  const mood =
    getAngerMood(angerLevel, stageSettings.angerStageCount) ||
    getAgeMood(ageLevel, stageSettings.ageStageCount);

  return {
    schemaVersion: WELLBEING_SCHEMA_VERSION,
    dayKey: normalizedState.dayKey,
    updatedAt: normalizedState.updatedAt,
    startedAt: normalizedState.startedAt,
    totalActiveMs: Math.round(Number(normalizedState.totalActiveMs || 0)),
    totalActiveMinutes: Math.floor(Number(normalizedState.totalActiveMs || 0) / 60000),
    domain,
    ageLevel,
    ageStageCount: stageSettings.ageStageCount,
    ageMinutesPerStage: stageSettings.ageMinutesPerStage,
    angerLevel,
    angerStageCount: stageSettings.angerStageCount,
    angerDetectionsPerStage: stageSettings.angerDetectionsPerStage,
    mood,
    policyVerdict: policy?.verdict || "allow",
    protectionEnabled: isGlobalProtectionEnabled(settings),
    currentPage,
    currentSite: currentPage,
    currentDomain: {
      activeMs: Math.round(Number(domainState.activeMs || 0)),
      detectionWeight: Math.round(Number(domainState.detectionWeight || 0)),
      maskedSpanCount: Math.round(Number(domainState.maskedSpanCount || 0)),
      blockedNodeCount: Math.round(Number(domainState.blockedNodeCount || 0)),
      profanityNodeCount: Math.round(Number(domainState.profanityNodeCount || 0)),
      toxicNodeCount: Math.round(Number(domainState.toxicNodeCount || 0)),
      hateNodeCount: Math.round(Number(domainState.hateNodeCount || 0))
    }
  };
}

function normalizeWellbeingDebugOverride(value) {
  const override = value && typeof value === "object" ? value : {};
  return {
    enabled: override.enabled === true,
    usageMinutes: Math.max(0, Math.min(24 * 60, Math.round(Number(override.usageMinutes || 0)))),
    profanityCount: Math.max(0, Math.min(999, Math.round(Number(override.profanityCount || 0)))),
    harmfulCount: Math.max(0, Math.min(999, Math.round(Number(override.harmfulCount || 0)))),
    policyVerdict: ["allow", "warning", "block"].includes(String(override.policyVerdict || "allow"))
      ? String(override.policyVerdict || "allow")
      : "allow",
    simulatedHour: Math.max(0, Math.min(23, Math.round(Number(override.simulatedHour || 0)))),
    updatedAt: Number(override.updatedAt || Date.now())
  };
}

async function readWellbeingDebugOverride() {
  const result = await chrome.storage.local.get(WELLBEING_DEBUG_OVERRIDE_STORAGE_KEY);
  return normalizeWellbeingDebugOverride(result?.[WELLBEING_DEBUG_OVERRIDE_STORAGE_KEY]);
}

async function setWellbeingDebugOverride(value) {
  const override = {
    ...normalizeWellbeingDebugOverride(value),
    enabled: true,
    updatedAt: Date.now()
  };
  await chrome.storage.local.set({
    [WELLBEING_DEBUG_OVERRIDE_STORAGE_KEY]: override
  });
  return override;
}

async function clearWellbeingDebugOverride() {
  await chrome.storage.local.remove(WELLBEING_DEBUG_OVERRIDE_STORAGE_KEY);
}

async function clearWellbeingState() {
  await chrome.storage.local.remove([
    WELLBEING_STATE_STORAGE_KEY,
    WELLBEING_DEBUG_OVERRIDE_STORAGE_KEY
  ]);
  await broadcastWellbeingStateReset();
  recordRuntimeLogEvent({
    type: "wellbeing-state",
    ok: true,
    status: "cleared",
    source: "options",
    reason: "위젯 사용량과 탐지 카운트를 초기화했습니다."
  });
}

function buildDebugSitePolicy(url, override) {
  if (!override?.enabled || override.policyVerdict === "allow") {
    return null;
  }
  const domain = domainFromUrl(url);
  return {
    url,
    domain,
    verdict: override.policyVerdict,
    risk_score: override.policyVerdict === "block" ? 0.94 : 0.68,
    site_category: "developer-test",
    security_threat: false,
    harmful_content: true,
    reasons: ["개발자 테스트 모드에서 생성한 사이트 판정입니다."],
    matched_entries: [],
    exact_match: null,
    retrieval_ms: 0,
    llm_timing_ms: 0,
    timing_ms: 0,
    agent: {
      mode: "developer-test",
      model: null,
      reason: "DEBUG_OVERRIDE",
      response:
        override.policyVerdict === "block"
          ? "개발자 테스트 모드: 차단 권장 사이트로 시뮬레이션 중입니다."
          : "개발자 테스트 모드: 접속 전 주의 사이트로 시뮬레이션 중입니다.",
      sub_agents: null
    }
  };
}

function buildDebugWellbeingState(url, override) {
  const now = Date.now();
  const domain = domainFromUrl(url);
  const pageKey = normalizeWellbeingPageKey(url);
  const harmfulCount = Math.max(Number(override.harmfulCount || 0), Number(override.profanityCount || 0));
  const event =
    harmfulCount > 0
      ? {
          key: `debug:${pageKey || domain}:${override.updatedAt || now}`,
          ts: now,
          url,
          domain,
          pageKey,
          source: "developer-test",
          blockedNodeCount: harmfulCount,
          maskedSpanCount: harmfulCount,
          profanityNodeCount: Number(override.profanityCount || 0),
          toxicNodeCount: harmfulCount,
          hateNodeCount: 0,
          detectionWeight: harmfulCount,
          maxScores: {
            profanity: override.profanityCount > 0 ? 0.98 : 0,
            toxicity: harmfulCount > 0 ? 0.88 : 0,
            hate: 0
          }
        }
      : null;
  const activeMs = Number(override.usageMinutes || 0) * 60000;
  return {
    schemaVersion: WELLBEING_SCHEMA_VERSION,
    dayKey: getLocalDayKey(now),
    startedAt: now - activeMs,
    updatedAt: now,
    totalActiveMs: activeMs,
    domains: {
      [domain]: {
        domain,
        activeMs,
        detectionWeight: harmfulCount,
        blockedNodeCount: harmfulCount,
        maskedSpanCount: harmfulCount,
        profanityNodeCount: Number(override.profanityCount || 0),
        toxicNodeCount: harmfulCount,
        hateNodeCount: 0,
        lastSeenAt: now,
        lastDetectionAt: event ? now : 0
      }
    },
    recentDetections: event ? [event] : [],
    lastHeartbeatByTab: {}
  };
}

async function buildWellbeingViewWithDebug(state, url, tabId, settings) {
  const override = await readWellbeingDebugOverride();
  if (!override.enabled) {
    return buildWellbeingView(state, url, tabId, settings);
  }
  return {
    ...buildWellbeingView(
      buildDebugWellbeingState(url, override),
      url,
      tabId,
      settings,
      buildDebugSitePolicy(url, override)
    ),
    debugOverride: override
  };
}

async function getWellbeingViewForUrl(url, sender) {
  const [state, settings] = await Promise.all([readWellbeingState(), getSettings()]);
  let viewState = state;
  const requestedUrl = String(url || sender?.tab?.url || "");
  if (await isSenderTabCurrentlyActive(sender)) {
    viewState = projectActiveWellbeingState(state, sender.tab);
  } else if (!sender?.tab && requestedUrl) {
    const activeTab = await getFocusedActiveHttpTab().catch(() => null);
    if (
      activeTab?.url &&
      normalizeWellbeingPageKey(activeTab.url) === normalizeWellbeingPageKey(requestedUrl)
    ) {
      viewState = projectActiveWellbeingState(state, activeTab);
    }
  }

  return {
    ok: true,
    view: await buildWellbeingViewWithDebug(
      viewState,
      requestedUrl,
      sender?.tab?.id,
      settings
    )
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
      "content-wellbeing-widget.js",
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

function normalizeHealthCacheTtlMs(value, fallbackMs) {
  const numberValue = Number(value);
  if (Number.isNaN(numberValue)) return fallbackMs;
  return Math.max(0, Math.min(10 * 60 * 1000, Math.round(numberValue)));
}

function getCachedBackendHealth(apiBaseUrl, options = {}) {
  if (!backendHealthCache || backendHealthCache.apiBaseUrl !== apiBaseUrl) {
    return null;
  }

  const ageMs = Date.now() - Number(backendHealthCache.checkedAt || 0);
  const defaultCacheTtlMs =
    backendHealthCache.response?.ok === false
      ? BACKEND_HEALTH_FAILURE_CACHE_TTL_MS
      : BACKEND_HEALTH_CACHE_TTL_MS;
  const requestedCacheTtlMs = normalizeHealthCacheTtlMs(options.cacheTtlMs, defaultCacheTtlMs);
  const cacheTtlMs =
    backendHealthCache.response?.ok === false
      ? Math.min(requestedCacheTtlMs, BACKEND_HEALTH_FAILURE_CACHE_TTL_MS)
      : requestedCacheTtlMs;
  const staleTtlMs = normalizeHealthCacheTtlMs(options.staleTtlMs, BACKEND_HEALTH_STALE_TTL_MS);

  if (ageMs <= cacheTtlMs) {
    return {
      ...backendHealthCache.response,
      cacheHit: true,
      stale: false,
      cachedAgeMs: ageMs
    };
  }

  if (options.staleOk && ageMs <= staleTtlMs) {
    return {
      ...backendHealthCache.response,
      cacheHit: true,
      stale: true,
      cachedAgeMs: ageMs,
      refreshing: Boolean(backendHealthInFlight)
    };
  }

  return null;
}

function setCachedBackendHealth(apiBaseUrl, response) {
  backendHealthCache = {
    apiBaseUrl,
    checkedAt: Date.now(),
    response: {
      ...(response || {}),
      apiBaseUrl
    }
  };
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

async function performBackendWarmupRequest(apiBaseUrl, requestTimeoutMs, sensitivity, mode = "background-validation") {
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
          `${apiBaseUrl}/warmup`,
          {
            method: "POST",
            body: JSON.stringify({
              load_classifier: true,
              load_span_detector: true,
              run_span_probe: true,
              sensitivity: normalizeSensitivity(sensitivity)
            })
          },
          requestTimeoutMs,
          diagnostics.abortSignal
        );
      }
    );

    return {
      body,
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

async function warmupBackendModels(message = {}) {
  const settings = await getSettings();
  const apiBaseUrl = sanitizeApiBaseUrl(message?.apiBaseUrl || settings.backendApiBaseUrl);
  const sensitivity = normalizeSensitivity(message?.sensitivity ?? settings.sensitivity);
  const analysisMode = normalizeAnalyzeBatchMode(message?.analysisMode || "background-validation");
  const requestTimeoutMs = Math.max(
    FOREGROUND_ANALYZE_MIN_TIMEOUT_MS,
    Math.min(
      BACKEND_WARMUP_TIMEOUT_MS,
      Number(message?.requestTimeoutMsOverride || BACKEND_WARMUP_TIMEOUT_MS)
    )
  );
  const startedAt = Date.now();

  if (!isBackendEnabled(settings)) {
    return {
      ok: true,
      skipped: true,
      backendStatus: "disabled",
      apiBaseUrl,
      durationMs: 0,
      reason: "BACKEND_DISABLED"
    };
  }

  try {
    const result = await performBackendWarmupRequest(
      apiBaseUrl,
      requestTimeoutMs,
      sensitivity,
      analysisMode
    );
    const body = result.body && typeof result.body === "object" ? result.body : {};
    const response = {
      ok: Boolean(body.ok !== false),
      apiBaseUrl,
      backendStatus: body.ok === false ? "degraded" : "ready",
      durationMs: Date.now() - startedAt,
      requestTimeoutMs,
      analysisMode,
      endpointTotalMs: Number(body.endpoint_total_ms || body.total_ms || 0),
      clientQueueWaitMs: Number(result.queueDiagnostics?.queueWaitMs || 0),
      clientQueueDepthAtEnqueue: Number(result.queueDiagnostics?.queueDepthAtEnqueue || 0),
      clientQueueDepthAtStart: Number(result.queueDiagnostics?.queueDepthAtStart || 0),
      timings: body.timings || {},
      models: body.after || {}
    };
    recordRuntimeLogEvent({
      type: "backend-warmup",
      ok: response.ok,
      status: response.backendStatus,
      source: analysisMode,
      apiBaseUrl,
      durationMs: response.durationMs,
      count: 1,
      message: `endpoint=${Math.round(response.endpointTotalMs)}ms queue=${response.clientQueueWaitMs}ms`
    });
    return response;
  } catch (error) {
    const normalized = normalizeBackendError(error, "BACKEND_WARMUP_FAILED");
    const fallbackTexts = Array.isArray(message?.fallbackTexts)
      ? message.fallbackTexts.map((item) => String(item || "").trim()).filter(Boolean)
      : [];

    if (
      fallbackTexts.length > 0 &&
      (normalized.errorCode === "HTTP_404" || normalized.errorCode === "INVALID_RESPONSE")
    ) {
      const fallback = await analyzeTextBatch({
        texts: fallbackTexts,
        sensitivity,
        requestTimeoutMsOverride: Math.min(requestTimeoutMs, BACKGROUND_ANALYZE_TIMEOUT_CAP_MS),
        analysisMode
      });
      return {
        ...fallback,
        warmupFallback: true,
        fallbackReason: normalized.errorCode,
        durationMs: Date.now() - startedAt
      };
    }

    recordRuntimeLogEvent({
      type: "backend-warmup",
      ok: false,
      status: "failed",
      source: analysisMode,
      apiBaseUrl,
      durationMs: Date.now() - startedAt,
      count: 1,
      errorCode: normalized.errorCode,
      reason: normalized.reason
    });
    return {
      ok: false,
      apiBaseUrl,
      backendStatus: "degraded",
      durationMs: Date.now() - startedAt,
      errorCode: normalized.errorCode,
      reason: normalized.reason,
      retryable: normalized.retryable
    };
  }
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

  for (let chunkIndex = 0; chunkIndex < chunks.length; chunkIndex += 1) {
    const chunk = chunks[chunkIndex];
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
      if (normalizeAnalyzeBatchMode(mode) === "foreground") {
        for (const remainingChunk of chunks.slice(chunkIndex + 1)) {
          results.push(...createSkippedAnalyzeBatchResults(remainingChunk));
          skippedChunkCount += 1;
          failedTextCount += remainingChunk.length;
        }
        break;
      }
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
  const fetchHealth = async (timeoutMs) => {
    const body = await fetchJsonWithTimeout(
      `${apiBaseUrl}/health`,
      { method: "GET" },
      timeoutMs
    );
    const modelReady = deriveBackendModelReady(body);

    return {
      ok: true,
      apiBaseUrl,
      ...(body || {}),
      model_ready: modelReady,
      backendStatus: modelReady ? "ready" : "degraded"
    };
  };

  const softTimeoutMs = Math.min(
    requestTimeoutMs,
    normalizeHealthRequestTimeoutMs(options.softTimeoutMs, BACKEND_HEALTH_TIMEOUT_MS)
  );
  const retryTimeoutMs = Math.min(
    requestTimeoutMs,
    Math.max(BACKEND_HEALTH_RETRY_TIMEOUT_MS, softTimeoutMs)
  );
  const retryOnTimeout = options.retryOnTimeout !== false;

  try {
    return await fetchHealth(softTimeoutMs);
  } catch (error) {
    const normalized = normalizeBackendError(error, "HEALTH_CHECK_FAILED");
    if (retryOnTimeout && normalized.errorCode === "TIMEOUT" && retryTimeoutMs > softTimeoutMs) {
      try {
        const retryResult = await fetchHealth(retryTimeoutMs);
        return {
          ...retryResult,
          slow: true,
          initialErrorCode: "TIMEOUT",
          backendStatus: retryResult.model_ready === false ? "degraded" : "slow"
        };
      } catch (retryError) {
        const retryNormalized = normalizeBackendError(retryError, "HEALTH_CHECK_RETRY_FAILED");
        if (!options.suppressErrorLog) {
          console.error("[청마루] checkApiHealth retry failed", retryError);
        }
        return {
          ok: false,
          apiBaseUrl,
          backendStatus: "degraded",
          initialErrorCode: normalized.errorCode,
          ...retryNormalized
        };
      }
    }

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

  if (!isBackendEnabled(settings)) {
    return {
      ok: true,
      apiBaseUrl,
      durationMs: 0,
      backendStatus: "disabled",
      analysisMode,
      requestedCount: texts.length,
      cacheHitCount: 0,
      inFlightHitCount: 0,
      requestCount: 0,
      splitRetryCount: 0,
      chunkSize: 0,
      skippedChunkCount: 1,
      failedTextCount: 0,
      lastBackendErrorCode: "BACKEND_DISABLED",
      requestTimeoutMs: 0,
      requestTimings: [],
      backendQueueWaitMs: 0,
      backendQueueDepthAtEnqueue: 0,
      backendQueueDepthAtStart: 0,
      results: createSkippedAnalyzeBatchResults(texts)
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

      const response = {
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
      recordAnalyzeBatchRuntimeLog(response);
      return response;
    }

    if (inFlightResultPromises.length > 0) {
      await Promise.all(inFlightResultPromises);
    }

    const response = {
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
    recordAnalyzeBatchRuntimeLog(response);
    return response;
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
      const response = {
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
      recordAnalyzeBatchRuntimeLog(response);
      return response;
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
    const response = {
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
    recordRuntimeLogEvent({
      type: "analyze-batch",
      ok: false,
      status: "failed",
      source: analysisMode,
      apiBaseUrl,
      durationMs: Date.now() - startedAt,
      count: texts.length,
      skippedCount: Number(analysisDiagnostics?.failedTextCount || texts.length),
      errorCode: normalized.errorCode,
      reason: normalized.reason
    });
    return response;
  }
}

async function checkApiHealth(options = {}) {
  const settings = await getSettings();
  const apiBaseUrl = sanitizeApiBaseUrl(options.apiBaseUrl || settings.backendApiBaseUrl);
  if (!isBackendEnabled(settings) && !options.ignoreBackendDisabled) {
    return {
      ok: false,
      apiBaseUrl,
      backendStatus: "disabled",
      errorCode: "BACKEND_DISABLED",
      reason: "백엔드 연동이 꺼져 있습니다.",
      model_ready: false,
      durationMs: 0,
      cacheHit: false,
      stale: false
    };
  }

  const fallbackTimeoutMs =
    options.retryOnTimeout === false
      ? BACKEND_HEALTH_FAST_TIMEOUT_MS
      : normalizeRequestTimeoutMs(settings.requestTimeoutMs);
  const requestTimeoutMs = normalizeHealthRequestTimeoutMs(
    options.requestTimeoutMs,
    fallbackTimeoutMs
  );
  const allowCached = options.allowCached !== false && !options.forceRefresh;
  const cached = allowCached ? getCachedBackendHealth(apiBaseUrl, options) : null;
  if (cached && !cached.stale) {
    return cached;
  }

  if (backendHealthInFlight?.apiBaseUrl === apiBaseUrl) {
    if (cached && options.staleOk) {
      return cached;
    }
    return backendHealthInFlight.promise;
  }

  const startedAt = Date.now();
  const healthPromise = checkApiHealthInternal(apiBaseUrl, requestTimeoutMs, {
    retryOnTimeout: options.retryOnTimeout,
    softTimeoutMs: options.softTimeoutMs || options.requestTimeoutMs,
    suppressErrorLog: options.suppressErrorLog
  })
    .then((result) => {
      const response = {
        ...result,
        durationMs: Date.now() - startedAt,
        cacheHit: false,
        stale: false
      };
      setCachedBackendHealth(apiBaseUrl, response);
      recordRuntimeLogEvent({
        type: "backend-health",
        ok: Boolean(response.ok && response.model_ready !== false),
        status: response.backendStatus || "",
        source: "health",
        apiBaseUrl: response.apiBaseUrl || apiBaseUrl,
        durationMs: response.durationMs,
        errorCode: response.errorCode || "",
        reason: response.reason || (response.model_ready === false ? "MODEL_NOT_READY" : "")
      });
      return response;
    })
    .finally(() => {
      if (backendHealthInFlight?.promise === healthPromise) {
        backendHealthInFlight = null;
      }
    });

  backendHealthInFlight = {
    apiBaseUrl,
    promise: healthPromise
  };

  if (cached && options.staleOk) {
    return {
      ...cached,
      refreshing: true
    };
  }

  return healthPromise;
}

async function fetchSitePolicyFromBackend(url, settings, options = {}) {
  const apiBaseUrl = sanitizeApiBaseUrl(settings.backendApiBaseUrl);
  const requestTimeoutMs = normalizeRequestTimeoutMs(settings.requestTimeoutMs);
  const isSearchResultCheck = options.context === "search-result";
  const payload = {
    url,
    title: String(options.title || ""),
    snippet: String(options.snippet || ""),
    force_refresh: Boolean(options.forceRefresh)
  };
  const fetchPolicy = (diagnostics = {}) =>
    fetchJsonWithTimeout(
      `${apiBaseUrl}/site/check`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      },
      Math.max(1200, Math.min(requestTimeoutMs, 12000)),
      diagnostics.abortSignal || null
    );

  if (isSearchResultCheck) {
    return enqueueBackendRequest("background-validation", fetchPolicy);
  }

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
  const settings = options.settings || await getSettings();
  if (!isGlobalProtectionEnabled(settings) || settings.siteProtectionEnabled === false) {
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
  const apiBaseUrl = sanitizeApiBaseUrl(settings.backendApiBaseUrl);
  const blockedRule = matchDomainRule(domain, settings.blockedDomains);
  if (blockedRule) {
    const policy = buildOverrideSitePolicy(url, "block", blockedRule);
    setCachedSitePolicy(url, policy);
    recordRuntimeLogEvent({
      type: "site-policy",
      ok: true,
      status: "ready",
      source: "manual-override",
      domain,
      url,
      verdict: "block",
      reason: blockedRule
    });
    return { ok: true, source: "manual-override", policy };
  }

  const warnedRule = matchDomainRule(domain, settings.warnDomains);
  if (warnedRule) {
    const policy = buildOverrideSitePolicy(url, "warning", warnedRule);
    setCachedSitePolicy(url, policy);
    recordRuntimeLogEvent({
      type: "site-policy",
      ok: true,
      status: "ready",
      source: "manual-override",
      domain,
      url,
      verdict: "warning",
      reason: warnedRule
    });
    return { ok: true, source: "manual-override", policy };
  }

  const curatedFallbackPolicy = getCuratedFallbackSitePolicy(url);
  if (curatedFallbackPolicy) {
    setCachedSitePolicy(url, curatedFallbackPolicy);
    recordRuntimeLogEvent({
      type: "site-policy",
      ok: true,
      status: "ready",
      source: "extension-curated-fallback",
      domain: curatedFallbackPolicy.domain || domain,
      url,
      verdict: curatedFallbackPolicy.verdict,
      reason: Array.isArray(curatedFallbackPolicy.reasons)
        ? curatedFallbackPolicy.reasons.join(" / ")
        : ""
    });
    return { ok: true, source: "extension-curated-fallback", policy: curatedFallbackPolicy };
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

  if (!isBackendEnabled(settings)) {
    return {
      ok: true,
      source: "backend-disabled",
      reason: "BACKEND_DISABLED",
      policy: null
    };
  }

  const inflightEntry = createInFlightSitePolicyEntry(url);
  try {
    const policy = await fetchSitePolicyFromBackend(url, settings, options);
    setCachedSitePolicy(url, policy);
    inflightEntry.resolve(policy);
    if (policy?.verdict && policy.verdict !== "allow") {
      recordRuntimeLogEvent({
        type: "site-policy",
        ok: true,
        status: "ready",
        source: "backend",
        domain: policy.domain || domain,
        url: policy.url || url,
        verdict: policy.verdict,
        apiBaseUrl,
        durationMs: policy.timing_ms || policy.retrieval_ms || 0,
        reason: Array.isArray(policy.reasons) ? policy.reasons.join(" / ") : ""
      });
    }
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
    recordRuntimeLogEvent({
      type: "site-policy",
      ok: false,
      status: "failed",
      source: "fallback",
      domain,
      url,
      verdict: "allow",
      apiBaseUrl,
      errorCode: normalized.errorCode,
      reason: normalized.reason
    });
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

async function prefetchSitePolicyForTab(tabId, url, options = {}) {
  if (!tabId || !isHttpPageUrl(url) || isUnsupportedTabUrl(url)) {
    return;
  }
  const settings = options.settings || await getSettings();
  const result = await getSitePolicyForUrl(url, { settings });
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

function isNsfwLoopbackHost(hostname) {
  const host = String(hostname || "").toLowerCase().replace(/^\[|\]$/g, "");
  return host === "localhost" || host === "127.0.0.1" || host === "::1";
}

function isNsfwPrivateAddressLiteral(hostname) {
  const host = String(hostname || "").toLowerCase().replace(/^\[|\]$/g, "");
  if (isNsfwLoopbackHost(host)) return true;
  if (/^10\./.test(host) || /^192\.168\./.test(host) || /^169\.254\./.test(host)) return true;
  const match = host.match(/^172\.(\d{1,3})\./);
  if (match && Number(match[1]) >= 16 && Number(match[1]) <= 31) return true;
  return host === "0.0.0.0" || host.endsWith(".local");
}

function isNsfwLoopbackPage(url) {
  try {
    return isNsfwLoopbackHost(new URL(String(url || "")).hostname);
  } catch {
    return false;
  }
}

function normalizeNsfwClassifierSource(value, allowLoopback) {
  const raw = String(value || "").trim();
  if (!raw) throw new Error("NSFW_SOURCE_MISSING");
  if (/^data:image\//i.test(raw)) {
    if (raw.length > NSFW_CLASSIFIER_MAX_DATA_URL_CHARS) {
      throw new Error("NSFW_SOURCE_TOO_LARGE");
    }
    return raw;
  }
  const parsed = new URL(raw);
  if (!["http:", "https:"].includes(parsed.protocol)) {
    throw new Error("NSFW_SOURCE_UNSUPPORTED");
  }
  if (parsed.username || parsed.password) {
    throw new Error("NSFW_SOURCE_CREDENTIALS");
  }
  if (isNsfwPrivateAddressLiteral(parsed.hostname) && !(allowLoopback && isNsfwLoopbackHost(parsed.hostname))) {
    throw new Error("NSFW_SOURCE_PRIVATE");
  }
  return parsed.href;
}

async function hasNsfwOffscreenDocument() {
  const offscreenUrl = chrome.runtime.getURL(NSFW_OFFSCREEN_DOCUMENT_PATH);
  if (typeof chrome.runtime.getContexts === "function") {
    const contexts = await chrome.runtime.getContexts({
      contextTypes: ["OFFSCREEN_DOCUMENT"],
      documentUrls: [offscreenUrl]
    });
    return contexts.length > 0;
  }
  if (globalThis.clients?.matchAll) {
    const matchedClients = await globalThis.clients.matchAll();
    return matchedClients.some((client) => client.url === offscreenUrl);
  }
  return false;
}

async function ensureNsfwOffscreenDocument() {
  if (!chrome.offscreen?.createDocument) {
    throw new Error("NSFW_OFFSCREEN_UNAVAILABLE");
  }
  if (await hasNsfwOffscreenDocument()) return;
  if (!nsfwOffscreenCreatePromise) {
    nsfwOffscreenCreatePromise = chrome.offscreen.createDocument({
      url: NSFW_OFFSCREEN_DOCUMENT_PATH,
      reasons: ["BLOBS"],
      justification: "Decode visible image thumbnails and run the bundled local NSFW classifier."
    }).finally(() => {
      nsfwOffscreenCreatePromise = null;
    });
  }
  await nsfwOffscreenCreatePromise;
}

async function closeNsfwOffscreenDocument() {
  if (!chrome.offscreen?.closeDocument) return;
  if (!(await hasNsfwOffscreenDocument())) return;
  await chrome.offscreen.closeDocument();
  nsfwClassifierReadyLogSignature = "";
}

async function sendNsfwOffscreenMessage(message) {
  await ensureNsfwOffscreenDocument();
  const response = await chrome.runtime.sendMessage({
    ...message,
    target: NSFW_OFFSCREEN_TARGET
  });
  if (!response) {
    throw new Error("NSFW_OFFSCREEN_NO_RESPONSE");
  }
  return response;
}

function recordNsfwClassifierReady(response, source = "service-worker") {
  if (!response?.ok || response?.testOverride === "fixture") return;
  const signature = [response.modelVersion, response.backend, response.modelLoadCount].join(":");
  if (!signature || signature === nsfwClassifierReadyLogSignature) return;
  nsfwClassifierReadyLogSignature = signature;
  recordRuntimeLogEvent({
    type: "media-safety-classifier-ready",
    ok: true,
    status: response.status || "ready",
    source,
    modelVersion: response.modelVersion,
    backend: response.backend,
    modelLoadMs: response.modelLoadMs,
    warmupMs: response.warmupMs,
    modelLoadCount: response.modelLoadCount,
    tensorCount: response.tensorCount,
    tensorBytes: response.tensorBytes,
    reason: "bundled local NSFW classifier ready"
  });
}

async function warmNsfwClassifier(options = {}) {
  const settings = options.settings || await getSettings();
  if (settings?.enabled === false || settings?.mediaSafetyEnabled !== true) {
    return { ok: true, status: "disabled", reason: "MEDIA_SAFETY_DISABLED" };
  }
  if (nsfwClassifierTestOverride === "off") {
    return { ok: true, status: "disabled", reason: "NSFW_TEST_OVERRIDE_OFF" };
  }
  if (nsfwClassifierTestOverride === "fixture") {
    const status = await sendNsfwOffscreenMessage({ type: "OFFSCREEN_NSFW_GET_STATUS" });
    return { ...status, ok: true, status: "test-fixture", testOverride: "fixture" };
  }
  const response = await sendNsfwOffscreenMessage({ type: "OFFSCREEN_NSFW_WARMUP" });
  if (!response?.ok) {
    const error = new Error(response?.reason || "NSFW_MODEL_LOAD_FAILED");
    error.errorCode = response?.errorCode || "NSFW_MODEL_LOAD_FAILED";
    throw error;
  }
  recordNsfwClassifierReady(response, options.source || "service-worker");
  return response;
}

async function syncNsfwClassifierLifecycle(settings) {
  const activeSettings = settings || await getSettings();
  if (activeSettings?.enabled !== false && activeSettings?.mediaSafetyEnabled === true && nsfwClassifierTestOverride !== "off") {
    // Keep the GPU/offscreen document idle until a visible unresolved image
    // actually needs classification. The content scripts request warm-up only
    // after candidate budgeting and cheap filtering.
    return { ok: true, status: "idle-until-visible-candidate" };
  }
  await closeNsfwOffscreenDocument();
  return { ok: true, status: "closed" };
}

async function classifyNsfwImageBatch(message, sender) {
  const settings = await getSettings();
  if (settings?.enabled === false || settings?.mediaSafetyEnabled !== true) {
    return { ok: true, status: "disabled", results: [], reason: "MEDIA_SAFETY_DISABLED" };
  }
  if (nsfwClassifierTestOverride === "off") {
    return { ok: true, status: "disabled", results: [], reason: "NSFW_TEST_OVERRIDE_OFF" };
  }

  const rawItems = Array.isArray(message?.items) ? message.items : [];
  if (rawItems.length === 0 || rawItems.length > NSFW_CLASSIFIER_BATCH_LIMIT) {
    return {
      ok: false,
      errorCode: "NSFW_BATCH_SIZE_INVALID",
      reason: `NSFW batch size must be 1-${NSFW_CLASSIFIER_BATCH_LIMIT}`
    };
  }
  const pageUrl = String(sender?.tab?.url || "");
  const allowLoopback = isNsfwLoopbackPage(pageUrl);
  const items = [];
  const seenKeys = new Set();
  for (const item of rawItems) {
    const candidateKey = String(item?.candidateKey || "").trim().slice(0, 96);
    if (!candidateKey || seenKeys.has(candidateKey)) continue;
    try {
      items.push({
        candidateKey,
        sourceUrl: normalizeNsfwClassifierSource(item?.sourceUrl, allowLoopback)
      });
      seenKeys.add(candidateKey);
    } catch (error) {
      return {
        ok: false,
        errorCode: String(error?.message || "NSFW_SOURCE_INVALID").slice(0, 80),
        reason: "NSFW classifier source validation failed"
      };
    }
  }
  if (items.length === 0) {
    return { ok: false, errorCode: "NSFW_BATCH_EMPTY", reason: "NSFW batch has no valid items" };
  }

  const response = await sendNsfwOffscreenMessage({
    type: "OFFSCREEN_NSFW_CLASSIFY_BATCH",
    requestId: String(message?.requestId || "").slice(0, 96),
    contextKey: String(message?.contextKey || "").slice(0, 160),
    allowLoopback,
    items
  });
  if (response?.ok) recordNsfwClassifierReady(response, "classifier-batch");
  return response;
}

async function getNsfwClassifierStatus() {
  if (!(await hasNsfwOffscreenDocument())) {
    return {
      ok: true,
      status: "closed",
      testOverride: nsfwClassifierTestOverride,
      cacheSize: 0,
      modelLoadCount: 0,
      tensorCount: 0,
      tensorBytes: 0
    };
  }
  return sendNsfwOffscreenMessage({ type: "OFFSCREEN_NSFW_GET_STATUS" });
}

function isTrustedNsfwTestOverrideSender(sender) {
  if (sender?.id !== chrome.runtime.id) return false;
  if (!sender?.tab) return true;
  return String(sender?.url || "").startsWith(chrome.runtime.getURL(""));
}

async function setNsfwClassifierTestOverride(message, sender) {
  if (!isTrustedNsfwTestOverrideSender(sender)) {
    return { ok: false, errorCode: "NSFW_TEST_OVERRIDE_FORBIDDEN", reason: "Extension context required" };
  }
  const requested = String(message?.mode || "normal").trim().toLowerCase();
  const mode = NSFW_CLASSIFIER_TEST_MODES.has(requested) ? requested : "normal";
  nsfwClassifierTestOverride = mode;
  nsfwClassifierReadyLogSignature = "";
  if (mode === "off") {
    if (await hasNsfwOffscreenDocument()) {
      await sendNsfwOffscreenMessage({ type: "OFFSCREEN_NSFW_SET_TEST_OVERRIDE", mode });
      await closeNsfwOffscreenDocument();
    }
    return { ok: true, status: "disabled", testOverride: mode };
  }
  const response = await sendNsfwOffscreenMessage({
    type: "OFFSCREEN_NSFW_SET_TEST_OVERRIDE",
    mode
  });
  return { ...response, testOverride: mode };
}

chrome.runtime.onInstalled.addListener(() => {
  FULL_ANALYSIS_RESPONSE_CACHE.clear();
  FULL_ANALYSIS_IN_FLIGHT_REQUESTS.clear();
  SITE_POLICY_CACHE.clear();
  SITE_POLICY_IN_FLIGHT.clear();
  SITE_POLICY_BY_TAB.clear();
  ensureWellbeingUsageAlarm();
  ensureSettings()
    .then((settings) => syncNsfwClassifierLifecycle(settings))
    .catch((error) => {
      console.error("[청마루] ensureSettings(onInstalled) failed", error);
    });
});

chrome.runtime.onStartup.addListener(() => {
  FULL_ANALYSIS_RESPONSE_CACHE.clear();
  FULL_ANALYSIS_IN_FLIGHT_REQUESTS.clear();
  SITE_POLICY_CACHE.clear();
  SITE_POLICY_IN_FLIGHT.clear();
  SITE_POLICY_BY_TAB.clear();
  ensureWellbeingUsageAlarm();
  ensureSettings()
    .then((settings) => syncNsfwClassifierLifecycle(settings))
    .catch((error) => {
      console.error("[청마루] ensureSettings(onStartup) failed", error);
    });
});

ensureWellbeingUsageAlarm();

chrome.storage.onChanged.addListener((changes, areaName) => {
  if (areaName === "local" && changes?.[DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY]) {
    developerRuntimeLogEnabledCache =
      changes[DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY].newValue === true;
    developerRuntimeLogStateLoaded = true;
    developerRuntimeLogStateInFlight = null;
    return;
  }

  if (areaName !== "sync" || !changes?.settings) {
    return;
  }

  settingsCache = mergeSettings(changes.settings.newValue || {});
  settingsCacheExpiresAt = Date.now() + SETTINGS_CACHE_TTL_MS;
  settingsInFlight = null;

  FULL_ANALYSIS_RESPONSE_CACHE.clear();
  FULL_ANALYSIS_IN_FLIGHT_REQUESTS.clear();
  SITE_POLICY_CACHE.clear();
  SITE_POLICY_IN_FLIGHT.clear();
  SITE_POLICY_BY_TAB.clear();
  backendHealthCache = null;
  backendHealthInFlight = null;

  const changedKeys = getChangedSettingsKeys(changes.settings.oldValue || {}, changes.settings.newValue || {});
  if (changedKeys.length) {
    recordRuntimeLogEvent({
      type: "settings-changed",
      ok: true,
      status: "cache-cleared",
      source: "storage",
      count: changedKeys.length,
      message: changedKeys.join(", "),
      reason: summarizeSettingsForRuntimeLog(changes.settings.newValue || {})
    });
    if (changedKeys.includes("mediaSafetyEnabled")) {
      const enabled = mergeSettings(changes.settings.newValue || {}).mediaSafetyEnabled === true;
      recordRuntimeLogEvent({
        type: "media-safety-scan",
        ok: true,
        status: enabled ? "enabled" : "disabled",
        source: "settings",
        count: 0,
        candidateCount: 0,
        visibleTileCount: 0,
        actionCount: 0,
        reason: "media safety setting changed"
      });
      syncNsfwClassifierLifecycle(settingsCache).catch((error) => {
        recordRuntimeLogEvent({
          type: "media-safety-classifier-error",
          ok: false,
          status: "lifecycle-failed",
          source: "settings",
          errorCode: String(error?.errorCode || error?.message || "NSFW_LIFECYCLE_FAILED").slice(0, 80),
          reason: String(error?.message || error || "NSFW lifecycle failed").slice(0, 220)
        });
      });
    }
  }
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  const nextUrl = String(changeInfo.url || tab?.url || "");
  if (!nextUrl || !isHttpPageUrl(nextUrl) || isUnsupportedTabUrl(nextUrl)) {
    return;
  }

  if (changeInfo.url) {
    prefetchSitePolicyForTab(tabId, nextUrl).catch((error) => {
      console.warn("[청마루] prefetchSitePolicyForTab failed", error);
    });
  }

  if (changeInfo.url || changeInfo.status === "loading") {
    if (tab?.active) {
      recordActiveBrowsingSample({ ...tab, id: tabId, url: nextUrl }).catch((error) => {
        console.warn("[청마루] wellbeing tab update sample failed", error);
      });
    }
  }
});

if (chrome.webNavigation?.onBeforeNavigate) {
  chrome.webNavigation.onBeforeNavigate.addListener(
    (details) => {
      handleSiteNavigationWarning(details).catch((error) => {
        console.warn("[청마루] site navigation warning failed", error);
      });
    },
    {
      url: [{ schemes: ["http"] }, { schemes: ["https"] }]
    }
  );
}

chrome.tabs.onActivated.addListener(async ({ tabId }) => {
  try {
    const tab = await chrome.tabs.get(tabId);
    if (tab?.url && isHttpPageUrl(tab.url) && !isUnsupportedTabUrl(tab.url)) {
      await prefetchSitePolicyForTab(tabId, tab.url);
      await recordActiveBrowsingSample(tab);
    }
  } catch (error) {
    console.warn("[청마루] tabs.onActivated handling failed", error);
  }
});

if (chrome.windows?.onFocusChanged) {
  chrome.windows.onFocusChanged.addListener((windowId) => {
    if (windowId === chrome.windows.WINDOW_ID_NONE) {
      return;
    }
    recordActiveBrowsingSample().catch((error) => {
      console.warn("[청마루] wellbeing focus sample failed", error);
    });
  });
}

if (chrome.alarms?.onAlarm) {
  chrome.alarms.onAlarm.addListener((alarm) => {
    if (alarm?.name !== WELLBEING_USAGE_ALARM_NAME) {
      return;
    }
    recordActiveBrowsingSample().catch((error) => {
      console.warn("[청마루] wellbeing alarm sample failed", error);
    });
  });
}

chrome.tabs.onRemoved.addListener((tabId) => {
  SITE_POLICY_BY_TAB.delete(tabId);
  for (const key of SITE_WARNING_ALLOWED_NAVIGATIONS.keys()) {
    if (key.startsWith(`${tabId}:`)) {
      SITE_WARNING_ALLOWED_NAVIGATIONS.delete(key);
    }
  }
  updateWellbeingState((state) => {
    delete state.lastHeartbeatByTab[String(tabId)];
  }).catch((error) => {
    console.warn("[청마루] wellbeing tab cleanup failed", error);
  });
});

function sendAsyncRuntimeResponse(promise, sendResponse, fallbackCode = "RUNTIME_MESSAGE_FAILED") {
  Promise.resolve(promise)
    .then((response) => {
      sendResponse(response ?? { ok: true });
    })
    .catch((error) => {
      const normalized = normalizeBackendError(error, fallbackCode);
      sendResponse({
        ok: false,
        reason: normalized.reason,
        errorCode: normalized.errorCode,
        retryable: Boolean(normalized.retryable)
      });
    });
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type === "GET_DEFAULT_SETTINGS") {
    sendResponse({ ok: true, defaults: DEFAULT_SETTINGS });
    return true;
  }

  if (message?.type === "RUN_PIPELINE_ON_ACTIVE_TAB" || message?.type === "APPLY_FILTER_TO_ACTIVE_TAB") {
    sendAsyncRuntimeResponse(runPipelineOnActiveTab(), sendResponse, "RUN_PIPELINE_ON_ACTIVE_TAB_FAILED");
    return true;
  }

  if (message?.type === "RUN_SELF_TEST_ON_ACTIVE_TAB") {
    sendAsyncRuntimeResponse(runSelfTestOnActiveTab(), sendResponse, "RUN_SELF_TEST_ON_ACTIVE_TAB_FAILED");
    return true;
  }

  if (message?.type === "GET_LAST_PIPELINE_STATE") {
    sendAsyncRuntimeResponse(getLastPipelineState(), sendResponse, "GET_LAST_PIPELINE_STATE_FAILED");
    return true;
  }

  if (message?.type === "GET_RUNTIME_EVENT_LOGS") {
    sendAsyncRuntimeResponse(
      getRuntimeEventLogs(message?.limit).then((logs) => ({ ok: true, logs })),
      sendResponse,
      "GET_RUNTIME_EVENT_LOGS_FAILED"
    );
    return true;
  }

  if (message?.type === "CLEAR_RUNTIME_EVENT_LOGS") {
    sendAsyncRuntimeResponse(
      clearRuntimeEventLogs().then(() => ({ ok: true })),
      sendResponse,
      "CLEAR_RUNTIME_EVENT_LOGS_FAILED"
    );
    return true;
  }

  if (message?.type === "ADD_RUNTIME_EVENT_LOG") {
    sendAsyncRuntimeResponse(
      addRuntimeEventLogFromMessage(message),
      sendResponse,
      "ADD_RUNTIME_EVENT_LOG_FAILED"
    );
    return true;
  }

  if (message?.type === "WARMUP_NSFW_CLASSIFIER") {
    sendAsyncRuntimeResponse(
      warmNsfwClassifier({ source: "content-script" }),
      sendResponse,
      "NSFW_MODEL_LOAD_FAILED"
    );
    return true;
  }

  if (message?.type === "CLASSIFY_NSFW_IMAGE_BATCH") {
    sendAsyncRuntimeResponse(
      classifyNsfwImageBatch(message, sender),
      sendResponse,
      "NSFW_CLASSIFIER_FAILED"
    );
    return true;
  }

  if (message?.type === "GET_NSFW_CLASSIFIER_STATUS") {
    sendAsyncRuntimeResponse(
      getNsfwClassifierStatus(),
      sendResponse,
      "NSFW_STATUS_FAILED"
    );
    return true;
  }

  if (message?.type === "SET_NSFW_CLASSIFIER_TEST_OVERRIDE") {
    sendAsyncRuntimeResponse(
      setNsfwClassifierTestOverride(message, sender),
      sendResponse,
      "NSFW_TEST_OVERRIDE_FAILED"
    );
    return true;
  }

  if (message?.type === "ANALYZE_TEXT_BATCH") {
    sendAsyncRuntimeResponse(analyzeTextBatch(message), sendResponse, "ANALYZE_TEXT_BATCH_FAILED");
    return true;
  }

  if (message?.type === "WARMUP_BACKEND_MODELS") {
    sendAsyncRuntimeResponse(warmupBackendModels(message), sendResponse, "BACKEND_WARMUP_FAILED");
    return true;
  }

  if (message?.type === "CHECK_API_HEALTH") {
    sendAsyncRuntimeResponse(checkApiHealth(message), sendResponse, "CHECK_API_HEALTH_FAILED");
    return true;
  }

  if (message?.type === "WELLBEING_HEARTBEAT") {
    sendAsyncRuntimeResponse(
      recordWellbeingHeartbeat(message, sender),
      sendResponse,
      "WELLBEING_HEARTBEAT_FAILED"
    );
    return true;
  }

  if (message?.type === "RECORD_WELLBEING_DETECTION") {
    sendAsyncRuntimeResponse(
      recordWellbeingDetection(message, sender),
      sendResponse,
      "RECORD_WELLBEING_DETECTION_FAILED"
    );
    return true;
  }

  if (message?.type === "GET_WELLBEING_STATE_FOR_URL") {
    sendAsyncRuntimeResponse(
      getWellbeingViewForUrl(message?.url, sender),
      sendResponse,
      "GET_WELLBEING_STATE_FOR_URL_FAILED"
    );
    return true;
  }

  if (message?.type === "WELLBEING_WIDGET_LAYOUT_UPDATED") {
    sendAsyncRuntimeResponse(
      broadcastWellbeingWidgetLayout(message.layout, sender?.tab?.id || 0).then(() => ({ ok: true })),
      sendResponse,
      "WELLBEING_WIDGET_LAYOUT_UPDATE_FAILED"
    );
    return true;
  }

  if (message?.type === "SET_WELLBEING_DEBUG_OVERRIDE") {
    sendAsyncRuntimeResponse(
      setWellbeingDebugOverride(message?.override).then((override) => ({ ok: true, override })),
      sendResponse,
      "SET_WELLBEING_DEBUG_OVERRIDE_FAILED"
    );
    return true;
  }

  if (message?.type === "CLEAR_WELLBEING_DEBUG_OVERRIDE") {
    sendAsyncRuntimeResponse(
      clearWellbeingDebugOverride().then(() => ({ ok: true })),
      sendResponse,
      "CLEAR_WELLBEING_DEBUG_OVERRIDE_FAILED"
    );
    return true;
  }

  if (message?.type === "CLEAR_WELLBEING_STATE") {
    sendAsyncRuntimeResponse(
      clearWellbeingState().then(() => ({ ok: true })),
      sendResponse,
      "CLEAR_WELLBEING_STATE_FAILED"
    );
    return true;
  }

  if (message?.type === "GET_SITE_WARNING_PAYLOAD") {
    sendAsyncRuntimeResponse(
      readSiteWarningPayload(message?.warningId).then((payload) => ({ ok: Boolean(payload), payload })),
      sendResponse,
      "GET_SITE_WARNING_PAYLOAD_FAILED"
    );
    return true;
  }

  if (message?.type === "ALLOW_SITE_WARNING_AND_CONTINUE") {
    sendAsyncRuntimeResponse(
      allowSiteWarningAndContinue(message, sender),
      sendResponse,
      "ALLOW_SITE_WARNING_AND_CONTINUE_FAILED"
    );
    return true;
  }

  if (message?.type === "GET_SITE_POLICY_FOR_URL") {
    const senderTabId = sender?.tab?.id;
    const requestedUrl = String(message?.url || sender?.tab?.url || "");
    const dismissed = isSiteNavigationAllowed(senderTabId, requestedUrl);
    const shouldStoreTabPolicy = senderTabId && message?.context !== "search-result";
    if (shouldStoreTabPolicy) {
      const current = SITE_POLICY_BY_TAB.get(senderTabId);
      if (current?.url === requestedUrl && current.policy) {
        sendResponse({
          ok: true,
          source: current.source || "tab-cache",
          dismissed: dismissed || Boolean(current.dismissed),
          policy: current.policy
        });
        return true;
      }
    }
    getSitePolicyForUrl(requestedUrl, {
      title: message?.title || "",
      snippet: message?.snippet || "",
      forceRefresh: Boolean(message?.forceRefresh),
      context: message?.context || ""
    })
      .then((result) => {
        if (message?.context === "options-policy-test") {
          recordRuntimeLogEvent({
            type: "site-policy-test",
            ok: Boolean(result?.ok),
            status: result?.policy ? "ready" : "empty",
            source: result?.source || "unknown",
            domain: result?.policy?.domain || domainFromUrl(requestedUrl),
            url: requestedUrl,
            verdict: result?.policy?.verdict || "",
            errorCode: result?.errorCode || "",
            reason: result?.reason || (
              Array.isArray(result?.policy?.reasons) ? result.policy.reasons.join(" / ") : ""
            )
          });
        }
        if (shouldStoreTabPolicy) {
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
          dismissed,
          policy: result?.policy || null
        });
      })
      .catch((error) => {
        const normalized = normalizeBackendError(error, "SITE_POLICY_MESSAGE_FAILED");
        sendResponse({
          ok: false,
          source: "error",
          reason: normalized.reason,
          errorCode: normalized.errorCode,
          dismissed,
          policy: null
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
      markSiteNavigationAllowed(senderTabId, requestedUrl);
    }
    sendResponse({ ok: true });
    return true;
  }

  return false;
});
