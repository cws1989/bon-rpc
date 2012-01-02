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
package rpc.codec.exception;

/**
 * Thrown when there is any data type not supported by the generator or parser.
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class UnsupportedDataTypeException extends Exception {

    private static final long serialVersionUID = 198902111L;

    public UnsupportedDataTypeException() {
        super();
    }

    public UnsupportedDataTypeException(String message) {
        super(message);
    }

    public UnsupportedDataTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsupportedDataTypeException(Throwable cause) {
        super(cause);
    }
}
