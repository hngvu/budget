package so.budget.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import so.budget.api.entity.Plan;

import java.util.List;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
    List<Plan> findAllByUserId(UUID userId);
    long countByUserId(UUID userId);
}
