package com.groovy.backend.content.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groovy.backend.content.MemoirLike;

public interface MemoirLikeRepository extends JpaRepository<MemoirLike, Long> {

	boolean existsByMemoirIdAndUserId(Long memoirId, Long userId);

	Optional<MemoirLike> findByMemoirIdAndUserId(Long memoirId, Long userId);

	long countByMemoirId(Long memoirId);

	void deleteAllByMemoirId(Long memoirId);

	@Query("SELECT l.memoir.id AS memoirId, COUNT(l) AS likeCount FROM MemoirLike l WHERE l.memoir.id IN :memoirIds GROUP BY l.memoir.id")
	List<MemoirLikeCount> countByMemoirIdIn(@Param("memoirIds") List<Long> memoirIds);

	@Query("SELECT l.memoir.id FROM MemoirLike l WHERE l.userId = :userId AND l.memoir.id IN :memoirIds")
	List<Long> findLikedMemoirIds(@Param("userId") Long userId, @Param("memoirIds") List<Long> memoirIds);

	interface MemoirLikeCount {
		Long getMemoirId();

		Long getLikeCount();
	}
}
