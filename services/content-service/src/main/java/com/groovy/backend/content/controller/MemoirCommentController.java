package com.groovy.backend.content.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.content.dto.MemoirCommentRequest;
import com.groovy.backend.content.dto.MemoirCommentResponse;
import com.groovy.backend.content.service.MemoirCommentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/memoirs/{memoirId}/comments")
@RequiredArgsConstructor
public class MemoirCommentController {

	private final MemoirCommentService memoirCommentService;

	@GetMapping
	public ApiResponse<List<MemoirCommentResponse>> getComments(@PathVariable Long memoirId) {
		return ApiResponse.of("SUCCESS", "댓글 목록 조회에 성공했습니다.", memoirCommentService.getComments(memoirId));
	}

	@PostMapping
	public ApiResponse<MemoirCommentResponse> createComment(
		@AuthenticationPrincipal Long userId,
		@PathVariable Long memoirId,
		@Valid @RequestBody MemoirCommentRequest request
	) {
		return ApiResponse.of("SUCCESS", "댓글이 작성되었습니다.", memoirCommentService.createComment(userId, memoirId, request));
	}

	@PutMapping("/{commentId}")
	public ApiResponse<MemoirCommentResponse> updateComment(
		@AuthenticationPrincipal Long userId,
		@PathVariable Long memoirId,
		@PathVariable Long commentId,
		@Valid @RequestBody MemoirCommentRequest request
	) {
		return ApiResponse.of("SUCCESS", "댓글이 수정되었습니다.", memoirCommentService.updateComment(userId, memoirId, commentId, request));
	}

	@DeleteMapping("/{commentId}")
	public ApiResponse<Void> deleteComment(
		@AuthenticationPrincipal Long userId,
		@PathVariable Long memoirId,
		@PathVariable Long commentId
	) {
		memoirCommentService.deleteComment(userId, memoirId, commentId);
		return ApiResponse.of("SUCCESS", "댓글이 삭제되었습니다.");
	}
}
