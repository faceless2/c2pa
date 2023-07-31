package com.bfo.box;

import com.bfo.json.*;
import java.io.*;

/**
 * A C2PAContainerBox wraps a {@link C2PAStore} inside a "uuid" box, for safe storage inside
 * an ISO BMFF-based file. It is described in the
 * <a href="https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_the_uuid_box_for_c2pa">C2PA Specification</a>.
 * @since 5
 */
public class C2PAContainerBox extends ExtensionBox {

    static String SUBTYPE = "d8fec3d61b0e483c92975828877ec481";

    private int version, padlength;
    private String purpose;
    private long offset;

    /**
     * Create a new C2PAContainerBox
     */
    public C2PAContainerBox() {
        super("uuid", SUBTYPE);
    }

    @Override protected void read(InputStream in, BoxFactory factory) throws IOException {
        super.read(in, factory);
        version = Box.readInt(in);
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c=in.read()) > 0) {
            sb.append((char)c);
        }
        purpose = sb.toString();
        if ("manifest".equals(purpose)) {
            offset = Box.readLong(in);
        }
        add(factory.load(in));
        while (in.read() >= 0) {
            padlength++;
        }
    }

    /**
     * Return the box purpose, eg "manifest" or "merkle"
     * @return the purpose
     */
    public String purpose() {
        if (purpose == null) {
            purpose = "manifest";
        }
        return purpose;
    }

    /**
     * For boxes with a purpose of "manifest", return the absolute file byte offset to the first auxiliary "uuid" C2PA box with purpose equal to "merkle".
     * @return the offset as described or zero if not applicable.
     */
    public long offset() {
        return offset;
    }

    @Override protected void write(OutputStream out) throws IOException {
        super.write(out);
        out.write(version>>24);
        out.write(version>>16);
        out.write(version>>8);
        out.write(version>>0);
        out.write(purpose().getBytes("ISO-8859-1"));
        out.write(0);
        if (purpose().equals("manifest")) {
            out.write((int)(offset>>56));
            out.write((int)(offset>>48));
            out.write((int)(offset>>40));
            out.write((int)(offset>>32));
            out.write((int)(offset>>24));
            out.write((int)(offset>>16));
            out.write((int)(offset>>8));
            out.write((int)(offset));
        }
        out.write(first().getEncoded());
        for (int i=0;i<padlength;i++) {
            out.write(0);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.setCharAt(sb.length() - 1, ',');
        sb.append("\"purpose\":\"" + purpose() + "\"");
        if (version > 0) {
            sb.append(",\"version\":" + version);
        }
        if (offset > 0) {
            sb.append(",\"offset\":" + offset);
        }
        if (padlength > 0) {
            sb.append(",\"padding\":" + padlength);
        }
        sb.append('}');
        return sb.toString();
    }

}
