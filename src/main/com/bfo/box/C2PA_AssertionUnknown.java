package com.bfo.box;

import com.bfo.json.*;
import java.io.*;
import java.util.*;

/**
 * A C2PA Assertion that will be used for unknown types of assertion
 * <p>
 * <b>Note</b>: this is a placeholder class; verify will return without error
 * </p>
 * @since 5
 */
public class C2PA_AssertionUnknown extends JUMBox implements C2PA_Assertion {

    private String url;

    void setURL(String url) {
        this.url = url;
    }

    /**
     * Return the URL this assertion was referenced by, if it's in the claim
     * list. If it's in the manifest list, return null
     * @return the url
     */
    public String url() {
        return url;
    }

}
