package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.LoginRequest;
import org.edmund.brokeai.dto.LoginResponse;
import org.edmund.brokeai.dto.RegisterRequest;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.security.JwtService;
import org.edmund.brokeai.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    public void register(RegisterRequest request) {
        validateRegisterRequest(request);

        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username sudah dipakai");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email sudah dipakai");
        }

        AppUser user = new AppUser();
        user.setNamaLengkap(request.namaLengkap().trim());
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));

        userRepository.save(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password()) /* true hits = 0*/) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username atau password salah");
        }

        AppUser user = userRepository.findByUsername(request.username().trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username atau password salah"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username atau password salah");
        }

        String token = jwtService.generateToken(user);
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(user.getNamaLengkap(), user.getEmail());
        return new LoginResponse(token, jwtService.getExpirationSeconds(), userInfo);
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null
            || isBlank(request.namaLengkap())
            || isBlank(request.username()) // true hits = 0
            || isBlank(request.email()) // true hits = 0
            || isBlank(request.password())) // true hits = 0
            {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data register belum lengkap");
        }
    }

    private boolean isBlank(String value) {
        return value == null /* true hits = 0*/|| value.isBlank();
    }
}
