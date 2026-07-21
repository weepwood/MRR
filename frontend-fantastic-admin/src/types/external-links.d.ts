import '#/global'

declare module '#/global' {
  namespace Settings {
    interface externalLink {
      id: string
      title: string
      url: string
      icon: string
    }

    interface menu {
      /**
       * 用户在当前浏览器中维护的外部网站快捷入口。
       * 仅在单栏分组导航模式下显示在“其他”分组中。
       */
      externalLinks?: externalLink[]
    }
  }
}
