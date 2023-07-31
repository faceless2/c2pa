package com.bfo.box;

import com.bfo.json.*;
import java.io.*;
import java.util.*;
import java.security.*;

/**
 * A C2PA Assertion for the "c2pa.hash.data" type
 * @since 5
 */
public class C2PA_AssertionHashData extends CborContainerBox implements C2PA_Assertion {

    private static final int PADLENGTH = 80;
    private static final String DEBUG = null; // "/tmp/hash-";

    /**
     * Create a new assertion
     */
    public C2PA_AssertionHashData() {
        super("cbor", "c2pa.hash.data");
    }

    /**
     * Set the exclusion range, a sequence of [start, length] pairs
     * determining which bytes from the InputStream read by {@link #verify}
     * to read and which to skip. The list must be in order and must not overlap.
     * @param exclusions an even-numbered list of longs, which may be zero length or null
     */
    public void setExclusions(long[] exclusions) {
        if (exclusions == null) {
            exclusions = new long[0];
        }
        if ((exclusions.length & 2) == 1) {
            throw new IllegalArgumentException("invalid exclusions " + Arrays.toString(exclusions));
        }
        cbor().put("exclusions", Json.read("[]"));
        long pos = -1;
        for (int i=0;i<exclusions.length/2;i++) {
            long start = exclusions[i*2];
            long len = exclusions[i*2+1];
            cbor().putPath("exclusions[" + i + "].start", start);
            cbor().putPath("exclusions[" + i + "].length", len);
            if (start <= pos || len <= 0) {
                throw new IllegalArgumentException("invalid exclusions " + Arrays.toString(exclusions));
            }
            pos = start + len;
        }
        int padlength = PADLENGTH - cbor().get("exclusions").toCbor().limit();
        cbor().put("pad", new byte[padlength]);
    }

    /**
     * Set the hash algorithm. If not set, defaults to the {@link C2PAClaim#getHashAlgorithm}
     * @param alg the algorithm
     */
    public void setHashAlgorithm(String alg) {
        if (alg == null) {
            cbor().remove("alg");
        } else {
            cbor().put("alg", alg);
        }
    }

    /**
     * Calculate the digest during signing. This assertion
     * retrieves the {@link InputStream} from {@link C2PAManifest#getInputStream}
     * and calculates the digest based on that, <i>ignoring any ranges specified
     * in the exclusions</i>. This method is called by {@link C2PASignature#sign}
     * and there should be no need to call it manually.
     * @throws IOException if an exception was thrown while calculating the digest
     * @return a list of failure codes, or an empty list on success
     */
    public List<C2PAStatus> sign() throws IOException {
        if (!cbor().isList("exclusions")) {
            setExclusions(new long[0]);
        }
        cbor().remove("hash");
        try {
            byte[] digest = calculateDigest(true);
            cbor().put("hash", digest);
            return Collections.<C2PAStatus>emptyList();
        } catch (NoSuchAlgorithmException e) {
            return Collections.<C2PAStatus>singletonList(new C2PAStatus(e, getManifest().find(this)));
        }
    }

    @Override public List<C2PAStatus> verify() throws IOException {
        try {
            byte[] digest = calculateDigest(false);
            byte[] storedDigest = cbor().bufferValue("hash").array();
            if (!Arrays.equals(digest, storedDigest)) {
                return Collections.<C2PAStatus>singletonList(new C2PAStatus(C2PAStatus.Code.assertion_dataHash_mismatch, "digest mismatch", getManifest().find(this), null));
            } else {
                return Collections.<C2PAStatus>emptyList();
            }
        } catch (NoSuchAlgorithmException e) {
            return Collections.<C2PAStatus>singletonList(new C2PAStatus(e, getManifest().find(this)));
        }
    }

    private byte[] calculateDigest(boolean signing) throws IOException, NoSuchAlgorithmException {
        InputStream in = getManifest().getInputStream();
        if (in == null) {
            throw new IllegalStateException("manifest has no InputStream set");
        }
        MessageDigest digest = getManifest().getMessageDigest(cbor(), signing);
        Json ex = cbor().get("exclusions");
        if (ex == null || !ex.isList()) {
            ex = Json.read("[]");
        }
        long next = Long.MAX_VALUE;
        CountingInputStream cin = new CountingInputStream(in);
        byte[] buf = new byte[8192];
        int l = 0;
        boolean reading = true;
        File debugfile = null;
        FileOutputStream debugout = null;
        if (DEBUG != null) {
            int ix = 0;
            debugfile = new File(DEBUG + ix);
            while (debugfile.canRead()) {
                debugfile = new File(DEBUG + (++ix));
            }
            debugout = new FileOutputStream(debugfile);
        }
        long writelen = 0;
        do {
            if (reading) {
                next = Long.MAX_VALUE;
            }
            if (!signing) {
                for (int i=0;i<ex.size();i++) {
                    if (ex.get(i).isNumber("start") && ex.get(i).isNumber("length")) {
                        long start = ex.get(i).intValue("start");
                        if (reading) {
                            if (start >= cin.tell()) {
                                next = Math.min(start, next);
                            }
                        } else {
                            start += ex.get(i).intValue("length");
                            if (start >= cin.tell()) {
                                next = Math.max(start, next);
                            }
                        }
                    }
                }
            }
            if (reading) {
                // System.out.println("# reading from " + cin.tell()+" to " + next + " : " + ex);
                while (cin.tell() < next && (l=cin.read(buf, 0, (int)Math.min(buf.length, next - cin.tell()))) >= 0) {
                    digest.update(buf, 0, l);
                    if (debugout != null) {
                        debugout.write(buf, 0, l);
                    }
                    writelen += l;
                }
            } else {
                // System.out.println("# skipping from " + cin.tell()+" to " + next + " : " + ex);
                while (cin.tell() < next && (l=(int)cin.skip(next - cin.tell())) >= 0);
            }
            reading = !reading;
        } while (l >= 0);
        if (debugout != null) {
            System.out.println("# wrote " + writelen + " bytes to " + debugfile);
            debugout.close();
        }
        cin.close();
        byte[] d = digest.digest();
        return d;
    }

}
