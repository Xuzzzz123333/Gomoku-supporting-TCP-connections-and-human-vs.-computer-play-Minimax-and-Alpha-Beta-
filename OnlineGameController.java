import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 联机 PvP 五子棋控制器（简单、确定性的规则）。
 * - 服务器默认为黑棋（先手），客户端默认为白棋（后手）。
 * - 使用基于 TCP 的微型文本协议：
 *   MOVE x y         // 落子
 *   TIMEOUT_MOVE x y // 系统超时随机落子
 *   CHAT <base64>    // 聊天
 *   NEW              // 请求新开局
 *   UNDO_REQ / UNDO_OK / UNDO_NO // 悔棋请求/通过/拒绝
 *
 * 悔棋规则（根据要求）：
 * - 只有刚刚落子的玩家可以请求悔棋，且必须在对方落子之前提出。
 * - 如果对方接受，仅撤销那一步落子，请求者重新落子。
 */
public class OnlineGameController {

	public interface UIHook {
		void setStatusText(String text);
		void appendSystemMessage(String text);
	}

	// 窗口应实现此接口，通过 TCP 转发消息。
	public interface NetHook {
		void sendLine(String line);
	}

	private static class Move {
		final int x, y;
		final boolean black;
		Move(int x, int y, boolean black) { this.x = x; this.y = y; this.black = black; }
	}

	private final Board board;
	private UIHook ui;
	private NetHook net;

	private final ArrayList<Move> history = new ArrayList<>();
	private int replayIndex = 0;

	private boolean connected = false;
	private boolean myBlack = true;      // 服务器：true，客户端：false
	private boolean blackTurn = true;    // 黑棋先手
	private boolean gameFinished = false;
	private boolean serverWantsBlack = true; // 服务器选择谁拿黑棋（先手）

	// winnerColor: 0 平局/无, 2 黑胜, 1 白胜
	private int winnerColor = 0;

	// 倒计时计时器（2 分钟 = 120 秒）
	private Timer countdownTimer;
	private int remainingSeconds = 120;
	private final int TOTAL_SECONDS = 120;
	private Random random = new Random();

	// 对手倒计时显示模拟
	private Timer opponentCountdownTimer;
	private int opponentRemainingSeconds = 120;

	private boolean waitingUndoResponse = false;
	private boolean waitingDrawResponse = false;

	public OnlineGameController(Board board) {
		this.board = board;
	}

	public void setUIHook(UIHook hook) {
		this.ui = hook;
	}

	public void setNetHook(NetHook hook) {
		this.net = hook;
	}

	public synchronized void setConnected(boolean connected) {
		this.connected = connected;
		if (!connected) {
			waitingUndoResponse = false;
			waitingDrawResponse = false;
			stopCountdown(); // 断开连接时停止倒计时
			stopOpponentCountdown(); // 断开连接时停止对手倒计时
		}
		updateStatus();
	}

	public synchronized void setMyBlack(boolean isServer) {
		// 根据服务器的选择决定谁是黑棋
		// 如果isServer为true，表示这是服务器调用
		// 如果isServer为false，表示这是客户端调用
		this.myBlack = isServer ? serverWantsBlack : !serverWantsBlack;
		updateStatus();
	}

	public synchronized void setServerWantsBlack(boolean serverWantsBlack) {
		this.serverWantsBlack = serverWantsBlack;
	}

	//启动当前回合玩家的倒计时
	private synchronized void startCountdown() {
		stopCountdown(); // 停止旧的计时器
		remainingSeconds = TOTAL_SECONDS;
		System.out.println("DEBUG: Starting countdown timer for " + (blackTurn ? "black" : "white") + " player");
		countdownTimer = new Timer(true); // 守护线程
		countdownTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				synchronized (OnlineGameController.this) {
					if (gameFinished || !connected) {
						stopCountdown();
						return;
					}

					remainingSeconds--;
					updateStatus(); // 更新ui界面

					if (remainingSeconds <= 0) {
						// 时间到！进行随机落子
						makeRandomMove();
						stopCountdown();
					}
				}
			}
		}, 1000, 1000); // 每秒更新
	}

	private synchronized void stopCountdown() {
		if (countdownTimer != null) {
			countdownTimer.cancel();
			countdownTimer = null;
		}
	}

	// 启动对手倒计时模拟显示
	private synchronized void startOpponentCountdown() {
		stopOpponentCountdown(); // 停止旧的计时器
		opponentRemainingSeconds = TOTAL_SECONDS;
		opponentCountdownTimer = new Timer(true); // 守护线程
		opponentCountdownTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				synchronized (OnlineGameController.this) {
					if (gameFinished || !connected) {
						stopOpponentCountdown();
						return;
					}

					opponentRemainingSeconds--;
					updateStatus(); // 更新ui

					if (opponentRemainingSeconds <= 0) {
						// 此处仅用于显示，实际超时落子由对手端处理
						stopOpponentCountdown();
					}
				}
			}
		}, 1000, 1000); // 每秒更新
	}

	//停止对手倒计时
	private synchronized void stopOpponentCountdown() {
		if (opponentCountdownTimer != null) {
			opponentCountdownTimer.cancel();
			opponentCountdownTimer = null;
		}
	}

	//为当前玩家随机落子
	private synchronized void makeRandomMove() {
		if (gameFinished || !connected) {
			System.out.println("DEBUG: Cannot make random move - gameFinished: " + gameFinished + ", connected: " + connected);
			return;
		}

		System.out.println("DEBUG: Making random move for " + (blackTurn ? "black" : "white") + " player");

		// 生成合法落子列表
		ArrayList<int[]> validMoves = board.generateMoves();
		if (validMoves.isEmpty()) {
			System.out.println("DEBUG: No valid moves available");
			return;
		}

		// 选取随机落子
		int[] randomMove = validMoves.get(random.nextInt(validMoves.size()));
		int x = randomMove[1]; // generateMoves 返回 [y, x]
		int y = randomMove[0];

		System.out.println("DEBUG: Selected random move at (" + x + "," + y + ")");

		// 在本地落子
		boolean ok = playMove(x, y, blackTurn, false); // 不直接发送 MOVE，而是发送 TIMEOUT_MOVE
		if (ok) {
			afterMove();
			// 发送特殊的超时落子消息给对方
			if (connected && net != null) {
				net.sendLine("TIMEOUT_MOVE " + x + " " + y);
			}

			if (ui != null) {
				ui.appendSystemMessage("系统：时间到！系统随机下棋于 (" + (x+1) + "," + (char)('A'+y) + ")");
			}
		} else {
			System.out.println("DEBUG: Failed to make random move");
		}
	}

	public synchronized boolean isConnected() {
		return connected;
	}

	public synchronized boolean isReviewMode() {
		return replayIndex != history.size();
	}

	public void start() {
		// 初始化棋盘到干净状态（不广播）
		newGameLocal(false, "进入联机对战界面。请先创建/加入房间。");
	}

	//本地按钮：新开局，广播给对方。
	public synchronized void newGame() {
		if (!connected || net == null) {
			newGameLocal(false, "新开局（未连接，仅本地重置）。");
			return;
		}
		newGameLocal(true, "你发起了新开局。");
	}

	//当对方发送 NEW 消息时调用。
	public synchronized void newGameFromPeer() {
		newGameLocal(false, "对方发起了新开局。");
	}

	// 在接收到 START 消息时重启游戏但不广播
	public synchronized void restartGameForStart() {
		stopCountdown();
		// 重置游戏状态，不显示消息
		board.reset();
		history.clear();
		replayIndex = 0;
		winnerColor = 0;
		gameFinished = false;
		blackTurn = true;
		waitingUndoResponse = false;
		if (connected && !gameFinished) {
			boolean myTurn = (blackTurn == myBlack);
			if (myTurn) {
				startCountdown();
			}
		}

		updateStatus();
	}

	private void newGameLocal(boolean broadcast, String sysMsg) {
		stopCountdown();
		stopOpponentCountdown();
		board.reset();
		history.clear();
		replayIndex = 0;
		winnerColor = 0;
		gameFinished = false;
		blackTurn = true;
		waitingUndoResponse = false;
		waitingDrawResponse = false;

		if (ui != null && sysMsg != null && !sysMsg.isEmpty()) ui.appendSystemMessage("系统：" + sysMsg);
		if (broadcast && connected && net != null) net.sendLine("NEW");

		// Start countdown for the first move (only if it's my turn)
		if (connected && !gameFinished) {
			boolean myTurn = (blackTurn == myBlack);
			if (myTurn) {
				startCountdown();
			}
		}

		updateStatus();
	}

	public synchronized void onBoardClicked(MouseEvent e) {
		if (!connected) {
			if (ui != null) ui.appendSystemMessage("系统：请先建立连接（创建/加入房间）");
			return;
		}
		if (waitingUndoResponse) {
			if (ui != null) ui.appendSystemMessage("系统：正在等待对方确认悔棋，请稍后");
			return;
		}
		if (gameFinished) return;

		if (isReviewMode()) {
			if (ui != null) ui.appendSystemMessage("系统：复盘中不能落子");
			return;
		}
		boolean myTurn = (blackTurn == myBlack);
		if (!myTurn) {
			if (ui != null) ui.appendSystemMessage("系统：现在轮到对方落子");
			return;
		}
		stopCountdown();

		int x, y;
		if (e.getSource() instanceof BoardCanvas) {
			BoardCanvas canvas = (BoardCanvas) e.getSource();
			x = canvas.getRelativePos(e.getX());
			y = canvas.getRelativePos(e.getY());
		} else {

			x = e.getX();
			y = e.getY();
		}
		if (x < 0 || y < 0) return;

		boolean ok = playMove(x, y, blackTurn, true);
		if (!ok) {

			if (connected && !gameFinished) {
				startCountdown();
			}
			return;
		}

		afterMove();
	}

	// 应用一次本地落子。如果 broadcast==true，发送 MOVE 给对方。
	private boolean playMove(int x, int y, boolean asBlack, boolean broadcast) {
		boolean ok = board.addStone(x, y, asBlack);
		if (!ok) return false;
		history.add(new Move(x, y, asBlack));
		replayIndex = history.size();
		if (broadcast && connected && net != null) {
			net.sendLine("MOVE " + x + " " + y);
		}
		blackTurn = !blackTurn;
		return true;
	}
	//当对方发送 TIMEOUT_MOVE（系统超时随机落子）时，由网络线程调用
	public synchronized void applyTimeoutMove(int x, int y) {
		if (!connected) return;
		if (gameFinished) return;
		stopCountdown();
		stopOpponentCountdown();

		if (isReviewMode()) {
			replayIndex = history.size();
		}
		boolean ok = playMove(x, y, blackTurn, false);
		if (ok) {
			afterMove();
			if (ui != null) {
				ui.appendSystemMessage("系统：对方时间到！系统随机下棋于 (" + (x+1) + "," + (char)('A'+y) + ")");
			}
		} else {
			if (ui != null) ui.appendSystemMessage("系统：警告：收到对方超时落子，但位置无效。");
		}
	}

	// 当对方发送 MOVE 时由网络线程调用。
	public synchronized void applyRemoteMove(int x, int y) {
		if (!connected) return;

		stopCountdown();
		stopOpponentCountdown();
		if (waitingUndoResponse) {
			waitingUndoResponse = false;
			if (ui != null) ui.appendSystemMessage("系统：对方已落子，本次悔棋请求自动失效。");
		}
		if (gameFinished) return;

		if (isReviewMode()) {
			replayIndex = history.size();
		}

		boolean ok = playMove(x, y, blackTurn, false);
		if (!ok) {
			if (ui != null) ui.appendSystemMessage("系统：警告：收到对方落子，但位置无效（可能已被占用）。");
			return;
		}
		afterMove();
	}

	private void afterMove() {
		winnerColor = checkWinnerColor();
		if (winnerColor != 0) {
			stopCountdown();
			gameFinished = true;
			printWinnerForLocal();
			updateStatus();
			return;
		}
		int n = board.getBoardSize();
		if (history.size() >= n * n) {
			stopCountdown();
			gameFinished = true;
			board.printWinner(0);
			updateStatus();
			return;
		}

		if (connected && !gameFinished) {
			boolean myTurn = (blackTurn == myBlack);
			if (myTurn) {
				startCountdown();
			} else {
				startOpponentCountdown();
			}
		}
		updateStatus();
	}
	private void printWinnerForLocal() {
		if (winnerColor == 0) {
			board.printWinner(0);
			return;
		}
		boolean localWin = (winnerColor == 2 && myBlack) || (winnerColor == 1 && !myBlack);
		board.printWinner(localWin ? 2 : 1);
	}

	private int checkWinnerColor() {
		if (Minimax.getScore(board, true, false) >= Minimax.getWinScore()) return 2;
		if (Minimax.getScore(board, false, true) >= Minimax.getWinScore()) return 1;
		return 0;
	}

	// ---------------- 悔棋（单步，受限） ----------------

	/**
	 * 请求悔棋“我的上一步”。仅在以下情况允许：
	 * - 当前轮到对方落子（即我刚刚下完），并且
	 * - 历史记录中最后一步是我落的。
	 */
	public synchronized boolean requestUndo() {
		if (!connected || net == null) return false;
		if (waitingUndoResponse) return false;
		if (isReviewMode()) return false;
		if (gameFinished) return false;
		if (!canRequestUndoNow()) return false;

		waitingUndoResponse = true;
		net.sendLine("UNDO_REQ");
		if (ui != null) ui.appendSystemMessage("系统：已向对方发送悔棋请求（撤销你刚刚下的那一步）");
		updateStatus();
		return true;
	}

	public synchronized boolean canRequestUndoNow() {
		if (!connected) return false;
		if (waitingUndoResponse) return false;
		if (isReviewMode()) return false;
		if (gameFinished) return false;
		if (history.isEmpty()) return false;

		boolean myTurn = (blackTurn == myBlack);
		Move last = history.get(history.size() - 1);
		//当且仅当是对手回合且上一步是我落的子时才可悔棋
		return !myTurn && (last.black == myBlack);
	}

	/**
	 * 我现在能否接受对方的悔棋请求？
	 * 应该是当我轮到我落子，且最后一步是对方下的时候。
	 */
	public synchronized boolean canAcceptUndoRequestNow() {
		if (!connected) return false;
		if (waitingUndoResponse) return false;
		if (isReviewMode()) return false;
		if (gameFinished) return false;
		if (history.isEmpty()) return false;

		boolean myTurn = (blackTurn == myBlack);
		Move last = history.get(history.size() - 1);
		return myTurn && (last.black != myBlack);
	}

	//对方请求悔棋；窗口可能提示用户，然后调用 respondUndoFromLocalDecision。
	public synchronized void onUndoRequestFromPeer() {
		if (ui != null) ui.appendSystemMessage("系统：对方请求悔棋（撤销其刚刚下的那一步）");
	}

	public synchronized void respondUndoFromLocalDecision(boolean accept) {
		if (!connected || net == null) return;

		if (!accept) {
			net.sendLine("UNDO_NO");
			if (ui != null) ui.appendSystemMessage("系统：你已拒绝对方的悔棋请求");
			return;
		}

		if (!canAcceptUndoRequestNow()) {
			net.sendLine("UNDO_NO");
			if (ui != null) ui.appendSystemMessage("系统：无法同意悔棋（对方已不是刚落子，或你已不在落子阶段）");
			return;
		}

		applyUndoSingle();
		net.sendLine("UNDO_OK");
		if (ui != null) ui.appendSystemMessage("系统：已同意悔棋，已撤销对方刚刚下的那一步");
		updateStatus();
	}

	public synchronized void onUndoAcceptedByPeer() {
		if (!waitingUndoResponse) return;
		waitingUndoResponse = false;

		// We only undo if last move is still mine.
		if (history.isEmpty()) {
			if (ui != null) ui.appendSystemMessage("系统：对方同意悔棋，但本地无可撤销步（可能已不同步）");
			updateStatus();
			return;
		}
		Move last = history.get(history.size() - 1);
		if (last.black != myBlack) {
			if (ui != null) ui.appendSystemMessage("系统：对方同意悔棋，但本地最新一步不是我方（可能已不同步）");
			updateStatus();
			return;
		}

		applyUndoSingle();
		if (ui != null) ui.appendSystemMessage("系统：对方同意悔棋，已撤销你刚刚下的那一步，请重新落子");
		updateStatus();
	}

	public synchronized void onUndoRejectedByPeer() {
		if (!waitingUndoResponse) return;
		waitingUndoResponse = false;
		if (ui != null) ui.appendSystemMessage("系统：对方拒绝悔棋请求");
		updateStatus();
	}

	/** 移除最后一步并重置棋盘；将回合归还给该玩家。 */
	private void applyUndoSingle() {
		if (history.isEmpty()) return;
		history.remove(history.size() - 1);

		replayIndex = history.size();
		rebuildBoardFromHistory(replayIndex);
		blackTurn = (history.size() % 2 == 0);

		winnerColor = 0;
		gameFinished = false;
	}

	private void rebuildBoardFromHistory(int upto) {
		int n = board.getBoardSize();
		int[][] m = new int[n][n];

		for (int i = 0; i < upto && i < history.size(); i++) {
			Move mv = history.get(i);
			// boardMatrix is [row][col] = [y][x]
			m[mv.y][mv.x] = mv.black ? 2 : 1;
		}
		board.setMatrixAndRedraw(m);
	}

	//复盘（仅在对局结束后）
	public synchronized boolean reviewPrev() {
		if (!gameFinished) return false;
		if (history.isEmpty()) return false;
		if (replayIndex <= 0) return false;
		replayIndex = replayIndex - 1;
		rebuildBoardFromHistory(replayIndex);
		updateStatus();
		return true;
	}

	public synchronized boolean reviewNext() {
		if (!gameFinished) return false;
		if (history.isEmpty()) return false;
		if (replayIndex >= history.size()) return false;
		replayIndex = replayIndex + 1;
		rebuildBoardFromHistory(replayIndex);
		updateStatus();
		return true;
	}

	private void updateStatus() {
		if (ui == null) return;

		if (!connected) {
			ui.setStatusText("状态：未连接（请创建/加入房间）");
			return;
		}

		String me = myBlack ? "黑棋(我)" : "白棋(我)";
		if (gameFinished) {
			if (winnerColor == 0) ui.setStatusText("状态：对局结束（平局）  我方：" + me);
			else {
				boolean localWin = (winnerColor == 2 && myBlack) || (winnerColor == 1 && !myBlack);
				ui.setStatusText("状态：对局结束（" + (localWin ? "我方胜" : "对方胜") + "）  我方：" + me);
			}
			return;
		}

		if (isReviewMode()) {
			ui.setStatusText("状态：复盘 " + replayIndex + "/" + history.size() + "  我方：" + me);
			return;
		}

		if (waitingUndoResponse) {
			ui.setStatusText("状态：等待对方确认悔棋...  我方：" + me);
			return;
		}

		String turn = blackTurn ? "黑棋" : "白棋";
		boolean myTurn = (blackTurn == myBlack);
		String countdownInfo = "";
		if (myTurn && countdownTimer != null && !gameFinished) {
			// 轮到己方时，显示实际的倒计时
			int minutes = remainingSeconds / 60;
			int seconds = remainingSeconds % 60;
			countdownInfo = String.format(" [倒计时 %d:%02d]", minutes, seconds);
		} else if (!myTurn && opponentCountdownTimer != null && !gameFinished) {
			// 轮到对方时，显示对手倒计时模拟
			int minutes = opponentRemainingSeconds / 60;
			int seconds = opponentRemainingSeconds % 60;
			countdownInfo = String.format(" [对方倒计时 %d:%02d]", minutes, seconds);
		} else if (!myTurn && !gameFinished) {
			// 对手倒计时未启动时，显示等待状态
			countdownInfo = " [等待对方下棋]";
		}
		if (waitingDrawResponse) {
			ui.setStatusText("状态：等待对方确认求和...  我方：" + me);
			return;
		}

		ui.setStatusText("状态：轮到：" + turn + (myTurn ? "（我）" : "（对方）") + countdownInfo + "  我方：" + me);
	}

	// ---------------- 求和 ----------------

	/**
	 * 发起求和请求。
	 */
	public synchronized boolean requestDraw() {
		if (!connected || net == null) return false;
		if (waitingDrawResponse) return false;
		if (waitingUndoResponse) return false;
		if (gameFinished) return false;

		waitingDrawResponse = true;
		net.sendLine("DRAW_REQ");
		if (ui != null) ui.appendSystemMessage("系统：已向对方发送求和请求");
		updateStatus();
		return true;
	}

	public synchronized boolean canRequestDrawNow() {
		if (!connected) return false;
		if (waitingDrawResponse) return false;
		if (waitingUndoResponse) return false;
		if (gameFinished) return false;
		return true;
	}

	/** 当对方发送 DRAW_REQ 时调用。 */
	public synchronized void onDrawRequestFromPeer() {
		if (ui != null) ui.appendSystemMessage("系统：对方请求求和（平局结束）");
	}

	public synchronized void respondDrawFromLocalDecision(boolean accept) {
		if (!connected || net == null) return;

		if (!accept) {
			net.sendLine("DRAW_NO");
			if (ui != null) ui.appendSystemMessage("系统：你已拒绝对方的求和请求");
			return;
		}
		if (gameFinished) {
			net.sendLine("DRAW_NO");
			if (ui != null) ui.appendSystemMessage("系统：游戏已结束，无法同意求和");
			return;
		}
		stopCountdown();
		stopOpponentCountdown();
		gameFinished = true;
		winnerColor = 0; // tie
		board.printWinner(0);
		net.sendLine("DRAW_OK");
		if (ui != null) ui.appendSystemMessage("系统：已同意求和，对局以平局结束");
		updateStatus();
	}

	public synchronized void onDrawAcceptedByPeer() {
		if (!waitingDrawResponse) return;
		waitingDrawResponse = false;
		stopCountdown();
		stopOpponentCountdown();
		gameFinished = true;
		winnerColor = 0;
		board.printWinner(0);
		if (ui != null) ui.appendSystemMessage("系统：对方同意求和，对局以平局结束");
		updateStatus();
	}

	public synchronized void onDrawRejectedByPeer() {
		if (!waitingDrawResponse) return;
		waitingDrawResponse = false;
		if (ui != null) ui.appendSystemMessage("系统：对方拒绝了求和请求");
		updateStatus();
	}

	// 认输

	/**
	 * 认输。对方直接获胜。
	 */
	public synchronized boolean resign() {
		if (!connected || net == null) return false;
		if (gameFinished) return false;
		stopCountdown();
		stopOpponentCountdown();
		gameFinished = true;
		winnerColor = myBlack ? 1 : 2;
		board.printWinner(1);
		net.sendLine("RESIGN");
		if (ui != null) ui.appendSystemMessage("系统：你已认输，对局结束");
		updateStatus();
		return true;
	}

	/** 当对方发送 RESIGN 消息时调用。 */
	public synchronized void onResignFromPeer() {
		if (gameFinished) return;
		stopCountdown();
		stopOpponentCountdown();
		gameFinished = true;
		winnerColor = myBlack ? 2 : 1;
		board.printWinner(2);
		if (ui != null) ui.appendSystemMessage("系统：对方认输，你获胜！");
		updateStatus();
	}
}
