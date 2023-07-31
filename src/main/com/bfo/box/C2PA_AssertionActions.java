package com.bfo.box;

import com.bfo.json.*;
import java.util.*;

/**
 * A C2PA Assertion for the "c2pa.actions" type
 * @since 5
 */
public class C2PA_AssertionActions extends CborContainerBox implements C2PA_Assertion {

    /**
     * Create a new assertion
     */
    public C2PA_AssertionActions() {
        super("cbor", "c2pa.actions");
    }

    /**
     * Add a new action to the list. This assertion must have already been added to
     * the manifest. An ingredient can be specified as a reference, and if so it too
     * must be in the manifest: its details will be added to the parameters.
     * @param action the action name, eg "c2pa.opened"
     * @param ingredient the ingredient to refer to, or null.
     * @param parameters any extra parameters, or null
     */
    public void add(String action, C2PA_AssertionIngredient ingredient, Json parameters) {
        if (action == null) {
            throw new IllegalArgumentException("action is null");
        }
        if (getManifest() == null) {
            throw new IllegalArgumentException("not in manifest");
        }
        Json actions = cbor().get("actions");
        if (actions == null) {
            cbor().put("actions", actions = Json.read("[]"));
        }
        Json j = Json.read("{}"); 
        j.put("action", action);
        if (ingredient != null) {
            String url = getManifest().find(ingredient);
            if (url == null || url.startsWith("self#jumbf=/")) {
                throw new IllegalArgumentException("ingredient not in manifest");
            }
            if (ingredient.cbor().isString("instanceID")) {
                j.put("instanceID", ingredient.cbor().get("instanceID").value());
            }
            if (parameters == null) {
                parameters = Json.read("{}");
            }
            Json jj = Json.read("{}");
            jj.put("url", url);
            C2PASignature.digestHashedURL(jj, getManifest(), true, true);
            parameters.put("ingredient", jj);
        }
        if (parameters != null) {
            j.put("parameters", parameters);
        }
        actions.put(actions.size(), j);
    }

    @Override public List<C2PAStatus> verify() {
        // For each action in the actions list:
        //
        // If the action field is c2pa.opened, c2pa.placed, c2pa.removed,
        // c2pa.repackaged, or c2pa.transcoded:.
        //
        // * Check the ingredient field that is a member of the parameters object for the
        //   presence of a JUMBF URI. If the JUMBF URI is not present, or cannot be resolved
        //   to the related ingredient assertion, the claim must be rejected with a failure
        //   code of assertion.action.ingredientMismatch..
        //
        // * Follow the JUMBF URI link in the ingredient field to the ingredient
        //   assertion. Check that the URI link resolves to an assertion in the active
        //   manifest. If it does not, the claim must be rejected with a failure code of
        //   assertion.action.ingredientMismatch.
        //
        // * For c2pa.opened, c2pa.repackaged, or c2pa.transcoded: Check that the value
        //   of the relationship field is parentOf. If it is not, the claim must be rejected
        //   with a failure code of assertion.action.ingredientMismatch..
        //
        // * For c2pa.placed or c2pa.removed: Check that the value of the relationship
        //   field is componentOf. If it is not, the claim must be rejected with a failure
        //   code of assertion.action.ingredientMismatch.
        //
        // * Check the c2pa_manifest field in the ingredient assertion for the presence
        //   of a hashed URI. If the hashed URI is not present, or cannot be resolved to a
        //   manifest, the claim must be rejected with a failure code of
        //   assertion.action.ingredientMismatch.
        //
        //  -- https://c2pa.org/specifications/specifications/1.2/specs/C2PA_Specification.html#_assertion_validation

        final List<C2PAStatus> status = new ArrayList<C2PAStatus>();
        Json actions = cbor().get("actions");
        for (int i=0;i<actions.size();i++) {
            Json action = actions.get(i);
            String type = action.stringValue("action");
            if (Arrays.asList("c2pa.opened", "c2pa.placed", "c2pa.removed", "c2pa.repackaged", "c2pa.transcoded").contains(type)) {
                String url = action.hasPath("parameters.ingredient.url") ? action.getPath("parameters.ingredient").stringValue("url") : null;
                JUMBox box = getManifest().find(url);
                if (box == null) {
                    status.add(new C2PAStatus(C2PAStatus.Code.assertion_action_ingredientMismatch, "action[" + i + "] \"" + type + "\" ingredient \"" + url + "\" not found", getManifest().find(this), null));
                } else if (!(box instanceof C2PA_AssertionIngredient && ((C2PA_Assertion)box).getManifest() == getManifest())) {
                    status.add(new C2PAStatus(C2PAStatus.Code.assertion_action_ingredientMismatch, "action[" + i + "] \"" + type + "\" ingredient \"" + url + "\" in different manifest", getManifest().find(this), null));
                } else {
                    C2PA_AssertionIngredient ingredient = (C2PA_AssertionIngredient)box;
                    String relationship = ingredient.cbor().stringValue("relationship");
                    if (Arrays.asList("c2pa.opened", "c2pa.repackaged", "c2pa.transcoded").contains(type) && !"parentOf".equals(relationship)) {
                        status.add(new C2PAStatus(C2PAStatus.Code.assertion_action_ingredientMismatch, "action[" + i + "] \"" + type + "\" ingredient \"" + url + "\" relationship \"" + relationship + "\"", getManifest().find(this), null));
                    } else if (Arrays.asList("c2pa.placed", "c2pa.removed").contains(type) && !"componentOf".equals(relationship)) {
                        status.add(new C2PAStatus(C2PAStatus.Code.assertion_action_ingredientMismatch, "action[" + i + "] \"" + type + "\" ingredient \"" + url + "\" relationship \"" + relationship + "\"", getManifest().find(this), null));
                    } else if (ingredient.hasTargetManifest()) {
                        C2PAManifest target = ingredient.getTargetManifest();
                        if (target == null) {
                        status.add(new C2PAStatus(C2PAStatus.Code.assertion_action_ingredientMismatch, "action[" + i + "] \"" + type + "\" ingredient \"" + url + "\" manifest \"" + ingredient.getTargetManifestURL() + "\" not found", getManifest().find(this), null));
                        }
                    }
                }
            }
        }
        return status;
    }

}
