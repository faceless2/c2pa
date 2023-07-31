package com.bfo.box;

import java.io.*;

/**
 * This superclass of Box handles the standad "extension" format defined in
 * ISO14496-12 s 11. It's simply a box where the first 16 bytes have a
 * further type (called "uuid" in ISO14496, "content-type" in ISO19566,
 * "guid" in 16684). We'll call it "subtype" and we will trim from 16 hex
 * digits to four letters if it ends with the "standard" suffix defined
 * in ISO14496.
 * @since 5
 */
public class ExtensionBox extends Box {

    private String subtype;

    /**
     * Create a new uninitialized box, for loading. Don't call this constructor
     */
    public ExtensionBox() {
    }

    /**
     * Create a new ExtensionBox. Both parameter are erquired
     * @param type a four letter alphanumeric code, often "uuid"
     * @param subtype either a four letter alphanumeric code or a sixteen-letter hex code
     */
    public ExtensionBox(String type, String subtype) {
        super(type);
        this.subtype = subtype;
    }

    @Override protected void read(InputStream in, BoxFactory factory) throws IOException {
        StringBuilder q = new StringBuilder(4);
        byte[] b = Box.readFully(in, new byte[16], 0, 16);
        subtype = hex(b);
        // Special case - we can trim this ending
        if (subtype.endsWith("00110010800000aa00389b71")) {
            q.setLength(0);
            q.append((char)b[0]);
            q.append((char)b[1]);
            q.append((char)b[2]);
            q.append((char)b[3]);
            subtype = q.toString();
        }
    }

    @Override protected void write(OutputStream out) throws IOException {
        if (subtype.length() == 4) {
            out.write(subtype.charAt(0));
            out.write(subtype.charAt(1));
            out.write(subtype.charAt(2));
            out.write(subtype.charAt(3));
            out.write(new byte[] { (byte)0x00, (byte)0x11, (byte)0x00, (byte)0x10, (byte)0x80, (byte)0x00, (byte)0x00, (byte)0xaa, (byte)0x00, (byte)0x38, (byte)0x9b, (byte)0x71 });
        } else {
            out.write(hex(subtype));
        }
    }

    /**
     * Return the subtype of this box, either a 4-character alpha-numeric
     * string (if the type is a "standard" subtype) or a 32-character hex string
     * @return the subtype
     */
    public String subtype() {
        return subtype;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.setCharAt(sb.length() - 1, ',');
        sb.append("\"subtype\":\"");
        sb.append(subtype());
        sb.append("\"}");
        return sb.toString();
    }

}
