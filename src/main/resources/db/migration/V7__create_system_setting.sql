-- V7: 系统设置表（key-value 结构）
--
-- 为什么需要这张表？
--   图标抓取代理（favicon.proxy.*）等系统级配置需要管理员在前端修改，
--   环境变量方案改一次要重建容器，落库后可运行时动态生效。
--
-- 为什么用 key-value 而不是固定列？
--   未来的系统设置（如默认主题、注册开关等）可以直接加新 key，
--   不需要每次 ALTER TABLE。
CREATE TABLE IF NOT EXISTS `system_setting` (
    `setting_key`   VARCHAR(100)  NOT NULL COMMENT '设置键，如 favicon.proxy.host',
    `setting_value` VARCHAR(500)  DEFAULT NULL COMMENT '设置值，统一存字符串，由读取方自行转换类型',
    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 图标抓取代理的默认值：关闭、无地址（直连）
-- enabled=false 时 FaviconService/IconFetchService 直连目标网站
INSERT INTO `system_setting` (`setting_key`, `setting_value`) VALUES
    ('favicon.proxy.enabled', 'false'),
    ('favicon.proxy.host', ''),
    ('favicon.proxy.port', '');
