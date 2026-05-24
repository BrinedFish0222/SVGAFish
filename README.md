# SVGA-Fish

SVGA-Fish 是一个面向 Android 的 SVGA 动画播放库，在 [yyued/SVGAPlayer-Android](https://github.com/yyued/SVGAPlayer-Android) 的基础上做了一系列优化，旨在降低内存占用。

## 相比原始项目的改进

原始项目使用 `SVGAParser` + `SVGAVideoEntity` + `SVGAImageView` 的组合。SVGA-Fish 将加载、缓存和播放拆分为职责更清晰的组件：

| 原始项目 | SVGA-Fish | 说明 |
|---------|-----------|------|
| `SVGAParser` | `SVGAResourceManager` | 统一入口：加载、缓存、创建会话 |
| `SVGAVideoEntity` | `SVGAVideoModel` + `SVGAVideoSession` | 模型与运行时分家：`Model` 是不可变的共享数据，`Session` 持有单次播放的运行时状态 |
| `SVGAImageView` | `SVGAPlayerView` | 播放视图 |
| `SVGACache`（黑盒单例） | `SVGAResourceStore`（接口） | `SVGAResourceStore` 是内存缓存的抽象接口，支持替换自定义实现。默认 `MemorySVGAResourceStore` 基于 `LinkedHashMap` 的线程安全 LRU，配合引用计数保护活跃条目、空闲 TTL 自动回收 |
| 无 | `SVGATaskScheduler` | 可插拔的任务调度接口：将 SVGA 文件的下载与解码任务提交到后台执行。默认实现 `ConcurrentTaskScheduler` 基于固定线程池，每个任务持有独立的 `CancellationSignal`，可在排队或运行期间取消，支持并发度配置 |
| 无 | `RequestCoordinator` | 相同 URL 的并发请求自动合并为单次下载 |

### 实际内存数据

以下数据由 Android Studio Memory Profiler（Analyze Memory Usage）采集，测试环境见 [demo 示例](app/src/main/java/com/svga/fish/)：

- **重复请求（3 播放器）**：`OldApiDeduplicatedNetworkLoadsActivity` vs `DeduplicatedNetworkLoadsActivity`，3 路并发请求同一 URL，验证请求去重。
- **批量加载（40 播放器）**：`OldApiGridSvgaDemoActivity` vs `GridSvgaDemoActivity`，10 种文件各 4 个，验证大量对象场景。

#### Native 内存

| 场景 | 原始库 | SVGA-Fish | 降低比例 |
|------|:-----:|:---------:|:--------:|
| 重复请求（3 播放器） | 约 8.23 MB | 约 2.93 MB | 约 64.5% |
| 批量加载（40 播放器） | 约 118.9 MB | 约 38.8 MB | 约 67.4% |

#### Shallow 内存（Java 对象开销）

| 场景 | 原始库 | SVGA-Fish | 降低比例 |
|------|:-----:|:---------:|:--------:|
| 重复请求（3 播放器） | 约 6.85 MB | 约 3.27 MB | 约 52.2% |
| 批量加载（40 播放器） | 约 212.3 MB | 约 41.9 MB | 约 80.2% |

> **Native 内存**反映位图等原生资源占用，SVGA-Fish 通过 LRU 缓存回收、请求去重减少重复解码来降低。**Shallow 内存**反映 Java 对象开销，SVGA-Fish 通过 `SVGAVideoModel` 共享（同一 URL 全局唯一）和 Session 分离架构大幅减少对象创建，40 个 SVGA 场景下 Shallow 降低约 80.2%。

## 使用教程

### 添加依赖

在根 `settings.gradle.kts` 中添加 JitPack 仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

在模块的 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    implementation("com.github.BrinedFish0222:SVGAFish:Tag")
}
```

### 快速开始

**1. 在布局中添加 SVGA 播放视图**

```xml
<com.svgafish.library.view.SVGAPlayerView
    android:id="@+id/svgaPlayer"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:loopCount="1"
    app:fillMode="Forward"
    app:antiAlias="true" />
```

**2. 加载并播放动画**

```kotlin
// 创建 SVGAResourceManager
val manager = SVGAResourceManager.create(context)

// 从 URL 加载动画（自动缓存）
manager.loadFromURL(
    url = URL("https://example.com/animation.svga"),
    callback = object : SVGAResourceManager.LoadCompletion {
        override fun onComplete(session: SVGAVideoSession) {
            // 绑定到视图并播放
            binding.svgaPlayer.setVideoSession(session)
            binding.svgaPlayer.startAnimation()
        }

        override fun onError() {
            // 处理加载失败
        }
    }
)
```

**3. 配置循环次数**

```kotlin
// 设置循环次数（0 = 无限循环）
binding.svgaPlayer.loops = 0
```

**4. 管理资源生命周期**

```kotlin
// 停止动画
binding.svgaPlayer.stopAnimation()

// 停止并清除视图
binding.svgaPlayer.clear()

// 清空空闲缓存（例如在 onTrimMemory 中调用）
manager.clearResources()
```

## 许可证

本项目继承原始库的许可证。详情请参见原始项目 [yyued/SVGAPlayer-Android](https://github.com/yyued/SVGAPlayer-Android)。
