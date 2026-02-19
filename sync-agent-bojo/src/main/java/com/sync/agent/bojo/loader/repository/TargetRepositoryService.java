package com.sync.agent.bojo.loader.repository;

import com.sync.agent.bojo.config.DynamicEntityManagerService;
import com.sync.agent.bojo.entity.iftable.rsv.IfRsvSecJewon;
import com.sync.agent.bojo.entity.iftable.rsv.IfRsvSecObsvdata;
import com.sync.agent.bojo.entity.target.LinkNgwis;
import com.sync.agent.bojo.entity.target.SecJewon;
import com.sync.agent.bojo.entity.target.SecObsvdata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Target DB CRUD용 Repository Service
 * 동적 EntityManager를 사용하여 JPA 스타일로 CRUD
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TargetRepositoryService {

    private final DynamicEntityManagerService entityManagerService;

    // ==================== SecJewon 조회 ====================

    /**
     * 제원 목록 조회 (페이징, 검색, 정렬 지원)
     */
    public List<SecJewon> findJewonWithPaging(int offset, int limit, String search, String searchColumn,
                                               String sortColumn, String sortDirection) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            StringBuilder jpql = new StringBuilder("SELECT j FROM SecJewon j");

            if (search != null && !search.isBlank()) {
                jpql.append(" WHERE ");
                if (searchColumn != null && !searchColumn.isBlank()) {
                    switch (searchColumn) {
                        case "id" -> jpql.append("CAST(j.id AS string) LIKE :search");
                        case "obsvCode" -> jpql.append("LOWER(j.obsvCode) LIKE :search");
                        case "sourceRefs" -> jpql.append("LOWER(j.sourceRefs) LIKE :search");
                        case "obsvName" -> jpql.append("LOWER(j.obsvName) LIKE :search");
                        case "sido" -> jpql.append("LOWER(j.sido) LIKE :search");
                        case "sigungu" -> jpql.append("LOWER(j.sigungu) LIKE :search");
                        default -> jpql.append("LOWER(j.obsvCode) LIKE :search OR LOWER(j.obsvName) LIKE :search");
                    }
                } else {
                    jpql.append("CAST(j.id AS string) LIKE :search OR LOWER(j.obsvCode) LIKE :search OR LOWER(j.obsvName) LIKE :search OR LOWER(j.sourceRefs) LIKE :search");
                }
            }

            // 정렬 적용 (허용된 컬럼만)
            String orderColumn = getJewonSortColumn(sortColumn);
            String orderDir = "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
            jpql.append(" ORDER BY j.").append(orderColumn).append(" ").append(orderDir);

            TypedQuery<SecJewon> query = em.createQuery(jpql.toString(), SecJewon.class);
            if (search != null && !search.isBlank()) {
                query.setParameter("search", "%" + search.toLowerCase() + "%");
            }
            query.setFirstResult(offset);
            query.setMaxResults(limit);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * 제원 목록 조회 (기존 호환용 - 정렬 없이)
     */
    public List<SecJewon> findJewonWithPaging(int offset, int limit, String search, String searchColumn) {
        return findJewonWithPaging(offset, limit, search, searchColumn, "id", "asc");
    }

    /**
     * 제원 총 개수 조회 (검색 지원)
     */
    public long countJewon(String search, String searchColumn) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            StringBuilder jpql = new StringBuilder("SELECT COUNT(j) FROM SecJewon j");

            if (search != null && !search.isBlank()) {
                jpql.append(" WHERE ");
                if (searchColumn != null && !searchColumn.isBlank()) {
                    switch (searchColumn) {
                        case "id" -> jpql.append("CAST(j.id AS string) LIKE :search");
                        case "obsvCode" -> jpql.append("LOWER(j.obsvCode) LIKE :search");
                        case "sourceRefs" -> jpql.append("LOWER(j.sourceRefs) LIKE :search");
                        case "obsvName" -> jpql.append("LOWER(j.obsvName) LIKE :search");
                        case "sido" -> jpql.append("LOWER(j.sido) LIKE :search");
                        case "sigungu" -> jpql.append("LOWER(j.sigungu) LIKE :search");
                        default -> jpql.append("LOWER(j.obsvCode) LIKE :search OR LOWER(j.obsvName) LIKE :search");
                    }
                } else {
                    jpql.append("CAST(j.id AS string) LIKE :search OR LOWER(j.obsvCode) LIKE :search OR LOWER(j.obsvName) LIKE :search OR LOWER(j.sourceRefs) LIKE :search");
                }
            }

            TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class);
            if (search != null && !search.isBlank()) {
                query.setParameter("search", "%" + search.toLowerCase() + "%");
            }
            return query.getSingleResult();
        } finally {
            em.close();
        }
    }

    // ==================== SecObsvdata 조회 ====================

    /**
     * 관측데이터 목록 조회 (페이징, 검색, 정렬 지원)
     */
    public List<SecObsvdata> findObsvDataWithPaging(int offset, int limit, String search, String searchColumn,
                                                     String sortColumn, String sortDirection) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            StringBuilder jpql = new StringBuilder("SELECT o FROM SecObsvdata o");

            if (search != null && !search.isBlank()) {
                jpql.append(" WHERE ");
                if (searchColumn != null && !searchColumn.isBlank()) {
                    switch (searchColumn) {
                        case "id" -> jpql.append("CAST(o.id AS string) LIKE :search");
                        case "sourceRefs" -> jpql.append("LOWER(o.sourceRefs) LIKE :search");
                        case "obsvCode" -> jpql.append("LOWER(o.obsvCode) LIKE :search");
                        case "remark" -> jpql.append("LOWER(o.remark) LIKE :search");
                        default -> jpql.append("LOWER(o.obsvCode) LIKE :search");
                    }
                } else {
                    jpql.append("CAST(o.id AS string) LIKE :search OR LOWER(o.sourceRefs) LIKE :search OR LOWER(o.obsvCode) LIKE :search");
                }
            }

            // 정렬 적용 (허용된 컬럼만)
            String orderColumn = getObsvSortColumn(sortColumn);
            String orderDir = "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
            jpql.append(" ORDER BY o.").append(orderColumn).append(" ").append(orderDir);

            TypedQuery<SecObsvdata> query = em.createQuery(jpql.toString(), SecObsvdata.class);
            if (search != null && !search.isBlank()) {
                query.setParameter("search", "%" + search.toLowerCase() + "%");
            }
            query.setFirstResult(offset);
            query.setMaxResults(limit);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * 관측데이터 목록 조회 (기존 호환용 - 정렬 없이)
     */
    public List<SecObsvdata> findObsvDataWithPaging(int offset, int limit, String search, String searchColumn) {
        return findObsvDataWithPaging(offset, limit, search, searchColumn, "id", "asc");
    }

    /**
     * 관측데이터 총 개수 조회 (검색 지원)
     */
    public long countObsvData(String search, String searchColumn) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            StringBuilder jpql = new StringBuilder("SELECT COUNT(o) FROM SecObsvdata o");

            if (search != null && !search.isBlank()) {
                jpql.append(" WHERE ");
                if (searchColumn != null && !searchColumn.isBlank()) {
                    switch (searchColumn) {
                        case "id" -> jpql.append("CAST(o.id AS string) LIKE :search");
                        case "sourceRefs" -> jpql.append("LOWER(o.sourceRefs) LIKE :search");
                        case "obsvCode" -> jpql.append("LOWER(o.obsvCode) LIKE :search");
                        case "remark" -> jpql.append("LOWER(o.remark) LIKE :search");
                        default -> jpql.append("LOWER(o.obsvCode) LIKE :search");
                    }
                } else {
                    jpql.append("CAST(o.id AS string) LIKE :search OR LOWER(o.sourceRefs) LIKE :search OR LOWER(o.obsvCode) LIKE :search");
                }
            }

            TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class);
            if (search != null && !search.isBlank()) {
                query.setParameter("search", "%" + search.toLowerCase() + "%");
            }
            return query.getSingleResult();
        } finally {
            em.close();
        }
    }

    // ==================== SecJewon 저장/삭제 ====================

    /**
     * 제원 저장 (INSERT or UPDATE)
     */
    public SecJewon saveJewon(SecJewon jewon) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            SecJewon merged = em.merge(jewon);
            tx.commit();
            return merged;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    /**
     * 제원 일괄 저장
     */
    public int saveAllJewon(List<SecJewon> jewonList) {
        if (jewonList.isEmpty()) return 0;

        EntityManager em = entityManagerService.getTargetEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            int count = 0;
            for (SecJewon jewon : jewonList) {
                em.merge(jewon);
                count++;
                // 배치 최적화: 주기적으로 flush
                if (count % 50 == 0) {
                    em.flush();
                    em.clear();
                }
            }
            tx.commit();
            return count;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    /**
     * 제원 삭제 (obsvCode 목록)
     */
    public int deleteJewonByObsvCodes(List<String> obsvCodes) {
        if (obsvCodes.isEmpty()) return 0;

        EntityManager em = entityManagerService.getTargetEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            int deleted = em.createQuery(
                    "DELETE FROM SecJewon j WHERE j.obsvCode IN :obsvCodes"
            ).setParameter("obsvCodes", obsvCodes).executeUpdate();
            tx.commit();
            log.info("Deleted {} jewon records", deleted);
            return deleted;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    // ==================== SecObsvdata ====================

    /**
     * 관측데이터 일괄 저장
     */
    public int saveAllObsvData(List<SecObsvdata> obsvDataList) {
        if (obsvDataList.isEmpty()) return 0;

        EntityManager em = entityManagerService.getTargetEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            int count = 0;
            for (SecObsvdata data : obsvDataList) {
                em.merge(data);
                count++;
                // 배치 최적화
                if (count % 100 == 0) {
                    em.flush();
                    em.clear();
                }
            }
            tx.commit();
            return count;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    // ==================== LinkNgwis ====================

    /**
     * Link 조회
     */
    public Optional<LinkNgwis> findLinkByObsvCode(String obsvCode) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            LinkNgwis link = em.find(LinkNgwis.class, obsvCode);
            return Optional.ofNullable(link);
        } finally {
            em.close();
        }
    }

    /**
     * Link 저장 (INSERT or UPDATE)
     */
    public LinkNgwis saveLink(LinkNgwis link) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            LinkNgwis merged = em.merge(link);
            tx.commit();
            return merged;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    /**
     * Link 업데이트 (마지막 동기화 시점 기록)
     */
    public void updateLinkLastSync(String obsvCode, java.sql.Date obsvDate, java.sql.Time obsvTime) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            LinkNgwis link = em.find(LinkNgwis.class, obsvCode);
            if (link == null) {
                // INSERT
                link = new LinkNgwis();
                link.setObsvCode(obsvCode);
            }
            // java.sql.Date -> LocalDateTime 변환
            if (obsvDate != null) {
                link.setObsvDate(obsvDate.toLocalDate().atStartOfDay());
            }
            // java.sql.Time -> String (HH:mm 형식)
            if (obsvTime != null) {
                link.setObsvTime(obsvTime.toLocalTime().format(DateTimeFormatter.ofPattern("HHmm")));
            }
            link.setUpdateTime(LocalDateTime.now());
            em.merge(link);
            tx.commit();
            log.debug("Updated link for {}: {}-{}", obsvCode, obsvDate, obsvTime);
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    // ==================== JDBC Batch UPSERT (성능 최적화) ====================

    private static final int JDBC_BATCH_SIZE = 1000;

    /**
     * 제원 JDBC batch UPSERT (ON CONFLICT 사용)
     * JPA merge() 대비 ~100x 성능 향상
     */
    public int batchUpsertJewon(List<SecJewon> jewonList) {
        if (jewonList.isEmpty()) return 0;

        JdbcTemplate jdbc = entityManagerService.getTargetJdbcTemplate();
        String sql = """
            INSERT INTO sec_jewon (obsv_code, obsv_name, well, sido, sigungu, upmyundo,
                bunji, ri, x, y, pyogo, insdate, guldep, guldia, regdate, casing_height,
                source_refs, link_status, execution_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (obsv_code) DO UPDATE SET
                obsv_name=EXCLUDED.obsv_name, well=EXCLUDED.well,
                sido=EXCLUDED.sido, sigungu=EXCLUDED.sigungu, upmyundo=EXCLUDED.upmyundo,
                bunji=EXCLUDED.bunji, ri=EXCLUDED.ri, x=EXCLUDED.x, y=EXCLUDED.y,
                pyogo=EXCLUDED.pyogo, insdate=EXCLUDED.insdate,
                guldep=EXCLUDED.guldep, guldia=EXCLUDED.guldia, regdate=EXCLUDED.regdate,
                casing_height=EXCLUDED.casing_height,
                source_refs=EXCLUDED.source_refs, link_status=EXCLUDED.link_status,
                execution_id=EXCLUDED.execution_id
            """;

        int totalUpserted = 0;
        for (int i = 0; i < jewonList.size(); i += JDBC_BATCH_SIZE) {
            List<SecJewon> batch = jewonList.subList(i, Math.min(i + JDBC_BATCH_SIZE, jewonList.size()));
            List<Object[]> batchParams = new ArrayList<>(batch.size());

            for (SecJewon j : batch) {
                batchParams.add(new Object[]{
                    j.getObsvCode(), j.getObsvName(), j.getWell(),
                    j.getSido(), j.getSigungu(), j.getUpmyundo(),
                    j.getBunji(), j.getRi(), j.getX(), j.getY(),
                    j.getPyogo(), j.getInsdate(), j.getGuldep(), j.getGuldia(),
                    j.getRegdate(), j.getCasingHeight(),
                    j.getSourceRefs(), j.getLinkStatus(), j.getExecutionId()
                });
            }

            int[] results = jdbc.batchUpdate(sql, batchParams);
            totalUpserted += batch.size();
            log.info("Batch UPSERT jewon: {}/{}", totalUpserted, jewonList.size());
        }
        return totalUpserted;
    }

    /**
     * 관측데이터 JDBC batch UPSERT (ON CONFLICT 사용)
     * RESYNC SELECT 완전 제거 - ON CONFLICT가 자동 처리
     */
    public int batchUpsertObsvdata(List<SecObsvdata> obsvList) {
        if (obsvList.isEmpty()) return 0;

        JdbcTemplate jdbc = entityManagerService.getTargetJdbcTemplate();
        String sql = """
            INSERT INTO sec_obsvdata (obsv_code, obsv_date, obsv_time, gwdep, gwtemp, ec,
                remark, source_refs, link_status, execution_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (obsv_code, obsv_date, obsv_time) DO UPDATE SET
                gwdep=EXCLUDED.gwdep, gwtemp=EXCLUDED.gwtemp, ec=EXCLUDED.ec,
                remark=EXCLUDED.remark, source_refs=EXCLUDED.source_refs,
                link_status=EXCLUDED.link_status, execution_id=EXCLUDED.execution_id
            """;

        int totalUpserted = 0;
        for (int i = 0; i < obsvList.size(); i += JDBC_BATCH_SIZE) {
            List<SecObsvdata> batch = obsvList.subList(i, Math.min(i + JDBC_BATCH_SIZE, obsvList.size()));
            List<Object[]> batchParams = new ArrayList<>(batch.size());

            for (SecObsvdata o : batch) {
                batchParams.add(new Object[]{
                    o.getObsvCode(), o.getObsvDate(), o.getObsvTime(),
                    o.getGwdep(), o.getGwtemp(), o.getEc(),
                    o.getRemark(), o.getSourceRefs(), o.getLinkStatus(), o.getExecutionId()
                });
            }

            int[] results = jdbc.batchUpdate(sql, batchParams);
            totalUpserted += batch.size();
            int percent = (int) ((totalUpserted * 100.0) / obsvList.size());
            log.info("Batch UPSERT obsvdata: {}/{} ({}%)", totalUpserted, obsvList.size(), percent);
        }
        return totalUpserted;
    }

    /**
     * Link 테이블 JDBC batch UPSERT
     * obsv_code가 PK이므로 ON CONFLICT (obsv_code) 사용
     */
    public int batchUpsertLinks(List<LinkNgwis> linkList) {
        if (linkList.isEmpty()) return 0;

        JdbcTemplate jdbc = entityManagerService.getTargetJdbcTemplate();
        String sql = """
            INSERT INTO link_ngwis (obsv_code, obsv_date, obsv_time, update_time)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (obsv_code) DO UPDATE SET
                obsv_date=EXCLUDED.obsv_date, obsv_time=EXCLUDED.obsv_time,
                update_time=EXCLUDED.update_time
            """;

        List<Object[]> batchParams = new ArrayList<>(linkList.size());
        for (LinkNgwis link : linkList) {
            batchParams.add(new Object[]{
                link.getObsvCode(),
                link.getObsvDate() != null ? Timestamp.valueOf(link.getObsvDate()) : null,
                link.getObsvTime(),
                link.getUpdateTime() != null ? Timestamp.valueOf(link.getUpdateTime()) : null
            });
        }

        int[] results = jdbc.batchUpdate(sql, batchParams);
        log.info("Batch UPSERT links: {} records", linkList.size());
        return linkList.size();
    }

    // ==================== IF -> Target 변환 헬퍼 ====================

    /**
     * IfRsvSecJewon -> SecJewon 변환
     * IF와 Target이 동일한 필드 구조를 가지므로 직접 복사
     * executionId: Loader의 실행 ID (Target에 기록)
     *
     * link_status 전파:
     * - Source가 RESYNC면 Target도 RESYNC (다음 에이전트도 UPSERT 필요)
     * - 그 외는 PENDING (일반 동기화)
     */
    public static SecJewon convertToSecJewon(IfRsvSecJewon ifJewon, String executionId) {
        // Source의 linkStatus가 RESYNC면 Target도 RESYNC로 전파
        String targetLinkStatus = "RESYNC".equals(ifJewon.getLinkStatus()) ? "RESYNC" : "PENDING";

        return SecJewon.builder()
                .obsvCode(ifJewon.getObsvCode())
                .sourceRefs(ifJewon.getSourceRefs())  // Source 참조 정보 복사 (JSON 객체: {"ZONE":["ds:table:pk"]})
                .obsvName(ifJewon.getObsvName())
                .well(ifJewon.getWell())
                .sido(ifJewon.getSido())
                .sigungu(ifJewon.getSigungu())
                .upmyundo(ifJewon.getUpmyundo())
                .bunji(ifJewon.getBunji())
                .ri(ifJewon.getRi())
                .x(ifJewon.getX())
                .y(ifJewon.getY())
                .pyogo(ifJewon.getPyogo())
                .insdate(ifJewon.getInsdate())
                .guldep(ifJewon.getGuldep())
                .guldia(ifJewon.getGuldia())
                .regdate(ifJewon.getRegdate())
                .casingHeight(ifJewon.getCasingHeight())
                .executionId(executionId)
                .linkStatus(targetLinkStatus)
                .build();
    }

    /**
     * IfRsvSecJewon -> SecJewon 변환 (하위 호환용)
     * @deprecated use convertToSecJewon(IfRsvSecJewon, String) instead
     */
    @Deprecated
    public static SecJewon convertToSecJewon(IfRsvSecJewon ifJewon) {
        return convertToSecJewon(ifJewon, null);
    }

    /**
     * IfRsvSecObsvdata -> SecObsvdata 변환
     * executionId: Loader의 실행 ID (Target에 기록)
     *
     * link_status 전파:
     * - Source가 RESYNC면 Target도 RESYNC (다음 에이전트도 UPSERT 필요)
     * - 그 외는 PENDING (일반 동기화)
     */
    public static SecObsvdata convertToSecObsvdata(IfRsvSecObsvdata ifData, String executionId) {
        // Source의 linkStatus가 RESYNC면 Target도 RESYNC로 전파
        String targetLinkStatus = "RESYNC".equals(ifData.getLinkStatus()) ? "RESYNC" : "PENDING";

        return SecObsvdata.builder()
                // id는 auto-generated (Target 자체 PK)
                .sourceRefs(ifData.getSourceRefs())  // Source 참조 정보 복사 (JSON 객체: {"ZONE":["ds:table:pk"]})
                .obsvDate(ifData.getObsvDate())
                .obsvCode(ifData.getObsvCode())
                .obsvTime(ifData.getObsvTime())
                .gwdep(ifData.getGwdep())
                .gwtemp(ifData.getGwtemp())
                .ec(ifData.getEc())
                .remark(ifData.getRemark())
                .executionId(executionId)
                .linkStatus(targetLinkStatus)
                .build();
    }

    /**
     * IfRsvSecObsvdata -> SecObsvdata 변환 (하위 호환용)
     * @deprecated use convertToSecObsvdata(IfRsvSecObsvdata, String) instead
     */
    @Deprecated
    public static SecObsvdata convertToSecObsvdata(IfRsvSecObsvdata ifData) {
        return convertToSecObsvdata(ifData, null);
    }

    // ==================== executionId 기반 조회 (Loader 실행 스냅샷용) ====================

    /**
     * 제원 조회 (executionId 기반) - Loader가 적재한 데이터 조회
     */
    public List<SecJewon> findJewonByExecutionId(String executionId) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT j FROM SecJewon j WHERE j.executionId = :executionId ORDER BY j.id",
                    SecJewon.class
            ).setParameter("executionId", executionId).getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * 제원 조회 (executionId 기반, 페이징)
     */
    public List<SecJewon> findJewonByExecutionIdWithPaging(String executionId, int offset, int limit,
                                                            String sortColumn, String sortDirection) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            String orderColumn = getJewonSortColumn(sortColumn);
            String orderDir = "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";

            return em.createQuery(
                    "SELECT j FROM SecJewon j WHERE j.executionId = :executionId ORDER BY j." + orderColumn + " " + orderDir,
                    SecJewon.class
            ).setParameter("executionId", executionId)
             .setFirstResult(offset)
             .setMaxResults(limit)
             .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * 제원 건수 조회 (executionId 기반)
     */
    public long countJewonByExecutionId(String executionId) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT COUNT(j) FROM SecJewon j WHERE j.executionId = :executionId",
                    Long.class
            ).setParameter("executionId", executionId).getSingleResult();
        } finally {
            em.close();
        }
    }

    /**
     * 관측데이터 조회 (executionId 기반) - Loader가 적재한 데이터 조회
     */
    public List<SecObsvdata> findObsvDataByExecutionId(String executionId) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT o FROM SecObsvdata o WHERE o.executionId = :executionId ORDER BY o.id",
                    SecObsvdata.class
            ).setParameter("executionId", executionId).getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * 관측데이터 조회 (executionId 기반, 페이징)
     */
    public List<SecObsvdata> findObsvDataByExecutionIdWithPaging(String executionId, int offset, int limit,
                                                                   String sortColumn, String sortDirection) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            String orderColumn = getObsvSortColumn(sortColumn);
            String orderDir = "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";

            return em.createQuery(
                    "SELECT o FROM SecObsvdata o WHERE o.executionId = :executionId ORDER BY o." + orderColumn + " " + orderDir,
                    SecObsvdata.class
            ).setParameter("executionId", executionId)
             .setFirstResult(offset)
             .setMaxResults(limit)
             .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * 관측데이터 건수 조회 (executionId 기반)
     */
    public long countObsvDataByExecutionId(String executionId) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT COUNT(o) FROM SecObsvdata o WHERE o.executionId = :executionId",
                    Long.class
            ).setParameter("executionId", executionId).getSingleResult();
        } finally {
            em.close();
        }
    }

    // ==================== sourceRefs 기반 조회 (실행 스냅샷용) ====================

    /**
     * 제원 조회 (sourceRefs 목록 기반) - 실행 시점 스냅샷 조회용
     */
    public List<SecJewon> findJewonBySourceRefs(List<String> sourceRefsList) {
        if (sourceRefsList == null || sourceRefsList.isEmpty()) return List.of();

        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            TypedQuery<SecJewon> query = em.createQuery(
                    "SELECT j FROM SecJewon j WHERE j.sourceRefs IN :sourceRefs ORDER BY j.id",
                    SecJewon.class
            );
            query.setParameter("sourceRefs", sourceRefsList);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * 제원 조회 (sourceRefs 목록 기반, 페이징 지원)
     */
    public List<SecJewon> findJewonBySourceRefsWithPaging(List<String> sourceRefsList, int offset, int limit,
                                                           String sortColumn, String sortDirection) {
        if (sourceRefsList == null || sourceRefsList.isEmpty()) return List.of();

        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            String orderColumn = getJewonSortColumn(sortColumn);
            String orderDir = "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";

            TypedQuery<SecJewon> query = em.createQuery(
                    "SELECT j FROM SecJewon j WHERE j.sourceRefs IN :sourceRefs ORDER BY j." + orderColumn + " " + orderDir,
                    SecJewon.class
            );
            query.setParameter("sourceRefs", sourceRefsList);
            query.setFirstResult(offset);
            query.setMaxResults(limit);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * 관측데이터 조회 (sourceRefs 목록 기반) - 실행 시점 스냅샷 조회용
     */
    public List<SecObsvdata> findObsvDataBySourceRefs(List<String> sourceRefsList) {
        if (sourceRefsList == null || sourceRefsList.isEmpty()) return List.of();

        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            TypedQuery<SecObsvdata> query = em.createQuery(
                    "SELECT o FROM SecObsvdata o WHERE o.sourceRefs IN :sourceRefs ORDER BY o.id",
                    SecObsvdata.class
            );
            query.setParameter("sourceRefs", sourceRefsList);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * 관측데이터 조회 (sourceRefs 목록 기반, 페이징 지원)
     */
    public List<SecObsvdata> findObsvDataBySourceRefsWithPaging(List<String> sourceRefsList, int offset, int limit,
                                                                  String sortColumn, String sortDirection) {
        if (sourceRefsList == null || sourceRefsList.isEmpty()) return List.of();

        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            String orderColumn = getObsvSortColumn(sortColumn);
            String orderDir = "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";

            TypedQuery<SecObsvdata> query = em.createQuery(
                    "SELECT o FROM SecObsvdata o WHERE o.sourceRefs IN :sourceRefs ORDER BY o." + orderColumn + " " + orderDir,
                    SecObsvdata.class
            );
            query.setParameter("sourceRefs", sourceRefsList);
            query.setFirstResult(offset);
            query.setMaxResults(limit);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    // ==================== RESYNC용 Unique Key 조회 ====================

    /**
     * sec_obsvdata - unique key(code+date+time)로 기존 ID 조회
     * RESYNC 시 기존 레코드를 UPDATE하기 위해 id를 찾아서 설정
     */
    public Integer findSecObsvdataIdByUniqueKey(String obsvCode, java.sql.Date obsvDate, java.sql.Time obsvTime) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            List<Integer> results = em.createQuery(
                    "SELECT o.id FROM SecObsvdata o WHERE o.obsvCode = :obsvCode AND o.obsvDate = :obsvDate AND o.obsvTime = :obsvTime",
                    Integer.class
            ).setParameter("obsvCode", obsvCode)
             .setParameter("obsvDate", obsvDate)
             .setParameter("obsvTime", obsvTime)
             .getResultList();
            return results.isEmpty() ? null : results.get(0);
        } finally {
            em.close();
        }
    }

    // ==================== 정렬 헬퍼 ====================

    /**
     * 제원 정렬 컬럼 매핑 (SQL Injection 방지)
     */
    private String getJewonSortColumn(String sortColumn) {
        if (sortColumn == null) return "id";
        return switch (sortColumn) {
            case "id" -> "id";
            case "obsvCode" -> "obsvCode";
            case "sourceRefs" -> "sourceRefs";
            case "obsvName" -> "obsvName";
            case "sido" -> "sido";
            case "sigungu" -> "sigungu";
            case "upmyundo" -> "upmyundo";
            case "x" -> "x";
            case "y" -> "y";
            case "pyogo" -> "pyogo";
            case "well" -> "well";
            case "guldep" -> "guldep";
            case "guldia" -> "guldia";
            default -> "id";
        };
    }

    /**
     * 관측데이터 정렬 컬럼 매핑 (SQL Injection 방지)
     */
    private String getObsvSortColumn(String sortColumn) {
        if (sortColumn == null) return "id";
        return switch (sortColumn) {
            case "id" -> "id";
            case "sourceRefs" -> "sourceRefs";
            case "obsvCode" -> "obsvCode";
            case "obsvDate" -> "obsvDate";
            case "obsvTime" -> "obsvTime";
            case "gwdep" -> "gwdep";
            case "gwtemp" -> "gwtemp";
            case "ec" -> "ec";
            case "remark" -> "remark";
            default -> "id";
        };
    }

    // ==================== IF_RSV ID 기반 조회 ====================

    /**
     * IF_RSV 제원 - ID로 조회
     */
    public IfRsvSecJewon findIfRsvJewonById(Integer id) {
        if (id == null) return null;

        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.find(IfRsvSecJewon.class, id);
        } finally {
            em.close();
        }
    }

    /**
     * IF_RSV 관측데이터 - ID로 조회
     */
    public IfRsvSecObsvdata findIfRsvObsvdataById(Integer id) {
        if (id == null) return null;

        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.find(IfRsvSecObsvdata.class, id);
        } finally {
            em.close();
        }
    }

    // ==================== IF_RSV sourceRefs 기반 조회 ====================

    /**
     * IF_RSV 제원 - sourceRefs 목록으로 조회
     */
    public List<IfRsvSecJewon> findIfRsvJewonBySourceRefs(List<String> sourceRefsList) {
        if (sourceRefsList == null || sourceRefsList.isEmpty()) return List.of();

        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT j FROM IfRsvSecJewon j WHERE j.sourceRefs IN :sourceRefs ORDER BY j.id",
                    IfRsvSecJewon.class
            ).setParameter("sourceRefs", sourceRefsList).getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * IF_RSV 제원 - sourceRefs 목록으로 조회 (페이징)
     */
    public List<IfRsvSecJewon> findIfRsvJewonBySourceRefsWithPaging(List<String> sourceRefsList, int offset, int limit,
                                                                      String sortColumn, String sortDirection) {
        if (sourceRefsList == null || sourceRefsList.isEmpty()) return List.of();

        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            String orderColumn = getJewonSortColumn(sortColumn);
            String orderDir = "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";

            return em.createQuery(
                    "SELECT j FROM IfRsvSecJewon j WHERE j.sourceRefs IN :sourceRefs ORDER BY j." + orderColumn + " " + orderDir,
                    IfRsvSecJewon.class
            ).setParameter("sourceRefs", sourceRefsList)
             .setFirstResult(offset)
             .setMaxResults(limit)
             .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * IF_RSV 관측데이터 - sourceRefs 목록으로 조회
     */
    public List<IfRsvSecObsvdata> findIfRsvObsvdataBySourceRefs(List<String> sourceRefsList) {
        if (sourceRefsList == null || sourceRefsList.isEmpty()) return List.of();

        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT o FROM IfRsvSecObsvdata o WHERE o.sourceRefs IN :sourceRefs ORDER BY o.id",
                    IfRsvSecObsvdata.class
            ).setParameter("sourceRefs", sourceRefsList).getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * IF_RSV 관측데이터 - sourceRefs 목록으로 조회 (페이징)
     */
    public List<IfRsvSecObsvdata> findIfRsvObsvdataBySourceRefsWithPaging(List<String> sourceRefsList, int offset, int limit,
                                                                            String sortColumn, String sortDirection) {
        if (sourceRefsList == null || sourceRefsList.isEmpty()) return List.of();

        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            String orderColumn = getObsvSortColumn(sortColumn);
            String orderDir = "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";

            return em.createQuery(
                    "SELECT o FROM IfRsvSecObsvdata o WHERE o.sourceRefs IN :sourceRefs ORDER BY o." + orderColumn + " " + orderDir,
                    IfRsvSecObsvdata.class
            ).setParameter("sourceRefs", sourceRefsList)
             .setFirstResult(offset)
             .setMaxResults(limit)
             .getResultList();
        } finally {
            em.close();
        }
    }

    // ==================== IF_RSV 제원 조회 (Target DB) ====================

    /**
     * IF_RSV 제원 - executionId로 조회
     */
    public List<IfRsvSecJewon> findIfRsvJewonByExecutionId(String executionId) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT j FROM IfRsvSecJewon j WHERE j.executionId = :executionId",
                    IfRsvSecJewon.class
            ).setParameter("executionId", executionId).getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * IF_RSV 제원 - executionId로 조회 (하위 호환용)
     * @deprecated use findIfRsvJewonByExecutionId instead
     */
    @Deprecated
    public List<IfRsvSecJewon> findIfRsvJewonBySndExecutionId(String executionId) {
        return findIfRsvJewonByExecutionId(executionId);
    }

    /**
     * IF_RSV 제원 - executionId로 조회 (하위 호환용)
     * @deprecated use findIfRsvJewonByExecutionId instead
     */
    @Deprecated
    public List<IfRsvSecJewon> findIfRsvJewonByRsvExecutionId(String executionId) {
        return findIfRsvJewonByExecutionId(executionId);
    }

    /**
     * IF_RSV 제원 - 전체 조회 (페이징)
     * Source 조회 시 사용 (다른 Agent가 생성한 데이터 포함)
     */
    public List<IfRsvSecJewon> findAllIfRsvJewon(int offset, int size) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT j FROM IfRsvSecJewon j ORDER BY j.id",
                    IfRsvSecJewon.class
            ).setFirstResult(offset).setMaxResults(size).getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * IF_RSV 제원 - 전체 건수 조회
     */
    public long countAllIfRsvJewon() {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT COUNT(j) FROM IfRsvSecJewon j",
                    Long.class
            ).getSingleResult();
        } finally {
            em.close();
        }
    }

    /**
     * IF_RSV 제원 - PENDING/RESYNC 상태 조회
     * 모든 PENDING/RESYNC 레코드를 조회 (executionId 무관)
     * - PENDING: 일반 동기화 (INSERT)
     * - RESYNC: 재동기화 필요 (UPSERT)
     */
    public List<IfRsvSecJewon> findIfRsvJewonPending(String executionId) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT j FROM IfRsvSecJewon j WHERE j.linkStatus IS NULL OR j.linkStatus IN ('PENDING', 'RESYNC')",
                    IfRsvSecJewon.class
            ).getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * IF_RSV 제원 - 전체 조회 (시간지정실행용, link_status 무시)
     * 시간지정실행 시 모든 데이터를 재동기화
     */
    public List<IfRsvSecJewon> findAllIfRsvJewonForResync() {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT j FROM IfRsvSecJewon j",
                    IfRsvSecJewon.class
            ).getResultList();
        } finally {
            em.close();
        }
    }

    // IF status 업데이트는 IfTableService를 통해 처리 (JDBC 공통화)

    // ==================== IF_RSV 관측데이터 조회 (Target DB) ====================

    /**
     * IF_RSV 관측데이터 - executionId로 조회
     */
    public List<IfRsvSecObsvdata> findIfRsvObsvdataByExecutionId(String executionId) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT o FROM IfRsvSecObsvdata o WHERE o.executionId = :executionId",
                    IfRsvSecObsvdata.class
            ).setParameter("executionId", executionId).getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * IF_RSV 관측데이터 - executionId로 조회 (하위 호환용)
     * @deprecated use findIfRsvObsvdataByExecutionId instead
     */
    @Deprecated
    public List<IfRsvSecObsvdata> findIfRsvObsvdataBySndExecutionId(String executionId) {
        return findIfRsvObsvdataByExecutionId(executionId);
    }

    /**
     * IF_RSV 관측데이터 - executionId로 조회 (하위 호환용)
     * @deprecated use findIfRsvObsvdataByExecutionId instead
     */
    @Deprecated
    public List<IfRsvSecObsvdata> findIfRsvObsvdataByRsvExecutionId(String executionId) {
        return findIfRsvObsvdataByExecutionId(executionId);
    }

    /**
     * IF_RSV 관측데이터 - 전체 조회 (페이징)
     * Source 조회 시 사용 (다른 Agent가 생성한 데이터 포함)
     */
    public List<IfRsvSecObsvdata> findAllIfRsvObsvdata(int offset, int size) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT o FROM IfRsvSecObsvdata o ORDER BY o.id",
                    IfRsvSecObsvdata.class
            ).setFirstResult(offset).setMaxResults(size).getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * IF_RSV 관측데이터 - 전체 건수 조회
     */
    public long countAllIfRsvObsvdata() {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT COUNT(o) FROM IfRsvSecObsvdata o",
                    Long.class
            ).getSingleResult();
        } finally {
            em.close();
        }
    }

    /**
     * IF_RSV 관측데이터 - PENDING/RESYNC 상태 조회
     * 모든 PENDING/RESYNC 레코드를 조회 (executionId 무관)
     * - PENDING: 일반 동기화 (INSERT)
     * - RESYNC: 재동기화 필요 (UPSERT)
     */
    public List<IfRsvSecObsvdata> findIfRsvObsvdataPending(String executionId) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT o FROM IfRsvSecObsvdata o WHERE o.linkStatus IS NULL OR o.linkStatus IN ('PENDING', 'RESYNC')",
                    IfRsvSecObsvdata.class
            ).getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * IF_RSV 관측데이터 - 시간 범위 기반 조회 (시간지정실행용, link_status 무시)
     * 시간지정실행 시 해당 시간 범위의 모든 데이터를 재동기화
     */
    public List<IfRsvSecObsvdata> findIfRsvObsvdataByTimeRange(
            java.time.LocalDateTime startTime, java.time.LocalDateTime endTime) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            StringBuilder jpql = new StringBuilder(
                    "SELECT o FROM IfRsvSecObsvdata o WHERE 1=1");

            if (startTime != null) {
                jpql.append(" AND o.obsvDate >= :startDate");
            }
            if (endTime != null) {
                jpql.append(" AND o.obsvDate <= :endDate");
            }

            TypedQuery<IfRsvSecObsvdata> query = em.createQuery(jpql.toString(), IfRsvSecObsvdata.class);

            if (startTime != null) {
                query.setParameter("startDate", java.sql.Date.valueOf(startTime.toLocalDate()));
            }
            if (endTime != null) {
                query.setParameter("endDate", java.sql.Date.valueOf(endTime.toLocalDate()));
            }

            return query.getResultList();
        } finally {
            em.close();
        }
    }

    // IF status 업데이트는 IfTableService를 통해 처리 (JDBC 공통화)

    /**
     * IF_RSV 관측데이터 - obsvCode + executionId로 조회
     */
    public List<IfRsvSecObsvdata> findIfRsvObsvdataByObsvCodeAndExecutionId(String obsvCode, String executionId) {
        EntityManager em = entityManagerService.getTargetEntityManager();
        try {
            return em.createQuery(
                    "SELECT o FROM IfRsvSecObsvdata o WHERE o.obsvCode = :obsvCode AND o.executionId = :executionId",
                    IfRsvSecObsvdata.class
            ).setParameter("obsvCode", obsvCode).setParameter("executionId", executionId).getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * IF_RSV 관측데이터 - obsvCode + executionId로 조회 (하위 호환용)
     * @deprecated use findIfRsvObsvdataByObsvCodeAndExecutionId instead
     */
    @Deprecated
    public List<IfRsvSecObsvdata> findIfRsvObsvdataByObsvCodeAndRsvExecutionId(String obsvCode, String executionId) {
        return findIfRsvObsvdataByObsvCodeAndExecutionId(obsvCode, executionId);
    }
}
