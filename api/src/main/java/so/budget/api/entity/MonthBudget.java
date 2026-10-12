package so.budget.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "month_budgets")
public class MonthBudget extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    /**
     * First day of the month (e.g. 2026-10-01)
     */
    @Column(name = "month", nullable = false)
    private LocalDate month;

    @Column(name = "note")
    private String note;

    /**
     * Total income for the month in milliunits
     */
    @Column(name = "income", nullable = false)
    @Builder.Default
    private Long income = 0L;

    /**
     * Total budgeted in the month in milliunits
     */
    @Column(name = "budgeted", nullable = false)
    @Builder.Default
    private Long budgeted = 0L;

    /**
     * Total activity/spending in the month in milliunits
     */
    @Column(name = "activity", nullable = false)
    @Builder.Default
    private Long activity = 0L;

    /**
     * "To be Budgeted" / "Ready to Assign" amount in milliunits
     */
    @Column(name = "to_be_budgeted", nullable = false)
    @Builder.Default
    private Long toBeBudgeted = 0L;

    @Column(name = "age_of_money")
    private Integer ageOfMoney;

    @OneToMany(mappedBy = "monthBudget", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MonthCategoryBudget> categoryBudgets = new ArrayList<>();
}
