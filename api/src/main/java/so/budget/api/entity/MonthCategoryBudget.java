package so.budget.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "month_category_budgets", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"month_budget_id", "category_id"})
})
public class MonthCategoryBudget extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "month_budget_id", nullable = false)
    private MonthBudget monthBudget;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * Budgeted (assigned) amount for this category in this month (milliunits)
     */
    @Column(name = "budgeted", nullable = false)
    @Builder.Default
    private Long budgeted = 0L;

    /**
     * Activity (spending) in this category for this month (milliunits)
     */
    @Column(name = "activity", nullable = false)
    @Builder.Default
    private Long activity = 0L;

    /**
     * Available balance at end of this month (milliunits)
     */
    @Column(name = "balance", nullable = false)
    @Builder.Default
    private Long balance = 0L;
}
