import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

/**
 * 棋盘渲染组件，基于网格交点（非格子单元）进行绘制。
 * 坐标系统：
 * - boardSize = 每边的交点数
 * - cellLength = 相邻交点之间的间距
 * - margin = 边距，cellLength / 2，使交点能美观地从边框缩进
 */
public class BoardCanvas extends JPanel {
	private final int sideLength;//棋盘边长
	private final int boardSize;//每条边的交点数
	private final int cellLength;//交点间距
	private final int margin;//边距

	private BufferedImage image;//缓冲图像
	private Graphics2D g2D;//绘制
	private boolean isAIThinking = false;//标记ai是否在思考

	public BoardCanvas(int sideLength, int boardSize) {
		this.sideLength = sideLength;
		this.boardSize = boardSize;
		this.cellLength = sideLength / boardSize;
		this.margin = cellLength / 2;

		setPreferredSize(new Dimension(sideLength, sideLength));
		resetBoard();
	}

	/** 清空棋盘背景并重新绘制网格线 */
	public final void resetBoard() {
		// 使用 TYPE_INT_RGB (无 alpha) 以避免与透明度相关的渲染问题
		image = new BufferedImage(sideLength, sideLength, BufferedImage.TYPE_INT_RGB);
		g2D = (Graphics2D) image.getGraphics();

		// 背景（木质色）
		g2D.setColor(new Color(245, 222, 179));
		g2D.fillRect(0, 0, sideLength, sideLength);

		// 网格线 - 使用 2 像素宽度以应对 Windows 的 DPI 缩放
		// 如果使用 1 像素线条，DPI 缩放可能会导致某些线条消失
		g2D.setColor(new Color(80, 80, 80));  // 深灰色，视觉效果更好

		int start = margin;
		int end = sideLength - margin;
		int gridLength = end - start;//网格长度

		// 绘制纵向线条
		for (int i = 0; i < boardSize; i++) {
			int p = start + i * cellLength;//横坐标
			g2D.fillRect(p, start, 2, gridLength + 1);//以矩形模拟线条
		}

		// 绘制横向线条
		for (int i = 0; i < boardSize; i++) {
			int p = start + i * cellLength;
			g2D.fillRect(start, p, gridLength + 1, 2);
		}

		// 边框（更粗一些）
		g2D.setColor(Color.BLACK);
		g2D.setStroke(new BasicStroke(3f));
		g2D.drawRect(start - 1, start - 1, gridLength + 2, gridLength + 2);
		g2D.setStroke(new BasicStroke(1f));

		repaint();
	}

	/**
	 * 将像素坐标映射到最近的交点索引。
	 * 如果点击位置超出棋盘范围太远，则返回 -1。
	 */
	public int getRelativePos(int x) {
		int start = margin;
		int end = sideLength - margin;

		// 允许一定的容错范围，即使用户稍微点出界也能识别
		int tol = cellLength / 2;
		if (x < start - tol || x > end + tol) return -1;

		double v = (x - start) / (double) cellLength;
		int idx = (int) Math.round(v);//四舍五入
		if (idx < 0) idx = 0;
		if (idx >= boardSize) idx = boardSize - 1;
		return idx;
	}

	private int pix(int idx) {
		return margin + idx * cellLength;
	}//转换为像素坐标

	public void drawStone(int posX, int posY, boolean black) {
		if (posX < 0 || posY < 0 || posX >= boardSize || posY >= boardSize) return;

		int cx = pix(posX);
		int cy = pix(posY);
		int d = (int) Math.round(cellLength * 0.85);
		int x = cx - d / 2;
		int y = cy - d / 2;

		BasicStroke oldStroke = (BasicStroke) g2D.getStroke();
		Object oldAA = g2D.getRenderingHint(RenderingHints.KEY_ANTIALIASING);

		// 在填充圆时关闭抗锯齿，防止棋子边缘与网格线颜色渗漏
		g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		// 棋子绘制：保持边缘纤细，确保网格线在落子后不会显得变粗。
		g2D.setColor(black ? Color.black : Color.white);
		g2D.fillOval(x, y, d, d);

		// 重新开启抗锯齿以获得平滑的轮廓
		g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// 仅为白棋绘制细轮廓（黑棋不需要轮廓）。
		if (!black) {
			g2D.setColor(Color.black);
			g2D.setStroke(new BasicStroke(1f));
			g2D.drawOval(x, y, d, d);
		}

		g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);//抗锯齿设置
		g2D.setStroke(oldStroke);//描边设置
		repaint();
	}


	/** 根据矩阵重新绘制所有内容（0 空，1 白，2 黑） */
	public void redrawFromMatrix(int[][] matrix) {
		resetBoard();
		for (int row = 0; row < matrix.length; row++) {
			for (int col = 0; col < matrix[row].length; col++) {
				int v = matrix[row][col];
				if (v == 1) drawStone(col, row, false);
				else if (v == 2) drawStone(col, row, true);
			}
		}
		repaint();
	}

	public void printWinner(int winner) {
		String text = winner == 2 ? "YOU WON!" : (winner == 1 ? "OPPONENT WON!" : "TIED!");

		g2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2D.setFont(new Font(g2D.getFont().getName(), Font.BOLD, 56));
		FontMetrics metrics = g2D.getFontMetrics(g2D.getFont());//字体度量

		int x = (sideLength - metrics.stringWidth(text)) / 2;
		int y = sideLength / 2;//计算居中位置

		// 绘制文字阴影轮廓
		g2D.setColor(Color.black);
		g2D.drawString(text, x - 2, y);
		g2D.drawString(text, x + 2, y);
		g2D.drawString(text, x, y - 2);
		g2D.drawString(text, x, y + 2);

		g2D.setColor(winner == 2 ? Color.green : (winner == 1 ? Color.red : Color.blue));//对手红，自己绿，平局蓝
		g2D.drawString(text, x, y);

		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		Graphics2D gg = (Graphics2D) g.create();
		// 使用邻近插值来防止缩放时细线消失
		gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		gg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
		// 以原始尺寸绘制图像，避免缩放引起的问题
		gg.drawImage(image, 0, 0, null);
		if (isAIThinking) printThinking(gg);
		gg.dispose();
	}

	private void printThinking(Graphics2D gg) {
		String text = "Thinking...";
		gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		gg.setFont(new Font(gg.getFont().getName(), Font.PLAIN, 48));
		FontMetrics metrics = gg.getFontMetrics(gg.getFont());

		int x = (sideLength - metrics.stringWidth(text)) / 2;
		int y = sideLength / 2;

		gg.setColor(new Color(255, 0, 0, 150));
		gg.drawString(text, x, y);
	}

	public void attachListener(MouseListener listener) {
		addMouseListener(listener);
	}

	public void setAIThinking(boolean flag) {
		isAIThinking = flag;
		repaint();
	}
}

