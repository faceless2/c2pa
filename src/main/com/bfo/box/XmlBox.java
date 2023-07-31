package com.bfo.box;

import com.bfo.json.*;
import java.io.*;

/**
 * An "xml " box (note the space) contains a single XML object. It is defined in
 * ISO19566-5 section B.5 but is unused in C2PA; all the cool kids use JSON.
 * @since 5
 */
public class XmlBox extends DataBox {

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.setCharAt(sb.length() - 1, ',');
        sb.append("\"xml\":\"");
        try { sb.append(new Json(new String(data(), "UTF-8"))).toString(); } catch (UnsupportedEncodingException e) {}
        sb.append("\"}");
        return sb.toString();
    }

}
