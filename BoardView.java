import java.awt.event.MouseListener;

/**
 * BoardView - 用于显示棋盘的 MVC 视图组件。
 * 实现 Board.BoardObserver 接口以接收来自模型的更新。
 */
public class BoardView implements Board.BoardObserver {

	private BoardCanvas gui;
	private int sideLength;
	private int boardSize;

	public BoardView(int sideLength, int boardSize) {
		this.sideLength = sideLength;
		this.boardSize = boardSize;
		this.gui = new BoardCanvas(sideLength, boardSize);
	}

	// BoardObserver 接口实现
	@Override
	public void onStonePlaced(int x, int y, boolean black) {
		gui.drawStone(x, y, black);
	}

	@Override
	public void onBoardReset() {
		gui.resetBoard();
	}

	@Override
	public void onBoardUpdated(int[][] newMatrix) {
		gui.redrawFromMatrix(newMatrix);
	}

	@Override
	public void onThinkingStateChanged(boolean isThinking) {
		gui.setAIThinking(isThinking);
	}

	@Override
	public void onWinnerDetermined(int winner) {
		gui.printWinner(winner);
	}

	// 为了向后兼容，将方法委托给 BoardCanvas
	public void attachListener(MouseListener listener) {
		gui.attachListener(listener);
	}

	public int getRelativePos(int x) {
		return gui.getRelativePos(x);
	}

	public BoardCanvas getGUI() {
		return gui;
	}
}

