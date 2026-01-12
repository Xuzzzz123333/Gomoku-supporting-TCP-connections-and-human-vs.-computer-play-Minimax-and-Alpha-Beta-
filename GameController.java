import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;

/**
 * 人机对战五子棋的游戏控制器。
 * - 玩家：黑棋 (2)
 * - AI：白棋 (1)
 *
 * 支持功能：
 * - 开始新游戏
 * - 悔棋 (移除最后两步：AI 和玩家)
 * - 历史回放 (复盘模式)
 * - UI 钩子 (状态更新 + 系统消息)
 */
public class GameController {
//内部接口，与消息界面的view通信
	public interface UIHook {
		void setStatusText(String text);//状态文本
		void appendSystemMessage(String text);//系统消息
	}

	public static class Move {
		public final int x;
		public final int y;
		public final boolean black;

		public Move(int x, int y, boolean black) {
			this.x = x;
			this.y = y;
			this.black = black;
		}
	}

	private final Board board;
	private final Minimax ai;

	private UIHook ui;

	private boolean isPlayersTurn = true;
	private boolean gameFinished = false;
	private int minimaxDepth = 3;//ai难度设置，默认深度为3
	private boolean aiStarts = false; // 默认：玩家先手
	private int winner = 0; // 0 无, 1 AI 胜, 2 玩家胜

	// 历史记录 + 复盘回放
	private final ArrayList<Move> history = new ArrayList<>();
	private int replayIndex = 0; //复盘索引 当前显示的步数

	public GameController(Board board) {
		this.board = board;
		this.ai = new Minimax(board);
	}

	public void setUIHook(UIHook hook) {
		this.ui = hook;
	}

	public void setAIDepth(int depth) {
		this.minimaxDepth = Math.max(1, depth);
		if (ui != null) ui.appendSystemMessage("AI 难度已设置：深度=" + this.minimaxDepth);
	}

	public void setAIStarts(boolean aiStarts) {
		this.aiStarts = aiStarts;
		// 如果应用了新设置，自动重新开始以生效
		if (ui != null) {
			newGame();
		}
	}

	public boolean isReviewMode() {
		return replayIndex != history.size();
	}//是否处在复盘模式

	/** 启动控制器（仅调用一次） */
	public void start() {
		newGame();
	}

	/** 开始新回合 */
	public synchronized void newGame() {
		board.reset();
		history.clear();
		replayIndex = 0;
		winner = 0;
		gameFinished = false;
		isPlayersTurn = true;
		if (ui != null) ui.appendSystemMessage("开始新游戏！");
		// 可选：AI 先手（白棋）
		if (aiStarts) {
			isPlayersTurn = false;
			// 在中心位置落第一子
			int c = board.getBoardSize() / 2;
			playMove(c, c, false);
			finishMoveAndSyncReplay();
			isPlayersTurn = true;
		}

		updateStatus();
	}

	/** 悔棋上一步对局（玩家+AI）。成功返回 true。 */
	public synchronized boolean undo() {
		if (history.isEmpty()) return false;
		// 复盘模式下不允许悔棋
		if (isReviewMode()) return false;

		// 移除最后一步（通常是 AI 或玩家）
		removeLastMove();
		// 再移除一步以保持回合同步
		if (!history.isEmpty()) removeLastMove();

		gameFinished = false;
		winner = 0;
		isPlayersTurn = true;

		rebuildBoardFromHistory(history.size());
		replayIndex = history.size();//复盘索引
		updateStatus();
		return true;
	}

	/** 在复盘中后退一步。 */
	public synchronized boolean reviewPrev() {
		if (history.isEmpty()) return false;
		if (replayIndex <= 0) return false;
		replayIndex = replayIndex - 1;
		rebuildBoardFromHistory(replayIndex);
		updateStatus();
		return true;
	}

	/** 在复盘中前进一步。 */
	public synchronized boolean reviewNext() {
		if (history.isEmpty()) return false;
		if (replayIndex >= history.size()) return false;
		replayIndex = replayIndex + 1;
		rebuildBoardFromHistory(replayIndex);
		updateStatus();
		return true;
	}

	private void removeLastMove() {
		if (history.isEmpty()) return;
		history.remove(history.size() - 1);
	}

	public void onBoardClicked(MouseEvent e) {
		synchronized (this) {
			if (gameFinished) return;
			if (!isPlayersTurn) return;
			if (isReviewMode()) {
				if (ui != null) ui.appendSystemMessage("正在复盘：请点击“复盘 下一步”回到最新局面后再落子。");
				return;
			}

			// 将鼠标点击坐标转换为棋盘网格索引
			int x, y;
			if (e.getSource() instanceof BoardCanvas) {//来源于boardcanvas
				BoardCanvas canvas = (BoardCanvas) e.getSource();
				x = canvas.getRelativePos(e.getX());
				y = canvas.getRelativePos(e.getY());
			} else {
				// 兜底方案
				x = e.getX();
				y = e.getY();
			}
			if (x < 0 || y < 0) return;

			// 玩家落子 (黑棋)
			if (!playMove(x, y, true)) {
				return;
			}
			finishMoveAndSyncReplay();

			winner = checkWinner();
			if (winner == 2) {
				board.printWinner(winner);
				gameFinished = true;
				updateStatus();
				return;
			}

			// 进入 AI 回合
			isPlayersTurn = false;
			updateStatus();
		}

		// AI 在后台线程计算，防止 UI 卡死
		Thread aiThread = new Thread(() -> {
			board.thinkingStarted();
			int[] mv = ai.calculateNextMove(minimaxDepth);
			board.thinkingFinished();

			synchronized (GameController.this) {
				if (gameFinished) return;
				if (mv == null) {//ai没有找到落子位置（棋子全满）
					board.printWinner(0);
					gameFinished = true;
					updateStatus();
					return;
				}

				int aiY = mv[0];
				int aiX = mv[1];
				playMove(aiX, aiY, false);
				finishMoveAndSyncReplay();

				winner = checkWinner();
				if (winner == 1) {
					board.printWinner(winner);
					gameFinished = true;
					updateStatus();
					return;
				}

				if (board.generateMoves().isEmpty()) {
					board.printWinner(0);
					gameFinished = true;
					updateStatus();
					return;
				}

				isPlayersTurn = true;
				updateStatus();
			}
		});
		aiThread.start();
	}

	private boolean playMove(int x, int y, boolean black) {
		boolean ok = board.addStone(x, y, black);
		if (!ok) return false;
		history.add(new Move(x, y, black));
		return true;
	}

	private void finishMoveAndSyncReplay() {
		replayIndex = history.size();
	}

	private int checkWinner() {
		// 在本项目中，黑棋/玩家使用 getScore(..., true, ...) 进行判断
		if (Minimax.getScore(board, true, false) >= Minimax.getWinScore()) return 2;
		if (Minimax.getScore(board, false, true) >= Minimax.getWinScore()) return 1;
		return 0;
	}
//用于悔棋或复盘后的重绘棋盘
	private void rebuildBoardFromHistory(int upto) {
		int n = board.getBoardSize();
		int[][] m = new int[n][n];
		for (int i = 0; i < upto && i < history.size(); i++) {
			Move mv = history.get(i);
			m[mv.y][mv.x] = mv.black ? 2 : 1;
		}
		board.setMatrixAndRedraw(m);
	}

	private void updateStatus() {
		if (ui == null) return;

		if (gameFinished) {
			ui.setStatusText("状态：对局结束" + (winner == 2 ? "（玩家胜）" : (winner == 1 ? "（电脑胜）" : "（平局）")));
			return;
		}

		if (isReviewMode()) {
			ui.setStatusText("状态：复盘 " + replayIndex + "/" + history.size());
			return;
		}

		ui.setStatusText("状态：轮到：" + (isPlayersTurn ? "黑棋(玩家)" : "白棋(AI)"));
	}
}

