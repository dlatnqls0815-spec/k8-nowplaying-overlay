# K8 나우플레잉 오버레이

기아 K8(Gen5W 헤드유닛, Android 4.4.2)의 내비 화면 위에 지금 재생 중인 곡을 작은 카드로 띄우는 앱입니다.

- 곡 제목·아티스트(길면 흐르게), 앨범 아트, 이전/재생·일시정지/다음 버튼
- 곡이 바뀔 때 뜨는 순정 곡 팝업을 바로 닫음
- 블루투스 통화 중이면 카드가 통화 카드로 바뀜: 상태·통화 시간, 이름·번호, 프라이버시 모드 표시, 받기·거절·끊기

순정 시스템 파일은 바꾸지 않습니다. 일반 앱 + 오버레이 창입니다.

## 필요한 것

- 기아 K8 Gen5W 헤드유닛에 **NEMESIS**(루트)가 설치돼 있어야 합니다. 이 헤드유닛은 일반 APK 설치를 거부합니다.
- 만든 사람 차(펌웨어 260902 계열)에서만 확인했습니다. 다른 펌웨어에서는 통화 카드나 순정 팝업 닫기가 안 될 수 있습니다.

## 설치 (USB)

1. [릴리스](../../releases)에서 `K8-nowplaying-<버전>-USB.zip`을 받습니다.
2. USB 맨 위에 이미 `NEMESIS.data`가 있으면 따로 백업합니다(덮어씁니다).
3. zip을 USB 맨 위에 풉니다 → `NEMESIS.data`, `packages/k8np.install-<버전>.zip`.
4. 정차·P 상태에서 USB를 꽂고 네메시스 설치를 확인합니다.
5. 네메시스 Manager → My Packages → **K8 Now Playing Installer** → Launch로 결과를 봅니다(`installed`면 성공). 다시 실행하면 다시 설치합니다.

설치기는 `pm install -r`을 하는 동안만 `sys.boot_completed`를 0으로 두고 바로 되돌립니다([nemesis-apk/bin/install.sh](nemesis-apk/bin/install.sh)).

이전/재생/다음 버튼은 별도 네메시스 패키지 `k8np.keys`(핸들 버튼 키를 대신 보내는 도우미, [nemesis/](nemesis/))가 있어야 동작합니다.

## 주의

- 개인 개조 프로젝트입니다. 쓰는 책임은 본인에게 있습니다. 차량 설정은 바꾸지 않지만 헤드유닛 루트 권한으로 앱을 설치합니다.
- 운전 중 화면 조작은 하지 마세요.
- [docs/FIELD_TEST_NEXT.md](docs/FIELD_TEST_NEXT.md)는 만든 사람의 실험 기록입니다. 그 안의 odex 파일 패치·서개모 후크는 실험 단계라 따라 하지 않는 것을 권합니다.

## 빌드

JDK 17, Android SDK(build-tools, android-19 platform)가 필요합니다.

```bash
python tools/build.py --sdk <SDK> --jdk <JDK>  # 앱 APK (v1 서명, 키는 직접 준비)
python tools/build_helper.py --jdk <JDK> # 버튼 도우미 k8np.keys
python tools/build_usb_install.py <apk> --version <버전>  # USB 설치 묶음
```
