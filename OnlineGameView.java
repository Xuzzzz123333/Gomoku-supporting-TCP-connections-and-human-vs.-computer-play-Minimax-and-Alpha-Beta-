import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * 联机对战窗口（基于简单 TCP）。
 * 布局类似于 GameView：
 * - 中间：棋盘
 * - 右侧：连接控制 + 按钮 + 音乐 + 聊天
 * - 底部：状态栏
 */
public class OnlineGameView extends JFrame implements OnlineGameController.UIHook {

	private final Board board;
	private final BoardView boardView;
	private final OnlineGameController game;

	private final BgmPlayer bgm = new BgmPlayer();
	private boolean bgmEnabled = true;

	private final JLabel statusLabel = new JLabel("状态：未连接（请创建/加入房间）");
	private final JTextArea chatHistory = new JTextArea();
	private final JTextArea chatInput = new JTextArea(3, 20);

	// 连接 UI 控件
	private final JTextField tfHost = new JTextField("localhost");
	private final JTextField tfPort = new JTextField("9999");
	private final JLabel connLabel = new JLabel("连接状态：未连接");
	private final JButton btnHost = new JButton("创建房间");
	private final JButton btnJoin = new JButton("加入房间");
	private final JButton btnDisconnect = new JButton("断开");

	// 先手选择
	private final JComboBox<String> cbFirstMove = new JComboBox<>(new String[]{"我方先手(黑)", "对方先手(白)"});
	private final JLabel lblFirstMove = new JLabel("⚫ 先手选择：");

	{
		// 设置默认选择为"对方先手(白)"，通常服务器会让出黑棋
		cbFirstMove.setSelectedIndex(1);
	}

	// 游戏控制按钮
	private final JButton btnPrev = new JButton("复盘  上一步");
	private final JButton btnNext = new JButton("复盘  下一步");

	private TcpPeer peer;
	private ServerSocket serverSocket; // 仅在主机模式下使用

	public OnlineGameView() {
		super("五子棋 - 联机对战（TCP）");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout()); // 确保 JFrame 使用 BorderLayout
		setMinimumSize(new Dimension(1300, 950));
		setLocationRelativeTo(null);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				disconnectInternal(true);
				bgm.close();
			}
		});

		int boardPixels = 900;
		int boardSize = 15;
		board = new Board(boardSize); // 创建不依赖 GUI 的 Model
		boardView = new BoardView(boardPixels, boardSize); // 创建 View
		board.addObserver(boardView); // 将 Model 与 View 关联

		game = new OnlineGameController(board);
		game.setUIHook(this);

		// 通过 View 转发鼠标事件处理
		boardView.attachListener(new java.awt.event.MouseListener() {
			public void mouseClicked(java.awt.event.MouseEvent e) {
				game.onBoardClicked(e);
			}
			public void mouseEntered(java.awt.event.MouseEvent e) {}
			public void mouseExited(java.awt.event.MouseEvent e) {}
			public void mousePressed(java.awt.event.MouseEvent e) {}
			public void mouseReleased(java.awt.event.MouseEvent e) {}
		});
		game.setNetHook(line -> {
			TcpPeer p = peer;
			if (p != null) p.sendLine(line);
		});

		// 创建主面板
		JPanel mainPanel = new JPanel(new BorderLayout());

		mainPanel.add(boardView.getGUI(), BorderLayout.CENTER);
		mainPanel.add(buildRightPanel(), BorderLayout.EAST);
		mainPanel.add(buildStatusBar(), BorderLayout.SOUTH);

		add(mainPanel);

		pack();
		setVisible(true);

		game.start();

		// 背景音乐加载
		if (bgm.load(null)) {
			bgm.setVolume01(0.6f);
			if (bgmEnabled) bgm.playLoop();
		} else {
			appendSystemMessage("系统：未找到背景音乐文件（支持WAV）：assets/bgm.wav");
		}
	}

	private JPanel buildStatusBar() {
		JPanel p = new JPanel(new BorderLayout());
		p.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		p.add(statusLabel, BorderLayout.WEST);
		return p;
	}

	private JPanel buildRightPanel() {
		JPanel right = new JPanel(new BorderLayout());
		right.setPreferredSize(new Dimension(330, 10));
		right.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		// ---- 顶部控制区 ----
		JPanel top = new JPanel(new BorderLayout());

		// 连接配置区
		JPanel connBox = new JPanel(new GridLayout(5, 1, 0, 6));
		connBox.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		JPanel row1 = new JPanel(new BorderLayout(6, 0));
		row1.add(new JLabel("主机："), BorderLayout.WEST);
		row1.add(tfHost, BorderLayout.CENTER);

		JPanel row2 = new JPanel(new BorderLayout(6, 0));
		row2.add(new JLabel("端口："), BorderLayout.WEST);
		row2.add(tfPort, BorderLayout.CENTER);

		JPanel row3 = new JPanel(new BorderLayout(6, 0));
		row3.add(lblFirstMove, BorderLayout.WEST);
		row3.add(cbFirstMove, BorderLayout.CENTER);

		JPanel row4 = new JPanel(new GridLayout(1, 3, 6, 0));
		row4.add(btnHost);
		row4.add(btnJoin);
		row4.add(btnDisconnect);
		btnDisconnect.setEnabled(false);

		connBox.add(connLabel);
		connBox.add(row1);
		connBox.add(row2);
		connBox.add(row3);
		connBox.add(row4);

		btnHost.addActionListener(e -> onHost());
		btnJoin.addActionListener(e -> onJoin());
		btnDisconnect.addActionListener(e -> disconnectInternal(false));

		JButton btnNew = new JButton("新开局");
		JButton btnUndo = new JButton("悔棋");

		btnNew.addActionListener(e -> game.newGame());
		btnUndo.addActionListener(e -> {
			boolean ok = game.requestUndo();
			if (!ok) appendSystemMessage("系统：只能在自己刚落子后、对方未落子前悔棋（且未处于复盘/结束/等待确认状态）");
		});

		btnPrev.addActionListener(e -> {
			boolean ok = game.reviewPrev();
			if (!ok) appendSystemMessage("系统：无法继续向前复盘（仅支持对局结束后复盘）");
		});
		btnNext.addActionListener(e -> {
			boolean ok = game.reviewNext();
			if (!ok) appendSystemMessage("系统：无法继续向后复盘（仅支持对局结束后复盘）");
		});

		JButton btnDraw = new JButton("求和");
		JButton btnResign = new JButton("认输");

		btnDraw.addActionListener(e -> {
			boolean ok = game.requestDraw();
			if (!ok) appendSystemMessage("系统：无法发起求和（未连接/游戏已结束/正在等待确认）");
		});
		btnResign.addActionListener(e -> {
			int confirm = javax.swing.JOptionPane.showConfirmDialog(
				OnlineGameView.this,
				"确定要认输吗？对方将直接获胜。",
				"认输确认",
				javax.swing.JOptionPane.YES_NO_OPTION
			);
			if (confirm == javax.swing.JOptionPane.YES_OPTION) {
				boolean ok = game.resign();
				if (!ok) appendSystemMessage("系统：无法认输（未连接/游戏已结束）");
			}
		});

		JPanel btnPanel = new JPanel(new GridLayout(6, 1, 0, 6));
		btnPanel.add(btnNew);
		btnPanel.add(btnUndo);
		btnPanel.add(btnDraw);
		btnPanel.add(btnResign);
		btnPanel.add(btnPrev);
		btnPanel.add(btnNext);

		// 音乐控制区
		JPanel musicPanel = new JPanel(new BorderLayout(6, 6));
		musicPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

		JCheckBox chkBgm = new JCheckBox("背景音乐", bgmEnabled);
		JButton btnBgmToggle = new JButton("暂停");
		btnBgmToggle.setEnabled(bgm.isLoaded());

		JSlider vol = new JSlider(0, 100, 60);
		vol.setEnabled(bgm.isLoaded());

		JButton btnChooseBgm = new JButton("选择...");

		JPanel left = new JPanel(new GridLayout(1, 2, 6, 0));
		left.add(chkBgm);
		left.add(btnBgmToggle);
		musicPanel.add(left, BorderLayout.NORTH);
		musicPanel.add(vol, BorderLayout.CENTER);
		musicPanel.add(btnChooseBgm, BorderLayout.SOUTH);

		chkBgm.addActionListener(ev -> {
			bgmEnabled = chkBgm.isSelected();
			if (!bgm.isLoaded()) return;
			if (bgmEnabled) {
				if (bgm.isPaused()) bgm.resumeLoop();
				else bgm.playLoop();
				btnBgmToggle.setText("暂停");
			} else {
				bgm.pause();
				btnBgmToggle.setText("播放");
			}
		});

		btnBgmToggle.addActionListener(ev -> {
			if (!bgm.isLoaded()) return;
			if (!bgmEnabled) {
				bgmEnabled = true;
				chkBgm.setSelected(true);
			}
			if (bgm.isPaused()) {
				bgm.resumeLoop();
				btnBgmToggle.setText("暂停");
			} else {
				bgm.pause();
				btnBgmToggle.setText("播放");
			}
		});

		vol.addChangeListener(ev -> {
			float v = vol.getValue() / 100f;
			bgm.setVolume01(v);
		});

		btnChooseBgm.addActionListener(ev -> {
			javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
			fc.setDialogTitle("选择背景音乐（WAV）");
			int r = fc.showOpenDialog(OnlineGameView.this);
			if (r == javax.swing.JFileChooser.APPROVE_OPTION) {
				String p = fc.getSelectedFile().getAbsolutePath();
				boolean ok = bgm.load(p);
				if (ok) {
					bgm.setVolume01(vol.getValue() / 100f);
					if (bgmEnabled) bgm.playLoop();
					btnBgmToggle.setEnabled(true);
					vol.setEnabled(true);
					appendSystemMessage("系统：已加载背景音乐：" + fc.getSelectedFile().getName());
				} else {
					appendSystemMessage("系统：加载失败：请使用WAV格式");
				}
			}
		});

		top.add(connBox, BorderLayout.NORTH);
		top.add(btnPanel, BorderLayout.CENTER);
		top.add(musicPanel, BorderLayout.SOUTH);
		right.add(top, BorderLayout.NORTH);

		//  聊天/消息面板
		JPanel chatPanel = new JPanel(new BorderLayout(0, 6));
		chatPanel.setBorder(BorderFactory.createTitledBorder("消息/聊天"));

		chatHistory.setEditable(false);
		chatHistory.setLineWrap(true);
		JScrollPane historyScroll = new JScrollPane(chatHistory);
		historyScroll.setPreferredSize(new Dimension(280, 320));
		chatPanel.add(historyScroll, BorderLayout.CENTER);

		JPanel inputRow = new JPanel(new BorderLayout(8, 0));
		JScrollPane inputScroll = new JScrollPane(chatInput);
		inputScroll.setPreferredSize(new Dimension(10, 120));

		JButton btnSend = new JButton("发送");
		btnSend.setPreferredSize(new Dimension(90, 10));

		Runnable doSend = () -> {
			String text = chatInput.getText().trim();
			if (text.isEmpty()) return;
			appendLine("我：" + text);
			chatInput.setText("");

			if (!game.isConnected() || peer == null) {
				appendLine("系统：未连接，对方收不到消息。");
				return;
			}

			String payload = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
			peer.sendLine("CHAT " + payload);
		};

		btnSend.addActionListener(e -> doSend.run());

		// 快捷键设置：Enter 发送，Shift+Enter 换行
		chatInput.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					if (e.isShiftDown()) {
						chatInput.append("\n");
					} else {
						e.consume();
						doSend.run();
					}
				}
			}
		});

		inputRow.add(inputScroll, BorderLayout.CENTER);
		inputRow.add(btnSend, BorderLayout.EAST);

		chatPanel.add(inputRow, BorderLayout.SOUTH);

		right.add(chatPanel, BorderLayout.CENTER);
		return right;
	}

	private void onHost() {
		int port = parsePort();
		if (port <= 0) return;

		// 显示先手选择确认
		int selectedIndex = cbFirstMove.getSelectedIndex();
		String firstMoveChoice = selectedIndex == 0 ? "你先手(黑棋)" : "对方先手(黑棋)";
		int confirm = JOptionPane.showConfirmDialog(
			this,
			"创建房间设置：\n" +
			"端口：" + port + "\n" +
			"先手：" + firstMoveChoice + "\n\n" +
			"确认创建房间吗？",
			"创建房间确认",
			JOptionPane.YES_NO_OPTION
		);

		if (confirm != JOptionPane.YES_OPTION) {
			return; // 用户取消
		}

		btnHost.setEnabled(false);
		btnJoin.setEnabled(false);
		btnDisconnect.setEnabled(true);
		tfHost.setEnabled(false);
		tfPort.setEnabled(false);
		cbFirstMove.setEnabled(false);

		setConnText("连接状态：作为服务器等待连接...");

		new Thread(() -> {
			try {
				serverSocket = new ServerSocket(port);
				Socket s = serverSocket.accept();
				setupPeer(s, true);
			} catch (IOException ex) {
				SwingUtilities.invokeLater(() -> {
					appendSystemMessage("系统：创建房间失败：" + ex.getMessage());
					resetConnButtons();
				});
			}
		}, "accept-thread").start();
	}

	private void onJoin() {
		int p0 = parsePort();
		if (p0 <= 0) return;
		String h0 = tfHost.getText().trim();
		if (h0.isEmpty()) h0 = "localhost";

		final int port = p0;
		final String host = h0;

		btnHost.setEnabled(false);
		btnJoin.setEnabled(false);
		btnDisconnect.setEnabled(true);
		tfHost.setEnabled(false);
		tfPort.setEnabled(false);

		setConnText("连接状态：正在连接到 " + host + ":" + port + "...");

		new Thread(() -> {
			try {
				Socket s = new Socket(host, port);
				setupPeer(s, false);
			} catch (IOException ex) {
				SwingUtilities.invokeLater(() -> {
					String errorMsg = ex.getMessage();
					appendSystemMessage("系统：连接失败：" + errorMsg);

					// 提供详细错误指导
					if ("127.0.0.1".equals(host) || "localhost".equals(host)) {
						appendSystemMessage("系统：请确保另一个游戏实例正在运行并已点击'创建房间'");
						appendSystemMessage("系统：如果端口不同，请手动修改端口号匹配服务器端口");
					}

					resetConnButtons();
				});
			}
		}, "connect-thread").start();
	}

	private int parsePort() {
		try {
			int port = Integer.parseInt(tfPort.getText().trim());
			if (port < 1 || port > 65535) throw new NumberFormatException();
			return port;
		} catch (NumberFormatException ex) {
			JOptionPane.showMessageDialog(this, "端口号必须是 1-65535 的整数", "端口错误", JOptionPane.ERROR_MESSAGE);
			return -1;
		}
	}

	private void setupPeer(Socket socket, boolean isHost) {
		try {
			TcpPeer p = new TcpPeer(socket);
			this.peer = p;

			SwingUtilities.invokeLater(() -> {
				if (isHost) {
					// 服务器端：读取设置
					int selectedIndex = cbFirstMove.getSelectedIndex();
					boolean serverWantsBlack = (selectedIndex == 0); // 0=我方先手(黑), 1=对方先手(白)
					cbFirstMove.setEnabled(false); 

					game.setServerWantsBlack(serverWantsBlack);
					game.setMyBlack(true); // 服务器
					game.setConnected(true);
					setConnText("连接状态：已连接（" + (serverWantsBlack ? "黑棋" : "白棋") + "）");
					appendSystemMessage("系统：连接成功！你是" + (serverWantsBlack ? "黑棋(先手)" : "白棋(后手)") + "。");

					// 发送 START 信息
					p.sendLine("START " + (serverWantsBlack ? "BLACK" : "WHITE"));
				} else {
					// 客户端端：等待 START
					setConnText("连接状态：已连接，等待游戏开始...");
					appendSystemMessage("系统：连接成功！等待对方选择先手...");
					cbFirstMove.setEnabled(false); 
				}
			});

			p.startReader(new TcpPeer.Listener() {
				@Override
				public void onLine(String line) {
					handleLine(line);
				}

				@Override
				public void onClosed(String reason) {
					SwingUtilities.invokeLater(() -> {
						appendSystemMessage("系统：" + reason);
						disconnectInternal(false);
					});
				}
			});
		} catch (IOException ex) {
			SwingUtilities.invokeLater(() -> {
				appendSystemMessage("系统：初始化连接失败：" + ex.getMessage());
				resetConnButtons();
			});
		}
	}

//处理对方发来的消息
	private void handleLine(String line) {
		if (line == null) return;
		line = line.trim();
		if (line.isEmpty()) return;

		try {
			if (line.startsWith("MOVE ")) {
				String[] sp = line.split("\\s+");
				if (sp.length >= 3) {
					int x = Integer.parseInt(sp[1]);
					int y = Integer.parseInt(sp[2]);
					game.applyRemoteMove(x, y);
				}
				return;
			}

			if (line.startsWith("TIMEOUT_MOVE ")) {
				String[] sp = line.split("\\s+");
				if (sp.length >= 3) {
					int x = Integer.parseInt(sp[1]);
					int y = Integer.parseInt(sp[2]);
					game.applyTimeoutMove(x, y);
				}
				return;
			}

			if (line.startsWith("CHAT ")) {
				String payload = line.substring(5).trim();
				String msg;
				try {
					byte[] data = Base64.getDecoder().decode(payload);
					msg = new String(data, StandardCharsets.UTF_8);
				} catch (IllegalArgumentException ex) {
					msg = "[无法解析的消息]";
				}
				String finalMsg = msg;
				SwingUtilities.invokeLater(() -> appendLine("对方：" + finalMsg));
				return;
			}

			if (line.startsWith("START ")) {
				String[] sp = line.split("\\s+");
				if (sp.length >= 2) {
					boolean serverWantsBlack = "BLACK".equals(sp[1]);
					game.setServerWantsBlack(serverWantsBlack);
					game.setMyBlack(false); // 客户端
					game.setConnected(true);
					setConnText("连接状态：已连接（" + (!serverWantsBlack ? "黑棋" : "白棋") + "）");
					appendSystemMessage("系统：游戏开始！" + (serverWantsBlack ? "对方先手(黑)" : "我方先手(黑)"));

					if (!serverWantsBlack) { // 如果客户端是黑棋（先手）
						game.restartGameForStart();
					}
				}
				return;
			}

			if (line.equals("NEW")) {
				game.newGameFromPeer();
				return;
			}
			if (line.equals("UNDO_REQ")) {
				game.onUndoRequestFromPeer();
				SwingUtilities.invokeLater(() -> {
					boolean can = game.canAcceptUndoRequestNow();
					if (!can) {
						game.respondUndoFromLocalDecision(false);
						return;
					}
					int r = JOptionPane.showConfirmDialog(
							OnlineGameView.this,
							"对方请求悔棋（撤销其刚刚下的一步）。\n同意后将轮到对方重新落子，是否同意？",
							"悔棋请求",
							JOptionPane.YES_NO_OPTION
					);
					game.respondUndoFromLocalDecision(r == JOptionPane.YES_OPTION);
				});
				return;
			}

			if (line.equals("UNDO_OK")) {
				game.onUndoAcceptedByPeer();
				return;
			}

			if (line.equals("UNDO_NO")) {
				game.onUndoRejectedByPeer();
				return;
			}

			if (line.equals("DRAW_REQ")) {
				game.onDrawRequestFromPeer();
				SwingUtilities.invokeLater(() -> {
					int r = JOptionPane.showConfirmDialog(
							OnlineGameView.this,
							"对方请求求和（平局结束）。\n是否同意？",
							"求和请求",
							JOptionPane.YES_NO_OPTION
					);
					game.respondDrawFromLocalDecision(r == JOptionPane.YES_OPTION);
				});
				return;
			}

			if (line.equals("DRAW_OK")) {
				game.onDrawAcceptedByPeer();
				return;
			}

			if (line.equals("DRAW_NO")) {
				game.onDrawRejectedByPeer();
				return;
			}

			if (line.equals("RESIGN")) {
				game.onResignFromPeer();
				return;
			}


			{
				String unknown = line;
				SwingUtilities.invokeLater(() -> appendSystemMessage("系统：收到未知消息：" + unknown));
			}
		} catch (Exception ex) {
			SwingUtilities.invokeLater(() -> appendSystemMessage("系统：处理消息失败：" + ex.getMessage()));
		}
	}

	private void disconnectInternal(boolean silent) {
		TcpPeer p = peer;
		peer = null;
		if (p != null) p.close();

		if (serverSocket != null) {
			try { serverSocket.close(); } catch (IOException ignored) {}
			serverSocket = null;
		}

		game.setConnected(false);
		resetConnButtons();

		if (!silent) {
			setConnText("连接状态：未连接");
		}
	}

	private void resetConnButtons() {
		btnHost.setEnabled(true);
		btnJoin.setEnabled(true);
		btnDisconnect.setEnabled(false);
		tfHost.setEnabled(true);
		tfPort.setEnabled(true);
		cbFirstMove.setEnabled(true);

		setConnText("连接状态：未连接");
	}

	private void setConnText(String text) {
		SwingUtilities.invokeLater(() -> connLabel.setText(text));
	}

	@Override
	public void setStatusText(String text) {
		SwingUtilities.invokeLater(() -> statusLabel.setText(text));
	}

	@Override
	public void appendSystemMessage(String text) {
		SwingUtilities.invokeLater(() -> {
			appendLine(text);
		});
	}

	private void appendLine(String line) {
		chatHistory.append(line + "\n");
		chatHistory.setCaretPosition(chatHistory.getDocument().getLength());
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(OnlineGameView::new);
	}
}