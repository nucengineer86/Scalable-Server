package cs455.scaling.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import cs455.scaling.task.IncomingTrafficTask;
import cs455.scaling.threadpool.ThreadPoolManager;

/**
 * <code>Server</code> class used to be the central point of the system. 
 * This class has been designed to be a Singleton and performs the following functions:
 * <ul>
 * <li>Establishes a <code>ServerSocketChannel</code> connection whereby incoming <code>Client</code>s can connect to.</li>
 * <li>Continuously monitors the system for connections and performs <code>Client</code> registration.</li>
 * <li>Instantiates a {@link ThreadPoolManager} to initialize worker threads for performing <code>Task</code>s.</li>
 * <li>Send data back to all <code>Client</code> connections.</li>
 * </ul>
 */
public final class Server {

	private static final Server INSTANCE = new Server();
	private static Set<SocketChannel> channelSet = new HashSet<>();
	private static AtomicLong messagesSent = new AtomicLong(0);
	private static ConcurrentHashMap<SocketChannel, List<Integer>> perClientMessageCount = new ConcurrentHashMap<>();
	private static final String EOL = System.lineSeparator();
	
	/**
	 * Private constructor to prevent instantiation
	 */
	private Server() {}
	
	/**
	 * Main method for <code>Server</code> setup and execution
	 * @param args <code>String[]</code> containing the port number to bind, thread pool size, 
	 * batch size for <code>Task</code> batching, and a time variable used to control the 
	 * processing of <code>Task</code> batches.
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		
		Selector serverSelector = Selector.open();
		ServerSocketChannel serverSocket = ServerSocketChannel.open();
		serverSocket.bind(new InetSocketAddress(Integer.parseInt(args[0])));
		serverSocket.configureBlocking(false);
		serverSocket.register(serverSelector, SelectionKey.OP_ACCEPT);
		
		int poolSize = Integer.parseInt(args[1]);
		assert poolSize > 0;
		int batchSize = Integer.parseInt(args[2]);
		assert batchSize > 0;
		float batchTime = Float.parseFloat(args[3]);
		assert batchTime > 0;
		
		ThreadPoolManager poolManager = new ThreadPoolManager(poolSize, batchSize);
		poolManager.initializeWorkerThreads();

		Timer throughPutTimer = new Timer();
		throughPutTimer.scheduleAtFixedRate(new ClientThroughputTimer(), 0, 20000);
		
		Timer batchTimer = new Timer();
		float batchTimeMilli = batchTime * 1000;
		batchTimer.scheduleAtFixedRate(new BatchTimerTask(batchTime, poolManager), 0, (long) batchTimeMilli);
			
		while (true) {
			
			if (serverSelector.selectNow() == 0) continue;
			Set<SelectionKey> selectedKeys = serverSelector.selectedKeys();
			Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
			while (keyIterator.hasNext()) {
				
				SelectionKey key = keyIterator.next();
				if (key.isValid() == false) {
					continue;
				}
				
				if (key.isAcceptable()) {
					registerChannel(key, serverSelector, serverSocket);
				}
				
				if (key.isReadable() && key.attachment() == null) {
					processClientChannelData(poolManager, key);
				}
				keyIterator.remove();
			}
		}
	}
	
	/**
	 * Get singleton <code>Server</code> instance.
	 * @return <code>Server</code>
	 */
	public static Server getInstance() {
		return INSTANCE;
	}
	
	/**
	 * Write re-computed SHA-1 hashes back to all connected <code>Client</codes.
	 * @param channel <code>Client</code> <code>SocketChannel</code>
	 * @param hash SHA-1 hash
	 * @throws IOException
	 */
	public synchronized void writeToClient(SocketChannel channel, String hash) throws IOException {
		
    	ByteBuffer buffer = ByteBuffer.wrap(hash.getBytes());
    	if (channel.isOpen()) {
        	while (buffer.hasRemaining()) {
        		channel.write(buffer);
        	}
        	messagesSent.incrementAndGet();
        	perClientMessageCount.computeIfAbsent(channel, count -> new ArrayList<>()).add(1);
    	}
    	else {
    		System.out.println("channel has been disconnected");
    		channelSet.remove(channel);
    		perClientMessageCount.remove(channel);
    	}
	}
	
	public synchronized void removeClientConnection(SocketChannel clientChannel) {
		channelSet.remove(clientChannel);
		perClientMessageCount.remove(clientChannel);
	}
	
	/**
	 * Establishes a new <code>Client</code> connection
	 * @param key <code>SelectionKey</code>
	 * @param serverSelector <code>Selector</code>
	 * @param serverSocket <code>ServerSocketChannel</code>
	 * @throws IOException
	 */
	private static void registerChannel(SelectionKey key, Selector serverSelector, ServerSocketChannel serverSocket) throws IOException {
		
		SocketChannel clientChannel = serverSocket.accept();
		clientChannel.configureBlocking(false);
		clientChannel.register(serverSelector, SelectionKey.OP_READ);
		channelSet.add(clientChannel);
		System.out.println("A new client has been registered.");
	}
	
	/**
	 * Creates a new {@link IncomingTrafficTask} for a <code>Client</code> connection that has readable data.
	 * @param poolManager <code>ThreadPoolManager</code>
	 * @param key <code>SelectionKey</code>
	 * @throws IOException
	 */
	private static void processClientChannelData(ThreadPoolManager poolManager, SelectionKey key) throws IOException {

		key.attach(new Object());
		try {
			poolManager.addTask(new IncomingTrafficTask(key, poolManager));
		} catch (InterruptedException ie) {
			ie.printStackTrace();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
	
	/**
	 * <code>TimerTask</code> used to check whether or not a batch should be added to the queue of 
	 * <code>Task</code>s. This timer gets executed at a fixed rate of <var>batchTime</var>
	 * and checks to see whether or not a batch has been added to the queue or not. If not, 
	 * the <code>ThreadPoolManager</code> will add the batch to the queue. 
	 */
	private static class BatchTimerTask extends TimerTask {

		private final float batchTime;
		private final ThreadPoolManager poolManager;
		private BatchTimerTask(float batchTime, ThreadPoolManager poolManager) {
			this.batchTime = batchTime;
			this.poolManager = poolManager;
		}
		
		@Override
		public void run() {
			Instant thisInstant = Instant.now();
			Instant lastBatchInstant = poolManager.getElapsedTimeSinceBatchRemoved();
			if (lastBatchInstant != null) {
			    Duration elapsedTimeSinceBatch = Duration.between(lastBatchInstant, thisInstant);
			    if (elapsedTimeSinceBatch.getSeconds() >= batchTime) {
			    	try {
						poolManager.addBatchTaskToQueue();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
			    }
			}
			else {
				try {
					poolManager.addBatchTaskToQueue();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * <code>TimerTask</code> used to display the following throughput statistics to the command line:
	 * <ul>
	 * <li>Total message count</li>
	 * <li>Mean throughput per-client</li>
	 * <li>Number of active <code>Client</code> connections</li>
	 * <li>Standard deviation of per-client throughput</li>
	 * </ul>
	 */
	private static class ClientThroughputTimer extends TimerTask {

		private ClientThroughputTimer() {}
		
		@Override
		public void run() {
			
			float messageSum = 0.0f;
			for (List<Integer> messageCount : perClientMessageCount.values()) {
				float size = messageCount.size();
				float perTwentySecondSum = size / 20;
				messageSum += perTwentySecondSum;
			}

			long numActiveChannels = channelSet.size();
			
			if (numActiveChannels > 0) {
				float perClientMean = messageSum / numActiveChannels;
				float perClientSumOfSquares = 0.0f;
				for (List<Integer> messageCount : perClientMessageCount.values()) {
					perClientSumOfSquares += (float) Math.pow(((float) messageCount.size() / 20) - perClientMean, 2);
				}
				float perClientSigma = (float) Math.sqrt(perClientSumOfSquares / (numActiveChannels - 1));
				System.out.println("Number of messages sent: " + (float) messagesSent.get() / 20 + EOL +
	                               "Number of Active Client Connections: " + numActiveChannels + EOL +
                                   "Mean Per Client Throughput: " + perClientMean + EOL + 
	                               "Std. Deviation of Per-client Throughput: " + perClientSigma);}
			else {
	            System.out.println("Number of messages sent: " + messagesSent.get() + EOL +
		                           "Number of Active Client Connections: " + numActiveChannels);
			}	
			perClientMessageCount.clear();
			messagesSent = new AtomicLong(0);
	    }
    }
}