/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.grizzly.http.ajp;

import com.sun.grizzly.tcp.InputBuffer;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;

import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import java.io.EOFException;
import java.io.IOException;
import java.util.Properties;

public class AjpInputBuffer extends InternalInputBuffer {
    private static final byte[] GET_BODY_CHUNK_PACKET;
    
    static {
         byte[] getBodyChunkPayload = new byte[2];
         AjpMessageUtils.putShort(getBodyChunkPayload, 0,
                 (short) AjpConstants.MAX_READ_SIZE);
         
         GET_BODY_CHUNK_PACKET =  AjpMessageUtils.createAjpPacket(
                 AjpConstants.JK_AJP13_GET_BODY_CHUNK,
                    getBodyChunkPayload);
    }

    private String secret;
    private boolean isTomcatAuthentication = true;

    private int thisPacketEnd;
    private int dataPacketRemaining;
    
    public AjpInputBuffer(Request request, int requestBufferSize) {
        super(request, requestBufferSize);
        
        inputStreamInputBuffer = new AjpInputStreamInputBuffer();
    }

    protected void readAjpMessageHeader() throws IOException {
        if (!ensureAvailable(4)) {
            throw new EOFException(sm.getString("iib.eof.error"));
        }
        
        final int magic = AjpMessageUtils.getShort(buf, pos);
        if (magic != 0x1234) {
            throw new IllegalStateException("Invalid packet magic number: " +
                    Integer.toHexString(magic) + " pos=" + pos + "lastValid=" + lastValid + " end=" + end);
        }
        
        final int size = AjpMessageUtils.getShort(buf, pos + 2);
        
        if (size + AjpConstants.H_SIZE > AjpConstants.MAX_PACKET_SIZE) {
            throw new IllegalStateException("The message is too large. " +
                    (size + AjpConstants.H_SIZE) + ">" +
                    AjpConstants.MAX_PACKET_SIZE);
        }
        
        pos += 4;
        
        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
        ajpRequest.setLength(size);
    }

    @Override
    public void parseRequestLine() throws IOException {
        readAjpPacketPayload();
    }

    @Override
    public void parseHeaders() throws IOException {
        super.parseHeaders();
        
        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
        if (ajpRequest.getType() == AjpConstants.JK_AJP13_FORWARD_REQUEST) {
            parseAjpHttpHeaders();
        }        
    }

    
    @Override
    public boolean parseHeader() throws IOException {
        return false;
    }

    protected void readAjpPacketPayload() throws IOException {
        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
        final int length = ajpRequest.getLength();
        
        if (!ensureAvailable(length)) {
            throw new EOFException(sm.getString("iib.eof.error"));
        }

        
        thisPacketEnd = pos + length;
        
        ajpRequest.setType(ajpRequest.isForwardRequestProcessing() ?
                AjpConstants.JK_AJP13_DATA : buf[pos++] & 0xFF);
    }

    protected void parseAjpHttpHeaders() {
        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
        pos = end = AjpMessageUtils.decodeForwardRequest(buf, pos,
                isTomcatAuthentication, ajpRequest);

        if (secret != null) {
            final String epSecret = ajpRequest.getSecret();
            if (epSecret == null || !secret.equals(epSecret)) {
                throw new IllegalStateException("Secret doesn't match");
            }
        }

        final long contentLength = request.getContentLength();
        if (contentLength > 0) {
            // if content-length > 0 - the first data chunk will come immediately,
            ajpRequest.setContentBytesRemaining((int) contentLength);
            ajpRequest.setExpectContent(true);
        } else {
            // content-length == 0 - no content is expected
            ajpRequest.setExpectContent(false);
        }
    }
    
    /**
     * Configure Ajp Filter using properties.
     * We support following properties: request.useSecret, request.secret, tomcatAuthentication.
     *
     * @param properties
     */
    public void configure(final Properties properties) {
        if (Boolean.parseBoolean(properties.getProperty("request.useSecret"))) {
            secret = Double.toString(Math.random());
        }

        secret = properties.getProperty("request.secret", secret);
        isTomcatAuthentication = Boolean.parseBoolean(properties.getProperty("tomcatAuthentication", "true"));
    }

    
    final void getBytesToMB(final MessageBytes messageBytes) {
        pos = AjpMessageUtils.getBytesToByteChunk(buf, pos, messageBytes);
    }

    @Override
    protected final boolean fill() throws IOException {
        throw new IllegalStateException("Should never be called for AJP");
    }

    private boolean ensureAvailable(final int length) throws IOException {
        final int available = available();
        
        if (available >= length) {
            return true;
        }
        
        if (pos == lastValid) {
            pos = lastValid = end;
        }
        
        // check if we can read the required amount of data to this buf
        if (pos + length > buf.length) {
            // we have to shift available data
            
            // check if we can reuse this array as target
            final byte[] targetArray;
            final int offs;
            if (end + length <= buf.length) { // we can use this array
                targetArray = buf;
                offs = end;
            } else { // this array is not big enough, we have to create a new one
                targetArray =
                        new byte[Math.max(buf.length, AjpConstants.MAX_PACKET_SIZE * 2)];
                offs = 0;
            }
            
            if (available > 0) {
                System.arraycopy(buf, pos, targetArray, offs, available);
            }
            
            buf = targetArray;
            pos = offs;
            lastValid = pos + available;
        }

        while (lastValid - pos < length) {
            final int readNow = inputStream.read(buf, lastValid, buf.length - lastValid);
            if (readNow < 0) {
                return false;
            }
            
            lastValid += readNow;
        }
        
        return true;
    }

    @Override
    public void endRequest() throws IOException {
        pos = thisPacketEnd;
        end = 0;
    }
    
    @Override
    public void recycle() {
        thisPacketEnd = 0;
        dataPacketRemaining = 0;
        
        super.recycle();
    }

    void parseDataChunk() throws IOException {
        pos = thisPacketEnd; // Reset pos to the next message
        
        readAjpMessageHeader();
        readAjpPacketPayload();
        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
        
        if (ajpRequest.getLength() != available()) {
            throw new IllegalStateException("Unexpected: read more data than JK_AJP13_DATA has");
        }

        if (ajpRequest.getType() != AjpConstants.JK_AJP13_DATA) {
            throw new IllegalStateException("Expected message type is JK_AJP13_DATA");
        }
        
        // Skip the content length field - we know the size from the packet header
        pos += 2;
        
        dataPacketRemaining = available();
    }
    
    private void requestDataChunk() throws IOException {
        ((AjpOutputBuffer) request.getResponse().getOutputBuffer())
                .writeEncodedAjpMessage(GET_BODY_CHUNK_PACKET, 0,
                GET_BODY_CHUNK_PACKET.length, true);
    }
    
    // ------------------------------------- InputStreamInputBuffer Inner Class

        
    /**
     * This class is an AJP input buffer which will read its data from an input
     * stream.
     */
    protected class AjpInputStreamInputBuffer 
        implements InputBuffer {


        /**
         * Read bytes into the specified chunk.
         */
        public int doRead(ByteChunk chunk, Request req ) 
            throws IOException {
    
            final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
            if (!ajpRequest.isExpectContent()) {
                return -1;
            }

            if (ajpRequest.getContentBytesRemaining() <= 0) {
                return -1;
            }

            if (dataPacketRemaining <= 0) {
                requestDataChunk();
                parseDataChunk();
            }

            final int length = dataPacketRemaining;
            chunk.setBytes(buf, pos, dataPacketRemaining);
            pos = pos + dataPacketRemaining;
            ajpRequest.setContentBytesRemaining(
                    ajpRequest.getContentBytesRemaining() - dataPacketRemaining);
            dataPacketRemaining = 0;
            
            return length;
        }
    }
}
