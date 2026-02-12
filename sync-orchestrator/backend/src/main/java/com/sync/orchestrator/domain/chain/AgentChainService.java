package com.sync.orchestrator.domain.chain;

import com.sync.orchestrator.domain.agent.Agent;
import com.sync.orchestrator.domain.agent.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentChainService {

    private final AgentChainRepository chainRepository;
    private final AgentRepository agentRepository;

    public List<AgentChainDto.Response> findAll() {
        return chainRepository.findAllWithMembers().stream()
                .map(AgentChainDto.Response::from)
                .collect(Collectors.toList());
    }

    public AgentChainDto.Response findById(String chainId) {
        AgentChain chain = chainRepository.findByIdWithMembers(chainId)
                .orElseThrow(() -> new IllegalArgumentException("Chain not found: " + chainId));
        return AgentChainDto.Response.from(chain);
    }

    @Transactional
    public AgentChainDto.Response create(AgentChainDto.CreateRequest request) {
        if (chainRepository.existsById(request.getChainId())) {
            throw new IllegalArgumentException("Chain already exists: " + request.getChainId());
        }

        AgentChain chain = AgentChain.builder()
                .chainId(request.getChainId())
                .chainName(request.getChainName())
                .description(request.getDescription())
                .triggerType(request.getTriggerType())
                .build();

        if (request.getMembers() != null) {
            for (AgentChainDto.MemberRequest memberReq : request.getMembers()) {
                Agent agent = agentRepository.findById(memberReq.getAgentId())
                        .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + memberReq.getAgentId()));

                AgentChainMember member = AgentChainMember.builder()
                        .agent(agent)
                        .seqOrder(memberReq.getSeqOrder())
                        .build();
                chain.addMember(member);
            }
        }

        AgentChain saved = chainRepository.save(chain);
        return AgentChainDto.Response.from(saved);
    }

    @Transactional
    public AgentChainDto.Response update(String chainId, AgentChainDto.UpdateRequest request) {
        AgentChain chain = chainRepository.findByIdWithMembers(chainId)
                .orElseThrow(() -> new IllegalArgumentException("Chain not found: " + chainId));

        if (request.getChainName() != null) {
            chain.setChainName(request.getChainName());
        }
        if (request.getDescription() != null) {
            chain.setDescription(request.getDescription());
        }
        if (request.getTriggerType() != null) {
            chain.setTriggerType(request.getTriggerType());
        }

        if (request.getMembers() != null) {
            chain.getMembers().clear();

            for (AgentChainDto.MemberRequest memberReq : request.getMembers()) {
                Agent agent = agentRepository.findById(memberReq.getAgentId())
                        .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + memberReq.getAgentId()));

                AgentChainMember member = AgentChainMember.builder()
                        .agent(agent)
                        .seqOrder(memberReq.getSeqOrder())
                        .build();
                chain.addMember(member);
            }
        }

        return AgentChainDto.Response.from(chain);
    }

    @Transactional
    public void delete(String chainId) {
        if (!chainRepository.existsById(chainId)) {
            throw new IllegalArgumentException("Chain not found: " + chainId);
        }
        chainRepository.deleteById(chainId);
    }
}
