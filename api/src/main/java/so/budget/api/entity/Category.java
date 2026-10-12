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
@Table(name = "categories")
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_group_id", nullable = false)
    private CategoryGroup categoryGroup;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "note")
    private String note;

    /**
     * Budgeted amount in the current month in milliunits
     */
    @Column(name = "budgeted", nullable = false)
    @Builder.Default
    private Long budgeted = 0L;

    /**
     * Activity/spending amount in the current month in milliunits
     */
    @Column(name = "activity", nullable = false)
    @Builder.Default
    private Long activity = 0L;

    /**
     * Available balance in milliunits
     */
    @Column(name = "balance", nullable = false)
    @Builder.Default
    private Long balance = 0L;

    // --- YNAB Targets 2.0 (Goals) ---
    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", length = 32)
    private GoalType goalType;

    /**
     * Target amount in milliunits
     */
    @Column(name = "goal_target")
    private Long goalTarget;

    /**
     * Cadence frequency period: WEEKLY, MONTHLY, YEARLY, CUSTOM
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "goal_cadence", length = 16)
    private GoalCadence goalCadence;

    /**
     * Cadence multiplier (e.g. 1 = every period, 3 = every 3 months / quarterly, 2 = every 2 years)
     */
    @Column(name = "goal_cadence_frequency")
    private Integer goalCadenceFrequency;

    /**
     * Due day in month (1-31) or day of week (1 = Monday, 7 = Sunday for weekly target)
     */
    @Column(name = "goal_day")
    private Integer goalDay;

    /**
     * Deadline month for target with fixed completion date (e.g. 2026-12-01)
     */
    @Column(name = "goal_target_month")
    private LocalDate goalTargetMonth;

    /**
     * Total amount needed in current month to stay on track (milliunits)
     */
    @Column(name = "goal_under_funded")
    private Long goalUnderFunded;

    /**
     * Total remaining amount to reach target (milliunits)
     */
    @Column(name = "goal_overall_left")
    private Long goalOverallLeft;

    /**
     * Whether target repeats after completion / deadline (e.g. yearly insurance repeats every year)
     */
    @Column(name = "goal_repeat", nullable = false)
    @Builder.Default
    private boolean goalRepeat = true;

    /**
     * Completion progress percentage (0 - 100)
     */
    @Column(name = "goal_percentage_complete")
    private Integer goalPercentageComplete;
}
