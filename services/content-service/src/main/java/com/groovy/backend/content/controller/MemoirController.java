package com.groovy.backend.content.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.content.dto.MemoirCreateRequest;
import com.groovy.backend.content.dto.MemoirResponse;
import com.groovy.backend.content.dto.MemoirStudyOptionResponse;
import com.groovy.backend.content.dto.MemoirUpdateRequest;
import com.groovy.backend.content.service.MemoirService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/memoirs")
@RequiredArgsConstructor
public class MemoirController {

	private final MemoirService memoirService;

	@PostMapping
	public ApiResponse<MemoirResponse> createMemoir(
		@AuthenticationPrincipal Long userId,
		@Valid @RequestBody MemoirCreateRequest request
	) {
		return ApiResponse.of("SUCCESS", "회고록이 작성되었습니다.", memoirService.createMemoir(userId, request));
	}

	// userId는 비회원이면 null(로그인 필수 아님). liked 여부를 뷰어 기준으로 계산하는 데만 쓰인다.
	// sortBy="popular"면 좋아요+댓글 수 기준 인기순, 그 외(기본값)엔 최신순.
	// 파라미터명을 "sort"로 두면 스프링 데이터가 Pageable 자체의 정렬 파라미터로도 동시에 해석해버려
	// (둘 다 같은 쿼리 파라미터를 가리키므로) 의도치 않은 정렬이 섞여 들어가 sortBy로 분리했다.
	@GetMapping
	public ApiResponse<Page<MemoirResponse>> getMemoirs(
		@AuthenticationPrincipal Long userId,
		@RequestParam(required = false) String keyword,
		@RequestParam(required = false, defaultValue = "latest") String sortBy,
		@PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
	) {
		return ApiResponse.of("SUCCESS", "회고록 목록 조회에 성공했습니다.", memoirService.getMemoirs(keyword, sortBy, userId, pageable));
	}

	// 리터럴 경로("/my-studies", "/mine")가 "/{memoirId}" 변수 경로보다 우선 매칭되므로 순서와 무관하게 안전하다.
	// 회고록 작성 시 연결할 수 있는, 내가 방장이거나 승인되어 속한 스터디 목록(로그인 필요).
	@GetMapping("/my-studies")
	public ApiResponse<List<MemoirStudyOptionResponse>> getMyStudyOptions() {
		return ApiResponse.of("SUCCESS", "내 스터디 목록 조회에 성공했습니다.", memoirService.getMyStudyOptions());
	}

	// "나의 활동" 탭: 내가 작성한 회고록만(로그인 필요).
	@GetMapping("/mine")
	public ApiResponse<Page<MemoirResponse>> getMyMemoirs(
		@AuthenticationPrincipal Long userId,
		@PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
	) {
		return ApiResponse.of("SUCCESS", "내 회고록 목록 조회에 성공했습니다.", memoirService.getMyMemoirs(userId, pageable));
	}

	@GetMapping("/{memoirId}")
	public ApiResponse<MemoirResponse> getMemoir(@AuthenticationPrincipal Long userId, @PathVariable Long memoirId) {
		return ApiResponse.of("SUCCESS", "회고록 상세 조회에 성공했습니다.", memoirService.getMemoir(memoirId, userId));
	}

	@PutMapping("/{memoirId}")
	public ApiResponse<MemoirResponse> updateMemoir(
		@AuthenticationPrincipal Long userId,
		@PathVariable Long memoirId,
		@Valid @RequestBody MemoirUpdateRequest request
	) {
		return ApiResponse.of("SUCCESS", "회고록이 수정되었습니다.", memoirService.updateMemoir(userId, memoirId, request));
	}

	@DeleteMapping("/{memoirId}")
	public ApiResponse<Void> deleteMemoir(@AuthenticationPrincipal Long userId, @PathVariable Long memoirId) {
		memoirService.deleteMemoir(userId, memoirId);
		return ApiResponse.of("SUCCESS", "회고록이 삭제되었습니다.");
	}

	@PostMapping("/{memoirId}/likes")
	public ApiResponse<MemoirResponse> likeMemoir(@AuthenticationPrincipal Long userId, @PathVariable Long memoirId) {
		return ApiResponse.of("SUCCESS", "좋아요를 눌렀습니다.", memoirService.likeMemoir(userId, memoirId));
	}

	@DeleteMapping("/{memoirId}/likes")
	public ApiResponse<MemoirResponse> unlikeMemoir(@AuthenticationPrincipal Long userId, @PathVariable Long memoirId) {
		return ApiResponse.of("SUCCESS", "좋아요를 취소했습니다.", memoirService.unlikeMemoir(userId, memoirId));
	}
}
