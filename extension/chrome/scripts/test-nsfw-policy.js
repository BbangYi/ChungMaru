const assert = require("node:assert/strict");
const policy = require("../content-media-classifier.js");

const explicit = policy.evaluate({ Porn: 0.62, Hentai: 0.04, Sexy: 0.1 }, false);
assert.equal(explicit.verdict, "block");
assert.equal(explicit.reason, "nsfw explicit visual");

const contextualExplicit = policy.evaluate({ Porn: 0.42, Hentai: 0.04, Sexy: 0.1 }, true);
assert.equal(contextualExplicit.verdict, "block");

const contextualSexy = policy.evaluate({ Porn: 0.03, Hentai: 0.01, Sexy: 0.82 }, true);
assert.equal(contextualSexy.verdict, "block");

const contextFreeSexy = policy.evaluate({ Porn: 0.03, Hentai: 0.01, Sexy: 0.82 }, false);
assert.equal(contextFreeSexy.verdict, "ambiguous");

const contextualDrawing = policy.evaluate({ Drawing: 0.41, Neutral: 0.55, Sexy: 0.04 }, true, true);
assert.equal(contextualDrawing.verdict, "block");
assert.equal(contextualDrawing.reason, "nsfw drawing plus explicit adult context");

const contextOnlyDrawing = policy.evaluate({ Drawing: 0.41, Neutral: 0.55, Sexy: 0.04 }, true, false);
assert.equal(contextOnlyDrawing.verdict, "benign");

const benign = policy.evaluate({ Neutral: 0.95, Porn: 0.01, Hentai: 0.01, Sexy: 0.02 }, false);
assert.equal(benign.verdict, "benign");

const boundary = policy.evaluate({ Porn: 0.44, Hentai: 0.01, Sexy: 0.79 }, true);
assert.equal(boundary.verdict, "block");

console.log("NSFW policy tests passed");
