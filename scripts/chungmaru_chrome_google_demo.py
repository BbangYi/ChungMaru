#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import csv
import io
import json
import shutil
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path
from typing import Any

from chungmaru_chrome_latency_smoke import (
    CdpWebSocket,
    build_extension_settings,
    create_tab,
    get_last_stats,
    send_to_fixture_tab,
    wait_for_page_ready,
    wait_for_service_worker,
)


DEFAULT_EXTENSION_DIR = Path("extension/chrome")
DEFAULT_CHROME_FOR_TESTING = Path(
    "/private/tmp/chungmaru-chrome-for-testing/chrome/mac_arm-149.0.7827.54/"
    "chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing"
)
DEFAULT_CHROME_APP = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
INTERVENTION_MODE_LABELS = {
    "mask": "가리기",
    "blur": "흐리기",
    "hide": "숨기기",
    "remove": "삭제",
}
INTERVENTION_MODE_ORDER = ("mask", "blur", "hide", "remove")
QUERY_SCENARIOS = [
    {
        "id": "core-profanity-direct",
        "query": "병신아 꺼져",
        "category": "profanity",
        "expected_result": "mask-spans",
        "sets": ["moderation-core", "moderation-speed", "full", "profanity"],
    },
    {
        "id": "speed-profanity-sibal",
        "query": "씨발 진짜 짜증나네",
        "category": "profanity",
        "expected_result": "mask-spans",
        "sets": ["moderation-speed", "full", "profanity"],
    },
    {
        "id": "speed-profanity-gaesaekki",
        "query": "개새끼 뭐하는 거야",
        "category": "profanity",
        "expected_result": "mask-spans",
        "sets": ["moderation-speed", "full", "profanity"],
    },
    {
        "id": "core-hate-gender-slur",
        "query": "한남충은 답이 없다",
        "category": "hate",
        "expected_result": "mask-spans",
        "sets": ["moderation-core", "moderation-speed", "full", "hate"],
    },
    {
        "id": "speed-hate-age-slur",
        "query": "틀딱들은 버스에서 내려라",
        "category": "hate",
        "expected_result": "mask-spans",
        "sets": ["moderation-speed", "full", "hate"],
    },
    {
        "id": "speed-hate-foreigner-slur",
        "query": "외노자들 다 추방해야 해",
        "category": "hate",
        "expected_result": "mask-spans",
        "sets": ["moderation-speed", "full", "hate"],
    },
    {
        "id": "core-toxicity-threat",
        "query": "너 한번만 더 그러면 죽여버린다",
        "category": "toxicity",
        "expected_result": "mask-spans",
        "sets": ["moderation-core", "moderation-speed", "full", "toxicity"],
    },
    {
        "id": "speed-toxicity-threat-muted",
        "query": "닥쳐 가만 안 둬",
        "category": "toxicity",
        "expected_result": "mask-spans",
        "sets": ["moderation-speed", "full", "toxicity"],
    },
    {
        "id": "speed-toxicity-warning",
        "query": "뒤질래 협박",
        "category": "toxicity",
        "expected_result": "mask-spans",
        "sets": ["moderation-speed", "full", "toxicity"],
    },
    {
        "id": "clean-policy-law",
        "query": "차별금지법 기사",
        "category": "clean-topic-bias",
        "expected_result": "allow",
        "sets": ["balanced", "full", "clean-regression"],
    },
    {
        "id": "clean-minority-topic",
        "query": "성소수자 인권 기사",
        "category": "clean-topic-bias",
        "expected_result": "allow",
        "sets": ["full", "clean-regression"],
    },
    {
        "id": "clean-adjacent-schedule",
        "query": "출장 일정 안내",
        "category": "benign-adjacent",
        "expected_result": "allow",
        "sets": ["balanced", "full", "clean-regression"],
    },
    {
        "id": "clean-adult-education",
        "query": "성인 대상 마사지 교육",
        "category": "benign-adjacent",
        "expected_result": "allow",
        "sets": ["full", "clean-regression"],
    },
    {
        "id": "adult-commerce-compact",
        "query": "콜걸성인마사지",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["balanced", "full", "adult-commerce"],
    },
    {
        "id": "adult-commerce-mixed",
        "query": "출장 마사지 콜걸성인마사지",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["balanced", "full", "adult-commerce"],
    },
    {
        "id": "adult-commerce-conditions",
        "query": "출장안마 조건만남",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["full", "adult-commerce"],
    },
    {
        "id": "initial-consonant-spaced",
        "query": "ㅅ ㅂ 뜻",
        "category": "bypass-initial-consonant",
        "expected_result": "mask-spans",
        "sets": ["balanced", "full", "bypass"],
    },
    {
        "id": "initial-consonant-compact",
        "query": "ㅁㅊㄴ 판례",
        "category": "bypass-initial-consonant",
        "expected_result": "mask-spans",
        "sets": ["balanced", "full", "bypass"],
    },
    {
        "id": "spaced-profanity",
        "query": "ㅈ ㄴ 짜증",
        "category": "bypass-spacing",
        "expected_result": "mask-spans",
        "sets": ["full", "bypass"],
    },
    {
        "id": "direct-profanity-reference",
        "query": "씨발 나무위키",
        "category": "direct-profanity",
        "expected_result": "mask-spans",
        "sets": ["full", "direct-profanity"],
    },
    {
        "id": "site-risk-search",
        "query": "adult-webtoon-plus.kr dcinside.com",
        "category": "site-risk",
        "expected_result": "site-warning",
        "sets": ["balanced", "full", "site-risk"],
    },
    {
        "id": "diverse-snippet-etymology",
        "query": "네 어머니와 씹을 할 놈 뜻",
        "category": "direct-profanity-snippet",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression", "full", "direct-profanity"],
    },
    {
        "id": "diverse-initial-sibal-definition",
        "query": "ㅆ ㅂ 뜻",
        "category": "bypass-initial-consonant",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression", "full", "bypass"],
    },
    {
        "id": "diverse-initial-byeongsin-case",
        "query": "ㅂ ㅅ 판례",
        "category": "bypass-initial-consonant",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression", "full", "bypass"],
    },
    {
        "id": "diverse-romanized-profanity",
        "query": "tlqkf 뜻",
        "category": "bypass-romanized",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression", "full", "bypass"],
    },
    {
        "id": "diverse-adult-condition-meeting",
        "query": "조건만남 후기",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression", "full", "adult-commerce"],
    },
    {
        "id": "diverse-adult-room-salon",
        "query": "룸싸롱 가격",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression", "full", "adult-commerce"],
    },
    {
        "id": "diverse-hate-mom-slur",
        "query": "맘충 뜻",
        "category": "hate",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression", "full", "hate"],
    },
    {
        "id": "diverse-hate-ethnic-slur",
        "query": "짱깨 뜻",
        "category": "hate",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression", "full", "hate"],
    },
    {
        "id": "diverse-toxicity-threat-explicit",
        "query": "죽여버릴거야 협박",
        "category": "toxicity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression", "full", "toxicity"],
    },
    {
        "id": "diverse-clean-policy-issue",
        "query": "차별금지법 쟁점",
        "category": "clean-topic-bias",
        "expected_result": "allow",
        "sets": ["diverse-regression", "full", "clean-regression"],
    },
    {
        "id": "diverse-clean-minority-report",
        "query": "성소수자 인권 보도",
        "category": "clean-topic-bias",
        "expected_result": "allow",
        "sets": ["diverse-regression", "full", "clean-regression"],
    },
    {
        "id": "diverse-clean-adult-massage-license",
        "query": "성인 마사지 자격증",
        "category": "benign-adjacent",
        "expected_result": "allow",
        "sets": ["diverse-regression", "full", "clean-regression"],
    },
    {
        "id": "diverse2-family-insult-definition",
        "query": "니엄마 패드립 뜻",
        "category": "profanity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-2", "full", "profanity"],
    },
    {
        "id": "diverse2-spaced-jonna-definition",
        "query": "ㅈ ㄴ 빡치네 뜻",
        "category": "bypass-spacing",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-2", "full", "bypass"],
    },
    {
        "id": "diverse2-hate-korean-male-slur",
        "query": "한남충 뜻",
        "category": "hate",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-2", "full", "hate"],
    },
    {
        "id": "diverse2-hate-age-slur",
        "query": "틀딱 뜻",
        "category": "hate",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-2", "full", "hate"],
    },
    {
        "id": "diverse2-hate-japanese-slur",
        "query": "쪽바리 뜻",
        "category": "hate",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-2", "full", "hate"],
    },
    {
        "id": "diverse2-hate-ethnic-slur-variant",
        "query": "짱개 뜻",
        "category": "hate",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-2", "full", "hate"],
    },
    {
        "id": "diverse2-toxicity-threat-question",
        "query": "뒤질래 협박",
        "category": "toxicity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-2", "full", "toxicity"],
    },
    {
        "id": "diverse2-adult-kiss-room",
        "query": "키스방 후기",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression-2", "full", "adult-commerce"],
    },
    {
        "id": "diverse2-adult-lip-cafe",
        "query": "립카페 추천",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression-2", "full", "adult-commerce"],
    },
    {
        "id": "diverse2-adult-anma-room",
        "query": "안마방 위치",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression-2", "full", "adult-commerce"],
    },
    {
        "id": "diverse2-clean-sibaljeom",
        "query": "시발점 뜻",
        "category": "clean-query-page-profanity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-2", "full", "clean-regression"],
    },
    {
        "id": "diverse2-clean-massage-education",
        "query": "마사지 자격증 교육",
        "category": "benign-adjacent",
        "expected_result": "allow",
        "sets": ["diverse-regression-2", "full", "clean-regression"],
    },
    {
        "id": "diverse2-clean-policy-debate",
        "query": "차별금지법 찬반 토론",
        "category": "clean-topic-bias",
        "expected_result": "allow",
        "sets": ["diverse-regression-2", "full", "clean-regression"],
    },
    {
        "id": "diverse2-clean-human-rights-report",
        "query": "장애인 인권 보도",
        "category": "clean-topic-bias",
        "expected_result": "allow",
        "sets": ["diverse-regression-2", "full", "clean-regression"],
    },
    {
        "id": "diverse3-profanity-sipal-variant",
        "query": "시팔 뜻",
        "category": "direct-profanity-variant",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-3", "full", "direct-profanity"],
    },
    {
        "id": "diverse3-profanity-gaesaekki-meaning",
        "query": "개새끼 의미",
        "category": "direct-profanity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-3", "full", "direct-profanity"],
    },
    {
        "id": "diverse3-initial-ssibal-question",
        "query": "ㅆㅂ 뭐야",
        "category": "bypass-initial-consonant",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-3", "full", "bypass"],
    },
    {
        "id": "diverse3-initial-jiral-definition",
        "query": "ㅈ ㄹ 뜻",
        "category": "bypass-initial-consonant",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-3", "full", "bypass"],
    },
    {
        "id": "diverse3-romanized-byeongsin",
        "query": "qudtls 뜻",
        "category": "bypass-romanized",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-3", "full", "bypass"],
    },
    {
        "id": "diverse3-hate-family-negeumma",
        "query": "느금마 뜻",
        "category": "hate-family-insult",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-3", "full", "hate"],
    },
    {
        "id": "diverse3-hate-family-nigeumma",
        "query": "니금마 패드립",
        "category": "hate-family-insult",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-3", "full", "hate"],
    },
    {
        "id": "diverse3-toxicity-kkeojyeo",
        "query": "꺼져 표현",
        "category": "toxicity-dismissal",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-3", "full", "toxicity"],
    },
    {
        "id": "diverse3-toxicity-dakchyeo",
        "query": "닥쳐 뜻",
        "category": "toxicity-command",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-3", "full", "toxicity"],
    },
    {
        "id": "diverse3-toxicity-die-threat",
        "query": "죽어 협박",
        "category": "toxicity-threat",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-3", "full", "toxicity"],
    },
    {
        "id": "diverse3-adult-shirt-room",
        "query": "셔츠룸 위치",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression-3", "full", "adult-commerce"],
    },
    {
        "id": "diverse3-adult-sex-trade",
        "query": "성매매 알선",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression-3", "full", "adult-commerce"],
    },
    {
        "id": "diverse3-adult-yuheung-pub",
        "query": "유흥주점 후기",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression-3", "full", "adult-commerce"],
    },
    {
        "id": "diverse3-adult-business-trip-anma",
        "query": "출장안마 후기",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression-3", "full", "adult-commerce"],
    },
    {
        "id": "diverse3-clean-sibal-station",
        "query": "시발역 위치",
        "category": "clean-adjacent",
        "expected_result": "allow",
        "sets": ["diverse-regression-3", "full", "clean-regression"],
    },
    {
        "id": "diverse3-clean-adult-literacy",
        "query": "성인 문해 교육",
        "category": "benign-adjacent",
        "expected_result": "allow",
        "sets": ["diverse-regression-3", "full", "clean-regression"],
    },
    {
        "id": "diverse3-clean-business-trip-repair",
        "query": "출장 수리 기사 예약",
        "category": "benign-adjacent",
        "expected_result": "allow",
        "sets": ["diverse-regression-3", "full", "clean-regression"],
    },
    {
        "id": "diverse3-clean-announcement",
        "query": "안내방송 확인",
        "category": "benign-adjacent",
        "expected_result": "allow",
        "sets": ["diverse-regression-3", "full", "clean-regression"],
    },
    {
        "id": "diverse4-profanity-byeongsin-direct",
        "query": "병신 뜻",
        "category": "direct-profanity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-4", "full", "direct-profanity"],
    },
    {
        "id": "diverse4-profanity-jiral-direct",
        "query": "지랄 어원",
        "category": "direct-profanity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-4", "full", "direct-profanity"],
    },
    {
        "id": "diverse4-profanity-jonna-direct",
        "query": "존나 뜻",
        "category": "direct-profanity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-4", "full", "direct-profanity"],
    },
    {
        "id": "diverse4-profanity-jotgatda",
        "query": "좆같다 뜻",
        "category": "direct-profanity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-4", "full", "direct-profanity"],
    },
    {
        "id": "diverse4-profanity-ssip-suffix",
        "query": "씹새끼 뜻",
        "category": "direct-profanity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-4", "full", "direct-profanity"],
    },
    {
        "id": "diverse4-profanity-michin-nom",
        "query": "미친놈 표현",
        "category": "direct-profanity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-4", "full", "direct-profanity"],
    },
    {
        "id": "diverse4-initial-die",
        "query": "뒤져라 뜻",
        "category": "toxicity-threat",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-4", "full", "toxicity"],
    },
    {
        "id": "diverse4-romanized-ssibal",
        "query": "ssibal meaning",
        "category": "bypass-romanized",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-4", "full", "bypass"],
    },
    {
        "id": "diverse4-romanized-byungsin",
        "query": "byungsin meaning",
        "category": "bypass-romanized",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-4", "full", "bypass"],
    },
    {
        "id": "diverse4-english-fuck",
        "query": "fucking meaning korean",
        "category": "english-profanity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-4", "full", "direct-profanity"],
    },
    {
        "id": "diverse4-english-asshole",
        "query": "asshole meaning",
        "category": "english-profanity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-4", "full", "direct-profanity"],
    },
    {
        "id": "diverse4-adult-room-salon-variant",
        "query": "룸살롱 후기",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression-4", "full", "adult-commerce"],
    },
    {
        "id": "diverse4-adult-condition-site",
        "query": "조건만남 사이트",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression-4", "full", "adult-commerce"],
    },
    {
        "id": "diverse4-adult-adult-shop",
        "query": "성인업소 구인",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression-4", "full", "adult-commerce"],
    },
    {
        "id": "diverse4-clean-byeongsindo",
        "query": "병신도 여행",
        "category": "clean-query-page-profanity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-4", "full", "direct-profanity"],
    },
    {
        "id": "diverse4-clean-sibal-taxi",
        "query": "시발택시 예약",
        "category": "clean-adjacent",
        "expected_result": "allow",
        "sets": ["diverse-regression-4", "full", "clean-regression"],
    },
    {
        "id": "diverse4-clean-adult-finance",
        "query": "성인 대상 금융 교육",
        "category": "benign-adjacent",
        "expected_result": "allow",
        "sets": ["diverse-regression-4", "full", "clean-regression"],
    },
    {
        "id": "diverse4-clean-yuheung-industry",
        "query": "유흥 산업 분석 보고서",
        "category": "clean-query-page-adult-commerce",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-4", "full", "adult-commerce"],
    },
    {
        "id": "diverse4-clean-cafe-list",
        "query": "카페 추천 목록",
        "category": "benign-adjacent",
        "expected_result": "allow",
        "sets": ["diverse-regression-4", "full", "clean-regression"],
    },
    {
        "id": "diverse5-profanity-ssipal-variant",
        "query": "씨팔 욕설 판례",
        "category": "direct-profanity-variant",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-5", "full", "direct-profanity"],
    },
    {
        "id": "diverse5-profanity-gaesaeki-variant",
        "query": "개새키 뜻",
        "category": "direct-profanity-variant",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-5", "full", "direct-profanity"],
    },
    {
        "id": "diverse5-profanity-momchung-issue",
        "query": "맘충 논란",
        "category": "hate-slur",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-5", "full", "hate"],
    },
    {
        "id": "diverse5-profanity-jjanggae-news",
        "query": "짱개 표현 문제",
        "category": "hate-slur",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-5", "full", "hate"],
    },
    {
        "id": "diverse5-initial-ssibal-dot",
        "query": "ㅅ.ㅂ 의미",
        "category": "bypass-initial-consonant",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-5", "full", "bypass"],
    },
    {
        "id": "diverse5-initial-byeongsin-inflected",
        "query": "ㅂㅅ같다 뜻",
        "category": "bypass-initial-consonant",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-5", "full", "bypass"],
    },
    {
        "id": "diverse5-initial-jiral-inflected",
        "query": "ㅈㄹ하네 뜻",
        "category": "bypass-initial-consonant",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-5", "full", "bypass"],
    },
    {
        "id": "diverse5-romanized-gaesaekki",
        "query": "gae saekki meaning",
        "category": "bypass-romanized",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-5", "full", "bypass"],
    },
    {
        "id": "diverse5-romanized-jiral",
        "query": "jiral meaning",
        "category": "bypass-romanized",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-5", "full", "bypass"],
    },
    {
        "id": "diverse5-english-bitch",
        "query": "bitch korean meaning",
        "category": "english-profanity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-5", "full", "direct-profanity"],
    },
    {
        "id": "diverse5-english-motherfucker",
        "query": "motherfucker meaning korean",
        "category": "english-profanity",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-5", "full", "direct-profanity"],
    },
    {
        "id": "diverse5-toxicity-kill-command",
        "query": "죽어라 협박",
        "category": "toxicity-threat",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-5", "full", "toxicity"],
    },
    {
        "id": "diverse5-toxicity-dismiss-command",
        "query": "꺼져라 표현",
        "category": "toxicity-dismissal",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-5", "full", "toxicity"],
    },
    {
        "id": "diverse5-toxicity-shutup-command",
        "query": "닥쳐라 뜻",
        "category": "toxicity-command",
        "expected_result": "mask-spans",
        "sets": ["diverse-regression-5", "full", "toxicity"],
    },
    {
        "id": "diverse5-adult-yuheung-massage",
        "query": "유흥마사지 예약",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression-5", "full", "adult-commerce"],
    },
    {
        "id": "diverse5-adult-shirt-room-review",
        "query": "셔츠룸 후기",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression-5", "full", "adult-commerce"],
    },
    {
        "id": "diverse5-adult-callgirl-massage-spaced",
        "query": "콜걸 성인마사지",
        "category": "adult-commerce",
        "expected_result": "mask",
        "sets": ["diverse-regression-5", "full", "adult-commerce"],
    },
    {
        "id": "diverse5-clean-sibaljeom-economy",
        "query": "시발점 경제 용어",
        "category": "clean-adjacent",
        "expected_result": "allow",
        "sets": ["diverse-regression-5", "full", "clean-regression"],
    },
    {
        "id": "diverse5-clean-adult-education-program",
        "query": "성인 교육 프로그램",
        "category": "benign-adjacent",
        "expected_result": "allow",
        "sets": ["diverse-regression-5", "full", "clean-regression"],
    },
    {
        "id": "diverse5-clean-salon-culture",
        "query": "살롱 문화사",
        "category": "benign-adjacent",
        "expected_result": "allow",
        "sets": ["diverse-regression-5", "full", "clean-regression"],
    },
    {
        "id": "diverse5-clean-pub-startup",
        "query": "주점 창업 통계",
        "category": "benign-adjacent",
        "expected_result": "allow",
        "sets": ["diverse-regression-5", "full", "clean-regression"],
    },
]
DEFAULT_QUERY_SET = "moderation-core"
DEFAULT_QUERIES = [item["query"] for item in QUERY_SCENARIOS if DEFAULT_QUERY_SET in item["sets"]]
DEFAULT_WARNING_URL = "https://adult-webtoon-plus.kr/"
DEFAULT_YOUTUBE_QUERY = "시발 또 다시 보여줘야해?"
DEFAULT_YOUTUBE_TARGET_HINTS = "식케이,Sik-K,다시 보여줘야해,보여줘야해"
DEFAULT_MODE_SHOWCASE_QUERY = "병신아 꺼져"
DEFAULT_LATEST_OUTPUT_DIR = Path("evaluation/latency/results/current")
DEFAULT_ARCHIVE_OUTPUT_ROOT = Path("/private/tmp/chungmaru-chrome-demo-archive")
DEFAULT_APPEND_LATENCY_CSV = Path("evaluation/latency/results/current/chrome-demo-latency.csv")
DEFAULT_APPEND_LATENCY_JSONL = Path("evaluation/latency/results/current/chrome-demo-latency.jsonl")
GOOGLE_ALLOWED_PIPELINE_REASONS = {
    "input",
    "input-hot-path",
    "initial-editable-pass",
    "google-dynamic-content",
    "manual",
    "manual-request",
    "manual-request-after-inject",
}


def query_scenarios_for_set(name: str) -> list[dict[str, str]]:
    selected = []
    for item in QUERY_SCENARIOS:
        if name == "all" or name in item.get("sets", []):
            selected.append(item)
    if not selected:
        available = sorted({name for item in QUERY_SCENARIOS for name in item.get("sets", [])} | {"all"})
        raise SystemExit(f"Unknown query set: {name}. Available: {', '.join(available)}")
    return selected


def queries_for_set(name: str) -> list[str]:
    return [item["query"] for item in query_scenarios_for_set(name)]


def query_metadata(query: str) -> dict[str, str]:
    for item in QUERY_SCENARIOS:
        if item.get("query") == query:
            return {
                "query_category": item.get("category", "custom"),
                "expected_result": item.get("expected_result", ""),
                "query_scenario_id": item.get("id", "custom"),
            }
    return {
        "query_category": "custom",
        "expected_result": "",
        "query_scenario_id": "custom",
    }


def google_pipeline_reason(reason: str) -> str:
    normalized = str(reason or "").strip()
    return normalized if normalized in GOOGLE_ALLOWED_PIPELINE_REASONS else "google-dynamic-content"


def now_id() -> str:
    return datetime.now().strftime("%Y%m%dT%H%M%S")


def parse_viewport(value: str) -> tuple[int, int]:
    raw = value.lower().strip()
    if "x" not in raw:
        raise argparse.ArgumentTypeError("viewport must be WIDTHxHEIGHT")
    width, height = raw.split("x", 1)
    try:
        return int(width), int(height)
    except ValueError as error:
        raise argparse.ArgumentTypeError("viewport must be WIDTHxHEIGHT") from error


def detect_chrome_path(value: str | None) -> Path:
    if value:
        return Path(value)
    for candidate in (DEFAULT_CHROME_FOR_TESTING, DEFAULT_CHROME_APP):
        if candidate.exists():
            return candidate
    raise SystemExit("Chrome executable not found. Install Chrome for Testing or pass --chrome-path.")


def launch_chrome(args: argparse.Namespace) -> subprocess.Popen[bytes]:
    extension_dir = args.extension_dir.resolve()
    profile_dir = args.profile_dir.resolve()
    if args.clean_profile and profile_dir.exists():
        shutil.rmtree(profile_dir)
    profile_dir.mkdir(parents=True, exist_ok=True)
    args.chrome_log.parent.mkdir(parents=True, exist_ok=True)
    if args.chrome_log.exists():
        args.chrome_log.unlink()

    width, height = args.viewport
    command = [
        str(args.chrome_path),
        f"--user-data-dir={profile_dir}",
        f"--remote-debugging-port={args.debugging_port}",
        f"--window-size={width},{height}",
        "--force-device-scale-factor=1",
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-background-networking",
        "--disable-features=DisableLoadExtensionCommandLineSwitch",
        "--enable-logging=stderr",
        f"--disable-extensions-except={extension_dir}",
        f"--load-extension={extension_dir}",
        "about:blank",
    ]
    if getattr(args, "headless", False):
        command.insert(1, "--headless=new")
        command.insert(2, "--disable-gpu")
    else:
        command.insert(4, f"--window-position={getattr(args, 'window_position', '0,0')}")
        if getattr(args, "start_minimized", False):
            command.insert(5, "--start-minimized")
    log_handle = args.chrome_log.open("ab")
    try:
        return subprocess.Popen(command, stdout=log_handle, stderr=log_handle)
    finally:
        log_handle.close()


def demo_settings(args: argparse.Namespace) -> dict[str, Any]:
    settings = build_extension_settings(args)
    settings.update(
        {
            "siteProtectionEnabled": True,
            "siteNavigationWarningEnabled": True,
            "searchResultProtectionEnabled": True,
            "showWellbeingWidget": True,
            "wellbeingWidgetStyle": "soft",
        }
    )
    return settings


def set_demo_settings(worker: CdpWebSocket, settings: dict[str, Any]) -> dict[str, Any]:
    expression = (
        "(async () => {"
        f"await chrome.storage.sync.set({json.dumps({'settings': settings}, ensure_ascii=False)});"
        "await chrome.storage.local.clear();"
        "return await chrome.storage.sync.get('settings');"
        "})()"
    )
    result = worker.evaluate(expression, timeout_s=20)
    return result if isinstance(result, dict) else {"result": result}


def clone_settings(settings: dict[str, Any]) -> dict[str, Any]:
    return json.loads(json.dumps(settings, ensure_ascii=False))


def build_mode_demo_settings(
    base_settings: dict[str, Any],
    *,
    enabled: bool,
    intervention_mode: str | None = None,
) -> dict[str, Any]:
    settings = clone_settings(base_settings)
    settings["enabled"] = bool(enabled)
    if intervention_mode:
        settings["interventionMode"] = intervention_mode
    return settings


def apply_demo_settings_to_page(
    worker: CdpWebSocket,
    *,
    page_url_prefix: str,
    settings: dict[str, Any],
    inject_on_failure: bool = True,
) -> dict[str, Any]:
    storage_response = set_demo_settings(worker, settings)
    snapshot_response = send_to_fixture_tab(
        worker,
        page_url_prefix,
        {"type": "APPLY_SETTINGS_SNAPSHOT", "settings": settings},
        inject_on_failure=inject_on_failure,
        timeout_s=12,
    )
    return {
        "storage_response": storage_response,
        "snapshot_response": snapshot_response,
    }


def is_settings_write_successful(result: dict[str, Any] | None) -> bool:
    settings = result.get("settings") if isinstance(result, dict) else None
    return isinstance(settings, dict) and settings.get("backendEnabled") is True


def number_like(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def csv_value(value: Any) -> str | int | float:
    if value is None:
        return ""
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (str, int, float)):
        return value
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def nested_get(value: dict[str, Any], path: list[str], default: Any = None) -> Any:
    current: Any = value
    for key in path:
        if not isinstance(current, dict):
            return default
        current = current.get(key)
    return current if current is not None else default


def effective_masked_span_count(pipeline: dict[str, Any], diagnostics: dict[str, Any]) -> int:
    visible_count = int(
        max(
            number_like(diagnostics.get("renderBoxCount") if isinstance(diagnostics, dict) else 0),
            number_like(diagnostics.get("inlineMaskCount") if isinstance(diagnostics, dict) else 0),
            number_like(diagnostics.get("editableOverlayCount") if isinstance(diagnostics, dict) else 0),
        )
    )
    if visible_count > 0:
        return visible_count

    requested_count = max(
        number_like(nested_get(pipeline, ["last_stats", "requestedAnalysisCount"], 0)),
        number_like(nested_get(pipeline, ["trigger_response", "response", "stats", "requestedAnalysisCount"], 0)),
    )
    if requested_count <= 0:
        return 0

    values = [
        nested_get(pipeline, ["last_stats", "maskedSpanCount"], 0),
        nested_get(pipeline, ["google_light_response", "response", "maskedSpanCount"], 0),
        nested_get(pipeline, ["trigger_response", "response", "stats", "maskedSpanCount"], 0),
    ]
    return int(max(number_like(value) for value in values))


def google_search_url(query: str) -> str:
    params = urllib.parse.urlencode(
        {
            "q": query,
            "hl": "ko",
            "num": "10",
            "pws": "0",
            "safe": "off",
        }
    )
    return f"https://www.google.com/search?{params}"


def youtube_search_url(query: str) -> str:
    params = urllib.parse.urlencode({"search_query": query})
    return f"https://www.youtube.com/results?{params}"


def parse_csv(value: str) -> list[str]:
    return [part.strip() for part in str(value or "").split(",") if part.strip()]


def warmup_backend(
    api_base_url: str,
    *,
    timeout_s: float = 60.0,
    sensitivity: int | None = None,
) -> dict[str, Any]:
    url = f"{api_base_url.rstrip('/')}/warmup"
    payload = {
        "load_classifier": True,
        "load_span_detector": True,
        "run_span_probe": True,
        "sensitivity": sensitivity,
    }
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout_s) as response:
            raw = response.read().decode("utf-8")
        result = json.loads(raw) if raw else {}
        result["ok"] = bool(result.get("ok", True))
    except Exception as error:
        result = {"ok": False, "error": str(error), "url": url}
    result["client_wall_ms"] = round((time.perf_counter() - started) * 1000, 3)
    return result


def dismiss_google_consent(page: CdpWebSocket) -> bool:
    script = r"""
(() => {
  const labels = ["모두 동의", "동의", "Accept all", "I agree", "Agree"];
  const clickable = Array.from(document.querySelectorAll("button, input[type='submit'], div[role='button']"));
  for (const node of clickable) {
    const text = `${node.innerText || node.value || node.getAttribute("aria-label") || ""}`.trim();
    if (labels.some((label) => text.includes(label))) {
      node.click();
      return true;
    }
  }
  return false;
})()
"""
    try:
        return bool(page.evaluate(script, timeout_s=3))
    except Exception:
        return False


def dismiss_youtube_overlays(page: CdpWebSocket) -> dict[str, Any]:
    script = r"""
(() => {
  const labels = [
    "닫기",
    "나중에",
    "취소",
    "건너뛰기",
    "Dismiss",
    "Close",
    "No thanks",
    "Not now",
    "Skip",
    "Skip Ads",
    "Skip ad"
  ];
  let clicked = 0;
  let removed = 0;
  let pausedVideos = 0;

  const isVisible = (node) => {
    if (!(node instanceof Element)) return false;
    const rect = node.getBoundingClientRect();
    const style = getComputedStyle(node);
    return rect.width > 0 && rect.height > 0 && style.visibility !== "hidden" && style.display !== "none";
  };

  const labelOf = (node) =>
    `${node.innerText || node.value || node.getAttribute("aria-label") || node.getAttribute("title") || ""}`.trim();

  for (const node of document.querySelectorAll("button, input[type='button'], input[type='submit'], yt-button-renderer, div[role='button']")) {
    if (!isVisible(node)) continue;
    const text = labelOf(node);
    if (!text) continue;
    if (labels.some((label) => text.includes(label))) {
      node.click();
      clicked += 1;
      if (clicked >= 3) break;
    }
  }

  const noisySelectors = [
    ".ytp-ad-module",
    ".ytp-ad-overlay-container",
    ".ytp-ad-player-overlay",
    ".video-ads",
    "ytd-mealbar-promo-renderer",
    "yt-mealbar-promo-renderer",
    "ytd-popup-container tp-yt-paper-dialog",
    "tp-yt-paper-dialog",
    "ytd-consent-bump-v2-lightbox",
    "ytd-background-promo-renderer"
  ];
  for (const selector of noisySelectors) {
    for (const node of document.querySelectorAll(selector)) {
      const text = `${node.textContent || ""}`;
      if (
        selector.includes("ytp-ad") ||
        selector.includes("video-ads") ||
        /YouTube Premium|Get YouTube without the ads|광고 없이|Premium|동의|consent|광고|Skip Ads?/i.test(text) ||
        selector.includes("consent")
      ) {
        node.remove();
        removed += 1;
      }
    }
  }

  for (const video of document.querySelectorAll("video")) {
    try {
      video.muted = true;
      video.pause();
      pausedVideos += 1;
    } catch (_) {}
  }

  return { clicked, removed, pausedVideos };
})()
"""
    try:
        value = page.evaluate(script, timeout_s=3)
    except Exception as error:  # noqa: BLE001 - demo diagnostic only
        return {"ok": False, "error": str(error), "clicked": 0, "removed": 0}
    if isinstance(value, dict):
        value["ok"] = True
        return value
    return {"ok": True, "value": value, "clicked": 0, "removed": 0}


def capture_frame(page: CdpWebSocket, output: Path) -> None:
    result = page.call(
        "Page.captureScreenshot",
        {
            "format": "png",
            "captureBeyondViewport": False,
            "fromSurface": True,
        },
        timeout_s=10,
    )
    data = result.get("data")
    if not data:
        raise RuntimeError("Page.captureScreenshot returned no image data")
    output.write_bytes(base64.b64decode(data))


def build_video(
    frames_dir: Path,
    output: Path,
    fps: int,
    *,
    crf: int = 16,
    preset: str = "slow",
    pix_fmt: str = "yuv420p",
) -> None:
    command = [
        "ffmpeg",
        "-y",
        "-framerate",
        str(fps),
        "-i",
        str(frames_dir / "frame-%04d.png"),
        "-vf",
        "scale=trunc(iw/2)*2:trunc(ih/2)*2",
        "-c:v",
        "libx264",
        "-preset",
        str(preset),
        "-crf",
        str(crf),
        "-pix_fmt",
        str(pix_fmt),
        "-movflags",
        "+faststart",
        str(output),
    ]
    subprocess.run(command, check=True)


class FrameRecorder:
    def __init__(
        self,
        page: CdpWebSocket,
        frames_dir: Path,
        fps: int,
        metadata: dict[str, Any],
        capture_fps: int | None = None,
    ) -> None:
        self.page = page
        self.frames_dir = frames_dir
        self.fps = fps
        self.metadata = metadata
        self.index = 0
        self.capture_interval = max(1, round(fps / max(1, capture_fps or fps)))
        self.actual_capture_count = 0
        self.duplicated_frame_count = 0
        self.last_frame_path: Path | None = None

    def set_page(self, page: CdpWebSocket) -> None:
        self.page = page
        self.last_frame_path = None

    @property
    def video_seconds(self) -> float:
        return self.index / max(1, self.fps)

    def _capture_one(self, *, force_capture: bool = False) -> None:
        self.index += 1
        output = self.frames_dir / f"frame-{self.index:04d}.png"
        if (
            not force_capture
            and self.last_frame_path
            and self.capture_interval > 1
            and self.index % self.capture_interval != 1
        ):
            shutil.copyfile(self.last_frame_path, output)
            self.duplicated_frame_count += 1
            return

        try:
            capture_frame(self.page, output)
            self.actual_capture_count += 1
            self.last_frame_path = output
        except Exception as error:
            if not self.last_frame_path:
                raise
            shutil.copyfile(self.last_frame_path, output)
            self.duplicated_frame_count += 1
            self.metadata.setdefault("capture_errors", []).append(
                {
                    "frame": self.index,
                    "error": str(error),
                    "fallback": "duplicated_previous_frame",
                }
            )

    def hold(self, seconds: float, label: str) -> None:
        first_frame = self.index + 1
        frame_count = max(1, int(round(seconds * self.fps)))
        for _ in range(frame_count):
            self._capture_one()
            time.sleep(1 / self.fps)
        self.metadata.setdefault("timeline", []).append(
            {
                "label": label,
                "first_frame": first_frame,
                "last_frame": self.index,
                "seconds": seconds,
            }
        )

    def capture_step(self, label: str) -> None:
        first_frame = self.index + 1
        self._capture_one(force_capture=True)
        self.metadata.setdefault("timeline", []).append(
            {
                "label": label,
                "first_frame": first_frame,
                "last_frame": self.index,
                "seconds": round(1 / self.fps, 3),
            }
        )


def frame_start_seconds(first_frame: int, fps: int) -> float:
    return round(max(0, first_frame - 1) / max(1, fps), 3)


def frame_end_seconds(last_frame: int, fps: int) -> float:
    return round(max(0, last_frame) / max(1, fps), 3)


def page_location(page: CdpWebSocket) -> str:
    try:
        value = page.evaluate("window.location.href", timeout_s=3)
    except Exception:
        return ""
    return str(value or "")


def set_demo_caption(page: CdpWebSocket, title: str, detail: str = "") -> bool:
    script = f"""
(() => {{
  const title = {json.dumps(title, ensure_ascii=False)};
  const detail = {json.dumps(detail, ensure_ascii=False)};
  let root = document.getElementById("__chungmaru_demo_caption");
  if (!root) {{
    root = document.createElement("div");
    root.id = "__chungmaru_demo_caption";
    root.setAttribute("data-shieldtext-overlay", "true");
    root.style.position = "fixed";
    root.style.left = "28px";
    root.style.bottom = "24px";
    root.style.zIndex = "2147483647";
    root.style.maxWidth = "620px";
    root.style.padding = "14px 18px";
    root.style.borderRadius = "16px";
    root.style.border = "1px solid rgba(40, 40, 40, 0.16)";
    root.style.background = "rgba(250, 250, 247, 0.96)";
    root.style.boxShadow = "0 10px 28px rgba(0, 0, 0, 0.14)";
    root.style.color = "#1f1f1f";
    root.style.fontFamily = "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
    root.style.pointerEvents = "none";
    const titleNode = document.createElement("div");
    titleNode.id = "__chungmaru_demo_caption_title";
    titleNode.setAttribute("data-shieldtext-overlay", "true");
    titleNode.style.fontSize = "18px";
    titleNode.style.fontWeight = "800";
    titleNode.style.lineHeight = "1.35";
    const detailNode = document.createElement("div");
    detailNode.id = "__chungmaru_demo_caption_detail";
    detailNode.setAttribute("data-shieldtext-overlay", "true");
    detailNode.style.marginTop = "5px";
    detailNode.style.fontSize = "13px";
    detailNode.style.fontWeight = "650";
    detailNode.style.color = "#6f7478";
    detailNode.style.lineHeight = "1.35";
    root.append(titleNode, detailNode);
    document.documentElement.appendChild(root);
  }}
  document.getElementById("__chungmaru_demo_caption_title").textContent = title;
  document.getElementById("__chungmaru_demo_caption_detail").textContent = detail;
  return true;
}})()
"""
    try:
        return bool(page.evaluate(script, timeout_s=3))
    except Exception:
        return False


def run_options_demo_action(
    page: CdpWebSocket,
    action: str,
    *,
    backend_url: str,
    warning_url: str,
) -> dict[str, Any]:
    script = f"""
(async () => {{
  const action = {json.dumps(action)};
  const backendUrl = {json.dumps(backend_url)};
  const warningUrl = {json.dumps(warning_url)};
  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const statusText = () => document.getElementById("optionsStatus")?.textContent || "";
  const connectionText = () => document.getElementById("connectionStatusText")?.textContent || "";
  const policyPreview = () => document.getElementById("policyTestPreview")?.value || "";
  const byId = (id) => document.getElementById(id);
  const click = (id) => {{
    const node = byId(id);
    if (!node) return false;
    node.scrollIntoView({{ block: "center", inline: "nearest" }});
    node.click();
    return true;
  }};
  const setInput = (id, value) => {{
    const node = byId(id);
    if (!node) return false;
    node.scrollIntoView({{ block: "center", inline: "nearest" }});
    node.focus?.();
    node.value = String(value);
    node.dispatchEvent(new InputEvent("input", {{ bubbles: true, inputType: "insertText", data: String(value) }}));
    node.dispatchEvent(new Event("change", {{ bubbles: true }}));
    return true;
  }};
  const setChecked = (id, value) => {{
    const node = byId(id);
    if (!node) return false;
    node.scrollIntoView({{ block: "center", inline: "nearest" }});
    node.checked = Boolean(value);
    node.dispatchEvent(new Event("change", {{ bubbles: true }}));
    return true;
  }};
  const setSelect = (id, value) => {{
    const node = byId(id);
    if (!node) return false;
    node.scrollIntoView({{ block: "center", inline: "nearest" }});
    node.value = String(value);
    node.dispatchEvent(new Event("change", {{ bubbles: true }}));
    return true;
  }};
  const waitFor = async (predicate, timeoutMs = 8000) => {{
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {{
      if (predicate()) return true;
      await sleep(160);
    }}
    return false;
  }};

  if (action === "open-developer") {{
    const panel = document.querySelector(".developer-panel");
    if (panel) panel.open = true;
    setInput("developerPassword", "chungmaru-dev");
    click("unlockDeveloperButton");
    await waitFor(() => !byId("developerToolsSection")?.hidden, 4000);
    byId("developerToolsSection")?.scrollIntoView({{ block: "start", inline: "nearest" }});
  }} else if (action === "enable-backend") {{
    setChecked("backendEnabledToggle", true);
    setInput("backendApiBaseUrl", backendUrl);
    setInput("requestTimeoutMs", "10000");
    await waitFor(() => /백엔드 연동 켜짐|연결됨|응답 지연|확인 불가/.test(statusText() + " " + connectionText()), 6000);
  }} else if (action === "check-connection") {{
    click("checkConnectionButton");
    await waitFor(() => /연결 확인 완료|연결됨|응답 지연|연결 실패|연결할 수 없습니다/.test(statusText() + " " + connectionText()), 12000);
  }} else if (action === "run-site-policy") {{
    setInput("policyTestUrl", warningUrl);
    setChecked("policyTestForceRefresh", true);
    click("runPolicyTestButton");
    await waitFor(() => policyPreview().length > 20 || /사이트 판정 완료|사이트 판정 실패/.test(statusText()), 12000);
  }} else if (action === "apply-widget-override") {{
    setInput("debugUsageMinutes", "160");
    setInput("debugProfanityCount", "12");
    setInput("debugHarmfulCount", "12");
    setSelect("debugPolicyVerdict", "block");
    click("applyDebugOverrideButton");
    await waitFor(() => /개발자 테스트 값 적용 완료|개발자 테스트 적용 실패/.test(statusText()), 8000);
  }} else if (action === "clear-widget-override") {{
    click("clearDebugOverrideButton");
    await waitFor(() => /개발자 테스트 값 해제 완료|개발자 테스트 해제 실패/.test(statusText()), 8000);
  }}

  let wellbeingState = null;
  try {{
    wellbeingState = await chrome.runtime.sendMessage({{
      type: "GET_WELLBEING_STATE_FOR_URL",
      url: warningUrl
    }});
  }} catch (error) {{
    wellbeingState = {{ ok: false, error: String(error?.message || error) }};
  }}
  return {{
    action,
    status: statusText(),
    connection: connectionText(),
    developerToolsVisible: !byId("developerToolsSection")?.hidden,
    backendEnabled: Boolean(byId("backendEnabledToggle")?.checked),
    backendUrl: byId("backendApiBaseUrl")?.value || "",
    policyPreview: policyPreview().slice(0, 900),
    debugUsageMinutes: byId("debugUsageMinutes")?.value || "",
    debugProfanityCount: byId("debugProfanityCount")?.value || "",
    debugHarmfulCount: byId("debugHarmfulCount")?.value || "",
    debugPolicyVerdict: byId("debugPolicyVerdict")?.value || "",
    wellbeingState
  }};
}})()
"""
    try:
        value = page.evaluate(script, timeout_s=16)
    except Exception as error:  # noqa: BLE001 - visual demo should record failures
        return {"action": action, "ok": False, "error": str(error)}
    if isinstance(value, dict):
        value.setdefault("ok", True)
        return value
    return {"action": action, "ok": bool(value), "value": value}


def record_options_demo_sequence(
    page: CdpWebSocket,
    recorder: "FrameRecorder",
    args: argparse.Namespace,
) -> list[dict[str, Any]]:
    steps = [
        (
            "open-developer",
            "개발자 설정 열기",
            "비밀번호 입력 후 테스트 도구 노출",
            "settings: open developer tools",
        ),
        (
            "enable-backend",
            "백엔드 연동 켜기",
            "API 주소 저장 후 runtime 설정 반영",
            "settings: enable backend",
        ),
        (
            "check-connection",
            "백엔드 연결 확인",
            "모델 서버 health 응답 확인",
            "settings: check backend connection",
        ),
        (
            "run-site-policy",
            "사이트 판정 실행",
            "고위험 URL 판정 결과를 화면에 출력",
            "settings: run site policy",
        ),
        (
            "apply-widget-override",
            "위젯 테스트 적용",
            "사용 시간·탐지 수 override 저장",
            "settings: apply wellbeing override",
        ),
        (
            "clear-widget-override",
            "위젯 테스트 해제",
            "override 제거 후 기본 runtime 상태 복귀",
            "settings: clear wellbeing override",
        ),
    ]
    results: list[dict[str, Any]] = []
    for action, title, detail, label in steps:
        set_demo_caption(page, title, detail)
        result = run_options_demo_action(
            page,
            action,
            backend_url=args.backend,
            warning_url=args.warning_url,
        )
        results.append(result)
        recorder.hold(args.settings_step_hold_seconds, label)
    return results


def set_demo_hover_callout(page: CdpWebSocket, probe: dict[str, Any]) -> bool:
    tooltip = str(probe.get("tooltip") or probe.get("aria_label") or "").strip()
    if not tooltip:
        return False
    x = number_like(probe.get("x"))
    y = number_like(probe.get("y"))
    script = f"""
(() => {{
  const tooltip = {json.dumps(tooltip, ensure_ascii=False)};
  const x = {json.dumps(x)};
  const y = {json.dumps(y)};
  let root = document.getElementById("__chungmaru_demo_hover_callout");
  if (!root) {{
    root = document.createElement("div");
    root.id = "__chungmaru_demo_hover_callout";
    root.setAttribute("data-shieldtext-overlay", "true");
    root.style.position = "fixed";
    root.style.zIndex = "2147483647";
    root.style.maxWidth = "380px";
    root.style.padding = "10px 12px";
    root.style.borderRadius = "12px";
    root.style.border = "1px solid rgba(24, 24, 24, 0.18)";
    root.style.background = "rgba(24, 24, 24, 0.92)";
    root.style.color = "#ffffff";
    root.style.fontFamily = "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
    root.style.fontSize = "13px";
    root.style.fontWeight = "750";
    root.style.lineHeight = "1.35";
    root.style.pointerEvents = "none";
    root.style.boxShadow = "0 10px 28px rgba(0, 0, 0, 0.2)";
    document.documentElement.appendChild(root);
  }}
  root.textContent = tooltip;
  const maxLeft = Math.max(16, window.innerWidth - 410);
  const maxTop = Math.max(16, window.innerHeight - 90);
  root.style.left = `${{Math.min(maxLeft, Math.max(16, x + 16))}}px`;
  root.style.top = `${{Math.min(maxTop, Math.max(16, y + 14))}}px`;
  return true;
}})()
"""
    try:
        return bool(page.evaluate(script, timeout_s=3))
    except Exception:
        return False


def clear_demo_hover_callout(page: CdpWebSocket) -> bool:
    try:
        return bool(
            page.evaluate(
                "(() => { document.getElementById('__chungmaru_demo_hover_callout')?.remove(); return true; })()",
                timeout_s=3,
            )
        )
    except Exception:
        return False


def hover_first_mask(
    page: CdpWebSocket,
    recorder: FrameRecorder,
    *,
    label: str,
    hold_seconds: float,
) -> dict[str, Any]:
    script = r"""
(() => {
  const candidates = Array.from(document.querySelectorAll(
    ".shieldtext-inline-mask, .shieldtext-inline-hide, .shieldtext-inline-blur, " +
    ".shieldtext-editable-mask, .shieldtext-editable-hide, .shieldtext-editable-blur"
  ));
  const visible = [];
  for (const node of candidates) {
    const rect = node.getBoundingClientRect();
    const style = window.getComputedStyle(node);
    if (rect.width < 2 || rect.height < 2 || style.visibility === "hidden" || style.display === "none") {
      continue;
    }
    const tooltip =
      node.getAttribute("title") ||
      node.getAttribute("aria-label") ||
      node.getAttribute("data-shieldtext-tooltip") ||
      "";
    const ariaLabel = node.getAttribute("aria-label") || "";
    visible.push({
      ok: true,
      x: Math.round(rect.left + rect.width / 2),
      y: Math.round(rect.top + rect.height / 2),
      rect: {
        left: Math.round(rect.left),
        top: Math.round(rect.top),
        width: Math.round(rect.width),
        height: Math.round(rect.height)
      },
      tooltip,
      aria_label: ariaLabel,
      mask_text: `${node.innerText || node.textContent || ""}`.slice(0, 80),
      class_name: node.className || "",
      score:
        (/청마루 보호/.test(tooltip) || /청마루 보호/.test(ariaLabel) ? 100 : 0) +
        (/모욕|욕설|혐오|유해|보호/.test(tooltip) || /모욕|욕설|혐오|유해|보호/.test(ariaLabel) ? 20 : 0) +
        (String(node.className || "").includes("preconceal") ? -10 : 0)
    });
  }
  if (visible.length > 0) {
    visible.sort((a, b) => b.score - a.score);
    return visible[0];
  }
  return { ok: false, reason: "NO_VISIBLE_MASK" };
})()
"""
    try:
        probe = page.evaluate(script, timeout_s=5)
    except Exception as error:  # noqa: BLE001 - demo metadata should capture failures
        return {"ok": False, "error": str(error)}
    if not isinstance(probe, dict) or not probe.get("ok"):
        return probe if isinstance(probe, dict) else {"ok": False, "value": probe}

    x = number_like(probe.get("x"))
    y = number_like(probe.get("y"))
    try:
        page.call(
            "Input.dispatchMouseEvent",
            {"type": "mouseMoved", "x": x, "y": y},
            timeout_s=3,
        )
    except Exception as error:  # noqa: BLE001 - still keep DOM tooltip evidence
        probe["mouse_error"] = str(error)
    tooltip = str(probe.get("tooltip") or probe.get("aria_label") or "").strip()
    set_demo_caption(page, "마스킹 근거 hover", tooltip or "마스크 tooltip 없음")
    set_demo_hover_callout(page, probe)
    recorder.hold(hold_seconds, label)
    clear_demo_hover_callout(page)
    return probe


def can_record_hover_probe(args: argparse.Namespace) -> bool:
    max_probes = max(0, int(getattr(args, "max_hover_probes", 0)))
    used = int(getattr(args, "_hover_probe_count", 0))
    return used < max_probes


def mark_hover_probe_recorded(args: argparse.Namespace) -> None:
    setattr(args, "_hover_probe_count", int(getattr(args, "_hover_probe_count", 0)) + 1)


def collect_render_diagnostics(page: CdpWebSocket) -> dict[str, Any]:
    script = r"""
(() => {
  const rendered = Array.from(document.querySelectorAll("[data-shieldtext-rendered='true']"));
  const renderBoxes = Array.from(document.querySelectorAll(".shieldtext-render-box"));
  const inlineMasks = Array.from(document.querySelectorAll(".shieldtext-inline-mask, .shieldtext-inline-hide, .shieldtext-inline-blur"));
  const editableOverlays = Array.from(document.querySelectorAll(".shieldtext-editable-overlay"));
  const concealedEditableSources = Array.from(document.querySelectorAll(".shieldtext-editable-source-concealed, .shieldtext-editable-hard-concealed"));
  const isVisible = (node) => {
    if (!node || !(node instanceof Element)) return false;
    const style = window.getComputedStyle(node);
    if (style.display === "none" || style.visibility === "hidden" || Number(style.opacity || 1) === 0) {
      return false;
    }
    return Array.from(node.getClientRects()).some((rect) =>
      rect.width > 0 &&
      rect.height > 0 &&
      rect.bottom >= 0 &&
      rect.right >= 0 &&
      rect.top <= window.innerHeight &&
      rect.left <= window.innerWidth
    );
  };
  const visibleRendered = rendered.filter(isVisible);
  const visibleRenderBoxes = renderBoxes.filter(isVisible);
  const visibleInlineMasks = inlineMasks.filter(isVisible);
  const visibleEditableOverlays = editableOverlays.filter(isVisible);
  const originals = new Map();
  for (const node of visibleRenderBoxes) {
    const original = node.getAttribute("data-shieldtext-original-text") || "";
    if (!original) continue;
    originals.set(original, (originals.get(original) || 0) + 1);
  }
  const duplicateOriginals = Array.from(originals.entries())
    .filter(([, count]) => count > 1)
    .map(([text, count]) => ({ text, count }))
    .slice(0, 12);
  const maskedOriginalTextSamples = Array.from(originals.keys())
    .map((text) => `${text}`.replace(/\s+/g, " ").trim())
    .filter(Boolean)
    .slice(0, 12)
    .map((text) => text.slice(0, 220));
  const inlineMaskTextSamples = visibleInlineMasks
    .map((node) => {
      const parentText = `${node.parentElement?.innerText || node.parentElement?.textContent || ""}`
        .replace(/\s+/g, " ")
        .trim();
      const maskText = `${node.innerText || node.textContent || ""}`.replace(/\s+/g, " ").trim();
      return { maskText, parentText: parentText.slice(0, 220) };
    })
    .filter((item) => item.maskText || item.parentText)
    .slice(0, 12);
  const editableOverlayTextSamples = visibleEditableOverlays
    .map((node) => `${node.innerText || node.textContent || ""}`.replace(/\s+/g, " ").trim())
    .filter(Boolean)
    .slice(0, 8)
    .map((text) => text.slice(0, 220));
  const searchInputs = Array.from(document.querySelectorAll("textarea[name='q'], textarea[role='combobox'], input[name='q'], input[type='search']"))
    .map((node) => ({
      tag: node.tagName,
      role: node.getAttribute("role") || "",
      ariaLabel: node.getAttribute("aria-label") || "",
      value: node.value || node.textContent || "",
    }))
    .slice(0, 5);
  const aiRoots = Array.from(document.querySelectorAll("[aria-label*='AI 개요' i], [aria-label*='AI Overview' i], [data-attrid*='ai_overview' i], [data-attrid*='AI Overview' i], [data-mcpr], [data-content-feature='1']"));
  const aiTextSamples = aiRoots
    .map((node) => `${node.innerText || node.textContent || ""}`.replace(/\s+/g, " ").trim())
    .filter(Boolean)
    .slice(0, 5)
    .map((text) => text.slice(0, 180));
  return {
    renderedCount: visibleRendered.length,
    renderBoxCount: visibleRenderBoxes.length,
    inlineMaskCount: visibleInlineMasks.length,
    editableOverlayCount: visibleEditableOverlays.length,
    concealedEditableSourceCount: concealedEditableSources.length,
    totalRenderedCount: rendered.length,
    totalRenderBoxCount: renderBoxes.length,
    totalInlineMaskCount: inlineMasks.length,
    totalEditableOverlayCount: editableOverlays.length,
    duplicateRenderedOriginalCount: duplicateOriginals.length,
    duplicateRenderedOriginals: duplicateOriginals,
    maskedOriginalTextSamples,
    inlineMaskTextSamples,
    editableOverlayTextSamples,
    searchInputs,
    aiOverviewCandidateCount: aiRoots.length,
    aiOverviewTextSamples: aiTextSamples,
  };
})()
"""
    try:
        value = page.evaluate(script, timeout_s=5)
    except Exception as error:  # noqa: BLE001 - diagnostic only
        return {"ok": False, "error": str(error)}
    return value if isinstance(value, dict) else {"value": value}


def send_extension_message(page: CdpWebSocket, payload: dict[str, Any], timeout_s: float = 10) -> dict[str, Any]:
    expression = (
        "(async () => {"
        f"const response = await chrome.runtime.sendMessage({json.dumps(payload, ensure_ascii=False)});"
        "return response || null;"
        "})()"
    )
    try:
        value = page.evaluate(expression, timeout_s=timeout_s)
    except Exception as error:  # noqa: BLE001 - E2E metadata should capture failures
        return {"ok": False, "error": str(error), "message": payload}
    return value if isinstance(value, dict) else {"ok": bool(value), "value": value}


def run_control_surface_checks(
    args: argparse.Namespace,
    extension_id: str,
    settings: dict[str, Any],
) -> dict[str, Any]:
    target = create_tab(args.debugging_port, f"chrome-extension://{extension_id}/options.html")
    options_page = CdpWebSocket(str(target["webSocketDebuggerUrl"]))
    try:
        options_page.call("Page.enable")
        options_page.call("Runtime.enable")
        wait_for_page_ready(options_page, timeout_s=10)
        time.sleep(0.4)
        settings_probe = options_page.evaluate(
            "(async () => { const stored = await chrome.storage.sync.get('settings'); return stored.settings || null; })()",
            timeout_s=10,
        )
        backend_health = send_extension_message(
            options_page,
            {"type": "CHECK_API_HEALTH", "apiBaseUrl": settings.get("backendApiBaseUrl")},
            timeout_s=15,
        )
        site_policy = send_extension_message(
            options_page,
            {
                "type": "GET_SITE_POLICY_FOR_URL",
                "url": args.warning_url,
                "context": "options-policy-test",
                "forceRefresh": True,
            },
            timeout_s=15,
        )
        debug_override = {
            "enabled": True,
            "usageMinutes": 160,
            "profanityCount": 12,
            "harmfulCount": 12,
            "policyVerdict": "block",
            "reason": "chrome-demo-e2e-control-surface",
        }
        set_override = send_extension_message(
            options_page,
            {"type": "SET_WELLBEING_DEBUG_OVERRIDE", "override": debug_override},
            timeout_s=10,
        )
        wellbeing_state = send_extension_message(
            options_page,
            {"type": "GET_WELLBEING_STATE_FOR_URL", "url": args.warning_url},
            timeout_s=10,
        )
        clear_override = send_extension_message(
            options_page,
            {"type": "CLEAR_WELLBEING_DEBUG_OVERRIDE"},
            timeout_s=10,
        )
        return {
            "settings_backend_enabled": bool(
                isinstance(settings_probe, dict) and settings_probe.get("backendEnabled")
            ),
            "settings_backend_url": settings_probe.get("backendApiBaseUrl") if isinstance(settings_probe, dict) else "",
            "backend_health": backend_health,
            "site_policy": site_policy,
            "wellbeing_set_override": set_override,
            "wellbeing_state_after_override": wellbeing_state,
            "wellbeing_clear_override": clear_override,
        }
    finally:
        options_page.close()


def wait_for_query_input(page: CdpWebSocket, timeout_s: float = 8) -> bool:
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        try:
            found = page.evaluate(
                "Boolean(document.querySelector('textarea[name=\"q\"], input[name=\"q\"]'))",
                timeout_s=2,
            )
        except Exception:
            found = False
        if found:
            return True
        time.sleep(0.2)
    return False


def set_google_query(page: CdpWebSocket, value: str) -> bool:
    expression = f"""
(() => {{
  const value = {json.dumps(value, ensure_ascii=False)};
  const input = document.querySelector('textarea[name="q"], input[name="q"]');
  if (!input) return false;
  input.focus();
  input.value = value;
  input.dispatchEvent(new InputEvent('input', {{ bubbles: true, inputType: 'insertText', data: value.slice(-1) }}));
  input.dispatchEvent(new Event('change', {{ bubbles: true }}));
  return true;
}})()
"""
    try:
        return bool(page.evaluate(expression, timeout_s=3))
    except Exception:
        return False


def type_google_query(page: CdpWebSocket, query: str, recorder: FrameRecorder, label: str) -> dict[str, Any]:
    wait_for_query_input(page, timeout_s=8)
    set_google_query(page, "")
    typed = ""
    captured = 0
    for character in query:
        typed += character
        ok = set_google_query(page, typed)
        if ok:
            recorder.capture_step(f"{label}: type {typed}")
            captured += 1
        else:
            break
        time.sleep(0.04)
    return {
        "query": query,
        "typed": typed,
        "captured_frames": captured,
        "ok": typed == query,
    }


def navigate_to_search(page: CdpWebSocket, query: str, timeout_s: float = 20) -> None:
    page.call("Page.navigate", {"url": google_search_url(query)}, timeout_s=8)
    wait_for_page_ready(page, timeout_s=timeout_s)


def navigate_to_youtube_search(
    page: CdpWebSocket,
    *,
    query: str,
    search_url: str | None = None,
    timeout_s: float = 24,
) -> None:
    page.call("Page.navigate", {"url": search_url or youtube_search_url(query)}, timeout_s=8)
    wait_for_page_ready(page, timeout_s=timeout_s)


def select_youtube_video_from_results(
    page: CdpWebSocket,
    *,
    hints: list[str],
    timeout_s: float = 18,
) -> dict[str, Any]:
    deadline = time.time() + timeout_s
    last_result: dict[str, Any] = {"ok": False, "reason": "NOT_STARTED"}
    hints = [hint for hint in hints if hint]
    while time.time() < deadline:
        expression = f"""
(() => {{
  const hints = {json.dumps(hints, ensure_ascii=False)};
  const normalize = (value) => `${{value || ""}}`.toLowerCase().replace(/\\s+/g, " ").trim();
  const anchors = Array.from(document.querySelectorAll(
    "ytd-video-renderer a#video-title, ytd-video-renderer a[href*='/watch'], a#video-title[href*='/watch']"
  ));
  const rows = anchors
    .map((anchor) => {{
      const renderer = anchor.closest("ytd-video-renderer") || anchor.closest("ytd-rich-item-renderer") || anchor;
      const title = `${{anchor.getAttribute("title") || anchor.textContent || anchor.getAttribute("aria-label") || ""}}`.replace(/\\s+/g, " ").trim();
      const channel = `${{renderer.querySelector("ytd-channel-name, #channel-name, .ytd-channel-name")?.textContent || ""}}`.replace(/\\s+/g, " ").trim();
      const href = anchor.href || "";
      const haystack = normalize(`${{title}} ${{channel}} ${{href}}`);
      let score = 0;
      for (const hint of hints) {{
        if (hint && haystack.includes(normalize(hint))) score += 10;
      }}
      if (/sik-k|식케이/i.test(`${{title}} ${{channel}}`)) score += 30;
      if (/다시\\s*보여줘야해|보여줘야해/i.test(title)) score += 25;
      if (/시발|씨발|sibal|ssibal/i.test(title)) score += 8;
      const rect = anchor.getBoundingClientRect();
      return {{ title, channel, href, score, top: Math.round(rect.top) }};
    }})
    .filter((item) => item.href && item.href.includes("/watch"));
  if (!rows.length) return {{ ok: false, reason: "NO_VIDEO_RESULTS" }};
  rows.sort((a, b) => b.score - a.score || a.top - b.top);
  const selected = rows[0];
  window.location.href = selected.href;
  return {{ ok: true, selected, candidates: rows.slice(0, 5) }};
}})()
"""
        try:
            value = page.evaluate(expression, timeout_s=5)
        except Exception as error:  # noqa: BLE001 - keep retry diagnostics
            value = {"ok": False, "error": str(error)}
        last_result = value if isinstance(value, dict) else {"ok": False, "value": value}
        if last_result.get("ok"):
            wait_for_page_ready(page, timeout_s=timeout_s)
            time.sleep(2.0)
            return last_result
        time.sleep(0.5)
    return last_result


def scroll_youtube_to_comments(
    page: CdpWebSocket,
    recorder: FrameRecorder,
    *,
    label: str,
    steps: int = 28,
) -> dict[str, Any]:
    expression = r"""
(() => {
  const comments =
    document.querySelector("ytd-comments") ||
    document.querySelector("#comments") ||
    document.querySelector("ytd-item-section-renderer#sections");
  const target = comments
    ? Math.max(0, comments.getBoundingClientRect().top + window.scrollY - 120)
    : Math.max(window.innerHeight, Math.floor(document.documentElement.scrollHeight * 0.48));
  return {
    target,
    commentsFound: Boolean(comments),
    scrollHeight: document.documentElement.scrollHeight,
    title: document.querySelector("h1 yt-formatted-string, h1")?.textContent || document.title || ""
  };
})()
"""
    try:
        target = page.evaluate(expression, timeout_s=5)
    except Exception as error:  # noqa: BLE001 - demo metadata only
        target = {"target": 1200, "commentsFound": False, "error": str(error)}
    target_y = int(number_like(target.get("target") if isinstance(target, dict) else 1200))
    first_frame = recorder.index + 1
    start_y = int(number_like(page.evaluate("window.scrollY", timeout_s=2) or 0))
    for index in range(max(2, steps)):
        ratio = index / max(1, steps - 1)
        position = int(start_y + (target_y - start_y) * ratio)
        page.evaluate(f"window.scrollTo(0, {position})", timeout_s=2)
        time.sleep(0.08)
        recorder._capture_one()
    recorder.metadata.setdefault("timeline", []).append(
        {
            "label": f"{label}: scroll-to-comments",
            "first_frame": first_frame,
            "last_frame": recorder.index,
            "seconds": round((recorder.index - first_frame + 1) / recorder.fps, 3),
        }
    )
    time.sleep(1.5)
    return target if isinstance(target, dict) else {"target": target_y}


def collect_youtube_diagnostics(page: CdpWebSocket) -> dict[str, Any]:
    expression = r"""
(() => {
  const text = (node) => `${node?.innerText || node?.textContent || ""}`.replace(/\s+/g, " ").trim();
  const commentNodes = Array.from(document.querySelectorAll(
    "ytd-comment-thread-renderer #content-text, ytd-comment-view-model #content-text, #comments #content-text"
  ));
  const title = text(document.querySelector("h1 yt-formatted-string, h1")) || document.title || "";
  const channel = text(document.querySelector("ytd-video-owner-renderer ytd-channel-name, #owner ytd-channel-name"));
  const visibleComments = commentNodes
    .filter((node) => {
      const rect = node.getBoundingClientRect();
      return rect.width > 0 && rect.height > 0 && rect.bottom >= 0 && rect.top <= window.innerHeight;
    })
    .map((node) => text(node).slice(0, 220))
    .filter(Boolean)
    .slice(0, 10);
  return {
    title,
    channel,
    url: location.href,
    commentCandidateCount: commentNodes.length,
    visibleCommentSamples: visibleComments,
    scrollY: Math.round(window.scrollY),
    scrollHeight: document.documentElement.scrollHeight,
  };
})()
"""
    try:
        value = page.evaluate(expression, timeout_s=5)
    except Exception as error:  # noqa: BLE001 - diagnostic only
        return {"ok": False, "error": str(error)}
    return value if isinstance(value, dict) else {"value": value}


def max_scroll_y(page: CdpWebSocket) -> int:
    expression = """
(() => Math.max(
  0,
  document.documentElement.scrollHeight,
  document.body ? document.body.scrollHeight : 0
) - window.innerHeight)()
"""
    try:
        return max(0, int(page.evaluate(expression, timeout_s=3) or 0))
    except Exception:
        return 0


def smooth_scroll(
    page: CdpWebSocket,
    recorder: FrameRecorder,
    *,
    label: str,
    direction: str = "down",
    steps: int = 24,
    fraction: float = 0.78,
) -> dict[str, Any]:
    maximum = max_scroll_y(page)
    if maximum <= 0:
        recorder.hold(1.0, f"{label}: no-scroll")
        return {"ok": False, "reason": "NO_SCROLL_RANGE", "max_scroll_y": maximum}

    start = 0 if direction == "down" else min(maximum, int(maximum * fraction))
    end = min(maximum, int(maximum * fraction)) if direction == "down" else 0
    first_frame = recorder.index + 1
    for index in range(max(2, steps)):
        ratio = index / max(1, steps - 1)
        position = int(start + (end - start) * ratio)
        page.evaluate(f"window.scrollTo(0, {position})", timeout_s=2)
        time.sleep(0.08)
        recorder._capture_one()
    recorder.metadata.setdefault("timeline", []).append(
        {
            "label": f"{label}: scroll-{direction}",
            "first_frame": first_frame,
            "last_frame": recorder.index,
            "seconds": round((recorder.index - first_frame + 1) / recorder.fps, 3),
        }
    )
    return {
        "ok": True,
        "direction": direction,
        "steps": steps,
        "start": start,
        "end": end,
        "max_scroll_y": maximum,
    }


def run_pipeline_for_google_scene(
    worker: CdpWebSocket,
    settings: dict[str, Any],
    settings_write_ok: bool,
    *,
    reason: str,
    force_settings_snapshot: bool = False,
    attempts: int = 3,
) -> dict[str, Any]:
    pipeline_reason = google_pipeline_reason(reason)
    if settings_write_ok and not force_settings_snapshot:
        settings_response = {
            "ok": True,
            "skipped": True,
            "reason": "SETTINGS_ALREADY_WRITTEN_TO_STORAGE",
        }
    else:
        settings_response = send_to_fixture_tab(
            worker,
            "https://www.google.com/search",
            {"type": "APPLY_SETTINGS_SNAPSHOT", "settings": settings},
            inject_on_failure=True,
            timeout_s=10,
        )
    time.sleep(0.35)

    attempt_records: list[dict[str, Any]] = []
    google_light_response: dict[str, Any] = {}
    trigger_response: dict[str, Any] = {}
    last_stats: dict[str, Any] = {}
    max_attempts = max(1, int(attempts or 1))

    for attempt_index in range(1, max_attempts + 1):
        attempt_started_at = time.time()
        google_light_started_at = time.time()
        google_light_response = send_to_fixture_tab(
            worker,
            "https://www.google.com/search",
            {"type": "RUN_GOOGLE_SEARCH_LIGHT_PROTECTION", "limit": 16},
            inject_on_failure=False,
            timeout_s=10,
        )
        google_light_duration_ms = round((time.time() - google_light_started_at) * 1000, 3)
        time.sleep(0.7)
        trigger_started_at = time.time()
        trigger_response = send_to_fixture_tab(
            worker,
            "https://www.google.com/search",
            {"type": "RUN_PIPELINE", "reason": pipeline_reason},
            inject_on_failure=False,
            timeout_s=15,
        )
        trigger_duration_ms = round((time.time() - trigger_started_at) * 1000, 3)
        time.sleep(1.0)
        last_stats = get_last_stats(worker)
        attempt_records.append(
            {
                "attempt": attempt_index,
                "started_at_epoch": round(attempt_started_at, 3),
                "ended_at_epoch": round(time.time(), 3),
                "attempt_wall_duration_ms": round((time.time() - attempt_started_at) * 1000, 3),
                "google_light_duration_ms": google_light_duration_ms,
                "trigger_duration_ms": trigger_duration_ms,
                "google_light_response": google_light_response,
                "trigger_response": trigger_response,
                "requested_reason": reason,
                "pipeline_reason": pipeline_reason,
                "maskedSpanCount": last_stats.get("maskedSpanCount"),
                "requestedAnalysisCount": last_stats.get("requestedAnalysisCount"),
                "backendEndpoint": last_stats.get("backendEndpoint"),
                "lastDecisionSource": last_stats.get("lastDecisionSource"),
            }
        )
        if number_like(last_stats.get("maskedSpanCount")) > 0:
            break
        if number_like(last_stats.get("requestedAnalysisCount")) > 0 and attempt_index >= 2:
            break
        time.sleep(0.45)

    return {
        "settings_response": settings_response,
        "google_light_response": google_light_response,
        "trigger_response": trigger_response,
        "attempts": attempt_records,
        "last_stats": last_stats,
    }


def run_pipeline_for_page_scene(
    worker: CdpWebSocket,
    settings: dict[str, Any],
    settings_write_ok: bool,
    *,
    page_url_prefix: str,
    reason: str,
    force_settings_snapshot: bool = False,
    attempts: int = 3,
) -> dict[str, Any]:
    if settings_write_ok and not force_settings_snapshot:
        settings_response = {
            "ok": True,
            "skipped": True,
            "reason": "SETTINGS_ALREADY_WRITTEN_TO_STORAGE",
        }
    else:
        settings_response = send_to_fixture_tab(
            worker,
            page_url_prefix,
            {"type": "APPLY_SETTINGS_SNAPSHOT", "settings": settings},
            inject_on_failure=True,
            timeout_s=12,
        )
    time.sleep(0.35)

    attempt_records: list[dict[str, Any]] = []
    trigger_response: dict[str, Any] = {}
    last_stats: dict[str, Any] = {}
    max_attempts = max(1, int(attempts or 1))

    for attempt_index in range(1, max_attempts + 1):
        attempt_started_at = time.time()
        trigger_started_at = time.time()
        trigger_response = send_to_fixture_tab(
            worker,
            page_url_prefix,
            {"type": "RUN_PIPELINE", "reason": reason},
            inject_on_failure=attempt_index == 1,
            timeout_s=18,
        )
        trigger_duration_ms = round((time.time() - trigger_started_at) * 1000, 3)
        time.sleep(1.0)
        last_stats = get_last_stats(worker) or {}
        attempt_records.append(
            {
                "attempt": attempt_index,
                "started_at_epoch": round(attempt_started_at, 3),
                "ended_at_epoch": round(time.time(), 3),
                "attempt_wall_duration_ms": round((time.time() - attempt_started_at) * 1000, 3),
                "google_light_duration_ms": "",
                "trigger_duration_ms": trigger_duration_ms,
                "google_light_response": {},
                "trigger_response": trigger_response,
                "requested_reason": reason,
                "pipeline_reason": reason,
                "maskedSpanCount": last_stats.get("maskedSpanCount"),
                "effectiveMaskedSpanCount": last_stats.get("effectiveMaskedSpanCount"),
                "requestedAnalysisCount": last_stats.get("requestedAnalysisCount"),
                "backendEndpoint": last_stats.get("backendEndpoint"),
                "lastDecisionSource": last_stats.get("lastDecisionSource"),
            }
        )
        if number_like(last_stats.get("effectiveMaskedSpanCount")) > 0:
            break
        if number_like(last_stats.get("maskedSpanCount")) > 0:
            break
        if number_like(last_stats.get("requestedAnalysisCount")) > 0 and attempt_index >= 2:
            break
        time.sleep(0.45)

    return {
        "settings_response": settings_response,
        "google_light_response": {},
        "trigger_response": trigger_response,
        "attempts": attempt_records,
        "last_stats": last_stats,
    }


LATENCY_FIELDS = [
    "run_id",
    "captured_at",
    "mode",
    "scene_index",
    "scene_type",
    "query",
    "query_scenario_id",
    "query_category",
    "expected_result",
    "intervention_mode",
    "protection_enabled",
    "typed_text",
    "query_or_url",
    "query_display",
    "input_surface",
    "target_url",
    "attempt",
    "attempt_status",
    "google_light_ok",
    "google_light_masked_span_count",
    "google_light_preconceal_count",
    "trigger_ok",
    "google_light_duration_ms",
    "trigger_duration_ms",
    "attempt_wall_duration_ms",
    "video_start_seconds",
    "video_end_seconds",
    "backend_endpoint",
    "backend_status",
    "decision_source",
    "duration_ms",
    "candidate_collect_ms",
    "parser_ms",
    "pre_backend_ms",
    "backend_round_trip_ms",
    "backend_reported_ms",
    "decision_build_ms",
    "mask_apply_ms",
    "post_backend_to_mask_ms",
    "total_to_mask_ms",
    "first_mask_latency_ms",
    "visible_first_mask_ms",
    "first_paint_mask_ms",
    "foreground_unit_build_ms",
    "foreground_backend_latency_ms",
    "backend_internal_pipeline_avg_ms",
    "backend_internal_model_avg_ms",
    "requested_analysis_count",
    "total_candidate_count",
    "foreground_candidate_count",
    "returned_span_count",
    "masked_span_count",
    "local_preflight_masked_span_count",
    "preconceal_count",
    "effective_masked_span_count",
    "render_box_count",
    "inline_mask_count",
    "duplicate_rendered_original_count",
    "ai_overview_candidate_count",
    "search_input_value",
    "hover_probe_ok",
    "hover_tooltip",
    "hover_mask_text",
]

TIMELINE_FIELDS = [
    "run_id",
    "label",
    "first_frame",
    "last_frame",
    "start_seconds",
    "end_seconds",
    "duration_seconds",
]

SCENE_FIELDS = [
    "run_id",
    "scene_index",
    "scene_type",
    "label",
    "query",
    "query_category",
    "expected_result",
    "intervention_mode",
    "protection_enabled",
    "input_surface",
    "start_seconds",
    "end_seconds",
    "duration_seconds",
    "attempt_count",
    "effective_masked_span_count",
    "render_box_count",
    "inline_mask_count",
    "ai_overview_candidate_count",
    "duplicate_rendered_original_count",
    "preconceal_count_max",
    "google_light_preconceal_count_max",
    "total_to_mask_ms_avg",
    "total_to_mask_ms_max",
    "backend_round_trip_ms_avg",
    "backend_round_trip_ms_max",
    "candidate_collect_ms_avg",
    "parser_ms_avg",
    "requested_analysis_count_max",
    "backend_live_attempt_count",
    "local_preflight_masked_span_count_max",
    "hover_probe_ok",
    "hover_tooltip",
    "site_warning_continue_hidden",
    "site_warning_continue_disabled",
    "settings_action_summary",
    "status",
    "evidence_note",
]


def attempt_stats(attempt: dict[str, Any], pipeline: dict[str, Any]) -> dict[str, Any]:
    stats = nested_get(attempt, ["trigger_response", "response", "stats"], None)
    if isinstance(stats, dict):
        return stats
    last_stats = pipeline.get("last_stats")
    return last_stats if isinstance(last_stats, dict) else {}


def first_search_input_value(diagnostics: dict[str, Any]) -> str:
    values = diagnostics.get("searchInputs") if isinstance(diagnostics, dict) else None
    if not isinstance(values, list) or not values:
        return ""
    first = values[0]
    return str(first.get("value") or "") if isinstance(first, dict) else ""


def build_latency_rows(
    *,
    run_id: str,
    scene: dict[str, Any],
    pipeline: dict[str, Any],
    diagnostics: dict[str, Any],
    effective_masks: int,
    video_start_seconds: float,
    video_end_seconds: float,
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    attempts = pipeline.get("attempts")
    if not isinstance(attempts, list):
        attempts = []
    if not attempts:
        attempts = [{"attempt": 1}]

    for attempt in attempts:
        if not isinstance(attempt, dict):
            continue
        stats = attempt_stats(attempt, pipeline)
        scene_type = str(scene.get("type") or "")
        if scene_type in {"google-search", "google-mode-showcase"}:
            meta = query_metadata(str(scene.get("query", "")))
            if scene.get("query_scenario_id"):
                meta["query_scenario_id"] = scene.get("query_scenario_id")
            if scene.get("query_category"):
                meta["query_category"] = scene.get("query_category")
            if scene.get("expected_result"):
                meta["expected_result"] = scene.get("expected_result")
            input_surface = "Google search textarea[role=combobox] / textarea[name=q]"
        else:
            meta = {
                "query_scenario_id": scene.get("query_scenario_id", scene_type),
                "query_category": scene.get("query_category", scene_type),
                "expected_result": scene.get("expected_result", ""),
            }
            input_surface = str(scene.get("input_surface") or "")
        phase = stats.get("phaseTimings") if isinstance(stats.get("phaseTimings"), dict) else {}
        internal = stats.get("backendInternalTimingSummary")
        if not isinstance(internal, dict):
            internal = {}
        row = {
            "run_id": run_id,
            "captured_at": datetime.now().isoformat(timespec="seconds"),
            "mode": "chrome-google-demo",
            "scene_index": scene.get("index"),
            "scene_type": scene_type,
            "query": scene.get("query", ""),
            "query_scenario_id": meta.get("query_scenario_id", ""),
            "query_category": meta.get("query_category", ""),
            "expected_result": meta.get("expected_result", ""),
            "intervention_mode": scene.get("intervention_mode", ""),
            "protection_enabled": scene.get("protection_enabled", ""),
            "typed_text": nested_get(scene, ["typed", "typed"], ""),
            "query_or_url": scene.get("query") or scene.get("target_url") or scene.get("url", ""),
            "query_display": scene.get("query_display") or scene.get("query") or scene.get("target_title") or "",
            "input_surface": input_surface,
            "target_url": scene.get("target_url") or scene.get("url", ""),
            "attempt": attempt.get("attempt", ""),
            "attempt_status": "ok" if nested_get(attempt, ["trigger_response", "ok"], False) else "check",
            "google_light_ok": bool(nested_get(attempt, ["google_light_response", "ok"], False)),
            "google_light_masked_span_count": nested_get(
                attempt,
                ["google_light_response", "response", "maskedSpanCount"],
                "",
            ),
            "google_light_preconceal_count": nested_get(
                attempt,
                ["google_light_response", "response", "preconcealCount"],
                "",
            ),
            "trigger_ok": bool(nested_get(attempt, ["trigger_response", "ok"], False)),
            "google_light_duration_ms": attempt.get("google_light_duration_ms", ""),
            "trigger_duration_ms": attempt.get("trigger_duration_ms", ""),
            "attempt_wall_duration_ms": attempt.get("attempt_wall_duration_ms", ""),
            "video_start_seconds": video_start_seconds,
            "video_end_seconds": video_end_seconds,
            "backend_endpoint": stats.get("backendEndpoint", attempt.get("backendEndpoint", "")),
            "backend_status": stats.get("backendStatus", ""),
            "decision_source": stats.get("lastDecisionSource", attempt.get("lastDecisionSource", "")),
            "duration_ms": stats.get("durationMs", ""),
            "candidate_collect_ms": phase.get("candidateCollectMs", ""),
            "parser_ms": phase.get("parserMs", stats.get("foregroundUnitBuildMs", "")),
            "pre_backend_ms": phase.get("preBackendMs", ""),
            "backend_round_trip_ms": phase.get("backendRoundTripMs", ""),
            "backend_reported_ms": phase.get("backendReportedMs", stats.get("backendDurationMs", "")),
            "decision_build_ms": phase.get("decisionBuildMs", ""),
            "mask_apply_ms": phase.get("maskApplyMs", ""),
            "post_backend_to_mask_ms": phase.get("postBackendToMaskMs", ""),
            "total_to_mask_ms": phase.get("totalToMaskMs", stats.get("durationMs", "")),
            "first_mask_latency_ms": stats.get("firstMaskLatencyMs", ""),
            "visible_first_mask_ms": stats.get("visibleFirstMaskMs", ""),
            "first_paint_mask_ms": stats.get("firstPaintMaskMs", ""),
            "foreground_unit_build_ms": stats.get("foregroundUnitBuildMs", ""),
            "foreground_backend_latency_ms": stats.get("foregroundBackendLatencyMs", stats.get("backendDurationMs", "")),
            "backend_internal_pipeline_avg_ms": nested_get(internal, ["backendPipeline", "avgMs"], ""),
            "backend_internal_model_avg_ms": nested_get(internal, ["backendModel", "avgMs"], ""),
            "requested_analysis_count": stats.get("requestedAnalysisCount", attempt.get("requestedAnalysisCount", "")),
            "total_candidate_count": stats.get("totalCandidateCount", ""),
            "foreground_candidate_count": stats.get("foregroundCandidateCount", ""),
            "returned_span_count": stats.get("returnedSpanCount", ""),
            "masked_span_count": stats.get("maskedSpanCount", attempt.get("maskedSpanCount", "")),
            "local_preflight_masked_span_count": stats.get("localPreflightMaskedSpanCount", ""),
            "preconceal_count": stats.get("preconcealCount", ""),
            "effective_masked_span_count": max(
                number_like(stats.get("effectiveMaskedSpanCount", "")),
                number_like(effective_masks),
            ),
            "render_box_count": diagnostics.get("renderBoxCount", ""),
            "inline_mask_count": diagnostics.get("inlineMaskCount", ""),
            "duplicate_rendered_original_count": diagnostics.get("duplicateRenderedOriginalCount", ""),
            "ai_overview_candidate_count": diagnostics.get("aiOverviewCandidateCount", ""),
            "search_input_value": first_search_input_value(diagnostics),
            "hover_probe_ok": bool(nested_get(scene, ["hover_probe", "ok"], False)),
            "hover_tooltip": nested_get(scene, ["hover_probe", "tooltip"], ""),
            "hover_mask_text": nested_get(scene, ["hover_probe", "mask_text"], ""),
        }
        rows.append(row)
    return rows


def record_google_mode_showcase(
    *,
    args: argparse.Namespace,
    page: CdpWebSocket,
    worker: CdpWebSocket,
    recorder: FrameRecorder,
    base_settings: dict[str, Any],
    metadata: dict[str, Any],
    latency_rows: list[dict[str, Any]],
    start_index: int,
) -> int:
    query = str(args.mode_showcase_query or DEFAULT_MODE_SHOWCASE_QUERY)
    next_index = int(start_index)

    off_settings = build_mode_demo_settings(base_settings, enabled=False)
    off_apply_response = set_demo_settings(worker, off_settings)
    scene_first_frame = recorder.index + 1
    set_demo_caption(page, "보호 OFF", "설정 적용 전 원문 노출 상태")
    navigate_to_search(page, query)
    time.sleep(args.mode_showcase_initial_wait)
    off_snapshot_response = send_to_fixture_tab(
        worker,
        "https://www.google.com/search",
        {"type": "APPLY_SETTINGS_SNAPSHOT", "settings": off_settings},
        inject_on_failure=True,
        timeout_s=12,
    )
    diagnostics_off = collect_render_diagnostics(page)
    recorder.hold(args.mode_showcase_raw_hold_seconds, "mode showcase: protection off raw screen")
    metadata["scenes"].append(
        {
            "type": "google-mode-showcase",
            "index": next_index,
            "query": query,
            "query_display": f"보호 OFF: {query}",
            "query_scenario_id": "masking-mode-off",
            "query_category": "masking-mode-showcase",
            "expected_result": "raw-visible",
            "intervention_mode": "off",
            "protection_enabled": False,
            "url": page_location(page),
            "settings_response": {
                "storage": off_apply_response,
                "snapshot": off_snapshot_response,
            },
            "first_frame": scene_first_frame,
            "last_frame": recorder.index,
            "video_start_seconds": frame_start_seconds(scene_first_frame, args.fps),
            "video_end_seconds": round(recorder.video_seconds, 3),
            "render_diagnostics_after_pipeline": diagnostics_off,
            "effective_masked_span_count": 0,
            "last_stats": get_last_stats(worker),
        }
    )
    next_index += 1

    for mode in INTERVENTION_MODE_ORDER:
        mode_label = INTERVENTION_MODE_LABELS[mode]
        mode_settings = build_mode_demo_settings(
            base_settings,
            enabled=True,
            intervention_mode=mode,
        )
        scene_first_frame = recorder.index + 1
        set_demo_caption(
            page,
            f"{mode_label} 방식",
            "같은 검색어 재로드 → 보호 ON → 분석 실행",
        )
        navigate_to_search(page, query)
        time.sleep(args.mode_showcase_initial_wait)
        settings_response = apply_demo_settings_to_page(
            worker,
            page_url_prefix="https://www.google.com/search",
            settings=mode_settings,
            inject_on_failure=True,
        )
        recorder.hold(args.mode_showcase_apply_hold_seconds, f"mode showcase: apply {mode}")
        pipeline_video_start = round(recorder.video_seconds, 3)
        pipeline = run_pipeline_for_google_scene(
            worker,
            mode_settings,
            settings_write_ok=False,
            reason=f"interactive-demo-google-mode-{mode}",
            force_settings_snapshot=True,
            attempts=args.mode_showcase_pipeline_attempts,
        )
        diagnostics_after_pipeline = collect_render_diagnostics(page)
        effective_masks = effective_masked_span_count(pipeline, diagnostics_after_pipeline)
        set_demo_caption(
            page,
            f"{mode_label} 결과",
            f"interventionMode={mode} · effective masks {effective_masks}",
        )
        recorder.hold(args.mode_showcase_result_hold_seconds, f"mode showcase: result {mode}")
        pipeline_video_end = round(recorder.video_seconds, 3)
        hover_probe: dict[str, Any] = {"ok": False, "reason": "DISABLED_OR_NO_MASK"}
        if mode == "mask" and not args.no_hover_probe and effective_masks > 0 and can_record_hover_probe(args):
            hover_probe = hover_first_mask(
                page,
                recorder,
                label=f"mode showcase: hover {mode}",
                hold_seconds=min(args.hover_hold_seconds, args.mode_showcase_hover_hold_seconds),
            )
            mark_hover_probe_recorded(args)
        scene = {
            "type": "google-mode-showcase",
            "index": next_index,
            "query": query,
            "query_display": f"{mode_label}: {query}",
            "query_scenario_id": f"masking-mode-{mode}",
            "query_category": "masking-mode-showcase",
            "expected_result": "mode-applied",
            "intervention_mode": mode,
            "protection_enabled": True,
            "url": page_location(page),
            "settings_response": settings_response,
            "pipeline": pipeline,
            "first_frame": scene_first_frame,
            "last_frame": recorder.index,
            "video_start_seconds": frame_start_seconds(scene_first_frame, args.fps),
            "video_end_seconds": round(recorder.video_seconds, 3),
            "pipeline_video_start_seconds": pipeline_video_start,
            "pipeline_video_end_seconds": pipeline_video_end,
            "render_diagnostics_after_pipeline": diagnostics_after_pipeline,
            "hover_probe": hover_probe,
            "effective_masked_span_count": effective_masks,
            "last_stats": get_last_stats(worker),
        }
        metadata["scenes"].append(scene)
        latency_rows.extend(
            build_latency_rows(
                run_id=args.run_id,
                scene=scene,
                pipeline=pipeline,
                diagnostics=diagnostics_after_pipeline,
                effective_masks=effective_masks,
                video_start_seconds=pipeline_video_start,
                video_end_seconds=pipeline_video_end,
            )
        )
        next_index += 1

    metadata["mode_showcase_restore_settings"] = set_demo_settings(worker, base_settings)
    return next_index


def write_csv(path: Path, rows: list[dict[str, Any]], fieldnames: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow({field: csv_value(row.get(field)) for field in fieldnames})


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def append_csv(path: Path, rows: list[dict[str, Any]], fieldnames: list[str]) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    needs_header = not path.exists() or path.stat().st_size == 0
    if not needs_header:
        raw = path.read_bytes()
        text = raw.decode("utf-8-sig")
        reader = csv.DictReader(io.StringIO(text))
        existing_header = reader.fieldnames or []
        if existing_header != fieldnames:
            existing_rows = list(reader)
            with path.open("w", encoding="utf-8-sig", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
                writer.writeheader()
                for row in existing_rows:
                    writer.writerow({field: row.get(field, "") for field in fieldnames})

    with path.open("a", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        if needs_header:
            writer.writeheader()
        for row in rows:
            writer.writerow({field: csv_value(row.get(field)) for field in fieldnames})
    return path


def append_jsonl(path: Path, rows: list[dict[str, Any]]) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")
    return path


def build_timeline_rows(metadata: dict[str, Any]) -> list[dict[str, Any]]:
    fps = int(metadata.get("fps") or 30)
    rows: list[dict[str, Any]] = []
    for item in metadata.get("timeline", []):
        if not isinstance(item, dict):
            continue
        first_frame = int(item.get("first_frame") or 0)
        last_frame = int(item.get("last_frame") or first_frame)
        rows.append(
            {
                "run_id": metadata.get("run_id", ""),
                "label": item.get("label", ""),
                "first_frame": first_frame,
                "last_frame": last_frame,
                "start_seconds": frame_start_seconds(first_frame, fps),
                "end_seconds": frame_end_seconds(last_frame, fps),
                "duration_seconds": round(max(0, last_frame - first_frame + 1) / max(1, fps), 3),
            }
        )
    return rows


def numeric_values(rows: list[dict[str, Any]], key: str) -> list[float]:
    values: list[float] = []
    for row in rows:
        value = row.get(key)
        try:
            if value != "":
                values.append(float(value))
        except (TypeError, ValueError):
            continue
    return values


def average(values: list[float]) -> str:
    if not values:
        return ""
    return f"{sum(values) / len(values):.3f}"


def maximum(values: list[float]) -> str:
    if not values:
        return ""
    return f"{max(values):.3f}"


def build_scene_summary_rows(
    metadata: dict[str, Any],
    latency_rows: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    latency_by_scene: dict[str, list[dict[str, Any]]] = {}
    for row in latency_rows:
        key = str(row.get("scene_index") or "")
        latency_by_scene.setdefault(key, []).append(row)

    rows: list[dict[str, Any]] = []
    for scene in metadata.get("scenes", []):
        if not isinstance(scene, dict):
            continue
        scene_type = str(scene.get("type") or "")
        scene_index = scene.get("index", "")
        key = str(scene_index or "")
        scene_latency = latency_by_scene.get(key, [])
        diagnostics = scene.get("render_diagnostics_after_pipeline")
        if not isinstance(diagnostics, dict):
            diagnostics = {}
        start_seconds = scene.get("video_start_seconds")
        if start_seconds is None and scene.get("first_frame"):
            start_seconds = frame_start_seconds(int(scene.get("first_frame") or 0), int(metadata.get("fps") or 30))
        end_seconds = scene.get("video_end_seconds")
        if end_seconds is None and scene.get("last_frame"):
            end_seconds = frame_end_seconds(int(scene.get("last_frame") or 0), int(metadata.get("fps") or 30))

        total_values = numeric_values(scene_latency, "total_to_mask_ms")
        backend_values = numeric_values(scene_latency, "backend_round_trip_ms")
        candidate_values = numeric_values(scene_latency, "candidate_collect_ms")
        parser_values = numeric_values(scene_latency, "parser_ms")
        requested_values = numeric_values(scene_latency, "requested_analysis_count")
        local_preflight_values = numeric_values(scene_latency, "local_preflight_masked_span_count")
        preconceal_values = numeric_values(scene_latency, "preconceal_count")
        google_light_preconceal_values = numeric_values(scene_latency, "google_light_preconceal_count")
        backend_live_attempt_count = sum(
            1
            for row in scene_latency
            if number_like(row.get("requested_analysis_count")) > 0
            or number_like(row.get("backend_round_trip_ms")) > 0
        )

        if scene_type == "google-search":
            masks = int(number_like(scene.get("effective_masked_span_count")))
            status = "masked" if masks > 0 else "clean-or-no-mask"
            label = f"Search {scene.get('display_index') or scene_index}"
            note = "Google search input/result/AI overview candidate run"
        elif scene_type == "google-mode-showcase":
            masks = int(number_like(scene.get("effective_masked_span_count")))
            mode = str(scene.get("intervention_mode") or "")
            enabled = scene.get("protection_enabled")
            mode_label = INTERVENTION_MODE_LABELS.get(mode, mode or "OFF")
            status = "off-raw" if enabled is False else ("mode-applied" if masks > 0 else "needs-review")
            label = f"Mode {mode_label}" if enabled is not False else "Protection OFF"
            note = "Google masking mode showcase: raw-off then intervention result"
        elif scene_type == "youtube-watch-comments":
            masks = int(number_like(scene.get("effective_masked_span_count")))
            status = "masked" if masks > 0 else "needs-review"
            label = "YouTube"
            selected = scene.get("selected_video") if isinstance(scene.get("selected_video"), dict) else {}
            title = selected.get("title") or scene.get("target_title") or ""
            note = f"YouTube search result to watch page and comments: {title}".strip()
        elif scene_type == "site-warning":
            state = scene.get("page_state") if isinstance(scene.get("page_state"), dict) else {}
            status = "blocked" if state.get("continueHidden") or state.get("continueDisabled") else "warning"
            label = "Site warning"
            note = "High-risk warning page continue policy"
        elif scene_type == "settings":
            status = "operated"
            label = "Settings"
            action_names = [
                str(action.get("action") or "")
                for action in scene.get("actions", [])
                if isinstance(action, dict)
            ]
            note = "Options UI action sequence: " + ", ".join(action_names)
        else:
            status = "recorded"
            label = scene_type
            note = ""

        state = scene.get("page_state") if isinstance(scene.get("page_state"), dict) else {}
        if scene_type == "google-search":
            meta = query_metadata(str(scene.get("query", "")))
            input_surface = "Google search textarea[role=combobox] / textarea[name=q]"
        elif scene_type == "google-mode-showcase":
            meta = {
                "query_category": scene.get("query_category", "masking-mode-showcase"),
                "expected_result": scene.get("expected_result", ""),
            }
            input_surface = "Google search result mode showcase"
        elif scene_type == "youtube-watch-comments":
            meta = {
                "query_category": scene.get("query_category", "youtube-comment-profanity"),
                "expected_result": scene.get("expected_result", "mask-spans"),
            }
            input_surface = scene.get("input_surface", "YouTube search result / watch comments")
        else:
            meta = {"query_category": "", "expected_result": ""}
            input_surface = ""
        rows.append(
            {
                "run_id": metadata.get("run_id", ""),
                "scene_index": scene_index,
                "scene_type": scene_type,
                "label": label,
                "query": scene.get("query", ""),
                "query_category": meta.get("query_category", ""),
                "expected_result": meta.get("expected_result", ""),
                "intervention_mode": scene.get("intervention_mode", ""),
                "protection_enabled": scene.get("protection_enabled", ""),
                "input_surface": input_surface,
                "start_seconds": start_seconds if start_seconds is not None else "",
                "end_seconds": end_seconds if end_seconds is not None else "",
                "duration_seconds": round(float(end_seconds) - float(start_seconds), 3)
                if start_seconds not in (None, "") and end_seconds not in (None, "")
                else "",
                "attempt_count": len(scene_latency),
                "effective_masked_span_count": scene.get("effective_masked_span_count", ""),
                "render_box_count": diagnostics.get("renderBoxCount", ""),
                "inline_mask_count": diagnostics.get("inlineMaskCount", ""),
                "ai_overview_candidate_count": diagnostics.get("aiOverviewCandidateCount", ""),
                "duplicate_rendered_original_count": diagnostics.get("duplicateRenderedOriginalCount", ""),
                "preconceal_count_max": maximum(preconceal_values),
                "google_light_preconceal_count_max": maximum(google_light_preconceal_values),
                "total_to_mask_ms_avg": average(total_values),
                "total_to_mask_ms_max": maximum(total_values),
                "backend_round_trip_ms_avg": average(backend_values),
                "backend_round_trip_ms_max": maximum(backend_values),
                "candidate_collect_ms_avg": average(candidate_values),
                "parser_ms_avg": average(parser_values),
                "requested_analysis_count_max": maximum(requested_values),
                "backend_live_attempt_count": backend_live_attempt_count,
                "local_preflight_masked_span_count_max": maximum(local_preflight_values),
                "hover_probe_ok": bool(nested_get(scene, ["hover_probe", "ok"], False)),
                "hover_tooltip": nested_get(scene, ["hover_probe", "tooltip"], ""),
                "site_warning_continue_hidden": state.get("continueHidden", ""),
                "site_warning_continue_disabled": state.get("continueDisabled", ""),
                "settings_action_summary": note if scene_type == "settings" else "",
                "status": status,
                "evidence_note": note,
            }
        )
    return rows


def write_qa_report(
    path: Path,
    metadata: dict[str, Any],
    scene_rows: list[dict[str, Any]],
    latency_rows: list[dict[str, Any]],
) -> None:
    latency_sorted = sorted(
        latency_rows,
        key=lambda row: number_like(row.get("total_to_mask_ms")),
        reverse=True,
    )
    scene_lines = [
        (
            f"| {row.get('label')} | {row.get('start_seconds')}s-{row.get('end_seconds')}s | "
            f"{row.get('query') or row.get('status')} | {row.get('query_category') or '-'} | "
            f"{row.get('expected_result') or '-'} | masks {row.get('effective_masked_span_count')} | "
            f"backend attempts {row.get('backend_live_attempt_count') or 0} | "
            f"max {row.get('total_to_mask_ms_max') or '-'}ms |"
        )
        for row in scene_rows
    ]
    latency_lines = [
        (
            f"| {row.get('scene_index')} | {row.get('query')} | {row.get('attempt')} | "
            f"{row.get('total_to_mask_ms') or '-'} | {row.get('backend_round_trip_ms') or '-'} | "
            f"{row.get('candidate_collect_ms') or '-'} | {row.get('parser_ms') or '-'} |"
        )
        for row in latency_sorted[:8]
    ]
    content = "\n".join(
        [
            "# Chungmaru Chrome Demo QA Report",
            "",
            f"- Run ID: `{metadata.get('run_id')}`",
            f"- Video: `{metadata.get('video', '')}`",
            f"- Duration: `{metadata.get('duration_seconds')}` seconds",
            f"- FPS: `{metadata.get('fps')}`",
            f"- Attempt rows: `{len(latency_rows)}`",
            "",
            "## Scene Summary",
            "",
            "| Scene | Time | Input / Status | Category | Expected | Mask Count | Backend Attempts | Max Total Latency |",
            "| --- | ---: | --- | --- | --- | ---: | ---: | ---: |",
            *scene_lines,
            "",
            "## Slowest Attempts",
            "",
            "| Scene | Query | Attempt | Total to Mask ms | Backend ms | Candidate ms | Parser ms |",
            "| ---: | --- | ---: | ---: | ---: | ---: | ---: |",
            *latency_lines,
            "",
            "## Notes",
            "",
            "- Video captions identify the typed query even when the extension masks the search box itself.",
            "- Hover rows use the actual mask `title` / `aria-label` and mirror it as an in-video callout.",
            "- Scene summary is for presentation review; attempt latency CSV is for detailed measurement.",
            "- Site-warning rows record whether continue is hidden or disabled.",
        ]
    )
    path.write_text(content + "\n", encoding="utf-8")


def write_demo_script(
    path: Path,
    metadata: dict[str, Any],
    timeline_rows: list[dict[str, Any]],
    scene_rows: list[dict[str, Any]],
) -> None:
    scene_lines = []
    for scene in metadata.get("scenes", []):
        if not isinstance(scene, dict):
            continue
        if scene.get("type") == "google-search":
            hover_tooltip = nested_get(scene, ["hover_probe", "tooltip"], "")
            scene_lines.append(
                f"- Search {scene.get('display_index') or scene.get('index')}: `{scene.get('query')}` "
                f"({query_metadata(str(scene.get('query', ''))).get('query_category')}) "
                f"입력, effective masks={scene.get('effective_masked_span_count')}, "
                f"hover=`{hover_tooltip or 'not-recorded'}`, url={scene.get('url', '')}"
            )
        elif scene.get("type") == "google-mode-showcase":
            hover_tooltip = nested_get(scene, ["hover_probe", "tooltip"], "")
            mode = scene.get("intervention_mode") or "off"
            mode_label = INTERVENTION_MODE_LABELS.get(str(mode), "보호 OFF")
            scene_lines.append(
                f"- Mode {mode_label}: `{scene.get('query')}` "
                f"protection={scene.get('protection_enabled')}, "
                f"effective masks={scene.get('effective_masked_span_count')}, "
                f"hover=`{hover_tooltip or 'not-recorded'}`"
            )
        elif scene.get("type") == "youtube-watch-comments":
            hover_tooltip = nested_get(scene, ["hover_probe", "tooltip"], "")
            selected = scene.get("selected_video") if isinstance(scene.get("selected_video"), dict) else {}
            scene_lines.append(
                f"- YouTube: `{scene.get('query')}` 검색 결과에서 "
                f"`{selected.get('title') or scene.get('target_title') or 'selected video'}` 진입, "
                f"comments masks={scene.get('effective_masked_span_count')}, "
                f"hover=`{hover_tooltip or 'not-recorded'}`, url={scene.get('url', '')}"
            )
        elif scene.get("type") == "site-warning":
            state = scene.get("page_state") if isinstance(scene.get("page_state"), dict) else {}
            scene_lines.append(
                "- Site warning: "
                f"`{scene.get('target_url')}` 접속, continueHidden={state.get('continueHidden')}, "
                f"continueDisabled={state.get('continueDisabled')}"
            )
        elif scene.get("type") == "settings":
            scene_lines.append("- Settings: backend 연결, site policy, wellbeing widget debug override 확인")

    timeline_lines = [
        f"| {row['start_seconds']:.3f}s | {row['end_seconds']:.3f}s | {row['label']} |"
        for row in timeline_rows
    ]
    scene_summary_lines = [
        (
            f"| {row.get('label')} | {row.get('start_seconds')}s-{row.get('end_seconds')}s | "
            f"{row.get('query') or row.get('status')} | {row.get('query_category') or '-'} | "
            f"{row.get('expected_result') or '-'} |"
        )
        for row in scene_rows
    ]
    content = "\n".join(
        [
            "# Chungmaru Chrome Demo Script",
            "",
            f"- Run ID: `{metadata.get('run_id')}`",
            f"- Video: `{metadata.get('video', '')}`",
            f"- Duration: `{metadata.get('duration_seconds', '')}` seconds",
            f"- FPS: `{metadata.get('fps')}`",
            f"- Backend: `{metadata.get('backend')}`",
            "",
            "## Scene Summary",
            "",
            *scene_lines,
            "",
            "## Scene Timing Summary",
            "",
            "| Scene | Time | Input / Status | Category | Expected |",
            "| --- | ---: | --- | --- | --- |",
            *scene_summary_lines,
            "",
            "## Timeline",
            "",
            "| Start | End | What happens |",
            "| ---: | ---: | --- |",
            *timeline_lines,
            "",
            "## Generated Evidence Files",
            "",
            "- `metadata.json`",
            "- `demo-timeline.csv`",
            "- `chrome-demo-scene-summary.csv`",
            "- `chrome-demo-qa-report.md`",
            "- `chrome-demo-attempt-latency.csv`",
            "- `chrome-demo-attempt-latency.jsonl`",
        ]
    )
    path.write_text(content + "\n", encoding="utf-8")


def write_demo_evidence_files(
    output_dir: Path,
    metadata: dict[str, Any],
    latency_rows: list[dict[str, Any]],
    *,
    append_latency_csv: Path | None = None,
    append_latency_jsonl: Path | None = None,
) -> dict[str, str]:
    timeline_rows = build_timeline_rows(metadata)
    timeline_csv = output_dir / "demo-timeline.csv"
    timeline_json = output_dir / "demo-timeline.json"
    scene_csv = output_dir / "chrome-demo-scene-summary.csv"
    qa_report = output_dir / "chrome-demo-qa-report.md"
    latency_csv = output_dir / "chrome-demo-attempt-latency.csv"
    latency_jsonl = output_dir / "chrome-demo-attempt-latency.jsonl"
    script_md = output_dir / "demo-script.md"
    scene_rows = build_scene_summary_rows(metadata, latency_rows)

    write_csv(timeline_csv, timeline_rows, TIMELINE_FIELDS)
    timeline_json.write_text(json.dumps(timeline_rows, ensure_ascii=False, indent=2), encoding="utf-8")
    write_csv(scene_csv, scene_rows, SCENE_FIELDS)
    write_csv(latency_csv, latency_rows, LATENCY_FIELDS)
    write_jsonl(latency_jsonl, latency_rows)
    appended_csv = append_csv(append_latency_csv, latency_rows, LATENCY_FIELDS) if append_latency_csv else None
    appended_jsonl = append_jsonl(append_latency_jsonl, latency_rows) if append_latency_jsonl else None
    write_qa_report(qa_report, metadata, scene_rows, latency_rows)
    write_demo_script(script_md, metadata, timeline_rows, scene_rows)
    files = {
        "timeline_csv": str(timeline_csv),
        "timeline_json": str(timeline_json),
        "scene_summary_csv": str(scene_csv),
        "qa_report": str(qa_report),
        "latency_csv": str(latency_csv),
        "latency_jsonl": str(latency_jsonl),
        "demo_script": str(script_md),
    }
    if appended_csv:
        files["append_latency_csv"] = str(appended_csv)
    if appended_jsonl:
        files["append_latency_jsonl"] = str(appended_jsonl)
    return files


def run_interactive_demo(args: argparse.Namespace) -> None:
    args.output_dir.mkdir(parents=True, exist_ok=True)
    frames_dir = args.output_dir / "frames"
    if frames_dir.exists():
        shutil.rmtree(frames_dir)
    frames_dir.mkdir(parents=True, exist_ok=True)

    chrome_process: subprocess.Popen[bytes] | None = None
    worker: CdpWebSocket | None = None
    page: CdpWebSocket | None = None
    options_page: CdpWebSocket | None = None
    recorder: FrameRecorder | None = None
    latency_rows: list[dict[str, Any]] = []
    args._hover_probe_count = 0
    metadata: dict[str, Any] = {
        "run_id": args.run_id,
        "mode": "interactive",
        "backend": args.backend,
        "queries": args.queries,
        "include_mode_showcase": bool(args.include_mode_showcase),
        "mode_showcase_query": args.mode_showcase_query,
        "mode_showcase_modes": list(INTERVENTION_MODE_ORDER),
        "include_youtube_demo": bool(args.include_youtube_demo),
        "youtube_query": args.youtube_query,
        "youtube_search_url": args.youtube_search_url or youtube_search_url(args.youtube_query),
        "youtube_target_hints": parse_csv(args.youtube_target_hints),
        "warning_url": args.warning_url,
        "viewport": f"{args.viewport[0]}x{args.viewport[1]}",
        "fps": args.fps,
        "capture_fps": args.capture_fps,
        "video_crf": args.video_crf,
        "video_preset": args.video_preset,
        "video_pix_fmt": args.video_pix_fmt,
        "max_hover_probes": args.max_hover_probes,
        "scroll_return": bool(args.scroll_return),
        "youtube_post_mask_scroll": bool(args.youtube_post_mask_scroll),
        "target_duration_seconds": args.target_duration_seconds,
        "output_dir": str(args.output_dir),
        "output_policy": "archive" if args.archive_video else "latest-overwrite",
        "archive_video": bool(args.archive_video),
        "append_latency_log": not args.no_append_latency_log,
        "append_latency_csv": "" if args.no_append_latency_log else str(args.append_latency_csv),
        "append_latency_jsonl": "" if args.no_append_latency_log else str(args.append_latency_jsonl),
        "backend_warmup": getattr(args, "backend_warmup_result", {}),
        "scenes": [],
        "timeline": [],
    }

    try:
        chrome_process = launch_chrome(args)
        extension_id, worker_target = wait_for_service_worker(args.debugging_port, args.startup_timeout)
        worker = CdpWebSocket(str(worker_target["webSocketDebuggerUrl"]))
        settings = demo_settings(args)
        settings_write_ok = False
        try:
            metadata["settings_write"] = set_demo_settings(worker, settings)
            settings_write_ok = is_settings_write_successful(metadata["settings_write"])
        except Exception as error:  # noqa: BLE001 - fallback is recorded for demo evidence
            metadata["settings_write_error"] = str(error)
        try:
            metadata["control_surface"] = run_control_surface_checks(args, extension_id, settings)
        except Exception as error:  # noqa: BLE001 - demo should continue even if options checks fail
            metadata["control_surface"] = {"ok": False, "error": str(error)}

        options_target = create_tab(args.debugging_port, f"chrome-extension://{extension_id}/options.html")
        options_page = CdpWebSocket(str(options_target["webSocketDebuggerUrl"]))
        options_page.call("Page.enable")
        options_page.call("Runtime.enable")
        width, height = args.viewport
        options_page.call(
            "Emulation.setDeviceMetricsOverride",
            {
                "width": width,
                "height": height,
                "deviceScaleFactor": 1,
                "mobile": False,
            },
        )
        wait_for_page_ready(options_page, timeout_s=10)
        set_demo_caption(
            options_page,
            "설정 조작",
            "backend 연결 · site policy · wellbeing widget override",
        )
        recorder = FrameRecorder(options_page, frames_dir, args.fps, metadata, args.capture_fps)
        settings_first_frame = recorder.index + 1
        recorder.hold(args.settings_hold_seconds, "settings: open options")
        settings_actions = record_options_demo_sequence(options_page, recorder, args)
        metadata["scenes"].append(
            {
                "type": "settings",
                "url": page_location(options_page),
                "first_frame": settings_first_frame,
                "last_frame": recorder.index,
                "video_start_seconds": frame_start_seconds(settings_first_frame, args.fps),
                "video_end_seconds": round(recorder.video_seconds, 3),
                "actions": settings_actions,
                "settings_backend_enabled": nested_get(metadata, ["control_surface", "settings_backend_enabled"], ""),
                "backend_health_ok": nested_get(metadata, ["control_surface", "backend_health", "ok"], ""),
                "site_policy_verdict": nested_get(metadata, ["control_surface", "site_policy", "policy", "verdict"], ""),
            }
        )

        page_target = create_tab(args.debugging_port, "https://www.google.com/?hl=ko")
        page = CdpWebSocket(str(page_target["webSocketDebuggerUrl"]))
        page.call("Page.enable")
        page.call("Runtime.enable")
        page.call(
            "Emulation.setDeviceMetricsOverride",
            {
                "width": width,
                "height": height,
                "deviceScaleFactor": 1,
                "mobile": False,
            },
        )
        wait_for_page_ready(page, timeout_s=20)
        time.sleep(1.0)
        dismissed_consent = dismiss_google_consent(page)
        if dismissed_consent:
            time.sleep(1.0)

        recorder.set_page(page)
        if options_page:
            options_page.close()
            options_page = None
        set_demo_caption(page, "Google 검색 준비", "설정 전후 비교부터 결과 보호까지 녹화")
        recorder.hold(args.home_hold_seconds, "Google home")

        next_scene_index = 1
        if args.include_mode_showcase:
            next_scene_index = record_google_mode_showcase(
                args=args,
                page=page,
                worker=worker,
                recorder=recorder,
                base_settings=settings,
                metadata=metadata,
                latency_rows=latency_rows,
                start_index=next_scene_index,
            )

        for search_index, query in enumerate(args.queries, start=1):
            scene_index = next_scene_index + search_index - 1
            scene_first_frame = recorder.index + 1
            set_demo_caption(
                page,
                f"입력 {search_index:02d}: {query}",
                "Google 검색창 textarea에 직접 입력",
            )
            typed = type_google_query(page, query, recorder, f"search {search_index}")
            recorder.hold(args.typed_hold_seconds, f"search {search_index}: typed query: {query}")
            navigate_to_search(page, query)
            time.sleep(args.initial_wait)
            if dismiss_google_consent(page):
                time.sleep(0.8)
            set_demo_caption(
                page,
                f"검색 결과 보호: {query}",
                "검색 결과 제목 · AI 개요 · 관련 영역 검사",
            )
            recorder.hold(args.result_hold_seconds, f"search {search_index}: result loaded: {query}")
            diagnostics_before = collect_render_diagnostics(page)
            pipeline_video_start = round(recorder.video_seconds, 3)
            set_demo_caption(
                page,
                f"분석 요청: {query}",
                "candidate 수집 → backend 판정 → span 검증",
            )
            recorder.hold(args.analysis_hold_seconds, f"search {search_index}: analysis request: {query}")
            pipeline = run_pipeline_for_google_scene(
                worker,
                settings,
                settings_write_ok,
                reason=f"interactive-demo-google-search-{search_index}",
                force_settings_snapshot=args.force_settings_snapshot,
                attempts=args.google_pipeline_attempts,
            )
            diagnostics_after_pipeline = collect_render_diagnostics(page)
            effective_masks = effective_masked_span_count(
                pipeline,
                diagnostics_after_pipeline,
            )
            set_demo_caption(
                page,
                f"마스킹 결과: {query}",
                f"effective masks {effective_masks} · latency CSV 저장",
            )
            recorder.hold(args.masked_hold_seconds, f"search {search_index}: masked results: {query}")
            pipeline_video_end = round(recorder.video_seconds, 3)
            hover_probe: dict[str, Any] = {"ok": False, "reason": "DISABLED_OR_NO_MASK"}
            if not args.no_hover_probe and effective_masks > 0 and can_record_hover_probe(args):
                hover_probe = hover_first_mask(
                    page,
                    recorder,
                    label=f"search {search_index}: hover mask evidence: {query}",
                    hold_seconds=args.hover_hold_seconds,
                )
                mark_hover_probe_recorded(args)
            down = smooth_scroll(page, recorder, label=f"search {search_index}", direction="down", steps=args.scroll_steps)
            scroll_refresh_pipeline = run_pipeline_for_google_scene(
                worker,
                settings,
                settings_write_ok,
                reason=f"interactive-demo-google-search-{search_index}-after-scroll",
                force_settings_snapshot=args.force_settings_snapshot,
                attempts=1,
            )
            diagnostics_after_scroll = collect_render_diagnostics(page)
            recorder.hold(args.scroll_hold_seconds, f"search {search_index}: after scroll: {query}")
            if args.scroll_return:
                up = smooth_scroll(page, recorder, label=f"search {search_index}", direction="up", steps=max(8, args.scroll_steps // 2))
            else:
                up = {"ok": False, "reason": "DISABLED_BY_DEFAULT"}
            scene = {
                "type": "google-search",
                "index": scene_index,
                "display_index": search_index,
                "query": query,
                "url": page_location(page),
                "typed": typed,
                "pipeline": pipeline,
                "scroll_refresh_pipeline": scroll_refresh_pipeline,
                "scroll_down": down,
                "scroll_up": up,
                "first_frame": scene_first_frame,
                "last_frame": recorder.index,
                "video_start_seconds": frame_start_seconds(scene_first_frame, args.fps),
                "video_end_seconds": round(recorder.video_seconds, 3),
                "pipeline_video_start_seconds": pipeline_video_start,
                "pipeline_video_end_seconds": pipeline_video_end,
                "render_diagnostics_before": diagnostics_before,
                "render_diagnostics_after_pipeline": diagnostics_after_pipeline,
                "render_diagnostics_after_scroll": diagnostics_after_scroll,
                "hover_probe": hover_probe,
                "effective_masked_span_count": effective_masks,
                "last_stats": get_last_stats(worker),
            }
            metadata["scenes"].append(scene)
            latency_rows.extend(
                build_latency_rows(
                    run_id=args.run_id,
                    scene=scene,
                    pipeline=pipeline,
                    diagnostics=diagnostics_after_pipeline,
                    effective_masks=effective_masks,
                    video_start_seconds=pipeline_video_start,
                    video_end_seconds=pipeline_video_end,
                )
            )

        next_scene_index += len(args.queries)

        if args.include_youtube_demo:
            youtube_scene_index = next_scene_index
            scene_first_frame = recorder.index + 1
            set_demo_caption(
                page,
                "YouTube 검색",
                f"{args.youtube_query} 결과에서 대상 영상 선택",
            )
            navigate_to_youtube_search(
                page,
                query=args.youtube_query,
                search_url=args.youtube_search_url,
                timeout_s=24,
            )
            time.sleep(args.youtube_initial_wait)
            youtube_overlay_cleanup: list[dict[str, Any]] = []
            youtube_overlay_cleanup.append(dismiss_youtube_overlays(page))
            recorder.hold(args.youtube_search_hold_seconds, f"youtube: search results: {args.youtube_query}")
            selected_video = select_youtube_video_from_results(
                page,
                hints=parse_csv(args.youtube_target_hints),
                timeout_s=18,
            )
            wait_for_page_ready(page, timeout_s=24)
            youtube_overlay_cleanup.append(dismiss_youtube_overlays(page))
            time.sleep(args.youtube_watch_wait)
            youtube_overlay_cleanup.append(dismiss_youtube_overlays(page))
            youtube_watch_url = page_location(page)
            youtube_page_info = collect_youtube_diagnostics(page)
            selected_title = nested_get(selected_video, ["selected", "title"], "") or youtube_page_info.get("title", "")
            set_demo_caption(
                page,
                "YouTube 영상 진입",
                selected_title or "대상 영상 페이지 로드",
            )
            recorder.hold(args.youtube_watch_hold_seconds, "youtube: watch page loaded")
            comments_scroll = scroll_youtube_to_comments(
                page,
                recorder,
                label="youtube",
                steps=args.youtube_scroll_steps,
            )
            youtube_overlay_cleanup.append(dismiss_youtube_overlays(page))
            set_demo_caption(
                page,
                "YouTube 댓글 검사",
                "영상 제목 · 댓글 후보 · 현재 화면 span 마스킹",
            )
            recorder.hold(args.youtube_comments_hold_seconds, "youtube: comments visible")
            diagnostics_before = collect_render_diagnostics(page)
            youtube_diagnostics_before = collect_youtube_diagnostics(page)
            pipeline_video_start = round(recorder.video_seconds, 3)
            set_demo_caption(
                page,
                "YouTube 분석 요청",
                "댓글 후보 수집 → backend 판정 → 현재 화면 반영",
            )
            recorder.hold(args.analysis_hold_seconds, "youtube: analysis request")
            pipeline = run_pipeline_for_page_scene(
                worker,
                settings,
                settings_write_ok,
                page_url_prefix="https://www.youtube.com/watch",
                reason="manual-request",
                force_settings_snapshot=args.force_settings_snapshot,
                attempts=args.youtube_pipeline_attempts,
            )
            youtube_overlay_cleanup.append(dismiss_youtube_overlays(page))
            diagnostics_after_pipeline = collect_render_diagnostics(page)
            youtube_diagnostics_after = collect_youtube_diagnostics(page)
            effective_masks = effective_masked_span_count(pipeline, diagnostics_after_pipeline)
            set_demo_caption(
                page,
                "YouTube 마스킹 결과",
                f"effective masks {effective_masks} · 댓글/제목 보호 확인",
            )
            recorder.hold(args.youtube_masked_hold_seconds, "youtube: masked comments")
            pipeline_video_end = round(recorder.video_seconds, 3)
            hover_probe: dict[str, Any] = {"ok": False, "reason": "DISABLED_OR_NO_MASK"}
            if not args.no_hover_probe and effective_masks > 0 and can_record_hover_probe(args):
                hover_probe = hover_first_mask(
                    page,
                    recorder,
                    label="youtube: hover mask evidence",
                    hold_seconds=args.hover_hold_seconds,
                )
                mark_hover_probe_recorded(args)
            if args.youtube_post_mask_scroll:
                comments_scroll_after = smooth_scroll(
                    page,
                    recorder,
                    label="youtube comments",
                    direction="down",
                    steps=max(8, args.youtube_scroll_steps // 2),
                    fraction=0.9,
                )
                recorder.hold(args.scroll_hold_seconds, "youtube: after comment scroll")
            else:
                comments_scroll_after = {"ok": False, "reason": "DISABLED_BY_DEFAULT"}
            scene = {
                "type": "youtube-watch-comments",
                "index": youtube_scene_index,
                "query": args.youtube_query,
                "query_display": f"YouTube: {args.youtube_query}",
                "query_scenario_id": "youtube-sik-k-comments",
                "query_category": "youtube-comment-profanity",
                "expected_result": "mask-spans",
                "input_surface": "YouTube search result / watch title / comments",
                "target_url": youtube_watch_url,
                "url": page_location(page),
                "target_title": selected_title,
                "selected_video": selected_video.get("selected") if isinstance(selected_video, dict) else selected_video,
                "selected_video_candidates": selected_video.get("candidates", []) if isinstance(selected_video, dict) else [],
                "selection_result": selected_video,
                "youtube_overlay_cleanup": youtube_overlay_cleanup,
                "comments_scroll": comments_scroll,
                "comments_scroll_after": comments_scroll_after,
                "pipeline": pipeline,
                "first_frame": scene_first_frame,
                "last_frame": recorder.index,
                "video_start_seconds": frame_start_seconds(scene_first_frame, args.fps),
                "video_end_seconds": round(recorder.video_seconds, 3),
                "pipeline_video_start_seconds": pipeline_video_start,
                "pipeline_video_end_seconds": pipeline_video_end,
                "render_diagnostics_before": diagnostics_before,
                "render_diagnostics_after_pipeline": diagnostics_after_pipeline,
                "youtube_diagnostics_before": youtube_diagnostics_before,
                "youtube_diagnostics_after": youtube_diagnostics_after,
                "hover_probe": hover_probe,
                "effective_masked_span_count": effective_masks,
                "last_stats": get_last_stats(worker),
            }
            metadata["scenes"].append(scene)
            latency_rows.extend(
                build_latency_rows(
                    run_id=args.run_id,
                    scene=scene,
                    pipeline=pipeline,
                    diagnostics=diagnostics_after_pipeline,
                    effective_masks=effective_masks,
                    video_start_seconds=pipeline_video_start,
                    video_end_seconds=pipeline_video_end,
                )
            )

        set_demo_caption(
            page,
            "위험 사이트 테스트",
            f"{args.warning_url} 이동 후 site-warning 정책 확인",
        )
        recorder.hold(args.result_hold_seconds, "site warning: before navigation")
        warning_first_frame = recorder.index + 1
        page.call("Page.navigate", {"url": args.warning_url}, timeout_s=8)
        time.sleep(2.5)
        wait_for_page_ready(page, timeout_s=10)
        warning_location = page_location(page)
        set_demo_caption(
            page,
            "위험 사이트 차단",
            "고위험 판정: 돌아가기만 활성 · 계속 접속 숨김",
        )
        try:
            warning_page_state = page.evaluate(
                """(() => ({
                  title: document.getElementById("warningTitle")?.textContent || "",
                  status: document.getElementById("warningStatus")?.textContent || "",
                  continueHidden: Boolean(document.getElementById("continueButton")?.hidden),
                  continueDisabled: Boolean(document.getElementById("continueButton")?.disabled),
                  continueText: document.getElementById("continueButton")?.textContent || "",
                  backText: document.getElementById("backButton")?.textContent || ""
                }))()""",
                timeout_s=5,
            )
        except Exception as error:  # noqa: BLE001 - diagnostic only
            warning_page_state = {"ok": False, "error": str(error)}
        recorder.hold(args.warning_hold_seconds, "site warning")
        metadata["scenes"].append(
            {
                "type": "site-warning",
                "target_url": args.warning_url,
                "current_url": warning_location,
                "first_frame": warning_first_frame,
                "last_frame": recorder.index,
                "video_start_seconds": frame_start_seconds(warning_first_frame, args.fps),
                "video_end_seconds": round(recorder.video_seconds, 3),
                "ok": warning_location.startswith("chrome-extension://") and "site-warning.html" in warning_location,
                "page_state": warning_page_state,
            }
        )

        current_duration = recorder.video_seconds
        if current_duration < args.target_duration_seconds:
            set_demo_caption(
                page,
                "최종 확인",
                "검색 보호 · AI 개요 · 사이트 경고 · 설정 로그 저장 완료",
            )
            recorder.hold(args.target_duration_seconds - current_duration, "final evidence review")

        output_video = args.output_dir / "chungmaru-google-demo.mp4"
        build_video(
            frames_dir,
            output_video,
            args.fps,
            crf=args.video_crf,
            preset=args.video_preset,
            pix_fmt=args.video_pix_fmt,
        )
        metadata["video"] = str(output_video)
        metadata["video_path"] = str(output_video)
        metadata["output_dir"] = str(args.output_dir)
        metadata["frame_count"] = recorder.index
        metadata["actual_capture_count"] = recorder.actual_capture_count
        metadata["duplicated_frame_count"] = recorder.duplicated_frame_count
        metadata["duration_seconds"] = round(recorder.index / max(1, args.fps), 3)
        metadata["extension_id"] = extension_id
        metadata["latency_attempt_count"] = len(latency_rows)
        metadata["evidence_files"] = write_demo_evidence_files(
            args.output_dir,
            metadata,
            latency_rows,
            append_latency_csv=None if args.no_append_latency_log else args.append_latency_csv,
            append_latency_jsonl=None if args.no_append_latency_log else args.append_latency_jsonl,
        )
        (args.output_dir / "metadata.json").write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        if not args.keep_frames:
            shutil.rmtree(frames_dir)
        print(f"video={output_video}")
        print(f"metadata={args.output_dir / 'metadata.json'}")
        print(f"timeline_csv={args.output_dir / 'demo-timeline.csv'}")
        print(f"latency_csv={args.output_dir / 'chrome-demo-attempt-latency.csv'}")
        print(f"demo_script={args.output_dir / 'demo-script.md'}")
        print(f"frames={'kept' if args.keep_frames else 'removed'}")
        print(f"extension_id={extension_id}")
    finally:
        if options_page:
            options_page.close()
        if page:
            page.close()
        if worker:
            worker.close()
        if chrome_process:
            chrome_process.terminate()
            try:
                chrome_process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                chrome_process.kill()


def run_demo(args: argparse.Namespace) -> None:
    args.output_dir.mkdir(parents=True, exist_ok=True)
    frames_dir = args.output_dir / "frames"
    if frames_dir.exists():
        shutil.rmtree(frames_dir)
    frames_dir.mkdir(parents=True, exist_ok=True)

    chrome_process: subprocess.Popen[bytes] | None = None
    worker: CdpWebSocket | None = None
    page: CdpWebSocket | None = None
    metadata: dict[str, Any] = {
        "run_id": args.run_id,
        "backend": args.backend,
        "queries": args.queries,
        "viewport": f"{args.viewport[0]}x{args.viewport[1]}",
        "fps": args.fps,
        "capture_fps": args.capture_fps,
        "video_crf": args.video_crf,
        "video_preset": args.video_preset,
        "video_pix_fmt": args.video_pix_fmt,
        "output_policy": "archive" if args.archive_video else "latest-overwrite",
        "archive_video": bool(args.archive_video),
        "seconds_per_scene": args.seconds_per_scene,
        "scenes": [],
    }

    try:
        chrome_process = launch_chrome(args)
        extension_id, worker_target = wait_for_service_worker(args.debugging_port, args.startup_timeout)
        worker = CdpWebSocket(str(worker_target["webSocketDebuggerUrl"]))
        settings = demo_settings(args)
        settings_write_ok = False
        try:
            metadata["settings_write"] = set_demo_settings(worker, settings)
            settings_write_ok = is_settings_write_successful(metadata["settings_write"])
        except Exception as error:  # noqa: BLE001 - fallback is recorded for demo evidence
            metadata["settings_write_error"] = str(error)
        try:
            metadata["control_surface"] = run_control_surface_checks(args, extension_id, settings)
        except Exception as error:  # noqa: BLE001 - demo should continue even if options checks fail
            metadata["control_surface"] = {"ok": False, "error": str(error)}

        first_url = google_search_url(args.queries[0])
        page_target = create_tab(args.debugging_port, first_url)
        page = CdpWebSocket(str(page_target["webSocketDebuggerUrl"]))
        page.call("Page.enable")
        page.call("Runtime.enable")
        width, height = args.viewport
        page.call(
            "Emulation.setDeviceMetricsOverride",
            {
                "width": width,
                "height": height,
                "deviceScaleFactor": 1,
                "mobile": False,
            },
        )

        frame_index = 0
        actual_capture_count = 0
        duplicated_frame_count = 0
        last_frame_path: Path | None = None
        capture_interval = max(1, round(args.fps / max(1, args.capture_fps)))
        frames_per_scene = max(1, int(args.seconds_per_scene * args.fps))
        for scene_index, query in enumerate(args.queries, start=1):
            url = google_search_url(query)
            page.call("Page.navigate", {"url": url}, timeout_s=8)
            wait_for_page_ready(page, timeout_s=20)
            time.sleep(args.initial_wait)
            dismissed_consent = dismiss_google_consent(page)
            if dismissed_consent:
                time.sleep(1.0)
            diagnostics_before = collect_render_diagnostics(page)
            if settings_write_ok:
                settings_response = {
                    "ok": True,
                    "skipped": True,
                    "reason": "SETTINGS_ALREADY_WRITTEN_TO_STORAGE",
                }
            else:
                settings_response = send_to_fixture_tab(
                    worker,
                    "https://www.google.com/search",
                    {"type": "APPLY_SETTINGS_SNAPSHOT", "settings": settings},
                    inject_on_failure=True,
                    timeout_s=10,
                )
            time.sleep(0.4)
            trigger_response = send_to_fixture_tab(
                worker,
                "https://www.google.com/search",
                {"type": "RUN_PIPELINE", "reason": google_pipeline_reason("demo-google-search")},
                inject_on_failure=False,
                timeout_s=15,
            )
            time.sleep(args.post_trigger_wait)
            diagnostics_after_pipeline = collect_render_diagnostics(page)
            last_stats = get_last_stats(worker)
            scene_meta = {
                "index": scene_index,
                "query": query,
                "url": url,
                "dismissed_consent": dismissed_consent,
                "settings_response": settings_response,
                "trigger_response": trigger_response,
                "render_diagnostics_before": diagnostics_before,
                "render_diagnostics_after_pipeline": diagnostics_after_pipeline,
                "effective_masked_span_count": effective_masked_span_count(
                    {
                        "trigger_response": trigger_response,
                        "last_stats": last_stats,
                    },
                    diagnostics_after_pipeline,
                ),
                "last_stats": last_stats,
                "first_frame": frame_index + 1,
            }
            for _ in range(frames_per_scene):
                frame_index += 1
                output_frame = frames_dir / f"frame-{frame_index:04d}.png"
                if last_frame_path and capture_interval > 1 and frame_index % capture_interval != 1:
                    shutil.copyfile(last_frame_path, output_frame)
                    duplicated_frame_count += 1
                else:
                    capture_frame(page, output_frame)
                    actual_capture_count += 1
                    last_frame_path = output_frame
                time.sleep(1 / args.fps)
            scene_meta["last_frame"] = frame_index
            metadata["scenes"].append(scene_meta)

        output_video = args.output_dir / "chungmaru-google-demo.mp4"
        build_video(
            frames_dir,
            output_video,
            args.fps,
            crf=args.video_crf,
            preset=args.video_preset,
            pix_fmt=args.video_pix_fmt,
        )
        metadata["video"] = str(output_video)
        metadata["frame_count"] = frame_index
        metadata["actual_capture_count"] = actual_capture_count
        metadata["duplicated_frame_count"] = duplicated_frame_count
        metadata["duration_seconds"] = round(frame_index / max(1, args.fps), 3)
        (args.output_dir / "metadata.json").write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        if not args.keep_frames:
            shutil.rmtree(frames_dir)
        print(f"video={output_video}")
        print(f"metadata={args.output_dir / 'metadata.json'}")
        print(f"frames={'kept' if args.keep_frames else 'removed'}")
        print(f"extension_id={extension_id}")
    finally:
        if page:
            page.close()
        if worker:
            worker.close()
        if chrome_process:
            chrome_process.terminate()
            try:
                chrome_process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                chrome_process.kill()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Record a Chrome Google-search demo with the Chungmaru extension.")
    parser.add_argument("--run-id", default=f"chrome-google-demo-{now_id()}")
    parser.add_argument("--mode", choices=["interactive", "simple"], default="interactive")
    parser.add_argument("--output-dir", type=Path, default=None)
    parser.add_argument("--archive-root", type=Path, default=DEFAULT_ARCHIVE_OUTPUT_ROOT)
    parser.add_argument("--archive-video", action="store_true")
    parser.add_argument("--append-latency-csv", type=Path, default=DEFAULT_APPEND_LATENCY_CSV)
    parser.add_argument("--append-latency-jsonl", type=Path, default=DEFAULT_APPEND_LATENCY_JSONL)
    parser.add_argument("--no-append-latency-log", action="store_true")
    parser.add_argument("--backend", default="http://127.0.0.1:8000")
    parser.add_argument("--no-backend-warmup", action="store_true")
    parser.add_argument("--backend-warmup-timeout", type=float, default=60.0)
    parser.add_argument("--extension-dir", type=Path, default=DEFAULT_EXTENSION_DIR)
    parser.add_argument("--chrome-path", type=Path, default=None)
    parser.add_argument("--profile-dir", type=Path, default=None)
    parser.add_argument("--chrome-log", type=Path, default=None)
    parser.add_argument("--headless", action="store_true")
    parser.add_argument("--visible", action="store_true", help="Show Chrome at 0,0 while recording.")
    parser.add_argument("--window-position", default=None)
    parser.add_argument("--debugging-port", type=int, default=9242)
    parser.add_argument("--startup-timeout", type=float, default=20)
    parser.add_argument("--viewport", type=parse_viewport, default=(1440, 900))
    parser.add_argument("--fps", type=int, default=30)
    parser.add_argument("--capture-fps", type=int, default=30)
    parser.add_argument("--video-crf", type=int, default=16)
    parser.add_argument("--video-preset", default="slow")
    parser.add_argument("--video-pix-fmt", choices=["yuv420p", "yuv444p"], default="yuv420p")
    parser.add_argument("--seconds-per-scene", type=float, default=5)
    parser.add_argument("--target-duration-seconds", type=float, default=105.0)
    parser.add_argument("--settings-hold-seconds", type=float, default=8.0)
    parser.add_argument("--settings-step-hold-seconds", type=float, default=2.0)
    parser.add_argument("--home-hold-seconds", type=float, default=4.0)
    parser.add_argument("--typed-hold-seconds", type=float, default=2.5)
    parser.add_argument("--result-hold-seconds", type=float, default=2.0)
    parser.add_argument("--analysis-hold-seconds", type=float, default=1.2)
    parser.add_argument("--masked-hold-seconds", type=float, default=7.0)
    parser.add_argument("--hover-hold-seconds", type=float, default=2.5)
    parser.add_argument("--max-hover-probes", type=int, default=2)
    parser.add_argument("--no-hover-probe", action="store_true")
    parser.add_argument("--scroll-hold-seconds", type=float, default=2.0)
    parser.add_argument("--initial-wait", type=float, default=1.6)
    parser.add_argument("--post-trigger-wait", type=float, default=2.4)
    parser.add_argument("--scroll-steps", type=int, default=16)
    parser.add_argument("--scroll-return", action="store_true")
    parser.add_argument("--warning-url", default=DEFAULT_WARNING_URL)
    parser.add_argument("--warning-hold-seconds", type=float, default=8.0)
    parser.add_argument("--force-settings-snapshot", action="store_true")
    parser.add_argument("--google-pipeline-attempts", type=int, default=3)
    parser.add_argument("--sensitivity", type=int, default=60)
    parser.add_argument("--query-set", default=DEFAULT_QUERY_SET)
    parser.add_argument("--queries", nargs="+", default=None)
    parser.add_argument("--no-mode-showcase", dest="include_mode_showcase", action="store_false")
    parser.set_defaults(include_mode_showcase=True)
    parser.add_argument("--mode-showcase-query", default=DEFAULT_MODE_SHOWCASE_QUERY)
    parser.add_argument("--mode-showcase-initial-wait", type=float, default=0.9)
    parser.add_argument("--mode-showcase-raw-hold-seconds", type=float, default=2.0)
    parser.add_argument("--mode-showcase-apply-hold-seconds", type=float, default=0.7)
    parser.add_argument("--mode-showcase-result-hold-seconds", type=float, default=2.4)
    parser.add_argument("--mode-showcase-hover-hold-seconds", type=float, default=1.2)
    parser.add_argument("--mode-showcase-pipeline-attempts", type=int, default=2)
    parser.add_argument("--include-youtube-demo", action="store_true")
    parser.add_argument("--youtube-query", default=DEFAULT_YOUTUBE_QUERY)
    parser.add_argument("--youtube-search-url", default=None)
    parser.add_argument("--youtube-target-hints", default=DEFAULT_YOUTUBE_TARGET_HINTS)
    parser.add_argument("--youtube-initial-wait", type=float, default=2.0)
    parser.add_argument("--youtube-search-hold-seconds", type=float, default=2.0)
    parser.add_argument("--youtube-watch-wait", type=float, default=1.4)
    parser.add_argument("--youtube-watch-hold-seconds", type=float, default=1.0)
    parser.add_argument("--youtube-comments-hold-seconds", type=float, default=3.0)
    parser.add_argument("--youtube-masked-hold-seconds", type=float, default=5.0)
    parser.add_argument("--youtube-scroll-steps", type=int, default=18)
    parser.add_argument("--youtube-post-mask-scroll", action="store_true")
    parser.add_argument("--youtube-pipeline-attempts", type=int, default=3)
    parser.add_argument("--clean-profile", action="store_true", default=True)
    parser.add_argument("--keep-frames", action="store_true")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    args.chrome_path = detect_chrome_path(str(args.chrome_path) if args.chrome_path else None)
    if args.output_dir is None:
        args.output_dir = (
            args.archive_root / args.run_id
            if args.archive_video
            else DEFAULT_LATEST_OUTPUT_DIR
        )
    if args.profile_dir is None:
        args.profile_dir = Path("/tmp") / f"chungmaru-google-demo-profile-{args.run_id}"
    if args.chrome_log is None:
        args.chrome_log = Path("/private/tmp/chungmaru-chrome-demo.log")
    if args.window_position is None:
        args.window_position = "0,0" if args.visible else "-4000,0"
    if args.queries is None:
        args.queries = queries_for_set(args.query_set)
    args.backend_warmup_result = (
        {"ok": True, "skipped": True}
        if args.no_backend_warmup
        else warmup_backend(
            args.backend,
            timeout_s=args.backend_warmup_timeout,
            sensitivity=args.sensitivity,
        )
    )
    print(f"backend_warmup={json.dumps(args.backend_warmup_result, ensure_ascii=False)}")
    args.start_minimized = False
    if not args.extension_dir.exists():
        raise SystemExit(f"Extension directory not found: {args.extension_dir}")
    if args.mode == "simple":
        run_demo(args)
    else:
        run_interactive_demo(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
