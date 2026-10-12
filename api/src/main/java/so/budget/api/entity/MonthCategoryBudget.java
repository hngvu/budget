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
@Table(name = "month_category_budgets")
@IdClass(MonthCategoryBudgetId.class)
public class MonthCategoryBudget extends BaseEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Id
    @Column(name = "month", nullable = false)
    private LocalDate month;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    /**
     * Assigned amount in milliunits
     */
    @Column(name = "budgeted", nullable = false)
    @Builder.Default
    private Long budgeted = 0L;
}
