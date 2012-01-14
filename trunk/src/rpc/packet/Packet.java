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

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class Packet {

    protected final boolean respond;
    protected final int requestTypeId;
    protected final int requestId;
    protected final Object content;

    protected Packet(boolean isRespond, int requestTypeId, int requestId, Object content) {
        this.respond = isRespond;
        this.requestTypeId = requestTypeId;
        this.requestId = requestId;
        this.content = content;
    }

    public boolean isRespond() {
        return respond;
    }

    public int getRequestTypeId() {
        return requestTypeId;
    }

    public int getRequestId() {
        return requestId;
    }

    /**
     * Get the packet content. Note do not change the content in the object.
     * @return the packet content
     */
    public Object getContent() {
        return content;
    }
}
