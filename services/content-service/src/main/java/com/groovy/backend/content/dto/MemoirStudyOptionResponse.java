package com.groovy.backend.content.dto;

import com.groovy.backend.content.client.StudyServiceClient.StudyOptionView;

/**
 * 회고록 작성 시 연결할 스터디를 고르기 위한 최소 정보. 내가 방장이거나 승인되어 속한 스터디만 대상이다.
 */
public record MemoirStudyOptionResponse(
	String studyId,
	String title
) {

	public static MemoirStudyOptionResponse from(StudyOptionView option) {
		return new MemoirStudyOptionResponse(option.studyId(), option.title());
	}
}
