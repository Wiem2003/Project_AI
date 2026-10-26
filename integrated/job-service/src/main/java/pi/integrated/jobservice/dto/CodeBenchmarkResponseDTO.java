package pi.integrated.jobservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeBenchmarkResponseDTO {
    private String fileName;
    private CodeAuditResponseDTO modelA; // 120B
    private CodeAuditResponseDTO modelB; // 20B
    private Double latencySpeedupRatio;  // ex: 2.3x plus rapide
    private Double tokenSavingsPercent;  // ex: 35% d'économie
    private Double costSavingsPercent;   // ex: 45% d'économie
    private String recommendation;
}
