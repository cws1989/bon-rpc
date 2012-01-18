// Copyright (c) 2012 Chan Wai Shing
//
// This file is part of BON RPC.
//
// BON RPC is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// BON RPC is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with BON RPC.  If not, see <http://www.gnu.org/licenses/>.
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
