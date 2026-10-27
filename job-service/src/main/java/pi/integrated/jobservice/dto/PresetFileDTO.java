package pi.integrated.jobservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresetFileDTO {
    private String id;
    private String name;
    private String path;
    private String language;
    private String description;
    private String code;
}
