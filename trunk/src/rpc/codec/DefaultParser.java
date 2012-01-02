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
package rpc.codec;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import rpc.codec.exception.InvalidFormatException;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class DefaultParser implements Parser {

    private static final Logger LOG = Logger.getLogger(DefaultParser.class.getName());
    protected InputStream in;
    private int byteRead = 0;
    private final byte[] buffer = new byte[8];

    protected DefaultParser() {
    }

    protected DefaultParser(InputStream inputStream) {
        this.in = inputStream;
    }

    @Override
    public Object parse(byte[] data) throws InvalidFormatException {
        try {
            return read(new ByteArrayInputStream(data));
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public Object read(InputStream inputStream) throws IOException, InvalidFormatException {
        DefaultParser parser = new DefaultParser(inputStream);

        int dataType = inputStream.read();
        switch (dataType) {
            case 1:
                return parser.readMap();
            case 2:
                return parser.readList();
            default:
                throw new InvalidFormatException(String.format("dataType '%1$d' not supported", dataType));
        }
    }

    protected List<Object> readList() throws IOException, InvalidFormatException {
        List<Object> returnList = new ArrayList<Object>();

        int dataType;
        while ((dataType = in.read()) > 0) {
            returnList.add(readItem(dataType));
        }

        return returnList;
    }

    protected Map<Object, Object> readMap() throws IOException, InvalidFormatException {
        Map<Object, Object> returnMap = new HashMap<Object, Object>();

        int dataType;

        dataType = in.read();
        if (dataType == 0) {
            return returnMap;
        } else if (dataType == -1) {
            throw new InvalidFormatException(String.format("Expected to read %1$d bytes but failed", 1, dataType));
        }
        Object elementName = readItem(dataType);

        while ((dataType = in.read()) > 0) {
            returnMap.put(elementName, readItem(dataType));

            dataType = in.read();
            if (dataType == 0) {
                return returnMap;
            } else if (dataType == -1) {
                throw new InvalidFormatException(String.format("Expected to read %1$d bytes but failed", 1, dataType));
            }
            elementName = readItem(dataType);
        }

        if (returnMap.isEmpty()) {
            throw new InvalidFormatException(String.format("Element data not found for key '%1$s'", elementName));
        }

        return returnMap;
    }

    protected Object readItem(int header) throws IOException, InvalidFormatException {
        switch (header) {
            case 1:
                return readMap();
            case 2:
                return readList();
            case 3:
                byteRead = in.read(buffer, 0, 4);
                if (byteRead != 4) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 4, byteRead));
                }
                int intBuffer = ((buffer[0] & 0xff) << 24)
                        | ((buffer[1] & 0xff) << 16)
                        | ((buffer[2] & 0xff) << 8)
                        | (buffer[3] & 0xff);
                return Float.intBitsToFloat(intBuffer);
            case 4:
                byteRead = in.read(buffer, 0, 8);
                if (byteRead != 8) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 8, byteRead));
                }
                long longBuffer = (((long) (buffer[0] & 0xff)) << 56)
                        | (((long) (buffer[1] & 0xff)) << 48)
                        | (((long) (buffer[2] & 0xff)) << 40)
                        | (((long) (buffer[3] & 0xff)) << 32)
                        | ((long) (buffer[4] & 0xff) << 24)
                        | ((buffer[5] & 0xff) << 16)
                        | ((buffer[6] & 0xff) << 8)
                        | (buffer[7] & 0xff);
                return Double.longBitsToDouble(longBuffer);
            case 8:
                return true;
            case 9:
                return false;
            case 10:
                return null;
            case 12:
                byteRead = in.read(buffer, 0, 1);
                if (byteRead != 1) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 1, byteRead));
                }
                int _shortStringLength = buffer[0] & 0xff;

                byte[] _shortStringBuffer = new byte[_shortStringLength];
                byteRead = in.read(_shortStringBuffer, 0, _shortStringLength);
                if (byteRead != _shortStringLength) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", _shortStringLength, byteRead));
                }

                return new String(_shortStringBuffer);
            case 13:
                byteRead = in.read(buffer, 0, 3);
                if (byteRead != 3) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 3, byteRead));
                }
                int _longStringLength = ((buffer[0] & 0xff) << 8) | ((buffer[1] & 0xff) << 8) | (buffer[2] & 0xff);

                byte[] _longStringBuffer = new byte[_longStringLength];
                byteRead = in.read(_longStringBuffer, 0, _longStringLength);
                if (byteRead != _longStringLength) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", _longStringLength, byteRead));
                }

                return new String(_longStringBuffer);
            case 14:
                byteRead = in.read(buffer, 0, 1);
                if (byteRead != 1) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 1, byteRead));
                }
                int _shortBinaryLength = (buffer[0] & 0xff);

                byte[] _shortBinaryBuffer = new byte[_shortBinaryLength];
                byteRead = in.read(_shortBinaryBuffer, 0, _shortBinaryLength);
                if (byteRead != _shortBinaryLength) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", _shortBinaryLength, byteRead));
                }

                return _shortBinaryBuffer;
            case 15:
                byteRead = in.read(buffer, 0, 3);
                if (byteRead != 3) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 3, byteRead));
                }
                int _longBinaryLength = ((buffer[0] & 0xff) << 8) | ((buffer[1] & 0xff) << 8) | (buffer[2] & 0xff);

                byte[] _longBinaryBuffer = new byte[_longBinaryLength];
                byteRead = in.read(_longBinaryBuffer, 0, _longBinaryLength);
                if (byteRead != _longBinaryLength) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", _longBinaryLength, byteRead));
                }

                return _longBinaryBuffer;
            default:
                int type = header & 0x0F;
                switch (type) {
                    case 5:
                        return readShort(header);
                    case 6:
                        return readInt(header);
                    case 7:
                        return readLong(header);
                    case 11:
                        return new Date(readLong(header));
                    default:
                        throw new InvalidFormatException(String.format("Unknown item header: %1$d", header));
                }
        }
    }

    protected short readShort(int header) throws IOException, InvalidFormatException {
        short returnValue = 0;

        int length = ((header >> 4) & 0x07);

        switch (length) {
            case 0:
                byteRead = in.read();
                if (byteRead == -1) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d byte but failed"));
                }
                returnValue |= byteRead;
                break;
            case 1:
                byteRead = in.read(buffer, 0, 2);
                if (byteRead != 2) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 2, byteRead));
                }
                returnValue |= (buffer[0] & 0xff) << 8;
                returnValue |= (buffer[1] & 0xff);
                break;
            default:
                throw new InvalidFormatException(String.format("Unexpected byte array length for 'short' value: %1$d", length));
        }

        if ((header & 0x80) != 0) {
            returnValue |= 0x80 << 8;
        }

        return returnValue;
    }

    protected int readInt(int header) throws IOException, InvalidFormatException {
        int returnValue = 0;

        int length = ((header >> 4) & 0x07);

        switch (length) {
            case 0:
                byteRead = in.read();
                if (byteRead == -1) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d byte but failed"));
                }
                returnValue |= byteRead;
                break;
            case 1:
                byteRead = in.read(buffer, 0, 2);
                if (byteRead != 2) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 2, byteRead));
                }
                returnValue |= (buffer[0] & 0xff) << 8;
                returnValue |= (buffer[1] & 0xff);
                break;
            case 2:
                byteRead = in.read(buffer, 0, 3);
                if (byteRead != 3) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 3, byteRead));
                }
                returnValue |= (buffer[0] & 0xff) << 16;
                returnValue |= (buffer[1] & 0xff) << 8;
                returnValue |= (buffer[2] & 0xff);
                break;
            case 3:
                byteRead = in.read(buffer, 0, 4);
                if (byteRead != 4) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 4, byteRead));
                }
                returnValue |= (buffer[0] & 0xff) << 24;
                returnValue |= (buffer[1] & 0xff) << 16;
                returnValue |= (buffer[2] & 0xff) << 8;
                returnValue |= (buffer[3] & 0xff);
                break;
            default:
                throw new InvalidFormatException(String.format("Unexpected byte array length for 'int' value: %1$d", length));
        }

        if ((header & 0x80) != 0) {
            returnValue |= 0x80 << 24;
        }

        return returnValue;
    }

    protected long readLong(int header) throws IOException, InvalidFormatException {
        long returnValue = 0;

        int length = ((header >> 4) & 0x07);

        switch (length) {
            case 0:
                byteRead = in.read();
                if (byteRead == -1) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d byte but failed"));
                }
                returnValue |= byteRead;
                break;
            case 1:
                byteRead = in.read(buffer, 0, 2);
                if (byteRead != 2) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 2, byteRead));
                }
                returnValue |= (buffer[0] & 0xff) << 8;
                returnValue |= (buffer[1] & 0xff);
                break;
            case 2:
                byteRead = in.read(buffer, 0, 3);
                if (byteRead != 3) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 3, byteRead));
                }
                returnValue |= (buffer[0] & 0xff) << 16;
                returnValue |= (buffer[1] & 0xff) << 8;
                returnValue |= (buffer[2] & 0xff);
                break;
            case 3:
                byteRead = in.read(buffer, 0, 4);
                if (byteRead != 4) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 4, byteRead));
                }
                returnValue |= ((long) buffer[0] & 0xff) << 24;
                returnValue |= (buffer[1] & 0xff) << 16;
                returnValue |= (buffer[2] & 0xff) << 8;
                returnValue |= (buffer[3] & 0xff);
                break;
            case 5:
                byteRead = in.read(buffer, 0, 6);
                if (byteRead != 6) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 6, byteRead));
                }
                returnValue |= ((long) (buffer[0] & 0xff)) << 40;
                returnValue |= ((long) (buffer[1] & 0xff)) << 32;
                returnValue |= ((long) buffer[2] & 0xff) << 24;
                returnValue |= (buffer[3] & 0xff) << 16;
                returnValue |= (buffer[4] & 0xff) << 8;
                returnValue |= (buffer[5] & 0xff);
                break;
            case 7:
                byteRead = in.read(buffer, 0, 8);
                if (byteRead != 8) {
                    throw new InvalidFormatException(String.format("Expected to read %1$d bytes but %2$s byte(s) read", 8, byteRead));
                }
                returnValue |= ((long) (buffer[0] & 0xff)) << 56;
                returnValue |= ((long) (buffer[1] & 0xff)) << 48;
                returnValue |= ((long) (buffer[2] & 0xff)) << 40;
                returnValue |= ((long) (buffer[3] & 0xff)) << 32;
                returnValue |= ((long) buffer[4] & 0xff) << 24;
                returnValue |= (buffer[5] & 0xff) << 16;
                returnValue |= (buffer[6] & 0xff) << 8;
                returnValue |= (buffer[7] & 0xff);
                break;
            default:
                throw new InvalidFormatException(String.format("Unexpected byte array length for 'long' value: %1$d", length));
        }

        if ((header & 0x80) != 0) {
            returnValue |= ((long) 0x80) << 56;
        }

        return returnValue;
    }
}
