package com.github.gather.service;

import com.github.gather.dto.request.UserLoginRequest;
import com.github.gather.dto.request.UserSignupRequest;
import com.github.gather.dto.response.UserLoginResponse;
import com.github.gather.entity.RefreshToken;
import com.github.gather.repositroy.RefreshTokenRepository;
import com.github.gather.repositroy.TokenBlacklistRepository;
import com.github.gather.repositroy.UserRepository;
import com.github.gather.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.github.gather.entity.User;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;


    public User signup(UserSignupRequest userData) {
        if (!checkUser(userData.getEmail())) {
            throw new DataIntegrityViolationException("이미 존재하는 이메일입니다.");
        }
        return User.builder()
                .email(userData.getEmail())
                .password(passwordEncoder.encode(userData.getPassword()))
                .locationId(userData.getLocation())
                .nickname(userData.getNickname())
                .phoneNumber(userData.getPhoneNumber())
                .image(userData.getImage())
                .userRole(userData.getUserRole())
                .isDeleted(false)
                .isLocked(false)
                .build();
    }

//    public UserLoginResponse login(UserLoginRequest user) {
//
//        User loginUser = userRepository.findByEmail(user.getEmail()).orElseThrow(
//                () -> new BadCredentialsException("이메일을 다시 확인해주세요.")
//        );
//
//        if (loginUser.getIsDeleted()) {
//            throw new LockedException("계정이 비활성화되었습니다.");
//        }
//
//        if (loginUser.getIsLocked()) {
//            throw new LockedException("계정이 잠겨있습니다.");
//        }
//
//        if (!passwordEncoder.matches(user.getPassword(), loginUser.getPassword())) {
//            // 비밀번호 오류 처리
//            loginUser.setLockCount(loginUser.getLockCount() + 1);
//            if (loginUser.getLockCount() >= 5) {
//                loginUser.setIsLocked(true);
//                userRepository.save(loginUser);
//                throw new LockedException("비밀번호 5회 이상 틀려 계정이 잠겼습니다.");
//            }
//            userRepository.save(loginUser);
//            throw new BadCredentialsException("비밀번호를 다시 확인해주세요.");
//        }
//
//        // 정상 로그인 처리
//        loginUser.setLockCount(0);
//        loginUser.setIsLocked(false);
//        userRepository.save(loginUser);
//
//        String newToken = jwtTokenProvider.createToken(loginUser);
//
//        return UserLoginResponse.builder()
//                .email(loginUser.getEmail())
//                .nickname(loginUser.getNickname())
//                .phoneNumber(loginUser.getPhoneNumber())
//                .location(loginUser.getLocationId())
//                .userRole(loginUser.getUserRole())
//                .image(loginUser.getImage())
//                .token(newToken)
//                .build();
//    }

    public UserLoginResponse login(UserLoginRequest user) {
        User loginUser = userRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new BadCredentialsException("이메일을 다시 확인해주세요."));

        if (loginUser.getIsDeleted()) {
            throw new LockedException("계정이 비활성화되었습니다.");
        }

        if (loginUser.getIsLocked()) {
            throw new LockedException("계정이 잠겨있습니다.");
        }

        if (!passwordEncoder.matches(user.getPassword(), loginUser.getPassword())) {
            // 비밀번호 오류 처리
            loginUser.setLockCount(loginUser.getLockCount() + 1);
            if (loginUser.getLockCount() >= 5) {
                loginUser.setIsLocked(true);
                userRepository.save(loginUser);
                throw new LockedException("비밀번호 5회 이상 틀려 계정이 잠겼습니다.");
            }
            userRepository.save(loginUser);
            throw new BadCredentialsException("비밀번호를 다시 확인해주세요.");
        }

        // 정상 로그인 처리
        loginUser.setLockCount(0);
        loginUser.setIsLocked(false);
        userRepository.save(loginUser);

        // 기존에 저장된 Refresh Token 조회
        Optional<RefreshToken> existingRefreshToken = refreshTokenRepository.findFirstByUserOrderByExpiryDateDesc(loginUser);

        String refreshToken;
        if (existingRefreshToken.isPresent() && !isRefreshTokenBlacklisted(existingRefreshToken.get().getToken())) {
            // 기존에 저장된 Refresh Token이 있고 블랙리스트에 없다면 재사용
            refreshToken = existingRefreshToken.get().getToken();
        } else {
            // 기존에 저장된 Refresh Token이 없거나 블랙리스트에 있다면 새로 발급
            refreshToken = jwtTokenProvider.createRefreshToken(loginUser);

            // Refresh Token을 데이터베이스에 저장
            LocalDateTime expiryDate = LocalDateTime.now().plusDays(7); // 만료 시간 7일 후로 설정
            RefreshToken newRefreshToken = new RefreshToken(loginUser, refreshToken, expiryDate);
            refreshTokenRepository.save(newRefreshToken);
        }

        // Access Token 발급
        String accessToken = jwtTokenProvider.createAccessToken(loginUser);

        return UserLoginResponse.builder()
                .email(loginUser.getEmail())
                .nickname(loginUser.getNickname())
                .phoneNumber(loginUser.getPhoneNumber())
                .location(loginUser.getLocationId())
                .userRole(loginUser.getUserRole())
                .image(loginUser.getImage())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public Boolean checkUser(String email) {
        Optional<User> user = userRepository.findByEmail(email);
        return user.isEmpty();
    }

    public Boolean checkEmail(String email) {
        return !userRepository.existsByEmail(email);
    }

    public Boolean checkNickname(String nickname) {
        return !userRepository.existsByNickname(nickname);
    }

    public void deleteUser(Long userIdx) {
        User user = userRepository.findById(userIdx).orElseThrow(()-> new RuntimeException("유저를 찾을 수 없습니다."));
        user.setIsDeleted(true);
        userRepository.save(user);
    }

    // 매일 자정에 실행되도록 스케줄링
    @Scheduled(cron = "0 0 0 * * ?") // 초 분 시간 일 월 주
    public void cleanupExpiredRefreshTokens() {
        // 현재 시간 이전의 만료된 Refresh Token을 삭제
        refreshTokenRepository.deleteByExpiryDateBefore(LocalDateTime.now());
    }

    public void logout(Long userId) {
        // 로그아웃 시 RefreshToken을 블랙리스트에 추가
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        RefreshToken storedRefreshToken = refreshTokenRepository.findByUser(user)
                .orElseThrow(() -> new BadCredentialsException("RefreshToken이 존재하지 않습니다."));

        jwtTokenProvider.invalidateRefreshToken(storedRefreshToken.getToken());
    }

    // 블랙리스트에 토큰이 있는지 확인
    private boolean isRefreshTokenBlacklisted(String refreshToken) {
        return tokenBlacklistRepository.existsById(refreshToken);
    }
}