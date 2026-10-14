package so.budget.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import so.budget.api.entity.Transaction;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findAllByPlanIdOrderByDateDescCreatedAtDesc(UUID planId);

    List<Transaction> findAllByAccountIdOrderByDateDescCreatedAtDesc(UUID accountId);

    long countByAccountId(UUID accountId);

    long countByCategoryId(UUID categoryId);

    long countByPayeeId(UUID payeeId);

    @Modifying
    @Query("UPDATE Transaction t SET t.payee.id = :targetPayeeId WHERE t.plan.id = :planId AND t.payee.id IN :sourcePayeeIds")
    int reassignPayees(
            @Param("planId") UUID planId,
            @Param("targetPayeeId") UUID targetPayeeId,
            @Param("sourcePayeeIds") List<UUID> sourcePayeeIds
    );
}
