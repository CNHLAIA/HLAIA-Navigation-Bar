-- 将 bookmark 和 staging_item 表的 icon_url 字段从 VARCHAR(500) 改为 TEXT
-- 原因：Chrome 浏览器导出的书签中，ICON 属性包含 base64 编码的 favicon 数据，
-- 这些 data URI 格式为 "data:image/png;base64,..." ，长度通常为几千到几万字符，
-- 远超 VARCHAR(500) 的限制，导致导入时报错。
ALTER TABLE `bookmark` MODIFY COLUMN `icon_url` TEXT DEFAULT NULL;
ALTER TABLE `staging_item` MODIFY COLUMN `icon_url` TEXT DEFAULT NULL;
