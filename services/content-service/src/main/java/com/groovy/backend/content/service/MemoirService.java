package com.groovy.backend.content.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.groovy.backend.content.Memoir;
import com.groovy.backend.content.MemoirLike;
import com.groovy.backend.content.client.StudyServiceClient;
import com.groovy.backend.content.client.StudyServiceClient.StudySummaryView;
import com.groovy.backend.content.client.StudyServiceClient.StudyView;
import com.groovy.backend.client.UserServiceClient;
import com.groovy.backend.content.dto.MemoirCreateRequest;
import com.groovy.backend.content.dto.MemoirResponse;
import com.groovy.backend.content.dto.MemoirStudyOptionResponse;
import com.groovy.backend.content.dto.MemoirUpdateRequest;
import com.groovy.backend.common.exception.ForbiddenException;
import com.groovy.backend.content.notification.NotificationOutboxPublisher;
import com.groovy.backend.content.repository.MemoirCommentRepository;
import com.groovy.backend.content.repository.MemoirCommentRepository.MemoirCommentCount;
import com.groovy.backend.content.repository.MemoirLikeRepository;
import com.groovy.backend.content.repository.MemoirLikeRepository.MemoirLikeCount;
import com.groovy.backend.content.repository.MemoirRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환(content-service 추출): Memoir가 groovy(레거시)에서 빠져나오면서, 같은 JVM의
 * UserService/StudyServiceClient(groovy가 study-service를 부르던 것)를 직접 호출하던 지점들이
 * 전부 이 서비스 자체의 UserServiceClient(identity-service 호출)/StudyServiceClient
 * (study-service 호출)로 바뀌었다. principal도 email이 아니라 userId(Long)를 바로 받는다 —
 * 이 서비스에는 User 테이블이 없어서 email로는 아무것도 조회할 수 없다(calendar-service/
 * study-service와 동일한 컨벤션).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemoirService {

	// 회고록 작성 시 연결된 스터디가 얻는 경험치. 레벨업 기준은 study-service의 Study.addExp를 참고.
	private static final int MEMOIR_EXP = 20;
	private static final String SORT_POPULAR = "popular";

	private final MemoirRepository memoirRepository;
	private final MemoirCommentRepository memoirCommentRepository;
	private final MemoirLikeRepository memoirLikeRepository;
	private final NotificationOutboxPublisher notificationOutboxPublisher;
	private final StudyServiceClient studyServiceClient;
	private final UserServiceClient userServiceClient;

	@Transactional
	public MemoirResponse createMemoir(Long authorId, MemoirCreateRequest request) {
		StudyView study = resolveMyStudy(authorId, request.studyId());

		Memoir memoir = Memoir.builder()
			.studyId(request.studyId())
			.authorId(authorId)
			.title(request.title())
			.content(request.content())
			.build();

		Memoir saved = memoirRepository.save(memoir);
		studyServiceClient.addExp(request.studyId(), MEMOIR_EXP);
		log.info("회고록 작성: memoirId={}, studyId={}, authorId={}", saved.getId(), request.studyId(), authorId);

		return MemoirResponse.from(saved, resolveUserName(authorId), study.title(), null, null, 0L, 0L, false);
	}

	// keyword가 비어 있으면 전체 목록, sort="popular"면 좋아요+댓글 수 기준 인기순, 그 외엔 최신순.
	public Page<MemoirResponse> getMemoirs(String keyword, String sortBy, Long viewerId, Pageable pageable) {
		String normalizedKeyword = normalizeKeyword(keyword);
		Page<Memoir> memoirs = SORT_POPULAR.equals(sortBy)
			? getMemoirsByPopularity(normalizedKeyword, pageable)
			: memoirRepository.search(normalizedKeyword, pageable);

		return toResponsePage(memoirs, viewerId);
	}

	public Page<MemoirResponse> getMyMemoirs(Long authorId, Pageable pageable) {
		Page<Memoir> memoirs = memoirRepository.findByAuthorIdWithStudyAndAuthor(authorId, pageable);
		return toResponsePage(memoirs, authorId);
	}

	public MemoirResponse getMemoir(Long memoirId, Long viewerId) {
		Memoir memoir = getMemoirEntity(memoirId);
		long commentCount = memoirCommentRepository.countByMemoirId(memoirId);
		long likeCount = memoirLikeRepository.countByMemoirId(memoirId);
		boolean liked = isLikedByViewer(memoirId, viewerId);

		StudySummaryView studyInfo = resolveStudyInfo(memoir.getStudyId());
		return MemoirResponse.from(memoir, resolveUserName(memoir.getAuthorId()), studyInfo.title(), studyInfo.level(), studyInfo.expPoint(), commentCount, likeCount, liked);
	}

	@Transactional
	public MemoirResponse updateMemoir(Long userId, Long memoirId, MemoirUpdateRequest request) {
		Memoir memoir = getMemoirEntity(memoirId);
		validateAuthor(memoir, userId);

		memoir.update(request.title(), request.content());
		log.info("회고록 수정: memoirId={}, userId={}", memoirId, userId);

		long commentCount = memoirCommentRepository.countByMemoirId(memoirId);
		long likeCount = memoirLikeRepository.countByMemoirId(memoirId);
		boolean liked = isLikedByViewer(memoirId, userId);
		StudySummaryView studyInfo = resolveStudyInfo(memoir.getStudyId());
		return MemoirResponse.from(memoir, resolveUserName(memoir.getAuthorId()), studyInfo.title(), studyInfo.level(), studyInfo.expPoint(), commentCount, likeCount, liked);
	}

	@Transactional
	public void deleteMemoir(Long userId, Long memoirId) {
		Memoir memoir = getMemoirEntity(memoirId);
		validateAuthor(memoir, userId);

		memoirCommentRepository.deleteAllByMemoirId(memoirId);
		memoirLikeRepository.deleteAllByMemoirId(memoirId);
		memoirRepository.delete(memoir);
		log.info("회고록 삭제: memoirId={}, userId={}", memoirId, userId);
	}

	@Transactional
	public MemoirResponse likeMemoir(Long userId, Long memoirId) {
		Memoir memoir = getMemoirEntity(memoirId);

		if (!memoirLikeRepository.existsByMemoirIdAndUserId(memoirId, userId)) {
			memoirLikeRepository.save(MemoirLike.builder().memoir(memoir).userId(userId).build());
			log.info("회고록 좋아요: memoirId={}, userId={}", memoirId, userId);

			// 신규 좋아요일 때만(멱등 재호출 제외), 본인 글에 본인이 누른 좋아요는 알림을 보내지 않는다.
			if (!memoir.isAuthor(userId)) {
				notificationOutboxPublisher.memoirLikeAdded(
					memoir.getAuthorId(), resolveUserName(userId), memoirId, memoir.getTitle());
			}
		}

		long commentCount = memoirCommentRepository.countByMemoirId(memoirId);
		long likeCount = memoirLikeRepository.countByMemoirId(memoirId);
		StudySummaryView studyInfo = resolveStudyInfo(memoir.getStudyId());
		return MemoirResponse.from(memoir, resolveUserName(memoir.getAuthorId()), studyInfo.title(), studyInfo.level(), studyInfo.expPoint(), commentCount, likeCount, true);
	}

	@Transactional
	public MemoirResponse unlikeMemoir(Long userId, Long memoirId) {
		Memoir memoir = getMemoirEntity(memoirId);

		memoirLikeRepository.findByMemoirIdAndUserId(memoirId, userId)
			.ifPresent(memoirLikeRepository::delete);
		log.info("회고록 좋아요 취소: memoirId={}, userId={}", memoirId, userId);

		long commentCount = memoirCommentRepository.countByMemoirId(memoirId);
		long likeCount = memoirLikeRepository.countByMemoirId(memoirId);
		StudySummaryView studyInfo = resolveStudyInfo(memoir.getStudyId());
		return MemoirResponse.from(memoir, resolveUserName(memoir.getAuthorId()), studyInfo.title(), studyInfo.level(), studyInfo.expPoint(), commentCount, likeCount, false);
	}

	// 회고록 작성 시 고를 수 있는, 내가 방장이거나 승인되어 속한 스터디 목록.
	public List<MemoirStudyOptionResponse> getMyStudyOptions() {
		return studyServiceClient.getMyStudyOptions().stream()
			.map(MemoirStudyOptionResponse::from)
			.toList();
	}

	Memoir getMemoirEntity(Long memoirId) {
		return memoirRepository.findByIdWithStudyAndAuthor(memoirId)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 회고록: memoirId={}", memoirId);
				return new IllegalArgumentException("존재하지 않는 회고록입니다.");
			});
	}

	void validateAuthor(Memoir memoir, Long userId) {
		if (!memoir.isAuthor(userId)) {
			log.warn("회고록 작성자 아님: memoirId={}, userId={}", memoir.getId(), userId);
			throw new ForbiddenException("작성자만 수행할 수 있는 작업입니다.");
		}
	}

	private Page<Memoir> getMemoirsByPopularity(String keyword, Pageable pageable) {
		Pageable pageRange = Pageable.ofSize(pageable.getPageSize()).withPage(pageable.getPageNumber());
		Page<Long> idPage = memoirRepository.findIdsByPopularity(keyword, pageRange);
		List<Long> ids = idPage.getContent();
		if (ids.isEmpty()) {
			return new PageImpl<>(List.of(), pageRange, idPage.getTotalElements());
		}

		Map<Long, Memoir> memoirById = memoirRepository.findAllWithStudyAndAuthorByIdIn(ids).stream()
			.collect(Collectors.toMap(Memoir::getId, memoir -> memoir));
		List<Memoir> ordered = ids.stream().map(memoirById::get).toList();

		return new PageImpl<>(ordered, pageRange, idPage.getTotalElements());
	}

	private Page<MemoirResponse> toResponsePage(Page<Memoir> memoirs, Long viewerId) {
		List<Long> memoirIds = memoirs.getContent().stream().map(Memoir::getId).toList();

		Map<Long, Long> commentCountByMemoirId = toCountMap(
			memoirCommentRepository.countByMemoirIdIn(memoirIds), MemoirCommentCount::getMemoirId, MemoirCommentCount::getCommentCount);
		Map<Long, Long> likeCountByMemoirId = toCountMap(
			memoirLikeRepository.countByMemoirIdIn(memoirIds), MemoirLikeCount::getMemoirId, MemoirLikeCount::getLikeCount);
		Set<Long> likedMemoirIds = resolveLikedMemoirIds(viewerId, memoirIds);
		List<Long> authorIds = memoirs.getContent().stream().map(Memoir::getAuthorId).distinct().toList();
		Map<Long, String> authorNameByAuthorId = userServiceClient.findNamesByIds(authorIds);
		List<Long> studyIds = memoirs.getContent().stream().map(Memoir::getStudyId).distinct().toList();
		Map<Long, StudySummaryView> studyInfoByStudyId = studyServiceClient.getStudySummaries(studyIds);

		return memoirs.map(memoir -> {
			StudySummaryView studyInfo = studyInfoByStudyId.get(memoir.getStudyId());
			return MemoirResponse.from(
				memoir,
				authorNameByAuthorId.get(memoir.getAuthorId()),
				studyInfo == null ? null : studyInfo.title(),
				studyInfo == null ? null : studyInfo.level(),
				studyInfo == null ? null : studyInfo.expPoint(),
				commentCountByMemoirId.getOrDefault(memoir.getId(), 0L),
				likeCountByMemoirId.getOrDefault(memoir.getId(), 0L),
				likedMemoirIds.contains(memoir.getId())
			);
		});
	}

	// 단건 조회/작성/좋아요 응답에서 이름 하나만 조회할 때 쓴다.
	private String resolveUserName(Long userId) {
		return userServiceClient.findNamesByIds(List.of(userId)).get(userId);
	}

	// 단건 조회(getMemoir/updateMemoir/likeMemoir/unlikeMemoir)에서 studyId 하나만 조회할 때 쓴다.
	private StudySummaryView resolveStudyInfo(Long studyId) {
		return studyServiceClient.getStudySummaries(List.of(studyId))
			.getOrDefault(studyId, new StudySummaryView(String.valueOf(studyId), null, null, null));
	}

	private <T> Map<Long, Long> toCountMap(List<T> rows, Function<T, Long> idFn, ToLongFunction<T> countFn) {
		Map<Long, Long> result = new LinkedHashMap<>();
		rows.forEach(row -> result.put(idFn.apply(row), countFn.applyAsLong(row)));
		return result;
	}

	private Set<Long> resolveLikedMemoirIds(Long viewerId, List<Long> memoirIds) {
		if (viewerId == null || memoirIds.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(memoirLikeRepository.findLikedMemoirIds(viewerId, memoirIds));
	}

	private boolean isLikedByViewer(Long memoirId, Long viewerId) {
		if (viewerId == null) {
			return false;
		}
		return memoirLikeRepository.existsByMemoirIdAndUserId(memoirId, viewerId);
	}

	private String normalizeKeyword(String keyword) {
		return StringUtils.hasText(keyword) ? keyword.trim() : null;
	}

	// study-service를 호출해 스터디가 실제로 존재하는지 + 이 유저가 방장이거나 승인된 멤버인지
	// 확인한다("/api/studies/{id}"의 myApplicationStatus를 재사용, 별도 멤버십 API를 만들지 않음).
	private StudyView resolveMyStudy(Long authorId, Long studyId) {
		StudyView study = studyServiceClient.getStudy(studyId)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 스터디: studyId={}", studyId);
				return new IllegalArgumentException("존재하지 않는 스터디입니다.");
			});

		boolean isMember = String.valueOf(authorId).equals(study.leaderId())
			|| "APPROVED".equals(study.myApplicationStatus());
		if (!isMember) {
			log.warn("스터디 멤버 아님: studyId={}, userId={}", studyId, authorId);
			throw new ForbiddenException("참여 중인 스터디만 회고록으로 연결할 수 있습니다.");
		}

		return study;
	}
}
