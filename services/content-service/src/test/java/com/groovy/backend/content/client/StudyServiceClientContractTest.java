package com.groovy.backend.content.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groovy.backend.content.client.StudyServiceClient.StudyView;
import com.sun.net.httpserver.HttpServer;

/**
 * MSA 전환(content-service 추출): content-service가 study-service의 "GET /api/studies/{id}"
 * (멤버십 확인, 게이트웨이를 거치지 않는 서비스 간 내부 API)를 정확히 소비하는지 검증하는
 * Consumer 쪽 계약 테스트. calendar-service의 StudyServiceClientContractTest와 동일한 방식(JDK
 * 내장 HttpServer 스텁)으로, 새 프레임워크 의존성 없이 검증한다.
 */
class StudyServiceClientContractTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer stubServer;

	@AfterEach
	void tearDown() {
		if (stubServer != null) {
			stubServer.stop(0);
		}
	}

	@Test
	void study_service의_study_응답_모양을_흉내낸_스텁에서_StudyView를_읽을_수_있다() throws Exception {
		String responseJson = objectMapper.writeValueAsString(Map.of(
			"status", "SUCCESS",
			"message", "스터디 조회에 성공했습니다.",
			"data", Map.of("id", "10", "leaderId", "1", "title", "알고리즘 스터디", "myApplicationStatus", "APPROVED")
		));
		int port = startStubServer("/api/studies/10", responseJson);

		StudyServiceClient client = new StudyServiceClient("http://localhost:" + port, 2000, 3000);

		StudyView study = client.getStudy(10L).orElseThrow();

		assertThat(study.id()).isEqualTo("10");
		assertThat(study.title()).isEqualTo("알고리즘 스터디");
		assertThat(study.myApplicationStatus()).isEqualTo("APPROVED");
	}

	@Test
	void study_service가_죽어있으면_예외_대신_빈_Optional을_반환한다() {
		StudyServiceClient client = new StudyServiceClient("http://localhost:1", 200, 200);

		assertThat(client.getStudy(10L)).isEmpty();
	}

	private int startStubServer(String path, String responseBody) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext(path, exchange -> {
			byte[] bytes = responseBody.getBytes();
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (var out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
		this.stubServer = server;
		TimeUnit.MILLISECONDS.sleep(50);
		return server.getAddress().getPort();
	}
}
