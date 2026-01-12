import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.*;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JSlider;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * 人机对战窗口布局：
 * - 左侧：棋盘
 * - 右侧：按钮控制区 + AI 难度设置 + 聊天/系统消息
 * - 底部：状态栏
 */
public class GameView extends JFrame implements GameController.UIHook {

	private final Board board;
	private final BoardView boardView;
	private final GameController game;

	private final BgmPlayer bgm = new BgmPlayer();
	private boolean bgmEnabled = true;

	private final JLabel statusLabel = new JLabel("状态：轮到：黑棋(玩家)");
	private final JTextArea chatHistory = new JTextArea();
	private final JTextArea chatInput = new JTextArea(4, 20);

	private final JButton btnPrev = new JButton("复盘  上一步");
	private final JButton btnNext = new JButton("复盘  下一步");

	public GameView() {
		super("五子棋 - 人机对战");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout()); // 确保 JFrame 使用 BorderLayout 布局
		setMinimumSize(new Dimension(1300, 950));
		setLocationRelativeTo(null);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				bgm.close();
			}
		});

		int boardPixels = 900;
		int boardSize = 15;
		board = new Board(boardSize); // 创建不依赖 GUI 的 Model
		boardView = new BoardView(boardPixels, boardSize); // 创建 View
		board.addObserver(boardView); // 将 Model 与 View 关联

		game = new GameController(board);
		game.setUIHook(this);
		game.setAIStarts(false);
		game.setAIDepth(3);

		// 通过 View 转发鼠标事件处理
		boardView.attachListener(new MouseListener() {
			public void mouseClicked(MouseEvent e) {
				game.onBoardClicked(e);
			}
			public void mouseEntered(MouseEvent e) {}
			public void mouseExited(MouseEvent e) {}
			public void mousePressed(MouseEvent e) {}
			public void mouseReleased(MouseEvent e) {}
		});

		// 创建使用 BorderLayout 的主面板
		JPanel mainPanel = new JPanel(new BorderLayout());

		mainPanel.add(boardView.getGUI(), BorderLayout.CENTER);
		mainPanel.add(buildRightPanel(), BorderLayout.EAST);
		mainPanel.add(buildStatusBar(), BorderLayout.SOUTH);

		add(mainPanel);

		pack();
		setVisible(true);

		game.start();
		// 加载并播放背景音乐
		if (bgm.load(null)) {
			bgm.setVolume01(0.6f);
			if (bgmEnabled) bgm.playLoop();
		} else {
			appendSystemMessage("未找到背景音乐文件（支持WAV）：assets/bgm.wav");
		}

		appendSystemMessage("开始新游戏！");
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

		// 顶部控制按钮区
		JPanel top = new JPanel(new BorderLayout());

		JPanel aiRow = new JPanel(new GridLayout(2, 1, 0, 6));
		aiRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		// AI 难度设置
		JPanel difficultyRow = new JPanel(new BorderLayout(6, 0));
		difficultyRow.add(new JLabel("AI难度："), BorderLayout.WEST);

		String[] levels = {
				"简单（深度1）",
				"普通（深度3）",
				"困难（深度4）",
				"地狱（深度5）"
		};
		JComboBox<String> cbDifficulty = new JComboBox<>(levels);
		cbDifficulty.setSelectedIndex(1);
		cbDifficulty.addActionListener(e -> {
			int idx = cbDifficulty.getSelectedIndex();
			int depth = switch (idx) {
				case 0 -> 1;
				case 1 -> 3;
				case 2 -> 4;
				default -> 5;
			};
			game.setAIDepth(depth);
			appendSystemMessage("AI难度已设置为：" + levels[idx]);
		});
		difficultyRow.add(cbDifficulty, BorderLayout.CENTER);

		// 先手设置
		JPanel firstMoveRow = new JPanel(new BorderLayout(6, 0));
		firstMoveRow.add(new JLabel("先手："), BorderLayout.WEST);

		String[] firstMoveOptions = {"我方先手(黑)", "AI先手(白)"};
		JComboBox<String> cbFirstMove = new JComboBox<>(firstMoveOptions);
		cbFirstMove.setSelectedIndex(0); // 默认玩家先手
		cbFirstMove.addActionListener(e -> {
			boolean aiStarts = (cbFirstMove.getSelectedIndex() == 1);
			game.setAIStarts(aiStarts);
			String msg = aiStarts ? "设置：AI先手（白棋）" : "设置：玩家先手（黑棋）";
			appendSystemMessage(msg);
		});
		firstMoveRow.add(cbFirstMove, BorderLayout.CENTER);

		aiRow.add(difficultyRow);
		aiRow.add(firstMoveRow);

		JButton btnNew = new JButton("新开局");
		JButton btnUndo = new JButton("悔棋");

		btnNew.addActionListener(e -> {
			game.newGame();
		});
		btnUndo.addActionListener(e -> {
			boolean ok = game.undo();
			if (!ok) appendSystemMessage("无法悔棋（步数不足或正在复盘）");
			else appendSystemMessage("已悔棋");
		});

		btnPrev.addActionListener(e -> {
			boolean ok = game.reviewPrev();
			if (!ok) appendSystemMessage("无法继续向前复盘");
		});
		btnNext.addActionListener(e -> {
			boolean ok = game.reviewNext();
			if (!ok) appendSystemMessage("无法继续向后复盘");
		});

		JPanel btnPanel = new JPanel(new GridLayout(4, 1, 0, 6));
		btnPanel.add(btnNew);
		btnPanel.add(btnUndo);
		btnPanel.add(btnPrev);
		btnPanel.add(btnNext);

		// 音乐控制面板
		JPanel musicPanel = new JPanel(new BorderLayout(6, 6));
		musicPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

		JCheckBox chkBgm = new JCheckBox("背景音乐", bgmEnabled);
		JButton btnBgmToggle = new JButton("暂停");
		btnBgmToggle.setEnabled(bgm.isLoaded());

		JSlider vol = new JSlider(0, 100, 60);//音量滑块
		vol.setEnabled(bgm.isLoaded());

		JButton btnChooseBgm = new JButton("选择...");

		// 左侧布局：复选框和开关按钮
		JPanel musicLeft = new JPanel(new GridLayout(1, 2, 6, 0));
		musicLeft.add(chkBgm);
		musicLeft.add(btnBgmToggle);

		musicPanel.add(musicLeft, BorderLayout.NORTH);
		musicPanel.add(vol, BorderLayout.CENTER);
		musicPanel.add(btnChooseBgm, BorderLayout.EAST);

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
			JFileChooser fc = new JFileChooser();
			fc.setDialogTitle("选择背景音乐（WAV）");
			int r = fc.showOpenDialog(GameView.this);
			if (r == JFileChooser.APPROVE_OPTION) {
				String p = fc.getSelectedFile().getAbsolutePath();
				boolean ok = bgm.load(p);
				if (ok) {
					bgm.setVolume01(vol.getValue()/100f);
					if (bgmEnabled) bgm.playLoop();
					btnBgmToggle.setEnabled(true);
					vol.setEnabled(true);
					appendSystemMessage("已加载背景音乐：" + fc.getSelectedFile().getName());
				} else {
					appendSystemMessage("加载失败：请使用WAV格式");
				}
			}
		});

		top.add(aiRow, BorderLayout.NORTH);
		top.add(btnPanel, BorderLayout.CENTER);
		top.add(musicPanel, BorderLayout.SOUTH);
		right.add(top, BorderLayout.NORTH);

		// 消息/聊天面板
		JPanel chatPanel = new JPanel(new BorderLayout());
		chatPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

		chatPanel.add(new JLabel("消息记录："), BorderLayout.NORTH);

		chatHistory.setEditable(false);
		chatHistory.setLineWrap(true);
		JScrollPane historyScroll = new JScrollPane(chatHistory);
		historyScroll.setPreferredSize(new Dimension(280, 320));
		chatPanel.add(historyScroll, BorderLayout.CENTER);

		JPanel inputRow = new JPanel(new BorderLayout(8, 0));
		JScrollPane inputScroll = new JScrollPane(chatInput);
		inputScroll.setPreferredSize(new Dimension(10, 180));

		JButton btnSend = new JButton("发送");
		btnSend.setPreferredSize(new Dimension(90, 10));

		Runnable doSend = () -> {
			String text = chatInput.getText().trim();
			if (text.isEmpty()) return;
			appendLine("我：" + text);
			chatInput.setText("");
			if (text.equalsIgnoreCase("help") || text.equals("?")) {
				appendLine("系统：提示：可用按钮【新开局/悔棋/复盘】。复盘时不能落子。");
			}
		};

		btnSend.addActionListener(e -> doSend.run());

		// 快捷键设置：Enter 发送，Shift+Enter 换行
		chatInput.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
					e.consume();
					doSend.run();
				}
			}
		});

		inputRow.add(inputScroll, BorderLayout.CENTER);
		inputRow.add(btnSend, BorderLayout.EAST);
		inputRow.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
		chatPanel.add(inputRow, BorderLayout.SOUTH);

		right.add(chatPanel, BorderLayout.CENTER);
		return right;
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
		SwingUtilities.invokeLater(GameView::new);
	}
}

