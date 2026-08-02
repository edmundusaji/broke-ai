    package org.edmund.brokeai.service.serviceimpl;

    import lombok.RequiredArgsConstructor;
    import org.edmund.brokeai.dto.LoginRequest;
    import org.edmund.brokeai.dto.LoginResponse;
    import org.edmund.brokeai.dto.RegisterRequest;
    import org.edmund.brokeai.dto.UpgradeGuestRequest;
    import org.edmund.brokeai.entity.AppUser;
    import org.edmund.brokeai.repository.UserRepository;
    import org.edmund.brokeai.security.JwtService;
    import org.edmund.brokeai.security.CurrentUserService;
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

            userRepository.save(user);
        }

        @Override
        public LoginResponse login(LoginRequest request) {
            if (request == null || isBlank(request.username()) || isBlank(request.password()) /* true hits = 0*/) {
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

            AppUser upgradedUser = userRepository.save(currentUser);
            return buildLoginResponse(upgradedUser);
        }

        private void validateRegisterRequest(RegisterRequest request) {
            if (request == null
                || isBlank(request.namaLengkap())
                || isBlank(request.username()) // true hits = 0
                || isBlank(request.email()) // true hits = 0
                || isBlank(request.password())) // true hits = 0
                {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration data is incomplete");
            }
        }

        private boolean isBlank(String value) {
            return value == null /* true hits = 0*/|| value.isBlank();
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
                userInfo
            );
        }
    }
