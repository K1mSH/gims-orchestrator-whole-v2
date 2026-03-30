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
