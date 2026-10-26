package pi.integrated.jobservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeAuditRequestDTO {
    private String fileName;
    private String language;           // "java", "typescript", "sql", "python"
    private String sourceCode;

    // Optimisations & Hyperparamètres
    @Builder.Default
    private String model = "openai/gpt-oss-120b";
    
    @Builder.Default
    private Double temperature = 0.1;

    @Builder.Default
    private Double topP = 0.95;

    @Builder.Default
    private Integer maxTokens = 3500;

    @Builder.Default
    private Boolean useCache = true;

    @Builder.Default
    private Boolean enableRag = true;
}
