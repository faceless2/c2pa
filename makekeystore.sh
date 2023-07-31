#!/bin/sh

# generate a keystore suitable for use with C2PA; non-trivial
# as the certs need certain properties and cannot be self-signed.
# We need to make a CA cert too and use that to sign the original

STORETYPE=pkcs12
KEYSTORE=testkeystore.${STORETYPE}
PASSWORD=password
ALIAS=testkey

KEYALG=EC
ALG=SHA256withECDSA
ALGPARAMS="-groupname secp256r1"
ALIASCA=testca
TMPFILE=/tmp/file.$$

ARGS="-keystore ${KEYSTORE} -storetype ${STORETYPE} -storepass ${PASSWORD} -keypass ${PASSWORD}"

rm -f ${KEYSTORE}
keytool -genkeypair ${ARGS} -alias ${ALIAS}   -keyalg ${KEYALG} -sigalg ${ALG} ${ALGPARAMS} -dname 'CN=BFO Test Signer, C=GB'
keytool -genkeypair ${ARGS} -alias ${ALIASCA} -keyalg ${KEYALG} -sigalg ${ALG} ${ALGPARAMS} -ext KeyUsage:critical=keyCertSign -ext BC:critical=CA:true -dname 'CN=BFO Test CA, C=GB'
keytool -certreq ${ARGS} -alias ${ALIAS} -file ${TMPFILE}.csr
keytool -gencert ${ARGS} -infile ${TMPFILE}.csr -alias ${ALIASCA} -outfile ${TMPFILE}.crt -ext KeyUsage:critical=digitalSignature,nonRepudiation -ext ExtendedKeyUsage=1.3.6.1.5.5.7.3.4 -ext BC:critical=CA:false
keytool -importcert ${ARGS} -alias ${ALIAS} -file ${TMPFILE}.crt
rm ${TMPFILE}.csr ${TMPFILE}.crt
