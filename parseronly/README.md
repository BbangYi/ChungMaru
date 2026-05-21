# Parser Only

이 폴더는 Android Studio에서 작업한 독립 실행형 SNS 댓글 파서 프로젝트입니다.

대상 플랫폼은 YouTube, Instagram, TikTok이며, 접근성 노드에서 댓글 후보를 추출해
로컬 JSON 파일로 저장합니다.

## 현재 상태

2026년 5월 21일 기준 최종 수정본입니다.

- YouTube: 댓글 패널이 열려 있을 때만 저장하고, Shorts 하단 제목/음원 텍스트를 저장하지 않습니다.
- Instagram: 댓글 surface가 확인될 때만 저장하고, 스토리/피드/릴스 표면 텍스트를 저장하지 않습니다.
- TikTok: 하단 좌측 영상 캡션/오버레이를 저장하지 않고, 작성자 없는 반복 본문은 중복으로 제거합니다.
- TikTok의 이모지만 있는 짧은 반응은 실제 댓글로 볼 수 있어 무조건 제거하지 않습니다.

## 주요 변경 사항

- `MainActivity.kt`
  - YouTube, Instagram, TikTok 중 하나만 자동화할 수 있는 플랫폼별 실행 버튼을 유지합니다.

- `AutomationSettingsStore.kt`
  - 선택한 플랫폼만 자동화할 수 있도록 플랫폼 모드 저장 기능을 유지합니다.

- `ParserModels.kt`
  - 저장 스냅샷에 `sourcePackage`, 파싱 시작/종료 시각, 파싱 시간, 노드 수, 파싱 댓글 수, 저장 댓글 수, 초당 댓글 수를 기록합니다.
  - rolling batch 저장을 위한 `RollingParseFile`, `RollingParseSummary` 모델을 추가했습니다.

- `YoutubeAccessibilityService.kt`
  - YouTube 댓글 패널이 열려 있지 않으면 파싱/저장을 건너뜁니다.
  - Instagram 댓글 surface가 열려 있지 않으면 파싱/저장을 건너뜁니다.
  - Instagram Reels 탭 진입, 댓글 버튼 클릭, 2-pane 댓글창 감지, 댓글창 내부 스크롤을 보강했습니다.
  - 파싱 시간과 초당 댓글 수를 로그/스냅샷에 남깁니다.

- `JsonFileStore.kt`
  - 플랫폼별 저장 필터를 분리했습니다.
  - YouTube는 작성자 없는 항목, 숫자/카운트, UI 텍스트, Shorts 표면 제목/음원 텍스트를 저장하지 않습니다.
  - Instagram은 작성자 없는 항목, 스토리/피드 설명, 카운트, 하단 캡션을 저장하지 않습니다.
  - TikTok은 UI 라벨, 시간/숫자 라벨, 하단 좌측 surface caption을 저장하지 않습니다.
  - `author_id + commentText` 중복 제거를 유지하고, TikTok처럼 작성자가 비는 경우에는 본문 기준 중복 제거를 추가했습니다.
  - 단일 파일이 커지는 문제를 줄이기 위해 플랫폼별 rolling batch JSON으로 저장합니다.

- `InstagramCommentExtractor.kt`
  - 댓글 본문 후보와 UI/메타 텍스트 구분을 강화했습니다.
  - 작성자/본문 후보를 더 안정적으로 묶도록 보강했습니다.

- `TiktokCommentExtractor.kt`
  - 클릭 가능한 사용자명 노드를 작성자 후보로 더 강하게 사용합니다.
  - 검색, 첫 댓글, 효과 라벨, 숫자형 멘션 라벨, 프로필 라벨, 댓글 제한 안내 등 댓글이 아닌 TikTok UI 텍스트를 차단합니다.

- `tools/pull_parser_results_to_desktop.ps1`
  - 기기에서 생성된 `parse_results`를 데스크톱으로 가져오는 보조 스크립트입니다.

## 저장 형식

저장 파일은 플랫폼별 rolling batch JSON입니다.

```text
Youtube_comment_batch_yyyyMMdd_HHmmss_SSS.json
Instagram_comment_batch_yyyyMMdd_HHmmss_SSS.json
Tiktok_comment_batch_yyyyMMdd_HHmmss_SSS.json
```

각 파일은 여러 스냅샷을 포함합니다.

```json
{
  "platform": "YouTube",
  "sourcePackage": "com.google.android.youtube",
  "snapshots": [
    {
      "timestamp": 1779372138965,
      "sourcePackage": "com.google.android.youtube",
      "parseDurationMs": 127,
      "visibleNodeCount": 123,
      "parsedCommentCount": 4,
      "savedCommentCount": 4,
      "commentsPerSecond": 31.49,
      "comments": [
        {
          "author_id": "sample_user",
          "commentText": "실제 댓글 본문",
          "boundsInScreen": {
            "left": 96,
            "top": 992,
            "right": 1144,
            "bottom": 1032
          }
        }
      ]
    }
  ]
}
```

## 검증 결과

### Instagram

2026년 5월 21일 재수집 데이터 기준입니다.

| 확인 항목 | 결과 |
| --- | ---: |
| batch 파일 수 | 14 |
| 스냅샷 수 | 49 |
| 파싱된 댓글 수 | 385 |
| 저장된 댓글 수 | 209 |
| 스토리/피드/캡션 의심 항목 | 0 |
| `author_id + commentText` 중복 | 0 |

### YouTube

YouTube 수정 APK 설치 후 재수집한 데이터 기준입니다.

| 확인 항목 | 결과 |
| --- | ---: |
| batch 파일 수 | 17 |
| 스냅샷 수 | 66 |
| 파싱된 댓글 수 | 251 |
| 저장된 댓글 수 | 243 |
| 작성자 누락 | 0 |
| 빈 댓글 본문 | 0 |
| Shorts 제목/음원 후보 | 0 |
| `author_id + commentText` 중복 | 0 |
| `commentText` 중복 | 0 |

### TikTok

TikTok 수정 APK 설치 후 재수집한 데이터 기준입니다.

| 확인 항목 | 결과 |
| --- | ---: |
| batch 파일 수 | 19 |
| 스냅샷 수 | 83 |
| 파싱된 댓글 수 | 311 |
| 저장된 댓글 수 | 238 |
| 빈 댓글 본문 | 0 |
| invalid bounds | 0 |
| 하단 좌측 surface caption 후보 | 0 |
| 의심 UI 텍스트 | 0 |

TikTok은 플랫폼 특성상 짧은 반응과 이모지-only 댓글이 실제 댓글로 자주 존재합니다.
따라서 이모지만 있는 댓글은 UI 오염으로 보지 않고 유지합니다.

## 빌드 및 설치

Android Studio에서 `parseronly/` 폴더를 열거나, 아래 명령으로 설치할 수 있습니다.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:installDebug
```

컴파일만 확인하려면 다음 명령을 사용합니다.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:compileDebugKotlin
```

## 요약

- YouTube: 댓글창 외 Shorts 제목/음원 저장 문제를 차단했습니다.
- Instagram: 댓글 surface가 아닌 피드/스토리/릴스 표면 저장 문제를 차단했습니다.
- TikTok: 영상 캡션/오버레이 저장과 작성자 없는 반복 본문 중복 저장을 줄였습니다.
- 세 플랫폼 모두 모델 입력 전 단계에서 실제 댓글 중심으로 저장되도록 정리했습니다.
