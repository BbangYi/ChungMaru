(function () {
  if (window.__shieldtextWellbeingWidgetLoaded) {
    return;
  }
  window.__shieldtextWellbeingWidgetLoaded = true;

  const HEARTBEAT_INTERVAL_MS = 15000;
  const VIEW_REFRESH_INTERVAL_MS = 8000;
  const INITIAL_WIDGET_IDLE_TIMEOUT_MS = 1200;
  const INITIAL_HEARTBEAT_DELAY_MS = 2200;
  const WIDGET_LAYOUT_STORAGE_KEY = "wellbeingWidgetLayout";
  const AVATAR_ASSET_STORAGE_KEY = "wellbeingAvatarImageAssets";
  const AVATAR_LOCAL_PREFIX = "local:";
  const AVATAR_IMAGE_STAGE_LIMIT = 10;
  const WIDGET_SIZE_ORDER = ["tiny", "small", "medium", "large"];
  const WIDGET_STYLE_SET = new Set(["soft", "bold", "minimal"]);
  const AVATAR_IMAGE_KEY_PATTERN = /^(default|calm|focused|tired|old|uneasy|annoyed|mad|angry|furious|age(?:[1-9]|10)|anger(?:[1-9]|10))$/;
  const DEFAULT_SETTINGS = {
    enabled: true,
    sensitivity: 60,
    showWellbeingWidget: true,
    wellbeingWidgetStyle: "soft",
    wellbeingAvatarImages: "",
    wellbeingAgeStageCount: 5,
    wellbeingAgeMinutesPerStage: 30,
    wellbeingAngerStageCount: 5,
    wellbeingAngerDetectionsPerStage: 3
  };
  const DEFAULT_WIDGET_LAYOUT = {
    size: "small",
    x: null,
    y: null
  };

  let root = null;
  let cachedSettings = DEFAULT_SETTINGS;
  let cachedAvatarImageAssets = {};
  let widgetLayout = DEFAULT_WIDGET_LAYOUT;
  let heartbeatTimerId = null;
  let refreshTimerId = null;
  let mountRetryTimerId = null;
  let saveLayoutTimerId = null;
  let deferredHeartbeatTimerId = null;
  let dragState = null;
  let widgetInitialized = false;

  function isHttpDocument() {
    return /^https?:$/i.test(location.protocol || "");
  }

  function getFullscreenElement() {
    return (
      document.fullscreenElement ||
      document.webkitFullscreenElement ||
      document.mozFullScreenElement ||
      document.msFullscreenElement ||
      null
    );
  }

  function isFullscreenActive() {
    return Boolean(getFullscreenElement());
  }

  function isExtensionContextAvailable() {
    try {
      return Boolean(chrome?.runtime?.id);
    } catch {
      return false;
    }
  }

  async function sendRuntimeMessage(message) {
    if (!isExtensionContextAvailable()) {
      return null;
    }
    return chrome.runtime.sendMessage(message);
  }

  function normalizeCoordinate(value) {
    if (value === null || value === undefined || value === "") {
      return null;
    }
    const numberValue = Number(value);
    return Number.isFinite(numberValue) ? Math.round(numberValue) : null;
  }

  function normalizeWidgetLayout(value) {
    const layout = value && typeof value === "object" ? value : {};
    const size = WIDGET_SIZE_ORDER.includes(layout.size) ? layout.size : DEFAULT_WIDGET_LAYOUT.size;
    return {
      size,
      x: normalizeCoordinate(layout.x),
      y: normalizeCoordinate(layout.y)
    };
  }

  function hasCustomPosition(layout = widgetLayout) {
    return Number.isFinite(layout.x) && Number.isFinite(layout.y);
  }

  function normalizeWidgetStyle(value) {
    const style = String(value || DEFAULT_SETTINGS.wellbeingWidgetStyle).trim();
    return WIDGET_STYLE_SET.has(style) ? style : DEFAULT_SETTINGS.wellbeingWidgetStyle;
  }

  function normalizeSensitivity(value) {
    const numberValue = Number(value);
    if (!Number.isFinite(numberValue)) return DEFAULT_SETTINGS.sensitivity;
    return Math.max(0, Math.min(100, Math.round(numberValue)));
  }

  function isGlobalProtectionEnabled(settings = cachedSettings) {
    return settings?.enabled !== false && normalizeSensitivity(settings?.sensitivity) > 0;
  }

  function normalizeStageCount(value, fallback = 5) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return fallback;
    return Math.max(1, Math.min(10, Math.round(numeric)));
  }

  function getVisualStage(level, stageCount) {
    const normalizedLevel = Math.max(0, Number(level || 0));
    const normalizedStageCount = normalizeStageCount(stageCount);
    if (normalizedLevel <= 0) {
      return 0;
    }
    return Math.max(
      1,
      Math.min(5, Math.ceil((Math.min(normalizedLevel, normalizedStageCount) / normalizedStageCount) * 5))
    );
  }

  function getConcreteStage(level, stageCount) {
    const normalizedLevel = Math.max(0, Number(level || 0));
    const normalizedStageCount = normalizeStageCount(stageCount);
    if (normalizedLevel <= 0) {
      return 0;
    }
    return Math.max(
      1,
      Math.min(AVATAR_IMAGE_STAGE_LIMIT, Math.min(normalizedLevel, normalizedStageCount))
    );
  }

  async function loadSettings() {
    if (!isExtensionContextAvailable()) {
      return DEFAULT_SETTINGS;
    }

    try {
      const [{ settings }, localResult] = await Promise.all([
        chrome.storage.sync.get("settings"),
        chrome.storage.local.get(AVATAR_ASSET_STORAGE_KEY)
      ]);
      cachedAvatarImageAssets = normalizeAvatarImageAssets(localResult?.[AVATAR_ASSET_STORAGE_KEY]);
      cachedSettings = {
        ...DEFAULT_SETTINGS,
        ...(settings || {}),
        enabled: settings?.enabled !== false,
        sensitivity: normalizeSensitivity(settings?.sensitivity),
        showWellbeingWidget: settings?.showWellbeingWidget !== false,
        wellbeingWidgetStyle: normalizeWidgetStyle(settings?.wellbeingWidgetStyle),
        wellbeingAvatarImages: String(settings?.wellbeingAvatarImages || ""),
        wellbeingAgeStageCount: normalizeStageCount(
          settings?.wellbeingAgeStageCount,
          DEFAULT_SETTINGS.wellbeingAgeStageCount
        ),
        wellbeingAngerStageCount: normalizeStageCount(
          settings?.wellbeingAngerStageCount,
          DEFAULT_SETTINGS.wellbeingAngerStageCount
        )
      };
    } catch {
      cachedSettings = DEFAULT_SETTINGS;
      cachedAvatarImageAssets = {};
    }
    return cachedSettings;
  }

  function normalizeAvatarImageAssets(value) {
    const assets = {};
    if (!value || typeof value !== "object") {
      return assets;
    }
    for (const [rawKey, rawUrl] of Object.entries(value)) {
      const key = String(rawKey || "").toLowerCase();
      const url = String(rawUrl || "").trim();
      if (AVATAR_IMAGE_KEY_PATTERN.test(key) && isAllowedAvatarUrl(url)) {
        assets[key] = url;
      }
    }
    return assets;
  }

  async function loadWidgetLayout() {
    if (!isExtensionContextAvailable()) {
      widgetLayout = DEFAULT_WIDGET_LAYOUT;
      return widgetLayout;
    }

    try {
      const result = await chrome.storage.local.get(WIDGET_LAYOUT_STORAGE_KEY);
      widgetLayout = normalizeWidgetLayout(result?.[WIDGET_LAYOUT_STORAGE_KEY]);
    } catch {
      widgetLayout = DEFAULT_WIDGET_LAYOUT;
    }
    return widgetLayout;
  }

  async function saveWidgetLayoutNow() {
    if (!isExtensionContextAvailable()) {
      return;
    }
    try {
      await chrome.storage.local.set({
        [WIDGET_LAYOUT_STORAGE_KEY]: widgetLayout
      });
      await sendRuntimeMessage({
        type: "WELLBEING_WIDGET_LAYOUT_UPDATED",
        layout: widgetLayout
      });
    } catch {
      // Layout persistence should not remove the widget from the page.
    }
  }

  function scheduleWidgetLayoutSave() {
    if (saveLayoutTimerId) {
      window.clearTimeout(saveLayoutTimerId);
    }
    saveLayoutTimerId = window.setTimeout(() => {
      saveLayoutTimerId = null;
      saveWidgetLayoutNow().catch(() => {});
    }, 180);
  }

  function clampPosition(x, y, widget = root) {
    const margin = 8;
    const rect = widget?.getBoundingClientRect?.();
    const width = Math.max(72, Number(rect?.width || widget?.offsetWidth || 108));
    const height = Math.max(88, Number(rect?.height || widget?.offsetHeight || 132));
    const maxX = Math.max(margin, window.innerWidth - width - margin);
    const maxY = Math.max(margin, window.innerHeight - height - margin);
    return {
      x: Math.round(Math.max(margin, Math.min(maxX, Number(x || margin)))),
      y: Math.round(Math.max(margin, Math.min(maxY, Number(y || margin))))
    };
  }

  function applyWidgetLayout(widget = root) {
    if (!widget) return;
    widget.dataset.size = widgetLayout.size;
    widget.dataset.style = normalizeWidgetStyle(cachedSettings.wellbeingWidgetStyle);

    if (hasCustomPosition()) {
      const clamped = clampPosition(widgetLayout.x, widgetLayout.y, widget);
      widget.dataset.position = "custom";
      widget.style.left = `${clamped.x}px`;
      widget.style.top = `${clamped.y}px`;
      widget.style.right = "auto";
      widget.style.bottom = "auto";
      widget.style.transform = "none";
      return;
    }

    widget.dataset.position = "edge";
    widget.style.removeProperty("left");
    widget.style.removeProperty("top");
    widget.style.removeProperty("right");
    widget.style.removeProperty("bottom");
    widget.style.removeProperty("transform");
  }

  function updateWidgetSize(delta) {
    const currentIndex = WIDGET_SIZE_ORDER.indexOf(widgetLayout.size);
    const nextIndex = Math.max(
      0,
      Math.min(WIDGET_SIZE_ORDER.length - 1, (currentIndex >= 0 ? currentIndex : 1) + delta)
    );
    const nextSize = WIDGET_SIZE_ORDER[nextIndex];
    if (nextSize === widgetLayout.size) {
      return;
    }

    widgetLayout = {
      ...widgetLayout,
      size: nextSize
    };
    applyWidgetLayout();
    window.requestAnimationFrame(() => {
      applyWidgetLayout();
      saveWidgetLayoutNow().catch(() => {});
    });
  }

  function removeWidget() {
    if (dragState && root) {
      try {
        root.releasePointerCapture?.(dragState.pointerId);
      } catch {
        // The pointer may already have been released by the page or browser.
      }
      root.removeAttribute("data-dragging");
    }
    dragState = null;
    if (root?.parentNode) {
      root.parentNode.removeChild(root);
    }
    root = null;
  }

  function isWidgetControl(target) {
    return Boolean(target?.closest?.(".shieldtext-wellbeing-size-button"));
  }

  function onWidgetPointerDown(event) {
    if (event.button !== 0 || !root || isWidgetControl(event.target)) {
      return;
    }

    const rect = root.getBoundingClientRect();
    dragState = {
      pointerId: event.pointerId,
      startClientX: event.clientX,
      startClientY: event.clientY,
      startX: rect.left,
      startY: rect.top,
      moved: false
    };
    root.dataset.dragging = "true";
    root.setPointerCapture?.(event.pointerId);
    event.preventDefault();
  }

  function onWidgetPointerMove(event) {
    if (!dragState || dragState.pointerId !== event.pointerId || !root) {
      return;
    }

    const dx = event.clientX - dragState.startClientX;
    const dy = event.clientY - dragState.startClientY;
    if (Math.abs(dx) > 2 || Math.abs(dy) > 2) {
      dragState.moved = true;
    }

    const next = clampPosition(dragState.startX + dx, dragState.startY + dy, root);
    widgetLayout = {
      ...widgetLayout,
      x: next.x,
      y: next.y
    };
    applyWidgetLayout();
    scheduleWidgetLayoutSave();
  }

  function finishWidgetDrag(event) {
    if (!dragState || dragState.pointerId !== event.pointerId) {
      return;
    }

    if (root) {
      try {
        root.releasePointerCapture?.(event.pointerId);
      } catch {
        // Capture can disappear if the widget is removed during fullscreen or navigation.
      }
      root.removeAttribute("data-dragging");
    }
    const shouldSave = dragState.moved;
    dragState = null;
    if (shouldSave) {
      saveWidgetLayoutNow().catch(() => {});
    }
  }

  function ensureWidgetRoot() {
    if (
      !isHttpDocument() ||
      !isGlobalProtectionEnabled() ||
      cachedSettings.showWellbeingWidget === false ||
      isFullscreenActive()
    ) {
      removeWidget();
      return null;
    }

    if (root?.isConnected) {
      applyWidgetLayout(root);
      return root;
    }

    const mountTarget = document.documentElement;
    if (!mountTarget) {
      return null;
    }

    root = document.createElement("aside");
    root.className = "shieldtext-wellbeing-widget";
    root.setAttribute("data-shieldtext-rendered", "true");
    root.setAttribute("aria-live", "polite");
    root.setAttribute("aria-label", "청마루 인터넷 사용 상태");
    root.title = "드래그해서 위치 이동";
    root.addEventListener("pointerdown", onWidgetPointerDown);
    root.addEventListener("pointermove", onWidgetPointerMove);
    root.addEventListener("pointerup", finishWidgetDrag);
    root.addEventListener("pointercancel", finishWidgetDrag);
    mountTarget.appendChild(root);
    applyWidgetLayout(root);
    return root;
  }

  function formatDuration(totalMs) {
    const normalizedMs = Math.max(0, Number(totalMs || 0));
    if (normalizedMs > 0 && normalizedMs < 60000) {
      return "1분 미만";
    }
    const totalMinutes = Math.max(0, Math.floor(normalizedMs / 60000));
    if (totalMinutes < 60) {
      return `${totalMinutes}분`;
    }
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return minutes ? `${hours}시간 ${minutes}분` : `${hours}시간`;
  }

  function getCurrentPageCounts(view) {
    const page = view?.currentPage || view?.currentSite || {};
    const renderedMaskCount = countRenderedMaskTargets();
    const profanity = Number(page.profanityNodeCount || 0);
    const harmful = Number(
      page.expressionCount ||
        Math.max(
          renderedMaskCount,
          Number(page.maskedSpanCount || 0),
          Number(page.blockedNodeCount || 0),
          Number(page.toxicNodeCount || 0),
          Number(page.hateNodeCount || 0)
        )
    );
    return {
      profanity: Math.max(0, profanity),
      harmful: Math.max(0, harmful),
      renderedMaskCount
    };
  }

  function formatCurrentPageLabel(view) {
    const counts = getCurrentPageCounts(view);
    if (counts.renderedMaskCount > 0) {
      return `마스킹 ${counts.renderedMaskCount}곳`;
    }
    if (counts.profanity > 0) {
      return `욕설 ${counts.profanity}개`;
    }
    if (counts.harmful > 0) {
      return `유해표현 ${counts.harmful}개`;
    }
    return "이 페이지 안전";
  }

  function countRenderedMaskTargets() {
    const selector = [
      ".shieldtext-render-box .shieldtext-inline-mask",
      ".shieldtext-render-box .shieldtext-inline-blur",
      ".shieldtext-render-box .shieldtext-inline-hide"
    ].join(", ");

    let count = 0;
    for (const element of document.querySelectorAll(selector)) {
      if (!(element instanceof Element)) {
        continue;
      }
      const rect = element.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) {
        continue;
      }
      count += 1;
    }
    return count;
  }

  function clearChildren(element) {
    while (element.firstChild) {
      element.removeChild(element.firstChild);
    }
  }

  function createFallbackFace() {
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

    return face;
  }

  function appendFace(parent) {
    const avatarImageUrl = getAvatarImageUrl(parent);
    if (avatarImageUrl) {
      const image = document.createElement("img");
      image.className = "shieldtext-wellbeing-avatar-image";
      image.src = avatarImageUrl;
      image.alt = "";
      image.draggable = false;
      image.referrerPolicy = "no-referrer";
      image.addEventListener(
        "error",
        () => {
          if (image.isConnected) {
            image.replaceWith(createFallbackFace());
          }
        },
        { once: true }
      );
      parent.appendChild(image);
      return;
    }

    parent.appendChild(createFallbackFace());
  }

  function parseAvatarImages(value) {
    const entries = new Map();
    for (const line of String(value || "").split(/\n+/)) {
      const match = line.match(/^\s*([a-z0-9_-]+)\s*=\s*(.+?)\s*$/i);
      if (!match) {
        continue;
      }
      const key = match[1].toLowerCase();
      const url = match[2].trim();
      if (!AVATAR_IMAGE_KEY_PATTERN.test(key) || !isAllowedAvatarUrl(url)) {
        continue;
      }
      entries.set(key, url);
    }
    return entries;
  }

  function isAllowedAvatarUrl(value) {
    try {
      const parsed = new URL(String(value || ""), location.href);
      return ["http:", "https:", "data:", "chrome-extension:", "local:"].includes(parsed.protocol);
    } catch {
      return false;
    }
  }

  function resolveAvatarImageUrl(key, value) {
    const url = String(value || "").trim();
    if (!url.toLowerCase().startsWith(AVATAR_LOCAL_PREFIX)) {
      return url;
    }
    const rawLocalKey = url.slice(AVATAR_LOCAL_PREFIX.length) || key;
    const localKey = String(rawLocalKey || "").toLowerCase();
    if (!AVATAR_IMAGE_KEY_PATTERN.test(localKey)) {
      return "";
    }
    return cachedAvatarImageAssets[localKey] || "";
  }

  function getAvatarImageUrl(widget) {
    const avatarImages = parseAvatarImages(cachedSettings.wellbeingAvatarImages);
    if (!avatarImages.size) {
      return "";
    }
    const angerLevel = Number(widget?.dataset?.angerLevel || 0);
    const angerStageCount = Number(widget?.dataset?.angerStageCount || 0);
    const ageLevel = Number(widget?.dataset?.ageLevel || 0);
    const ageStageCount = Number(widget?.dataset?.ageStageCount || 0);
    const angerConcreteStage = getConcreteStage(angerLevel, angerStageCount);
    const ageConcreteStage = getConcreteStage(ageLevel, ageStageCount);
    const angerVisualLevel = Number(widget?.dataset?.angerVisualLevel || 0);
    const ageVisualLevel = Number(widget?.dataset?.ageVisualLevel || 0);
    const mood = String(widget?.dataset?.mood || "calm");
    const keys = [];
    if (angerConcreteStage > 0) keys.push(`anger${angerConcreteStage}`);
    if (angerVisualLevel > 0) keys.push(`anger${Math.min(5, angerVisualLevel)}`);
    if (ageConcreteStage > 0) keys.push(`age${ageConcreteStage}`);
    if (ageVisualLevel > 0) keys.push(`age${Math.min(5, ageVisualLevel)}`);
    keys.push(mood, "default");
    for (const key of Array.from(new Set(keys))) {
      const url = avatarImages.get(key);
      const resolvedUrl = resolveAvatarImageUrl(key, url);
      if (resolvedUrl) {
        return resolvedUrl;
      }
    }
    return "";
  }

  function appendControls(parent) {
    const controls = document.createElement("div");
    controls.className = "shieldtext-wellbeing-controls";

    const handle = document.createElement("span");
    handle.className = "shieldtext-wellbeing-drag-handle";
    handle.textContent = "⠿";
    handle.setAttribute("aria-hidden", "true");
    controls.appendChild(handle);

    const buttonGroup = document.createElement("div");
    buttonGroup.className = "shieldtext-wellbeing-size-controls";

    const shrinkButton = document.createElement("button");
    shrinkButton.type = "button";
    shrinkButton.className = "shieldtext-wellbeing-size-button";
    shrinkButton.textContent = "−";
    shrinkButton.title = "위젯 작게";
    shrinkButton.setAttribute("aria-label", "위젯 작게");
    shrinkButton.addEventListener("click", (event) => {
      event.stopPropagation();
      updateWidgetSize(-1);
    });
    buttonGroup.appendChild(shrinkButton);

    const growButton = document.createElement("button");
    growButton.type = "button";
    growButton.className = "shieldtext-wellbeing-size-button";
    growButton.textContent = "+";
    growButton.title = "위젯 크게";
    growButton.setAttribute("aria-label", "위젯 크게");
    growButton.addEventListener("click", (event) => {
      event.stopPropagation();
      updateWidgetSize(1);
    });
    buttonGroup.appendChild(growButton);

    controls.appendChild(buttonGroup);
    parent.appendChild(controls);
  }

  function renderWidget(view) {
    const widget = ensureWidgetRoot();
    if (!widget) {
      return;
    }

    const normalizedView = view || {};
    const ageLevel = Math.max(0, Number(normalizedView.ageLevel || 0));
    const ageStageCount = normalizeStageCount(
      normalizedView.ageStageCount,
      cachedSettings.wellbeingAgeStageCount
    );
    const angerLevel = Math.max(0, Number(normalizedView.angerLevel || 0));
    const angerStageCount = normalizeStageCount(
      normalizedView.angerStageCount,
      cachedSettings.wellbeingAngerStageCount
    );
    const ageVisualLevel = getVisualStage(ageLevel, ageStageCount);
    const angerVisualLevel = getVisualStage(angerLevel, angerStageCount);

    widget.dataset.mood = String(normalizedView.mood || "calm");
    widget.dataset.ageLevel = String(ageLevel);
    widget.dataset.ageStageCount = String(ageStageCount);
    widget.dataset.ageVisualLevel = String(ageVisualLevel);
    widget.dataset.angerLevel = String(angerLevel);
    widget.dataset.angerStageCount = String(angerStageCount);
    widget.dataset.angerVisualLevel = String(angerVisualLevel);
    widget.dataset.policyVerdict = String(normalizedView.policyVerdict || "allow");
    widget.dataset.style = normalizeWidgetStyle(cachedSettings.wellbeingWidgetStyle);
    widget.style.setProperty(
      "--shieldtext-age-progress",
      String(Math.min(1, ageLevel / ageStageCount).toFixed(3))
    );
    widget.style.setProperty(
      "--shieldtext-anger-progress",
      String(Math.min(1, angerLevel / angerStageCount).toFixed(3))
    );

    clearChildren(widget);
    appendControls(widget);
    appendFace(widget);

    const metrics = document.createElement("div");
    metrics.className = "shieldtext-wellbeing-metrics";

    const usage = document.createElement("strong");
    usage.textContent = formatDuration(normalizedView.totalActiveMs);
    metrics.appendChild(usage);

    const site = document.createElement("span");
    site.textContent = formatCurrentPageLabel(normalizedView);
    metrics.appendChild(site);

    widget.appendChild(metrics);
    applyWidgetLayout(widget);
  }

  async function refreshWidgetView() {
    await loadSettings();
    if (!dragState) {
      await loadWidgetLayout();
    }
    if (!isGlobalProtectionEnabled() || cachedSettings.showWellbeingWidget === false || !isHttpDocument()) {
      removeWidget();
      return;
    }
    if (isFullscreenActive()) {
      removeWidget();
      return;
    }

    try {
      const response = await sendRuntimeMessage({
        type: "GET_WELLBEING_STATE_FOR_URL",
        url: location.href
      });
      renderWidget(response?.view || null);
    } catch {
      removeWidget();
    }
  }

  async function sendHeartbeat() {
    if (
      !isGlobalProtectionEnabled() ||
      cachedSettings.showWellbeingWidget === false ||
      !isHttpDocument() ||
      document.visibilityState !== "visible"
    ) {
      return;
    }

    try {
      const response = await sendRuntimeMessage({
        type: "WELLBEING_HEARTBEAT",
        url: location.href,
        title: document.title || "",
        visible: document.visibilityState === "visible",
        focused: document.hasFocus()
      });
      if (response?.view) {
        renderWidget(response.view);
      }
    } catch {
      // The extension can be reloaded while content scripts are still alive.
    }
  }

  function scheduleMountRetry() {
    if (mountRetryTimerId) return;
    mountRetryTimerId = window.setTimeout(() => {
      mountRetryTimerId = null;
      refreshWidgetView().catch(() => {});
    }, 200);
  }

  function startTimers() {
    if (!heartbeatTimerId) {
      heartbeatTimerId = window.setInterval(() => {
        sendHeartbeat().catch(() => {});
      }, HEARTBEAT_INTERVAL_MS);
    }

    if (!refreshTimerId) {
      refreshTimerId = window.setInterval(() => {
        refreshWidgetView().catch(() => {});
      }, VIEW_REFRESH_INTERVAL_MS);
    }
  }

  function scheduleIdleWidgetTask(callback) {
    if (typeof callback !== "function") {
      return;
    }

    if ("requestIdleCallback" in window) {
      window.requestIdleCallback(callback, { timeout: INITIAL_WIDGET_IDLE_TIMEOUT_MS });
      return;
    }

    window.setTimeout(callback, INITIAL_WIDGET_IDLE_TIMEOUT_MS);
  }

  function scheduleDeferredHeartbeat() {
    if (deferredHeartbeatTimerId) {
      return;
    }

    deferredHeartbeatTimerId = window.setTimeout(() => {
      deferredHeartbeatTimerId = null;
      sendHeartbeat().catch(() => {});
    }, INITIAL_HEARTBEAT_DELAY_MS);
  }

  function shouldRunWidgetRuntime() {
    return isHttpDocument() && isGlobalProtectionEnabled() && cachedSettings.showWellbeingWidget !== false;
  }

  async function activateWidgetRuntime(options = {}) {
    if (!shouldRunWidgetRuntime()) {
      removeWidget();
      return;
    }
    widgetInitialized = true;
    startTimers();
    await refreshWidgetView();
    if (options.deferHeartbeat === true) {
      scheduleDeferredHeartbeat();
    } else {
      await sendHeartbeat();
    }
  }

  async function initialize(options = {}) {
    if (!isHttpDocument()) {
      return;
    }

    await Promise.all([loadSettings(), loadWidgetLayout()]);
    if (!shouldRunWidgetRuntime()) {
      removeWidget();
      return;
    }
    if (!ensureWidgetRoot()) {
      scheduleMountRetry();
    }
    widgetInitialized = true;
    await activateWidgetRuntime({
      deferHeartbeat: options.deferHeartbeat === true
    });
  }

  function scheduleWidgetInitialization() {
    scheduleIdleWidgetTask(() => {
      initialize({ deferHeartbeat: true }).catch(() => {});
    });
  }

  window.addEventListener("resize", () => {
    if (!hasCustomPosition()) {
      return;
    }
    applyWidgetLayout();
  });
  window.addEventListener("focus", () => {
    if (!widgetInitialized) {
      return;
    }
    sendHeartbeat().catch(() => {});
    refreshWidgetView().catch(() => {});
  });
  document.addEventListener("visibilitychange", () => {
    if (!widgetInitialized) {
      return;
    }
    sendHeartbeat().catch(() => {});
    refreshWidgetView().catch(() => {});
  });
  function handleFullscreenChange() {
    if (!widgetInitialized) {
      return;
    }
    if (isFullscreenActive()) {
      removeWidget();
      return;
    }
    refreshWidgetView().catch(() => {});
    sendHeartbeat().catch(() => {});
  }
  document.addEventListener("fullscreenchange", handleFullscreenChange);
  document.addEventListener("webkitfullscreenchange", handleFullscreenChange);
  document.addEventListener("mozfullscreenchange", handleFullscreenChange);
  document.addEventListener("MSFullscreenChange", handleFullscreenChange);

  if (isExtensionContextAvailable()) {
    chrome.storage.onChanged.addListener((changes, areaName) => {
      if (areaName === "sync" && changes.settings) {
        loadSettings()
          .then(() => activateWidgetRuntime({ deferHeartbeat: true }))
          .catch(() => {});
      }
      if (areaName === "local" && changes[WIDGET_LAYOUT_STORAGE_KEY]) {
        if (!dragState) {
          widgetLayout = normalizeWidgetLayout(changes[WIDGET_LAYOUT_STORAGE_KEY].newValue);
          applyWidgetLayout();
        }
      }
      if (areaName === "local" && changes[AVATAR_ASSET_STORAGE_KEY]) {
        cachedAvatarImageAssets = normalizeAvatarImageAssets(changes[AVATAR_ASSET_STORAGE_KEY].newValue);
        refreshWidgetView().catch(() => {});
      }
      if (areaName === "local" && changes.wellbeingState) {
        refreshWidgetView().catch(() => {});
      }
    });
    chrome.runtime.onMessage.addListener((message) => {
      if (message?.type === "WELLBEING_STATE_RESET") {
        refreshWidgetView().catch(() => {});
        return;
      }
      if (message?.type !== "APPLY_WELLBEING_WIDGET_LAYOUT" || dragState) {
        return;
      }
      widgetLayout = normalizeWidgetLayout(message.layout);
      applyWidgetLayout();
    });
  }

  scheduleWidgetInitialization();
})();
