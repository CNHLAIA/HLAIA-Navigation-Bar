-- 将 bookmark 表的唯一约束从 (user_id, url) 改为 (user_id, folder_id, url)
-- 允许同一个 URL 出现在不同文件夹中，但同一文件夹内不能重复
ALTER TABLE `bookmark`
    DROP INDEX `uk_bookmark_user_url`,
    ADD UNIQUE KEY `uk_bookmark_user_folder_url` (`user_id`, `folder_id`, `url`(500));
