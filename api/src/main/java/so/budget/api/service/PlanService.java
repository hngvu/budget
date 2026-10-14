package so.budget.api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import so.budget.api.dto.CreatePlanRequest;
import so.budget.api.dto.PlanResponse;
import so.budget.api.dto.UpdatePlanRequest;
import so.budget.api.entity.Plan;
import so.budget.api.entity.User;
import so.budget.api.exception.ForbiddenException;
import so.budget.api.exception.ResourceNotFoundException;
import so.budget.api.repository.PlanRepository;
import so.budget.api.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;
    private final UserRepository userRepository;

    public List<PlanResponse> getPlans(UUID userId) {
        return planRepository.findAllByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public PlanResponse getPlan(UUID planId, UUID userId) {
        Plan plan = findAndVerifyOwnership(planId, userId);
        return toResponse(plan);
    }

    @Transactional
    public PlanResponse createPlan(UUID userId, CreatePlanRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        Plan plan = Plan.builder()
                .name(request.name().trim())
                .currencyCode(request.currencyCode().toUpperCase())
                .decimalDigits(request.decimalDigits())
                .user(user)
                .build();

        // Trigger trg_plan_defaults sẽ tự động sinh Internal Master Category + RTA category
        Plan saved = planRepository.save(plan);
        return toResponse(saved);
    }

    @Transactional
    public PlanResponse updatePlan(UUID planId, UUID userId, UpdatePlanRequest request) {
        Plan plan = findAndVerifyOwnership(planId, userId);

        plan.setName(request.name().trim());
        plan.setCurrencyCode(request.currencyCode().toUpperCase());
        plan.setDecimalDigits(request.decimalDigits());

        return toResponse(planRepository.save(plan));
    }

    @Transactional
    public void deletePlan(UUID planId, UUID userId) {
        Plan plan = findAndVerifyOwnership(planId, userId);
        // Cascade delete đã cấu hình ở database và JPA mapping
        planRepository.delete(plan);
    }

    public Plan findAndVerifyOwnership(UUID planId, UUID userId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch ngân sách"));

        if (!plan.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Bạn không có quyền truy cập kế hoạch ngân sách này");
        }
        return plan;
    }

    private PlanResponse toResponse(Plan plan) {
        return PlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .currencyCode(plan.getCurrencyCode())
                .decimalDigits(plan.getDecimalDigits())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}
