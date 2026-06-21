CREATE TABLE IF NOT EXISTS `user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `username`   VARCHAR(50)  NOT NULL,
    `password`   VARCHAR(255) NOT NULL,
    `email`      VARCHAR(100) DEFAULT NULL,
    `role`       VARCHAR(10)  NOT NULL DEFAULT 'USER',
    `status`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0=normal, 1=banned',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `folder` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       NOT NULL,
    `parent_id`  BIGINT       DEFAULT NULL COMMENT 'NULL means root level',
    `name`       VARCHAR(100) NOT NULL,
    `sort_order` INT          NOT NULL DEFAULT 0,
    `icon`       VARCHAR(50)  DEFAULT NULL,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_parent_id` (`parent_id`),
    CONSTRAINT `fk_folder_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_folder_parent` FOREIGN KEY (`parent_id`) REFERENCES `folder` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bookmark` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT        NOT NULL,
    `folder_id`   BIGINT        NOT NULL,
    `title`       VARCHAR(255)  NOT NULL,
    `url`         VARCHAR(2048) NOT NULL,
    `description` VARCHAR(500)  DEFAULT NULL,
    `icon_url`    VARCHAR(500)  DEFAULT NULL,
    `sort_order`  INT           NOT NULL DEFAULT 0,
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_folder_id` (`folder_id`),
    UNIQUE KEY `uk_bookmark_user_url` (`user_id`, `url`(500)),
    CONSTRAINT `fk_bookmark_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_bookmark_folder` FOREIGN KEY (`folder_id`) REFERENCES `folder` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `staging_item` (
    `id`         BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT        NOT NULL,
    `title`      VARCHAR(255)  NOT NULL,
    `url`        VARCHAR(2048) NOT NULL,
    `icon_url`   VARCHAR(500)  DEFAULT NULL,
    `expire_at`  DATETIME      NOT NULL,
    `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_expire_at` (`expire_at`),
    CONSTRAINT `fk_staging_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `operation_log` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       DEFAULT NULL,
    `action`     VARCHAR(50)  NOT NULL,
    `target`     VARCHAR(200) DEFAULT NULL,
    `detail`     TEXT         DEFAULT NULL,
    `ip`         VARCHAR(50)  DEFAULT NULL,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
