package com.bfo.box;

import com.bfo.json.*;
import java.io.*;
import java.nio.*;

/**
 * An EmbeddedFileContainerBox is a JUMBF wrapper around a pair of "bfdb" and "bidb" boxes.
 * It is defined in ISO19566-5 AMD-1. The contents is an file (or URL of a file) with a mediaType
 * and optional filename
 * @since 5
 */
public class EmbeddedFileContainerBox extends JUMBox {

    static final String SUBTYPE = "40cb0c32bb8a489da70b2ad6f47f4369";

    /**
     * Create a new uninitialized box, for loading.
     */
    protected EmbeddedFileContainerBox() {
    }

    /**
     * Create a new EmbeddedFileContainerBox. The stream will be fully read but not closed
     * @param label the label (required)
     * @param mediaType the mediaType (required)
     * @param fileName the fileName (may be null)
     * @param data the data to read (required)
     * @throws IOException if the stream cannot be read.
     */
    public EmbeddedFileContainerBox(String label, String mediaType, String fileName, InputStream data) throws IOException {
        super(SUBTYPE, label);
        add(new BfdbBox(mediaType, fileName, false));
        add(new DataBox("bidb", readFully(data, null, 0, 0)));
    }

    @Override protected void read(InputStream in, BoxFactory factory) throws IOException {
        addIfNotNull(factory.subFactory(new JumdBox()).load(in));
        addIfNotNull(factory.subFactory(new BfdbBox()).load(in));
        addIfNotNull(factory.subFactory(new DataBox()).load(in));
    }

    private BfdbBox bfdb() {
        return first() != null && first().next() instanceof BfdbBox ? (BfdbBox)first().next() : null;
    }

    /**
     * Return the media-type of the embedded file
     * @return the media-type
     */
    public String mediaType() {
        return bfdb() != null ? bfdb().mediaType() : null;
    }

    /**
     * Return the file-name of the embedded file, or null if not specified
     * @return the file-name
     */
    public String fileName() {
        return bfdb() != null ? bfdb().fileName() : null;
    }

    /**
     * Return the URL of the embedded file it is an external reference, otherwise return null
     * @return the URL of the external file reference
     */
    public String fileURL() {
        BfdbBox bfdb = bfdb();
        if (bfdb.isExternal() && bfdb.next() instanceof DataBox) {
            byte[] data = ((DataBox)bfdb.next()).data();
            try {
                // supposed to be null terminated, but why?
                int len = data[data.length - 1] == 0 ? data.length - 1 : data.length;
                return new String(data, 0, len, "UTF-8");
            } catch (IOException e) {}
        }
        return null;
    }

    /**
     * Return the embedded file as a ByteBuffer if its embedded, otherwise return null
     * @return the file data
     */
    public ByteBuffer data() {
        BfdbBox bfdb = bfdb();
        if (!bfdb.isExternal() && bfdb.next() instanceof DataBox) {
            return ByteBuffer.wrap(((DataBox)bfdb.next()).data());
        }
        return null;
    }

}
