package com.bfo.box;

import com.bfo.json.*;
import java.io.*;
import java.util.*;

/**
 * A C2PA Assertion for the "c2pa.soft-binding" type
 * <p>
 * <b>Note</b>: this is a placeholder class; verify will return without error
 * </p>
 * @since 5
 */
public class C2PA_AssertionSoftBinding extends CborContainerBox implements C2PA_Assertion {

    /**
     * Create a new assertion
     */
    public C2PA_AssertionSoftBinding() {
        super("cbor", "c2pa.soft-binding");
    }

}
