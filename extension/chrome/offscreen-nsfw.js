const NSFW_OFFSCREEN_TARGET = "chungmaru-nsfw-offscreen";
const NSFW_MODEL_VERSION = "nsfwjs-mobilenet-v2@v4.2.1";
const NSFW_MODEL_URL = chrome.runtime.getURL("vendor/nsfw/model/model.json");
const NSFW_WASM_ASSET_URLS = {
  "tfjs-backend-wasm.wasm": chrome.runtime.getURL("vendor/nsfw/wasm/tfjs-backend-wasm.wasm"),
  "tfjs-backend-wasm-simd.wasm": chrome.runtime.getURL("vendor/nsfw/wasm/tfjs-backend-wasm-simd.wasm"),
  "tfjs-backend-wasm-threaded-simd.wasm": chrome.runtime.getURL("vendor/nsfw/wasm/tfjs-backend-wasm-threaded-simd.wasm")
};
const NSFW_INPUT_SIZE = 224;
const NSFW_CLASS_NAMES = ["Drawing", "Hentai", "Neutral", "Porn", "Sexy"];
const NSFW_MAX_BATCH_SIZE = 4;
const NSFW_CACHE_LIMIT = 256;
const NSFW_CACHE_TTL_MS = 30 * 60 * 1000;
const NSFW_FETCH_TIMEOUT_MS = 1500;
const NSFW_WARM_BATCH_SIZE = 2;
const NSFW_MIN_REMAINING_INFERENCE_BUDGET_MS = 120;
const NSFW_MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const NSFW_MAX_BATCH_BYTES = 12 * 1024 * 1024;
const NSFW_MAX_DATA_URL_CHARS = 1024 * 1024;

let nsfwModel = null;
let nsfwModelPromise = null;
let nsfwBackend = "";
let nsfwForcedBackend = "";
let nsfwModelLoadCount = 0;
let nsfwModelLoadMs = 0;
let nsfwWarmupMs = 0;
let nsfwWarmBatchSize = 1;
let nsfwTestOverride = "normal";
let nsfwInferenceQueue = Promise.resolve();
const NSFW_PREDICTION_CACHE = new Map();

function createNsfwError(errorCode, message) {
  const error = new Error(message || errorCode);
  error.errorCode = errorCode;
  return error;
}

function serializeNsfwError(error, fallbackCode) {
  return {
    errorCode: String(error?.errorCode || fallbackCode || "NSFW_CLASSIFIER_FAILED").slice(0, 80),
    reason: String(error?.message || error || fallbackCode || "NSFW classifier failed").slice(0, 220)
  };
}

function getNsfwDeadlineEpochMs(message) {
  const value = Number(message?.deadlineEpochMs || 0);
  return Number.isFinite(value) && value > 0 ? value : 0;
}

function getNsfwRemainingBudgetMs(deadlineEpochMs) {
  if (!deadlineEpochMs) return Number.POSITIVE_INFINITY;
  return Math.max(0, Math.floor(deadlineEpochMs - Date.now()));
}

function assertNsfwDeadline(deadlineEpochMs, minimumRemainingMs = 1) {
  if (getNsfwRemainingBudgetMs(deadlineEpochMs) < minimumRemainingMs) {
    throw createNsfwError("NSFW_DEADLINE_EXCEEDED", "NSFW classifier response budget exceeded");
  }
}

function isLoopbackHost(hostname) {
  const host = String(hostname || "").toLowerCase();
  return host === "localhost" || host === "127.0.0.1" || host === "[::1]" || host === "::1";
}

function isPrivateAddressLiteral(hostname) {
  const host = String(hostname || "").toLowerCase().replace(/^\[|\]$/g, "");
  if (isLoopbackHost(host)) return true;
  if (/^10\./.test(host) || /^192\.168\./.test(host) || /^169\.254\./.test(host)) return true;
  const match = host.match(/^172\.(\d{1,3})\./);
  if (match && Number(match[1]) >= 16 && Number(match[1]) <= 31) return true;
  return host.endsWith(".local") || host === "0.0.0.0";
}

function normalizeNsfwSourceUrl(value, allowLoopback) {
  const raw = String(value || "").trim();
  if (!raw) {
    throw createNsfwError("NSFW_SOURCE_MISSING", "NSFW image source is missing");
  }
  if (/^data:image\//i.test(raw)) {
    if (raw.length > NSFW_MAX_DATA_URL_CHARS) {
      throw createNsfwError("NSFW_SOURCE_TOO_LARGE", "NSFW data URL exceeds the allowed size");
    }
    return raw;
  }
  let parsed;
  try {
    parsed = new URL(raw);
  } catch {
    throw createNsfwError("NSFW_SOURCE_INVALID", "NSFW image source URL is invalid");
  }
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw createNsfwError("NSFW_SOURCE_UNSUPPORTED", `Unsupported NSFW image scheme: ${parsed.protocol}`);
  }
  if (parsed.username || parsed.password) {
    throw createNsfwError("NSFW_SOURCE_CREDENTIALS", "Credential-bearing image URLs are not allowed");
  }
  if (isPrivateAddressLiteral(parsed.hostname) && !(allowLoopback && isLoopbackHost(parsed.hostname))) {
    throw createNsfwError("NSFW_SOURCE_PRIVATE", "Private image hosts are not allowed");
  }
  return parsed.href;
}

function getCachedNsfwPrediction(sourceUrl) {
  const cached = NSFW_PREDICTION_CACHE.get(sourceUrl);
  if (!cached) return null;
  if (Date.now() - cached.savedAt > NSFW_CACHE_TTL_MS) {
    NSFW_PREDICTION_CACHE.delete(sourceUrl);
    return null;
  }
  NSFW_PREDICTION_CACHE.delete(sourceUrl);
  NSFW_PREDICTION_CACHE.set(sourceUrl, cached);
  return cached.scores;
}

function cacheNsfwPrediction(sourceUrl, scores) {
  NSFW_PREDICTION_CACHE.delete(sourceUrl);
  NSFW_PREDICTION_CACHE.set(sourceUrl, { scores, savedAt: Date.now() });
  while (NSFW_PREDICTION_CACHE.size > NSFW_CACHE_LIMIT) {
    const firstKey = NSFW_PREDICTION_CACHE.keys().next().value;
    if (!firstKey) break;
    NSFW_PREDICTION_CACHE.delete(firstKey);
  }
}

function fixtureScoresForUrl(sourceUrl) {
  const value = String(sourceUrl || "").toLowerCase();
  if (value.includes("visual=explicit") || value.includes("visual-explicit")) {
    return { Drawing: 0.01, Hentai: 0.03, Neutral: 0.04, Porn: 0.86, Sexy: 0.06 };
  }
  if (value.includes("visual=contextual") || value.includes("visual-contextual")) {
    return { Drawing: 0.01, Hentai: 0.03, Neutral: 0.08, Porn: 0.05, Sexy: 0.83 };
  }
  return { Drawing: 0.03, Hentai: 0.01, Neutral: 0.93, Porn: 0.01, Sexy: 0.02 };
}

async function selectNsfwBackend() {
  if (!globalThis.tf) {
    throw createNsfwError("NSFW_TFJS_MISSING", "TensorFlow.js runtime is unavailable");
  }
  tf.enableProdMode();
  // WASM SIMD is CPU-only and avoids the extension competing with page WebGL.
  // The non-SIMD binary stays bundled as the runtime fallback for older CPUs.
  const preferred = nsfwForcedBackend || "wasm";
  try {
    if (preferred === "wasm") {
      if (typeof tf.wasm?.setWasmPaths !== "function") {
        throw createNsfwError("NSFW_WASM_RUNTIME_MISSING", "TensorFlow.js WASM runtime is unavailable");
      }
      tf.wasm.setWasmPaths(NSFW_WASM_ASSET_URLS);
    }
    const selected = await tf.setBackend(preferred);
    if (!selected) throw new Error(`backend rejected: ${preferred}`);
    await tf.ready();
  } catch (error) {
    if (preferred === "cpu") throw error;
    if (preferred === "wasm") {
      throw createNsfwError(
        "NSFW_WASM_UNAVAILABLE",
        `WASM classifier backend is unavailable: ${String(error?.message || error)}`
      );
    }
    throw createNsfwError(
      "NSFW_WEBGL_UNAVAILABLE",
      `WebGL backend is unavailable: ${String(error?.message || error)}`
    );
  }
  nsfwBackend = String(tf.getBackend() || "unknown");
}

async function warmNsfwShape(batchSize) {
  const startedAt = performance.now();
  const input = tf.zeros([batchSize, NSFW_INPUT_SIZE, NSFW_INPUT_SIZE, 3]);
  let output = null;
  try {
    output = nsfwModel.predict(input);
    const tensor = Array.isArray(output) ? output[0] : output;
    await tensor.data();
  } finally {
    input.dispose();
    if (Array.isArray(output)) {
      output.forEach((tensor) => tensor?.dispose?.());
    } else {
      output?.dispose?.();
    }
  }
  return Math.round(performance.now() - startedAt);
}

async function loadNsfwModelByFormat(modelUrl) {
  let format = "";
  try {
    const response = await fetch(modelUrl, { cache: "no-store" });
    if (!response.ok) {
      throw createNsfwError("NSFW_MODEL_MANIFEST_FETCH_FAILED", `Model manifest returned ${response.status}`);
    }
    format = String((await response.json())?.format || "").trim().toLowerCase();
  } catch (error) {
    if (error?.errorCode) throw error;
    throw createNsfwError("NSFW_MODEL_MANIFEST_FETCH_FAILED", String(error?.message || error));
  }

  if (format === "graph-model") {
    return tf.loadGraphModel(modelUrl);
  }
  return tf.loadLayersModel(modelUrl);
}

async function loadNsfwModel() {
  if (nsfwModel) return getNsfwStatus();
  if (nsfwModelPromise) return nsfwModelPromise;

  nsfwModelPromise = (async () => {
    const loadStartedAt = performance.now();
    await selectNsfwBackend();
    nsfwModel = await loadNsfwModelByFormat(NSFW_MODEL_URL);
    nsfwModelLoadMs = Math.round(performance.now() - loadStartedAt);
    nsfwModelLoadCount += 1;

    const warmupStartedAt = performance.now();
    await warmNsfwShape(1);
    try {
      await warmNsfwShape(NSFW_WARM_BATCH_SIZE);
      nsfwWarmBatchSize = NSFW_WARM_BATCH_SIZE;
    } catch {
      nsfwWarmBatchSize = 1;
    }
    nsfwWarmupMs = Math.round(performance.now() - warmupStartedAt);
    return getNsfwStatus();
  })().catch((error) => {
    nsfwModel?.dispose?.();
    nsfwModel = null;
    throw error;
  }).finally(() => {
    nsfwModelPromise = null;
  });

  return nsfwModelPromise;
}

function getNsfwStatus() {
  let memory = {};
  if (globalThis.tf && typeof tf.memory === "function") {
    try {
      memory = tf.memory();
    } catch {
      // A headless or GPU-restricted browser can reject backend initialization.
      // Status reporting must not make fixture/cheap-filter handling unavailable.
      memory = {};
    }
  }
  return {
    ok: Boolean(nsfwModel),
    status: nsfwModel ? "ready" : nsfwModelPromise ? "loading" : "idle",
    modelVersion: NSFW_MODEL_VERSION,
    backend: nsfwBackend || "",
    modelLoadMs: nsfwModelLoadMs,
    warmupMs: nsfwWarmupMs,
    warmBatchSize: nsfwWarmBatchSize,
    modelLoadCount: nsfwModelLoadCount,
    tensorCount: Math.max(0, Number(memory.numTensors || 0)),
    tensorBytes: Math.max(0, Number(memory.numBytes || 0)),
    cacheSize: NSFW_PREDICTION_CACHE.size,
    testOverride: nsfwTestOverride
  };
}

async function readImageResponse(response, byteBudget) {
  const contentType = String(response.headers.get("content-type") || "").toLowerCase();
  if (contentType && !contentType.startsWith("image/")) {
    throw createNsfwError("NSFW_FETCH_CONTENT_TYPE", `Unsupported image content type: ${contentType}`);
  }
  const declaredLength = Number(response.headers.get("content-length") || 0);
  if (declaredLength > NSFW_MAX_IMAGE_BYTES) {
    throw createNsfwError("NSFW_FETCH_TOO_LARGE", "Image exceeds the per-image byte limit");
  }

  const chunks = [];
  let totalBytes = 0;
  if (response.body?.getReader) {
    const reader = response.body.getReader();
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      totalBytes += value.byteLength;
      byteBudget.total += value.byteLength;
      if (totalBytes > NSFW_MAX_IMAGE_BYTES || byteBudget.total > NSFW_MAX_BATCH_BYTES) {
        await reader.cancel().catch(() => {});
        throw createNsfwError("NSFW_FETCH_TOO_LARGE", "Image batch exceeds the byte limit");
      }
      chunks.push(value);
    }
    return new Blob(chunks, { type: contentType || "image/*" });
  }

  const blob = await response.blob();
  byteBudget.total += blob.size;
  if (blob.size > NSFW_MAX_IMAGE_BYTES || byteBudget.total > NSFW_MAX_BATCH_BYTES) {
    throw createNsfwError("NSFW_FETCH_TOO_LARGE", "Image batch exceeds the byte limit");
  }
  return blob;
}

async function fetchAndDecodeNsfwImage(item, allowLoopback, byteBudget, deadlineEpochMs) {
  const sourceUrl = normalizeNsfwSourceUrl(item.sourceUrl, allowLoopback);
  assertNsfwDeadline(deadlineEpochMs, NSFW_MIN_REMAINING_INFERENCE_BUDGET_MS);
  const fetchStartedAt = performance.now();
  const controller = new AbortController();
  const timeoutMs = Math.min(NSFW_FETCH_TIMEOUT_MS, getNsfwRemainingBudgetMs(deadlineEpochMs));
  const timeoutId = setTimeout(() => controller.abort(), Math.max(1, timeoutMs));
  let response;
  try {
    response = await fetch(sourceUrl, {
      cache: "force-cache",
      credentials: "omit",
      redirect: "follow",
      signal: controller.signal
    });
  } catch (error) {
    if (error?.name === "AbortError") {
      throw createNsfwError("NSFW_FETCH_TIMEOUT", "Image fetch timed out");
    }
    throw createNsfwError("NSFW_FETCH_FAILED", String(error?.message || error));
  } finally {
    clearTimeout(timeoutId);
  }
  if (!response.ok) {
    throw createNsfwError("NSFW_FETCH_STATUS", `Image fetch returned ${response.status}`);
  }
  normalizeNsfwSourceUrl(response.url || sourceUrl, allowLoopback);
  const blob = await readImageResponse(response, byteBudget);
  assertNsfwDeadline(deadlineEpochMs, NSFW_MIN_REMAINING_INFERENCE_BUDGET_MS);
  const fetchMs = Math.round(performance.now() - fetchStartedAt);

  const decodeStartedAt = performance.now();
  let bitmap;
  try {
    bitmap = await createImageBitmap(blob, {
      resizeWidth: NSFW_INPUT_SIZE,
      resizeHeight: NSFW_INPUT_SIZE,
      resizeQuality: "medium"
    });
  } catch (error) {
    throw createNsfwError("NSFW_DECODE_FAILED", String(error?.message || error));
  }
  return {
    candidateKey: String(item.candidateKey || "").slice(0, 96),
    sourceUrl,
    bitmap,
    fetchMs,
    decodeMs: Math.round(performance.now() - decodeStartedAt)
  };
}

function tensorForNsfwBitmap(bitmap) {
  return tf.tidy(() => {
    const pixels = tf.browser.fromPixels(bitmap, 3);
    const normalized = pixels.toFloat().div(255);
    if (pixels.shape[0] === NSFW_INPUT_SIZE && pixels.shape[1] === NSFW_INPUT_SIZE) {
      return normalized;
    }
    return tf.image.resizeBilinear(normalized, [NSFW_INPUT_SIZE, NSFW_INPUT_SIZE], true);
  });
}

async function inferNsfwBitmaps(decoded) {
  const inputTensors = decoded.map((item) => tensorForNsfwBitmap(item.bitmap));
  const batch = tf.stack(inputTensors);
  let output = null;
  try {
    const inferenceStartedAt = performance.now();
    output = nsfwModel.predict(batch);
    const outputTensor = Array.isArray(output) ? output[0] : output;
    const values = await outputTensor.data();
    const inferenceMs = Math.round(performance.now() - inferenceStartedAt);
    const predictions = [];
    for (let row = 0; row < decoded.length; row += 1) {
      const scores = {};
      for (let column = 0; column < NSFW_CLASS_NAMES.length; column += 1) {
        scores[NSFW_CLASS_NAMES[column]] = Number(values[row * NSFW_CLASS_NAMES.length + column] || 0);
      }
      predictions.push({ candidateKey: decoded[row].candidateKey, scores });
    }
    return { predictions, inferenceMs };
  } finally {
    inputTensors.forEach((tensor) => tensor.dispose());
    batch.dispose();
    if (Array.isArray(output)) {
      output.forEach((tensor) => tensor?.dispose?.());
    } else {
      output?.dispose?.();
    }
    decoded.forEach((item) => item.bitmap?.close?.());
  }
}

async function classifyNsfwBatch(message, queuedAt) {
  const startedAt = performance.now();
  const queueWaitMs = Math.round(startedAt - queuedAt);
  const deadlineEpochMs = getNsfwDeadlineEpochMs(message);
  assertNsfwDeadline(deadlineEpochMs, NSFW_MIN_REMAINING_INFERENCE_BUDGET_MS);
  const items = Array.isArray(message?.items) ? message.items.slice(0, NSFW_MAX_BATCH_SIZE) : [];
  if (items.length === 0) {
    throw createNsfwError("NSFW_BATCH_EMPTY", "NSFW classifier batch is empty");
  }
  if (nsfwTestOverride === "off") {
    return { ok: true, status: "disabled", results: [], queueWaitMs, ...getNsfwStatus() };
  }

  if (nsfwTestOverride === "fixture") {
    const results = items.map((item) => ({
      candidateKey: String(item.candidateKey || "").slice(0, 96),
      ok: true,
      cacheHit: false,
      fetchMs: 0,
      decodeMs: 0,
      scores: fixtureScoresForUrl(item.sourceUrl)
    }));
    return {
      ...getNsfwStatus(),
      status: "classified",
      results,
      batchSize: results.length,
      cacheHitCount: 0,
      fetchMs: 0,
      decodeMs: 0,
      inferenceMs: 0,
      queueWaitMs,
      totalMs: Math.round(performance.now() - startedAt),
      ok: true
    };
  }

  await loadNsfwModel();
  assertNsfwDeadline(deadlineEpochMs, NSFW_MIN_REMAINING_INFERENCE_BUDGET_MS);
  const allowLoopback = message?.allowLoopback === true;
  const resultsByKey = new Map();
  const misses = [];
  for (const item of items) {
    const candidateKey = String(item?.candidateKey || "").slice(0, 96);
    try {
      const sourceUrl = normalizeNsfwSourceUrl(item?.sourceUrl, allowLoopback);
      const scores = getCachedNsfwPrediction(sourceUrl);
      if (scores) {
        resultsByKey.set(candidateKey, {
          candidateKey,
          ok: true,
          cacheHit: true,
          fetchMs: 0,
          decodeMs: 0,
          scores
        });
      } else {
        misses.push({ candidateKey, sourceUrl });
      }
    } catch (error) {
      resultsByKey.set(candidateKey, {
        candidateKey,
        ok: false,
        cacheHit: false,
        fetchMs: 0,
        decodeMs: 0,
        ...serializeNsfwError(error, "NSFW_SOURCE_INVALID")
      });
    }
  }

  let inferenceMs = 0;
  if (misses.length > 0) {
    const byteBudget = { total: 0 };
    const decodedOrErrors = await Promise.all(misses.map(async (item) => {
      try {
        return await fetchAndDecodeNsfwImage(item, allowLoopback, byteBudget, deadlineEpochMs);
      } catch (error) {
        return {
          candidateKey: item.candidateKey,
          sourceUrl: item.sourceUrl,
          error: serializeNsfwError(error, "NSFW_IMAGE_PREPARE_FAILED")
        };
      }
    }));
    const decoded = decodedOrErrors.filter((item) => item.bitmap);
    for (const item of decodedOrErrors) {
      if (item.error) {
        resultsByKey.set(item.candidateKey, {
          candidateKey: item.candidateKey,
          ok: false,
          cacheHit: false,
          fetchMs: 0,
          decodeMs: 0,
          ...item.error
        });
      }
    }
    if (decoded.length > 0) {
      assertNsfwDeadline(deadlineEpochMs, NSFW_MIN_REMAINING_INFERENCE_BUDGET_MS);
      const inference = await inferNsfwBitmaps(decoded);
      inferenceMs = inference.inferenceMs;
      for (const prediction of inference.predictions) {
        const prepared = decoded.find((item) => item.candidateKey === prediction.candidateKey);
        cacheNsfwPrediction(prepared.sourceUrl, prediction.scores);
        resultsByKey.set(prediction.candidateKey, {
          candidateKey: prediction.candidateKey,
          ok: true,
          cacheHit: false,
          fetchMs: prepared.fetchMs,
          decodeMs: prepared.decodeMs,
          scores: prediction.scores
        });
      }
    }
  }

  const results = items.map((item) => resultsByKey.get(String(item.candidateKey || "").slice(0, 96))).filter(Boolean);
  return {
    ok: true,
    status: "classified",
    results,
    batchSize: results.length,
    cacheHitCount: results.filter((item) => item.cacheHit).length,
    fetchMs: Math.max(0, ...results.map((item) => Number(item.fetchMs || 0))),
    decodeMs: Math.max(0, ...results.map((item) => Number(item.decodeMs || 0))),
    inferenceMs,
    queueWaitMs,
    totalMs: Math.round(performance.now() - startedAt),
    ...getNsfwStatus()
  };
}

function enqueueNsfwBatch(message) {
  const queuedAt = performance.now();
  const operation = nsfwInferenceQueue
    .catch(() => {})
    .then(() => classifyNsfwBatch(message, queuedAt));
  nsfwInferenceQueue = operation.then(() => undefined, () => undefined);
  return operation;
}

async function setNsfwTestOverride(mode) {
  const normalized = ["normal", "off", "fixture", "cpu", "wasm"].includes(mode) ? mode : "normal";
  const nextForcedBackend = normalized === "cpu" || normalized === "wasm" ? normalized : "";
  const backendChanged = nextForcedBackend !== nsfwForcedBackend;
  nsfwTestOverride = normalized;
  nsfwForcedBackend = nextForcedBackend;
  if (backendChanged && nsfwModel) {
    nsfwModel.dispose();
    nsfwModel = null;
    nsfwBackend = "";
    NSFW_PREDICTION_CACHE.clear();
  }
  return { ...getNsfwStatus(), ok: true };
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.target !== NSFW_OFFSCREEN_TARGET) return false;

  if (message?.type === "OFFSCREEN_NSFW_WARMUP") {
    loadNsfwModel()
      .then((status) => sendResponse({ ok: true, ...status }))
      .catch((error) => sendResponse({ ok: false, ...serializeNsfwError(error, "NSFW_MODEL_LOAD_FAILED") }));
    return true;
  }
  if (message?.type === "OFFSCREEN_NSFW_CLASSIFY_BATCH") {
    enqueueNsfwBatch(message)
      .then(sendResponse)
      .catch((error) => sendResponse({ ok: false, ...serializeNsfwError(error, "NSFW_CLASSIFIER_FAILED") }));
    return true;
  }
  if (message?.type === "OFFSCREEN_NSFW_GET_STATUS") {
    sendResponse({ ok: true, ...getNsfwStatus() });
    return false;
  }
  if (message?.type === "OFFSCREEN_NSFW_SET_TEST_OVERRIDE") {
    setNsfwTestOverride(String(message?.mode || "normal"))
      .then(sendResponse)
      .catch((error) => sendResponse({ ok: false, ...serializeNsfwError(error, "NSFW_TEST_OVERRIDE_FAILED") }));
    return true;
  }
  return false;
});
