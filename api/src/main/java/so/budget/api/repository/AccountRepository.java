package so.budget.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import so.budget.api.entity.Account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findAllByPlanIdOrderBySortOrderAsc(UUID planId);

    Optional<Account> findByIdAndPlanId(UUID id, UUID planId);

    boolean existsByPlanIdAndNameIgnoreCase(UUID planId, String name);

    interface AccountBalanceProjection {
        UUID getId();
        String getName();
        String getType();
        boolean isClosed();
        String getNote();
        Integer getSortOrder();
        Long getBalance();
        Long getClearedBalance();
    }

    @Query(value = """
            SELECT a.id AS id,
                   a.name AS name,
                   a.type AS type,
                   a.closed AS closed,
                   a.note AS note,
                   a.sort_order AS sortOrder,
                   COALESCE(b.balance, 0) AS balance,
                   COALESCE(b.cleared_balance, 0) AS clearedBalance
            FROM accounts a
            LEFT JOIN v_account_balances b ON b.account_id = a.id AND b.plan_id = a.plan_id
            WHERE a.plan_id = :planId
            ORDER BY a.sort_order ASC, a.name ASC
            """, nativeQuery = true)
    List<AccountBalanceProjection> findAccountBalancesByPlanId(@Param("planId") UUID planId);
}
