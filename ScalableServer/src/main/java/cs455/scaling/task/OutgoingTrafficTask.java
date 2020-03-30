package cs455.scaling.task;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import cs455.scaling.server.Server;

/**
 * Class representing an <code>OutgoingTrafficTask</code> which is a specific type of <code>Task</code> which delegates
 * the sending of hashes to <code>Client</code>s to the <code>Server</code>. 
 */
public class OutgoingTrafficTask implements Task {
	
	private final ConcurrentHashMap<SocketChannel, String> hashBatchMap;
	
	/**
	 * Constructor which accepts a <code>Map</code> containing the batched SHA-1 hashes for a given number 
	 * of <code>SocketChannel</code>s.
	 * @param hashBatchMap <code>ConcurrentHashMap</code> containing the batch map
	 */
	public OutgoingTrafficTask(ConcurrentHashMap<SocketChannel, String> hashBatchMap) {
		this.hashBatchMap = hashBatchMap;
	}

	@Override
	public void execute() {
		
		for (Entry<SocketChannel, String> hashEntrySet : hashBatchMap.entrySet()) {
		    try {
		    	Server.getInstance().writeToClient(hashEntrySet.getKey(), hashEntrySet.getValue());
		    } catch (IOException ioe) {
			System.out.println(ioe.getMessage());
		    }
		}
	}
}
