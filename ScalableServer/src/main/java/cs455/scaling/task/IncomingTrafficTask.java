package cs455.scaling.task;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import cs455.scaling.server.Server;
import cs455.scaling.threadpool.ThreadPoolManager;
import cs455.scaling.util.HashGenerator;

/**
 * Class representing an <code>IncomingTrafficTask</code> which is a specific type of <code>Task</code>
 * designed to perform the following functions:
 * <ul>
 * <li>Reads incoming data from a given <code>SocketChannel</code>.</li>
 * <li>Generates a SHA-1 hash <code>String</code> representation of the incoming data.</li>
 * <li>Delegates to {@link ThreadPoolManager} for batching a given <code>SocketChannel</code> and SHA-1 hash.</li>
 * </ul>
 */
public class IncomingTrafficTask implements Task {

	private final SocketChannel clientChannel;
	private final ThreadPoolManager poolManager;
	private final SelectionKey key;
	
	/**
	 * Constructor accepting a <code>Client</code> <code>SelectionKey</code> and <code>ThreadPoolManager</code>
	 * @param key <code>SelectionKey</code>
	 * @param poolManager <code>ThreadPoolManager</code>
	 * @throws IOException
	 */
	public IncomingTrafficTask(SelectionKey key, ThreadPoolManager poolManager) throws IOException {
		this.clientChannel = (SocketChannel) key.channel();
		this.poolManager = poolManager;
		this.key = key;
	}

	@Override
	public void execute() {
		
		key.attach(null);
		ByteBuffer clientBuffer = ByteBuffer.allocate(8000);
		int numBytes = 0;
		while (clientBuffer.hasRemaining() && numBytes != -1) {
			try {
				if (clientChannel.isOpen()) {
					numBytes = clientChannel.read(clientBuffer);
				}
				else {
					Server.getInstance().removeClientConnection(clientChannel);
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
		
		if (numBytes == -1) {
			try {
				clientChannel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("Client has been disconnected.");
		}
		else {
			byte[] clientData = clientBuffer.array();
			String serverSha1Hash = HashGenerator.computeSha1Hash(clientData);
			clientBuffer.flip();
			try {
				poolManager.addBatchHash(clientChannel, serverSha1Hash);
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}
		}
	}
}