package so.budget.api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import so.budget.api.dto.AccountResponse;
import so.budget.api.dto.CreateAccountRequest;
import so.budget.api.dto.UpdateAccountRequest;
import so.budget.api.entity.*;
import so.budget.api.exception.DuplicateResourceException;
import so.budget.api.exception.ForbiddenException;
import so.budget.api.exception.ResourceNotFoundException;
import so.budget.api.repository.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final PlanService planService;
    private final PayeeRepository payeeRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    public List<AccountResponse> getAccounts(UUID planId, UUID userId) {
        planService.findAndVerifyOwnership(planId, userId);

        return accountRepository.findAccountBalancesByPlanId(planId).stream()
                .map(p -> AccountResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .type(AccountType.valueOf(p.getType()))
                        .closed(p.isClosed())
                        .note(p.getNote())
                        .sortOrder(p.getSortOrder())
                        .balance(p.getBalance())
                        .clearedBalance(p.getClearedBalance())
                        .build())
                .toList();
    }

    public AccountResponse getAccount(UUID planId, UUID accountId, UUID userId) {
        planService.findAndVerifyOwnership(planId, userId);
        Account account = findAccount(planId, accountId);

        // Lấy balance từ projection
        return accountRepository.findAccountBalancesByPlanId(planId).stream()
                .filter(p -> p.getId().equals(accountId))
                .findFirst()
                .map(p -> AccountResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .type(AccountType.valueOf(p.getType()))
                        .closed(p.isClosed())
                        .note(p.getNote())
                        .sortOrder(p.getSortOrder())
                        .balance(p.getBalance())
                        .clearedBalance(p.getClearedBalance())
                        .build())
                .orElseGet(() -> toResponse(account, 0L, 0L));
    }

    @Transactional
    public AccountResponse createAccount(UUID planId, UUID userId, CreateAccountRequest request) {
        Plan plan = planService.findAndVerifyOwnership(planId, userId);

        String trimmedName = request.name().trim();
        if (accountRepository.existsByPlanIdAndNameIgnoreCase(planId, trimmedName)) {
            throw new DuplicateResourceException("Tài khoản '" + trimmedName + "' đã tồn tại trong kế hoạch này");
        }

        // Tính sortOrder tự động
        int nextSort = accountRepository.findAllByPlanIdOrderBySortOrderAsc(planId).size();

        Account account = Account.builder()
                .plan(plan)
                .name(trimmedName)
                .type(request.type())
                .note(request.note())
                .sortOrder(nextSort)
                .closed(false)
                .build();
        Account savedAccount = accountRepository.save(account);

        long balance = 0L;
        // Xử lý Starting Balance (Initial Balance) nếu có
        if (request.initialBalance() != null && request.initialBalance() != 0) {
            balance = request.initialBalance();
            createStartingBalanceTransaction(plan, savedAccount, request.initialBalance(), request.initialBalanceDate());
        }

        return toResponse(savedAccount, balance, balance);
    }

    @Transactional
    public AccountResponse updateAccount(UUID planId, UUID accountId, UUID userId, UpdateAccountRequest request) {
        planService.findAndVerifyOwnership(planId, userId);
        Account account = findAccount(planId, accountId);

        String trimmedName = request.name().trim();
        if (!account.getName().equalsIgnoreCase(trimmedName) &&
            accountRepository.existsByPlanIdAndNameIgnoreCase(planId, trimmedName)) {
            throw new DuplicateResourceException("Tài khoản '" + trimmedName + "' đã tồn tại");
        }

        account.setName(trimmedName);
        account.setType(request.type());
        account.setNote(request.note());
        if (request.sortOrder() != null) {
            account.setSortOrder(request.sortOrder());
        }

        Account updated = accountRepository.save(account);
        return getAccount(planId, updated.getId(), userId);
    }

    @Transactional
    public AccountResponse closeAccount(UUID planId, UUID accountId, UUID userId) {
        planService.findAndVerifyOwnership(planId, userId);
        Account account = findAccount(planId, accountId);

        // Kiểm tra số dư phải bằng 0 trước khi đóng
        AccountResponse balanceInfo = getAccount(planId, accountId, userId);
        if (balanceInfo.balance() != null && balanceInfo.balance() != 0) {
            throw new ForbiddenException("Số dư tài khoản phải bằng 0 trước khi đóng. Vui lòng chuyển hoặc điều chỉnh số dư.");
        }

        account.setClosed(true);
        accountRepository.save(account);
        return getAccount(planId, accountId, userId);
    }

    @Transactional
    public AccountResponse reopenAccount(UUID planId, UUID accountId, UUID userId) {
        planService.findAndVerifyOwnership(planId, userId);
        Account account = findAccount(planId, accountId);

        account.setClosed(false);
        accountRepository.save(account);
        return getAccount(planId, accountId, userId);
    }

    @Transactional
    public void deleteAccount(UUID planId, UUID accountId, UUID userId) {
        planService.findAndVerifyOwnership(planId, userId);
        Account account = findAccount(planId, accountId);

        // Chỉ cho phép xóa nếu không có giao dịch nào
        long txCount = transactionRepository.countByAccountId(accountId);
        if (txCount > 0) {
            throw new ForbiddenException("Tài khoản đã phát sinh " + txCount + " giao dịch. Vui lòng đóng (close) thay vì xóa.");
        }

        accountRepository.delete(account);
    }

    private void createStartingBalanceTransaction(Plan plan, Account account, Long amount, LocalDate date) {
        LocalDate txDate = date != null ? date : LocalDate.now();

        // 1. Payee "Starting Balance"
        Payee startingBalancePayee = payeeRepository.findByPlanIdAndNameIgnoreCase(plan.getId(), "Starting Balance")
                .orElseGet(() -> payeeRepository.save(Payee.builder()
                        .plan(plan)
                        .name("Starting Balance")
                        .build()));

        // 2. Inflow Category: "Inflow: Ready to Assign"
        Category readyToAssign = categoryRepository.findByPlanIdAndSystemKind(plan.getId(), "INFLOW_READY_TO_ASSIGN")
                .orElse(null);

        // 3. Transaction
        Transaction initialTx = Transaction.builder()
                .plan(plan)
                .account(account)
                .payee(startingBalancePayee)
                .category(readyToAssign)
                .amount(amount)
                .date(txDate)
                .memo("Starting Balance")
                .cleared(true)
                .build();
        transactionRepository.save(initialTx);
    }

    private Account findAccount(UUID planId, UUID accountId) {
        return accountRepository.findByIdAndPlanId(accountId, planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }

    private AccountResponse toResponse(Account account, Long balance, Long clearedBalance) {
        return AccountResponse.builder()
                .id(account.getId())
                .name(account.getName())
                .type(account.getType())
                .closed(account.isClosed())
                .note(account.getNote())
                .sortOrder(account.getSortOrder())
                .balance(balance)
                .clearedBalance(clearedBalance)
                .build();
    }
}
