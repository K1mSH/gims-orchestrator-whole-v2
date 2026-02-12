package com.sync.orchestrator.domain.chain;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chains")
@RequiredArgsConstructor
public class AgentChainController {

    private final AgentChainService chainService;

    @GetMapping
    public ResponseEntity<List<AgentChainDto.Response>> getChains() {
        return ResponseEntity.ok(chainService.findAll());
    }

    @GetMapping("/{chainId}")
    public ResponseEntity<AgentChainDto.Response> getChain(@PathVariable String chainId) {
        return ResponseEntity.ok(chainService.findById(chainId));
    }

    @PostMapping
    public ResponseEntity<AgentChainDto.Response> createChain(@Valid @RequestBody AgentChainDto.CreateRequest request) {
        AgentChainDto.Response response = chainService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{chainId}")
    public ResponseEntity<AgentChainDto.Response> updateChain(
            @PathVariable String chainId,
            @RequestBody AgentChainDto.UpdateRequest request) {
        return ResponseEntity.ok(chainService.update(chainId, request));
    }

    @DeleteMapping("/{chainId}")
    public ResponseEntity<Void> deleteChain(@PathVariable String chainId) {
        chainService.delete(chainId);
        return ResponseEntity.noContent().build();
    }
}
