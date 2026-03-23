package com.infolink.collector.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 테스트용 Mock API 컨트롤러
 * - 외부 뉴스 API 시뮬레이션
 * - GIMS 내부 공통코드 API 시뮬레이션
 * dev 프로파일에서만 활성화
 */
@RestController
@RequestMapping("/mock")
@Slf4j
public class MockApiController {

    /**
     * Mock 뉴스 API — 외부 API 시뮬레이션
     */
    @GetMapping("/news")
    public Map<String, Object> getNews(
            @RequestParam(defaultValue = "20260323") String date,
            @RequestParam(defaultValue = "1") int page) {

        log.info("Mock 뉴스 API 호출: date={}, page={}", date, page);

        List<Map<String, Object>> items = new ArrayList<>();
        String[][] newsData = {
                {"지하수 관측소 신규 설치 계획 발표", "https://www.chosun.com/article/101", "2026-03-23"},
                {"충청권 지하수 수위 하락 경고", "https://www.daejonilbo.com/news/202", "2026-03-23"},
                {"전국 지하수 관측망 확대 추진", "https://news.kbs.co.kr/news/view/303", "2026-03-22"},
                {"지하수 오염 방지 대책 마련", "https://www.hani.co.kr/arti/society/404", "2026-03-22"},
                {"가뭄 대비 지하수 비상 급수 체계 구축", "https://www.donga.com/news/Society/505", "2026-03-21"},
                {"지역 상수도 지하수 의존도 조사 결과", "https://www.khan.co.kr/national/606", "2026-03-21"},
                {"지하수법 개정안 국회 통과", "https://www.seoul.co.kr/news/707", "2026-03-20"},
                {"관측소 장비 현대화 사업 착수", "https://news.sbs.co.kr/news/808", "2026-03-20"},
                {"농업용 지하수 이용 효율화 방안", "https://www.mk.co.kr/economy/909", "2026-03-19"},
                {"지하수 자원 보전 국제 심포지엄 개최", "https://www.yna.co.kr/view/1010", "2026-03-19"},
        };

        for (String[] row : newsData) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", row[0]);
            item.put("orignl_url", row[1]);
            item.put("reg_date", row[2]);
            items.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("resultCode", "00");
        response.put("totalCount", items.size());
        response.put("items", items);
        return response;
    }

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
}
