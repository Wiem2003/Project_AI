package pi.integrated.preevaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExplainMistakeResponse {
    private String explanation;
    @Builder.Default
    private List<String> sources = new ArrayList<>();
}
