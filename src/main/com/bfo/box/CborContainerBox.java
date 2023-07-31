package com.bfo.box;

import com.bfo.json.*;
import java.io.*;
import java.math.*;

/**
 * A CborContainerBox is a JUMBF wrapper around a single {@link CborBox}. It is defined in
 * ISO19566-5 Appendix B.5
 * @since 5
 */
public class CborContainerBox extends JUMBox {

    /**
     * Create a new uninitialized box, for loading. Don't call this constructor
     */
    public CborContainerBox() {
    }

    /**
     * Create a new CBOR container box
     * @param label the label, which must not be null
     * @param json the cbor object, which will be intialized to an empty map if it's null
     */
    public CborContainerBox(String label, Json json) {
        this("cbor", label, json);
    }

    CborContainerBox(String subtype, String label) {
        this(subtype, label, null);
    }

    CborContainerBox(String subtype, String label, Json json) {
        super(subtype, label);
        add(new CborBox(json != null ? json : Json.read("{}")));
    }

    @Override protected void read(InputStream in, BoxFactory factory) throws IOException {
        addIfNotNull(factory.subFactory(new JumdBox()).load(in));
        addIfNotNull(factory.subFactory(new CborBox()).load(in));
    }

    /**
     * Return the {@link CborBox} child
     * @return the CborBox
     */
    public CborBox getBox() {
        return first() != null && first().next() instanceof CborBox ? (CborBox)first().next() : null;
    }

    /**
     * Return the CBOR object stored within this container
     * @return the cbor
     */
    public Json cbor() {
        CborBox box = getBox();
        return box == null ? null : box.cbor();
    }

}
