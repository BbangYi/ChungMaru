const els = {
  shell: document.querySelector(".warning-shell"),
  title: document.getElementById("warningTitle"),
  description: document.getElementById("warningDescription"),
  domain: document.getElementById("warningDomain"),
  category: document.getElementById("warningCategory"),
  risk: document.getElementById("warningRisk"),
  reasons: document.getElementById("warningReasons"),
  backButton: document.getElementById("backButton"),
  continueButton: document.getElementById("continueButton"),
  status: document.getElementById("warningStatus")
};

function getWarningId() {
  return new URLSearchParams(location.search).get("id") || "";
}

function setStatus(message) {
  els.status.textContent = message || "";
}

function setBusy(isBusy) {
  els.backButton.disabled = isBusy;
  els.continueButton.disabled =
    isBusy || els.continueButton.hidden || els.continueButton.dataset.canContinue === "false";
}

function formatRisk(value) {
  const numeric = Number(value || 0);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return "-";
  }
  return `${Math.round(numeric * 100)}%`;
}

function renderPayload(payload) {
  const policy = payload?.policy || {};
  const verdict = String(policy.verdict || "warning");
  els.shell.dataset.verdict = verdict;
  els.title.textContent =
    verdict === "block" ? "차단 권장 사이트입니다" : "접속 전 확인이 필요합니다";
  els.description.textContent =
    String(policy.agent?.response || "").trim() ||
    "청마루가 사이트 주소와 위험 신호를 확인했습니다. 계속 이동할지 선택하세요.";
  els.domain.textContent = policy.domain || "-";
  els.category.textContent = policy.site_category || "-";
  els.risk.textContent = formatRisk(policy.risk_score);
  const canContinue = payload?.can_continue !== false;
  els.continueButton.dataset.canContinue = canContinue ? "true" : "false";
  els.continueButton.hidden = !canContinue;
  els.continueButton.disabled = !canContinue;
  els.continueButton.textContent = verdict === "block" ? "위험 감수하고 계속" : "계속 접속";
  if (!canContinue) {
    setStatus("현재 민감도에서는 돌아가기만 허용됩니다.");
  } else {
    setStatus("");
  }

  while (els.reasons.firstChild) {
    els.reasons.removeChild(els.reasons.firstChild);
  }
  for (const reason of Array.isArray(policy.reasons) ? policy.reasons.slice(0, 6) : []) {
    const item = document.createElement("li");
    item.textContent = String(reason);
    els.reasons.appendChild(item);
  }
}

async function loadPayload() {
  const warningId = getWarningId();
  if (!warningId) {
    throw new Error("SITE_WARNING_ID_MISSING");
  }
  const response = await chrome.runtime.sendMessage({
    type: "GET_SITE_WARNING_PAYLOAD",
    warningId
  });
  if (!response?.ok || !response.payload) {
    throw new Error("SITE_WARNING_PAYLOAD_MISSING");
  }
  return response.payload;
}

async function continueToSite() {
  if (els.continueButton.dataset.canContinue === "false") {
    setStatus("현재 민감도에서는 돌아가기만 허용됩니다.");
    return;
  }
  setBusy(true);
  setStatus("사이트로 이동 중입니다.");
  try {
    const response = await chrome.runtime.sendMessage({
      type: "ALLOW_SITE_WARNING_AND_CONTINUE",
      warningId: getWarningId()
    });
    if (!response?.ok) {
      throw new Error(response?.reason || "ALLOW_SITE_WARNING_FAILED");
    }
  } catch (error) {
    setBusy(false);
    setStatus(`이동 실패: ${error?.message || error}`);
  }
}

function goBack() {
  if (history.length > 1) {
    history.back();
    return;
  }
  location.replace("about:blank");
}

async function initialize() {
  try {
    renderPayload(await loadPayload());
  } catch {
    els.title.textContent = "경고 정보를 불러오지 못했습니다";
    els.description.textContent = "이전 페이지로 돌아가거나 주소를 다시 확인하세요.";
    els.domain.textContent = "-";
    els.category.textContent = "-";
    els.risk.textContent = "-";
    els.continueButton.disabled = true;
  }

  els.backButton.addEventListener("click", goBack);
  els.continueButton.addEventListener("click", () => {
    continueToSite().catch((error) => {
      setBusy(false);
      setStatus(`이동 실패: ${error?.message || error}`);
    });
  });
}

initialize().catch((error) => {
  setStatus(`초기화 실패: ${error?.message || error}`);
});
