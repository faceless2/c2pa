package com.bfo.box;

import java.io.*;

/**
 * ISO14496-12 4.2
 */
abstract class FullBox extends Box {

    protected int version, flags;

    @Override protected void read(InputStream in, BoxFactory factory) throws IOException {
        int vf = readInt(in);
        version = vf >> 24;
        flags = vf & 0xFFFFFF;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.setCharAt(sb.length() - 1, ',');
        sb.append("\"version\":");
        sb.append(version);
        sb.append(",\"flags\":");
        sb.append(flags);
        sb.append('}');
        return sb.toString();
    }

}
