package com.infolink.collector.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Mock API 컨트롤러 — GIMS 내부 시스템 시뮬레이션
 * - 공통코드 API (LOOKUP용)
 * - API 키 목록 (인증키 조회용)
 */
@RestController
@RequestMapping("/mock")
@Slf4j
public class MockApiController {

    /**
     * Mock 공통코드 API — GIMS 내부 API 시뮬레이션
     * 실제 GIMS: GET /api/common/select/{groupCode}
     */
    @GetMapping("/common/select/{groupCode}")
    public Map<String, Object> getCommonCode(@PathVariable String groupCode) {

        log.info("Mock 공통코드 API 호출: groupCode={}", groupCode);

        List<Map<String, Object>> commonList = new ArrayList<>();

        if ("NGW_0118".equals(groupCode)) {
            // 언론사 코드
            String[][] pressData = {
                    {"ajunews.com", "아주경제"},
                    {"asiatoday.co.kr", "아시아투데이"},
                    {"busan.com", "부산일보"},
                    {"ccdailynews.com", "충청일보"},
                    {"cctimes.kr", "충청타임즈"},
                    {"cctoday.co.kr", "충청투데이"},
                    {"chosun.com", "조선일보"},
                    {"daejonilbo.com", "대전일보"},
                    {"dnews.co.kr", "대한경제"},
                    {"domin.co.kr", "전북도민일보"},
                    {"donga.com", "동아일보(신동아)"},
                    {"dt.co.kr", "디지털타임스"},
                    {"edaily.co.kr", "이데일리"},
                    {"etnews.com", "전자신문"},
                    {"etoday.co.kr", "이투데이"},
                    {"fnnews.com", "파이낸셜뉴스"},
                    {"hani.co.kr", "한겨레"},
                    {"hankookilbo.com", "한국일보"},
                    {"hankyung.com", "한국경제"},
                    {"idaegu.co.kr", "대구신문"},
                    {"idaegu.com", "대구일보"},
                    {"idomin.com", "경남도민일보"},
                    {"ihalla.com", "한라일보"},
                    {"imnews.imbc.com", "MBC"},
                    {"incheonilbo.com", "인천일보"},
                    {"inews365.com", "충북일보(아이뉴스365)"},
                    {"iusm.co.kr", "울산매일"},
                    {"jbnews.com", "중부매일(중부뉴스)"},
                    {"jemin.com", "제민일보"},
                    {"jjan.kr", "전북일보"},
                    {"jnilbo.com", "전남일보"},
                    {"joongang.co.kr", "중앙일보"},
                    {"joongboo.com", "중부일보"},
                    {"joongdo.co.kr", "중도일보"},
                    {"kado.net", "강원도민일보"},
                    {"khan.co.kr", "경향신문"},
                    {"kjdaily.com", "광주매일신문"},
                    {"knnews.co.kr", "경남신문"},
                    {"kookje.co.kr", "국제신문"},
                    {"ksilbo.co.kr", "경상일보"},
                    {"kwangju.co.kr", "광주일보"},
                    {"kwnews.co.kr", "강원일보"},
                    {"kyeonggi.com", "경기일보"},
                    {"kyeongin.com", "경인일보"},
                    {"kyongbuk.co.kr", "경북일보"},
                    {"mdilbo.com", "무등일보"},
                    {"mk.co.kr", "매일경제"},
                    {"moneys.mt.co.kr", "머니투데이"},
                    {"munhwa.com", "문화일보"},
                    {"naeil.com", "내일신문"},
                    {"news.heraldcorp.com", "헤럴드경제"},
                    {"news.imaeil.com", "매일신문"},
                    {"news.kbs.co.kr", "KBS"},
                    {"news.kmib.co.kr", "국민일보"},
                    {"news.sbs.co.kr", "SBS"},
                    {"obsnews.co.kr", "OBS"},
                    {"sedaily.com", "서울경제"},
                    {"segye.com", "세계일보"},
                    {"seoul.co.kr", "서울신문"},
                    {"view.asiae.co.kr", "아시아경제"},
                    {"viva100.com", "브릿지경제"},
                    {"yeongnam.com", "영남일보"},
                    {"yna.co.kr", "연합뉴스"},
                    {"yonhapnewstv.co.kr", "연합뉴스tv"},
                    {"ytn.co.kr", "YTN"},
            };

            int id = 1;
            for (String[] row : pressData) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", id++);
                item.put("groupId", 15);
                item.put("detailCode", row[0]);
                item.put("detailCodeName", row[1]);
                item.put("shortCode", null);
                item.put("useYn", "Y");
                item.put("createdBy", "시스템관리자");
                item.put("createdAt", "2025-09-04 12:20:41");
                item.put("updatedAt", null);
                commonList.add(item);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("common", commonList);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "요청이 성공적으로 처리되었습니다.");
        response.put("data", data);
        return response;
    }

    // ===================== 안양시 이용량 Mock =====================

    /**
     * Mock 안양시 시설정보 API
     */
    @GetMapping("/anyang/fac")
    public Map<String, Object> getAnyangFac() {
        log.info("Mock 안양 시설정보 API 호출");

        List<Map<String, Object>> items = new ArrayList<>();
        Object[][] facData = {
                {"1", "하이텍", "1041-001-040-0800-90-2", "삼원프라자호텔", "2", "2026-03-20 10:00:00", "0", "NL1122800284", "37.398216", "126.922561", "22-330073", "32", "0", null, "2026-03-24 08:00:00", "안양 1동 장내로 139번길 7", "450-06-1239072110", "86-470004-845212-2"},
                {"1", "하이텍", "1041-001-040-0800-91-3", "안양시청", "2", "2026-03-18 09:30:00", "0", "NL1122800301", "37.394015", "126.956764", "22-330088", "50", "0", null, "2026-03-24 07:55:00", "안양 만안구 안양로 345", "450-06-1239072222", "86-470004-845213-3"},
                {"1", "하이텍", "1041-001-040-0800-92-4", "범계역 지하상가", "2", "2026-03-15 11:00:00", "0", "NL1122800315", "37.389765", "126.951234", "22-330092", "40", "0", null, "2026-03-24 08:10:00", "동안구 시민대로 180", "450-06-1239072333", "86-470004-845214-4"},
                {"1", "하이텍", "1041-001-040-0800-93-5", "평촌학원가 빌딩", "2", "2026-03-10 14:00:00", "0", "NL1122800322", "37.392100", "126.958800", "22-330101", "25", "0", null, "2026-03-24 08:05:00", "동안구 평촌대로 223번길 12", "450-06-1239072444", "86-470004-845215-5"},
                {"1", "하이텍", "1041-001-040-0800-94-6", "안양역 환승주차장", "2", "2026-03-05 08:00:00", "0", "NL1122800330", "37.401234", "126.923456", "22-330115", "32", "0", null, "2026-03-24 07:50:00", "만안구 안양역로 15", "450-06-1239072555", "86-470004-845216-6"},
        };

        String[] keys = {"company_cd", "company_nm", "account_no", "account_nm", "status_device", "connect_dtm", "state_display", "device_sn", "gps_latitude", "gps_longitude", "meter_sn", "caliber_cd", "mt_down", "mt_down_dtm", "mt_last_dtm", "full_addr", "cdma_no", "nwk"};
        for (Object[] row : facData) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (int j = 0; j < keys.length; j++) {
                item.put(keys[j], row[j]);
            }
            items.add(item);
        }

        return Map.of("data", items);
    }

    /**
     * Mock 안양시 이용량 API
     */
    @GetMapping("/anyang/data")
    public Map<String, Object> getAnyangData() {
        log.info("Mock 안양 이용량 API 호출");

        List<Map<String, Object>> items = new ArrayList<>();
        Object[][] dataRows = {
                {"1041-001-040-0800-90-2", "2026-03-24 08:00:00", 4869, 0, "0", 35, "0", "0", "0", "0", "0", "2026-03-24 08:05:00", 141707364, 4869, 2},
                {"1041-001-040-0800-90-2", "2026-03-23 08:00:00", 4867, 0, "0", 36, "0", "0", "0", "0", "0", "2026-03-23 08:04:00", 141707200, 4867, 3},
                {"1041-001-040-0800-91-3", "2026-03-24 07:55:00", 12050, 0, "0", 40, "0", "0", "0", "0", "0", "2026-03-24 08:00:00", 141707365, 12050, 15},
                {"1041-001-040-0800-91-3", "2026-03-23 08:00:00", 12035, 0, "0", 41, "0", "0", "0", "0", "0", "2026-03-23 08:03:00", 141707201, 12035, 12},
                {"1041-001-040-0800-92-4", "2026-03-24 08:10:00", 8320, 0, "0", 32, "0", "0", "0", "0", "0", "2026-03-24 08:15:00", 141707366, 8320, 5},
                {"1041-001-040-0800-93-5", "2026-03-24 08:05:00", 2100, 0, "0", 45, "0", "0", "0", "0", "0", "2026-03-24 08:10:00", 141707367, 2100, 1},
                {"1041-001-040-0800-94-6", "2026-03-24 07:50:00", 6540, 0, "0", 28, "0", "0", "0", "0", "0", "2026-03-24 07:55:00", 141707368, 6540, 8},
                {"1041-001-040-0800-92-4", "2026-03-23 08:00:00", 8315, 0, "0", 33, "0", "0", "0", "0", "0", "2026-03-23 08:05:00", 141707202, 8315, 4},
                {"1041-001-040-0800-93-5", "2026-03-23 08:00:00", 2099, 0, "0", 46, "0", "0", "0", "0", "0", "2026-03-23 08:04:00", 141707203, 2099, 1},
                {"1041-001-040-0800-94-6", "2026-03-23 08:00:00", 6532, 0, "0", 29, "0", "0", "0", "0", "0", "2026-03-23 08:03:00", 141707204, 6532, 7},
        };

        String[] keys = {"account_no", "meter_dtm", "value", "digits", "leak_state", "term_batt", "m_low_batt", "m_leak", "m_over_load", "m_reverse", "m_not_use", "db_in_dtm", "db_in_seq", "last_meter_value", "useqty"};
        for (Object[] row : dataRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (int j = 0; j < keys.length; j++) {
                item.put(keys[j], row[j]);
            }
            items.add(item);
        }

        return Map.of("data", items);
    }

    // ===================== 제주 보조관측망 Mock =====================

    /**
     * Mock 제주 관측점 마스터 API
     * 실제: POST http://water.jeju.go.kr/obsvsystem/rest/selectObsv.json
     * 파라미터 없이 전체 조회, 응답: { "data": [...] }
     */
    @PostMapping("/jeju/obsv")
    public Map<String, Object> getJejuObsv() {
        log.info("Mock 제주 관측점 마스터 API 호출");

        List<Map<String, Object>> items = new ArrayList<>();
        // siteCode, siteName, wDtlSrv, wDrinkYn, wDevlocCi, wDevlocDo, wDevlocLi, wDevlocBunji, wDevlocHo, wX, wY, siteLitd, siteLttd, wCsiDia, wElev, wNatWtlv
        Object[][] obsvData = {
                {"SC001", "한림관측소", "상수도", "음용", "제주시", "한림읍", "한림리", "100", "", "160000.0", "250000.0", "", "", "200", "45.2", "3.5"},
                {"SC002", "서귀포관측소", "농업용", "비음용", "서귀포시", "중문동", "", "200", "", "170000.0", "240000.0", "", "", "150", "12.0", "5.2"},
                {"SC003", "조천관측소", "상수도(마을)", "음용", "제주시", "조천읍", "조천리", "50", "", "165000.0", "255000.0", "", "", "250", "78.5", "2.1"},
                {"SC004", "남원관측소", "기타", "비음용", "서귀포시", "남원읍", "남원리", "30", "", "175000.0", "235000.0", "", "", "100", "120.0", "8.3"},
                {"SC005", "애월관측소", "농업용(축산)", "비음용", "제주시", "애월읍", "애월리", "15", "", "155000.0", "252000.0", "", "", "180", "55.0", "4.0"},
                {"SC006", "대정관측소", "상수도", "음용", "서귀포시", "대정읍", "하모리", "88", "", "150000.0", "238000.0", "", "", "220", "30.5", "6.1"},
                {"SC007", "구좌관측소", "농업용", "비음용", "제주시", "구좌읍", "세화리", "22", "", "172000.0", "258000.0", "", "", "160", "92.0", "1.8"},
                {"SC008", "성산관측소", "기타(온천)", "비음용", "서귀포시", "성산읍", "성산리", "10", "", "180000.0", "245000.0", "", "", "300", "15.0", "12.5"},
                {"SC009", "한경관측소", "상수도", "음용", "제주시", "한경면", "고산리", "5", "", "148000.0", "248000.0", "", "", "190", "60.0", "3.0"},
                {"SC010", "표선관측소", "농업용(수산)", "비음용", "서귀포시", "표선면", "표선리", "7", "", "177000.0", "237000.0", "", "", "140", "8.0", "7.7"},
                // siteLitd가 이미 있는 케이스 (좌표변환 스킵)
                {"SC011", "제주시청관측소", "상수도", "음용", "제주시", "이도이동", "", "1", "", "162000.0", "253000.0", "126.531", "33.499", "200", "50.0", "2.5"},
                // wX가 비어있는 케이스 (좌표변환 스킵)
                {"SC012", "우도관측소", "기타", "비음용", "제주시", "우도면", "연평리", "1", "", "", "", "", "", "100", "5.0", ""},
        };

        String[] keys = {"siteCode", "siteName", "wDtlSrv", "wDrinkYn", "wDevlocCi", "wDevlocDo",
                "wDevlocLi", "wDevlocBunji", "wDevlocHo", "wX", "wY", "siteLitd", "siteLttd",
                "wCsiDia", "wElev", "wNatWtlv"};
        for (Object[] row : obsvData) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (int j = 0; j < keys.length; j++) {
                item.put(keys[j], row[j]);
            }
            items.add(item);
        }

        return Map.of("data", items);
    }

    /**
     * Mock 제주 수위 관측 데이터 API
     * 실제: POST http://water.jeju.go.kr/obsvsystem/rest/selectObsvData.json
     * 파라미터: site_code, data_time
     * 응답: { "data": [ { siteCode, siteName, dataTime, gl, scond, wTemp, mSn }, ... ] }
     */
    @PostMapping("/jeju/obsv-data")
    public Map<String, Object> getJejuObsvData(@RequestParam(required = false) String site_code,
                                                @RequestParam(required = false) String data_time) {
        log.info("Mock 제주 수위 관측 API 호출: site_code={}, data_time={}", site_code, data_time);

        List<Map<String, Object>> items = new ArrayList<>();

        // site_code별 Mock 데이터 (센서 S11=수위전용, S21/S22=다심도)
        Map<String, Object[][]> mockData = new LinkedHashMap<>();
        mockData.put("SC001", new Object[][]{
                {"SC001", "한림관측소", "20260330010000", "3.52", "248", "15.1", "S11"},
                {"SC001", "한림관측소", "20260330020000", "3.48", "250", "15.0", "S11"},
                {"SC001", "한림관측소", "20260330010000", "4.10", "260", "14.8", "S21"},
        });
        mockData.put("SC002", new Object[][]{
                {"SC002", "서귀포관측소", "20260330010000", "5.20", "310", "16.3", "S11"},
                {"SC002", "서귀포관측소", "20260330020000", "5.18", "312", "16.2", "S11"},
        });
        mockData.put("SC003", new Object[][]{
                {"SC003", "조천관측소", "20260330010000", "2.15", "180", "14.5", "S11"},
                {"SC003", "조천관측소", "20260330020000", "2.12", "182", "14.4", "S11"},
                {"SC003", "조천관측소", "20260330010000", "3.80", "200", "14.0", "S21"},
                {"SC003", "조천관측소", "20260330010000", "5.50", "220", "13.5", "S22"},
        });
        mockData.put("SC004", new Object[][]{
                {"SC004", "남원관측소", "20260330010000", "8.30", "420", "17.1", "S11"},
        });
        mockData.put("SC005", new Object[][]{
                {"SC005", "애월관측소", "20260330010000", "4.05", "230", "15.5", "S11"},
                {"SC005", "애월관측소", "20260330020000", "4.02", "232", "15.4", "S11"},
        });

        String[] keys = {"siteCode", "siteName", "dataTime", "gl", "scond", "wTemp", "mSn"};

        // site_code가 있으면 해당 것만, 없으면 전체
        if (site_code != null && !site_code.isEmpty()) {
            Object[][] rows = mockData.get(site_code);
            if (rows != null) {
                for (Object[] row : rows) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    for (int j = 0; j < keys.length; j++) item.put(keys[j], row[j]);
                    items.add(item);
                }
            }
        } else {
            for (Object[][] rows : mockData.values()) {
                for (Object[] row : rows) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    for (int j = 0; j < keys.length; j++) item.put(keys[j], row[j]);
                    items.add(item);
                }
            }
        }

        return Map.of("data", items);
    }

    // ===================== 제주 이용시설 Mock =====================

    /**
     * Mock 제주 이용시설 API — selectJejuUse.json 시뮬레이션
     * 레거시: RgetstgmsProgram / yearProgram
     * 페이징: totalCount + data 배열, 1000건 단위
     */
    @PostMapping("/jeju/facility")
    public Map<String, Object> jejuFacility(
            @RequestParam(value = "reg_year", defaultValue = "2025") String regYear,
            @RequestParam(value = "page", required = false) Integer page) {

        log.info("Mock 제주 이용시설 API 호출: reg_year={}, page={}", regYear, page);

        List<Map<String, Object>> items = new ArrayList<>();

        // 다양한 코드값 커버 (wellSrvCode, wellDtlsrv_code, wellType, wellPublic, wellStatusCode, wellDrinkYn)
        Object[][] rows = {
                {"5001-001", "A1", "14", "permit", "pub", "01", "A0", "제주시", "한림읍", "", "123", "4", "2020-05-15", "", 150.5, 12.0, 100.0, 50.0, 200.0, 10.0, 3.5, 1.2, 5.0, 25.0, regYear, "1"},
                {"5001-002", "A2", "24", "report", "pri", "03", "A1", "서귀포시", "표선면", "1", "456-7", "2", "2019-03-20", "2025-08-10", 200.0, 15.0, 120.0, 60.0, 180.0, 8.0, 4.2, 1.5, 6.0, 30.0, regYear, "1"},
                {"5001-003", "A3", "25", "permit", "pub", "06", "A0", "제주시", "조천읍", "", "789", "1", "2018-11-01", "2024-12-20", 180.0, 10.0, 80.0, 40.0, 150.0, 12.0, 2.8, 0.8, 4.0, 20.0, regYear, "1"},
                {"5001-004", "A3", "26", "report", "pri", "01", "", "서귀포시", "남원읍", "", "321", "3", "2021-07-25", "", 160.0, 8.0, 90.0, 45.0, 170.0, 9.0, 3.0, 1.0, 5.5, 22.0, regYear, "1"},
                {"5001-005", "A1", "17", "permit", "pub", "01", "A1", "제주시", "애월읍", "1", "654-3", "5", "2017-01-10", "", 220.0, 20.0, 150.0, 70.0, 250.0, 15.0, 5.0, 2.0, 7.0, 35.0, regYear, "1"},
                {"5001-006", "A1", "18", "report", "pri", "01", "A0", "서귀포시", "대정읍", "", "987", "2", "2022-09-05", "", 130.0, 11.0, 110.0, 55.0, 190.0, 7.0, 3.8, 1.3, 4.5, 28.0, regYear, "1"},
                {"5001-007", "A1", "2", "permit", "pub", "01", "A1", "제주시", "구좌읍", "", "111", "1", "2016-04-20", "", 170.0, 13.0, 95.0, 48.0, 160.0, 11.0, 4.0, 1.1, 5.2, 24.0, regYear, "1"},
                {"5001-008", "A3", "27", "report", "pri", "01", "A0", "제주시", "한경면", "", "222", "3", "2023-02-28", "", 140.0, 9.0, 85.0, 42.0, 140.0, 6.0, 2.5, 0.9, 3.8, 18.0, regYear, "1"},
                {"5001-009", "A3", "28", "permit", "pub", "03", "A1", "서귀포시", "안덕면", "1", "333-1", "4", "2015-06-15", "2026-01-05", 190.0, 16.0, 130.0, 65.0, 220.0, 13.0, 4.5, 1.7, 6.5, 32.0, regYear, "1"},
                {"5001-010", "A4", "29", "permit", "pub", "01", "A0", "제주시", "우도면", "", "444", "2", "2020-12-01", "", 110.0, 7.0, 70.0, 35.0, 120.0, 5.0, 2.0, 0.6, 3.0, 15.0, regYear, "1"},
                {"5001-011", "A4", "40", "report", "pri", "06", "", "서귀포시", "성산읍", "", "555", "1", "2019-08-20", "2025-03-15", 145.0, 12.0, 100.0, 50.0, 180.0, 10.0, 3.2, 1.0, 4.8, 26.0, regYear, "1"},
                {"5001-012", "A1", "12", "permit", "pub", "01", "A1", "제주시", "이도동", "", "666", "5", "2024-01-15", "", 125.0, 14.0, 115.0, 58.0, 200.0, 8.0, 3.5, 1.4, 5.8, 29.0, regYear, "1"},
        };

        // EPSG:5186 좌표 (제주도 범위)
        double[][] coords = {
                {155000.0, 300000.0}, {160000.0, 280000.0}, {158000.0, 305000.0}, {162000.0, 285000.0},
                {153000.0, 298000.0}, {165000.0, 282000.0}, {157000.0, 307000.0}, {151000.0, 296000.0},
                {164000.0, 284000.0}, {159000.0, 310000.0}, {163000.0, 286000.0}, {156000.0, 302000.0},
        };

        for (int i = 0; i < rows.length; i++) {
            Object[] r = rows[i];
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("wellNo", r[0]);           // perm_nt_no
            item.put("wellSrvCode", r[1]);      // substring(1) → 용도코드
            item.put("wellDtlsrv_code", r[2]);  // 세부용도코드
            item.put("wellType", r[3]);          // permit/report
            item.put("wellPublic", r[4]);        // pub/pri
            item.put("wellStatusCode", r[5]);    // 01/03/06
            item.put("wellDrinkYn", r[6]);       // A0/A1/""
            item.put("countynm", r[7]);          // 제주시/서귀포시
            item.put("wellDong", r[8]);          // dvop_loc_regn_code 용
            item.put("wellSan", r[9]);           // dvop_loc_san
            item.put("wellBunji", r[10]);        // dvop_loc_bunji
            item.put("wellHo", r[11]);           // dvop_loc_ho
            item.put("wellFstPermitDt", r[12]);  // 최초허가일
            item.put("wellDealDt", r[13]);       // 처분일(양도/폐공)
            item.put("wellDepth", r[14]);        // dig_dph
            item.put("wellDiameter", r[15]);     // dig_diam
            item.put("fwPlanQty", r[16]);        // frw_pln_qua
            item.put("pumpHrp", r[17]);          // dyn_eqn_hrp
            item.put("pipeDiameter", r[18]);     // pipe_diam
            item.put("rwtCapacity", r[19]);      // rwt_cap
            item.put("natWtlv", r[20]);          // nat_wtlv
            item.put("stbWtlv", r[21]);          // stb_wtlv
            item.put("wellElev", r[22]);         // elev
            item.put("yearUseQty", r[23]);       // y_use_qua
            item.put("yyGbn", r[24]);            // yy_gbn (= reg_year)
            item.put("permYn", r[25]);           // perm_yn
            item.put("wellX", coords[i][0]);     // EPSG:5186 X
            item.put("wellY", coords[i][1]);     // EPSG:5186 Y
            items.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalCount", items.size());
        response.put("data", items);
        return response;
    }

    // ===================== 제주 수질검사 Mock =====================

    /**
     * Mock 제주 수질검사 API — selectSujil.json 시뮬레이션
     * 레거시: RgetnwaviProgram
     * 페이징 없음, data 배열 직접 반환
     */
    @PostMapping("/jeju/water-quality")
    public Map<String, Object> jejuWaterQuality(
            @RequestParam(value = "data_date", defaultValue = "2025") String dataDate) {

        log.info("Mock 제주 수질검사 API 호출: data_date={}", dataDate);

        List<Map<String, Object>> items = new ArrayList<>();

        // 같은 permissionNum에 여러 항목(itemName)이 오는 구조
        Object[][] rows = {
                {"6510-WQ-001", "JJ-2025-001", "음용수(원수)", "제주시 한림읍", "탁도(Turbidity)", "0.5", "2025/03/15 10:30:00"},
                {"6510-WQ-001", "JJ-2025-001", "음용수(원수)", "제주시 한림읍", "총대장균군", "불검출", "2025/03/15 10:30:00"},
                {"6510-WQ-001", "JJ-2025-001", "음용수(원수)", "제주시 한림읍", "일반세균", "2", "2025/03/15 10:30:00"},
                {"6510-WQ-001", "JJ-2025-001", "음용수(원수)", "제주시 한림읍", "암모니아성질소", "0.01", "2025/03/15 10:30:00"},
                {"6510-WQ-002", "JJ-2025-002", "생활용수", "서귀포시 표선면", "탁도(Turbidity)", "1.2", "2025/04/20 14:00:00"},
                {"6510-WQ-002", "JJ-2025-002", "생활용수", "서귀포시 표선면", "색도(Colority)", "3", "2025/04/20 14:00:00"},
                {"6510-WQ-002", "JJ-2025-002", "생활용수", "서귀포시 표선면", "불소(F)", "0.15", "2025/04/20 14:00:00"},
                {"6510-WQ-003", "JJ-2025-003", "음용수(원수)", "제주시 조천읍", "분원성대장균군", "불검출", "2025/05/10 09:15:00"},
                {"6510-WQ-003", "JJ-2025-003", "음용수(원수)", "제주시 조천읍", "망간(Mn)", "0.02", "2025/05/10 09:15:00"},
                {"6510-WQ-003", "JJ-2025-003", "음용수(원수)", "제주시 조천읍", "맛(Taste)", "무미", "2025/05/10 09:15:00"},
                {"6510-WQ-003", "JJ-2025-003", "음용수(원수)", "제주시 조천읍", "냄새(Odor)", "무취", "2025/05/10 09:15:00"},
                {"6510-WQ-004", "JJ-2025-004", "생활용수", "서귀포시 남원읍", "알루미늄(Al)", "0.05", "2025/06/05 11:45:00"},
                {"6510-WQ-004", "JJ-2025-004", "생활용수", "서귀포시 남원읍", "탁도(Turbidity)", "0.8", "2025/06/05 11:45:00"},
                {"6510-WQ-004", "JJ-2025-004", "생활용수", "서귀포시 남원읍", "총대장균군", "불검출", "2025/06/05 11:45:00"},
                {"6510-WQ-005", "JJ-2025-005", "음용수(원수)", "제주시 애월읍", "일반세균", "1", "2025/07/22 16:20:00"},
        };

        for (Object[] r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("permissionNum", r[0]);   // PERM_NT_NO
            item.put("acceptNum", r[1]);       // → qwIspSno (하이픈 뒤)
            item.put("codeNm", r[2]);          // 음용수(원수)→A / else→D
            item.put("checkAddress", r[3]);    // 제주시→6510000 / else→6520000
            item.put("itemName", r[4]);        // 한글→영문 매핑
            item.put("dataValue", r[5]);       // RT (검사결과값)
            item.put("dataIndate", r[6]);      // FIRST_REG_DTHR
            items.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", items);
        return response;
    }

    // ===================== GIMS 내부 시스템 Mock =====================

    /**
     * Mock API 키 목록 — GIMS 내부 API 시뮬레이션
     */
    @GetMapping("/api-keys")
    public Map<String, Object> getApiKeys() {

        log.info("Mock API 키 목록 호출");

        List<Map<String, Object>> keyList = new ArrayList<>();
        Object[][] keyData = {
                {1, "네이버 검색 Client-ID", "6ZMOvG6WUSN5P7l2D65H", "Y", "2026-12-31", "정상", 283},
                {2, "네이버 검색 Client-Secret", "cK2B2OMt5E", "Y", "2026-12-31", "정상", 283},
                {3, "공공데이터포털 인증키", "qaj/1MknxOAoegbjhf6jCDH8pSH4Pt8Je88U572wPHObU85DnuxJ/vmoBeNlry6ELaUkjgmXHz+EPWst7gZcOw==", "Y", "2026-12-31", "정상", 283},
        };

        for (Object[] row : keyData) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row[0]);
            item.put("serviceName", row[1]);
            item.put("apiKey", row[2]);
            item.put("useAt", row[3]);
            item.put("expiryDate", row[4]);
            item.put("expiryType", row[5]);
            item.put("author", "manager");
            item.put("createdAt", "2026-01-20 12:58:57");
            item.put("updatedAt", null);
            item.put("dday", row[6]);
            keyList.add(item);
        }

        Map<String, Object> apis = new LinkedHashMap<>();
        apis.put("content", keyList);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apis", apis);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "요청이 성공적으로 처리되었습니다.");
        response.put("data", data);
        return response;
    }
}
