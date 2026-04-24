"""
Provide Operation 일괄 등록 스크립트 (2026-04-24)

작업:
1. 기존 A2(megokrApi/ngw09), A3(megokrApi/ngw09_01) 의 alias 오타 수정 (REL_TRANS_ → REL_TRANS)
2. A4 ~ B2 (9건) 신규 등록

기존 등록 패턴 준수:
- datasourceId: "api-provider"
- operationName: "{A|B}{번호}_{한글설명}"
- operationId: 레거시 URL 그대로
- paramName: DB 컬럼명 동일
"""
import json
import sys
import urllib.request
import urllib.error

BASE = "http://localhost:8095/api/manage"


def req(method, path, body=None):
    url = f"{BASE}{path}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    r = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        print(f"  ERR {method} {path}: {e.code} {e.read().decode('utf-8')[:200]}")
        raise


def save_columns(op_id, cols):
    payload = [
        {
            "columnName": c[0],
            "aliasName": c[1],
            "displayOrder": i,
            "transformType": c[2] if len(c) > 2 else "NONE",
            "transformParam": c[3] if len(c) > 3 else None,
        }
        for i, c in enumerate(cols)
    ]
    req("PUT", f"/operations/{op_id}/columns", payload)


def save_params(op_id, params):
    req("PUT", f"/operations/{op_id}/params", params)


def ensure_published(op_id):
    cur = req("GET", f"/operations/{op_id}")
    if not cur.get("isPublished"):
        req("PUT", f"/operations/{op_id}/publish")


def create_operation(spec):
    """spec: dict with operation, columns, params"""
    op_body = spec["operation"]
    print(f"▶ {op_body['operationId']} — {op_body['operationName']}")
    created = req("POST", "/operations", op_body)
    op_id = created["id"]
    print(f"  id={op_id}")
    save_columns(op_id, spec["columns"])
    print(f"  columns={len(spec['columns'])}")
    save_params(op_id, spec.get("params", []))
    print(f"  params={len(spec.get('params', []))}")
    if op_body.get("isPublished") and not created.get("isPublished"):
        req("PUT", f"/operations/{op_id}/publish")
    print(f"  ✓ 등록 완료")


# ============================================================
# A2/A3 alias 오타 수정 (REL_TRANS_ → REL_TRANS)
# ============================================================

A2_A3_COLS = [
    ("link_trsm_sgg_cd", "REL_TRANS"),       # ← 오타 수정
    ("prmsn_dclr_no", "PERM_NT_NO"),
    ("prmsn_dclr_frm_cd", "PERM_NT_FO"),
    ("yr_se", "YY_GBN"),
    ("rgn_cd", "REGNCODE"),
    ("ctpv_nm", "BRTC_NM"),
    ("sgg_nm", "SIGUN_NM"),
    ("emd_nm", "EMD_NM"),
    ("li_nm", "LI_NM"),
    ("mtn", "SAN"),
    ("bnj", "BUNJI"),
    ("ho", "HO"),
    ("ugwtr_usg", "UWATER_SRV"),
    ("ugwtr_dtl_usg_cd", "UWATER_DTL"),
    ("dkpp_yn", "UWATER_POT"),
    ("lat_dg", "LTTD_DG"),
    ("lat_mi", "LTTD_MINT"),
    ("lat_ss", "LTTD_SC"),
    ("lot_dg", "LITD_DG"),
    ("lot_mi", "LITD_MINT"),
    ("lot_ss", "LITD_SC"),
    ("dph_vl", "DPH"),
    ("dgg_calbr", "DIG_DIAM"),
    ("delp_dia", "PIPE_DIAM"),
    ("pump_hrspw", "PUMP_HRP"),
    ("wtrit_plan_qtr", "FRW_PLN_QU"),
    ("wpmp_ablt", "RWT_CAP"),
    ("yr_usqty", "Y_USE_QUA"),
    ("pub_prvtest_se", "PUB_PRI_GB"),
    ("wq_insp_ymd", "QW_ISP_YMD"),
    ("wq_insp_rslt", "QW_ISP_RT"),
    ("pnu", "PNU"),
    ("xcrd", "TMX_VALUE"),
    ("ycrd", "TMY_VALUE"),
]

# ============================================================
# A4: drought119Api/selectDrought119
# ============================================================

A4 = {
    "operation": {
        "operationId": "drought119Api/selectDrought119",
        "operationName": "A4_가뭄119 인허가관정",
        "datasourceId": "api-provider",
        "tableName": "api_prv_wt_dream_permwell_public_21033",
        "responseFormat": "JSON",
        "pageSize": 100,
        "maxPageSize": 1000,
        "orderByColumn": "objectid",
        "orderByDirection": "ASC",
        "isPublished": True,
        "isActive": True,
    },
    # 33개, 레거시 SQL SELECT 순서 그대로 (REL_TRANS_ 없음, PNU 없음)
    "columns": [
        ("objectid", "OBJECTID"),
        ("prmsn_dclr_no", "PERM_NT_NO"),
        ("prmsn_dclr_frm_cd", "PERM_NT_FO"),
        ("yr_se", "YY_GBN"),
        ("rgn_cd", "REGNCODE"),
        ("ctpv_nm", "BRTC_NM"),
        ("sgg_nm", "SIGUN_NM"),
        ("emd_nm", "EMD_NM"),
        ("li_nm", "LI_NM"),
        ("mtn", "SAN"),
        ("bnj", "BUNJI"),
        ("ho", "HO"),
        ("ugwtr_usg", "UWATER_SRV"),
        ("ugwtr_dtl_usg_cd", "UWATER_DTL"),
        ("dkpp_yn", "UWATER_POT"),
        ("lat_dg", "LTTD_DG"),
        ("lat_mi", "LTTD_MINT"),
        ("lat_ss", "LTTD_SC"),
        ("lot_dg", "LITD_DG"),
        ("lot_mi", "LITD_MINT"),
        ("lot_ss", "LITD_SC"),
        ("dph_vl", "DPH"),
        ("dgg_calbr", "DIG_DIAM"),
        ("delp_dia", "PIPE_DIAM"),
        ("pump_hrspw", "PUMP_HRP"),
        ("wtrit_plan_qtr", "FRW_PLN_QU"),
        ("wpmp_ablt", "RWT_CAP"),
        ("yr_usqty", "Y_USE_QUA"),
        ("pub_prvtest_se", "PUB_PRI_GB"),
        ("wq_insp_ymd", "QW_ISP_YMD"),
        ("wq_insp_rslt", "QW_ISP_RT"),
        ("xcrd", "TMX_VALUE"),
        ("ycrd", "TMY_VALUE"),
    ],
    "params": [],
}

# ============================================================
# A5: OPN data/groundwaterMonitoringNetworkService/* + surveyFacilitiesService/getBasicSurvey
# 모두 같은 타겟 api_prv_tm_gd120001, josacode 고정값만 다름
# ============================================================

A5_COLS = [
    ("ugwtr_exmn_cd", "JOSACODE"),
    ("brnch_nm", "JIGUNAME"),
    ("addr", "ADDR"),
    ("prmtv_data_nm", "SOURDATA", "COALESCE", "2011;"),
    ("prmtv_data_inst_nm", "SOUR_GOV", "COALESCE", "2011;"),
]

A5_COMMON_OP = {
    "datasourceId": "api-provider",
    "tableName": "api_prv_tm_gd120001",
    "responseFormat": "JSON",
    "pageSize": 100,
    "maxPageSize": 1000,
    "orderByColumn": "sn",
    "orderByDirection": "ASC",
    "isPublished": True,
    "isActive": True,
}


def a5_spec(op_id, name, josacode):
    return {
        "operation": {**A5_COMMON_OP, "operationId": op_id, "operationName": name},
        "columns": A5_COLS,
        "params": [
            {"paramName": "gennum", "columnName": "gwel_no", "operator": "EQ",
             "isRequired": True, "defaultValue": None, "dataType": "NUMBER", "isHidden": False},
            {"paramName": "josacode", "columnName": "ugwtr_exmn_cd", "operator": "EQ",
             "isRequired": False, "defaultValue": josacode, "dataType": "STRING", "isHidden": True},
        ],
    }


A5_1 = a5_spec("api/data/groundwaterMonitoringNetworkService/getNationalGroundwater",
               "A5_국가지하수 관측망 상세", "104")
A5_2 = a5_spec("api/data/groundwaterMonitoringNetworkService/getSeawaterPermeation",
               "A5_해수침투 관측망 상세", "112")
A5_3 = a5_spec("api/data/groundwaterMonitoringNetworkService/getRuralGroundwater",
               "A5_농촌지하수 관측망 상세", "113")
A5_4 = a5_spec("api/data/surveyFacilitiesService/getBasicSurvey",
               "A5_기초조사 상세", "215")

# ============================================================
# A6: OPN surveyFacilitiesService/getImpactInvestigation
# ============================================================

A6 = {
    "operation": {
        "operationId": "api/data/surveyFacilitiesService/getImpactInvestigation",
        "operationName": "A6_영향조사보고서 상세",
        "datasourceId": "api-provider",
        "tableName": "api_prv_tm_gd130001",
        "responseFormat": "JSON",
        "pageSize": 100,
        "maxPageSize": 1000,
        "orderByColumn": "sn",
        "orderByDirection": "ASC",
        "isPublished": True,
        "isActive": True,
    },
    "columns": [
        ("isvr_no", "YH_SNO"),
        ("isvr_nm", "RPT_TITLE"),
        ("prmtv_data_inst_nm", "SOUR_GOV"),
        ("data_crtr_yr", "PRESSYEAR"),
        ("pblcn_mm", "PRESSMONTH"),
        ("isvr_ccd", "GUBUN"),
        ("prlg_sn", "EXTEN_NUM"),
    ],
    "params": [
        {"paramName": "yh_sno", "columnName": "isvr_no", "operator": "EQ",
         "isRequired": True, "defaultValue": None, "dataType": "STRING", "isHidden": False},
    ],
}

# ============================================================
# A7: megokrApi/ngw04_01 (TMP_MEGOKR_API, 126 컬럼)
# sn → QLTWTR_INSPCT_SN + wt_* 125개 대문자 alias
# ============================================================

WT_COLS = [
    "wt_tot_col_cnts", "wt_tot_clf", "wt_fcl_cfs", "wt_esc_col", "wt_plb", "wt_flr",
    "wt_asn", "wt_sln", "wt_hdg", "wt_cya", "wt_amn_ntg", "wt_ntr_ntg", "wt_cdm",
    "wt_bor", "wt_chr", "wt_pen", "wt_dzn", "wt_prt", "wt_fnt", "wt_cbr",
    "wt_111_tce", "wt_pce", "wt_tce", "wt_dcm", "wt_bez", "wt_tle", "wt_ebz",
    "wt_csl", "wt_011_dre", "wt_ctc", "wt_012_dbr_003_crp", "wt_014_dox", "wt_hdn",
    "wt_ppc", "wt_sml", "wt_fev", "wt_cop", "wt_cmc", "wt_dtg", "wt_hid", "wt_zic",
    "wt_cri", "wt_evr", "wt_ste", "wt_mgn", "wt_tbd", "wt_sai", "wt_alm", "wt_ecd",
    "wt_ogp", "wt_006_chr", "wt_hid_lbt", "wt_tds", "wt_dso", "wt_orp", "wt_ehc",
    "wt_trt", "wt_ntr", "wt_kal", "wt_cal", "wt_mgs", "wt_clr", "wt_bbn", "wt_cai",
    "wt_nti", "wt_snt", "wt_brm", "wt_bru", "wt_atm", "wt_slc", "wt_ltu", "wt_mbd",
    "wt_vnd", "wt_gmn", "wt_cpe", "wt_nke", "wt_epn", "wt_pta", "wt_mst", "wt_crf",
    "wt_012_dre", "wt_toc", "wt_btr", "wt_mbe", "wt_ssc", "wt_sdm", "wt_smn",
    "wt_sgl", "wt_ahp", "wt_yne", "wt_ntn", "wt_coi", "wt_cpm", "wt_ctm",
    "wt_012_dcm", "wt_mtb", "wt_zcm", "wt_mgm", "wt_mbm", "wt_stm", "wt_bam",
    "wt_bsm", "wt_anm", "wt_nnm", "wt_frm", "wt_tcl", "wt_tcm", "wt_mtm", "wt_thm",
    "wt_ops", "wt_dro", "wt_grc", "wt_cbt", "wt_cbd", "wt_psp", "wt_amn", "wt_hgs",
    "wt_scd", "wt_ami", "wt_tbc", "wt_urn", "wt_rdn", "wt_fap", "wt_nai", "wt_wtl",
]
assert len(WT_COLS) == 125

A7 = {
    "operation": {
        "operationId": "megokrApi/ngw04_01",
        "operationName": "A7_수질검사결과 (TMP 페이징)",
        "datasourceId": "api-provider",
        "tableName": "api_prv_tmp_megokr_api",
        "responseFormat": "JSON",
        "pageSize": 10000,
        "maxPageSize": 10000,
        "orderByColumn": "sn",
        "orderByDirection": "ASC",
        "isPublished": True,
        "isActive": True,
    },
    "columns": [("sn", "QLTWTR_INSPCT_SN")] + [(c, c.upper()) for c in WT_COLS],
    "params": [
        {"paramName": "sn", "columnName": "sn", "operator": "GTE",
         "isRequired": False, "defaultValue": "0", "dataType": "NUMBER", "isHidden": False},
        {"paramName": "gennum", "columnName": "gennum", "operator": "EQ",
         "isRequired": False, "defaultValue": None, "dataType": "NUMBER", "isHidden": False},
    ],
}

# ============================================================
# B1: megokrApi/ngw03 — 수질검사개요 단건 (TM_GD110301)
# ============================================================

B1 = {
    "operation": {
        "operationId": "megokrApi/ngw03",
        "operationName": "B1_수질검사개요 (단건)",
        "datasourceId": "api-provider",
        "tableName": "api_prv_tm_gd110301",
        "responseFormat": "JSON",
        "pageSize": 100,
        "maxPageSize": 1000,
        "orderByColumn": "wq_insp_sn",
        "orderByDirection": "ASC",
        "isPublished": True,
        "isActive": True,
    },
    "columns": [
        ("wq_insp_sn", "QLTWTR_INSPCT_SN"),
        ("gwel_no", "GENNUM"),
        ("exmn_yr", "INVSTG_YEAR"),
        ("cycl", "ODR"),
        ("dph_clsf_cd", "DPH_CL_CODE"),
        ("dph_vl", "DPH_VALUE"),
        ("wtsmp_ymd", "WATSMP_DE"),
        ("wq_insp_ymd", "QLTWTR_INSPCT_DE"),
        ("data_inpt_ymd", "DTA_INPUT_DE"),
        ("cfmtn_ymd", "DCSN_DE"),
        ("frst_reg_dt", "FRST_REGIST_DT"),
        ("last_chg_dt", "LAST_CHANGE_DT"),
        ("ugwtr_usg_cd", "UGRWTR_PRPOS_CODE"),
        ("dkpp_yn", "DRNK_AT"),
        ("ugwtr_wqmn_inpt_inst_cd", "UGRWTR_WQN_INPUT_INSTT_CODE"),
        ("wq_insp_imps_rsn_cn", "QLTWTR_INSPCT_IMPRTY_RESN_CTNT"),
        ("ctpv_nm", "BRTC_NM"),
        ("sgg_nm", "SIGUN_NM"),
        ("emd_nm", "EMD_NM"),
        ("li_nm", "LI_NM"),
        ("addr", "ADDR"),
        ("pub_gwel_yn", "PUBWELL_AT"),
    ],
    "params": [
        {"paramName": "gennum", "columnName": "gwel_no", "operator": "EQ",
         "isRequired": True, "defaultValue": None, "dataType": "NUMBER", "isHidden": False},
        {"paramName": "invstg_year", "columnName": "exmn_yr", "operator": "LTE",
         "isRequired": False, "defaultValue": "2024", "dataType": "STRING", "isHidden": True},
    ],
}

# ============================================================
# B2: megokrApi/ngw03_01 — 수질검사개요 목록 (TMP 재활용)
# ============================================================

B2 = {
    "operation": {
        "operationId": "megokrApi/ngw03_01",
        "operationName": "B2_수질검사개요 (목록)",
        "datasourceId": "api-provider",
        "tableName": "api_prv_tmp_megokr_api",
        "responseFormat": "JSON",
        "pageSize": 10000,
        "maxPageSize": 10000,
        "orderByColumn": "sn",
        "orderByDirection": "ASC",
        "isPublished": True,
        "isActive": True,
    },
    # 22 메타 — api_prv_tmp_megokr_api 엔티티 컬럼 기준
    "columns": [
        ("qltwtr_inspct_sn", "QLTWTR_INSPCT_SN"),
        ("gennum", "GENNUM"),
        ("invstg_year", "INVSTG_YEAR"),
        ("odr", "ODR"),
        ("dph_cl_code", "DPH_CL_CODE"),
        ("dph_value", "DPH_VALUE"),
        ("watsmp_de", "WATSMP_DE"),
        ("qltwtr_inspct_de", "QLTWTR_INSPCT_DE"),
        ("dta_input_de", "DTA_INPUT_DE"),
        ("dcsn_de", "DCSN_DE"),
        ("frst_regist_dt", "FRST_REGIST_DT"),
        ("last_change_dt", "LAST_CHANGE_DT"),
        ("ugrwtr_prpos_code", "UGRWTR_PRPOS_CODE"),
        ("drnk_at", "DRNK_AT"),
        ("ugrwtr_wqn_input_instt_code", "UGRWTR_WQN_INPUT_INSTT_CODE"),
        ("qltwtr_inspct_imprty_resn_ctnt", "QLTWTR_INSPCT_IMPRTY_RESN_CTNT"),
        ("brtc_nm", "BRTC_NM"),
        ("sigun_nm", "SIGUN_NM"),
        ("emd_nm", "EMD_NM"),
        ("li_nm", "LI_NM"),
        ("addr", "ADDR"),
        ("pubwell_at", "PUBWELL_AT"),
    ],
    "params": [
        {"paramName": "sn", "columnName": "sn", "operator": "GTE",
         "isRequired": False, "defaultValue": "0", "dataType": "NUMBER", "isHidden": False},
        {"paramName": "gennum", "columnName": "gennum", "operator": "EQ",
         "isRequired": False, "defaultValue": None, "dataType": "NUMBER", "isHidden": False},
    ],
}

# ============================================================
# 실행
# ============================================================

def main():
    print("=" * 80)
    print("[1/3] 기존 A2/A3 alias 오타 수정 (REL_TRANS_ → REL_TRANS)")
    print("=" * 80)
    # 기존 operations 조회
    ops = req("GET", "/operations")
    id_by_opid = {o["operationId"]: o["id"] for o in ops}

    for opid in ("megokrApi/ngw09", "megokrApi/ngw09_01"):
        if opid in id_by_opid:
            oid = id_by_opid[opid]
            print(f"▶ {opid} (id={oid}) alias 정정")
            save_columns(oid, A2_A3_COLS)
            print("  ✓ 정정 완료")

    print()
    print("=" * 80)
    print("[2/3] 신규 9건 등록")
    print("=" * 80)

    targets = [A4, A5_1, A5_2, A5_3, A5_4, A6, A7, B1, B2]
    for spec in targets:
        opid = spec["operation"]["operationId"]
        if opid in id_by_opid:
            print(f"▶ {opid} (이미 존재 id={id_by_opid[opid]}) — 건너뜀")
            continue
        create_operation(spec)

    print()
    print("=" * 80)
    print("[3/3] 최종 상태")
    print("=" * 80)
    ops = req("GET", "/operations")
    ops.sort(key=lambda o: o["operationName"])
    for o in ops:
        print(f"  id={o['id']:>2} | {o['operationId']:<60} | {o['operationName']:<40} | cols={len(o['columns'])}, params={len(o['params'])}, pub={o['isPublished']}")
    print()
    print(f"총 {len(ops)}건")


if __name__ == "__main__":
    main()
