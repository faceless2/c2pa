package com.bfo.box;

import com.bfo.json.*;
import java.util.*;
import java.io.*;
import java.math.BigInteger;

/**
 * <p>
 * A general class for ISO base media boxes, eg M4V, M4A, quicktime, as defined in ISO14496,
 * and also JUMBox as defined in ISO19566.
 * This is the format of many modern media types (video/mp4, audio/aac, image/jp2) and is
 * also used for <a href="https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html">C2PA</a>.
 * </p>
 * <p>Each box is encoded like so:</p>
 * <ul>
 *  <li><code>bytes 0-3</code> - length of this box, including the length field and type, 0 for "to the end of the stream", or 1 for "extended length".
 *  <li><code>bytes 4-7</code> - four-byte tag, interpreted as a 4-character ASCII string</li>
 *  <li><code>bytes 7-15 (optional)</code> - if length == 1, this 8 bytes is the "extended length". These are supported for reading but not writing by this API</li>
 *  <li><code>remaining</code> - ths box data, which may be a list of other boxes (for "container" boxes), or other types of data
 * </ul>
 * <p>
 * Most types of boxes are loaded but not stored; you can traverse the tree, but attempting to write them out
 * again will fail (this is intentional; we don't want to store a multi-GB movie in memory when all you want is the metadata).
 * Some particular types of boxes can also be created from scratch; currently this only applies to C2PA related boxes.
 * Some simple examples.
 * </p>
 * <pre style="background: #eee; border: 1px solid #888; font-size: 0.8em">
 * BoxFactory factory = new BoxFactory();
 * Box box;
 * while ((box=factory.load(inpustream)) != null) {
 *   traverse(box, "");
 * }
 * 
 * void traverse(Box box, String prefix) {
 *   System.out.println(box.type()); // a 4-character string
 *   if (box instanceof XMPBox) {
 *     byte[] xmpdata = ((XMPBox)box).data(); // box subclasses are more interesting
 *   }
 *   for (Box child=box.first();box!=null;box=box.next()) {
 *     assert child.parent() == box;
 *     traverse(child, prefix + " ");
 *   }
 * }
 * </pre>
 * @see BoxFactory
 * @see C2PAStore
 * @since 5
 */
public class Box {

    private Box parent, first, next;
    int type;
    long len;   // store because not every Box is going to get parsed on read
    boolean sparse;    // if true, we skipped some data on load.
    byte[] debugReadBytes;      // orig bytes read from disk, only set if BoxFactory.debug

    /**
     * @hidden
     */
    protected static long readLong(InputStream in) throws IOException {
        long v = 0;
        for (int i=0;i<8;i++) {
            int q = in.read();
            if (q < 0) {
                throw new EOFException();
            }
            v = (v<<8) | q;
        }
        return v;
    }

    /**
     * @hidden
     */
    protected static int readInt(InputStream in) throws IOException {
        int v = 0;
        for (int i=0;i<4;i++) {
            int q = in.read();
            if (q < 0) {
                throw new EOFException();
            }
            v = (v<<8) | q;
        }
        return v;
    }

    /**
     * @hidden
     */
    protected static byte[] readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        if (buf == null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int l;
            while ((l=in.read(tmp, 0, tmp.length)) >= 0) {
                out.write(tmp, 0, l);
            }
            return out.toByteArray();
        } else {
            len += off;
            int l;
            while (off < len && (l=in.read(buf, off, len - off)) >= 0) {
                off += l;
            }
            if (off < len) {
                throw new EOFException();
            }
            return buf;
        }
    }

    /**
     * @hidden
     */
    protected static ByteArrayInputStream getByteArrayInputStream(byte[] buf, int off, int len) {
        if (len < 0) {
            len = buf.length;
        }
        return new ByteArrayInputStream(buf, off, len) {
            public String toString() {
                return "{bais:hash="+Integer.toHexString(hashCode())+" off="+pos+" len="+count+"}";
            }
        };
    }


    /**
     * @hidden
     */
    protected static float readFixed16(InputStream in) throws IOException {
        return (readInt(in) & 0xFFFFFFFFl) / 65535f;
    }

    /**
     * @hidden
     */
    protected static int readShort(InputStream in) throws IOException {
        int v = 0;
        for (int i=0;i<2;i++) {
            int q = in.read();
            if (q < 0) {
                throw new EOFException();
            }
            v = (v<<8) | q;
        }
        return v;
    }

    /**
     * @hidden
     * @param s the string
     * @return the type
     */
    public static int stringToType(String s) {
        int v = (s.length() < 1 ? 0 : s.charAt(0) & 0xFF) << 24;
        v |=    (s.length() < 2 ? 0 : s.charAt(1) & 0xFF) << 16;
        v |=    (s.length() < 3 ? 0 : s.charAt(2) & 0xFF) << 8;
        v |=    (s.length() < 4 ? 0 : s.charAt(3) & 0xFF);
        return v;
    }

    /**
     * @hidden
     * @param type the type
     * @return the string
     */
    public static String typeToString(int type) {
        char[] c = new char[4];
        c[0] = (char)((type>>24)&0xFF);
        c[1] = (char)((type>>16)&0xFF);
        c[2] = (char)((type>>8)&0xFF);
        c[3] = (char)(type&0xFF);
        return new String(c);
    }

    //------------------------------------------------------------------------------------------------

    /**
     * Create a new uninitialized box, for loading. Don't call this constructor
     */
    protected Box() {
    }

    /**
     * Create a new Box
     * @param type the type, which must be a four-letter alphanumeric string
     */
    protected Box(String type) {
        if (type == null || type.length() != 4) {
            throw new IllegalArgumentException("bad type");
        }
        this.type = stringToType(type);
    }

    /**
     * Add this box to the end of the list of children
     * @param box the box, which must not be already part of a tree
     * @throws IllegalStateException if box is already part of a tree
     */
    public void add(Box box) {
        if (box.parent != null) {
            throw new IllegalStateException("already added");
        } else if (first == null) {
            first = box;
            box.parent = this;
        } else {
            Box b = first;
            while (b.next != null) {
                b = b.next;
            }
            b.next = box;
            box.parent = this;
        }
    }

    void addIfNotNull(Box box) {
        if (box != null) {
            add(box);
        }
    }

    /**
     * Remove this box from its parent
     */
    public void remove() {
        if (parent != null) {
            if (parent.first == this) {
                parent.first = next;
                parent = next = null;
            } else {
                Box b = parent.first;
                while (b.next != null && b.next != this) {
                    b = b.next;
                }
                if (b.next == this) {
                    b.next = next;
                    parent = next = null;
                }
            }
        }
    }

    /**
     * Insert this box before the specified box. If this box already exists
     * @param other the Box to insert this box before. The box must have a parent.
     * @throws IllegalStateException if this box is already part of a tree, or other has no parent
     */
    public void insertBefore(Box other) {
        if (parent != null) {
            throw new IllegalStateException("already added");
        }
        if (other == null) {
            throw new IllegalStateException("other is null");
        }
        if (other.parent == null) {
            throw new IllegalStateException("other has no parent");
        }
        Box parent = other.parent;
        if (parent.first == other) {
            parent.first = this;
            this.parent = parent;
            this.next = other;
        } else {
            Box b = parent.first;
            while (b.next != other) {
                b = b.next;
            }
            b.next = this;
            this.parent = parent;
            this.next = other;
        }
    }

    /**
     * Read the box content from the stream
     * The type/length have already been read from the stream.
     * The stream will return EOF when this box's data is read.
     * @param in the stream
     */
    protected void read(InputStream in, BoxFactory factory) throws IOException {
        if (factory.isContainer(type())) {
            Box b;
            while ((b=factory.load(in)) != null) {
                add(b);
            }
        }
    }

    /**
     * Write the box content to the specified stream
     * @param out the OutputStream
     */
    private final void dowrite(OutputStream out) throws IOException {
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        write(tmp);
        tmp.close();
        byte[] data = tmp.toByteArray();
        int len = data.length + 8;
        int type = stringToType(type());
        out.write(len>>24);
        out.write(len>>16);
        out.write(len>>8);
        out.write(len>>0);
        out.write(type>>24);
        out.write(type>>16);
        out.write(type>>8);
        out.write(type>>0);
        out.write(data);
    }

    /**
     * Write the box content. For boxes that are not {@link BoxFactory#isContainer containers},
     * this method must be overridden otherwise the box cannot be encoded
     * @param out the OutputStream to write to
     * @throws UnsupportedOperationException if the box is not a container box and this method hasn't been overridden
     */
    protected void write(OutputStream out) throws IOException {
        if (isSparse()) {
            throw new UnsupportedOperationException("sparse box: " + this);
        } else {
            for (Box b=first;b!=null;b=b.next) {
                b.dowrite(out);
            }
        }
    }

    /**
     * Return a <code>byte[]</code> array containing the encoded box structure.
     * All descendents that are not container boxes must override {@link #write}
     * @return the encoded box
     * @throws UnsupportedOperationException if any descendent of this box is not a container box and {@link #write} hasn't been overridden
     */
    public final byte[] getEncoded() {
        UsefulByteArrayOutputStream out = new UsefulByteArrayOutputStream();
        try {
            dowrite(out);
        } catch (IOException e) {}
        return out.toByteArray();
    }

    /**
     * Return true if this box was not completely read during the {@link BoxFactory#load} method.
     * Sparse boxes cannot be written, so {@link #getEncoded} will fail.
     * @return whether box is sparse
     */
    public boolean isSparse() {
        return sparse;
    }

    /**
     * Return the type of this box, a four-character string
     * @return the Box type
     */
    public String type() {
        return typeToString(type);
    }

    /**
     * Return the full hierarchy of types from the root box to this
     * box, separated by dots. For example if this box has type "udat"
     * and the parent box has type "moov", this method returns "moov.udat"
     * @return the Box type hierarchy
     */
    public String fullType() {
        return parent() == null ? type() : parent.type() + "." + type();
    }

    /**
     * Return the length of this box in bytes, or 0 for "until end of file".
     * Boxes that have not been read from disk will always have zero length
     * @return the Box length
     */
    public long length() {
        return len;
    }

    /**
     * Return the parent of this box, or null if this is the root
     * @return the parent
     */
    public Box parent() {
        return parent;
    }

    /**
     * Return the next box in the list, or null if this is the last.
     * @return the next sibling
     */
    public Box next() {
        return next;
    }

    /**
     * Return the first child of this box, or null if this box has no children
     * @return the first child
     */
    public Box first() {
        return first;
    }

    /**
     * Return a deep duplicate of this box, duplicating all its children too
     * if necessary.
     * @return the duplicated box
     */
    public Box duplicate() {
        try {
            Box dup = BoxFactory.newBox(getClass());
            dup.type = type;
            if (first != null) {
                for (Box b=first();b!=null;b=b.next()) {
                    dup.add(b.duplicate());
                }
            } else {
                byte[] q = getEncoded();
                dup.read(new ByteArrayInputStream(q, 8, q.length - 8), new BoxFactory());
            }
            return dup;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return a String representation of this Box, which will be parseable as JSON
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"type\":\"");
        sb.append(type());
        sb.append("\"");
        if (getClass() != Box.class) {
            String name = getClass().getName();
            name = name.substring(name.lastIndexOf(".") + 1);
            sb.append(",\"class\":\"");
            sb.append(name);
            sb.append("\"");
        }
        if (isSparse()) {
            sb.append(",\"sparse\":true");
        }
        if (length() > 0) {
            sb.append(",\"size\":");
            sb.append(length());
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * A convenience method to dump the box trree
     * @param prefix the prefix to add to each line, or null for none
     * @param out the Appendable to write to, or null to create a new StringBuilder
     * @return the appendable
     * @throws RuntimeException wrapping an IOException if encountered while writing to the Appendable.
     */
    public Appendable dump(String prefix, Appendable out) {
        if (prefix == null) {
            prefix = "";
        }
        if (out == null) {
            out = new StringBuilder();
        }
        return dump(prefix, out, false);
    }

    private Appendable dump(String prefix, Appendable out, boolean addnl) {
        try {
            out.append(prefix);
            out.append(this.toString());
            if (first() != null || addnl) {
                out.append("\n");
            }
            prefix += " ";
            for (Box box = first();box!=null;box=box.next()) {
                box.dump(prefix, out, addnl || box.next() != null);
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (int i=0;i<b.length;i++) {
            int v = b[i] & 0xFF;
            int v1 = (v >> 4) & 0xf;
            sb.append((char)(v1 < 10 ? '0' + v1 : v1 - 10 + 'a'));
            v1 = v & 0xf;
            sb.append((char)(v1 < 10 ? '0' + v1 : v1 - 10 + 'a'));
        }
        return sb.toString();
    }

    static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i=0;i<s.length();i+=2) {
            b[i/2] = (byte)Integer.parseInt(s.substring(i, i + 2), 16);
        }
        return b;
    }

}
