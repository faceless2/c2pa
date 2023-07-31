package com.bfo.box;

import java.io.*;
import java.util.*;

class UsefulByteArrayOutputStream extends OutputStream {
    
    private byte[] buffer;
    private int pos;

    UsefulByteArrayOutputStream() {
        this.buffer = new byte[8192];
    }

    @Override public void write(int v) {
        if (pos == buffer.length) {
            byte[] b = new byte[buffer.length + (buffer.length>>1)];
            System.arraycopy(buffer, 0, b, 0, buffer.length);
            buffer = b;
        }
        buffer[pos++] = (byte)v;
    }

    @Override public void write(byte[] buf, int off, int len) {
        if (pos + len >= buffer.length) {
            byte[] b = new byte[Math.max(pos + len, buffer.length + (buffer.length>>1))];
            System.arraycopy(buffer, 0, b, 0, buffer.length);
            buffer = b;
        }
        System.arraycopy(buf, off, buffer, pos, len);
        pos += len;
    }

    public void write(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        int l;
        while ((l=in.read(buf, 0, buf.length)) >= 0) {
            write(buf, 0, l);
        }
    }

    public int tell() {
        return pos;
    }

    public void seek(int v) {
        pos = v;
    }

    public void writeInt(int v) {
        write(v>>24);
        write(v>>16);
        write(v>>8);
        write(v);
    }

    public byte[] toByteArray() {
        return toByteArray(0, pos);
    }

    public byte[] toByteArray(int off, int len) {
        byte[] b = new byte[len];
        System.arraycopy(buffer, off, b, 0, len);
        return b;
    }

    public void writeTo(OutputStream out) throws IOException {
        out.write(buffer, 0, pos);
    }

    public InputStream toInputStream() {
        return toInputStream(0, pos);
    }

    public InputStream toInputStream(int off, int len) {
        return new ByteArrayInputStream(buffer, off, len);
    }

    public String toString() {
        return "{ubaos: pos="+pos+"}";
    }

}
