五子棋游戏 - 支持单机AI对战和联机对战

项目结构 (MVC架构)：
src/
├── assets/                 # 资源文件目录
│   └── main_menu_bg.jpg    # 主菜单背景图片
├── classes/                # 编译后的class文件
├── MainClass.java         # 程序入口
├── Board.java             # Model - 棋盘数据模型
├── BoardView.java         # View - 棋盘显示组件
├── BoardCanvas.java       # View - 棋盘绘制画布
├── GameController.java    # Controller - 人机对战控制器
├── OnlineGameController.java # Controller - 联机对战控制器
├── GameView.java          # View - 人机对战界面
├── OnlineGameView.java    # View - 联机对战界面
├── MainView.java          # View - 主菜单界面 (带背景图片)
├── Gomuku.jar            # 可执行JAR文件
├── build.bat             # 构建脚本
├── clean.bat             # 清理脚本
├── run_game.bat          # 运行脚本
└── *.md                  # 说明文档

界面特性：
- 主菜单：精美背景图片，提升视觉体验
- 人机对战：简洁界面，专注对战功能
- 联机对战：清晰界面，享受多人对战
- 背景图片支持：主菜单自动降级到渐变背景 (如果图片加载失败)

运行方式：
java -Dfile.encoding=UTF-8 -jar Gomuku.jar

要求：Java 8 或更高版本

构建项目：
build.bat          # 编译并打包JAR
clean.bat          # 清理编译文件
run_game.bat       # 运行游戏

界面特性：
- 美观的渐变背景主菜单
- 支持自定义背景图片 (assets/main_menu_bg.jpg)
- 现代化按钮设计和悬停效果

功能特性：
- 人机对战（AI难度可调，先手可选择）
- 联机双人对战（TCP网络，先手可选择）
- 背景音乐播放
- 悔棋功能
- 游戏复盘

背景音乐：
- 默认文件：assets/bgm.wav
- 支持格式：WAV/AIFF/AU
- MP3需要额外库支持

联机对战：
- 主菜单点击"联机对战"
- 一方：创建房间，选择端口（默认9999），⚫先手选择（默认对方先手）
- 另一方：加入房间，输入主机地址（默认localhost）和端口（默认9999）
- 连接后点击棋盘下棋，落子通过TCP同步
- ⏰ 每回合2分钟倒计时，超时自动随机下棋
- 协议：START BLACK/WHITE / MOVE x y / TIMEOUT_MOVE x y / CHAT base64 / NEW / UNDO_REQ/UNDO_OK/UNDO_NO

网络连接：
- ✅ 局域网连接（同一个WiFi网络）
- ✅ 互联网连接（需要端口转发配置）
- 📖 详细网络配置请参考 NETWORK_SETUP_GUIDE.md

多实例运行：
- IDEA中可同时运行多个游戏实例
- 所有实例使用相同默认端口9999
- 多实例测试时需要手动修改端口
