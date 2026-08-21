-- MSA 전환(content-service 추출, Phase 7 DB per Service 패턴 재사용): groovy(레거시)의
-- memoirs/memoir_comments/memoir_likes/outbox_events를 content_db 스키마로 옮겨온다. study_id는
-- study-service 소속(Study Aggregate), author_id/user_id는 identity-service 소속(User
-- Aggregate)이라 이 스키마에 FK를 걸지 않는다(study_db의 leader_id, calendar_db의 user_id/
-- study_id와 동일한 이유) — groovy V3/V4의 FK 제약은 여기서 전부 뺀다.

CREATE TABLE `memoirs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `content` text NOT NULL,
  `study_id` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_memoirs_study_id` (`study_id`),
  KEY `idx_memoirs_author_id` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `memoir_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `content` text NOT NULL,
  `memoir_id` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_memoir_comments_memoir_id` (`memoir_id`),
  KEY `idx_memoir_comments_author_id` (`author_id`),
  CONSTRAINT `FK_memoir_comments_memoir` FOREIGN KEY (`memoir_id`) REFERENCES `memoirs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `memoir_likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `memoir_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_memoir_like` (`memoir_id`,`user_id`),
  KEY `idx_memoir_likes_user_id` (`user_id`),
  CONSTRAINT `FK_memoir_likes_memoir` FOREIGN KEY (`memoir_id`) REFERENCES `memoirs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `outbox_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `event_id` varchar(36) NOT NULL,
  `event_type` varchar(50) NOT NULL,
  `payload` TEXT NOT NULL,
  `published` boolean NOT NULL DEFAULT false,
  `published_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_unpublished` (`published`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
