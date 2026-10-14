package so.budget.api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import so.budget.api.dto.*;
import so.budget.api.entity.Plan;
import so.budget.api.entity.SubscriptionTier;
import so.budget.api.entity.User;
import so.budget.api.exception.DuplicateResourceException;
import so.budget.api.repository.PlanRepository;
import so.budget.api.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email '" + request.email() + "' đã được sử dụng");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .subscriptionTier(SubscriptionTier.FREE)
                .build();
        user = userRepository.save(user);

        // Tạo plan mặc định đầu tiên cho user
        // Trigger DB trg_plan_defaults sẽ tự động tạo category group nội bộ và Inflow: Ready to Assign
        Plan defaultPlan = Plan.builder()
                .name("Ngân sách cá nhân")
                .currencyCode("VND")
                .decimalDigits((short) 0)
                .user(user)
                .build();
        planRepository.save(defaultPlan);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Email hoặc mật khẩu không chính xác"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Email hoặc mật khẩu không chính xác");
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.builder()
                .token(jwtService.generateToken(user))
                .user(UserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .displayName(user.getDisplayName())
                        .subscriptionTier(user.getSubscriptionTier().name())
                        .build())
                .build();
    }
}
