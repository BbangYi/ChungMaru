#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const extensionRoot = path.resolve(__dirname, "..");
const contentScriptPath = path.join(extensionRoot, "content-script.js");
const source = fs.readFileSync(contentScriptPath, "utf8");

function fail(message) {
  console.error(message);
  process.exit(1);
}

function extractStringConstant(name) {
  const match = source.match(new RegExp(`const ${name} =\\s*"([^"]+)";`));
  if (!match) {
    fail(`${name} not found`);
  }
  return JSON.parse(`"${match[1]}"`);
}

function extractTemplateConstant(name) {
  const match = source.match(new RegExp(`const ${name} =\\s*\\n\\s*\`([\\s\\S]*?)\`;`));
  if (!match) {
    fail(`${name} template not found`);
  }
  return match[1];
}

const initialSeparator = extractStringConstant("KOREAN_INITIAL_SEPARATOR");
const obfuscationSeparator = extractStringConstant("KOREAN_OBFUSCATION_SEPARATOR");
const koreanWordCharClass = extractStringConstant("KOREAN_WORD_CHAR_CLASS");
const inflectionSuffix = extractStringConstant("KOREAN_PROFANITY_INFLECTION_SUFFIX");
const adultCommerceSeparator = extractStringConstant("KOREAN_ADULT_COMMERCE_SEPARATOR");
const adultCommerceTemplate = extractTemplateConstant("KOREAN_ADULT_COMMERCE_PATTERN");

if (!adultCommerceTemplate.includes("${KOREAN_ADULT_COMMERCE_SEPARATOR}")) {
  fail("KOREAN_ADULT_COMMERCE_PATTERN does not use KOREAN_ADULT_COMMERCE_SEPARATOR");
}

const adultCommercePattern = `(?:콜${adultCommerceSeparator}걸|성인${adultCommerceSeparator}(?:업소|마사지${adultCommerceSeparator}(?:업소|샵|구인|후기|추천|가격|예약|콜|출장|불법|알선))|출장${adultCommerceSeparator}(?:마사지|안마|만남)|유흥${adultCommerceSeparator}(?:업소|마사지|주점)|안마${adultCommerceSeparator}방|키스${adultCommerceSeparator}방|립${adultCommerceSeparator}카페|룸${adultCommerceSeparator}(?:싸롱|살롱)|셔츠${adultCommerceSeparator}룸|조건${adultCommerceSeparator}만남|성${adultCommerceSeparator}매매)`;
const adultCommerceRegex = new RegExp(adultCommercePattern, "i");
const initialRegex = new RegExp(`ㅅ${initialSeparator}ㅂ`, "i");
const initialInflectedRegex = new RegExp(
  [
    `ㅂ${initialSeparator}ㅅ(?:같(?:다|은|네)?)?`,
    `ㅈ${initialSeparator}ㄹ(?:하(?:네|다|고|면|며|니)?)?`,
    `ㅈ${initialSeparator}(?:(?:되|된|돼|됐)(?:다|네|고|면|며|니|는|서|어|겠(?:다|네)|버(?:렸(?:다|네)?|리(?:다|네|고|면)?|린|림|려)?)?|같(?:다|은|네|이)?)`
  ].join("|"),
  "i"
);
const koreanProfanityLeftEdge = `(?<![${koreanWordCharClass}])`;
const koreanProfanityRightEdge = `(?![${koreanWordCharClass}])`;
const safeCompoundRegex = /시[이]*발(?:점|역|택시)/i;
const safeCompoundFragmentRegex = /^시[이]*발$/i;
function koreanProfanityWithInflection(pattern) {
  return `${koreanProfanityLeftEdge}(?:${pattern})${inflectionSuffix}${koreanProfanityRightEdge}`;
}
function koreanProfanity(pattern) {
  return `${koreanProfanityLeftEdge}(?:${pattern})${koreanProfanityRightEdge}`;
}
function koreanSeparatedSyllables(...parts) {
  return parts.join(obfuscationSeparator);
}
const inflectedProfanityRegex = new RegExp(
  [
    koreanProfanityWithInflection(`씨[이]*${obfuscationSeparator}발`),
    koreanProfanityWithInflection(`시[이]*${obfuscationSeparator}발`),
    koreanProfanityWithInflection(koreanSeparatedSyllables("병", "신")),
    koreanProfanityWithInflection("씹"),
    koreanProfanity(`씹${obfuscationSeparator}감${obfuscationSeparator}다${obfuscationSeparator}살[ㅋㅎ]*`),
    koreanProfanity(`ㅈ${initialSeparator}(?:(?:되|된|돼|됐|대|댄|댔|댐)(?:다|네|고|면|며|니|는|서|어|겠(?:다|네)|버(?:렸(?:다|네)?|리(?:다|네|고|면)?|린|림|려)?)?|같(?:다|은|네|이)?)`),
    koreanProfanity(`좆${obfuscationSeparator}(?:(?:되|된|돼|됐|대|댄|댔|댐)(?:다|네|고|면|며|니|는|서|어|겠(?:다|네)|버(?:렸(?:다|네)?|리(?:다|네|고|면)?|린|림|려)?)?)`),
    koreanProfanity(`좇${obfuscationSeparator}(?:(?:되|된|돼|됐|대|댄|댔|댐)(?:다|네|고|면|며|니|는|서|어|겠(?:다|네)|버(?:렸(?:다|네)?|리(?:다|네|고|면)?|린|림|려)?)?)`),
    koreanProfanityWithInflection("좆"),
    koreanProfanityWithInflection(koreanSeparatedSyllables("맘", "충")),
    koreanProfanityWithInflection(koreanSeparatedSyllables("한", "남", "충")),
    koreanProfanityWithInflection(koreanSeparatedSyllables("틀", "딱")),
    koreanProfanityWithInflection(koreanSeparatedSyllables("쪽", "바", "리")),
    koreanProfanityWithInflection(koreanSeparatedSyllables("짱", "깨")),
    koreanProfanityWithInflection(koreanSeparatedSyllables("짱", "개")),
    koreanProfanityWithInflection(koreanSeparatedSyllables("니", "엄", "마")),
    koreanProfanityWithInflection(`너${obfuscationSeparator}[희히]${obfuscationSeparator}엄${obfuscationSeparator}마`),
    koreanProfanity(`꺼${obfuscationSeparator}[져저](?:라)?`),
    koreanProfanity(`닥${obfuscationSeparator}(?:쳐|치)(?:라)?`),
    koreanProfanity(`죽${obfuscationSeparator}어(?:라)?`),
    koreanProfanity(`(?:뒤|디|뒈)${obfuscationSeparator}[져저](?:라)?`),
    koreanProfanity(`(?:뒤|디|뒈)${obfuscationSeparator}지(?:긴(?:(?:하|한)(?:다|네|고|면)?)?|다|네|고|면|냐|겠(?:다|네)?|는|게)?`),
    koreanProfanity(koreanSeparatedSyllables("뒤", "질", "래")),
    koreanProfanityWithInflection("죽여(?:버릴|버린|버리|버려|버림)?"),
    koreanProfanityWithInflection("미친"),
    koreanProfanityWithInflection(`개${obfuscationSeparator}[새세]${obfuscationSeparator}[끼키]`)
  ].join("|"),
  "i"
);

const positiveCases = [
  "콜걸성인마사지",
  "콜걸 성인마사지",
  "콜.걸 성인-마사지",
  "출장마사지 콜걸",
  "출장 마사지 후기",
  "출장안마 후기",
  "출장 만남 광고",
  "성인업소 홍보",
  "성인 마사지 후기",
  "성인 마사지 예약",
  "유흥업소 후기",
  "유흥 마사지 추천",
  "유흥주점 후기",
  "안마방 위치",
  "키스방 후기",
  "립카페 추천",
  "룸싸롱 가격",
  "룸살롱 후기",
  "셔츠룸 위치",
  "조건만남 광고",
  "성매매 알선",
  "adult-webtoon-plus.kr 콜걸성인마사지"
];

const negativeCases = [
  "성인 교육 프로그램",
  "성인 대상 금융 교육",
  "성인 문해 교육 기사",
  "성인 대상 마사지 교육",
  "성인 마사지 자격증",
  "출장 일정 안내",
  "출장 수리 기사 예약",
  "마사지 자격증 교육",
  "유흥 산업 분석 보고서",
  "주점 창업 통계",
  "카페 추천 목록",
  "살롱 문화사",
  "셔츠 보관 룸 인테리어",
  "안내방송 확인"
];

const initialPositiveCases = [
  "ㅅㅂ",
  "ㅅ ㅂ",
  "ㅅ.ㅂ",
  "ㅅ/ㅂ",
  "ㅅ-ㅂ"
];

const initialInflectedPositiveCases = [
  "ㅂㅅ같다",
  "ㅈㄹ하네",
  "ㅈ된다 진짜",
  "ㅈ 된다 진짜",
  "ㅈ됐네",
  "ㅈ 됐네",
  "ㅈ돼버림",
  "ㅈ같네"
];

const initialNegativeCases = [
  "ㅅ 대상 ㅂ",
  "ㅅ성인ㅂ",
  "ㅅ검색어ㅂ"
];

const inflectedPositiveCases = [
  "씨발의 의미",
  "씨 발의 의미",
  "시발이 뭐야",
  "시-발처럼 기호를 섞은 문장",
  "병신아 꺼져",
  "병 신처럼 띄어쓴 표현",
  "씹을 할 놈",
  "음원으로 만든게 씹감다살ㅋㅋ",
  "좆된다 진짜",
  "ㅈ댄다 진짜",
  "ㅈ 댄다 진짜",
  "좆댄다 진짜",
  "좆 됐다",
  "개새끼들",
  "개 새 끼처럼 공백을 많이 넣은 표현",
  "좆같네",
  "맘충 뜻",
  "맘 충 뜻",
  "짱깨 뜻",
  "짱개 뜻",
  "한 남 충 뜻",
  "틀딱 뜻",
  "쪽바리 뜻",
  "쪽 바 리 뜻",
  "니 엄마 패드립",
  "너희엄마 패드립",
  "꺼져라 표현",
  "꺼 져라 표현",
  "닥쳐라 뜻",
  "죽어라 협박",
  "죽 어라 협박",
  "뒤져라 뜻",
  "뒤지긴한다",
  "와 진짜 뒤 지긴한다",
  "와 진짜 뒤지긴한다 좆된다 진짜",
  "뒤질래 협박",
  "죽여버릴 거야",
  "미친..."
];

const inflectedNegativeCases = [
  "시발점",
  "시 발점",
  "시발역",
  "시발택시",
  "병신도",
  "병신자"
];

for (const text of positiveCases) {
  if (!adultCommerceRegex.test(text)) {
    fail(`expected adult-commerce pattern to match: ${text}`);
  }
}

for (const text of negativeCases) {
  if (adultCommerceRegex.test(text)) {
    fail(`expected adult-commerce pattern not to match: ${text}`);
  }
}

for (const text of initialPositiveCases) {
  if (!initialRegex.test(text)) {
    fail(`expected initial profanity pattern to match: ${text}`);
  }
}

for (const text of initialInflectedPositiveCases) {
  if (!initialInflectedRegex.test(text)) {
    fail(`expected initial inflected profanity pattern to match: ${text}`);
  }
}

for (const text of initialNegativeCases) {
  if (initialRegex.test(text)) {
    fail(`expected initial profanity pattern not to match: ${text}`);
  }
}

for (const text of inflectedPositiveCases) {
  if (!inflectedProfanityRegex.test(text)) {
    fail(`expected inflected profanity pattern to match: ${text}`);
  }
}

for (const text of inflectedNegativeCases) {
  if (inflectedProfanityRegex.test(text)) {
    fail(`expected inflected profanity pattern not to match: ${text}`);
  }
}

if (!source.includes("KOREAN_ADULT_COMMERCE_PATTERN,")) {
  fail("KOREAN_ADULT_COMMERCE_PATTERN is not included in HIGH_SIGNAL_PROFANITY_PATTERN");
}

if (!source.includes("koreanProfanityWithInflection(\"씹\")")) {
  fail("inflected Korean profanity helper is not applied to the 씹 pattern");
}

if (!source.includes("ㅈ${KOREAN_INITIAL_SEPARATOR}(?:(?:되|된|돼|됐|대|댄|댔|댐)")) {
  fail("obfuscated ㅈ되다-style profanity pattern is not included in HIGH_SIGNAL_PROFANITY_PATTERN");
}

if (!safeCompoundRegex.test("시발점") || !safeCompoundFragmentRegex.test("시발")) {
  fail("safe compound fragment guard is not represented by the test harness");
}

if (
  !source.includes("SAFE_HIGH_SIGNAL_COMPOUND_FRAGMENT_PATTERN") ||
  !source.includes("SAFE_HIGH_SIGNAL_CONTEXT_PATTERN") ||
  !source.includes("국제차량제작\\s+시[이]*발") ||
  !source.includes("국가중요과학기술자료\\s*#?시[이]*발") ||
  !source.includes("SAFE_HIGH_SIGNAL_CONTEXT_PATTERN.test(localContext)") ||
  !source.includes("hasUnsafeHighSignalMatch(stateText, contextText)") ||
  !source.includes("renderPreconcealNodeState(candidate.state, sourceText, contextText)") ||
  !source.includes("hasUnsafeHighSignalMatch(unitText, unitText)")
) {
  fail("preconceal high-signal checks do not use compound-safe context");
}

console.log("high-signal pattern ok");
