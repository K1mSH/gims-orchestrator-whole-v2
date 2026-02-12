package com.sync.orchestrator.domain.chain;

import com.sync.orchestrator.domain.agent.Agent;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "agent_chain_member")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentChainMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chain_id", nullable = false)
    private AgentChain chain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "seq_order", nullable = false)
    private Integer seqOrder;
}
