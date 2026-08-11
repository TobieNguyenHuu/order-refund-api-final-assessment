package com.assessment.orderapi.identity.service;

import com.assessment.orderapi.common.config.JwtProperties;
import com.assessment.orderapi.common.enums.ErrorCode;
import com.assessment.orderapi.common.exception.AppException;
import com.assessment.orderapi.identity.dto.request.AuthenticationRequest;
import com.assessment.orderapi.identity.dto.response.AuthenticationResponse;
import com.assessment.orderapi.identity.entity.Role;
import com.assessment.orderapi.identity.entity.User;
import com.assessment.orderapi.identity.repository.UserRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        User user = userRepository.findByUsernameOrEmail(request.login())
                .orElseThrow(() -> new AppException(
                        ErrorCode.UNAUTHENTICATED, "Invalid credentials"));

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Invalid credentials");
        }

        Set<String> roles = user.getRoles().stream()
                .map(Role::getRoleName)
                .collect(Collectors.toSet());

        return AuthenticationResponse.builder()
                .token(generateToken(user, roles))
                .userId(user.getId())
                .username(user.getUsername())
                .roles(roles)
                .expiresIn(jwtProperties.validDuration())
                .build();
    }

    private String generateToken(User user, Set<String> roles) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("order-api")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plus(jwtProperties.validDuration(), ChronoUnit.SECONDS)))
                // Stored as String on purpose: numeric JWT claims deserialize
                // inconsistently as Integer or Long depending on magnitude.
                .claim("userId", String.valueOf(user.getId()))
                .claim("scope", roles.stream().sorted().toList())
                .build();

        try {
            SignedJWT signedJWT = new SignedJWT(header, claimsSet);
            signedJWT.sign(new MACSigner(jwtProperties.signerKey().getBytes()));
            return signedJWT.serialize();
        } catch (JOSEException e) {
            log.error("Unable to sign JWT for user {}", user.getUsername(), e);
            throw new AppException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
