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
@Table(name = "accounts", uniqueConstraints = {
    @UniqueConstraint(name = "uq_accounts_budget_id", columnNames = {"budget_id", "id"})
})
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

    @Column(name = "type", nullable = false, length = 16)
    private AccountType type;

    @Column(name = "closed", nullable = false)
    @Builder.Default
    private boolean closed = false;

    @Column(name = "note")
    private String note;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
