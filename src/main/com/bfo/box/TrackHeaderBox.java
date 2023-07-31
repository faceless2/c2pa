package com.bfo.box;

import java.io.*;

/**
 * Represents a "tkhd" Track Header box, defined in ISO14496-12 section 8.3.2.
 * Nothing to do with C2PA, this is simply useful to extract metadata about media dimensions etc.
 * @since 5
 */
public class TrackHeaderBox extends FullBox {

    long ctime, mtime, duration;
    int trackid, layer, alternate_group, volume;
    float width, height;
    int[] matrix;

    @Override protected void read(InputStream in, BoxFactory factory) throws IOException {
        super.read(in, factory);
        // See ISO14496-12
        if (version == 1) {
            ctime = readLong(in);
            mtime = readLong(in);
            trackid = readInt(in);
            readInt(in);
            duration = readLong(in);
        } else {
            ctime = readInt(in) & 0xFFFFFFFFl;
            mtime = readInt(in) & 0xFFFFFFFFl;
            trackid = readInt(in);
            readInt(in);
            duration = readInt(in) & 0xFFFFFFFFl;
        }
        readInt(in);
        readInt(in);
        layer = readShort(in);
        alternate_group = readShort(in);
        volume = readShort(in);
        readShort(in);
        matrix = new int[9];
        for (int i=0;i<9;i++) {
            matrix[i] = readInt(in);
        }
        // These are fixed point, but we'll round
        width = readFixed16(in);
        height = readFixed16(in);
    }

    /**
     * Return the video width, rounded to the nearest pixel
     * @return the width
     */
    public int width() {
        return Math.round(width);
    }

    /**
     * Return the video height, rounded to the nearest pixel
     * @return the height
     */
    public int height() {
        return Math.round(height);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.setCharAt(sb.length() - 1, ',');
        sb.append("\"ctime\":");
        sb.append(ctime);
        sb.append(",\"mtime\":");
        sb.append(mtime);
        sb.append(",\"trackid\":");
        sb.append(trackid);
        sb.append(",\"duration\":");
        sb.append(duration);
        sb.append(",\"layer\":");
        sb.append(layer);
        sb.append(",\"alternate_group\":");
        sb.append(alternate_group);
        sb.append(",\"volume\":");
        sb.append(volume);
        sb.append(",\"width\":");
        sb.append(width);
        sb.append(",\"height\":");
        sb.append(height);
        sb.append('}');
        return sb.toString();
    }

}
