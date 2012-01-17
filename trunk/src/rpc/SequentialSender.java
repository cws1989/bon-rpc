package rpc;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class SequentialSender {

    protected AtomicLong sequenceIdCounter;
    protected long currentSequenceId;
    protected final Map<Long, Runnable> taskList;

    public SequentialSender() {
        sequenceIdCounter = new AtomicLong(0L);
        currentSequenceId = 0;
        taskList = new HashMap<Long, Runnable>();
    }

    public long acquireSequenceId() {
        return sequenceIdCounter.getAndIncrement();
    }

    public long getCurrentSequenceId() {
        return currentSequenceId;
    }

    public synchronized void send(long sequenceId, Runnable task) {
        if (sequenceId == currentSequenceId) {
            task.run();
            currentSequenceId++;

            Runnable _task = null;
            while ((_task = taskList.remove(currentSequenceId)) != null) {
                _task.run();
                currentSequenceId++;
            }
        } else if (sequenceId > currentSequenceId && sequenceId < sequenceIdCounter.get()) {
            taskList.put(sequenceId, task);
        } else {
            // may log
            return;
        }
    }
}
