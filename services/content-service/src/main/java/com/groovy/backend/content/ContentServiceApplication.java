package com.groovy.backend.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.groovy.backend.observability.TracingConfig;

/**
 * 공통 코드 분리(groovy-common) 후 배선:
 *  - outbox 모듈(com.groovy.backend.outbox)이 이 서비스 base 패키지 밖이라 스캔 대상에 명시.
 *    @EntityScan/@EnableJpaRepositories 는 한 번이라도 지정하면 Boot 기본값을 대체하므로
 *    두 패키지를 모두 나열한다.
 *  - observability 모듈의 TracingConfig(@Configuration)는 @Import 로 가져온다.
 */
@SpringBootApplication(scanBasePackages = {"com.groovy.backend.content", "com.groovy.backend.outbox"})
@EnableJpaAuditing
@EntityScan(basePackages = {"com.groovy.backend.content", "com.groovy.backend.outbox"})
@EnableJpaRepositories(basePackages = {"com.groovy.backend.content", "com.groovy.backend.outbox"})
@Import(TracingConfig.class)
public class ContentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ContentServiceApplication.class, args);
	}
}
