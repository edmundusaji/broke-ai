package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.CurrentUserResponse;
import org.edmund.brokeai.dto.LoginRequest;
import org.edmund.brokeai.dto.LoginResponse;
import org.edmund.brokeai.dto.RegisterRequest;
import org.edmund.brokeai.dto.UpgradeGuestRequest;
import org.edmund.brokeai.dto.MergeGuestRequest;
import org.edmund.brokeai.dto.MergeGuestResponse;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.exception.ApiException;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.repository.TransactionRepository;
import org.edmund.brokeai.repository.UserSessionRepository;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.security.JwtService;
import org.edmund.brokeai.service.AuthService;
import org.edmund.brokeai.service.SecurityAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;
    private final TransactionRepository transactionRepository;
    private final GuestDataPurgeService guestDataPurgeService;
    private final SecurityAuditService auditService;
    private final UserSessionRepository userSessionRepository;

    @Override
    public void register(RegisterRequest request) {
        validateRegisterRequest(request);

        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        AppUser guestUser = currentUserService.getCurrentUserIfAuthenticated()
            .filter(user -> Boolean.TRUE.equals(user.getIsGuest()))
            .orElse(null);

        validateUniqueCredentials(username, email, guestUser);

        AppUser user = guestUser == null ? new AppUser() : guestUser;
        user.setFullName(request.fullName().trim());
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setIsGuest(false);
        user.setAiTrialCount(0);

        userRepository.save(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        AppUser user = userRepository.findByUsername(request.username().trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (Boolean.TRUE.equals(user.getIsGuest())
            || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        if (!"active".equals(user.getStatus()) && !"pending_deletion".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        if (passwordEncoder.upgradeEncoding(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.password()));
            userRepository.save(user);
        }

        return buildLoginResponse(user);
    }

    @Override
    public LoginResponse guestLogin() {
        AppUser guest = new AppUser();
        guest.setFullName("Guest User");
        guest.setUsername("guest_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        guest.setEmail(null);
        guest.setPassword(null);
        guest.setIsGuest(true);
        guest.setAiTrialCount(2);

        AppUser savedGuest = userRepository.save(guest);
        return buildLoginResponse(savedGuest);
    }

    @Override
    public LoginResponse upgradeGuest(UpgradeGuestRequest request) {
        validateUpgradeRequest(request);

        AppUser currentUser = currentUserService.getCurrentUser();
        if (!Boolean.TRUE.equals(currentUser.getIsGuest())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current user is not a guest");
        }

        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already in use");
        }

        currentUser.setFullName(request.fullName().trim());
        currentUser.setEmail(email);
        currentUser.setPassword(passwordEncoder.encode(request.password()));
        currentUser.setIsGuest(false);
        currentUser.setAiTrialCount(0);

        AppUser upgradedUser = userRepository.save(currentUser);
        return buildLoginResponse(upgradedUser);
    }

    @Override
    public CurrentUserResponse getCurrentUser() {
        AppUser currentUser = currentUserService.getCurrentUser();
        return new CurrentUserResponse(
            currentUser.getUsername(),
            currentUser.getFullName(),
            currentUser.getEmail(),
            roleFor(currentUser),
            remainingAiTrials(currentUser)
        );
    }

    @Override
    @Transactional
    public MergeGuestResponse mergeGuest(MergeGuestRequest request) {
        AppUser authenticated = currentUserService.getCurrentUser();
        AppUser guest = userRepository.findByIdForUpdate(authenticated.getId())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Guest account not found."));
        if (!Boolean.TRUE.equals(guest.getIsGuest())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GUEST_ONLY", "Only a guest session can be merged.");
        }

        AppUser destination = userRepository.findByUsernameForUpdate(request.username().trim())
            .filter(user -> !Boolean.TRUE.equals(user.getIsGuest()))
            .filter(user -> "active".equals(user.getStatus()))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password."));
        if (!passwordEncoder.matches(request.password(), destination.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password.");
        }

        Set<TransactionKey> destinationKeys = new HashSet<>();
        for (Transaction transaction : transactionRepository
            .findByUserIdAndDeletedAtIsNullOrderByDateDesc(destination.getId())) {
            destinationKeys.add(TransactionKey.of(transaction));
        }

        long moved = 0;
        long duplicates = 0;
        for (Transaction transaction : transactionRepository
            .findByUserIdAndDeletedAtIsNullOrderByDateDesc(guest.getId())) {
            if (!destinationKeys.add(TransactionKey.of(transaction))) {
                duplicates++;
                continue;
            }
            transaction.setUser(destination);
            transaction.setUpdatedAt(java.time.Instant.now());
            transactionRepository.save(transaction);
            moved++;
        }
        transactionRepository.flush();

        userSessionRepository.revokeAll(guest.getId(), java.time.Instant.now());
        auditService.record(destination, "GUEST_MERGED", null, Map.of(
            "guestUserId", guest.getId(),
            "transactionsMoved", moved,
            "duplicatesSkipped", duplicates
        ));
        guestDataPurgeService.hardDeleteGuest(guest);

        return new MergeGuestResponse(
            jwtService.generateToken(destination),
            jwtService.getExpirationSeconds(),
            destination.getUsername(),
            false,
            new MergeGuestResponse.MergeResult(moved, duplicates)
        );
    }

    private record TransactionKey(
        LocalDateTime date,
        Double amount,
        String category,
        String paymentMethod,
        String description
    ) {
        private static TransactionKey of(Transaction value) {
            return new TransactionKey(
                value.getDate(), value.getAmount(), normalize(value.getCategory()),
                normalize(value.getPaymentMethod()), normalize(value.getDescription())
            );
        }

        private static String normalize(String value) {
            return value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null
            || isBlank(request.fullName())
            || isBlank(request.username())
            || isBlank(request.email())
            || isBlank(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration data is incomplete");
        }
    }

    private void validateUpgradeRequest(UpgradeGuestRequest request) {
        if (request == null
            || isBlank(request.fullName())
            || isBlank(request.email())
            || isBlank(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guest upgrade data is incomplete");
        }
    }

    private void validateUniqueCredentials(String username, String email, AppUser guestUser) {
        boolean usernameExists = guestUser == null
            ? userRepository.existsByUsername(username)
            : userRepository.existsByUsernameAndIdNot(username, guestUser.getId());
        if (usernameExists) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "USERNAME_ALREADY_USED",
                "That username is unavailable.",
                "username"
            );
        }

        boolean emailExists = guestUser == null
            ? userRepository.existsByEmail(email)
            : userRepository.existsByEmailAndIdNot(email, guestUser.getId());
        if (emailExists) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_USED",
                "That email address is already in use.",
                "email"
            );
        }
    }

    private LoginResponse buildLoginResponse(AppUser user) {
        String token = jwtService.generateToken(user);
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(user.getFullName(), user.getEmail());
        return new LoginResponse(
            token,
            jwtService.getExpirationSeconds(),
            user.getUsername(),
            Boolean.TRUE.equals(user.getIsGuest()),
            remainingAiTrials(user),
            userInfo
        );
    }

    private String roleFor(AppUser user) {
        return Boolean.TRUE.equals(user.getIsGuest()) ? "ROLE_GUEST" : "ROLE_USER";
    }

    private int remainingAiTrials(AppUser user) {
        if (!Boolean.TRUE.equals(user.getIsGuest())) {
            return 0;
        }
        return Math.max(0, user.getAiTrialCount() == null ? 0 : user.getAiTrialCount());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
