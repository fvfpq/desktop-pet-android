# 萝莉宠物桌面悬浮窗 (LoliPet)

Android 桌面二次元萝莉宠物应用：通过悬浮窗在屏幕任意位置展示高清 Live2D 角色，支持拖拽、点击互动、抚摸反馈，并可接入 DeepSeek API 实现语音/文字聊天。

## 功能特性

- **悬浮窗常驻**：Live2D 角色显示在其他应用上层，可自由拖拽并吸附屏幕边缘
- **高清 Live2D 角色**：内置两个官方免费示例模型
  - **Shizuku**（Cubism2 模型，经典看板娘，浅色系）
  - **Haru**（Cubism4 模型，高清 2048 纹理，深色系）
- **点击交互**：点击角色播放触碰动画并切换表情
- **情绪反馈**：高兴 / 害羞 / 难过 / 生气 四种表情
- **AI 聊天**：接入 DeepSeek API（OpenAI 兼容），支持流式回复
- **语音识别**：通过系统语音识别输入问题
- **TTS 朗读**：角色回复时朗读文本（语音转文字朗读）
- **开机自启**：重启后自动恢复悬浮宠物
- **角色切换**：设置页在 Shizuku / Haru 之间一键切换

## 安装

1. 下载 APK：`app/build/outputs/apk/debug/app-debug.apk`
2. 安装到 Android 8.0+ 设备（minSdk 26）
3. 首次启动授予「显示在其他应用上层」（悬浮窗）权限
4. （可选）如需语音功能，授予「麦克风」权限

## DeepSeek API 配置

在应用设置页填写：

- **API Key**：你的 DeepSeek API Key
- **Base URL**：`https://api.deepseek.com`（默认）
- **模型名**：`deepseek-chat`（默认）

密钥仅保存在本机应用内，不会上传到任何第三方。

## 使用说明

- **拖拽**：长按并拖动角色可移动位置，松手后吸附到屏幕边缘
- **点击**：点击角色触发触碰动画与表情反馈
- **聊天**：点击角色打开聊天页，输入文字或使用语音识别提问
- **收起**：点击悬浮窗顶部的「收起」按钮退出宠物

## 技术架构

```
┌────────────────────────────────────────────┐
│  MainActivity / ChatActivity (设置/聊天 UI) │
├────────────────────────────────────────────┤
│  PetService (悬浮窗服务)                    │
│   ├─ WebView + WebViewAssetLoader           │
│   │    └─ pet.html (PixiJS + Live2D 渲染)   │
│   ├─ 拖拽/吸附/点击交互                      │
│   └─ DeepSeekClient (AI 对话)               │
│   └─ PetTts (文字转语音朗读)                │
└────────────────────────────────────────────┘
```

### 渲染方案

- 使用 **WebView + PixiJS 5 + pixi-live2d-display** 渲染 Live2D 模型
- 资源打包在 `assets/live2d/` 内，离线可用，无需网络加载
- 通过 `WebViewAssetLoader`（`appassets.androidplatform.net` 虚拟域）加载本地资产，保证 fetch/XHR 正常工作
- 透明背景：PIXI `backgroundAlpha: 0` + WebView `setBackgroundColor(TRANSPARENT)`

### Live2D 资源

- `assets/live2d/libs/live2d.min.js`：Cubism2 core（Shizuku 模型必需）
- `assets/live2d/libs/live2dcubismcore.min.js`：Cubism4 core（Haru 模型必需）
- `assets/live2d/libs/pixi-bundle.js`：esbuild 打包的 PIXI + pixi-live2d-display（full 入口，含 Cubism2+4）
- `assets/live2d/models/shizuku/`：Shizuku 模型（moc2，含触碰音效）
- `assets/live2d/models/haru/`：Haru 模型（moc3，2048 高清纹理）

### 交互映射

| 功能 | 实现 |
|------|------|
| 拖拽 | PetService `GestureDetector`，更新悬浮窗位置 |
| 吸附 | 松手后平滑移动到最近的屏幕边缘 |
| 点击 | `pet.html` 捕获 pointerdown → 触发 `tapGroup` 触碰动画 |
| 表情 | `model.expression(表情名)`，按模型适配（shizuku/haru 表情名不同） |
| 说话嘴型 | 驱动 `ParamMouthOpenY` 参数开合 |
| AI 回复 | DeepSeekClient 流式请求，TTS 朗读，角色嘴型同步 |

## 构建

```bash
# 环境要求
# Android SDK（compileSdk 34）+ Gradle 8.4 + JDK 17

cd desktop-pet-android
ANDROID_HOME=/opt/android-sdk /opt/gradle/gradle-8.4/bin/gradle assembleDebug

# 产物
# app/build/outputs/apk/debug/app-debug.apk
```

## 模型来源声明

- **Shizuku / Haru**：Live2D 官方免费示例模型（Cubism 官网公开提供）
- **pixi-live2d-display**：MIT 开源库（GitHub: guansss/pixi-live2d-display）
- 本项目未自行生成任何形象资源

## 许可证

- 代码：MIT
- Live2D 模型资源遵循 Live2D 官方示例模型许可（仅允许用于学习与演示，不得商用）
