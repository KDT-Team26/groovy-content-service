package com.groovy.backend.content.client;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.groovy.backend.client.ResilientCallExecutor;
import com.groovy.backend.common.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * MSA 전환(content-service 추출): groovy(레거시)의 StudyServiceClient와 동일한 계약이지만
 * 이 서비스가 실제로 쓰는 4개 메서드(getStudy/getStudySummaries/getMyStudyOptions/addExp)만
 * 옮겼다(calendar-service가 자기가 쓰는 3개만 남긴 것과 동일한 방식).
 *
 * 원 요청의 Authorization 헤더를 그대로 study-service에 전달한다 — study-service의
 * "/api/studies/{id}"가 로그인 사용자별로 myApplicationStatus를 채워주므로, 그 값으로
 * "이 유저가 이 스터디 멤버인지"를 판단할 수 있다(별도의 멤버십 확인 API를 새로 만들지 않는다).
 *
 * study-service가 다운이 아니라 느려지기만 해도 매 요청이 read-timeout만큼 블로킹되는 걸
 * 막기 위해 ResilientCallExecutor(CircuitBreaker+Retry)로 각 호출을 감싼다(8번 항목).
 */
@Component
public class StudyServiceClient {

	private final RestClient restClient;
	private final ResilientCallExecutor executor;

	public StudyServiceClient(
		@Value("${study-service.url:http://study-service:8082}") String studyServiceUrl,
		@Value("${study-service.connect-timeout-ms:2000}") long connectTimeoutMs,
		@Value("${study-service.read-timeout-ms:3000}") long readTimeoutMs
	) {
		var httpClient = java.net.http.HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(connectTimeoutMs))
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

		this.restClient = RestClient.builder()
			.baseUrl(studyServiceUrl)
			.requestFactory(requestFactory)
			.build();
		this.executor = new ResilientCallExecutor("study-service-client");
	}

	// study-service의 공개 API("/api/studies/{id}")를 그대로 재사용한다. 없는 스터디거나
	// study-service가 죽어 있으면 빈 Optional을 반환한다 — 호출부가 "존재하지 않는 스터디"로
	// 처리한다.
	public Optional<StudyView> getStudy(Long studyId) {
		return executor.execute(
			() -> {
				ApiResponse<StudyView> response = restClient.get()
					.uri("/api/studies/{id}", studyId)
					.headers(this::forwardAuthorization)
					.retrieve()
					.body(new ParameterizedTypeReference<ApiResponse<StudyView>>() {
					});
				return Optional.ofNullable(response == null ? null : response.data());
			},
			Optional::empty);
	}

	// Memoir 응답에 studyTitle/studyLevel/studyExpPoint를 채울 때 쓰는 배치 조회
	// (study-service StudyController#getStudySummaries가 소비하는 내부 API).
	public Map<Long, StudySummaryView> getStudySummaries(List<Long> studyIds) {
		if (studyIds.isEmpty()) {
			return Map.of();
		}
		return executor.execute(
			() -> {
				ApiResponse<Map<Long, StudySummaryView>> response = restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/api/studies/summary").queryParam("ids", studyIds).build())
					.retrieve()
					.body(new ParameterizedTypeReference<ApiResponse<Map<Long, StudySummaryView>>>() {
					});
				return response == null || response.data() == null ? Map.<Long, StudySummaryView>of() : response.data();
			},
			Map::of);
	}

	// 회고록 작성 시 고를 수 있는 "내가 방장이거나 승인되어 속한 스터디" 옵션 목록. study-service의
	// 기존 공개 API 2개("/api/users/me/studies", "/api/users/me/applications")를 그대로 재사용해
	// 합치고 studyId 기준으로 중복 제거한다 — 이 조합만을 위한 내부 API를 새로 만들지 않는다.
	public List<StudyOptionView> getMyStudyOptions() {
		Map<String, StudyOptionView> optionsById = new LinkedHashMap<>();

		List<MyStudyView> ledStudies = executor.execute(
			() -> {
				ApiResponse<List<MyStudyView>> response = restClient.get()
					.uri("/api/users/me/studies")
					.headers(this::forwardAuthorization)
					.retrieve()
					.body(new ParameterizedTypeReference<ApiResponse<List<MyStudyView>>>() {
					});
				return response == null || response.data() == null ? List.<MyStudyView>of() : response.data();
			},
			List::of);
		ledStudies.forEach(study -> optionsById.put(study.id(), new StudyOptionView(study.id(), study.title(), true)));

		List<MyApplicationView> applications = executor.execute(
			() -> {
				ApiResponse<List<MyApplicationView>> response = restClient.get()
					.uri("/api/users/me/applications")
					.headers(this::forwardAuthorization)
					.retrieve()
					.body(new ParameterizedTypeReference<ApiResponse<List<MyApplicationView>>>() {
					});
				return response == null || response.data() == null ? List.<MyApplicationView>of() : response.data();
			},
			List::of);
		applications.stream()
			.filter(application -> "APPROVED".equals(application.status()))
			.forEach(application -> optionsById.put(application.studyId(), new StudyOptionView(application.studyId(), application.studyTitle(), false)));

		return List.copyOf(optionsById.values());
	}

	// 회고록/댓글 작성 시 소속 스터디에 경험치를 적립한다(study-service StudyExpController가 소비).
	// 실패해도 회고록/댓글 저장 자체를 막지 않는다 — 경험치는 부가 효과일 뿐 핵심 도메인 동작이
	// 아니기 때문이다(알려진 한계: 이 호출이 실패하면 경험치가 유실될 수 있다. 완전한 정합성이
	// 필요해지면 Outbox+이벤트 기반으로 전환해야 한다).
	public void addExp(Long studyId, int amount) {
		executor.execute(
			() -> restClient.post()
				.uri("/api/studies/{id}/exp", studyId)
				.headers(this::forwardAuthorization)
				.body(new AddExpRequest(amount))
				.retrieve()
				.toBodilessEntity(),
			() -> {
			});
	}

	private void forwardAuthorization(HttpHeaders headers) {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return;
		}
		HttpServletRequest request = attributes.getRequest();
		String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorizationHeader != null) {
			headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record StudyView(String id, String leaderId, String title, String myApplicationStatus) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record StudySummaryView(String id, String title, Integer level, Integer expPoint) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record MyStudyView(String id, String title) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record MyApplicationView(String studyId, String studyTitle, String status) {
	}

	public record StudyOptionView(String studyId, String title, boolean isLeader) {
	}

	private record AddExpRequest(int amount) {
	}
}
