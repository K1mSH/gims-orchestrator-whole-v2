set pages 100 lines 250 feedback off
SELECT qltwtrInspctSn, cmpnmNm, brtcNm, sigunNm, invstgYear, odr,
       NVL(MAX(CASE WHEN WQ_INSP_ARTCL_CD='0001' THEN RSLT_VL END), '-') AS "C0001",
       NVL(MAX(CASE WHEN WQ_INSP_ARTCL_CD='0002' THEN RSLT_VL END), '-') AS "C0002",
       NVL(MAX(CASE WHEN WQ_INSP_ARTCL_CD='0009' THEN RSLT_VL END), '-') AS "C0009",
       NVL(MAX(CASE WHEN WQ_INSP_ARTCL_CD='9999' THEN RSLT_VL END), '-') AS "C9999",
       usrNm, prmisnDclrNo
FROM (
  SELECT T.WQ_INSP_SN AS qltwtrInspctSn,
         T.CONM_NM AS cmpnmNm,
         T.CTPV_NM AS brtcNm,
         T.SGG_NM AS sigunNm,
         SUBSTR(T.DMND_YMD, 1, 4) AS invstgYear,
         CASE WHEN TO_NUMBER(SUBSTR(T.DMND_YMD, 5, 2)) BETWEEN 1 AND 3 THEN 1
              WHEN TO_NUMBER(SUBSTR(T.DMND_YMD, 5, 2)) BETWEEN 4 AND 6 THEN 2
              WHEN TO_NUMBER(SUBSTR(T.DMND_YMD, 5, 2)) BETWEEN 7 AND 9 THEN 3
              ELSE 4 END AS odr,
         T.PRMSN_DCLR_NO AS prmisnDclrNo,
         (SELECT USER_NM FROM TM_GD010910 WHERE USER_ID = T.RGTR_ID) AS usrNm,
         R.WQ_INSP_ARTCL_CD, R.RSLT_VL
  FROM   TM_GD110350 T
  LEFT   JOIN TM_GD110351 R ON T.WQ_INSP_SN = R.WQ_INSP_SN
  WHERE  T.UGWTR_EXMN_CD = 104
  AND    SUBSTR(T.DMND_YMD, 1, 4) = '2024'
  AND    TO_NUMBER(SUBSTR(T.DMND_YMD, 5, 2)) BETWEEN 1 AND 3
)
GROUP BY qltwtrInspctSn, cmpnmNm, brtcNm, sigunNm, invstgYear, odr, usrNm, prmisnDclrNo
ORDER BY qltwtrInspctSn;
EXIT;
