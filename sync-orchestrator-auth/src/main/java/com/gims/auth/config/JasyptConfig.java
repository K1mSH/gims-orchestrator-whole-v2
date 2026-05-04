package com.gims.auth.config;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import org.springframework.context.annotation.Configuration;

/**
 * yml 의 ENC(...) 자동 복호화 활성화.
 * jasypt.encryptor.password 환경변수 (또는 yml default) 로 복호화.
 */
@Configuration
@EnableEncryptableProperties
public class JasyptConfig {
}
