package so.budget.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private AccountType type;

    @Column(name = "on_budget", nullable = false)
    private boolean onBudget;

    @Column(name = "closed", nullable = false)
    private boolean closed;

    @Column(name = "note")
    private String note;

    /**
     * Balances are stored in milliunits (1 USD = 1000 milliunits, 10,000 VND = 10,000,000 milliunits)
     */
    @Column(name = "balance", nullable = false)
    @Builder.Default
    private Long balance = 0L;

    @Column(name = "cleared_balance", nullable = false)
    @Builder.Default
    private Long clearedBalance = 0L;

    @Column(name = "uncleared_balance", nullable = false)
    @Builder.Default
    private Long unclearedBalance = 0L;

    /**
     * Transfer payee associated with this account (transferring to/from this account uses this payee)
     */
    @Column(name = "transfer_payee_id")
    private UUID transferPayeeId;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();
}
