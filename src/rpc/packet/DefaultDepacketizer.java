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

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import rpc.codec.CodecFactory;
import rpc.codec.Parser;
import rpc.codec.exception.InvalidFormatException;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class DefaultDepacketizer extends Depacketizer {

    private static final Logger LOG = Logger.getLogger(DefaultDepacketizer.class.getName());
    protected final Parser parser;
    // header
    protected boolean packetStarted = false;
    protected int _headerRead = 0;
    // packet length
    protected final byte[] _packetLengthBuffer = new byte[16];
    protected int _packetLengthBufferRead = 0;
    protected int _packetLength = -1;
    // packet type, request type id, request id
    protected final byte[] _infoBuffer = new byte[6];
    protected int _infoBufferRead = 0;
    protected boolean _isRespond = false;
    protected int _requestTypeId = -1;
    protected int _requestId = -1;
    // packet content
    protected byte[] _content = null;
    protected int _contentRead = 0;
    // crc
    protected final byte[] _crcBuffer = new byte[4];
    protected int _crcBufferRead = 0;
    protected final CRC32 _crc32 = new CRC32();
    protected boolean _crcMatched = false;

    public DefaultDepacketizer() {
        parser = CodecFactory.getParser();
    }

    protected void reset() {
        packetStarted = false;
        _headerRead = 0;

        _packetLengthBufferRead = 0;
        _packetLength = -1;

        _infoBufferRead = 0;
        _isRespond = false;
        _requestTypeId = -1;
        _requestId = -1;

        _content = null;
        _contentRead = 0;

        _crcBufferRead = 0;
        _crc32.reset();
        _crcMatched = false;
    }

    @Override
    public void unpack(byte[] b, int offset, int length) {
        int start = offset, end = offset + length;

        //<editor-fold defaultstate="collapsed" desc="read packet header">
        if (!packetStarted) {
            while (start < end) {
                try {
                    if (b[start] == DefaultPacketizer.packetHeader[0]) {
                        _headerRead = 1;
                    } else if (_headerRead == 1 && b[start] == DefaultPacketizer.packetHeader[1]) {
                        reset();
                        packetStarted = true;
                        break;
                    } else {
                        _headerRead = 0;
                    }
                } finally {
                    start++;
                }
            }
        }
        // packet header should have been received
        if (start >= end) {
            return;
        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="read packetLength">
        if (_packetLength == -1) {
            if (_packetLengthBufferRead <= 1) {
                start = fillPacketLengthBuffer(b, start, end, 2);
            }

            if (_packetLengthBufferRead >= 2) {
                do {
                    if ((_packetLengthBuffer[0] & 128) == 0) {
                        int _packetLength_1 = 0;
                        _packetLength_1 |= (_packetLengthBuffer[0] & 0xff) << 8;
                        _packetLength_1 |= (_packetLengthBuffer[1] & 0xff);

                        if (_packetLength_1 > 255) {
                            start = fillPacketLengthBuffer(b, start, end, 4);
                            if (_packetLengthBufferRead < 4) {
                                break;
                            }

                            int _packetLength_2 = 0;
                            _packetLength_2 |= (_packetLengthBuffer[2] & 0xff);
                            _packetLength_2 |= (_packetLengthBuffer[3] & 0xff) << 8;
                            if (_packetLength_1 != _packetLength_2) {
                                refeed(b, start, end - start);
                                return;
                            }
                        }

                        _packetLength = _packetLength_1;
                    } else {
                        start = fillPacketLengthBuffer(b, start, end, 12);
                        if (_packetLengthBufferRead < 12) {
                            break;
                        }

                        int _packetLength_1 = 0;
                        _packetLength_1 |= (_packetLengthBuffer[0] & 127) << 24;
                        _packetLength_1 |= (_packetLengthBuffer[1] & 0xff) << 16;
                        _packetLength_1 |= (_packetLengthBuffer[2] & 0xff) << 8;
                        _packetLength_1 |= (_packetLengthBuffer[3] & 0xff);

                        if (_packetLength_1 <= 32767) {
                            refeed(b, start, end - start);
                            return;
                        }

                        int _packetLength_2 = 0;
                        _packetLength_2 |= (_packetLengthBuffer[4] & 0xff);
                        _packetLength_2 |= (_packetLengthBuffer[5] & 0xff) << 8;
                        _packetLength_2 |= (_packetLengthBuffer[6] & 0xff) << 16;
                        _packetLength_2 |= (_packetLengthBuffer[7] & 0xff) << 24;
                        if (_packetLength_1 != _packetLength_2) {
                            refeed(b, start, end - start);
                            return;
                        }

                        _packetLength_2 = 0;
                        _packetLength_2 |= (_packetLengthBuffer[8] & 0xff);
                        _packetLength_2 |= (_packetLengthBuffer[9] & 0xff) << 8;
                        _packetLength_2 |= (_packetLengthBuffer[10] & 0xff) << 16;
                        _packetLength_2 |= (_packetLengthBuffer[11] & 0xff) << 24;
                        if (_packetLength_1 != _packetLength_2) {
                            refeed(b, start, end - start);
                            return;
                        }

                        if (_packetLength_1 > 65535) {
                            start = fillPacketLengthBuffer(b, start, end, 16);
                            if (_packetLengthBufferRead < 16) {
                                break;
                            }

                            _packetLength_2 = 0;
                            _packetLength_2 |= (_packetLengthBuffer[12] & 0xff);
                            _packetLength_2 |= (_packetLengthBuffer[13] & 0xff) << 8;
                            _packetLength_2 |= (_packetLengthBuffer[14] & 0xff) << 16;
                            _packetLength_2 |= (_packetLengthBuffer[15] & 0xff) << 24;
                            if (_packetLength_1 != _packetLength_2) {
                                refeed(b, start, end - start);
                                return;
                            }
                        }

                        _packetLength = _packetLength_1;
                    }
                } while (false);
            }
        }
        // packet length might not have been read if packet data is not enough
        if (start >= end) {
            // packet data not enough or just enough
            return;
        }
        // packet length should have been read
        // if packet data is corrupted, packet length might be wrong if packet length is short (<=255)
        if (_packetLength <= 0) {
            // packet data is corrupted
            refeed(b, start, end - start);
            return;
        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="read isRespond, requestTypeId and requestId">
        if (_requestTypeId == -1) {
            start = fillInfoBuffer(b, start, end);
            if (_infoBufferRead == 6) {
                _infoBufferRead = 0;

                _isRespond = (_infoBuffer[_infoBufferRead] & 128) != 0;

                _requestTypeId = 0;
                if ((_infoBuffer[_infoBufferRead] & 64) == 0) {
                    _requestTypeId = (_infoBuffer[_infoBufferRead++] & 63);
                } else {
                    _requestTypeId |= (_infoBuffer[_infoBufferRead++] & 63) << 8;
                    _requestTypeId |= (_infoBuffer[_infoBufferRead++] & 0xff);
                }

                _requestId = 0;
                if ((_infoBuffer[_infoBufferRead] & 128) == 0) {
                    _requestId |= (_infoBuffer[_infoBufferRead++] & 0xff) << 8;
                    _requestId |= (_infoBuffer[_infoBufferRead++] & 0xff);
                } else {
                    if ((_infoBuffer[_infoBufferRead] & 64) == 0) {
                        _requestId |= (_infoBuffer[_infoBufferRead++] & 63) << 16;
                        _requestId |= (_infoBuffer[_infoBufferRead++] & 0xff) << 8;
                        _requestId |= (_infoBuffer[_infoBufferRead++] & 0xff);
                    } else {
                        _requestId |= (_infoBuffer[_infoBufferRead++] & 63) << 24;
                        _requestId |= (_infoBuffer[_infoBufferRead++] & 0xff) << 16;
                        _requestId |= (_infoBuffer[_infoBufferRead++] & 0xff) << 8;
                        _requestId |= (_infoBuffer[_infoBufferRead++] & 0xff);
                    }
                }

                _crc32.update(_infoBuffer, 0, _infoBufferRead);
                _content = new byte[_packetLength];

                if (_infoBufferRead != 6) {
                    int _newBufferRead = fillContent(_infoBuffer, _infoBufferRead, 6);
                    if (_contentRead == _packetLength) {
                        _crc32.update(_content);
                    }
                    if (_newBufferRead != 6) {
                        _newBufferRead = fillCRCBuffer(_infoBuffer, _newBufferRead, 6);
                    }
                    if (_newBufferRead != 6) {
                        LOG.log(Level.SEVERE, "infoBuffer not consumed");
                    }
                }
            }
        }
        // data might not have been read if packet data is not enough
        if (start >= end) {
            // packet data not enough or just enough
            return;
        }
        // request type id and request id should have been read
        if (_requestTypeId < 0 || _requestTypeId > 16383 || _requestId <= 0 || _requestId > 1073741823) {
            // packet data is corrupted
            refeed(b, start, end - start);
            return;
        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="read content">
        if (_contentRead != _packetLength) {
            start = fillContent(b, start, end);

            if (_contentRead == _packetLength) {
                _crc32.update(_content);
            }
        }
        // packet content might not have been read if packet data is not enough
        if (start >= end) {
            // packet data not enough or just enough
            return;
        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="read crc32">
        if (_crcBufferRead != 4) {
            start = fillCRCBuffer(b, start, end);

            if (_crcBufferRead == 4) {
                packetStarted = false;

                long crc32 = 0;
                crc32 |= (_crcBuffer[0] & 0xff);
                crc32 |= (_crcBuffer[1] & 0xff) << 8;
                crc32 |= (_crcBuffer[2] & 0xff) << 16;
                crc32 |= ((long) (_crcBuffer[3] & 0xff)) << 24;

                long crc32Value = _crc32.getValue();
                _crcMatched = crc32 == crc32Value;
            }
        }
        // crc data might not have been read if packet data is not enough
        // can't use (start >= end) check here, because there is crc match checking below
        if (_crcBufferRead != 4) {
            return;
        }
        //</editor-fold>

        if (_crcMatched) {
            Object content = null;
            try {
                content = parser.parse(_content);
            } catch (InvalidFormatException ex) {
                LOG.log(Level.SEVERE, null, ex);
                refeed(b, start, end - start);
                return;
            }
            Packet packet = new Packet(_isRespond, _requestTypeId, _requestId, content);
            synchronized (listeners) {
                for (DepacketizerListener listener : listeners) {
                    listener.packetReceived(packet);
                }
            }

            packetStarted = false;
            if (start < end) {
                unpack(b, start, end - start);
                return;
            }
        } else {
            // crc failed, put the packet (except the header) back for reading again
            refeed(b, start, end - start);
            return;
        }
    }

    protected void refeed(byte[] b, int offset, int length) {
        int __packetLengthBufferRead = _packetLengthBufferRead;
        int __infoBufferRead = _infoBufferRead;
        int __packetLength = _packetLength;
        int __crcBufferRead = _crcBufferRead;
        byte[] __packetLengthBuffer = null;
        byte[] __infoBuffer = null;
        byte[] __content = null;
        byte[] __crcBuffer = null;
        do {
            if (__packetLengthBufferRead <= 0) {
                break;
            }
            __packetLengthBuffer = new byte[__packetLengthBufferRead];
            System.arraycopy(_packetLengthBuffer, 0, __packetLengthBuffer, 0, __packetLengthBufferRead);

            if (__infoBufferRead <= 0) {
                break;
            }
            __infoBuffer = new byte[__infoBufferRead];
            System.arraycopy(_infoBuffer, 0, __infoBuffer, 0, __infoBufferRead);

            if (__packetLength <= 0) {
                break;
            }
            __content = new byte[__packetLength];
            System.arraycopy(_content, 0, __content, 0, __packetLength);

            if (__crcBufferRead <= 0) {
                break;
            }
            __crcBuffer = new byte[__crcBufferRead];
            System.arraycopy(_crcBuffer, 0, __crcBuffer, 0, __crcBufferRead);
        } while (false);

        reset();

        do {
            if (__packetLengthBufferRead <= 0 || __packetLengthBuffer == null) {
                break;
            }
            unpack(__packetLengthBuffer, 0, __packetLengthBufferRead);

            if (__infoBufferRead <= 0 || __infoBuffer == null) {
                break;
            }
            unpack(__infoBuffer, 0, __infoBufferRead);

            if (__packetLength <= 0 || __content == null) {
                break;
            }
            unpack(__content, 0, __packetLength);

            if (__crcBufferRead <= 0 || __crcBuffer == null) {
                break;
            }
            unpack(__crcBuffer, 0, __crcBufferRead);
        } while (false);

        if (length > 0) {
            unpack(b, offset, length);
        }
    }

    protected int fillPacketLengthBuffer(byte[] b, int start, int end, int fillSize) {
        // b != null
        // start >= 0, end >= 0, end <= start
        // _packetLengthBufferRead >= 0, _packetLengthBufferRead <= fillSize
        // fillSize >= 0, fillSize <= 16
        int maxLength = end - start;
        int byteToRead = fillSize - _packetLengthBufferRead;
        if (maxLength < byteToRead) {
            byteToRead = maxLength;
        }
        if (byteToRead <= 0) {
            return start;
        }
        System.arraycopy(b, start, _packetLengthBuffer, _packetLengthBufferRead, byteToRead);
        _packetLengthBufferRead += byteToRead;
        return start + byteToRead;
    }

    protected int fillInfoBuffer(byte[] b, int start, int end) {
        // b != null
        // start >= 0, end >= 0, end <= start
        // _packetLengthBufferRead >= 0, _packetLengthBufferRead <= 6
        int maxLength = end - start;
        int byteToRead = 6 - _infoBufferRead;
        if (maxLength < byteToRead) {
            byteToRead = maxLength;
        }
        if (byteToRead <= 0) {
            return start;
        }
        System.arraycopy(b, start, _infoBuffer, _infoBufferRead, byteToRead);
        _infoBufferRead += byteToRead;
        return start + byteToRead;
    }

    protected int fillContent(byte[] b, int start, int end) {
        // b != null
        // start >= 0, end >= 0, end <= start
        // _contentRead >= 0, _contentRead <= _packetLength
        // _packetLength >= 0
        // _contentRead <= _packetLength
        // _content != null
        // _content.length == _packetLength
        int maxLength = end - start;
        int byteToRead = _packetLength - _contentRead;
        if (maxLength < byteToRead) {
            byteToRead = maxLength;
        }
        if (byteToRead <= 0) {
            return start;
        }
        System.arraycopy(b, start, _content, _contentRead, byteToRead);
        _contentRead += byteToRead;
        return start + byteToRead;
    }

    protected int fillCRCBuffer(byte[] b, int start, int end) {
        // b != null
        // start >= 0, end >= 0, end <= start
        // _crcBufferRead >= 0, _crcBufferRead <= 4
        int maxLength = end - start;
        int byteToRead = 4 - _crcBufferRead;
        if (maxLength < byteToRead) {
            byteToRead = maxLength;
        }
        if (byteToRead <= 0) {
            return start;
        }
        System.arraycopy(b, start, _crcBuffer, _crcBufferRead, byteToRead);
        _crcBufferRead += byteToRead;
        return start + byteToRead;
    }
}
