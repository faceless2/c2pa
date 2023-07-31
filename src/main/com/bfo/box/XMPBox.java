package com.bfo.box;

import com.bfo.json.*;
import java.io.*;

/**
 * An XMP box is a semi-standardized box holding XMP metadata, which is now defined in
 * ISO16684, although this method of storage for XMP dates from "part 3" of the pre-ISO specifications.
 * @since 5
 */
public class XMPBox extends ExtensionBox {

    static String SUBTYPE = "cbcf7abea997e8429c71999491e3afa";

    private byte[] data;

    @Override protected void read(InputStream in, BoxFactory factory) throws IOException {
        super.read(in, factory);
        data = readFully(in, null, 0, 0);
    }

    @Override protected void write(OutputStream out) throws IOException {
        super.write(out);
        out.write(data);
    }

    /**
     * Return the XMP object stored in this box as a byte array
     * @return the object
     */
    public byte[] data() {
        return (byte[])data.clone();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.setCharAt(sb.length() - 1, ',');
        try {
            String s = new String(data, "UTF-8");
            sb.append("\"xmp\":");
            sb.append(new Json(s));
        } catch (IOException e) {}
        sb.append('}');
        return sb.toString();
    }

}
