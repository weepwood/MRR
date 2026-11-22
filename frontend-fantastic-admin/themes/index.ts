export const lightTheme = {
  'color-scheme': 'light',
  // shadcn
  '--background': '210 40% 98%',
  '--foreground': '222.2 84% 4.9%',
  '--card': '0 0% 100%',
  '--card-foreground': '222.2 84% 4.9%',
  '--popover': '0 0% 100%',
  '--popover-foreground': '222.2 84% 4.9%',
  // 首帧默认值：与 tokens.css 的 --color-primary（#2563EB）一致。运行时 theme-color.ts 会按
  // 用户主色覆盖；此前这里是 201 96% 32%（青蓝），JS 接管前的首帧按钮、链接与焦点环会闪一下色相。
  '--primary': '221.2 83.2% 53.3%',
  '--primary-foreground': '0 0% 100%',
  '--secondary': '210 40% 96.1%',
  '--secondary-foreground': '222.2 47.4% 11.2%',
  '--muted': '210 40% 96.1%',
  // 次要文字在工作区底（#F1F5F9）上原为 4.34:1，低于 WCAG AA 4.5:1；加深到 4.87:1（卡面 5.34:1）。
  '--muted-foreground': '215.4 19% 44%',
  '--accent': '210 40% 96.1%',
  '--accent-foreground': '222.2 47.4% 11.2%',
  // 与 --color-danger（#D13C3C）一致。原 #EF4444 作为表单错误文字在白底上只有 3.76:1。
  '--destructive': '0 61.8% 52.7%',
  '--destructive-foreground': '0 0% 100%',
  '--border': '214.3 31.8% 91.4%',
  '--input': '214.3 31.8% 91.4%',
  '--ring': '221.2 83.2% 53.3%',
  // 主要区域
  '--g-main-area-bg': 'hsl(210 40% 96%)',
  // 头部
  '--g-header-bg': 'hsl(var(--background))',
  '--g-header-color': 'hsl(var(--foreground))',
  '--g-header-menu-color': 'hsl(var(--accent-foreground))',
  '--g-header-menu-hover-bg': 'hsl(var(--accent))',
  '--g-header-menu-hover-color': 'hsl(var(--accent-foreground))',
  '--g-header-menu-active-bg': 'hsl(var(--primary))',
  '--g-header-menu-active-color': 'hsl(var(--primary-foreground))',
  // 主导航
  '--g-main-sidebar-bg': 'hsl(var(--background))',
  '--g-main-sidebar-menu-color': 'hsl(var(--accent-foreground))',
  '--g-main-sidebar-menu-hover-bg': 'hsl(var(--accent))',
  '--g-main-sidebar-menu-hover-color': 'hsl(var(--accent-foreground))',
  '--g-main-sidebar-menu-active-bg': 'hsl(var(--primary))',
  '--g-main-sidebar-menu-active-color': 'hsl(var(--primary-foreground))',
  // 次导航
  '--g-sub-sidebar-bg': 'hsl(var(--background))',
  '--g-sub-sidebar-menu-color': 'hsl(var(--accent-foreground))',
  '--g-sub-sidebar-menu-hover-bg': 'hsl(var(--accent))',
  '--g-sub-sidebar-menu-hover-color': 'hsl(var(--accent-foreground))',
  '--g-sub-sidebar-menu-active-bg': 'hsl(var(--primary))',
  '--g-sub-sidebar-menu-active-color': 'hsl(var(--primary-foreground))',
  // 标签栏
  '--g-tabbar-bg': 'var(--g-main-area-bg)',
  '--g-tabbar-dividers-bg': 'hsl(var(--accent-foreground) / 50%)',
  '--g-tabbar-tab-color': 'hsl(var(--accent-foreground) / 50%)',
  '--g-tabbar-tab-hover-bg': 'hsl(var(--border))',
  '--g-tabbar-tab-hover-color': 'hsl(var(--accent-foreground) / 50%)',
  '--g-tabbar-tab-active-bg': 'hsl(var(--background))',
  '--g-tabbar-tab-active-color': 'hsl(var(--foreground))',
  // 工具栏
  '--g-toolbar-bg': 'hsl(var(--background))',
}

export const darkTheme = {
  'color-scheme': 'dark',
  // shadcn
  '--background': '222 28% 9%',
  '--foreground': '216 28% 95%',
  '--card': '222 23% 12%',
  '--card-foreground': '216 28% 95%',
  '--popover': '222 22% 14%',
  '--popover-foreground': '0 0% 98%',
  // 暗色首帧同样使用品牌蓝。原值是 shadcn 默认的近白色：JS 接管前 FaButton 主按钮是白底黑字，
  // 而 Element Plus 主按钮是蓝底，两套按钮在暗色下首帧颜色完全不同。
  '--primary': '221.2 83.2% 53.3%',
  '--primary-foreground': '0 0% 100%',
  '--secondary': '220 20% 18%',
  '--secondary-foreground': '0 0% 98%',
  '--muted': '220 20% 18%',
  '--muted-foreground': '215 16% 70%',
  '--accent': '220 20% 18%',
  '--accent-foreground': '0 0% 98%',
  // 原 #7F1D1D 在暗色卡面上只有 1.7:1，注册/改密表单的错误提示几乎看不见；提亮到 5.7:1。
  '--destructive': '0 80% 68%',
  '--destructive-foreground': '222 28% 9%',
  '--border': '218 18% 25%',
  '--input': '218 18% 25%',
  '--ring': '221.2 83.2% 53.3%',
  // 主要区域
  '--g-main-area-bg': 'hsl(var(--background))',
  // 头部
  '--g-header-bg': 'hsl(var(--background))',
  '--g-header-color': 'hsl(var(--foreground))',
  '--g-header-menu-color': 'hsl(var(--muted-foreground))',
  '--g-header-menu-hover-bg': 'hsl(var(--muted))',
  '--g-header-menu-hover-color': 'hsl(var(--muted-foreground))',
  '--g-header-menu-active-bg': 'hsl(var(--accent))',
  '--g-header-menu-active-color': 'hsl(var(--accent-foreground))',
  // 主导航
  '--g-main-sidebar-bg': 'hsl(var(--background))',
  '--g-main-sidebar-menu-color': 'hsl(var(--muted-foreground))',
  '--g-main-sidebar-menu-hover-bg': 'hsl(var(--muted))',
  '--g-main-sidebar-menu-hover-color': 'hsl(var(--muted-foreground))',
  '--g-main-sidebar-menu-active-bg': 'hsl(var(--accent))',
  '--g-main-sidebar-menu-active-color': 'hsl(var(--accent-foreground))',
  // 次导航
  '--g-sub-sidebar-bg': 'hsl(var(--background))',
  '--g-sub-sidebar-menu-color': 'hsl(var(--muted-foreground))',
  '--g-sub-sidebar-menu-hover-bg': 'hsl(var(--muted))',
  '--g-sub-sidebar-menu-hover-color': 'hsl(var(--muted-foreground))',
  '--g-sub-sidebar-menu-active-bg': 'hsl(var(--accent))',
  '--g-sub-sidebar-menu-active-color': 'hsl(var(--accent-foreground))',
  // 标签栏
  '--g-tabbar-bg': 'var(--g-main-area-bg)',
  '--g-tabbar-dividers-bg': 'hsl(var(--accent-foreground) / 50%)',
  '--g-tabbar-tab-color': 'hsl(var(--accent-foreground) / 50%)',
  '--g-tabbar-tab-hover-bg': 'hsl(var(--accent) / 50%)',
  '--g-tabbar-tab-hover-color': 'hsl(var(--accent-foreground) / 50%)',
  '--g-tabbar-tab-active-bg': 'hsl(var(--secondary))',
  '--g-tabbar-tab-active-color': 'hsl(var(--foreground))',
  // 工具栏
  '--g-toolbar-bg': 'hsl(var(--background))',
}
