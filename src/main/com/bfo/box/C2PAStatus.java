package com.bfo.box;

import com.bfo.json.*;
import java.security.NoSuchAlgorithmException;

/**
 * A representation of a C2PA
 * <a href="https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_status_codes">status code</a>,
 * with a message and an optional URL describing its origin
 */
public class C2PAStatus {

    private final Code code;
    private final boolean success;
    private final String codeString, message, url;
    private Throwable throwable;
    private C2PAStatus referenced;

    /**
     * Create a new status based on the specified standard code
     * @param code the code
     * @param message the message, or null to use the standard one for that code
     * @param url the url of the object that the status relates to, or null
     * @param throwable an optional Throwable relating to the status
     */
    public C2PAStatus(Code code, String message, String url, Throwable throwable) {
        this(code, code.isOK(), code.getCode(), message != null ? message : code.getDescription(), url, throwable, null);
    }

    /**
     * Create a new status based on a custom code
     * @param success true if this status is {@link #isOK ok}, false if it's an error
     * @param code the code (required)
     * @param message the message (required)
     * @param url the url of the object that the status relates to, or null
     * @param throwable an optional Throwable relating to the status
     */
    public C2PAStatus(boolean success, String code, String message, String url, Throwable throwable) {
        this(null, success, code, message, url, throwable, null);
    }

    C2PAStatus(Code code, boolean success, String codeString, String message, String url, Throwable throwable, C2PAStatus referenced) {
        if (codeString == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        this.code = code;
        this.success = success;
        this.codeString = codeString;
        this.message = message;
        this.url = url;
        this.throwable = throwable;
        this.referenced = referenced;
    }

    C2PAStatus(NoSuchAlgorithmException e, String url) {
        this(Code.algorithm_unsupported, e.getMessage(), url, e);
    }


    static C2PAStatus read(Json j) {
        String codeString = j.stringValue("code");
        String url = j.stringValue("url");
        String message = j.stringValue("message");
        Code code = null;
        for (Code c : Code.values()) {
            if (c.getCode().equals(codeString)) {
                code = c;
                break;
            }
        }
        boolean success = j.isBoolean("success") ? j.booleanValue("success") : code != null ? code.isOK() : false;
        return new C2PAStatus(code, success, codeString, message, url, null, null);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        if (isError()) {
            sb.append("ERROR ");
        }
        sb.append(getCode());
        sb.append("] ");
        sb.append(getMessage());
        if (sb.charAt(sb.length() - 1) == '.') {
            sb.setLength(sb.length() - 1);
        }
        if (getURL() != null) {
            sb.append(" (at ");
            sb.append(getURL());
            sb.append(")");
        }
        return sb.toString();
    }

    /**
     * Return true if this is an error status
     * @return true if this is an error status
     */
    public boolean isError() {
        return !success;
    }

    /**
     * Return true if this is not an error status
     * @return true if this is not an error status
     */
    public boolean isOK() {
        return success;
    }

    /**
     * Return the code string
     * @return the code string
     */
    public String getCode() {
        return codeString;
    }

    /**
     * Return the Code enum, if this is a standard code, or null otherwise
     * @return the code
     */
    public Code getStandardCode() {
        return code;
    }

    /**
     * Return the message if specified, or null
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Return the URL if specified, or null
     * @return the url
     */
    public String getURL() {
        return url;
    }

    /**
     * Return the Throwable if specified, or null
     * @return the throwable
     */
    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * Return the referenced C2PAStatus if specified, or null
     * @return the referenced status
     */
    public C2PAStatus getReferenced() {
        return referenced;
    }

    /**
     * Return a representation of this object as Json
     * @return the json
     */
    public Json toJson() {
        Json j = Json.read("{}");
        if (code == null) {
            j.put("success", isOK());
        }
        j.put("code", codeString);
        if (url != null) {
            j.put("url", url);
        }
        if (message != null) {
            j.put("explanation", message);
        }
        return j;
    }

    /**
     * An enum listing the predefined
     * <a href="https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_status_codes">status code</a>
     */
    public static enum Code {

        claimSignature_validated(true, "The claim signature referenced in the ingredient's claim validated.", "C2PA Claim Signature Box"),
        signingCredential_trusted(true, "The signing credential is listed on the validator's trust list.", "C2PA Claim Signature Box"),
        timeStamp_trusted(true, "The time-stamp credential is listed on the validator's trust list.", "C2PA Claim Signature Box"),
        assertion_hashedURI_match(true, "The hash of the the referenced assertion in the ingredient's manifest matches the corresponding hash in the assertion's hashed URI in the claim.", "C2PA Assertion"),
        assertion_dataHash_match(true, "Hash of a byte range of the asset matches the hash declared in the data hash assertion.", "C2PA Assertion"),
        assertion_bmffHash_match(true, "Hash of a box-based asset matches the hash declared in the BMFF hash assertion.", "C2PA Assertion"),

        assertion_accessible(false, "A non-embedded (remote) assertion was accessible at the time of validation.", "C2PA Assertion"),
        claim_missing(false, "The referenced claim in the ingredient's manifest cannot be found.", "C2PA Claim Box"),
        claim_multiple(false, "More than one claim box is present in the manifest.", "C2PA Claim Box"),
        claim_hardBindings_missing(false, "No hard bindings are present in the claim.", "C2PA Claim Box"),
        claim_required_missing(false, "A required field is not present in the claim.", "C2PA Claim Box"),
        claim_cbor_invalid(false, "The cbor of the claim is not valid", "C2PA Claim Box"),
        ingredient_hashedURI_mismatch(false, "The hash of the the referenced ingredient claim in the manifest does not match the corresponding hash in the ingredient's hashed URI in the claim.", "C2PA Assertion"),
        claimSignature_missing(false, "The claim signature referenced in the ingredient's claim cannot be found in its manifest.", "C2PA Claim Signature Box"),
        claimSignature_mismatch(false, "The claim signature referenced in the ingredient's claim failed to validate.", "C2PA Claim Signature Box"),
        manifest_multipleParents(false, "The manifest has more than one ingredient whose relationship is parentOf.", "C2PA Claim Box"),
        manifest_update_invalid(false, "The manifest is an update manifest, but it contains a disallowed assertion, such as a hard binding or actions assertions.", "C2PA Claim Box"),
        manifest_update_wrongParents(false, "The manifest is an update manifest, but it contains either zero or multiple parentOf ingredients.", "C2PA Claim Box"),
        signingCredential_untrusted(false, "The signing credential is not listed on the validator's trust list.", "C2PA Claim Signature Box"),
        signingCredential_invalid(false, "The signing credential is not valid for signing.", "C2PA Claim Signature Box"),
        signingCredential_revoked(false, "The signing credential has been revoked by the issuer.", "C2PA Claim Signature Box"),
        signingCredential_expired(false, "The signing credential has expired.", "C2PA Claim Signature Box"),
        timeStamp_mismatch(false, "The time-stamp does not correspond to the contents of the claim.", "C2PA Claim Signature Box"),
        timeStamp_untrusted(false, "The time-stamp credential is not listed on the validator's trust list.", "C2PA Claim Signature Box"),
        timeStamp_outsideValidity(false, "The signed time-stamp attribute in the signature falls outside the validity window of the signing certificate or the TSA's certificate.", "C2PA Claim Signature Box"),
        assertion_hashedURI_mismatch(false, "The hash of the the referenced assertion in the manifest does not match the corresponding hash in the assertion's hashed URI in the claim.", "C2PA Assertion"),
        assertion_missing(false, "An assertion listed in the ingredient's claim is missing from the ingredient's manifest.", "C2PA Claim Box"),
        assertion_multipleHardBindings(false, "The manifest has more than one hard binding assertion.", "C2PA Assertion Store Box"),
        assertion_undeclared(false, "An assertion was found in the ingredient's manifest that was not explicitly declared in the ingredient's claim.", "C2PA Claim Box or C2PA Assertion"),
        assertion_inaccessible(false, "A non-embedded (remote) assertion was inaccessible at the time of validation.", "C2PA Assertion"),
        assertion_notRedacted(false, "An assertion was declared as redacted in the ingredient's claim but is still present in the ingredient's manifest.", "C2PA Assertion"),
        assertion_selfRedacted(false, "An assertion was declared as redacted by its own claim.", "C2PA Claim Box"),
        assertion_required_missing(false, "A required field is not present in an assertion.", "C2PA Assertion"),
        assertion_json_invalid(false, "The JSON(-LD) of an assertion is not valid", "C2PA Assertion"),
        assertion_cbor_invalid(false, "The cbor of an assertion is not valid", "C2PA Assertion"),
        assertion_action_ingredientMismatch(false, "An action that requires an associated ingredient either does not have one or the one specified cannot be located", "C2PA Assertion"),
        assertion_action_redacted(false, "An action assertion was redacted when the ingredient's claim was created.", "C2PA Assertion"),
        assertion_dataHash_mismatch(false, "The hash of a byte range of the asset does not match the hash declared in the data hash assertion.", "C2PA Assertion"),
        assertion_bmffHash_mismatch(false, "The hash of a box-based asset does not match the hash declared in a BMFF hash assertion.", "C2PA Assertion"),
        assertion_cloud_data_hardBinding(false, "A hard binding assertion is in a cloud data assertion.", "C2PA Assertion"),
        assertion_cloud_data_actions(false, "An update manifest contains a cloud data assertion referencing an actions assertion.", "C2PA Assertion"),
        algorithm_unsupported(false, "The value of an alg header, or other header that specifies an algorithm used to compute the value of another field, is unknown or unsupported.", "C2PA Claim Box or C2PA Assertion"),
        general_error(false, "A value to be used when there was an error not specifically listed here.", "C2PA Claim Box or C2PA Assertion");

        private final boolean valid;
        private final String description, src, tostring;

        Code(boolean valid, String description, String src) {
            this.valid = valid;
            this.description = description;
            this.src = src;
            String name = name();
            name = name.replaceAll("_", ".");
            name = name.replaceAll("cloud_data", "cloud-data");
            this.tostring = name;
        }

        /**
         * Return the official code of the status code
         * @return the code
         */
        public String toString() {
            return tostring;
        }

        /**
         * Return the official code of the status code
         * @return the code
         */
        public String getCode() {
            return tostring;
        }

        /**
         * Return true if this status code is an error
         * @return whether this status is an error
         */
        public boolean isError() {
            return !valid;
        }

        /**
         * Return true if this status code is not an error
         * @return whether this status is not an error
         */
        public boolean isOK() {
            return valid;
        }

        /**
         * Return the description from the specification for this status code
         * @return the description
         */
        public String getDescription() {
            return description;
        }
    }

}
