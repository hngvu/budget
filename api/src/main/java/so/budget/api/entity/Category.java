package so.budget.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "categories", uniqueConstraints = {
    @UniqueConstraint(name = "uq_categories_budget_id", columnNames = {"budget_id", "id"})
})
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_group_id", nullable = false)
    private CategoryGroup categoryGroup;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "hidden", nullable = false)
    @Builder.Default
    private boolean hidden = false;

    @Column(name = "note")
    private String note;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    /**
     * System marker: 'INFLOW_READY_TO_ASSIGN' marks the hidden category that receives income.
     */
    @Column(name = "system_kind", length = 32)
    private String systemKind;

    // --- YNAB Targets 2.0 ---
    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", length = 16)
    private GoalType goalType;

    /**
     * Target amount in milliunits
     */
    @Column(name = "goal_target")
    private Long goalTarget;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_cadence", length = 8)
    private GoalCadence goalCadence;

    @Column(name = "goal_cadence_frequency", nullable = false)
    @Builder.Default
    private Short goalCadenceFrequency = 1;

    /**
     * 1-7 weekly (day of week: 1=Monday, 7=Sunday), 1-31 otherwise
     */
    @Column(name = "goal_day")
    private Short goalDay;

    /**
     * Due month for custom targets (yyyy-MM-01)
     */
    @Column(name = "goal_target_month")
    private LocalDate goalTargetMonth;

    @Column(name = "goal_repeat", nullable = false)
    @Builder.Default
    private boolean goalRepeat = true;

    /**
     * Month in which the target is snoozed (yyyy-MM-01)
     */
    @Column(name = "goal_snoozed_month")
    private LocalDate goalSnoozedMonth;
}
