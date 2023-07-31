package com.bfo.box;

import com.bfo.json.*;
import java.io.*;
import java.util.*;

/**
 * A C2PA Assertion for the "c2pa.hash.bmff" or "c2pa.hash.bmff.v2" types
 * <p>
 * <b>Note</b>: this is a placeholder class; verify and sign will throw an {@link UnsupportedOperationException}
 * </p>
 * @since 5
 */
public class C2PA_AssertionHashBMFF extends CborContainerBox implements C2PA_Assertion {

    /**
     * Create a new assertion
     */
    protected C2PA_AssertionHashBMFF() {
    }

    /**
     * Create a new BMFF assertion
     * @param v2 if true, create a version 2 BMFF
     */
    public C2PA_AssertionHashBMFF(boolean v2) {
        super("cbor", v2 ? "c2pa.hash.bmff.v2" : "c2pa.hash.bmff");
    }

    /**
     * Return whether this BMFF is version 2
     * @return true if version 2
     */
    public boolean isV2() {
        return "c2pa.hash.bmff.v2".equals(label());
    }

    @Override public List<C2PAStatus> verify() throws IOException {
        // TODO https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_validating_a_bmff_hash
        throw new UnsupportedOperationException(label() + " not yet implemented");
    }

    /**
     * Calculate the digest during signing. Not yet implemented, throws UnsupportedOperationException
     * @return a list of failure codes, or an empty list on success
     * @throws IOException if such an exception was encountered during signing
     */
    public List<C2PAStatus> sign() throws IOException {
        throw new UnsupportedOperationException(label() + " not yet implemented");
    }

}
