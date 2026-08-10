# Android YouTube 유해 댓글 보호 기술 및 시연 가이드

## 1. 문서 목적과 현재 범위

이 문서는 발표자 또는 팀원이 Codex 없이도 ChungMaru Android의 YouTube 댓글 보호 기능을 설명하고 시연할 수 있도록 다음 내용을 한곳에 정리한다.

- Android 댓글 수집과 안전 댓글 표시 구조
- OCR 사용 범위
- Android와 분석 서버 사이의 요청 및 응답 형식
- 실제 측정한 처리 시간
- Windows 노트북과 USB 연결 Android 기기를 이용한 서버 실행 및 APK 시연 절차
- 장애 확인 순서와 현재 기술적 한계

현재 발표 권장 범위는 **YouTube 댓글 보호 기능**이다. Instagram 댓글 미러는 아직 기기별 안정화가 충분하지 않으므로 이번 시연 범위에서 제외한다.

## 2. 발표용 요약

> ChungMaru Android는 YouTube 앱을 수정하지 않고 Android 접근성 서비스로 댓글 구조를 수집한다. 댓글 본문이 접근성 트리에 노출되지 않는 경우에만 ML Kit OCR을 보조적으로 사용한다. 수집한 댓글은 이미지 파일이 아니라 본문, 작성자 식별자, 화면 좌표가 담긴 JSON 배치로 FastAPI 서버에 전달한다. 서버의 v3 문장 분류 모델과 span CRF 모델이 유해 여부와 실제 유해 표현 구간을 분석하며, 앱은 유해 댓글을 제외한 안전 댓글만 YouTube 형태의 별도 댓글창에 표시한다.

## 3. 전체 구조

```text
YouTube 댓글창 열림
        |
        v
AccessibilityService가 댓글창 감지 및 선가림
        |
        v
기존 AccessibilityNodeInfo 파서로 작성자/본문/좌표 수집
        |
        +-- 본문 누락 시 댓글 본문 영역만 ML Kit OCR 보완
        |
        v
중복 제거 및 기존 분석 캐시 확인
        |
        v
POST /analyze_android (JSON 배치)
        |
        v
FastAPI -> 정규화 -> v3 문장 분류 -> 유해 댓글만 span CRF 추출
        |
        v
판정/점수/evidence_spans/원래 좌표를 JSON으로 반환
        |
        v
Android가 유해 댓글 제외, 안전 댓글 누적, 미러 댓글창 렌더링
        |
        v
사용자가 끝까지 스크롤하면 원본 댓글창을 한 화면 이동 후 반복
```

## 4. 사용 기술

| 구분 | 기술 | 역할 |
|---|---|---|
| Android | Kotlin, Android SDK 24-36 | 앱과 접근성 서비스 구현 |
| 댓글 수집 | `AccessibilityService`, `AccessibilityNodeInfo` | YouTube 댓글창, 작성자, 본문, 좌표, 스크롤 노드 수집 |
| 원본 스크롤 | `ACTION_SCROLL_FORWARD` | 사용자가 미러 끝에 도달했을 때 원본 댓글 목록을 한 화면 이동 |
| 화면 보호 | `TYPE_ACCESSIBILITY_OVERLAY` | 분석 중 원본 댓글을 가리고 안전 댓글 미러 표시 |
| 미러 UI | Android Canvas, GestureDetector, OverScroller | YouTube 형태의 안전 댓글 목록과 스크롤 구현 |
| 보조 OCR | Google ML Kit Text Recognition, Korean Text Recognition | 접근성에서 본문이 누락된 댓글 행만 인식 |
| 화면 캡처 | `takeScreenshot`, Android 14 이상 `takeScreenshotOfWindow` | OCR 대상 댓글 본문 영역 캡처 |
| Android 통신 | `HttpURLConnection`, Gson | JSON 직렬화와 `/analyze_android` 호출 |
| 서버 | Python, FastAPI, Uvicorn, Pydantic | 요청 검증과 분석 API 제공 |
| 문장 분류 | v3 mDeBERTa encoder + 분류 head | 욕설, 독성, 혐오 여부와 점수 판정 |
| 표현 추출 | XLM-RoBERTa-large + CRF span 모델 | 유해하다고 분류된 문장의 실제 유해 표현 구간 추출 |
| 성능 | 배치 처리, TTL 캐시, 정확/유사 중복 제거 | 재분석과 불필요한 서버 요청 감소 |
| 기기 연결 | Android Debug Bridge의 `adb reverse` | 태블릿의 `127.0.0.1:8000`을 노트북 서버로 전달 |

일반적인 YouTube 화면 전체 OCR은 비활성화되어 있다. 접근성 파서가 기본 수집 방식이고, OCR은 작성자와 답글 위치는 확인되지만 댓글 본문만 누락된 행에 한정한 fallback이다.

## 5. 서버 데이터 형식

Android는 이미지, CSV 또는 별도 JSON 파일을 업로드하지 않는다. 메모리에 생성한 JSON을 HTTP 요청 본문으로 전송한다. `charBoxes`와 같은 Android 내부 렌더링 정보는 서버로 전송하지 않는다.

### 요청 예시

```json
{
  "timestamp": 1786300000000,
  "sensitivity": 100,
  "comments": [
    {
      "commentText": "댓글 내용",
      "boundsInScreen": {
        "left": 84,
        "top": 679,
        "right": 1007,
        "bottom": 831
      },
      "author_id": "android-accessibility-comment:youtube:@user"
    }
  ]
}
```

### 응답 예시

```json
{
  "timestamp": 1786300000000,
  "filtered_count": 1,
  "results": [
    {
      "original": "댓글 내용",
      "boundsInScreen": {
        "left": 84,
        "top": 679,
        "right": 1007,
        "bottom": 831
      },
      "author_id": "android-accessibility-comment:youtube:@user",
      "is_offensive": true,
      "is_profane": true,
      "is_toxic": false,
      "is_hate": false,
      "scores": {
        "profanity": 0.97,
        "toxicity": 0.31,
        "hate": 0.04
      },
      "evidence_spans": [
        {
          "text": "유해 표현",
          "start": 0,
          "end": 5,
          "score": 0.95
        }
      ]
    }
  ]
}
```

Android는 응답 개수와 JSON 구조를 검증한 후 원문과 작성자 식별자를 기준으로 요청 댓글에 결과를 다시 연결한다. 최종 차단 대상으로 사용할 때는 `is_offensive=true`뿐 아니라 유효한 `evidence_spans`가 존재하는지도 확인한다.

## 6. 모델 처리 순서

1. 입력 필터가 `답글`, `좋아요`, 날짜, 정렬 버튼 등 UI 문구를 제거한다.
2. 정규화기가 초성, 반복 문자, 공백 삽입, 영문 키보드 우회 표현 등을 정리한다.
3. v3 문장 분류 모델이 profanity, toxicity, hate 점수와 유해 여부를 판정한다.
4. 안전 판정 문장은 span 모델을 실행하지 않고 바로 반환한다.
5. 유해 판정 문장만 span CRF 모델에 넣어 실제 유해 표현의 시작/끝 위치를 추출한다.
6. 서버가 원래 화면 좌표와 작성자 식별자를 보존해 Android에 반환한다.
7. Android의 안전 댓글 버퍼가 유해 댓글을 제외하고 새 안전 댓글만 누적한다.

핵심 댓글 검열 API는 OpenAI API나 LLM을 사용하지 않는다. 저장소의 Agent API는 설명 기능용 별도 경로이며 YouTube 시연에 필요하지 않다.

## 7. 현재 처리 시간

아래 수치는 개발 노트북, 로컬 FastAPI 서버, v3 및 span 실제 모델을 사용한 측정값이다. 기기와 CPU/GPU 상태, 댓글 수, OCR 사용 여부에 따라 달라질 수 있다.

| 구간 | 측정 시간 |
|---|---:|
| 모델 최초 콜드 요청 | 약 5.5초 |
| 웜 상태 신규 댓글 배치 | 약 0.2-0.3초 |
| 캐시된 댓글 재조회 | 약 0.01초 |
| 스크롤 후 수집부터 미러 갱신 | 약 0.8-1.2초 |
| 웜 상태 초기 댓글창 표시 | 보통 1-2초 |

증분 수집에는 YouTube의 원본 댓글 목록이 멈출 때까지 기다리는 고정 `620ms`가 포함된다. 발표 전에 `/warmup`을 한 번 실행하면 첫 댓글 요청에서 발생하는 모델 로딩 지연을 피할 수 있다.

## 8. 시연 전 준비

### 필수 조건

- Windows 노트북
- Python 3.10 이상과 `backend/.venv`
- Android SDK Platform Tools의 `adb.exe`
- USB 디버깅을 허용한 Android 태블릿 또는 휴대전화
- v3 모델 폴더
- span 모델 폴더
- ChungMaru APK

모델은 용량 때문에 일반 Git checkout에 포함되지 않을 수 있다. 서버 실행 전에 다음 구조를 확인한다.

```text
<MODEL_BASE>/v3/
<MODEL_BASE>/models/span_large_combined_crf/
```

현재 발표 노트북에서는 실행 코드와 모델이 서로 다른 worktree에 있으므로 다음 PowerShell 변수를 그대로 사용할 수 있다.

```powershell
$WORKSPACE = Join-Path $HOME "Documents\Codex\2026-07-01\dnfl"
$REPO = Join-Path $WORKSPACE "ChungMaru_youtube_runtime_fix"
$MODEL_BASE = Join-Path $WORKSPACE "ChungMaru\backend"
$API_DIR = Join-Path $REPO "backend\api"
$PYTHON = Join-Path $MODEL_BASE ".venv\Scripts\python.exe"
$ADB = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
```

다른 PC에서는 `$REPO`, `$MODEL_BASE`, `$PYTHON`만 실제 위치에 맞게 변경한다. 모델과 가상환경이 현재 checkout의 `backend` 안에 있다면 `$MODEL_BASE = Join-Path $REPO "backend"`로 지정하면 된다.

## 9. 서버 실행과 USB 연결

### 9.1 파일 확인

```powershell
Test-Path $PYTHON
Test-Path (Join-Path $MODEL_BASE "v3")
Test-Path (Join-Path $MODEL_BASE "models\span_large_combined_crf")
Test-Path $ADB
```

네 명령이 모두 `True`여야 한다.

### 9.2 태블릿 연결 확인

```powershell
& $ADB devices -l
```

기기 옆에 `device`가 표시되어야 한다. `unauthorized`이면 태블릿 화면의 USB 디버깅 허용 창을 승인하고 다시 실행한다. `offline`이면 USB 케이블을 다시 연결한 후 확인한다.

### 9.3 분석 서버 실행

첫 번째 PowerShell 창에서 다음을 실행하고 시연이 끝날 때까지 창을 닫지 않는다.

```powershell
$env:MODEL_BASE = $MODEL_BASE
Set-Location $API_DIR
& $PYTHON run_server.py --port 8000
```

정상 실행되면 Uvicorn 시작 로그와 `http://...:8000` 리스너가 표시된다. 이 창에는 실제 댓글 요청과 분석 시간이 계속 출력된다.

### 9.4 ADB reverse 설정

두 번째 PowerShell 창에서 실행한다.

```powershell
& $ADB reverse tcp:8000 tcp:8000
& $ADB reverse --list
```

출력에 다음 항목이 있어야 한다.

```text
UsbFfs tcp:8000 tcp:8000
```

이 연결은 태블릿에서 호출한 `http://127.0.0.1:8000`을 USB를 통해 노트북의 8000번 서버로 전달한다. USB 케이블을 뽑거나 기기를 재부팅하면 사라질 수 있으므로 다시 연결한 뒤 위 두 명령을 재실행한다.

### 9.5 서버 상태와 모델 워밍업

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health | ConvertTo-Json -Depth 5

$body = @{
  load_classifier = $true
  load_span_detector = $true
  run_span_probe = $true
  sensitivity = 100
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri http://127.0.0.1:8000/warmup `
  -ContentType "application/json" `
  -Body $body | ConvertTo-Json -Depth 5
```

`/health`의 `status`가 `ok`이고 모델 관련 ready 값이 `true`인지 확인한다. 워밍업은 처음 한 번 수 초가 걸릴 수 있으므로 응답이 올 때까지 기다린다.

## 10. APK 설치와 실제 시연

APK가 이미 설치되어 있으면 설치 단계는 생략한다. 최신 debug APK를 덮어쓰려면 다음을 실행한다.

```powershell
$APK = Join-Path $REPO "android\app\build\outputs\apk\debug\app-debug.apk"
& $ADB install -r $APK
```

시연 순서는 다음과 같다.

1. ChungMaru 앱을 한 번 실행한다.
2. Android 설정에서 ChungMaru 접근성 서비스를 켠다.
3. YouTube 앱을 열고 시연 영상을 재생한다.
4. 댓글창을 연다.
5. 분석 중에는 원본 댓글 영역이 먼저 가려지는지 확인한다.
6. 분석이 끝나면 안전 댓글만 미러 댓글창에 표시되는지 확인한다.
7. 미러 댓글창 끝까지 스크롤한다.
8. 원본 댓글이 한 화면 이동하고 새 안전 댓글이 기존 목록 뒤에 추가되는지 확인한다.
9. 화면 상단에서 아래로 충분히 당겨 댓글창이 함께 닫히는지 확인한다.

시연 중 USB, 서버 PowerShell 창, `adb reverse` 연결을 유지한다.

## 11. 종료 방법

서버를 실행한 첫 번째 PowerShell 창에서 `Ctrl+C`를 누른다. 그다음 두 번째 창에서 reverse 규칙을 제거한다.

```powershell
& $ADB reverse --remove tcp:8000
Get-NetTCPConnection -State Listen -LocalPort 8000 -ErrorAction SilentlyContinue
```

8000번 포트를 계속 점유하는 서버가 남았다면 해당 포트의 프로세스만 종료한다.

```powershell
$serverPid = (Get-NetTCPConnection -State Listen -LocalPort 8000).OwningProcess
Stop-Process -Id $serverPid
```

## 12. 문제 발생 시 확인 순서

### `유튜브 댓글 분석 서버에 연결하지 못했습니다`

1. 서버 PowerShell 창이 열려 있는지 확인한다.
2. 노트북에서 `Invoke-RestMethod http://127.0.0.1:8000/health`를 실행한다.
3. `adb devices -l`에서 기기가 `device`인지 확인한다.
4. `adb reverse --list`에서 `tcp:8000 tcp:8000`을 확인한다.
5. reverse 항목이 없으면 `adb reverse tcp:8000 tcp:8000`을 다시 실행한다.
6. YouTube 댓글창을 닫았다가 다시 연다.
7. 재설치는 마지막 수단으로만 수행한다.

### 서버는 켜졌지만 모델 준비가 실패함

1. `$env:MODEL_BASE`가 v3와 span 모델의 상위 `backend` 폴더인지 확인한다.
2. `v3`와 `models/span_large_combined_crf` 폴더가 실제로 존재하는지 확인한다.
3. 서버를 다시 실행하고 `/warmup` 응답의 오류 내용을 확인한다.
4. 가상환경의 Python으로 `backend/requirements.txt`가 설치되었는지 확인한다.

### 댓글이 일부만 보임

1. YouTube 원본 댓글이 실제로 로드되었는지 확인한다.
2. 미러 목록 끝까지 스크롤해 추가 수집을 발생시킨다.
3. 광고, 로그인 팝업, 정렬 메뉴가 댓글창 위를 덮고 있지 않은지 확인한다.
4. 서버 창에서 `/analyze_android` 요청의 댓글 개수와 시간을 확인한다.

### 안전 댓글 미러가 닫히지 않음

미러가 맨 위에 있는 상태에서 화면 상단부터 아래 방향으로 충분히 당긴다. YouTube 댓글창 자체가 이미 사라졌는데 미러가 남으면 YouTube 앱 화면으로 한 번 돌아간 뒤 댓글창을 다시 열어 상태를 초기화한다.

## 13. 현재 검증 결과

- Android 단위 테스트 348개 통과
- v3 및 span 실제 모델 서버 연동 확인
- 최초 안전 댓글 3개 표시 확인
- 사용자 스크롤 후 안전 댓글 6개로 증분 추가 확인
- 화면이 겹친 구간의 중복 댓글 제거 확인
- 새 viewport에서 발견된 유해 댓글 제외 확인
- 원본 끝에서 `ACTION_SCROLL_FORWARD=false`일 때 기존 안전 댓글 유지 확인
- debug APK 빌드와 실제 태블릿 설치 확인

최신 증분 수집은 에뮬레이터와 실제 모델 서버 조합으로 검증했다. 실제 YouTube의 광고, 로그인 상태, 앱 업데이트에 따른 접근성 구조 변화까지 포함한 최종 물리 기기 반복 검증은 계속 필요하다.

## 14. 현재 문제점과 한계

- 현재 권장 시연 방식은 태블릿 단독 실행이 아니라 노트북 서버, USB 연결, `adb reverse` 조합이다.
- USB 분리 또는 재부팅 뒤에는 reverse 규칙을 다시 설정해야 한다.
- 최초 모델 로딩은 약 5.5초가 걸리므로 발표 전 워밍업이 필요하다.
- YouTube 앱 버전, 언어, 화면 크기, 광고, 로그인 상태에 따라 접근성 노드 구조가 바뀔 수 있다.
- OCR은 이모지, 혼합 언어, 저대비 글자, 변형 문자를 잘못 인식할 수 있다.
- 한 번에 전체 댓글을 수집하지 않고 현재 viewport 단위로 처리하므로 스크롤할 때 추가 분석이 발생한다.
- 미러 UI는 작성자와 댓글 본문 중심이며 좋아요, 싫어요, 답글 등 원본 상호작용을 완전히 재현하지 않는다.
- 모델은 새로운 커뮤니티 은어, 띄어쓰기 우회, 철자 변형에서 오탐 또는 미탐이 발생할 수 있다.
- 로컬 HTTP API에는 운영 서비스용 인증, TLS, 사용자별 권한 관리가 적용되어 있지 않다.
- 대형 PyTorch 모델을 노트북에서 실행하므로 CPU/GPU, 메모리, 팬, 배터리 사용량이 증가한다.
- LAN 자동 탐색과 외부 서버 배포는 현재 발표 필수 경로가 아니며 완성 기능으로 간주하지 않는다.
- Instagram 안전 댓글 미러는 이번 발표의 안정 시연 범위에서 제외한다.

## 15. 발표 마무리 문장

> 현재 구현은 유해 댓글 위에 좌표 마스크를 계속 따라붙이는 방식의 불안정성을 피하기 위해 원본 댓글창을 보호하고 안전 댓글만 별도 미러에 재구성한다. 접근성 파서를 주 수집 수단으로 유지하고 제한적인 OCR fallback, 배치 모델 분석, 결과 캐시, viewport 증분 수집을 결합해 웜 상태에서 약 1초 전후의 스크롤 갱신을 구현했다. 향후에는 분석 서버 배포, 기기 단독 연결, YouTube 버전별 회귀 테스트, 미러 상호작용 확장을 진행한다.
