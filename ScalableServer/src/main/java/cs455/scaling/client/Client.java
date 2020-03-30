package cs455.scaling.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import cs455.scaling.util.HashGenerator;

/**
 * Client class designed to perform the following functions:
 * <ul>
 * <li>Open a <code>SocketChannel</code> connection to the {@link Server}.</li>
 * <li>Continuously generate and send 8kB payloads to the <code>Server</code>.</li>
 * <li>Write the number of total messages sent and received every 20 seconds.</li>
 * </ul>
 */
public class Client {

	private static SocketChannel channel;
	private static ByteBuffer buffer;
	private static LinkedList<String> sha1List;
	private static final int PAYLOAD_SIZE = 8000;
	private static AtomicInteger messageSentCounter = new AtomicInteger(0);
	private static ClientReceiverThread receiver;
	
	/**
	 * Main method for <code>Client</code> execution
	 * @param args <p><code>String[]</code> containg the <code>Server</code> host, port, and
	 * factor for rate at which messages are sent.</p>
	 */
	public static void main(String[] args) {
		
		try {
			String serverHost = args[0];
			int serverPort = Integer.parseInt(args[1]);
			channel = SocketChannel.open(new InetSocketAddress(serverHost, serverPort));
			buffer = ByteBuffer.allocate(PAYLOAD_SIZE);
			sha1List = new LinkedList<>();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		
		receiver = new ClientReceiverThread(channel);
		Thread receiverThread = new Thread(receiver);
		receiverThread.start();
		
		Timer sendTimer = new Timer();
		sendTimer.scheduleAtFixedRate(new ClientTimer(receiver), 0, 20000);
		
		while (true) {
			buffer = generatePayload(new byte[PAYLOAD_SIZE]);
			try {
				while (buffer.hasRemaining()) {
					channel.write(buffer);
				}
				messageSentCounter.incrementAndGet();
				Thread.sleep(1000/Integer.parseInt(args[2]));
				buffer.clear();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}
		}
	}

	/**
	 * Generate random 8kB payload and ensure its uniqueness.
	 * @param payload 8kB byte[]
	 * @return <code>ByteBuffer</code> containing the 8kB payload.
	 */
    private static ByteBuffer generatePayload(byte[] payload) {

	    Random random = new Random();
	    random.nextBytes(payload);
	    
	    String sha1String = HashGenerator.computeSha1Hash(payload);
	    
	    if (!sha1List.contains(sha1String)) {
	    	sha1List.add(sha1String);
	    	receiver.addHash(sha1String);
	    }
	    else {
	    	generatePayload(new byte[PAYLOAD_SIZE]);
	    }
	    return ByteBuffer.wrap(payload);
    }
    
    /**
     * <code>TimerTask</code> used to print out <code>Client</code> throughput statistics every 20 seconds such as the 
     * number of messages sent and received.
     */
    private static class ClientTimer extends TimerTask {

    	private ClientReceiverThread receiver;
    	private ClientTimer(ClientReceiverThread receiver) {
    		this.receiver = receiver;
    	}
    	
		@Override
		public void run() {
			System.out.println("Total Sent Count: " + messageSentCounter + " Total Received Count: " + receiver.getReceivedMessageCount());
			messageSentCounter = new AtomicInteger(0);
			receiver.resetReceivedMessageCount();
		}
    }
}