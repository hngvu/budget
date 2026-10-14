package so.budget.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import so.budget.api.config.SecurityConfig;
import so.budget.api.entity.SubscriptionTier;
import so.budget.api.entity.User;
import so.budget.api.service.JwtService;

import javax.crypto.SecretKey;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        String secretHex = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        SecurityConfig config = new SecurityConfig();
        SecretKey key = config.jwtSecretKey(secretHex);
        jwtService = new JwtService(config.jwtEncoder(key));
        ReflectionTestUtils.setField(jwtService, "expirationMs", 2592000000L); // 30 ngày
    }

    @Test
    void generateToken() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("test@budget.local")
                .displayName("Tester")
                .subscriptionTier(SubscriptionTier.FREE)
                .build();

        String token = jwtService.generateToken(user);
        assertNotNull(token);
        assertFalse(token.isBlank());
    }
}
