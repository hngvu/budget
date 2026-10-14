package so.budget.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import so.budget.api.dto.*;
import so.budget.api.service.CategoryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans/{planId}")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // --- Category Groups ---

    @GetMapping("/category-groups")
    public List<CategoryGroupResponse> getCategoryGroups(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return categoryService.getCategoryGroupsWithCategories(planId, getUserId(jwt));
    }

    @PostMapping("/category-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryGroupResponse createCategoryGroup(
            @PathVariable UUID planId,
            @Valid @RequestBody CreateCategoryGroupRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return categoryService.createCategoryGroup(planId, getUserId(jwt), request);
    }

    @PutMapping("/category-groups/{groupId}")
    public CategoryGroupResponse updateCategoryGroup(
            @PathVariable UUID planId,
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateCategoryGroupRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return categoryService.updateCategoryGroup(planId, groupId, getUserId(jwt), request);
    }

    @DeleteMapping("/category-groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategoryGroup(
            @PathVariable UUID planId,
            @PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        categoryService.deleteCategoryGroup(planId, groupId, getUserId(jwt));
    }

    // --- Categories ---

    @PostMapping("/category-groups/{groupId}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(
            @PathVariable UUID planId,
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateCategoryRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return categoryService.createCategory(planId, groupId, getUserId(jwt), request);
    }

    @PutMapping("/categories/{categoryId}")
    public CategoryResponse updateCategory(
            @PathVariable UUID planId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return categoryService.updateCategory(planId, categoryId, getUserId(jwt), request);
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(
            @PathVariable UUID planId,
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        categoryService.deleteCategory(planId, categoryId, getUserId(jwt));
    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
