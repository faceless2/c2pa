# Java BMFF and C2PA toolkit

# ISO BMFF and C2PA 

The BFO C2PA toolkit is a general purpose parser for the
_ISO Base Media Format_, or _BMFF_. This is a standard box model used in a number of file formats,
including MP4, JP2 etc. The parser is very general, and will not load unrecognised boxes into memory so
can be used to scan large files for metadata (which is the primary reason we use it; most of the
currently recognised boxes have a metadata focus).

```java
import com.bfo.box.*;

Box box;
BoxFactory factory = new BoxFactory();
InputStream in = new BufferedInputStream(new FileInputStream("file.mpf"));
while ((box=factory.load(in)) != null) {
    traverse(box, "");
}

void traverse(Box box, String prefix) {
    System.out.println(prefix + box);
    for (Box b=box.first();b!=null;b=b.next()) {
        traverse(box, prefix + " ");
    }
}
```

A specific use of BMFF is [C2PA](https://c2pa.org), and most of this package are
classes to read and write C2PA objects ("stores"), including a
[helper class](https://faceless2.github.io/c2pa/docs/com/bfo/box/C2PAHelper.html)
to deal with embdding them into JPEG.
While the C2PA format is built on BMFF boxes, those boxes typically contain JSON or CBOR
and the signature is COSE. So this package makes heavy use of `com.bfo.json` from [https://github.com/faceless2/json]

The [C2PAStore](https://faceless2.github.io/c2pa/docs/com/bfo/box/C2PAStore.html) class is the top
level entrypoint into the C2PA package. Here's a quick example showing how to verify C2PA embedded
in a JPEG

```java
import com.bfo.box.*;
import com.bfo.json.*;

Json json = C2PAHelper.readJPEG(new FileInputStream(file));
if (json.has("c2pa")) {
    C2PAStore c2pa = (C2PAStore)new BoxFactory().load(json.bufferValue("c2pa"));
    C2PAManifest manifest = c2pa.getActiveManifest();
    manifest.setInputStream(new FileInputStream(file));
    boolean valid = true;
    for (C2PAStatus status : manifest.getSignature().verify(keystore)) {
        System.out.println(status);
        if (status.isError()) {
            valid = false;
        }
    }
}
```

and here's how to sign a JPEG with the bare minimum single assertion.

```java
import com.bfo.box.*;
import com.bfo.json.*;

PrivateKey key = ...
List<X509Certificate> certs = ...
C2PAStore c2pa = new C2PAStore();
C2PAManifest manifest = new C2PAManifest("urn:manifestid");
c2pa.getManifests().add(manifest);
C2PAClaim claim = manifest.getClaim();
C2PASignature sig = manifest.getSignature();
claim.setFormat("image/jpeg");
claim.setInstanceID("urn:instanceid");
manifest.getAssertions().add(new C2PA_AssertionHashData());
manifest.getSignature().setSigner(key, certs);
Json json = C2PAHelper.readJPEG(new FileInputStream("unsigned.jpg"));
C2PAHelper.writeJPEG(json, c2pa, new FileOutputStream("signed.jpg"));
```

There is a `main()` method on the `C2PAHelper` class which can be used for basic
operations on JPEG files. To run it, download the [Jar](https://faceless2.github.io/c2pa/dist/bfoc2pa-1.0.jar) and
the [JSON Jar](https://faceless2.github.io/json/dist/bfojson-1.6.jar)  then
`java -cp bfojson-1.6.jar:bfoc2pa-1.0.jar com.bfo.box.C2PAHelper`

```
java com.bfo.box.C2PAHelper args...
   --help                  this help
   --verify                switch to verify mode (the default)
   --sign                  switch to signing mode
   --debug                 turn on debug to dump the c2pa store as CBOR-diag
   --boxdebug              turn on debug to dump the c2pa store as a box tree
   --nodebug               turn off --debug
   --noboxdebug            turn off --boxdebug
   --repackage             if signing a file with an existing C2PA, reference it from a 'repackage' action
   --keystore <path>       if signing, the path to Keystore to load credentials from
   --alias <name>          if signing, the alias from the keystore (default is the first one)
   --password <password>   if signing, the password to open the keystore
   --alg <algorithm>       if signing, the hash algorithm
   --creativework <path>   if signing, filename containing a JSON schema to embed
   --out <path>            if signing, filename to write signed output to (default will derive from input)
   --c2pa <path>           if signing/verifying, filename to dump the C2PA object to (default is not dumped)
   <path>                  the filename to sign or verify
```

The C2PA classes have been developed against C2PA 1.2; output from earlier versions may not verify.

NOTE: <i>The C2PA classes are under development, so changes are likely</i>

# Requirements

Requires the BFO JSON API from https://github.com/faceless2/json

-------

This code is written by the team at [bfo.com](https://bfo.com). If you like it, come and see what else we do.
