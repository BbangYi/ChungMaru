# GitHub 협업 가이드

이 문서는 현재 저장소에서 실제로 사용하는 협업 흐름만 정리합니다.

## 기본 원칙

1. 기본 작업 흐름은 `feature branch -> PR -> main` 입니다.
2. `main`은 보호 브랜치로 유지하고, 머지는 `Squash merge`만 사용합니다.
3. 리뷰와 최종 판단은 사람이 합니다.
4. 관리자(`@Gimminu`)는 긴급 수정이나 정리 작업 시 직접 반영할 수 있습니다.
5. 자동화는 보조 수단이고, 충돌 여부와 영향 범위 확인은 사람이 직접 합니다.

## 현재 보호 정책

1. `main`은 linear history를 유지합니다.
2. CODEOWNER 리뷰 1개가 필요합니다.
3. stale review 자동 무효화는 사용하지 않습니다.
4. last push approval 강제는 사용하지 않습니다.
5. 관리자 bypass는 허용합니다.

즉, 일반 팀원은 PR 중심으로 작업하고, 관리자만 예외적으로 직접 정리할 수 있습니다.

## 팀원 작업 방법

### 1. 브랜치 생성

```bash
./scripts/new-branch.sh feat <short-topic> --push
```

예시:

```bash
./scripts/new-branch.sh feat android-comment-capture --push
```

### 2. 작업 및 커밋

```bash
git add .
git commit -m "feat: <what changed>"
```

### 3. PR 생성

1. 작업 브랜치에서 `main`으로 PR 생성
2. PR 템플릿 작성
3. 변경 범위, 충돌 여부, 테스트 결과를 리뷰어가 바로 볼 수 있게 정리

## 리뷰/머지 흐름

1. PR 작성
2. 충돌 여부 확인
3. 필요 시 `main` 기준 동기화
4. 기능 적합성, 제약 사항, 담당 범위 충돌 여부 확인
5. 서비스 범위 변경이 있으면 `docs/service-definition.md`와 `README.md` 반영 여부 먼저 판단
6. 리뷰 후 `Squash merge`
7. 머지 후 작업 브랜치 삭제

## PR 검토 판단 기준

PR을 받으면 아래 셋 중 하나로 처리합니다.

1. 반영: 현재 서비스 목표와 맞고 영향 범위가 명확할 때
2. 수정 후 반영: 방향은 맞지만 테스트, 계약, 문서, 구현이 일부 비어 있을 때
3. 보류: 현재 범위를 벗어나거나 담당 경계 충돌이 크고 설명이 부족할 때

즉, 코드를 먼저 합치는 것이 아니라 현재 서비스 정의와 맞는지부터 확인합니다.

## 문서 동기화 원칙

아래 변경이 발생하면 문서를 함께 갱신합니다.

1. 핵심 기능 추가 또는 삭제
2. Demo 범위 변경
3. 사용자 시나리오 변경
4. 플랫폼 역할 변경
5. 정책 또는 공통 계약의 의미 있는 수정

정책은 `README.md`를 짧은 진입 문서로 유지하고, 서비스 설명 본문은 `docs/service-definition.md`에서 관리하는 것입니다.

## 관리자 작업 원칙

`@Gimminu`는 아래 경우 직접 수정 또는 직접 머지할 수 있습니다.

1. 브랜치 구조가 불필요하게 꼬였을 때
2. 팀원이 충돌 해결을 못 하고 멈춘 상태일 때
3. 문서/설정/브랜치 정리처럼 빠른 정비가 더 중요한 경우
4. 자기 PR을 직접 확인하고 바로 머지해야 하는 경우

다만, 가능하면 PR 기록은 남기는 쪽이 좋습니다.

## 충돌 해결 기본

```bash
git fetch origin
git checkout feat/<short-topic>
git rebase origin/main
# 충돌 수정
git add <fixed-files>
git rebase --continue
git push --force-with-lease
```

## 자주 틀리는 부분

1. `main`에서 바로 작업 시작함
2. PR 설명 없이 변경 파일만 올림
3. 여러 기능을 한 PR에 섞음
4. Android, extension, backend를 한 번에 크게 건드림
5. 충돌이 난 상태로 방치함
