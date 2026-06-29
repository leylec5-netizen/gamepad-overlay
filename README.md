# 🎮 GamePad Overlay — Android 앱

에뮬레이터 게임 화면 위에 투명하게 겹쳐지는 가상 게임패드 오버레이 앱입니다.

---

## 📦 빌드 방법 (Android Studio)

### 1단계: Android Studio 설치
- https://developer.android.com/studio 에서 다운로드 후 설치

### 2단계: 프로젝트 열기
1. Android Studio 실행
2. **File → Open** 클릭
3. 이 폴더(`gamepad-overlay`) 선택 후 **OK**
4. Gradle 동기화가 자동으로 시작됩니다 (인터넷 필요, 1~3분 소요)

### 3단계: APK 빌드
- 메뉴에서 **Build → Build Bundle(s) / APK(s) → Build APK(s)** 클릭
- 빌드 완료 후 우측 하단 팝업에서 **locate** 클릭
- `app/build/outputs/apk/debug/app-debug.apk` 파일이 생성됩니다

### 4단계: 핸드폰에 설치
- APK 파일을 핸드폰으로 전송 (USB 또는 카카오톡/구글드라이브)
- 설정 → 보안 → **알 수 없는 앱 설치** 허용
- APK 파일 탭 → 설치

---

## 📱 사용 방법

1. **GamePad Overlay** 앱 실행
2. `오버레이 권한 허용` 버튼 탭 → 권한 허용
3. `게임패드 오버레이 시작` 버튼 탭
4. 에뮬레이터 앱 실행 (자동으로 홈으로 이동됨)
5. 화면 위에 게임패드가 표시됩니다!

---

## 🎮 게임패드 기능

| 버튼 | 설명 |
|------|------|
| D-PAD (↑↓←→) | 방향 입력 |
| A / B / X / Y | 액션 버튼 |
| L1 / L2 / R1 / R2 | 어깨 버튼 |
| ⊙ (HOME) | 홈 버튼 |
| ◀◀ SELECT / ▶▶ START | 메뉴 버튼 |
| 왼쪽 스틱 | 아날로그 좌측 (LX, LY 축) |
| 오른쪽 스틱 | 아날로그 우측 (RX, RY 축) |

### ⚙️ 설정 (우측 상단 ⚙ 버튼)
- **투명도 조절**: 게임 화면이 잘 보이도록 조절
- **크기 조절**: 화면 크기에 맞게 조절
- **레이아웃**: 풀 컨트롤 / D-PAD만 / 스틱만
- **게임 모드 잠금**: 실수로 설정창이 열리지 않게 잠금
  - 해제: 화면 4구석을 동시에 탭

---

## 🔧 에뮬레이터 호환성

| 에뮬레이터 | 지원 방식 |
|-----------|----------|
| RetroArch | Gamepad API (자동 인식) |
| PPSSPP | Gamepad API (자동 인식) |
| Dolphin | Gamepad API (자동 인식) |
| 기타 | 에뮬레이터 앱의 키 매핑 설정 필요 |

> **팁**: 에뮬레이터가 Gamepad API를 지원하면 자동으로 인식됩니다.
> 인식이 안 될 경우 에뮬레이터 설정에서 컨트롤러 매핑을 수동으로 설정해주세요.

---

## 📋 필요 권한

- `SYSTEM_ALERT_WINDOW`: 다른 앱 위에 오버레이 표시
- `FOREGROUND_SERVICE`: 에뮬레이터 사용 중 오버레이 유지
- `VIBRATE`: 버튼 누를 때 햅틱 진동

---

## 최소 요구사항
- Android 8.0 (API 26) 이상
- Chromium 기반 WebView 지원 기기
