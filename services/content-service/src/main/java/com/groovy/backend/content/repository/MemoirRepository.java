package com.groovy.backend.content.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groovy.backend.content.Memoir;

public interface MemoirRepository extends JpaRepository<Memoir, Long> {

	// MSA 전환(content-service 추출): study/author 둘 다 더 이상 JPA 연관관계가 아니라 studyId/
	// authorId(Long)뿐이라 fetch join이 필요 없다. 제목/레벨/경험치, 작성자 이름 등 표시용 정보는
	// MemoirService가 StudyServiceClient/UserServiceClient로 배치 조회한다. 메서드 이름은 groovy
	// 시절 호출부와 맞추기 위해 그대로 남겨둔다.
	@Query(
		value = "SELECT m FROM Memoir m "
			+ "WHERE (:keyword IS NULL "
			+ "OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')))",
		countQuery = "SELECT COUNT(m) FROM Memoir m "
			+ "WHERE (:keyword IS NULL "
			+ "OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')))"
	)
	Page<Memoir> search(@Param("keyword") String keyword, Pageable pageable);

	@Query(
		value = "SELECT m FROM Memoir m WHERE m.authorId = :authorId",
		countQuery = "SELECT COUNT(m) FROM Memoir m WHERE m.authorId = :authorId"
	)
	Page<Memoir> findByAuthorIdWithStudyAndAuthor(@Param("authorId") Long authorId, Pageable pageable);

	@Query("SELECT m FROM Memoir m WHERE m.id = :id")
	Optional<Memoir> findByIdWithStudyAndAuthor(@Param("id") Long id);

	@Query("SELECT m FROM Memoir m WHERE m.id IN :ids")
	List<Memoir> findAllWithStudyAndAuthorByIdIn(@Param("ids") List<Long> ids);

	/**
	 * "인기순"(좋아요 수 + 댓글 수 내림차순) 정렬 id 목록. 좋아요/댓글 카운트를 서브쿼리로 미리 집계한 뒤
	 * 정렬·페이지네이션을 DB에서 수행해, 회고록 건수가 늘어나도 요청당 처리량은 페이지 크기만큼으로 고정된다.
	 *
	 * MSA 전환(content-service 추출): content_db에는 users 테이블이 물리적으로 없다(identity_db
	 * 소속) — groovy 시절엔 같은 스키마에 있어 "작성자 이름으로 검색"을 위해 users를 JOIN했지만
	 * 이 서비스에서는 그 조인 자체가 불가능하다. 제목/내용 검색만 남기고 작성자 이름 검색은
	 * 제거했다(복원하려면 identity-service에 이름 검색 API를 만들고 2단계 조회로 바꿔야 한다).
	 */
	@Query(
		value = "SELECT m.id FROM memoirs m "
			+ "LEFT JOIN (SELECT memoir_id, COUNT(*) AS cnt FROM memoir_likes GROUP BY memoir_id) lc ON lc.memoir_id = m.id "
			+ "LEFT JOIN (SELECT memoir_id, COUNT(*) AS cnt FROM memoir_comments GROUP BY memoir_id) cc ON cc.memoir_id = m.id "
			+ "WHERE (:keyword IS NULL "
			+ "OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
			+ "ORDER BY (COALESCE(lc.cnt, 0) + COALESCE(cc.cnt, 0)) DESC, m.id DESC",
		countQuery = "SELECT COUNT(*) FROM memoirs m "
			+ "WHERE (:keyword IS NULL "
			+ "OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')))",
		nativeQuery = true
	)
	Page<Long> findIdsByPopularity(@Param("keyword") String keyword, Pageable pageable);
}
