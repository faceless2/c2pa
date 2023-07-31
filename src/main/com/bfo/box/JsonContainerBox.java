package com.bfo.box;

import com.bfo.json.*;
import java.io.*;

/**
 * A JsonContainerBox is a JUMBF wrapper around a single {@link JsonBox}. It is defined in
 * ISO19566-5 Appendix B.4
 * @since 5
 */
public class JsonContainerBox extends JUMBox {

    /**
     * Create a new uninitialized box, for loading. Don't call this constructor
     */
    public JsonContainerBox() {
    }

    /**
     * Create a new JsonContainerBox
     * @param label the label
     * @param json the Json object, which will be initialized to an empty map if null
     */
    public JsonContainerBox(String label, Json json) {
        super("json", label);
        add(new JsonBox(json != null ? json : Json.read("{}")));
    }

    @Override protected void read(InputStream in, BoxFactory factory) throws IOException {
        addIfNotNull(factory.subFactory(new JumdBox()).load(in));
        addIfNotNull(factory.subFactory(new JsonBox()).load(in));
    }

    /**
     * Return the {@link JsonBox} child of this container
     * @return the JsonBox
     */
    public JsonBox getBox() {
        return first() != null && first().next() instanceof JsonBox ? (JsonBox)first().next() : null;
    }

    /**
     * Return the JSON object stored in the {@link #getBox JsonBox}
     * @return the json
     */
    public final Json json() {
        JsonBox box = getBox();
        return box == null ? null : box.json();
    }

}
