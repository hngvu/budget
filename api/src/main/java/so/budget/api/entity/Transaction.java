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
@Table(name = "transactions", uniqueConstraints = {
    @UniqueConstraint(name = "uq_transactions_plan_id", columnNames = {"plan_id", "id"})
})
public class Transaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * (+) Inflow, (-) Outflow in milliunits
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_id")
    private Payee payee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "memo")
    private String memo;

    @Column(name = "cleared", nullable = false)
    @Builder.Default
    private boolean cleared = false;

    @Column(name = "transfer_account_id")
    private UUID transferAccountId;

    @Column(name = "transfer_transaction_id")
    private UUID transferTransactionId;

    @Column(name = "import_id")
    private String importId;
}
