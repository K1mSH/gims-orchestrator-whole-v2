package com.sync.orchestrator.domain.chain;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agent_chain")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentChain {

    @Id
    @Column(name = "chain_id", length = 50)
    private String chainId;

    @Column(name = "chain_name", length = 100, nullable = false)
    private String chainName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 20, nullable = false)
    private TriggerType triggerType;

    @OneToMany(mappedBy = "chain", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seqOrder ASC")
    @Builder.Default
    private List<AgentChainMember> members = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
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
