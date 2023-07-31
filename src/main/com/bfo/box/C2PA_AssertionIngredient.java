package com.bfo.box;

import com.bfo.json.*;
import java.util.*;

/**
 * A C2PA Assertion for the "c2pa.ingredient" type
 * @since 5
 */
public class C2PA_AssertionIngredient extends CborContainerBox implements C2PA_Assertion {

    /**
     * Create a new assertion
     */
    public C2PA_AssertionIngredient() {
        super("cbor", "c2pa.ingredient");
    }

    /**
     * Create a new assertion
     * @param label the label (will default to "c2pa.ingredient" if null)
     * @param json the Json to initialize the assertion with
     */
    public C2PA_AssertionIngredient(String label, Json json) {
        super("cbor", label == null ? "c2pa.ingredient" : label, json);
    }

    String getTargetManifestURL() {
        return cbor().has("c2pa_manifest") ?  cbor().get("c2pa_manifest").stringValue("url") : null;
    }

    /**
     * Set this ingredient to refer to the specified manifest. The manifest
     * must be already in the same {@link C2PAStore store} as this one.
     * @param relationship should be "parentOf" or "componentOf"
     * @param manifest the manifest
     * @param status if not null, a list of status codes to be attached as <code>validationStatus</code>.
     */
    public void setTargetManifest(String relationship, C2PAManifest manifest, List<C2PAStatus> status) {
        String url;
        if (manifest == null || getManifest() == null || manifest == getManifest() || manifest.parent() != getManifest().parent() || (url=getManifest().find(manifest)) == null) {
            throw new IllegalStateException("manifest and this assertion's manifest must be in the same store");
        }
        final Json json = cbor();
        json.remove("c2pa_manifest");
        json.remove("dc:format");
        json.remove("dc:title");
        json.remove("instanceId");
        json.remove("thumbnail");
        json.remove("validationStatus");
        json.put("relationship", relationship);
        Json j = Json.read("{}");
        j.put("url", url);
        C2PASignature.digestHashedURL(j, manifest, true, true);
        json.put("c2pa_manifest", j);
        for (String s : Arrays.asList("dc:format", "dc:title", "instanceID")) {
            if (manifest.getClaim().cbor().has(s)) {
                json.put(s, manifest.getClaim().cbor().get(s).value());
            }
        }
        if (status != null && !status.isEmpty()) {
            Json s = Json.read("[]");
            for (C2PAStatus st : status) {
                s.put(s.size(), st.toJson());
            }
            json.put("validationStatus", s);
        }
    }

    /**
     * If this ingredient has a c2pa_manifest value, return the target manifest, or null if
     * it's not specified or can't be found
     * @return the target manifest, or null
     */
    public C2PAManifest getTargetManifest() {
        String url = getTargetManifestURL();
        JUMBox box = getManifest().find(url);
        return box instanceof C2PAManifest ? (C2PAManifest)box : null;
    }

    /**
     * If this ingredient has a validationStatus value, return it as a list of {@link C2PAStatus}
     * @return the list of validation status codes, or an empty list
     */
    public List<C2PAStatus> getValidationStatus() {
        Json j = cbor().get("validationStatus");
        if (j != null && j.size() > 0) {
            List<C2PAStatus> l = new ArrayList<C2PAStatus>();
            for (int i=0;i<j.size();i++) {
                l.add(C2PAStatus.read(j.get(i)));
            }
            return Collections.<C2PAStatus>unmodifiableList(l);
        }
        return Collections.<C2PAStatus>emptyList();
    }

    boolean hasTargetManifest() {
        return cbor().has("c2pa_manifest");
    }

    /**
     * Return the specified relationship between this ingredient and the manifest it refers to
     * @return one of "parentOf", "componentOf"
     */
    public String relationship() {
        return cbor().stringValue("relationship");
    }

    @Override public List<C2PAStatus> verify() {
        final List<C2PAStatus> status = new ArrayList<C2PAStatus>();
        //
        // Validate that there are zero or one c2pa.ingredient assertions whose
        // relationship is parentOf. If there is more than one, the manifest must be
        // rejected with a failure code of manifest.multipleParents.
        //
        //     -- https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_validate_the_correct_assertions_for_the_type_of_manifest

        int count = 0;
        for (C2PA_Assertion a : getManifest().getAssertions()) {
            if (a instanceof C2PA_AssertionIngredient && "parentOf".equals(((C2PA_AssertionIngredient)a).cbor().stringValue("relationship")))  {
                count++;
            }
        }
        if (count > 1) {
            status.add(new C2PAStatus(C2PAStatus.Code.manifest_multipleParents, "manifest has multiple \"parentOf\" c2pa.ingredient assertions", getManifest().find(this), null));
            return status;
        }

        if (cbor().isMap("c2pa_manifest")) {
            if (getTargetManifest() == null) {
                status.add(new C2PAStatus(C2PAStatus.Code.claim_missing, "\"" + getTargetManifestURL() + "\" not in manifest", getManifest().find(this), null));
                return status;
            }

            // adobe-20220124-E-uri-CIE-sig-CA.jpg
            // 
            //   active manifest ends in 644a63d1f7d0: the "c2pa.ingredient" refers to another manifest,
            //   7af56501ce4b, which has been deliberately altered. So this would be invalid IFF were
            //   are to recursively validate ingredients. However it does have a "validationStatus"
            //   showing it was validated as a failure.
            // 
            // adobe-20220124-CACA.jpg
            // 
            //   active manifest ends in f85380524443: the "c2pa.ingredient" assertion refers to another
            //   manifest 7af56501ce4b with a digest "3epjVN8X1spZW0Z6TYQO/6owR7xADaDDVzeeDBOGV4g=",
            //   but that manifest doesn't digest this way. Moreover, if I change one byte in that
            //   manifest the overall signature fails, but "c2pa.ingredient" does not. So based on this,
            //   we not NOT supposed to recursively validate ingredients.
            //
            // Conclusion: recursively validating manifests is NOT done, but if there is a validationStatus
            // listed, we will report that.

            if (cbor().isList("validationStatus")) {
                Json vals = cbor().get("validationStatus");
                for (int i=0;i<vals.size();i++) {
                    C2PAStatus st = C2PAStatus.read(vals.get(i));
                    if (st.isError()) {
                        status.add(new C2PAStatus(C2PAStatus.Code.ingredient_hashedURI_mismatch, false, C2PAStatus.Code.ingredient_hashedURI_mismatch.getCode(), "referenced ingredient at \"" + getTargetManifestURL() + "\" validationStatus has error", getManifest().find(this), null, st));
                    }
                }
            }
            // If we were recursive, we'd do this
            // C2PASignature.digestHashedURL(cbor().get("c2pa_manifest"), getManifest(), true);
        }
        return status;
    }

}
