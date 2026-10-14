package so.budget.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Global Exception Handler tuân thủ chuẩn RFC 9457 (thay thế RFC 7807).
 * Định dạng application/problem+json với cấu trúc Problem Details chuẩn hóa:
 * - type: Định danh loại lỗi (URI URN hoặc null thay vì about:blank dư thừa)
 * - title: Tóm tắt mã trạng thái ngắn gọn
 * - status: HTTP status code
 * - detail: Mô tả chi tiết vấn đề cụ thể
 * - timestamp: Thời điểm phát sinh lỗi
 * - invalidParams: Danh sách các trường dữ liệu vi phạm (đối với validation error)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE_TYPE = "urn:budget:error:";

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_TYPE + "duplicate-resource"));
        problem.setTitle("Resource Conflict");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_TYPE + "resource-not-found"));
        problem.setTitle("Resource Not Found");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_TYPE + "forbidden"));
        problem.setTitle("Forbidden");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác");
        problem.setType(URI.create(PROBLEM_BASE_TYPE + "unauthorized"));
        problem.setTitle("Unauthorized");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(JwtException.class)
    public ProblemDetail handleJwtException(JwtException ex) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn");
        problem.setType(URI.create(PROBLEM_BASE_TYPE + "invalid-token"));
        problem.setTitle("Invalid Token");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ");
        problem.setType(URI.create(PROBLEM_BASE_TYPE + "invalid-params"));
        problem.setTitle("Validation Failed");
        problem.setProperty("timestamp", Instant.now());

        // Theo RFC 9457 Extension Guidelines: đặt tên rõ nghĩa như invalidParams
        List<InvalidParam> invalidParams = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new InvalidParam(err.getField(), err.getDefaultMessage()))
                .toList();
        problem.setProperty("invalidParams", invalidParams);
        return problem;
    }

    public record InvalidParam(String name, String reason) {}
}
