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
@Table(name = "transactions")
public class Transaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * Transaction amount in milliunits (positive for inflow/income, negative for outflow/expense)
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "memo")
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "cleared", nullable = false, length = 16)
    @Builder.Default
    private ClearedStatus cleared = ClearedStatus.UNCLEARED;

    @Column(name = "approved", nullable = false)
    @Builder.Default
    private boolean approved = true;

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
     * In transfer transactions, transferAccountId is the destination or source account.
     * transferTransactionId links to the opposing split or matched transaction on the other account.
     */
    @Column(name = "transfer_account_id")
    private UUID transferAccountId;

    @Column(name = "transfer_transaction_id")
    private UUID transferTransactionId;

    /**
     * Matched transaction id for bank import matching
     */
    @Column(name = "matched_transaction_id")
    private UUID matchedTransactionId;

    @Column(name = "import_id")
    private String importId;

    @OneToMany(mappedBy = "parentTransaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SubTransaction> subtransactions = new ArrayList<>();
}
