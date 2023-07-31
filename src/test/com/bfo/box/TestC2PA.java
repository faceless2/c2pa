package com.bfo.box;

import com.bfo.json.*;
import java.util.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.text.*;
import java.io.*;

public class TestC2PA {

    private static final String[] names = new String[] { 
        "resources/adobe-20220124-C.jpg.c2pa",
        "resources/adobe-20220124-CA.jpg.c2pa",
        "resources/adobe-20220124-CACA.jpg.c2pa",
        "resources/adobe-20220124-CACAICAICICA.jpg.c2pa",
        "resources/adobe-20220124-CAI.jpg.c2pa",
        "resources/adobe-20220124-CAIAIIICAICIICAIICICA.jpg.c2pa",
        "resources/adobe-20220124-CAICA.jpg.c2pa",
        "resources/adobe-20220124-CAICAI.jpg.c2pa",
        "resources/adobe-20220124-CI.jpg.c2pa",
        "resources/adobe-20220124-CICA.jpg.c2pa",
        "resources/adobe-20220124-CICACACA.jpg.c2pa",
        "resources/adobe-20220124-CIE-sig-CA.jpg.c2pa",
        "resources/adobe-20220124-CII.jpg.c2pa",
        "resources/adobe-20220124-E-clm-CAICAI.jpg.c2pa",
        "resources/adobe-20220124-E-dat-CA.jpg.c2pa",
        "resources/adobe-20220124-E-sig-CA.jpg.c2pa",
        "resources/adobe-20220124-E-uri-CA.jpg.c2pa",
        "resources/adobe-20220124-E-uri-CIE-sig-CA.jpg.c2pa",
        "resources/adobe-20220124-XCA.jpg.c2pa",
        "resources/adobe-20220124-XCI.jpg.c2pa",
        "resources/adobe-20221004-ukraine_building.jpeg.c2pa",
        "resources/nikon-20221019-building.jpeg.c2pa",
        "resources/truepic-20230212-camera.jpg.c2pa",
        "resources/truepic-20230212-landscape.jpg.c2pa",
        "resources/truepic-20230212-library.jpg.c2pa"
    };

    public static void main(String[] args) throws Exception {
        System.out.println("----- BEGIN C2PA TESTS -----");
        JsonWriteOptions wo = new JsonWriteOptions().setPretty(true).setSpaceAfterColon(true).setCborDiag("hex");
        BoxFactory factory = new BoxFactory();
        for (String s : names) {
            System.out.println("  " + s);
            InputStream in = TestC2PA.class.getResourceAsStream(s);
            C2PAStore box = (C2PAStore)factory.load(in);
//            String s1 = box.toJson().toString(wo);
            String s1 = box.dump("", null).toString();
//            System.out.println(s1);
            byte[] d1 = box.getEncoded();
            boolean errorExpected = s.contains("E-") || s.contains("X");    // sketchy
            List<C2PAManifest> manifests = ((C2PAStore)box).getManifests();
            // Ony the last manifest needs testing directly - it's the "active manifest"
            manifests = Collections.<C2PAManifest>singletonList(manifests.get(manifests.size() - 1));
            for (C2PAManifest manifest : manifests) {
                manifest.setInputStream(TestC2PA.class.getResourceAsStream(s.replaceAll(".c2pa", "")));
                System.out.println("    manifest " + manifest.label());
                List<C2PAStatus> status = manifest.getSignature().verify(null);
                boolean verified = true;
                for (C2PAStatus st : status) {
                    verified &= st.isOK();
                    System.out.println("      " + st);
                }
                try {
                    if (verified && errorExpected) {
                        assert false : s + " manifest " + manifest.label()+" verified but should have failed";
                    } else if (!verified && !errorExpected) {
                        assert false : s + " manifest " + manifest.label()+" verify failed";
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            // FileOutputStream out = new FileOutputStream("/tmp/t"); out.write(bout.toByteArray()); out.close();
            box = (C2PAStore)factory.load(new ByteArrayInputStream(d1));
            String s2 = box.dump("", null).toString();
            s1 = s1.replaceAll(",\"size\":[0-9]+", "");
            s2 = s2.replaceAll(",\"size\":[0-9]+", "");
            // out = new FileOutputStream("/tmp/t1"); out.write(s1.getBytes("UTF-8")); out.close();
            // out = new FileOutputStream("/tmp/t2"); out.write(s2.getBytes("UTF-8")); out.close();
//            assert s1.equals(s2) : s2;
        }

        /*
        final String keystorefile = 
        final char[] keystorepassword = 
        final String keystorealias = 

        if (keystorefile != null && keystorealias != null && keystorepassword != null) {
            C2PAStore store = new C2PAStore();
            C2PAManifest manifest = new C2PAManifest("urn:foo");
            store.getManifests().add(manifest);
            manifest.getAssertions().add(new C2PA_AssertionSchema("stds.schema-org.CreativeWork", Json.read("{}")));
            manifest.getClaim().setFormat("application/pdf");
            manifest.getClaim().setInstanceID("urn:foo");
            manifest.getClaim().getAssertions().addAll(manifest.getAssertions());

            KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(new FileInputStream(keystorefile), keystorepassword);
            PrivateKey key = (PrivateKey)keystore.getKey(keystorealias, keystorepassword);
            List<X509Certificate> certs = new ArrayList<X509Certificate>();
            for (Certificate c : keystore.getCertificateChain(keystorealias)) {
                if (c instanceof X509Certificate) {
                    certs.add((X509Certificate)c);
                }
            }
            manifest.getSignature().sign(key, certs);
            System.out.println(store.dump("", null).toString());
        }
        */

        System.out.println("----- END C2PA TESTS -----");
    }

}
