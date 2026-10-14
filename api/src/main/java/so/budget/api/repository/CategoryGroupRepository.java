package so.budget.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import so.budget.api.entity.CategoryGroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryGroupRepository extends JpaRepository<CategoryGroup, UUID> {

    List<CategoryGroup> findAllByPlanIdAndHiddenFalseOrderBySortOrderAsc(UUID planId);

    List<CategoryGroup> findAllByPlanIdOrderBySortOrderAsc(UUID planId);

    Optional<CategoryGroup> findByIdAndPlanId(UUID id, UUID planId);

    boolean existsByPlanIdAndNameIgnoreCase(UUID planId, String name);
}
