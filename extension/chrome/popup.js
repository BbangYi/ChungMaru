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
  requestTimeoutMs: 10000
};

const POPUP_FAST_HEALTH_TIMEOUT_MS = 1000;
const POPUP_HEALTH_CACHE_TTL_MS = 30 * 1000;
const POPUP_HEALTH_STALE_TTL_MS = 5 * 60 * 1000;

const els = {
  shell: document.querySelector(".popup-shell"),
  enabledToggle: document.getElementById("enabledToggle"),
  currentDomain: document.getElementById("currentDomain"),
  protectionSummary: document.getElementById("protectionSummary"),
  siteVerdictBadge: document.getElementById("siteVerdictBadge"),
  todayUsage: document.getElementById("todayUsage"),
  siteDetections: document.getElementById("siteDetections"),
  widgetToggle: document.getElementById("widgetToggle"),
  textMaskingToggle: document.getElementById("textMaskingToggle"),
  siteProtectionToggle: document.getElementById("siteProtectionToggle"),
  mediaSafetyToggle: document.getElementById("mediaSafetyToggle"),
  backendStatusCard: document.getElementById("backendStatusCard"),
  backendStatusText: document.getElementById("backendStatusText"),
  backendStatusDetail: document.getElementById("backendStatusDetail"),
  backendRefreshButton: document.getElementById("backendRefreshButton"),
  sensitivityRange: document.getElementById("sensitivityRange"),
  sensitivityLabel: document.getElementById("sensitivityLabel"),
  sensitivityHint: document.getElementById("sensitivityHint"),
  catAbuse: document.getElementById("catAbuse"),
  catHate: document.getElementById("catHate"),
  catInsult: document.getElementById("catInsult"),
  statusMessage: document.getElementById("statusMessage"),
  applyNowButton: document.getElementById("applyNowButton"),
  openOptionsButton: document.getElementById("openOptionsButton")
};

let isRunningPipeline = false;
let canRunPipelineOnCurrentTab = true;
let applyButtonIdleLabel = "현재 페이지 검사";
let backendHealthRequestId = 0;
let lastRuntimeView = null;

function getModeInputs() {
  return [...document.querySelectorAll('input[name="mode"]')];
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
    mediaSafetyInterventionMode: stored?.mediaSafetyInterventionMode || DEFAULT_SETTINGS.mediaSafetyInterventionMode,
    mediaSafetyStartupGateEnabled: stored?.mediaSafetyStartupGateEnabled === true,
    categories: {
      ...DEFAULT_SETTINGS.categories,
      ...(stored?.categories || {})
    }
  };
}

function normalizeSensitivity(value) {
  const numberValue = Number(value);
  if (Number.isNaN(numberValue)) return DEFAULT_SETTINGS.sensitivity;
  return Math.max(0, Math.min(100, Math.round(numberValue)));
}

function normalizeInterventionMode(value) {
  const mode = String(value || DEFAULT_SETTINGS.interventionMode).trim();
  return ["mask", "blur", "hide", "remove"].includes(mode) ? mode : DEFAULT_SETTINGS.interventionMode;
}

function getSensitivityHint(value) {
  const sensitivity = normalizeSensitivity(value);
  if (sensitivity <= 0) return "꺼짐";
  if (sensitivity < 35) return "낮음";
  if (sensitivity < 75) return "보통";
  return "강함";
}

async function loadSettings() {
  const { settings } = await chrome.storage.sync.get("settings");
  return mergeSettings(settings || {});
}

async function saveSettings(settings) {
  await chrome.storage.sync.set({ settings });
}

function renderStatus(message) {
  els.statusMessage.textContent = message;
  if (!message) return;

  window.setTimeout(() => {
    if (els.statusMessage.textContent === message) {
      els.statusMessage.textContent = "";
    }
  }, 2000);
}

function formatUnexpectedError(error) {
  return String(error?.message || error || "unknown");
}

function setApplyButtonBusy(isBusy) {
  els.applyNowButton.disabled = isBusy;
  if (!isBusy && !canRunPipelineOnCurrentTab) {
    els.applyNowButton.disabled = true;
  }
  els.applyNowButton.textContent = isBusy ? "검사 중..." : applyButtonIdleLabel;
}

function mapRunFailureReason(reason, errorCode) {
  const code = String(errorCode || "");
  const value = String(reason || "");
  if (code === "UNSUPPORTED_TAB" || value.includes("UNSUPPORTED_TAB")) {
    return "지원되지 않는 탭입니다";
  }
  if (code === "ACTIVE_TAB_NOT_FOUND" || value.includes("ACTIVE_TAB_NOT_FOUND")) {
    return "현재 탭을 찾지 못했습니다";
  }
  if (value.includes("Cannot access contents of url")) {
    return "크롬 정책상 접근할 수 없는 페이지입니다";
  }
  if (code === "HTTP_503" || value.includes("HTTP_503")) {
    return "백엔드 모델이 아직 준비되지 않았습니다";
  }
  if (code === "NETWORK_UNREACHABLE") {
    return "백엔드 서버에 연결할 수 없습니다";
  }
  if (code === "BACKEND_DISABLED") {
    return "백엔드 연동이 꺼져 있습니다";
  }
  if (code === "TIMEOUT") {
    return "백엔드 응답 시간이 초과되었습니다";
  }
  if (code === "ABORTED") {
    return "요청이 취소되었습니다";
  }
  return code || value || "unknown";
}

function safeParseHostname(url) {
  try {
    const hostname = new URL(url).hostname || "-";
    return hostname.startsWith("www.") ? hostname.slice(4) : hostname;
  } catch {
    return "-";
  }
}

function isSupportedHttpUrl(url) {
  return /^https?:/i.test(String(url || "")) && !getUnsupportedTabReason(url);
}

function getUnsupportedTabReason(url) {
  const value = String(url || "").trim();
  if (!value) {
    return "NO_ACTIVE_TAB";
  }
  if (!/^https?:/i.test(value)) {
    return "NON_HTTP_URL";
  }
  try {
    const parsed = new URL(value);
    const hostname = parsed.hostname.toLowerCase();
    if (
      hostname === "chrome.google.com" ||
      hostname === "chromewebstore.google.com" ||
      hostname.endsWith(".chromewebstore.google.com")
    ) {
      return "BROWSER_RESTRICTED_PAGE";
    }
  } catch {
    return "INVALID_URL";
  }
  return "";
}

function isSiteWarningUrl(url) {
  try {
    return String(url || "").startsWith(chrome.runtime.getURL("site-warning.html"));
  } catch {
    return false;
  }
}

async function getActiveTabInfo() {
  const tabs = await chrome.tabs.query({ active: true, currentWindow: true });
  const tab = tabs?.[0] || null;
  const url = tab?.url || "";
  const siteWarning = isSiteWarningUrl(url);
  return {
    id: tab?.id || null,
    url,
    hostname: siteWarning ? "청마루 경고 화면" : url ? safeParseHostname(url) : "-",
    supported: isSupportedHttpUrl(url),
    unsupportedReason: getUnsupportedTabReason(url),
    siteWarning
  };
}

function formatDuration(totalMs) {
  const normalizedMs = Math.max(0, Number(totalMs || 0));
  if (normalizedMs > 0 && normalizedMs < 60000) return "1분 미만";
  const totalMinutes = Math.max(0, Math.floor(normalizedMs / 60000));
  if (totalMinutes < 60) return `${totalMinutes}분`;
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return minutes ? `${hours}시간 ${minutes}분` : `${hours}시간`;
}

function isBackendHealthy(health) {
  return Boolean(
    health?.ok &&
      (health.backendStatus === "ready" || health.backendStatus === "slow") &&
      health.model_ready !== false
  );
}

function isBackendDisabledHealth(health) {
  return health?.backendStatus === "disabled" || health?.errorCode === "BACKEND_DISABLED";
}

function getBackendHealthStatus(health) {
  if (!health) return "checking";
  if (isBackendDisabledHealth(health)) {
    return "disabled";
  }
  if (isBackendHealthy(health)) {
    return health?.backendStatus === "slow" || health?.slow ? "slow" : "ready";
  }

  const errorCode = String(health?.errorCode || "");
  if (health?.model_ready === false || health?.backendStatus === "model-not-ready") {
    return "model";
  }
  if (errorCode === "NETWORK_UNREACHABLE") {
    return "offline";
  }
  if (errorCode === "TIMEOUT" || errorCode === "ABORTED") {
    return "timeout";
  }
  return "degraded";
}

function formatBackendHealthText(health) {
  const status = getBackendHealthStatus(health);
  if (status === "ready") return "연결됨";
  if (status === "slow") return "응답 지연";
  if (status === "offline") return "서버 꺼짐";
  if (status === "timeout") return "응답 지연";
  if (status === "model") return "모델 준비 중";
  if (status === "disabled") return "꺼짐";
  if (status === "checking") return "확인 중";
  return "연결 확인 필요";
}

function formatBackendHealthMeta(health) {
  if (!health) return "";
  if (health.refreshing) return " · 갱신 중";
  if (health.cacheHit) return health.stale ? " · 최근 상태" : " · 캐시";

  const durationMs = Math.round(Number(health.durationMs || 0));
  if (durationMs > 0) return ` · ${durationMs}ms`;
  return "";
}

function formatBackendHealthDetail(health, settings) {
  const apiBaseUrl = String(health?.apiBaseUrl || settings?.backendApiBaseUrl || "").trim();
  const status = getBackendHealthStatus(health);
  const meta = formatBackendHealthMeta(health);
  if (status === "checking") {
    return apiBaseUrl || "-";
  }

  const errorCode = String(health?.errorCode || "");
  if (status === "disabled") {
    return "개발자 모드에서만 분석 서버를 사용합니다";
  }
  if (status === "offline") {
    return `${apiBaseUrl || "API 주소"} · 백엔드를 실행해 주세요${meta}`;
  }
  if (status === "slow") {
    return `${apiBaseUrl || "API 주소"} · 느리지만 연결됨${meta}`;
  }
  if (status === "timeout") {
    return `${apiBaseUrl || "API 주소"} · 타임아웃${meta}`;
  }
  if (status === "model") {
    return `${apiBaseUrl || "API 주소"} · 모델 로딩/파일 확인${meta}`;
  }
  if (status === "degraded") {
    return `${apiBaseUrl || "API 주소"} · ${errorCode || health?.reason || "확인 실패"}${meta}`;
  }
  return `${apiBaseUrl || "정상 응답"}${meta}`;
}

function renderBackendHealth(settings, health) {
  const status = getBackendHealthStatus(health);
  els.backendStatusCard.dataset.status = status;
  els.backendStatusText.textContent = formatBackendHealthText(health);
  els.backendStatusDetail.textContent = formatBackendHealthDetail(health, settings);
  els.backendStatusDetail.title = els.backendStatusDetail.textContent;
}

function getUnsupportedTabSummary(reason) {
  const value = String(reason || "");
  if (value === "BROWSER_RESTRICTED_PAGE") {
    return "브라우저 제한 탭입니다";
  }
  if (value === "NON_HTTP_URL") {
    return "웹페이지가 아닙니다";
  }
  if (value === "NO_ACTIVE_TAB") {
    return "현재 탭 없음";
  }
  return "지원되지 않는 탭입니다";
}

function getCurrentPageDetectionSummary(wellbeing) {
  if (wellbeing?.protectionEnabled === false) {
    return {
      count: 0,
      label: "0개"
    };
  }

  const page = wellbeing?.currentPage || wellbeing?.currentSite || {};
  const profanity = Number(page.profanityNodeCount || 0);
  const harmful = Number(
    page.expressionCount ||
      Math.max(
        Number(page.maskedSpanCount || 0),
        Number(page.blockedNodeCount || 0),
        Number(page.toxicNodeCount || 0),
        Number(page.hateNodeCount || 0)
      )
  );
  if (profanity > 0) {
    return {
      count: Math.max(0, Math.round(profanity)),
      label: `욕설 ${Math.max(0, Math.round(profanity))}개`
    };
  }
  if (harmful > 0) {
    return {
      count: Math.max(0, Math.round(harmful)),
      label: `유해표현 ${Math.max(0, Math.round(harmful))}개`
    };
  }
  return {
    count: 0,
    label: "0개"
  };
}

function buildProtectionState(settings, state, health, wellbeing) {
  if (state?.siteWarningTab) {
    return {
      key: "warning",
      summary: "접속 전 경고를 표시 중입니다",
      badge: "경고"
    };
  }

  if (state?.unsupportedTab) {
    return {
      key: "unsupported",
      summary: getUnsupportedTabSummary(state.unsupportedReason),
      badge: "제외"
    };
  }

  if (settings.enabled === false || normalizeSensitivity(settings.sensitivity) <= 0) {
    return {
      key: "off",
      summary: "필터가 꺼져 있습니다",
      badge: "꺼짐"
    };
  }

  if (wellbeing?.policyVerdict === "block") {
    return {
      key: "blocked",
      summary: "차단이 필요한 사이트입니다",
      badge: "차단"
    };
  }

  if (wellbeing?.policyVerdict === "warning") {
    return {
      key: "warning",
      summary: "주의가 필요한 사이트입니다",
      badge: "주의"
    };
  }

  if (health && !isBackendDisabledHealth(health) && !isBackendHealthy(health)) {
    return {
      key: "degraded",
      summary: formatBackendHealthText(health),
      badge: "점검"
    };
  }

  const detectionSummary = getCurrentPageDetectionSummary(wellbeing);
  if (detectionSummary.count > 0) {
    return {
      key: "detected",
      summary: "유해 표현을 감지했습니다",
      badge: detectionSummary.label
    };
  }

  return {
    key: "ready",
    summary: "보호 중입니다",
    badge: "안전"
  };
}

async function notifyActiveTabSettingsSnapshot(settings) {
  const tab = await getActiveTabInfo();
  if (!tab.id) return { ok: false, reason: "ACTIVE_TAB_NOT_FOUND" };
  if (!tab.supported) return { ok: false, reason: "UNSUPPORTED_TAB", skipped: true };

  try {
    return await chrome.tabs.sendMessage(tab.id, {
      type: "APPLY_SETTINGS_SNAPSHOT",
      settings
    });
  } catch (error) {
    const message = String(error?.message || error || "");
    if (message.includes("Receiving end does not exist")) {
      return { ok: false, reason: "CONTENT_SCRIPT_NOT_READY" };
    }
    return { ok: false, reason: message || "SETTINGS_SNAPSHOT_FAILED" };
  }
}

async function persistAndApplySettings(settings, statusMessage) {
  try {
    await saveSettings(settings);
    if (statusMessage) {
      renderStatus(statusMessage);
    }

    const snapshotResult = await notifyActiveTabSettingsSnapshot(settings);
    if (!snapshotResult?.ok) {
      if (
        settings.enabled === false ||
        normalizeSensitivity(settings.sensitivity) <= 0 ||
        snapshotResult?.reason === "UNSUPPORTED_TAB" ||
        snapshotResult?.reason === "ACTIVE_TAB_NOT_FOUND"
      ) {
        await refreshRuntimeState(settings);
        return {
          ok: true,
          appliedBy: "settings-only",
          reason: snapshotResult.reason
        };
      }
      await runPipelineNow();
      return {
        ok: true,
        appliedBy: "pipeline-fallback",
        reason: snapshotResult?.reason || "SNAPSHOT_FAILED"
      };
    }

    await refreshRuntimeState(settings);
    return {
      ok: true,
      appliedBy: "settings-snapshot"
    };
  } catch (error) {
    const reason = formatUnexpectedError(error);
    renderStatus(`저장 실패: ${reason}`);
    return {
      ok: false,
      reason
    };
  }
}

async function loadWellbeingView(tab) {
  if (!tab?.url || !/^https?:/i.test(tab.url)) {
    return null;
  }

  const response = await chrome.runtime.sendMessage({
    type: "GET_WELLBEING_STATE_FOR_URL",
    url: tab.url
  });
  return response?.view || null;
}

function renderRuntimeState(settings, state, tab, health, wellbeing) {
  lastRuntimeView = {
    settings,
    state: state?.ok ? state : state || null,
    tab,
    wellbeing
  };

  const detectionSummary = getCurrentPageDetectionSummary(wellbeing);
  const normalizedState = tab?.siteWarning
    ? { ...(state || {}), siteWarningTab: true }
    : tab?.supported === false
      ? { ...(state || {}), unsupportedTab: true, unsupportedReason: tab.unsupportedReason }
      : state;
  const protection = buildProtectionState(settings, normalizedState, health, wellbeing);
  const filteringEnabled = settings.enabled !== false && normalizeSensitivity(settings.sensitivity) > 0;

  canRunPipelineOnCurrentTab = Boolean(tab?.supported && filteringEnabled && !tab?.siteWarning);
  applyButtonIdleLabel = tab?.siteWarning
    ? "경고 화면"
    : tab?.supported === false
    ? "검사 불가"
    : filteringEnabled
      ? "현재 페이지 검사"
      : "보호 꺼짐";

  els.shell.dataset.state = protection.key;
  els.currentDomain.textContent = tab?.siteWarning
    ? "청마루 경고 화면"
    : tab?.supported === false
    ? tab.hostname || "지원되지 않는 탭"
    : wellbeing?.domain || tab?.hostname || "-";
  els.protectionSummary.textContent = protection.summary;
  els.siteVerdictBadge.textContent = protection.badge;
  els.todayUsage.textContent = formatDuration(wellbeing?.totalActiveMs || 0);
  els.siteDetections.textContent = detectionSummary.label;
  renderBackendHealth(settings, health);
  els.sensitivityLabel.textContent = String(normalizeSensitivity(settings.sensitivity));
  els.sensitivityHint.textContent = getSensitivityHint(settings.sensitivity);
  setApplyButtonBusy(isRunningPipeline);
}

function buildHealthCheckFailure(error, settings) {
  return {
    ok: false,
    backendStatus: "degraded",
    errorCode: "HEALTH_CHECK_FAILED",
    reason: formatUnexpectedError(error),
    apiBaseUrl: settings?.backendApiBaseUrl || ""
  };
}

function renderBackendHealthInRuntime(settings, health) {
  if (lastRuntimeView) {
    renderRuntimeState(
      settings || lastRuntimeView.settings,
      lastRuntimeView.state,
      lastRuntimeView.tab,
      health,
      lastRuntimeView.wellbeing
    );
    return;
  }

  renderBackendHealth(settings, health);
}

function requestBackendHealth(settings, options = {}) {
  const fast = options.fast !== false;
  if (settings?.backendEnabled !== true) {
    return Promise.resolve({
      ok: false,
      backendStatus: "disabled",
      errorCode: "BACKEND_DISABLED",
      reason: "백엔드 연동이 꺼져 있습니다.",
      apiBaseUrl: settings?.backendApiBaseUrl || "",
      durationMs: 0
    });
  }

  return chrome.runtime.sendMessage({
    type: "CHECK_API_HEALTH",
    requestTimeoutMs: fast ? POPUP_FAST_HEALTH_TIMEOUT_MS : settings.requestTimeoutMs,
    softTimeoutMs: fast ? POPUP_FAST_HEALTH_TIMEOUT_MS : undefined,
    retryOnTimeout: fast ? false : true,
    allowCached: options.forceRefresh ? false : true,
    forceRefresh: Boolean(options.forceRefresh),
    staleOk: fast,
    cacheTtlMs: POPUP_HEALTH_CACHE_TTL_MS,
    staleTtlMs: POPUP_HEALTH_STALE_TTL_MS,
    suppressErrorLog: fast
  });
}

async function refreshRuntimeState(preloadedSettings = null) {
  const settings = preloadedSettings || await loadSettings();
  const tab = await getActiveTabInfo().catch(() => ({ url: "", hostname: "-" }));
  const [state, wellbeing] = await Promise.all([
    chrome.runtime.sendMessage({ type: "GET_LAST_PIPELINE_STATE" }).catch(() => null),
    loadWellbeingView(tab).catch(() => null)
  ]);

  renderRuntimeState(settings, state?.ok ? state : null, tab, null, wellbeing);
  refreshBackendHealthOnly({ settings, quiet: true, fast: true }).catch(() => {});
}

async function refreshBackendHealthOnly(options = {}) {
  const settings = options.settings || await loadSettings();
  const requestId = ++backendHealthRequestId;
  const quiet = Boolean(options.quiet);
  const fast = options.fast !== false;

  if (!quiet) {
    els.backendRefreshButton.disabled = true;
  }
  els.backendStatusCard.dataset.status = "checking";
  els.backendStatusText.textContent = "확인 중";
  els.backendStatusDetail.textContent = settings.backendApiBaseUrl || "-";

  try {
    const health = await requestBackendHealth(settings, {
      fast,
      forceRefresh: options.forceRefresh
    })
      .catch((error) => buildHealthCheckFailure(error, settings));
    if (requestId !== backendHealthRequestId) return;

    renderBackendHealthInRuntime(settings, health);
    if (!quiet) {
      renderStatus(isBackendHealthy(health) ? "분석 서버 연결됨" : formatBackendHealthDetail(health, settings));
    }
  } finally {
    if (requestId === backendHealthRequestId) {
      els.backendRefreshButton.disabled = false;
    }
  }
}

function bindMode(settings) {
  for (const input of getModeInputs()) {
    input.checked = input.value === normalizeInterventionMode(settings.interventionMode);
    input.addEventListener("change", async () => {
      settings.interventionMode = normalizeInterventionMode(input.value);
      await persistAndApplySettings(settings, "처리 방식 저장됨");
    });
  }
}

function setSiteProtectionBundle(settings, enabled) {
  settings.siteProtectionEnabled = enabled;
  settings.siteNavigationWarningEnabled = enabled;
  settings.searchResultProtectionEnabled = enabled;
}

function renderFeatureToggles(settings) {
  if (els.textMaskingToggle) {
    els.textMaskingToggle.checked = settings.textMaskingEnabled !== false;
  }
  if (els.siteProtectionToggle) {
    els.siteProtectionToggle.checked = settings.siteProtectionEnabled !== false;
  }
  if (els.mediaSafetyToggle) {
    els.mediaSafetyToggle.checked = settings.mediaSafetyEnabled === true;
  }
}

async function runPipelineNow() {
  if (isRunningPipeline) return;
  if (!canRunPipelineOnCurrentTab) {
    renderStatus(
      applyButtonIdleLabel === "검사 불가"
        ? "지원되지 않는 탭입니다"
        : applyButtonIdleLabel === "경고 화면"
          ? "경고 화면에서는 검사가 필요하지 않습니다"
          : "보호가 꺼져 있습니다"
    );
    return;
  }
  isRunningPipeline = true;
  setApplyButtonBusy(true);

  try {
    const response = await chrome.runtime.sendMessage({
      type: "RUN_PIPELINE_ON_ACTIVE_TAB"
    });

    if (!response?.ok) {
      renderStatus(`검사 실패: ${mapRunFailureReason(response?.reason, response?.errorCode)}`);
      return;
    }

    if (response.contentResult?.ok === false) {
      renderStatus(
        `검사 실패: ${mapRunFailureReason(
          response.contentResult.reason,
          response.contentResult.errorCode
        )}`
      );
      await refreshRuntimeState();
      return;
    }

    renderStatus("현재 페이지 검사 완료");
    await refreshRuntimeState();
  } finally {
    isRunningPipeline = false;
    setApplyButtonBusy(false);
  }
}

async function initialize() {
  let settings = await loadSettings();
  let sensitivitySaveTimerId = null;

  els.enabledToggle.checked = settings.enabled !== false;
  els.widgetToggle.checked = settings.showWellbeingWidget !== false;
  renderFeatureToggles(settings);
  els.sensitivityRange.value = normalizeSensitivity(settings.sensitivity);
  els.catAbuse.checked = settings.categories.abuse !== false;
  els.catHate.checked = settings.categories.hate !== false;
  els.catInsult.checked = settings.categories.insult !== false;

  bindMode(settings);
  renderRuntimeState(settings, null, { hostname: "-" }, null, null);
  await refreshRuntimeState(settings);

  els.backendRefreshButton.addEventListener("click", () => {
    refreshBackendHealthOnly({ forceRefresh: true, fast: false }).catch((error) => {
      renderStatus(`연결 확인 실패: ${formatUnexpectedError(error)}`);
      els.backendRefreshButton.disabled = false;
    });
  });

  els.enabledToggle.addEventListener("change", async () => {
    settings.enabled = els.enabledToggle.checked;
    await persistAndApplySettings(settings, settings.enabled ? "보호를 켰습니다" : "보호를 껐습니다");
  });

  els.widgetToggle.addEventListener("change", async () => {
    settings.showWellbeingWidget = els.widgetToggle.checked;
    await saveSettings(settings);
    renderStatus(settings.showWellbeingWidget ? "화면 위젯을 켰습니다" : "화면 위젯을 껐습니다");
    await refreshRuntimeState(settings);
  });

  els.textMaskingToggle?.addEventListener("change", async () => {
    settings.textMaskingEnabled = els.textMaskingToggle.checked;
    await persistAndApplySettings(
      settings,
      settings.textMaskingEnabled ? "텍스트 마스킹을 켰습니다" : "텍스트 마스킹을 껐습니다"
    );
  });

  els.siteProtectionToggle?.addEventListener("change", async () => {
    setSiteProtectionBundle(settings, els.siteProtectionToggle.checked);
    await persistAndApplySettings(
      settings,
      settings.siteProtectionEnabled ? "유해 사이트 차단을 켰습니다" : "유해 사이트 차단을 껐습니다"
    );
  });

  els.mediaSafetyToggle?.addEventListener("change", async () => {
    settings.mediaSafetyEnabled = els.mediaSafetyToggle.checked;
    await persistAndApplySettings(
      settings,
      settings.mediaSafetyEnabled ? "유해 이미지 차단을 켰습니다" : "유해 이미지 차단을 껐습니다"
    );
  });

  async function persistSensitivityFromControl() {
    settings.sensitivity = normalizeSensitivity(els.sensitivityRange.value);
    await persistAndApplySettings(settings, "필터 강도 저장됨");
  }

  els.sensitivityRange.addEventListener("input", () => {
    settings.sensitivity = normalizeSensitivity(els.sensitivityRange.value);
    els.sensitivityLabel.textContent = String(settings.sensitivity);
    els.sensitivityHint.textContent = getSensitivityHint(settings.sensitivity);

    if (sensitivitySaveTimerId) {
      clearTimeout(sensitivitySaveTimerId);
      sensitivitySaveTimerId = null;
    }

    sensitivitySaveTimerId = setTimeout(() => {
      sensitivitySaveTimerId = null;
      persistSensitivityFromControl().catch((error) => {
        renderStatus(`저장 실패: ${error?.message || error}`);
      });
    }, 220);
  });

  els.sensitivityRange.addEventListener("change", async () => {
    if (sensitivitySaveTimerId) {
      clearTimeout(sensitivitySaveTimerId);
      sensitivitySaveTimerId = null;
    }
    await persistSensitivityFromControl();
  });

  const categoryInputs = [
    ["insult", els.catInsult],
    ["abuse", els.catAbuse],
    ["hate", els.catHate]
  ];

  for (const [key, input] of categoryInputs) {
    input.addEventListener("change", async () => {
      settings.categories[key] = input.checked;
      await persistAndApplySettings(settings, "감지 범위 저장됨");
    });
  }

  els.applyNowButton.addEventListener("click", () => {
    runPipelineNow().catch((error) => {
      console.error(error);
      renderStatus("현재 페이지 검사 실패");
    });
  });

  els.openOptionsButton.addEventListener("click", () => {
    chrome.runtime.openOptionsPage();
  });

  chrome.storage.onChanged.addListener((changes, areaName) => {
    if (areaName === "sync" && changes.settings?.newValue) {
      settings = mergeSettings(changes.settings.newValue);
      els.enabledToggle.checked = settings.enabled !== false;
      els.widgetToggle.checked = settings.showWellbeingWidget !== false;
      renderFeatureToggles(settings);
      els.sensitivityRange.value = normalizeSensitivity(settings.sensitivity);
      els.catAbuse.checked = settings.categories.abuse !== false;
      els.catHate.checked = settings.categories.hate !== false;
      els.catInsult.checked = settings.categories.insult !== false;
      for (const input of getModeInputs()) {
        input.checked = input.value === normalizeInterventionMode(settings.interventionMode);
      }
      refreshRuntimeState(settings).catch(() => {});
      return;
    }

    if (areaName !== "local") return;
    if (
      !changes.lastRunAt &&
      !changes.lastStats &&
      !changes.lastDecision &&
      !changes.lastPipelineError &&
      !changes.wellbeingState
    ) {
      return;
    }
    refreshRuntimeState().catch(() => {});
  });
}

initialize().catch((error) => {
  console.error(error);
  renderStatus("팝업 로드 실패");
});
