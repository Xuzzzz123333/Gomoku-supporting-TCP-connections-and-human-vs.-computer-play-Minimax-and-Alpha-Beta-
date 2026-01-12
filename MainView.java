import java.awt.*;

import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * 主菜单界面：
 * - 人机对战
 * - 联机对战
 * - 退出游戏
 */
public class MainView extends JFrame {

	public MainView() {
		super("五子棋 - 主菜单");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLayout(new BorderLayout());
		setPreferredSize(new Dimension(1300, 900));//首先大小
		setMinimumSize(new Dimension(1000, 700));//最小大小
		setLocationRelativeTo(null);//放置平面中央

		// 创建带有背景图片的面板
		BackgroundPanel backgroundPanel = new BackgroundPanel("/assets/main_menu_bg.jpg");
		backgroundPanel.setLayout(new BorderLayout());

		// 标题面板 (顶部)
		JPanel titlePanel = new JPanel();
		titlePanel.setOpaque(false);//透明设置
		JLabel titleLabel = new JLabel("五子棋游戏", SwingConstants.CENTER);
		titleLabel.setFont(new Font("SansSerif", Font.BOLD, 36));//设置字体
		titleLabel.setForeground(Color.WHITE);//文字颜色
		titleLabel.setBorder(BorderFactory.createEmptyBorder(30, 0, 20, 0));//标题边框
		titlePanel.add(titleLabel);

		// 间隔面板 (中部区域，用于将按钮向下挤压)
		JPanel spacerPanel = new JPanel();
		spacerPanel.setOpaque(false);

		// 创建按钮容器 (居中靠下位置)
		JPanel buttonContainer = new JPanel();
		buttonContainer.setLayout(new GridLayout(3, 1, 0, 15));//3行一列布局，间距15像素
		buttonContainer.setBorder(BorderFactory.createEmptyBorder(10, 0, 50, 0));//边框
		buttonContainer.setOpaque(false); // 设为透明以显示背景图

		// 按钮尺寸设置
		int buttonWidth = 250;
		int buttonHeight = 50;

		Font btnFont = new Font("SansSerif", Font.BOLD, 18);

		JButton btnAi = new JButton("人机对战");
		btnAi.setFont(btnFont);
		btnAi.setPreferredSize(new Dimension(buttonWidth, buttonHeight));
		styleMenuButton(btnAi);
		btnAi.addActionListener(e -> new GameView());

		JButton btnOnline = new JButton("联机对战");
		btnOnline.setFont(btnFont);
		btnOnline.setPreferredSize(new Dimension(buttonWidth, buttonHeight));
		styleMenuButton(btnOnline);
		btnOnline.addActionListener(e -> new OnlineGameView());

		JButton btnExit = new JButton("退出");
		btnExit.setFont(btnFont);
		btnExit.setPreferredSize(new Dimension(buttonWidth, buttonHeight));
		styleMenuButton(btnExit);
		btnExit.addActionListener(e -> System.exit(0));

		buttonContainer.add(btnAi);
		buttonContainer.add(btnOnline);
		buttonContainer.add(btnExit);

		// 南部面板，用于收纳按钮并使其居中
		JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		southPanel.setOpaque(false);
		southPanel.add(buttonContainer);

		backgroundPanel.add(titlePanel, BorderLayout.NORTH);
		backgroundPanel.add(spacerPanel, BorderLayout.CENTER);
		backgroundPanel.add(southPanel, BorderLayout.SOUTH);
		add(backgroundPanel, BorderLayout.CENTER);

		pack();
		setVisible(true);
	}

	private void styleMenuButton(JButton button) {
		button.setOpaque(true);
		button.setBackground(new Color(255, 255, 255, 220)); // 半透明白色背景
		button.setForeground(new Color(25, 25, 112)); // 深蓝色文字
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(70, 130, 180), 2),
			BorderFactory.createEmptyBorder(6, 12, 6, 12)
		));//复合边框
		button.setFocusPainted(false);//禁用焦点绘制

		// 添加鼠标悬停效果
		button.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseEntered(java.awt.event.MouseEvent e) {
				button.setBackground(new Color(70, 130, 180, 200)); // 悬停时变为蓝色
				button.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e) {
				button.setBackground(new Color(255, 255, 255, 220));
				button.setForeground(new Color(25, 25, 112));
			}
		});
	}

	/**
	 * 内部类：用于在面板上绘制背景图片
	 */
	class BackgroundPanel extends JPanel {
		private Image image;

		public BackgroundPanel(String path) {
			try {
				// 尝试通过资源路径加载图片
				image = ImageIO.read(getClass().getResource(path));
			} catch (Exception e) {
				System.err.println("无法加载背景图片: " + path);
			}
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			// 如果图片加载成功，则将其绘制并拉伸到面板大小
			if (image != null) {
				g.drawImage(image, 0, 0, getWidth(), getHeight(), this);
			}
		}
	}

	public static void main(String[] args) {
		// 禁用 Windows DPI 缩放
		System.setProperty("sun.java2d.uiScale", "1.0");
		System.setProperty("sun.java2d.dpiaware", "true");
		SwingUtilities.invokeLater(()->new MainView());
	}
}

