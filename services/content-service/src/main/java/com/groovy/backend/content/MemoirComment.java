package com.groovy.backend.content;

import com.groovy.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MSA 전환(content-service 추출): author는 더 이상 User Aggregate(identity-service)를 JPA
 * 연관관계로 들고 있지 않고 FK 값(authorId)만 저장한다 — 이 서비스에는 User 테이블 자체가
 * 없다. 이름이 필요하면 UserServiceClient로 조회한다(MemoirCommentService 참고).
 */
@Entity
@Table(name = "memoir_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoirComment extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "memoir_id", nullable = false)
	private Memoir memoir;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Builder
	public MemoirComment(Memoir memoir, Long authorId, String content) {
		this.memoir = memoir;
		this.authorId = authorId;
		this.content = content;
	}

	public void update(String content) {
		this.content = content;
	}

	public boolean isAuthor(Long userId) {
		return this.authorId.equals(userId);
	}
}
