# 다음 차 연결 때 한 번에 할 것 (2026-11-16 정리)

차 ADB: 무선 디버깅. 명령 전 logcat에서 `BCMManager.getSpeed_KMHUnit() = 0.0` 확인. Git Bash는 `export MSYS_NO_PATHCONV=1`.

## 1. 나우플레잉 0.4.1 설치 (사용자 승인 받음: "같이 볼게")
- 파일: `deliverables/k8-nowplaying-0.4.1.apk` (v1 서명, `private/k8np-sha1.apk`와 같음)
- 설치: /data/local/tmp에 push → `nemesis root -c "sh /data/local/tmp/np-install.sh"`
- 확인
  - 긴 제목/가수가 옆으로 흐르는지 (MarqueeText)
  - 곡 넘어갈 때 순정 OSD가 닫히는지: logcat `K8NowPlaying` "stock OSD cancel sent",
    `SplitScreen`/`SplitScreenViewBTAudio`의 OSD 닫힘(`onOsdDismiss`) 여부
  - 안 닫히면: 분할화면 앱(`com.mobis.splitscreen.right`)이 `ACTION_SPLITSCREEN_CONTROL`을 권한으로 막는지 확인
  - 대안: 순정 설정 '미디어 변경 알림'(Settings.System `media_notification`) 끄기 → 자동 넘김 OSD만 사라짐
- 이번 기회에 k8np.keys 도우미가 시동 후 자동으로 떴는지 `nemesis service status k8np.keys/keys`

## 2. 통화 오버레이 조사용 로그 (전화 1통: 걸려 옴 → 받기 → 끊기)
- logcat 전체를 통화 전후로 기록 (`private/`에 저장, 커밋 금지 — 전화번호·이름 포함)
- 볼 것
  - `com.hkmc.phone.widgetupdate` extras (이름, 번호, 통화시간, 상태 키 이름)
    → 0.4.1이 두 통화 방송의 extras를 logcat `K8NowPlaying` "call ... extras:"로 남김 (화면 표시 없음)
  - `com.broadcom.bt.hfdevice.profile.action.CALL_STATE_CHANGED`
  - 순정 수신 화면이 Activity인지 OSD인지 (`am_resume`/`checkShowOSD`/SplitScreenViewBTPhone)
  - 핸들 통화/종료 키 코드 (`[key] from` 로그) → 도우미 허용 목록 후보
  - 0.4.1 `CallProbe`: Broadcom `BluetoothHfDevice`(우리 차 /system/framework/com.broadcom.bt.jar에 있음)를
    일반 앱에서 열 수 있는지 확인. logcat `K8NowPlaying` "call probe getProxy=… / connected / inCall=… number=…".
    읽기만 함(받기·끊기·음소거·이벤트 핸들러 등록 안 함). 열리면 answer()/hangup()/setMicMute()로
    도우미 없이 통화 버튼 가능.

## 참고: EV6-NOWPLAYING 0.5.10 (2026-11-17 검토)
- HMG-API 후크 모듈이라 우리 차(서개모)에는 그대로 못 씀. 코드에 라이선스가 없어 복사하지 않고 방식만 참고.
- 우리 차 RightScreenHome.odex에 EV6가 쓰는 `com.mobis.splitscreen.right.r.i`, `k.w`, `k.q`가 같은 이름으로 있음
  (같은 260902 계열). 순정 BT OSD는 EV6가 `r.i.show(int,int)` 후크로 숨김 → 서개모 후크로 옮길 수 있는 예비안.
- 분할화면 앱의 `ACTION_SPLITSCREEN_CONTROL` 수신기는 권한 없이 등록(v/a.smali) → 0.4.1 닫기 신호는 받아질 가능성 높음.
- 통화: EV6는 BluetoothHfDevice(profile 100) + `content://com.android.contacts_bt/phone_lookup`(이름)
  + `ContactsContract.Contacts.CONTENT_URI_BT`의 contact_photo(사진). 우리 차 jar에도 같은 메서드 있음.
- 설정 아이디어: 순정 곡 알림이 뜰 때 카드 이동 / 숨김 선택, 진행선 켜기, Pretendard 글꼴(OFL).

## 2026-11-17 실차 결과 (0.4.1)
- 곡 넘김 순정 OSD: 닫기 방송이 받아져 약 0.2초 만에 닫힘(`RightSideOsd cancel ... immediate=true`). 깜빡임까지 없애려면 서개모 후크.
- 통화: 일반 앱에서 BluetoothHfDevice 열림. getCallStateInfo로 setup(2 발신, 3 연결, 4 수신, 6 없음)과 active 수는 읽힘, 번호는 null.
  - 번호는 MobisBTCallInfo(onMobisBTCallInfo 콜백)와 런처 BTCallView에만 있음. 콜백은 registerEventHandler가 서비스 쪽에서
    하나만 받는지 모르니 블루투스 APK(com.android.bluetooth) 확인 전에는 쓰지 않음.
  - 순정 전화 설정 '프라이버시 모드'가 켜져 있음(번호 대신 "프라이버시 모드"). 내비가 앞이면 순정은 팝업 대신 오른쪽 분할 화면을 전화로 바꿈
    (com.hkmc.bluetooth → `com.mobis.intent.action.SPLIT_SCREEN_SHOW`).
  - `com.hkmc.phone.widgetupdate`는 우리 수신기에 안 옴(런처만 받음).
- 0.4.2: 통화 중이면 카드가 "전화 수신 중 / 전화 거는 중 / 통화 중 m:ss"로 바뀜(설정에서 끄기 가능). 번호는 읽히면 표시, 아니면 "블루투스 전화".
- 0.4.3 (차 설치함): 통화 시작 때 `content://com.android.contacts_bt/profile_privacy_mode`를 selectionArgs {Settings.System `user_profile_driver`, "1", "read"}로 한 번 읽어
  켜져 있으면 둘째 줄을 "프라이버시 모드"로 표시(READ_CONTACTS). 동승석 착석 + 설정 켜짐일 때 1.
- 통화 받으면(OffHook) 분할화면 앱 SplitScreenHome이 `ACTION_MOBIS_BT_CALL_STATUS`를 받아 전화 페이지로 바꿈(내부 메시지, 공개 방송 없음).
  막거나 되돌리려면 서개모 후크 필요(모듈 설치 = 별도 승인).

## 서개모 후크 k8np-call-split 0.1.0 (2026-11-18, 차 설치 전)
- `hook_apps`로 com.mobis.splitscreen.right 안에서 `SplitScreenHome.i(int state, int callType)` 뒤에 실행.
  state=2(OffHook)·callType=0(BT)일 때만 핸들러 `pv`의 메시지 0x3f1(50ms 뒤 전화 페이지로 넘김)을 지움.
  통화 상태·전화 뷰·끊은 뒤 복원은 순정 그대로. 블루링크/비상통화(callType≠0)는 건드리지 않음.
- 빌드: `python tools/build_hsm.py --jdk <jdk17>` → `private/seogaemo/k8np-call-split-0.1.0.hsm` (deliverables에도 복사).
- 설치: 서개모 앱 → 모듈 추가 → USB `HSM/`에서 가져오기 → 켜기 → 헤드유닛 재시작 1회.
- 확인: 통화 받을 때 오른쪽이 그대로인지, 서개모 로그 "k8np: kept split screen page on call".
- 되돌리기: 모듈 끄고 헤드유닛 재시작.

## 분할화면 앱 파일 패치 (2026-11-18 차 적용, 서개모 후킹은 이 차에서 실패해 대신 씀)
- 서개모 후킹 실패 원인: app_process 교체 후 /system ro 재마운트가 EPERM(실행 중 앱이 옛 app_process를 매핑). 모듈 k8np-call-split은 설치만 되고 꺼짐.
- `/system/app/RightScreenHome.odex`: SplitScreenHome.i(II) OffHook 분기의 `n/l->a(Message,J)` 송신(0x3f1, 50ms) 6바이트를 nop 3개로.
  dex 안 오프셋 0x5eaa4(파일 0x5eacc), dex adler32 다시 계산(SHA-1은 원래도 불일치라 그대로), odex 헤더 체크섬은 deps/opt만 덮어 변경 없음.
- 원본 md5 55db33cf1a249331e6151427e056f4f3 → 패치 5f5da733b9438da8cc168ee79a855630. 원본 백업: 차 `/data/nemesis/backup/k8np-split/RightScreenHome.odex.orig`, PC `private/splitpatch/`.
- 되돌리기: 원본을 /system/app/RightScreenHome.odex로 복사(644 root:root) 후 재시작.
- 펌웨어 업데이트하면 원본으로 바뀜(다시 패치 필요, 오프셋은 빌드마다 다름).

## 순정 수신 팝업 막기 (2026-11-18 차 적용, 사용자 승인)
- `/system/app/HKMC_BtPhoneContacts.odex`: InCallAlert(ab) `a(MobisBTCallInfo)`와 `k(Context)`의 `if-eqz v0` → `goto/16`
  (dex 0x133996, 0x1382a4 첫 바이트 0x38→0x29) → onCreate/onResume(창 띄우기)만 건너뜀. 닫기 `a(ab)`는 hA=false면 아무것도 안 함.
- 원본 md5 3cbade0283adc670514d77ce9ab59ee7 → 패치 7e582341169d03520de5d55c0f480544. 백업: 차 `/data/nemesis/backup/k8np-split/`, PC `private/btpatch/`.
- 0.4.4: 이벤트 핸들러(RemoteCallbackList라 순정과 같이 받음)로 번호·이름, 연락처 phone_lookup으로 이름, 카드에 받기/거절/끊기.
- 2차(현재 차에 적용): 1차 패치(onCreate/onResume 건너뜀)는 벨소리·이름 음성 안내까지 빠짐 → 원본에서 다시.
  InCallAlert.onResume addView 직전 `or-int/lit8 flags,0x2; iput dimAmount=0.7` 자리를 `or-int/lit16 flags,0x218`(NO_LIMITS|NOT_TOUCHABLE|NOT_FOCUSABLE),
  `x=10000`으로 바꿔 창은 뜨되 화면 밖·터치 통과. dex 0x1399e6. md5 1053397d2e258115a8baa2de200e44b3.
- 0.4.5: BT 연락처 phone_lookup은 projection을 무시하고 모든 열을 줌 → getColumnIndex("display_name").

## 2026-11-18 02시 정리 (차는 통화 쪽 순정으로 되돌림)
- 전화 앱 패치 3·4차 시도 중, 그리고 전화 앱 원본 + 분할화면 패치 상태에서 "전화 올 때 화면 전체 까맣게" 발생(사진, 시동 ON).
  로그: 벨 울리는 동안 MicomPowerService SCREEN_ON이 1~2초마다 반복, 끄는 쪽 기록 없음, 내비 프로세스는 유지.
- 분할화면·전화 앱 둘 다 원본으로 되돌리고 재시작한 뒤 통화: 화면 안 꺼짐, 순정 수신 창이 내비를 가림(정상 순정 동작).
  → 까만 화면은 분할화면 패치(OffHook 0x3f1 송신 제거)와 관련 있을 가능성. 00:41 첫 시험(분할화면 패치만)은 정상이라 간헐적.
- 현재 차: RightScreenHome·HKMC_BtPhoneContacts 원본(md5 55db33…, 3cbade…), 나우플레잉 0.4.5. 서개모 k8np-call-split 모듈은 설치만 되고 꺼짐.
- 다음 진단 아이디어: 분할화면 패치만 다시 넣고 벨 울리는 동안 screencap 자동 저장 + dumpsys window로 맨 위 창 확인.

## 다음 현장 시험: 까만 화면 진단 + 전화 앱 patch4 (2026-11-18 준비, 미실행)
도구: `tools/field_call_test.sh` (Git Bash). 주소는 `K8_ADDR` 또는 `private/k8-addr`. odex 교체(`set`)는 사용자 승인 후에만.
- `status`: 연결·속도·두 odex md5·uptime·프로세스. `set`은 속도 0일 때만 진행하고, 교체 후 md5 확인 → 서개모 재시작 →
  `/proc/uptime`이 줄었는지 확인(조용한 실패면 1회 재시도, 그래도 안 되면 시동 OFF/ON 요청 후 중단).
- `call <이름> [초]`: 차에서 약 2초마다 screencap + `dumpsys window windows` + `dumpsys power` + 백라이트 값 + logcat 수집,
  `private/logs/field-<시각>-<이름>/`으로 가져와 시점별 요약(맨 위 창, wakefulness, 백라이트, SCREEN_ON 횟수).
  까만 화면일 때 screencap이 정상 내비면 → 백라이트/전원 쪽, screencap도 까마면 → 맨 위 창이 원인.

순서(재시작 3~4회, 각 단계 전화 1통: 벨 15초 → 받기 → 15초 → 끊기):
1. `status` (현재 둘 다 원본이어야 함)
2. `set patch orig` → `call split` (분할화면 패치만, 까만 화면 재현·수집. 간헐적이라 2통 권장)
3. `set orig p4` → `call p4` (전화 앱 patch4만: 순정 수신 창이 안 보이는지, 벨소리·이름 음성 안내 나오는지 귀로 확인)
4. 3이 좋으면 `set patch p4` → `call both` (최종 조합)
5. 마무리: 결과에 따라 유지하거나 `set orig orig`로 원복
patch4 바이트 차이(원본 대비, 파일 오프셋 10진수): 1273309~13과 1279047~52를 0으로(nop), 1284626~39 바꿈(크기 0 창 LayoutParams), 49~52(dex adler32). md5 ed2b5dc6cd967406ed6cd606baf0facc.

## USB 설치 묶음 k8np.install 0.4.5 (2026-11-18, 차 미시험)
- 네메시스 패키지가 앱 APK를 품고, postinstall에서 `nemesis-apk/bin/install.sh`로 서개모와 같은 방식(sys.boot_completed 잠깐 0 → `pm install -r` → 1로 복원, trap으로 중단돼도 복원) 설치.
  네메시스 매니저에서 앱 실행 = 다시 설치 + 결과 토스트. 로그 `/data/nemesis/logs/k8np.install/install.log`.
- 빌드: `python tools/build_usb_install.py <apk> --version 0.4.5` → `private/nemesis-apk/` (usb/ 구성과 공유용 zip).
- 공유 zip `K8-nowplaying-0.4.5-USB.zip`(sha256 b10fad7c…, deliverables·구글 드라이브 루트 "K8-나우플레잉-0.4.5-USB설치.zip"):
  압축을 USB 루트에 풀면 `NEMESIS.data` + `packages/k8np.install-0.4.5.zip`. 기존 USB의 NEMESIS.data는 덮어쓰이니 먼저 백업.
- 미확인: 네메시스 postinstall 안에서 pm install이 되는지(ADB `nemesis root -c`로는 됨). 안 되면 네메시스 앱 목록에서 실행해 결과 확인.
