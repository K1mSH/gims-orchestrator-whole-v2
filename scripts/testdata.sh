#!/bin/bash
# 외부 업체 DB 테스트 데이터 생성/삭제 스크립트
# PostgreSQL(29000): daejeon, bytek, chungnam, keunsan (4개)
# MySQL(29010): infoworld_local/seoul, hydronet_ara/idc/kyungnam/wonju (6개)
# 업체당 관측소 400개 (총 4000개), 관측데이터 = 400 x 24시간 = 9600건/일

PG_HOST="localhost"
PG_PORT="29000"
PG_USER="k1m"
export PGPASSWORD="1111"

MY_CONTAINER="gims_orchestrator_outer_mysql"
MY_USER="k1m"
MY_PASS="1111"

# 업체 목록: db명:접두사:case:시도:dbtype
COMPANIES=(
  "daejeon:DJ:lower:대전광역시:pg"
  "bytek:BT:lower:경기도:pg"
  "chungnam:CN:lower:충청남도:pg"
  "keunsan:KS:upper:경상남도:pg"
  "infoworld_local:IWL:lower:대전광역시:my"
  "infoworld_seoul:IWS:lower:서울특별시:my"
  "hydronet_ara:HNA:lower:인천광역시:my"
  "hydronet_idc:HNI:lower:서울특별시:my"
  "hydronet_kyungnam:HNK:lower:경상남도:my"
  "hydronet_wonju:HNW:lower:강원특별자치도:my"
)

STATIONS=400

# ============================================================
# DB별 실행 함수
# ============================================================
pg_cmd() {
  local db=$1; shift
  psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$db" "$@"
}

# MySQL: -e 옵션으로 간단한 쿼리
my_cmd() {
  local db=$1; shift
  docker exec "$MY_CONTAINER" mysql -u "$MY_USER" -p"$MY_PASS" "$db" "$@" 2>/dev/null
}

# MySQL: stdin 파이프로 긴 쿼리
my_pipe() {
  local db=$1
  docker exec -i "$MY_CONTAINER" mysql -u "$MY_USER" -p"$MY_PASS" "$db" 2>/dev/null
}

# ============================================================
# 업체별 시군구/읍면동
# ============================================================
get_sigungu_array() {
  case $1 in
    daejeon)           echo "유성구,서구,중구,대덕구,동구" ;;
    bytek)             echo "수원시,성남시,용인시,화성시,평택시" ;;
    infoworld_local)   echo "유성구,대덕구,서구,중구,동구" ;;
    infoworld_seoul)   echo "강남구,서초구,송파구,강동구,관악구" ;;
    chungnam)          echo "천안시,아산시,서산시,논산시,공주시" ;;
    keunsan)           echo "창원시,김해시,양산시,진주시,거제시" ;;
    hydronet_ara)      echo "서구,남동구,부평구,계양구,연수구" ;;
    hydronet_idc)      echo "금천구,구로구,영등포구,동작구,관악구" ;;
    hydronet_kyungnam) echo "창원시,통영시,사천시,밀양시,함안군" ;;
    hydronet_wonju)    echo "원주시,횡성군,영월군,평창군,정선군" ;;
  esac
}

get_dong_array() {
  case $1 in
    daejeon)           echo "전민동,둔산동,대흥동,신탄진동,판암동" ;;
    bytek)             echo "영통동,분당동,수지동,동탄동,평택동" ;;
    infoworld_local)   echo "궁동,대화동,갈마동,은행동,가오동" ;;
    infoworld_seoul)   echo "역삼동,서초동,잠실동,천호동,봉천동" ;;
    chungnam)          echo "성정동,온천동,동문동,연무동,금학동" ;;
    keunsan)           echo "성산구,내동,중앙동,칠암동,장승포동" ;;
    hydronet_ara)      echo "검단동,논현동,부평동,계산동,송도동" ;;
    hydronet_idc)      echo "가산동,구로동,여의동,상도동,신림동" ;;
    hydronet_kyungnam) echo "마산동,봉평동,용현동,내이동,가야읍" ;;
    hydronet_wonju)    echo "단계동,우산동,영월읍,대화면,고한읍" ;;
  esac
}

# ============================================================
# jewon 초기 데이터 생성 (업체당 400개)
# ============================================================
init_jewon() {
  echo "=== 관측소 제원 데이터 초기 생성 (업체당 ${STATIONS}개) ==="

  for entry in "${COMPANIES[@]}"; do
    IFS=':' read -r db prefix case_type sido dbtype <<< "$entry"
    echo -n "  $db [${dbtype}] (${prefix}-0001~$(printf '%04d' $STATIONS))... "

    local sigungu_csv=$(get_sigungu_array "$db")
    local dong_csv=$(get_dong_array "$db")
    IFS=',' read -ra SGU <<< "$sigungu_csv"
    IFS=',' read -ra DNG <<< "$dong_csv"

    if [ "$dbtype" = "pg" ]; then
      # PostgreSQL: generate_series 사용
      if [ "$case_type" = "upper" ]; then
        # keunsan: 대문자 컬럼 (quoted)
        pg_cmd "$db" -q -c "
INSERT INTO \"SEC_JEWON_VIEW\"
  (\"OBSV_CODE\",\"OBSV_NAME\",\"SIDO\",\"SIGUNGU\",\"UPMYUNDO\",\"RI\",\"BUNJI\",
   \"X\",\"Y\",\"PYOGO\",\"WELL\",\"GULDEP\",\"GULDIA\",\"CASING_HEIGHT\",\"INSDATE\",\"REGDATE\")
SELECT
  '${prefix}-' || LPAD(s::text, 4, '0'),
  '${sido} 관측소 ' || s || '호',
  '${sido}',
  (string_to_array('${sigungu_csv}', ','))[1 + (s % 5)],
  (string_to_array('${dong_csv}', ','))[1 + (s % 5)],
  '',
  (s % 200 + 1)::text || '-' || (s % 30 + 1)::text,
  ROUND((127.0 + (s % 400) * 0.005)::numeric, 6)::text,
  ROUND((35.5 + (s % 400) * 0.003)::numeric, 6)::text,
  ROUND((30.0 + (s % 80) * 1.2)::numeric, 1),
  1 + (s % 3),
  ROUND((15.0 + (s % 50) * 1.5)::numeric, 1),
  ROUND((0.10 + (s % 6) * 0.05)::numeric, 2),
  ROUND((0.40 + (s % 5) * 0.10)::numeric, 2),
  '2018-01-01'::date + (s % 1000),
  '2018-01-01'::date + (s % 500)
FROM generate_series(1, ${STATIONS}) s;
" 2>&1 | grep -q ERROR && echo "FAIL" || echo "OK"
      else
        # 소문자 컬럼 (unquoted)
        pg_cmd "$db" -q -c "
INSERT INTO sec_jewon_view
  (obsv_code, obsv_name, sido, sigungu, upmyundo, ri, bunji,
   x, y, pyogo, well, guldep, guldia, casing_height, insdate, regdate)
SELECT
  '${prefix}-' || LPAD(s::text, 4, '0'),
  '${sido} 관측소 ' || s || '호',
  '${sido}',
  (string_to_array('${sigungu_csv}', ','))[1 + (s % 5)],
  (string_to_array('${dong_csv}', ','))[1 + (s % 5)],
  '',
  (s % 200 + 1)::text || '-' || (s % 30 + 1)::text,
  ROUND((127.0 + (s % 400) * 0.005)::numeric, 6)::text,
  ROUND((35.5 + (s % 400) * 0.003)::numeric, 6)::text,
  ROUND((30.0 + (s % 80) * 1.2)::numeric, 1),
  1 + (s % 3),
  ROUND((15.0 + (s % 50) * 1.5)::numeric, 1),
  ROUND((0.10 + (s % 6) * 0.05)::numeric, 2),
  ROUND((0.40 + (s % 5) * 0.10)::numeric, 2),
  '2018-01-01'::date + (s % 1000),
  '2018-01-01'::date + (s % 500)
FROM generate_series(1, ${STATIONS}) s;
" 2>&1 | grep -q ERROR && echo "FAIL" || echo "OK"
      fi

    else
      # MySQL: Recursive CTE 사용 (DELIMITER 문제 회피)
      echo "
INSERT INTO sec_jewon_view
  (obsv_code, obsv_name, sido, sigungu, upmyundo, ri, bunji,
   x, y, pyogo, well, guldep, guldia, casing_height, insdate, regdate)
WITH RECURSIVE seq AS (
  SELECT 1 AS s
  UNION ALL
  SELECT s + 1 FROM seq WHERE s < ${STATIONS}
)
SELECT
  CONCAT('${prefix}-', LPAD(s, 4, '0')),
  CONCAT('${sido} 관측소 ', s, '호'),
  '${sido}',
  ELT((s % 5) + 1, '${SGU[0]}','${SGU[1]}','${SGU[2]}','${SGU[3]}','${SGU[4]}'),
  ELT((s % 5) + 1, '${DNG[0]}','${DNG[1]}','${DNG[2]}','${DNG[3]}','${DNG[4]}'),
  '',
  CONCAT(s % 200 + 1, '-', s % 30 + 1),
  ROUND(127.0 + (s % 400) * 0.005, 6),
  ROUND(35.5 + (s % 400) * 0.003, 6),
  ROUND(30.0 + (s % 80) * 1.2, 1),
  1 + (s % 3),
  ROUND(15.0 + (s % 50) * 1.5, 1),
  ROUND(0.10 + (s % 6) * 0.05, 2),
  ROUND(0.40 + (s % 5) * 0.10, 2),
  DATE_ADD('2018-01-01', INTERVAL (s % 1000) DAY),
  DATE_ADD('2018-01-01', INTERVAL (s % 500) DAY)
FROM seq;
" | my_pipe "$db" && echo "OK" || echo "FAIL"
    fi
  done
  echo "=== 완료 (총 $((STATIONS * 10))개) ==="
}

# ============================================================
# obsvdata 생성 (날짜 지정, jewon의 모든 관측소 x 24시간)
# ============================================================
generate_obsvdata() {
  local target_date=$1
  if [ -z "$target_date" ]; then
    echo "사용법: $0 generate <YYYY-MM-DD>"
    exit 1
  fi

  echo "=== 관측 데이터 생성: ${target_date} ==="

  for entry in "${COMPANIES[@]}"; do
    IFS=':' read -r db prefix case_type sido dbtype <<< "$entry"
    echo -n "  $db [${dbtype}]... "

    if [ "$dbtype" = "pg" ]; then
      if [ "$case_type" = "upper" ]; then
        # keunsan: 대문자 테이블/컬럼
        pg_cmd "$db" -q -c "
INSERT INTO \"SEC_OBSVDATA_VIEW\"
  (\"OBSV_CODE\",\"OBSV_DATE\",\"OBSV_TIME\",\"GWDEP\",\"GWTEMP\",\"EC\",\"REMARK\")
SELECT
  j.\"OBSV_CODE\",
  '${target_date}'::date,
  make_time(h, 0, 0),
  ROUND((j.\"GULDEP\" * 0.15 + h * 0.02 + random() * 0.5)::numeric, 2),
  ROUND((14.0 + (h % 12) * 0.3 + random() * 1.0)::numeric, 1),
  ROUND((180 + j.\"WELL\" * 20 + h * 0.8 + random() * 15)::numeric, 0)::int,
  'auto'
FROM \"SEC_JEWON_VIEW\" j
CROSS JOIN generate_series(0, 23) h;
" 2>&1 | grep -q ERROR && { echo "FAIL"; continue; }
        local cnt=$(pg_cmd "$db" -t -q -c "SELECT count(*) FROM \"SEC_OBSVDATA_VIEW\" WHERE \"OBSV_DATE\" = '${target_date}';" | tr -d ' ')
        echo "OK (${cnt}건)"
      else
        # 소문자 테이블/컬럼
        pg_cmd "$db" -q -c "
INSERT INTO sec_obsvdata_view
  (obsv_code, obsv_date, obsv_time, gwdep, gwtemp, ec, remark)
SELECT
  j.obsv_code,
  '${target_date}'::date,
  make_time(h, 0, 0),
  ROUND((j.guldep * 0.15 + h * 0.02 + random() * 0.5)::numeric, 2),
  ROUND((14.0 + (h % 12) * 0.3 + random() * 1.0)::numeric, 1),
  ROUND((180 + j.well * 20 + h * 0.8 + random() * 15)::numeric, 0)::int,
  'auto'
FROM sec_jewon_view j
CROSS JOIN generate_series(0, 23) h;
" 2>&1 | grep -q ERROR && { echo "FAIL"; continue; }
        local cnt=$(pg_cmd "$db" -t -q -c "SELECT count(*) FROM sec_obsvdata_view WHERE obsv_date = '${target_date}';" | tr -d ' ')
        echo "OK (${cnt}건)"
      fi

    else
      # MySQL: CROSS JOIN으로 24시간 생성
      my_cmd "$db" -e "
INSERT INTO sec_obsvdata_view (obsv_code, obsv_date, obsv_time, gwdep, gwtemp, ec, remark)
SELECT
  j.obsv_code,
  '${target_date}',
  SEC_TO_TIME(h * 3600),
  ROUND(j.guldep * 0.15 + h * 0.02 + RAND() * 0.5, 2),
  ROUND(14.0 + (h % 12) * 0.3 + RAND() * 1.0, 1),
  ROUND(180 + j.well * 20 + h * 0.8 + RAND() * 15),
  'auto'
FROM sec_jewon_view j
CROSS JOIN (
  SELECT 0 AS h UNION SELECT 1 UNION SELECT 2 UNION SELECT 3
  UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7
  UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11
  UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15
  UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19
  UNION SELECT 20 UNION SELECT 21 UNION SELECT 22 UNION SELECT 23
) hours;
" && {
        local cnt=$(my_cmd "$db" -N -e "SELECT COUNT(*) FROM sec_obsvdata_view WHERE obsv_date = '${target_date}';")
        echo "OK (${cnt}건)"
      } || echo "FAIL"
    fi
  done
  echo "=== 완료 ==="
}

# ============================================================
# obsvdata 삭제 (관측데이터만)
# ============================================================
clear_obsvdata() {
  local target_date=$1
  if [ -z "$target_date" ]; then
    echo "=== 관측 데이터 전체 삭제 ==="
  else
    echo "=== 관측 데이터 삭제: ${target_date} ==="
  fi

  for entry in "${COMPANIES[@]}"; do
    IFS=':' read -r db prefix case_type sido dbtype <<< "$entry"
    echo -n "  $db [${dbtype}]... "

    if [ "$dbtype" = "pg" ]; then
      if [ "$case_type" = "upper" ]; then
        local cond=""
        [ -n "$target_date" ] && cond="WHERE \"OBSV_DATE\" = '${target_date}'"
        local deleted=$(pg_cmd "$db" -t -q -c "WITH d AS (DELETE FROM \"SEC_OBSVDATA_VIEW\" ${cond} RETURNING 1) SELECT count(*) FROM d;" 2>&1 | tr -d ' ')
      else
        local cond=""
        [ -n "$target_date" ] && cond="WHERE obsv_date = '${target_date}'"
        local deleted=$(pg_cmd "$db" -t -q -c "WITH d AS (DELETE FROM sec_obsvdata_view ${cond} RETURNING 1) SELECT count(*) FROM d;" 2>&1 | tr -d ' ')
      fi
      echo "삭제 ${deleted}건"
    else
      local cond=""
      [ -n "$target_date" ] && cond="WHERE obsv_date = '${target_date}'"
      local before=$(my_cmd "$db" -N -e "SELECT COUNT(*) FROM sec_obsvdata_view ${cond};")
      my_cmd "$db" -e "DELETE FROM sec_obsvdata_view ${cond};"
      echo "삭제 ${before}건"
    fi
  done
  echo "=== 완료 ==="
}

# ============================================================
# 전체 초기화 (jewon + obsvdata 삭제)
# ============================================================
reset_all() {
  echo "=== 전체 데이터 초기화 ==="

  for entry in "${COMPANIES[@]}"; do
    IFS=':' read -r db prefix case_type sido dbtype <<< "$entry"
    echo -n "  $db [${dbtype}]... "

    if [ "$dbtype" = "pg" ]; then
      if [ "$case_type" = "upper" ]; then
        pg_cmd "$db" -q -c "DELETE FROM \"SEC_OBSVDATA_VIEW\"; DELETE FROM \"SEC_JEWON_VIEW\";" 2>&1 | grep -q ERROR && echo "FAIL" || echo "OK"
      else
        pg_cmd "$db" -q -c "DELETE FROM sec_obsvdata_view; DELETE FROM sec_jewon_view;" 2>&1 | grep -q ERROR && echo "FAIL" || echo "OK"
      fi
    else
      my_cmd "$db" -e "DELETE FROM sec_obsvdata_view; DELETE FROM sec_jewon_view;" && echo "OK" || echo "FAIL"
    fi
  done
  echo "=== 완료 ==="
}

# ============================================================
# 현황 조회
# ============================================================
status() {
  echo "=== 데이터 현황 ==="
  printf "%-20s %5s %8s %10s\n" "DB" "type" "jewon" "obsvdata"
  printf "%-20s %5s %8s %10s\n" "---" "----" "-----" "--------"

  local total_j=0 total_o=0
  for entry in "${COMPANIES[@]}"; do
    IFS=':' read -r db prefix case_type sido dbtype <<< "$entry"

    if [ "$dbtype" = "pg" ]; then
      if [ "$case_type" = "upper" ]; then
        local jc=$(pg_cmd "$db" -t -q -c "SELECT count(*) FROM \"SEC_JEWON_VIEW\";" 2>&1 | tr -d ' ')
        local oc=$(pg_cmd "$db" -t -q -c "SELECT count(*) FROM \"SEC_OBSVDATA_VIEW\";" 2>&1 | tr -d ' ')
      else
        local jc=$(pg_cmd "$db" -t -q -c "SELECT count(*) FROM sec_jewon_view;" 2>&1 | tr -d ' ')
        local oc=$(pg_cmd "$db" -t -q -c "SELECT count(*) FROM sec_obsvdata_view;" 2>&1 | tr -d ' ')
      fi
    else
      local jc=$(my_cmd "$db" -N -e "SELECT COUNT(*) FROM sec_jewon_view;")
      local oc=$(my_cmd "$db" -N -e "SELECT COUNT(*) FROM sec_obsvdata_view;")
    fi

    printf "%-20s %5s %8s %10s\n" "$db" "$dbtype" "$jc" "$oc"
    total_j=$((total_j + jc))
    total_o=$((total_o + oc))
  done
  printf "%-20s %5s %8s %10s\n" "---" "----" "-----" "--------"
  printf "%-20s %5s %8s %10s\n" "TOTAL" "" "$total_j" "$total_o"
}

# ============================================================
case "${1}" in
  init)           init_jewon ;;
  generate)       generate_obsvdata "$2" ;;
  clear-obsvdata) clear_obsvdata "$2" ;;
  reset)          reset_all ;;
  status)         status ;;
  *)
    echo "사용법:"
    echo "  $0 init                    - 관측소 제원 초기 생성 (1회, 업체당 ${STATIONS}개)"
    echo "  $0 generate <YYYY-MM-DD>   - 관측 데이터 생성 (날짜별, 관측소×24시간)"
    echo "  $0 clear-obsvdata [날짜]   - 관측 데이터 삭제 (날짜 미지정시 전체)"
    echo "  $0 reset                   - 전체 초기화 (jewon + obsvdata 삭제)"
    echo "  $0 status                  - 데이터 현황 조회"
    ;;
esac
