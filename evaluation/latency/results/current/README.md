# Chungmaru Current Evidence Folder

이 폴더는 최신 latency/evidence 산출물의 작업 공간입니다. 루트에는 `README.md`, `presentable/`, `archive/`만 남기고, 발표나 공유에 바로 쓸 파일은 `presentable/` 아래에서만 찾도록 정리했습니다.

## 바로 볼 것

| 목적 | 위치 |
| --- | --- |
| Android 현재 상태 E2E 데모 | `presentable/android-current-state/` |
| Android 마스킹 대표 프레임 | `presentable/android-current-state/mask-proof.png` |
| Android 현재 상태 설명 | `presentable/android-current-state/README.md` |
| Android 실제 Google 모바일 확인 | `presentable/android-google-mobile/` |
| Android 실제 Google + YouTube E2E | `Validation Needed` - 현재 저장소에 유효한 발표용 영상 없음 |
| 이전 Chrome 데스크톱 영상/자막 부산물 | `archive/chrome-desktop-20260609/` |
| 루트에 흩어져 있던 기존 CSV/JSON/보고서 | `archive/root-raw-20260609/` |

## Chrome Evidence Set Rules

Chrome 데모와 latency 자료는 같은 파일명이라도 서로 다른 실행일 수 있다. 따라서 영상, QA report, metadata, latency CSV를 서로 다른 run에서 섞어 발표 근거로 사용하지 않는다.

| evidence set | 상태 | 사용 규칙 |
| --- | --- | --- |
| `chrome-google-youtube-demo-20260608-final6` | 이전 tracked root run | 기존 Git revision에서 보존된다. 이 run을 사용할 때는 해당 run의 video, QA report, metadata, latency CSV만 함께 사용한다. |
| `archive/root-raw-20260609/` | archive run | 2026-06-09에 재생성된 자료가 포함되어 있다. 이전 root run의 byte-identical 이동본으로 취급하지 않는다. |
| `archive/chrome-desktop-20260609/` | 편집/영상 부산물 | 발표용 영상 편집 자료이며 latency 대표값의 source로 사용하지 않는다. |

발표 슬라이드나 최종 보고서를 갱신하기 전에는 하나의 Chrome evidence set을 선택하고, 선택한 run ID와 source path를 표·영상·caption에 동일하게 적는다. 새 Chrome 실측이 완료되기 전에는 archive 간의 숫자를 합산하거나 더 좋은 값만 골라 쓰지 않는다.

### Current Canonical Sources

- Chrome 발표 데모: `archive/root-raw-20260609/`의 `chrome-google-demo-20260609T104101` run을 사용한다. 영상, QA report, metadata, demo latency CSV/JSONL은 모두 이 경로에서만 참조한다.
- Chrome controlled benchmark: `archive/root-raw-20260609/chrome-e2e-summary.csv`의 `chrome-10k-fixture-20260607T2314` run을 사용한다. 이 값은 발표 데모의 현장 체감 수치가 아니라 controlled fixture benchmark로 표기한다.
- `chrome-google-youtube-demo-20260608-final6`은 Git revision `6289339`에 보존된 legacy run이다. archive의 2026-06-09 자료와 같은 실행으로 간주하지 않는다.
- 전체 이동/대체 목록은 `archive/evidence-inventory.md`를 source of truth로 둔다.

## 현재 Android 데모의 한계

`presentable/android-current-state/`의 영상은 실제 Google 검색 데모가 아니라 Android Chrome fixture 기반 current-state 증거입니다. 마스킹이 되는 장면은 확인되지만, 사용자가 보기에는 테스트 페이지 느낌이 강합니다.

실제 Google 모바일 검색으로 다시 찍을 때는 `scripts/android-google-mobile-evidence.sh`를 사용합니다. 기본값은 데모용으로 짧게 조정되어 있습니다.

현재 `presentable/android-google-mobile/`에는 실제 Google 검색 화면 진입과 검색창 마스킹을 확인한 정지 프레임이 있습니다. 다만 clean E2E 영상은 ADB/emulator 재실행 중 device가 끊겨 아직 유효 산출물로 만들지 못했습니다.

```bash
RECORD_SECONDS=12 \
WAIT_AFTER_LOAD_SECONDS=3 \
SWIPE_SETTLE_SECONDS=0.7 \
FINAL_SETTLE_SECONDS=0.5 \
GOOGLE_QUERY_SET=moderation-core \
ANDROID_GOOGLE_VIDEO_POLICY=first \
scripts/android-google-mobile-evidence.sh run
```

느린 네트워크나 안정성 검증 배치에서는 `WAIT_AFTER_LOAD_SECONDS=7`, `RECORD_SECONDS=20` 이상으로 올립니다.

Google 검색과 YouTube 탐색을 한 영상으로 묶어 다시 촬영할 때는 아래 스크립트를 사용합니다. 유효한 runtime 결과가 확인되기 전에는 이 경로의 산출물을 발표 근거로 사용하지 않습니다.

```bash
RECORD_SECONDS=45 \
GOOGLE_QUERY="병신아 꺼져" \
YOUTUBE_QUERY="욕설 댓글 테스트" \
scripts/android-real-e2e-demo.sh run
```

## 파일 정리 기준

- `presentable/`: 발표나 공유에서 먼저 열 파일.
- `archive/`: 재현이나 편집용 부산물. 발표에서 직접 쓰지 않는 파일.
- `archive/root-raw-20260609/`: 루트에 흩어져 있던 기존 CSV/JSON/MD/MP4와 차트. 보존은 하되 발표 기본 경로에서는 숨깁니다.
