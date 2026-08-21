package com.groovy.backend.content;

import com.groovy.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "memoirs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Memoir extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// MSA 전환(content-service 추출): study는 Study Aggregate(study-service) 소속이라 JPA
	// 연관관계 대신 FK 값만 저장한다. 제목/레벨/경험치 등 표시용 정보는 StudyServiceClient로
	// 배치 조회한다.
	@Column(name = "study_id", nullable = false)
	private Long studyId;

	// MSA 전환: author는 User Aggregate(identity-service) 소속이라 JPA 연관관계 대신 FK 값만
	// 저장한다. 이름 등 상세 정보가 필요하면 UserServiceClient로 조회한다.
	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(nullable = false)
	private String title;

	// 마크다운 원문을 그대로 저장하고, 렌더링은 프론트에서 처리한다.
	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Builder
	public Memoir(Long studyId, Long authorId, String title, String content) {
		this.studyId = studyId;
		this.authorId = authorId;
		this.title = title;
		this.content = content;
	}

	public void update(String title, String content) {
		this.title = title;
		this.content = content;
	}

	public boolean isAuthor(Long userId) {
		return this.authorId.equals(userId);
	}
}
