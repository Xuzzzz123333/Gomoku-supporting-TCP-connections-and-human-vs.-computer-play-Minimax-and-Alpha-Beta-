import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 简单的基于行的 TCP 对等端（Peer）。
 * - sendLine(): 发送一行数据（自动刷新缓冲区）
 * - startReader(): 启动一个守护线程异步读取接收到的数据行
 */
public class TcpPeer {

	public interface Listener {
		void onLine(String line);
		void onClosed(String reason);
	}

	private final Socket socket;
	private final BufferedReader in;
	private final PrintWriter out;

	private Thread readerThread;
	private volatile boolean closed = false;

	public TcpPeer(Socket socket) throws IOException {
		this.socket = socket;
		this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
		this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
	}

	public synchronized void startReader(Listener listener) {
		if (readerThread != null) return;

		readerThread = new Thread(() -> {
			try {
				String line;
				while (!closed && (line = in.readLine()) != null) {
					listener.onLine(line);
				}
				if (!closed) {
					closeInternal("对方断开连接");
					listener.onClosed("对方断开连接");
				}
			} catch (IOException ex) {
				if (!closed) {
					closeInternal("网络错误：" + ex.getMessage());
					listener.onClosed("网络错误：" + ex.getMessage());
				}
			}
		}, "tcp-peer-reader");
		readerThread.setDaemon(true);
		readerThread.start();
	}

	public synchronized void sendLine(String line) {
		if (closed) return;
		out.println(line);
	}

	public synchronized void close() {
		if (closed) return;
		closeInternal("本地主动断开");
	}

	private void closeInternal(String reason) {
		closed = true;
		try { socket.close(); } catch (IOException ignored) {}
		try { in.close(); } catch (IOException ignored) {}
		out.close();
	}
}

