#!/usr/bin/env bash
# 전화 수신 현장 시험 묶음 (Git Bash, PC에서 실행).
#   status                    연결·정차·odex md5·uptime 확인
#   set <split> <bt>          split: orig|patch, bt: orig|p4  → 파일 교체 후 재시작·확인 (바뀐 게 없으면 재시작 안 함)
#   call <label> [초]         차에서 화면·창·전원 상태를 2초마다 모은 뒤 private/logs/로 가져옴 (기본 90초)
#   summary <dir>             모은 결과를 시간별 한 줄로 요약
# 주소: K8_ADDR 환경변수 또는 private/k8-addr (한 줄, ip:5555). 저장소에 주소를 쓰지 않는다.
# 차량 시스템 파일을 바꾸므로 set은 사용자 승인 후에만 실행한다.
set -u
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.." || exit 1
ADB=${ADB:-$HOME/.local/share/android-sdk/platform-tools/adb.exe}
ADDR=${K8_ADDR:-$(tr -d '\r\n ' < private/k8-addr 2>/dev/null)}
[ -n "$ADDR" ] || { echo "주소 없음: K8_ADDR 또는 private/k8-addr"; exit 2; }
A="$ADB -s $ADDR"
BK=/data/nemesis/backup/k8np-split
SEO=/data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root

RSH_ORIG=55db33cf1a249331e6151427e056f4f3
RSH_PATCH=5f5da733b9438da8cc168ee79a855630
BT_ORIG=3cbade0283adc670514d77ce9ab59ee7
BT_P4=ed2b5dc6cd967406ed6cd606baf0facc

sh_() { $A shell "$@" </dev/null | tr -d '\r'; }
root() { sh_ "nemesis root -c '$1'"; }

connect() {
  [ "$($A get-state 2>/dev/null)" = device ] && return 0
  timeout 15 $ADB connect "$ADDR" >/dev/null 2>&1
  [ "$($A get-state 2>/dev/null)" = device ] || { echo "연결 실패: $ADDR (주소가 바뀌었으면 5555 포트 스캔 후 private/k8-addr 수정)"; exit 3; }
}

speed() { $A logcat -d </dev/null | grep -a "getSpeed_KMHUnit() =" | tail -1 | sed -n 's/.*getSpeed_KMHUnit() = *\([0-9.]*\).*/\1/p'; }

require_parked() {
  local s; s=$(speed)
  case "$s" in
    0|0.0) echo "정차 확인 (속도 0)";;
    "") echo "속도 로그 없음 → 정차를 확인할 수 없어 중단"; exit 4;;
    *) echo "주행 중 (속도 $s) → 중단"; exit 4;;
  esac
}

uptime_s() { sh_ cat /proc/uptime | sed 's/\..*//'; }
md5of() { sh_ md5 "$1" | awk '{print $1}'; }

status() {
  connect
  echo "uptime: $(uptime_s)s, 속도: $(speed)"
  echo "RightScreenHome:      $(md5of /system/app/RightScreenHome.odex)  (원본 $RSH_ORIG / 패치 $RSH_PATCH)"
  echo "HKMC_BtPhoneContacts: $(md5of /system/app/HKMC_BtPhoneContacts.odex)  (원본 $BT_ORIG / p4 $BT_P4)"
  sh_ dumpsys package io.github.k8nowplaying | grep -a versionName
  sh_ ps | grep -aE "splitscreen.right$|com.hkmc.bluetooth$|k8nowplaying$|mnsoft.navi$"
}

# place <차 경로> <원하는 md5> <원본이면 백업 경로, 아니면 PC 파일> <임시이름>
place() {
  local dst=$1 want=$2 src=$3 tmp=$4 cur
  cur=$(md5of "$dst")
  [ "$cur" = "$want" ] && { echo "$(basename "$dst"): 이미 원하는 상태"; return 1; }
  if [ "${src#/data/}" != "$src" ]; then
    [ "$(md5of "$src")" = "$want" ] || { echo "차 백업 md5 불일치: $src"; exit 5; }
  else
    [ "$(md5sum "$src" | awk '{print $1}')" = "$want" ] || { echo "PC 파일 md5 불일치: $src"; exit 5; }
    $A push "$src" /data/local/tmp/$tmp.odex >/dev/null || exit 5
    src=/data/local/tmp/$tmp.odex
  fi
  root "cp $src /system/app/.$tmp.new && chmod 644 /system/app/.$tmp.new && chown root:root /system/app/.$tmp.new && mv /system/app/.$tmp.new $dst && sync" >/dev/null
  sh_ rm -f /data/local/tmp/$tmp.odex
  cur=$(md5of "$dst")
  [ "$cur" = "$want" ] || { echo "교체 후 md5 불일치: $dst = $cur"; exit 5; }
  echo "$(basename "$dst"): 교체 완료 ($want)"
  return 0
}

reboot_hu() {
  local before after try
  for try in 1 2; do
    before=$(uptime_s)
    echo "재시작 요청 ($try회째, 이전 uptime ${before}s)"
    root "sh $SEO device reboot" >/dev/null
    sleep 45
    for i in $(seq 1 40); do
      timeout 8 $ADB connect "$ADDR" >/dev/null 2>&1
      [ "$(sh_ getprop sys.boot_completed 2>/dev/null)" = 1 ] && break
      sleep 10
    done
    sleep 15
    after=$(uptime_s)
    if [ -n "$after" ] && [ "$after" -lt "$before" ]; then
      echo "재시작 확인 (uptime ${after}s)"; return 0
    fi
    echo "재시작 안 됨 (uptime ${after:-?}s)"
  done
  echo "재시작이 두 번 조용히 실패 → 시동 OFF/ON으로 헤드유닛을 직접 다시 켠 뒤 status로 확인"
  exit 6
}

setstate() {
  local s=$1 b=$2 changed=0 want_rsh want_bt src_rsh src_bt
  case "$s" in orig) want_rsh=$RSH_ORIG; src_rsh=$BK/RightScreenHome.odex.orig;;
                patch) want_rsh=$RSH_PATCH; src_rsh=private/splitpatch/RightScreenHome.odex.patched;;
                *) echo "split은 orig|patch"; exit 2;; esac
  case "$b" in orig) want_bt=$BT_ORIG; src_bt=$BK/HKMC_BtPhoneContacts.odex.orig;;
                p4) want_bt=$BT_P4; src_bt=private/btpatch/HKMC_BtPhoneContacts.odex.patched4;;
                *) echo "bt는 orig|p4"; exit 2;; esac
  connect; require_parked
  place /system/app/RightScreenHome.odex "$want_rsh" "$src_rsh" rsh && changed=1
  place /system/app/HKMC_BtPhoneContacts.odex "$want_bt" "$src_bt" btp && changed=1
  [ $changed = 1 ] && reboot_hu || echo "바뀐 파일 없음 → 재시작 생략"
  status
}

CAP='d=/data/local/tmp/k8cap/$1; n=$2; mkdir -p $d
logcat -v time -f $d/logcat.txt & lp=$!
i=0
while [ $i -lt $n ]; do
  read up idle < /proc/uptime
  echo "== $i $up" >> $d/win.txt; dumpsys window windows >> $d/win.txt
  echo "== $i $up" >> $d/power.txt; dumpsys power >> $d/power.txt
  echo "== $i $up $(cat /sys/class/backlight/*/brightness 2>/dev/null)" >> $d/backlight.txt
  screencap -p $d/s_$i.png
  i=$((i+1)); sleep 1
done
kill $lp
echo done'

call() {
  local label=${1:?label} secs=${2:-90} n out
  n=$((secs / 2))
  connect
  local f; f=$(mktemp); echo "$CAP" > "$f"
  $A push "$f" /data/local/tmp/k8cap.sh >/dev/null; rm -f "$f"
  out=private/logs/field-$(date +%Y%m%d-%H%M)-$label
  mkdir -p "$out"
  status > "$out/status.txt" 2>&1
  echo "수집 시작 (${secs}초). 지금 다른 폰으로 전화를 걸어 주세요: 벨 15초 → 받기 → 15초 통화 → 끊기"
  root "sh /data/local/tmp/k8cap.sh $label $n" >/dev/null
  echo "수집 끝 → 가져오는 중"
  $A pull /data/local/tmp/k8cap/$label "$out" >/dev/null 2>&1
  root "rm -rf /data/local/tmp/k8cap/$label /data/local/tmp/k8cap.sh" >/dev/null
  echo "저장: $out"
  summary "$out"
}

summary() {
  local d=$1; [ -d "$d/$(basename "$d")" ] || true
  local w; w=$(find "$d" -name win.txt | head -1); [ -n "$w" ] || { echo "win.txt 없음"; return; }
  local p; p=$(dirname "$w")/power.txt
  local bl; bl=$(dirname "$w")/backlight.txt
  awk '
    FILENAME ~ /win.txt$/ && /^== / {i=$2; up[i]=$3}
    FILENAME ~ /win.txt$/ && /mCurrentFocus=/ {f=$0; sub(/.*mCurrentFocus=/,"",f); foc[i]=f}
    FILENAME ~ /power.txt$/ && /^== / {i=$2}
    FILENAME ~ /power.txt$/ && /mWakefulness=|mScreenOn=|mDisplayReady=/ {gsub(/^ +/,""); pw[i]=pw[i]" "$0}
    FILENAME ~ /backlight.txt$/ && /^== / {bl[$2]=$4}
    END {for (k=0; k in up; k++) printf "%3d %8ss bl=%-4s%s | %s\n", k, up[k], bl[k], pw[k], foc[k]}
  ' "$w" "$p" "$bl"
  echo "벨 동안 SCREEN_ON 반복 여부:"; grep -a -c "SCREEN_ON" "$(dirname "$w")/logcat.txt" 2>/dev/null
}

case "${1:-}" in
  status) status;;
  set) setstate "${2:-}" "${3:-}";;
  call) shift; call "$@";;
  summary) summary "${2:?dir}";;
  *) sed -n '2,8p' "$0"; exit 2;;
esac
