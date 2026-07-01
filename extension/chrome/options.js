const DEFAULT_SETTINGS = {
  customBlockWords: "",
  customAllowWords: "",
  blockedDomains: "",
  warnDomains: "",
  showReason: true,
  interventionMode: "mask",
  textMaskingEnabled: true,
  siteProtectionEnabled: true,
  siteNavigationWarningEnabled: true,
  searchResultProtectionEnabled: true,
  mediaSafetyEnabled: false,
  mediaSafetyInterventionMode: "auto",
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

const AVATAR_ASSET_STORAGE_KEY = "wellbeingAvatarImageAssets";
const DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY = "developerRuntimeLogEnabled";
const AVATAR_LOCAL_PREFIX = "local:";
const AVATAR_IMAGE_MAX_DIMENSION = 512;
const AVATAR_IMAGE_EXPORT_MIME = "image/webp";
const AVATAR_IMAGE_EXPORT_QUALITY = 0.82;
const AVATAR_IMAGE_STAGE_LIMIT = 10;
const AVATAR_MAPPING_SYNC_TEXT_LIMIT = 6000;
const AVATAR_IMAGE_KEYS = [
  "default",
  ...Array.from({ length: AVATAR_IMAGE_STAGE_LIMIT }, (_, index) => `age${index + 1}`),
  ...Array.from({ length: AVATAR_IMAGE_STAGE_LIMIT }, (_, index) => `anger${index + 1}`)
];
const SITE_POLICY_TEST_DEFAULT_URL = "https://adult-webtoon-plus.kr/";
const SMOKE_TEST_CHECKLIST = [
  "# 청마루 확장 smoke 체크리스트",
  "",
  "1. `chrome://extensions`에서 청마루 확장을 새로고침한다.",
  "2. 기본 상태에서 팝업의 분석 서버가 `꺼짐`으로 보이고 백엔드 호출 없이 동작하는지 확인한다.",
  "3. 상세 설정의 개발자 테스트 비밀번호 `chungmaru-dev`로 열고 `백엔드 연동 켜기`를 체크한 뒤 `연결 확인`을 누른다.",
  "4. 개발자 테스트의 `사이트 판정 확인`에 `https://adult-webtoon-plus.kr/`를 넣어 `block`이 나오는지 확인한다.",
  "5. 같은 도구에서 `https://dcinside.com/`을 넣어 `warning`이 나오는지 확인한다.",
  "6. 직접 `https://adult-webtoon-plus.kr/`로 이동했을 때 청마루 접속 전 경고 페이지가 먼저 뜨는지 확인한다.",
  "7. Google에서 `디시인사이드`를 검색했을 때 `dcinside.com` 결과 카드가 경고/흐림 처리되는지 확인한다.",
  "8. 위젯을 Off로 바꾸면 기존 페이지에서 사라지고, 다시 On으로 바꾸면 사용 시간이 1분 단위로 증가하는지 확인한다.",
  "9. `위젯 통계 초기화`를 누른 뒤 현재 페이지 위젯 사용 시간과 탐지 수가 초기화되는지 확인한다.",
  "10. 문제가 있으면 `최근 런타임 로그`에서 `Notion용 복사`를 눌러 재현 메모와 함께 남긴다."
].join("\n");

const els = {
  blockWords: document.getElementById("blockWords"),
  allowWords: document.getElementById("allowWords"),
  blockedDomains: document.getElementById("blockedDomains"),
  warnDomains: document.getElementById("warnDomains"),
  textMaskingEnabledToggle: document.getElementById("textMaskingEnabledToggle"),
  showReasonToggle: document.getElementById("showReasonToggle"),
  interventionModeSelect: document.getElementById("interventionModeSelect"),
  siteProtectionEnabledToggle: document.getElementById("siteProtectionEnabledToggle"),
  siteNavigationWarningEnabledToggle: document.getElementById("siteNavigationWarningEnabledToggle"),
  searchResultProtectionEnabledToggle: document.getElementById("searchResultProtectionEnabledToggle"),
  mediaSafetyEnabledToggle: document.getElementById("mediaSafetyEnabledToggle"),
  showWellbeingWidgetToggle: document.getElementById("showWellbeingWidgetToggle"),
  wellbeingWidgetStyle: document.getElementById("wellbeingWidgetStyle"),
  wellbeingAgeStageCount: document.getElementById("wellbeingAgeStageCount"),
  wellbeingAgeMinutesPerStage: document.getElementById("wellbeingAgeMinutesPerStage"),
  wellbeingAngerStageCount: document.getElementById("wellbeingAngerStageCount"),
  wellbeingAngerDetectionsPerStage: document.getElementById("wellbeingAngerDetectionsPerStage"),
  backendEnabledToggle: document.getElementById("backendEnabledToggle"),
  backendApiBaseUrl: document.getElementById("backendApiBaseUrl"),
  requestTimeoutMs: document.getElementById("requestTimeoutMs"),
  checkConnectionButton: document.getElementById("checkConnectionButton"),
  connectionStatusText: document.getElementById("connectionStatusText"),
  runNowButton: document.getElementById("runNowButton"),
  saveOptionsButton: document.getElementById("saveOptionsButton"),
  refreshJsonButton: document.getElementById("refreshJsonButton"),
  runSelfTestButton: document.getElementById("runSelfTestButton"),
  payloadPreview: document.getElementById("payloadPreview"),
  decisionPreview: document.getElementById("decisionPreview"),
  runtimeLogPreview: document.getElementById("runtimeLogPreview"),
  refreshRuntimeLogButton: document.getElementById("refreshRuntimeLogButton"),
  copyRuntimeLogButton: document.getElementById("copyRuntimeLogButton"),
  copyRuntimeLogReportButton: document.getElementById("copyRuntimeLogReportButton"),
  clearRuntimeLogButton: document.getElementById("clearRuntimeLogButton"),
  runtimeManualNote: document.getElementById("runtimeManualNote"),
  addRuntimeNoteButton: document.getElementById("addRuntimeNoteButton"),
  selfTestPreview: document.getElementById("selfTestPreview"),
  selfTestHistoryPreview: document.getElementById("selfTestHistoryPreview"),
  optionsStatus: document.getElementById("optionsStatus"),
  statBlocked: document.getElementById("statBlocked"),
  statFalsePositive: document.getElementById("statFalsePositive"),
  statLatency: document.getElementById("statLatency"),
  statAnalyzed: document.getElementById("statAnalyzed"),
  diagFirstMask: document.getElementById("diagFirstMask"),
  diagHotPath: document.getElementById("diagHotPath"),
  diagHotPathState: document.getElementById("diagHotPathState"),
  diagHotPathError: document.getElementById("diagHotPathError"),
  diagWorkerInit: document.getElementById("diagWorkerInit"),
  diagBackendReconcile: document.getElementById("diagBackendReconcile"),
  diagMaskedSpans: document.getElementById("diagMaskedSpans"),
  diagWorkerCacheHit: document.getElementById("diagWorkerCacheHit"),
  diagBackendCacheHit: document.getElementById("diagBackendCacheHit"),
  diagVisibleBatch: document.getElementById("diagVisibleBatch"),
  diagReconcileQueue: document.getElementById("diagReconcileQueue"),
  diagDecisionSource: document.getElementById("diagDecisionSource"),
  diagnosticsPanel: document.getElementById("diagnosticsPanel"),
  developerPassword: document.getElementById("developerPassword"),
  unlockDeveloperButton: document.getElementById("unlockDeveloperButton"),
  developerLockSection: document.getElementById("developerLockSection"),
  developerToolsSection: document.getElementById("developerToolsSection"),
  developerRuntimeLogEnabledToggle: document.getElementById("developerRuntimeLogEnabledToggle"),
  mediaSafetyInterventionModeSelect: document.getElementById("mediaSafetyInterventionMode"),
  debugSimulatedHour: document.getElementById("debugSimulatedHour"),
  debugUsageMinutes: document.getElementById("debugUsageMinutes"),
  debugProfanityCount: document.getElementById("debugProfanityCount"),
  debugHarmfulCount: document.getElementById("debugHarmfulCount"),
  debugPolicyVerdict: document.getElementById("debugPolicyVerdict"),
  policyTestUrl: document.getElementById("policyTestUrl"),
  policyTestForceRefresh: document.getElementById("policyTestForceRefresh"),
  runPolicyTestButton: document.getElementById("runPolicyTestButton"),
  copySmokeChecklistButton: document.getElementById("copySmokeChecklistButton"),
  policyTestPreview: document.getElementById("policyTestPreview"),
  avatarImageSlot: document.getElementById("avatarImageSlot"),
  avatarPasteZone: document.getElementById("avatarPasteZone"),
  avatarPasteTargetText: document.getElementById("avatarPasteTargetText"),
  avatarImageFile: document.getElementById("avatarImageFile"),
  chooseAvatarImageButton: document.getElementById("chooseAvatarImageButton"),
  clearAvatarImageButton: document.getElementById("clearAvatarImageButton"),
  avatarImagePreviewList: document.getElementById("avatarImagePreviewList"),
  wellbeingAvatarImages: document.getElementById("wellbeingAvatarImages"),
  applyDebugOverrideButton: document.getElementById("applyDebugOverrideButton"),
  clearDebugOverrideButton: document.getElementById("clearDebugOverrideButton"),
  resetWellbeingStateButton: document.getElementById("resetWellbeingStateButton")
};

const DEVELOPER_MODE_PASSWORD = "chungmaru-dev";
let currentSettings = null;
let isRunningPipeline = false;
let isRunningSelfTest = false;
let currentRuntimeLogs = [];
let developerRuntimeLogEnabled = false;

const RUNTIME_LOG_TIME_FORMATTER = new Intl.DateTimeFormat("ko-KR", {
  dateStyle: "short",
  timeStyle: "medium",
  hour12: false
});

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
    showWellbeingWidget: stored?.showWellbeingWidget !== false,
    wellbeingAvatarImages: String(stored?.wellbeingAvatarImages || ""),
    wellbeingWidgetStyle: normalizeWidgetStyle(stored?.wellbeingWidgetStyle),
    wellbeingAgeStageCount: normalizeStageCount(
      stored?.wellbeingAgeStageCount,
      DEFAULT_SETTINGS.wellbeingAgeStageCount
    ),
    wellbeingAgeMinutesPerStage: normalizeStageStep(
      stored?.wellbeingAgeMinutesPerStage,
      DEFAULT_SETTINGS.wellbeingAgeMinutesPerStage,
      5,
      240
    ),
    wellbeingAngerStageCount: normalizeStageCount(
      stored?.wellbeingAngerStageCount,
      DEFAULT_SETTINGS.wellbeingAngerStageCount
    ),
    wellbeingAngerDetectionsPerStage: normalizeStageStep(
      stored?.wellbeingAngerDetectionsPerStage,
      DEFAULT_SETTINGS.wellbeingAngerDetectionsPerStage,
      1,
      50
    ),
    backendEnabled: stored?.backendEnabled === true,
    backendApiBaseUrl: sanitizeApiBaseUrl(stored?.backendApiBaseUrl),
    requestTimeoutMs: normalizeRequestTimeoutMs(stored?.requestTimeoutMs)
  };
}

function sanitizeApiBaseUrl(value) {
  const normalized = String(value || DEFAULT_SETTINGS.backendApiBaseUrl).trim();
  if (!normalized) return DEFAULT_SETTINGS.backendApiBaseUrl;
  return normalized.replace(/\/+$/, "");
}

function normalizeRequestTimeoutMs(value) {
  const numeric = Number(value);
  if (Number.isNaN(numeric)) return DEFAULT_SETTINGS.requestTimeoutMs;
  return Math.max(1000, Math.min(30000, Math.round(numeric)));
}

function normalizeWidgetStyle(value) {
  const style = String(value || DEFAULT_SETTINGS.wellbeingWidgetStyle).trim();
  return ["soft", "bold", "minimal"].includes(style) ? style : DEFAULT_SETTINGS.wellbeingWidgetStyle;
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

function normalizeStageCount(value, fallback = 5) {
  const numeric = Number(value);
  if (Number.isNaN(numeric)) return fallback;
  return Math.max(1, Math.min(10, Math.round(numeric)));
}

function normalizeStageStep(value, fallback, min, max) {
  const numeric = Number(value);
  if (Number.isNaN(numeric)) return fallback;
  return Math.max(min, Math.min(max, Math.round(numeric)));
}

function renderStatus(message) {
  els.optionsStatus.textContent = message;
  if (!message) return;

  window.setTimeout(() => {
    if (els.optionsStatus.textContent === message) {
      els.optionsStatus.textContent = "";
    }
  }, 2200);
}

function formatUnexpectedError(error) {
  return String(error?.message || error || "unknown");
}

function getAvatarMappingSyncError(value) {
  const text = String(value || "").trim();
  if (!text) {
    return "";
  }
  if (text.length > AVATAR_MAPPING_SYNC_TEXT_LIMIT) {
    return "위젯 이미지 매핑이 너무 큽니다. 이미지 본문은 매핑칸이 아니라 위 붙여넣기/드롭 영역으로 저장해주세요.";
  }

  for (const line of text.split(/\n+/)) {
    const match = line.match(/^\s*([a-z0-9_-]+)\s*=\s*(.+?)\s*$/i);
    if (!match) {
      continue;
    }
    if (match[2].trim().toLowerCase().startsWith("data:")) {
      return "data:image 값은 동기화 설정에 직접 저장하지 않습니다. 단계 선택 후 이미지 붙여넣기 영역을 사용해주세요.";
    }
  }

  return "";
}

function assertSettingsCanSync(settings) {
  const avatarError = getAvatarMappingSyncError(settings?.wellbeingAvatarImages);
  if (avatarError) {
    throw new Error(avatarError);
  }
}

async function saveSettingsToSync(settings) {
  assertSettingsCanSync(settings);
  await chrome.storage.sync.set({ settings });
}

function mapRunFailureReason(reason, errorCode) {
  const code = String(errorCode || "");
  const value = String(reason || "");
  if (code === "UNSUPPORTED_SELF_TEST_PAGE" || value.includes("UNSUPPORTED_SELF_TEST_PAGE")) {
    return "필터 랩 페이지에서만 실행할 수 있습니다";
  }
  if (code === "UNSUPPORTED_TAB" || value.includes("UNSUPPORTED_TAB")) {
    return "지원되지 않는 탭입니다 (chrome://, 확장 페이지 등)";
  }
  if (code === "ACTIVE_TAB_NOT_FOUND" || value.includes("ACTIVE_TAB_NOT_FOUND")) {
    return "현재 활성 탭을 찾지 못했습니다";
  }
  if (value.includes("Cannot access contents of url")) {
    return "이 페이지는 크롬 정책상 접근할 수 없습니다";
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
    return "백엔드 요청이 취소되었습니다";
  }
  return code || value || "unknown";
}

function setRunNowBusy(isBusy) {
  if (!els.runNowButton) return;
  els.runNowButton.disabled = isBusy;
  els.runNowButton.textContent = isBusy ? "검사 중..." : "현재 탭 검사";
}

function renderStats(stats) {
  els.statBlocked.textContent = String(Number(stats?.blockedCount || 0));
  els.statFalsePositive.textContent = String(Number(stats?.falsePositiveCount || 0));
  els.statLatency.textContent = `${Number(stats?.averageLatencyMs || 0)}ms`;
  els.statAnalyzed.textContent = String(Number(stats?.totalAnalyzedCount || 0));
}

function formatLatency(value) {
  const numeric = Number(value || 0);
  if (!numeric) return "-";
  return `${numeric}ms`;
}

function renderDiagnostics(lastStats) {
  els.diagFirstMask.textContent = formatLatency(lastStats?.firstMaskLatencyMs);
  els.diagHotPath.textContent = formatLatency(
    lastStats?.foregroundBackendLatencyMs ?? lastStats?.hotPathLatencyMs
  );
  els.diagHotPathState.textContent = String(lastStats?.hotPathStatus || "idle");
  els.diagHotPathError.textContent = String(lastStats?.hotPathErrorCode || "-");
  els.diagWorkerInit.textContent = String(lastStats?.foregroundBackendSource || "-");
  els.diagBackendReconcile.textContent = formatLatency(lastStats?.backendReconcileLatencyMs);
  els.diagMaskedSpans.textContent = String(Number(lastStats?.maskedSpanCount || 0));
  els.diagWorkerCacheHit.textContent = String(Number(lastStats?.workerCacheHitCount || 0));
  els.diagBackendCacheHit.textContent = String(Number(lastStats?.backendCacheHitCount || 0));
  els.diagVisibleBatch.textContent = String(Number(lastStats?.visibleContainerBatchSize || 0));
  els.diagReconcileQueue.textContent = String(Number(lastStats?.reconcileQueueDepth || 0));
  els.diagDecisionSource.textContent = String(lastStats?.lastDecisionSource || "-");
}

function stringifyPreview(value) {
  if (!value) return "(데이터 없음)";

  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return "(JSON 직렬화 실패)";
  }
}

function formatRuntimeLogTime(value) {
  const date = new Date(value || Date.now());
  if (Number.isNaN(date.getTime())) {
    return "-";
  }
  return RUNTIME_LOG_TIME_FORMATTER.format(date);
}

function normalizeReportCell(value, fallback = "-") {
  const text = String(value ?? "").trim();
  if (!text) {
    return fallback;
  }
  return text
    .replace(/\s+/g, " ")
    .replace(/\|/g, "/")
    .slice(0, 180);
}

function truncateReportLine(value, limit = 280) {
  const text = normalizeReportCell(value, "");
  if (text.length <= limit) {
    return text;
  }
  return `${text.slice(0, limit - 1)}…`;
}

function domainFromReportUrl(url) {
  try {
    return new URL(String(url || "")).hostname;
  } catch {
    return "";
  }
}

async function getActiveTabDiagnosticContext() {
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (!tab) {
      return { title: "", url: "", domain: "" };
    }
    return {
      title: String(tab.title || ""),
      url: String(tab.url || ""),
      domain: domainFromReportUrl(tab.url)
    };
  } catch {
    return { title: "", url: "", domain: "" };
  }
}

function summarizeRuntimeLogCounts(items) {
  const summary = {
    total: 0,
    backendFailureCount: 0,
    backendTimeoutCount: 0,
    siteBlockCount: 0,
    siteWarningCount: 0,
    siteFailureCount: 0,
    siteTestCount: 0,
    analysisEventCount: 0,
    positiveDetectionCount: 0,
    skippedTextCount: 0,
    mediaEventCount: 0,
    mediaActionCount: 0,
    mediaRemovedCount: 0,
    mediaPlaceholderCount: 0,
    mediaCollapsedGroupCount: 0,
    mediaMissedVisibleTileCount: 0,
    manualNoteCount: 0,
    settingsChangeCount: 0
  };

  for (const item of Array.isArray(items) ? items : []) {
    summary.total += 1;
    if (item?.type === "backend-health" && item.ok === false) {
      summary.backendFailureCount += 1;
      if (item.errorCode === "TIMEOUT") {
        summary.backendTimeoutCount += 1;
      }
    }
    if (item?.type === "site-policy") {
      if (item.verdict === "block") summary.siteBlockCount += 1;
      if (item.verdict === "warning") summary.siteWarningCount += 1;
      if (item.ok === false) summary.siteFailureCount += 1;
    }
    if (item?.type === "site-policy-test") {
      summary.siteTestCount += 1;
    }
    if (item?.type === "analyze-batch") {
      summary.analysisEventCount += 1;
      summary.positiveDetectionCount += Number(item.positiveCount || 0);
      summary.skippedTextCount += Number(item.skippedCount || 0);
    }
    if (String(item?.type || "").startsWith("media-safety")) {
      summary.mediaEventCount += 1;
      summary.mediaActionCount += Number(item.actionCount || 0);
      summary.mediaRemovedCount = (summary.mediaRemovedCount || 0) + Number(item.removedCount || 0);
      summary.mediaPlaceholderCount = (summary.mediaPlaceholderCount || 0) + Number(item.placeholderCount || 0);
      summary.mediaCollapsedGroupCount = (summary.mediaCollapsedGroupCount || 0) + Number(item.collapsedGroupCount || 0);
      summary.mediaMissedVisibleTileCount += Number(item.missedVisibleTileCount || 0);
    }
    if (item?.type === "manual-note") {
      summary.manualNoteCount += 1;
    }
    if (item?.type === "settings-changed") {
      summary.settingsChangeCount += 1;
    }
  }

  return summary;
}

function formatRuntimeLogTarget(item) {
  return item?.domain || domainFromReportUrl(item?.url) || item?.apiBaseUrl || "-";
}

function formatRuntimeLogOutcome(item) {
  return item?.verdict || item?.status || (item?.ok === false ? "failed" : "ok");
}

function buildRuntimeLogNotionReport(items, context = {}) {
  const logs = Array.isArray(items) ? items : [];
  const summary = summarizeRuntimeLogCounts(logs);
  const apiBaseUrl = sanitizeApiBaseUrl(els.backendApiBaseUrl?.value || currentSettings?.backendApiBaseUrl);
  const currentTabLine = context?.url
    ? `${truncateReportLine(context.title || context.url, 120)} (${context.url})`
    : "확인 불가";
  const rows = logs.slice(0, 40).map((item) => [
    formatRuntimeLogTime(item.isoTime || item.ts),
    normalizeReportCell(item.type || "event"),
    item.ok === false ? "실패" : item.ok === true ? "정상" : "-",
    normalizeReportCell(item.source || "-"),
    normalizeReportCell(formatRuntimeLogTarget(item)),
    normalizeReportCell(formatRuntimeLogOutcome(item)),
    normalizeReportCell(
      [
        item.positiveCount ? `positive=${item.positiveCount}` : "",
        item.skippedCount ? `skipped=${item.skippedCount}` : "",
        item.candidateCount ? `candidate=${item.candidateCount}` : "",
        item.visibleTileCount ? `visible=${item.visibleTileCount}` : "",
        item.actionCount ? `action=${item.actionCount}` : "",
        item.removedCount ? `removed=${item.removedCount}` : "",
        item.placeholderCount ? `placeholder=${item.placeholderCount}` : "",
        item.mergedTargetCount ? `merged=${item.mergedTargetCount}` : "",
        item.collapsedGroupCount ? `groups=${item.collapsedGroupCount}` : "",
        item.hiddenAreaPx ? `area=${item.hiddenAreaPx}px` : "",
        item.viewportCoveragePct ? `viewport=${item.viewportCoveragePct}%` : "",
        item.falseHiddenCount ? `falseHidden=${item.falseHiddenCount}` : "",
        item.domAddedToActionMs ? `dom=${item.domAddedToActionMs}ms` : "",
        item.collectMs ? `collect=${item.collectMs}ms` : "",
        item.cheapFilterMs ? `cheap=${item.cheapFilterMs}ms` : "",
        item.applyMs ? `apply=${item.applyMs}ms` : "",
        item.count ? `count=${item.count}` : "",
        item.durationMs ? `${item.durationMs}ms` : ""
      ].filter(Boolean).join(", ")
    ),
    normalizeReportCell(item.errorCode || item.message || item.reason || "-")
  ]);

  return [
    "# 청마루 런타임 진단 로그",
    "",
    `- 생성 시각: ${formatRuntimeLogTime(Date.now())}`,
    `- 백엔드 API: ${apiBaseUrl}`,
    `- 현재 탭: ${currentTabLine}`,
    `- 이벤트 수: ${summary.total}`,
    "",
    "## 요약",
    "",
    `- 백엔드 실패: ${summary.backendFailureCount}건 (타임아웃 ${summary.backendTimeoutCount}건)`,
    `- 사이트 차단/경고: 차단 ${summary.siteBlockCount}건, 경고 ${summary.siteWarningCount}건, 판정 실패 ${summary.siteFailureCount}건, 수동 판정 확인 ${summary.siteTestCount}건`,
    `- 유해표현 분석: 이벤트 ${summary.analysisEventCount}건, 양성 판정 ${summary.positiveDetectionCount}개, 건너뜀 ${summary.skippedTextCount}개`,
    `- 유해 이미지 보호: 이벤트 ${summary.mediaEventCount}건, 처리 ${summary.mediaActionCount}개, 놓친 visible tile ${summary.mediaMissedVisibleTileCount}개`,
    `- 설정 변경/수동 메모: 설정 변경 ${summary.settingsChangeCount}건, 수동 재현 메모 ${summary.manualNoteCount}건`,
    "",
    "## 최근 이벤트",
    "",
    "| 시간 | 종류 | 상태 | 출처 | 대상 | 결과 | 수치 | 메모 |",
    "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ...(rows.length ? rows.map((row) => `| ${row.join(" | ")} |`) : ["| - | - | - | - | - | - | - | 로그 없음 |"]),
    "",
    "## 다음 확인",
    "",
    "- Chrome 확장 프로그램을 새로고침한 뒤 같은 URL에서 재현한다.",
    "- 백엔드가 필요한 증상은 팝업의 백엔드 상태와 위 이벤트의 `backend-health`를 같이 확인한다."
  ].join("\n");
}

function summarizeRuntimeLogs(items) {
  if (!Array.isArray(items) || items.length === 0) {
    return "(런타임 로그 없음)";
  }

  return stringifyPreview({
    count: items.length,
    logs: items.map((item) => ({
      time: item.isoTime || item.ts || null,
      type: item.type || "event",
      ok: item.ok,
      status: item.status || "",
      source: item.source || "",
      domain: item.domain || "",
      title: item.title || "",
      url: item.url || "",
      verdict: item.verdict || "",
      profile: item.profile || "",
      action: item.action || "",
      durationMs: Number(item.durationMs || 0),
      candidateCount: Number(item.candidateCount || 0),
      visibleTileCount: Number(item.visibleTileCount || 0),
      actionCount: Number(item.actionCount || 0),
      removedCount: Number(item.removedCount || 0),
      placeholderCount: Number(item.placeholderCount || 0),
      mergedTargetCount: Number(item.mergedTargetCount || 0),
      collapsedGroupCount: Number(item.collapsedGroupCount || 0),
      hiddenAreaPx: Number(item.hiddenAreaPx || 0),
      viewportCoveragePct: Number(item.viewportCoveragePct || 0),
      falseHiddenCount: Number(item.falseHiddenCount || 0),
      domAddedToActionMs: Number(item.domAddedToActionMs || 0),
      collectMs: Number(item.collectMs || 0),
      cheapFilterMs: Number(item.cheapFilterMs || 0),
      applyMs: Number(item.applyMs || 0),
      count: Number(item.count || 0),
      positiveCount: Number(item.positiveCount || 0),
      skippedCount: Number(item.skippedCount || 0),
      errorCode: item.errorCode || "",
      message: item.message || "",
      reason: item.reason || "",
      apiBaseUrl: item.apiBaseUrl || ""
    }))
  });
}

async function loadRuntimeLogs() {
  if (!els.runtimeLogPreview) {
    return [];
  }
  const response = await chrome.runtime.sendMessage({
    type: "GET_RUNTIME_EVENT_LOGS",
    limit: 80
  });
  const logs = response?.ok && Array.isArray(response.logs) ? response.logs : [];
  currentRuntimeLogs = logs;
  els.runtimeLogPreview.value = summarizeRuntimeLogs(logs);
  return logs;
}

async function clearRuntimeLogs() {
  await chrome.runtime.sendMessage({ type: "CLEAR_RUNTIME_EVENT_LOGS" });
  currentRuntimeLogs = [];
  if (els.runtimeLogPreview) {
    els.runtimeLogPreview.value = "(런타임 로그 없음)";
  }
}

async function loadDeveloperRuntimeLogEnabled() {
  const result = await chrome.storage.local.get(DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY);
  developerRuntimeLogEnabled = result?.[DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY] === true;
  if (els.developerRuntimeLogEnabledToggle) {
    els.developerRuntimeLogEnabledToggle.checked = developerRuntimeLogEnabled;
  }
  return developerRuntimeLogEnabled;
}

async function saveDeveloperRuntimeLogEnabled(enabled) {
  developerRuntimeLogEnabled = enabled === true;
  await chrome.storage.local.set({
    [DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY]: developerRuntimeLogEnabled
  });
  if (els.developerRuntimeLogEnabledToggle) {
    els.developerRuntimeLogEnabledToggle.checked = developerRuntimeLogEnabled;
  }
  return developerRuntimeLogEnabled;
}

async function copyRuntimeLogs() {
  const value = els.runtimeLogPreview?.value || "";
  if (!value || value === "(런타임 로그 없음)") {
    renderStatus("복사할 로그가 없습니다");
    return;
  }
  await navigator.clipboard.writeText(value);
  renderStatus("런타임 로그 복사됨");
}

async function copyRuntimeLogReport() {
  const logs = currentRuntimeLogs.length ? currentRuntimeLogs : await loadRuntimeLogs();
  if (!logs.length) {
    renderStatus("복사할 로그가 없습니다");
    return;
  }
  const context = await getActiveTabDiagnosticContext();
  await navigator.clipboard.writeText(buildRuntimeLogNotionReport(logs, context));
  renderStatus("Notion용 런타임 보고서 복사됨");
}

async function addRuntimeManualNote() {
  const message = String(els.runtimeManualNote?.value || "").trim();
  if (!message) {
    renderStatus("추가할 재현 메모를 입력해주세요");
    return;
  }

  const context = await getActiveTabDiagnosticContext();
  const response = await chrome.runtime.sendMessage({
    type: "ADD_RUNTIME_EVENT_LOG",
    event: {
      type: "manual-note",
      ok: true,
      status: "note",
      source: "options",
      title: context.title,
      url: context.url,
      domain: context.domain,
      message,
      reason: message
    }
  });

  if (!response?.ok) {
    throw new Error(response?.reason || response?.errorCode || "ADD_RUNTIME_EVENT_LOG_FAILED");
  }
  if (response?.skipped) {
    renderStatus("개발자 로그 기록이 꺼져 있습니다");
    return;
  }

  els.runtimeManualNote.value = "";
  await loadRuntimeLogs();
  renderStatus("재현 메모가 런타임 로그에 추가됨");
}

function summarizeSelfTestResult(value) {
  if (!value) {
    return "(self-test 결과 없음)";
  }

  const summary = value?.summary || {};
  return stringifyPreview({
    ok: Boolean(value?.ok),
    timestamp: value?.timestamp || null,
    url: value?.url || null,
    durationMs: Number(value?.durationMs || 0),
    summary: {
      totalCases: Number(summary.totalCases || 0),
      visibleCases: Number(summary.visibleCases || 0),
      failedCases: Number(summary.failedCases || 0),
      backendMismatchCount: Number(summary.backendMismatchCount || 0),
      extensionMismatchCount: Number(summary.extensionMismatchCount || 0),
      extensionBackendMismatchCount: Number(summary.extensionBackendMismatchCount || 0)
    },
    backend: value?.backend || null,
    cases: Array.isArray(value?.cases) ? value.cases.filter((entry) => entry.pass === false) : []
  });
}

function summarizeSelfTestHistory(items) {
  if (!Array.isArray(items) || items.length === 0) {
    return "(self-test 이력 없음)";
  }

  const repeatedFailures = new Map();
  for (const item of items) {
    for (const entry of Array.isArray(item?.cases) ? item.cases : []) {
      if (entry?.pass !== false) {
        continue;
      }

      const key = `${entry.caseId || "unknown"}::${entry.expectationKind || "unknown"}`;
      const current = repeatedFailures.get(key) || {
        caseId: entry.caseId || "unknown",
        expectationKind: entry.expectationKind || "unknown",
        sampleText: entry.sampleText || "",
        count: 0,
        backendMismatchCount: 0,
        extensionMismatchCount: 0
      };
      current.count += 1;
      if (entry.backendMatchesExpectation === false) {
        current.backendMismatchCount += 1;
      }
      if (entry.extensionMatchesExpectation === false) {
        current.extensionMismatchCount += 1;
      }
      repeatedFailures.set(key, current);
    }
  }

  return stringifyPreview({
    recentRuns: items.slice(0, 10).map((item) => ({
      timestamp: item?.timestamp || null,
      ok: Boolean(item?.ok),
      url: item?.url || null,
      durationMs: Number(item?.durationMs || 0),
      totalCases: Number(item?.summary?.totalCases || 0),
      failedCases: Number(item?.summary?.failedCases || 0),
      backendMismatchCount: Number(item?.summary?.backendMismatchCount || 0),
      extensionMismatchCount: Number(item?.summary?.extensionMismatchCount || 0)
    })),
    repeatedFailures: [...repeatedFailures.values()]
      .sort((left, right) => right.count - left.count)
      .slice(0, 12)
  });
}

function formatConnectionStatus(result) {
  if (!result) return "아직 연결 확인 전";
  const apiBaseUrl = String(result.apiBaseUrl || currentSettings?.backendApiBaseUrl || "");

  if (result.backendStatus === "disabled" || result.errorCode === "BACKEND_DISABLED") {
    return "백엔드 연동 꺼짐 · 개발자 모드에서만 사용";
  }

  if (!result.ok) {
    return `연결 실패 · ${mapRunFailureReason(result.reason, result.errorCode)} (${apiBaseUrl || "API 주소 없음"})`;
  }

  if (result.model_ready === false) {
    return `모델 준비 중 · ${apiBaseUrl}`;
  }

  if (result.backendStatus === "slow" || result.slow) {
    return `응답 지연 · 연결됨 · ${apiBaseUrl}`;
  }

  return `연결됨 · ${apiBaseUrl}`;
}

async function refreshConnectionState() {
  currentSettings = {
    ...currentSettings,
    backendEnabled: els.backendEnabledToggle?.checked === true,
    backendApiBaseUrl: sanitizeApiBaseUrl(els.backendApiBaseUrl?.value || currentSettings?.backendApiBaseUrl),
    requestTimeoutMs: normalizeRequestTimeoutMs(
      els.requestTimeoutMs?.value || currentSettings?.requestTimeoutMs
    )
  };

  if (currentSettings.backendEnabled !== true) {
    const result = {
      ok: false,
      backendStatus: "disabled",
      errorCode: "BACKEND_DISABLED",
      reason: "백엔드 연동이 꺼져 있습니다.",
      apiBaseUrl: currentSettings.backendApiBaseUrl,
      durationMs: 0
    };
    els.connectionStatusText.textContent = formatConnectionStatus(result);
    return result;
  }

  const result = await chrome.runtime.sendMessage({
    type: "CHECK_API_HEALTH",
    apiBaseUrl: currentSettings.backendApiBaseUrl,
    requestTimeoutMs: currentSettings.requestTimeoutMs,
    forceRefresh: true
  });
  els.connectionStatusText.textContent = formatConnectionStatus(result);
  return result;
}

async function runPipelineNowFromOptions() {
  if (isRunningPipeline) return;

  isRunningPipeline = true;
  setRunNowBusy(true);

  try {
    const response = await chrome.runtime.sendMessage({ type: "RUN_PIPELINE_ON_ACTIVE_TAB" });
    if (!response?.ok) {
      renderStatus(`분석 실패: ${mapRunFailureReason(response?.reason, response?.errorCode)}`);
      return;
    }

    await loadRuntimeState();
    renderStatus("현재 탭 분석 완료");
  } finally {
    isRunningPipeline = false;
    setRunNowBusy(false);
  }
}

async function runSelfTestFromOptions() {
  if (isRunningSelfTest) return;

  isRunningSelfTest = true;
  if (els.runSelfTestButton) {
    els.runSelfTestButton.disabled = true;
    els.runSelfTestButton.textContent = "실행 중...";
  }

  try {
    const response = await chrome.runtime.sendMessage({ type: "RUN_SELF_TEST_ON_ACTIVE_TAB" });
    if (!response?.ok) {
      renderStatus(`self-test 실패: ${mapRunFailureReason(response?.reason, response?.errorCode)}`);
      return;
    }

    await loadRuntimeState();
    renderStatus("self-test 완료");
  } finally {
    isRunningSelfTest = false;
    if (els.runSelfTestButton) {
      els.runSelfTestButton.disabled = false;
      els.runSelfTestButton.textContent = "랩 self-test 실행";
    }
  }
}

async function loadRuntimeState() {
  const [state] = await Promise.all([
    chrome.runtime.sendMessage({ type: "GET_LAST_PIPELINE_STATE" }),
    loadRuntimeLogs().catch((error) => {
      if (els.runtimeLogPreview) {
        els.runtimeLogPreview.value = `런타임 로그 조회 실패: ${formatUnexpectedError(error)}`;
      }
      return [];
    })
  ]);

  if (!state?.ok) {
    els.payloadPreview.value = "(상태 조회 실패)";
    els.decisionPreview.value = "(상태 조회 실패)";
    if (els.selfTestPreview) {
      els.selfTestPreview.value = "(상태 조회 실패)";
    }
    if (els.selfTestHistoryPreview) {
      els.selfTestHistoryPreview.value = "(상태 조회 실패)";
    }
    renderStats(null);
    return;
  }

  els.payloadPreview.value = stringifyPreview(state.lastPayload);
  els.decisionPreview.value = stringifyPreview(
    state.lastPipelineError
      ? {
          lastDecision: state.lastDecision || null,
          lastPipelineError: state.lastPipelineError,
          lastForegroundDiagnostics: state?.lastStats?.lastForegroundDiagnostics || null,
          lastReconcileDiagnostics: state?.lastStats?.lastReconcileDiagnostics || null
        }
      : {
          lastDecision: state.lastDecision || null,
          lastForegroundDiagnostics: state?.lastStats?.lastForegroundDiagnostics || null,
          lastReconcileDiagnostics: state?.lastStats?.lastReconcileDiagnostics || null
        }
  );
  if (els.selfTestPreview) {
    els.selfTestPreview.value = summarizeSelfTestResult(state.lastSelfTest);
  }
  if (els.selfTestHistoryPreview) {
    els.selfTestHistoryPreview.value = summarizeSelfTestHistory(state.lastSelfTestHistory);
  }
  renderStats(state.sessionStats);
  renderDiagnostics(state.lastStats);
}

function readSettingsFromForm() {
  const siteProtectionEnabled = els.siteProtectionEnabledToggle?.checked !== false;
  return {
    ...currentSettings,
    customBlockWords: els.blockWords.value.trim(),
    customAllowWords: els.allowWords.value.trim(),
    blockedDomains: els.blockedDomains.value.trim(),
    warnDomains: els.warnDomains.value.trim(),
    textMaskingEnabled: els.textMaskingEnabledToggle?.checked !== false,
    showReason: els.showReasonToggle.checked,
    interventionMode: normalizeInterventionMode(els.interventionModeSelect?.value),
    siteProtectionEnabled,
    siteNavigationWarningEnabled: siteProtectionEnabled,
    searchResultProtectionEnabled: siteProtectionEnabled,
    mediaSafetyEnabled: els.mediaSafetyEnabledToggle?.checked === true,
    mediaSafetyInterventionMode: normalizeMediaSafetyInterventionMode(
      els.mediaSafetyInterventionModeSelect?.value
    ),
    showWellbeingWidget: els.showWellbeingWidgetToggle.checked,
    wellbeingWidgetStyle: normalizeWidgetStyle(els.wellbeingWidgetStyle.value),
    wellbeingAvatarImages: els.wellbeingAvatarImages?.value.trim() || "",
    wellbeingAgeStageCount: normalizeStageCount(
      els.wellbeingAgeStageCount.value,
      DEFAULT_SETTINGS.wellbeingAgeStageCount
    ),
    wellbeingAgeMinutesPerStage: normalizeStageStep(
      els.wellbeingAgeMinutesPerStage.value,
      DEFAULT_SETTINGS.wellbeingAgeMinutesPerStage,
      5,
      240
    ),
    wellbeingAngerStageCount: normalizeStageCount(
      els.wellbeingAngerStageCount.value,
      DEFAULT_SETTINGS.wellbeingAngerStageCount
    ),
    wellbeingAngerDetectionsPerStage: normalizeStageStep(
      els.wellbeingAngerDetectionsPerStage.value,
      DEFAULT_SETTINGS.wellbeingAngerDetectionsPerStage,
      1,
      50
    ),
    backendEnabled: els.backendEnabledToggle?.checked === true,
    backendApiBaseUrl: sanitizeApiBaseUrl(els.backendApiBaseUrl.value),
    requestTimeoutMs: normalizeRequestTimeoutMs(els.requestTimeoutMs.value)
  };
}

function renderSettingsToForm(settings) {
  els.blockWords.value = settings.customBlockWords;
  els.allowWords.value = settings.customAllowWords;
  els.blockedDomains.value = settings.blockedDomains || "";
  els.warnDomains.value = settings.warnDomains || "";
  if (els.textMaskingEnabledToggle) {
    els.textMaskingEnabledToggle.checked = settings.textMaskingEnabled !== false;
  }
  els.showReasonToggle.checked = settings.showReason;
  if (els.interventionModeSelect) {
    els.interventionModeSelect.value = normalizeInterventionMode(settings.interventionMode);
  }
  if (els.siteProtectionEnabledToggle) {
    els.siteProtectionEnabledToggle.checked = settings.siteProtectionEnabled !== false;
  }
  if (els.siteNavigationWarningEnabledToggle) {
    els.siteNavigationWarningEnabledToggle.checked = settings.siteNavigationWarningEnabled !== false;
  }
  if (els.searchResultProtectionEnabledToggle) {
    els.searchResultProtectionEnabledToggle.checked = settings.searchResultProtectionEnabled !== false;
  }
  if (els.mediaSafetyEnabledToggle) {
    els.mediaSafetyEnabledToggle.checked = settings.mediaSafetyEnabled === true;
  }
  if (els.mediaSafetyInterventionModeSelect) {
    els.mediaSafetyInterventionModeSelect.value = normalizeMediaSafetyInterventionMode(
      settings.mediaSafetyInterventionMode
    );
  }
  els.showWellbeingWidgetToggle.checked = settings.showWellbeingWidget !== false;
  els.wellbeingWidgetStyle.value = normalizeWidgetStyle(settings.wellbeingWidgetStyle);
  if (els.wellbeingAvatarImages) {
    els.wellbeingAvatarImages.value = settings.wellbeingAvatarImages || "";
  }
  els.wellbeingAgeStageCount.value = String(
    normalizeStageCount(settings.wellbeingAgeStageCount, DEFAULT_SETTINGS.wellbeingAgeStageCount)
  );
  els.wellbeingAgeMinutesPerStage.value = String(
    normalizeStageStep(settings.wellbeingAgeMinutesPerStage, DEFAULT_SETTINGS.wellbeingAgeMinutesPerStage, 5, 240)
  );
  els.wellbeingAngerStageCount.value = String(
    normalizeStageCount(settings.wellbeingAngerStageCount, DEFAULT_SETTINGS.wellbeingAngerStageCount)
  );
  els.wellbeingAngerDetectionsPerStage.value = String(
    normalizeStageStep(
      settings.wellbeingAngerDetectionsPerStage,
      DEFAULT_SETTINGS.wellbeingAngerDetectionsPerStage,
      1,
      50
    )
  );
  if (els.backendEnabledToggle) {
    els.backendEnabledToggle.checked = settings.backendEnabled === true;
  }
  els.backendApiBaseUrl.value = settings.backendApiBaseUrl;
  els.requestTimeoutMs.value = String(settings.requestTimeoutMs);
  updateBackendDependentControls();
  updateProtectionDependentControls();
}

function updateProtectionDependentControls() {
  const siteProtectionEnabled = els.siteProtectionEnabledToggle?.checked !== false;
  for (const input of [
    els.siteNavigationWarningEnabledToggle,
    els.searchResultProtectionEnabledToggle
  ]) {
    if (!input) continue;
    input.disabled = !siteProtectionEnabled;
  }
}

function updateBackendDependentControls() {
  const backendEnabled = els.backendEnabledToggle?.checked === true;
  for (const input of [
    els.backendApiBaseUrl,
    els.requestTimeoutMs,
    els.checkConnectionButton,
    els.policyTestForceRefresh
  ]) {
    if (!input) continue;
    input.disabled = !backendEnabled;
  }
}

function normalizeAvatarImageKey(value) {
  const key = String(value || "").trim().toLowerCase();
  return AVATAR_IMAGE_KEYS.includes(key) ? key : "default";
}

function getAvatarImageLabel(key) {
  if (key === "default") {
    return "기본";
  }
  const ageMatch = String(key || "").match(/^age(\d+)$/);
  if (ageMatch) {
    return `늙음 ${ageMatch[1]}`;
  }
  const angerMatch = String(key || "").match(/^anger(\d+)$/);
  if (angerMatch) {
    return `화남 ${angerMatch[1]}`;
  }
  return key;
}

function getConfiguredAvatarImageKeys() {
  const ageCount = normalizeStageCount(
    els.wellbeingAgeStageCount?.value || currentSettings?.wellbeingAgeStageCount,
    DEFAULT_SETTINGS.wellbeingAgeStageCount
  );
  const angerCount = normalizeStageCount(
    els.wellbeingAngerStageCount?.value || currentSettings?.wellbeingAngerStageCount,
    DEFAULT_SETTINGS.wellbeingAngerStageCount
  );
  return [
    "default",
    ...Array.from({ length: ageCount }, (_, index) => `age${index + 1}`),
    ...Array.from({ length: angerCount }, (_, index) => `anger${index + 1}`)
  ];
}

function renderAvatarSlotOptions() {
  if (!els.avatarImageSlot) {
    return;
  }
  const previousValue = normalizeAvatarImageKey(els.avatarImageSlot.value);
  const keys = getConfiguredAvatarImageKeys();
  const fragment = document.createDocumentFragment();
  for (const key of keys) {
    const option = document.createElement("option");
    option.value = key;
    option.textContent = getAvatarImageLabel(key);
    fragment.appendChild(option);
  }
  els.avatarImageSlot.replaceChildren(fragment);
  els.avatarImageSlot.value = keys.includes(previousValue) ? previousValue : "default";
  updateAvatarSlotSelectionUi();
}

function getSelectedAvatarImageKey() {
  return normalizeAvatarImageKey(els.avatarImageSlot?.value || "default");
}

function updateAvatarSlotSelectionUi() {
  const selectedKey = getSelectedAvatarImageKey();
  const selectedLabel = getAvatarImageLabel(selectedKey);

  if (els.avatarPasteTargetText) {
    els.avatarPasteTargetText.textContent = `${selectedLabel} 단계에 이미지 복사/붙여넣기 또는 드롭`;
  }
  if (els.avatarPasteZone) {
    els.avatarPasteZone.setAttribute("aria-label", `${selectedLabel} 단계 이미지 붙여넣기`);
  }

  if (!els.avatarImagePreviewList) {
    return;
  }
  for (const card of els.avatarImagePreviewList.querySelectorAll(".avatar-preview-card")) {
    const isSelected = card.dataset.avatarKey === selectedKey;
    card.classList.toggle("is-selected", isSelected);
    card.dataset.selected = isSelected ? "true" : "false";
    card.setAttribute("aria-pressed", isSelected ? "true" : "false");
  }
}

function selectAvatarImageSlot(key, options = {}) {
  const normalizedKey = normalizeAvatarImageKey(key);
  if (els.avatarImageSlot) {
    els.avatarImageSlot.value = normalizedKey;
  }
  updateAvatarSlotSelectionUi();
  if (options.announce !== false) {
    renderStatus(`${getAvatarImageLabel(normalizedKey)} 단계 선택됨`);
  }
}

function parseAvatarMappingText(value) {
  const mapping = new Map();
  for (const line of String(value || "").split(/\n+/)) {
    const match = line.match(/^\s*([a-z0-9_-]+)\s*=\s*(.+?)\s*$/i);
    if (!match) {
      continue;
    }
    mapping.set(match[1].toLowerCase(), match[2].trim());
  }
  return mapping;
}

function upsertAvatarMappingLine(key, value) {
  if (!els.wellbeingAvatarImages) {
    return;
  }
  const normalizedKey = normalizeAvatarImageKey(key);
  const nextLine = `${normalizedKey}=${value}`;
  const lines = String(els.wellbeingAvatarImages.value || "").split(/\n/);
  let didReplace = false;
  const nextLines = lines.map((line) => {
    const match = line.match(/^\s*([a-z0-9_-]+)\s*=/i);
    if (match && match[1].toLowerCase() === normalizedKey) {
      didReplace = true;
      return nextLine;
    }
    return line;
  });
  if (!didReplace) {
    nextLines.push(nextLine);
  }
  els.wellbeingAvatarImages.value = nextLines
    .map((line) => line.trimEnd())
    .filter((line, index, array) => line.trim() || index < array.length - 1)
    .join("\n")
    .trim();
}

function removeAvatarMappingLine(key) {
  if (!els.wellbeingAvatarImages) {
    return;
  }
  const normalizedKey = normalizeAvatarImageKey(key);
  els.wellbeingAvatarImages.value = String(els.wellbeingAvatarImages.value || "")
    .split(/\n/)
    .filter((line) => {
      const match = line.match(/^\s*([a-z0-9_-]+)\s*=/i);
      return !(match && match[1].toLowerCase() === normalizedKey);
    })
    .map((line) => line.trimEnd())
    .join("\n")
    .trim();
}

async function loadAvatarImageAssets() {
  try {
    const result = await chrome.storage.local.get(AVATAR_ASSET_STORAGE_KEY);
    const stored = result?.[AVATAR_ASSET_STORAGE_KEY];
    return stored && typeof stored === "object" ? stored : {};
  } catch {
    return {};
  }
}

function resolveAvatarPreviewUrl(key, mapping, assets) {
  const rawValue = String(mapping.get(key) || "").trim();
  if (!rawValue) {
    return "";
  }
  if (rawValue.toLowerCase().startsWith(AVATAR_LOCAL_PREFIX)) {
    const localKey = normalizeAvatarImageKey(rawValue.slice(AVATAR_LOCAL_PREFIX.length) || key);
    return typeof assets[localKey] === "string" ? assets[localKey] : "";
  }
  return rawValue;
}

async function renderAvatarImagePreviewList() {
  if (!els.avatarImagePreviewList || !els.wellbeingAvatarImages) {
    return;
  }
  const [assets, mapping] = await Promise.all([
    loadAvatarImageAssets(),
    Promise.resolve(parseAvatarMappingText(els.wellbeingAvatarImages.value))
  ]);
  const fragment = document.createDocumentFragment();
  const selectedKey = getSelectedAvatarImageKey();
  for (const key of getConfiguredAvatarImageKeys()) {
    const card = document.createElement("article");
    card.className = "avatar-preview-card";
    card.dataset.avatarKey = key;
    card.dataset.selected = key === selectedKey ? "true" : "false";
    card.tabIndex = 0;
    card.setAttribute("role", "button");
    card.setAttribute("aria-pressed", key === selectedKey ? "true" : "false");
    card.setAttribute("aria-label", `${getAvatarImageLabel(key)} 단계 선택`);
    if (key === selectedKey) {
      card.classList.add("is-selected");
    }

    const url = resolveAvatarPreviewUrl(key, mapping, assets);
    if (url) {
      const image = document.createElement("img");
      image.src = url;
      image.alt = getAvatarImageLabel(key);
      card.appendChild(image);
    } else {
      const empty = document.createElement("div");
      empty.className = "avatar-preview-empty";
      empty.setAttribute("aria-hidden", "true");
      card.appendChild(empty);
    }

    const label = document.createElement("span");
    label.textContent = getAvatarImageLabel(key);
    card.appendChild(label);

    if (url) {
      const removeButton = document.createElement("button");
      removeButton.type = "button";
      removeButton.className = "avatar-preview-remove";
      removeButton.dataset.avatarKey = key;
      removeButton.textContent = "삭제";
      removeButton.setAttribute("aria-label", `${getAvatarImageLabel(key)} 이미지 삭제`);
      card.appendChild(removeButton);
    }
    fragment.appendChild(card);
  }
  els.avatarImagePreviewList.replaceChildren(fragment);
  updateAvatarSlotSelectionUi();
}

function getImageFileFromTransfer(transfer) {
  if (!transfer) {
    return null;
  }
  for (const item of Array.from(transfer.items || [])) {
    if (item.kind === "file" && String(item.type || "").startsWith("image/")) {
      return item.getAsFile();
    }
  }
  for (const file of Array.from(transfer.files || [])) {
    if (String(file.type || "").startsWith("image/")) {
      return file;
    }
  }
  return null;
}

function loadImageFromUrl(url) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("image-load-failed"));
    image.src = url;
  });
}

async function fileToAvatarDataUrl(file) {
  if (!file || !String(file.type || "").startsWith("image/")) {
    throw new Error("not-image-file");
  }
  const objectUrl = URL.createObjectURL(file);
  try {
    const image = await loadImageFromUrl(objectUrl);
    const sourceWidth = image.naturalWidth || image.width;
    const sourceHeight = image.naturalHeight || image.height;
    if (!sourceWidth || !sourceHeight) {
      throw new Error("invalid-image-size");
    }
    const scale = Math.min(1, AVATAR_IMAGE_MAX_DIMENSION / Math.max(sourceWidth, sourceHeight));
    const targetWidth = Math.max(1, Math.round(sourceWidth * scale));
    const targetHeight = Math.max(1, Math.round(sourceHeight * scale));
    const canvas = document.createElement("canvas");
    canvas.width = targetWidth;
    canvas.height = targetHeight;
    const context = canvas.getContext("2d");
    if (!context) {
      throw new Error("canvas-unavailable");
    }
    context.drawImage(image, 0, 0, targetWidth, targetHeight);
    return canvas.toDataURL(AVATAR_IMAGE_EXPORT_MIME, AVATAR_IMAGE_EXPORT_QUALITY);
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}

async function saveAvatarImageAsset(key, dataUrl) {
  const normalizedKey = normalizeAvatarImageKey(key);
  const assets = await loadAvatarImageAssets();
  assets[normalizedKey] = dataUrl;
  await chrome.storage.local.set({ [AVATAR_ASSET_STORAGE_KEY]: assets });
  upsertAvatarMappingLine(normalizedKey, `${AVATAR_LOCAL_PREFIX}${normalizedKey}`);
  currentSettings = readSettingsFromForm();
  await saveSettingsToSync(currentSettings);
  await renderAvatarImagePreviewList();
}

async function clearAvatarImageAsset(key) {
  const normalizedKey = normalizeAvatarImageKey(key);
  const label = getAvatarImageLabel(normalizedKey);
  const assets = await loadAvatarImageAssets();
  delete assets[normalizedKey];
  await chrome.storage.local.set({ [AVATAR_ASSET_STORAGE_KEY]: assets });
  removeAvatarMappingLine(normalizedKey);
  currentSettings = readSettingsFromForm();
  await saveSettingsToSync(currentSettings);
  await renderAvatarImagePreviewList();
  renderStatus(`${label} 이미지 삭제 완료`);
}

async function handleAvatarImageFile(file) {
  if (!file) {
    renderStatus("이미지 파일을 찾지 못했습니다");
    return;
  }
  const slot = normalizeAvatarImageKey(els.avatarImageSlot?.value);
  const label = getAvatarImageLabel(slot);
  renderStatus(`${label} 이미지 처리 중`);
  try {
    const dataUrl = await fileToAvatarDataUrl(file);
    await saveAvatarImageAsset(slot, dataUrl);
    renderStatus(`${label} 이미지 저장 완료`);
  } catch (error) {
    console.error(error);
    renderStatus("이미지를 저장하지 못했습니다. PNG/JPG/WebP 이미지를 다시 넣어주세요.");
  }
}

function clampDebugNumber(value, fallback, min, max) {
  const numeric = Number(value);
  if (Number.isNaN(numeric)) return fallback;
  return Math.max(min, Math.min(max, Math.round(numeric)));
}

function unlockDeveloperTools() {
  const password = String(els.developerPassword?.value || "");
  if (password !== DEVELOPER_MODE_PASSWORD) {
    renderStatus("개발자 모드 비밀번호가 맞지 않습니다");
    return;
  }

  if (els.developerLockSection) {
    els.developerLockSection.hidden = true;
  }
  if (els.developerToolsSection) {
    els.developerToolsSection.hidden = false;
  }
  if (els.diagnosticsPanel) {
    els.diagnosticsPanel.hidden = false;
  }
  renderStatus("개발자 테스트 모드 열림");
}

async function applyDeveloperDebugOverride() {
  currentSettings = readSettingsFromForm();
  await saveSettingsToSync(currentSettings);
  const override = {
    enabled: true,
    simulatedHour: clampDebugNumber(els.debugSimulatedHour.value, new Date().getHours(), 0, 23),
    usageMinutes: clampDebugNumber(els.debugUsageMinutes.value, 0, 0, 24 * 60),
    profanityCount: clampDebugNumber(els.debugProfanityCount.value, 0, 0, 999),
    harmfulCount: clampDebugNumber(els.debugHarmfulCount.value, 0, 0, 999),
    policyVerdict: String(els.debugPolicyVerdict.value || "allow")
  };
  await chrome.runtime.sendMessage({
    type: "SET_WELLBEING_DEBUG_OVERRIDE",
    override
  });
  renderStatus("개발자 테스트 값 적용 완료");
}

async function clearDeveloperDebugOverride() {
  await chrome.runtime.sendMessage({ type: "CLEAR_WELLBEING_DEBUG_OVERRIDE" });
  renderStatus("개발자 테스트 값 해제 완료");
}

async function resetWellbeingState() {
  const response = await chrome.runtime.sendMessage({ type: "CLEAR_WELLBEING_STATE" });
  if (!response?.ok) {
    throw new Error(response?.reason || response?.errorCode || "CLEAR_WELLBEING_STATE_FAILED");
  }
  await loadRuntimeLogs().catch(() => {});
  renderStatus("위젯 통계 초기화 완료");
}

function normalizePolicyTestUrl(value) {
  const raw = String(value || "").trim() || SITE_POLICY_TEST_DEFAULT_URL;
  const withScheme = /^[a-z][a-z0-9+.-]*:\/\//i.test(raw) ? raw : `https://${raw}`;
  const parsed = new URL(withScheme);
  return parsed.href;
}

function summarizeSitePolicyTest(response, requestedUrl) {
  const policy = response?.policy || null;
  return stringifyPreview({
    ok: Boolean(response?.ok),
    requestedUrl,
    source: response?.source || "unknown",
    dismissed: Boolean(response?.dismissed),
    reason: response?.reason || "",
    errorCode: response?.errorCode || "",
    policy: policy
      ? {
          verdict: policy.verdict || "",
          domain: policy.domain || "",
          category: policy.site_category || policy.category || "",
          riskScore: Number(policy.risk_score || 0),
          securityThreat: Boolean(policy.security_threat),
          harmfulContent: Boolean(policy.harmful_content),
          exactMatch: policy.exact_match || null,
          reasons: Array.isArray(policy.reasons) ? policy.reasons : []
        }
      : null
  });
}

async function runSitePolicyTest() {
  const requestedUrl = normalizePolicyTestUrl(els.policyTestUrl?.value);
  if (els.policyTestUrl) {
    els.policyTestUrl.value = requestedUrl;
  }

  currentSettings = readSettingsFromForm();
  await saveSettingsToSync(currentSettings);

  if (els.runPolicyTestButton) {
    els.runPolicyTestButton.disabled = true;
    els.runPolicyTestButton.textContent = "판정 중...";
  }

  try {
    const response = await chrome.runtime.sendMessage({
      type: "GET_SITE_POLICY_FOR_URL",
      context: "options-policy-test",
      url: requestedUrl,
      forceRefresh: Boolean(els.policyTestForceRefresh?.checked)
    });
    if (els.policyTestPreview) {
      els.policyTestPreview.value = summarizeSitePolicyTest(response, requestedUrl);
    }
    await loadRuntimeLogs().catch(() => {});
    const verdict = response?.policy?.verdict || response?.reason || "unknown";
    renderStatus(`사이트 판정 완료: ${verdict}`);
  } finally {
    if (els.runPolicyTestButton) {
      els.runPolicyTestButton.disabled = false;
      els.runPolicyTestButton.textContent = "사이트 판정 실행";
    }
  }
}

async function copySmokeChecklist() {
  await navigator.clipboard.writeText(SMOKE_TEST_CHECKLIST);
  renderStatus("확장 smoke 체크리스트 복사됨");
}

async function initialize() {
  const { settings } = await chrome.storage.sync.get("settings");
  currentSettings = mergeSettings(settings || {});

  renderSettingsToForm(currentSettings);
  await loadDeveloperRuntimeLogEnabled().catch(() => {});
  renderAvatarSlotOptions();
  await renderAvatarImagePreviewList();
  if (els.debugSimulatedHour) {
    els.debugSimulatedHour.value = String(new Date().getHours());
  }
  if (els.policyTestUrl && !els.policyTestUrl.value) {
    els.policyTestUrl.value = SITE_POLICY_TEST_DEFAULT_URL;
  }
  await Promise.all([loadRuntimeState(), refreshConnectionState()]);

  els.saveOptionsButton.addEventListener("click", async () => {
    els.saveOptionsButton.disabled = true;
    try {
      currentSettings = readSettingsFromForm();
      await saveSettingsToSync(currentSettings);
      await refreshConnectionState();
      renderStatus("옵션 저장 완료");
      await runPipelineNowFromOptions();
    } catch (error) {
      renderStatus(`옵션 저장 실패: ${formatUnexpectedError(error)}`);
    } finally {
      els.saveOptionsButton.disabled = false;
    }
  });

  els.refreshJsonButton.addEventListener("click", async () => {
    try {
      await loadRuntimeState();
      renderStatus("최근 JSON 새로고침 완료");
    } catch (error) {
      renderStatus(`최근 JSON 새로고침 실패: ${formatUnexpectedError(error)}`);
    }
  });

  els.runNowButton.addEventListener("click", async () => {
    try {
      await runPipelineNowFromOptions();
    } catch (error) {
      renderStatus(`현재 탭 검사 실패: ${formatUnexpectedError(error)}`);
    }
  });

  if (els.siteProtectionEnabledToggle) {
    els.siteProtectionEnabledToggle.addEventListener("change", updateProtectionDependentControls);
  }

  if (els.backendEnabledToggle) {
    els.backendEnabledToggle.addEventListener("change", async () => {
      updateBackendDependentControls();
      currentSettings = readSettingsFromForm();
      await saveSettingsToSync(currentSettings);
      await refreshConnectionState();
      renderStatus(
        currentSettings.backendEnabled
          ? "백엔드 연동 켜짐"
          : "백엔드 연동 꺼짐"
      );
    });
  }

  if (els.runSelfTestButton) {
    els.runSelfTestButton.addEventListener("click", async () => {
      try {
        await runSelfTestFromOptions();
      } catch (error) {
        renderStatus(`self-test 실패: ${formatUnexpectedError(error)}`);
      }
    });
  }

  if (els.unlockDeveloperButton) {
    els.unlockDeveloperButton.addEventListener("click", unlockDeveloperTools);
  }
  if (els.developerRuntimeLogEnabledToggle) {
    els.developerRuntimeLogEnabledToggle.addEventListener("change", async () => {
      try {
        const enabled = await saveDeveloperRuntimeLogEnabled(els.developerRuntimeLogEnabledToggle.checked);
        renderStatus(enabled ? "개발자 런타임 로그 켜짐" : "개발자 런타임 로그 꺼짐");
      } catch (error) {
        els.developerRuntimeLogEnabledToggle.checked = developerRuntimeLogEnabled;
        renderStatus(`개발자 로그 설정 실패: ${formatUnexpectedError(error)}`);
      }
    });
  }
  if (els.mediaSafetyInterventionModeSelect) {
    els.mediaSafetyInterventionModeSelect.addEventListener("change", async () => {
      currentSettings = readSettingsFromForm();
      await saveSettingsToSync(currentSettings);
      renderStatus(`이미지 처리 방식: ${currentSettings.mediaSafetyInterventionMode}`);
    });
  }
  if (els.developerPassword) {
    els.developerPassword.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        unlockDeveloperTools();
      }
    });
  }
  if (els.applyDebugOverrideButton) {
    els.applyDebugOverrideButton.addEventListener("click", async () => {
      try {
        await applyDeveloperDebugOverride();
      } catch (error) {
        renderStatus(`개발자 테스트 적용 실패: ${formatUnexpectedError(error)}`);
      }
    });
  }
  if (els.clearDebugOverrideButton) {
    els.clearDebugOverrideButton.addEventListener("click", async () => {
      try {
        await clearDeveloperDebugOverride();
      } catch (error) {
        renderStatus(`개발자 테스트 해제 실패: ${formatUnexpectedError(error)}`);
      }
    });
  }
  if (els.resetWellbeingStateButton) {
    els.resetWellbeingStateButton.addEventListener("click", async () => {
      try {
        await resetWellbeingState();
      } catch (error) {
        renderStatus(`위젯 통계 초기화 실패: ${formatUnexpectedError(error)}`);
      }
    });
  }
  if (els.runPolicyTestButton) {
    els.runPolicyTestButton.addEventListener("click", async () => {
      try {
        await runSitePolicyTest();
      } catch (error) {
        renderStatus(`사이트 판정 실패: ${formatUnexpectedError(error)}`);
      }
    });
  }
  if (els.policyTestUrl) {
    els.policyTestUrl.addEventListener("keydown", (event) => {
      if (event.key !== "Enter") {
        return;
      }
      event.preventDefault();
      runSitePolicyTest().catch((error) => {
        renderStatus(`사이트 판정 실패: ${formatUnexpectedError(error)}`);
      });
    });
  }
  if (els.copySmokeChecklistButton) {
    els.copySmokeChecklistButton.addEventListener("click", () => {
      copySmokeChecklist().catch((error) => {
        renderStatus(`체크리스트 복사 실패: ${formatUnexpectedError(error)}`);
      });
    });
  }
  if (els.chooseAvatarImageButton && els.avatarImageFile) {
    els.chooseAvatarImageButton.addEventListener("click", () => {
      els.avatarImageFile.click();
    });
    els.avatarImageFile.addEventListener("change", async () => {
      const file = getImageFileFromTransfer(els.avatarImageFile);
      await handleAvatarImageFile(file);
      els.avatarImageFile.value = "";
    });
  }
  if (els.clearAvatarImageButton) {
    els.clearAvatarImageButton.addEventListener("click", async () => {
      try {
        await clearAvatarImageAsset(els.avatarImageSlot?.value || "default");
      } catch (error) {
        renderStatus(`이미지 삭제 실패: ${formatUnexpectedError(error)}`);
      }
    });
  }
  if (els.avatarPasteZone) {
    els.avatarPasteZone.addEventListener("click", () => {
      els.avatarPasteZone.focus();
    });
    els.avatarPasteZone.addEventListener("paste", async (event) => {
      const file = getImageFileFromTransfer(event.clipboardData);
      if (!file) {
        renderStatus("클립보드에서 이미지 파일을 찾지 못했습니다");
        return;
      }
      event.preventDefault();
      await handleAvatarImageFile(file);
    });
    els.avatarPasteZone.addEventListener("dragover", (event) => {
      event.preventDefault();
      els.avatarPasteZone.classList.add("is-dragging");
    });
    els.avatarPasteZone.addEventListener("dragleave", () => {
      els.avatarPasteZone.classList.remove("is-dragging");
    });
    els.avatarPasteZone.addEventListener("drop", async (event) => {
      event.preventDefault();
      els.avatarPasteZone.classList.remove("is-dragging");
      const file = getImageFileFromTransfer(event.dataTransfer);
      await handleAvatarImageFile(file);
    });
  }
  if (els.wellbeingAvatarImages) {
    els.wellbeingAvatarImages.addEventListener("input", () => {
      renderAvatarImagePreviewList().catch(() => {});
    });
  }
  if (els.avatarImagePreviewList) {
    els.avatarImagePreviewList.addEventListener("click", async (event) => {
      const button = event.target instanceof Element
        ? event.target.closest(".avatar-preview-remove")
        : null;
      if (button instanceof HTMLButtonElement) {
        event.stopPropagation();
        try {
          await clearAvatarImageAsset(button.dataset.avatarKey || "default");
        } catch (error) {
          renderStatus(`이미지 삭제 실패: ${formatUnexpectedError(error)}`);
        }
        return;
      }

      const card = event.target instanceof Element
        ? event.target.closest(".avatar-preview-card")
        : null;
      if (card instanceof HTMLElement) {
        selectAvatarImageSlot(card.dataset.avatarKey || "default");
      }
    });
    els.avatarImagePreviewList.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") {
        return;
      }
      const card = event.target instanceof Element
        ? event.target.closest(".avatar-preview-card")
        : null;
      if (!(card instanceof HTMLElement)) {
        return;
      }
      event.preventDefault();
      selectAvatarImageSlot(card.dataset.avatarKey || "default");
    });
  }
  if (els.avatarImageSlot) {
    els.avatarImageSlot.addEventListener("change", () => {
      selectAvatarImageSlot(els.avatarImageSlot.value, { announce: false });
    });
  }
  for (const stageCountInput of [els.wellbeingAgeStageCount, els.wellbeingAngerStageCount]) {
    if (!stageCountInput) {
      continue;
    }
    stageCountInput.addEventListener("input", () => {
      renderAvatarSlotOptions();
      renderAvatarImagePreviewList().catch(() => {});
    });
  }

  els.checkConnectionButton.addEventListener("click", async () => {
    try {
      currentSettings = readSettingsFromForm();
      await saveSettingsToSync(currentSettings);
      const result = await refreshConnectionState();
      await loadRuntimeLogs().catch(() => {});
      renderStatus(
        result?.ok && result.model_ready !== false
          ? "백엔드 연결 확인 완료"
          : formatConnectionStatus(result)
      );
    } catch (error) {
      renderStatus(`백엔드 연결 확인 실패: ${formatUnexpectedError(error)}`);
    }
  });

  els.refreshRuntimeLogButton?.addEventListener("click", async () => {
    try {
      await loadRuntimeLogs();
      renderStatus("런타임 로그 새로고침 완료");
    } catch (error) {
      renderStatus(`런타임 로그 조회 실패: ${formatUnexpectedError(error)}`);
    }
  });

  els.copyRuntimeLogButton?.addEventListener("click", () => {
    copyRuntimeLogs().catch((error) => {
      renderStatus(`런타임 로그 복사 실패: ${formatUnexpectedError(error)}`);
    });
  });

  els.copyRuntimeLogReportButton?.addEventListener("click", () => {
    copyRuntimeLogReport().catch((error) => {
      renderStatus(`Notion용 로그 복사 실패: ${formatUnexpectedError(error)}`);
    });
  });

  els.addRuntimeNoteButton?.addEventListener("click", () => {
    addRuntimeManualNote().catch((error) => {
      renderStatus(`재현 메모 추가 실패: ${formatUnexpectedError(error)}`);
    });
  });
  els.runtimeManualNote?.addEventListener("keydown", (event) => {
    if ((event.metaKey || event.ctrlKey) && event.key === "Enter") {
      event.preventDefault();
      addRuntimeManualNote().catch((error) => {
        renderStatus(`재현 메모 추가 실패: ${formatUnexpectedError(error)}`);
      });
    }
  });

  els.clearRuntimeLogButton?.addEventListener("click", async () => {
    try {
      await clearRuntimeLogs();
      renderStatus("런타임 로그 삭제됨");
    } catch (error) {
      renderStatus(`런타임 로그 삭제 실패: ${formatUnexpectedError(error)}`);
    }
  });

  chrome.storage.onChanged.addListener((changes, areaName) => {
    if (
      areaName === "local" &&
      (
        changes.lastPayload ||
        changes.lastDecision ||
        changes.lastRunAt ||
        changes.sessionStats ||
        changes.lastPipelineError ||
        changes.lastSelfTest ||
        changes.lastSelfTestHistory ||
        changes.runtimeEventLog ||
        changes[DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY]
      )
    ) {
      if (changes[DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY]) {
        developerRuntimeLogEnabled =
          changes[DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY].newValue === true;
        if (els.developerRuntimeLogEnabledToggle) {
          els.developerRuntimeLogEnabledToggle.checked = developerRuntimeLogEnabled;
        }
      }
      loadRuntimeState().catch(() => {});
      return;
    }

    if (areaName === "sync" && changes.settings?.newValue) {
      currentSettings = mergeSettings(changes.settings.newValue);
      renderSettingsToForm(currentSettings);
      renderAvatarSlotOptions();
      renderAvatarImagePreviewList().catch(() => {});
      refreshConnectionState().catch(() => {});
    }
    if (areaName === "local" && changes[AVATAR_ASSET_STORAGE_KEY]) {
      renderAvatarImagePreviewList().catch(() => {});
    }
  });
}

initialize().catch((error) => {
  console.error(error);
  renderStatus("옵션 로드 실패");
});
