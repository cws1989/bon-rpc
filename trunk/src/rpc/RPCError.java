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

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public enum RPCError {

    REMOTE_CONNECTION_METHOD_NOT_REGISTERED((short) 0),
    REMOTE_CONNECTION_METHOD_INSTANCE_NOT_REGISTERED((short) 1),
    REMOTE_CONNECTION_SEQUENTIAL_ID_NOT_REGISTERED((short) 2),
    REMOTE_METHOD_INVOKE_ERROR((short) 3),
    RESPOND_ID_UPDATE_FAILED((short) 4);
    protected final short value;

    RPCError(short value) {
        this.value = value;
    }

    public short getValue() {
        return value;
    }

    public static RPCError getRPCError(short value) {
        RPCError[] errors = RPCError.values();
        for (RPCError error : errors) {
            if (error.getValue() == value) {
                return error;
            }
        }
        return null;
    }
}
