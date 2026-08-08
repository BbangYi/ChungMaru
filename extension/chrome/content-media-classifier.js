(function initializeChungmaruNsfwPolicy(root) {
  const THRESHOLDS = Object.freeze({
    explicitBlock: 0.65,
    contextualExplicitBlock: 0.45,
    contextualSexyBlock: 0.8,
    strongContextDrawingBlock: 0.4,
    ambiguousExplicit: 0.35,
    ambiguousSexy: 0.65
  });

  function clampScore(value) {
    const number = Number(value || 0);
    if (!Number.isFinite(number)) return 0;
    return Math.max(0, Math.min(1, number));
  }

  function normalizeScores(scores) {
    return {
      Drawing: clampScore(scores?.Drawing),
      Hentai: clampScore(scores?.Hentai),
      Neutral: clampScore(scores?.Neutral),
      Porn: clampScore(scores?.Porn),
      Sexy: clampScore(scores?.Sexy)
    };
  }

  function evaluate(scores, adultContext = false, strongAdultContext = false) {
    const normalized = normalizeScores(scores);
    const explicitScore = Math.min(1, normalized.Porn + normalized.Hentai);
    if (explicitScore >= THRESHOLDS.explicitBlock) {
      return {
        verdict: "block",
        reason: "nsfw explicit visual",
        explicitScore,
        sexyScore: normalized.Sexy
      };
    }
    if (adultContext && explicitScore >= THRESHOLDS.contextualExplicitBlock) {
      return {
        verdict: "block",
        reason: "nsfw visual plus adult context",
        explicitScore,
        sexyScore: normalized.Sexy
      };
    }
    if (adultContext && normalized.Sexy >= THRESHOLDS.contextualSexyBlock) {
      return {
        verdict: "block",
        reason: "nsfw suggestive visual plus adult context",
        explicitScore,
        sexyScore: normalized.Sexy
      };
    }
    if (strongAdultContext && normalized.Drawing >= THRESHOLDS.strongContextDrawingBlock) {
      return {
        verdict: "block",
        reason: "nsfw drawing plus explicit adult context",
        explicitScore,
        sexyScore: normalized.Sexy
      };
    }
    if (
      explicitScore >= THRESHOLDS.ambiguousExplicit ||
      normalized.Sexy >= THRESHOLDS.ambiguousSexy
    ) {
      return {
        verdict: "ambiguous",
        reason: "nsfw visual confidence below block threshold",
        explicitScore,
        sexyScore: normalized.Sexy
      };
    }
    return {
      verdict: "benign",
      reason: "nsfw visual classified benign",
      explicitScore,
      sexyScore: normalized.Sexy
    };
  }

  const api = Object.freeze({ THRESHOLDS, normalizeScores, evaluate });
  root.ChungmaruNsfwPolicy = api;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);
