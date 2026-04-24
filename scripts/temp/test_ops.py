"""
12건 operation 전수 호출 테스트 (2026-04-24)
"""
import json
import sys
import urllib.request
import urllib.parse
import urllib.error

HOST = "http://localhost:8095"
API_KEY = "test-key-2026"

# (operationId, 설명, 쿼리파라미터 dict)
TESTS = [
    ("megokrApi/ngw08", "A1 공공관정 가뭄지원", {"ctpv_nm": "대전광역시"}),
    ("megokrApi/ngw09", "A2 공공관정 상세 단건", {"prmsn_dclr_no": "PWD-2020-0003"}),
    ("megokrApi/ngw09_01", "A3 공공관정 상세 목록", {"ctpv_nm": "충청남도"}),
    ("drought119Api/selectDrought119", "A4 가뭄119 인허가관정", {"pageSize": "2"}),
    ("groundwaterMonitoringNetworkService/getNationalGroundwater", "A5 국가지하수(josacode=104)", {"gennum": "10001"}),
    ("groundwaterMonitoringNetworkService/getSeawaterPermeation", "A5 해수침투(josacode=112)", {"gennum": "10002"}),
    ("groundwaterMonitoringNetworkService/getRuralGroundwater", "A5 농촌지하수(josacode=113)", {"gennum": "10003"}),
    ("surveyFacilitiesService/getBasicSurvey", "A5 기초조사(josacode=215)", {"gennum": "10003"}),
    ("surveyFacilitiesService/getImpactInvestigation", "A6 영향조사보고서", {"yh_sno": "Y-003"}),
    ("megokrApi/ngw04_01", "A7 수질검사결과 TMP 목록", {"pageSize": "2"}),
    ("megokrApi/ngw03", "B1 수질검사개요 단건", {"gennum": "10004"}),
    ("megokrApi/ngw03_01", "B2 수질검사개요 목록", {"pageSize": "3"}),
]


def call(opid, params):
    q = {"apiKey": API_KEY, **params}
    url = f"{HOST}/api/provide/{opid}?" + urllib.parse.urlencode(q)
    try:
        with urllib.request.urlopen(url, timeout=15) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, json.loads(raw)
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")[:300]
        return e.code, {"error": body}
    except Exception as e:
        return -1, {"error": str(e)}


def fmt_keys(row, n=5):
    if not row:
        return "[]"
    keys = list(row.keys())[:n]
    more = f"... (+{len(row)-n})" if len(row) > n else ""
    return ", ".join(keys) + more


def main():
    print(f"{'#':>2} | {'Operation':<60} | {'설명':<35} | HTTP | 건수 | 응답필드 샘플")
    print("-" * 180)
    for i, (opid, desc, params) in enumerate(TESTS, 1):
        status, body = call(opid, params)
        if status == 200:
            total = body.get("pagination", {}).get("totalCount", "?")
            first = body.get("data", [{}])[0] if body.get("data") else None
            sample = fmt_keys(first, 5) if first else "(0건)"
            mark = "✓" if isinstance(total, int) else "?"
        else:
            total = "-"
            err = body.get("error", "") if isinstance(body, dict) else str(body)[:80]
            sample = f"ERR: {err[:100]}"
            mark = "✗"
        print(f"{i:>2} | {opid:<60} | {desc:<35} | {status} {mark:<2}| {str(total):>4} | {sample}")


if __name__ == "__main__":
    main()
