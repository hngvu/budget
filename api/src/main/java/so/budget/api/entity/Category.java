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
    @UniqueConstraint(name = "uq_categories_plan_id", columnNames = {"plan_id", "id"})
})
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

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

    // --- Official YNAB API Target Fields ---
    /**
     * Target type: TB (Target Balance), TBD (Target Balance by Date), MF (Monthly Funding), NEED (Plan Your Spending), DEBT
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 16)
    private TargetType targetType;

    /**
     * Target amount in milliunits
     */
    @Column(name = "target_amount")
    private Long targetAmount;

    /**
     * Target due date in ISO format (yyyy-MM-dd)
     */
    @Column(name = "target_date")
    private LocalDate targetDate;

    /**
     * The creation month of the target (yyyy-MM-01)
     */
    @Column(name = "target_creation_month")
    private LocalDate targetCreationMonth;

    /**
     * Cadence integer code according to YNAB API:
     * 0 = no repeat, 1 = monthly, 2 = weekly, 13 = yearly (3-12, 14 = other recurrence periods)
     */
    @Column(name = "target_cadence")
    private Integer targetCadence;

    /**
     * Cadence frequency scale (used when targetCadence is 1 or 2, e.g., every 2 weeks/months)
     */
    @Column(name = "target_cadence_frequency")
    private Integer targetCadenceFrequency;

    /**
     * Day of week or day of month:
     * For weekly targets: 0 = Sunday, 1 = Monday, ..., 6 = Saturday.
     * For monthly/yearly targets: 1 - 31 (day of month, 31 = last day of month).
     */
    @Column(name = "target_day")
    private Integer targetDay;

    /**
     * Relevant for NEED targets:
     * true = "Set aside another" (needs the whole amount, unspent funds don't discount next period)
     * false = "Refill up to" (unspent funds from previous period reduce what is needed)
     */
    @Column(name = "target_needs_whole_amount")
    private Boolean targetNeedsWholeAmount;

    /**
     * [YNAB Web / App Extension] Month in which the target is snoozed (yyyy-MM-01)
     */
    @Column(name = "target_snoozed_month")
    private LocalDate targetSnoozedMonth;
}
