# dallruby crack e-book maker

달루비가 만든 크랙AI 채팅 E-book 메이커입니다.

크랙 채팅을 Markdown 백업 파일이나 EPUB 전자책으로 저장하고, 나중에는 Android 앱에서 이북처럼 읽는 것을 목표로 합니다.

## 1. 템퍼몽키 채팅 저장 도구

크랙 채팅방을 켠 상태에서 현재 채팅을 `.md` 또는 `.epub` 파일로 저장합니다.

### 설치

1. Chrome에 Tampermonkey를 설치합니다.
2. 아래 링크를 누릅니다.
3. Tampermonkey 설치 화면에서 `설치`를 누릅니다.

[dallruby crack e-book maker 설치하기](https://raw.githubusercontent.com/Dallruby/carck-e-book-maker/main/userscript/dallruby_crack_ebook_maker_v10.user.js)

### 사용법

1. 크랙에서 저장하고 싶은 채팅방을 엽니다.
2. 오른쪽 위의 `dallruby e-book maker` 패널을 엽니다.
3. `다시 잡기`를 한 번 누릅니다.
4. `현재 채팅 저장하기`를 누릅니다.

## 2. Android APK 앱

크랙에서 저장한 Markdown 파일을 불러와서 크랙 UI와 비슷한 이북 화면으로 읽는 앱입니다.

현재 앱 프로젝트는 `android` 폴더에 들어 있습니다.

앱 기능:

- Markdown 파일 불러오기
- 유저 이름 / 캐릭터 이름 직접 입력
- Markdown 형식 적용
- 이미지 링크 표시
- 긴 채팅도 페이지 단위로 나눠서 렉 줄이기
- 크랙과 비슷한 어두운 읽기 화면

## APK 만드는 법

이 리포지토리의 `Actions` 탭에서 `Build Android APK`가 실행되면 APK 파일이 만들어집니다.

완성된 파일은 Actions 실행 결과의 `Artifacts`에서 받을 수 있습니다.

## 폴더 구조

```text
userscript/
  dallruby_crack_ebook_maker_v10.user.js

android/
  Android APK 앱 프로젝트

.github/workflows/
  build-apk.yml
```

