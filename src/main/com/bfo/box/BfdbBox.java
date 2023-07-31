package com.bfo.box;

import java.io.*;
import java.math.*;

/**
 * ISO19566 AMD-1 "bfdb" box for embedded file metadata
 * @hidden
 */
class BfdbBox extends Box {

    private String mediaType, fileName;
    private boolean external;

    public BfdbBox() {
    }

    public BfdbBox(String mediaType, String fileName, boolean external) {
        super("bfdb");
        if (mediaType == null) {
            throw new IllegalArgumentException("mediaType is null");
        }
        this.mediaType = mediaType;
        this.fileName = fileName;
        this.external = external;
    }

    @Override protected void read(InputStream in, BoxFactory factory) throws IOException {
        int toggles = in.read();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int c;
        while ((c=in.read()) > 0) {
            out.write(c);
        }
        mediaType = new String(out.toByteArray(), "UTF-8");
        if ((toggles & 1) != 0) {        // filename present
            out = new ByteArrayOutputStream();
            while ((c=in.read()) > 0) {
                out.write(c);
            }
            fileName = new String(out.toByteArray(), "UTF-8");
        } else {
            fileName = null;
        }
        external = (toggles & 2) != 0;
    }

    @Override protected void write(OutputStream out) throws IOException {
        int toggles = (external ? 2 : 0) | (fileName != null ? 1 : 0);
        out.write(toggles);
        out.write(mediaType.getBytes("UTF-8"));
        out.write(0);
        if (fileName != null) {
            out.write(fileName.getBytes("UTF-8"));
            out.write(0);
        }
    }

    /**
     * Return the mediaType
     */
    public String mediaType() {
        return mediaType;
    }

    /**
     * Return the fileName, or null if not specified
     */
    public String fileName() {
        return fileName;
    }

    /**
     * Return true if the subsequent bidb box contains a URL rather than data
     */
    public boolean isExternal() {
        return external;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.setCharAt(sb.length() - 1, ',');
        sb.append("\"media_type\":\"");
        sb.append(mediaType());
        sb.append("\",");
        if (fileName() != null) {
            sb.append("\"fileName\":\"");
            sb.append(fileName());
            sb.append("\",");
        }
        if (isExternal()) {
            sb.append("\"external\":true,");
        }
        sb.setCharAt(sb.length() - 1, '}');
        return sb.toString();
    }

}
