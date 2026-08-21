package com.groovy.backend.content.notification;

import org.springframework.stereotype.Component;

import com.groovy.backend.content.outbox.OutboxEventWriter;
import com.groovy.backend.eventcontract.notification.NotificationPayload;

import lombok.RequiredArgsConstructor;

/**
 * MSA 전환(content-service 추출): groovy(레거시)의 NotificationOutboxPublisher에서 Memoir가
 * 쓰던 두 메서드(memoirCommentAdded, memoirLikeAdded)를 옮겨온다. Memoir가 실제로 이 서비스로
 * 이관되면서 groovy 쪽 Outbox/notification 인프라는 더 이상 쓰는 도메인이 없어 통째로
 * 제거했다(Tag는 Outbox를 쓰지 않는다).
 */
@Component
@RequiredArgsConstructor
public class NotificationOutboxPublisher {

	private final OutboxEventWriter outboxEventWriter;

	public void memoirCommentAdded(Long recipientUserId, String commenterName, Long memoirId, String memoirTitle) {
		publish(recipientUserId, "MEMOIR_COMMENT_ADDED", "새로운 댓글이 달렸습니다",
			"%s님이 \"%s\"에 댓글을 남겼어요.".formatted(commenterName, memoirTitle), memoirId);
	}

	public void memoirLikeAdded(Long recipientUserId, String likerName, Long memoirId, String memoirTitle) {
		publish(recipientUserId, "MEMOIR_LIKE_ADDED", "회고록에 좋아요가 눌렸어요",
			"%s님이 \"%s\"을(를) 좋아해요.".formatted(likerName, memoirTitle), memoirId);
	}

	private void publish(Long recipientUserId, String type, String title, String message, Long targetId) {
		outboxEventWriter.write(type, new NotificationPayload(recipientUserId, type, title, message, targetId));
	}
}
