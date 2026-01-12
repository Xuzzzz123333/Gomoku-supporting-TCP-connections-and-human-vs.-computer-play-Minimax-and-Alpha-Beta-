import javax.swing.SwingUtilities;

public class MainClass {
	public static void main(String[] args) {
		System.setProperty("sun.java2d.uiScale", "1.0");// 禁用 Windows 的 DPI 缩放，防止网格线渲染出现虚影或错位
		// 强制 Java 以 1:1 的像素比例进行渲染
		System.setProperty("sun.java2d.dpiaware", "true");
		//启用 DPI 感知，让 Java 识别系统 DPI 设置.
		SwingUtilities.invokeLater(()->new MainView());
		//启动EDT线程，用于swing组件更新。
	}
}

