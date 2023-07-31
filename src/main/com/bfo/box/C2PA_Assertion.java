package com.bfo.box;

import java.io.*;
import java.util.*;

/**
 * An interface implemented by JUMBox objects that represent assertions.
 */
public interface C2PA_Assertion {

    /**
     * This default method in the marker interface casts this object to a JUMBox.
     * Every assertion must be a JUMBox
     * @return this
     */
    public default JUMBox asBox() {
        return (JUMBox)this;
    }

    /**
     * Return the manifest box containing this assertion
     * @return the manifest containing this assertion
     */
    public default C2PAManifest getManifest() {
        return (C2PAManifest)asBox().parent().parent();
    }

    /**
     * Verify this assertion, returning a list of status codes describing why this assertion failed
     * or an empty list if it succeeded.
     * The default implementation <b>succeeds</b>, and returns an empty list.
     * @throws UnsupportedOperationException if the assertion isn't implemented
     * @throws IOException if the assertion verification involved I/O and the IO layer threw an exception
     * @return a list of status codes, which may be empty on success
     */
    public default List<C2PAStatus> verify() throws IOException, UnsupportedOperationException {
        return Collections.<C2PAStatus>emptyList();
    }

}
