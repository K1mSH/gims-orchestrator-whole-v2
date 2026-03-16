package com.infolink.collector.dto;

import com.infolink.collector.domain.ApiSchedule;
import lombok.*;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public class ApiScheduleDto {

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotBlank private String cronExpression;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        @NotBlank private String cronExpression;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long apiEndpointId;
        private String cronExpression;
        private Boolean isEnabled;
        private LocalDateTime createdAt;

        public static Response from(ApiSchedule s) {
            return Response.builder()
                    .id(s.getId())
                    .apiEndpointId(s.getApiEndpoint().getId())
                    .cronExpression(s.getCronExpression())
                    .isEnabled(s.getIsEnabled())
                    .createdAt(s.getCreatedAt())
                    .build();
        }
    }
}
