package com.bfo.box;

import java.io.*;
import java.math.*;

/**
 * ISO19566 A.3 "jumd"
 * @hidden
 */
class JumdBox extends ExtensionBox {
    private Integer id;
    private String label;
    private boolean requestable;
    private byte[] signature;
    private byte[] salt;

    public JumdBox() {
    }

    public JumdBox(String type, String label) {
        this(type, label, Integer.MIN_VALUE);
    }

    public JumdBox(String subtype, String label, int id) {
        super("jumd", subtype);
        this.requestable = true;
        for (int i=0;i<label.length();i++) {
            int c = label.codePointAt(i);
            // if (c < 0x1f || (c >= 0x7f && c <= 0x9f) || c == '/' || c == ';' || c == '?' || c == ':' || c == '#')    // official list from ISO19566
            if (c < 0x1f || (c >= 0x7f && c <= 0x9f) || c == '/' || c == ';' || c == '?' || c == '#' || (c >= 0xd800 && c <= 0xdfff) || c == 0xfffe || c == 0xffff || Character.getType(c) == Character.FORMAT) {
                throw new IllegalArgumentException("label has invalid character " + (c > 0x30 && c <= 0x7f ? ("\"" + ((char)c) + "\"") : Integer.toHexString(c)));
            }
        }
        this.label = label;
        this.id = id < 0 || id > 65535 ? null : Integer.valueOf(id);
    }

    @Override protected void read(InputStream in, BoxFactory factory) throws IOException {
        super.read(in, factory);
        int toggles = in.read();
        requestable = (toggles & 1) == 1;
        if ((toggles & 2) == 2) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int c;
            while ((c=in.read()) > 0) {
                out.write(c);
            }
            label = new String(out.toByteArray(), "UTF-8");
        } else {
            label = null;
        }
        if ((toggles & 4) != 0) {   // ID present
            id = readShort(in);
        } else {
            id = null;
        }
        if ((toggles & 8) != 0) {   // SHA256 Signature present
            signature = new byte[32];
            for (int i=0;i<signature.length;i++) {
                signature[i] = (byte)in.read();
            }
        } else {
            signature = null;
        }
        if ((toggles & 16) != 0) {
            // Will be in JUMBF (ISO 19566-5:2022/3)
            // See https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_hashing_jumbf_boxes
            int saltlen = readInt(in);
            int salttype = readInt(in);
            if (typeToString(salttype).equals("c2sh")) {
                salt = new byte[saltlen - 8];
                for (int i=0;i<salt.length;i++) {
                    salt[i] = (byte)in.read();
                }
            } else {
                throw new IOException("salt not c2sh in " + this);
            }
        }
    }

    @Override protected void write(OutputStream out) throws IOException {
        super.write(out);
        int toggles = 0;
        if (requestable) {
            toggles |= 1;
        }
        if (label != null) {
            toggles |= 2;
        }
        if (id != null) {
            toggles |= 4;
        }
        if (signature != null) {
            toggles |= 8;
        }
        if (salt != null) {
            toggles |= 16;
        }
        out.write(toggles);
        if (label != null) {
            out.write(label.getBytes("UTF-8"));
            out.write(0);
        }
        if (id != null) {
            out.write(id.intValue() >> 8);
            out.write(id.intValue());
        }
        if (signature != null) {
            out.write(signature);
        }
        if (salt != null) {
            int len = salt.length + 8;
            out.write(len>>24);
            out.write(len>>16);
            out.write(len>>8);
            out.write(len>>0);
            out.write('c');
            out.write('2');
            out.write('s');
            out.write('h');
            out.write(salt);
        }
    }

    /**
     * Return the label, or null if its not specified
     */
    public String label() {
        return label;
    }

    /**
     * Return the box signature, or null if its not specified
     */
    public byte[] signature() {
        return signature == null ? null : (byte[])signature.clone();
    }

    /** 
     * Return the box salt, or null if its not specified
     */
    public byte[] salt() {
        return salt == null ? null : (byte[])salt.clone();
    }

    /**
     * Return the box id, or null if its not specified
     */
    public Integer id() {
        return id;
    }

    /**
     * Return true if this box is requestable
     */
    public boolean isRequestable() {
        return requestable && label != null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.setCharAt(sb.length() - 1, ',');
        if (label != null) {
            sb.append("\"label\":\"");
            sb.append(label());
            sb.append("\",");
        }
        if (id() != null) {
            sb.append("\"id\":\"");
            sb.append(id());
            sb.append("\",");
        }
        if (signature != null) {
            sb.append("\"signature\":\"");
            sb.append(new BigInteger(1, signature).toString(16));
            sb.append("\",");
        }
        if (salt != null) {
            sb.append("\"salt\":\"");
            sb.append(new BigInteger(1, salt).toString(16));
            sb.append("\",");
        }
        sb.setCharAt(sb.length() - 1, '}');
        return sb.toString();
    }
}
