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
@Table(name = "scheduled_transactions")
public class ScheduledTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /**
     * Date of first occurrence
     */
    @Column(name = "date_first", nullable = false)
    private LocalDate dateFirst;

    /**
     * Date of next upcoming occurrence
     */
    @Column(name = "date_next", nullable = false)
    private LocalDate dateNext;

    /**
     * Recurrence frequency: NEVER, DAILY, WEEKLY, MONTHLY, YEARLY, etc.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 32)
    @Builder.Default
    private RecurrenceFrequency frequency = RecurrenceFrequency.NEVER;

    /**
     * Amount in milliunits (+ for inflow, - for outflow)
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "memo")
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_color", length = 16)
    private FlagColor flagColor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_id")
    private Payee payee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /**
     * If this scheduled transaction is a transfer, destination/source account
     */
    @Column(name = "transfer_account_id")
    private UUID transferAccountId;

    @OneToMany(mappedBy = "scheduledTransaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ScheduledSubTransaction> subtransactions = new ArrayList<>();
}
