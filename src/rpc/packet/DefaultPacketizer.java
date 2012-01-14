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
import java.util.Arrays;
import java.util.zip.CRC32;
import rpc.codec.CodecFactory;
import rpc.codec.exception.UnsupportedDataTypeException;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class DefaultPacketizer implements Packetizer {

    protected static final byte[] packetHeader;

    static {
        packetHeader = new byte[2];
        packetHeader[0] = (byte) 1;
        packetHeader[1] = (byte) 7;
    }

    @Override
    public byte[] pack(boolean isRespond, int requestTypeId, int requestId, Object[] args) throws UnsupportedDataTypeException {
        //<editor-fold defaultstate="collapsed" desc="prepare requestId and requestTypeId">
        int sendBufferIndex = 0;
        byte[] sendBuffer = new byte[6];

        // first bit is the packet type, 0 for send, 1 for respond
        sendBuffer[sendBufferIndex] = isRespond ? (byte) 128 : (byte) 0;

        if (requestTypeId <= 63) {
            sendBuffer[sendBufferIndex++] |= (byte) requestTypeId;
        } else {
            // max: 16383
            sendBuffer[sendBufferIndex] |= (byte) (requestTypeId >> 8);
            sendBuffer[sendBufferIndex++] |= 64;
            sendBuffer[sendBufferIndex++] = (byte) requestTypeId;
        }

        if (requestId != 0) {
            if (requestId <= 32767) {
                sendBuffer[sendBufferIndex++] = (byte) (requestId >> 8);
                // first bit is 0
                sendBuffer[sendBufferIndex++] = (byte) requestId;
            } else if (requestId <= 4194303) {
                sendBuffer[sendBufferIndex] = (byte) (requestId >> 16);
                sendBuffer[sendBufferIndex++] |= 128;
                // first bit is 1, second bit is 0
                sendBuffer[sendBufferIndex++] = (byte) (requestId >> 8);
                sendBuffer[sendBufferIndex++] = (byte) requestId;
            } else {
                // max: 1073741823
                sendBuffer[sendBufferIndex] = (byte) (requestId >> 24);
                sendBuffer[sendBufferIndex++] |= 192;
                // first bit is 1, second bit is 1
                sendBuffer[sendBufferIndex++] = (byte) (requestId >> 16);
                sendBuffer[sendBufferIndex++] = (byte) (requestId >> 8);
                sendBuffer[sendBufferIndex++] = (byte) requestId;
            }
        }
        //</editor-fold>

        byte[] content = CodecFactory.getGenerator().generate(args == null ? new ArrayList<Object>() : Arrays.asList(args));
        if (content == null) {
            throw new UnsupportedDataTypeException("error occurred when packing the data");
        }

        //<editor-fold defaultstate="collapsed" desc="prepare packet">
        int packetLength = content.length;
        int packetLengthByteLength = 0;
        if (packetLength <= 255) {
            packetLengthByteLength = 2;
        } else if (packetLength <= 32767) {
            packetLengthByteLength = 4;
        } else if (packetLength <= 65535) {
            packetLengthByteLength = 12;
        } else {
            packetLengthByteLength = 16;
        }
        int byteLength = 2 + packetLengthByteLength + sendBufferIndex + content.length + 4;

        int packetBufferIndex = 0;
        byte[] packetBuffer = new byte[byteLength];

        System.arraycopy(packetHeader, 0, packetBuffer, packetBufferIndex, 2);
        packetBufferIndex += 2;

        if (packetLength <= 32767) {
            packetBuffer[packetBufferIndex++] = (byte) (packetLength >> 8);
            packetBuffer[packetBufferIndex++] = (byte) (packetLength);

            // redundance for error checking
            if (packetLength > 255) {
                packetBuffer[packetBufferIndex++] = (byte) (packetLength);
                packetBuffer[packetBufferIndex++] = (byte) (packetLength >> 8);
            }
        } else {
            packetBuffer[packetBufferIndex] = (byte) (packetLength >> 24);
            packetBuffer[packetBufferIndex++] |= 128;
            packetBuffer[packetBufferIndex++] = (byte) (packetLength >> 16);
            packetBuffer[packetBufferIndex++] = (byte) (packetLength >> 8);
            packetBuffer[packetBufferIndex++] = (byte) (packetLength);

            // redundance for error checking
            int repeatTimes = packetLength <= 65535 ? 2 : 3;
            while (repeatTimes-- > 0) {
                packetBuffer[packetBufferIndex++] = (byte) (packetLength);
                packetBuffer[packetBufferIndex++] = (byte) (packetLength >> 8);
                packetBuffer[packetBufferIndex++] = (byte) (packetLength >> 16);
                packetBuffer[packetBufferIndex++] = (byte) (packetLength >> 24);
            }
        }

        System.arraycopy(sendBuffer, 0, packetBuffer, packetBufferIndex, sendBufferIndex);
        packetBufferIndex += sendBufferIndex;
        System.arraycopy(content, 0, packetBuffer, packetBufferIndex, content.length);
        packetBufferIndex += content.length;

        CRC32 crc32 = new CRC32();
        crc32.update(sendBuffer, 0, sendBufferIndex);
        crc32.update(content);

        long crc32Value = crc32.getValue();
        packetBuffer[packetBufferIndex++] = (byte) (crc32Value);
        packetBuffer[packetBufferIndex++] = (byte) (crc32Value >> 8);
        packetBuffer[packetBufferIndex++] = (byte) (crc32Value >> 16);
        packetBuffer[packetBufferIndex++] = (byte) (crc32Value >> 24);
        //</editor-fold>

        return packetBuffer;
    }
}
