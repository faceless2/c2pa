package com.bfo.box;

import com.bfo.json.*;
import java.io.*;
import java.util.*;

/**
 * A C2PA Assertion for the "stds.exif", "stds.iptc", "stds.schema-org.ClaimReview", "stds.schema-org.CreativeWork" types
 * <p>
 * <b>Note</b>: no validation is made on the schema; any Json object is accepted.
 * </p>
 * @since 5
 */
public class C2PA_AssertionSchema extends JsonContainerBox implements C2PA_Assertion {

    /**
     * Create a new assertion
     */
    protected C2PA_AssertionSchema() {
    }

    /**
     * Create a new assertion
     * @param schema one of "stds.exif", "stds.iptc", "stds.schema-org.ClaimReview", "stds.schema-org.CreativeWork"
     * @param json the JSON data
     */
    public C2PA_AssertionSchema(String schema, Json json) {
        super(schema, json);
    }

}
