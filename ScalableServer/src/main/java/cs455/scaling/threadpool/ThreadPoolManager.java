package cs455.scaling.threadpool;

import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import cs455.scaling.task.OutgoingTrafficTask;
import cs455.scaling.task.Task;

/**
 * Class representing a <code>ThreadPoolManager</code> which manages a fixed number of threads, all of which 
 * serve the purpose of executing <code>Task</code>s. 
 */
public class ThreadPoolManager {

	private final LinkedBlockingQueue<Task> taskQueue;
	private final int batchSize;
	private final int poolSize;
	private static Instant batchRemovalTime;
	private final Map<SocketChannel, String> hashBatchMap = new LinkedHashMap<>();

	/**
	 * Constructor which accepts the size of the thread pool and batches
	 * @param poolSize thread pool size
	 * @param batchSize batch size
	 */
	public ThreadPoolManager(int poolSize, int batchSize) {

		taskQueue = new LinkedBlockingQueue<>();
		this.poolSize = poolSize;
		this.batchSize = batchSize;
	}
	
	/**
	 * Start all worker threads in the pool.
	 */
	public void initializeWorkerThreads() {
		for (int i = 0; i < poolSize; i++) {
			WorkerThread workerThread = new WorkerThread();
			workerThread.start();
		}
	}
	
	/**
	 * Add a <code>Task</code> to the queue of <code>Task</code>s to be performed by the worker threads.
	 * @param poolTask <code>Task</code>
	 * @throws InterruptedException
	 */
	public void addTask(Task poolTask) throws InterruptedException {
		taskQueue.put(poolTask);
    }
	
	/**
	 * Get the {@link Instant} since a batch was last removed, i.e. added to the <code>Task</code> queue.
	 * @return <code>Instant</code>
	 */
	public Instant getElapsedTimeSinceBatchRemoved() {
		return batchRemovalTime;
	}
	
	/**
	 * Add a batch <code>Task</code> to the queue. This method is to <em><b>only</em></b> be used
	 * if a user-defined time has elapsed since a batch was added to the queue.
	 * @throws InterruptedException
	 */
	public void addBatchTaskToQueue() throws InterruptedException {
		
		synchronized (hashBatchMap) {
			if (!hashBatchMap.isEmpty()) {
				addTask(new OutgoingTrafficTask(new ConcurrentHashMap<>(hashBatchMap)));
				hashBatchMap.clear();
			}
		}
	}
	
	/**
	 * Add a <code>Task</code> batch to the queue. 
	 * @param channel <code>Client</code> <code>SocketChannel</code>
	 * @param hash SHA-1 hash
	 * @throws InterruptedException
	 */
	public void addBatchHash(SocketChannel channel, String hash) throws InterruptedException {

		synchronized (hashBatchMap) {
			if (hashBatchMap.size() == batchSize) {
				addTask(new OutgoingTrafficTask(new ConcurrentHashMap<>(hashBatchMap)));
				batchRemovalTime = Instant.now();
				hashBatchMap.clear();
			}
			else {
				hashBatchMap.put(channel, hash);
			}
		}
	}
	
	/**
	 * Class representing a <code>WorkerThread</code> whose job is to perform <code>Task</code>s as they become available
	 * in the queue. 
	 */
	private class WorkerThread extends Thread {

		private WorkerThread() {}
		
		public void run() {
			
			Task workerTask;
			
			while (true) {
				try {
					workerTask = taskQueue.take();
					workerTask.execute();
					Thread.sleep(5000);
				} catch (InterruptedException re) {
					System.out.println(re.getMessage());
				} 
			}
		}
	}
}