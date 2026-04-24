export default {
  common: {
    cancel: '取消',
    delete: '删除',
    save: '保存',
    confirm: '确认',
    loading: '加载中...',
    failed: '操作失败',
    success: '操作成功',
    add: '添加',
    create: '创建',
    rename: '重命名',
    edit: '编辑',
    search: '搜索',
    noData: '暂无数据',
    actions: '操作',
    previous: '上一页',
    next: '下一页'
  },
  auth: {
    login: {
      subtitle: '登录到你的导航栏',
      username: '用户名',
      password: '密码',
      signIn: '登录',
      noAccount: '还没有账号？',
      createOne: '立即注册',
      validation: {
        usernameRequired: '请输入用户名',
        usernameLength: '用户名长度为 3-50 个字符',
        passwordRequired: '请输入密码',
        passwordLength: '密码至少 6 个字符'
      },
      toast: {
        success: '登录成功',
        signedOut: '已退出登录',
        signOutFailed: '退出登录失败'
      }
    },
    register: {
      subtitle: '创建你的账号',
      username: '用户名',
      password: '密码',
      confirmPassword: '确认密码',
      nickname: '昵称（可选）',
      nicknamePlaceholder: '给自己取个名字吧',
      createAccount: '注册',
      hasAccount: '已有账号？',
      signIn: '去登录',
      validation: {
        usernameRequired: '请输入用户名',
        usernameLength: '用户名长度为 3-50 个字符',
        passwordRequired: '请输入密码',
        passwordLength: '密码至少 6 个字符',
        confirmPasswordRequired: '请确认密码',
        passwordMismatch: '两次输入的密码不一致',
        nicknameLength: '昵称不能超过15个字符'
      },
      toast: {
        success: '注册成功'
      }
    }
  },
  nav: {
    bookmarks: '书签',
    staging: '暂存区',
    admin: '管理',
    settings: '设置',
    signOut: '退出登录'
  },
  bookmarks: {
    loading: '正在加载书签...',
    addBookmark: '添加书签',
    selectFolder: '请选择一个文件夹',
    empty: {
      noBookmarks: '暂无书签',
      addFirst: '添加你的第一个书签到此文件夹',
      chooseFolder: '从侧边栏选择一个文件夹来查看书签'
    },
    contextMenu: {
      edit: '编辑',
      delete: '删除',
      moveTo: '移动到...',
      moveSelectedTo: '移动选中到...'
    },
    editDialog: {
      title: '编辑书签'
    },
    form: {
      title: '标题',
      url: '网址',
      iconUrl: '图标网址（可选）',
      titlePlaceholder: '书签标题',
      urlPlaceholder: 'https://example.com',
      iconUrlPlaceholder: '网站图标网址'
    },
    validation: {
      titleRequired: '请输入标题',
      urlRequired: '请输入网址'
    },
    toast: {
      added: '书签已添加',
      updated: '书签已更新',
      deleted: '书签已删除',
      orderFailed: '排序更新失败',
      moved: '书签已移动',
      movedBatch: '已移动 {count} 个书签',
      moveFailed: '移动失败'
    },
    deleteConfirm: {
      title: '确定删除"{title}"吗？',
      confirmTitle: '删除书签'
    },
    batch: {
      confirmTitle: '删除书签',
      confirmMessage: '确定要删除选中的 {count} 个书签吗？',
      deleted: '已删除 {count} 个书签',
      linksCopied: '链接已复制到剪贴板',
      copyFailed: '复制链接失败'
    },
    moveDialog: {
      title: '移动到文件夹',
      description: '选择目标文件夹：',
      confirm: '移动',
      empty: '暂无可用文件夹'
    },
    importDialog: {
      title: '导入书签',
      selectFile: '选择文件',
      fileTip: '仅支持 Chrome 浏览器导出的 HTML 书签文件',
      targetFolder: '目标文件夹',
      targetFolderHint: '书签将导入到选中的文件夹下',
      duplicateMode: '重复处理',
      overwrite: '覆盖更新',
      skip: '跳过',
      importBtn: '开始导入',
      importing: '正在导入...',
      noFile: '请先选择要导入的文件',
      result: '导入完成：新建 {folders} 个文件夹，{bookmarks} 个书签，{duplicates} 个{mode}',
      overwritten: '覆盖',
      skipped: '跳过',
      failed: '导入失败，请检查文件格式是否正确'
    }
  },
  batchToolbar: {
    selected: '已选择',
    delete: '删除',
    copyLinks: '复制链接',
    selectAll: '全选',
    deselectAll: '取消全选'
  },
  folders: {
    newFolder: '新建文件夹',
    noFolders: '暂无文件夹',
    renameFolder: '重命名文件夹',
    folderName: '文件夹名称',
    create: '创建',
    save: '保存',
    contextMenu: {
      rename: '重命名',
      newSubfolder: '新建子文件夹',
      delete: '删除'
    },
    validation: {
      nameRequired: '请输入文件夹名称'
    },
    toast: {
      created: '文件夹已创建',
      renamed: '文件夹已重命名',
      deleted: '文件夹已删除',
      orderFailed: '排序更新失败'
    },
    confirm: {
      title: '删除文件夹',
      message: '该文件夹及其所有子文件夹和书签将被永久删除，此操作无法撤销。'
    },
    newFolderDialog: {
      title: '新建文件夹'
    }
  },
  staging: {
    empty: {
      title: '暂存区为空',
      description: '将链接保存到这里，方便日后阅读或整理'
    },
    timer: {
      noExpiry: '不过期',
      expired: '已过期'
    },
    tooltips: {
      moveToFolder: '移至文件夹',
      setExpiry: '设置过期时间',
      delete: '删除'
    },
    view: {
      title: '暂存区',
      addToStaging: '添加到暂存区',
      toast: {
        signedOut: '已退出登录',
        signOutFailed: '退出登录失败',
        loadFailed: '加载暂存数据失败',
        added: '已添加到暂存区',
        moved: '已移至文件夹',
        expiryUpdated: '过期时间已更新',
        removed: '已从暂存区移除'
      },
      addDialog: {
        title: '添加到暂存区',
        titlePlaceholder: '页面标题',
        urlPlaceholder: 'https://example.com',
        iconUrlPlaceholder: '网站图标网址'
      },
      moveDialog: {
        title: '移至文件夹',
        description: '选择一个目标文件夹：',
        empty: '暂无可用文件夹，请先在书签页面创建一个。'
      },
      expiryDialog: {
        title: '设置过期时间',
        label: '过期时间（分钟）',
        presets: {
          '30min': '30 分钟',
          '1hour': '1 小时',
          '6hours': '6 小时',
          '1day': '1 天',
          '3days': '3 天',
          '7days': '7 天'
        }
      },
      removeConfirm: {
        title: '从暂存区移除',
        message: '确定要将此项从暂存区移除吗？'
      },
      validation: {
        titleRequired: '请输入标题',
        urlRequired: '请输入网址'
      }
    }
  },
  admin: {
    users: {
      title: '用户管理',
      countSuffix: '个用户',
      loading: '正在加载用户...',
      tableHeaders: {
        username: '用户名',
        email: '邮箱',
        role: '角色',
        status: '状态',
        joined: '注册时间',
        actions: '操作'
      },
      status: {
        active: '正常',
        banned: '已封禁'
      },
      tooltips: {
        viewDetails: '查看详情',
        unban: '解封用户',
        ban: '封禁用户'
      },
      empty: {
        noUsers: '没有找到用户',
        description: '系统中暂无注册用户'
      },
      toast: {
        loadFailed: '加载用户列表失败',
        unbanned: '用户 {username} 已解封',
        banned: '用户 {username} 已被封禁',
        actionFailed: '封禁/解封操作失败'
      },
      banConfirm: {
        title: '封禁用户',
        message: '确定要封禁用户 {username} 吗？封禁后该用户将无法登录。'
      }
    },
    userDetail: {
      backToUsers: '返回用户列表',
      loading: '正在加载用户数据...',
      noEmail: '未设置邮箱',
      labels: {
        role: '角色',
        status: '状态',
        joined: '注册时间',
        active: '正常',
        banned: '已封禁'
      },
      sidebar: {
        folders: '文件夹',
        noFolders: '暂无文件夹'
      },
      empty: {
        noFolders: '该用户尚未创建任何文件夹。'
      },
      content: {
        selectFolder: '选择一个文件夹',
        noBookmarks: '该文件夹中没有书签',
        chooseFolder: '选择文件夹以查看书签'
      },
      deleteConfirm: {
        title: '删除文件夹',
        message: '{name} 及其所有内容将被永久删除，此操作无法撤销。'
      },
      error: {
        notFound: '用户不存在',
        notFoundDescription: '请求的用户不存在或已被删除。'
      },
      toast: {
        loadFailed: '加载用户数据失败',
        bookmarksLoadFailed: '加载书签失败',
        folderDeleted: '文件夹已删除'
      }
    }
  },
  mainView: {
    toast: {
      foldersLoadFailed: '加载文件夹失败'
    }
  },
  notFound: {
    title: '页面未找到',
    description: '您访问的页面不存在',
    goBack: '返回首页'
  },
  settings: {
    title: '个人设置',
    profile: {
      title: '基本信息',
      nickname: '昵称',
      nicknamePlaceholder: '输入昵称',
      email: '邮箱',
      emailPlaceholder: '输入邮箱地址',
      save: '保存修改',
      toast: {
        saved: '个人信息已更新',
        loadFailed: '加载个人信息失败',
        saveFailed: '保存失败'
      }
    },
    password: {
      title: '修改密码',
      oldPassword: '当前密码',
      oldPasswordPlaceholder: '输入当前密码',
      newPassword: '新密码',
      newPasswordPlaceholder: '输入新密码（至少6位）',
      confirmPassword: '确认新密码',
      confirmPasswordPlaceholder: '再次输入新密码',
      change: '修改密码',
      toast: {
        changed: '密码修改成功',
        mismatch: '两次输入的新密码不一致',
        wrongOld: '当前密码不正确'
      },
      validation: {
        oldRequired: '请输入当前密码',
        newRequired: '请输入新密码',
        newLength: '新密码至少6个字符',
        confirmRequired: '请确认新密码'
      }
    }
  }
}
