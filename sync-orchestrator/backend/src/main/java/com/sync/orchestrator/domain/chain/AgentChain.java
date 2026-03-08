package com.sync.orchestrator.domain.chain;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agent_chain")
@org.hibernate.annotations.Table(appliesTo = "agent_chain", comment = "에이전트 체인")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentChain {

    @Id
    @Column(name = "chain_id", length = 50)
    @Comment("체인 ID (PK)")
    private String chainId;

    @Column(name = "chain_name", length = 100, nullable = false)
    @Comment("체인명")
    private String chainName;

    @Column(name = "description", columnDefinition = "TEXT")
    @Comment("설명")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 20, nullable = false)
    @Comment("트리거 유형 (SEQUENTIAL/PARALLEL)")
    private TriggerType triggerType;

    @OneToMany(mappedBy = "chain", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seqOrder ASC")
    @Builder.Default
    private List<AgentChainMember> members = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Comment("생성 시각")
    private LocalDateTime createdAt;

    public void addMember(AgentChainMember member) {
        members.add(member);
        member.setChain(this);
    }

    public void removeMember(AgentChainMember member) {
        members.remove(member);
        member.setChain(null);
    }
}
