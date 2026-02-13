package com.sync.orchestrator.domain.chain;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class AgentChainDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotBlank
        private String chainId;
        @NotBlank
        private String chainName;
        private String description;
        @NotNull
        private TriggerType triggerType;
        private List<MemberRequest> members;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private String chainName;
        private String description;
        private TriggerType triggerType;
        private List<MemberRequest> members;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MemberRequest {
        @NotNull
        private Long agentId;
        @NotNull
        private Integer seqOrder;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private String chainId;
        private String chainName;
        private String description;
        private TriggerType triggerType;
        private List<MemberResponse> members;
        private LocalDateTime createdAt;

        public static Response from(AgentChain chain) {
            return Response.builder()
                    .chainId(chain.getChainId())
                    .chainName(chain.getChainName())
                    .description(chain.getDescription())
                    .triggerType(chain.getTriggerType())
                    .members(chain.getMembers().stream()
                            .map(MemberResponse::from)
                            .collect(Collectors.toList()))
                    .createdAt(chain.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MemberResponse {
        private Long id;
        private Long agentId;
        private String agentCode;
        private String agentName;
        private String zone;
        private Integer seqOrder;

        public static MemberResponse from(AgentChainMember member) {
            return MemberResponse.builder()
                    .id(member.getId())
                    .agentId(member.getAgent().getId())
                    .agentCode(member.getAgent().getAgentCode())
                    .agentName(member.getAgent().getAgentName())
                    .zone(member.getAgent().getZone())
                    .seqOrder(member.getSeqOrder())
                    .build();
        }
    }
}
