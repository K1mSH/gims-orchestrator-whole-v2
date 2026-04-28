set pages 100 lines 250 feedback off
SELECT qltwtrInspctSn, invstgYear, odr, spotNm, address, drnkAt,
       CASE ugrwtrPrposCode
         WHEN '01' THEN '생활용'
         WHEN '02' THEN '공업용'
         WHEN '03' THEN '농업용'
         WHEN '04' THEN '기타용'
       END AS ugrwtrPrposCode,
       qltwtrInspctDe,
       TO_CHAR(registDt,'YYYY-MM-DD') registDt,
       usrNM,
       NVL(MAX(CASE WHEN WQ_INSP_ARTCL_CD='0001' THEN RSLT_VL END), '-') AS "c0001",
       NVL(MAX(CASE WHEN WQ_INSP_ARTCL_CD='0002' THEN RSLT_VL END), '-') AS "c0002",
       NVL(MAX(CASE WHEN WQ_INSP_ARTCL_CD='0009' THEN RSLT_VL END), '-') AS "c0009",
       NVL(MAX(CASE WHEN WQ_INSP_ARTCL_CD='9999' THEN RSLT_VL END), '-') AS "c9999"
FROM (
  SELECT T1.GWEL_NO,
         T3.WQ_INSP_SN AS qltwtrInspctSn,
         T3.EXMN_YR AS invstgYear,
         T3.CYCL AS odr,
         T1.BRNCH_NM AS spotNm,
         T1.GRNDS_GWEL_NO AS sptGennum,
         T1.CTPV_NM || ' ' || T1.SGG_NM || ' ' || NVL(T1.EMD_NM,'') || ' ' || NVL(T1.LI_NM,'') || ' ' || NVL(T1.ADDR,'') AS address,
         T1.LOT AS loValue,
         T1.LAT AS laValue,
         T3.DKPP_YN AS drnkAt,
         T3.UGWTR_USG_CD AS ugrwtrPrposCode,
         T3.WQ_INSP_YMD AS qltwtrInspctDe,
         T3.FRST_REG_DT AS registDt,
         T3.LAST_CHG_DT AS changeDt,
         (SELECT TRIM(B.CD_CN) FROM TC_GD00002 B
          WHERE B.GROUP_CD_SN = 'NGW_0028' AND TRIM(B.UGWTR_COM_CD) = T3.UGWTR_WQMN_INPT_INST_CD) AS usrNM,
         T32.WQ_INSP_ARTCL_CD, T32.RSLT_VL
  FROM   TM_GD120001 T1
  INNER  JOIN TM_GD110301 T3 ON T1.GWEL_NO = T3.GWEL_NO
  LEFT   JOIN TM_GD110302 T32 ON T3.WQ_INSP_SN = T32.WQ_INSP_SN
  WHERE  T1.UGWTR_EXMN_CD = '104'
  AND    T3.EXMN_YR = '2024'
  AND    T3.CYCL = 1
)
GROUP BY qltwtrInspctSn, invstgYear, odr, spotNm, sptGennum, address, loValue, laValue, drnkAt,
         ugrwtrPrposCode, qltwtrInspctDe, registDt, changeDt, usrNM
ORDER BY spotNm, address;
EXIT;
