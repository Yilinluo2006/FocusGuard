# Week 01 开发记录

时间：2026-05-26 ~ 2026-05-28  
项目：FocusGuard

## 本周目标

完成 FocusGuard 项目的初始化，并实现应用列表展示与选择功能，为后续应用使用时间限制功能打基础。

## 已完成内容

### 1. 项目初始化

- 使用 Android Studio 创建 Java Android 项目
- 配置 Git 本地仓库
- 创建 GitHub 远程仓库
- 完成首次提交和远程推送
- 编写 README 项目说明

### 2. 首页开发

- 创建 FocusGuard 首页布局
- 添加今日使用时长、受限应用数量、剩余时间等占位信息
- 添加“管理应用限制”按钮
- 实现首页跳转到应用管理页面

### 3. 应用管理页面

- 新建 AppManageActivity
- 新建 activity_app_manage.xml
- 在 AndroidManifest.xml 中注册新页面
- 实现页面跳转和基础按钮交互

### 4. 应用列表展示

- 使用 RecyclerView 展示应用列表
- 创建 AppInfo 数据类
- 创建 AppListAdapter 适配器
- 创建 item_app.xml 列表项布局
- 通过 PackageManager 读取手机中已安装的可启动应用
- 使用 Manifest queries 适配 Android 11 之后的应用可见性限制
- 展示应用图标、应用名称和包名

### 5. 应用选择功能

- 为 AppInfo 增加 selected 状态
- 使用 Switch 表示应用是否被选择
- 点击列表项或 Switch 可以切换选择状态
- 顶部显示当前已选择应用数量
- 自定义 Switch 选中颜色为蓝色

## 本周学习到的知识点

- Android XML 布局基础
- TextView、Button、ImageView、Switch 的使用
- LinearLayout 横向和纵向排列
- Activity 页面跳转
- Intent 的基本使用
- AndroidManifest.xml 的作用
- RecyclerView、Adapter、ViewHolder 的基本关系
- PackageManager 读取应用信息
- Android 11 queries 应用可见性声明
- Drawable 图标数据
- Java interface 回调机制
- Git 基础操作和 GitHub 项目维护流程

## 遇到的问题

### 1. Git 初始化目录错误

一开始误在用户主目录执行了 `git init`，导致 Git 扫描整个用户文件夹。  
解决方式：删除用户目录下错误创建的 `.git`，并在 `/e/FocusGuard` 项目目录重新执行 Git 操作。

### 2. Activity 闪退

AppManageActivity 创建后没有继承 AppCompatActivity，导致点击跳转时闪退。  
解决方式：让 AppManageActivity 继承 AppCompatActivity，并实现 onCreate 方法。

### 3. 只能读取到 FocusGuard 自己

Android 11 之后限制应用查询权限，导致一开始只能看到本应用。  
解决方式：在 AndroidManifest.xml 中添加 queries 声明。

### 4. CheckBox 颜色显示异常

CheckBox 修改颜色后勾选图标看不清。  
解决方式：改用 Switch，并通过颜色选择器设置开关轨道颜色。

## 当前项目状态

FocusGuard 目前已经可以：

- 显示首页
- 跳转到应用管理页面
- 读取手机真实安装应用
- 显示应用图标、名称和包名
- 选择多个应用
- 实时显示已选择应用数量

## 下一步计划

- 保存用户选择的应用
- 为选中的应用设置每日限制时间
- 使用本地数据库保存限制规则
- 读取应用使用时长
- 实现超时提醒或限制页面