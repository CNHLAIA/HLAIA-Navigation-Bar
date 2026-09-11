-- 将 bookmark 表的 description 字段从 VARCHAR(500) 改为 TEXT
-- 原因：该字段现用于承载书签的 Markdown 备注，备注可能包含标题、列表、
-- 代码块等较长内容，VARCHAR(500) 对 Markdown 长文本偏紧；
-- 改为 TEXT（64KB）为非破坏性变更，已有数据原样保留。
-- 先例参照 V4：icon_url 同样因长度不足从 VARCHAR(500) 拓宽为 TEXT。
ALTER TABLE `bookmark` MODIFY COLUMN `description` TEXT COMMENT '书签备注(Markdown)';
