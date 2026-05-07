package com.infolink.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt 비밀번호 인코더 (round 12).
 * SecurityConfig (Step 7) 에서 import 가능하도록 분리.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder(@Value("${auth.bcrypt.rounds:12}") int rounds) {
        return new BCryptPasswordEncoder(rounds);
    }
}
