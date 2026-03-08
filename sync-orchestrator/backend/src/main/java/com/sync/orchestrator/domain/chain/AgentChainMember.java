package com.sync.orchestrator.domain.chain;

import com.sync.orchestrator.domain.agent.Agent;
import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "agent_chain_member")
@org.hibernate.annotations.Table(appliesTo = "agent_chain_member", comment = "체인 구성원")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentChainMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Comment("PK")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chain_id", nullable = false)
    private AgentChain chain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "seq_order", nullable = false)
    @Comment("실행 순서")
    private Integer seqOrder;
}
