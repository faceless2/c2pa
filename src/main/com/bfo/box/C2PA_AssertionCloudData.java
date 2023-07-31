package com.bfo.box;

import com.bfo.json.*;
import java.io.*;
import java.util.*;

/**
 * A C2PA Assertion for the "c2pa.cloud-data" type
 * <p>
 * <b>Note</b>: this is a placeholder class; verify will throw an {@link UnsupportedOperationException}
 * </p>
 * @since 5
 */
public class C2PA_AssertionCloudData extends CborContainerBox implements C2PA_Assertion {

    /**
     * Create a new assertion
     */
    public C2PA_AssertionCloudData() {
        super("cbor", "c2pa.cloud-data");
    }

    @Override public List<C2PAStatus> verify() {
        throw new UnsupportedOperationException(label() + " not yet implemented");
    }
}
