package com.bfo.box;

import com.bfo.json.*;
import java.io.*;

/**
 * A "json" box contains a single JSON object. It is identical to {@link CborBox}
 * except the value is serialized as JSON. It is defined in ISO19566-5 appendix B.4
 * @since 5
 */
public class JsonBox extends DataBox {

    private Json json;

    /**
     * Create a new uninitialized box, for loading. Don't call this constructor
     */
    public JsonBox() {
    }

    /**
     * Create a new JsonBox
     * @param json the Json object, which must not be null
     */
    public JsonBox(Json json) {
        super("json");
        setJson(json);
    }

    @Override protected void read(InputStream in, BoxFactory factory) throws IOException {
        json = Json.read(in, null);
    }

    @Override public byte[] data() {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try {
            json.write(new UTF8Writer(b, false), null);
        } catch (IOException e) {}
        return b.toByteArray();
    }

    /**
     * Set a new Json object to replace the one in this box
     * @param json the Json, which must not be null
     */
    public void setJson(Json json) {
        if (json == null) {
            throw new NullPointerException("no json");
        }
        this.json = json;
    }

    /**
     * Return the {@link Json} object from this Box.
     * @return the object
     */
    public Json json() {
        return json;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.setCharAt(sb.length() - 1, ',');
        sb.append("\"json\":");
        sb.append(json());
        sb.append('}');
        return sb.toString();
    }

}
