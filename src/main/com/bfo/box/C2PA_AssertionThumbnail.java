package com.bfo.box;

import com.bfo.json.*;
import java.io.*;
import java.util.*;

/**
 * A C2PA Assertion for the "c2pa.thumbnail.claim" and "c2pa.thumbnail.ingredient" types
 * <p>
 * <b>Note</b>: this is a placeholder class; verify will return without error
 * </p>
 * @since 5
 */
public class C2PA_AssertionThumbnail extends EmbeddedFileContainerBox implements C2PA_Assertion {

    /**
     * Create a new assertion
     */
    protected  C2PA_AssertionThumbnail() {
    }

    /**
     * Create a new assertion
     * @param mediaType the media-type of the file (image/*, required)
     * @param fileName the file-name of the file (optional)
     * @param data the InputStream to read the data from
     * @param claim if true, this assertion is a claim, if false it is an ingredient
     * @throws IOException if the thumbnail cannot be read
     */
    public C2PA_AssertionThumbnail(String mediaType, String fileName, InputStream data, boolean claim) throws IOException {
        super((claim ? "c2pa.thumbnail.claim." : "c2pa.thumbnail.ingredient.") + mediaSubtype(mediaType), mediaType, fileName, data);
    }

    private static String mediaSubtype(String mt) {
        if (mt != null && mt.toLowerCase().startsWith("image/")) {
            mt = mt.toLowerCase().substring(6);
            if (mt.indexOf(";") >= 0) {
                mt = mt.substring(0, mt.indexOf(";"));
            }
            return mt;  // eg "jpeg", "png
        }
        throw new IllegalArgumentException("mediaType is " + mt);
    }

}
