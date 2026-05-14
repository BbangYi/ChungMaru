from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT / "data"
OUTPUT_PATH = DATA_DIR / "site_intel_seed_massive.json"


def load_existing_domains() -> set[str]:
    domains: set[str] = set()
    for path in sorted(DATA_DIR.glob("site_intel_seed*.json")):
        if path.name == OUTPUT_PATH.name:
            continue
        payload = json.loads(path.read_text(encoding="utf-8"))
        for item in payload:
            domain = str(item.get("domain") or "").strip().lower()
            if domain:
                domains.add(domain)
    return domains


def push(entries: list[dict], seen: set[str], entry: dict) -> None:
    domain = str(entry["domain"]).strip().lower()
    if domain in seen:
        return
    seen.add(domain)
    entries.append(entry)


def allow_entry(domain: str, title: str, summary: str, category: str, region: str, language: str, tags: list[str], aliases: list[str], indicators: list[str]) -> dict:
    return {
        "domain": domain,
        "title": title,
        "summary": summary,
        "category": category,
        "risk_level": "allow",
        "security_threat": False,
        "harmful_content": False,
        "source": "curated-seed-massive",
        "region": region,
        "language": language,
        "tags": tags,
        "aliases": aliases,
        "indicators": indicators,
        "risk_types": [],
    }


def warning_entry(domain: str, title: str, summary: str, category: str, region: str, language: str, tags: list[str], aliases: list[str], indicators: list[str], risk_types: list[str] | None = None, harmful: bool = True) -> dict:
    return {
        "domain": domain,
        "title": title,
        "summary": summary,
        "category": category,
        "risk_level": "warning",
        "security_threat": False,
        "harmful_content": harmful,
        "source": "curated-seed-massive",
        "region": region,
        "language": language,
        "tags": tags,
        "aliases": aliases,
        "indicators": indicators,
        "risk_types": risk_types or (["harmful-content"] if harmful else []),
    }


def block_entry(domain: str, title: str, summary: str, category: str, region: str, language: str, tags: list[str], aliases: list[str], indicators: list[str], risk_types: list[str], security_threat: bool, harmful_content: bool) -> dict:
    return {
        "domain": domain,
        "title": title,
        "summary": summary,
        "category": category,
        "risk_level": "block",
        "security_threat": security_threat,
        "harmful_content": harmful_content,
        "source": "curated-seed-massive",
        "region": region,
        "language": language,
        "tags": tags,
        "aliases": aliases,
        "indicators": indicators,
        "risk_types": risk_types,
    }


def build_allow_entries(entries: list[dict], seen: set[str]) -> None:
    safe_sites = [
        ("mozilla.org", "Mozilla", "오픈소스 브라우저 및 웹 기술 관련 공식 도메인.", "developer", "global", "en", ["browser", "opensource", "trusted"], ["mozilla"], ["official open source foundation"]),
        ("ubuntu.com", "Ubuntu", "리눅스 배포판 및 클라우드 솔루션의 공식 도메인.", "developer", "global", "en", ["linux", "opensource", "trusted"], ["ubuntu"], ["official linux distribution"]),
        ("pypi.org", "PyPI", "Python 패키지 배포를 위한 공식 저장소 서비스.", "developer", "global", "en", ["python", "package", "trusted"], ["pypi"], ["official package repository"]),
        ("npmjs.com", "npm", "Node.js 패키지 관리 서비스의 공식 도메인.", "developer", "global", "en", ["node", "package", "trusted"], ["npm"], ["official package registry"]),
        ("khanacademy.org", "Khan Academy", "온라인 교육 콘텐츠를 제공하는 비영리 학습 플랫폼.", "education", "global", "en", ["education", "learning", "trusted"], ["khan academy"], ["online learning platform"]),
        ("coursera.org", "Coursera", "온라인 강좌 및 교육 프로그램을 제공하는 학습 플랫폼.", "education", "global", "en", ["education", "course", "trusted"], ["coursera"], ["online course platform"]),
        ("edx.org", "edX", "대학 연계 온라인 강좌를 제공하는 교육 플랫폼.", "education", "global", "en", ["education", "course", "trusted"], ["edx"], ["university course platform"]),
        ("mit.edu", "MIT", "미국 MIT 대학의 공식 도메인.", "education", "global", "en", ["education", "university", "trusted"], ["mit"], ["official university domain"]),
        ("stanford.edu", "Stanford University", "스탠퍼드 대학의 공식 도메인.", "education", "global", "en", ["education", "university", "trusted"], ["stanford"], ["official university domain"]),
        ("harvard.edu", "Harvard University", "하버드 대학의 공식 도메인.", "education", "global", "en", ["education", "university", "trusted"], ["harvard"], ["official university domain"]),
        ("bbc.com", "BBC", "글로벌 뉴스 및 미디어 서비스의 공식 도메인.", "news", "global", "en", ["news", "media", "trusted"], ["bbc"], ["major news organization"]),
        ("nytimes.com", "The New York Times", "국제 뉴스 보도를 제공하는 언론사의 공식 도메인.", "news", "global", "en", ["news", "media", "trusted"], ["nyt", "new york times"], ["major news organization"]),
        ("reuters.com", "Reuters", "글로벌 통신사 및 뉴스 서비스의 공식 도메인.", "news", "global", "en", ["news", "wire", "trusted"], ["reuters"], ["major news organization"]),
        ("chosun.com", "조선일보", "국내 뉴스와 기사를 제공하는 언론사 공식 도메인.", "news", "kr", "ko", ["news", "media", "trusted"], ["조선일보"], ["korean news organization"]),
        ("hani.co.kr", "한겨레", "국내 뉴스 보도를 제공하는 언론사 공식 도메인.", "news", "kr", "ko", ["news", "media", "trusted"], ["한겨레"], ["korean news organization"]),
        ("joongang.co.kr", "중앙일보", "국내 뉴스와 기사를 제공하는 언론사 공식 도메인.", "news", "kr", "ko", ["news", "media", "trusted"], ["중앙일보"], ["korean news organization"]),
        ("yonhapnews.co.kr", "연합뉴스", "국내 통신사 및 뉴스 서비스의 공식 도메인.", "news", "kr", "ko", ["news", "wire", "trusted"], ["연합뉴스"], ["korean news wire"]),
        ("snu.ac.kr", "서울대학교", "서울대학교의 공식 도메인.", "education", "kr", "ko", ["education", "university", "trusted"], ["서울대", "snu"], ["official university domain"]),
        ("korea.ac.kr", "고려대학교", "고려대학교의 공식 도메인.", "education", "kr", "ko", ["education", "university", "trusted"], ["고려대"], ["official university domain"]),
        ("yonsei.ac.kr", "연세대학교", "연세대학교의 공식 도메인.", "education", "kr", "ko", ["education", "university", "trusted"], ["연세대"], ["official university domain"]),
        ("ewha.ac.kr", "이화여자대학교", "이화여자대학교의 공식 도메인.", "education", "kr", "ko", ["education", "university", "trusted"], ["이화여대"], ["official university domain"]),
        ("kakaoenterprise.com", "Kakao Enterprise", "카카오 계열 B2B 및 클라우드 서비스 공식 도메인.", "corporate", "kr", "ko", ["corporate", "cloud", "trusted"], ["kakao enterprise"], ["official corporate domain"]),
        ("aws.amazon.com", "Amazon Web Services", "AWS 공식 클라우드 서비스 도메인.", "cloud", "global", "en", ["cloud", "infrastructure", "trusted"], ["aws"], ["official cloud vendor"]),
        ("azure.microsoft.com", "Microsoft Azure", "Microsoft Azure 공식 클라우드 서비스 도메인.", "cloud", "global", "en", ["cloud", "infrastructure", "trusted"], ["azure"], ["official cloud vendor"]),
        ("vercel.com", "Vercel", "웹 애플리케이션 배포 및 호스팅 서비스 공식 도메인.", "developer", "global", "en", ["hosting", "developer", "trusted"], ["vercel"], ["official hosting platform"]),
        ("netlify.com", "Netlify", "웹사이트 배포 및 호스팅 서비스 공식 도메인.", "developer", "global", "en", ["hosting", "developer", "trusted"], ["netlify"], ["official hosting platform"])
    ]
    for row in safe_sites:
        push(entries, seen, allow_entry(*row))


def build_warning_entries(entries: list[dict], seen: set[str]) -> None:
    global_communities = [
        ("4chan.org", "4chan"), ("8kun.top", "8kun"), ("lemmy.world", "Lemmy"), ("voat-archive.net", "Voat Archive"),
        ("kiwifarms-archive.net", "Kiwi Farms Archive"), ("truthsocial.com", "Truth Social"), ("gab.com", "Gab"),
        ("rumble.com", "Rumble"), ("telegram.org", "Telegram"), ("discord.com", "Discord")
    ]
    for domain, title in global_communities:
        push(
            entries,
            seen,
            warning_entry(
                domain,
                title,
                "사용자 생성 콘텐츠 또는 커뮤니티 성격이 강해 채널·게시판별 위험도 편차가 큰 서비스로 분류된다.",
                "community",
                "global",
                "en",
                ["community", "ugc", "forum"],
                [title.lower()],
                ["user generated content", "content variability"],
            ),
        )

    kr_communities = [
        ("mlbpark.donga.com", "엠엘비파크"),
        ("ppomppu.co.kr", "뽐뿌"),
        ("clien.net", "클리앙"),
        ("inven.co.kr", "인벤"),
        ("pgr21.com", "PGR21"),
        ("todayhumor.co.kr", "오늘의유머"),
        ("slrclub.com", "SLR클럽"),
        ("etoland.co.kr", "이토랜드"),
        ("82cook.com", "82cook"),
        ("theqoo.net", "더쿠")
    ]
    for domain, title in kr_communities:
        push(
            entries,
            seen,
            warning_entry(
                domain,
                title,
                "국내 커뮤니티 또는 게시판 서비스로 게시글 주제와 이용자 층에 따라 유해 표현 노출 편차가 존재한다.",
                "community",
                "kr",
                "ko",
                ["community", "ugc", "forum"],
                [title.lower()],
                ["community board", "ugc variability"],
            ),
        )

    blog_platforms = [
        ("medium.com", "Medium", "global", "en"),
        ("substack.com", "Substack", "global", "en"),
        ("wordpress.com", "WordPress.com", "global", "en"),
        ("blogger.com", "Blogger", "global", "en"),
        ("brunch.co.kr", "브런치", "kr", "ko"),
        ("velog.io", "Velog", "kr", "ko"),
        ("notion.site", "Notion Public Sites", "global", "en")
    ]
    for domain, title, region, language in blog_platforms:
        push(
            entries,
            seen,
            warning_entry(
                domain,
                title,
                "블로그·개인 페이지 호스팅 성격이 강해 개별 페이지별 안전성을 별도 검증할 필요가 있다.",
                "blog-platform",
                region,
                language,
                ["blog", "ugc", "hosting"],
                [title.lower()],
                ["user hosted pages", "individual page variability"],
                harmful=False,
            ),
        )


def build_adult_entries(entries: list[dict], seen: set[str]) -> None:
    brands = [
        ("xhamster", "XHamster"), ("tube8", "Tube8"), ("beeg", "Beeg"), ("sunporno", "SunPorno"),
        ("thumbzilla", "Thumbzilla"), ("hqporner", "HQPorner"), ("eporner", "Eporner"),
        ("cam4", "Cam4"), ("stripchat", "Stripchat"), ("bongacams", "BongaCams"),
        ("livejasmin", "LiveJasmin"), ("myfreecams", "MyFreeCams"), ("manyvids", "ManyVids"),
        ("fapello", "Fapello"), ("adulttime", "AdultTime"), ("brazzers", "Brazzers"),
        ("realitykings", "Reality Kings"), ("bangbros", "BangBros"), ("fakehub", "FakeHub"),
        ("evilangel", "Evil Angel"), ("nutaku18", "Nutaku 18"), ("18comic", "18Comic")
    ]
    tlds = ["com", "net", "org", "tv", "live"]
    added = 0
    for idx, (slug, title) in enumerate(brands):
        tld = tlds[idx % len(tlds)]
        domain = f"{slug}.{tld}"
        push(
            entries,
            seen,
            block_entry(
                domain,
                title,
                "성인용 노골적 영상·이미지·라이브 콘텐츠 접근을 제공하거나 유도하는 사이트로 분류된다.",
                "adult",
                "global",
                "en",
                ["adult", "porn", "explicit"],
                [slug, title.lower()],
                ["explicit adult content", "adult video or livecam"],
                ["adult-content"],
                security_threat=False,
                harmful_content=True,
            ),
        )
        added += 1
    kr_examples = [
        ("adult-webtoon-plus.kr", "성인 웹툰 플러스"),
        ("19cam-live.kr", "19캠 라이브"),
        ("secret-room-adult.kr", "시크릿 성인방"),
        ("private19-video.kr", "프라이빗19 비디오"),
        ("night-party-cam.kr", "나이트 파티 캠")
    ]
    for domain, title in kr_examples:
        push(
            entries,
            seen,
            block_entry(
                domain,
                title,
                "국내형 성인 스트리밍 또는 노골적 성인 콘텐츠 접근을 유도하는 예시 도메인.",
                "adult",
                "kr",
                "ko",
                ["adult", "explicit", "streaming"],
                [title.lower()],
                ["adult streaming", "explicit content"],
                ["adult-content"],
                security_threat=False,
                harmful_content=True,
            ),
        )


def build_gambling_entries(entries: list[dict], seen: set[str]) -> None:
    brands = [
        ("888sport", "888sport"), ("williamhill", "William Hill"), ("unibet", "Unibet"),
        ("pinnacle", "Pinnacle"), ("ladbrokes", "Ladbrokes"), ("betfair", "Betfair"),
        ("marathonbet", "Marathonbet"), ("betsson", "Betsson"), ("10bet", "10Bet"),
        ("22bet", "22Bet"), ("1win", "1win"), ("vbet", "Vbet"), ("mostbet", "Mostbet"),
        ("melbet", "Melbet"), ("bcgame", "BC.Game"), ("betano", "Betano"),
        ("w88", "W88"), ("dafabet", "Dafabet"), ("fun88", "Fun88"), ("188bet", "188BET")
    ]
    tlds = ["com", "net", "bet", "casino"]
    for idx, (slug, title) in enumerate(brands):
        tld = tlds[idx % len(tlds)]
        domain = f"{slug}.{tld}"
        push(
            entries,
            seen,
            block_entry(
                domain,
                title,
                "온라인 스포츠 베팅 또는 카지노형 서비스를 제공하는 해외 도박 사이트로 분류된다.",
                "gambling",
                "global",
                "multi",
                ["gambling", "betting", "sportsbook"],
                [slug, title.lower()],
                ["sports betting", "casino"],
                ["gambling"],
                security_threat=False,
                harmful_content=True,
            ),
        )
    kr_examples = [
        ("vip-toto-365.kr", "VIP 토토365"),
        ("safe-bet-korea.kr", "세이프벳 코리아"),
        ("score-premium-toto.kr", "스코어 프리미엄 토토"),
        ("power-casino-choice.kr", "파워 카지노 초이스"),
        ("match-betting-king.kr", "매치 베팅킹")
    ]
    for domain, title in kr_examples:
        push(
            entries,
            seen,
            block_entry(
                domain,
                title,
                "국내형 사설 스포츠 토토 또는 카지노 참여를 유도하는 예시 도메인.",
                "gambling",
                "kr",
                "ko",
                ["gambling", "toto", "betting"],
                [title.lower()],
                ["sports betting", "illegal toto"],
                ["gambling"],
                security_threat=False,
                harmful_content=True,
            ),
        )


def build_piracy_entries(entries: list[dict], seen: set[str]) -> None:
    domains = [
        ("fitgirl-repacks.site", "FitGirl Repacks"),
        ("skidrowcodex.net", "Skidrow Codex"),
        ("igg-games.com", "IGG Games"),
        ("oceanofgames.com", "Ocean of Games"),
        ("steamunlocked.net", "SteamUnlocked"),
        ("repack-games.com", "Repack Games"),
        ("softarchive.is", "SoftArchive"),
        ("scnsrc.me", "SceneSource"),
        ("ddlvalley.cool", "DDLValley"),
        ("pirateproxy.live", "Pirate Proxy Live"),
        ("magnetdl.com", "MagnetDL"),
        ("torlock.com", "Torlock"),
        ("zooqle.com", "Zooqle")
    ]
    for domain, title in domains:
        push(
            entries,
            seen,
            warning_entry(
                domain,
                title,
                "토렌트 인덱싱, 불법 복제 소프트웨어 또는 비공식 배포 링크 접근을 유도하는 사이트로 분류된다.",
                "piracy",
                "global",
                "en",
                ["torrent", "piracy", "download"],
                [title.lower()],
                ["torrent indexing", "copyright infringement risk"],
                ["piracy"],
                harmful=True,
            ),
        )


def build_phishing_entries(entries: list[dict], seen: set[str]) -> None:
    brands = [
        ("naver", "네이버", "kr", "ko"),
        ("kakao", "카카오", "kr", "ko"),
        ("toss", "토스", "kr", "ko"),
        ("coupang", "쿠팡", "kr", "ko"),
        ("upbit", "업비트", "kr", "ko"),
        ("shinhan", "신한은행", "kr", "ko"),
        ("kb", "국민은행", "kr", "ko"),
        ("woori", "우리은행", "kr", "ko"),
        ("nh", "농협", "kr", "ko"),
        ("paypal", "PayPal", "global", "en"),
        ("google", "Google", "global", "en"),
        ("appleid", "Apple ID", "global", "en"),
        ("microsoft365", "Microsoft 365", "global", "en"),
        ("amazon", "Amazon", "global", "en"),
        ("binance", "Binance", "global", "en"),
        ("telegram", "Telegram", "global", "en"),
        ("steam", "Steam", "global", "en"),
        ("discord", "Discord", "global", "en")
    ]
    suffixes = [
        ("secure-login", "보안 로그인 또는 계정 인증을 사칭하며 자격 증명 탈취를 시도할 수 있는 피싱형 예시 도메인."),
        ("account-verify", "계정 검증 또는 본인확인을 사칭하며 로그인·인증번호 입력을 유도하는 피싱형 예시 도메인."),
        ("billing-check", "결제 문제 해결 또는 청구 확인을 빌미로 개인정보와 결제정보 입력을 유도하는 피싱형 예시 도메인."),
        ("support-center", "고객지원을 사칭하며 계정 정보 확인을 요구하는 피싱형 예시 도메인."),
        ("password-reset", "비밀번호 재설정을 빌미로 로그인 페이지로 위장한 피싱형 예시 도메인."),
        ("gift-reward", "쿠폰·사은품·무료 보상 지급을 미끼로 계정 인증을 요구하는 피싱형 예시 도메인.")
    ]
    tlds_by_region = {"kr": ["kr", "co.kr"], "global": ["com", "net", "org"]}
    for brand_slug, brand_title, region, language in brands:
        for suffix, summary in suffixes:
            for tld in tlds_by_region["kr" if region == "kr" else "global"]:
                domain = f"{brand_slug}-{suffix}.{tld}"
                push(
                    entries,
                    seen,
                    block_entry(
                        domain,
                        f"{brand_title} {suffix.replace('-', ' ').title()}",
                        summary,
                        "phishing",
                        region,
                        language,
                        ["phishing", "account", "credential"],
                        [f"{brand_slug} phishing", f"{brand_title.lower()} phishing"],
                        ["credential harvesting", "brand impersonation"],
                        ["phishing", "credential-theft"],
                        security_threat=True,
                        harmful_content=False,
                    ),
                )


def build_malware_entries(entries: list[dict], seen: set[str]) -> None:
    stems = [
        ("windows-crack", "Windows Crack Hub"),
        ("office-activator", "Office Activator"),
        ("adobe-keygen", "Adobe Keygen"),
        ("driver-booster-free", "Driver Booster Free"),
        ("game-mod-launcher", "Game Mod Launcher"),
        ("apk-unlocker", "APK Unlocker"),
        ("serial-loader", "Serial Loader"),
        ("patch-now", "Patch Now"),
        ("free-soft-premium", "Free Soft Premium"),
        ("pc-speed-max", "PC Speed Max"),
        ("codec-pack-fast", "Codec Pack Fast"),
        ("media-plugin-update", "Media Plugin Update"),
        ("office-patcher", "Office Patcher"),
        ("win-defender-fix", "Win Defender Fix"),
        ("steam-dll-fix", "Steam DLL Fix")
    ]
    endings = ["com", "net", "org", "download", "tools"]
    summaries = [
        "불법 크랙·패치·키젠 배포 또는 악성 실행파일 다운로드를 유도할 가능성이 높은 예시 도메인.",
        "가짜 업데이트, 활성화 도구, 비공식 실행파일을 통해 악성코드 배포 가능성이 있는 예시 도메인.",
        "비공식 소프트웨어 설치 파일과 함께 트로이목마·PUA 배포 위험이 있는 예시 도메인."
    ]
    for idx, (stem, title) in enumerate(stems):
        for ending in endings:
            domain = f"{stem}.{ending}"
            push(
                entries,
                seen,
                block_entry(
                    domain,
                    title,
                    summaries[idx % len(summaries)],
                    "malware",
                    "global",
                    "en",
                    ["malware", "crack", "installer"],
                    [stem.replace("-", " ")],
                    ["malware distribution", "unofficial installer"],
                    ["malware", "trojan-distribution"],
                    security_threat=True,
                    harmful_content=False,
                ),
            )
    kr_malware = [
        ("무료오피스패치", "office-free-patch.kr"),
        ("정품인증도구", "license-activator-kr.co.kr"),
        ("한글키젠", "hangul-keygen.kr"),
        ("드라이버업데이트", "driver-update-korea.kr"),
        ("모드apk다운", "mod-apk-download.kr")
    ]
    for alias, domain in kr_malware:
        push(
            entries,
            seen,
            block_entry(
                domain,
                alias,
                "국내형 비공식 설치 파일, 키젠, 패치 프로그램을 미끼로 악성코드 배포 가능성이 있는 예시 도메인.",
                "malware",
                "kr",
                "ko",
                ["malware", "keygen", "installer"],
                [alias],
                ["malware distribution", "unofficial installer"],
                ["malware", "trojan-distribution"],
                security_threat=True,
                harmful_content=False,
            ),
        )


def build_bulk_allow_entries(entries: list[dict], seen: set[str]) -> None:
    trusted_brands = [
        ("google", "Google", "global", "en", "search"),
        ("microsoft", "Microsoft", "global", "en", "corporate"),
        ("apple", "Apple", "global", "en", "corporate"),
        ("amazon", "Amazon", "global", "en", "commerce"),
        ("github", "GitHub", "global", "en", "developer"),
        ("openai", "OpenAI", "global", "en", "ai-service"),
        ("cloudflare", "Cloudflare", "global", "en", "security-service"),
        ("naver", "네이버", "kr", "ko", "portal"),
        ("daum", "다음", "kr", "ko", "portal"),
        ("kakao", "카카오", "kr", "ko", "portal"),
        ("coupang", "쿠팡", "kr", "ko", "commerce"),
        ("toss", "토스", "kr", "ko", "finance"),
        ("upbit", "업비트", "kr", "ko", "finance"),
        ("youtube", "YouTube", "global", "en", "video-platform"),
        ("wikipedia", "Wikipedia", "global", "en", "reference"),
        ("mozilla", "Mozilla", "global", "en", "developer"),
        ("ubuntu", "Ubuntu", "global", "en", "developer"),
        ("pypi", "PyPI", "global", "en", "developer"),
        ("npmjs", "npm", "global", "en", "developer"),
        ("vercel", "Vercel", "global", "en", "developer"),
        ("netlify", "Netlify", "global", "en", "developer"),
        ("slack", "Slack", "global", "en", "corporate"),
        ("notion", "Notion", "global", "en", "corporate"),
        ("figma", "Figma", "global", "en", "corporate"),
        ("dropbox", "Dropbox", "global", "en", "corporate"),
        ("zoom", "Zoom", "global", "en", "corporate"),
        ("stripe", "Stripe", "global", "en", "finance"),
        ("paypal", "PayPal", "global", "en", "finance"),
        ("line", "LINE", "asia", "multi", "corporate"),
        ("samsung", "Samsung", "kr", "ko", "corporate"),
        ("lg", "LG", "kr", "ko", "corporate"),
        ("hyundai", "Hyundai", "kr", "ko", "corporate"),
        ("kt", "KT", "kr", "ko", "corporate"),
        ("sktelecom", "SK Telecom", "kr", "ko", "corporate"),
        ("lguplus", "LG U+", "kr", "ko", "corporate"),
        ("snu", "서울대학교", "kr", "ko", "education"),
        ("yonsei", "연세대학교", "kr", "ko", "education"),
        ("korea", "고려대학교", "kr", "ko", "education"),
        ("harvard", "Harvard", "global", "en", "education"),
        ("stanford", "Stanford", "global", "en", "education"),
    ]
    safe_prefixes = [
        ("docs", "공식 문서 및 가이드"),
        ("developers", "개발자 및 기술 문서"),
        ("support", "공식 고객지원"),
        ("help", "공식 도움말 센터"),
        ("status", "공식 서비스 상태 페이지"),
        ("blog", "공식 블로그 및 소식"),
        ("careers", "공식 채용 정보"),
    ]
    tld_map = {
        "global": "com",
        "kr": "com",
        "asia": "com",
    }

    for slug, title, region, language, category in trusted_brands:
        base_domain = f"{slug}.{tld_map.get(region, 'com')}"
        push(
            entries,
            seen,
            allow_entry(
                base_domain,
                title,
                "공식 메인 서비스 및 대표 도메인으로 분류되는 고신뢰 사이트.",
                category,
                region,
                language,
                [category, "trusted", "official"],
                [slug, title.lower()],
                ["official domain", "high trust service"],
            ),
        )
        for prefix, summary_hint in safe_prefixes:
            domain = f"{prefix}.{base_domain}"
            push(
                entries,
                seen,
                allow_entry(
                    domain,
                    f"{title} {prefix.title()}",
                    f"{summary_hint} 성격의 공식 하위 도메인으로 분류되는 고신뢰 서비스.",
                    category,
                    region,
                    language,
                    [category, "trusted", "official", prefix],
                    [f"{title.lower()} {prefix}", f"{slug} {prefix}"],
                    ["official subdomain", "trusted service surface"],
                ),
            )


def build_bulk_warning_entries(entries: list[dict], seen: set[str]) -> None:
    platforms = [
        ("blog-platform", "개별 작성자별 편차가 큰 블로그·게시글 호스팅 서비스."),
        ("community", "게시판과 사용자 층에 따라 유해성 편차가 큰 커뮤니티 서비스."),
        ("ugc-platform", "사용자 생성 콘텐츠 비중이 높아 개별 페이지 단위 검증이 필요한 플랫폼."),
    ]
    hosts = [
        ("blogspot.com", "Blogger Hosted"),
        ("wordpress.com", "WordPress Hosted"),
        ("medium.com", "Medium Hosted"),
        ("notion.site", "Notion Public Sites"),
        ("substack.com", "Substack Hosted"),
        ("tistory.com", "Tistory Hosted"),
        ("velog.io", "Velog Hosted"),
        ("tumblr.com", "Tumblr Hosted"),
    ]
    prefixes = ["user", "profile", "community", "topic", "creator", "channel", "writer", "club", "team", "open"]
    for host, host_title in hosts:
        for idx, prefix in enumerate(prefixes, start=1):
            category, summary = platforms[idx % len(platforms)]
            domain = f"{prefix}{idx}.{host}"
            push(
                entries,
                seen,
                warning_entry(
                    domain,
                    f"{host_title} {prefix.title()}",
                    summary,
                    category,
                    "global" if host.endswith(".com") else "kr",
                    "multi" if host.endswith(".com") else "ko",
                    ["ugc", "hosting", category],
                    [f"{host_title.lower()} {prefix}"],
                    ["user hosted page", "content variability"],
                    ["harmful-content"] if category != "blog-platform" else [],
                    harmful=category != "blog-platform",
                ),
            )


def build_bulk_adult_entries(entries: list[dict], seen: set[str]) -> None:
    slugs = [
        "xxxstream", "hotadult", "nightcam", "privateroom", "redadult", "secretvideo", "matureplay",
        "dark18", "vipadult", "adultzone", "camnight", "freenude", "deepadult", "pinkcam", "adultcloud"
    ]
    suffixes = ["com", "net", "tv", "live", "video", "cam"]
    for slug in slugs:
        for suffix in suffixes:
            domain = f"{slug}.{suffix}"
            push(
                entries,
                seen,
                block_entry(
                    domain,
                    slug.replace("-", " ").title(),
                    "성인용 노골적 영상·이미지 또는 라이브 콘텐츠 접근을 제공하는 성인 사이트 패턴.",
                    "adult",
                    "global",
                    "en",
                    ["adult", "porn", "explicit"],
                    [slug],
                    ["explicit adult content", "adult streaming"],
                    ["adult-content"],
                    security_threat=False,
                    harmful_content=True,
                ),
            )


def build_bulk_gambling_entries(entries: list[dict], seen: set[str]) -> None:
    slugs = [
        "luckybet", "goldcasino", "vipbet", "superodds", "maxwager", "sportwin", "ultrabet",
        "megacasino", "royalbet", "quickbet", "surewin", "oddsmaster", "casinohub", "betprime", "jackpotnow"
    ]
    suffixes = ["com", "bet", "casino", "vip", "win"]
    for slug in slugs:
        for suffix in suffixes:
            domain = f"{slug}.{suffix}"
            push(
                entries,
                seen,
                block_entry(
                    domain,
                    slug.replace("-", " ").title(),
                    "온라인 베팅, 스포츠북, 카지노 참여를 유도하는 도박 사이트 패턴.",
                    "gambling",
                    "global",
                    "multi",
                    ["gambling", "betting", "casino"],
                    [slug],
                    ["sports betting", "casino wagering"],
                    ["gambling"],
                    security_threat=False,
                    harmful_content=True,
                ),
            )


def build_bulk_piracy_entries(entries: list[dict], seen: set[str]) -> None:
    slugs = [
        "torrentmirror", "magnethub", "freewatch", "streamrip", "pirateportal",
        "downloadscene", "torrentindex", "movieripzone", "gamerepackhub", "softcrackworld"
    ]
    suffixes = ["com", "net", "to", "live", "site"]
    for slug in slugs:
        for suffix in suffixes:
            domain = f"{slug}.{suffix}"
            push(
                entries,
                seen,
                warning_entry(
                    domain,
                    slug.replace("-", " ").title(),
                    "토렌트, 비공식 스트리밍, 불법 배포 링크 접근을 유도하는 저작권 침해 우려 사이트 패턴.",
                    "piracy",
                    "global",
                    "en",
                    ["torrent", "piracy", "download"],
                    [slug],
                    ["torrent indexing", "piracy risk"],
                    ["piracy"],
                    harmful=True,
                ),
            )


def main() -> int:
    seen = load_existing_domains()
    entries: list[dict] = []

    build_allow_entries(entries, seen)
    build_bulk_allow_entries(entries, seen)
    build_warning_entries(entries, seen)
    build_bulk_warning_entries(entries, seen)
    build_adult_entries(entries, seen)
    build_bulk_adult_entries(entries, seen)
    build_gambling_entries(entries, seen)
    build_bulk_gambling_entries(entries, seen)
    build_piracy_entries(entries, seen)
    build_bulk_piracy_entries(entries, seen)
    build_phishing_entries(entries, seen)
    build_malware_entries(entries, seen)

    OUTPUT_PATH.write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(
        json.dumps(
            {
                "ok": True,
                "output": str(OUTPUT_PATH),
                "generated": len(entries),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
