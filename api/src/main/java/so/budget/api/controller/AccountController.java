package so.budget.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import so.budget.api.dto.AccountResponse;
import so.budget.api.dto.CreateAccountRequest;
import so.budget.api.dto.UpdateAccountRequest;
import so.budget.api.service.AccountService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans/{planId}/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public List<AccountResponse> getAccounts(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return accountService.getAccounts(planId, getUserId(jwt));
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(
            @PathVariable UUID planId,
            @PathVariable UUID accountId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return accountService.getAccount(planId, accountId, getUserId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(
            @PathVariable UUID planId,
            @Valid @RequestBody CreateAccountRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return accountService.createAccount(planId, getUserId(jwt), request);
    }

    @PutMapping("/{accountId}")
    public AccountResponse updateAccount(
            @PathVariable UUID planId,
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return accountService.updateAccount(planId, accountId, getUserId(jwt), request);
    }

    @PutMapping("/{accountId}/close")
    public AccountResponse closeAccount(
            @PathVariable UUID planId,
            @PathVariable UUID accountId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return accountService.closeAccount(planId, accountId, getUserId(jwt));
    }

    @PutMapping("/{accountId}/reopen")
    public AccountResponse reopenAccount(
            @PathVariable UUID planId,
            @PathVariable UUID accountId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return accountService.reopenAccount(planId, accountId, getUserId(jwt));
    }

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(
            @PathVariable UUID planId,
            @PathVariable UUID accountId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        accountService.deleteAccount(planId, accountId, getUserId(jwt));
    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
