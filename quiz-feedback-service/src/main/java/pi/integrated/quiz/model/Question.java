package pi.integrated.quiz.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Question {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 1000)
    private String questionText;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;
    
    @ElementCollection
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "option_text")
    private List<String> options = new ArrayList<>();
    
    @Column(nullable = false)
    private String correctAnswer;
    
    private Integer points = 1;
    
    @Column(length = 1000)
    private String explanation;
    
    private Integer orderIndex;

    // Source in the course for questions generated from a lesson (RAG); null for other questions.
    /** e.g. "Leçon 1 – 02:21" or "PDF p. 2". */
    private String sourceLabel;

    /** Front-end link to the passage, e.g. /courses/1/lessons/3?t=141. */
    @Column(length = 500)
    private String sourceUrl;

    /** Course passage the question was written from, shown to the student after a wrong answer. */
    @Column(columnDefinition = "TEXT")
    private String sourceExcerpt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;
    
    @Column(name = "quiz_id", insertable = false, updatable = false)
    private Long quizId;
}
