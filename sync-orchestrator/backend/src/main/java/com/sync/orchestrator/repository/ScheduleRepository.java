package com.sync.orchestrator.repository;

import com.sync.orchestrator.entity.Schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByAgentId(Long agentId);

    List<Schedule> findByIsEnabledTrue();

    @Query("SELECT s FROM Schedule s JOIN FETCH s.agent WHERE s.isEnabled = true")
    List<Schedule> findEnabledSchedulesWithAgent();

    Optional<Schedule> findByAgentIdAndCronExpression(Long agentId, String cronExpression);
}
