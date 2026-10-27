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
public class AiCandidateRankingDTO {
    private Long applicationId;
    private Long teacherId;
    private String teacherName;
    private Integer rank;
    private Integer aiScore;
    private String recommendation;
    private String summary;
    private List<String> keyStrengths;
    private List<String> missingSkills;
    private String justification;
    private String cvSnippet;
}
