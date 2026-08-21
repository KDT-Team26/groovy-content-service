package com.groovy.backend.content.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groovy.backend.content.MemoirComment;

public interface MemoirCommentRepository extends JpaRepository<MemoirComment, Long> {

	// MSA 전환(content-service 추출): author가 더 이상 JPA 연관관계가 아니라 authorId(Long)뿐이라
	// fetch join이 필요 없다(예전엔 JOIN FETCH c.author가 있었다). 작성자 이름은
	// MemoirCommentService가 UserServiceClient로 배치 조회한다.
	@Query("SELECT c FROM MemoirComment c WHERE c.memoir.id = :memoirId ORDER BY c.id ASC")
	List<MemoirComment> findByMemoirIdWithAuthor(@Param("memoirId") Long memoirId);

	long countByMemoirId(Long memoirId);

	void deleteAllByMemoirId(Long memoirId);

	@Query("SELECT c.memoir.id AS memoirId, COUNT(c) AS commentCount FROM MemoirComment c WHERE c.memoir.id IN :memoirIds GROUP BY c.memoir.id")
	List<MemoirCommentCount> countByMemoirIdIn(@Param("memoirIds") List<Long> memoirIds);

	interface MemoirCommentCount {
		Long getMemoirId();

		Long getCommentCount();
	}
}
