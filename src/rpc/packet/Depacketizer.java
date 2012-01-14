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
package rpc.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public abstract class Depacketizer {

    protected final List<DepacketizerListener> listeners;

    public Depacketizer() {
        listeners = Collections.synchronizedList(new ArrayList<DepacketizerListener>());
    }

    public void addListener(DepacketizerListener listener) {
        listeners.add(listener);
    }

    public boolean removeListener(DepacketizerListener listener) {
        return listeners.remove(listener);
    }

    public List<DepacketizerListener> getListeners() {
        return new ArrayList<DepacketizerListener>(listeners);
    }

    public void clearListeners() {
        listeners.clear();
    }

    public abstract void unpack(byte[] b, int offset, int length);
}
