package so.budget.api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import so.budget.api.dto.*;
import so.budget.api.entity.Category;
import so.budget.api.entity.CategoryGroup;
import so.budget.api.entity.Plan;
import so.budget.api.exception.DuplicateResourceException;
import so.budget.api.exception.ForbiddenException;
import so.budget.api.exception.ResourceNotFoundException;
import so.budget.api.repository.CategoryGroupRepository;
import so.budget.api.repository.CategoryRepository;
import so.budget.api.repository.TransactionRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryGroupRepository categoryGroupRepository;
    private final CategoryRepository categoryRepository;
    private final PlanService planService;
    private final TransactionRepository transactionRepository;

    public List<CategoryGroupResponse> getCategoryGroupsWithCategories(UUID planId, UUID userId) {
        planService.findAndVerifyOwnership(planId, userId);

        List<CategoryGroup> groups = categoryGroupRepository.findAllByPlanIdAndHiddenFalseOrderBySortOrderAsc(planId);
        List<Category> allCategories = categoryRepository.findAllByPlanIdAndHiddenFalseOrderBySortOrderAsc(planId);

        return groups.stream()
                .map(g -> {
                    List<CategoryResponse> categoriesInGroup = allCategories.stream()
                            .filter(c -> c.getCategoryGroup().getId().equals(g.getId()))
                            .map(this::toCategoryResponse)
                            .toList();

                    return CategoryGroupResponse.builder()
                            .id(g.getId())
                            .name(g.getName())
                            .hidden(g.isHidden())
                            .sortOrder(g.getSortOrder())
                            .categories(categoriesInGroup)
                            .build();
                })
                .toList();
    }

    @Transactional
    public CategoryGroupResponse createCategoryGroup(UUID planId, UUID userId, CreateCategoryGroupRequest request) {
        Plan plan = planService.findAndVerifyOwnership(planId, userId);

        String trimmedName = request.name().trim();
        if (categoryGroupRepository.existsByPlanIdAndNameIgnoreCase(planId, trimmedName)) {
            throw new DuplicateResourceException("Nhóm danh mục '" + trimmedName + "' đã tồn tại");
        }

        int nextSort = categoryGroupRepository.findAllByPlanIdOrderBySortOrderAsc(planId).size();

        CategoryGroup group = CategoryGroup.builder()
                .plan(plan)
                .name(trimmedName)
                .hidden(false)
                .sortOrder(nextSort)
                .build();

        CategoryGroup saved = categoryGroupRepository.save(group);
        return CategoryGroupResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .hidden(saved.isHidden())
                .sortOrder(saved.getSortOrder())
                .categories(List.of())
                .build();
    }

    @Transactional
    public CategoryGroupResponse updateCategoryGroup(UUID planId, UUID groupId, UUID userId, UpdateCategoryGroupRequest request) {
        planService.findAndVerifyOwnership(planId, userId);
        CategoryGroup group = findGroup(planId, groupId);

        String trimmedName = request.name().trim();
        if (!group.getName().equalsIgnoreCase(trimmedName) &&
            categoryGroupRepository.existsByPlanIdAndNameIgnoreCase(planId, trimmedName)) {
            throw new DuplicateResourceException("Nhóm danh mục '" + trimmedName + "' đã tồn tại");
        }

        group.setName(trimmedName);
        if (request.hidden() != null) group.setHidden(request.hidden());
        if (request.sortOrder() != null) group.setSortOrder(request.sortOrder());

        CategoryGroup saved = categoryGroupRepository.save(group);
        return CategoryGroupResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .hidden(saved.isHidden())
                .sortOrder(saved.getSortOrder())
                .categories(saved.getCategories().stream().map(this::toCategoryResponse).toList())
                .build();
    }

    @Transactional
    public void deleteCategoryGroup(UUID planId, UUID groupId, UUID userId) {
        planService.findAndVerifyOwnership(planId, userId);
        CategoryGroup group = findGroup(planId, groupId);

        // Kiểm tra xem các category con có transaction nào không
        for (Category c : group.getCategories()) {
            long count = transactionRepository.countByCategoryId(c.getId());
            if (count > 0) {
                throw new ForbiddenException("Danh mục con '" + c.getName() + "' đã có giao dịch. Hãy ẩn nhóm thay vì xóa.");
            }
        }

        categoryGroupRepository.delete(group);
    }

    @Transactional
    public CategoryResponse createCategory(UUID planId, UUID groupId, UUID userId, CreateCategoryRequest request) {
        Plan plan = planService.findAndVerifyOwnership(planId, userId);
        CategoryGroup group = findGroup(planId, groupId);

        String trimmedName = request.name().trim();
        if (categoryRepository.existsByCategoryGroupIdAndNameIgnoreCase(groupId, trimmedName)) {
            throw new DuplicateResourceException("Danh mục '" + trimmedName + "' đã tồn tại trong nhóm này");
        }

        int nextSort = categoryRepository.findAllByPlanIdOrderBySortOrderAsc(planId).size();

        Category category = Category.builder()
                .plan(plan)
                .categoryGroup(group)
                .name(trimmedName)
                .note(request.note())
                .hidden(false)
                .sortOrder(nextSort)
                .targetType(request.targetType())
                .targetAmount(request.targetAmount())
                .targetDate(request.targetDate())
                .targetCreationMonth(request.targetCreationMonth())
                .targetCadence(request.targetCadence())
                .targetCadenceFrequency(request.targetCadenceFrequency())
                .targetDay(request.targetDay())
                .targetNeedsWholeAmount(request.targetNeedsWholeAmount())
                .build();

        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(UUID planId, UUID categoryId, UUID userId, UpdateCategoryRequest request) {
        planService.findAndVerifyOwnership(planId, userId);
        Category category = findCategory(planId, categoryId);

        String trimmedName = request.name().trim();
        if (!category.getName().equalsIgnoreCase(trimmedName) &&
            categoryRepository.existsByCategoryGroupIdAndNameIgnoreCase(category.getCategoryGroup().getId(), trimmedName)) {
            throw new DuplicateResourceException("Danh mục '" + trimmedName + "' đã tồn tại trong nhóm này");
        }

        category.setName(trimmedName);
        category.setNote(request.note());
        if (request.hidden() != null) category.setHidden(request.hidden());
        if (request.sortOrder() != null) category.setSortOrder(request.sortOrder());
        category.setTargetType(request.targetType());
        category.setTargetAmount(request.targetAmount());
        category.setTargetDate(request.targetDate());
        category.setTargetCreationMonth(request.targetCreationMonth());
        category.setTargetCadence(request.targetCadence());
        category.setTargetCadenceFrequency(request.targetCadenceFrequency());
        category.setTargetDay(request.targetDay());
        category.setTargetNeedsWholeAmount(request.targetNeedsWholeAmount());

        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(UUID planId, UUID categoryId, UUID userId) {
        planService.findAndVerifyOwnership(planId, userId);
        Category category = findCategory(planId, categoryId);

        if ("INFLOW_READY_TO_ASSIGN".equalsIgnoreCase(category.getSystemKind())) {
            throw new ForbiddenException("Không thể xóa danh mục hệ thống 'Inflow: Ready to Assign'");
        }

        long txCount = transactionRepository.countByCategoryId(categoryId);
        if (txCount > 0) {
            throw new ForbiddenException("Danh mục đã có " + txCount + " giao dịch. Vui lòng ẩn (hidden) thay vì xóa.");
        }

        categoryRepository.delete(category);
    }

    private CategoryGroup findGroup(UUID planId, UUID groupId) {
        return categoryGroupRepository.findByIdAndPlanId(groupId, planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm danh mục"));
    }

    private Category findCategory(UUID planId, UUID categoryId) {
        return categoryRepository.findByIdAndPlanId(categoryId, planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục"));
    }

    private CategoryResponse toCategoryResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .categoryGroupId(category.getCategoryGroup().getId())
                .name(category.getName())
                .hidden(category.isHidden())
                .note(category.getNote())
                .sortOrder(category.getSortOrder())
                .systemKind(category.getSystemKind())
                .targetType(category.getTargetType())
                .targetAmount(category.getTargetAmount())
                .targetDate(category.getTargetDate())
                .targetCreationMonth(category.getTargetCreationMonth())
                .targetCadence(category.getTargetCadence())
                .targetCadenceFrequency(category.getTargetCadenceFrequency())
                .targetDay(category.getTargetDay())
                .targetNeedsWholeAmount(category.getTargetNeedsWholeAmount())
                .build();
    }
}
