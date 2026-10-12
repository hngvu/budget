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
@Table(name = "payees")
public class Payee extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * If this payee is an internal transfer payee, transferAccountId points to the related Account
     */
    @Column(name = "transfer_account_id")
    private UUID transferAccountId;
}
