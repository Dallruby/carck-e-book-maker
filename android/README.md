# dallruby crack e-book maker

크랙 채팅 Markdown 파일을 Android에서 이북처럼 읽는 앱입니다.

## 지금 들어간 기능

- `.md` 파일 열기
- 유저 이름 / 캐릭터 이름 입력
- `## User`, `## AI`를 이름으로 바꿔서 표시
- 이미지 링크를 실제 이미지로 표시
- `INFO` 블록을 회색 카드로 표시
- 긴 채팅방은 페이지로 나눠서 렉을 줄임
- 마지막 읽은 페이지 저장

## APK 만드는 쉬운 방법

이 폴더를 GitHub에 올리면 GitHub가 APK를 대신 만들어줍니다.

1. GitHub에 새 저장소를 만듭니다.
2. 이 `DallrubyCrackEbookMaker` 폴더 안의 파일들을 올립니다.
3. GitHub 저장소에서 `Actions` 탭을 누릅니다.
4. `Build APK`를 누릅니다.
5. `Run workflow`를 누릅니다.
6. 빌드가 끝나면 아래쪽 `Artifacts`에서 APK를 다운로드합니다.

다운로드 이름은 보통 이렇게 뜹니다.

```txt
dallruby-crack-ebook-maker-debug-apk
```

그 안에 들어있는 `.apk` 파일을 안드로이드 폰에 설치하면 됩니다.

## 참고

지금 버전은 테스트용 debug APK입니다. 처음 폰에 설치할 때 “알 수 없는 앱 설치” 허용이 필요할 수 있습니다.
