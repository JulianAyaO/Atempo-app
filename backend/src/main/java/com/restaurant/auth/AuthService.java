package com.restaurant.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import jakarta.validation.constraints.NotBlank;

@Service
public class AuthService {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(StaffRepository staffRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public record LoginRequest(
        @NotBlank(message = "name es obligatorio") String name,
        @NotBlank(message = "pin es obligatorio") String pin
    ) {}
    public record LoginResponse(String token, String role, String name, Long staffId) {}

    public LoginResponse login(LoginRequest request) {
        Staff staff = staffRepository.findByNameIgnoreCaseAndActive(request.name(), true)
            .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
        if (!passwordEncoder.matches(request.pin(), staff.getPinHash())) {
            throw new BadCredentialsException("Credenciales inválidas");
        }
        String token = jwtUtil.generateToken(staff.getId(), staff.getName(), staff.getRole());
        return new LoginResponse(token, staff.getRole(), staff.getName(), staff.getId());
    }
}
