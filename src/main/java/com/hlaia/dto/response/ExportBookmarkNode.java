package com.hlaia.dto.response;

import lombok.Data;

/**
 * 【导出书签节点】—— 可视化 HTML 导出时，单个书签的精简视图
 *
 * 为什么不直接复用 {@link BookmarkResponse}？
 *   BookmarkResponse 是「书签编辑/详情」接口的契约，含 id / folderId / createdAt 等字段，
 *   这些在导出的可视化 HTML 里毫无意义（用户只关心标题、网址、图标长什么样）。
 *   导出是一次性全量快照，独立 DTO 更干净，也避免序列化一堆用不到的字段。
 *
 * 字段对应的用途（在导出 HTML 中）：
 *   - title：卡片上的标题文字
 *   - url：卡片点击跳转的链接（&lt;a href&gt;）
 *   - iconUrl：favicon 图标地址，数据库里存的就是 base64 data URI，可直接喂给 &lt;img src&gt;
 *   - description：可选备注，未来可在卡片上展示（首版未渲染也不影响结构）
 *   - sortOrder：排序序号，前端按文件夹分区时保持原始顺序
 */
@Data
public class ExportBookmarkNode {

    /** 书签标题（卡片主文字） */
    private String title;

    /** 书签链接地址 */
    private String url;

    /**
     * 网站图标地址
     * 数据库里存的是 base64 data URI（如 "data:image/png;base64,iVBOR..."），
     * 前端导出 HTML 直接放进 &lt;img src="..."&gt; 即可离线显示，无需联网抓取。
     */
    private String iconUrl;

    /** 书签描述/备注，可为空 */
    private String description;

    /** 排序序号，同文件夹内数字越小越靠前 */
    private Integer sortOrder;
}
