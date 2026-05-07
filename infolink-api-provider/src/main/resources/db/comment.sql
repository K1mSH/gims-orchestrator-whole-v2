-- ========== 관리 테이블 ==========

-- api_prv_operation: 오퍼레이션
COMMENT ON TABLE api_prv_operation IS 'API제공 오퍼레이션';
COMMENT ON COLUMN api_prv_operation.id IS 'PK';
COMMENT ON COLUMN api_prv_operation.operation_id IS '오퍼레이션ID(URL경로)';
COMMENT ON COLUMN api_prv_operation.operation_name IS '오퍼레이션명';
COMMENT ON COLUMN api_prv_operation.description IS '설명';
COMMENT ON COLUMN api_prv_operation.datasource_id IS '대상데이터소스ID';
COMMENT ON COLUMN api_prv_operation.table_name IS '대상테이블명';
COMMENT ON COLUMN api_prv_operation.response_format IS '응답포맷(JSON/XML)';
COMMENT ON COLUMN api_prv_operation.page_size IS '기본페이지크기';
COMMENT ON COLUMN api_prv_operation.max_page_size IS '최대페이지크기';
COMMENT ON COLUMN api_prv_operation.order_by_column IS '정렬컬럼';
COMMENT ON COLUMN api_prv_operation.order_by_direction IS '정렬방향(ASC/DESC)';
COMMENT ON COLUMN api_prv_operation.is_published IS '활성여부(외부노출)';
COMMENT ON COLUMN api_prv_operation.is_active IS '활성여부';
COMMENT ON COLUMN api_prv_operation.created_at IS '생성일시';
COMMENT ON COLUMN api_prv_operation.updated_at IS '수정일시';

-- api_prv_operation_column: 제공컬럼
COMMENT ON TABLE api_prv_operation_column IS 'API제공 컬럼설정';
COMMENT ON COLUMN api_prv_operation_column.id IS 'PK';
COMMENT ON COLUMN api_prv_operation_column.operation_id IS '오퍼레이션FK';
COMMENT ON COLUMN api_prv_operation_column.column_name IS 'DB컬럼명';
COMMENT ON COLUMN api_prv_operation_column.alias_name IS '응답필드명(별칭)';
COMMENT ON COLUMN api_prv_operation_column.display_order IS '표시순서';
COMMENT ON COLUMN api_prv_operation_column.transform_type IS '가공타입(NONE/ROUND/DATE_FORMAT/COALESCE/SUBSTRING)';
COMMENT ON COLUMN api_prv_operation_column.transform_param IS '가공파라미터';

-- api_prv_operation_param: WHERE파라미터
COMMENT ON TABLE api_prv_operation_param IS 'API제공 WHERE파라미터';
COMMENT ON COLUMN api_prv_operation_param.id IS 'PK';
COMMENT ON COLUMN api_prv_operation_param.operation_id IS '오퍼레이션FK';
COMMENT ON COLUMN api_prv_operation_param.param_name IS '요청파라미터명';
COMMENT ON COLUMN api_prv_operation_param.column_name IS 'WHERE대상컬럼명';
COMMENT ON COLUMN api_prv_operation_param.operator IS '연산자(EQ/LIKE/GTE/LTE/IN/BETWEEN)';
COMMENT ON COLUMN api_prv_operation_param.is_required IS '필수여부';
COMMENT ON COLUMN api_prv_operation_param.default_value IS '기본값';
COMMENT ON COLUMN api_prv_operation_param.data_type IS '데이터타입(STRING/NUMBER/DATE)';
COMMENT ON COLUMN api_prv_operation_param.is_hidden IS '숨김여부(외부미노출,기본값고정)';

-- api_prv_call_history: 호출이력
COMMENT ON TABLE api_prv_call_history IS 'API제공 호출이력';
COMMENT ON COLUMN api_prv_call_history.id IS 'PK';
COMMENT ON COLUMN api_prv_call_history.operation_id IS '오퍼레이션FK';
COMMENT ON COLUMN api_prv_call_history.api_key IS '사용API키';
COMMENT ON COLUMN api_prv_call_history.client_ip IS '요청IP';
COMMENT ON COLUMN api_prv_call_history.request_params IS '요청파라미터(JSON)';
COMMENT ON COLUMN api_prv_call_history.response_count IS '응답건수';
COMMENT ON COLUMN api_prv_call_history.status IS '상태(SUCCESS/FAILED)';
COMMENT ON COLUMN api_prv_call_history.error_message IS '에러메시지';
COMMENT ON COLUMN api_prv_call_history.duration_ms IS '처리시간(ms)';
COMMENT ON COLUMN api_prv_call_history.called_at IS '호출시각';

-- ========== 제공용 테이블 ==========

-- api_prv_tm_gd000203: 공통가뭄상태
COMMENT ON TABLE api_prv_tm_gd000203 IS '공통가뭄상태';
COMMENT ON COLUMN api_prv_tm_gd000203.sn IS '일련번호';
COMMENT ON COLUMN api_prv_tm_gd000203.ctpv_nm IS '시도명';
COMMENT ON COLUMN api_prv_tm_gd000203.sgg_nm IS '시군구명';
COMMENT ON COLUMN api_prv_tm_gd000203.emd_nm IS '읍면동명';
COMMENT ON COLUMN api_prv_tm_gd000203.li_nm IS '리명';
COMMENT ON COLUMN api_prv_tm_gd000203.ppltn_cnt IS '인구수';
COMMENT ON COLUMN api_prv_tm_gd000203.lpcd_cn IS '지역특성내용';
COMMENT ON COLUMN api_prv_tm_gd000203.dmd_qnt_vl IS '수요량값';
COMMENT ON COLUMN api_prv_tm_gd000203.sply_psblqy_vl IS '공급가능량값';
COMMENT ON COLUMN api_prv_tm_gd000203.ovshrts_qnt_vl IS '부족량값';
COMMENT ON COLUMN api_prv_tm_gd000203.tot_pub_gwel_cnt IS '총공공관정수';
COMMENT ON COLUMN api_prv_tm_gd000203.use_pub_gwel_cnt IS '가용공공관정수';

-- api_prv_tm_gd110301: 수질측정망검사개요
COMMENT ON TABLE api_prv_tm_gd110301 IS '수질측정망검사개요';
COMMENT ON COLUMN api_prv_tm_gd110301.sn IS '일련번호';
COMMENT ON COLUMN api_prv_tm_gd110301.wq_insp_sn IS '수질검사일련번호';
COMMENT ON COLUMN api_prv_tm_gd110301.gwel_no IS '관정번호';
COMMENT ON COLUMN api_prv_tm_gd110301.exmn_yr IS '검사년도';
COMMENT ON COLUMN api_prv_tm_gd110301.cycl IS '차수';
COMMENT ON COLUMN api_prv_tm_gd110301.dph_clsf_cd IS '심도구분코드';
COMMENT ON COLUMN api_prv_tm_gd110301.dph_vl IS '심도값';
COMMENT ON COLUMN api_prv_tm_gd110301.wtsmp_ymd IS '시료채취일자';
COMMENT ON COLUMN api_prv_tm_gd110301.wq_insp_ymd IS '수질검사일자';
COMMENT ON COLUMN api_prv_tm_gd110301.data_inpt_ymd IS '자료입력일자';
COMMENT ON COLUMN api_prv_tm_gd110301.cfmtn_ymd IS '확인일자';
COMMENT ON COLUMN api_prv_tm_gd110301.frst_reg_dt IS '최초등록일시';
COMMENT ON COLUMN api_prv_tm_gd110301.last_chg_dt IS '최종변경일시';
COMMENT ON COLUMN api_prv_tm_gd110301.ugwtr_usg_cd IS '지하수용도코드';
COMMENT ON COLUMN api_prv_tm_gd110301.dkpp_yn IS '음용여부';
COMMENT ON COLUMN api_prv_tm_gd110301.ugwtr_wqmn_inpt_inst_cd IS '수질측정망입력기관코드';
COMMENT ON COLUMN api_prv_tm_gd110301.wq_insp_imps_rsn_cn IS '수질검사불가사유내용';
COMMENT ON COLUMN api_prv_tm_gd110301.ctpv_nm IS '시도명';
COMMENT ON COLUMN api_prv_tm_gd110301.sgg_nm IS '시군구명';
COMMENT ON COLUMN api_prv_tm_gd110301.emd_nm IS '읍면동명';
COMMENT ON COLUMN api_prv_tm_gd110301.li_nm IS '리명';
COMMENT ON COLUMN api_prv_tm_gd110301.addr IS '주소';
COMMENT ON COLUMN api_prv_tm_gd110301.pub_gwel_yn IS '공공관정여부';

-- api_prv_tm_gd110302: 수질측정망검사결과
COMMENT ON TABLE api_prv_tm_gd110302 IS '수질측정망검사결과';
COMMENT ON COLUMN api_prv_tm_gd110302.sn IS '일련번호';
COMMENT ON COLUMN api_prv_tm_gd110302.wq_insp_sn IS '수질검사일련번호';
COMMENT ON COLUMN api_prv_tm_gd110302.wq_insp_artcl_cd IS '수질검사항목코드';
COMMENT ON COLUMN api_prv_tm_gd110302.rslt_vl IS '결과값';

-- api_prv_tm_gd112002: 드림서비스 공공관정
COMMENT ON TABLE api_prv_tm_gd112002 IS '드림서비스 공공관정';
COMMENT ON COLUMN api_prv_tm_gd112002.sn IS '일련번호';
COMMENT ON COLUMN api_prv_tm_gd112002.link_trsm_sgg_cd IS '연계전송시군구코드';
COMMENT ON COLUMN api_prv_tm_gd112002.prmsn_dclr_no IS '허가신고번호';
COMMENT ON COLUMN api_prv_tm_gd112002.prmsn_dclr_frm_cd IS '허가신고형태코드';
COMMENT ON COLUMN api_prv_tm_gd112002.yr_se IS '연도구분';
COMMENT ON COLUMN api_prv_tm_gd112002.rgn_cd IS '지역코드';
COMMENT ON COLUMN api_prv_tm_gd112002.ctpv_nm IS '시도명';
COMMENT ON COLUMN api_prv_tm_gd112002.sgg_nm IS '시군구명';
COMMENT ON COLUMN api_prv_tm_gd112002.emd_nm IS '읍면동명';
COMMENT ON COLUMN api_prv_tm_gd112002.li_nm IS '리명';
COMMENT ON COLUMN api_prv_tm_gd112002.mtn IS '산';
COMMENT ON COLUMN api_prv_tm_gd112002.bnj IS '번지';
COMMENT ON COLUMN api_prv_tm_gd112002.ho IS '호';
COMMENT ON COLUMN api_prv_tm_gd112002.ugwtr_usg IS '지하수용도';
COMMENT ON COLUMN api_prv_tm_gd112002.ugwtr_dtl_usg_cd IS '지하수상세용도코드';
COMMENT ON COLUMN api_prv_tm_gd112002.dkpp_yn IS '음용여부';
COMMENT ON COLUMN api_prv_tm_gd112002.lat_dg IS '위도(도)';
COMMENT ON COLUMN api_prv_tm_gd112002.lat_mi IS '위도(분)';
COMMENT ON COLUMN api_prv_tm_gd112002.lat_ss IS '위도(초)';
COMMENT ON COLUMN api_prv_tm_gd112002.lot_dg IS '경도(도)';
COMMENT ON COLUMN api_prv_tm_gd112002.lot_mi IS '경도(분)';
COMMENT ON COLUMN api_prv_tm_gd112002.lot_ss IS '경도(초)';
COMMENT ON COLUMN api_prv_tm_gd112002.dph_vl IS '심도값';
COMMENT ON COLUMN api_prv_tm_gd112002.dgg_calbr IS '굴착구경';
COMMENT ON COLUMN api_prv_tm_gd112002.delp_dia IS '케이싱구경';
COMMENT ON COLUMN api_prv_tm_gd112002.pump_hrspw IS '펌프마력';
COMMENT ON COLUMN api_prv_tm_gd112002.wtrit_plan_qtr IS '취수계획량';
COMMENT ON COLUMN api_prv_tm_gd112002.wpmp_ablt IS '양수능력';
COMMENT ON COLUMN api_prv_tm_gd112002.yr_usqty IS '연간사용량';
COMMENT ON COLUMN api_prv_tm_gd112002.pub_prvtest_se IS '공공민간구분';
COMMENT ON COLUMN api_prv_tm_gd112002.wq_insp_ymd IS '수질검사일자';
COMMENT ON COLUMN api_prv_tm_gd112002.wq_insp_rslt IS '수질검사결과';
COMMENT ON COLUMN api_prv_tm_gd112002.pnu IS '필지고유번호';
COMMENT ON COLUMN api_prv_tm_gd112002.xcrd IS 'X좌표';
COMMENT ON COLUMN api_prv_tm_gd112002.ycrd IS 'Y좌표';
