package pi.integrated.jobservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pi.integrated.jobservice.dto.CodeAuditRequestDTO;
import pi.integrated.jobservice.dto.CodeAuditResponseDTO;
import pi.integrated.jobservice.dto.PresetFileDTO;
import pi.integrated.jobservice.service.ai.CodeAuditAiService;

import java.util.List;

@RestController
@RequestMapping({"/api/code-audit", "/api/jobs/code-audit"})
@RequiredArgsConstructor
public class CodeAuditController {

    private final CodeAuditAiService codeAuditAiService;
    private final pi.integrated.jobservice.service.ai.MultiAgentSecurityService multiAgentSecurityService;

    @PostMapping("/analyze")
    public ResponseEntity<CodeAuditResponseDTO> analyzeCode(@RequestBody CodeAuditRequestDTO request) {
        CodeAuditResponseDTO response = codeAuditAiService.analyzeCode(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/benchmark")
    public ResponseEntity<pi.integrated.jobservice.dto.CodeBenchmarkResponseDTO> benchmarkModels(@RequestBody CodeAuditRequestDTO request) {
        return ResponseEntity.ok(codeAuditAiService.benchmarkModels(request));
    }

    @PostMapping("/multi-agent")
    public ResponseEntity<pi.integrated.jobservice.dto.MultiAgentAuditDTO> runMultiAgentAudit(@RequestBody CodeAuditRequestDTO request) {
        return ResponseEntity.ok(multiAgentSecurityService.runMultiAgentAudit(request));
    }

    @GetMapping("/preset-files")
    public ResponseEntity<List<PresetFileDTO>> getPresetFiles() {
        return ResponseEntity.ok(codeAuditAiService.getPresetFiles());
    }
}
