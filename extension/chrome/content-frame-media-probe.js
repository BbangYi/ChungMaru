(() => {
  if (window.top === window || globalThis.__chungmaruFrameMediaProbeStarted) {
    return;
  }
  globalThis.__chungmaruFrameMediaProbeStarted = true;

  const MAX_CANDIDATES = 2;
  const MIN_AREA_PX = 3600;
  const RESCAN_INTERVAL_MS = 900;
  const SETTLE_DELAY_MS = 220;
  const PROBE_SELECTOR = "img, picture, video[poster], [role='img'][aria-label], [style*='background-image']";
  const EXPLICIT_CONTEXT_PATTERN = /(?:19\s*금|19\s*세|19\s*등급|성인\s*(?:물|전용|인증|만화|영상|방송|사이트|콘텐츠)|야동|포르노|porn|porno|nsfw|섹스|sex(?:ual)?|노출|후방\s*주의|무삭제)/i;
  const GAMBLING_CONTEXT_PATTERN = /(?:카지노|도박|토토|바카라|슬롯|베팅|casino|sportsbook|가입\s*코드|첫\s*충|페이백|콤프)/i;

  let mediaSafetyEnabled = false;
  let scanTimerId = null;
  let lastScanAt = 0;
  let scanInFlight = false;
  let sequence = 0;
  let elementStates = new WeakMap();

  function normalizeText(value) {
    return String(value || "").replace(/\s+/g, " ").trim();
  }

  function isEnabled(settings) {
    return settings?.enabled !== false && settings?.mediaSafetyEnabled === true;
  }

  function isVisible(element) {
    if (!(element instanceof Element) || !element.isConnected) return false;
    const rect = element.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0 || rect.width * rect.height < MIN_AREA_PX) return false;
    return rect.bottom >= 0 && rect.top <= window.innerHeight && rect.right >= 0 && rect.left <= window.innerWidth;
  }

  function sourceUrlFor(element) {
    if (element instanceof HTMLImageElement) {
      return String(element.currentSrc || element.src || element.getAttribute("src") || "");
    }
    if (typeof HTMLPictureElement !== "undefined" && element instanceof HTMLPictureElement) {
      const image = element.querySelector("img");
      return image instanceof HTMLImageElement ? String(image.currentSrc || image.src || image.getAttribute("src") || "") : "";
    }
    if (element instanceof HTMLVideoElement) {
      return String(element.poster || element.getAttribute("poster") || "");
    }
    try {
      const background = window.getComputedStyle(element).backgroundImage || "";
      const match = background.match(/url\((['"]?)(.*?)\1\)/i);
      return match?.[2] || "";
    } catch {
      return "";
    }
  }

  function contextFor(element) {
    const container = element.closest("a, article, figure, li, [role='img']") || element.parentElement || element;
    return normalizeText([
      element.getAttribute("alt"),
      element.getAttribute("aria-label"),
      element.getAttribute("title"),
      container?.getAttribute?.("aria-label"),
      container?.textContent,
      location.hostname
    ].filter(Boolean).join(" ")).slice(0, 320);
  }

  function targetFor(element) {
    const rect = element.getBoundingClientRect();
    const parent = element.closest("a, article, figure, li, [role='img']");
    if (parent instanceof Element) {
      const parentRect = parent.getBoundingClientRect();
      const mediaArea = Math.max(1, rect.width * rect.height);
      const parentArea = parentRect.width * parentRect.height;
      if (parentArea >= mediaArea && parentArea <= mediaArea * 5) {
        return parent;
      }
    }
    return element;
  }

  function markProtected(target, reason) {
    if (!(target instanceof Element) || target.hasAttribute("data-chungmaru-frame-media-hidden")) return false;
    target.setAttribute("data-chungmaru-frame-media-hidden", "true");
    target.setAttribute("data-chungmaru-frame-media-reason", reason);
    target.classList.add("chungmaru-frame-media-hidden");
    return true;
  }

  function emit(event) {
    chrome.runtime.sendMessage({
      type: "ADD_RUNTIME_EVENT_LOG",
      event: {
        source: "frame-media-probe",
        profile: "iframe",
        domain: location.hostname || "",
        url: `${location.origin}${location.pathname}`.slice(0, 180),
        ...event
      }
    }).catch(() => {});
  }

  function collectCandidates() {
    const nodes = new Set(Array.from(document.querySelectorAll(PROBE_SELECTOR)).slice(0, 40));
    const width = Math.max(0, window.innerWidth || 0);
    const height = Math.max(0, window.innerHeight || 0);
    for (const x of [0.25, 0.5, 0.75]) {
      for (const y of [0.25, 0.5, 0.75]) {
        for (const element of document.elementsFromPoint(Math.floor(width * x), Math.floor(height * y)).slice(0, 4)) {
          if (!(element instanceof Element)) continue;
          const backgroundTarget = element.closest("a, article, figure, li, div") || element;
          nodes.add(backgroundTarget);
        }
      }
    }

    return Array.from(nodes)
      .filter((element) => isVisible(element) && !element.closest("[data-chungmaru-frame-media-hidden='true']"))
      .map((element) => {
        const sourceUrl = sourceUrlFor(element);
        const target = targetFor(element);
        return {
          element,
          target,
          sourceUrl,
          context: contextFor(element),
          area: Math.round(element.getBoundingClientRect().width * element.getBoundingClientRect().height)
        };
      })
      .filter((candidate) => candidate.sourceUrl && candidate.target instanceof Element)
      .sort((left, right) => right.area - left.area)
      .slice(0, MAX_CANDIDATES);
  }

  async function scan(reason) {
    if (!mediaSafetyEnabled || scanInFlight) return;
    scanInFlight = true;
    const startedAt = performance.now();
    const candidates = collectCandidates();
    let actionCount = 0;
    const classifyItems = [];

    for (const candidate of candidates) {
      const previous = elementStates.get(candidate.target);
      if (previous?.sourceUrl === candidate.sourceUrl && ["queued", "blocked", "benign"].includes(previous.status)) {
        continue;
      }
      if (EXPLICIT_CONTEXT_PATTERN.test(candidate.context) || GAMBLING_CONTEXT_PATTERN.test(candidate.context)) {
        if (markProtected(candidate.target, "frame explicit context")) actionCount += 1;
        elementStates.set(candidate.target, { sourceUrl: candidate.sourceUrl, status: "blocked" });
        continue;
      }
      elementStates.set(candidate.target, { sourceUrl: candidate.sourceUrl, status: "queued" });
      classifyItems.push(candidate);
    }

    try {
      if (classifyItems.length > 0) {
        const requestId = `frame-${++sequence}`;
        const response = await chrome.runtime.sendMessage({
          type: "CLASSIFY_NSFW_IMAGE_BATCH",
          requestId,
          contextKey: `iframe:${location.origin}${location.pathname}`.slice(0, 160),
          items: classifyItems.map((candidate, index) => ({
            candidateKey: `${requestId}-${index}`,
            sourceUrl: candidate.sourceUrl
          }))
        });
        const results = new Map((response?.results || []).map((item) => [String(item?.candidateKey || ""), item]));
        let blockedCount = 0;
        let benignCount = 0;
        let ambiguousCount = 0;
        for (const [index, candidate] of classifyItems.entries()) {
          const result = results.get(`${requestId}-${index}`);
          const decision = result?.ok ? globalThis.ChungmaruNsfwPolicy?.evaluate?.(result.scores, false) : null;
          if (decision?.verdict === "block") {
            if (markProtected(candidate.target, decision.reason || "frame NSFW classifier")) {
              actionCount += 1;
              blockedCount += 1;
            }
            elementStates.set(candidate.target, { sourceUrl: candidate.sourceUrl, status: "blocked" });
          } else {
            const status = decision?.verdict === "ambiguous" ? "ambiguous" : "benign";
            elementStates.set(candidate.target, { sourceUrl: candidate.sourceUrl, status });
            if (status === "ambiguous") ambiguousCount += 1;
            else benignCount += 1;
          }
        }
        emit({
          type: "media-safety-frame-classifier-batch",
          ok: true,
          status: "classified",
          candidateCount: candidates.length,
          classifierCandidateCount: classifyItems.length,
          blockedCount,
          benignCount,
          ambiguousCount,
          actionCount,
          fetchMs: response?.fetchMs || 0,
          decodeMs: response?.decodeMs || 0,
          inferenceMs: response?.inferenceMs || 0,
          queueWaitMs: response?.queueWaitMs || 0,
          classifierDecisionMs: Math.round(performance.now() - startedAt),
          reason
        });
      }
    } catch (error) {
      emit({
        type: "media-safety-frame-error",
        ok: false,
        status: "classifier-failed",
        candidateCount: candidates.length,
        errorCount: 1,
        errorCode: String(error?.message || "FRAME_CLASSIFIER_FAILED").slice(0, 80),
        reason
      });
    } finally {
      if (actionCount > 0 && classifyItems.length === 0) {
        emit({
          type: "media-safety-frame-action",
          ok: true,
          status: "cheap-filter-blocked",
          candidateCount: candidates.length,
          cheapFilterHitCount: actionCount,
          actionCount,
          reason
        });
      }
      lastScanAt = Date.now();
      scanInFlight = false;
    }
  }

  function schedule(reason, delayMs = SETTLE_DELAY_MS) {
    if (!mediaSafetyEnabled) return;
    if (scanTimerId) return;
    const elapsed = Date.now() - lastScanAt;
    const delay = Math.max(delayMs, RESCAN_INTERVAL_MS - elapsed);
    scanTimerId = window.setTimeout(() => {
      scanTimerId = null;
      scan(reason);
    }, Math.max(0, delay));
  }

  async function refreshSettings() {
    const result = await chrome.storage.sync.get("settings");
    mediaSafetyEnabled = isEnabled(result?.settings || {});
    if (mediaSafetyEnabled) schedule("frame-bootstrap", 80);
  }

  chrome.storage.onChanged.addListener((changes, areaName) => {
    if (areaName === "sync" && changes.settings) refreshSettings().catch(() => {});
  });
  document.addEventListener("load", (event) => {
    if (event?.target instanceof HTMLImageElement || event?.target instanceof HTMLVideoElement) schedule("frame-media-load", 0);
  }, true);
  new MutationObserver((records) => {
    if (records.some((record) => record.addedNodes?.length > 0)) schedule("frame-mutation");
  }).observe(document.documentElement, { childList: true, subtree: true });
  window.addEventListener("scroll", () => schedule("frame-scroll", 80), { passive: true });
  window.addEventListener("resize", () => schedule("frame-resize", 80), { passive: true });
  refreshSettings().catch(() => {});
})();
