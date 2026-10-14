package so.budget.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import so.budget.api.entity.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByPlanIdAndHiddenFalseOrderBySortOrderAsc(UUID planId);

    List<Category> findAllByPlanIdOrderBySortOrderAsc(UUID planId);

    Optional<Category> findByIdAndPlanId(UUID id, UUID planId);

    Optional<Category> findByPlanIdAndSystemKind(UUID planId, String systemKind);

    boolean existsByCategoryGroupIdAndNameIgnoreCase(UUID categoryGroupId, String name);
}
