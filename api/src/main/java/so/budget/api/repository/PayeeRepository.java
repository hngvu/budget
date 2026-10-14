package so.budget.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import so.budget.api.entity.Payee;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayeeRepository extends JpaRepository<Payee, UUID> {

    List<Payee> findAllByPlanIdOrderByNameAsc(UUID planId);

    Optional<Payee> findByIdAndPlanId(UUID id, UUID planId);

    Optional<Payee> findByPlanIdAndNameIgnoreCase(UUID planId, String name);

    boolean existsByPlanIdAndNameIgnoreCase(UUID planId, String name);
}
