import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

/**
 * 棋盘数据模型。
 * 负责维护棋盘矩阵并通知观察者界面更新。
 */
public class Board {

	/**
	 * MVC 观察者模式接口，用于解耦模型和视图
	 */
	public interface BoardObserver {
		void onStonePlaced(int x, int y, boolean black);
		void onBoardReset();
		void onBoardUpdated(int[][] newMatrix);
		void onThinkingStateChanged(boolean isThinking);
		void onWinnerDetermined(int winner);
	}
//接口类，实现view与model的沟通
	private List<BoardObserver> observers = new ArrayList<>();
	private BoardCanvas gui;
	private int[][] boardMatrix; // 0: 空, 1: 白色, 2: 黑色
	
	
	public Board(int sideLength, int boardSize) {
		// 为了向后兼容，仍然创建 GUI 但标记为旧版
		// 为了向后兼容，仍然创建 GUI 但标记为旧版
		gui = new BoardCanvas(sideLength, boardSize);
		boardMatrix = new int[boardSize][boardSize];
	}

	public Board(int boardSize) {
		// 符合 MVC 规范的构造函数，没有 GUI 依赖
		this.gui = null; // 在此模式下没有 GUI
		boardMatrix = new int[boardSize][boardSize];
	}

	public void addObserver(BoardObserver observer) {
		if (observer != null && !observers.contains(observer)) {
			observers.add(observer);
		}
	}

	public void removeObserver(BoardObserver observer) {
		observers.remove(observer);
	}

	private void notifyStonePlaced(int x, int y, boolean black) {
		for (BoardObserver observer : observers) {
			observer.onStonePlaced(x, y, black);
		}
	}//通知所有观察者棋子被放置

	private void notifyBoardReset() {
		for (BoardObserver observer : observers) {
			observer.onBoardReset();
		}
	}//重置

	private void notifyBoardUpdated() {
		int[][] copy = new int[boardMatrix.length][];
		for (int i = 0; i < boardMatrix.length; i++) {
			copy[i] = boardMatrix[i].clone();
		}
		for (BoardObserver observer : observers) {
			observer.onBoardUpdated(copy);
		}
	}//传递棋盘副本

	private void notifyThinkingStateChanged(boolean isThinking) {
		for (BoardObserver observer : observers) {
			observer.onThinkingStateChanged(isThinking);
		}
	}

	private void notifyWinnerDetermined(int winner) {
		for (BoardObserver observer : observers) {
			observer.onWinnerDetermined(winner);
		}
	}

	/** 重置矩阵并通知观察者 */
	public void reset() {
		for (int r = 0; r < boardMatrix.length; r++) {
			for (int c = 0; c < boardMatrix[r].length; c++) {
				boardMatrix[r][c] = 0;
			}
		}
		if (gui != null) gui.resetBoard();
		notifyBoardReset();
	}

	//从当前矩阵重新渲染棋盘。适用于悔棋/复盘。
	public void redrawFromMatrix() {
		if (gui != null) gui.redrawFromMatrix(boardMatrix);
	}

	// 替换内部矩阵并重绘
	public void setMatrixAndRedraw(int[][] matrix) {
		for (int r = 0; r < boardMatrix.length; r++) {
			for (int c = 0; c < boardMatrix[r].length; c++) {
				boardMatrix[r][c] = matrix[r][c];
			}
		}
		if (gui != null) gui.redrawFromMatrix(boardMatrix); // 保持向后兼容
		notifyBoardUpdated();
	}

	/** 拷贝构造函数（仅拷贝棋盘矩阵） */
	public Board(Board board) {
		int[][] matrixToCopy = board.getBoardMatrix();
		boardMatrix = new int[matrixToCopy.length][matrixToCopy.length];
		for(int i=0;i<matrixToCopy.length; i++) {
			for(int j=0; j<matrixToCopy.length; j++) {
				boardMatrix[i][j] = matrixToCopy[i][j];
			}
		}
	}

	public int getBoardSize() {
		return boardMatrix.length;
	}
//模拟的添加/删除棋子，不经过gui，适用于ai思考
	public void removeStoneNoGUI(int posX, int posY){
		boardMatrix[posY][posX] = 0;
	}

	public void addStoneNoGUI(int posX, int posY, boolean black) {
		boardMatrix[posY][posX] = black ? 2 : 1;
	}

	// 在棋盘上落子
	public boolean addStone(int posX, int posY, boolean black) {
		if(posX < 0 || posY < 0 || posX >= boardMatrix.length || posY >= boardMatrix.length) return false;

		// 检查格子是否为空
		if(boardMatrix[posY][posX] != 0) return false;

		if (gui != null) gui.drawStone(posX, posY, black); // 保持向后兼容
		boardMatrix[posY][posX] = black ? 2 : 1;
		notifyStonePlaced(posX, posY, black);
		return true;
	}

	// 生成所有可能的移动（优化 AI 搜索范围）
	public ArrayList<int[]> generateMoves() {
		ArrayList<int[]> moveList = new ArrayList<int[]>();
		int boardSize = boardMatrix.length;
		// 寻找在相邻格子中至少有一个棋子的空位
		for(int i=0; i<boardSize; i++) {
			for(int j=0; j<boardSize; j++) {
				if(boardMatrix[i][j] > 0) continue;
				if(i > 0) {
					if(j > 0) {
						if(boardMatrix[i-1][j-1] > 0 ||
						   boardMatrix[i][j-1] > 0) {
							int[] move = {i,j};
							moveList.add(move);
							continue;
						}
					}
					if(j < boardSize-1) {
						if(boardMatrix[i-1][j+1] > 0 ||
						   boardMatrix[i][j+1] > 0) {
							int[] move = {i,j};
							moveList.add(move);
							continue;
						}
					}
					if(boardMatrix[i-1][j] > 0) {
						int[] move = {i,j};
						moveList.add(move);
						continue;
					}
				}
				if( i < boardSize-1) {
					if(j > 0) {
						if(boardMatrix[i+1][j-1] > 0 ||
						   boardMatrix[i][j-1] > 0) {
							int[] move = {i,j};
							moveList.add(move);
							continue;
						}
					}
					if(j < boardSize-1) {
						if(boardMatrix[i+1][j+1] > 0 ||
						   boardMatrix[i][j+1] > 0) {
							int[] move = {i,j};
							moveList.add(move);
							continue;
						}
					}
					if(boardMatrix[i+1][j] > 0) {
						int[] move = {i,j};
						moveList.add(move);
						continue;
					}
				}
				
			}
		}

		return moveList;
		
	}

	public int[][] getBoardMatrix() {
		return boardMatrix;
	}
	
	public void startListening(MouseListener listener) {
		if (gui != null) gui.attachListener(listener);
	}

	public BoardCanvas getGUI() {
		return gui;
	}

	public boolean hasGUI() {
		return gui != null;
	}

	public int getRelativePos(int x) {
		return gui != null ? gui.getRelativePos(x) : x;
	}

	//显示获胜者信息
	public void printWinner(int winner) {
		if (gui != null) gui.printWinner(winner); // 保持向后兼容
		notifyWinnerDetermined(winner);
	}

	// AI 开始思考
	public void thinkingStarted() {
		if (gui != null) gui.setAIThinking(true); // 保持向后兼容
		notifyThinkingStateChanged(true);
	}

	//AI 结束思考 
	public void thinkingFinished() {
		if (gui != null) gui.setAIThinking(false); // 保持向后兼容
		notifyThinkingStateChanged(false);
	}
}

