package com.bfo.box;

import com.bfo.json.*;
import java.io.*;
import java.util.*;

/**
 * A C2PA Assertion for the "c2pa.endorsement" type
 * <p>
 * <b>Note</b>: this is a placeholder class; verify will throw an {@link UnsupportedOperationException}
 * </p>
 * @since 5
 */
public class C2PA_AssertionEndorsement extends CborContainerBox implements C2PA_Assertion {

    /**
     * Create a new assertion
     */
    public C2PA_AssertionEndorsement() {
        super("cbor", "c2pa.endorsement");
    }

    @Override public List<C2PAStatus> verify() {
        // TODO https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_validate_the_endorsements
        throw new UnsupportedOperationException(label() + " not yet implemented");
    }
}
