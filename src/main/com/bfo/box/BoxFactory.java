package com.bfo.box;

import java.util.*;
import java.io.*;
import java.nio.*;
import com.bfo.json.*;

/**
 * The BoxFactory creates {@link Box} objects from an {@link InputStream}.
 */
public class BoxFactory {

    private boolean debug = false;
    private final Map<String,Class<? extends Box>> registry = new HashMap<String,Class<? extends Box>>();
    private final Set<String> containers = new HashSet<String>();
    private final Set<String> subtypes = new HashSet<String>();

    /**
     * Create a new BoxFactory
     */
    public BoxFactory() {
    }

    /**
     * Return true if the specified type is a container
     * @param tag the tag
     * @return whether box with that tag is a container
     * @see #register
     */
    public boolean isContainer(String tag) {
        if (registry.isEmpty()) {
            registerDefaults();
        }
        return containers.contains(tag);
    }

    /**
     * Return true if the specified type has boxes that begin with an ISO box subtype
     * @param tag the tag
     * @return whether box with that tag is subtyped
     * @see #register
     * @hidden
     */
    public boolean isSubtyped(String tag) {
        if (registry.isEmpty()) {
            registerDefaults();
        }
        return subtypes.contains(tag);
    }

    /**
     * Create a new Box
     * @param type the type
     * @param subtype the subtype (if type requires subtypes), or null
     * @param label if the box type is jumb, the label from the nested decription, or null
     * @return a new Box
     * @see #register
     */
    public Box createBox(String type, String subtype, String label) {
        if (registry.isEmpty()) {
            registerDefaults();
        }
        Box box = null;
        int minlength = type.length() + (subtype == null ? 1 : subtype.length() + 2);
        if (label != null) {
            type = type + "." + subtype + "." + label;
        } else if (subtype !=null) {
            type = type + "." + subtype;
        }
        String origt = type;
        while (box == null && type.length() > 0) {
            if (type.length() > minlength && Character.isDigit(type.charAt(type.length() - 1))) {
                // If it ends in "__1", "__11" etc. then strip. But only from label components
                StringBuilder st = new StringBuilder(type);
                while (st.length() > 1 && Character.isDigit(st.charAt(st.length() - 1))) {
                    st.setLength(st.length() - 1);
                }
                if (st.length() > 2 && st.charAt(st.length() - 1) == '_' && st.charAt(st.length() - 2) == '_') {
                    st.setLength(st.length() - 2);
                    type = st.toString();
                }
            }
            Class<? extends Box> cl = registry.get(type);
            if (cl != null) {
                box = newBox(cl);
            }
            type = type.indexOf(".") > 0 ? type.substring(0, type.lastIndexOf(".")) : "";
        }
        if (box == null) {
            box = new Box();
        }
        assert box.first() == null;
        // System.out.println("CREATE: t="+origt+" resolved="+type+" box="+box.getClass().getName());
        return box;
    }

    /**
     * Create a new, empty box. Always call this rather than newInstance
     * as it clears any default children
     */
    static Box newBox(Class<? extends Box> clazz) {
        try {
            Box box = (Box)clazz.getDeclaredConstructor().newInstance();
            while (box.first() != null) {
                box.first().remove();
            }
            return box;
        } catch (Exception e) {}
        return null;
    }

    /**
     * Register a new Box type. Calls to {@link #createBox} will match the registered types
     * to determine which type to create
     * @param type the type, a four-letter code, or null to set the default Box type (which defaults to {@link Box} but could be {@link DataBox} to load unrecognised boxes into memory
     * @param subtype the subtype, if this box type begins with a 16-byte ISO extension (see {@link ExtensionBox} which should be extracted as subtype. May be null
     * @param label the label to match against  the nested "jumd" box. May be null
     * @param container if true boxes of this type will be registered as containers, and will create children when read 
     * @param clazz the subclass of Box to create 
     */
    public void register(String type, String subtype, String label, boolean container, Class<? extends Box> clazz) {
        if (type == null) {
            registry.put("", clazz);
        } else if (type.length() == 4) {
            if (label != null) {
                registry.put(type + "." + subtype + "." + label, clazz);
                subtypes.add(type);
            } else if (subtype != null) {
                registry.put(type + "." + subtype, clazz);
                subtypes.add(type);
            } else {
                registry.put(type, clazz);
                if (container) {
                    containers.add(type);
                }
            }
        }
    }

    /**
     * Read a Box from a ByteBuffer.
     *
     * @param data the byte buffer to load from
     * @return the box, or <code>null</code> if the stream is fully consumed
     * @throws IOException if the stream is corrupt or fails to read
     */
    public Box load(ByteBuffer data) throws IOException {
        if (data == null) {
            throw new NullPointerException("data is null");
        }
        return load(new ByteBufferInputStream(data));
    }

    /**
     * Read a Box from the InputStream.
     *
     * @param stream the InputStream.
     * @return the box, or <code>null</code> if the stream is fully consumed
     * @throws IOException if the stream is corrupt or fails to read
     */
    public Box load(InputStream stream) throws IOException {
        if (stream == null) {
            throw new NullPointerException("stream is null");
        }
        CountingInputStream in = stream instanceof CountingInputStream ? (CountingInputStream)stream : new CountingInputStream(stream);
        long off = in.tell();
        long len = in.read();
        if (len < 0) {
            return null;
        }
        for (int i=0;i<3;i++) {
            int q = in.read();
            if (q < 0) {
                throw new EOFException();
            }
            len = (len<<8) | q;
        }

        // If debug is on, take a local copy of the table
        // into a byte array, then compare it later to
        // the same table when we write it out.
        byte[] localcopy = null;
        if (debug) {
            localcopy = new byte[(int)len];
            int tmpoff = 0;
            localcopy[tmpoff++] = (byte)(len>>24);
            localcopy[tmpoff++] = (byte)(len>>16);
            localcopy[tmpoff++] = (byte)(len>>8);
            localcopy[tmpoff++] = (byte)(len>>0);
            while (tmpoff < len) {
                localcopy[tmpoff++] = (byte)in.read();
            }
            in = new CountingInputStream(Box.getByteArrayInputStream(localcopy, 0, localcopy.length));
            off = 0;
            in.limit(len);
            in.skip(4);
        }

        int typeval = Box.readInt(in);
        if (len == 1) {
            len = Box.readLong(in);
        }
        long limit = in.limit();
        in.limit(len == 0 ? -1 : off + len);

        String type = Box.typeToString(typeval);
        String subtype = null, label = null;
        if (isSubtyped(type)) {
            byte[] tmp;
            Box desc;
            if (isContainer(type)) {
                int nextlen = Box.readInt(in);
                tmp = new byte[nextlen + 4];
                tmp[0] = (byte)(nextlen>>24);
                tmp[1] = (byte)(nextlen>>16);
                tmp[2] = (byte)(nextlen>>8);
                tmp[3] = (byte)(nextlen>>0);
                Box.readFully(in, tmp, 4, tmp.length - 4);
                desc = load(new CountingInputStream(Box.getByteArrayInputStream(tmp, 0, tmp.length)));
            } else {
                tmp = Box.readFully(in, null, 0, 0);
                desc = createBox(type, null, null);
                desc.len = len;
                desc.type = typeval;
                desc.read(new CountingInputStream(Box.getByteArrayInputStream(tmp, 0, tmp.length)), this);
            }
            in.rewind(tmp);
            if (desc instanceof ExtensionBox) {
                subtype = ((ExtensionBox)desc).subtype();
                if (desc instanceof JumdBox) {
                    label = ((JumdBox)desc).label();
                }
            }
        }
        Box box = createBox(type, subtype, label);
        // System.out.println("READING type=\"" + type + "\" subtype="+subtype+" label="+label+" of " + len + " at " + off + " cl=" + box.getClass().getName() + " in=" + in);
        box.len = len;
        box.type = typeval;
        box.read(in, this);
        // if read doesn't read the entire box then we skip to the end, we we can't call getEncoded on it
        if (!box.sparse) {
            if (len == 0) {
                // skip to end of file
                long skip;
                while ((skip = in.skip(65536)) > 0) {
                    box.sparse = true;
                }
            } else {
                long skip = in.limit() - in.tell();
                if (skip > 0) {
                    box.sparse = true;
                    long l;
                    while (skip > 0 && (l=in.skip(skip)) > 0) {
                        skip -= l;
                    }
                }
            }
        }
        in.limit(limit);
        if (debug && !box.sparse) {
            byte[] writecopy = box.getEncoded();
            if (!Arrays.equals(writecopy, localcopy)) {
                assert false : "Load Mismatch for " + box;
            }
            box.debugReadBytes = localcopy;
        }
        return box;
    }

    /**
     * Create a version of this factory where the first Box it creates is the supplied
     * one, before falling back to normal behaviour. Used to force typing from {@link Box#read}
     * @param override the overridden box type
     * @return the sub-factory
     */
    public BoxFactory subFactory(Box override) {
        final BoxFactory parent = this;
        return new BoxFactory() {
            private Box box = override;
            @Override public boolean isContainer(String tag) {
                return parent.isContainer(tag);
            }
            @Override public boolean isSubtyped(String tag) {
                return parent.isSubtyped(tag);
            }
            @Override public Box createBox(String type, String subtype, String label) {
                if (this.box != null) {
                    Box box = this.box;
                    this.box = null;
                    return box;
                }
                return parent.createBox(type, subtype, label);
            }
            @Override public Box load(InputStream stream) throws IOException {
                return parent.load(stream);
            }
            @Override public BoxFactory subFactory(Box override) {
                return parent.subFactory(override);
            }
        };
    }

    /**
     * If no registrations are made when this factory is first used,
     * this method will be called to register several defaults.
     * Should be called from the constructor for subclasses of this factory
     */
    public void registerDefaults() {
        String[] containerList = new String[] {
            // origin of this list unsure; mostly ISO14496 but there will be others
            "moov", "trak", "edts", "mdia", "minf", "dinf", "stbl", "mp4a",
            "mvex", "moof", "traf", "mfra", "udta", "ipro", "sinf", /*"meta",*/
            "ilst", "----", "?alb", "?art", "aART", "?cmt", "?day", "?nam",
            "?gen", "gnre", "trkn", "disk", "?wrt", "?too", "tmpo", "cprt",
            "cpil", "covr", "rtng", "?grp", "stik", "pcst", "catg", "keyw",
            "purl", "egid", "desc", "?lyr", "tvnn", "tvsh", "tven", "tvsn",
            "tves", "purd", "pgap",
        };
        for (String s : containerList) {
            containers.add(s);
        }
        register("uuid", null,   null, false, ExtensionBox.class);
        register("jumb", null,   null, true,  JUMBox.class);
        register("jumd", null,   null, false, JumdBox.class);
        register("cbor", null,   null, false, CborBox.class);                        // Raw CBOR box with no children
        register("json", null,   null, false, JsonBox.class);                        // Raw JSON box with no children
        register("xml ", null,   null, false, XmlBox.class);
        register("tkhd", null,   null, false, TrackHeaderBox.class);
        register("bfdb", null,   null, false, BfdbBox.class);
        register("bidb", null,   null, false, DataBox.class);
        register("jumb", "cbor", null, true,  CborContainerBox.class);          // Jumb CBOR box - has [jumd, cbor]
        register("jumb", "json", null, true,  JsonContainerBox.class);          // Jumb JSON box - has [jumd, json]
        register("jumb", "c2pa", null, true,  C2PAStore.class);
        register("jumb", "c2ma", null, true,  C2PAManifest.class);
        register("jumb", "c2cl", null, true,  C2PAClaim.class);
        register("jumb", "c2cs", null, true,  C2PASignature.class);

        register("jumb", EmbeddedFileContainerBox.SUBTYPE, null, true, EmbeddedFileContainerBox.class);
        register("uuid", "be7acfcb97a942e89c71999491e3afac", null, false, XMPBox.class);// wrong order, magnificently prioritised in XMP spec
        register("uuid", XMPBox.SUBTYPE, null, false, XMPBox.class); // byte order FFS
        register("uuid", C2PAContainerBox.SUBTYPE, null, false, C2PAContainerBox.class);

        // special assertions
        register("jumb", EmbeddedFileContainerBox.SUBTYPE, "c2pa.thumbnail", true, C2PA_AssertionThumbnail.class);
        register("jumb", "cbor", "c2pa.actions", true, C2PA_AssertionActions.class);
        register("jumb", "cbor", "c2pa.hash.data", true, C2PA_AssertionHashData.class);
        register("jumb", "cbor", "c2pa.hash.bmff", true, C2PA_AssertionHashBMFF.class);
        register("jumb", "cbor", "c2pa.hash.bmff.v2", true, C2PA_AssertionHashBMFF.class);
        register("jumb", "cbor", "c2pa.soft-binding", true, C2PA_AssertionSoftBinding.class);
        register("jumb", "cbor", "c2pa.cloud-data", true, C2PA_AssertionCloudData.class);
        register("jumb", "cbor", "c2pa.ingredient", true, C2PA_AssertionIngredient.class);
        register("jumb", "cbor", "c2pa.depthmap.GDepth", true, C2PA_AssertionGDepth.class);
        register("jumb", "cbor", "c2pa.endorsement", true, C2PA_AssertionEndorsement.class);
        register("jumb", "json", "stds.exif", true, C2PA_AssertionSchema.class);
        register("jumb", "json", "stds.iptc", true, C2PA_AssertionSchema.class);
        register("jumb", "json", "stds.schema-org.ClaimReview", true, C2PA_AssertionSchema.class);
        register("jumb", "json", "stds.schema-org.CreativeWork", true, C2PA_AssertionSchema.class);
    }


  /*
    private static Map<String,Field> tag2field = new HashMap<String,Field>();
    private static {
        tag2field.put("moov.udta.meta.ilst.\u00A9alb", Field.Album);
        tag2field.put("moov.udta.meta.ilst.aART", Field.AlbumArtist);
        tag2field.put("moov.udta.meta.ilst.\u00A9ART", Field.AlbumArtist);
        tag2field.put("moov.udta.meta.ilst.\u00A9cmt", Field.Comment);
        tag2field.put("moov.udta.meta.ilst.\u00A9day", Field.Year);
        tag2field.put("moov.udta.meta.ilst.\u00A9nam", Field.Title);
        tag2field.put("moov.udta.meta.ilst.\u00A9wrt", Field.Composer);
        tag2field.put("moov.udta.meta.ilst.\u00A9too", Field.Encoder);
        tag2field.put("moov.udta.meta.ilst.cprt", Field.Copyright);
        tag2field.put("moov.udta.meta.ilst.\u00A9grp", Field.Group);
        tag2field.put("moov.udta.meta.ilst.\u00A9gen", Field.Genre);
        tag2field.put("moov.udta.meta.ilst.gnre", Field.Genre);
        tag2field.put("moov.udta.meta.ilst.trkn", Field.Track);
        tag2field.put("moov.udta.meta.ilst.disk", Field.Disc);
        tag2field.put("moov.udta.meta.ilst.tmpo", Field.BPM);
        tag2field.put("moov.udta.meta.ilst.cpil", Field.Compilation);
        tag2field.put("moov.udta.meta.ilst.covr", Field.ImageOffset);
        tag2field.put("moov.udta.meta.ilst.pgap", Field.Gapless);
        tag2field.put("moov.udta.meta.ilst.com.apple.iTunes.iTunNORM", Field.Gain);
        tag2field.put("moov.udta.meta.ilst.com.apple.iTunes.iTunes_CDDB_1", Field.CDDB);
    }
    */

}
