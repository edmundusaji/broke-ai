package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.CurrentUserResponse;
import org.edmund.brokeai.dto.LoginRequest;
import org.edmund.brokeai.dto.LoginResponse;
import org.edmund.brokeai.dto.RegisterRequest;
import org.edmund.brokeai.dto.UpgradeGuestRequest;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.security.JwtService;
import org.edmund.brokeai.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    @Override
    public void register(RegisterRequest request) {
        validateRegisterRequest(request);

        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is already in use");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already in use");
        }

        AppUser user = new AppUser();
        user.setNamaLengkap(request.namaLengkap().trim());
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

        return buildLoginResponse(user);
    }

    @Override
    public LoginResponse guestLogin() {
        AppUser guest = new AppUser();
        guest.setNamaLengkap("Guest User");
        guest.setUsername("guest_" + UUID.randomUUID().toString().replace("-", ""));
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

        currentUser.setNamaLengkap(request.namaLengkap().trim());
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
            currentUser.getNamaLengkap(),
            currentUser.getEmail(),
            roleFor(currentUser),
            remainingAiTrials(currentUser)
        );
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null
            || isBlank(request.namaLengkap())
            || isBlank(request.username())
            || isBlank(request.email())
            || isBlank(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration data is incomplete");
        }
    }

    private void validateUpgradeRequest(UpgradeGuestRequest request) {
        if (request == null
            || isBlank(request.namaLengkap())
            || isBlank(request.email())
            || isBlank(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guest upgrade data is incomplete");
        }
    }

    private LoginResponse buildLoginResponse(AppUser user) {
        String token = jwtService.generateToken(user);
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(user.getNamaLengkap(), user.getEmail());
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
