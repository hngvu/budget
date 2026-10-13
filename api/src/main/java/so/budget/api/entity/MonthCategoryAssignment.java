package so.budget.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "month_category_assignments")
@IdClass(MonthCategoryAssignmentId.class)
public class MonthCategoryAssignment extends BaseEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Id
    @Column(name = "month", nullable = false)
    private LocalDate month;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    /**
     * Assigned amount in milliunits
     */
    @Column(name = "assigned", nullable = false)
    @Builder.Default
    private Long assigned = 0L;
}
