package com.bfo.box;

import java.io.*;
import java.util.*;
import java.security.*;
import com.bfo.json.*;

/**
 * <p>
 * The <a href="https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_manifests">manifest box</a>
 * represents a signed sequence of assertions. There is at least one manifest for any {@link C2PAStore store box}.
 * A valid manifest has one or more assertions, a {@link C2PAClaim claim box} which lists some or all of those assertions
 * along with some additional metadata, and a {@link C2PASignature signature box} which signs the claim.
 * </p>
 * @see C2PAStore
 * @since 5
 */
public class C2PAManifest extends JUMBox {

    private InputStream inputStream;

    /**
     * Create a new uninitialized box, for loading. Don't call this constructor
     */
    public C2PAManifest() {
    }

    /**
     * Create a new C2PAManifest
     * @param uuid the UUID, which must not be null
     */
    public C2PAManifest(String uuid) {
        super("c2ma", uuid);
        if (label() == null) {
            throw new IllegalArgumentException("uuid is null");
        }
    }

    /**
     * Verifying C2PA requires verifying the data - this method need to be
     * called before verifying the assertions returned by {@link #getAssertions}
     * to supply that data.
     * @param in the InputStream to read from
     */
    public void setInputStream(InputStream in) {
        this.inputStream = in;
    }

    /**
     * Retrieves the InputStream set by {@link #setInputStream} <i>and clears it</i>.
     * Calling the method a second time will return null.
     * @return the InputStream to read from
     */
    public InputStream getInputStream() {
        InputStream in = inputStream;
        inputStream = null;
        return in;
    }

    /**
     * Return a live list of <i>assertions</i>, which can be edited.
     * @return the assertion list
     */
    public List<C2PA_Assertion> getAssertions() {
        Box c2as = null;
        for (Box b=first();b!=null;b=b.next()) {
            if (b instanceof JUMBox && ((JUMBox)b).subtype().equals("c2as")) {
                c2as = b;
                break;
            }
        }
        if (c2as == null) {
            add(c2as = new JUMBox("c2as", "c2pa.assertions"));
        }
        // hack! here is where we upcast any assertions we didn't find
        // into instanceof of C2PA_AssertionUnknown, move the children
        // across and replace the original
        for (Box b=c2as.first();b!=null;b=b.next()) {
            if (!(b instanceof C2PA_Assertion || b instanceof JumdBox)) {
                Box b2 = new C2PA_AssertionUnknown();
                while (b.first() != null) {
                    Box f = b.first();
                    f.remove();
                    b2.add(f);
                }
                b2.insertBefore(b);
                b.remove();
                b = b2;
            }
        }
        return new BoxList<C2PA_Assertion>(c2as, C2PA_Assertion.class);
    }

    /**
     * Return the {@link C2PAClaim claim} object, creating it if required
     * @return the claim
     */
    public C2PAClaim getClaim() {
        C2PAClaim claim = null;
        for (Box b=first();b!=null;b=b.next()) {
            if (b instanceof C2PAClaim) {
                claim = (C2PAClaim)b;
                break;
            }
        }
        if (claim == null) {
            add(claim = new C2PAClaim(null));
        }
        return claim;
    }

    /**
     * Return the {@link C2PASignature signature} object, creating it if required
     * @return the signature
     */
    public C2PASignature getSignature() {
        C2PASignature claim = null;
        for (Box b=first();b!=null;b=b.next()) {
            if (b instanceof C2PASignature) {
                claim = (C2PASignature)b;
                break;
            }
        }
        if (claim == null) {
            add(claim = new C2PASignature(null));
        }
        return claim;
    }

    MessageDigest getMessageDigest(Json j, boolean signing) throws NoSuchAlgorithmException {
        String alg = null;
        while (alg == null && j != null) {
            alg = j.stringValue("alg");
            j = j.parent();
        }
        if (alg == null) {
            alg = getClaim().getHashAlgorithm();
            if (alg == null && signing) {
                getClaim().setHashAlgorithm(C2PAClaim.DEFAULT_HASH_ALGORITHM);
                alg = getClaim().getHashAlgorithm();
            }
        }
        return getMessageDigest(alg);
    }

    static MessageDigest getMessageDigest(String alg) throws NoSuchAlgorithmException {
        if ("sha256".equals(alg) || "sha384".equals(alg) || "sha512".equals(alg)) {
            try {
                return MessageDigest.getInstance(alg.toUpperCase());
            } catch (NoSuchAlgorithmException e) {
                throw new NoSuchAlgorithmException("alg \"" + alg + "\" not found", e);
            }
        }
        throw new NoSuchAlgorithmException("alg \"" + alg + "\" not found");
    }

}
