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
