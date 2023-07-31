package com.bfo.box;

import java.io.*;
import java.util.*;
import com.bfo.json.*;

/**
 * <p>
 * The <a href="https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_c2pa_box_details">store box</a>
 * is the top-level box for any C2PA object. It contains one or more {@link C2PAManifest manifest boxes}
 * which must be added to {@link #getManifests}
 * </p><p>
 * Here's an example showing how to create a new C2PAStore. It uses the C2PAHelper class
 * to embed the store in JPEG file.
 * </p>
 * <pre style="background: #eee; border: 1px solid #888; font-size: 0.8em">
 *  C2PAStore c2pa = new C2PAStore();
 *  C2PAManifest manifest = new C2PAManifest("urn:manifestid");
 *  c2pa.getManifests().add(manifest);
 *  C2PAClaim claim = manifest.getClaim();
 *  C2PASignature sig = manifest.getSignature();
 *  claim.setFormat("image/jpeg");
 *  claim.setInstanceID("urn:instanceid");
 *  manifest.getAssertions().add(new C2PA_AssertionHashData());
 *  Json schemaJson = Json.read("{\"@context\":\"https://schema.org/\",\"@type\":\"VideoObject\",\"name\":\"LearnJSON-LD\",\"author\":{\"@type\":\"Person\",\"name\":\"Foo Bar\"}}");
 *  manifest.getAssertions().add(new C2PA_AssertionSchema("stds.schema-org.CreativeWork", schemaJson));
 *
 *  KeyStore keystore = KeyStore.getInstance("PKCS12");
 *  keystore.load(new FileInputStream(keystorefile), keystorepassword);
 *  PrivateKey key = (PrivateKey)keystore.getKey(keystorealias, keystorepassword);
 *  List&lt;gX509Certificate&gt; certs = new ArrayList&lt;X509Certificate&gt;();
 *  for (Certificate c : keystore.getCertificateChain(keystorealias)) {
 *      certs.add((X509Certificate)c);
 *  }
 *  manifest.getSignature().setSigner(key, certs);
 *  Json j = C2PAHelper.readJPEG(new FileInputStream("unsigned.jpg"));
 *  C2PAHelper.writeJPEG(j, c2pa, new FileOutputStream("signed.jpg"));
 * </pre>
 * and here's an example showing how to read the file back and verify it
 * <pre style="background: #eee; border: 1px solid #888; font-size: 0.8em">
 *  Json j = C2PAHelper.readJPEG(new FileInputStream("signed.jpg"));
 *  if (j.has("c2pa")) {
 *    C2PAStore c2pa = (C2PAStore)new BoxFactory().load(j.bufferValue("c2pa"));
 *    C2PAManifest manifest = c2pa.getActiveManifest();
 *    manifest.setInputStream(new FileInputStream("signed.jpg"));
 *    boolean valid = true;
 *    for (C2PAStatus status : manifest.getSignature().verify(null)) {
 *      System.out.println(status);
 *      if (status.isError()) {
 *        valid = false;
 *      }
 *    }
 *  }
 * </pre>
 * @see C2PAHelper
 * @see BoxFactory
 * @since 5
 */
public class C2PAStore extends JUMBox {

    /**
     * Create a new C2PAStore
     */
    public C2PAStore() {
    }

    /**
     * Return a live list of the {@link C2PAManifest manifest} objects in this store
     * @return the list of manifests
     */
    public List<C2PAManifest> getManifests() {
        if (first() == null) {
           add(new JumdBox("c2pa", "c2pa"));
        }
        return new BoxList<C2PAManifest>(this, C2PAManifest.class);
    }

    /**
     * Return the active manifest, which is just the last one
     * in the list returned by {@link #getManifests}
     * @return the active manifest
     */
    public C2PAManifest getActiveManifest() {
        List<C2PAManifest> l = getManifests();
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    /**
     * Return a representation of this store as a single Json object.
     * The returned value is not live, changes will not affect this object.
     * The object should be largely comparable to the output from <code>c2patool</code> 
     * @return the json
     */
    public Json toJson() {
        Json out = Json.read("{\"manifests\":{}}");
        for (C2PAManifest manifest : getManifests()) {
            Json m = Json.read("{}");
            out.get("manifests").put(manifest.label(), m);
            m.put("claim", manifest.getClaim().getBox().cbor().duplicate().sort());
            Json al = Json.read("{}");
            m.put("assertion_store", al);
            for (C2PA_Assertion assertion : manifest.getAssertions()) {
                if (assertion instanceof JsonContainerBox) {
                    al.put(assertion.asBox().label(), ((JsonContainerBox)assertion.asBox()).getBox().json().duplicate().sort());
                } else if (assertion instanceof CborContainerBox) {
                    al.put(assertion.asBox().label(), ((CborContainerBox)assertion.asBox()).getBox().cbor().duplicate().sort());
                } else if (assertion instanceof EmbeddedFileContainerBox) {
                    al.put(assertion.asBox().label(), ((EmbeddedFileContainerBox)assertion.asBox()).data());
                } else {
                    al.put(assertion.asBox().label(), assertion.asBox().getEncoded());
                }
            }
            COSE signature = manifest.getSignature().cose();
            if (signature.isInitialized()) {
                m.put("signature.alg", signature.getAlgorithm(0));
            }
            if (!signature.getCertificates().isEmpty()) {
                m.put("signature.issuer", signature.getCertificates().get(0).getSubjectX500Principal().getName());
            }
            m.put("signature.length", signature.toCbor().limit());
            m.put("signature.cose", signature);
        }
        return out;
    }

}
