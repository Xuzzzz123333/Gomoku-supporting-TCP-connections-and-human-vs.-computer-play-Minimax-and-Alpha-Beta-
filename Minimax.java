import java.util.ArrayList;


public class Minimax {
	
	// 此变量用于跟踪评估次数，以便进行基准测试。
	public static int evaluationCount = 0;
	// Board 实例负责处理棋盘机制
	private Board board;
	// 获胜分数应大于所有可能的棋盘评估分
	private static final int WIN_SCORE = 100_000_000;

	public Minimax(Board board) {
		this.board = board;
	}
	
	// winScore 的 Getter 函数
	public static int getWinScore() {
		return WIN_SCORE;
	}

	// 此函数计算白棋相对于黑棋的相对得分。
	// (即白棋比黑棋早赢的可能性有多大)
	// 该值将用作 Minimax 算法中的得分。
	public static double evaluateBoardForWhite(Board board, boolean blacksTurn) {
		evaluationCount++;
		// 获取双方玩家的棋盘得分。
		double blackScore = getScore(board, true, blacksTurn);
		double whiteScore = getScore(board, false, blacksTurn);
		
		if(blackScore == 0) blackScore = 1.0;
		
		// 计算白棋相对于黑棋的相对得分
		return whiteScore / blackScore;
	}

	// 此函数计算指定玩家的棋盘得分。
	// (即：考虑该玩家在棋盘上有多少个连续的 2、3、4 子，其中有多少个被堵死等，以此来衡量玩家的整体局势)
	public static int getScore(Board board, boolean forBlack, boolean blacksTurn) {
		
		// 读取棋盘
		int[][] boardMatrix = board.getBoardMatrix();

		// 计算 3 个方向（水平、垂直、对角线）的总得分
		return evaluateHorizontal(boardMatrix, forBlack, blacksTurn) +
				evaluateVertical(boardMatrix, forBlack, blacksTurn) +
				evaluateDiagonal(boardMatrix, forBlack, blacksTurn);
	}
	
	// 此函数用于获取 AI 的下一步智能落子。
	public int[] calculateNextMove(int depth) {
		// 锁定棋盘，让 AI 进行决策。
		board.thinkingStarted();

		int[] move = new int[2];

		// 仅用于基准测试目的。
		long startTime = System.currentTimeMillis();

		// 检查是否有任何空位可以直接结束游戏。
		// 确保 AI 始终抓住赢棋的机会。
		Object[] bestMove = searchWinningMove(board);

		if(bestMove != null ) {
			// 找到赢棋点。
			move[0] = (Integer)(bestMove[1]);
			move[1] = (Integer)(bestMove[2]);
			
		} else {
			// 如果没有直接赢棋的步子，则使用指定深度搜索 Minimax 树。
			bestMove = minimaxSearchAB(depth, new Board(board), true, -1.0, getWinScore());
			if(bestMove[1] == null) {
				move = null;//没有找到落子
			} else {
				move[0] = (Integer)(bestMove[1]);
				move[1] = (Integer)(bestMove[2]);
			}
		}
		System.out.println("计算案例数: " + evaluationCount + " 计算耗时: " + (System.currentTimeMillis() - startTime) + " ms");
		board.thinkingFinished();
		
		evaluationCount=0;
		
		return move;
	}
	
	
	/*
	 * alpha : AI 的最佳落子 (极大值)
	 * beta : 玩家的最佳落子 (极小值)
	 * 返回: {得分, 坐标[0], 坐标[1]}
	 * */
	private static Object[] minimaxSearchAB(int depth, Board dummyBoard, boolean max, double alpha, double beta) {

		// 到达最大搜索深度（叶子节点），评估当前棋盘得分。
		if(depth == 0) {
			Object[] x = {evaluateBoardForWhite(dummyBoard, !max), null, null};
			return x;
		}
		
		// 从 Minimax 树的该节点生成所有可能的进一步落子
		/*
		 *                  (落子 1)
		 *	               /
		 *  (当前节点) --- (落子 2)
		 *				   \   ...
		 *                  (落子 N)
		 */
		ArrayList<int[]> allPossibleMoves = dummyBoard.generateMoves();
		
		// 如果没有剩下的空位，将此节点视为叶子节点并返回得分。
		if(allPossibleMoves.size() == 0) {
			Object[] x = {evaluateBoardForWhite(dummyBoard, !max), null, null};
			return x;
		}
		
		Object[] bestMove = new Object[3];
		
		// 生成 Minimax 树并计算各节点的分数。
		if(max) {
			// 用负无穷初始化初始最佳步的分数。
			bestMove[0] = -1.0;
			// 遍历所有可能的落子位置。
			for(int[] move : allPossibleMoves) {

				// 在模拟棋盘上模拟落子，不触发真实 GUI 绘制
				dummyBoard.addStoneNoGUI(move[1], move[0], false);
				
				// 针对下一深度调用 Minimax 函数，寻找极小值分。
				// 该函数从该节点递归生成新的 Minimax 分支树（若深度 > 0），
				// 并搜索每个子树中的最低得分。
				// 我们将选取更低层深度中最小得分中的最大值。
				Object[] tempMove = minimaxSearchAB(depth-1, dummyBoard, false, alpha, beta);

				// 回溯：移除模拟落子
				dummyBoard.removeStoneNoGUI(move[1],move[0]);

				// 更新 alpha（alpha 持有当前找到的最大分值）
				// 在寻找极小值时，如果子节点得分低于 alpha（上一层极大节点的极大值），
				// 则可以剪掉该子树，因为极大值玩家绝不会选择比 alpha 更低的节点。
				if((Double)(tempMove[0]) > alpha) {
					alpha = (Double)(tempMove[0]);
				}
				// Beta 剪枝
				// Beta 持有一层之上极小节点的当前最小值。
				// 如果当前分数高于 beta，我们可以停止搜索，因为极小值玩家（上一层）
				// 绝不会让局势发展到比 beta 更糟糕的分支。
				if((Double)(tempMove[0]) >= beta) {
					return tempMove;
				}

				// 找到具有最高分的落子。
				if((Double)tempMove[0] > (Double)bestMove[0]) {
					bestMove = tempMove;
					bestMove[1] = move[0];
					bestMove[2] = move[1];
				}
			}
		}
		else {
			// 用正无穷初始化初始最佳步的分数。
			bestMove[0] = 100_000_000.0;
			bestMove[1] = allPossibleMoves.get(0)[0];
			bestMove[2] = allPossibleMoves.get(0)[1];
			
			// 遍历所有可能的落子位置。
			for(int[] move : allPossibleMoves) {

				// 模拟落子
				dummyBoard.addStoneNoGUI(move[1], move[0], true);
				
				// 针对下一深度调用 Minimax 函数，寻找极大值分。
				Object[] tempMove = minimaxSearchAB(depth-1, dummyBoard, true, alpha, beta);

				// 回溯
				dummyBoard.removeStoneNoGUI(move[1],move[0]);
				
				// 更新 beta（beta 持有当前找到的最小分值）
				if(((Double)tempMove[0]) < beta) {
					beta = (Double)(tempMove[0]);
				}
				// Alpha 剪枝
				if((Double)(tempMove[0]) <= alpha) {
					return tempMove;
				}
				
				// 找到具有最低分的落子。
				if((Double)tempMove[0] < (Double)bestMove[0]) {
					bestMove = tempMove;
					bestMove[1] = move[0];
					bestMove[2] = move[1];
				}
			}
		}
		return bestMove;
	}
	
	// 此函数寻找一个可以直接获胜的位置。
	private static Object[] searchWinningMove(Board board) {
		ArrayList<int[]> allPossibleMoves = board.generateMoves();
		Object[] winningMove = new Object[3];
		
		// 遍历所有可能的落子
		for(int[] move : allPossibleMoves) {
			evaluationCount++;
			// 创建模拟棋盘
			Board dummyBoard = new Board(board);
			// 模拟落子
			dummyBoard.addStoneNoGUI(move[1], move[0], false);
			
			// 如果在模拟棋盘上白棋获得了赢棋分，则返回该步。
			if(getScore(dummyBoard,false,false) >= WIN_SCORE) {
				winningMove[1] = move[0];
				winningMove[2] = move[1];
				return winningMove;
			}
		}
		return null;
	}

	// 该函数通过评估水平方向的棋子分布来计算分值。
	public static int evaluateHorizontal(int[][] boardMatrix, boolean forBlack, boolean playersTurn ) {

		int[] evaluations = {0, 2, 0}; // [0] -> 连续个数, [1] -> 阻碍个数, [2] -> 得分
		// blocks 变量用于检查一串连续的棋子是否被对手或棋盘边缘阻挡。
		// 如果两端都被阻挡，blocks 为 2。
		// 如果只有一端被阻挡，blocks 为 1；如果两端都空闲，blocks 为 0。
		// 系统默认第一列左侧是被棋盘边缘阻挡的。
		// 遍历所有行
		for(int i=0; i<boardMatrix.length; i++) {
			// 遍历行内的所有单元格
			for(int j=0; j<boardMatrix[0].length; j++) {
				// 检查所选玩家是否在当前格有子
				evaluateDirections(boardMatrix,i,j,forBlack,playersTurn,evaluations);
			}
			evaluateDirectionsAfterOnePass(evaluations, forBlack, playersTurn);
		}

		return evaluations[2];
	}
	
	// 在垂直方向同理评估。
	public static  int evaluateVertical(int[][] boardMatrix, boolean forBlack, boolean playersTurn ) {

		int[] evaluations = {0, 2, 0}; 
		
		for(int j=0; j<boardMatrix[0].length; j++) {
			for(int i=0; i<boardMatrix.length; i++) {
				evaluateDirections(boardMatrix,i,j,forBlack,playersTurn,evaluations);
			}
			evaluateDirectionsAfterOnePass(evaluations,forBlack,playersTurn);
			
		}
		return evaluations[2];
	}

	// 在对角线方向同理评估。
	public static  int evaluateDiagonal(int[][] boardMatrix, boolean forBlack, boolean playersTurn ) {

		int[] evaluations = {0, 2, 0}; 
		// 从左下到右上
		for (int k = 0; k <= 2 * (boardMatrix.length - 1); k++) {
		    int iStart = Math.max(0, k - boardMatrix.length + 1);
		    int iEnd = Math.min(boardMatrix.length - 1, k);
		    for (int i = iStart; i <= iEnd; ++i) {
		        evaluateDirections(boardMatrix,i,k-i,forBlack,playersTurn,evaluations);
		    }
		    evaluateDirectionsAfterOnePass(evaluations,forBlack,playersTurn);
		}
		// 从左上到右下
		for (int k = 1-boardMatrix.length; k < boardMatrix.length; k++) {
		    int iStart = Math.max(0, k);
		    int iEnd = Math.min(boardMatrix.length + k - 1, boardMatrix.length-1);
		    for (int i = iStart; i <= iEnd; ++i) {
				evaluateDirections(boardMatrix,i,i-k,forBlack,playersTurn,evaluations);
		    }
			evaluateDirectionsAfterOnePass(evaluations,forBlack,playersTurn);
		}
		return evaluations[2];
	}
	public static void evaluateDirections(int[][] boardMatrix, int i, int j, boolean isBot, boolean botsTurn, int[] eval) {
		// 检查玩家是否在该格有子
		if (boardMatrix[i][j] == (isBot ? 2 : 1)) {
			// 增加连续棋子计数
			eval[0]++;
		}
		// 如果是空位
		else if (boardMatrix[i][j] == 0) {
			// 检查之前是否有连续的棋子
			if (eval[0] > 0) {
				// 该方向之前有棋子，且当前位置是空的，减少一端阻碍
				eval[1]--;
				// 计算得分
				eval[2] += getConsecutiveSetScore(eval[0], eval[1], isBot == botsTurn);
				// 重置计数
				eval[0] = 0;
			}
			// 下一个连续棋子串最多只有一侧被阻碍
			eval[1] = 1;
		}
		// 单元格被对手占据
		else if (eval[0] > 0) {
			// 计算之前连续棋子的得分
			eval[2] += getConsecutiveSetScore(eval[0], eval[1], isBot == botsTurn);
			// 重置计数
			eval[0] = 0;
			// 当前位置被对手占据，因此下一个串可能在此端被阻碍
			eval[1] = 2;
		} else {
			eval[1] = 2;
		}
	}
	private static void evaluateDirectionsAfterOnePass(int[] eval, boolean isBot, boolean playersTurn) {
		// 读到行末，检查是否有未结算的连子（右侧/下侧边缘算阻碍）
		if (eval[0] > 0) {
			eval[2] += getConsecutiveSetScore(eval[0], eval[1], isBot == playersTurn);
		}
		// 面向下一行/列重置状态
		eval[0] = 0;
		eval[1] = 2;
	}

	// 此函数返回给定连续棋子集的评分分数。
	// count: 连续多少子, blocks: 被堵了几端
	public static  int getConsecutiveSetScore(int count, int blocks, boolean currentTurn) {
		final int winGuarantee = 1000000;
		// 如果两端都被堵死且少于5子，该连子没有任何价值，返回0。
		if(blocks == 2 && count < 5) return 0;

		switch(count) {
		case 5: {
			// 5子直接获胜
			return WIN_SCORE;
		}
		case 4: {
			// 在轮到该玩家时，4连子必赢（玩家可以在第5个位置直接赢）。
			if(currentTurn) return winGuarantee;
			else {
				// 如果如果是对手回合，如果没有被堵死，4连子保证在下一回合获胜。
				if(blocks == 0) return winGuarantee/4;
				// 如果一端被堵，对手必须堵掉另一端。分数依然较高。
				else return 200;
			}
		}
		case 3: {
			// 3子连珠
			if(blocks == 0) {
				// 两端都没堵。
				// 如果轮到该玩家，接下来的两步内基本必赢。
				if(currentTurn) return 50_000;
				// 如果是对方回合，这也将迫使对方堵掉一端。
				else return 200;
			}
			else {
				// 一端被堵。
				if(currentTurn) return 10;
				else return 5;
			}
		}
		case 2: {
			// 2子连珠
			if(blocks == 0) {
				if(currentTurn) return 7;
				else return 5;
			}
			else {
				return 3;
			}
		}
		case 1: {
			return 1;
		}
		}

		// 超过 5 子的情况
		return WIN_SCORE*2;
	}
}
