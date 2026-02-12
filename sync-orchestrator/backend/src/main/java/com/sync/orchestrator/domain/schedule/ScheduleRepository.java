package com.sync.orchestrator.domain.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByAgentAgentId(String agentId);

    List<Schedule> findByIsEnabledTrue();

    @Query("SELECT s FROM Schedule s JOIN FETCH s.agent WHERE s.isEnabled = true")
    List<Schedule> findEnabledSchedulesWithAgent();

    Optional<Schedule> findByAgentAgentIdAndCronExpression(String agentId, String cronExpression);
}
