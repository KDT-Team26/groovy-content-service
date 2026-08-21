package com.groovy.backend.content;

import com.groovy.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Memoir(N:1) - User(N:1) 좋아요 중개 엔티티. 한 유저는 같은 회고록에 좋아요를 한 번만 남길 수 있다.
 *
 * MSA 전환(content-service 추출): user는 더 이상 User Aggregate(identity-service)를 JPA
 * 연관관계로 들고 있지 않고 FK 값(userId)만 저장한다 — 이 서비스에는 User 테이블이 없다.
 */
@Entity
@Table(
	name = "memoir_likes",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_memoir_like",
		columnNames = {"memoir_id", "user_id"}
	)
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoirLike extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "memoir_id", nullable = false)
	private Memoir memoir;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Builder
	public MemoirLike(Memoir memoir, Long userId) {
		this.memoir = memoir;
		this.userId = userId;
	}
}
