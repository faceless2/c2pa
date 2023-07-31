package com.bfo.box;

import com.bfo.json.*;
import java.io.*;
import java.math.*;

/**
 * A "cbor" box contains a single CBOR object. It is identical to {@link JsonBox}
 * except the value is serialized as CBOR. It is defined in ISO19566-5 appendix B.5
 * @since 5
 */
public class CborBox extends DataBox {

    private Json cbor;

    /**
     * Create a new uninitialized box, for loading. Don't call this constructor
     */
    public CborBox() {
    }

    /**
     * Create a new CborBox
     * @param cbor the Json object, which must not be null
     */
    public CborBox(Json cbor) {
        super("cbor");
        setCbor(cbor);
    }

    /**
     * Replace the CBOR object on this struture.
     * @param cbor the JSon object, which must not be null
     */
    public void setCbor(Json cbor) {
        if (cbor == null) {
            throw new NullPointerException("no cbor");
        }
        this.cbor = cbor;
    }

    @Override protected void read(InputStream in, BoxFactory factory) throws IOException {
        cbor = Json.readCbor(in, null);
    }

    @Override protected void write(OutputStream out) throws IOException {
        out.write(cbor.toCbor().array());
    }

    /**
     * Return the CBOR object contained in this struture.
     * @return the object
     */
    public Json cbor() {
        return cbor;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.setCharAt(sb.length() - 1, ',');
        sb.append("\"json\":");
        sb.append(new Json(cbor().toString(new JsonWriteOptions().setCborDiag("hex"))).toString());
//        sb.append(",\"cbor\":");
//        sb.append(hex(cbor().toCbor().array()));
        sb.append("}");
        return sb.toString();
    }
}
