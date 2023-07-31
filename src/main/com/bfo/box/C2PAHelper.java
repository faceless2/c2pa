package com.bfo.box;

import java.io.*;
import java.util.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import com.bfo.json.*;

/**
 * A general Helper class for C2PA which functions as a main method, provides
 * utility methods for embedding C2PA in files. See {@link C2PAStore} for 
 * details on how to use it
 * @see C2PAStore
 */
public class C2PAHelper {

    private static final String APP1_XMP = "http://ns.adobe.com/xap/1.0/\u0000";

    /**
     * Read a JPEG image from the supplied InputStream and return a Json
     * object describing various aspects of it that are relevant for C2PA.
     * <ul>
     *  <li>
     *   <b>c2pa</b> - if the JPEG image contains a C2PA object, this will
     *   be set to a {@link Json#bufferValue buffer} containing the encoded C2PA object
     *  </li>
     *  <li>
     *   <b>xmp</b> - if the JPEG image contains an XMP block, this will
     *   be set to a {@link Json#bufferValue buffer} containing the XMP data
     *  </li>
     *  <li>
     *   <b>data</b> - a {@link Json#bufferValue buffer} which contains
     *   the image data after the C2PA and XMP are removed
     *  </li>
     *  <li>
     *   <b>insert_offset</b> - an offset into the "data" object
     *   that is the suggested location of any new C2PA or XMP data.
     *  </li>
     * </ul>
     * @param in the InputStream (required)
     * @return the data as described
     * @throws IOException if the object failed to load
     */
    public static Json readJPEG(InputStream in) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("in is null");
        }
        if (!in.markSupported()) {
            in = new BufferedInputStream(in);
        }
        CountingInputStream cin = new CountingInputStream(in);

        final Map<Integer,ByteArrayOutputStream> app11 = new HashMap<Integer,ByteArrayOutputStream>();
        final long[] headerOffset = new long[] { 0 };
        final ByteArrayOutputStream xmp = new ByteArrayOutputStream();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        readJPEG(new CountingInputStream(in), new Callback() {
            boolean header = true;
            @Override public boolean segment(final int table, final int length, final CountingInputStream in) throws IOException {
                boolean write = true;
                byte[] data = null;

                if (table == 0xffeb && length > 17) {
                    header = false;
                    // 0xffeb - app11 marker (already read)
                    // 2 byte length (already read)
                    // 2 byte ID, which is always 0x4a50
                    // 2 byte box instance number "disambiguates between JPEG XT marker segments"
                    // 4 byte packet sequence number, which C2PA requires they're in order
                    // 4 byte box length
                    // 4 byte box type
                    // optional 8-byte extended box length if box length=1
                    // data
                    data = new byte[length - 2];
                    int p = 0, v;
                    while ((v=in.read(data, p, data.length - p)) >= 0) {
                        p += v;
                    }
                    if (p != data.length) {
                        throw new EOFException("Couldn't read segment");
                    }
                    if (data[0] == 0x4a && data[1] == 0x50) {
                        write = false;
                        int id = (data[2]&0xff)<<8 | (data[3]&0xff);
                        int seq = ((data[4]&0xff)<<24) | ((data[5]&0xff)<<16) | ((data[6]&0xff)<<8) | ((data[7]&0xff)<<0);
                        int boxlen = ((data[8]&0xff)<<24) | ((data[9]&0xff)<<16) | ((data[10]&0xff)<<8) | ((data[11]&0xff)<<0);
                        int boxtype = ((data[12]&0xff)<<24) | ((data[13]&0xff)<<16) | ((data[14]&0xff)<<8) | ((data[15]&0xff)<<0);
                        // System.out.println("seglen="+length+" id="+id+" seq="+seq+" boxlen="+boxlen+" boxtype="+Box.typeToString(boxtype));
                        if (boxtype == 0x6a756d62) { // "jumb"
                            int skip = 8;
                            if (app11.get(id) == null) {
                                app11.put(id, new ByteArrayOutputStream());
                            } else {
                                skip += 8;   // because boxlen and boxtype are repeated (with identical length) for every packet
                                if (boxlen == 1) {
                                    skip += 8;
                                }
                            }
                            app11.get(id).write(data, skip, data.length - skip);
                        }
                    }
                } else if (table == 0xffe1 && length > 6) {
                    data = new byte[length - 2];
                    int p = 0, v;
                    while ((v=in.read(data, p, data.length - p)) >= 0) {
                        p += v;
                    }
                    if (p != data.length) {
                        throw new EOFException("Couldn't read segment");
                    }
                    if (data[0] != 'E' || data[1] != 'x' || data[2] != 'i' || data[3] != 'f' || data[4] != 0 || data[5] != 0) {
                        header = false;
                        boolean eq = data.length > APP1_XMP.length();
                        for (int i=0;eq && i<APP1_XMP.length();i++) {
                            if ((data[i] & 0xFF) != APP1_XMP.charAt(i)) {
                                eq = false;
                            }
                        }
                        if (eq) {
                            write = false;
                            xmp.write(data, APP1_XMP.length(), p - APP1_XMP.length());
                        }
                    }
                } else if (table != 0xffe0 && table != 0xffd8) {
                    header = false;
                }
                if (write) {
                    out.write(table>>8);
                    out.write(table);
                    if (length > 0) {
                        out.write(length >> 8);
                        out.write(length);
                    }
                    if (data != null) {
                        out.write(data);
                    } else {
                        byte[] buf = new byte[8192];
                        int l;
                        while ((l=in.read(buf)) >= 0) {
                            out.write(buf, 0, l);
                        }
                    }
                }
                if (header) {
                    headerOffset[0] = in.tell();
                }
                return true;
            }
        });

        Json json = Json.read("{}");
        json.put("data", out.toByteArray());
        json.put("insert_offset", headerOffset[0]);
        if (xmp.size() > 0) {
            json.put("xmp", xmp.toByteArray());
        }
        if (!app11.isEmpty()) {
            // Pick the first - surely there will be only one?
            json.put("c2pa", app11.values().iterator().next().toByteArray());
        }
        return json;
    }

    /**
     * Save a JPEG, signing and inserting the supplied C2PAStore.
     * <ul>
     *  <li>
     *   <b>data</b> - a {@link Json#bufferValue buffer} which contains
     *   the image data after the C2PA and XMP are removed
     *  </li>
     *  <li>
     *   <b>insert_offset</b> - the offset into the "data" object
     *   that is the location of any the C2PA and XMP data.
     *  </li>
     *  <li>
     *   <b>xmp</b> - (optional) a string or bytebuffer that contains the
     *   XMP data to insert. If this value is zero length, or is any other
     *   value other than null, a basic XMP will be created and inserted.
     *  </li>
     * </ul>
     * <p>
     * After this method exits, the <code>image</code> parameter will be
     * updated to add a <code>c2pa</code>, which is encoded version of the
     * C2PA object that was written.
     * </p><p>
     * The C2PA store supplied must have an active manifest with a {@link C2PA_AssertionHashData}
     * and that has had {@link C2PASignature#setSigner} called on it.
     * </p>
     *
     * @param image the image data, which must have at least <code>data</code> and <code>insert_offset</code>
     * @param store the C2PA store to insert
     * @param out the OutputStream to write to
     * @return the list of C2PA status codes from signing
     * @throws IOException if the object failed to save
     */
    public static List<C2PAStatus> writeJPEG(Json image, C2PAStore store, OutputStream out) throws IOException {
        List<C2PAStatus> status;
        if (out == null) {
            throw new IllegalArgumentException("out is null");
        }
        if (!(image != null && image.isBuffer("data") && image.isNumber("insert_offset"))) {
            throw new IllegalArgumentException("image is missing data and insert_offset");
        }
        if (store == null) {
            out.write(image.bufferValue("data").array());
            return null;
        }

        final byte[] inputBytes = image.bufferValue("data").array();
        final int inputOffset = image.intValue("insert_offset");

        C2PAManifest manifest = store.getActiveManifest();
        if (manifest == null) {
            throw new IllegalArgumentException("store has no active manifest");
        }
        if (!manifest.getSignature().hasSigner()) {
            throw new IllegalArgumentException("manifest has no signing identity");
        }
        C2PA_AssertionHashData hash = null;
        for (C2PA_Assertion a : manifest.getAssertions()) {
            if (a instanceof C2PA_AssertionHashData) {
                hash = (C2PA_AssertionHashData)a;
                break;
            }
        }
        if (hash == null) {
            throw new IllegalArgumentException("active manifest has no hashData assertion");
        }

        // Set xmpbytes to the XMP segment. Presume only one.
        byte[] xmpbytes = new byte[0];
        if (image.isBuffer("xmp") || image.isString("xmp")) {
            byte[] b = image.isBuffer("xmp") ? image.bufferValue("xmp").array() : image.stringValue("xmp").getBytes("UTF-8");
            if (b.length == 0) {
                String s = "<?xpacket begin=\"\ufeff\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?><x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"><rdf:Description rdf:about=\"\" xmlns:dcterms=\"http://purl.org/dc/terms/\" dcterms:provenance=\"" + store.find(manifest) + "\"/></rdf:RDF></x:xmpmeta><?xpacket end=\"r\"?>";
                b = s.getBytes("UTF-8");
            }
            int datalen = b.length + APP1_XMP.length() + 2;
            if (datalen > 65535) {
                throw new IllegalArgumentException("XMP too large (" + datalen + " bytes)");
            }
            xmpbytes = new byte[datalen + 2];
            xmpbytes[0] = (byte)0xff;
            xmpbytes[1] = (byte)0xe1;
            xmpbytes[2] = (byte)(datalen>>8);
            xmpbytes[3] = (byte)datalen;
            System.arraycopy(APP1_XMP.getBytes("ISO-8859-1"), 0, xmpbytes, 4, APP1_XMP.length());
            System.arraycopy(b, 0, xmpbytes, APP1_XMP.length() + 4, b.length);
        }

        // Dummy sign to determine length
        manifest.setInputStream(new ByteArrayInputStream(new byte[0]));
        manifest.getSignature().sign();
        byte[] dummydata = store.getEncoded();
        int siglength = dummydata.length;
        final int maxsegmentlength = 65535;
        final int segheaderlen = 20;
        int numsegments = (int)Math.ceil((siglength - 8) / (float)(maxsegmentlength - segheaderlen));
        hash.setExclusions(new long[] { inputOffset, (siglength - 8) + (numsegments * segheaderlen) });
        // Sign a second time now we have set exclusions
        manifest.setInputStream(new SequenceInputStream(new ByteArrayInputStream(inputBytes, 0, inputOffset), new SequenceInputStream(new ByteArrayInputStream(xmpbytes), new ByteArrayInputStream(inputBytes, inputOffset, inputBytes.length - inputOffset)))); //yuk
        status = manifest.getSignature().sign();
        byte[] data = store.getEncoded();
        if (data.length != siglength) {
            //System.out.println(((C2PAStore)new BoxFactory().load(new ByteArrayInputStream(dummydata))).toJson());
            //System.out.println(((C2PAStore)new BoxFactory().load(new ByteArrayInputStream(data))).toJson());
            throw new IllegalStateException("Expected " + siglength + " bytes, second signing gave us " + data.length);
        }

        // Begin writing process; copy the bit we've already read
        out.write(inputBytes, 0, inputOffset);
        // Write our C2PA as segments
        int segid = 0;
        for (int i=0;i<numsegments;) {
            int start2 = 8 + (i * (maxsegmentlength - segheaderlen));
            int len = Math.min(maxsegmentlength - segheaderlen, data.length - start2);
            // System.out.println("WRITE " + i+"/"+numsegments+" start2="+start2+" len="+len+" to " + (start2+len)+"/"+data.length);
            i++;                   // c2patool wants packets to start at 1
            int seglen = len + segheaderlen - 2;  // excluding marker
            out.write(0xff);       // app11
            out.write(0xeb);
            out.write(seglen>>8);  // segment length
            out.write(seglen);
            out.write(0x4a);       // 0x4a50 constant
            out.write(0x50);
            out.write(segid>>8);   // two byte box instance number
            out.write(segid);
            out.write(i>>24);      // four byte sequence number
            out.write(i>>16);
            out.write(i>>8);
            out.write(i);
            out.write(data, 0, 8); // this must be repeated each segment
            out.write(data, start2, len);
        }
        out.write(xmpbytes);

        out.write(inputBytes, inputOffset, inputBytes.length - inputOffset);
        out.flush();
        if (xmpbytes.length > 0) {
            image.put("xmp", xmpbytes);
        } else {
            image.remove("xmp");
        }
        image.put("c2pa", data);
        return status;
    }


    private static interface Callback {
        /**
         * A JPEG segment has been read.
         * @param table the table
         * @param length the length of this segment, excluding the two-byte table header. May be 0 if no length applies to this table
         * @param in the stream to read from, which will be limited to "length" bytes
         * @return true if reading should continue
         */
        boolean segment(int table, int length, CountingInputStream in) throws IOException;
    }

    /**
     * if out is null, read an entire JPEG, extract any C2PA data and return it
     * if out is not null, copy until the first marker after the initial JFIF marker(s) then stop
     */
    private static void readJPEG(CountingInputStream in, Callback callback) throws IOException {
        int table;
        do {
            long start = in.tell();
            table = in.read();
            if (table < 0) {
                break;
            }
            int v = in.read();
            if (v < 0) {
                throw new EOFException(Long.toString(in.tell()));
            }
            table = (table<<8) | v;
            int length = 0;
            if (table == 0xffda) {
                length = -1;
            } else if (table != 0xff01 && (table < 0xffd0 || table > 0xffd8)) {
                int len = in.read();
                if (len < 0) {
                    throw new EOFException();
                }
                v = in.read();
                if (v < 0) {
                    throw new EOFException();
                }
                length = (len<<8) | v;
            }
            final long end = length < 0 ? -1 : start + length + 2;
            // System.out.println("* seg=0x"+Integer.toHexString(table)+" start="+start+" len="+length+" end="+end);
            in.limit(end);
            if (callback != null && !callback.segment(table, length, in)) {
                return;
            }
            in.limit(-1);
            if (end < 0) {
                // WTF - skip past EOF seems to fail on FileInputStream
                while (in.read() >= 0);
            } else {
                while (in.tell() < end) {
                    if (in.read() < 0) {
                        break;
                    }
                }
            }
        } while (table != 0xffda);
    }


    public static void main(String[] args) throws Exception {
        String storepath = null, password = "", alias = null, alg = null, cw = null, outname = null, outc2pa = null;
        List<X509Certificate> certs = new ArrayList<X509Certificate>();
        PrivateKey key = null;
        boolean sign = false, debug = false, boxdebug = false, repackage = false;

        // No apologies for this, it's a quick and nasty test
        KeyStore keystore = null;
        if (args.length == 0) {
            help();
        }
        for (int i=0;i<args.length;i++) {
            String s = args[i];
            if (s.equals("--keystore")) {
                storepath = args[++i];
            } else if (s.equals("--help")) {
                help();
            } else if (s.equals("--password")) {
                password = args[++i];
            } else if (s.equals("--sign")) {
                sign = true;
            } else if (s.equals("--verify")) {
                sign = false;
            } else if (s.equals("--debug")) {
                debug = true;
            } else if (s.equals("--boxdebug")) {
                boxdebug = true;
            } else if (s.equals("--nodebug")) {
                debug = false;
            } else if (s.equals("--noboxdebug")) {
                boxdebug = false;
            } else if (s.equals("--alias")) {
                alias = args[++i];
            } else if (s.equals("--alg")) {
                alg = args[++i];
            } else if (s.equals("--creativework")) {
                cw = args[++i];
            } else if (s.equals("--repackage")) {
                repackage = true;
            } else if (s.equals("--out")) {
                outname = args[++i];
            } else if (s.equals("--c2pa")) {
                outc2pa = args[++i];
            } else {
                if (sign) {
                    // This block loads the private key and certificates
                    if (storepath == null) {
                        throw new IllegalStateException("no keystore");
                    }
                    BufferedInputStream in = new BufferedInputStream(new FileInputStream(storepath));
                    in.mark(4);
                    int v = (in.read()<<24) | (in.read()<<16) | (in.read()<<8) | in.read();
                    in.reset();
                    String storetype = v == 0xfeedfeed ? "jks" : v == 0xcececece ? "jceks" : "pkcs12";
                    keystore = KeyStore.getInstance(storetype);
                    keystore.load(in, password.toCharArray());
                    in.close();
                    if (alias == null) {
                        for (Enumeration<String> e = keystore.aliases();e.hasMoreElements();) {
                            alias = e.nextElement();
                            try {
                                key = (PrivateKey)keystore.getKey(alias, password.toCharArray());
                                break;
                            } catch (Exception e2) {
                                alias = null;
                            }
                        }
                    } else {
                        key = (PrivateKey)keystore.getKey(alias, password.toCharArray());
                    }
                    for (Certificate c : keystore.getCertificateChain(alias)) {
                        certs.add((X509Certificate)c);
                    }
                    if (certs.size() > 1) {
                        // "The trust anchor’s certificate (also called the root certificate) should not be included."
                        certs.remove(certs.size() - 1);
                    }
                }
                do {
                    String inname = args[i];
//                    System.out.println(inname);
                    InputStream in = new FileInputStream(inname);
                    if (sign) {
                        if (outname == null) {
                            outname = inname.indexOf(".") > 0 ? inname.substring(0, inname.lastIndexOf(".")) + "-signed" + inname.substring(inname.lastIndexOf(".")) : inname + "-signed.jpg";
                        }

                        // Load image
                        final Json img = readJPEG(in);
                        img.put("xmp", "");
                        in = new FileInputStream(inname);

                        // Prepare store
                        C2PAStore c2pa = new C2PAStore();
                        C2PAManifest manifest = new C2PAManifest("urn:uuid:" + UUID.randomUUID().toString());
                        c2pa.getManifests().add(manifest);
                        manifest.getClaim().setInstanceID("urn:uuid:" + UUID.randomUUID().toString());
                        manifest.getClaim().setFormat("image/jpeg");
                        if (alg != null) {
                            manifest.getClaim().setHashAlgorithm(alg);
                        }
                        manifest.getAssertions().add(new C2PA_AssertionHashData());
                        if (cw != null) {
                            Json j = Json.read(new FileInputStream(cw), null);
                            manifest.getAssertions().add(new C2PA_AssertionSchema("stds.schema-org.CreativeWork", j));
                        }
                        manifest.getSignature().setSigner(key, certs);
                        if (repackage && img.isBuffer("c2pa")) {
                            C2PAStore original = (C2PAStore)new BoxFactory().load(img.bufferValue("c2pa"));
                            C2PAManifest lastmanifest = original.getActiveManifest();
                            lastmanifest.setInputStream(new FileInputStream(inname));
                            List<C2PAStatus> laststatus = lastmanifest.getSignature().verify(null);
                            for (C2PAManifest mf : original.getManifests()) {
                                lastmanifest = (C2PAManifest)mf.duplicate();
                                lastmanifest.insertBefore(manifest);
                            }
                            C2PA_AssertionIngredient ing = new C2PA_AssertionIngredient();
                            manifest.getAssertions().add(ing);
                            ing.setTargetManifest("parentOf", lastmanifest, laststatus);
                            C2PA_AssertionActions act = new C2PA_AssertionActions();
                            manifest.getAssertions().add(act);
                            act.add("c2pa.repackaged", ing, null);
                        }

                        // Save image
                        OutputStream out = new BufferedOutputStream(new FileOutputStream(outname));
                        List<C2PAStatus> status = writeJPEG(img, c2pa, out);
                        out.close();
                        out = null;
                        if (outc2pa != null) {
                            OutputStream rawout = new FileOutputStream(outc2pa);
                            rawout.write(img.bufferValue("c2pa").array());
                            rawout.close();
                            outc2pa = null;
                        }
                        if (debug) {
                            System.out.println(c2pa.toJson().toString(new JsonWriteOptions().setPretty(true).setCborDiag("hex")));
                        }
                        if (boxdebug) {
                            System.out.println(c2pa.dump(null, null));
                        }

                        boolean ok = true;
                        for (C2PAStatus st : status) {
                            ok &= st.isOK();
                            System.out.println("# " + st);
                        }
                        if (ok) {
                            System.out.println(inname + ": SIGNED, wrote to \"" + outname + "\"");
                        } else {
                            System.out.println(inname + ": SIGNED WITH ERRORS, wrote to \"" + outname + "\"");
                        }
                        System.out.println();
                    } else {
                        Json j = readJPEG(new FileInputStream(inname));
                        C2PAStore c2pa = null;
                        if (j.isBuffer("c2pa")) {
                            c2pa = (C2PAStore)new BoxFactory().load(j.bufferValue("c2pa"));
                        }
                        if (c2pa != null) {
                            if (outc2pa != null) {
                                OutputStream rawout = new BufferedOutputStream(new FileOutputStream(outc2pa));
                                rawout.write(c2pa.getEncoded());
                                rawout.close();
                                outc2pa = null;
                            }
                            if (boxdebug) {
                                System.out.println(c2pa.dump(null, null));
                            }
                            if (debug) {
                                System.out.println(c2pa.toJson().toString(new JsonWriteOptions().setPretty(true).setCborDiag("hex")));
                            }
//                            List<C2PAManifest> manifests = c2pa.getManifests();       // to validate ALL manifests
                            List<C2PAManifest> manifests = Collections.<C2PAManifest>singletonList(c2pa.getActiveManifest());   // validate only current manifest
                            for (C2PAManifest manifest : manifests) {
                                in = new FileInputStream(inname);
                                manifest.setInputStream(in);
                                System.out.println("# verifying " + (manifest == c2pa.getActiveManifest() ? "active " : "") + "manifest \"" + manifest.label() + "\"");
                                List<C2PAStatus> status = manifest.getSignature().verify(null);
                                boolean ok = true;
                                for (C2PAStatus st : status) {
                                    ok &= st.isOK();
                                    System.out.println("# " + st);
                                }
                                if (ok) {
                                    System.out.println(inname + ": VALIDATED");
                                } else {
                                    System.out.println(inname + ": VALIDATION FAILED");
                                }
                                System.out.println();
                            }
                        } 
                    }
                    while (++i < args.length) {
                        s = args[i];
                        if (s.equals("--alg")) {
                            alg = args[++i];
                        } else if (s.equals("--creativework")) {
                            cw = args[++i];
                        } else if (s.equals("--out")) {
                            outname = args[++i];
                        } else if (s.equals("--c2pa")) {
                            outc2pa = args[++i];
                        } else if (s.equals("--sign")) {
                            sign = true;
                        } else if (s.equals("--verify")) {
                            sign = false;
                        } else if (s.equals("--debug")) {
                            debug = true;
                        } else if (s.equals("--boxdebug")) {
                            boxdebug = true;
                        } else if (s.equals("--nodebug")) {
                            debug = false;
                        } else if (s.equals("--noboxdebug")) {
                            boxdebug = false;
                        } else if (s.equals("--help")) {
                            help();
                        } else {
                            break;
                        }
                    }
                } while (i < args.length);
            }
        }
    }

    private static void help() {
        System.out.println("java com.bfo.box.C2PAHelper args...");
        System.out.println("   --help                  this help");
        System.out.println("   --verify                switch to verify mode (the default)");
        System.out.println("   --sign                  switch to signing mode");
        System.out.println("   --debug                 turn on debug to dump the c2pa store as CBOR-diag");
        System.out.println("   --boxdebug              turn on debug to dump the c2pa store as a box tree");
        System.out.println("   --nodebug               turn off --debug");
        System.out.println("   --noboxdebug            turn off --boxdebug");
        System.out.println("   --repackage             if signing a file with an existing C2PA, reference it from a 'repackage' action");
        System.out.println("   --keystore <path>       if signing, the path to Keystore to load credentials from");
        System.out.println("   --alias <name>          if signing, the alias from the keystore (default is the first one)");
        System.out.println("   --password <password>   if signing, the password to open the keystore");
        System.out.println("   --alg <algorithm>       if signing, the hash algorithm");
        System.out.println("   --creativework <path>   if signing, filename containing a JSON schema to embed");
        System.out.println("   --out <path>            if signing, filename to write signed output to (default will derive from input)");
        System.out.println("   --c2pa <path>           if signing/verifying, filename to dump the C2PA object to (default is not dumped)");
        System.out.println("   <path>                  the filename to sign or verify");
        System.out.println();
        System.exit(0);
    }

}
