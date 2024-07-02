# wix-package
compose desktop 默认的打包插件有一些问题：
- 不能生成卸载快捷方式，
- 不能设置单独快捷方式的名称，有一些 windows 用户的安装目录不能有中文字符，但是快捷方式的名称又必须是中文的。
- 比如卸载软件的时候可能会误删文件，如果操作失误把软件安装到了 `D:\Program Files` 而不是 `C:\Program Files\productName` 卸载的时候会把 `D:\Program Files` 下的所有文件都删除掉。

compose desktop 的打包插件底层依赖 jpackage, jpackage 在 windows 系统底层依赖 wix，wix 是一个功能强大的打包工具，可以解决上述问题。
compose desktop 插件在执行 `createDistributable` 任务时会自动下载 wix 的安装包到 `build\wix311` 目录下。

这个脚本的打包思路是：
1. 先使用 compose desktop 的 `createDistributable` 任务生成 app-image
2. 然后使用 wix 的 heat 命令收集 app-image 文件夹里的文件，生成一个 wxs 文件
3. 编辑 wxs 文件,填充一些产品信息，设置快捷方式
4. 编译 wxs 文件,生成 wixobj 文件
5. 链接 wixobj文件 生成 msi 安装包


现在还只是一个脚本，后续可能会封装成一个插件。
## 使用方法
1. 把 `wix.gradle.kts` 复制到项目根目录
2. 在 `build.gradle.kts` 添加
    ```kotlin
      apply(from = "wix.gradle.kts")
    ```
3. 设置 `wix.gradle.kts` 中的 manufacturer、shortcutName、licenseFile 和 iconFile 等参数

4. 执行 `lightWixobj` 任务，就可以生成 msi 安装包了。
5. 这段脚本有 4 个任务，分别是 `harvest`、`editWxs`、`compileWxs`、`lightWixobj`，可以单独执行，方便调试。

## Task 说明
- `harvest` 依赖 `createDistributable` Task 创建的 app-image 文件夹,然后使用 Wix 的 heat 命令收集 app-image 文件夹里的文件，生成一个 wxs 文件
- `editWxs` 编辑 wxs 文件,填充一些产品信息，设置快捷键
- `compileWxs` 编译 wxs 文件,生成 wixobj 文件
- `lightWixobj` 链接 wixobj文件 生成 msi 安装包
