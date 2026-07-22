/**
 * 导出页 mock 数据 —— 开发期实时预览用
 *
 * 结构与后端 ExportDataResponse 严格对齐：
 *   { exportedAt, folders: ExportFolderNode[] }
 *   ExportFolderNode { id, name, icon, sortOrder, children: [], bookmarks: [] }
 *   ExportBookmarkNode { title, url, iconUrl, description, sortOrder }
 *
 * 覆盖典型形态：多根文件夹、多层嵌套、带图标书签、空文件夹、长标题/长 URL 边界。
 * 这些 mock 不联网，iconUrl 用内联 SVG data URI，保证预览页离线可见。
 */

// 内联 SVG favicon 生成器：用一个字母 + 背景色拼成 data URI，模拟站点 favicon
function letterIcon(letter, bg) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48"><rect width="48" height="48" rx="10" fill="${bg}"/><text x="24" y="33" font-size="24" font-family="sans-serif" font-weight="700" fill="#fff" text-anchor="middle">${letter}</text></svg>`
  return 'data:image/svg+xml;base64,' + btoa(svg)
}

export const exportMockData = {
  exportedAt: '2026-07-22',
  folders: [
    {
      id: 1,
      name: '常用工具',
      icon: null,
      sortOrder: 1,
      children: [],
      bookmarks: [
        { title: 'GitHub', url: 'https://github.com', iconUrl: letterIcon('G', '#24292e'), description: '全球最大代码托管平台', sortOrder: 1 },
        { title: 'Stack Overflow', url: 'https://stackoverflow.com', iconUrl: letterIcon('S', '#f48024'), description: '程序员问答社区', sortOrder: 2 },
        { title: 'Google', url: 'https://www.google.com', iconUrl: letterIcon('G', '#4285f4'), description: null, sortOrder: 3 },
        { title: 'Vite', url: 'https://vitejs.dev', iconUrl: letterIcon('V', '#646cff'), description: '下一代前端构建工具', sortOrder: 4 },
        { title: 'MDN', url: 'https://developer.mozilla.org', iconUrl: letterIcon('M', '#000'), description: 'Web 文档', sortOrder: 5 },
        { title: 'Notion', url: 'https://notion.so', iconUrl: letterIcon('N', '#111'), description: '笔记工作台', sortOrder: 6 },
        { title: 'Figma', url: 'https://figma.com', iconUrl: letterIcon('F', '#a259ff'), description: '协作设计工具', sortOrder: 7 },
        { title: 'Vercel', url: 'https://vercel.com', iconUrl: letterIcon('V', '#000'), description: '前端部署平台', sortOrder: 8 },
        { title: 'Cloudflare', url: 'https://cloudflare.com', iconUrl: letterIcon('C', '#f38020'), description: 'CDN 与边缘计算', sortOrder: 9 },
        { title: 'Docker', url: 'https://docker.com', iconUrl: letterIcon('D', '#2496ed'), description: '容器化平台', sortOrder: 10 },
        { title: 'npm', url: 'https://npmjs.com', iconUrl: letterIcon('n', '#cb3837'), description: 'Node 包仓库', sortOrder: 11 },
        { title: 'ChatGPT', url: 'https://chat.openai.com', iconUrl: letterIcon('C', '#10a37f'), description: 'AI 助手', sortOrder: 12 }
      ]
    },
    {
      id: 2,
      name: '开发资源',
      icon: null,
      sortOrder: 2,
      children: [
        {
          id: 3,
          name: '前端框架',
          icon: null,
          sortOrder: 1,
          children: [],
          bookmarks: [
            { title: 'Vue.js', url: 'https://vuejs.org', iconUrl: letterIcon('V', '#42b883'), description: '渐进式 JavaScript 框架', sortOrder: 1 },
            { title: 'Element Plus', url: 'https://element-plus.org', iconUrl: letterIcon('E', '#409eff'), description: null, sortOrder: 2 },
            { title: 'Pinia', url: 'https://pinia.vuejs.org', iconUrl: letterIcon('P', '#ffd859'), description: 'Vue 官方推荐状态管理库', sortOrder: 3 }
          ]
        },
        {
          id: 4,
          name: '后端技术',
          icon: null,
          sortOrder: 2,
          children: [],
          bookmarks: [
            { title: 'Spring Boot', url: 'https://spring.io/projects/spring-boot', iconUrl: letterIcon('S', '#6db33f'), description: 'Java 应用框架', sortOrder: 1 },
            { title: 'MyBatis-Plus', url: 'https://baomidou.com', iconUrl: letterIcon('M', '#3273dc'), description: 'MyBatis 增强工具', sortOrder: 2 },
            { title: 'Redis', url: 'https://redis.io', iconUrl: letterIcon('R', '#dc382d'), description: null, sortOrder: 3 }
          ]
        }
      ],
      bookmarks: [
        { title: 'Maven Repository', url: 'https://mvnrepository.com', iconUrl: letterIcon('M', '#c71a36'), description: 'Java 依赖仓库', sortOrder: 1 }
      ]
    },
    {
      id: 5,
      name: '学习园地',
      icon: null,
      sortOrder: 3,
      children: [],
      bookmarks: [
        { title: 'MDN Web Docs', url: 'https://developer.mozilla.org', iconUrl: letterIcon('M', '#000000'), description: 'Web 技术权威文档', sortOrder: 1 },
        { title: '菜鸟教程', url: 'https://www.runoob.com', iconUrl: letterIcon('R', '#3cc'), description: null, sortOrder: 2 },
        { title: '一个标题特别长特别长特别长的书签用来测试文本截断效果的边界情况', url: 'https://example.com/very/long/path/that/should/be/truncated/gracefully/when/displayed/in/the/card', iconUrl: letterIcon('L', '#8e44ad'), description: '边界用例：长标题 + 长 URL', sortOrder: 3 }
      ]
    },
    {
      id: 6,
      name: '空文件夹',
      icon: null,
      sortOrder: 4,
      children: [],
      bookmarks: []
    }
  ]
}
