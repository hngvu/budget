package so.budget.api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import so.budget.api.dto.CreatePayeeRequest;
import so.budget.api.dto.MergePayeeRequest;
import so.budget.api.dto.PayeeResponse;
import so.budget.api.dto.UpdatePayeeRequest;
import so.budget.api.entity.Payee;
import so.budget.api.entity.Plan;
import so.budget.api.exception.DuplicateResourceException;
import so.budget.api.exception.ForbiddenException;
import so.budget.api.exception.ResourceNotFoundException;
import so.budget.api.repository.PayeeRepository;
import so.budget.api.repository.TransactionRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayeeService {

    private final PayeeRepository payeeRepository;
    private final PlanService planService;
    private final TransactionRepository transactionRepository;

    public List<PayeeResponse> getPayees(UUID planId, UUID userId) {
        planService.findAndVerifyOwnership(planId, userId);

        return payeeRepository.findAllByPlanIdOrderByNameAsc(planId).stream()
                .map(p -> PayeeResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .build())
                .toList();
    }

    public PayeeResponse getPayee(UUID planId, UUID payeeId, UUID userId) {
        planService.findAndVerifyOwnership(planId, userId);
        Payee payee = findPayee(planId, payeeId);
        return PayeeResponse.builder()
                .id(payee.getId())
                .name(payee.getName())
                .build();
    }

    @Transactional
    public PayeeResponse createPayee(UUID planId, UUID userId, CreatePayeeRequest request) {
        Plan plan = planService.findAndVerifyOwnership(planId, userId);

        String trimmedName = request.name().trim();
        if (payeeRepository.existsByPlanIdAndNameIgnoreCase(planId, trimmedName)) {
            throw new DuplicateResourceException("Đối tượng '" + trimmedName + "' đã tồn tại trong kế hoạch này");
        }

        Payee payee = Payee.builder()
                .plan(plan)
                .name(trimmedName)
                .build();

        Payee saved = payeeRepository.save(payee);
        return PayeeResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .build();
    }

    @Transactional
    public PayeeResponse updatePayee(UUID planId, UUID payeeId, UUID userId, UpdatePayeeRequest request) {
        planService.findAndVerifyOwnership(planId, userId);
        Payee payee = findPayee(planId, payeeId);

        String trimmedName = request.name().trim();
        if (!payee.getName().equalsIgnoreCase(trimmedName) &&
            payeeRepository.existsByPlanIdAndNameIgnoreCase(planId, trimmedName)) {
            throw new DuplicateResourceException("Đối tượng '" + trimmedName + "' đã tồn tại");
        }

        payee.setName(trimmedName);
        Payee saved = payeeRepository.save(payee);
        return PayeeResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .build();
    }

    @Transactional
    public void deletePayee(UUID planId, UUID payeeId, UUID userId) {
        planService.findAndVerifyOwnership(planId, userId);
        Payee payee = findPayee(planId, payeeId);

        long txCount = transactionRepository.countByPayeeId(payeeId);
        if (txCount > 0) {
            throw new ForbiddenException("Không thể xóa đối tượng đang gắn với " + txCount + " giao dịch. Hãy gộp đối tượng (merge) thay vì xóa.");
        }

        payeeRepository.delete(payee);
    }

    @Transactional
    public PayeeResponse mergePayees(UUID planId, UUID userId, MergePayeeRequest request) {
        planService.findAndVerifyOwnership(planId, userId);
        Payee targetPayee = findPayee(planId, request.targetPayeeId());

        List<UUID> sourceIds = request.sourcePayeeIds().stream()
                .filter(id -> !id.equals(targetPayee.getId()))
                .toList();

        if (!sourceIds.isEmpty()) {
            // Cập nhật lại toàn bộ transactions từ các sourcePayee sang targetPayee
            transactionRepository.reassignPayees(planId, targetPayee.getId(), sourceIds);

            // Xóa các source payee cũ
            for (UUID srcId : sourceIds) {
                payeeRepository.findByIdAndPlanId(srcId, planId).ifPresent(payeeRepository::delete);
            }
        }

        return PayeeResponse.builder()
                .id(targetPayee.getId())
                .name(targetPayee.getName())
                .build();
    }

    private Payee findPayee(UUID planId, UUID payeeId) {
        return payeeRepository.findByIdAndPlanId(payeeId, planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đối tượng giao dịch"));
    }
}
