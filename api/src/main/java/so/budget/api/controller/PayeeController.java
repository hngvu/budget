package so.budget.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import so.budget.api.dto.CreatePayeeRequest;
import so.budget.api.dto.MergePayeeRequest;
import so.budget.api.dto.PayeeResponse;
import so.budget.api.dto.UpdatePayeeRequest;
import so.budget.api.service.PayeeService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans/{planId}/payees")
@RequiredArgsConstructor
public class PayeeController {

    private final PayeeService payeeService;

    @GetMapping
    public List<PayeeResponse> getPayees(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return payeeService.getPayees(planId, getUserId(jwt));
    }

    @GetMapping("/{payeeId}")
    public PayeeResponse getPayee(
            @PathVariable UUID planId,
            @PathVariable UUID payeeId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return payeeService.getPayee(planId, payeeId, getUserId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PayeeResponse createPayee(
            @PathVariable UUID planId,
            @Valid @RequestBody CreatePayeeRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return payeeService.createPayee(planId, getUserId(jwt), request);
    }

    @PutMapping("/{payeeId}")
    public PayeeResponse updatePayee(
            @PathVariable UUID planId,
            @PathVariable UUID payeeId,
            @Valid @RequestBody UpdatePayeeRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return payeeService.updatePayee(planId, payeeId, getUserId(jwt), request);
    }

    @PostMapping("/merge")
    public PayeeResponse mergePayees(
            @PathVariable UUID planId,
            @Valid @RequestBody MergePayeeRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return payeeService.mergePayees(planId, getUserId(jwt), request);
    }

    @DeleteMapping("/{payeeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayee(
            @PathVariable UUID planId,
            @PathVariable UUID payeeId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        payeeService.deletePayee(planId, payeeId, getUserId(jwt));
    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
