# 文档目录

适配 Apple Music 新构建从[宿主适配手册](host-adaptation-guide.md)开始。先确认[架构与改动位置](host-adaptation-guide.md#architecture)、[版本解析](host-adaptation-guide.md#profile-contracts)和[适配流程](host-adaptation-guide.md#adaptation-workflow)，再按[功能域索引](host-adaptation-guide.md#feature-index)定位接缝。

| 文档 | 用途与内容归属 |
|---|---|
| [宿主适配手册](host-adaptation-guide.md) | 当前架构、页面族、宿主接缝、通用维护规则与变更触发；区分新构建接入和共享策略修改 |
| [宿主适配清单](host-adaptation-checklist.md) | 每个构建的证据、适用性、安装/降级判定及交付模板；复制到任务记录填写 |
| [功能保全矩阵](feature-preservation.md) | 用户可见行为、适用边界和人工场景的集中依据，含[结果记录模板](feature-preservation.md#acceptance-record) |
| [TTML 格式说明](apple-music-ttml-format.md) | 歌词编写、转换与时间轴整理的格式约定 |
| [插件开发手册](plugin-development.md) | SDK、ZIP、生命周期与冲突规则的参考 |
| [插件作者教程](plugin-author-guide.md) | 从零建工程、Hook/设置、打包导入、调试和版本适配的操作教程 |
| [本地化说明](localization.md) | 设置文案、语言选择、共享文本目录与翻译维护 |

精确版本、成员描述符、启用资格、证据与已知降级以 [host-profiles](../host-applemusic/src/main/resources/host-profiles/) 为准，必需角色和类型关系以代码 Contract / Inventory 为准。手册解释语义，清单引用规则登记证据，矩阵维护人工场景；修改功能时分别更新受影响职责，避免复制同一段正文。单次排查、构建日志与实际验收结果只保存在对应任务记录。
