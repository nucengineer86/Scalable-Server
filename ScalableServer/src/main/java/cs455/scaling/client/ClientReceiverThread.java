package cs455.scaling.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Client receiver class used to continuously monitor incoming messages sent from the {@link Server}.
 */
public class ClientReceiverThread implements Runnable {

	private SocketChannel channelSocket;
	private static final int BUFFER_SIZE = 40;
	private int messageReceivedCounter = 0;
	private LinkedList<String> sha1List;
	
	/**
	 * Constructor which accepts the <code>SocketChannel</code> from which messages are received.
	 * @param channelSocket <code>SocketChannel</code>
	 */
	public ClientReceiverThread(SocketChannel channelSocket) {
		this.channelSocket = channelSocket;
		sha1List = new LinkedList<>();
	}
	
	@Override
	public void run() {
		
		while (true) {
			ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
			int bytesRead = 0;
			while (buffer.hasRemaining() && bytesRead != -1) {
				try {
					bytesRead = channelSocket.read(buffer);
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}
			buffer.rewind();
			verifyServerHash(new String(buffer.array()));
			messageReceivedCounter += 1;
			buffer.clear();
		}
	}
	
	/**
	 * Store <code>Client</code> generated SHA-1 hash which is used to validate 
	 * the SHA-1 hash received from the <code>Server</code>.
	 * @param sha1Hash the SHA-1 hash
	 */
	public void addHash(String sha1Hash) {
		sha1List.add(sha1Hash);
	}
	
	/**
	 * Get the number of messages received.
	 * @return number of messages received.
	 */
	public int getReceivedMessageCount() {
		return messageReceivedCounter;
	}
	
	/**
	 * Reset the number of messages received. This
	 * method should only be called within the <code>ClientTimerTask</code>
	 * which executes every 20 seconds.
	 */
	public synchronized void resetReceivedMessageCount() {
		messageReceivedCounter = 0;
	}
	
	/**
	 * Verify that the SHA-1 hash from the <code>Server</code> matches the 
	 * SHA-1 hash generating and sent by the <code>Client</code>.
	 * @param serverSha1 SHA-1 hash from <code>Server</code>
	 */
    private void verifyServerHash(String serverSha1) {
    	
    	ListIterator<String> iterator = sha1List.listIterator();
    	while (iterator.hasNext()) {
    		String clientSha1 = iterator.next();
    		if (clientSha1.equals(serverSha1)) {
    			sha1List.remove(serverSha1);
    			break;
    		}
    	}
    }
}