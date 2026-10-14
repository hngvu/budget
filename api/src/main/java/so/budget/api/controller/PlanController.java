package so.budget.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import so.budget.api.dto.CreatePlanRequest;
import so.budget.api.dto.PlanResponse;
import so.budget.api.dto.UpdatePlanRequest;
import so.budget.api.service.PlanService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @GetMapping
    public List<PlanResponse> getPlans(@AuthenticationPrincipal Jwt jwt) {
        return planService.getPlans(getUserId(jwt));
    }

    @GetMapping("/{planId}")
    public PlanResponse getPlan(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return planService.getPlan(planId, getUserId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse createPlan(
            @Valid @RequestBody CreatePlanRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return planService.createPlan(getUserId(jwt), request);
    }

    @PutMapping("/{planId}")
    public PlanResponse updatePlan(
            @PathVariable UUID planId,
            @Valid @RequestBody UpdatePlanRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return planService.updatePlan(planId, getUserId(jwt), request);
    }

    @DeleteMapping("/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        planService.deletePlan(planId, getUserId(jwt));
    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
