# Chungmaru 현재 Worktree Lane 분류

작성일: 2026-05-11

이 문서는 현재 dirty tree를 lane 기준으로 분류해, 다음 Codex 작업이 Android/backend/docs 변경을 한 번에 다시 넓게 읽지 않도록 만든 운영 스냅샷이다. 기존 변경은 사용자 또는 다른 Codex 작업으로 보고 되돌리지 않는다.

## 현재 상태

- branch: `codex/android-overlay-candidate-tightening`
- 상태: substantial dirty tree
- 작업 정책: 커밋, stash, reset, rebase 없이 lane 단위로 이어받는다.
- live helper: `scripts/codex-lane-status.sh`

## Lane별 현재 변경

| lane | 현재 변경 성격 | 다음 작업 기본 경계 |
| --- | --- | --- |
| `android` | Android manifest, `MainActivity`, `youtubeparser` OCR/ROI/mask planner, Android unit tests | `android/app/**`와 관련 Gradle unit test로 제한 |
| `backend` | FastAPI app, input filter, normalizer, pipeline, backend tests | `backend/api/**`, `backend/tests/**`와 좁은 pytest/TestClient 범위로 제한 |
| `extension` | 현재 큰 변경 없음 | extension 작업 요청이 있을 때만 `extension/**` 확인 |
| `shared-contract` | 현재 큰 변경 없음 | contract 변경이 있을 때만 consumer 최소 확인 |
| `docs-evaluation` | constraints, engineering history, evaluation cases, backend model notes, presentation material, lane helper | 문서/JSONL/운영 helper 형식 확인 중심 |

## 작업 시작 전 명령

```bash
scripts/codex-lane-status.sh
```

이 helper 결과에서 이번 작업의 primary lane을 하나 고른 뒤 시작한다. cross-lane이 필요한 경우에는 `primary lane`, `secondary lane`, `reason`, `verification`을 작업 지시문에 명시한다.

## 금지 사항

- 기존 dirty 파일을 정리 목적으로 되돌리지 않는다.
- Android emulator, backend server, model download, watch mode는 작업에 꼭 필요할 때만 실행한다.
- lane이 다른 파일을 “같이 보이는 김에” 수정하지 않는다.
