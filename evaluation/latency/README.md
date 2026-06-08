# Chungmaru Latency CSV

이 폴더는 청마루 처리 지연을 같은 CSV schema로 누적하기 위한 평가 위치입니다.
원본 CSV는 `evaluation/latency/results/chungmaru-latency-samples.csv`에 쌓습니다.

## 측정 단위

CSV 한 줄은 하나의 처리 run입니다.

| 구간 | CSV 컬럼 | 의미 |
| --- | --- | --- |
| 후보 수집/파서 | `candidate_collect_ms`, `parser_ms` | DOM 후보 수집과 분석 입력 구성 시간 |
| 백엔드 전 준비 | `pre_backend_ms` | pipeline 시작부터 backend 요청 직전까지 |
| 백엔드 왕복 | `backend_roundtrip_ms`, `backend_reported_ms` | extension/service worker가 본 backend 왕복 시간 |
| 백엔드 내부 | `backend_internal_avg_ms`, `backend_model_avg_ms` | `/analyze_batch` 응답의 per-text pipeline/model timing 평균 |
| 응답 후 반영 | `decision_build_ms`, `mask_apply_ms`, `post_backend_to_mask_ms` | backend 응답 이후 decision 생성과 DOM 마스킹 적용 |
| 사용자 체감 | `first_mask_ms`, `total_to_mask_ms` | 첫 마스킹까지, 또는 최종 마스킹 적용까지 |

`backend-direct` 측정은 backend만 직접 호출하므로 parser/masking 단계는 비워 둡니다.
실제 Chrome 화면에서 나온 `lastStats`를 import하면 모든 단계가 같은 CSV로 합쳐집니다.
Android benchmark에서 나온 `raw_runs.csv`도 import하면 접근성 후보 수집, 좌표 검증, overlay gate까지 같은 schema로 비교합니다.

## CSV 생성

빈 CSV header만 만듭니다.

```bash
python3 scripts/chungmaru_latency_csv.py --overwrite init
```

로컬 backend가 실행 중일 때 direct `/analyze_batch`를 1000회 누적합니다.

```bash
python3 scripts/chungmaru_latency_csv.py \
  --output evaluation/latency/results/chungmaru-latency-samples.csv \
  backend \
  --backend http://127.0.0.1:8000 \
  --samples 1000 \
  --batch-size 16 \
  --sensitivity 60
```

기본적으로 측정 전 warmup 1회를 실행하고 CSV에는 기록하지 않습니다.
더 큰 부하를 보려면 `--samples 10000` 또는 `--batch-size 24`, `--batch-size 32`를 사용합니다.
여러 번 실행하면 같은 CSV에 append됩니다.

## Scenario x batch matrix

발표/보고서용 평균은 단일 batch만 보지 말고 scenario와 batch size를 나눠서 봅니다.
아래 명령은 기본 9개 scenario와 6개 batch size 조합에 총 1000개 request row를 균등 배분합니다.
한 row가 하나의 backend request이고, 실제 분석 텍스트 수는 `batch_size` 합계만큼 더 많습니다.

```bash
python3 scripts/chungmaru_latency_csv.py \
  --output evaluation/latency/results/chungmaru-latency-samples.csv \
  --overwrite \
  matrix \
  --backend http://127.0.0.1:8000 \
  --target-samples 1000 \
  --sensitivity 60
```

더 크게 보려면 다음처럼 실행합니다.

```bash
python3 scripts/chungmaru_latency_csv.py \
  --output evaluation/latency/results/chungmaru-latency-samples.csv \
  --overwrite \
  matrix \
  --backend http://127.0.0.1:8000 \
  --target-samples 10000 \
  --sensitivity 60
```

기본 scenario:

| Scenario | 목적 |
| --- | --- |
| `clean` | 정상 댓글 baseline |
| `clean-topic` | 차별금지법, 성소수자 등 topic-bias clean 문장 |
| `profanity` | 명시적 욕설 |
| `toxicity` | 공격성/조롱성 문장 |
| `hate` | 혐오/차별 판단 후보 |
| `bypass` | 초성, 로마자, 띄어쓰기 우회 |
| `parser-noise` | 좋아요, 답글, 시간 등 UI 오염 포함 |
| `search-result` | Google 검색 결과 제목/스니펫 형태 |
| `mixed` | 위 항목을 섞은 실제 화면 근사 |

matrix 실행 후 아래 두 파일이 같이 생성됩니다.

| 파일 | 용도 |
| --- | --- |
| `evaluation/latency/results/chungmaru-latency-summary.csv` | `scenario + batch_size + metric`별 `avg`, `median`, `p95`, `min`, `max` |
| `evaluation/latency/results/chungmaru-latency-report.md` | 발표 검토용 `avg / p95 ms` pivot table |

## Chrome extension 측정 import

extension runtime은 `chrome.storage.local.lastStats`에 `phaseTimings`를 남깁니다.
아래처럼 `lastStats`를 JSON으로 저장한 뒤 import합니다.

```json
{
  "url": "https://www.google.com/search?q=...",
  "lastStats": {
    "runReason": "visibility",
    "phaseTimings": {
      "candidateCollectMs": 4,
      "parserMs": 3,
      "preBackendMs": 12,
      "backendRoundTripMs": 41,
      "postBackendToMaskMs": 5,
      "totalToMaskMs": 58
    }
  }
}
```

```bash
python3 scripts/chungmaru_latency_csv.py \
  --output evaluation/latency/results/chungmaru-latency-samples.csv \
  extension-export \
  --input /path/to/last-stats.json \
  --scenario google-search
```

자동 smoke harness를 쓸 수 있는 환경이면 아래처럼 실제 unpacked extension을 Chrome에 로드해 `lastStats` JSONL을 만듭니다.
일부 macOS/Chrome stable 조합은 command-line unpacked extension 로드를 제한할 수 있으므로, 이 경우 수동 Chrome profile 또는 Chrome for Testing에서 export합니다.

```bash
python3 scripts/chungmaru_chrome_latency_smoke.py \
  --output evaluation/latency/results/chrome-last-stats.jsonl \
  --backend http://127.0.0.1:8000 \
  --scenarios mixed,search-result,profanity,bypass \
  --batch-sizes 4,8,16 \
  --samples-per-combo 3 \
  --clean-profile
```

Chrome Google 데모/빠른 QA의 기본 검색 세트는 `moderation-core`입니다.
발표 데모 검색 순서는 아래 3개로 고정합니다.

| 순서 | 범주 | 검색어 | 기대 결과 |
| ---: | --- | --- | --- |
| 1 | 욕설 | `병신아 꺼져` | span mask |
| 2 | 혐오 표현 | `한남충은 답이 없다` | span mask |
| 3 | 공격적 발화 | `너 한번만 더 그러면 죽여버린다` | span mask |

마사지/성인유흥, 차별금지법/성소수자 clean-topic 쿼리는 regression set에만 남기고 발표 데모 검색 루프에는 넣지 않습니다.

속도 evidence는 영상 길이를 늘리지 않고 `moderation-speed` 세트로 따로 측정합니다.
`moderation-speed`는 욕설/혐오/공격적 발화 각 3개씩 총 9개 Google 검색 장면을 빠른 QA로 돌려 평균, 최대, p95를 확인하는 용도입니다.
즉 발표 영상은 대표 3개, 속도 표는 9개 이상 누적 측정으로 분리합니다.

## 데스크톱 장기 측정 runner

장기 측정은 Mac에서 30분마다 SSH로 시작하는 방식이 아니라, 데스크톱 쪽 runner가 스스로 돌고 공유 폴더의 설정/제어 파일을 읽는 방식으로 운영합니다.
Mac은 공유 폴더의 `runner.env`, `control/command.txt`, 결과 CSV/report를 확인하거나 수정하는 관찰자 역할만 합니다.

전제:

- 데스크톱에 Chungmaru repo가 준비되어 있다.
- backend는 데스크톱 `127.0.0.1:8000`에서 실행 중이다.
- Pi SSD 또는 NAS 공유 폴더가 데스크톱에 mount되어 있다.
- 결과는 공유 폴더에 쓰되, repo와 모델은 가능하면 데스크톱 로컬 디스크에서 실행한다.

초기화:

```bash
cd "/Users/giminu0930/Documents/000 Project/Chungmaru"
SHARE_ROOT="/Volumes/pi-ssd/chungmaru-e2e-results" \
  scripts/chungmaru_desktop_e2e.sh share-init
```

공유 폴더에는 아래 구조가 생깁니다.

| 경로 | 의미 |
| --- | --- |
| `<SHARE_ROOT>/runner.env` | 반복 실행 설정. runner가 매 cycle 전에 다시 읽음 |
| `<SHARE_ROOT>/control/command.txt` | `run`, `pause`, `stop`, `once`, `chrome-only`, `android-only` 제어 |
| `<SHARE_ROOT>/state/runner-state.txt` | 현재 상태 |
| `<SHARE_ROOT>/state/heartbeat.txt` | runner 생존 시각 |
| `<SHARE_ROOT>/desktop-loop-logs/*.log` | cycle별 로그 |

데스크톱에서 runner를 한 번 시작합니다.

```bash
cd "/Users/giminu0930/Documents/000 Project/Chungmaru"
SHARE_ROOT="/Volumes/pi-ssd/chungmaru-e2e-results" \
  scripts/chungmaru_desktop_e2e.sh share-loop
```

터미널을 닫아도 계속 돌려야 하면 데스크톱에서만 background로 띄웁니다.

```bash
cd "/Users/giminu0930/Documents/000 Project/Chungmaru"
SHARE_ROOT="/Volumes/pi-ssd/chungmaru-e2e-results"
nohup scripts/chungmaru_desktop_e2e.sh share-loop \
  > "${SHARE_ROOT}/desktop-loop-logs/runner.out" 2>&1 &
```

공유 폴더에서 제어합니다.

```bash
echo pause > "/Volumes/pi-ssd/chungmaru-e2e-results/control/command.txt"
echo run > "/Volumes/pi-ssd/chungmaru-e2e-results/control/command.txt"
echo once > "/Volumes/pi-ssd/chungmaru-e2e-results/control/command.txt"
echo stop > "/Volumes/pi-ssd/chungmaru-e2e-results/control/command.txt"
```

현재 상태 확인:

```bash
SHARE_ROOT="/Volumes/pi-ssd/chungmaru-e2e-results" \
  scripts/chungmaru_desktop_e2e.sh share-status
```

기본 Chrome long-run은 `TARGET_DETECTIONS=10000`이고, 결과는
`<SHARE_ROOT>/<RUN_ID>/` 아래에 생성됩니다.

| 파일 | 용도 |
| --- | --- |
| `chrome-last-stats.jsonl` | extension runtime 원본 `lastStats` |
| `chrome-e2e-samples.csv` | CSV schema로 import된 Chrome E2E row |
| `chrome-e2e-summary.csv` | metric별 평균/중앙값/p95/p99 |
| `chrome-e2e-report.md` | 발표/보고서 검토용 요약 |

Android는 `RUN_ANDROID=auto`가 기본값입니다.
`adb devices`에 기기나 emulator가 없으면 해당 cycle에 skip report만 남기고 Chrome 측정을 계속합니다.

share-loop 결과 구조:

| 경로 | 의미 |
| --- | --- |
| `<SHARE_ROOT>/desktop-loop-logs/*.log` | cycle별 실행 로그 |
| `<SHARE_ROOT>/<cycle>-chrome/` | Chrome E2E JSONL/CSV/report |
| `<SHARE_ROOT>/<cycle>-android/` | Android benchmark CSV/report 또는 skip report |
| `<SHARE_ROOT>/<cycle>/cycle-status.txt` | 한 cycle의 Chrome/Android 상태 |

SSH 기반 `remote-*` 명령은 초기 접속/수동 실행용 보조 경로입니다.
장기 측정 운영 기준은 `share-loop`입니다.

## Android pipeline 측정 import

Android는 `scripts/android-pipeline-benchmark.sh`가 만든 `raw_runs.csv`를 같은 CSV에 붙입니다.
이 값은 backend-only가 아니라 접근성 수집, backend 요청, 좌표 검증, 화면 반영 gate를 포함합니다.

```bash
env RUNS_PER_MODE=2 RECORD_SECONDS=8 \
  MODES="s1_collect_only s12_collect_backend s1234_collect_backend_ocr_coord s12345_full" \
  REPORT_DIR=evaluation/latency/results/android-pipeline-benchmark-smoke \
  BATCH_ROOT=/private/tmp/chungmaru-android-pipeline-benchmark-smoke \
  scripts/android-pipeline-benchmark.sh run

python3 scripts/chungmaru_latency_csv.py \
  --output evaluation/latency/results/chungmaru-latency-samples.csv \
  android-pipeline-import \
  --input evaluation/latency/results/android-pipeline-benchmark-smoke/raw_runs.csv \
  --backend http://10.0.2.2:8000
```

데스크톱에 Android emulator 또는 adb 연결 기기가 있으면 Android runner도 같은 방식으로 실행합니다.

```bash
ssh HOME@desktop-gmqfqtr
cd "/Users/giminu0930/Documents/000 Project/Chungmaru"
RUNS_PER_MODE=3 RECORD_SECONDS=8 scripts/chungmaru_desktop_e2e.sh android
```

Android 결과는 `raw_runs.csv`와 단계별 CSV를 만든 뒤,
`android-e2e-samples.csv`, `android-e2e-summary.csv`, `android-e2e-report.md`로 다시 import됩니다.
emulator는 기본적으로 `ANDROID_ANALYSIS_INPUT=10.0.2.2:8000`을 사용하고, 실제 기기는 데스크톱에서 접근 가능한 backend 주소로 바꿔야 합니다.

Android Studio emulator 준비 기준:

1. Android Studio에서 Device Manager를 열고 Pixel 계열 virtual device를 생성합니다.
2. system image는 API 35/36 x86_64 또는 arm64 중 데스크톱에 맞는 이미지를 설치합니다.
3. emulator를 실행한 뒤 `adb devices`에서 `device` 상태로 보여야 합니다.
4. backend가 데스크톱 `127.0.0.1:8000`에서 실행 중이면 emulator 안에서는 `10.0.2.2:8000`으로 접근합니다.
5. 실제 USB/원격 기기를 쓰면 `ANDROID_ANALYSIS_INPUT=<데스크톱 LAN 또는 Tailscale IP>:8000`으로 바꿉니다.

Android row는 `source=android-pipeline-benchmark`로 들어갑니다.
`overlay_rendered=0`인 smoke는 최종 마스킹 품질 근거가 아니라 단계별 지연 구조 확인용으로만 해석합니다.

## 튀는 ms 해석

평균만으로 “사용자가 인식하기 전 마스킹”을 말하지 않습니다.
발표/보고서에서는 `median`, `p95`, `max`를 같이 보고, max가 튀면 아래 순서로 원인을 분리합니다.

| 튀는 위치 | 주로 의심할 원인 |
| --- | --- |
| `candidate_collect_ms`, Android `collect_ms` | DOM/접근성 노드 수 증가, 화면 상태 차이, 스크롤 직후 노드 재수집 |
| `parser_ms`, `pre_backend_ms` | UI noise 제거, dedupe, foreground 후보 선정 증가 |
| `backend_roundtrip_ms` | backend cold path, 모델/파이프라인 cache miss, 요청 큐 대기, connection reuse 실패 |
| `backend_internal_avg_ms`, `backend_model_avg_ms` | 실제 모델 추론 또는 backend pipeline 비용 증가 |
| `post_backend_to_mask_ms`, Android `coord_ms`/`display_ms` | 응답 이후 span/bounds 검증, stale guard, overlay render gate, layout 변경 |
| `total_to_mask_ms`, `first_mask_ms` | 위 단계 중 가장 느린 구간의 합성 결과 |

일반적으로 사용자가 “즉시 가려졌다”고 느끼려면 첫 반영은 대략 100ms 안쪽이 유리하고, 100~200ms는 짧은 지연으로 인식될 수 있으며, 200ms 이상은 상황에 따라 눈에 띄기 쉽습니다.
따라서 청마루 기준은 평균보다 `first_mask_ms`와 `total_to_mask_ms`의 p95가 100~200ms 안에 들어오는지로 봅니다.

## 요약

```bash
python3 scripts/chungmaru_latency_csv.py summary \
  --input evaluation/latency/results/chungmaru-latency-samples.csv \
  --metric backend_roundtrip_ms

python3 scripts/chungmaru_latency_csv.py summary \
  --input evaluation/latency/results/chungmaru-latency-samples.csv \
  --metric total_to_mask_ms
```

summary CSV를 다시 만들려면:

```bash
python3 scripts/chungmaru_latency_csv.py aggregate \
  --input evaluation/latency/results/chungmaru-latency-samples.csv \
  --output evaluation/latency/results/chungmaru-latency-summary.csv
```

Markdown report를 다시 만들려면:

```bash
python3 scripts/chungmaru_latency_csv.py report-md \
  --input evaluation/latency/results/chungmaru-latency-samples.csv \
  --output evaluation/latency/results/chungmaru-latency-report.md
```
