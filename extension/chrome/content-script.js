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
  siteNavigationWarningEnabled: true,
  searchResultProtectionEnabled: true,
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

const CATEGORY_LABELS = {
  abuse: "공격",
  hate: "혐오",
  insult: "모욕",
  spam: "스팸",
  custom: "사용자"
};

const SKIP_TAGS = new Set([
  "SCRIPT",
  "STYLE",
  "NOSCRIPT",
  "TEXTAREA",
  "INPUT",
  "BUTTON",
  "SELECT",
  "OPTION",
  "CODE",
  "PRE",
  "KBD",
  "SAMP",
  "IMG",
  "SVG",
  "CANVAS",
  "VIDEO",
  "AUDIO",
  "IFRAME",
  "FIGCAPTION"
]);

const PIPELINE_DEBOUNCE_MS = 48;
const BACKGROUND_PIPELINE_DEBOUNCE_MS = 180;
const MAX_CANDIDATES = 80;
const MAX_FOREGROUND_CONTAINERS = 5;
const MAX_BACKGROUND_CONTAINERS = 8;
const VIEWPORT_BUFFER_PX = 720;
const SCROLL_REFRESH_TEXT_NODE_LIMIT = 80;
const SCROLL_SETTLE_REFRESH_DELAY_MS = 110;
const SCROLL_LATE_REFRESH_DELAY_MS = 340;
const MAX_ANALYSIS_CONTEXT_LENGTH = 360;
const MAX_RECONCILE_CONTEXT_LENGTH = 560;
const MIN_ANALYSIS_CONTEXT_LENGTH = 24;
const MAX_ANALYSIS_CONTAINER_ASCENT = 5;
const ANALYSIS_CACHE_LIMIT = 500;
const MAX_DIRTY_TEXT_NODES_PER_MUTATION = 40;
const MAX_INITIAL_TEXT_NODES = 120;
const HOT_PATH_WORKER_TIMEOUT_MS = 90;
const HOT_PATH_WORKER_INIT_TIMEOUT_MS = 900;
const HOT_PATH_WORKER_BACKOFF_MS = 8000;
const MAX_HOT_PATH_CONTEXT_LENGTH = 320;
const INPUT_PIPELINE_DEBOUNCE_MS = 0;
const VISIBILITY_PIPELINE_DEBOUNCE_MS = 80;
const RECONCILE_FLUSH_DELAY_MS = 20;
const RECONCILE_FAST_FLUSH_DELAY_MS = 0;
const RECONCILE_CHUNK_SIZE = 2;
const MAX_FOREGROUND_CANDIDATES = 16;
const MAX_FOREGROUND_WAVE_CANDIDATES = 8;
const MAX_FOREGROUND_WAVE_CONTAINERS = 3;
const MAX_BACKGROUND_CANDIDATES = 24;
const MAX_BACKGROUND_VALIDATION_CANDIDATES = 12;
const MAX_BACKGROUND_VALIDATION_ANALYSIS_UNITS = 4;
const MAX_HOT_PATH_CONTAINERS = 8;
const INITIAL_EDITABLE_PASS_LIMIT = 2;
const STARTUP_FOLLOWUP_DELAYS_MS = [650, 1800];
const ROUTE_CHANGE_FOLLOWUP_DELAYS_MS = [160, 520, 1400];
const NAVIGATION_POLL_INTERVAL_MS = 1000;
const INITIAL_ANALYSIS_IDLE_TIMEOUT_MS = 1600;
const SAME_ROUTE_DIRTY_REFRESH_REASONS = new Set([
  "load",
  "pageshow",
  "readystatechange",
  "turbo-load",
  "navigation-api",
  "yt-navigate-start",
  "yt-navigate-finish",
  "yt-page-data-updated"
]);
const GOOGLE_SEARCH_ALLOWED_PIPELINE_REASONS = new Set([
  "input",
  "input-hot-path",
  "initial-editable-pass",
  "google-dynamic-content",
  "manual",
  "manual-request",
  "manual-request-after-inject"
]);
const MAX_DOMAIN_PRIORITY_CANDIDATES = 10;
const MAX_GOOGLE_CANDIDATES_PER_CONTAINER = 16;
const GOOGLE_SEARCH_LIGHT_CANDIDATE_LIMIT = 6;
const GOOGLE_SEARCH_LIGHT_PROTECTION_MIN_INTERVAL_MS = 80;
const GOOGLE_VISIBLE_HIGH_SIGNAL_SCAN_NODE_LIMIT = 240;
const GOOGLE_INITIAL_PRECONCEAL_TTL_MS = 2200;
const GOOGLE_INITIAL_PRECONCEAL_LIMIT = 16;
const SEARCH_RESULT_BACKEND_CHECK_LIMIT = 8;
const SEARCH_RESULT_BACKEND_CHECK_CONCURRENCY = 1;
const SEARCH_RESULT_POLICY_CACHE_LIMIT = 200;
const SEARCH_RESULT_POLICY_CACHE_TTL_MS = 10 * 60 * 1000;
const SEARCH_RESULT_POLICY_ERROR_CACHE_TTL_MS = 30 * 1000;
const SEARCH_RESULT_POLICY_PREEMPTED_CACHE_TTL_MS = 2500;
const SEARCH_RESULT_SNIPPET_LIMIT = 280;
const WELLBEING_EXPLICIT_SCORE_THRESHOLD = 0.72;
const MAX_SELF_TEST_CASES = 32;
const MAX_SELF_TEST_HISTORY = 20;
const FOREGROUND_ANALYZE_TIMEOUT_MS = 650;
const RECONCILE_ANALYZE_TIMEOUT_MS = 1500;
const SKIPPED_ANALYSIS_RETRY_BACKOFF_MS = 1200;
const HIGH_SIGNAL_SKIPPED_RETRY_BACKOFF_MS = 260;
const MAX_HIGH_SIGNAL_SKIPPED_RETRY_ATTEMPTS = 2;
const HIGH_SIGNAL_SKIPPED_RETRY_MAX_BACKOFF_MS = 2200;
const SKIPPED_RETRY_MAX_BACKOFF_MS = 5000;
const PERFORMANCE_GUARD_SLOW_PIPELINE_MS = 900;
const PERFORMANCE_GUARD_CANDIDATE_LIMIT = 100;
const PERFORMANCE_GUARD_UNIT_BUILD_MS = 180;
const PERFORMANCE_GUARD_COOLDOWN_MS = 30 * 1000;
const PERFORMANCE_GUARD_ALLOWED_PIPELINE_REASONS = new Set([
  "input",
  "input-hot-path",
  "initial-editable-pass",
  "google-dynamic-content",
  "manual",
  "manual-request",
  "manual-request-after-inject"
]);
const FOREGROUND_BACKEND_BATCH_SIZE = 4;
const BACKGROUND_VALIDATION_BACKEND_BATCH_SIZE = 4;
const RECONCILE_BACKEND_BATCH_SIZE = 2;
const BACKGROUND_VALIDATION_BACKEND_BUDGET_MS = 140;
const BACKEND_WARMUP_DELAY_MS = 1800;
const BACKEND_WARMUP_REQUEST_TIMEOUT_MS = 8000;
const BACKEND_WARMUP_FALLBACK_TEXTS = ["안녕하세요", "씨발 테스트", "검색 테스트", "청마루 실시간 필터"];
const FOREGROUND_STANDALONE_SAFE_CACHE_TTL_MS = 7000;
const FOREGROUND_CONTEXTUAL_SAFE_CACHE_TTL_MS = 800;
const RECONCILE_CONTEXTUAL_SAFE_CACHE_TTL_MS = 600;
const OFFENSIVE_CACHE_TTL_MS = 90000;
const ANALYSIS_CACHE_SCHEMA_VERSION = "content-v13";
const DECISION_STAGE_RANK = Object.freeze({
  "local-preflight": 0,
  foreground: 1,
  reconcile: 2
});
const SEARCH_RESULT_CURATED_FALLBACK_POLICIES = Object.freeze([
  {
    domain: "google-account-verify.com",
    verdict: "block",
    matchedRule: "phishing smoke seed"
  },
  {
    domain: "adult-webtoon-plus.kr",
    verdict: "block",
    matchedRule: "adult smoke seed"
  },
  {
    domain: "dcinside.com",
    verdict: "warning",
    matchedRule: "community smoke seed"
  }
]);

const TEXT_NODE_ID_MAP = new WeakMap();
const NODE_STATE_BY_ID = new Map();
const EDITABLE_VALUE_ID_MAP = new WeakMap();
const EDITABLE_VALUE_STATE_BY_ID = new Map();
const DIRTY_NODE_IDS = new Set();
const VISIBLE_NODE_IDS = new Set();
const OBSERVED_ELEMENT_NODE_IDS = new WeakMap();
const ANALYSIS_CACHE = new Map();
const MASKED_EDITABLE_STATE_IDS = new Set();
const SKIPPED_RETRY_NODE_IDS = new Map();

const SAFE_BROWSER_UI_LABELS = new Set([
  ".github",
  ".gitignore",
  "actions",
  "activity",
  "agents",
  "android",
  "backend",
  "code",
  "contributors",
  "docs",
  "fork",
  "insights",
  "issues",
  "packages",
  "projects",
  "public",
  "pull requests",
  "readme",
  "readme.md",
  "scripts",
  "security & quality",
  "security and quality",
  "settings",
  "shared",
  "star",
  "watch",
  "wiki"
]);

// Backend remains the authority; this mirror only prioritizes obvious candidates
// and prevents trusted backend spans from being discarded by frontend sanity checks.
const KOREAN_WORD_CHAR_CLASS = "0-9A-Za-z가-힣ㄱ-ㅎㅏ-ㅣ";
const KOREAN_PROFANITY_LEFT_EDGE = `(?<![${KOREAN_WORD_CHAR_CLASS}])`;
const KOREAN_PROFANITY_RIGHT_EDGE = `(?![${KOREAN_WORD_CHAR_CLASS}])`;
const KOREAN_PROFANITY_INFLECTION_SUFFIX = "(?:(?:이라는|이라고|이라|이란)|(?:하고|하며|해서|하면|하다|한|할|해)|(?:같은|같다|같네|처럼)|(?:새끼|새키)|[이가은는을를의아야]|[놈년련들])?";

function koreanProfanity(pattern) {
  return `${KOREAN_PROFANITY_LEFT_EDGE}(?:${pattern})${KOREAN_PROFANITY_RIGHT_EDGE}`;
}

function koreanProfanityWithInflection(pattern) {
  return `${KOREAN_PROFANITY_LEFT_EDGE}(?:${pattern})${KOREAN_PROFANITY_INFLECTION_SUFFIX}${KOREAN_PROFANITY_RIGHT_EDGE}`;
}

const KOREAN_INITIAL_SEPARATOR = "[\\s._/·ㆍ|:;,'’()\\[\\]{}<>-]*";
const KOREAN_OBFUSCATION_SEPARATOR = "[\\s._/·ㆍ|:;,'’()\\[\\]{}<>-]*";
const KOREAN_ADULT_COMMERCE_SEPARATOR = "[\\s._/·ㆍ|:;,'’()\\[\\]{}<>-]*";
const KOREAN_ADULT_COMMERCE_PATTERN =
  `(?:콜${KOREAN_ADULT_COMMERCE_SEPARATOR}걸|성인${KOREAN_ADULT_COMMERCE_SEPARATOR}(?:업소|마사지${KOREAN_ADULT_COMMERCE_SEPARATOR}(?:업소|샵|구인|후기|추천|가격|예약|콜|출장|불법|알선))|출장${KOREAN_ADULT_COMMERCE_SEPARATOR}(?:마사지|안마|만남)|유흥${KOREAN_ADULT_COMMERCE_SEPARATOR}(?:업소|마사지|주점)|안마${KOREAN_ADULT_COMMERCE_SEPARATOR}방|키스${KOREAN_ADULT_COMMERCE_SEPARATOR}방|립${KOREAN_ADULT_COMMERCE_SEPARATOR}카페|룸${KOREAN_ADULT_COMMERCE_SEPARATOR}(?:싸롱|살롱)|셔츠${KOREAN_ADULT_COMMERCE_SEPARATOR}룸|조건${KOREAN_ADULT_COMMERCE_SEPARATOR}만남|성${KOREAN_ADULT_COMMERCE_SEPARATOR}매매)`;

function koreanSeparatedSyllables(...parts) {
  return parts.join(KOREAN_OBFUSCATION_SEPARATOR);
}

const HIGH_SIGNAL_PROFANITY_PATTERN = new RegExp(
  [
    koreanProfanityWithInflection(`씨[이]*${KOREAN_OBFUSCATION_SEPARATOR}발`),
    koreanProfanityWithInflection(`시[이]*${KOREAN_OBFUSCATION_SEPARATOR}발`),
    koreanProfanityWithInflection(`씨[이]*${KOREAN_OBFUSCATION_SEPARATOR}팔`),
    koreanProfanityWithInflection(`시[이]*${KOREAN_OBFUSCATION_SEPARATOR}팔`),
    koreanProfanity(`ㅅ${KOREAN_INITIAL_SEPARATOR}ㅂ`),
    koreanProfanity(`ㅆ${KOREAN_INITIAL_SEPARATOR}ㅂ`),
    koreanProfanityWithInflection(koreanSeparatedSyllables("병", "신")),
    koreanProfanity(`ㅂ${KOREAN_INITIAL_SEPARATOR}ㅅ(?:같(?:다|은|네)?)?`),
    koreanProfanityWithInflection(`지[이]*${KOREAN_OBFUSCATION_SEPARATOR}랄`),
    koreanProfanity(`ㅈ${KOREAN_INITIAL_SEPARATOR}ㄹ(?:하(?:네|다|고|면|며|니)?)?`),
    koreanProfanityWithInflection(koreanSeparatedSyllables("존", "나")),
    koreanProfanity(`ㅈ${KOREAN_INITIAL_SEPARATOR}ㄴ`),
    koreanProfanity(`ㅈ${KOREAN_INITIAL_SEPARATOR}(?:(?:되|된|돼|됐|대|댄|댔|댐)(?:다|네|고|면|며|니|는|서|어|겠(?:다|네)|버(?:렸(?:다|네)?|리(?:다|네|고|면)?|린|림|려)?)?|같(?:다|은|네|이)?)`),
    koreanProfanity(`좆${KOREAN_OBFUSCATION_SEPARATOR}(?:(?:되|된|돼|됐|대|댄|댔|댐)(?:다|네|고|면|며|니|는|서|어|겠(?:다|네)|버(?:렸(?:다|네)?|리(?:다|네|고|면)?|린|림|려)?)?)`),
    koreanProfanity(`좇${KOREAN_OBFUSCATION_SEPARATOR}(?:(?:되|된|돼|됐|대|댄|댔|댐)(?:다|네|고|면|며|니|는|서|어|겠(?:다|네)|버(?:렸(?:다|네)?|리(?:다|네|고|면)?|린|림|려)?)?)`),
    koreanProfanityWithInflection("좆"),
    koreanProfanityWithInflection("좇"),
    koreanProfanityWithInflection("씹"),
    koreanProfanity(`씹${KOREAN_OBFUSCATION_SEPARATOR}감${KOREAN_OBFUSCATION_SEPARATOR}다${KOREAN_OBFUSCATION_SEPARATOR}살[ㅋㅎ]*`),
    koreanProfanityWithInflection(koreanSeparatedSyllables("맘", "충")),
    koreanProfanityWithInflection(koreanSeparatedSyllables("한", "남", "충")),
    koreanProfanityWithInflection(koreanSeparatedSyllables("틀", "딱")),
    koreanProfanityWithInflection(koreanSeparatedSyllables("쪽", "바", "리")),
    koreanProfanityWithInflection(koreanSeparatedSyllables("짱", "깨")),
    koreanProfanityWithInflection(koreanSeparatedSyllables("짱", "개")),
    koreanProfanityWithInflection(koreanSeparatedSyllables("니", "엄", "마")),
    koreanProfanityWithInflection(koreanSeparatedSyllables("느", "금", "마")),
    koreanProfanityWithInflection(`너${KOREAN_OBFUSCATION_SEPARATOR}[희히]${KOREAN_OBFUSCATION_SEPARATOR}엄${KOREAN_OBFUSCATION_SEPARATOR}마`),
    koreanProfanityWithInflection(`개${KOREAN_OBFUSCATION_SEPARATOR}[새세]${KOREAN_OBFUSCATION_SEPARATOR}[끼키]`),
    koreanProfanity(`꺼${KOREAN_OBFUSCATION_SEPARATOR}[져저](?:라)?`),
    koreanProfanity(`닥${KOREAN_OBFUSCATION_SEPARATOR}(?:쳐|치)(?:라)?`),
    koreanProfanity(`죽${KOREAN_OBFUSCATION_SEPARATOR}어(?:라)?`),
    koreanProfanity(`(?:뒤|디|뒈)${KOREAN_OBFUSCATION_SEPARATOR}[져저](?:라)?`),
    koreanProfanity(`(?:뒤|디|뒈)${KOREAN_OBFUSCATION_SEPARATOR}지(?:긴(?:(?:하|한)(?:다|네|고|면)?)?|다|네|고|면|냐|겠(?:다|네)?|는|게)?`),
    koreanProfanity(koreanSeparatedSyllables("뒤", "질", "래")),
    koreanProfanityWithInflection("죽여(?:버릴|버린|버리|버려|버림)?"),
    koreanProfanityWithInflection(`느${KOREAN_OBFUSCATION_SEPARATOR}[금끔]${KOREAN_OBFUSCATION_SEPARATOR}마`),
    koreanProfanityWithInflection(`니${KOREAN_OBFUSCATION_SEPARATOR}[금끔]${KOREAN_OBFUSCATION_SEPARATOR}마`),
    koreanProfanityWithInflection("미친"),
    koreanProfanityWithInflection(`미${KOREAN_OBFUSCATION_SEPARATOR}친${KOREAN_OBFUSCATION_SEPARATOR}(?:놈|년|새${KOREAN_OBFUSCATION_SEPARATOR}(?:끼|키)?)`),
    KOREAN_ADULT_COMMERCE_PATTERN,
    "блядь",
    "сука",
    "くそ",
    "死ね",
    "操你妈",
    "傻逼",
    "كسمك",
    "(?<![A-Za-z])(?:ssibal|shi[\\s_-]*bal|(?<!kapil\\s)sibal|tlqkf|qudtls|byungsin|byeongsin|gae[\\s_-]*sae?k(?:ki)?|rotoRI|whssk|michin|kkeo[\\s_-]*jo|jiral|jonna|nigaumma|negeumma|fuck(?:ing|er|ed)?|shit(?:ty|head|s)?|bitch(?:es)?|ass[\\s_-]*hole|bastard(?:s)?|mother[\\s_-]*fucker|dick|pussy|slut|whore|puta|mierda|putain|ta\\s+gueule|schei(?:ss|ß)e|arschloch|porra|caralho|orospu|nigga|faggot|retard)(?![A-Za-z])"
  ].join("|"),
  "i"
);
const HIGH_SIGNAL_PROFANITY_SPAN_PATTERN = new RegExp(HIGH_SIGNAL_PROFANITY_PATTERN.source, "gi");
const SAFE_HIGH_SIGNAL_COMPOUND_PATTERN = /시[이]*발(?:점|역|택시)/i;
const SAFE_HIGH_SIGNAL_COMPOUND_FRAGMENT_PATTERN = /^시[이]*발$/i;
const SAFE_HIGH_SIGNAL_CONTEXT_PATTERN = /(?:국제차량제작\s+시[이]*발|국가중요과학기술자료\s*#?시[이]*발|시[이]*발\.\s*1955년[\s\S]{0,80}자동차)/i;
const GOOGLE_SFC_CONTAINER_SELECTOR = [
  "[data-container-id='main-col']",
  "[data-container-id='main-col'] .n6owBd",
  "[data-container-id='main-col'] .MFrAxb",
  "[data-container-id='main-col'] .EJw9bc",
  "[data-container-id='main-col'] .jydCyd",
  "[data-sfc-root='c'] .n6owBd",
  "[data-sfc-root='c'] .MFrAxb",
  "[data-sfc-root='c'] .EJw9bc",
  "[data-sfc-root='c'] .jydCyd",
  ".mZJni.Dn7Fzd",
  ".n6owBd.awi2gc"
].join(", ");
const GOOGLE_SFC_TEXT_SELECTOR = [
  "[data-container-id='main-col'] [data-subtree]",
  "[data-container-id='main-col'] mark.HxTRcb",
  "[data-container-id='main-col'] .HxTRcb",
  "[data-container-id='main-col'] .NDNGvf",
  "[data-container-id='main-col'] .n6owBd",
  "[data-container-id='main-col'] .MFrAxb",
  "[data-sfc-root='c'] [data-subtree]",
  "[data-sfc-root='c'] mark.HxTRcb",
  "[data-sfc-root='c'] .HxTRcb",
  "[data-sfc-root='c'] .NDNGvf",
  "[data-sfc-root='c'] .n6owBd",
  "[data-sfc-root='c'] .MFrAxb"
].join(", ");
const GOOGLE_AI_OVERVIEW_SELECTOR = [
  "[aria-label*='AI 개요' i]",
  "[aria-label*='AI Overview' i]",
  "[data-attrid*='ai_overview' i]",
  "[data-attrid*='AI Overview' i]",
  "[data-mcpr]",
  "[data-content-feature='1']"
].join(", ");
const GOOGLE_HIGH_SIGNAL_TEXT_SELECTOR = [
  GOOGLE_AI_OVERVIEW_SELECTOR,
  "[role='heading'][data-attrid='title']",
  "[data-attrid='title'] [role='heading']",
  "[data-attrid='title']",
  "[data-attrid='description']",
  "[data-attrid='kc:/common/topic/description']",
  "main [role='heading']",
  "main [aria-level]",
  "main .PZPZlf",
  "main .B5dxMb",
  "main .VwiC3b",
  "main .MUxGbd",
  "main [data-sncf]",
  "main [data-snf]",
  "main [data-content-feature='1']",
  "#rhs [role='heading']",
  "#rhs [aria-level]",
  "#rhs [data-attrid]",
  "#rhs [data-subtree]",
  "#rhs [data-attrid='title']",
  "#rhs [data-attrid='description']",
  "#rhs [data-attrid='kc:/common/topic/description']",
  "#rhs mark.HxTRcb",
  "#rhs .HxTRcb",
  "#rhs .PZPZlf",
  "#rhs .B5dxMb",
  "#rhs .kno-rdesc",
  "#rhs .IZ6rdc",
  "#rhs .wDYxhc",
  "#kp-wp-tab-overview [role='heading']",
  "#kp-wp-tab-overview [data-subtree]",
  "#kp-wp-tab-overview [data-attrid]",
  "#kp-wp-tab-overview [data-attrid='title']",
  "#kp-wp-tab-overview [data-attrid='description']",
  "#kp-wp-tab-overview mark.HxTRcb",
  "#kp-wp-tab-overview .HxTRcb",
  "[data-container-id='main-col'] [role='heading']",
  "[data-container-id='main-col'] [aria-level]",
  "[data-container-id='main-col'] [data-subtree]",
  "[data-container-id='main-col'] [data-attrid]",
  "[data-container-id='main-col'] .PZPZlf",
  "[data-container-id='main-col'] .VwiC3b",
  "[data-container-id='main-col'] .MUxGbd",
  "[data-sfc-root='c'] [role='heading']",
  "[data-sfc-root='c'] [aria-level]",
  "[data-sfc-root='c'] [data-subtree]",
  "[data-sfc-root='c'] [data-attrid]",
  "[data-sfc-root='c'] .PZPZlf",
  "[data-sfc-root='c'] .VwiC3b",
  "[data-sfc-root='c'] .MUxGbd",
  ".PZPZlf.ssJ7i",
  ".PZPZlf.B5dxMb"
].join(", ");
const GOOGLE_DYNAMIC_CONTENT_SELECTOR = [
  GOOGLE_AI_OVERVIEW_SELECTOR,
  GOOGLE_SFC_CONTAINER_SELECTOR,
  GOOGLE_SFC_TEXT_SELECTOR,
  GOOGLE_HIGH_SIGNAL_TEXT_SELECTOR,
  "#search h3",
  "#search [role='heading']",
  "#search .VwiC3b",
  "#search .MUxGbd",
  "#search [data-sncf]",
  "#search [data-snf]",
  "#rso h3",
  "#rso [role='heading']",
  "#rso .VwiC3b",
  "#rso .MUxGbd",
  "#rso [data-sncf]",
  "#rso [data-snf]",
  "#bres",
  "#botstuff",
  "#rhs"
].join(", ");
const YOUTUBE_HIGH_SIGNAL_TEXT_SELECTOR = [
  "#content-text",
  "[id='content-text']",
  "ytd-comment-thread-renderer #content-text",
  "ytd-comment-thread-renderer [id='content-text']",
  "ytd-comment-view-model #content-text",
  "ytd-comment-view-model [id='content-text']",
  "ytd-watch-metadata h1",
  "ytd-watch-metadata #title",
  "ytd-watch-metadata [id='title']",
  "yt-formatted-string#video-title",
  "#video-title",
  "[id='video-title']",
  "ytd-video-renderer #video-title",
  "ytd-rich-item-renderer #video-title",
  "ytd-compact-video-renderer #video-title"
].join(", ");

let nextTextNodeId = 1;
let nextEditableValueId = 1;
let nextAttributeValueId = 1;
let observer = null;
let visibilityObserver = null;
let debounceTimerId = null;
let scheduledPipelineReason = "";
let scheduledPipelineDeadlineMs = 0;
let isPipelineRunning = false;
let queuedReason = null;
let ignoreMutationsUntil = 0;
let latestPipelineSequence = 0;
let latestAnalysisGeneration = 0;
let settingsRevision = 0;
let cachedSettings = null;
let sitePolicyOverlayElement = null;
let lastSitePolicyUrl = "";
let settingsLoadPromise = null;
let extensionContextInvalidated = false;
let realtimeWorkerStatus = "idle";
let realtimeWorkerFailure = null;
let realtimeWorkerBackoffUntil = 0;
let realtimeWorkerInitLatencyMs = 0;
let realtimeWorkerStrategy = null;
let pendingImmediateInputElement = null;
let immediateInputTimerId = null;
let initialEditablePassFrameId = null;
let overlaySyncFrameId = null;
let pendingEditableOverlaySyncFrames = 0;
let scrollVisibilityRefreshFrameId = null;
let scrollVisibilityRefreshSettleTimerId = null;
let scrollVisibilityRefreshLateTimerId = null;
let suppressedMutationRefreshTimerId = null;
let skippedAnalysisRetryTimerId = null;
let skippedAnalysisRetryDueAt = 0;
let reconcileFlushTimerId = null;
let scheduledReconcileDelayMs = 0;
let isReconcileRunning = false;
let hotPathStatsPersistTimerId = null;
let pendingHotPathStats = null;
const RECONCILE_QUEUE = new Map();
let bootstrapStarted = false;
let bootstrapRetryTimerId = null;
let initialPageAnalysisScheduled = false;
let initialPageAnalysisStarted = false;
let navigationListenersInitialized = false;
let routeRefreshFrameId = null;
let searchResultProtectionFrameId = null;
let googleSearchLocalPreflightFrameId = null;
let googleSearchLocalPreflightTimerId = null;
let searchResultProtectionClickGuardInitialized = false;
let searchResultProtectionRunId = 0;
let lastGoogleSearchLocalPreflightAt = 0;
let lastGoogleSearchLocalPreflightHref = "";
let navigationPollTimerId = null;
let routeRefreshSequence = 0;
const ROUTE_REFRESH_TIMEOUT_IDS = new Set();
const STARTUP_FOLLOWUP_TIMEOUT_IDS = new Set();
const SEARCH_RESULT_POLICY_CACHE = new Map();
const SEARCH_RESULT_POLICY_IN_FLIGHT = new Map();
const ATTRIBUTE_MASK_NAMES = ["aria-label", "title", "alt"];
const ATTRIBUTE_VALUE_ID_MAP = new WeakMap();
const ATTRIBUTE_VALUE_STATE_BY_ID = new Map();
let lastObservedLocationHref = String(location.href || "");
let staleResponseDropCount = 0;
let foregroundApplyCount = 0;
let reconcileOverwriteCount = 0;
let reconcileUnmaskCount = 0;
let inputMaskResetCount = 0;
let editableMaskCarryForwardCount = 0;
let skippedHighSignalRetryCount = 0;
let skippedHighSignalRetrySuppressedCount = 0;
let managedMutationSkipCount = 0;
let overlayLayoutReuseCount = 0;
let overlayLayoutRebuildCount = 0;
let backendWarmupStarted = false;
let extensionContextInvalidatedLogged = false;
let lastAppliedSettingsSnapshotKey = "";
let lastAppliedSettingsSnapshotAt = 0;
let performanceGuardUntil = 0;
let performanceGuardReason = "";

function normalizeText(value) {
  return String(value || "").replace(/\s+/g, " ").trim();
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

function getSensitivityMode(settings) {
  if (settings?.enabled === false) return "off";
  return normalizeSensitivity(settings?.sensitivity) <= 0 ? "disabled" : "normal";
}

function getSensitivityScoreThreshold(settings) {
  const sensitivity = normalizeSensitivity(settings?.sensitivity);
  if (sensitivity <= 0) return 1.01;
  return Math.max(0.35, Math.min(0.9, 0.95 - sensitivity * 0.006));
}

function isFilteringSuppressedBySensitivity(settings) {
  return normalizeSensitivity(settings?.sensitivity) <= 0;
}

function buildSettingsSnapshotKey(settings) {
  const normalizedSettings = getMergedSettings(settings || {});
  return JSON.stringify({
    enabled: normalizedSettings.enabled !== false,
    sensitivity: normalizeSensitivity(normalizedSettings.sensitivity),
    interventionMode: normalizeInterventionMode(normalizedSettings.interventionMode),
    categories: normalizedSettings.categories || DEFAULT_SETTINGS.categories,
    customBlockWords: String(normalizedSettings.customBlockWords || ""),
    customAllowWords: String(normalizedSettings.customAllowWords || ""),
    blockedDomains: String(normalizedSettings.blockedDomains || ""),
    warnDomains: String(normalizedSettings.warnDomains || ""),
    siteProtectionEnabled: normalizedSettings.siteProtectionEnabled !== false,
    siteNavigationWarningEnabled: normalizedSettings.siteNavigationWarningEnabled !== false,
    searchResultProtectionEnabled: normalizedSettings.searchResultProtectionEnabled !== false,
    showWellbeingWidget: normalizedSettings.showWellbeingWidget !== false,
    wellbeingWidgetStyle: normalizedSettings.wellbeingWidgetStyle || DEFAULT_SETTINGS.wellbeingWidgetStyle,
    wellbeingAvatarImages: String(normalizedSettings.wellbeingAvatarImages || ""),
    wellbeingAgeStageCount: normalizeWellbeingStageCount(
      normalizedSettings.wellbeingAgeStageCount,
      DEFAULT_SETTINGS.wellbeingAgeStageCount
    ),
    wellbeingAgeMinutesPerStage: normalizeWellbeingStageStep(
      normalizedSettings.wellbeingAgeMinutesPerStage,
      DEFAULT_SETTINGS.wellbeingAgeMinutesPerStage,
      5,
      240
    ),
    wellbeingAngerStageCount: normalizeWellbeingStageCount(
      normalizedSettings.wellbeingAngerStageCount,
      DEFAULT_SETTINGS.wellbeingAngerStageCount
    ),
    wellbeingAngerDetectionsPerStage: normalizeWellbeingStageStep(
      normalizedSettings.wellbeingAngerDetectionsPerStage,
      DEFAULT_SETTINGS.wellbeingAngerDetectionsPerStage,
      1,
      50
    ),
    backendEnabled: normalizedSettings.backendEnabled === true,
    backendApiBaseUrl: sanitizeApiBaseUrl(normalizedSettings.backendApiBaseUrl),
    requestTimeoutMs: normalizeRequestTimeoutMs(normalizedSettings.requestTimeoutMs)
  });
}

function shouldSkipDuplicateSettingsSnapshot(settings) {
  const snapshotKey = buildSettingsSnapshotKey(settings);
  const now = Date.now();
  const isDuplicate =
    snapshotKey === lastAppliedSettingsSnapshotKey &&
    now - Number(lastAppliedSettingsSnapshotAt || 0) < 750;

  lastAppliedSettingsSnapshotKey = snapshotKey;
  lastAppliedSettingsSnapshotAt = now;
  return isDuplicate;
}

function bumpSettingsRevision() {
  settingsRevision += 1;
  return settingsRevision;
}

function isSettingsRevisionCurrent(revision) {
  return Number(revision || 0) === Number(settingsRevision || 0);
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

function normalizeLabel(value) {
  return normalizeText(value).toLowerCase();
}

function parseWordList(value) {
  return String(value || "")
    .split(/[\n,\t]+/)
    .map((item) => normalizeText(item))
    .filter(Boolean);
}

function isExtensionContextAvailable() {
  if (extensionContextInvalidated) return false;

  try {
    return Boolean(globalThis.chrome?.runtime?.id);
  } catch {
    return false;
  }
}

function isExtensionContextInvalidatedError(error) {
  const message = String(error?.message || error || "");
  return message.includes("Extension context invalidated");
}

function teardownInvalidatedExtensionContext() {
  extensionContextInvalidated = true;

  if (observer) {
    observer.disconnect();
    observer = null;
  }
  if (visibilityObserver) {
    visibilityObserver.disconnect();
    visibilityObserver = null;
  }
  if (debounceTimerId) {
    window.clearTimeout(debounceTimerId);
    debounceTimerId = null;
  }
  scheduledPipelineReason = "";
  scheduledPipelineDeadlineMs = 0;
  if (reconcileFlushTimerId) {
    window.clearTimeout(reconcileFlushTimerId);
    reconcileFlushTimerId = null;
  }
  if (hotPathStatsPersistTimerId) {
    window.clearTimeout(hotPathStatsPersistTimerId);
    hotPathStatsPersistTimerId = null;
  }
  if (immediateInputTimerId) {
    window.cancelAnimationFrame(immediateInputTimerId);
    immediateInputTimerId = null;
  }
  if (initialEditablePassFrameId) {
    window.cancelAnimationFrame(initialEditablePassFrameId);
    initialEditablePassFrameId = null;
  }
  if (overlaySyncFrameId) {
    window.cancelAnimationFrame(overlaySyncFrameId);
    overlaySyncFrameId = null;
  }
  if (scrollVisibilityRefreshFrameId) {
    window.cancelAnimationFrame(scrollVisibilityRefreshFrameId);
    scrollVisibilityRefreshFrameId = null;
  }
  if (scrollVisibilityRefreshSettleTimerId) {
    window.clearTimeout(scrollVisibilityRefreshSettleTimerId);
    scrollVisibilityRefreshSettleTimerId = null;
  }
  if (scrollVisibilityRefreshLateTimerId) {
    window.clearTimeout(scrollVisibilityRefreshLateTimerId);
    scrollVisibilityRefreshLateTimerId = null;
  }
  if (bootstrapRetryTimerId) {
    window.clearTimeout(bootstrapRetryTimerId);
    bootstrapRetryTimerId = null;
  }
  if (routeRefreshFrameId) {
    window.cancelAnimationFrame(routeRefreshFrameId);
    routeRefreshFrameId = null;
  }
  if (googleSearchLocalPreflightFrameId) {
    window.cancelAnimationFrame(googleSearchLocalPreflightFrameId);
    googleSearchLocalPreflightFrameId = null;
  }
  if (suppressedMutationRefreshTimerId) {
    window.clearTimeout(suppressedMutationRefreshTimerId);
    suppressedMutationRefreshTimerId = null;
  }
  if (skippedAnalysisRetryTimerId) {
    window.clearTimeout(skippedAnalysisRetryTimerId);
    skippedAnalysisRetryTimerId = null;
    skippedAnalysisRetryDueAt = 0;
  }
  SKIPPED_RETRY_NODE_IDS.clear();
  if (navigationPollTimerId) {
    window.clearInterval(navigationPollTimerId);
    navigationPollTimerId = null;
  }
  for (const timeoutId of ROUTE_REFRESH_TIMEOUT_IDS) {
    window.clearTimeout(timeoutId);
  }
  ROUTE_REFRESH_TIMEOUT_IDS.clear();
  clearStartupFollowupPipelines();

  cleanupRealtimeWorker();
}

function handleExtensionContextError(error) {
  if (!isExtensionContextInvalidatedError(error)) {
    return false;
  }

  teardownInvalidatedExtensionContext();
  if (!extensionContextInvalidatedLogged) {
    extensionContextInvalidatedLogged = true;
    console.warn("[청마루] extension context invalidated");
  }
  return true;
}

async function safeStorageSyncGet(keys) {
  if (!isExtensionContextAvailable()) return {};

  try {
    return await chrome.storage.sync.get(keys);
  } catch (error) {
    if (handleExtensionContextError(error)) {
      return {};
    }
    throw error;
  }
}

async function safeStorageLocalGet(keys) {
  if (!isExtensionContextAvailable()) return {};

  try {
    return await chrome.storage.local.get(keys);
  } catch (error) {
    if (handleExtensionContextError(error)) {
      return {};
    }
    throw error;
  }
}

async function safeStorageSyncSet(value) {
  if (!isExtensionContextAvailable()) return;

  try {
    await chrome.storage.sync.set(value);
  } catch (error) {
    if (!handleExtensionContextError(error)) {
      throw error;
    }
  }
}

async function safeStorageLocalSet(value) {
  if (!isExtensionContextAvailable()) return;

  try {
    await chrome.storage.local.set(value);
  } catch (error) {
    if (!handleExtensionContextError(error)) {
      throw error;
    }
  }
}

async function safeRuntimeSendMessage(message) {
  if (!isExtensionContextAvailable()) return null;

  try {
    return await chrome.runtime.sendMessage(message);
  } catch (error) {
    if (handleExtensionContextError(error)) {
      return null;
    }
    throw error;
  }
}

function getRuntimeUrl(path) {
  if (!isExtensionContextAvailable()) {
    throw new Error("EXTENSION_CONTEXT_INVALIDATED");
  }

  return chrome.runtime.getURL(path);
}

function isSpeculationRulesElement(element) {
  return (
    element instanceof HTMLScriptElement &&
    String(element.type || "").toLowerCase() === "speculationrules"
  );
}

function isShieldTextManagedElement(element) {
  if (!(element instanceof Element)) {
    return false;
  }

  return Boolean(
    element.closest(
      ".shieldtext-editable-overlay, .shieldtext-site-policy-overlay, [data-shieldtext-rendered='true'], [data-shieldtext-wrapper='true'], [data-shieldtext-overlay='true']"
    )
  );
}

function isShieldTextManagedNode(node) {
  if (node instanceof Text) {
    return isShieldTextManagedElement(node.parentElement);
  }

  if (node instanceof Element) {
    return isShieldTextManagedElement(node);
  }

  if (node instanceof DocumentFragment) {
    const childNodes = [...node.childNodes];
    return childNodes.length > 0 && childNodes.every((child) => isShieldTextManagedNode(child));
  }

  return false;
}

function isShieldTextManagedMutation(mutation) {
  if (!mutation) return false;

  if (isShieldTextManagedNode(mutation.target)) {
    return true;
  }

  const changedNodes = [
    ...Array.from(mutation.addedNodes || []),
    ...Array.from(mutation.removedNodes || [])
  ];

  return changedNodes.length > 0 && changedNodes.every((node) => isShieldTextManagedNode(node));
}

// runtime-status helpers are loaded from content-runtime-status.js

function isUnsupportedDocumentTarget() {
  if (!location || !location.href) return true;
  if (location.protocol === "chrome:" || location.protocol === "chrome-extension:") return true;
  if ((document.contentType || "").toLowerCase().includes("pdf")) return true;
  return false;
}

function isUnsupportedPage() {
  if (!document.body) return true;
  return isUnsupportedDocumentTarget();
}

function parseDomainList(rawValue) {
  return String(rawValue || "")
    .split(/[\n,]/)
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);
}

function normalizeDomainForPolicy(value) {
  const domain = String(value || "").trim().toLowerCase();
  return domain.startsWith("www.") ? domain.slice(4) : domain;
}

function matchDomainRule(domain, rawValue) {
  const normalizedDomain = normalizeDomainForPolicy(domain);
  const rules = parseDomainList(rawValue);
  for (const rule of rules) {
    const normalizedRule = normalizeDomainForPolicy(rule);
    if (!normalizedRule) continue;
    if (normalizedDomain === normalizedRule || normalizedDomain.endsWith(`.${normalizedRule}`)) {
      return normalizedRule;
    }
  }
  return "";
}

function parseHttpUrl(value) {
  try {
    const parsed = new URL(String(value || ""), location.href);
    if (!/^https?:$/i.test(parsed.protocol)) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

function domainFromHref(value) {
  const parsed = parseHttpUrl(value);
  return parsed ? normalizeDomainForPolicy(parsed.hostname) : "";
}

function getMergedSettings(storedSettings) {
  return {
    ...DEFAULT_SETTINGS,
    ...(storedSettings || {}),
    interventionMode: normalizeInterventionMode(storedSettings?.interventionMode),
    siteProtectionEnabled: storedSettings?.siteProtectionEnabled !== false,
    siteNavigationWarningEnabled: storedSettings?.siteNavigationWarningEnabled !== false,
    searchResultProtectionEnabled: storedSettings?.searchResultProtectionEnabled !== false,
    wellbeingAvatarImages: String(storedSettings?.wellbeingAvatarImages || ""),
    wellbeingAgeStageCount: normalizeWellbeingStageCount(
      storedSettings?.wellbeingAgeStageCount,
      DEFAULT_SETTINGS.wellbeingAgeStageCount
    ),
    wellbeingAgeMinutesPerStage: normalizeWellbeingStageStep(
      storedSettings?.wellbeingAgeMinutesPerStage,
      DEFAULT_SETTINGS.wellbeingAgeMinutesPerStage,
      5,
      240
    ),
    wellbeingAngerStageCount: normalizeWellbeingStageCount(
      storedSettings?.wellbeingAngerStageCount,
      DEFAULT_SETTINGS.wellbeingAngerStageCount
    ),
    wellbeingAngerDetectionsPerStage: normalizeWellbeingStageStep(
      storedSettings?.wellbeingAngerDetectionsPerStage,
      DEFAULT_SETTINGS.wellbeingAngerDetectionsPerStage,
      1,
      50
    ),
    backendEnabled: storedSettings?.backendEnabled === true,
    backendApiBaseUrl: sanitizeApiBaseUrl(storedSettings?.backendApiBaseUrl),
    requestTimeoutMs: normalizeRequestTimeoutMs(storedSettings?.requestTimeoutMs),
    sensitivity: normalizeSensitivity(storedSettings?.sensitivity),
    categories: {
      ...DEFAULT_SETTINGS.categories,
      ...(storedSettings?.categories || {})
    }
  };
}

function updateCachedSettings(storedSettings) {
  cachedSettings = getMergedSettings(storedSettings || {});
  return cachedSettings;
}

function removeSitePolicyOverlay() {
  if (sitePolicyOverlayElement?.parentNode) {
    sitePolicyOverlayElement.parentNode.removeChild(sitePolicyOverlayElement);
  }
  sitePolicyOverlayElement = null;
}

function appendSitePolicyFace(parent, verdict) {
  const widget = document.createElement("div");
  widget.className = "shieldtext-site-policy-widget";
  widget.dataset.mood = verdict === "block" ? "furious" : "annoyed";
  widget.dataset.ageLevel = "0";
  widget.dataset.angerLevel = verdict === "block" ? "5" : "2";
  widget.dataset.policyVerdict = verdict === "block" ? "block" : "warning";
  widget.setAttribute("aria-hidden", "true");

  const face = document.createElement("div");
  face.className = "shieldtext-wellbeing-face";

  const hair = document.createElement("span");
  hair.className = "shieldtext-wellbeing-hair";
  face.appendChild(hair);

  const wrinkle = document.createElement("span");
  wrinkle.className = "shieldtext-wellbeing-wrinkle";
  face.appendChild(wrinkle);

  const leftBrow = document.createElement("span");
  leftBrow.className = "shieldtext-wellbeing-brow left";
  face.appendChild(leftBrow);

  const rightBrow = document.createElement("span");
  rightBrow.className = "shieldtext-wellbeing-brow right";
  face.appendChild(rightBrow);

  const leftEye = document.createElement("span");
  leftEye.className = "shieldtext-wellbeing-eye left";
  face.appendChild(leftEye);

  const rightEye = document.createElement("span");
  rightEye.className = "shieldtext-wellbeing-eye right";
  face.appendChild(rightEye);

  const mouth = document.createElement("span");
  mouth.className = "shieldtext-wellbeing-mouth";
  face.appendChild(mouth);

  widget.appendChild(face);
  parent.appendChild(widget);
}

function renderSitePolicyOverlay(policy) {
  if (!policy || policy.verdict === "allow") {
    removeSitePolicyOverlay();
    return;
  }

  if (!document.body && !document.documentElement) {
    return;
  }

  removeSitePolicyOverlay();
  lastSitePolicyUrl = String(policy.url || location.href || "");

  const root = document.createElement("div");
  root.className = "shieldtext-site-policy-overlay";
  root.dataset.verdict = String(policy.verdict || "warning");
  root.setAttribute("data-shieldtext-site-policy", "true");
  root.setAttribute("role", "dialog");
  root.setAttribute("aria-modal", "true");
  root.tabIndex = -1;

  const box = document.createElement("div");
  box.className = "shieldtext-site-policy-box";
  box.dataset.verdict = String(policy.verdict || "warning");

  appendSitePolicyFace(box, policy.verdict);

  const title = document.createElement("h2");
  title.id = "shieldtext-site-policy-title";
  title.textContent =
    policy.verdict === "block"
      ? "청마루가 이 사이트를 차단했습니다"
      : "청마루가 접속 전 확인을 요청합니다";
  root.setAttribute("aria-labelledby", title.id);

  const description = document.createElement("p");
  description.textContent =
    typeof policy.agent?.response === "string" && policy.agent.response.trim()
      ? policy.agent.response
      : "사이트 주소, 저장된 인텔, 위험 신호를 바탕으로 경고가 필요하다고 판단했습니다.";

  const meta = document.createElement("p");
  meta.textContent = [
    policy.domain ? `도메인: ${policy.domain}` : "",
    policy.site_category ? `분류: ${policy.site_category}` : "",
    Number.isFinite(Number(policy.risk_score))
      ? `위험 점수: ${(Number(policy.risk_score) * 100).toFixed(1)}%`
      : ""
  ].filter(Boolean).join(" · ");

  box.appendChild(title);
  box.appendChild(description);
  if (meta.textContent) {
    box.appendChild(meta);
  }

  if (Array.isArray(policy.reasons) && policy.reasons.length) {
    const list = document.createElement("ul");
    list.className = "shieldtext-site-policy-list";
    for (const reason of policy.reasons.slice(0, 5)) {
      const item = document.createElement("li");
      item.textContent = String(reason);
      list.appendChild(item);
    }
    box.appendChild(list);
  }

  const actions = document.createElement("div");
  actions.className = "shieldtext-site-policy-actions";

  const backButton = document.createElement("button");
  backButton.type = "button";
  backButton.className =
    policy.verdict === "block"
      ? "shieldtext-site-policy-close"
      : "shieldtext-site-policy-secondary";
  backButton.textContent = "뒤로 가기";
  backButton.addEventListener("click", () => {
    try {
      if (history.length > 1) {
        history.back();
      } else {
        location.replace("about:blank");
      }
    } catch {
      location.href = "about:blank";
    }
  });

  if (policy.verdict === "block") {
    actions.appendChild(backButton);
  } else {
    const continueButton = document.createElement("button");
    continueButton.type = "button";
    continueButton.className = "shieldtext-site-policy-close";
    continueButton.textContent = "계속 접속";
    continueButton.addEventListener("click", async () => {
      removeSitePolicyOverlay();
      if (!isExtensionContextAvailable()) {
        return;
      }
      try {
        await chrome.runtime.sendMessage({
          type: "DISMISS_SITE_POLICY",
          url: lastSitePolicyUrl || location.href
        });
      } catch (error) {
        handleExtensionContextError(error);
      }
    });
    actions.appendChild(continueButton);
    actions.appendChild(backButton);
  }

  box.appendChild(actions);
  root.appendChild(box);

  const mountTarget = document.body || document.documentElement;
  if (mountTarget) {
    mountTarget.appendChild(root);
    sitePolicyOverlayElement = root;
    window.setTimeout(() => {
      root.focus?.({ preventScroll: true });
    }, 0);
  }
}

function isSearchResultProtectionEnabled(settings) {
  return (
    settings?.enabled !== false &&
    normalizeSensitivity(settings?.sensitivity) > 0 &&
    settings?.siteProtectionEnabled !== false &&
    settings?.searchResultProtectionEnabled !== false
  );
}

function extractSearchResultTargetUrl(anchor) {
  if (!(anchor instanceof HTMLAnchorElement)) {
    return "";
  }

  const parsed = parseHttpUrl(anchor.href || anchor.getAttribute("href") || "");
  if (!parsed) {
    return "";
  }

  const host = normalizeDomainForPolicy(parsed.hostname);
  if (/(\.|^)google\./i.test(host) && parsed.pathname === "/url") {
    const nested = parsed.searchParams.get("url") || parsed.searchParams.get("q");
    const nestedParsed = parseHttpUrl(nested);
    return nestedParsed ? nestedParsed.href : "";
  }

  return parsed.href;
}

function normalizeSearchResultPolicyKey(url) {
  const parsed = parseHttpUrl(url);
  if (!parsed) return "";
  parsed.hash = "";
  return parsed.href;
}

function getSearchResultSnippet(container) {
  if (!(container instanceof Element)) {
    return "";
  }
  return normalizeText(container.innerText || container.textContent || "").slice(0, SEARCH_RESULT_SNIPPET_LIMIT);
}

function getLocalSearchResultPolicy(url, settings) {
  const domain = domainFromHref(url);
  if (!domain) {
    return null;
  }

  const blockedRule = matchDomainRule(domain, settings?.blockedDomains);
  if (blockedRule) {
    return {
      verdict: "block",
      domain,
      matchedRule: blockedRule,
      source: "manual"
    };
  }

  const warnedRule = matchDomainRule(domain, settings?.warnDomains);
  if (warnedRule) {
    return {
      verdict: "warning",
      domain,
      matchedRule: warnedRule,
      source: "manual"
    };
  }

  const curatedFallback = SEARCH_RESULT_CURATED_FALLBACK_POLICIES.find((entry) => {
    const fallbackDomain = normalizeDomainForPolicy(entry.domain);
    return domain === fallbackDomain || domain.endsWith(`.${fallbackDomain}`);
  });
  if (curatedFallback) {
    return {
      verdict: curatedFallback.verdict,
      domain,
      matchedRule: curatedFallback.matchedRule,
      source: "curated-fallback"
    };
  }

  return null;
}

function getCachedBackendSearchResultPolicy(url) {
  const key = normalizeSearchResultPolicyKey(url);
  if (!key) return { cached: false, policy: null };

  const entry = SEARCH_RESULT_POLICY_CACHE.get(key);
  if (!entry) return { cached: false, policy: null };
  if (Number(entry.expiresAt || 0) <= Date.now()) {
    SEARCH_RESULT_POLICY_CACHE.delete(key);
    return { cached: false, policy: null };
  }
  return {
    cached: true,
    policy: entry.policy || null
  };
}

function trimSearchResultPolicyCache(now = Date.now()) {
  for (const [key, entry] of SEARCH_RESULT_POLICY_CACHE.entries()) {
    if (Number(entry?.expiresAt || 0) <= now) {
      SEARCH_RESULT_POLICY_CACHE.delete(key);
    }
  }

  if (SEARCH_RESULT_POLICY_CACHE.size <= SEARCH_RESULT_POLICY_CACHE_LIMIT) {
    return;
  }

  const overflowCount = SEARCH_RESULT_POLICY_CACHE.size - SEARCH_RESULT_POLICY_CACHE_LIMIT;
  const oldestEntries = Array.from(SEARCH_RESULT_POLICY_CACHE.entries())
    .sort(([, left], [, right]) => Number(left?.expiresAt || 0) - Number(right?.expiresAt || 0))
    .slice(0, overflowCount);
  for (const [key] of oldestEntries) {
    SEARCH_RESULT_POLICY_CACHE.delete(key);
  }
}

function setCachedBackendSearchResultPolicy(url, policy, ttlMs = SEARCH_RESULT_POLICY_CACHE_TTL_MS) {
  const key = normalizeSearchResultPolicyKey(url);
  if (!key) return;
  SEARCH_RESULT_POLICY_CACHE.set(key, {
    policy: policy || null,
    expiresAt: Date.now() + Math.max(1000, Number(ttlMs || SEARCH_RESULT_POLICY_CACHE_TTL_MS))
  });
  trimSearchResultPolicyCache();
}

function normalizeBackendSearchResultPolicy(url, response) {
  const policy = response?.policy || null;
  const verdict = String(policy?.verdict || "allow");
  if (verdict !== "block" && verdict !== "warning") {
    return null;
  }

  const riskScore = Number(policy?.risk_score || 0);
  const hasExactMatch = Boolean(policy?.exact_match?.domain);
  const hasStrongBackendSignal =
    verdict === "block" ||
    Boolean(policy?.security_threat) ||
    Boolean(policy?.harmful_content) ||
    riskScore >= 0.34;

  if (!hasExactMatch && !hasStrongBackendSignal) {
    return null;
  }

  return {
    verdict,
    domain: normalizeDomainForPolicy(policy?.domain || domainFromHref(url)),
    matchedRule:
      String(policy?.exact_match?.domain || policy?.site_category || policy?.reasons?.[0] || response?.source || "site-check"),
    source: response?.source || "site-check",
    riskScore
  };
}

async function getBackendSearchResultPolicy(candidate) {
  const key = normalizeSearchResultPolicyKey(candidate?.url);
  if (!key) return null;

  const cached = getCachedBackendSearchResultPolicy(key);
  if (cached.cached) {
    return cached.policy;
  }

  const inflight = SEARCH_RESULT_POLICY_IN_FLIGHT.get(key);
  if (inflight) {
    return inflight;
  }

  const request = safeRuntimeSendMessage({
    type: "GET_SITE_POLICY_FOR_URL",
    url: key,
    title: candidate?.title || "",
    snippet: candidate?.snippet || "",
    context: "search-result"
  })
    .then((response) => {
      if (response?.ok === false || response?.source === "fallback" || response?.errorCode) {
        const errorTtl =
          response?.errorCode === "PREEMPTED_BY_FOREGROUND"
            ? SEARCH_RESULT_POLICY_PREEMPTED_CACHE_TTL_MS
            : SEARCH_RESULT_POLICY_ERROR_CACHE_TTL_MS;
        setCachedBackendSearchResultPolicy(key, null, errorTtl);
        return null;
      }
      const policy = normalizeBackendSearchResultPolicy(key, response);
      setCachedBackendSearchResultPolicy(key, policy);
      return policy;
    })
    .catch((error) => {
      const errorTtl =
        error?.errorCode === "PREEMPTED_BY_FOREGROUND" || error?.code === "PREEMPTED_BY_FOREGROUND"
          ? SEARCH_RESULT_POLICY_PREEMPTED_CACHE_TTL_MS
          : SEARCH_RESULT_POLICY_ERROR_CACHE_TTL_MS;
      setCachedBackendSearchResultPolicy(key, null, errorTtl);
      return null;
    })
    .finally(() => {
      SEARCH_RESULT_POLICY_IN_FLIGHT.delete(key);
    });

  SEARCH_RESULT_POLICY_IN_FLIGHT.set(key, request);
  return request;
}

function getSearchResultContainerForAnchor(anchor) {
  if (!(anchor instanceof Element)) {
    return null;
  }

  const selectors = [
    "#search .MjjYud",
    "#search .g",
    "#search .tF2Cxc",
    "#search .yuRUbf",
    "#rso .MjjYud",
    "#rso .g",
    "#rso .tF2Cxc",
    "#rso [data-sokoban-container]",
    "#rso [data-content-feature]",
    "#rso [data-attrid]",
    "#bres li",
    "#botstuff li",
    "g-section-with-header",
    "article",
    "li"
  ];

  for (const selector of selectors) {
    const container = anchor.closest(selector);
    if (container && !["HTML", "BODY"].includes(container.tagName)) {
      return promoteSearchResultContainer(container, anchor);
    }
  }

  return promoteSearchResultContainer(anchor.closest("div"), anchor);
}

function isOversizedSearchResultContainer(container) {
  if (!(container instanceof Element)) {
    return false;
  }

  const rect = container.getBoundingClientRect();
  if (rect.width <= 0 || rect.height <= 0) {
    return false;
  }

  const maxHeight = Math.max(360, window.innerHeight * 0.42);
  const maxBroadHeight = Math.max(220, window.innerHeight * 0.28);
  return rect.height > maxHeight || (rect.width > window.innerWidth * 0.86 && rect.height > maxBroadHeight);
}

function getCompactSearchResultContainerForAnchor(anchor) {
  if (!(anchor instanceof Element)) {
    return null;
  }

  const selectors = [
    "#search .yuRUbf",
    "#rso .yuRUbf",
    "#search .tF2Cxc",
    "#rso .tF2Cxc",
    "#bres li",
    "#botstuff li",
    "#search a[href]",
    "#rso a[href]"
  ];

  for (const selector of selectors) {
    const compact = anchor.closest(selector);
    if (
      compact instanceof Element &&
      !["HTML", "BODY"].includes(compact.tagName) &&
      !isOversizedSearchResultContainer(compact)
    ) {
      return compact;
    }
  }

  return null;
}

function promoteSearchResultContainer(container, anchor) {
  if (!(container instanceof Element)) {
    return null;
  }

  const roots = [
    "#search .MjjYud",
    "#rso .MjjYud",
    "#search .g",
    "#rso .g",
    "#search [data-sokoban-container]",
    "#rso [data-sokoban-container]",
    "#search [data-content-feature]",
    "#rso [data-content-feature]",
    "[role='button'][aria-label]",
    "article",
    "li"
  ];

  for (const selector of roots) {
    const root = container.closest(selector) || anchor.closest(selector);
    if (
      root &&
      !["HTML", "BODY"].includes(root.tagName) &&
      (root.contains(container) || root.contains(anchor))
    ) {
      return isOversizedSearchResultContainer(root)
        ? getCompactSearchResultContainerForAnchor(anchor)
        : root;
    }
  }

  return isOversizedSearchResultContainer(container)
    ? getCompactSearchResultContainerForAnchor(anchor)
    : container;
}

function getSearchPolicyPriority(policy) {
  if (policy?.verdict === "block") return 2;
  if (policy?.verdict === "warning") return 1;
  return 0;
}

function selectSearchResultPolicy(selected, container, policy) {
  if (!(container instanceof Element) || !policy) {
    return;
  }
  const current = selected.get(container);
  if (!current || getSearchPolicyPriority(policy) > getSearchPolicyPriority(current)) {
    selected.set(container, policy);
  }
}

function clearProtectedSearchResultContainer(container) {
  if (!(container instanceof Element)) {
    return;
  }
  container.classList.remove("shieldtext-search-result-protected");
  container.removeAttribute("data-shieldtext-search-policy");
  container.removeAttribute("data-shieldtext-search-domain");
  container.removeAttribute("data-shieldtext-search-rule");
  container.querySelectorAll(":scope > .shieldtext-search-result-notice").forEach((node) => {
    node.remove();
  });
}

function upsertSearchResultNotice(container, policy) {
  let notice = container.querySelector(":scope > .shieldtext-search-result-notice");
  if (!notice) {
    notice = document.createElement("div");
    notice.className = "shieldtext-search-result-notice";
    notice.setAttribute("data-shieldtext-rendered", "true");
    notice.setAttribute("role", "note");
    container.appendChild(notice);
  }

  const title =
    policy.verdict === "block"
      ? "청마루가 이 검색결과를 가렸습니다"
      : "주의가 필요한 검색결과입니다";
  const action =
    policy.verdict === "block"
      ? policy.source === "manual"
        ? "차단 도메인 목록과 일치해 링크와 요약을 숨겼습니다."
        : policy.source === "curated-fallback"
          ? "청마루 기본 위험 사이트 예시와 일치해 링크와 요약을 숨겼습니다."
        : "사이트 판별 결과 위험도가 높아 링크와 요약을 숨겼습니다."
      : policy.source === "manual"
        ? "경고 도메인 목록과 일치해 내용을 흐리게 표시했습니다."
        : policy.source === "curated-fallback"
          ? "청마루 기본 주의 사이트 예시와 일치해 내용을 흐리게 표시했습니다."
        : "사이트 판별 결과 주의가 필요해 내용을 흐리게 표시했습니다.";

  notice.textContent = "";
  const strong = document.createElement("strong");
  strong.textContent = title;
  const detail = document.createElement("span");
  detail.textContent = `${policy.domain} · ${action}`;
  notice.appendChild(strong);
  notice.appendChild(detail);
}

function applyProtectedSearchResultContainer(container, policy) {
  if (!(container instanceof Element) || !policy) {
    return;
  }

  container.classList.add("shieldtext-search-result-protected");
  container.dataset.shieldtextSearchPolicy = policy.verdict;
  container.dataset.shieldtextSearchDomain = policy.domain;
  container.dataset.shieldtextSearchRule = policy.matchedRule || "";
  upsertSearchResultNotice(container, policy);
}

function clearSearchResultProtection() {
  document
    .querySelectorAll(".shieldtext-search-result-protected")
    .forEach((container) => clearProtectedSearchResultContainer(container));
}

async function checkBackendSearchResultPolicies(candidates, runId) {
  const unique = [];
  const seen = new Set();
  for (const candidate of candidates) {
    const key = normalizeSearchResultPolicyKey(candidate?.url);
    if (!key || seen.has(key)) continue;
    seen.add(key);
    unique.push({ ...candidate, url: key });
    if (unique.length >= SEARCH_RESULT_BACKEND_CHECK_LIMIT) break;
  }

  let changed = false;
  for (let index = 0; index < unique.length; index += SEARCH_RESULT_BACKEND_CHECK_CONCURRENCY) {
    const chunk = unique.slice(index, index + SEARCH_RESULT_BACKEND_CHECK_CONCURRENCY);
    const policies = await Promise.all(chunk.map((candidate) => getBackendSearchResultPolicy(candidate)));
    changed = changed || policies.some(Boolean);
  }

  if (changed && runId === searchResultProtectionRunId && isGoogleTextSearchAnalysisPage()) {
    scheduleSearchResultProtection(cachedSettings);
  }
}

function applySearchResultProtection(settings = cachedSettings, runId = ++searchResultProtectionRunId) {
  if (
    !document.body ||
    !isGoogleTextSearchAnalysisPage() ||
    !isSearchResultProtectionEnabled(settings)
  ) {
    clearSearchResultProtection();
    return 0;
  }

  const selected = new Map();
  const backendCandidates = [];
  const shouldUseBackendPolicies = settings?.backendEnabled === true;
  const anchors = [
    ...document.querySelectorAll(
      "#search a[href], #rso a[href], #bres a[href], #botstuff a[href]"
    )
  ].slice(0, 160);

  for (const anchor of anchors) {
    const targetUrl = extractSearchResultTargetUrl(anchor);
    if (!targetUrl) {
      continue;
    }
    const container = getSearchResultContainerForAnchor(anchor);
    if (!container) continue;

    const localPolicy = getLocalSearchResultPolicy(targetUrl, settings);
    if (localPolicy) {
      selectSearchResultPolicy(selected, container, localPolicy);
      continue;
    }

    if (shouldUseBackendPolicies) {
      const cached = getCachedBackendSearchResultPolicy(targetUrl);
      if (cached.cached) {
        if (cached.policy) {
          selectSearchResultPolicy(selected, container, cached.policy);
        }
        continue;
      }

      if (backendCandidates.length < SEARCH_RESULT_BACKEND_CHECK_LIMIT) {
        backendCandidates.push({
          url: targetUrl,
          title: normalizeText(anchor.textContent || ""),
          snippet: getSearchResultSnippet(container)
        });
      }
    }
  }

  for (const container of document.querySelectorAll(".shieldtext-search-result-protected")) {
    if (!selected.has(container)) {
      clearProtectedSearchResultContainer(container);
    }
  }

  for (const [container, policy] of selected.entries()) {
    applyProtectedSearchResultContainer(container, policy);
  }

  if (shouldUseBackendPolicies && backendCandidates.length > 0) {
    checkBackendSearchResultPolicies(backendCandidates, runId).catch((error) => {
      if (!handleExtensionContextError(error)) {
        console.warn("[청마루] search result site policy check failed", error);
      }
    });
  }

  return selected.size;
}

function scheduleSearchResultProtection(settings = cachedSettings) {
  if (searchResultProtectionFrameId) {
    window.cancelAnimationFrame(searchResultProtectionFrameId);
  }
  const runId = ++searchResultProtectionRunId;
  searchResultProtectionFrameId = window.requestAnimationFrame(() => {
    searchResultProtectionFrameId = null;
    applySearchResultProtection(settings || cachedSettings, runId);
  });
}

function applyGoogleSearchLightModeProtection(settings = cachedSettings, options = {}) {
  if (extensionContextInvalidated || isUnsupportedPage() || !isGoogleSearchPage()) {
    return 0;
  }
  const startedAt = performance.now();

  if (isGoogleImageSearchPage()) {
    clearSearchResultProtection();
    lastGoogleSearchLocalPreflightAt = performance.now();
    lastGoogleSearchLocalPreflightHref = String(location.href || "");
    return {
      maskedSpanCount: 0,
      preconcealCount: 0
    };
  }

  scheduleSearchResultProtection(settings || cachedSettings);
  const localPreflight = applyCachedLocalPreflightForVisiblePage({
    limit: Number.isFinite(options.limit)
      ? Number(options.limit)
      : MAX_DOMAIN_PRIORITY_CANDIDATES,
    startedAt
  });
  lastGoogleSearchLocalPreflightAt = performance.now();
  lastGoogleSearchLocalPreflightHref = String(location.href || "");
  scheduleInitialEditablePass();
  return {
    maskedSpanCount: Number(localPreflight.decision?.maskedSpanCount || 0),
    preconcealCount: Number(localPreflight.preconcealCount || 0)
  };
}

function scheduleGoogleSearchLightModeProtection(settings = cachedSettings, options = {}) {
  if (extensionContextInvalidated || isUnsupportedPage() || !isGoogleSearchPage()) {
    return;
  }

  if (googleSearchLocalPreflightFrameId) {
    window.cancelAnimationFrame(googleSearchLocalPreflightFrameId);
    googleSearchLocalPreflightFrameId = null;
  }

  if (googleSearchLocalPreflightTimerId) {
    window.clearTimeout(googleSearchLocalPreflightTimerId);
    googleSearchLocalPreflightTimerId = null;
  }

  const href = String(location.href || "");
  const now = performance.now();
  const minIntervalMs = Number.isFinite(options.minIntervalMs)
    ? Math.max(0, Number(options.minIntervalMs))
    : GOOGLE_SEARCH_LIGHT_PROTECTION_MIN_INTERVAL_MS;
  const shouldRunNow =
    options.force === true ||
    !lastGoogleSearchLocalPreflightAt ||
    href !== lastGoogleSearchLocalPreflightHref;
  const delayMs = shouldRunNow
    ? 0
    : Math.max(0, minIntervalMs - (now - lastGoogleSearchLocalPreflightAt));

  const scheduleFrame = () => {
    googleSearchLocalPreflightTimerId = null;
    googleSearchLocalPreflightFrameId = window.requestAnimationFrame(() => {
      googleSearchLocalPreflightFrameId = null;
      applyGoogleSearchLightModeProtection(settings || cachedSettings, options);
    });
  };

  if (delayMs > 0) {
    googleSearchLocalPreflightTimerId = window.setTimeout(scheduleFrame, delayMs);
    return;
  }

  scheduleFrame();
}

function initializeSearchResultProtectionClickGuard() {
  if (searchResultProtectionClickGuardInitialized) {
    return;
  }
  searchResultProtectionClickGuardInitialized = true;
  document.addEventListener(
    "click",
    (event) => {
      const target = event.target;
      if (!(target instanceof Element)) {
        return;
      }

      const blockedResult = target.closest(
        ".shieldtext-search-result-protected[data-shieldtext-search-policy='block']"
      );
      if (!blockedResult || !target.closest("a[href]")) {
        return;
      }

      event.preventDefault();
      event.stopImmediatePropagation();
    },
    true
  );
}

async function requestCurrentSitePolicy() {
  const settings = await loadSettings().catch(() => getMergedSettings({}));
  if (
    settings?.enabled === false ||
    normalizeSensitivity(settings?.sensitivity) <= 0 ||
    settings?.siteProtectionEnabled === false
  ) {
    removeSitePolicyOverlay();
    clearSearchResultProtection();
    return;
  }
  if (isGoogleImageSearchPage()) {
    clearSearchResultProtection();
  } else {
    scheduleSearchResultProtection(settings);
  }
  if (!isExtensionContextAvailable() || !location?.href || !/^https?:/i.test(location.href)) {
    return;
  }

  try {
    const response = await chrome.runtime.sendMessage({
      type: "GET_SITE_POLICY_FOR_URL",
      url: location.href,
      title: document.title || "",
      snippet:
        document.querySelector?.("meta[name='description']")?.getAttribute?.("content") || ""
    });

    if (response?.dismissed) {
      removeSitePolicyOverlay();
      return;
    }

    const policy = response?.policy || null;
    if (!policy || policy.verdict === "allow") {
      removeSitePolicyOverlay();
      return;
    }

    if (
      policy.site_category !== "manual-policy" &&
      policy.agent?.mode !== "override" &&
      !policy.exact_match?.domain
    ) {
      removeSitePolicyOverlay();
      return;
    }

    renderSitePolicyOverlay(policy);
  } catch (error) {
    handleExtensionContextError(error);
  }
}

function suppressMutationFeedback(ms = 160) {
  const nextUntil = Date.now() + Math.max(40, Number(ms || 0));
  ignoreMutationsUntil = Math.max(ignoreMutationsUntil, nextUntil);
}

function invalidateAnalysisForSettingsChange() {
  ANALYSIS_CACHE.clear();
  latestAnalysisGeneration += 1;
  latestPipelineSequence += 1;
  suppressMutationFeedback(180);

  for (const state of NODE_STATE_BY_ID.values()) {
    state.analysisGeneration = latestAnalysisGeneration;
    state.hasProcessed = false;
    state.lastFingerprint = "";
    state.lastSkippedAnalysisAt = 0;
    state.lastSkippedFingerprint = "";
    state.lastSkippedRetryBackoffMs = 0;
    state.lastSkippedRetryCount = 0;
    state.lastSkippedRetryFingerprint = "";
    state.lastAppliedFingerprint = "";
    state.lastAppliedStage = "";
    state.lastReconcileFingerprint = "";
    state.lastQueuedReconcileFingerprint = "";
    state.reconcileInFlightFingerprint = "";
    if (state.nodeId) {
      DIRTY_NODE_IDS.add(state.nodeId);
    }
  }

  for (const state of EDITABLE_VALUE_STATE_BY_ID.values()) {
    state.analysisGeneration = latestAnalysisGeneration;
    state.hasProcessed = false;
    state.lastFingerprint = "";
    state.lastSkippedAnalysisAt = 0;
    state.lastSkippedFingerprint = "";
    state.lastSkippedRetryBackoffMs = 0;
    state.lastSkippedRetryCount = 0;
    state.lastSkippedRetryFingerprint = "";
    state.lastAppliedFingerprint = "";
    state.lastAppliedStage = "";
    state.lastReconcileFingerprint = "";
    state.lastQueuedReconcileFingerprint = "";
    state.reconcileInFlightFingerprint = "";
    if (state.nodeId) {
      DIRTY_NODE_IDS.add(state.nodeId);
    }
  }

  RECONCILE_QUEUE.clear();
  SKIPPED_RETRY_NODE_IDS.clear();
  if (skippedAnalysisRetryTimerId) {
    window.clearTimeout(skippedAnalysisRetryTimerId);
    skippedAnalysisRetryTimerId = null;
    skippedAnalysisRetryDueAt = 0;
  }
  if (reconcileFlushTimerId) {
    window.clearTimeout(reconcileFlushTimerId);
    reconcileFlushTimerId = null;
  }
  scheduledReconcileDelayMs = 0;
}

function restoreAllRenderedContent() {
  suppressMutationFeedback(240);

  for (const state of NODE_STATE_BY_ID.values()) {
    restoreNodeState(state);
  }

  for (const state of EDITABLE_VALUE_STATE_BY_ID.values()) {
    restoreEditableValueState(state);
  }

  for (const state of ATTRIBUTE_VALUE_STATE_BY_ID.values()) {
    restoreAttributeValueState(state);
  }

  restoreOrphanRenderedContent();

  RECONCILE_QUEUE.clear();
  SKIPPED_RETRY_NODE_IDS.clear();
  queuedReason = null;
  if (reconcileFlushTimerId) {
    window.clearTimeout(reconcileFlushTimerId);
    reconcileFlushTimerId = null;
  }
  scheduledReconcileDelayMs = 0;
}

function restoreCandidatesRenderedContent(candidates) {
  suppressMutationFeedback(180);

  for (const candidate of Array.isArray(candidates) ? candidates : []) {
    if (!candidate?.state) continue;

    if (candidate.candidateKind === "editable-value") {
      restoreEditableValueState(candidate.state);
    } else if (candidate.candidateKind === "attribute-value") {
      restoreAttributeValueState(candidate.state);
    } else {
      restoreNodeState(candidate.state);
    }
  }
}

function restoreOrphanRenderedContent() {
  suppressMutationFeedback(240);

  for (const overlay of document.querySelectorAll(".shieldtext-editable-overlay")) {
    overlay.remove();
  }

  for (const element of document.querySelectorAll(
    ".shieldtext-editable-source-concealed, .shieldtext-editable-hard-concealed, [data-shieldtext-hard-conceal='true']"
  )) {
    if (!(element instanceof HTMLElement)) continue;
    element.classList.remove("shieldtext-editable-source-concealed");
    element.classList.remove("shieldtext-editable-hard-concealed");
    delete element.dataset.shieldtextHardConceal;
    element.style.removeProperty("color");
    element.style.removeProperty("-webkit-text-fill-color");
    element.style.removeProperty("caret-color");
    element.style.removeProperty("text-shadow");
    element.style.removeProperty("filter");
    element.style.removeProperty("opacity");
    element.style.removeProperty("-webkit-text-security");
    element.style.removeProperty("text-security");
  }

  for (const wrapper of document.querySelectorAll("[data-shieldtext-wrapper='true']")) {
    if (!(wrapper instanceof Element) || !wrapper.parentNode) continue;
    wrapper.removeAttribute("data-shieldtext-state");
    wrapper.removeAttribute("data-shieldtext-tooltip");

    const renderedChildren = [...wrapper.children].filter(
      (child) => child.dataset?.shieldtextRendered === "true"
    );
    const originalText = renderedChildren
      .map((child) => child.dataset?.shieldtextOriginalText || "")
      .find((value) => value);

    if (originalText) {
      wrapper.replaceChildren(document.createTextNode(originalText));
    } else {
      for (const rendered of renderedChildren) {
        rendered.remove();
      }
    }

    const childNodes = [...wrapper.childNodes];
    const canUnwrap =
      childNodes.length === 1 &&
      childNodes[0] instanceof Text &&
      String(childNodes[0].nodeValue || "").length > 0;

    if (!canUnwrap) {
      continue;
    }

    wrapper.parentNode.insertBefore(childNodes[0], wrapper);
    wrapper.remove();
  }
}

function includeEditableCandidatesForSettingsRefresh(candidates) {
  const nextCandidates = Array.isArray(candidates) ? [...candidates] : [];
  const seenNodeIds = new Set(nextCandidates.map((candidate) => candidate?.nodeId).filter(Boolean));

  for (const candidate of collectEditableValueCandidates(INITIAL_EDITABLE_PASS_LIMIT)) {
    if (!candidate?.nodeId || seenNodeIds.has(candidate.nodeId)) {
      continue;
    }
    seenNodeIds.add(candidate.nodeId);
    nextCandidates.unshift(candidate);
  }

  return nextCandidates;
}

async function loadSettings(options = {}) {
  if (!isExtensionContextAvailable()) {
    return getMergedSettings(cachedSettings || {});
  }

  if (!options.force && cachedSettings) {
    return cachedSettings;
  }

  if (!options.force && settingsLoadPromise) {
    return settingsLoadPromise;
  }

  settingsLoadPromise = safeStorageSyncGet("settings")
    .then(({ settings }) => updateCachedSettings(settings || {}))
    .finally(() => {
      settingsLoadPromise = null;
    });

  return settingsLoadPromise;
}

function shouldForceSettingsLoadForRun(runReason) {
  return (
    runReason === "settings-updated" ||
    runReason === "manual" ||
    runReason === "manual-request" ||
    runReason === "manual-request-after-inject"
  );
}

function isElementVisible(element) {
  if (!(element instanceof Element)) return false;

  const style = window.getComputedStyle(element);
  if (style.display === "none" || style.visibility === "hidden") return false;
  if (Number(style.opacity) === 0) return false;

  const rect = element.getBoundingClientRect();
  if (rect.width <= 0 || rect.height <= 0) return false;

  return true;
}

function isEditableValueLayoutVisible(element) {
  if (!(element instanceof HTMLInputElement) && !(element instanceof HTMLTextAreaElement)) {
    return false;
  }

  const style = window.getComputedStyle(element);
  if (style.display === "none" || style.visibility === "hidden") {
    return false;
  }

  const rect = element.getBoundingClientRect();
  return rect.width > 0 && rect.height > 0;
}

function isElementNearViewport(rect) {
  return rect.bottom >= -VIEWPORT_BUFFER_PX && rect.top <= window.innerHeight + VIEWPORT_BUFFER_PX;
}

function getElementAnalysisText(element) {
  if (!(element instanceof Element)) return "";

  const values = [];
  if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) {
    values.push(element.value);
  }
  values.push(
    element.innerText,
    element.textContent,
    element.getAttribute("aria-label"),
    element.getAttribute("title"),
    element.getAttribute("value")
  );

  const uniqueValues = [];
  const seenValues = new Set();
  for (const value of values) {
    const normalized = normalizeText(value || "");
    if (!normalized || seenValues.has(normalized)) continue;
    seenValues.add(normalized);
    uniqueValues.push(normalized);
  }

  return normalizeText(uniqueValues.join(" "));
}

function isEditableElement(element) {
  if (!(element instanceof Element)) return false;
  if (element.isContentEditable) return true;
  if (element.closest("[contenteditable='true']")) return true;

  const tagName = element.tagName;
  return tagName === "INPUT" || tagName === "TEXTAREA" || tagName === "SELECT";
}

function getGoogleInteractiveRoot(element) {
  if (!isGoogleTextSearchAnalysisPage() || !(element instanceof Element)) {
    return null;
  }

  return element.closest("button, [role='button'], a[href], [data-ved]");
}

function getGoogleSfcAnalysisContainer(element) {
  if (!isGoogleTextSearchAnalysisPage() || !(element instanceof Element)) {
    return null;
  }

  const sourceCard = element.closest(".MFrAxb, .jydCyd");
  if (sourceCard instanceof Element && sourceCard.closest("[data-sfc-root='c'], [data-container-id='main-col']")) {
    return sourceCard;
  }

  const answerBlock = element.closest(".n6owBd, .mZJni.Dn7Fzd, [data-container-id='main-col']");
  if (answerBlock instanceof Element && answerBlock.closest("[data-sfc-root='c'], [data-container-id='main-col']")) {
    return answerBlock;
  }

  return null;
}

function isGoogleSfcTextElement(element) {
  if (!isGoogleTextSearchAnalysisPage() || !(element instanceof Element)) {
    return false;
  }

  return Boolean(element.closest(GOOGLE_SFC_TEXT_SELECTOR));
}

function shouldAllowGoogleInteractiveElement(element) {
  const interactiveRoot = getGoogleInteractiveRoot(element);
  if (!(interactiveRoot instanceof Element)) {
    return false;
  }

  if (
    interactiveRoot.closest(
      "header, nav, [role='navigation'], [role='tablist'], [aria-label='탐색'], form"
    )
  ) {
    return false;
  }

  if (!interactiveRoot.closest("#search, main, [role='main'], #rhs, #bres, #botstuff")) {
    return false;
  }

  const text = getElementAnalysisText(interactiveRoot);
  if (!text || !isCandidateTextUseful(text, interactiveRoot)) {
    return false;
  }

  return HIGH_SIGNAL_PROFANITY_PATTERN.test(text);
}

function isSkippableElement(element) {
  if (!(element instanceof Element)) return true;
  const allowGoogleInteractive = shouldAllowGoogleInteractiveElement(element);
  if (SKIP_TAGS.has(element.tagName) && !(element.tagName === "BUTTON" && allowGoogleInteractive)) {
    return true;
  }
  if (isShieldTextManagedElement(element)) return true;
  if (isEditableElement(element)) return true;
  if (element.closest("form") && !allowGoogleInteractive) return true;
  if (element.closest("pre, code, textarea, input, select")) return true;
  if (element.closest("button, [role='button']") && !allowGoogleInteractive) {
    return true;
  }
  if (element.closest("[data-shieldtext-rendered='true']")) return true;
  if (element.getAttribute("role") === "button" && !allowGoogleInteractive) return true;
  if (element.getAttribute("role") === "textbox") return true;
  return false;
}

function shouldSkipTextNodeParent(element) {
  return isSkippableElement(element) || isSpeculationRulesElement(element);
}

function shouldSkipGoogleVisibleTextNodeParent(element) {
  if (!(element instanceof Element)) return true;
  if (isSpeculationRulesElement(element)) return true;
  if (isShieldTextManagedElement(element)) return true;
  if (isEditableElement(element)) return true;
  if (element.closest("[data-shieldtext-rendered='true']")) return true;
  if (element.closest("header, nav, [role='navigation'], [role='tablist'], form")) return true;
  if (element.closest("pre, code, textarea, input, select, button, [role='button']")) return true;
  if (SKIP_TAGS.has(element.tagName)) return true;
  return false;
}

function shouldSkipGoogleInteractiveTextNodeParent(element) {
  if (!(element instanceof Element)) return true;
  if (isSpeculationRulesElement(element)) return true;
  if (isShieldTextManagedElement(element)) return true;
  if (isEditableElement(element)) return true;
  if (element.closest("[data-shieldtext-rendered='true']")) return true;
  if (element.closest("pre, code, textarea, input, select")) return true;
  if (SKIP_TAGS.has(element.tagName) && element.tagName !== "BUTTON") return true;
  return false;
}

function looksLikeRawUrl(text) {
  const compact = String(text || "").replace(/\s+/g, "");
  if (!compact) return false;
  if (compact.includes("://")) return true;
  if (/^www\./i.test(compact)) return true;
  if (/^[\w.-]+\.(com|net|org|kr|co|io|me|wiki)(\/\S*)?$/i.test(compact)) return true;
  return false;
}

function looksLikeInteractionMetadataText(text) {
  const normalizedText = normalizeText(text);
  if (!normalizedText) return false;
  if (HIGH_SIGNAL_PROFANITY_PATTERN.test(normalizedText)) return false;

  const compact = normalizedText
    .replace(/[·ㆍ•|,/]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  if (!compact || compact.length > 80) {
    return false;
  }

  const hasUiAction = /(좋아요|답글달기|답글|댓글|공유|저장|신고|더보기|조회수)/.test(compact);
  const hasCountOrTime =
    /(\d+\s*(개|명|회|분|시간|일|주|개월|년)|방금\s*전|어제|오늘)/.test(compact);
  if (hasUiAction && hasCountOrTime) {
    return true;
  }

  return /^(좋아요|답글달기|답글|댓글|공유|저장|신고|더보기)(\s+(좋아요|답글달기|답글|댓글|공유|저장|신고|더보기))*$/.test(compact);
}

function isCandidateTextUseful(text, element) {
  const normalizedText = normalizeText(text);
  if (!normalizedText) return false;
  if (/^[\d\s.,\-:/|]+$/.test(normalizedText)) return false;
  if (looksLikeRawUrl(normalizedText)) return false;
  if (SAFE_BROWSER_UI_LABELS.has(normalizeLabel(normalizedText))) return false;
  if (HIGH_SIGNAL_PROFANITY_PATTERN.test(normalizedText)) return true;
  if (looksLikeInteractionMetadataText(normalizedText)) return false;
  if (normalizedText.length < 2) return false;

  if (element instanceof Element) {
    const tagName = element.tagName;
    if (tagName === "CITE") return false;
    if (tagName === "A" && looksLikeRawUrl(normalizedText)) return false;
    if (element.closest("cite")) return false;
  }

  return true;
}

function getTextNodeId(textNode) {
  let nodeId = TEXT_NODE_ID_MAP.get(textNode);
  if (!nodeId) {
    nodeId = `text-node-${nextTextNodeId++}`;
    TEXT_NODE_ID_MAP.set(textNode, nodeId);
  }
  return nodeId;
}

function isMaskableValueElement(element) {
  if (!(element instanceof Element)) return false;
  const isNativeTextField =
    element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement;

  if (!isNativeTextField) {
    return false;
  }

  if (!isEditableValueLayoutVisible(element)) return false;
  if (!isElementNearViewport(element.getBoundingClientRect())) return false;
  if ("disabled" in element && element.disabled) return false;

  if (element instanceof HTMLInputElement) {
    const inputType = (element.type || "text").toLowerCase();
    if (!["text", "search", ""].includes(inputType)) {
      return false;
    }
  }

  return normalizeText(getEditableElementText(element)).length > 0;
}

function getEditableElementText(element) {
  if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) {
    return String(element.value || "");
  }

  return String(element.innerText || element.textContent || "");
}

function isLikelyChungmaruTooltipTitle(value) {
  const text = String(value || "").trim();
  if (!text) return false;
  return /(?:공격|모욕|혐오|스팸|유해|콘텐츠|\d{1,3}%)/.test(text);
}

function clearLikelyChungmaruTooltipTitle(element) {
  if (!(element instanceof Element)) return;
  if (isLikelyChungmaruTooltipTitle(element.getAttribute("title"))) {
    element.removeAttribute("title");
  }
}

function getEditableValueId(element) {
  let nodeId = EDITABLE_VALUE_ID_MAP.get(element);
  if (!nodeId) {
    nodeId = `editable-value-${nextEditableValueId++}`;
    EDITABLE_VALUE_ID_MAP.set(element, nodeId);
  }
  return nodeId;
}

function getEditableValueState(element) {
  clearLikelyChungmaruTooltipTitle(element);
  const nodeId = getEditableValueId(element);
  let state = EDITABLE_VALUE_STATE_BY_ID.get(nodeId);

  if (!state) {
    state = {
      nodeId,
      element,
      hasProcessed: false,
      lastFingerprint: "",
      lastSkippedAnalysisAt: 0,
      lastSkippedFingerprint: "",
      lastSkippedRetryBackoffMs: 0,
      lastSkippedRetryCount: 0,
      lastSkippedRetryFingerprint: "",
      lastDecisionKey: "",
      lastAppliedFingerprint: "",
      lastAppliedStage: "",
      lastAppliedBlocked: false,
      lastReconcileFingerprint: "",
      lastQueuedReconcileFingerprint: "",
      reconcileInFlightFingerprint: "",
      analysisGeneration: 0,
      isMasked: false,
      isPending: false,
      originalTitle: isLikelyChungmaruTooltipTitle(element.getAttribute("title"))
        ? ""
        : element.getAttribute("title") || "",
      originalColor: element.style.color || "",
      originalWebkitTextFillColor: element.style.webkitTextFillColor || "",
      originalCaretColor: element.style.caretColor || "",
      originalTextShadow: element.style.textShadow || "",
      originalFilter: element.style.filter || "",
      originalOpacity: element.style.opacity || "",
      originalWebkitTextSecurity: element.style.webkitTextSecurity || "",
      originalTextSecurity: element.style.textSecurity || "",
      overlayRoot: null,
      overlayContent: null,
      overlayMode: "",
      overlayHost: null,
      overlayHostPositionPatched: false,
      overlayHostOriginalPosition: "",
      maskedText: "",
      maskedSpans: [],
      overlayTooltip: "",
      overlayRenderKey: "",
      overlayLayoutKey: "",
      overlayTextColor: "",
      overlayTextFillColor: "",
      nativeMaskApplied: false
    };
    EDITABLE_VALUE_STATE_BY_ID.set(nodeId, state);
  } else {
    state.element = element;
  }

  return state;
}

function getAttributeValueId(element, attributeName) {
  if (!(element instanceof Element)) return "";
  const normalizedAttributeName = String(attributeName || "").toLowerCase();
  let attributeIdMap = ATTRIBUTE_VALUE_ID_MAP.get(element);
  if (!attributeIdMap) {
    attributeIdMap = new Map();
    ATTRIBUTE_VALUE_ID_MAP.set(element, attributeIdMap);
  }
  let nodeId = attributeIdMap.get(normalizedAttributeName);
  if (!nodeId) {
    nodeId = `attribute-value-${nextAttributeValueId++}`;
    attributeIdMap.set(normalizedAttributeName, nodeId);
  }
  return nodeId;
}

function getAttributeValueState(element, attributeName) {
  const normalizedAttributeName = String(attributeName || "").toLowerCase();
  const nodeId = getAttributeValueId(element, normalizedAttributeName);
  if (!nodeId) return null;

  let state = ATTRIBUTE_VALUE_STATE_BY_ID.get(nodeId);
  const currentValue = String(element.getAttribute(normalizedAttributeName) || "");
  if (!state) {
    state = {
      nodeId,
      element,
      attributeName: normalizedAttributeName,
      originalValue: currentValue,
      hasProcessed: false,
      lastFingerprint: "",
      lastSkippedAnalysisAt: 0,
      lastSkippedFingerprint: "",
      lastSkippedRetryBackoffMs: 0,
      lastSkippedRetryCount: 0,
      lastSkippedRetryFingerprint: "",
      lastDecisionKey: "",
      lastAppliedFingerprint: "",
      lastAppliedStage: "",
      lastAppliedBlocked: false,
      lastReconcileFingerprint: "",
      lastQueuedReconcileFingerprint: "",
      reconcileInFlightFingerprint: "",
      analysisGeneration: 0,
      isMasked: false,
      isPending: false
    };
    ATTRIBUTE_VALUE_STATE_BY_ID.set(nodeId, state);
  } else {
    state.element = element;
    state.attributeName = normalizedAttributeName;
    if (!state.isMasked && currentValue && currentValue !== state.originalValue) {
      state.originalValue = currentValue;
    }
  }

  return state;
}

function getAttributeSourceValue(state) {
  if (!state?.element || !state.attributeName) return "";
  return state.isMasked
    ? String(state.originalValue || "")
    : String(state.element.getAttribute(state.attributeName) || "");
}

function getNodeState(textNode) {
  const nodeId = getTextNodeId(textNode);
  let state = NODE_STATE_BY_ID.get(nodeId);

  if (!state) {
    state = {
      nodeId,
      textNode,
      wrapper: null,
      originalText: String(textNode.nodeValue || ""),
      hasProcessed: false,
      lastFingerprint: "",
      lastSkippedAnalysisAt: 0,
      lastSkippedFingerprint: "",
      lastSkippedRetryBackoffMs: 0,
      lastSkippedRetryCount: 0,
      lastSkippedRetryFingerprint: "",
      lastDecisionKey: "",
      lastAppliedFingerprint: "",
      lastAppliedStage: "",
      lastAppliedBlocked: false,
      lastReconcileFingerprint: "",
      lastQueuedReconcileFingerprint: "",
      reconcileInFlightFingerprint: "",
      analysisGeneration: 0,
      isMasked: false,
      isPending: false,
      observedElement: null
    };
    NODE_STATE_BY_ID.set(nodeId, state);
  } else {
    state.textNode = textNode;
    if (!state.wrapper && textNode.parentElement?.dataset?.shieldtextWrapper === "true") {
      state.wrapper = textNode.parentElement;
    }
  }

  return state;
}

function getRenderableParent(textNode) {
  if (!(textNode instanceof Text)) return null;
  let parent = textNode.parentElement;
  if (!parent) return null;

  if (parent.dataset?.shieldtextWrapper === "true") {
    parent = parent.parentElement;
  }

  return parent instanceof Element ? parent : null;
}

function isBlockLikeElement(element) {
  if (!(element instanceof Element)) return false;
  const display = window.getComputedStyle(element).display;
  return (
    display === "block" ||
    display === "flex" ||
    display === "grid" ||
    display === "list-item" ||
    display === "table" ||
    display === "table-row" ||
    display === "table-cell"
  );
}

function getElementReadableText(element) {
  if (!(element instanceof Element)) return "";
  return normalizeText(element.innerText || element.textContent || "");
}

function getGoogleSearchAnalysisContainer(element) {
  if (!isGoogleTextSearchAnalysisPage() || !(element instanceof Element)) {
    return null;
  }

  const sfcContainer = getGoogleSfcAnalysisContainer(element);
  if (sfcContainer) {
    return sfcContainer;
  }

  const interactiveRoot = element.closest(
    "#bres a[href], #bres [role='button'], #bres [data-ved], #botstuff a[href], #botstuff [role='button'], #botstuff [data-ved], main [role='button'], main [data-ved]"
  );
  if (
    interactiveRoot instanceof Element &&
    shouldAllowGoogleInteractiveElement(interactiveRoot)
  ) {
    return interactiveRoot;
  }

  return (
    element.closest(
      "#search .MjjYud, #search .g, #search .tF2Cxc, #search .yuRUbf, #search .ULSxyf, #rso .MjjYud, #rso .g, #rso .tF2Cxc, #rso .ULSxyf, #rso [data-content-feature], #rso [data-sokoban-container], #rso [data-attrid], #rso .wDYxhc, #rso .kp-wholepage, #botstuff, #bres, g-section-with-header, #rhs [data-attrid], #rhs .kp-wholepage, #rhs"
    ) ||
    null
  );
}

function isExcludedGoogleAnalysisContainer(element) {
  if (!(element instanceof Element)) {
    return true;
  }

  return Boolean(element.closest("g-scrolling-carousel"));
}

function getGoogleHighSignalInteractiveContainers(limit = MAX_DOMAIN_PRIORITY_CANDIDATES) {
  if (!isGoogleTextSearchAnalysisPage()) {
    return [];
  }

  const selectors = [
    "[data-container-id='main-col'] a[href]",
    "[data-container-id='main-col'] [role='button']",
    "[data-container-id='main-col'] [data-ved]",
    "[data-sfc-root='c'] a[href]",
    "[data-sfc-root='c'] [role='button']",
    "[data-sfc-root='c'] [data-ved]",
    "#bres a[href]",
    "#bres [role='button']",
    "#botstuff a[href]",
    "#botstuff [role='button']",
    "main a[href]",
    "main [role='button']",
    "#rhs a[href]",
    "#rhs [role='button']"
  ];
  const containers = [];
  const seenContainers = new Set();
  const collectionLimit = Math.max(limit, limit * 4);

  for (const selector of selectors) {
    for (const element of document.querySelectorAll(selector)) {
      if (!(element instanceof Element)) continue;

      const interactiveRoot = getGoogleInteractiveRoot(element) || element;
      if (!(interactiveRoot instanceof Element)) continue;
      if (seenContainers.has(interactiveRoot)) continue;
      if (!interactiveRoot.isConnected || !isElementVisible(interactiveRoot)) continue;
      if (!isElementNearViewport(interactiveRoot.getBoundingClientRect())) continue;
      if (!shouldAllowGoogleInteractiveElement(interactiveRoot)) continue;

      seenContainers.add(interactiveRoot);
      containers.push(interactiveRoot);
      if (containers.length >= collectionLimit) {
        break;
      }
    }

    if (containers.length >= collectionLimit) {
      break;
    }
  }

  containers.sort((left, right) => {
    const leftRect = left.getBoundingClientRect();
    const rightRect = right.getBoundingClientRect();
    const leftText = getElementAnalysisText(left);
    const rightText = getElementAnalysisText(right);
    const leftHighSignal = HIGH_SIGNAL_PROFANITY_PATTERN.test(leftText) ? 1 : 0;
    const rightHighSignal = HIGH_SIGNAL_PROFANITY_PATTERN.test(rightText) ? 1 : 0;
    if (leftHighSignal !== rightHighSignal) {
      return rightHighSignal - leftHighSignal;
    }
    if (leftRect.top !== rightRect.top) {
      return leftRect.top - rightRect.top;
    }
    return leftRect.left - rightRect.left;
  });

  return containers.slice(0, limit);
}

function getGoogleVisibleAnalysisContainers(limit = MAX_HOT_PATH_CONTAINERS) {
  if (!isGoogleTextSearchAnalysisPage()) {
    return [];
  }

  const selectors = [
    GOOGLE_HIGH_SIGNAL_TEXT_SELECTOR,
    GOOGLE_SFC_CONTAINER_SELECTOR,
    GOOGLE_SFC_TEXT_SELECTOR,
    "main [data-attrid='title']",
    "main .PZPZlf",
    "main .B5dxMb",
    "#search .MjjYud",
    "#search .g",
    "#search .tF2Cxc",
    "#search .ULSxyf",
    "#rso .MjjYud",
    "#rso .g",
    "#rso .tF2Cxc",
    "#rso .ULSxyf",
    "#rso [data-content-feature]",
    "#rso [data-sokoban-container]",
    "#rso [data-attrid]",
    "#rso .wDYxhc",
    "#rso .kp-wholepage",
    "#bres a[href]",
    "#bres [role='button']",
    "#botstuff a[href]",
    "#botstuff [role='button']",
    "#botstuff",
    "#bres",
    "g-section-with-header",
    "main [role='button']",
    "#rhs [role='heading']",
    "#rhs [aria-level]",
    "#rhs [data-attrid='title']",
    "#rhs .PZPZlf",
    "#rhs .B5dxMb",
    "#rhs [data-attrid]",
    "#rhs .kp-wholepage",
    "#rhs"
  ];
  const containers = [];
  const seenContainers = new Set();
  const addContainer = (container, options = {}) => {
    if (!(container instanceof Element)) return false;
    if (seenContainers.has(container)) return false;
    if (!options.allowExcluded && isExcludedGoogleAnalysisContainer(container)) return false;
    if (!container.isConnected || !isElementVisible(container)) return false;
    if (!isElementNearViewport(container.getBoundingClientRect())) return false;

    seenContainers.add(container);
    containers.push(container);
    return true;
  };

  for (const container of getGoogleHighSignalInteractiveContainers(limit)) {
    addContainer(container, { allowExcluded: true });
  }

  for (const selector of selectors) {
    for (const element of document.querySelectorAll(selector)) {
      if (!(element instanceof Element)) continue;

      const container = getGoogleSearchAnalysisContainer(element) || element;
      addContainer(container);
    }
  }

  containers.sort((left, right) => {
    const leftRect = left.getBoundingClientRect();
    const rightRect = right.getBoundingClientRect();
    if (leftRect.top !== rightRect.top) {
      return leftRect.top - rightRect.top;
    }
    return leftRect.left - rightRect.left;
  });

  return containers.slice(0, limit);
}

function isYouTubePage() {
  return /(^|\.)youtube\.com$/i.test(location.hostname || "");
}

function getYouTubeAnalysisContainer(element) {
  if (!isYouTubePage() || !(element instanceof Element)) {
    return null;
  }

  const commentText = element.closest("#content-text, [id='content-text']");
  if (commentText instanceof Element) {
    return commentText;
  }

  return (
    element.closest(
      "ytd-watch-metadata, ytd-video-renderer, ytd-rich-item-renderer, ytd-compact-video-renderer, ytd-comment-thread-renderer, ytd-comment-view-model"
    ) ||
    null
  );
}

function isYouTubeMaskTargetElement(element) {
  if (!isYouTubePage() || !(element instanceof Element)) {
    return false;
  }

  if (
    element.closest(
      "#author-text, #published-time-text, #vote-count-middle, ytd-comment-engagement-bar, ytd-menu-renderer"
    )
  ) {
    return false;
  }

  return Boolean(
    element.closest(
      "#content-text, [id='content-text'], #video-title, #title, h1, h2, h3, yt-formatted-string#video-title"
    )
  );
}

function getYouTubeVisibleAnalysisContainers(limit = MAX_HOT_PATH_CONTAINERS) {
  if (!isYouTubePage()) {
    return [];
  }

  const selectors = [
    "#content-text",
    "[id='content-text']",
    "ytd-comment-thread-renderer",
    "ytd-comment-view-model",
    "ytd-watch-metadata",
    "ytd-video-renderer",
    "ytd-rich-item-renderer",
    "ytd-compact-video-renderer"
  ];
  const containers = [];
  const seenContainers = new Set();

  for (const selector of selectors) {
    for (const element of document.querySelectorAll(selector)) {
      if (!(element instanceof Element)) continue;

      const container = getYouTubeAnalysisContainer(element) || element;
      if (!(container instanceof Element)) continue;
      if (seenContainers.has(container)) continue;
      if (!container.isConnected || !isElementVisible(container)) continue;
      if (!isElementNearViewport(container.getBoundingClientRect())) continue;

      seenContainers.add(container);
      containers.push(container);
    }
  }

  containers.sort((left, right) => {
    const leftRect = left.getBoundingClientRect();
    const rightRect = right.getBoundingClientRect();
    if (leftRect.top !== rightRect.top) {
      return leftRect.top - rightRect.top;
    }
    return leftRect.left - rightRect.left;
  });

  return containers.slice(0, limit);
}

function getAnalysisContainer(element) {
  if (!(element instanceof Element)) return null;

  const googleContainer = getGoogleSearchAnalysisContainer(element);
  if (googleContainer) {
    return googleContainer;
  }

  const youtubeContainer = getYouTubeAnalysisContainer(element);
  if (youtubeContainer) {
    return youtubeContainer;
  }

  const elementText = getElementReadableText(element);
  let fallback = element;
  let current = element;

  for (let depth = 0; depth < MAX_ANALYSIS_CONTAINER_ASCENT && current && current !== document.body; depth += 1) {
    const currentText = getElementReadableText(current);
    if (!currentText) break;

    if (currentText.length <= MAX_ANALYSIS_CONTEXT_LENGTH) {
      fallback = current;
    }

    const hasMeaningfulContext =
      currentText.length >= Math.max(MIN_ANALYSIS_CONTEXT_LENGTH, elementText.length + 8) &&
      currentText.length <= MAX_ANALYSIS_CONTEXT_LENGTH;

    if (hasMeaningfulContext && isBlockLikeElement(current)) {
      return current;
    }

    current = current.parentElement;
  }

  return fallback;
}

function getSourceText(state) {
  const liveText = String(state?.textNode?.nodeValue ?? "");
  if (liveText || (!state.isMasked && !state.isPending)) {
    state.originalText = liveText;
    return liveText;
  }

  return String(state.originalText || "");
}

function buildFingerprint(text) {
  return normalizeText(text);
}

function isStateInSkippedRetryBackoff(state, currentFingerprint) {
  if (!state?.lastSkippedAnalysisAt || !state.lastSkippedFingerprint) {
    return false;
  }

  if (String(state.lastSkippedFingerprint || "") !== String(currentFingerprint || "")) {
    return false;
  }

  const backoffMs = Math.max(
    0,
    Number(state.lastSkippedRetryBackoffMs || SKIPPED_ANALYSIS_RETRY_BACKOFF_MS)
  );
  return Date.now() - Number(state.lastSkippedAnalysisAt || 0) < backoffMs;
}

function isStateSettledForFingerprint(state, fingerprint) {
  if (!state?.nodeId || !fingerprint) {
    return false;
  }

  if (DIRTY_NODE_IDS.has(state.nodeId)) {
    return false;
  }

  return Boolean(
    state.hasProcessed &&
      String(state.lastFingerprint || "") === String(fingerprint || "")
  );
}

function shouldForceHighSignalDirty(state, fingerprint) {
  if (!state?.nodeId || !fingerprint) {
    return false;
  }

  if (isStateInSkippedRetryBackoff(state, fingerprint)) {
    return false;
  }

  if (String(state.lastReconcileFingerprint || "") === String(fingerprint || "")) {
    return false;
  }

  return !isStateSettledForFingerprint(state, fingerprint);
}

function shouldMarkStateDirtyForVisibility(state) {
  if (!state?.nodeId) {
    return false;
  }

  const currentFingerprint = getCurrentStateFingerprint(state);
  if (!currentFingerprint) {
    return false;
  }

  if (isStateInSkippedRetryBackoff(state, currentFingerprint)) {
    return false;
  }

  if (DIRTY_NODE_IDS.has(state.nodeId)) {
    return true;
  }

  return !isStateSettledForFingerprint(state, currentFingerprint);
}

function doesRegisteredStateNeedAnalysis(state, options = {}) {
  if (!state?.nodeId) {
    return false;
  }

  const currentFingerprint = buildFingerprint(normalizeText(getSourceText(state)));
  if (isStateInSkippedRetryBackoff(state, currentFingerprint)) {
    return false;
  }

  if (options.markDirty === true || DIRTY_NODE_IDS.has(state.nodeId)) {
    return true;
  }

  return !state.hasProcessed || String(state.lastFingerprint || "") !== String(currentFingerprint || "");
}

function unlinkObservedElement(state) {
  if (!state?.observedElement) return;

  const linkedNodeIds = OBSERVED_ELEMENT_NODE_IDS.get(state.observedElement);
  if (linkedNodeIds) {
    linkedNodeIds.delete(state.nodeId);
    if (linkedNodeIds.size === 0) {
      visibilityObserver?.unobserve(state.observedElement);
    }
  }

  VISIBLE_NODE_IDS.delete(state.nodeId);
  state.observedElement = null;
}

function linkObservedElement(state, element) {
  if (!state || !(element instanceof Element)) return;
  if (state.observedElement === element) {
    if (isElementVisible(element) && isElementNearViewport(element.getBoundingClientRect())) {
      VISIBLE_NODE_IDS.add(state.nodeId);
    }
    return;
  }

  unlinkObservedElement(state);

  let linkedNodeIds = OBSERVED_ELEMENT_NODE_IDS.get(element);
  if (!linkedNodeIds) {
    linkedNodeIds = new Set();
    OBSERVED_ELEMENT_NODE_IDS.set(element, linkedNodeIds);
    visibilityObserver?.observe(element);
  }
  linkedNodeIds.add(state.nodeId);
  state.observedElement = element;

  if (isElementVisible(element) && isElementNearViewport(element.getBoundingClientRect())) {
    VISIBLE_NODE_IDS.add(state.nodeId);
  }
}

function syncObservedElement(state) {
  if (!state?.textNode?.isConnected) {
    unlinkObservedElement(state);
    return null;
  }

  const element = getRenderableParent(state.textNode);
  if (!element || isSkippableElement(element)) {
    unlinkObservedElement(state);
    return null;
  }

  linkObservedElement(state, element);
  return element;
}

function registerTextNode(textNode, options = {}) {
  if (!(textNode instanceof Text)) return null;
  const state = getNodeState(textNode);
  const element = syncObservedElement(state);
  if (!element) return null;

  getSourceText(state);
  if (options.markDirty) {
    DIRTY_NODE_IDS.add(state.nodeId);
  }

  return state;
}

function registerTextNodesInTree(root, options = {}) {
  const limit = Number.isFinite(options.limit) ? options.limit : Number.POSITIVE_INFINITY;
  const onlyVisible = Boolean(options.onlyVisible);
  const markHighSignalDirty = options.markHighSignalDirty === true;
  const highSignalDirtyLimit = Number.isFinite(options.highSignalDirtyLimit)
    ? Math.max(0, Number(options.highSignalDirtyLimit))
    : 24;
  let visitedCount = 0;
  let usefulCount = 0;
  let actionableCount = 0;
  let highSignalDirtyCount = 0;
  const maxVisited = Number.isFinite(limit)
    ? Math.max(limit + 24, limit * 4)
    : Number.POSITIVE_INFINITY;

  function registerAndCount(textNode) {
    if (usefulCount >= limit || visitedCount >= maxVisited) return;
    visitedCount += 1;

    const state = registerTextNode(textNode, options);
    if (!state) return;

    const element = getRenderableParent(state.textNode);
    const normalizedText = normalizeText(getSourceText(state));
    if (!isCandidateTextUseful(normalizedText, element)) {
      return;
    }

    if (
      markHighSignalDirty &&
      highSignalDirtyCount < highSignalDirtyLimit &&
      !state.isMasked &&
      HIGH_SIGNAL_PROFANITY_PATTERN.test(normalizedText) &&
      shouldForceHighSignalDirty(state, buildFingerprint(normalizedText))
    ) {
      DIRTY_NODE_IDS.add(state.nodeId);
      highSignalDirtyCount += 1;
    }

    usefulCount += 1;
    if (doesRegisteredStateNeedAnalysis(state, options)) {
      actionableCount += 1;
    }
  }

  if (root instanceof Text) {
    registerAndCount(root);
    return actionableCount;
  }

  if (!(root instanceof Element) && !(root instanceof DocumentFragment) && root !== document.body) {
    return actionableCount;
  }

  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      if (!(node instanceof Text)) return NodeFilter.FILTER_REJECT;
      if (!node.parentElement) return NodeFilter.FILTER_REJECT;
      if (shouldSkipTextNodeParent(node.parentElement)) {
        return NodeFilter.FILTER_REJECT;
      }
      if (onlyVisible) {
        if (!isElementVisible(node.parentElement)) return NodeFilter.FILTER_REJECT;
        if (!isElementNearViewport(node.parentElement.getBoundingClientRect())) {
          return NodeFilter.FILTER_REJECT;
        }
      }
      return NodeFilter.FILTER_ACCEPT;
    }
  });

  while (walker.nextNode()) {
    if (usefulCount >= limit || visitedCount >= maxVisited) break;
    registerAndCount(walker.currentNode);
  }

  return actionableCount;
}

function cleanupDisconnectedStates() {
  for (const [nodeId, state] of NODE_STATE_BY_ID.entries()) {
    const textConnected = Boolean(state.textNode?.isConnected);
    const wrapperConnected = Boolean(state.wrapper?.isConnected);
    if (!textConnected && !wrapperConnected) {
      unlinkObservedElement(state);
      NODE_STATE_BY_ID.delete(nodeId);
      DIRTY_NODE_IDS.delete(nodeId);
      VISIBLE_NODE_IDS.delete(nodeId);
    }
  }

  for (const [nodeId, state] of EDITABLE_VALUE_STATE_BY_ID.entries()) {
    if (!state.element?.isConnected) {
      if (state.overlayRoot?.isConnected) {
        state.overlayRoot.remove();
      }
      EDITABLE_VALUE_STATE_BY_ID.delete(nodeId);
      DIRTY_NODE_IDS.delete(nodeId);
      VISIBLE_NODE_IDS.delete(nodeId);
      MASKED_EDITABLE_STATE_IDS.delete(nodeId);
    }
  }

  for (const [nodeId, state] of ATTRIBUTE_VALUE_STATE_BY_ID.entries()) {
    if (!state.element?.isConnected) {
      ATTRIBUTE_VALUE_STATE_BY_ID.delete(nodeId);
      DIRTY_NODE_IDS.delete(nodeId);
      VISIBLE_NODE_IDS.delete(nodeId);
    }
  }
}

function buildCandidateFromState(state) {
  if (!state?.textNode?.isConnected) return null;

  const element = syncObservedElement(state);
  if (!element || !VISIBLE_NODE_IDS.has(state.nodeId)) return null;
  if (!isElementVisible(element)) {
    VISIBLE_NODE_IDS.delete(state.nodeId);
    return null;
  }

  const rect = element.getBoundingClientRect();
  if (!isElementNearViewport(rect)) {
    return null;
  }

  const text = getSourceText(state);
  const normalizedText = normalizeText(text);
  if (!isCandidateTextUseful(normalizedText, element)) {
    return null;
  }

  const analysisContainer = getAnalysisContainer(element) || element;

  return {
    nodeId: state.nodeId,
    textNode: state.textNode,
    state,
    element,
    text,
    normalizedText: normalizedText.toLowerCase(),
    analysisContainer,
    packageName: `web::${location.hostname || "unknown"}`,
    className:
      typeof element.className === "string" && element.className.trim()
        ? element.className.trim()
        : element.tagName,
    top: Math.round(rect.top + window.scrollY),
    bottom: Math.round(rect.bottom + window.scrollY),
    left: Math.round(rect.left + window.scrollX),
    right: Math.round(rect.right + window.scrollX),
    distanceFromViewport: Math.abs(rect.top),
    fingerprint: buildFingerprint(normalizedText)
  };
}

function buildForcedVisibleCandidateFromTextNode(textNode) {
  if (!(textNode instanceof Text) || !textNode.isConnected) return null;

  const state = getNodeState(textNode);
  const element = syncObservedElement(state);
  if (!element || !isElementVisible(element)) {
    return null;
  }

  const rect = element.getBoundingClientRect();
  if (!isElementNearViewport(rect)) {
    return null;
  }

  VISIBLE_NODE_IDS.add(state.nodeId);
  return buildCandidateFromState(state);
}

function buildEditableValueCandidate(element) {
  if (!isMaskableValueElement(element)) return null;

  const state = getEditableValueState(element);
  const analysisContainer = element;
  const text = getEditableElementText(element);
  const normalizedText = normalizeText(text);
  if (!normalizedText) return null;

  const rect = element.getBoundingClientRect();
  const nodeId = state.nodeId;
  VISIBLE_NODE_IDS.add(nodeId);

  return {
    nodeId,
    candidateKind: "editable-value",
    element,
    state,
    text,
    normalizedText: normalizedText.toLowerCase(),
    analysisContainer,
    packageName: `web::${location.hostname || "unknown"}`,
    className:
      typeof element.className === "string" && element.className.trim()
        ? element.className.trim()
        : element.tagName,
    top: Math.round(rect.top + window.scrollY),
    bottom: Math.round(rect.bottom + window.scrollY),
    left: Math.round(rect.left + window.scrollX),
    right: Math.round(rect.right + window.scrollX),
    distanceFromViewport: Math.abs(rect.top),
    fingerprint: buildFingerprint(normalizedText)
  };
}

function isMaskableAttributeValue(element, attributeName) {
  if (!(element instanceof Element)) return false;
  if (!ATTRIBUTE_MASK_NAMES.includes(String(attributeName || "").toLowerCase())) return false;
  if (!element.isConnected || isShieldTextManagedElement(element)) return false;
  if (!isElementVisible(element)) return false;
  if (!isElementNearViewport(element.getBoundingClientRect())) return false;
  if (
    element.closest(
      "header, nav, [role='navigation'], [role='tablist'], form, [data-shieldtext-rendered='true']"
    )
  ) {
    return false;
  }

  const value = normalizeText(element.getAttribute(attributeName) || "");
  return Boolean(
    value &&
    HIGH_SIGNAL_PROFANITY_PATTERN.test(value) &&
    isCandidateTextUseful(value, element)
  );
}

function buildAttributeValueCandidate(element, attributeName) {
  if (!isMaskableAttributeValue(element, attributeName)) return null;

  const state = getAttributeValueState(element, attributeName);
  if (!state) return null;

  const text = getAttributeSourceValue(state);
  const normalizedText = normalizeText(text);
  if (!normalizedText) return null;

  const rect = element.getBoundingClientRect();
  const nodeId = state.nodeId;
  const analysisContainer = getAnalysisContainer(element) || element;
  VISIBLE_NODE_IDS.add(nodeId);
  DIRTY_NODE_IDS.add(nodeId);

  return {
    nodeId,
    candidateKind: "attribute-value",
    element,
    state,
    text,
    normalizedText: normalizedText.toLowerCase(),
    analysisContainer,
    packageName: `web::${location.hostname || "unknown"}`,
    className:
      typeof element.className === "string" && element.className.trim()
        ? element.className.trim()
        : `${element.tagName}[${String(attributeName).toLowerCase()}]`,
    top: Math.round(rect.top + window.scrollY),
    bottom: Math.round(rect.bottom + window.scrollY),
    left: Math.round(rect.left + window.scrollX),
    right: Math.round(rect.right + window.scrollX),
    distanceFromViewport: Math.abs(rect.top),
    fingerprint: buildFingerprint(normalizedText)
  };
}

function collectAttributeValueCandidates(limit = MAX_DOMAIN_PRIORITY_CANDIDATES * 2) {
  if (isGoogleSearchPage()) {
    return [];
  }

  const selectors = isGoogleSearchPage()
    ? [
        "#search [aria-label]",
        "#search [title]",
        "#search img[alt]",
        "main [aria-label]",
        "main [title]",
        "main img[alt]",
        "#rhs [aria-label]",
        "#rhs [title]",
        "#rhs img[alt]",
        "#bres [aria-label]",
        "#botstuff [aria-label]"
      ]
    : [
        "[aria-label]",
        "[title]",
        "img[alt]"
      ];
  const candidates = [];
  const seen = new Set();

  for (const selector of selectors) {
    for (const element of document.querySelectorAll(selector)) {
      if (!(element instanceof Element) || seen.has(element)) continue;
      seen.add(element);

      for (const attributeName of ATTRIBUTE_MASK_NAMES) {
        const candidate = buildAttributeValueCandidate(element, attributeName);
        if (!candidate) continue;
        candidates.push(candidate);
        if (candidates.length >= limit) {
          return candidates;
        }
      }
    }
  }

  return candidates;
}

function getEditableCandidatePriority(candidate) {
  if (!candidate) return Number.NEGATIVE_INFINITY;

  let score = 0;
  if (candidate.element === pendingImmediateInputElement) {
    score += 120;
  }
  if (candidate.element === document.activeElement) {
    score += 100;
  }
  if (candidate.element?.matches?.('textarea[name="q"], textarea[role="combobox"], input[name="q"]')) {
    score += 48;
  }
  if (
    candidate.element instanceof HTMLInputElement &&
    (candidate.element.type || "text").toLowerCase() === "search"
  ) {
    score += 28;
  }
  if ((candidate.element?.getAttribute("role") || "").toLowerCase() === "searchbox") {
    score += 20;
  }

  score += Math.max(0, 360 - Number(candidate.distanceFromViewport || 0)) / 8;
  score += Math.min(18, normalizeText(candidate.text).length / 3);
  return score;
}

function collectEditableValueCandidates(limit = Number.POSITIVE_INFINITY) {
  const elements = document.querySelectorAll("input, textarea");
  const candidates = [];

  for (const element of elements) {
    const candidate = buildEditableValueCandidate(element);
    if (candidate) {
      candidates.push(candidate);
    }
  }

  candidates.sort((left, right) => {
    const priorityGap = getEditableCandidatePriority(right) - getEditableCandidatePriority(left);
    if (priorityGap !== 0) return priorityGap;
    if (left.distanceFromViewport !== right.distanceFromViewport) {
      return left.distanceFromViewport - right.distanceFromViewport;
    }
    return left.top - right.top;
  });

  return Number.isFinite(limit) ? candidates.slice(0, limit) : candidates;
}

function collectTextCandidatesFromElements(elements, limit = Number.POSITIVE_INFINITY, options = {}) {
  const candidates = [];
  const seenNodeIds = new Set();
  const candidateFilter =
    typeof options.candidateFilter === "function" ? options.candidateFilter : null;
  const skipParentFilter =
    typeof options.skipParentFilter === "function" ? options.skipParentFilter : shouldSkipTextNodeParent;
  const perElementLimit = Math.max(
    1,
    Number.isFinite(options.perElementLimit)
      ? Number(options.perElementLimit)
      : Number.POSITIVE_INFINITY
  );

  for (const element of elements) {
    if (!(element instanceof Element)) continue;
    if (!element.isConnected || !isElementVisible(element)) continue;
    if (!isElementNearViewport(element.getBoundingClientRect())) continue;

    registerTextNodesInTree(element, {
      onlyVisible: true,
      limit: 24
    });

    const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        if (!(node instanceof Text)) return NodeFilter.FILTER_REJECT;
        if (!node.parentElement) return NodeFilter.FILTER_REJECT;
        if (skipParentFilter(node.parentElement)) {
          return NodeFilter.FILTER_REJECT;
        }
        return NodeFilter.FILTER_ACCEPT;
      }
    });

    let perElementCount = 0;
    while (walker.nextNode()) {
      if (candidates.length >= limit) {
        return candidates;
      }
      if (perElementCount >= perElementLimit) {
        break;
      }

      const candidate = buildForcedVisibleCandidateFromTextNode(walker.currentNode);
      if (!candidate) continue;
      if (candidateFilter && !candidateFilter(candidate, element)) continue;
      if (seenNodeIds.has(candidate.nodeId)) continue;

      seenNodeIds.add(candidate.nodeId);
      candidates.push(candidate);
      perElementCount += 1;
    }
  }

  return candidates;
}

function collectGoogleSearchPriorityContainerCandidates(limit = MAX_DOMAIN_PRIORITY_CANDIDATES) {
  if (!isGoogleTextSearchAnalysisPage()) {
    return [];
  }

  const containerLimit = Math.max(MAX_HOT_PATH_CONTAINERS, Number(limit || 0));
  const containers = getGoogleVisibleAnalysisContainers(containerLimit);
  return collectTextCandidatesFromElements(
    containers,
    Math.max(1, containers.length) * MAX_GOOGLE_CANDIDATES_PER_CONTAINER,
    {
      perElementLimit: Math.min(4, MAX_GOOGLE_CANDIDATES_PER_CONTAINER),
      skipParentFilter: shouldSkipGoogleVisibleTextNodeParent
    }
  );
}

function collectGoogleHighSignalInteractiveCandidates(limit = MAX_DOMAIN_PRIORITY_CANDIDATES) {
  if (!isGoogleTextSearchAnalysisPage()) {
    return [];
  }

  const selectors = [
    "[data-container-id='main-col'] a[href]",
    "[data-container-id='main-col'] [role='button']",
    "[data-container-id='main-col'] [data-ved]",
    "[data-sfc-root='c'] a[href]",
    "[data-sfc-root='c'] [role='button']",
    "[data-sfc-root='c'] [data-ved]",
    "main button",
    "main [role='button']",
    "main a[href]",
    "main [data-ved]",
    "#search button",
    "#search [role='button']",
    "#search a[href]",
    "#search [data-ved]",
    "#bres button",
    "#bres [role='button']",
    "#bres a[href]",
    "#bres [data-ved]",
    "#botstuff button",
    "#botstuff [role='button']",
    "#botstuff a[href]",
    "#botstuff [data-ved]",
    "#rhs button",
    "#rhs [role='button']",
    "#rhs a[href]",
    "#rhs [data-ved]"
  ];
  const elements = [];
  const seenElements = new Set();

  for (const selector of selectors) {
    for (const element of document.querySelectorAll(selector)) {
      if (!(element instanceof Element)) continue;
      if (seenElements.has(element)) continue;
      if (!element.isConnected || !isElementVisible(element)) continue;
      if (!isElementNearViewport(element.getBoundingClientRect())) continue;

      const text = getElementAnalysisText(element);
      if (!text || !HIGH_SIGNAL_PROFANITY_PATTERN.test(text)) continue;
      if (SAFE_BROWSER_UI_LABELS.has(normalizeLabel(text))) continue;

      seenElements.add(element);
      elements.push(element);
      if (elements.length >= limit * 3) {
        break;
      }
    }

    if (elements.length >= limit * 3) {
      break;
    }
  }

  elements.sort((left, right) => {
    const leftRect = left.getBoundingClientRect();
    const rightRect = right.getBoundingClientRect();
    if (leftRect.top !== rightRect.top) {
      return leftRect.top - rightRect.top;
    }
    return leftRect.left - rightRect.left;
  });

  return collectTextCandidatesFromElements(elements, limit * 2, {
    perElementLimit: 3,
    skipParentFilter: shouldSkipGoogleInteractiveTextNodeParent,
    candidateFilter(candidate) {
      const text = normalizeText(candidate.text);
      return Boolean(text) && HIGH_SIGNAL_PROFANITY_PATTERN.test(text);
    }
  });
}

function collectGoogleDirectHighSignalTextCandidates(limit = MAX_DOMAIN_PRIORITY_CANDIDATES * 3) {
  if (!isGoogleTextSearchAnalysisPage()) {
    return [];
  }

  const elements = [];
  const seenElements = new Set();
  let inspectedElementCount = 0;
  const maxInspectedElements = Math.max(120, limit * 12);

  for (const selector of [
    GOOGLE_AI_OVERVIEW_SELECTOR,
    GOOGLE_HIGH_SIGNAL_TEXT_SELECTOR,
    GOOGLE_SFC_TEXT_SELECTOR,
    "#search h3, #search [role='heading'], #rso h3, #rso [role='heading']",
    "#bres [role='heading'], #botstuff [role='heading']"
  ]) {
    for (const element of document.querySelectorAll(selector)) {
      inspectedElementCount += 1;
      if (inspectedElementCount > maxInspectedElements) {
        break;
      }
      if (!(element instanceof Element)) continue;
      if (seenElements.has(element)) continue;
      if (!element.isConnected || !isElementVisible(element)) continue;
      if (element.closest("[data-shieldtext-rendered='true']")) continue;
      if (!isElementNearViewport(element.getBoundingClientRect())) continue;

      const text = getElementAnalysisText(element);
      if (!text || !HIGH_SIGNAL_PROFANITY_PATTERN.test(text)) continue;
      if (SAFE_BROWSER_UI_LABELS.has(normalizeLabel(text))) continue;

      seenElements.add(element);
      elements.push(element);
      if (elements.length >= limit * 2) {
        break;
      }
    }

    if (elements.length >= limit * 2 || inspectedElementCount > maxInspectedElements) {
      break;
    }
  }

  elements.sort((left, right) => {
    const leftRect = left.getBoundingClientRect();
    const rightRect = right.getBoundingClientRect();
    if (leftRect.top !== rightRect.top) {
      return leftRect.top - rightRect.top;
    }
    return leftRect.left - rightRect.left;
  });

  return collectTextCandidatesFromElements(elements, limit, {
    perElementLimit: 3,
    skipParentFilter: shouldSkipGoogleVisibleTextNodeParent,
    candidateFilter(candidate) {
      const text = normalizeText(candidate?.text || "");
      const isHighSignalCandidate = Boolean(text) &&
        HIGH_SIGNAL_PROFANITY_PATTERN.test(text) &&
        isGoogleVisibleHighSignalCandidate(candidate);

      if (isHighSignalCandidate && candidate?.nodeId) {
        DIRTY_NODE_IDS.add(candidate.nodeId);
      }

      return isHighSignalCandidate;
    }
  });
}

function collectGoogleVisibleHighSignalTextCandidates(limit = MAX_DOMAIN_PRIORITY_CANDIDATES * 3) {
  if (!isGoogleTextSearchAnalysisPage()) {
    return [];
  }

  const roots = [];
  const seenRoots = new Set();
  for (const selector of [
    GOOGLE_AI_OVERVIEW_SELECTOR,
    "#rso",
    "#rhs",
    "#bres",
    "#botstuff",
    "[data-sfc-root='c']",
    "[data-container-id='main-col']",
    "#search",
    "main",
    "[role='main']"
  ]) {
    for (const root of document.querySelectorAll(selector)) {
      if (!(root instanceof Element)) continue;
      if (seenRoots.has(root)) continue;
      if (!root.isConnected || !isElementVisible(root)) continue;
      if (root.closest("[data-shieldtext-rendered='true']")) continue;
      if (!isElementNearViewport(root.getBoundingClientRect())) continue;
      let coveredByExistingRoot = false;
      for (let index = roots.length - 1; index >= 0; index -= 1) {
        const existingRoot = roots[index];
        if (existingRoot.contains(root)) {
          seenRoots.delete(existingRoot);
          roots.splice(index, 1);
          continue;
        }
        if (root.contains(existingRoot)) {
          coveredByExistingRoot = true;
        }
      }
      if (coveredByExistingRoot) continue;
      seenRoots.add(root);
      roots.push(root);
    }
  }

  const candidates = [];
  const seenNodeIds = new Set();
  let visitedCount = 0;
  const maxVisitedCount = Math.min(
    GOOGLE_VISIBLE_HIGH_SIGNAL_SCAN_NODE_LIMIT,
    Math.max(120, limit * 16)
  );

  for (const root of roots) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        visitedCount += 1;
        if (visitedCount > maxVisitedCount) {
          return NodeFilter.FILTER_REJECT;
        }

        if (!(node instanceof Text)) return NodeFilter.FILTER_REJECT;
        const parent = node.parentElement;
        if (!parent || shouldSkipGoogleVisibleTextNodeParent(parent)) {
          return NodeFilter.FILTER_REJECT;
        }

        const text = normalizeText(node.nodeValue || "");
        if (!text || !HIGH_SIGNAL_PROFANITY_PATTERN.test(text)) {
          return NodeFilter.FILTER_REJECT;
        }

        if (!isElementVisible(parent) || !isElementNearViewport(parent.getBoundingClientRect())) {
          return NodeFilter.FILTER_REJECT;
        }

        return NodeFilter.FILTER_ACCEPT;
      }
    });

    while (walker.nextNode()) {
      if (candidates.length >= limit || visitedCount > maxVisitedCount) {
        break;
      }

      const candidate = buildForcedVisibleCandidateFromTextNode(walker.currentNode);
      if (!candidate || seenNodeIds.has(candidate.nodeId)) {
        continue;
      }

      seenNodeIds.add(candidate.nodeId);
      DIRTY_NODE_IDS.add(candidate.nodeId);
      candidates.push(candidate);
    }

    if (candidates.length >= limit || visitedCount > maxVisitedCount) {
      break;
    }
  }

  candidates.sort((left, right) => {
    if (left.distanceFromViewport !== right.distanceFromViewport) {
      return left.distanceFromViewport - right.distanceFromViewport;
    }
    if (left.top !== right.top) {
      return left.top - right.top;
    }
    return left.left - right.left;
  });

  return candidates.slice(0, limit);
}

function isUsefulGoogleDynamicContentText(text) {
  const normalized = normalizeText(text || "");
  if (!normalized) return false;
  if (SAFE_BROWSER_UI_LABELS.has(normalizeLabel(normalized))) return false;
  if (/^(AI\s*개요|AI\s*Overview|더보기|관련 검색어)$/i.test(normalized)) return false;
  if (/^[\d\s.,:;()[\]{}'"’“”·ㆍ-]+$/.test(normalized)) return false;
  return normalized.length >= 2;
}

function collectGoogleDynamicContentTextCandidates(limit = MAX_DOMAIN_PRIORITY_CANDIDATES) {
  if (!isGoogleTextSearchAnalysisPage()) {
    return [];
  }

  const roots = [];
  const seenRoots = new Set();
  for (const selector of [
    GOOGLE_AI_OVERVIEW_SELECTOR,
    GOOGLE_SFC_CONTAINER_SELECTOR,
    "[data-container-id='main-col']",
    "[data-sfc-root='c']"
  ]) {
    for (const root of document.querySelectorAll(selector)) {
      if (!(root instanceof Element)) continue;
      if (seenRoots.has(root)) continue;
      if (!root.isConnected || !isElementVisible(root)) continue;
      if (root.closest("[data-shieldtext-rendered='true']")) continue;
      if (!isElementNearViewport(root.getBoundingClientRect())) continue;
      seenRoots.add(root);
      roots.push(root);
    }
  }

  return collectTextCandidatesFromElements(roots, limit, {
    perElementLimit: 6,
    skipParentFilter: shouldSkipGoogleVisibleTextNodeParent,
    candidateFilter(candidate) {
      const text = normalizeText(candidate?.text || "");
      if (!isUsefulGoogleDynamicContentText(text)) return false;
      if (candidate?.nodeId) {
        DIRTY_NODE_IDS.add(candidate.nodeId);
      }
      return true;
    }
  });
}

function markGoogleHighSignalCandidatesDirty(limit = 16) {
  if (!isGoogleTextSearchAnalysisPage() || limit <= 0) {
    return 0;
  }

  const selectedNodeIds = new Set();
  let dirtyCount = 0;

  const markCandidate = (candidate) => {
    if (dirtyCount >= limit || !candidate?.nodeId || selectedNodeIds.has(candidate.nodeId)) {
      return;
    }

    const text = normalizeText(
      candidate.text || (candidate.state ? getSourceText(candidate.state) : "") || ""
    );
    if (!text || !HIGH_SIGNAL_PROFANITY_PATTERN.test(text)) {
      return;
    }

    const fingerprint = buildFingerprint(text);
    if (
      !candidate.state ||
      candidate.state.isMasked ||
      !shouldForceHighSignalDirty(candidate.state, fingerprint)
    ) {
      return;
    }

    selectedNodeIds.add(candidate.nodeId);
    DIRTY_NODE_IDS.add(candidate.nodeId);
    dirtyCount += 1;
  };

  for (const candidate of collectGoogleDirectHighSignalTextCandidates(limit)) {
    markCandidate(candidate);
  }

  const remainingLimit = Math.max(0, limit - dirtyCount);
  if (remainingLimit > 0) {
    for (const candidate of collectGoogleVisibleHighSignalTextCandidates(remainingLimit)) {
      markCandidate(candidate);
    }
  }

  return dirtyCount;
}

function collectGoogleSearchPriorityCandidates(limit = MAX_DOMAIN_PRIORITY_CANDIDATES, options = {}) {
  if (!isGoogleTextSearchAnalysisPage()) {
    return [];
  }
  const includeDeepVisibleScan = options.includeDeepVisibleScan !== false;

  const selectors = [
    GOOGLE_AI_OVERVIEW_SELECTOR,
    GOOGLE_HIGH_SIGNAL_TEXT_SELECTOR,
    GOOGLE_SFC_TEXT_SELECTOR,
    GOOGLE_SFC_CONTAINER_SELECTOR,
    "main [data-attrid='title']",
    "main .PZPZlf",
    "main .B5dxMb",
    "#rhs [role='heading']",
    "#rhs [aria-level]",
    "#rhs [data-attrid='title']",
    "#rhs .PZPZlf",
    "#rhs .B5dxMb",
    "#rso h3",
    "#rso [role='heading']",
    "#rso [aria-level='3']",
    "#rso a[href] h3",
    "#rso .LC20lb",
    "#rso .DKV0Md",
    "#rso .VwiC3b",
    "#rso .MUxGbd",
    "#rso .yXK7lf",
    "#rso [data-sncf]",
    "#rso [data-snf]",
    "#rso [data-content-feature]",
    "#rso [data-sokoban-container]",
    "#rso [data-attrid]",
    "#rso .wDYxhc",
    "#search h3",
    "#search [role='heading']",
    "#search [aria-level='3']",
    "#search a[href] h3",
    "#search .LC20lb",
    "#search .DKV0Md",
    "#search .VwiC3b",
    "#search .MUxGbd",
    "#search .yXK7lf",
    "#search [data-sncf]",
    "#search [data-snf]",
    "#search button",
    "#search [role='button']",
    "#bres button",
    "#bres [role='button']",
    "#bres a[href]",
    "#bres [data-ved]",
    "#botstuff button",
    "#botstuff [role='button']",
    "#botstuff a[href]",
    "#botstuff [data-ved]",
    "#rhs button",
    "#rhs [role='button']",
    "#rhs [data-ved]"
  ];

  const elements = [];
  const seenElements = new Set();
  for (const selector of selectors) {
    for (const element of document.querySelectorAll(selector)) {
      if (!(element instanceof Element)) continue;
      if (seenElements.has(element)) continue;
      seenElements.add(element);
      elements.push(element);
      if (elements.length >= limit * 2) {
        break;
      }
    }
    if (elements.length >= limit * 2) {
      break;
    }
  }

  const candidates = [];
  const seenNodeIds = new Set();

  for (const candidate of collectGoogleDirectHighSignalTextCandidates(limit * 2)) {
    if (seenNodeIds.has(candidate.nodeId)) continue;
    seenNodeIds.add(candidate.nodeId);
    candidates.push(candidate);
    if (candidates.length >= limit * 2) {
      return candidates;
    }
  }

  for (const candidate of collectGoogleHighSignalInteractiveCandidates(limit)) {
    if (seenNodeIds.has(candidate.nodeId)) continue;
    seenNodeIds.add(candidate.nodeId);
    candidates.push(candidate);
    if (candidates.length >= limit * 2) {
      return candidates;
    }
  }

  if (includeDeepVisibleScan) {
    for (const candidate of collectGoogleVisibleHighSignalTextCandidates(limit * 2)) {
      if (seenNodeIds.has(candidate.nodeId)) continue;
      seenNodeIds.add(candidate.nodeId);
      candidates.push(candidate);
      if (candidates.length >= limit * 3) {
        return candidates;
      }
    }
  }

  for (const candidate of collectGoogleSearchPriorityContainerCandidates(limit)) {
    if (seenNodeIds.has(candidate.nodeId)) continue;
    seenNodeIds.add(candidate.nodeId);
    candidates.push(candidate);
    if (candidates.length >= limit * 2) {
      return candidates;
    }
  }

  for (const candidate of collectTextCandidatesFromElements(elements, limit, {
    skipParentFilter: shouldSkipGoogleVisibleTextNodeParent
  })) {
    if (seenNodeIds.has(candidate.nodeId)) continue;
    seenNodeIds.add(candidate.nodeId);
    candidates.push(candidate);
    if (candidates.length >= limit * 2) {
      break;
    }
  }

  return candidates;
}

function collectYouTubeDirectHighSignalTextCandidates(limit = MAX_DOMAIN_PRIORITY_CANDIDATES * 2) {
  if (!isYouTubePage()) {
    return [];
  }

  const elements = [];
  const seenElements = new Set();
  let inspectedElementCount = 0;
  const maxInspectedElements = Math.max(80, limit * 10);

  for (const element of document.querySelectorAll(YOUTUBE_HIGH_SIGNAL_TEXT_SELECTOR)) {
    inspectedElementCount += 1;
    if (inspectedElementCount > maxInspectedElements) {
      break;
    }
    if (!(element instanceof Element)) continue;
    if (seenElements.has(element)) continue;
    if (!element.isConnected || !isElementVisible(element)) continue;
    if (!isElementNearViewport(element.getBoundingClientRect())) continue;
    if (!isYouTubeMaskTargetElement(element)) continue;

    const text = getElementAnalysisText(element);
    if (!text || !HIGH_SIGNAL_PROFANITY_PATTERN.test(text)) continue;
    if (SAFE_BROWSER_UI_LABELS.has(normalizeLabel(text))) continue;

    seenElements.add(element);
    elements.push(element);
    if (elements.length >= limit) {
      break;
    }
  }

  elements.sort((left, right) => {
    const leftRect = left.getBoundingClientRect();
    const rightRect = right.getBoundingClientRect();
    if (leftRect.top !== rightRect.top) {
      return leftRect.top - rightRect.top;
    }
    return leftRect.left - rightRect.left;
  });

  return collectTextCandidatesFromElements(elements, limit, {
    perElementLimit: 3,
    candidateFilter(candidate) {
      const text = normalizeText(candidate?.text || "");
      const isHighSignalCandidate = Boolean(text) &&
        HIGH_SIGNAL_PROFANITY_PATTERN.test(text) &&
        isYouTubeMaskTargetElement(candidate?.element);

      if (isHighSignalCandidate && candidate?.nodeId) {
        DIRTY_NODE_IDS.add(candidate.nodeId);
      }

      return isHighSignalCandidate;
    }
  });
}

function collectYouTubePriorityCandidates(limit = MAX_DOMAIN_PRIORITY_CANDIDATES) {
  if (!isYouTubePage()) {
    return [];
  }

  const containers = getYouTubeVisibleAnalysisContainers(
    Math.max(MAX_HOT_PATH_CONTAINERS, Number(limit || 0))
  );

  const candidates = [];
  const seenNodeIds = new Set();

  for (const candidate of collectYouTubeDirectHighSignalTextCandidates(limit * 2)) {
    if (!candidate?.nodeId || seenNodeIds.has(candidate.nodeId)) continue;
    seenNodeIds.add(candidate.nodeId);
    candidates.push(candidate);
    if (candidates.length >= limit * 2) {
      return candidates;
    }
  }

  for (const candidate of collectTextCandidatesFromElements(
    containers,
    Math.max(1, containers.length) * MAX_GOOGLE_CANDIDATES_PER_CONTAINER,
    {
      perElementLimit: Math.min(6, MAX_GOOGLE_CANDIDATES_PER_CONTAINER),
      candidateFilter(candidate) {
        const text = normalizeText(candidate?.text || "");
        return Boolean(text) &&
          isCandidateTextUseful(text, candidate?.element) &&
          isYouTubeMaskTargetElement(candidate?.element);
      }
    }
  )) {
    if (!candidate?.nodeId || seenNodeIds.has(candidate.nodeId)) continue;
    seenNodeIds.add(candidate.nodeId);
    candidates.push(candidate);
    if (candidates.length >= limit * 2) {
      break;
    }
  }

  return candidates;
}

function collectGoogleSearchLightCandidates(limit = GOOGLE_SEARCH_LIGHT_CANDIDATE_LIMIT) {
  const candidates = [];
  const seenNodeIds = new Set();
  const maxCandidates = Math.max(1, Number(limit || MAX_DOMAIN_PRIORITY_CANDIDATES));

  const addCandidate = (candidate) => {
    if (!candidate?.nodeId || seenNodeIds.has(candidate.nodeId)) {
      return false;
    }
    seenNodeIds.add(candidate.nodeId);
    candidates.push(candidate);
    return candidates.length >= maxCandidates;
  };

  if (isGoogleTextSearchAnalysisPage()) {
    for (const candidate of collectEditableValueCandidates(INITIAL_EDITABLE_PASS_LIMIT)) {
      if (addCandidate(candidate)) return candidates;
    }

    for (const candidate of collectGoogleDirectHighSignalTextCandidates(maxCandidates * 2)) {
      if (addCandidate(candidate)) return candidates;
    }

    for (const candidate of collectGoogleVisibleHighSignalTextCandidates(maxCandidates * 2)) {
      if (addCandidate(candidate)) return candidates;
    }

    for (const candidate of collectGoogleDynamicContentTextCandidates(maxCandidates * 2)) {
      if (addCandidate(candidate)) return candidates;
    }

    for (const candidate of collectGoogleSearchPriorityCandidates(maxCandidates, {
      includeDeepVisibleScan: false
    })) {
      if (addCandidate(candidate)) return candidates;
    }
  }

  if (!isGoogleTextSearchAnalysisPage()) {
    for (const candidate of collectEditableValueCandidates(INITIAL_EDITABLE_PASS_LIMIT)) {
      if (addCandidate(candidate)) return candidates;
    }
  }

  return candidates;
}

function collectGoogleImageSearchLightCandidates() {
  return collectGoogleSearchLightCandidates();
}

function collectCandidates(runReason = "") {
  cleanupDisconnectedStates();

  if (isGoogleSearchPage()) {
    return collectGoogleSearchLightCandidates();
  }

  if (isPerformanceGuardActive() && !shouldAllowPipelineDuringPerformanceGuard(runReason)) {
    return collectEditableValueCandidates(INITIAL_EDITABLE_PASS_LIMIT);
  }

  const candidates = [];
  const seenNodeIds = new Set();

  for (const candidate of collectGoogleSearchPriorityCandidates()) {
    if (seenNodeIds.has(candidate.nodeId)) continue;
    seenNodeIds.add(candidate.nodeId);
    candidates.push(candidate);
  }

  for (const candidate of collectYouTubePriorityCandidates()) {
    if (seenNodeIds.has(candidate.nodeId)) continue;
    seenNodeIds.add(candidate.nodeId);
    candidates.push(candidate);
  }

  for (const candidate of collectAttributeValueCandidates()) {
    if (seenNodeIds.has(candidate.nodeId)) continue;
    seenNodeIds.add(candidate.nodeId);
    candidates.push(candidate);
  }

  for (const nodeId of VISIBLE_NODE_IDS) {
    const state = NODE_STATE_BY_ID.get(nodeId);
    const candidate = buildCandidateFromState(state);
    if (candidate) {
      if (seenNodeIds.has(candidate.nodeId)) continue;
      seenNodeIds.add(candidate.nodeId);
      candidates.push(candidate);
    }
  }

  const editableElements = [];
  if (pendingImmediateInputElement instanceof Element) {
    editableElements.push(pendingImmediateInputElement);
  }
  if (
    document.activeElement instanceof Element &&
    document.activeElement !== pendingImmediateInputElement
  ) {
    editableElements.push(document.activeElement);
  }

  for (const element of editableElements) {
    const candidate = buildEditableValueCandidate(element);
    if (!candidate || seenNodeIds.has(candidate.nodeId)) continue;
    seenNodeIds.add(candidate.nodeId);
    candidates.push(candidate);
  }

  const hints = buildRealtimeHints(cachedSettings);
  candidates.sort((left, right) => {
    const urgencyGap = getCandidateUrgency(right, hints) - getCandidateUrgency(left, hints);
    if (urgencyGap !== 0) {
      return urgencyGap;
    }

    if (left.distanceFromViewport !== right.distanceFromViewport) {
      return left.distanceFromViewport - right.distanceFromViewport;
    }

    return left.top - right.top;
  });

  return candidates.slice(0, MAX_CANDIDATES);
}

function isBroadAnalysisReason(runReason) {
  return (
    runReason === "background-validation" ||
    runReason === "manual-request" ||
    runReason === "manual-request-after-inject" ||
    runReason === "manual" ||
    runReason === "settings-updated"
  );
}

function containsAnyWord(text, words) {
  if (!text || !Array.isArray(words) || words.length === 0) return false;
  return words.some((word) => word && text.includes(word.toLowerCase()));
}

function getCustomWordList(value) {
  const seen = new Set();
  return parseWordList(value)
    .map((item) => item.toLowerCase())
    .filter((item) => {
      if (!item || seen.has(item)) return false;
      seen.add(item);
      return true;
    });
}

function findCustomWordSpans(text, words) {
  const source = String(text || "");
  const loweredSource = source.toLowerCase();
  if (!source || !Array.isArray(words) || words.length === 0) return [];

  const spans = [];
  for (const word of words) {
    if (!word) continue;

    let offset = 0;
    while (offset < loweredSource.length) {
      const start = loweredSource.indexOf(word, offset);
      if (start === -1) break;

      const end = start + word.length;
      spans.push({
        start,
        end,
        score: 1,
        text: source.slice(start, end),
        keyword: word
      });
      offset = Math.max(start + 1, end);
    }
  }

  return spans.sort((left, right) => left.start - right.start || left.end - right.end);
}

function buildRealtimeHints(settings) {
  return {
    allowWords: getCustomWordList(settings?.customAllowWords),
    blockWords: getCustomWordList(settings?.customBlockWords)
  };
}

function getCandidateUrgency(candidate, hints) {
  const loweredText = normalizeText(candidate?.text || "").toLowerCase();
  if (!loweredText) return 0;

  if (containsAnyWord(loweredText, hints?.allowWords)) {
    return 0;
  }

  let score = 0;

  if (candidate?.candidateKind === "editable-value") {
    score += 6;
  }

  if (candidate?.candidateKind === "attribute-value") {
    score += 10;
  }

  if (shouldPreferStandaloneAnalysis(candidate)) {
    score += 8;
  }

  if (isGooglePriorityCandidate(candidate)) {
    score += 6;
  }

  if (isGoogleVisibleHighSignalCandidate(candidate)) {
    score += 12;
  }

  const element = candidate?.element;
  if (element instanceof Element) {
    if (element.matches("h3, [role='heading']") || element.closest("h3, [role='heading']")) {
      score += 8;
    }
    if (
      element.closest(".VwiC3b, .MUxGbd, [data-sncf], [data-snf], [data-content-feature='1'], [data-sokoban-container], .wDYxhc")
    ) {
      score += 3;
    }
  }

  if (containsAnyWord(loweredText, hints?.blockWords)) {
    score += 12;
  }

  if (HIGH_SIGNAL_PROFANITY_PATTERN.test(loweredText)) {
    score += 10;
  }

  if (/[ㄱ-ㅎㅏ-ㅣ가-힣]/.test(loweredText)) {
    score += 1;
  }

  return score;
}

function sortCandidatesByUrgency(candidates, hints) {
  return [...candidates].sort((left, right) => {
    const scoreGap = getCandidateUrgency(right, hints) - getCandidateUrgency(left, hints);
    if (scoreGap !== 0) return scoreGap;
    if (left.distanceFromViewport !== right.distanceFromViewport) {
      return left.distanceFromViewport - right.distanceFromViewport;
    }
    return left.top - right.top;
  });
}

function buildPayload(processedCandidates, totalCandidateCount, droppedCandidateCount) {
  return {
    commentCandidates: [],
    packageName: `web::${location.hostname || "unknown"}`,
    rawTextNodes: processedCandidates.map((item) => ({
      nodeId: item.nodeId,
      approxTop: item.top,
      top: item.top,
      bottom: item.bottom,
      left: item.left,
      right: item.right,
      className: item.className,
      packageName: item.packageName,
      isVisibleToUser: true,
      text: item.text,
      displayText: item.text,
      contentDescription: item.text
    })),
    timestamp: Date.now(),
    totalCandidateCount,
    selectedCandidateCount: processedCandidates.length,
    droppedCandidateCount
  };
}

function selectForegroundCandidatesByContainer(candidates, containerLimit) {
  const selected = [];
  const selectedContainers = new Set();

  for (const candidate of candidates) {
    const container = candidate.analysisContainer || candidate.element;
    if (!container) continue;

    if (!selectedContainers.has(container)) {
      if (selectedContainers.size >= containerLimit) {
        continue;
      }
      selectedContainers.add(container);
    }

    selected.push(candidate);
  }

  return selected;
}

function isShortHighSignalCandidate(candidate) {
  const text = normalizeText(candidate?.text || "");
  if (!text) return false;
  if (!HIGH_SIGNAL_PROFANITY_PATTERN.test(text)) return false;

  const compactLength = text.replace(/\s+/g, "").length;
  const tokenCount = text.split(/\s+/).filter(Boolean).length;
  if (compactLength <= 12) {
    return true;
  }

  return compactLength <= 24 && tokenCount <= 3;
}

function isGoogleHighSignalSurfaceCandidate(candidate) {
  if (!isGoogleTextSearchAnalysisPage() || !isShortHighSignalCandidate(candidate)) {
    return false;
  }

  const element = candidate?.element;
  if (!(element instanceof Element)) {
    return false;
  }

  if (shouldAllowGoogleInteractiveElement(element)) {
    return true;
  }

  if (
    element.closest("#search, main, [role='main'], [data-container-id='main-col'], [data-sfc-root='c']") &&
    isGoogleMaskTargetElement(element)
  ) {
    return true;
  }

  return Boolean(element.closest("#bres, #botstuff, #rhs"));
}

function isGoogleVisibleHighSignalCandidate(candidate) {
  if (!isGoogleTextSearchAnalysisPage()) {
    return false;
  }

  const text = normalizeText(candidate?.text || "");
  if (!text || !HIGH_SIGNAL_PROFANITY_PATTERN.test(text)) {
    return false;
  }

  const element = candidate?.element;
  if (!(element instanceof Element)) {
    return false;
  }

  if (shouldAllowGoogleInteractiveElement(element)) {
    return true;
  }

  if (
    element.closest("#search, #rso, main, [role='main'], #bres, #botstuff, #rhs, [data-container-id='main-col'], [data-sfc-root='c']") &&
    isGoogleMaskTargetElement(element)
  ) {
    return true;
  }

  return false;
}

function shouldPreferStandaloneAnalysis(candidate) {
  if (!candidate) {
    return false;
  }

  if (candidate.candidateKind === "editable-value") {
    return true;
  }

  if (isGoogleTextSearchAnalysisPage()) {
    if (isGoogleVisibleHighSignalCandidate(candidate) && isShortHighSignalCandidate(candidate)) {
      return true;
    }

    if (
      candidate.element instanceof Element &&
      shouldAllowGoogleInteractiveElement(candidate.element) &&
      isShortHighSignalCandidate(candidate)
    ) {
      return true;
    }
    return false;
  }

  if (!isShortHighSignalCandidate(candidate)) {
    return false;
  }

  const element = candidate.element;
  if (!(element instanceof Element)) {
    return true;
  }

  if (isGoogleTextSearchAnalysisPage()) {
    if (
      element.matches("h1, h2, h3, h4, [role='heading']") ||
      element.closest("h1, h2, h3, h4, [role='heading']")
    ) {
      return true;
    }

    if (
      element.matches("a[href]") ||
      element.closest("a[href]")
    ) {
      return true;
    }
  }

  if (
    element.closest(
      "#search .g, #search .tF2Cxc, #search .MjjYud, #search .yuRUbf, #search article, article, li, section, [role='article'], [data-hveid]"
    )
  ) {
    return false;
  }

  if (
    element.matches("a, h1, h2, h3, [role='heading']") ||
    element.closest("a, h1, h2, h3, [role='heading']")
  ) {
    return false;
  }

  return true;
}

function isForegroundMetadataCandidate(candidate) {
  if (looksLikeInteractionMetadataText(candidate?.text || "")) {
    return true;
  }

  const element = candidate?.element;
  if (!(element instanceof Element)) return false;
  if (
    isYouTubePage() &&
    element.closest(
      "#author-text, #published-time-text, #vote-count-middle, ytd-comment-engagement-bar, ytd-menu-renderer, ytd-menu-service-item-renderer"
    )
  ) {
    return true;
  }
  if (element.tagName === "CITE") return true;
  if (element.tagName === "A" && !HIGH_SIGNAL_PROFANITY_PATTERN.test(candidate.text || "")) {
    return true;
  }
  if (element.closest("header, nav, [role='navigation'], [role='tablist'], [aria-label='탐색']")) {
    return true;
  }
  return false;
}

function isGoogleSearchPage() {
  return /(^|\.)google\./i.test(location.hostname || "") && location.pathname === "/search";
}

function isGoogleImageSearchPage() {
  if (!/(^|\.)google\./i.test(location.hostname || "")) {
    return false;
  }

  if (location.pathname === "/imghp") {
    return true;
  }

  if (location.pathname !== "/search") {
    return false;
  }

  const params = new URLSearchParams(location.search || "");
  return params.get("tbm") === "isch" || params.get("udm") === "2";
}

function isGoogleTextSearchAnalysisPage() {
  return isGoogleSearchPage() && !isGoogleImageSearchPage();
}

function shouldSuppressPipelineForGoogleSearch(reason) {
  if (!isGoogleSearchPage()) {
    return false;
  }

  return !GOOGLE_SEARCH_ALLOWED_PIPELINE_REASONS.has(String(reason || ""));
}

function isPerformanceGuardActive(now = Date.now()) {
  return Number(performanceGuardUntil || 0) > now;
}

function shouldAllowPipelineDuringPerformanceGuard(reason) {
  return PERFORMANCE_GUARD_ALLOWED_PIPELINE_REASONS.has(String(reason || ""));
}

function shouldSuppressPipelineForPerformanceGuard(reason) {
  return isPerformanceGuardActive() && !shouldAllowPipelineDuringPerformanceGuard(reason);
}

function getPerformanceGuardDiagnostics(now = Date.now()) {
  const active = isPerformanceGuardActive(now);
  return {
    performanceGuardActive: active,
    performanceGuardReason: active ? performanceGuardReason : "",
    performanceGuardRemainingMs: active ? Math.max(0, Math.round(performanceGuardUntil - now)) : 0
  };
}

function activatePerformanceGuard(reason, details = {}) {
  const now = Date.now();
  performanceGuardUntil = Math.max(
    Number(performanceGuardUntil || 0),
    now + PERFORMANCE_GUARD_COOLDOWN_MS
  );
  performanceGuardReason = String(reason || "slow-page");
  scheduleHotPathStatsPersist({
    ...details,
    ...getPerformanceGuardDiagnostics(now),
    lastDecisionSource: details.lastDecisionSource || "performance-guard"
  });
}

function maybeActivatePerformanceGuard(stats, runReason) {
  if (shouldAllowPipelineDuringPerformanceGuard(runReason)) {
    return false;
  }

  const totalCandidateCount = Number(stats?.totalCandidateCount || 0);
  const durationMs = Number(stats?.durationMs || 0);
  const foregroundUnitBuildMs = Number(stats?.foregroundUnitBuildMs || 0);
  if (
    totalCandidateCount < PERFORMANCE_GUARD_CANDIDATE_LIMIT &&
    durationMs < PERFORMANCE_GUARD_SLOW_PIPELINE_MS &&
    foregroundUnitBuildMs < PERFORMANCE_GUARD_UNIT_BUILD_MS
  ) {
    return false;
  }

  const reason =
    totalCandidateCount >= PERFORMANCE_GUARD_CANDIDATE_LIMIT
      ? "candidate-count"
      : foregroundUnitBuildMs >= PERFORMANCE_GUARD_UNIT_BUILD_MS
        ? "unit-build"
        : "slow-pipeline";
  activatePerformanceGuard(reason, {
    hostname: stats?.hostname || location.hostname || "unknown",
    runReason,
    durationMs,
    totalCandidateCount,
    foregroundUnitBuildMs
  });
  return true;
}

function isRapidlyChangingRealtimeHost() {
  const hostname = String(location.hostname || "").toLowerCase();
  return /(^|\.)google\./i.test(hostname) || /(^|\.)youtube\.com$/i.test(hostname);
}

function isGooglePriorityCandidate(candidate) {
  if (!isGoogleTextSearchAnalysisPage()) return false;
  const element = candidate?.element;
  if (!(element instanceof Element)) return false;

  const inSearchSurface = element.closest(
    "#search, #rso, main, [role='main'], #rhs, #bres, #botstuff, [data-container-id='main-col'], [data-sfc-root='c']"
  );
  if (!inSearchSurface) return false;

  if (candidate?.candidateKind === "editable-value") {
    return true;
  }

  if (isGoogleSfcTextElement(element)) {
    return true;
  }

  if (
    shouldAllowGoogleInteractiveElement(element) &&
    HIGH_SIGNAL_PROFANITY_PATTERN.test(candidate?.text || "")
  ) {
    return true;
  }

  if (element.matches("h3, [role='heading']") || element.closest("h3, [role='heading']")) {
    return true;
  }

  if (element.closest(".LC20lb, .DKV0Md, .yXK7lf")) {
    return true;
  }

  if (element.closest("a[href]")) {
    return true;
  }

  if (
    element.closest(".VwiC3b, .MUxGbd, [data-sncf], [data-snf], [data-content-feature='1'], [data-sokoban-container], .wDYxhc")
  ) {
    return true;
  }

  return false;
}

function isGoogleHeadingCandidate(candidate) {
  const element = candidate?.element;
  if (!(element instanceof Element)) {
    return false;
  }

  return Boolean(
    element.matches("h3, [role='heading']") ||
      element.closest("h3, [role='heading'], .LC20lb, .DKV0Md, .yXK7lf, .NDNGvf")
  );
}

function isGoogleSnippetCandidate(candidate) {
  const element = candidate?.element;
  if (!(element instanceof Element)) {
    return false;
  }

  return Boolean(
    element.closest(".VwiC3b, .MUxGbd, [data-sncf], [data-snf], [data-content-feature='1'], [data-sokoban-container], .wDYxhc") ||
      isGoogleSfcTextElement(element)
  );
}

function selectGoogleForegroundCandidates(candidates) {
  if (!isGoogleTextSearchAnalysisPage()) {
    return [];
  }

  const visibleContainers = getGoogleVisibleAnalysisContainers(MAX_HOT_PATH_CONTAINERS);
  if (visibleContainers.length === 0) {
    return [];
  }

  const containerOrder = new Map(
    visibleContainers.map((container, index) => [container, index])
  );

  const editableCandidates = collectEditableValueCandidates(1).filter((candidate) =>
    Array.isArray(candidates) && candidates.some((item) => item.nodeId === candidate.nodeId)
  );

  const selected = [];
  const selectedNodeIds = new Set();
  const perContainerCount = new Map();

  for (const candidate of editableCandidates) {
    selected.push(candidate);
    selectedNodeIds.add(candidate.nodeId);
  }

  const highSignalInteractiveCandidates = sortCandidatesByUrgency(
    candidates.filter(
      (candidate) =>
        candidate?.candidateKind !== "editable-value" &&
        candidate.element instanceof Element &&
        shouldAllowGoogleInteractiveElement(candidate.element) &&
        isShortHighSignalCandidate(candidate)
    ),
    buildRealtimeHints(cachedSettings)
  );

  for (const candidate of highSignalInteractiveCandidates) {
    if (selected.length >= MAX_FOREGROUND_WAVE_CANDIDATES) {
      break;
    }
    if (selectedNodeIds.has(candidate.nodeId)) {
      continue;
    }

    selected.push(candidate);
    selectedNodeIds.add(candidate.nodeId);
  }

  const orderedTextCandidates = [...candidates]
    .filter((candidate) => {
      if (!candidate || candidate.candidateKind === "editable-value") {
        return false;
      }

      const container = candidate.analysisContainer;
      return containerOrder.has(container) && !isForegroundMetadataCandidate(candidate);
    })
    .sort((left, right) => {
      const leftContainerOrder = containerOrder.get(left.analysisContainer) ?? Number.MAX_SAFE_INTEGER;
      const rightContainerOrder = containerOrder.get(right.analysisContainer) ?? Number.MAX_SAFE_INTEGER;
      if (leftContainerOrder !== rightContainerOrder) {
        return leftContainerOrder - rightContainerOrder;
      }
      if (left.distanceFromViewport !== right.distanceFromViewport) {
        return left.distanceFromViewport - right.distanceFromViewport;
      }
      return left.top - right.top;
    });

  for (const candidate of orderedTextCandidates) {
    if (selectedNodeIds.has(candidate.nodeId)) continue;

    const container = candidate.analysisContainer;
    const count = perContainerCount.get(container) || 0;
    if (count >= MAX_GOOGLE_CANDIDATES_PER_CONTAINER) {
      continue;
    }

    perContainerCount.set(container, count + 1);
    selectedNodeIds.add(candidate.nodeId);
    selected.push(candidate);
  }

  return selected;
}

function selectCandidatesForRun(candidates, settings, runReason) {
  const hints = buildRealtimeHints(settings);
  const sortedCandidates = sortCandidatesByUrgency(candidates, hints);

  if (!isBroadAnalysisReason(runReason) && isGoogleTextSearchAnalysisPage()) {
    const prioritized = [];
    const selectedNodeIds = new Set();

    for (const candidate of selectGoogleForegroundCandidates(candidates)) {
      if (!candidate || selectedNodeIds.has(candidate.nodeId)) {
        continue;
      }
      selectedNodeIds.add(candidate.nodeId);
      prioritized.push(candidate);
    }

    for (const candidate of sortedCandidates) {
      if (!candidate || selectedNodeIds.has(candidate.nodeId)) {
        continue;
      }

      selectedNodeIds.add(candidate.nodeId);
      prioritized.push(candidate);

      if (prioritized.length >= MAX_BACKGROUND_CANDIDATES) {
        break;
      }
    }

    if (prioritized.length > 0) {
      return prioritized;
    }
  }

  if (isBroadAnalysisReason(runReason)) {
    const limit = runReason === "background-validation"
      ? MAX_BACKGROUND_VALIDATION_CANDIDATES
      : MAX_BACKGROUND_CANDIDATES;
    return sortedCandidates.slice(0, limit);
  }

  const domainPriorityCandidates = sortedCandidates.filter(
    (candidate) =>
      isGooglePriorityCandidate(candidate) &&
      getCandidateUrgency(candidate, hints) >= 6
  );

  const standalonePriorityCandidates = sortedCandidates.filter(
    (candidate) =>
      shouldPreferStandaloneAnalysis(candidate) &&
      getCandidateUrgency(candidate, hints) >= 8
  );
  const selectedNodeIds = new Set(
    [
      ...domainPriorityCandidates.slice(0, MAX_DOMAIN_PRIORITY_CANDIDATES),
      ...standalonePriorityCandidates.slice(0, MAX_FOREGROUND_CANDIDATES)
    ]
      .filter(Boolean)
      .map((candidate) => candidate.nodeId)
  );

  const suspiciousCandidates = sortedCandidates.filter(
    (candidate) =>
      !selectedNodeIds.has(candidate.nodeId) &&
      getCandidateUrgency(candidate, hints) >= 8 &&
      !isForegroundMetadataCandidate(candidate)
  );

  const mergedCandidates = [
    ...domainPriorityCandidates.slice(0, MAX_DOMAIN_PRIORITY_CANDIDATES),
    ...standalonePriorityCandidates.slice(0, MAX_FOREGROUND_CANDIDATES),
    ...suspiciousCandidates
  ];

  if (mergedCandidates.length > 0) {
    return mergedCandidates.slice(
      0,
      Math.max(MAX_FOREGROUND_CANDIDATES + MAX_DOMAIN_PRIORITY_CANDIDATES, 16)
    );
  }

  return [];
}

function buildForegroundAnalysisUnits(candidates) {
  if (!Array.isArray(candidates) || candidates.length === 0) {
    return [];
  }

  return candidates.filter((candidate) =>
    !looksLikeInteractionMetadataText(candidate?.text || "")
  ).map((candidate) => ({
    cacheScope:
      candidate?.candidateKind === "editable-value"
        ? "foreground-editable"
        : "foreground-standalone",
    cacheKey: normalizeText(candidate.text),
    members: [
      {
        candidate,
        start: 0,
        end: candidate.text.length
      }
    ],
    text: candidate.text
  }));
}

function selectForegroundWaveCandidates(candidates, settings, runReason) {
  const nextCandidates = (Array.isArray(candidates) ? candidates : []).filter(Boolean);
  if (nextCandidates.length === 0) {
    return [];
  }

  if (
    runReason === "input" ||
    runReason === "input-hot-path" ||
    runReason === "initial-editable-pass"
  ) {
    return nextCandidates.slice(0, 1);
  }

  if (runReason === "background-validation") {
    const hints = buildRealtimeHints(settings);
    const backgroundCandidates = sortCandidatesByUrgency(
      nextCandidates.filter((candidate) => !isForegroundMetadataCandidate(candidate)),
      hints
    );
    const candidateLimit = MAX_BACKGROUND_VALIDATION_CANDIDATES;

    if (isGoogleTextSearchAnalysisPage()) {
      return selectForegroundCandidatesByContainer(
        backgroundCandidates,
        MAX_BACKGROUND_CONTAINERS
      ).slice(0, candidateLimit);
    }

    return backgroundCandidates.slice(0, candidateLimit);
  }

  if (isGoogleTextSearchAnalysisPage()) {
    const hints = buildRealtimeHints(settings);
    const editableCandidates = collectEditableValueCandidates(1).filter((candidate) =>
      nextCandidates.some((item) => item.nodeId === candidate.nodeId)
    );
    const textCandidates = selectGoogleForegroundCandidates(nextCandidates).filter(
      (candidate) => candidate.candidateKind !== "editable-value"
    );
    const selected = [...editableCandidates];
    const selectedNodeIds = new Set(selected.map((candidate) => candidate.nodeId));
    const visibleContainers = getGoogleVisibleAnalysisContainers(MAX_FOREGROUND_WAVE_CONTAINERS);
    const candidatesByContainer = new Map();
    const directHighSignalCandidates = sortCandidatesByUrgency(
      nextCandidates.filter(
        (candidate) =>
          candidate?.candidateKind !== "editable-value" &&
          isGoogleVisibleHighSignalCandidate(candidate)
      ),
      hints
    );

    for (const candidate of directHighSignalCandidates) {
      if (selected.length >= Math.min(MAX_FOREGROUND_WAVE_CANDIDATES, 10)) {
        break;
      }
      if (selectedNodeIds.has(candidate.nodeId)) {
        continue;
      }

      selected.push(candidate);
      selectedNodeIds.add(candidate.nodeId);
    }

    for (const candidate of textCandidates) {
      if (selected.length >= MAX_FOREGROUND_WAVE_CANDIDATES) {
        break;
      }

      if (
        !selectedNodeIds.has(candidate.nodeId) &&
        isGoogleHighSignalSurfaceCandidate(candidate)
      ) {
        selected.push(candidate);
        selectedNodeIds.add(candidate.nodeId);
      }
    }

    for (const candidate of textCandidates) {
      const container = candidate.analysisContainer;
      if (!container) continue;
      if (!candidatesByContainer.has(container)) {
        candidatesByContainer.set(container, []);
      }
      candidatesByContainer.get(container).push(candidate);
    }

    const firstContainer = visibleContainers[0] || null;
    const firstContainerCandidates = firstContainer
      ? (candidatesByContainer.get(firstContainer) || [])
      : [];
    const firstTitleCandidate =
      firstContainerCandidates.find((candidate) => isGoogleHeadingCandidate(candidate)) ||
      firstContainerCandidates[0] ||
      null;
    if (firstTitleCandidate && !selectedNodeIds.has(firstTitleCandidate.nodeId)) {
      selected.push(firstTitleCandidate);
      selectedNodeIds.add(firstTitleCandidate.nodeId);
    }

    const firstSnippetCandidate = firstContainerCandidates.find(
      (candidate) =>
        !selectedNodeIds.has(candidate.nodeId) &&
        !isForegroundMetadataCandidate(candidate) &&
        isGoogleSnippetCandidate(candidate)
    );
    if (firstSnippetCandidate && !selectedNodeIds.has(firstSnippetCandidate.nodeId)) {
      selected.push(firstSnippetCandidate);
      selectedNodeIds.add(firstSnippetCandidate.nodeId);
    }

    for (const container of visibleContainers.slice(1)) {
      if (selected.length >= MAX_FOREGROUND_WAVE_CANDIDATES) {
        break;
      }

      const containerCandidates = candidatesByContainer.get(container) || [];
      const nextCandidate =
        containerCandidates.find(
          (candidate) =>
            !selectedNodeIds.has(candidate.nodeId) &&
            isGoogleHeadingCandidate(candidate)
        ) ||
        containerCandidates.find(
        (candidate) =>
          !selectedNodeIds.has(candidate.nodeId) &&
          !isForegroundMetadataCandidate(candidate)
        );
      if (!nextCandidate) {
        continue;
      }

      selected.push(nextCandidate);
      selectedNodeIds.add(nextCandidate.nodeId);
    }

    if (selected.length < MAX_FOREGROUND_WAVE_CANDIDATES) {
      for (const container of visibleContainers.slice(1)) {
        if (selected.length >= MAX_FOREGROUND_WAVE_CANDIDATES) {
          break;
        }

        const containerCandidates = candidatesByContainer.get(container) || [];
        const snippetCandidate = containerCandidates.find(
          (candidate) =>
            !selectedNodeIds.has(candidate.nodeId) &&
            !isForegroundMetadataCandidate(candidate) &&
            isGoogleSnippetCandidate(candidate)
        );
        if (!snippetCandidate) {
          continue;
        }

        selected.push(snippetCandidate);
        selectedNodeIds.add(snippetCandidate.nodeId);
      }
    }

    return selected.slice(0, MAX_FOREGROUND_WAVE_CANDIDATES);
  }

  const hints = buildRealtimeHints(settings);
  const editableCandidates = sortCandidatesByUrgency(
    nextCandidates.filter((candidate) => candidate.candidateKind === "editable-value"),
    hints
  ).slice(0, 1);
  const textCandidates = sortCandidatesByUrgency(
    nextCandidates.filter(
      (candidate) =>
        candidate.candidateKind !== "editable-value" &&
        !isForegroundMetadataCandidate(candidate)
    ),
    hints
  );

  return [...editableCandidates, ...textCandidates].slice(0, MAX_FOREGROUND_WAVE_CANDIDATES);
}

function markCandidatesAnalysisGeneration(candidates, generation) {
  for (const candidate of Array.isArray(candidates) ? candidates : []) {
    if (candidate?.state) {
      candidate.state.analysisGeneration = generation;
    }
  }
}

function getDecisionStageRank(stage) {
  return Number(DECISION_STAGE_RANK[String(stage || "")] || 0);
}

function isCandidateGenerationCurrent(candidate, generation) {
  if (!candidate?.state) {
    return false;
  }

  if (Number(generation || 0) > 0 &&
      Number(candidate.state.analysisGeneration || 0) !== Number(generation)) {
    return false;
  }

  if (candidate.candidateKind === "editable-value") {
    return Boolean(candidate.element?.isConnected) &&
      buildFingerprint(normalizeText(candidate.element.value || "")) === candidate.fingerprint;
  }

  if (candidate.candidateKind === "attribute-value") {
    return Boolean(candidate.element?.isConnected) &&
      buildFingerprint(normalizeText(getAttributeSourceValue(candidate.state))) === candidate.fingerprint;
  }

  return Boolean(candidate.textNode?.isConnected) &&
    buildFingerprint(normalizeText(getSourceText(candidate.state))) === candidate.fingerprint;
}

function shouldSkipCandidateApply(candidate, state, stage) {
  if (!candidate?.fingerprint || !state?.lastAppliedFingerprint) {
    return false;
  }

  if (String(state.lastAppliedFingerprint) !== String(candidate.fingerprint)) {
    return false;
  }

  return getDecisionStageRank(stage) < getDecisionStageRank(state.lastAppliedStage);
}

function getCandidateCurrentSourceText(candidate, state) {
  if (candidate?.candidateKind === "editable-value") {
    return String(candidate.element?.value ?? candidate.text ?? "");
  }

  if (candidate?.candidateKind === "attribute-value") {
    return String(getAttributeSourceValue(state) || candidate.text || "");
  }

  return String(getSourceText(state) || candidate?.text || "");
}

function shouldPreserveHighSignalMask(candidate, state, outcome, stage) {
  const normalizedStage = String(stage || "");
  if (
    normalizedStage !== "foreground" &&
    normalizedStage !== "reconcile" &&
    normalizedStage !== "background-validation"
  ) {
    return false;
  }

  if (outcome?.blocked || !state?.isMasked || !state?.lastAppliedBlocked) {
    return false;
  }

  if (
    !candidate?.fingerprint ||
    String(state.lastAppliedFingerprint || "") !== String(candidate.fingerprint || "")
  ) {
    return false;
  }

  return findHighSignalProfanitySpans(getCandidateCurrentSourceText(candidate, state)).length > 0;
}

function markCandidateSettledAfterLowerPriorityApplySkip(candidate) {
  const state = candidate?.state;
  if (!state?.nodeId || !candidate?.fingerprint) {
    return;
  }

  state.hasProcessed = true;
  state.lastFingerprint = String(candidate.fingerprint || "");
  state.lastSkippedAnalysisAt = 0;
  state.lastSkippedFingerprint = "";
  state.lastSkippedRetryBackoffMs = 0;
  state.lastSkippedRetryCount = 0;
  state.lastSkippedRetryFingerprint = "";
  DIRTY_NODE_IDS.delete(state.nodeId);
}

function markCandidateApplied(candidate, stage, blocked) {
  if (!candidate?.state) {
    return;
  }

  candidate.state.lastAppliedFingerprint = String(candidate.fingerprint || "");
  candidate.state.lastAppliedStage = String(stage || "");
  candidate.state.lastAppliedBlocked = Boolean(blocked);
  candidate.state.lastSkippedAnalysisAt = 0;
  candidate.state.lastSkippedFingerprint = "";
  candidate.state.lastSkippedRetryBackoffMs = 0;
  candidate.state.lastSkippedRetryCount = 0;
  candidate.state.lastSkippedRetryFingerprint = "";

  if (String(stage || "") === "reconcile") {
    candidate.state.lastReconcileFingerprint = String(candidate.fingerprint || "");
    candidate.state.lastQueuedReconcileFingerprint = "";
    candidate.state.reconcileInFlightFingerprint = "";
  }
}

function buildSyntheticTextCandidate(state, element, text) {
  const sourceText = String(text || "");
  return {
    nodeId: state.nodeId,
    textNode: state.textNode,
    state,
    element,
    text: sourceText,
    normalizedText: normalizeText(sourceText).toLowerCase(),
    analysisContainer: getAnalysisContainer(element) || element,
    packageName: `web::${location.hostname || "unknown"}`,
    className:
      typeof element?.className === "string" && element.className.trim()
        ? element.className.trim()
        : element?.tagName || "TEXT",
    top: 0,
    bottom: 0,
    left: 0,
    right: 0,
    distanceFromViewport: 0,
    fingerprint: buildFingerprint(normalizeText(sourceText))
  };
}

function isGoogleMaskTargetElement(element) {
  if (!(element instanceof Element)) {
    return false;
  }

  if (element.matches("cite, [role='navigation'], nav")) {
    return false;
  }

  if (element.closest("cite, [role='navigation'], nav")) {
    return false;
  }

  if (isGoogleSfcTextElement(element)) {
    return true;
  }

  if (element.matches("h1, h2, h3, h4, [role='heading']")) {
    return true;
  }

  if (element.closest("h1, h2, h3, h4, [role='heading']")) {
    return true;
  }

  if (element.closest(".VwiC3b, .MUxGbd, [data-sncf], [data-snf], [data-content-feature='1'], [data-sokoban-container], .wDYxhc")) {
    return true;
  }

  if (shouldAllowGoogleInteractiveElement(element)) {
    return true;
  }

  if (element.closest("[data-attrid], .kno-rdesc, .IZ6rdc")) {
    return true;
  }

  return false;
}

function shouldCreateContainerMember(segmentElement, normalizedSegment, selectedCandidate) {
  if (looksLikeInteractionMetadataText(normalizedSegment)) {
    return false;
  }

  if (selectedCandidate) {
    return true;
  }

  if (!isCandidateTextUseful(normalizedSegment, segmentElement)) {
    return false;
  }

  if (!isGoogleTextSearchAnalysisPage()) {
    return false;
  }

  return isGoogleMaskTargetElement(segmentElement);
}

function buildContainerMemberCandidate(textNode, selectedCandidate, containerCandidates) {
  if (selectedCandidate) {
    return selectedCandidate;
  }

  const candidate = buildForcedVisibleCandidateFromTextNode(textNode);
  if (!candidate) {
    return null;
  }

  const inheritedGeneration = Math.max(
    0,
    ...containerCandidates.map((item) => Number(item?.state?.analysisGeneration || 0))
  );
  if (inheritedGeneration > 0 && candidate.state) {
    candidate.state.analysisGeneration = inheritedGeneration;
  }

  return candidate;
}

function collectUnitCandidates(analysisUnits) {
  const candidatesByNodeId = new Map();

  for (const unit of Array.isArray(analysisUnits) ? analysisUnits : []) {
    for (const member of Array.isArray(unit?.members) ? unit.members : []) {
      const candidate = member?.candidate;
      if (!candidate?.nodeId) continue;
      if (!candidatesByNodeId.has(candidate.nodeId)) {
        candidatesByNodeId.set(candidate.nodeId, candidate);
      }
    }
  }

  return [...candidatesByNodeId.values()];
}

function boundAnalysisUnitForHotPath(unit) {
  if (!unit?.text || !Array.isArray(unit.members) || unit.members.length === 0) {
    return unit;
  }

  const sourceText = String(unit.text || "");
  if (sourceText.length <= MAX_HOT_PATH_CONTEXT_LENGTH) {
    return unit;
  }

  const memberStart = Math.min(...unit.members.map((member) => Number(member.start || 0)));
  const memberEnd = Math.max(...unit.members.map((member) => Number(member.end || 0)));
  let sliceStart = Math.max(0, memberStart - 36);
  let sliceEnd = Math.min(sourceText.length, memberEnd + 72);

  if ((sliceEnd - sliceStart) > MAX_HOT_PATH_CONTEXT_LENGTH) {
    sliceEnd = Math.min(sourceText.length, sliceStart + MAX_HOT_PATH_CONTEXT_LENGTH);
    if (memberEnd > sliceEnd) {
      sliceStart = Math.max(0, memberEnd - MAX_HOT_PATH_CONTEXT_LENGTH);
      sliceEnd = Math.min(sourceText.length, sliceStart + MAX_HOT_PATH_CONTEXT_LENGTH);
    }
  }

  return {
    ...unit,
    cacheKey: normalizeText(sourceText.slice(sliceStart, sliceEnd)),
    text: sourceText.slice(sliceStart, sliceEnd),
    members: unit.members
      .map((member) => ({
        ...member,
        start: Number(member.start || 0) - sliceStart,
        end: Number(member.end || 0) - sliceStart
      }))
      .filter((member) => member.end > member.start)
  };
}

function boundAnalysisUnitForReconcile(unit) {
  if (!unit?.text || !Array.isArray(unit.members) || unit.members.length === 0) {
    return unit;
  }

  const sourceText = String(unit.text || "");
  if (sourceText.length <= MAX_RECONCILE_CONTEXT_LENGTH) {
    return unit;
  }

  const memberStart = Math.min(...unit.members.map((member) => Number(member.start || 0)));
  const memberEnd = Math.max(...unit.members.map((member) => Number(member.end || 0)));
  let sliceStart = Math.max(0, memberStart - 96);
  let sliceEnd = Math.min(sourceText.length, memberEnd + 192);

  if ((sliceEnd - sliceStart) > MAX_RECONCILE_CONTEXT_LENGTH) {
    sliceEnd = Math.min(sourceText.length, sliceStart + MAX_RECONCILE_CONTEXT_LENGTH);
    if (memberEnd > sliceEnd) {
      sliceStart = Math.max(0, memberEnd - MAX_RECONCILE_CONTEXT_LENGTH);
      sliceEnd = Math.min(sourceText.length, sliceStart + MAX_RECONCILE_CONTEXT_LENGTH);
    }
  }

  return {
    ...unit,
    cacheKey: normalizeText(sourceText.slice(sliceStart, sliceEnd)),
    text: sourceText.slice(sliceStart, sliceEnd),
    members: unit.members
      .map((member) => ({
        ...member,
        start: Number(member.start || 0) - sliceStart,
        end: Number(member.end || 0) - sliceStart
      }))
      .filter((member) => member.end > member.start)
  };
}

function buildHotPathAnalysisUnits(candidates, options = {}) {
  if (!Array.isArray(candidates) || candidates.length === 0) {
    return [];
  }

  const editableCandidates = [];
  const textCandidates = [];

  for (const candidate of candidates) {
    if (candidate?.candidateKind === "editable-value") {
      editableCandidates.push(candidate);
    } else {
      textCandidates.push(candidate);
    }
  }

  const units = [];

  if (editableCandidates.length > 0) {
    units.push(...buildForegroundAnalysisUnits(editableCandidates));
  }

  if (textCandidates.length > 0) {
    if (isGoogleTextSearchAnalysisPage()) {
      const preferStandaloneGoogle = options.preferStandaloneGoogle !== false;
      if (preferStandaloneGoogle) {
        units.push(...buildForegroundAnalysisUnits(textCandidates));
      } else {
        const containerLimit = Math.max(
          1,
          Number.isFinite(options.containerLimit)
            ? Number(options.containerLimit)
            : MAX_FOREGROUND_WAVE_CONTAINERS
        );
        const contextualUnits = buildContainerAnalysisUnits(
          selectForegroundCandidatesByContainer(textCandidates, containerLimit)
        )
          .map((unit) => ({
            ...unit,
            cacheScope: "foreground-contextual",
            cacheKey: normalizeText(unit?.text || "")
          }))
          .map((unit) => (options.boundContext ? boundAnalysisUnitForHotPath(unit) : unit))
          .filter((unit) => unit?.text && Array.isArray(unit.members) && unit.members.length > 0);

        units.push(...contextualUnits);
      }
    } else if (isYouTubePage()) {
      const containerLimit = Math.max(
        1,
        Number.isFinite(options.containerLimit)
          ? Number(options.containerLimit)
          : MAX_FOREGROUND_WAVE_CONTAINERS
      );
      const contextualUnits = buildContainerAnalysisUnits(
        selectForegroundCandidatesByContainer(textCandidates, containerLimit)
      )
        .map((unit) => ({
          ...unit,
          cacheScope: "foreground-contextual",
          cacheKey: normalizeText(unit?.text || "")
        }))
        .map((unit) => (options.boundContext ? boundAnalysisUnitForHotPath(unit) : unit))
        .filter((unit) => unit?.text && Array.isArray(unit.members) && unit.members.length > 0);

      units.push(...contextualUnits);
    } else {
      units.push(...buildForegroundAnalysisUnits(textCandidates));
    }
  }

  return units;
}

function buildContainerAnalysisUnits(candidates) {
  if (!Array.isArray(candidates) || candidates.length === 0) {
    return [];
  }

  const standaloneCandidates = candidates.filter((candidate) =>
    shouldPreferStandaloneAnalysis(candidate)
  );
  const contextualCandidates = candidates.filter(
    (candidate) => !shouldPreferStandaloneAnalysis(candidate)
  );

  const units = [];
  if (standaloneCandidates.length > 0) {
    units.push(...buildForegroundAnalysisUnits(standaloneCandidates));
  }

  if (contextualCandidates.length === 0) {
    return units;
  }

  const groupedCandidates = new Map();

  for (const candidate of contextualCandidates) {
    const container = candidate.analysisContainer || getAnalysisContainer(candidate.element) || candidate.element;
    if (!container) continue;
    const key = container;
    if (!groupedCandidates.has(key)) {
      groupedCandidates.set(key, []);
    }
    groupedCandidates.get(key).push(candidate);
  }

  for (const [container, containerCandidates] of groupedCandidates.entries()) {
    const selectedByNodeId = new Map(
      containerCandidates.map((candidate) => [candidate.nodeId, candidate])
    );
    const memberByNodeId = new Map();
    const members = [];
    let text = "";
    let offset = 0;

    const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        if (!(node instanceof Text)) return NodeFilter.FILTER_REJECT;
        if (!node.parentElement) return NodeFilter.FILTER_REJECT;
        if (shouldSkipTextNodeParent(node.parentElement)) {
          return NodeFilter.FILTER_REJECT;
        }
        return NodeFilter.FILTER_ACCEPT;
      }
    });

    while (walker.nextNode()) {
      const textNode = walker.currentNode;
      const state = registerTextNode(textNode);
      if (!state) continue;

      const segmentElement = getRenderableParent(textNode);
      const segmentText = getSourceText(state);
      const normalizedSegment = normalizeText(segmentText);
      const selectedCandidate = selectedByNodeId.get(state.nodeId);

      if (!normalizedSegment) continue;
      if (!shouldCreateContainerMember(segmentElement, normalizedSegment, selectedCandidate)) {
        continue;
      }

      if (text.length > 0) {
        text += "\n";
        offset += 1;
      }

      const start = offset;
      text += segmentText;
      offset += segmentText.length;

      const memberCandidate = buildContainerMemberCandidate(
        textNode,
        selectedCandidate,
        containerCandidates
      );
      if (!memberCandidate) {
        continue;
      }

      if (!memberByNodeId.has(memberCandidate.nodeId)) {
        const member = {
          candidate: memberCandidate,
          start,
          end: offset
        };
        memberByNodeId.set(memberCandidate.nodeId, member);
        members.push(member);
      }
    }

    if (!text.trim() || members.length === 0) {
      for (const candidate of containerCandidates) {
        units.push({
          text: candidate.text,
          members: [
            {
              candidate,
              start: 0,
              end: candidate.text.length
            }
          ]
        });
      }
      continue;
    }

    const coveredNodeIds = new Set(members.map((member) => member.candidate.nodeId));
    units.push({ cacheKey: normalizeText(text), text, members });

    for (const candidate of containerCandidates) {
      if (coveredNodeIds.has(candidate.nodeId)) continue;
      units.push({
        cacheKey: normalizeText(candidate.text),
        text: candidate.text,
        members: [
          {
            candidate,
            start: 0,
            end: candidate.text.length
          }
        ]
      });
    }
  }

  return units;
}

function buildContextualAnalysisUnits(candidates) {
  if (!Array.isArray(candidates) || candidates.length === 0) {
    return [];
  }

  const groupedCandidates = new Map();
  for (const candidate of candidates) {
    if (!candidate || candidate.candidateKind === "editable-value") {
      continue;
    }

    const container =
      candidate.analysisContainer || getAnalysisContainer(candidate.element) || candidate.element;
    if (!container) continue;

    if (!groupedCandidates.has(container)) {
      groupedCandidates.set(container, []);
    }
    groupedCandidates.get(container).push(candidate);
  }

  const units = [];
  for (const [container, containerCandidates] of groupedCandidates.entries()) {
    const selectedByNodeId = new Map(
      containerCandidates.map((candidate) => [candidate.nodeId, candidate])
    );
    const memberByNodeId = new Map();
    const members = [];
    let text = "";
    let offset = 0;

    const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        if (!(node instanceof Text)) return NodeFilter.FILTER_REJECT;
        if (!node.parentElement) return NodeFilter.FILTER_REJECT;
        if (shouldSkipTextNodeParent(node.parentElement)) {
          return NodeFilter.FILTER_REJECT;
        }
        return NodeFilter.FILTER_ACCEPT;
      }
    });

    while (walker.nextNode()) {
      const textNode = walker.currentNode;
      const state = registerTextNode(textNode);
      if (!state) continue;

      const segmentElement = getRenderableParent(textNode);
      const segmentText = getSourceText(state);
      const normalizedSegment = normalizeText(segmentText);
      const selectedCandidate = selectedByNodeId.get(state.nodeId);

      if (!normalizedSegment) continue;
      const shouldIncludeSegment =
        Boolean(selectedCandidate) ||
        isCandidateTextUseful(normalizedSegment, segmentElement) ||
        (isGoogleTextSearchAnalysisPage() && isGoogleMaskTargetElement(segmentElement));

      if (!shouldIncludeSegment) {
        continue;
      }

      if (text.length > 0) {
        text += "\n";
        offset += 1;
      }

      const start = offset;
      text += segmentText;
      offset += segmentText.length;

      const memberCandidate = buildContainerMemberCandidate(
        textNode,
        selectedCandidate,
        containerCandidates
      );
      if (!memberCandidate) {
        continue;
      }

      if (!memberByNodeId.has(memberCandidate.nodeId)) {
        const member = {
          candidate: memberCandidate,
          start,
          end: offset
        };
        memberByNodeId.set(memberCandidate.nodeId, member);
        members.push(member);
      }
    }

    if (!text.trim() || members.length === 0) {
      for (const candidate of containerCandidates) {
        units.push({
          cacheScope: "reconcile-fallback",
          cacheKey: normalizeText(candidate.text),
          text: candidate.text,
          members: [
            {
              candidate,
              start: 0,
              end: candidate.text.length
            }
          ]
        });
      }
      continue;
    }

    units.push({
      cacheScope: "reconcile-contextual",
      cacheKey: normalizeText(text),
      text,
      members
    });
  }

  return units
    .map((unit) => boundAnalysisUnitForReconcile(unit))
    .filter((unit) => unit?.text && Array.isArray(unit.members) && unit.members.length > 0);
}

function emptyCategoryHits() {
  return {
    abuse: 0,
    hate: 0,
    insult: 0,
    spam: 0,
    custom: 0
  };
}

function getAnalysisCacheKey(entry) {
  const scope = normalizeLabel(entry?.cacheScope || "default");
  const sensitivity = normalizeSensitivity(
    entry?.cacheSensitivity ?? cachedSettings?.sensitivity ?? DEFAULT_SETTINGS.sensitivity
  );
  const backendKey = normalizeText(
    entry?.cacheApiBaseUrl ?? cachedSettings?.backendApiBaseUrl ?? DEFAULT_SETTINGS.backendApiBaseUrl
  );
  const textKey = normalizeText(entry?.cacheKey || entry?.text || "");
  return `${ANALYSIS_CACHE_SCHEMA_VERSION}::${backendKey}::${scope}::${sensitivity}::${textKey}`;
}

function getCachedAnalysis(entry) {
  const key = getAnalysisCacheKey(entry);
  if (!ANALYSIS_CACHE.has(key)) return null;

  const cached = ANALYSIS_CACHE.get(key);
  if (!cached || typeof cached !== "object") {
    ANALYSIS_CACHE.delete(key);
    return null;
  }
  if (Number(cached.expiresAt || 0) <= Date.now()) {
    ANALYSIS_CACHE.delete(key);
    return null;
  }
  ANALYSIS_CACHE.delete(key);
  ANALYSIS_CACHE.set(key, cached);
  return cached.value;
}

function shouldReuseCachedAnalysis(entry, cachedValue) {
  if (!cachedValue || typeof cachedValue !== "object") {
    return false;
  }

  if (cachedValue.__shieldtextSkipped === true) {
    return false;
  }

  if (cachedValue.is_offensive) {
    return true;
  }

  const scope = normalizeLabel(entry?.cacheScope || "");
  if (
    !cachedValue.is_offensive &&
    isRapidlyChangingRealtimeHost() &&
    (scope === "reconcile-contextual" || scope === "reconcile-fallback")
  ) {
    return false;
  }

  if (scope === "reconcile-contextual" || scope === "reconcile-fallback") {
    return true;
  }

  const text = normalizeText(entry?.text || entry?.cacheKey || "");
  if (!text) {
    return false;
  }

  if (HIGH_SIGNAL_PROFANITY_PATTERN.test(text)) {
    return false;
  }

  return scope === "foreground-editable";
}

function shouldCacheAnalysisResult(value) {
  if (!value || typeof value !== "object") {
    return false;
  }

  if (value.__shieldtextSkipped === true) {
    return false;
  }

  const hasExpectedShape = Boolean(
    "is_offensive" in value &&
    "is_profane" in value &&
    "is_toxic" in value &&
    "is_hate" in value
  );
  if (!hasExpectedShape) {
    return false;
  }

  const sourceText = String(value.original || value.text || "");
  if (value.is_offensive && normalizeEvidenceSpans(value.evidence_spans, sourceText).length === 0) {
    return false;
  }

  return true;
}

function getAnalysisCacheTtlMs(entry, value) {
  if (!value || typeof value !== "object") {
    return 0;
  }

  if (value.is_offensive) {
    return OFFENSIVE_CACHE_TTL_MS;
  }

  const scope = normalizeLabel(entry?.cacheScope || "");
  if (scope === "foreground-standalone") {
    return 0;
  }
  if (scope === "foreground-editable") {
    return 350;
  }
  if (scope === "foreground-contextual") {
    return 0;
  }
  if (scope === "reconcile-contextual") {
    return RECONCILE_CONTEXTUAL_SAFE_CACHE_TTL_MS;
  }
  return FOREGROUND_STANDALONE_SAFE_CACHE_TTL_MS;
}

function setCachedAnalysis(entry, value) {
  const key = getAnalysisCacheKey(entry);
  if (!key) return;

  if (!shouldCacheAnalysisResult(value)) {
    ANALYSIS_CACHE.delete(key);
    return;
  }

  const ttlMs = getAnalysisCacheTtlMs(entry, value);
  if (ttlMs <= 0) {
    ANALYSIS_CACHE.delete(key);
    return;
  }

  if (ANALYSIS_CACHE.has(key)) {
    ANALYSIS_CACHE.delete(key);
  }

  ANALYSIS_CACHE.set(key, {
    value,
    expiresAt: Date.now() + ttlMs
  });

  while (ANALYSIS_CACHE.size > ANALYSIS_CACHE_LIMIT) {
    const oldestKey = ANALYSIS_CACHE.keys().next().value;
    ANALYSIS_CACHE.delete(oldestKey);
  }
}

function chunkArray(items, chunkSize) {
  const chunks = [];
  for (let index = 0; index < items.length; index += chunkSize) {
    chunks.push(items.slice(index, index + chunkSize));
  }
  return chunks;
}

function getBackendRequestBatchSize(analysisMode) {
  const mode = String(analysisMode || "");
  if (mode === "background-validation") {
    return BACKGROUND_VALIDATION_BACKEND_BATCH_SIZE;
  }
  if (mode === "reconcile") {
    return RECONCILE_BACKEND_BATCH_SIZE;
  }
  return FOREGROUND_BACKEND_BATCH_SIZE;
}

function isRenderableEvidenceSpan(spanText) {
  const text = normalizeText(spanText);
  if (!text) return false;

  if (/\s/.test(text) && !HIGH_SIGNAL_PROFANITY_PATTERN.test(text)) {
    return false;
  }

  if (SAFE_BROWSER_UI_LABELS.has(normalizeLabel(text))) {
    return false;
  }

  if (/^[a-z0-9._:/-]+$/i.test(text) && !HIGH_SIGNAL_PROFANITY_PATTERN.test(text)) {
    return false;
  }

  return true;
}

function isSafeHighSignalCompoundSpan(sourceText, start, end, contextText = "") {
  const source = String(sourceText || "");
  const spanStart = Math.max(0, Number(start || 0));
  const spanEnd = Math.max(spanStart, Number(end || 0));
  if (!source || spanEnd <= spanStart) return false;

  const windowText = source.slice(spanStart, Math.min(source.length, spanEnd + 8));
  if (SAFE_HIGH_SIGNAL_COMPOUND_PATTERN.test(windowText)) {
    return true;
  }

  const spanText = source.slice(spanStart, spanEnd);
  const context = String(contextText || "");
  const localContext = source.slice(
    Math.max(0, spanStart - 48),
    Math.min(source.length, spanEnd + 120)
  );
  return (
    SAFE_HIGH_SIGNAL_COMPOUND_FRAGMENT_PATTERN.test(spanText) &&
    (
      SAFE_HIGH_SIGNAL_COMPOUND_PATTERN.test(localContext) ||
      SAFE_HIGH_SIGNAL_CONTEXT_PATTERN.test(localContext) ||
      SAFE_HIGH_SIGNAL_COMPOUND_PATTERN.test(context) ||
      SAFE_HIGH_SIGNAL_CONTEXT_PATTERN.test(context)
    )
  );
}

function hasUnsafeHighSignalMatch(text, contextText = "") {
  const sourceText = String(text || "");
  if (!sourceText) return false;

  const regex = new RegExp(HIGH_SIGNAL_PROFANITY_SPAN_PATTERN.source, "gi");
  let match;
  while ((match = regex.exec(sourceText)) !== null) {
    const matchText = String(match[0] || "");
    if (!matchText) {
      regex.lastIndex += 1;
      continue;
    }

    if (!isSafeHighSignalCompoundSpan(sourceText, match.index, match.index + matchText.length, contextText)) {
      return true;
    }
  }

  return false;
}

function expandEvidenceSpanToHighSignalMatch(span, sourceText) {
  const source = String(sourceText || "");
  const start = Math.max(0, Number(span?.start ?? 0));
  const end = Math.min(source.length, Number(span?.end ?? 0));
  if (!source || end <= start) {
    return { ...span, start, end, text: source.slice(start, end) };
  }

  const regex = new RegExp(HIGH_SIGNAL_PROFANITY_SPAN_PATTERN.source, "gi");
  let match;
  while ((match = regex.exec(source)) !== null) {
    const matchText = match[0] || "";
    const matchStart = match.index;
    const matchEnd = matchStart + matchText.length;
    if (isSafeHighSignalCompoundSpan(source, matchStart, matchEnd)) {
      if (matchText.length === 0) {
        regex.lastIndex += 1;
      }
      continue;
    }
    if (matchEnd <= start || matchStart >= end) {
      if (matchText.length === 0) {
        regex.lastIndex += 1;
      }
      continue;
    }

    return {
      ...span,
      start: matchStart,
      end: matchEnd,
      text: source.slice(matchStart, matchEnd)
    };
  }

  return { ...span, start, end, text: source.slice(start, end) };
}

function normalizeEvidenceSpans(spans, originalText) {
  const sourceText = String(originalText || "");
  const nextSpans = (Array.isArray(spans) ? spans : [])
    .map((span) => {
      const start = Math.max(0, Number(span?.start ?? 0));
      const end = Math.min(sourceText.length, Number(span?.end ?? 0));
      return expandEvidenceSpanToHighSignalMatch({
        start,
        end,
        score: Number(span?.score ?? 0),
        text: sourceText.slice(start, end)
      }, sourceText);
    })
    .filter((span) => (
      Number.isFinite(span.start) &&
      Number.isFinite(span.end) &&
      span.end > span.start &&
      !isSafeHighSignalCompoundSpan(sourceText, span.start, span.end) &&
      isRenderableEvidenceSpan(span.text)
    ))
    .sort((left, right) => left.start - right.start || left.end - right.end);

  const merged = [];
  for (const span of nextSpans) {
    const previous = merged[merged.length - 1];
    if (previous && span.start <= previous.end) {
      previous.end = Math.max(previous.end, span.end);
      previous.score = Math.max(previous.score, span.score);
      previous.text = sourceText.slice(previous.start, previous.end);
      continue;
    }

    merged.push({
      ...span,
      text: sourceText.slice(span.start, span.end)
    });
  }

  return merged;
}

function findHighSignalProfanitySpans(text) {
  const sourceText = String(text || "");
  if (!sourceText) return [];

  const regex = new RegExp(HIGH_SIGNAL_PROFANITY_SPAN_PATTERN.source, "gi");
  const spans = [];
  let match;
  while ((match = regex.exec(sourceText)) !== null) {
    const matchText = String(match[0] || "");
    if (!matchText) {
      regex.lastIndex += 1;
      continue;
    }
    if (isSafeHighSignalCompoundSpan(sourceText, match.index, match.index + matchText.length)) {
      continue;
    }

    spans.push({
      start: match.index,
      end: match.index + matchText.length,
      score: 1,
      text: matchText
    });
  }

  return normalizeEvidenceSpans(spans, sourceText);
}

function normalizeCustomWordDisplaySpans(spans, originalText) {
  const sourceText = String(originalText || "");
  const nextSpans = (Array.isArray(spans) ? spans : [])
    .map((span) => {
      const start = Math.max(0, Number(span?.start ?? 0));
      const end = Math.min(sourceText.length, Number(span?.end ?? 0));
      return {
        start,
        end,
        score: Number(span?.score ?? 1),
        text: sourceText.slice(start, end),
        keyword: String(span?.keyword || span?.text || "").toLowerCase()
      };
    })
    .filter((span) => (
      Number.isFinite(span.start) &&
      Number.isFinite(span.end) &&
      span.end > span.start &&
      normalizeText(span.text)
    ))
    .sort((left, right) => left.start - right.start || left.end - right.end);

  const merged = [];
  for (const span of nextSpans) {
    const previous = merged[merged.length - 1];
    if (previous && span.start <= previous.end) {
      previous.end = Math.max(previous.end, span.end);
      previous.score = Math.max(previous.score, span.score);
      previous.text = sourceText.slice(previous.start, previous.end);
      previous.keyword = [previous.keyword, span.keyword].filter(Boolean).join(",");
      continue;
    }

    merged.push({ ...span });
  }

  return merged;
}

function collectCustomSpanKeywords(spans) {
  const keywords = new Set();
  for (const span of Array.isArray(spans) ? spans : []) {
    String(span?.keyword || "")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean)
      .forEach((item) => keywords.add(item));
  }
  return [...keywords];
}

function mergeDisplaySpans(spans, originalText) {
  const sourceText = String(originalText || "");
  const nextSpans = (Array.isArray(spans) ? spans : [])
    .map((span) => {
      const start = Math.max(0, Number(span?.start ?? 0));
      const end = Math.min(sourceText.length, Number(span?.end ?? 0));
      return {
        ...span,
        start,
        end,
        score: Number(span?.score ?? 0),
        text: sourceText.slice(start, end)
      };
    })
    .filter((span) => (
      Number.isFinite(span.start) &&
      Number.isFinite(span.end) &&
      span.end > span.start &&
      normalizeText(span.text)
    ))
    .sort((left, right) => left.start - right.start || left.end - right.end);

  const merged = [];
  for (const span of nextSpans) {
    const previous = merged[merged.length - 1];
    if (previous && span.start <= previous.end) {
      previous.end = Math.max(previous.end, span.end);
      previous.score = Math.max(previous.score, span.score);
      previous.text = sourceText.slice(previous.start, previous.end);
      continue;
    }

    merged.push({ ...span });
  }

  return merged;
}

function countRawEvidenceSpans(spans, originalText) {
  const sourceText = String(originalText || "");
  return (Array.isArray(spans) ? spans : []).filter((span) => {
    const start = Math.max(0, Number(span?.start ?? 0));
    const end = Math.min(sourceText.length, Number(span?.end ?? 0));
    return Number.isFinite(start) && Number.isFinite(end) && end > start;
  }).length;
}

function getForegroundBackendSource(meta) {
  const requestedCount = Number(meta?.requestedCount || 0);
  const contentCacheHitCount = Number(meta?.cacheHitCount || 0);
  const backendCacheHitCount = Number(meta?.backendCacheHitCount || 0);

  if (requestedCount > 0) {
    return "live-backend";
  }
  if (backendCacheHitCount > 0 && contentCacheHitCount > 0) {
    return "mixed-cache";
  }
  if (backendCacheHitCount > 0) {
    return "service-worker-cache";
  }
  if (contentCacheHitCount > 0) {
    return "content-cache";
  }
  return "fallback-none";
}

async function analyzePayloadWithRealtimeWorker(analysisUnits, settings, onProgress, options = {}) {
  const startedAt = performance.now();
  const suppressHotPathFailure = options.suppressHotPathFailure === true;

  try {
    const response = await analyzePayloadWithBackend(analysisUnits, onProgress, {
      ...options,
      settings
    });
    if (!response?.ok) {
      const failure = {
        reason: String(response?.error?.reason || "FOREGROUND_BACKEND_FAILED"),
        errorCode: String(response?.error?.errorCode || "FOREGROUND_BACKEND_FAILED"),
        retryable: Boolean(response?.error?.retryable)
      };

      if (!suppressHotPathFailure) {
        markRealtimeWorkerFailure(
          Object.assign(new Error(failure.reason), failure),
          {
            errorCode: failure.errorCode,
            phase: "foreground-backend",
            strategy: "backend-first"
          }
        );
      }

      return {
        ok: false,
        error: failure,
        apiBaseUrl: response?.apiBaseUrl || settings?.backendApiBaseUrl || "",
        backendStatus: response?.error?.backendStatus || "degraded",
        requestCount: Number(response?.requestCount || 0),
        splitRetryCount: Number(response?.splitRetryCount || 0),
        skippedChunkCount: Number(response?.skippedChunkCount || 0),
        failedTextCount: Number(response?.failedTextCount || 0),
        chunkSize: Number(response?.chunkSize || 0),
        requestTimeoutMs: Number(response?.requestTimeoutMs || 0),
        lastBackendErrorCode: String(response?.lastBackendErrorCode || ""),
        backendQueueWaitMs: Number(response?.backendQueueWaitMs || 0),
        backendQueueDepthAtEnqueue: Number(response?.backendQueueDepthAtEnqueue || 0),
        backendRequestTimings: Array.isArray(response?.backendRequestTimings)
          ? response.backendRequestTimings
          : []
      };
    }

    if (!suppressHotPathFailure) {
      setRealtimeWorkerStatus("ready", {
        failure: null,
        initLatencyMs: 0,
        strategy: "backend-first"
      });
    }

    return {
      ok: true,
      cacheHitCount: Number(response?.cacheHitCount || 0),
      backendCacheHitCount: Number(response?.backendCacheHitCount || 0),
      requestCount: Number(response?.requestCount || 0),
      splitRetryCount: Number(response?.splitRetryCount || 0),
      skippedChunkCount: Number(response?.skippedChunkCount || 0),
      failedTextCount: Number(response?.failedTextCount || 0),
      chunkSize: Number(response?.chunkSize || 0),
      requestTimeoutMs: Number(response?.requestTimeoutMs || 0),
      lastBackendErrorCode: String(response?.lastBackendErrorCode || ""),
      backendQueueWaitMs: Number(response?.backendQueueWaitMs || 0),
      backendQueueDepthAtEnqueue: Number(response?.backendQueueDepthAtEnqueue || 0),
      backendRequestTimings: Array.isArray(response?.backendRequestTimings)
        ? response.backendRequestTimings
        : [],
      durationMs: Math.max(
        Number(response?.backendDurationMs || 0),
        Math.round(performance.now() - startedAt)
      ),
      strategy: "backend-first",
      apiBaseUrl: response?.apiBaseUrl || settings?.backendApiBaseUrl || "",
      backendStatus: response?.backendStatus || "ready",
      requestedCount: Number(response?.requestedCount || 0),
      foregroundBackendSource: getForegroundBackendSource(response),
      results: Array.isArray(response?.results) ? response.results : []
    };
  } catch (error) {
    const invalidated = handleExtensionContextError(error);
    const failure = {
      reason: String(error?.message || error || "FOREGROUND_BACKEND_FAILED"),
      errorCode: invalidated
        ? "EXTENSION_CONTEXT_INVALIDATED"
        : String(error?.errorCode || "FOREGROUND_BACKEND_FAILED"),
      retryable: !invalidated
    };

    if (!invalidated && !suppressHotPathFailure) {
      markRealtimeWorkerFailure(
        Object.assign(new Error(failure.reason), failure),
        {
          errorCode: failure.errorCode,
          phase: error?.phase || "foreground-backend",
          strategy: "backend-first"
        }
      );
    }

    return {
      ok: false,
      error: failure,
      apiBaseUrl: settings?.backendApiBaseUrl || "",
      backendStatus: "degraded"
    };
  }
}

function scheduleHotPathStatsPersist(partialStats) {
  pendingHotPathStats = {
    ...(pendingHotPathStats || {}),
    ...(partialStats || {})
  };

  if (hotPathStatsPersistTimerId) {
    window.clearTimeout(hotPathStatsPersistTimerId);
  }

  hotPathStatsPersistTimerId = window.setTimeout(async () => {
    hotPathStatsPersistTimerId = null;
    const statsPatch = pendingHotPathStats;
    pendingHotPathStats = null;
    if (!statsPatch) return;

    try {
      const currentState = await safeStorageLocalGet(["lastStats"]);
      await safeStorageLocalSet({
        lastRunAt: Date.now(),
        lastPipelineError: null,
        lastStats: {
          ...(currentState.lastStats || {}),
          ...getRealtimeWorkerDiagnostics(),
          ...getPerformanceGuardDiagnostics(),
          ...statsPatch
        }
      });
    } catch (error) {
      console.error("[청마루] hot path stats persist failed", error);
    }
  }, 140);
}

function isRetryableBackendErrorCode(errorCode) {
  return (
    errorCode === "TIMEOUT" ||
    errorCode === "NETWORK_UNREACHABLE" ||
    errorCode === "ABORTED" ||
    errorCode === "QUEUE_DROPPED" ||
    errorCode === "PREEMPTED_BY_FOREGROUND" ||
    errorCode === "HTTP_503" ||
    errorCode === "HTTP_504" ||
    errorCode === "ANALYZE_TEXT_BATCH_FAILED"
  );
}

function shouldSkipTransientAnalyzeFailure(response, analysisMode) {
  const mode = String(analysisMode || "");
  if (mode !== "foreground" && mode !== "reconcile" && mode !== "background-validation") {
    return false;
  }

  if (!response) {
    return true;
  }

  const errorCode = String(response?.errorCode || response?.error?.errorCode || "");
  return Boolean(response?.retryable) || isRetryableBackendErrorCode(errorCode);
}

function createSkippedAnalysisResult(text) {
  return {
    __shieldtextSkipped: true,
    original: String(text || ""),
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
  };
}

function createTransientAnalyzeFailureResponse(error, requestBatch, analysisMode) {
  const rawErrorCode = String(
    error?.errorCode ||
      error?.code ||
      error?.reason ||
      error?.message ||
      "ANALYZE_TEXT_BATCH_FAILED"
  );
  const errorCode = [
    "ANALYZE_TEXT_BATCH_FAILED",
    "TIMEOUT",
    "NETWORK_UNREACHABLE",
    "ABORTED",
    "QUEUE_DROPPED",
    "PREEMPTED_BY_FOREGROUND",
    "HTTP_503",
    "HTTP_504"
  ].find((code) => rawErrorCode.includes(code)) || rawErrorCode;
  if (!isRetryableBackendErrorCode(errorCode)) {
    return null;
  }

  return {
    ok: false,
    reason: String(error?.reason || error?.message || errorCode),
    errorCode,
    retryable: true,
    backendStatus: "degraded",
    requestCount: 1,
    skippedChunkCount: 1,
    failedTextCount: Array.isArray(requestBatch) ? requestBatch.length : 0,
    chunkSize: Array.isArray(requestBatch) ? requestBatch.length : 0,
    requestTimings: [
      {
        mode: String(analysisMode || ""),
        textCount: Array.isArray(requestBatch) ? requestBatch.length : 0,
        errorCode
      }
    ]
  };
}

function summarizeBackendRequestTimings(requestTimings) {
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

async function analyzePayloadWithBackend(items, onProgress, options = {}) {
  const startedAt = performance.now();
  const activeSettings = getMergedSettings(options.settings || cachedSettings || {});
  if (activeSettings.backendEnabled !== true) {
    return {
      ok: true,
      results: items.map((item) => createSkippedAnalysisResult(item?.text || "")),
      apiBaseUrl: "",
      backendDurationMs: 0,
      backendStatus: "disabled",
      requestedCount: 0,
      requestCount: 0,
      splitRetryCount: 0,
      skippedChunkCount: items.length > 0 ? 1 : 0,
      failedTextCount: 0,
      chunkSize: 0,
      requestTimeoutMs: 0,
      lastBackendErrorCode: "BACKEND_DISABLED",
      backendQueueWaitMs: 0,
      backendQueueDepthAtEnqueue: 0,
      backendRequestTimings: [],
      cacheHitCount: 0,
      backendCacheHitCount: 0
    };
  }

  const cacheSensitivity = normalizeSensitivity(
    options.sensitivity ?? activeSettings.sensitivity ?? DEFAULT_SETTINGS.sensitivity
  );
  const resultsByText = new Map();
  const pendingRequests = [];
  const pendingRequestKeys = new Set();
  const itemsByText = new Map();
  let cacheHitCount = 0;
  let backendCacheHitCount = 0;
  const requestTimeoutMs = Math.max(150, Number(options.requestTimeoutMs || 0) || 0);

  for (const item of items) {
    const cacheEntry = {
      ...item,
      cacheSensitivity
    };
    const key = getAnalysisCacheKey(cacheEntry);
    if (!itemsByText.has(key)) {
      itemsByText.set(key, []);
    }
    itemsByText.get(key).push(cacheEntry);

    const cached = getCachedAnalysis(cacheEntry);

    if (shouldReuseCachedAnalysis(cacheEntry, cached)) {
      resultsByText.set(key, cached);
      cacheHitCount += 1;
      continue;
    }

    if (!pendingRequestKeys.has(key)) {
      pendingRequestKeys.add(key);
      pendingRequests.push({
        key,
        entry: cacheEntry,
        text: item.text
      });
    }
  }

  let backendDurationMs = 0;
  let apiBaseUrl = "";
  let backendStatus = "ready";
  let serviceWorkerRequestCount = 0;
  let serviceWorkerSplitRetryCount = 0;
  let serviceWorkerSkippedChunkCount = 0;
  let serviceWorkerFailedTextCount = 0;
  let serviceWorkerChunkSize = 0;
  let serviceWorkerLastBackendErrorCode = "";
  let serviceWorkerRequestTimeoutMs = 0;
  let serviceWorkerBackendQueueWaitMs = 0;
  let serviceWorkerBackendQueueDepth = 0;
  const backendRequestTimings = [];
  const requestBatchSize = Math.max(
    1,
    getBackendRequestBatchSize(String(options.analysisMode || ""))
  );
  const requestBatches = pendingRequests.length > 0
    ? chunkArray(pendingRequests, requestBatchSize)
    : [];

  async function emitProgress(resolvedCandidates) {
    if (typeof onProgress !== "function" || resolvedCandidates.length === 0) {
      return;
    }

    await onProgress({
      items: resolvedCandidates,
      results: resolvedCandidates.map(
        (item) => resultsByText.get(getAnalysisCacheKey(item)) || null
      ),
      apiBaseUrl,
      backendDurationMs,
      backendStatus
    });
  }

  if (resultsByText.size > 0) {
    const cachedCandidates = items.filter((item) =>
      resultsByText.has(getAnalysisCacheKey(item))
    );
    const cachedOffensiveCandidates = cachedCandidates.filter((item) => {
      const cachedResult = resultsByText.get(getAnalysisCacheKey(item));
      return Boolean(cachedResult?.is_offensive);
    });
    await emitProgress(cachedOffensiveCandidates);
  }

  if (pendingRequests.length > 0) {
    const analysisMode = String(options.analysisMode || "");

    for (let requestBatchIndex = 0; requestBatchIndex < requestBatches.length; requestBatchIndex += 1) {
      const requestBatch = requestBatches[requestBatchIndex];
      let response = null;
      try {
        response = await safeRuntimeSendMessage({
          type: "ANALYZE_TEXT_BATCH",
          texts: requestBatch.map((request) => request.text),
          requestTimeoutMsOverride: requestTimeoutMs || undefined,
          sensitivity: cacheSensitivity,
          analysisMode
        });
      } catch (error) {
        response = createTransientAnalyzeFailureResponse(error, requestBatch, analysisMode);
        if (!response) {
          throw error;
        }
      }

      if (!response?.ok) {
        if (shouldSkipTransientAnalyzeFailure(response, analysisMode)) {
          const skippedResults = requestBatch.map((request) =>
            createSkippedAnalysisResult(request?.text || "")
          );
          const resolvedCandidates = [];

          skippedResults.forEach((result, index) => {
            const request = requestBatch[index];
            if (!request) {
              return;
            }

            resultsByText.set(request.key, result);
            for (const item of itemsByText.get(request.key) || []) {
              resolvedCandidates.push(item);
            }
          });

          serviceWorkerRequestCount += Math.max(1, Number(response?.requestCount || 0));
          serviceWorkerSkippedChunkCount += Math.max(1, Number(response?.skippedChunkCount || 0));
          serviceWorkerFailedTextCount += Number(response?.failedTextCount || requestBatch.length);
          serviceWorkerChunkSize = Number(response?.chunkSize || serviceWorkerChunkSize || requestBatchSize);
          serviceWorkerLastBackendErrorCode =
            String(response?.lastBackendErrorCode || response?.errorCode || serviceWorkerLastBackendErrorCode || "");
          serviceWorkerRequestTimeoutMs = Number(
            response?.requestTimeoutMs || serviceWorkerRequestTimeoutMs || requestTimeoutMs || 0
          );
          if (Array.isArray(response?.requestTimings)) {
            backendRequestTimings.push(...response.requestTimings);
            while (backendRequestTimings.length > 12) {
              backendRequestTimings.shift();
            }
          }
          serviceWorkerBackendQueueWaitMs = Math.max(
            serviceWorkerBackendQueueWaitMs,
            Number(response?.backendQueueWaitMs || summarizeBackendRequestTimings(response?.requestTimings).maxQueueWaitMs)
          );
          serviceWorkerBackendQueueDepth = Math.max(
            serviceWorkerBackendQueueDepth,
            Number(
              response?.backendQueueDepthAtEnqueue ||
                summarizeBackendRequestTimings(response?.requestTimings).maxQueueDepthAtEnqueue
            )
          );
          backendStatus = response?.backendStatus || "degraded";
          apiBaseUrl = response?.apiBaseUrl || apiBaseUrl;

          await emitProgress(resolvedCandidates);
          continue;
        }

        return {
          ok: false,
          error: {
            reason: response?.reason || "ANALYZE_TEXT_BATCH_FAILED",
            errorCode: response?.errorCode || "ANALYZE_TEXT_BATCH_FAILED",
            retryable: Boolean(response?.retryable),
            backendStatus: response?.backendStatus || "degraded"
          },
          apiBaseUrl: response?.apiBaseUrl || apiBaseUrl,
          backendDurationMs,
          requestCount: serviceWorkerRequestCount + Number(response?.requestCount || 0),
          splitRetryCount: serviceWorkerSplitRetryCount + Number(response?.splitRetryCount || 0),
          skippedChunkCount: serviceWorkerSkippedChunkCount + Number(response?.skippedChunkCount || 0),
          failedTextCount: serviceWorkerFailedTextCount + Number(response?.failedTextCount || 0),
          chunkSize: serviceWorkerChunkSize || Number(response?.chunkSize || requestBatchSize),
          lastBackendErrorCode:
            serviceWorkerLastBackendErrorCode || String(response?.lastBackendErrorCode || response?.errorCode || ""),
          requestTimeoutMs: serviceWorkerRequestTimeoutMs || Number(response?.requestTimeoutMs || requestTimeoutMs || 0),
          backendQueueWaitMs: Math.max(
            serviceWorkerBackendQueueWaitMs,
            Number(response?.backendQueueWaitMs || summarizeBackendRequestTimings(response?.requestTimings).maxQueueWaitMs)
          ),
          backendQueueDepthAtEnqueue: Math.max(
            serviceWorkerBackendQueueDepth,
            Number(
              response?.backendQueueDepthAtEnqueue ||
                summarizeBackendRequestTimings(response?.requestTimings).maxQueueDepthAtEnqueue
            )
          ),
          backendRequestTimings: [
            ...backendRequestTimings,
            ...(Array.isArray(response?.requestTimings) ? response.requestTimings : [])
          ].slice(-12)
        };
      }

      apiBaseUrl = response.apiBaseUrl || apiBaseUrl;
      backendDurationMs += Number(response.durationMs || 0);
      backendStatus = response.backendStatus || backendStatus;
      backendCacheHitCount += Number(response.cacheHitCount || 0);
      serviceWorkerRequestCount += Number(response.requestCount || 0);
      serviceWorkerSplitRetryCount += Number(response.splitRetryCount || 0);
      serviceWorkerSkippedChunkCount += Number(response.skippedChunkCount || 0);
      serviceWorkerFailedTextCount += Number(response.failedTextCount || 0);
      serviceWorkerChunkSize = Number(response.chunkSize || serviceWorkerChunkSize || requestBatchSize);
      serviceWorkerLastBackendErrorCode =
        String(response.lastBackendErrorCode || serviceWorkerLastBackendErrorCode || "");
      serviceWorkerRequestTimeoutMs = Number(
        response.requestTimeoutMs || serviceWorkerRequestTimeoutMs || requestTimeoutMs || 0
      );
      if (Array.isArray(response.requestTimings)) {
        backendRequestTimings.push(...response.requestTimings);
        while (backendRequestTimings.length > 12) {
          backendRequestTimings.shift();
        }
      }
      serviceWorkerBackendQueueWaitMs = Math.max(
        serviceWorkerBackendQueueWaitMs,
        Number(response.backendQueueWaitMs || summarizeBackendRequestTimings(response.requestTimings).maxQueueWaitMs)
      );
      serviceWorkerBackendQueueDepth = Math.max(
        serviceWorkerBackendQueueDepth,
        Number(
          response.backendQueueDepthAtEnqueue ||
            summarizeBackendRequestTimings(response.requestTimings).maxQueueDepthAtEnqueue
        )
      );
      const resolvedCandidates = [];

      response.results.forEach((result, index) => {
        const request = requestBatch[index];
        if (!request) {
          return;
        }

        const skippedResult = result?.__shieldtextSkipped === true;
        const cacheableResult =
          skippedResult
            ? null
            : result && typeof result === "object"
            ? {
                ...result,
                text: String(result.text || result.original || request.text || "")
              }
            : result || null;
        resultsByText.set(request.key, cacheableResult || null);
        setCachedAnalysis(request.entry, cacheableResult || null);

        for (const item of itemsByText.get(request.key) || []) {
          resolvedCandidates.push(item);
        }
      });

      await emitProgress(resolvedCandidates);

      if (
        analysisMode === "background-validation" &&
        requestBatchIndex < requestBatches.length - 1 &&
        Math.round(performance.now() - startedAt) >= BACKGROUND_VALIDATION_BACKEND_BUDGET_MS
      ) {
        const skippedCandidates = [];
        for (const remainingBatch of requestBatches.slice(requestBatchIndex + 1)) {
          serviceWorkerSkippedChunkCount += 1;
          serviceWorkerFailedTextCount += remainingBatch.length;
          for (const request of remainingBatch) {
            const skippedResult = createSkippedAnalysisResult(request?.text || "");
            resultsByText.set(request.key, skippedResult);
            for (const item of itemsByText.get(request.key) || []) {
              skippedCandidates.push(item);
            }
          }
        }
        await emitProgress(skippedCandidates);
        break;
      }

      if (
        analysisMode === "foreground" &&
        Number(response.skippedChunkCount || 0) > 0 &&
        isRetryableBackendErrorCode(String(response.lastBackendErrorCode || ""))
      ) {
        const skippedCandidates = [];
        for (const remainingBatch of requestBatches.slice(requestBatchIndex + 1)) {
          serviceWorkerSkippedChunkCount += 1;
          serviceWorkerFailedTextCount += remainingBatch.length;
          for (const request of remainingBatch) {
            const skippedResult = createSkippedAnalysisResult(request?.text || "");
            resultsByText.set(request.key, skippedResult);
            for (const item of itemsByText.get(request.key) || []) {
              skippedCandidates.push(item);
            }
          }
        }
        await emitProgress(skippedCandidates);
        break;
      }
    }
  }

  const orderedResults = items.map((item) => resultsByText.get(getAnalysisCacheKey(item)) || null);

  return {
    ok: true,
    results: orderedResults,
    apiBaseUrl,
    backendDurationMs,
    backendStatus,
    requestedCount: pendingRequests.length,
    requestCount: serviceWorkerRequestCount || requestBatches.length,
    splitRetryCount: serviceWorkerSplitRetryCount,
    skippedChunkCount: serviceWorkerSkippedChunkCount,
    failedTextCount: serviceWorkerFailedTextCount,
    chunkSize: serviceWorkerChunkSize || requestBatchSize,
    requestTimeoutMs: serviceWorkerRequestTimeoutMs || requestTimeoutMs || 0,
    lastBackendErrorCode: serviceWorkerLastBackendErrorCode,
    backendQueueWaitMs: serviceWorkerBackendQueueWaitMs,
    backendQueueDepthAtEnqueue: serviceWorkerBackendQueueDepth,
    backendRequestTimings: backendRequestTimings.slice(-12),
    cacheHitCount,
    backendCacheHitCount
  };
}

function buildLocalSpansFromAnalysis(unitText, member, analysis) {
  const analysisSpans = normalizeEvidenceSpans(
    Array.isArray(analysis?.evidence_spans) ? analysis.evidence_spans : [],
    unitText
  );

  const localSpans = [];
  for (const span of analysisSpans) {
    if (span.end <= member.start || span.start >= member.end) {
      continue;
    }

    const localStart = Math.max(0, span.start - member.start);
    const localEnd = Math.min(member.end - member.start, span.end - member.start);
    if (localEnd <= localStart) {
      continue;
    }

    localSpans.push({
      start: localStart,
      end: localEnd,
      score: span.score,
      text: member.candidate.text.slice(localStart, localEnd)
    });
  }

  return localSpans;
}

function getMaxOutcomeScore(scores) {
  return Math.max(
    Number(scores?.profanity || 0),
    Number(scores?.toxicity || 0),
    Number(scores?.hate || 0)
  );
}

function filterSpansForSensitivity(spans, scores, settings) {
  const normalizedSpans = Array.isArray(spans) ? spans : [];
  if (normalizedSpans.length === 0) {
    return [];
  }

  if (isFilteringSuppressedBySensitivity(settings)) {
    return [];
  }

  const threshold = getSensitivityScoreThreshold(settings);
  const maxScore = getMaxOutcomeScore(scores);
  if (maxScore >= threshold) {
    return normalizedSpans;
  }

  return normalizedSpans.filter((span) =>
    HIGH_SIGNAL_PROFANITY_PATTERN.test(span?.text || "") &&
    Math.max(Number(span?.score || 0), maxScore) >= Math.max(0.55, threshold - 0.18)
  );
}

function buildNodeOutcome(candidate, analysis, settings, evidenceSpans) {
  const sourceText = String(candidate?.text || "");
  const allowWordSpans = findCustomWordSpans(sourceText, getCustomWordList(settings?.customAllowWords));
  if (allowWordSpans.length > 0) {
    return {
      blocked: false,
      categories: [],
      reasons: [],
      scores: {
        profanity: Number(analysis?.scores?.profanity || 0),
        toxicity: Number(analysis?.scores?.toxicity || 0),
        hate: Number(analysis?.scores?.hate || 0)
      },
      spans: [],
      matchedKeywords: collectCustomSpanKeywords(allowWordSpans)
    };
  }

  const scores = {
    profanity: Number(analysis?.scores?.profanity || 0),
    toxicity: Number(analysis?.scores?.toxicity || 0),
    hate: Number(analysis?.scores?.hate || 0)
  };
  const normalizedLocalSpans = normalizeEvidenceSpans(
    Array.isArray(evidenceSpans) ? evidenceSpans : [],
    sourceText
  );
  const customBlockSpans = isFilteringSuppressedBySensitivity(settings)
    ? []
    : normalizeCustomWordDisplaySpans(
      findCustomWordSpans(sourceText, getCustomWordList(settings?.customBlockWords)),
      sourceText
    );
  const displaySpans = mergeDisplaySpans(
    [
      ...filterSpansForSensitivity(normalizedLocalSpans, scores, settings),
      ...customBlockSpans
    ],
    sourceText
  );
  const flaggedProfanity = Boolean(analysis?.is_profane);
  const flaggedToxicity = Boolean(analysis?.is_toxic);
  const flaggedHate = Boolean(analysis?.is_hate);
  const flaggedOffensive =
    Boolean(analysis?.is_offensive) &&
    (displaySpans.length > 0 || getMaxOutcomeScore(scores) >= getSensitivityScoreThreshold(settings));
  const categories = [];
  const reasons = [];

  if (customBlockSpans.length > 0) {
    categories.push("custom");
    reasons.push("사용자 차단 단어");
  }

  if (settings.categories?.insult && flaggedProfanity) {
    categories.push("insult");
    reasons.push(`모욕 ${Math.round(scores.profanity * 100)}%`);
  }

  if (settings.categories?.abuse && flaggedToxicity) {
    categories.push("abuse");
    reasons.push(`공격 ${Math.round(scores.toxicity * 100)}%`);
  }

  if (settings.categories?.hate && flaggedHate) {
    categories.push("hate");
    reasons.push(`혐오 ${Math.round(scores.hate * 100)}%`);
  }

  if (categories.length === 0 && flaggedOffensive && displaySpans.length > 0) {
    if (settings.categories?.insult) {
      categories.push("insult");
      reasons.push("유해 표현");
    } else if (settings.categories?.abuse) {
      categories.push("abuse");
      reasons.push("공격적 표현");
    } else if (settings.categories?.hate) {
      categories.push("hate");
      reasons.push("혐오 표현");
    }
  }

  const uniqueCategories = [...new Set(categories)];
  if (uniqueCategories.length === 0) {
    return {
      blocked: false,
      categories: [],
      reasons: [],
      scores,
      spans: [],
      matchedKeywords: []
    };
  }

  if (displaySpans.length === 0) {
    return {
      blocked: false,
      categories: [],
      reasons: [],
      scores,
      spans: [],
      matchedKeywords: []
    };
  }

  return {
    blocked: true,
    categories: uniqueCategories,
    reasons: [...new Set(reasons)],
    scores,
    spans: displaySpans,
    matchedKeywords: collectCustomSpanKeywords(customBlockSpans)
  };
}

function buildDecisionFromBackend(analysisUnits, analysisResults, settings, backendMeta) {
  const blockedNodeIdSet = new Set();
  const matchedKeywordSet = new Set();
  const categoryHits = emptyCategoryHits();
  const nodeCategoryMap = {};
  const nodeReasonMap = {};
  const nodeScoreMap = {};
  const nodeEvidenceMap = {};
  const nodePendingMap = {};
  const nodeOutcomeMap = {};
  let maskedSpanCount = 0;
  let returnedSpanCount = 0;

  analysisUnits.forEach((unit, index) => {
    const analysis = Array.isArray(analysisResults) ? analysisResults[index] : null;
    if (!analysis || analysis.__shieldtextSkipped === true) {
      return;
    }

    returnedSpanCount += countRawEvidenceSpans(
      Array.isArray(analysis?.evidence_spans) ? analysis.evidence_spans : [],
      unit?.text || ""
    );

    for (const member of unit.members || []) {
      const candidate = member.candidate;
      const localSpans = buildLocalSpansFromAnalysis(unit.text, member, analysis);
      const outcome = buildNodeOutcome(candidate, analysis, settings, localSpans);
      nodeOutcomeMap[candidate.nodeId] = outcome;

      if (!outcome.blocked) {
        continue;
      }

      blockedNodeIdSet.add(candidate.nodeId);
      nodeCategoryMap[candidate.nodeId] = outcome.categories;
      nodeReasonMap[candidate.nodeId] = outcome.reasons;
      nodeScoreMap[candidate.nodeId] = outcome.scores;
      nodeEvidenceMap[candidate.nodeId] = outcome.spans;
      nodePendingMap[candidate.nodeId] = false;
      maskedSpanCount += outcome.spans.length;

      outcome.categories.forEach((category) => {
        categoryHits[category] = Number(categoryHits[category] || 0) + 1;
      });
      outcome.matchedKeywords.forEach((keyword) => {
        matchedKeywordSet.add(keyword);
      });
    }
  });

  return {
    blockedNodeIds: [...blockedNodeIdSet],
    matchedKeywords: [...matchedKeywordSet],
    categoryHits,
    nodeCategoryMap,
    nodeReasonMap,
    nodeScoreMap,
    nodeEvidenceMap,
    nodePendingMap,
    nodeOutcomeMap,
    analyzedNodeCount: Object.keys(nodeOutcomeMap).length,
    blockedNodeCount: blockedNodeIdSet.size,
    backendEndpoint: backendMeta.apiBaseUrl,
    backendDurationMs: backendMeta.backendDurationMs,
    backendStatus: backendMeta.backendStatus || "ready",
    maskedSpanCount,
    returnedSpanCount,
    droppedSpanCount: Math.max(0, returnedSpanCount - maskedSpanCount),
    apiMode: "backend-first"
  };
}

function unitHasCustomBlockWord(unit, settings) {
  const blockWords = getCustomWordList(settings?.customBlockWords);
  if (blockWords.length === 0) {
    return false;
  }

  return (Array.isArray(unit?.members) ? unit.members : []).some((member) =>
    findCustomWordSpans(member?.candidate?.text || "", blockWords).length > 0
  );
}

function buildLocalPreflightAnalysisResult(unit, settings) {
  const unitText = String(unit?.text || "");
  if (!unitText) return null;

  const highSignalSpans = findHighSignalProfanitySpans(unitText);
  const hasCustomBlockWord = unitHasCustomBlockWord(unit, settings);
  if (highSignalSpans.length === 0 && !hasCustomBlockWord) {
    return null;
  }

  return {
    text: unitText,
    original: unitText,
    is_offensive: true,
    is_profane: highSignalSpans.length > 0,
    is_toxic: false,
    is_hate: false,
    scores: {
      profanity: highSignalSpans.length > 0 ? 0.99 : 0,
      toxicity: highSignalSpans.length > 0 ? 0.7 : 0,
      hate: 0
    },
    evidence_spans: highSignalSpans
  };
}

function buildLocalPreflightDecision(analysisUnits, settings) {
  const units = Array.isArray(analysisUnits) ? analysisUnits : [];
  const localResults = units.map((unit) => buildLocalPreflightAnalysisResult(unit, settings));
  if (!localResults.some(Boolean)) {
    return null;
  }

  const decision = buildDecisionFromBackend(units, localResults, settings, {
    apiBaseUrl: "local-preflight",
    backendDurationMs: 0,
    backendStatus: "local-preflight"
  });
  return {
    ...decision,
    apiMode: "local-preflight"
  };
}

function applyLocalPreflightDecision(unitCandidates, analysisUnits, settings, options = {}) {
  const decision = buildLocalPreflightDecision(analysisUnits, settings);
  if (!decision || Number(decision.maskedSpanCount || 0) <= 0) {
    return {
      decision,
      firstMaskLatencyMs: 0
    };
  }

  suppressMutationFeedback(120);
  applyDecision(unitCandidates, decision, settings, {
    generation: options.generation,
    stage: "local-preflight",
    settingsRevision: options.settingsRevision
  });
  if (Number(options.pipelineSequence || 0) > 0) {
    recordWellbeingDetectionFromDecision(
      decision,
      "local-preflight",
      options.pipelineSequence
    );
  }

  return {
    decision,
    firstMaskLatencyMs: Math.round(performance.now() - Number(options.startedAt || performance.now()))
  };
}

function shouldRunLocalPreflight(settings) {
  return Boolean(
    settings &&
    settings.enabled !== false &&
    !isFilteringSuppressedBySensitivity(settings) &&
    !extensionContextInvalidated &&
    !isUnsupportedPage()
  );
}

function applyCachedLocalPreflightForCandidates(candidates, options = {}) {
  const settings = cachedSettings;
  if (!shouldRunLocalPreflight(settings)) {
    return {
      decision: null,
      firstMaskLatencyMs: 0,
      preconcealCount: 0
    };
  }

  const nextCandidates = (Array.isArray(candidates) ? candidates : []).filter(Boolean);
  if (nextCandidates.length === 0) {
    return {
      decision: null,
      firstMaskLatencyMs: 0,
      preconcealCount: 0
    };
  }

  const analysisUnits = buildForegroundAnalysisUnits(nextCandidates);
  const unitCandidates = collectUnitCandidates(analysisUnits);
  if (analysisUnits.length === 0 || unitCandidates.length === 0) {
    return {
      decision: null,
      firstMaskLatencyMs: 0,
      preconcealCount: 0
    };
  }

  const preconcealCount = preConcealGoogleAnalysisUnits(analysisUnits, {
    limit: options.preconcealLimit
  });
  const result = applyLocalPreflightDecision(unitCandidates, analysisUnits, settings, {
    generation: options.generation,
    settingsRevision: settingsRevision,
    startedAt: options.startedAt
  });
  return {
    ...result,
    preconcealCount
  };
}

function collectVisibleLocalPreflightCandidates(limit = MAX_DOMAIN_PRIORITY_CANDIDATES) {
  const maxCandidates = Math.max(1, Number(limit || MAX_DOMAIN_PRIORITY_CANDIDATES));
  const candidates = [];
  const seenNodeIds = new Set();

  const addCandidate = (candidate) => {
    if (!candidate?.nodeId || seenNodeIds.has(candidate.nodeId)) {
      return;
    }
    seenNodeIds.add(candidate.nodeId);
    candidates.push(candidate);
  };

  if (isGoogleSearchPage()) {
    if (isGoogleImageSearchPage()) {
      return candidates;
    }

    for (const candidate of collectEditableValueCandidates(INITIAL_EDITABLE_PASS_LIMIT)) {
      addCandidate(candidate);
      if (candidates.length >= maxCandidates) return candidates;
    }

    for (const candidate of collectGoogleDirectHighSignalTextCandidates(maxCandidates * 2)) {
      addCandidate(candidate);
      if (candidates.length >= maxCandidates) return candidates;
    }

    for (const candidate of collectGoogleSearchPriorityCandidates(maxCandidates, {
      includeDeepVisibleScan: false
    })) {
      addCandidate(candidate);
      if (candidates.length >= maxCandidates) return candidates;
    }

    for (const candidate of collectGoogleVisibleHighSignalTextCandidates(maxCandidates)) {
      addCandidate(candidate);
      if (candidates.length >= maxCandidates) return candidates;
    }

    for (const candidate of collectGoogleSearchLightCandidates()) {
      addCandidate(candidate);
      if (candidates.length >= maxCandidates) return candidates;
    }
    return candidates;
  }

  if (isYouTubePage()) {
    for (const candidate of collectYouTubeDirectHighSignalTextCandidates(maxCandidates)) {
      addCandidate(candidate);
      if (candidates.length >= maxCandidates) return candidates;
    }
  }

  return candidates;
}

function applyCachedLocalPreflightForVisiblePage(options = {}) {
  const startedAt = Number(options.startedAt || performance.now());
  return applyCachedLocalPreflightForCandidates(
    collectVisibleLocalPreflightCandidates(options.limit),
    { startedAt }
  );
}

function getDecisionWellbeingCounters(decision) {
  const counters = {
    blockedNodeCount: 0,
    maskedSpanCount: 0,
    profanityNodeCount: 0,
    toxicNodeCount: 0,
    hateNodeCount: 0
  };

  const nodeCategoryMap = decision?.nodeCategoryMap || {};
  const nodeScoreMap = decision?.nodeScoreMap || {};
  const nodeEvidenceMap = decision?.nodeEvidenceMap || {};

  for (const [nodeId, categories] of Object.entries(nodeCategoryMap)) {
    const categorySet = new Set(Array.isArray(categories) ? categories : []);
    const scores = nodeScoreMap[nodeId] || {};
    const spans = Array.isArray(nodeEvidenceMap[nodeId]) ? nodeEvidenceMap[nodeId] : [];
    const spanCount = Math.max(1, spans.length);
    const highSignalProfanitySpanCount = spans.filter((span) =>
      HIGH_SIGNAL_PROFANITY_PATTERN.test(String(span?.text || ""))
    ).length;
    const hasExplicitProfanityScore =
      categorySet.has("insult") &&
      Number(scores.profanity || 0) >= WELLBEING_EXPLICIT_SCORE_THRESHOLD;
    const profanityCount = highSignalProfanitySpanCount > 0
      ? highSignalProfanitySpanCount
      : hasExplicitProfanityScore
        ? spanCount
        : 0;
    const isExplicitToxicity =
      categorySet.has("abuse") &&
      Number(scores.toxicity || 0) >= WELLBEING_EXPLICIT_SCORE_THRESHOLD;
    const isExplicitHate =
      categorySet.has("hate") &&
      Number(scores.hate || 0) >= WELLBEING_EXPLICIT_SCORE_THRESHOLD;
    const isCustomBlocked = categorySet.has("custom");
    const shouldCountForWidget =
      profanityCount > 0 || isExplicitToxicity || isExplicitHate || isCustomBlocked;

    if (!shouldCountForWidget) {
      continue;
    }

    counters.blockedNodeCount += 1;
    counters.maskedSpanCount += spanCount;
    counters.profanityNodeCount += profanityCount;
    if (isExplicitToxicity) counters.toxicNodeCount += spanCount;
    if (isExplicitHate) counters.hateNodeCount += spanCount;
  }

  return counters;
}

function summarizeDecisionForWellbeing(decision, pipelineSequence) {
  const nodeScores = Object.values(decision?.nodeScoreMap || {});
  const maxScores = nodeScores.reduce(
    (current, scores) => ({
      profanity: Math.max(current.profanity, Number(scores?.profanity || 0)),
      toxicity: Math.max(current.toxicity, Number(scores?.toxicity || 0)),
      hate: Math.max(current.hate, Number(scores?.hate || 0))
    }),
    { profanity: 0, toxicity: 0, hate: 0 }
  );
  const categoryHits = {
    ...emptyCategoryHits(),
    ...(decision?.categoryHits || {})
  };
  const wellbeingCounters = getDecisionWellbeingCounters(decision);

  return {
    pipelineSequence: Number(pipelineSequence || 0),
    blockedNodeCount: Number(wellbeingCounters.blockedNodeCount || 0),
    maskedSpanCount: Number(wellbeingCounters.maskedSpanCount || 0),
    profanityNodeCount: Number(wellbeingCounters.profanityNodeCount || 0),
    toxicNodeCount: Number(wellbeingCounters.toxicNodeCount || 0),
    hateNodeCount: Number(wellbeingCounters.hateNodeCount || 0),
    categoryHits,
    maxScores
  };
}

function recordWellbeingDetectionFromDecision(decision, source, pipelineSequence) {
  const summary = summarizeDecisionForWellbeing(decision, pipelineSequence);
  if (
    !decision ||
    Number(summary.maskedSpanCount || 0) <= 0
  ) {
    return;
  }

  safeRuntimeSendMessage({
    type: "RECORD_WELLBEING_DETECTION",
    url: location.href,
    title: document.title || "",
    source,
    summary
  }).catch((error) => {
    handleExtensionContextError(error);
  });
}

function buildMaskTooltip(categories, reasons, settings) {
  const firstCategory = Array.isArray(categories) && categories[0] ? categories[0] : "abuse";
  const label = CATEGORY_LABELS[firstCategory] || CATEGORY_LABELS.abuse;
  if (settings?.showReason === false) {
    return `${label} 콘텐츠`;
  }

  if (Array.isArray(reasons) && reasons.length > 0) {
    return reasons.join(", ");
  }

  return `${label} 콘텐츠`;
}

function buildMaskAccessibilityLabel(tooltip) {
  const reason = String(tooltip || "").trim();
  return reason ? `청마루 보호: ${reason}` : "청마루 보호: 마스킹됨";
}

function buildVisibleMaskReplacement(text) {
  const graphemeCount = Array.from(String(text || "")).length;
  return "*".repeat(Math.max(2, Math.min(12, graphemeCount || 2)));
}

function buildAttributeReplacementText(sourceText, spans, settings) {
  const interventionMode = normalizeInterventionMode(settings?.interventionMode);
  let cursor = 0;
  let nextValue = "";
  for (const span of spans) {
    if (span.start > cursor) {
      nextValue += sourceText.slice(cursor, span.start);
    }

    const spanText = sourceText.slice(span.start, span.end);
    if (interventionMode === "remove") {
      cursor = span.end;
      continue;
    }
    if (interventionMode === "hide") {
      nextValue += "[숨김]";
    } else {
      nextValue += buildVisibleMaskReplacement(spanText);
    }
    cursor = span.end;
  }

  if (cursor < sourceText.length) {
    nextValue += sourceText.slice(cursor);
  }

  return normalizeText(nextValue);
}

function ensureWrapper(state) {
  if (state.wrapper?.isConnected && state.textNode?.parentNode === state.wrapper) {
    return state.wrapper;
  }

  if (!(state.textNode instanceof Text) || !state.textNode.parentNode) {
    return null;
  }

  if (state.textNode.parentElement?.dataset?.shieldtextWrapper === "true") {
    state.wrapper = state.textNode.parentElement;
    return state.wrapper;
  }

  const wrapper = document.createElement("span");
  wrapper.className = "shieldtext-inline-wrapper";
  wrapper.dataset.shieldtextWrapper = "true";
  state.textNode.parentNode.replaceChild(wrapper, state.textNode);
  wrapper.appendChild(state.textNode);
  state.wrapper = wrapper;
  return wrapper;
}

function clearRenderedContent(state) {
  if (!(state.wrapper instanceof Element)) return;
  suppressMutationFeedback(120);

  for (const child of [...state.wrapper.children]) {
    if (child.dataset?.shieldtextRendered === "true") {
      child.remove();
    }
  }

  state.wrapper.removeAttribute("data-shieldtext-state");
  state.wrapper.removeAttribute("data-shieldtext-tooltip");
}

function clearPreconcealState(state) {
  if (!state) return;
  if (state.preconcealTimerId) {
    window.clearTimeout(state.preconcealTimerId);
  }
  if (state.preconcealElement instanceof Element) {
    state.preconcealElement.classList.remove("shieldtext-preconceal-element");
    state.preconcealElement.removeAttribute("data-shieldtext-preconceal");
    state.preconcealElement.removeAttribute("data-shieldtext-preconceal-source");
    if (state.preconcealElementTitle) {
      state.preconcealElement.setAttribute("title", state.preconcealElementTitle);
    } else {
      state.preconcealElement.removeAttribute("title");
    }
  }
  state.preconcealTimerId = 0;
  state.preconcealToken = "";
  state.preconcealElement = null;
  state.preconcealElementTitle = "";
}

function getPreconcealCompoundContextText(candidate, fallbackText = "") {
  const pieces = [
    fallbackText,
    candidate?.text,
    candidate?.element instanceof Element ? candidate.element.textContent : ""
  ];
  return pieces
    .map((value) => String(value || "").trim())
    .filter(Boolean)
    .join("\n");
}

function extractHighSignalPreconcealSpans(sourceText, contextText = "") {
  const text = String(sourceText || "");
  if (!text) return [];

  const matcher = new RegExp(HIGH_SIGNAL_PROFANITY_PATTERN.source, "gi");
  const spans = [];
  let match = matcher.exec(text);
  while (match) {
    const value = String(match[0] || "");
    if (value && !isSafeHighSignalCompoundSpan(text, match.index, match.index + value.length, contextText)) {
      spans.push({
        start: match.index,
        end: match.index + value.length,
        text: value
      });
    }
    if (matcher.lastIndex === match.index) {
      matcher.lastIndex += 1;
    }
    match = matcher.exec(text);
  }

  return normalizeEvidenceSpans(spans, text);
}

function isPreconcealableGoogleCandidate(candidate) {
  if (!isGoogleTextSearchAnalysisPage()) return false;
  if (!candidate || candidate.candidateKind === "editable-value") return false;
  if (candidate.candidateKind === "attribute-value") return false;

  const state = candidate.state;
  if (!state || state.isMasked || state.isPending) return false;
  if (!isGooglePriorityCandidate(candidate)) return false;

  const stateText = String(getSourceText(state) || "");
  const candidateText = String(candidate.text || "");
  const contextText = getPreconcealCompoundContextText(candidate, stateText);
  return Boolean(
    (stateText && hasUnsafeHighSignalMatch(stateText, contextText)) ||
    (candidateText && hasUnsafeHighSignalMatch(candidateText, contextText))
  );
}

function releasePreconcealNodeState(state, token) {
  if (!state || state.preconcealToken !== token || state.isMasked) {
    return;
  }

  clearPreconcealState(state);
  clearRenderedContent(state);
  if (state.textNode?.isConnected) {
    state.textNode.nodeValue = state.originalText;
  }
  unwrapInlineWrapperIfRestored(state);
  state.isPending = false;
}

function getPreconcealElementForCandidate(candidate) {
  const element = candidate?.element;
  if (!(element instanceof Element)) return null;

  const target = element.closest(
    [
      GOOGLE_AI_OVERVIEW_SELECTOR,
      "h3",
      "[role='heading']",
      ".LC20lb",
      ".DKV0Md",
      ".VwiC3b",
      ".MUxGbd",
      "[data-sncf]",
      "[data-snf]",
      "[data-content-feature='1']",
      "[data-attrid='description']",
      "[data-attrid='title']"
    ].join(", ")
  ) || element;

  if (
    target === document.body ||
    target === document.documentElement ||
    target.matches("main, #search, #rso, #rhs, #bres, #botstuff")
  ) {
    return null;
  }

  const rect = target.getBoundingClientRect();
  if (!isElementNearViewport(rect)) return null;
  const viewportArea = Math.max(1, window.innerWidth * window.innerHeight);
  const targetArea = Math.max(1, rect.width * rect.height);
  if (targetArea / viewportArea > 0.35) return null;

  return target;
}

function renderElementPreconcealCandidate(candidate) {
  const state = candidate?.state;
  if (!state || state.isMasked || state.isPending) return false;

  const element = getPreconcealElementForCandidate(candidate);
  if (!(element instanceof Element)) return false;

  clearPreconcealState(state);
  const tooltip = "청마루 보호: 분석 중";
  state.preconcealElementTitle = element.getAttribute("title") || "";
  element.classList.add("shieldtext-preconceal-element");
  element.setAttribute("data-shieldtext-preconceal", "true");
  element.setAttribute("data-shieldtext-preconceal-source", "google-high-signal");
  element.setAttribute("title", tooltip);

  const token = `${Date.now()}:${Math.random().toString(36).slice(2)}`;
  state.preconcealElement = element;
  state.preconcealToken = token;
  state.preconcealTimerId = window.setTimeout(
    () => releasePreconcealNodeState(state, token),
    GOOGLE_INITIAL_PRECONCEAL_TTL_MS
  );
  state.isPending = true;
  return true;
}

function renderPreconcealNodeState(state, sourceText, contextText = "") {
  if (!state || state.isMasked || state.isPending) return false;

  const text = String(sourceText || getSourceText(state) || "");
  const spans = extractHighSignalPreconcealSpans(text, contextText);
  if (!text || spans.length === 0) return false;

  const wrapper = ensureWrapper(state);
  if (!wrapper) return false;

  clearPreconcealState(state);
  clearRenderedContent(state);
  state.originalText = text;
  state.textNode.nodeValue = "";

  const renderBox = document.createElement("span");
  renderBox.dataset.shieldtextRendered = "true";
  renderBox.dataset.shieldtextOriginalText = text;
  renderBox.className = "shieldtext-render-box shieldtext-preconceal-box";

  const tooltip = "청마루 보호: 분석 중";
  wrapper.dataset.shieldtextState = "pending";
  wrapper.dataset.shieldtextTooltip = tooltip;
  wrapper.setAttribute("title", tooltip);

  let cursor = 0;
  for (const span of spans) {
    if (span.start > cursor) {
      renderBox.appendChild(document.createTextNode(text.slice(cursor, span.start)));
    }

    const mask = document.createElement("span");
    mask.className = "shieldtext-inline-mask shieldtext-preconceal-mask";
    mask.dataset.shieldtextTooltip = tooltip;
    mask.setAttribute("title", tooltip);
    mask.setAttribute("aria-label", tooltip);
    mask.textContent = buildVisibleMaskReplacement(span.text);
    renderBox.appendChild(mask);
    cursor = span.end;
  }

  if (cursor < text.length) {
    renderBox.appendChild(document.createTextNode(text.slice(cursor)));
  }

  wrapper.appendChild(renderBox);
  const token = `${Date.now()}:${Math.random().toString(36).slice(2)}`;
  state.preconcealToken = token;
  state.preconcealTimerId = window.setTimeout(
    () => releasePreconcealNodeState(state, token),
    GOOGLE_INITIAL_PRECONCEAL_TTL_MS
  );
  state.isPending = true;
  return true;
}

function preConcealGoogleCandidates(candidates, options = {}) {
  const limit = Math.max(
    0,
    Number.isFinite(options.limit)
      ? Number(options.limit)
      : GOOGLE_INITIAL_PRECONCEAL_LIMIT
  );
  if (limit <= 0 || !isGoogleTextSearchAnalysisPage()) return 0;

  let count = 0;
  for (const candidate of Array.isArray(candidates) ? candidates : []) {
    if (count >= limit) break;
    if (!isPreconcealableGoogleCandidate(candidate)) continue;

    const sourceText = String(getSourceText(candidate.state) || "");
    const contextText = getPreconcealCompoundContextText(candidate, sourceText);
    const didPreconceal =
      hasUnsafeHighSignalMatch(sourceText, contextText)
        ? renderPreconcealNodeState(candidate.state, sourceText, contextText)
        : renderElementPreconcealCandidate(candidate);
    if (didPreconceal) {
      count += 1;
    }
  }

  return count;
}

function preConcealGoogleAnalysisUnits(analysisUnits, options = {}) {
  const limit = Math.max(
    0,
    Number.isFinite(options.limit)
      ? Number(options.limit)
      : GOOGLE_INITIAL_PRECONCEAL_LIMIT
  );
  if (limit <= 0 || !isGoogleTextSearchAnalysisPage()) return 0;

  let count = 0;
  const seenNodeIds = new Set();
  for (const unit of Array.isArray(analysisUnits) ? analysisUnits : []) {
    if (count >= limit) break;
    const unitText = String(unit?.text || "");
    if (!hasUnsafeHighSignalMatch(unitText, unitText)) continue;

    for (const member of Array.isArray(unit?.members) ? unit.members : []) {
      if (count >= limit) break;
      const candidate = member?.candidate;
      if (!candidate?.nodeId || seenNodeIds.has(candidate.nodeId)) continue;
      seenNodeIds.add(candidate.nodeId);
      if (!candidate.state || candidate.state.isMasked || candidate.state.isPending) continue;
      if (!isGooglePriorityCandidate(candidate)) continue;

      const sourceText = String(getSourceText(candidate.state) || "");
      const contextText = getPreconcealCompoundContextText(candidate, unitText || sourceText);
      const didPreconceal =
        hasUnsafeHighSignalMatch(sourceText, contextText)
          ? renderPreconcealNodeState(candidate.state, sourceText, contextText)
          : renderElementPreconcealCandidate(candidate);
      if (didPreconceal) {
        count += 1;
      }
    }
  }

  return count;
}

function unwrapInlineWrapperIfRestored(state) {
  const wrapper = state?.wrapper;
  const textNode = state?.textNode;
  if (!(wrapper instanceof Element) || !(textNode instanceof Text)) {
    return;
  }
  if (!wrapper.isConnected || textNode.parentNode !== wrapper || !wrapper.parentNode) {
    return;
  }

  const remainingNodes = [...wrapper.childNodes].filter((node) => node !== textNode);
  if (remainingNodes.length > 0) {
    return;
  }

  suppressMutationFeedback(120);
  wrapper.parentNode.insertBefore(textNode, wrapper);
  wrapper.remove();
  state.wrapper = null;
}

function restoreNodeState(state) {
  if (!state) return;
  suppressMutationFeedback(120);

  clearPreconcealState(state);
  clearRenderedContent(state);
  if (state.textNode?.isConnected) {
    state.textNode.nodeValue = state.originalText;
  }
  unwrapInlineWrapperIfRestored(state);

  state.isMasked = false;
  state.isPending = false;
  state.lastDecisionKey = "";
}

function restoreAttributeValueState(state) {
  if (!state?.element || !state.attributeName) return;
  suppressMutationFeedback(120);
  if (state.originalValue) {
    state.element.setAttribute(state.attributeName, state.originalValue);
  } else {
    state.element.removeAttribute(state.attributeName);
  }
  state.isMasked = false;
  state.isPending = false;
  state.lastDecisionKey = "";
}

// editable overlay helpers are loaded from content-editable-overlay.js

function renderAttributeValueOutcome(state, outcome, settings) {
  if (!state?.element || !state.attributeName) return;
  suppressMutationFeedback(120);

  if (!outcome?.blocked) {
    restoreAttributeValueState(state);
    return;
  }

  const sourceText = String(state.originalValue || "");
  const spans = normalizeEvidenceSpans(outcome.spans, sourceText);
  if (!sourceText || spans.length === 0) {
    restoreAttributeValueState(state);
    return;
  }

  const decisionKey = JSON.stringify({
    text: sourceText,
    attributeName: state.attributeName,
    categories: outcome.categories,
    interventionMode: normalizeInterventionMode(settings?.interventionMode),
    spans
  });
  if (decisionKey === state.lastDecisionKey) {
    return;
  }

  const replacementText = buildAttributeReplacementText(sourceText, spans, settings);
  if (replacementText) {
    state.element.setAttribute(state.attributeName, replacementText);
  } else {
    state.element.removeAttribute(state.attributeName);
  }
  state.isMasked = true;
  state.isPending = false;
  state.lastDecisionKey = decisionKey;
}

function renderOutcome(state, outcome, settings) {
  if (!state) return;
  suppressMutationFeedback(180);
  clearPreconcealState(state);

  if (!outcome?.blocked) {
    restoreNodeState(state);
    return;
  }

  const sourceText = String(state.originalText || "");
  const spans = normalizeEvidenceSpans(outcome.spans, sourceText);
  if (!sourceText || spans.length === 0) {
    restoreNodeState(state);
    return;
  }

  const decisionKey = JSON.stringify({
    text: sourceText,
    categories: outcome.categories,
    interventionMode: normalizeInterventionMode(settings?.interventionMode),
    tooltip: buildMaskTooltip(outcome.categories, outcome.reasons, settings),
    spans
  });
  if (decisionKey === state.lastDecisionKey && !state.isPending) {
    return;
  }

  const wrapper = ensureWrapper(state);
  if (!wrapper) return;

  clearRenderedContent(state);
  state.textNode.nodeValue = "";

  const renderBox = document.createElement("span");
  renderBox.dataset.shieldtextRendered = "true";
  renderBox.dataset.shieldtextOriginalText = sourceText;
  renderBox.className = "shieldtext-render-box";

  const tooltip = buildMaskTooltip(outcome.categories, outcome.reasons, settings);
  const accessibilityLabel = buildMaskAccessibilityLabel(tooltip);
  wrapper.dataset.shieldtextState = "blocked";
  wrapper.dataset.shieldtextTooltip = tooltip;
  wrapper.setAttribute("title", accessibilityLabel);

  let cursor = 0;
  const interventionMode = normalizeInterventionMode(settings?.interventionMode);
  for (const span of spans) {
    if (span.start > cursor) {
      renderBox.appendChild(document.createTextNode(sourceText.slice(cursor, span.start)));
    }

    if (interventionMode === "remove") {
      cursor = span.end;
      continue;
    }

    const mask = document.createElement("span");
    const shouldHide = interventionMode === "hide";
    const shouldBlur = interventionMode === "blur";
    mask.className = shouldHide
      ? "shieldtext-inline-hide"
      : shouldBlur
        ? "shieldtext-inline-blur"
        : "shieldtext-inline-mask";
    mask.dataset.shieldtextTooltip = accessibilityLabel;
    mask.setAttribute("title", accessibilityLabel);
    mask.setAttribute("aria-label", accessibilityLabel);
    if (shouldHide) {
      mask.style.setProperty("color", "transparent", "important");
      mask.style.setProperty("-webkit-text-fill-color", "transparent", "important");
      mask.style.setProperty("text-shadow", "none", "important");
      const hiddenText = document.createElement("span");
      hiddenText.className = "shieldtext-hidden-mask-text";
      hiddenText.textContent = span.text;
      hiddenText.style.setProperty("visibility", "hidden", "important");
      hiddenText.style.setProperty("opacity", "0", "important");
      hiddenText.style.setProperty("color", "transparent", "important");
      hiddenText.style.setProperty("-webkit-text-fill-color", "transparent", "important");
      hiddenText.style.setProperty("text-shadow", "none", "important");
      mask.appendChild(hiddenText);
    } else if (shouldBlur) {
      mask.textContent = span.text;
    } else {
      mask.textContent = buildVisibleMaskReplacement(span.text);
    }
    renderBox.appendChild(mask);

    cursor = span.end;
  }

  if (cursor < sourceText.length) {
    renderBox.appendChild(document.createTextNode(sourceText.slice(cursor)));
  }

  wrapper.appendChild(renderBox);
  state.isMasked = true;
  state.isPending = false;
  state.lastDecisionKey = decisionKey;
}

function applyDecision(candidates, decision, settings, options = {}) {
  const applySummary = {
    preservedHighSignalMaskCount: 0
  };
  const expectedGeneration = Number(options.generation || 0);
  const expectedSettingsRevision = Number(options.settingsRevision ?? settingsRevision);
  const stage = String(options.stage || "foreground");
  const currentSettings = cachedSettings || settings || {};
  const currentFilteringDisabled =
    currentSettings?.enabled === false ||
    isFilteringSuppressedBySensitivity(currentSettings);
  if (
    settings?.enabled === false ||
    currentFilteringDisabled ||
    (Number.isFinite(expectedSettingsRevision) && expectedSettingsRevision !== settingsRevision)
  ) {
    if (currentFilteringDisabled) {
      restoreCandidatesRenderedContent(candidates);
    }
    staleResponseDropCount += Array.isArray(candidates) ? candidates.length : 0;
    return applySummary;
  }

  for (const candidate of candidates) {
    const state = candidate.state;
    const hasOutcome = Object.prototype.hasOwnProperty.call(
      decision.nodeOutcomeMap || {},
      candidate.nodeId
    );
    if (!hasOutcome) {
      continue;
    }
    const outcome = decision.nodeOutcomeMap?.[candidate.nodeId];
    if (!state) continue;
    if (expectedGeneration > 0 && Number(state.analysisGeneration || 0) !== expectedGeneration) {
      staleResponseDropCount += 1;
      continue;
    }
    if (shouldSkipCandidateApply(candidate, state, stage)) {
      staleResponseDropCount += 1;
      markCandidateSettledAfterLowerPriorityApplySkip(candidate);
      continue;
    }
    if (shouldPreserveHighSignalMask(candidate, state, outcome, stage)) {
      applySummary.preservedHighSignalMaskCount += Math.max(
        1,
        findHighSignalProfanitySpans(getCandidateCurrentSourceText(candidate, state)).length
      );
      markCandidateApplied(candidate, stage, true);
      continue;
    }

    if (candidate.candidateKind === "editable-value") {
      if (!isCandidateGenerationCurrent(candidate, expectedGeneration)) {
        staleResponseDropCount += 1;
        continue;
      }

      const wasMasked = Boolean(state.isMasked);
      const previousDecisionKey = String(state.lastDecisionKey || "");
      renderEditableValueOutcome(
        candidate,
        outcome || {
          blocked: false,
          categories: [],
          reasons: [],
          spans: []
        },
        settings
      );

      if (stage === "foreground" && !wasMasked && state.isMasked) {
        foregroundApplyCount += 1;
      } else if (stage === "reconcile") {
        if (wasMasked && !state.isMasked) {
          reconcileUnmaskCount += 1;
        } else if (
          state.isMasked &&
          previousDecisionKey &&
          previousDecisionKey !== String(state.lastDecisionKey || "")
        ) {
          reconcileOverwriteCount += 1;
        }
      }
      markCandidateApplied(candidate, stage, state.isMasked);
      continue;
    }

    if (candidate.candidateKind === "attribute-value") {
      if (!isCandidateGenerationCurrent(candidate, expectedGeneration)) {
        staleResponseDropCount += 1;
        continue;
      }

      const wasMasked = Boolean(state.isMasked);
      const previousDecisionKey = String(state.lastDecisionKey || "");
      renderAttributeValueOutcome(
        state,
        outcome || {
          blocked: false,
          categories: [],
          reasons: [],
          spans: []
        },
        settings
      );

      if (stage === "foreground" && !wasMasked && state.isMasked) {
        foregroundApplyCount += 1;
      } else if (stage === "reconcile") {
        if (wasMasked && !state.isMasked) {
          reconcileUnmaskCount += 1;
        } else if (
          state.isMasked &&
          previousDecisionKey &&
          previousDecisionKey !== String(state.lastDecisionKey || "")
        ) {
          reconcileOverwriteCount += 1;
        }
      }
      markCandidateApplied(candidate, stage, state.isMasked);
      continue;
    }

    if (!isCandidateGenerationCurrent(candidate, expectedGeneration)) {
      staleResponseDropCount += 1;
      continue;
    }

    const wasMasked = Boolean(state.isMasked);
    const previousDecisionKey = String(state.lastDecisionKey || "");
    renderOutcome(
      state,
      outcome || {
        blocked: false,
        categories: [],
        reasons: [],
        spans: []
      },
      settings
    );

    if (stage === "foreground" && !wasMasked && state.isMasked) {
      foregroundApplyCount += 1;
    } else if (stage === "reconcile") {
      if (wasMasked && !state.isMasked) {
        reconcileUnmaskCount += 1;
      } else if (
        state.isMasked &&
        previousDecisionKey &&
        previousDecisionKey !== String(state.lastDecisionKey || "")
      ) {
        reconcileOverwriteCount += 1;
      }
    }
    markCandidateApplied(candidate, stage, state.isMasked);
  }
  return applySummary;
}

function createEmptySessionStats() {
  return {
    totalRuns: 0,
    blockedCount: 0,
    falsePositiveCount: 0,
    averageLatencyMs: 0,
    totalAnalyzedCount: 0,
    byCategory: emptyCategoryHits()
  };
}

function serializeFailureReason(reason) {
  if (!reason) {
    return "UNKNOWN_PIPELINE_ERROR";
  }

  if (typeof reason === "string") {
    return reason;
  }

  if (reason instanceof Error) {
    return String(reason.message || reason.name || "UNKNOWN_PIPELINE_ERROR");
  }

  if (typeof reason === "object") {
    if (reason.message) return String(reason.message);
    if (reason.reason) return String(reason.reason);
    if (reason.errorCode) return String(reason.errorCode);
    try {
      return JSON.stringify(reason);
    } catch {
      return String(reason);
    }
  }

  return String(reason);
}

function serializeFailure(reason, errorCode, retryable) {
  return {
    reason: serializeFailureReason(reason),
    errorCode: String(errorCode || "UNKNOWN_PIPELINE_ERROR"),
    retryable: Boolean(retryable)
  };
}

function truncateDiagnosticText(value, maxLength = 160) {
  const text = normalizeText(value);
  if (!text) return "";
  if (text.length <= maxLength) return text;
  return `${text.slice(0, Math.max(0, maxLength - 1))}\u2026`;
}

function summarizeAnalysisResultForDiagnostics(result, sourceText = "") {
  const spans = normalizeEvidenceSpans(result?.evidence_spans, sourceText)
    .slice(0, 3)
    .map((span) => ({
      start: span.start,
      end: span.end,
      text: truncateDiagnosticText(span.text, 40),
      score: Number(span.score || 0)
    }));

  return {
    is_offensive: Boolean(result?.is_offensive),
    is_profane: Boolean(result?.is_profane),
    is_toxic: Boolean(result?.is_toxic),
    is_hate: Boolean(result?.is_hate),
    spanCount: spans.length,
    spans,
    scores: {
      profanity: Number(result?.scores?.profanity || 0),
      toxicity: Number(result?.scores?.toxicity || 0),
      hate: Number(result?.scores?.hate || 0)
    }
  };
}

function summarizeAnalysisTimingResults(results) {
  const entries = Array.isArray(results) ? results : [];
  const pipelineTimings = [];
  const modelTimings = [];

  for (const result of entries) {
    const pipelineMs = Number(result?.timing_ms);
    const modelMs = Number(result?.model_timing_ms);
    if (Number.isFinite(pipelineMs) && pipelineMs >= 0) {
      pipelineTimings.push(pipelineMs);
    }
    if (Number.isFinite(modelMs) && modelMs >= 0) {
      modelTimings.push(modelMs);
    }
  }

  const summarize = (values) => {
    if (values.length === 0) {
      return {
        count: 0,
        avgMs: 0,
        maxMs: 0
      };
    }
    const total = values.reduce((sum, value) => sum + value, 0);
    return {
      count: values.length,
      avgMs: Math.round((total / values.length) * 1000) / 1000,
      maxMs: Math.round(Math.max(...values) * 1000) / 1000
    };
  };

  return {
    backendPipeline: summarize(pipelineTimings),
    backendModel: summarize(modelTimings)
  };
}

function buildAnalysisDiagnostics(analysisUnits, analysisResults, meta = {}) {
  const units = Array.isArray(analysisUnits) ? analysisUnits : [];
  const results = Array.isArray(analysisResults) ? analysisResults : [];
  const backendInternalTimingSummary =
    meta.backendInternalTimingSummary || summarizeAnalysisTimingResults(results);

  return {
    decisionSource: String(meta.decisionSource || "backend"),
    apiBaseUrl: String(meta.apiBaseUrl || ""),
    backendStatus: String(meta.backendStatus || ""),
    foregroundBackendSource: String(meta.foregroundBackendSource || ""),
    requestedTextCount: Number(meta.requestedTextCount || 0),
    requestCount: Number(meta.requestCount || 0),
    splitRetryCount: Number(meta.splitRetryCount || 0),
    skippedChunkCount: Number(meta.skippedChunkCount || 0),
    failedTextCount: Number(meta.failedTextCount || 0),
    chunkSize: Number(meta.chunkSize || 0),
    requestTimeoutMs: Number(meta.requestTimeoutMs || 0),
    lastBackendErrorCode: String(meta.lastBackendErrorCode || ""),
    backendQueueWaitMs: Number(meta.backendQueueWaitMs || 0),
    backendQueueDepthAtEnqueue: Number(meta.backendQueueDepthAtEnqueue || 0),
    cacheHitCount: Number(meta.cacheHitCount || 0),
    backendCacheHitCount: Number(meta.backendCacheHitCount || 0),
    durationMs: Number(meta.durationMs || 0),
    returnedSpanCount: Number(meta.returnedSpanCount || 0),
    appliedSpanCount: Number(meta.appliedSpanCount || 0),
    droppedSpanCount: Number(meta.droppedSpanCount || 0),
    backendInternalTimingSummary,
    backendRequestTimings: Array.isArray(meta.backendRequestTimings)
      ? meta.backendRequestTimings.slice(-8)
      : [],
    batchSize: units.length,
    items: units.slice(0, 4).map((unit, index) => ({
      text: truncateDiagnosticText(unit?.text, 180),
      memberCount: Array.isArray(unit?.members) ? unit.members.length : 0,
      result: summarizeAnalysisResultForDiagnostics(results[index], unit?.text || "")
    }))
  };
}

async function persistDebug(payload, decision, stats) {
  const runtimeStats = {
    ...(stats || {}),
    ...getRealtimeWorkerDiagnostics(),
    ...getPerformanceGuardDiagnostics()
  };
  const { sessionStats } = await safeStorageLocalGet(["sessionStats"]);
  const currentSessionStats = {
    ...createEmptySessionStats(),
    ...(sessionStats || {}),
    byCategory: {
      ...emptyCategoryHits(),
      ...(sessionStats?.byCategory || {})
    }
  };

  const nextTotalRuns = Number(currentSessionStats.totalRuns || 0) + 1;
  const previousAverageLatencyMs = Number(currentSessionStats.averageLatencyMs || 0);
  const nextAverageLatencyMs = Math.round(
    ((previousAverageLatencyMs * (nextTotalRuns - 1)) + Number(runtimeStats.durationMs || 0)) / nextTotalRuns
  );

  const nextSessionStats = {
    ...currentSessionStats,
    totalRuns: nextTotalRuns,
    blockedCount: Number(currentSessionStats.blockedCount || 0) + Number(runtimeStats.blockedNodeCount || 0),
    totalAnalyzedCount:
      Number(currentSessionStats.totalAnalyzedCount || 0) + Number(runtimeStats.analyzedNodeCount || 0),
    averageLatencyMs: nextAverageLatencyMs,
    byCategory: {
      ...currentSessionStats.byCategory
    }
  };

  Object.entries(decision.categoryHits || {}).forEach(([category, value]) => {
    nextSessionStats.byCategory[category] =
      Number(nextSessionStats.byCategory[category] || 0) + Number(value || 0);
  });

  await safeStorageLocalSet({
    lastPayload: payload,
    lastDecision: decision,
    lastRunAt: Date.now(),
    lastStats: runtimeStats,
    lastPipelineError: null,
    sessionStats: nextSessionStats
  });

  console.info("[청마루] pipeline", {
    analyzedNodeCount: runtimeStats.analyzedNodeCount,
    blockedNodeCount: runtimeStats.blockedNodeCount,
    hostname: runtimeStats.hostname,
    runReason: runtimeStats.runReason,
    backendEndpoint: runtimeStats.backendEndpoint,
    backendStatus: runtimeStats.backendStatus,
    hotPathStatus: runtimeStats.hotPathStatus,
    hotPathErrorCode: runtimeStats.hotPathErrorCode
  });
}

function shouldUseBlockingBackendFallback(runReason) {
  return (
    runReason === "manual" ||
    runReason === "manual-request" ||
    runReason === "manual-request-after-inject"
  );
}

function shouldScheduleBackgroundValidation(runReason) {
  if (
    runReason === "input" ||
    runReason === "input-hot-path" ||
    runReason === "initial-editable-pass" ||
    runReason === "background-validation"
  ) {
    return false;
  }

  if (
    isRapidlyChangingRealtimeHost() &&
    (
      runReason === "mutation" ||
      runReason === "visibility" ||
      runReason === "route-change"
    )
  ) {
    return false;
  }

  return true;
}

function shouldPersistEmptyPipelineRun(runReason) {
  return (
    runReason === "initial-load" ||
    runReason === "manual" ||
    runReason === "manual-request" ||
    runReason === "manual-request-after-inject" ||
    runReason === "settings-updated"
  );
}

function shouldPersistHotPathFailure(runReason) {
  if (
    (
      runReason === "mutation" ||
      runReason === "visibility" ||
      runReason === "route-change" ||
      runReason === "input" ||
      runReason === "input-hot-path" ||
      runReason === "initial-editable-pass"
    ) &&
    isRapidlyChangingRealtimeHost()
  ) {
    return false;
  }

  return !isBroadAnalysisReason(runReason);
}

async function persistFailure(failure, stats) {
  const serialized = serializeFailure(failure?.reason, failure?.errorCode, failure?.retryable);
  const runtimeStats = {
    ...(stats || {}),
    ...getRealtimeWorkerDiagnostics()
  };

  await safeStorageLocalSet({
    lastRunAt: Date.now(),
    lastStats: runtimeStats,
    lastPipelineError: {
      ...serialized,
      timestamp: Date.now(),
      hostname: runtimeStats.hostname,
      runReason: runtimeStats.runReason
    }
  });

  console.error(
    `[청마루] pipeline error ${formatDiagnosticError(serialized)} host=${runtimeStats.hostname || "-"} runReason=${runtimeStats.runReason || "-"} hotPath=${runtimeStats.hotPathStatus || "-"} hotPathError=${runtimeStats.hotPathErrorCode || "-"}`
  );
}

async function persistReconcileDecision(payload, decision, stats, pipelineSequence) {
  const currentState = await safeStorageLocalGet(["lastPayload", "lastDecision", "lastStats"]);
  if (Number(currentState?.lastStats?.pipelineSequence || 0) !== Number(pipelineSequence)) {
    return;
  }

  await safeStorageLocalSet({
    lastPayload: payload,
    lastDecision: decision,
    lastStats: {
      ...currentState.lastStats,
      ...getRealtimeWorkerDiagnostics(),
      analyzedNodeCount: stats.analyzedNodeCount,
      backendCacheHitCount: stats.backendCacheHitCount,
      backendDurationMs: stats.backendDurationMs,
      backendEndpoint: stats.backendEndpoint,
      backendReconcileLatencyMs: stats.backendReconcileLatencyMs,
      backendStatus: stats.backendStatus,
      blockedNodeCount: stats.blockedNodeCount,
      lastDecisionSource: "backend-reconcile",
      maskedSpanCount: stats.maskedSpanCount,
      reconcileRequestCount: Number(stats.reconcileRequestCount || 1),
      reconcileQueueDepth: RECONCILE_QUEUE.size,
      lastReconcileDiagnostics: stats.lastReconcileDiagnostics || null
    },
    lastPipelineError: null
  });
}

async function persistReconcileFailure(failure, stats, pipelineSequence) {
  const currentState = await safeStorageLocalGet(["lastStats"]);
  if (Number(currentState?.lastStats?.pipelineSequence || 0) !== Number(pipelineSequence)) {
    return;
  }

  const serialized = serializeFailure(failure?.reason, failure?.errorCode, failure?.retryable);
  await safeStorageLocalSet({
    lastPipelineError: {
      ...serialized,
      timestamp: Date.now(),
      hostname: stats.hostname,
      runReason: stats.runReason
    },
    lastStats: {
      ...currentState.lastStats,
      ...getRealtimeWorkerDiagnostics(),
      backendEndpoint: stats.backendEndpoint,
      backendStatus: stats.backendStatus,
      backendReconcileLatencyMs: stats.backendReconcileLatencyMs,
      lastDecisionSource: "backend-reconcile-failed",
      lastReconcileDiagnostics: stats.lastReconcileDiagnostics || null
    }
  });
}

function getQueuedCandidateKey(candidate) {
  return `${candidate.nodeId}::${candidate.fingerprint}`;
}

function getCandidateFingerprint(candidate) {
  return String(candidate?.fingerprint || "");
}

function isReconcileAlreadyResolvedOrRunning(candidate) {
  const state = candidate?.state;
  const fingerprint = getCandidateFingerprint(candidate);
  if (!state || !fingerprint) {
    return true;
  }

  return (
    String(state.lastReconcileFingerprint || "") === fingerprint ||
    String(state.reconcileInFlightFingerprint || "") === fingerprint ||
    String(state.lastQueuedReconcileFingerprint || "") === fingerprint
  );
}

function isReconcileResolvedOrInFlight(candidate) {
  const state = candidate?.state;
  const fingerprint = getCandidateFingerprint(candidate);
  if (!state || !fingerprint) {
    return true;
  }

  return (
    String(state.lastReconcileFingerprint || "") === fingerprint ||
    String(state.reconcileInFlightFingerprint || "") === fingerprint
  );
}

function markReconcileQueued(candidate) {
  const fingerprint = getCandidateFingerprint(candidate);
  if (candidate?.state && fingerprint) {
    candidate.state.lastQueuedReconcileFingerprint = fingerprint;
  }
}

function clearReconcileQueued(candidate) {
  if (candidate?.state) {
    candidate.state.lastQueuedReconcileFingerprint = "";
  }
}

function markReconcileInFlight(candidates) {
  for (const candidate of candidates || []) {
    const fingerprint = getCandidateFingerprint(candidate);
    if (candidate?.state && fingerprint) {
      candidate.state.reconcileInFlightFingerprint = fingerprint;
      candidate.state.lastQueuedReconcileFingerprint = "";
    }
  }
}

function clearReconcileInFlight(candidates) {
  for (const candidate of candidates || []) {
    if (candidate?.state) {
      candidate.state.reconcileInFlightFingerprint = "";
    }
  }
}

function scheduleReconcileFlush(delayMs = RECONCILE_FLUSH_DELAY_MS) {
  const normalizedDelay = Math.max(0, Number(delayMs || RECONCILE_FLUSH_DELAY_MS));
  if (reconcileFlushTimerId && scheduledReconcileDelayMs > 0 && scheduledReconcileDelayMs <= normalizedDelay) {
    return;
  }

  if (reconcileFlushTimerId) {
    window.clearTimeout(reconcileFlushTimerId);
  }

  scheduledReconcileDelayMs = normalizedDelay;

  reconcileFlushTimerId = window.setTimeout(() => {
    reconcileFlushTimerId = null;
    scheduledReconcileDelayMs = 0;
    flushReconcileQueue().catch((error) => {
      console.error("[청마루] reconcile queue flush failed", error);
    });
  }, normalizedDelay);
}

function enqueueReconcileCandidates(candidates, pipelineSequence, context, options = {}) {
  for (const candidate of candidates) {
    if (isReconcileAlreadyResolvedOrRunning(candidate)) {
      continue;
    }

    const queueKey = getQueuedCandidateKey(candidate);
    if (RECONCILE_QUEUE.has(queueKey)) {
      continue;
    }

    markReconcileQueued(candidate);
    RECONCILE_QUEUE.set(queueKey, {
      candidate,
      context,
      pipelineSequence
    });
  }

  if (RECONCILE_QUEUE.size > 0) {
    scheduleReconcileFlush(options.delayMs);
  }
}

async function flushReconcileQueue() {
  if (isReconcileRunning || RECONCILE_QUEUE.size === 0) {
    return;
  }

  if (isPipelineRunning) {
    scheduleReconcileFlush(Math.max(RECONCILE_FLUSH_DELAY_MS, 64));
    return;
  }

  isReconcileRunning = true;
  if (reconcileFlushTimerId) {
    window.clearTimeout(reconcileFlushTimerId);
    reconcileFlushTimerId = null;
  }
  scheduledReconcileDelayMs = 0;

  try {
    const queuedEntries = [...RECONCILE_QUEUE.values()];
    const entries = [];

    for (const entry of queuedEntries) {
      const queuedSettingsRevision = Number(entry?.context?.settingsRevision || 0);
      const isStaleSettings =
        queuedSettingsRevision > 0 && !isSettingsRevisionCurrent(queuedSettingsRevision);

      if (isStaleSettings || entry?.context?.enabled === false) {
        RECONCILE_QUEUE.delete(getQueuedCandidateKey(entry.candidate));
        clearReconcileQueued(entry.candidate);
        staleResponseDropCount += 1;
        continue;
      }

      entries.push(entry);
      if (entries.length >= RECONCILE_CHUNK_SIZE) {
        break;
      }
    }

    if (entries.length === 0) {
      return;
    }

    for (const entry of entries) {
      RECONCILE_QUEUE.delete(getQueuedCandidateKey(entry.candidate));
      clearReconcileQueued(entry.candidate);
    }

    const settings = await loadSettings();
    const latestEntry = entries[entries.length - 1];
    const analysisGeneration = Number(latestEntry?.context?.analysisGeneration || 0);
    const candidates = entries
      .map((entry) => entry.candidate)
      .filter(
        (candidate) =>
          isCandidateGenerationCurrent(candidate, analysisGeneration) &&
          !isReconcileResolvedOrInFlight(candidate)
      );

    if (candidates.length === 0) {
      return;
    }

    const units = buildContextualAnalysisUnits(candidates);
    if (units.length === 0) {
      return;
    }
    markReconcileInFlight(candidates);

    await reconcileAnalysisUnitsWithBackend(
      units,
      candidates,
      settings,
      buildPayload(candidates, candidates.length, 0),
      Number(latestEntry?.pipelineSequence || 0),
      latestEntry?.context?.runReason || "background-validation",
      latestEntry?.context?.hostname || location.hostname || "unknown",
      latestEntry?.context?.startedAt || performance.now(),
      analysisGeneration,
      Number(latestEntry?.context?.settingsRevision || settingsRevision)
    );
  } finally {
    isReconcileRunning = false;
    if (RECONCILE_QUEUE.size > 0) {
      scheduleReconcileFlush();
    }
  }
}

function markCandidatesSettled(candidates, generation) {
  for (const candidate of candidates) {
    if (!isCandidateGenerationCurrent(candidate, generation)) {
      continue;
    }
    candidate.state.lastFingerprint = candidate.fingerprint;
    candidate.state.hasProcessed = true;
    candidate.state.lastSkippedAnalysisAt = 0;
    candidate.state.lastSkippedFingerprint = "";
    candidate.state.lastSkippedRetryBackoffMs = 0;
    candidate.state.lastSkippedRetryCount = 0;
    candidate.state.lastSkippedRetryFingerprint = "";
    DIRTY_NODE_IDS.delete(candidate.nodeId);
  }
}

function collectSettledCandidatesFromAnalysisUnits(analysisUnits, analysisResults) {
  const settledCandidates = [];
  const seenNodeIds = new Set();

  (Array.isArray(analysisUnits) ? analysisUnits : []).forEach((unit, index) => {
    const result = Array.isArray(analysisResults) ? analysisResults[index] : null;
    if (!result || result.__shieldtextSkipped === true) {
      return;
    }

    for (const member of unit.members || []) {
      const candidate = member?.candidate;
      if (!candidate?.nodeId || seenNodeIds.has(candidate.nodeId)) {
        continue;
      }
      seenNodeIds.add(candidate.nodeId);
      settledCandidates.push(candidate);
    }
  });

  return settledCandidates;
}

function isHighSignalRetryCandidate(candidate) {
  if (!candidate) {
    return false;
  }

  if (candidate.candidateKind === "editable-value") {
    return true;
  }

  const text = normalizeText(candidate.text || "");
  return Boolean(text && HIGH_SIGNAL_PROFANITY_PATTERN.test(text));
}

function getCurrentStateFingerprint(state) {
  if (!state) {
    return "";
  }

  if (state.element instanceof HTMLInputElement || state.element instanceof HTMLTextAreaElement) {
    return buildFingerprint(normalizeText(getEditableElementText(state.element)));
  }

  if (state.textNode instanceof Text) {
    return buildFingerprint(normalizeText(getSourceText(state)));
  }

  return "";
}

function armSkippedAnalysisRetryTimer() {
  if (SKIPPED_RETRY_NODE_IDS.size === 0) {
    skippedAnalysisRetryDueAt = 0;
    return;
  }

  let nextDueAt = Number.POSITIVE_INFINITY;
  for (const entry of SKIPPED_RETRY_NODE_IDS.values()) {
    nextDueAt = Math.min(nextDueAt, Number(entry?.dueAt || 0));
  }

  if (!Number.isFinite(nextDueAt)) {
    skippedAnalysisRetryDueAt = 0;
    return;
  }

  if (skippedAnalysisRetryTimerId && skippedAnalysisRetryDueAt <= nextDueAt) {
    return;
  }

  if (skippedAnalysisRetryTimerId) {
    window.clearTimeout(skippedAnalysisRetryTimerId);
    skippedAnalysisRetryTimerId = null;
  }

  skippedAnalysisRetryDueAt = nextDueAt;
  const delayMs = Math.max(32, nextDueAt - Date.now());
  skippedAnalysisRetryTimerId = window.setTimeout(() => {
    skippedAnalysisRetryTimerId = null;
    skippedAnalysisRetryDueAt = 0;
    if (extensionContextInvalidated || isUnsupportedPage()) {
      SKIPPED_RETRY_NODE_IDS.clear();
      return;
    }

    const now = Date.now();
    let shouldSchedule = false;

    for (const [nodeId, retryEntry] of SKIPPED_RETRY_NODE_IDS.entries()) {
      if (Number(retryEntry?.dueAt || 0) > now) {
        continue;
      }

      SKIPPED_RETRY_NODE_IDS.delete(nodeId);
      const state = NODE_STATE_BY_ID.get(nodeId) || EDITABLE_VALUE_STATE_BY_ID.get(nodeId);
      if (!state?.lastSkippedFingerprint) {
        continue;
      }
      if (Number(state.analysisGeneration || 0) !== Number(retryEntry?.generation || 0)) {
        continue;
      }
      if (String(state.lastSkippedFingerprint || "") !== String(retryEntry?.fingerprint || "")) {
        continue;
      }
      if (String(state.lastSkippedFingerprint || "") !== getCurrentStateFingerprint(state)) {
        continue;
      }

      DIRTY_NODE_IDS.add(nodeId);
      state.lastSkippedAnalysisAt = 0;
      state.lastSkippedFingerprint = "";
      state.lastSkippedRetryBackoffMs = 0;
      skippedHighSignalRetryCount += 1;
      shouldSchedule = true;
    }

    if (shouldSchedule) {
      schedulePipeline("visibility");
    }

    armSkippedAnalysisRetryTimer();
  }, delayMs);
}

function scheduleSkippedAnalysisRetry(candidates, generation) {
  const retryCandidates = (Array.isArray(candidates) ? candidates : [])
    .filter((candidate) => candidate?.state && isCandidateGenerationCurrent(candidate, generation));
  if (retryCandidates.length === 0) {
    return;
  }

  for (const candidate of retryCandidates) {
    const state = candidate.state;
    const fingerprint = String(candidate.fingerprint || "");
    const dueAt = Number(state.lastSkippedAnalysisAt || Date.now()) +
      Math.max(HIGH_SIGNAL_SKIPPED_RETRY_BACKOFF_MS, Number(state.lastSkippedRetryBackoffMs || 0));

    SKIPPED_RETRY_NODE_IDS.set(candidate.nodeId, {
      generation: Number(state.analysisGeneration || generation || 0),
      fingerprint,
      dueAt
    });
  }

  armSkippedAnalysisRetryTimer();
}

function markSkippedCandidatesForRetryBackoff(analysisUnits, analysisResults, generation) {
  const now = Date.now();
  const highSignalRetryCandidates = [];

  (Array.isArray(analysisUnits) ? analysisUnits : []).forEach((unit, index) => {
    const result = Array.isArray(analysisResults) ? analysisResults[index] : null;
    if (result && result.__shieldtextSkipped !== true) {
      return;
    }

    for (const member of unit.members || []) {
      const candidate = member?.candidate;
      if (!candidate?.state || !isCandidateGenerationCurrent(candidate, generation)) {
        continue;
      }

      const isHighSignal = isHighSignalRetryCandidate(candidate);
      const previousRetryCount =
        String(candidate.state.lastSkippedRetryFingerprint || "") === String(candidate.fingerprint || "")
          ? Number(candidate.state.lastSkippedRetryCount || 0)
          : 0;
      const retryCount = previousRetryCount + 1;
      const baseBackoffMs = isHighSignal
        ? HIGH_SIGNAL_SKIPPED_RETRY_BACKOFF_MS
        : SKIPPED_ANALYSIS_RETRY_BACKOFF_MS;
      const maxBackoffMs = isHighSignal
        ? HIGH_SIGNAL_SKIPPED_RETRY_MAX_BACKOFF_MS
        : SKIPPED_RETRY_MAX_BACKOFF_MS;
      const backoffMs = Math.min(
        maxBackoffMs,
        Math.round(baseBackoffMs * Math.pow(2, Math.max(0, previousRetryCount)))
      );

      candidate.state.lastSkippedAnalysisAt = now;
      candidate.state.lastSkippedFingerprint = candidate.fingerprint;
      candidate.state.lastSkippedRetryBackoffMs = backoffMs;
      candidate.state.lastSkippedRetryCount = retryCount;
      candidate.state.lastSkippedRetryFingerprint = candidate.fingerprint;
      DIRTY_NODE_IDS.delete(candidate.nodeId);

      if (isHighSignal && retryCount <= MAX_HIGH_SIGNAL_SKIPPED_RETRY_ATTEMPTS) {
        highSignalRetryCandidates.push(candidate);
      } else if (isHighSignal) {
        skippedHighSignalRetrySuppressedCount += 1;
      }
    }
  });

  scheduleSkippedAnalysisRetry(highSignalRetryCandidates, generation);
}

function getDirtyCandidates(candidates, runReason) {
  const forceRefresh =
    runReason === "initial-load" ||
    runReason === "manual-request" ||
    runReason === "manual-request-after-inject" ||
    runReason === "settings-updated" ||
    runReason === "manual";

  return candidates.filter((candidate) => {
    if (forceRefresh) return true;
    if (isStateInSkippedRetryBackoff(candidate.state, candidate.fingerprint)) return false;
    if (DIRTY_NODE_IDS.has(candidate.nodeId)) return true;
    if (!candidate.state.hasProcessed) return true;
    return candidate.state.lastFingerprint !== candidate.fingerprint;
  });
}

function collectImmediateInputCandidates() {
  const element = pendingImmediateInputElement;
  pendingImmediateInputElement = null;

  if (!element) {
    return [];
  }

  const candidate = buildEditableValueCandidate(element);
  return candidate ? [candidate] : [];
}

function clearStaleEditableMaskForElement(element) {
  if (!(element instanceof HTMLInputElement) && !(element instanceof HTMLTextAreaElement)) {
    return;
  }

  const editableId = EDITABLE_VALUE_ID_MAP.get(element);
  if (!editableId) {
    return;
  }

  const state = EDITABLE_VALUE_STATE_BY_ID.get(editableId);
  if (!state?.isMasked) {
    return;
  }

  const currentText = getEditableElementText(element);
  const currentFingerprint = buildFingerprint(normalizeText(currentText));
  const appliedFingerprint = String(state.lastAppliedFingerprint || "");
  if (!currentFingerprint || !appliedFingerprint || currentFingerprint === appliedFingerprint) {
    return;
  }

  if (
    typeof carryForwardEditableMask === "function" &&
    carryForwardEditableMask(state, currentText, cachedSettings)
  ) {
    editableMaskCarryForwardCount += 1;
    return;
  }

  inputMaskResetCount += 1;
  restoreEditableValueState(state);
}

function collectBackendReconcileCandidates(candidates, foregroundCandidates) {
  const reconcileCandidates = new Map();
  const orderedCandidates = Array.isArray(foregroundCandidates) ? foregroundCandidates : [];

  for (const candidate of orderedCandidates) {
    if (!candidate || candidate.candidateKind === "editable-value") {
      continue;
    }
    if (isReconcileAlreadyResolvedOrRunning(candidate)) {
      continue;
    }

    reconcileCandidates.set(candidate.nodeId, candidate);
    if (reconcileCandidates.size >= RECONCILE_CHUNK_SIZE) {
      break;
    }
  }

  return [...reconcileCandidates.values()];
}

async function executeHotPathForCandidates(candidates, runReason) {
  const nextCandidates = (Array.isArray(candidates) ? candidates : []).filter(Boolean);
  if (extensionContextInvalidated || nextCandidates.length === 0 || isUnsupportedPage()) {
    return { ok: true, skipped: true };
  }

  const settings = await loadSettings();
  const activeSettingsRevision = settingsRevision;

  if (!settings.enabled) {
    for (const candidate of nextCandidates) {
      if (candidate.candidateKind === "editable-value") {
        restoreEditableValueState(candidate.state);
      } else if (candidate.candidateKind === "attribute-value") {
        restoreAttributeValueState(candidate.state);
      } else {
        restoreNodeState(candidate.state);
      }
    }
    return { ok: true, skipped: true };
  }

  if (isFilteringSuppressedBySensitivity(settings)) {
    restoreAllRenderedContent();
    scheduleHotPathStatsPersist({
      enabled: true,
      runReason,
      backendStatus: "ready",
      sensitivityMode: getSensitivityMode(settings),
      maskedSpanCount: 0,
      visibleContainerBatchSize: 0,
      lastDecisionSource: "sensitivity-disabled"
    });
    return { ok: true, skipped: true, reason: "SENSITIVITY_DISABLED" };
  }

  const currentCandidates = nextCandidates
    .map((candidate) => {
      if (candidate.candidateKind === "editable-value") {
        return buildEditableValueCandidate(candidate.element);
      }
      if (candidate.candidateKind === "attribute-value") {
        return buildAttributeValueCandidate(candidate.element, candidate.state?.attributeName);
      }
      return candidate;
    })
    .filter(Boolean);

  if (currentCandidates.length === 0) {
    return { ok: true, skipped: true };
  }

  const analysisGeneration = ++latestAnalysisGeneration;
  markCandidatesAnalysisGeneration(currentCandidates, analysisGeneration);
  const foregroundCandidates = selectForegroundWaveCandidates(currentCandidates, settings, runReason);
  const analysisUnits = buildHotPathAnalysisUnits(foregroundCandidates, {
      containerLimit: MAX_FOREGROUND_WAVE_CONTAINERS,
      boundContext: true,
      preferStandaloneGoogle:
        runReason === "input-hot-path" ||
        runReason === "input" ||
        runReason === "initial-editable-pass"
    });
  if (analysisUnits.length === 0) {
    return { ok: true, skipped: true };
  }
  const unitCandidates = collectUnitCandidates(analysisUnits);

  const startedAt = performance.now();
  const pipelineSequence = ++latestPipelineSequence;
  const preconcealCount = preConcealGoogleAnalysisUnits(analysisUnits, {
    limit: GOOGLE_INITIAL_PRECONCEAL_LIMIT,
    runReason
  });
  const localPreflight = applyLocalPreflightDecision(unitCandidates, analysisUnits, settings, {
    generation: analysisGeneration,
    settingsRevision: activeSettingsRevision,
    startedAt,
    pipelineSequence
  });
  let firstMaskLatencyMs = Number(localPreflight.firstMaskLatencyMs || 0);
  const hotPathMeta = await analyzePayloadWithRealtimeWorker(
    analysisUnits,
    settings,
    null,
    {
      requestTimeoutMs: FOREGROUND_ANALYZE_TIMEOUT_MS,
      analysisMode: "foreground"
    }
  );

  if (
    analysisGeneration !== latestAnalysisGeneration ||
    !isSettingsRevisionCurrent(activeSettingsRevision)
  ) {
    staleResponseDropCount += unitCandidates.length;
    return { ok: true, stale: true };
  }

  const hostname = location.hostname || "unknown";

  if (!hotPathMeta.ok) {
    return {
      ok: false,
      errorCode: hotPathMeta.error?.errorCode,
      reason: hotPathMeta.error?.reason
    };
  }

  const decision = buildDecisionFromBackend(
    analysisUnits,
    hotPathMeta.results,
    settings,
    {
      apiBaseUrl: hotPathMeta.apiBaseUrl || settings.backendApiBaseUrl,
      backendDurationMs: Number(hotPathMeta.durationMs || 0),
      backendStatus: hotPathMeta.backendStatus || "ready"
    }
  );
  recordWellbeingDetectionFromDecision(decision, "backend-foreground", pipelineSequence);

  if (Number(decision.maskedSpanCount || 0) > 0) {
    suppressMutationFeedback(180);
  }

  applyDecision(unitCandidates, decision, settings, {
    generation: analysisGeneration,
    stage: "foreground",
    settingsRevision: activeSettingsRevision
  });
  markCandidatesSettled(
    collectSettledCandidatesFromAnalysisUnits(analysisUnits, hotPathMeta.results),
    analysisGeneration
  );
  markSkippedCandidatesForRetryBackoff(analysisUnits, hotPathMeta.results, analysisGeneration);

  if (!firstMaskLatencyMs && Number(decision.maskedSpanCount || 0) > 0) {
    firstMaskLatencyMs = Math.round(performance.now() - startedAt);
  }

  scheduleHotPathStatsPersist({
    analyzedNodeCount: decision.analyzedNodeCount,
    backendEndpoint: hotPathMeta.apiBaseUrl || settings.backendApiBaseUrl,
    backendStatus: hotPathMeta.backendStatus || "ready",
    blockedNodeCount: decision.blockedNodeCount,
    lastDecisionSource: "backend-foreground",
    lastForegroundDiagnostics: buildAnalysisDiagnostics(
      analysisUnits,
      hotPathMeta.results,
      {
        decisionSource: "backend-foreground",
        apiBaseUrl: hotPathMeta.apiBaseUrl || settings.backendApiBaseUrl,
        backendStatus: hotPathMeta.backendStatus || "ready",
        foregroundBackendSource: hotPathMeta.foregroundBackendSource || "",
        requestedTextCount: Number(hotPathMeta.requestedCount || 0),
        requestCount: Number(hotPathMeta.requestCount || 0),
        splitRetryCount: Number(hotPathMeta.splitRetryCount || 0),
        skippedChunkCount: Number(hotPathMeta.skippedChunkCount || 0),
        failedTextCount: Number(hotPathMeta.failedTextCount || 0),
        chunkSize: Number(hotPathMeta.chunkSize || 0),
        requestTimeoutMs: Number(hotPathMeta.requestTimeoutMs || 0),
        lastBackendErrorCode: String(hotPathMeta.lastBackendErrorCode || ""),
        backendQueueWaitMs: Number(hotPathMeta.backendQueueWaitMs || 0),
        backendQueueDepthAtEnqueue: Number(hotPathMeta.backendQueueDepthAtEnqueue || 0),
        backendRequestTimings: Array.isArray(hotPathMeta.backendRequestTimings)
          ? hotPathMeta.backendRequestTimings
          : [],
        cacheHitCount: Number(hotPathMeta.cacheHitCount || 0),
        backendCacheHitCount: Number(hotPathMeta.backendCacheHitCount || 0),
        durationMs: Number(hotPathMeta.durationMs || 0),
        returnedSpanCount: Number(decision.returnedSpanCount || 0),
        appliedSpanCount: Number(decision.maskedSpanCount || 0),
        droppedSpanCount: Number(decision.droppedSpanCount || 0)
      }
    ),
    durationMs: firstMaskLatencyMs || Number(hotPathMeta.durationMs || 0),
    enabled: true,
    firstMaskLatencyMs,
    hostname,
    hotPathLatencyMs: Number(hotPathMeta.durationMs || 0),
    maskedSpanCount: Number(decision.maskedSpanCount || 0),
    localPreflightMaskedSpanCount: Number(localPreflight.decision?.maskedSpanCount || 0),
    preconcealCount,
    pipelineSequence,
    reconcileQueueDepth: RECONCILE_QUEUE.size,
    runReason,
    visibleContainerBatchSize: analysisUnits.length,
    foregroundCandidateCount: unitCandidates.length,
    sensitivityMode: getSensitivityMode(settings),
    workerCacheHitCount: Number(hotPathMeta.cacheHitCount || 0),
    backendCacheHitCount: Number(hotPathMeta.backendCacheHitCount || 0),
    foregroundBackendSource: hotPathMeta.foregroundBackendSource || "",
    foregroundRequestCount: Number(hotPathMeta.requestCount || 0),
    foregroundSplitRetryCount: Number(hotPathMeta.splitRetryCount || 0),
    foregroundSkippedChunkCount: Number(hotPathMeta.skippedChunkCount || 0),
    foregroundFailedTextCount: Number(hotPathMeta.failedTextCount || 0),
    foregroundRequestTimeoutMs: Number(hotPathMeta.requestTimeoutMs || 0),
    foregroundLastBackendErrorCode: String(hotPathMeta.lastBackendErrorCode || ""),
    foregroundBackendQueueWaitMs: Number(hotPathMeta.backendQueueWaitMs || 0),
    foregroundBackendQueueDepth: Number(hotPathMeta.backendQueueDepthAtEnqueue || 0),
    returnedSpanCount: Number(decision.returnedSpanCount || 0),
    droppedSpanCount: Number(decision.droppedSpanCount || 0)
  });

  const reconcileCandidates = collectBackendReconcileCandidates(currentCandidates, foregroundCandidates)
    .filter((candidate) => isCandidateGenerationCurrent(candidate, analysisGeneration));

  if (reconcileCandidates.length > 0) {
    enqueueReconcileCandidates(reconcileCandidates, pipelineSequence, {
      hostname,
      runReason,
      startedAt,
      analysisGeneration,
      settingsRevision: activeSettingsRevision,
      enabled: settings.enabled !== false
    }, {
      delayMs:
        Number(decision.blockedNodeCount || 0) > 0
          ? RECONCILE_FAST_FLUSH_DELAY_MS
          : RECONCILE_FLUSH_DELAY_MS
    });
  }

  return {
    ok: true,
    decision,
    latencyMs: Number(hotPathMeta.durationMs || 0),
    pipelineSequence,
    analysisGeneration
  };
}

function scheduleImmediateInputPipeline(element, runReason = "input-hot-path") {
  if (extensionContextInvalidated) return;
  pendingImmediateInputElement = element;

  if (immediateInputTimerId) {
    return;
  }

  immediateInputTimerId = window.requestAnimationFrame(() => {
    immediateInputTimerId = null;
    const candidates = collectImmediateInputCandidates();
    if (candidates.length === 0) {
      return;
    }

    executeHotPathForCandidates(candidates, runReason).catch((error) => {
      console.error("[청마루] immediate input hot path failed", error);
    });
  });
}

function scheduleInitialEditablePass() {
  if (extensionContextInvalidated) return;
  if (initialEditablePassFrameId) return;

  initialEditablePassFrameId = window.requestAnimationFrame(() => {
    initialEditablePassFrameId = null;
    const candidates = collectEditableValueCandidates(INITIAL_EDITABLE_PASS_LIMIT);
    if (candidates.length === 0) {
      return;
    }

    executeHotPathForCandidates(candidates, "initial-editable-pass").catch((error) => {
      console.error("[청마루] initial editable hot path failed", error);
    });
  });
}

async function reconcileAnalysisUnitsWithBackend(
  analysisUnits,
  prioritizedCandidates,
  settings,
  payload,
  pipelineSequence,
  runReason,
  hostname,
  startedAt,
  analysisGeneration,
  expectedSettingsRevision = settingsRevision
) {
  const unitCandidates = collectUnitCandidates(analysisUnits);
  try {
    const reconcileStartedAt = performance.now();
    const fullMeta = await analyzePayloadWithBackend(
      analysisUnits,
      null,
      {
        requestTimeoutMs: RECONCILE_ANALYZE_TIMEOUT_MS,
        analysisMode: "reconcile"
      }
    );

    if (!fullMeta.ok) {
      await persistReconcileFailure(
        fullMeta.error,
        {
          backendEndpoint: fullMeta.apiBaseUrl || settings.backendApiBaseUrl,
          backendReconcileLatencyMs: Math.round(performance.now() - reconcileStartedAt),
          backendStatus: fullMeta.error?.backendStatus || "degraded",
          hostname,
          runReason
        },
        pipelineSequence
      );
      return;
    }

    if (!isSettingsRevisionCurrent(expectedSettingsRevision) || settings?.enabled === false) {
      staleResponseDropCount += unitCandidates.length;
      return;
    }

    const decision = buildDecisionFromBackend(
      analysisUnits,
      fullMeta.results,
      settings,
      {
        apiBaseUrl: fullMeta.apiBaseUrl || settings.backendApiBaseUrl,
        backendDurationMs: fullMeta.backendDurationMs,
        backendStatus: fullMeta.backendStatus || "ready"
      }
    );
    recordWellbeingDetectionFromDecision(decision, "backend-reconcile", pipelineSequence);

    suppressMutationFeedback(120);
    applyDecision(unitCandidates, decision, settings, {
      generation: analysisGeneration,
      stage: "reconcile",
      settingsRevision: expectedSettingsRevision
    });
    markCandidatesSettled(
      collectSettledCandidatesFromAnalysisUnits(analysisUnits, fullMeta.results),
      analysisGeneration
    );
    markSkippedCandidatesForRetryBackoff(analysisUnits, fullMeta.results, analysisGeneration);

    await persistReconcileDecision(
      payload,
      decision,
      {
        analyzedNodeCount: decision.analyzedNodeCount,
        backendCacheHitCount: Number(fullMeta.cacheHitCount || 0),
        backendDurationMs: Number(fullMeta.backendDurationMs || 0),
        backendEndpoint: fullMeta.apiBaseUrl || settings.backendApiBaseUrl,
        backendReconcileQueueWaitMs: Number(fullMeta.backendQueueWaitMs || 0),
        backendReconcileQueueDepth: Number(fullMeta.backendQueueDepthAtEnqueue || 0),
        backendReconcileLatencyMs: Math.round(performance.now() - startedAt),
        backendStatus: fullMeta.backendStatus || "ready",
        blockedNodeCount: decision.blockedNodeCount,
        maskedSpanCount: Number(decision.maskedSpanCount || 0),
        lastReconcileDiagnostics: buildAnalysisDiagnostics(
          analysisUnits,
          fullMeta.results,
          {
            decisionSource: "backend-reconcile",
            apiBaseUrl: fullMeta.apiBaseUrl || settings.backendApiBaseUrl,
            backendStatus: fullMeta.backendStatus || "ready",
            foregroundBackendSource: getForegroundBackendSource(fullMeta),
            requestedTextCount: Number(fullMeta.requestedCount || 0),
            requestCount: Number(fullMeta.requestCount || 0),
            splitRetryCount: Number(fullMeta.splitRetryCount || 0),
            skippedChunkCount: Number(fullMeta.skippedChunkCount || 0),
            failedTextCount: Number(fullMeta.failedTextCount || 0),
            chunkSize: Number(fullMeta.chunkSize || 0),
            requestTimeoutMs: Number(fullMeta.requestTimeoutMs || 0),
            lastBackendErrorCode: String(fullMeta.lastBackendErrorCode || ""),
            backendQueueWaitMs: Number(fullMeta.backendQueueWaitMs || 0),
            backendQueueDepthAtEnqueue: Number(fullMeta.backendQueueDepthAtEnqueue || 0),
            backendRequestTimings: Array.isArray(fullMeta.backendRequestTimings)
              ? fullMeta.backendRequestTimings
              : [],
            cacheHitCount: Number(fullMeta.cacheHitCount || 0),
            backendCacheHitCount: Number(fullMeta.backendCacheHitCount || 0),
            durationMs: Number(fullMeta.backendDurationMs || 0),
            returnedSpanCount: Number(decision.returnedSpanCount || 0),
            appliedSpanCount: Number(decision.maskedSpanCount || 0),
            droppedSpanCount: Number(decision.droppedSpanCount || 0)
          }
        )
      },
      pipelineSequence
    );
  } catch (error) {
    await persistReconcileFailure(
      {
        reason: String(error?.message || error || "BACKEND_RECONCILE_FAILED"),
        errorCode: "BACKEND_RECONCILE_FAILED",
        retryable: true
      },
      {
        backendEndpoint: settings.backendApiBaseUrl,
        backendReconcileLatencyMs: Math.round(performance.now() - startedAt),
        backendStatus: "degraded",
        hostname,
        runReason,
        lastReconcileDiagnostics: {
          decisionSource: "backend-reconcile-failed",
          batchSize: Array.isArray(analysisUnits) ? analysisUnits.length : 0,
          apiBaseUrl: settings.backendApiBaseUrl,
          backendStatus: "degraded"
        }
      },
      pipelineSequence
    );
  } finally {
    clearReconcileInFlight(unitCandidates);
  }
}

async function executePipeline(runReason) {
  if (isUnsupportedPage()) {
    return { ok: false, reason: "UNSUPPORTED_PAGE", errorCode: "UNSUPPORTED_PAGE" };
  }

  if (isPipelineRunning) {
    queuedReason = chooseHigherPriorityPipelineReason(queuedReason, runReason);
    return { ok: true, queued: true };
  }

  isPipelineRunning = true;
  const pipelineSequence = ++latestPipelineSequence;
  const startedAt = performance.now();

  try {
    const settings = await loadSettings({ force: shouldForceSettingsLoadForRun(runReason) });
    const settingsLoadedAt = performance.now();
    const activeSettingsRevision = settingsRevision;
    const hostname = location.hostname || "unknown";

    if (!settings.enabled) {
      restoreAllRenderedContent();
      cancelScheduledPipeline();

      const payload = buildPayload([], 0, 0);
      const decision = {
        blockedNodeIds: [],
        matchedKeywords: [],
        categoryHits: emptyCategoryHits(),
        nodeCategoryMap: {},
        nodeReasonMap: {},
        nodeScoreMap: {},
        nodeEvidenceMap: {},
        nodePendingMap: {},
        nodeOutcomeMap: {},
        analyzedNodeCount: 0,
        blockedNodeCount: 0,
        backendEndpoint: settings.backendApiBaseUrl,
        backendDurationMs: 0,
        backendStatus: "disabled",
        apiMode: "disabled"
      };

      const stats = {
        hostname,
        analyzedNodeCount: 0,
        blockedNodeCount: 0,
        matchedKeywordCount: 0,
        durationMs: Math.round(performance.now() - startedAt),
        runReason,
        enabled: false,
        backendEndpoint: settings.backendApiBaseUrl,
        backendStatus: "disabled",
        totalCandidateCount: 0,
        requestedAnalysisCount: 0,
        cacheHitCount: 0
      };

      await persistDebug(payload, decision, stats);
      return { ok: true, stats };
    }

    if (isFilteringSuppressedBySensitivity(settings)) {
      restoreAllRenderedContent();
      cancelScheduledPipeline();

      const payload = buildPayload([], 0, 0);
      const decision = {
        blockedNodeIds: [],
        matchedKeywords: [],
        categoryHits: emptyCategoryHits(),
        nodeCategoryMap: {},
        nodeReasonMap: {},
        nodeScoreMap: {},
        nodeEvidenceMap: {},
        nodePendingMap: {},
        nodeOutcomeMap: {},
        analyzedNodeCount: 0,
        blockedNodeCount: 0,
        backendEndpoint: settings.backendApiBaseUrl,
        backendDurationMs: 0,
        backendStatus: "ready",
        apiMode: "sensitivity-disabled"
      };
      const stats = {
        hostname,
        analyzedNodeCount: 0,
        blockedNodeCount: 0,
        matchedKeywordCount: 0,
        durationMs: Math.round(performance.now() - startedAt),
        runReason,
        enabled: true,
        sensitivityDisabled: true,
        sensitivityMode: getSensitivityMode(settings),
        sensitivity: normalizeSensitivity(settings.sensitivity),
        backendEndpoint: settings.backendApiBaseUrl,
        backendStatus: "ready",
        totalCandidateCount: 0,
        requestedAnalysisCount: 0,
        cacheHitCount: 0,
        lastDecisionSource: "sensitivity-disabled",
        maskedSpanCount: 0,
        firstMaskLatencyMs: 0,
        reconcileQueueDepth: 0,
        lastForegroundDiagnostics: {
          decisionSource: "sensitivity-disabled",
          apiBaseUrl: settings.backendApiBaseUrl,
          backendStatus: "ready",
          foregroundBackendSource: "disabled",
          sensitivityMode: getSensitivityMode(settings),
          batchSize: 0,
          items: []
        }
      };

      await persistDebug(payload, decision, stats);
      return { ok: true, stats };
    }

    const candidateCollectStartedAt = performance.now();
    const immediateInputCandidates = runReason === "input" ? collectImmediateInputCandidates() : [];
    let candidates = immediateInputCandidates.length > 0 ? immediateInputCandidates : collectCandidates(runReason);
    if (runReason === "settings-updated") {
      candidates = includeEditableCandidatesForSettingsRefresh(candidates);
    }
    const candidatesCollectedAt = performance.now();
    const dirtySelectStartedAt = performance.now();
    const dirtyCandidates = immediateInputCandidates.length > 0
      ? immediateInputCandidates
      : getDirtyCandidates(candidates, runReason);
    const dirtySelectedAt = performance.now();
    const prioritizeStartedAt = performance.now();
    const prioritizedCandidates = selectCandidatesForRun(
      dirtyCandidates,
      settings,
      runReason
    );
    const prioritizedAt = performance.now();
    const analysisGeneration = ++latestAnalysisGeneration;
    markCandidatesAnalysisGeneration(prioritizedCandidates, analysisGeneration);
    const foregroundSelectStartedAt = performance.now();
    const foregroundCandidates = selectForegroundWaveCandidates(
      prioritizedCandidates,
      settings,
      runReason
    );
    const foregroundSelectedAt = performance.now();
    const foregroundUnitBuildStartedAt = performance.now();
    let analysisUnits = buildHotPathAnalysisUnits(foregroundCandidates, {
      containerLimit: isBroadAnalysisReason(runReason)
        ? MAX_HOT_PATH_CONTAINERS
        : MAX_FOREGROUND_WAVE_CONTAINERS,
      boundContext: true,
      preferStandaloneGoogle:
        runReason === "input" ||
        runReason === "input-hot-path" ||
        runReason === "initial-editable-pass"
    });
    if (runReason === "background-validation") {
      analysisUnits = analysisUnits.slice(0, MAX_BACKGROUND_VALIDATION_ANALYSIS_UNITS);
    }
    const unitCandidates = collectUnitCandidates(analysisUnits);
    const analyzedCandidateIds = new Set(unitCandidates.map((candidate) => candidate.nodeId));
    const foregroundUnitBuildMs = Math.round(performance.now() - foregroundUnitBuildStartedAt);
    const droppedCandidateCount = Math.max(0, dirtyCandidates.length - prioritizedCandidates.length);
    const remainingPrioritizedCandidateCount = prioritizedCandidates.filter(
      (candidate) => !analyzedCandidateIds.has(candidate.nodeId)
    ).length;
    const contextualReconcileCandidates = runReason === "background-validation"
      ? []
      : collectBackendReconcileCandidates(
          prioritizedCandidates,
          foregroundCandidates
        ).filter((candidate) => isCandidateGenerationCurrent(candidate, analysisGeneration));

    if (prioritizedCandidates.length === 0 || foregroundCandidates.length === 0 || analysisUnits.length === 0) {
      const payload = buildPayload([], candidates.length, 0);
      const decision = {
        blockedNodeIds: [],
        matchedKeywords: [],
        categoryHits: emptyCategoryHits(),
        nodeCategoryMap: {},
        nodeReasonMap: {},
        nodeScoreMap: {},
        nodeEvidenceMap: {},
        nodePendingMap: {},
        nodeOutcomeMap: {},
        analyzedNodeCount: 0,
        blockedNodeCount: 0,
        backendEndpoint: settings.backendApiBaseUrl,
        backendDurationMs: 0,
        backendStatus: "ready",
        apiMode: "backend-first"
      };
      const stats = {
        hostname,
        analyzedNodeCount: 0,
        blockedNodeCount: 0,
        matchedKeywordCount: 0,
        durationMs: Math.round(performance.now() - startedAt),
        runReason,
        enabled: true,
        backendEndpoint: settings.backendApiBaseUrl,
        backendStatus: "ready",
        backendDurationMs: 0,
        foregroundBackendLatencyMs: 0,
        foregroundBackendSource: "fallback-none",
        cacheHitCount: 0,
        foregroundUnitBuildMs,
        firstPaintMaskMs: 0,
        reconcileQueueDepth: RECONCILE_QUEUE.size,
        reconcileSkippedCount: 0,
        requestedAnalysisCount: 0,
        totalCandidateCount: candidates.length,
        droppedCandidateCount: 0,
        firstMaskLatencyMs: 0,
        foregroundRequestCount: 0,
        reconcileRequestCount: 0,
        lastDecisionSource: "backend-foreground",
        sensitivityMode: getSensitivityMode(settings),
        lastForegroundDiagnostics: {
          decisionSource: "backend-foreground",
          apiBaseUrl: settings.backendApiBaseUrl,
          backendStatus: "ready",
          foregroundBackendSource: "fallback-none",
          sensitivityMode: getSensitivityMode(settings),
          batchSize: 0,
          items: []
        }
      };
      maybeActivatePerformanceGuard(stats, runReason);
      if (shouldPersistEmptyPipelineRun(runReason)) {
        await persistDebug(payload, decision, stats);
      } else {
        scheduleHotPathStatsPersist({
          hostname,
          durationMs: Math.round(performance.now() - startedAt),
          runReason,
          totalCandidateCount: candidates.length,
          requestedAnalysisCount: 0,
          reconcileQueueDepth: RECONCILE_QUEUE.size,
          visibleContainerBatchSize: 0
        });
      }

      if (
        dirtyCandidates.length > 0 &&
        shouldScheduleBackgroundValidation(runReason) &&
        !queuedReason
      ) {
        queuedReason = "background-validation";
      }

      return { ok: true, stats };
    }

    let firstMaskLatencyMs = 0;
    const payload = buildPayload(foregroundCandidates, candidates.length, droppedCandidateCount);
    const preBackendCompletedAt = performance.now();
    const preconcealCount = preConcealGoogleAnalysisUnits(analysisUnits, {
      limit: GOOGLE_INITIAL_PRECONCEAL_LIMIT,
      runReason
    });
    const localPreflight = applyLocalPreflightDecision(unitCandidates, analysisUnits, settings, {
      generation: analysisGeneration,
      settingsRevision: activeSettingsRevision,
      startedAt,
      pipelineSequence
    });
    if (!firstMaskLatencyMs && Number(localPreflight.firstMaskLatencyMs || 0) > 0) {
      firstMaskLatencyMs = Number(localPreflight.firstMaskLatencyMs || 0);
    }
    const localPreflightCompletedAt = performance.now();
    const backendStartedAt = performance.now();
    const hotPathMeta = await analyzePayloadWithRealtimeWorker(
      analysisUnits,
      settings,
      async (partialMeta) => {
        if (
          pipelineSequence !== latestPipelineSequence ||
          !isSettingsRevisionCurrent(activeSettingsRevision)
        ) {
          staleResponseDropCount += collectUnitCandidates(partialMeta.items).length;
          return;
        }

        const partialDecision = buildDecisionFromBackend(
          partialMeta.items,
          partialMeta.results,
          settings,
          partialMeta
        );

        if (!firstMaskLatencyMs && Number(partialDecision.maskedSpanCount || 0) > 0) {
          firstMaskLatencyMs = Math.round(performance.now() - startedAt);
        }

        suppressMutationFeedback(220);
        applyDecision(
          collectUnitCandidates(partialMeta.items),
          partialDecision,
          settings,
          {
            generation: analysisGeneration,
            stage: "foreground",
            settingsRevision: activeSettingsRevision
          }
        );
      },
      {
        requestTimeoutMs:
          runReason === "background-validation"
            ? RECONCILE_ANALYZE_TIMEOUT_MS
            : FOREGROUND_ANALYZE_TIMEOUT_MS,
        suppressHotPathFailure: runReason === "background-validation",
        analysisMode:
          runReason === "background-validation"
            ? "background-validation"
            : "foreground"
      }
    );
    const backendCompletedAt = performance.now();
    let stats = null;

    if (!isSettingsRevisionCurrent(activeSettingsRevision)) {
      staleResponseDropCount += unitCandidates.length;
      return { ok: true, stale: true };
    }

    if (!hotPathMeta.ok) {
      const failureStats = {
        hostname,
        analyzedNodeCount: analysisUnits.length,
        blockedNodeCount: 0,
        matchedKeywordCount: 0,
        durationMs: Math.round(performance.now() - startedAt),
        runReason,
        enabled: true,
        backendEndpoint: hotPathMeta.apiBaseUrl || settings.backendApiBaseUrl,
        backendStatus: hotPathMeta.backendStatus || "degraded",
        foregroundBackendLatencyMs: Number(hotPathMeta.durationMs || 0),
        foregroundBackendSource: hotPathMeta.foregroundBackendSource || "fallback-none",
        foregroundRequestCount: Number(hotPathMeta.requestCount || 0),
        foregroundSplitRetryCount: Number(hotPathMeta.splitRetryCount || 0),
        foregroundSkippedChunkCount: Number(hotPathMeta.skippedChunkCount || 0),
        foregroundFailedTextCount: Number(hotPathMeta.failedTextCount || 0),
        foregroundRequestTimeoutMs: Number(hotPathMeta.requestTimeoutMs || 0),
        foregroundLastBackendErrorCode: String(hotPathMeta.lastBackendErrorCode || ""),
        foregroundBackendQueueWaitMs: Number(hotPathMeta.backendQueueWaitMs || 0),
        foregroundBackendQueueDepth: Number(hotPathMeta.backendQueueDepthAtEnqueue || 0),
        reconcileRequestCount: 0,
        totalCandidateCount: candidates.length,
        requestedAnalysisCount: analysisUnits.length,
        cacheHitCount: 0,
        localPreflightMaskedSpanCount: Number(localPreflight.decision?.maskedSpanCount || 0),
        preconcealCount,
        lastDecisionSource: "backend-foreground-failed",
        sensitivityMode: getSensitivityMode(settings),
        lastForegroundDiagnostics: {
          decisionSource: "backend-foreground-failed",
          apiBaseUrl: hotPathMeta.apiBaseUrl || settings.backendApiBaseUrl,
          backendStatus: hotPathMeta.backendStatus || "degraded",
          foregroundBackendSource: hotPathMeta.foregroundBackendSource || "fallback-none",
          durationMs: Number(hotPathMeta.durationMs || 0),
          requestCount: Number(hotPathMeta.requestCount || 0),
          splitRetryCount: Number(hotPathMeta.splitRetryCount || 0),
          skippedChunkCount: Number(hotPathMeta.skippedChunkCount || 0),
          failedTextCount: Number(hotPathMeta.failedTextCount || 0),
          chunkSize: Number(hotPathMeta.chunkSize || 0),
          requestTimeoutMs: Number(hotPathMeta.requestTimeoutMs || 0),
          lastBackendErrorCode: String(hotPathMeta.lastBackendErrorCode || ""),
          backendQueueWaitMs: Number(hotPathMeta.backendQueueWaitMs || 0),
          backendQueueDepthAtEnqueue: Number(hotPathMeta.backendQueueDepthAtEnqueue || 0),
          backendRequestTimings: Array.isArray(hotPathMeta.backendRequestTimings)
            ? hotPathMeta.backendRequestTimings.slice(-8)
            : [],
          batchSize: analysisUnits.length,
          items: analysisUnits.slice(0, 4).map((unit) => ({
            text: truncateDiagnosticText(unit?.text, 180),
            memberCount: Array.isArray(unit?.members) ? unit.members.length : 0
          }))
        }
      };

      if (shouldPersistHotPathFailure(runReason)) {
        await persistFailure(hotPathMeta.error, failureStats);
      } else {
        scheduleHotPathStatsPersist({
          ...failureStats,
          lastDecisionSource: "backend-foreground-transient-failed",
          lastForegroundDiagnostics: failureStats.lastForegroundDiagnostics
        });
      }
      return {
        ok: false,
        reason: hotPathMeta.error?.reason,
        errorCode: hotPathMeta.error?.errorCode,
        retryable: hotPathMeta.error?.retryable
      };
    }

    const decisionBuildStartedAt = performance.now();
    const decision = buildDecisionFromBackend(
      analysisUnits,
      hotPathMeta.results,
      settings,
      {
        apiBaseUrl: hotPathMeta.apiBaseUrl || settings.backendApiBaseUrl,
        backendDurationMs: Number(hotPathMeta.durationMs || 0),
        backendStatus: hotPathMeta.backendStatus || "ready"
      }
    );
    const decisionBuiltAt = performance.now();
    recordWellbeingDetectionFromDecision(decision, "backend-foreground", pipelineSequence);

    if (Number(decision.maskedSpanCount || 0) > 0 && !firstMaskLatencyMs) {
      firstMaskLatencyMs = Math.round(performance.now() - startedAt);
    }

    suppressMutationFeedback(220);
    const maskApplyStartedAt = performance.now();
    const applySummary = applyDecision(unitCandidates, decision, settings, {
      generation: analysisGeneration,
      stage: "foreground",
      settingsRevision: activeSettingsRevision
    });
    const maskAppliedAt = performance.now();
    markCandidatesSettled(
      collectSettledCandidatesFromAnalysisUnits(analysisUnits, hotPathMeta.results),
      analysisGeneration
    );
    markSkippedCandidatesForRetryBackoff(analysisUnits, hotPathMeta.results, analysisGeneration);

    if (contextualReconcileCandidates.length > 0) {
      enqueueReconcileCandidates(
        contextualReconcileCandidates,
        pipelineSequence,
        {
          hostname,
          runReason,
          startedAt,
          analysisGeneration,
          settingsRevision: activeSettingsRevision,
          enabled: settings.enabled !== false
        },
        {
          delayMs:
            Number(decision.blockedNodeCount || 0) > 0
              ? RECONCILE_FAST_FLUSH_DELAY_MS
              : RECONCILE_FLUSH_DELAY_MS
        }
      );
    }

    const backendInternalTimingSummary = summarizeAnalysisTimingResults(hotPathMeta.results);
    const phaseTimings = {
      settingsLoadMs: Math.round(settingsLoadedAt - startedAt),
      candidateCollectMs: Math.round(candidatesCollectedAt - candidateCollectStartedAt),
      dirtySelectMs: Math.round(dirtySelectedAt - dirtySelectStartedAt),
      prioritizeMs: Math.round(prioritizedAt - prioritizeStartedAt),
      foregroundSelectMs: Math.round(foregroundSelectedAt - foregroundSelectStartedAt),
      parserMs: foregroundUnitBuildMs,
      preBackendMs: Math.round(preBackendCompletedAt - startedAt),
      localPreflightMs: Math.round(localPreflightCompletedAt - preBackendCompletedAt),
      backendRoundTripMs: Math.round(backendCompletedAt - backendStartedAt),
      backendReportedMs: Number(hotPathMeta.durationMs || 0),
      decisionBuildMs: Math.round(decisionBuiltAt - decisionBuildStartedAt),
      maskApplyMs: Math.round(maskAppliedAt - maskApplyStartedAt),
      postBackendToMaskMs: Math.round(maskAppliedAt - backendCompletedAt),
      totalToMaskMs: Math.round(maskAppliedAt - startedAt)
    };

    stats = {
      hostname,
      analyzedNodeCount: decision.analyzedNodeCount,
      blockedNodeCount: decision.blockedNodeCount,
      matchedKeywordCount: decision.matchedKeywords.length,
      maskedSpanCount: Number(decision.maskedSpanCount || 0),
      localPreflightMaskedSpanCount: Number(localPreflight.decision?.maskedSpanCount || 0),
      preservedHighSignalMaskCount: Number(applySummary?.preservedHighSignalMaskCount || 0),
      effectiveMaskedSpanCount:
        Number(decision.maskedSpanCount || 0) +
        Number(applySummary?.preservedHighSignalMaskCount || 0),
      durationMs: Math.round(performance.now() - startedAt),
      firstMaskLatencyMs,
      runReason,
      enabled: true,
      backendEndpoint: hotPathMeta.apiBaseUrl || settings.backendApiBaseUrl,
      backendStatus: hotPathMeta.backendStatus || "ready",
      backendDurationMs: Number(hotPathMeta.durationMs || 0),
      backendCacheHitCount: Number(hotPathMeta.backendCacheHitCount || 0),
      backendReconcileLatencyMs: 0,
      cacheHitCount: Number(hotPathMeta.cacheHitCount || 0),
      foregroundRequestCount: Number(hotPathMeta.requestCount || 0),
      foregroundSplitRetryCount: Number(hotPathMeta.splitRetryCount || 0),
      foregroundSkippedChunkCount: Number(hotPathMeta.skippedChunkCount || 0),
      foregroundFailedTextCount: Number(hotPathMeta.failedTextCount || 0),
      foregroundRequestTimeoutMs: Number(hotPathMeta.requestTimeoutMs || 0),
      foregroundLastBackendErrorCode: String(hotPathMeta.lastBackendErrorCode || ""),
      foregroundBackendQueueWaitMs: Number(hotPathMeta.backendQueueWaitMs || 0),
      foregroundBackendQueueDepth: Number(hotPathMeta.backendQueueDepthAtEnqueue || 0),
      reconcileRequestCount: contextualReconcileCandidates.length > 0 ? 1 : 0,
      foregroundUnitBuildMs,
      phaseTimings,
      backendInternalTimingSummary,
      firstPaintMaskMs: firstMaskLatencyMs,
      hotPathLatencyMs: Number(hotPathMeta.durationMs || 0),
      foregroundBackendLatencyMs: Number(hotPathMeta.durationMs || 0),
      foregroundBackendSource: hotPathMeta.foregroundBackendSource || "",
      requestedAnalysisCount: Number(hotPathMeta.requestedCount || 0),
      reconcileQueueDepth: RECONCILE_QUEUE.size,
      reconcileSkippedCount: 0,
      totalCandidateCount: candidates.length,
      droppedCandidateCount,
      pipelineSequence,
      visibleContainerBatchSize: analysisUnits.length,
      foregroundCandidateCount: unitCandidates.length,
      contextualCandidateCount: contextualReconcileCandidates.length,
      remainingPrioritizedCandidateCount,
      workerCacheHitCount: Number(hotPathMeta.cacheHitCount || 0),
      returnedSpanCount: Number(decision.returnedSpanCount || 0),
      droppedSpanCount: Number(decision.droppedSpanCount || 0),
      preconcealCount,
      sensitivityMode: getSensitivityMode(settings),
      lastDecisionSource: "backend-foreground",
      lastForegroundDiagnostics: buildAnalysisDiagnostics(
        analysisUnits,
        hotPathMeta.results,
        {
          decisionSource: "backend-foreground",
          apiBaseUrl: hotPathMeta.apiBaseUrl || settings.backendApiBaseUrl,
          backendStatus: hotPathMeta.backendStatus || "ready",
          foregroundBackendSource: hotPathMeta.foregroundBackendSource || "",
          requestedTextCount: Number(hotPathMeta.requestedCount || 0),
          requestCount: Number(hotPathMeta.requestCount || 0),
          splitRetryCount: Number(hotPathMeta.splitRetryCount || 0),
          skippedChunkCount: Number(hotPathMeta.skippedChunkCount || 0),
          failedTextCount: Number(hotPathMeta.failedTextCount || 0),
          chunkSize: Number(hotPathMeta.chunkSize || 0),
          requestTimeoutMs: Number(hotPathMeta.requestTimeoutMs || 0),
          lastBackendErrorCode: String(hotPathMeta.lastBackendErrorCode || ""),
          backendQueueWaitMs: Number(hotPathMeta.backendQueueWaitMs || 0),
          backendQueueDepthAtEnqueue: Number(hotPathMeta.backendQueueDepthAtEnqueue || 0),
          backendRequestTimings: Array.isArray(hotPathMeta.backendRequestTimings)
            ? hotPathMeta.backendRequestTimings
            : [],
          backendInternalTimingSummary,
          cacheHitCount: Number(hotPathMeta.cacheHitCount || 0),
          backendCacheHitCount: Number(hotPathMeta.backendCacheHitCount || 0),
          durationMs: Number(hotPathMeta.durationMs || 0),
          returnedSpanCount: Number(decision.returnedSpanCount || 0),
          appliedSpanCount: Number(decision.maskedSpanCount || 0),
          preservedHighSignalMaskCount: Number(applySummary?.preservedHighSignalMaskCount || 0),
          effectiveAppliedSpanCount:
            Number(decision.maskedSpanCount || 0) +
            Number(applySummary?.preservedHighSignalMaskCount || 0),
          droppedSpanCount: Number(decision.droppedSpanCount || 0)
        }
      )
    };

    maybeActivatePerformanceGuard(stats, runReason);
    await persistDebug(payload, decision, stats);

    if (
      shouldScheduleBackgroundValidation(runReason) &&
      remainingPrioritizedCandidateCount > 0 &&
      !queuedReason
    ) {
      queuedReason = "background-validation";
    }

    return { ok: true, stats };
  } catch (error) {
    const failure = serializeFailure(error?.message || error, error?.errorCode, error?.retryable);
    const failureStats = {
      hostname: location.hostname || "unknown",
      analyzedNodeCount: 0,
      blockedNodeCount: 0,
      matchedKeywordCount: 0,
      durationMs: Math.round(performance.now() - startedAt),
      runReason,
      enabled: true,
      backendEndpoint: "",
      backendStatus: "degraded"
    };

    if (shouldPersistHotPathFailure(runReason)) {
      await persistFailure(failure, failureStats);
    } else {
      scheduleHotPathStatsPersist({
        ...failureStats,
        lastDecisionSource: "backend-transient-failed",
        hotPathErrorCode: failure.errorCode,
        hotPathStatus: "degraded"
      });
    }
    return {
      ok: false,
      reason: failure.reason,
      errorCode: failure.errorCode,
      retryable: failure.retryable
    };
  } finally {
    isPipelineRunning = false;

    if (queuedReason) {
      const scheduledReason = queuedReason;
      queuedReason = null;
      schedulePipeline(scheduledReason);
    }
  }
}

function getPipelineScheduleDelayMs(reason) {
  if (reason === "input") {
    return INPUT_PIPELINE_DEBOUNCE_MS;
  }

  if (
    reason === "visibility" ||
    reason === "mutation" ||
    reason === "route-change" ||
    reason === "google-dynamic-content"
  ) {
    return VISIBILITY_PIPELINE_DEBOUNCE_MS;
  }

  if (reason === "background-validation") {
    return BACKGROUND_PIPELINE_DEBOUNCE_MS;
  }

  return PIPELINE_DEBOUNCE_MS;
}

function getPipelineReasonPriority(reason) {
  const normalizedReason = String(reason || "");
  if (normalizedReason === "input" || normalizedReason === "input-hot-path") {
    return 6;
  }

  if (
    normalizedReason === "visibility" ||
    normalizedReason === "mutation" ||
    normalizedReason === "route-change" ||
    normalizedReason === "google-dynamic-content"
  ) {
    return 5;
  }

  if (
    normalizedReason === "initial-load" ||
    normalizedReason === "settings-updated" ||
    normalizedReason === "manual" ||
    normalizedReason === "manual-request" ||
    normalizedReason === "manual-request-after-inject"
  ) {
    return 4;
  }

  if (normalizedReason === "background-validation") {
    return 1;
  }

  return 2;
}

function chooseHigherPriorityPipelineReason(currentReason, nextReason) {
  if (!currentReason) {
    return nextReason;
  }

  return getPipelineReasonPriority(nextReason) > getPipelineReasonPriority(currentReason)
    ? nextReason
    : currentReason;
}

function handleScheduledPipelineError(reason, error) {
  if (handleExtensionContextError(error)) {
    return;
  }

  console.error("[청마루] scheduled pipeline failed", {
    reason: String(reason || ""),
    error: serializeFailureReason(error)
  });
}

function schedulePipeline(reason) {
  if (extensionContextInvalidated || isUnsupportedPage()) return;
  if (shouldSuppressPipelineForGoogleSearch(reason)) return;
  if (shouldSuppressPipelineForPerformanceGuard(reason)) return;

  const delay = getPipelineScheduleDelayMs(reason);
  const deadlineMs = performance.now() + delay;

  if (debounceTimerId) {
    const currentPriority = getPipelineReasonPriority(scheduledPipelineReason);
    const nextPriority = getPipelineReasonPriority(reason);
    const existingDeadlineMs = Number(scheduledPipelineDeadlineMs || 0);
    const keepExisting =
      currentPriority > nextPriority ||
      (currentPriority === nextPriority && existingDeadlineMs > 0 && existingDeadlineMs <= deadlineMs);

    if (keepExisting) {
      return;
    }

    window.clearTimeout(debounceTimerId);
  }

  scheduledPipelineReason = reason;
  scheduledPipelineDeadlineMs = deadlineMs;
  debounceTimerId = window.setTimeout(() => {
    debounceTimerId = null;
    scheduledPipelineReason = "";
    scheduledPipelineDeadlineMs = 0;
    executePipeline(reason).catch((error) => {
      handleScheduledPipelineError(reason, error);
    });
  }, delay);
}

function cancelScheduledPipeline() {
  if (debounceTimerId) {
    window.clearTimeout(debounceTimerId);
    debounceTimerId = null;
  }
  scheduledPipelineReason = "";
  scheduledPipelineDeadlineMs = 0;
  queuedReason = null;
}

function scheduleIdleStartupTask(callback) {
  if (typeof callback !== "function") {
    return;
  }

  if ("requestIdleCallback" in window) {
    window.requestIdleCallback(callback, { timeout: INITIAL_ANALYSIS_IDLE_TIMEOUT_MS });
    return;
  }

  window.setTimeout(callback, INITIAL_ANALYSIS_IDLE_TIMEOUT_MS);
}

function scheduleStartupFollowupPipelines() {
  clearStartupFollowupPipelines();
  if (isGoogleSearchPage()) {
    return;
  }

  for (const delayMs of STARTUP_FOLLOWUP_DELAYS_MS) {
    const timeoutId = window.setTimeout(() => {
      STARTUP_FOLLOWUP_TIMEOUT_IDS.delete(timeoutId);
      if (extensionContextInvalidated || isUnsupportedPage()) return;
      const registeredCount = refreshVisibleCandidateRegistrations({
        markDirty: false,
        markHighSignalDirty: true,
        highSignalDirtyLimit: 32
      });
      if (registeredCount > 0) {
        schedulePipeline("visibility");
      }
    }, delayMs);
    STARTUP_FOLLOWUP_TIMEOUT_IDS.add(timeoutId);
  }
}

function clearStartupFollowupPipelines() {
  for (const timeoutId of STARTUP_FOLLOWUP_TIMEOUT_IDS) {
    window.clearTimeout(timeoutId);
  }
  STARTUP_FOLLOWUP_TIMEOUT_IDS.clear();
}

function scheduleBackendWarmup(options = {}) {
  if (backendWarmupStarted || extensionContextInvalidated || isUnsupportedDocumentTarget()) {
    return;
  }
  backendWarmupStarted = true;

  const runWarmup = async () => {
    try {
      const settings = await loadSettings();
      if (!settings.enabled || settings.backendEnabled !== true) {
        return;
      }

      await safeRuntimeSendMessage({
        type: "WARMUP_BACKEND_MODELS",
        fallbackTexts: BACKEND_WARMUP_FALLBACK_TEXTS.slice(0, 2),
        requestTimeoutMsOverride: BACKEND_WARMUP_REQUEST_TIMEOUT_MS,
        sensitivity: settings.sensitivity,
        analysisMode: "background-validation"
      });
    } catch (error) {
      if (!handleExtensionContextError(error)) {
        scheduleHotPathStatsPersist({
          hotPathStatus: "degraded",
          hotPathErrorCode: "BACKEND_WARMUP_FAILED"
        });
      }
    }
  };

  window.setTimeout(() => {
    if (!options.immediate && "requestIdleCallback" in window) {
      window.requestIdleCallback(runWarmup, { timeout: 1000 });
      return;
    }
    runWarmup();
  }, options.immediate ? 0 : BACKEND_WARMUP_DELAY_MS);
}

function invalidatePendingAnalysisForNavigation() {
  latestAnalysisGeneration += 1;
  latestPipelineSequence += 1;
  suppressMutationFeedback(180);
  clearStartupFollowupPipelines();

  for (const state of NODE_STATE_BY_ID.values()) {
    state.analysisGeneration = latestAnalysisGeneration;
    state.hasProcessed = false;
    state.lastFingerprint = "";
    state.lastSkippedAnalysisAt = 0;
    state.lastSkippedFingerprint = "";
    state.lastAppliedStage = "";
    state.lastQueuedReconcileFingerprint = "";
    state.reconcileInFlightFingerprint = "";
    if (state.nodeId) {
      DIRTY_NODE_IDS.add(state.nodeId);
    }
  }

  for (const state of EDITABLE_VALUE_STATE_BY_ID.values()) {
    state.analysisGeneration = latestAnalysisGeneration;
    state.hasProcessed = false;
    state.lastFingerprint = "";
    state.lastSkippedAnalysisAt = 0;
    state.lastSkippedFingerprint = "";
    state.lastAppliedStage = "";
    state.lastQueuedReconcileFingerprint = "";
    state.reconcileInFlightFingerprint = "";
    if (state.nodeId) {
      DIRTY_NODE_IDS.add(state.nodeId);
    }
  }

  RECONCILE_QUEUE.clear();
  if (reconcileFlushTimerId) {
    window.clearTimeout(reconcileFlushTimerId);
    reconcileFlushTimerId = null;
  }
  scheduledReconcileDelayMs = 0;
}

function refreshCurrentRouteCandidates(options = {}) {
  if (extensionContextInvalidated || isUnsupportedPage() || !document.body) {
    return 0;
  }

  if (isGoogleSearchPage()) {
    cleanupDisconnectedStates();
    scheduleGoogleSearchLightModeProtection(cachedSettings, {
      limit: MAX_DOMAIN_PRIORITY_CANDIDATES,
      force: options.force === true
    });
    return 0;
  }

  scheduleSearchResultProtection(cachedSettings);
  cleanupDisconnectedStates();
  const markDirty = options.markDirty === true;
  const registeredCount = refreshVisibleCandidateRegistrations({
    markDirty,
    markHighSignalDirty: options.markHighSignalDirty === true,
    highSignalDirtyLimit: options.highSignalDirtyLimit
  });
  applyCachedLocalPreflightForVisiblePage({
    limit: Math.min(18, Math.max(8, options.highSignalDirtyLimit || 12)),
    startedAt: performance.now()
  });
  scheduleInitialEditablePass();
  if (options.scheduleStartupFollowups !== false) {
    scheduleStartupFollowupPipelines();
  }
  return registeredCount;
}

function runRouteRefreshWave(sequence, options = {}) {
  if (sequence !== routeRefreshSequence) {
    return;
  }

  if (isGoogleSearchPage()) {
    refreshCurrentRouteCandidates({
      scheduleStartupFollowups: false,
      markDirty: options.markDirty === true,
      highSignalDirtyLimit: 0
    });
    return;
  }

  const registeredCount = refreshCurrentRouteCandidates({
    scheduleStartupFollowups: false,
    markDirty: options.markDirty === true,
    markHighSignalDirty: true,
    highSignalDirtyLimit: 18
  });
  if (registeredCount > 0) {
    schedulePipeline("route-change");
  } else {
    scheduleScrollVisibilityRefresh();
  }
}

function clearRouteRefreshFollowups() {
  for (const timeoutId of ROUTE_REFRESH_TIMEOUT_IDS) {
    window.clearTimeout(timeoutId);
  }
  ROUTE_REFRESH_TIMEOUT_IDS.clear();
}

function scheduleRouteRefreshFollowups(sequence) {
  clearRouteRefreshFollowups();

  for (const delayMs of ROUTE_CHANGE_FOLLOWUP_DELAYS_MS) {
    const timeoutId = window.setTimeout(() => {
      ROUTE_REFRESH_TIMEOUT_IDS.delete(timeoutId);
      runRouteRefreshWave(sequence, { markDirty: false });
    }, delayMs);
    ROUTE_REFRESH_TIMEOUT_IDS.add(timeoutId);
  }
}

function scheduleRouteRefresh(reason = "route-change") {
  if (extensionContextInvalidated || isUnsupportedPage()) {
    return;
  }

  const currentHref = String(location.href || "");
  const normalizedReason = String(reason || "");
  const isActualRouteChange = currentHref !== lastObservedLocationHref;
  const allowSameRouteRefresh = SAME_ROUTE_DIRTY_REFRESH_REASONS.has(normalizedReason);
  if (!isActualRouteChange && !allowSameRouteRefresh) {
    return;
  }

  if (isActualRouteChange) {
    lastObservedLocationHref = currentHref;
    invalidatePendingAnalysisForNavigation();
  }
  const sequence = ++routeRefreshSequence;

  if (routeRefreshFrameId) {
    window.cancelAnimationFrame(routeRefreshFrameId);
  }

  routeRefreshFrameId = window.requestAnimationFrame(() => {
    routeRefreshFrameId = null;
    runRouteRefreshWave(sequence, { markDirty: isActualRouteChange || allowSameRouteRefresh });
    scheduleRouteRefreshFollowups(sequence);
  });
}

function initializeNavigationListeners() {
  if (navigationListenersInitialized) {
    return;
  }
  navigationListenersInitialized = true;

  const wrapHistoryMethod = (methodName) => {
    const original = history?.[methodName];
    if (typeof original !== "function") {
      return;
    }

    history[methodName] = function patchedHistoryMethod(...args) {
      const result = original.apply(this, args);
      window.setTimeout(() => scheduleRouteRefresh(methodName), 0);
      return result;
    };
  };

  try {
    wrapHistoryMethod("pushState");
    wrapHistoryMethod("replaceState");
  } catch {
    // Some pages expose non-writable history methods in the isolated world.
  }

  window.addEventListener("popstate", () => scheduleRouteRefresh("popstate"), true);
  window.addEventListener("hashchange", () => scheduleRouteRefresh("hashchange"), true);
  window.addEventListener("pageshow", () => scheduleRouteRefresh("pageshow"), true);
  window.addEventListener("load", () => scheduleRouteRefresh("load"), true);
  document.addEventListener(
    "readystatechange",
    () => {
      if (document.readyState === "interactive" || document.readyState === "complete") {
        scheduleRouteRefresh("readystatechange");
      }
    },
    true
  );
  document.addEventListener("turbo:load", () => scheduleRouteRefresh("turbo-load"), true);
  document.addEventListener("yt-page-data-updated", () => scheduleRouteRefresh("yt-page-data-updated"), true);
  document.addEventListener("yt-navigate-start", () => scheduleRouteRefresh("yt-navigate-start"), true);
  document.addEventListener("yt-navigate-finish", () => scheduleRouteRefresh("yt-navigate-finish"), true);
  try {
    if (window.navigation?.addEventListener) {
      window.navigation.addEventListener(
        "currententrychange",
        () => scheduleRouteRefresh("navigation-api"),
        true
      );
      window.navigation.addEventListener(
        "navigate",
        () => window.setTimeout(() => scheduleRouteRefresh("navigation-api"), 0),
        true
      );
    }
  } catch {
    // Navigation API is optional and can be blocked by the page.
  }
  document.addEventListener(
    "visibilitychange",
    () => {
      if (document.visibilityState === "visible") {
        scheduleRouteRefresh("pageshow");
      }
    },
    true
  );

  navigationPollTimerId = window.setInterval(() => {
    if (extensionContextInvalidated || isUnsupportedPage()) {
      return;
    }
    if (document.visibilityState !== "visible") {
      return;
    }

    if (String(location.href || "") !== lastObservedLocationHref) {
      scheduleRouteRefresh("location-poll");
    }
  }, NAVIGATION_POLL_INTERVAL_MS);
}

function refreshVisibleCandidateRegistrations(options = {}) {
  let registeredCount = 0;
  const markDirty = options.markDirty === true;
  const markHighSignalDirty = options.markHighSignalDirty === true;
  const highSignalDirtyLimit = Number.isFinite(options.highSignalDirtyLimit)
    ? Number(options.highSignalDirtyLimit)
    : undefined;

  if (isPerformanceGuardActive()) {
    return 0;
  }

  if (isGoogleImageSearchPage()) {
    return 0;
  }

  if (isGoogleSearchPage()) {
    return 0;
  }

  if (isYouTubePage()) {
    const containers = getYouTubeVisibleAnalysisContainers(MAX_BACKGROUND_CONTAINERS);
    for (const container of containers) {
      registeredCount += registerTextNodesInTree(container, {
        markDirty,
        markHighSignalDirty,
        highSignalDirtyLimit,
        onlyVisible: true,
        limit: MAX_GOOGLE_CANDIDATES_PER_CONTAINER
      });
    }
    if (registeredCount > 0) {
      return registeredCount;
    }
  }

  return registerTextNodesInTree(document.body, {
    markDirty,
    markHighSignalDirty,
    highSignalDirtyLimit,
    onlyVisible: true,
    limit: SCROLL_REFRESH_TEXT_NODE_LIMIT
  });
}

function runScrollVisibilityRefresh() {
  if (isGoogleSearchPage() || isPerformanceGuardActive()) {
    return;
  }

  if (scrollVisibilityRefreshFrameId) {
    return;
  }

  scrollVisibilityRefreshFrameId = window.requestAnimationFrame(() => {
    scrollVisibilityRefreshFrameId = null;
    if (extensionContextInvalidated || isUnsupportedPage()) {
      return;
    }

    const registeredCount = refreshVisibleCandidateRegistrations({
      markHighSignalDirty: true,
      highSignalDirtyLimit: 32
    });
    if (registeredCount > 0) {
      applyCachedLocalPreflightForVisiblePage({
        limit: 12,
        startedAt: performance.now()
      });
      schedulePipeline("visibility");
    }
  });
}

function scheduleScrollVisibilityRefresh(options = {}) {
  if (isGoogleSearchPage() || isPerformanceGuardActive()) {
    return;
  }

  runScrollVisibilityRefresh();

  if (options.withSettleRefresh === false) {
    return;
  }

  if (scrollVisibilityRefreshSettleTimerId) {
    window.clearTimeout(scrollVisibilityRefreshSettleTimerId);
  }

  scrollVisibilityRefreshSettleTimerId = window.setTimeout(() => {
    scrollVisibilityRefreshSettleTimerId = null;
    runScrollVisibilityRefresh();
  }, SCROLL_SETTLE_REFRESH_DELAY_MS);

  if (scrollVisibilityRefreshLateTimerId) {
    window.clearTimeout(scrollVisibilityRefreshLateTimerId);
  }

  scrollVisibilityRefreshLateTimerId = window.setTimeout(() => {
    scrollVisibilityRefreshLateTimerId = null;
    runScrollVisibilityRefresh();
  }, SCROLL_LATE_REFRESH_DELAY_MS);
}

function scheduleSuppressedMutationRefresh() {
  if (suppressedMutationRefreshTimerId || extensionContextInvalidated || isUnsupportedPage()) {
    return;
  }

  const delayMs = Math.max(16, Math.min(260, ignoreMutationsUntil - Date.now() + 24));
  suppressedMutationRefreshTimerId = window.setTimeout(() => {
    suppressedMutationRefreshTimerId = null;
    if (extensionContextInvalidated || isUnsupportedPage()) {
      return;
    }

    scheduleScrollVisibilityRefresh({ withSettleRefresh: false });
  }, delayMs);
}

function isGoogleDynamicAnalysisElement(element) {
  if (!(element instanceof Element)) {
    return false;
  }

  return Boolean(
    element.closest(GOOGLE_DYNAMIC_CONTENT_SELECTOR) ||
      element.querySelector(GOOGLE_DYNAMIC_CONTENT_SELECTOR)
  );
}

function hasGoogleDynamicAnalysisText(node) {
  if (node instanceof Text) {
    if (!isGoogleDynamicAnalysisElement(node.parentElement)) {
      return false;
    }
    return normalizeText(node.nodeValue || "").length >= 8;
  }

  if (node instanceof Element) {
    if (!isGoogleDynamicAnalysisElement(node)) {
      return false;
    }
    return normalizeText(node.textContent || node.getAttribute("aria-label") || "").length >= 8;
  }

  if (node instanceof DocumentFragment) {
    return [...node.childNodes].some((child) => hasGoogleDynamicAnalysisText(child));
  }

  return false;
}

function shouldScheduleGoogleDynamicContentPipeline(mutationList) {
  if (!isGoogleTextSearchAnalysisPage() || !Array.isArray(mutationList)) {
    return false;
  }

  for (const mutation of mutationList) {
    if (!mutation) continue;
    if (mutation.type === "characterData" && hasGoogleDynamicAnalysisText(mutation.target)) {
      return true;
    }

    for (const node of mutation.addedNodes || []) {
      if (hasGoogleDynamicAnalysisText(node)) {
        return true;
      }
    }
  }

  return false;
}

function markTextNodeDirty(textNode, options = {}) {
  if (!(textNode instanceof Text)) return false;
  const state = registerTextNode(textNode);
  if (!state) return false;
  const fingerprint = getCurrentStateFingerprint(state);
  if (!options.force && isStateSettledForFingerprint(state, fingerprint)) {
    return false;
  }
  const wasDirty = DIRTY_NODE_IDS.has(state.nodeId);
  DIRTY_NODE_IDS.add(state.nodeId);
  return !wasDirty;
}

function markDirtyFromTarget(target, options = {}) {
  if (isGoogleSearchPage() || isPerformanceGuardActive()) {
    return false;
  }

  const forceDirty = options.force === true;

  if (target instanceof Text) {
    if (shouldSkipTextNodeParent(target.parentElement)) return false;
    return markTextNodeDirty(target, { force: forceDirty });
  }

  if (!(target instanceof Element)) return false;
  if (shouldSkipTextNodeParent(target)) return false;
  const shouldInspectGoogleMutation =
    isGoogleTextSearchAnalysisPage() &&
    target !== document.body &&
    target !== document.documentElement &&
    isElementNearViewport(target.getBoundingClientRect());
  const isGoogleHighSignalMutation =
    shouldInspectGoogleMutation &&
    HIGH_SIGNAL_PROFANITY_PATTERN.test(normalizeText(getElementAnalysisText(target)));
  const registeredCount = registerTextNodesInTree(target, {
    markDirty: forceDirty,
    markHighSignalDirty: isGoogleHighSignalMutation,
    highSignalDirtyLimit: isGoogleHighSignalMutation ? 16 : undefined,
    onlyVisible: true,
    limit: MAX_DIRTY_TEXT_NODES_PER_MUTATION
  });
  const highSignalDirtyCount = isGoogleHighSignalMutation
    ? markGoogleHighSignalCandidatesDirty(12)
    : 0;
  return registeredCount > 0 || highSignalDirtyCount > 0;
}

function initializeVisibilityObserver() {
  if (visibilityObserver) {
    visibilityObserver.disconnect();
  }

  visibilityObserver = new IntersectionObserver(
    (entries) => {
      let shouldSchedule = false;

      entries.forEach((entry) => {
        const linkedNodeIds = OBSERVED_ELEMENT_NODE_IDS.get(entry.target);
        if (!linkedNodeIds) return;

        linkedNodeIds.forEach((nodeId) => {
          const wasVisible = VISIBLE_NODE_IDS.has(nodeId);
          if (entry.isIntersecting) {
            VISIBLE_NODE_IDS.add(nodeId);
            const state = NODE_STATE_BY_ID.get(nodeId) || EDITABLE_VALUE_STATE_BY_ID.get(nodeId);
            if (!wasVisible && shouldMarkStateDirtyForVisibility(state)) {
              DIRTY_NODE_IDS.add(nodeId);
              shouldSchedule = true;
            }
          } else {
            VISIBLE_NODE_IDS.delete(nodeId);
          }
        });
      });

      if (shouldSchedule) {
        schedulePipeline("visibility");
      }
    },
    {
      root: null,
      rootMargin: `${VIEWPORT_BUFFER_PX}px 0px ${VIEWPORT_BUFFER_PX}px 0px`,
      threshold: 0.01
    }
  );
}

function initializeObserver() {
  if (extensionContextInvalidated || !document.documentElement) return;
  if (observer) observer.disconnect();

  observer = new MutationObserver((mutationList) => {
    if (!mutationList || mutationList.length === 0) return;
    const pageMutations = mutationList.filter((mutation) => !isShieldTextManagedMutation(mutation));
    managedMutationSkipCount += mutationList.length - pageMutations.length;
    if (pageMutations.length === 0) return;

    if (isGoogleSearchPage()) {
      scheduleGoogleSearchLightModeProtection(cachedSettings, {
        limit: MAX_DOMAIN_PRIORITY_CANDIDATES
      });
      if (shouldScheduleGoogleDynamicContentPipeline(pageMutations)) {
        schedulePipeline("google-dynamic-content");
      }
      if (isGoogleTextSearchAnalysisPage()) {
        scheduleSearchResultProtection(cachedSettings);
      }
      return;
    }

    if (Date.now() < ignoreMutationsUntil) {
      scheduleSuppressedMutationRefresh();
      return;
    }
    let shouldSchedule = false;
    let sawAddedContent = false;

    pageMutations.forEach((mutation) => {
      if (mutation.type === "characterData") {
        shouldSchedule = markDirtyFromTarget(mutation.target, { force: true }) || shouldSchedule;
        return;
      }

      mutation.addedNodes.forEach((node) => {
        if (node instanceof Text || node instanceof Element || node instanceof DocumentFragment) {
          sawAddedContent = true;
        }
        shouldSchedule = markDirtyFromTarget(node, { force: true }) || shouldSchedule;
      });
    });

    if (shouldSchedule) {
      schedulePipeline("mutation");
    }

    if (sawAddedContent && !shouldSchedule) {
      scheduleScrollVisibilityRefresh({ withSettleRefresh: false });
    }
    if (sawAddedContent && isGoogleTextSearchAnalysisPage()) {
      scheduleSearchResultProtection(cachedSettings);
    }
  });

  observer.observe(document.documentElement, {
    childList: true,
    subtree: true,
    characterData: true
  });
}

function initializeInputListeners() {
  document.addEventListener(
    "input",
    (event) => {
      const target = event.target;
      if (!(target instanceof HTMLInputElement) && !(target instanceof HTMLTextAreaElement)) {
        return;
      }

      const candidate = buildEditableValueCandidate(target);
      if (!candidate) {
        const existingId = EDITABLE_VALUE_ID_MAP.get(target);
        const state = existingId ? EDITABLE_VALUE_STATE_BY_ID.get(existingId) : null;
        if (state) {
          restoreEditableValueState(state);
        }
        return;
      }

      clearStaleEditableMaskForElement(target);
      applyCachedLocalPreflightForCandidates([candidate], {
        startedAt: performance.now()
      });
      pendingImmediateInputElement = target;
      DIRTY_NODE_IDS.add(candidate.nodeId);
      scheduleImmediateInputPipeline(target, "input-hot-path");
    },
    true
  );

  document.addEventListener(
    "compositionend",
    (event) => {
      const target = event.target;
      if (!(target instanceof HTMLInputElement) && !(target instanceof HTMLTextAreaElement)) {
        return;
      }

      clearStaleEditableMaskForElement(target);
      const candidate = buildEditableValueCandidate(target);
      if (candidate) {
        applyCachedLocalPreflightForCandidates([candidate], {
          startedAt: performance.now()
        });
        DIRTY_NODE_IDS.add(candidate.nodeId);
      }
      pendingImmediateInputElement = target;
      scheduleImmediateInputPipeline(target, "input-hot-path");
    },
    true
  );

  document.addEventListener(
    "scroll",
    (event) => {
      const target = event.target;
      if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) {
        scheduleEditableOverlaySync();
      }
    },
    true
  );

  document.addEventListener(
    "selectionchange",
    () => {
      const activeElement = document.activeElement;
      if (activeElement instanceof HTMLInputElement || activeElement instanceof HTMLTextAreaElement) {
        const activeId = EDITABLE_VALUE_ID_MAP.get(activeElement);
        const activeState = activeId ? EDITABLE_VALUE_STATE_BY_ID.get(activeId) : null;
        if (activeState?.isMasked) {
          scheduleEditableOverlaySync();
        }
      }
    },
    true
  );
}

function initializeViewportListeners() {
  const syncOverlays = () => {
    scheduleEditableOverlaySync();
  };

  window.addEventListener(
    "scroll",
    () => {
      syncOverlays();
      scheduleScrollVisibilityRefresh();
    },
    true
  );

  window.addEventListener("resize", () => {
    syncOverlays();
    scheduleScrollVisibilityRefresh();
    schedulePipeline("visibility");
  });

  if (window.visualViewport) {
    window.visualViewport.addEventListener(
      "scroll",
      () => {
        syncOverlays();
        scheduleScrollVisibilityRefresh();
      },
      { passive: true }
    );
    window.visualViewport.addEventListener(
      "resize",
      () => {
        syncOverlays();
        scheduleScrollVisibilityRefresh();
      },
      { passive: true }
    );
  }
}

// self-test helpers are loaded from content-self-test.js

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (!isExtensionContextAvailable()) {
    sendResponse({ ok: false, reason: "EXTENSION_CONTEXT_INVALIDATED", errorCode: "EXTENSION_CONTEXT_INVALIDATED" });
    return false;
  }

  if (message?.type === "APPLY_SETTINGS_SNAPSHOT") {
    const result = applySettingsSnapshot(message.settings || {}, "settings-updated");
    sendResponse(result);
    return false;
  }

  if (message?.type === "APPLY_SITE_POLICY") {
    if (message?.policy) {
      renderSitePolicyOverlay(message.policy);
    } else {
      removeSitePolicyOverlay();
    }
    sendResponse({ ok: true });
    return false;
  }

  if (message?.type === "RUN_GOOGLE_SEARCH_LIGHT_PROTECTION") {
    try {
      if (!isGoogleSearchPage()) {
        sendResponse({
          ok: false,
          reason: "NOT_GOOGLE_SEARCH_PAGE",
          href: String(location.href || "")
        });
        return false;
      }

      const lightProtection = applyGoogleSearchLightModeProtection(cachedSettings, {
        force: true,
        minIntervalMs: 0,
        limit: Number.isFinite(message.limit)
          ? Number(message.limit)
          : MAX_DOMAIN_PRIORITY_CANDIDATES
      });
      sendResponse({
        ok: true,
        maskedSpanCount: Number(lightProtection?.maskedSpanCount || 0),
        preconcealCount: Number(lightProtection?.preconcealCount || 0),
        href: String(location.href || "")
      });
    } catch (error) {
      sendResponse({
        ok: false,
        reason: serializeFailureReason(error),
        errorCode: String(error?.errorCode || "RUN_GOOGLE_SEARCH_LIGHT_PROTECTION_FAILED")
      });
    }
    return false;
  }

  if (message?.type === "RUN_PIPELINE" || message?.type === "RUN_FILTER") {
    executePipeline(message.reason || "manual")
      .then((result) => {
        sendResponse(result);
      })
      .catch((error) => {
        if (!handleExtensionContextError(error)) {
          handleScheduledPipelineError(message.reason || "manual", error);
        }
        sendResponse({
          ok: false,
          reason: serializeFailureReason(error),
          errorCode: String(error?.errorCode || "RUN_PIPELINE_FAILED"),
          retryable: Boolean(error?.retryable)
        });
      });
    return true;
  }

  if (message?.type === "RUN_SELF_TEST") {
    runFilterLabSelfTest()
      .then(sendResponse)
      .catch((error) => {
        sendResponse({
          ok: false,
          reason: serializeFailureReason(error),
          errorCode: String(error?.errorCode || "RUN_SELF_TEST_FAILED"),
          retryable: Boolean(error?.retryable)
        });
      });
    return true;
  }

  return false;
});

function applySettingsSnapshot(storedSettings, runReason = "settings-updated") {
  if (shouldSkipDuplicateSettingsSnapshot(storedSettings)) {
    return {
      ok: true,
      skipped: true,
      reason: "DUPLICATE_SETTINGS_SNAPSHOT"
    };
  }

  bumpSettingsRevision();
  const nextSettings = updateCachedSettings(storedSettings || {});
  invalidateAnalysisForSettingsChange();
  restoreAllRenderedContent();

  if (nextSettings.enabled === false) {
    removeSitePolicyOverlay();
    clearSearchResultProtection();
    cancelScheduledPipeline();
    scheduleHotPathStatsPersist({
      enabled: false,
      runReason,
      backendStatus: "disabled",
      sensitivityMode: "off",
      maskedSpanCount: 0,
      visibleContainerBatchSize: 0
    });
    return { ok: true, enabled: false, sensitivityMode: "off" };
  }

  if (isFilteringSuppressedBySensitivity(nextSettings)) {
    removeSitePolicyOverlay();
    clearSearchResultProtection();
    cancelScheduledPipeline();
    scheduleHotPathStatsPersist({
      enabled: true,
      runReason,
      backendStatus: "ready",
      sensitivityMode: getSensitivityMode(nextSettings),
      maskedSpanCount: 0,
      visibleContainerBatchSize: 0,
      lastDecisionSource: "sensitivity-disabled"
    });
    return {
      ok: true,
      enabled: true,
      sensitivityMode: getSensitivityMode(nextSettings),
      skipped: true,
      reason: "SENSITIVITY_DISABLED"
    };
  }

  requestCurrentSitePolicy().catch((error) => {
    handleExtensionContextError(error);
  });
  if (nextSettings.backendEnabled === true) {
    scheduleBackendWarmup({ immediate: isGoogleSearchPage() });
  }
  if (isGoogleSearchPage()) {
    scheduleGoogleSearchLightModeProtection(nextSettings, {
      limit: MAX_DOMAIN_PRIORITY_CANDIDATES,
      force: true
    });
    scheduleHotPathStatsPersist({
      enabled: true,
      runReason,
      backendStatus: "ready",
      sensitivityMode: getSensitivityMode(nextSettings),
      maskedSpanCount: 0,
      visibleContainerBatchSize: 0,
      lastDecisionSource: "google-search-light-mode"
    });
    return {
      ok: true,
      enabled: true,
      sensitivityMode: getSensitivityMode(nextSettings),
      skipped: true,
      reason: "GOOGLE_SEARCH_LIGHT_MODE"
    };
  }
  scheduleSearchResultProtection(nextSettings);
  scheduleInitialEditablePass();
  schedulePipeline(runReason);
  return {
    ok: true,
    enabled: true,
    sensitivityMode: getSensitivityMode(nextSettings)
  };
}

chrome.storage.onChanged.addListener((changes, areaName) => {
  if (areaName !== "sync") return;
  if (!changes?.settings) return;
  applySettingsSnapshot(changes.settings.newValue || {}, "settings-updated");
});

async function runInitialPageAnalysis(initialSettings, scheduledAt) {
  if (extensionContextInvalidated || isUnsupportedPage() || !document.body) {
    return;
  }

  const settings = await loadSettings().catch(() => initialSettings || getMergedSettings({}));
  if (settings.enabled === false || normalizeSensitivity(settings.sensitivity) <= 0) {
    removeSitePolicyOverlay();
    clearSearchResultProtection();
    initializeObserver();
    return;
  }

  if (settings.backendEnabled === true) {
    scheduleBackendWarmup();
  }

  if (isGoogleSearchPage()) {
    applyGoogleSearchLightModeProtection(settings, {
      limit: MAX_DOMAIN_PRIORITY_CANDIDATES,
      force: true
    });
    scheduleGoogleSearchLightModeProtection(settings, {
      limit: MAX_DOMAIN_PRIORITY_CANDIDATES,
      force: true
    });
    initializeObserver();
    return;
  }

  scheduleSearchResultProtection(settings);
  registerTextNodesInTree(document.body, {
    markDirty: true,
    onlyVisible: true,
    limit: MAX_INITIAL_TEXT_NODES
  });
  refreshVisibleCandidateRegistrations({
    markDirty: true,
    markHighSignalDirty: true,
    highSignalDirtyLimit: 18
  });
  scheduleInitialEditablePass();
  scheduleStartupFollowupPipelines();
  initializeObserver();
  scheduleHotPathStatsPersist({
    startupDeferredMs: Math.round(performance.now() - Number(scheduledAt || performance.now())),
    lastDecisionSource: "deferred-startup"
  });

  executePipeline("initial-load").catch((error) => {
    if (!handleExtensionContextError(error)) {
      console.error("[청마루] initial-load pipeline error", error);
    }
  });
}

function scheduleInitialPageAnalysis(initialSettings) {
  if (initialPageAnalysisScheduled || initialPageAnalysisStarted) {
    return;
  }

  initialPageAnalysisScheduled = true;
  const scheduledAt = performance.now();
  window.setTimeout(() => {
    initialPageAnalysisScheduled = false;
    if (initialPageAnalysisStarted) {
      return;
    }
    initialPageAnalysisStarted = true;
    runInitialPageAnalysis(initialSettings, scheduledAt).catch((error) => {
      if (!handleExtensionContextError(error)) {
        console.error("[청마루] initial page analysis failed", error);
      }
      initializeObserver();
    });
  });
}

async function bootstrap() {
  if (bootstrapStarted || extensionContextInvalidated || isUnsupportedPage()) return;
  if (!document.body || !document.documentElement) return;

  bootstrapStarted = true;
  const initialSettings = await loadSettings({ force: true }).catch(() => getMergedSettings({}));
  initializeVisibilityObserver();
  initializeSearchResultProtectionClickGuard();
  initializeInputListeners();
  initializeViewportListeners();
  initializeNavigationListeners();
  initializeLabSelfTestListeners();

  if (initialSettings.enabled === false || normalizeSensitivity(initialSettings.sensitivity) <= 0) {
    removeSitePolicyOverlay();
    clearSearchResultProtection();
    initializeObserver();
    return;
  }

  requestCurrentSitePolicy().catch((error) => {
    handleExtensionContextError(error);
  });
  if (initialSettings.backendEnabled === true) {
    scheduleBackendWarmup({ immediate: isGoogleSearchPage() });
  }

  if (isGoogleSearchPage()) {
    applyGoogleSearchLightModeProtection(initialSettings, {
      limit: MAX_DOMAIN_PRIORITY_CANDIDATES,
      force: true
    });
    scheduleGoogleSearchLightModeProtection(initialSettings, {
      limit: MAX_DOMAIN_PRIORITY_CANDIDATES,
      force: true
    });
    initializeObserver();
    return;
  }

  scheduleSearchResultProtection(initialSettings);
  scheduleInitialEditablePass();
  scheduleInitialPageAnalysis(initialSettings);
}

function scheduleBootstrapWhenReady() {
  if (bootstrapStarted || extensionContextInvalidated) return;

  if (document.body && document.documentElement) {
    bootstrap().catch((error) => {
      if (!handleExtensionContextError(error)) {
        console.error("[청마루] bootstrap error", error);
      }
    });
    return;
  }

  if (bootstrapRetryTimerId) {
    return;
  }

  bootstrapRetryTimerId = window.setTimeout(() => {
    bootstrapRetryTimerId = null;
    scheduleBootstrapWhenReady();
  }, 16);
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", () => {
    scheduleBootstrapWhenReady();
  });
  scheduleBootstrapWhenReady();
} else {
  scheduleBootstrapWhenReady();
}
