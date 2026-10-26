package pi.integrated.jobservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobAiRankingResponseDTO {
    private Long jobId;
    private String jobTitle;
    private Integer totalCandidates;
    private String globalAnalysis;
    private String modelUsed;
    private List<AiCandidateRankingDTO> rankings;
}
