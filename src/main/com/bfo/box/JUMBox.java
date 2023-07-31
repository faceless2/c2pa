package com.bfo.box;

import java.io.*;
import java.math.*;
import com.bfo.json.*;

/**
 * Represents a "JUMBF" ("JPEG Univesal Metadata Box Format") box as defined in
 * ISO19566 appendix A.2. The {@link Box#type} is always "jumb",
 * and this box always contains exactly one "jumd" description box (not public, but accessible with {@link Box#first})
 * and one or more content boxes to follow that.
 * @since 5
 */
public class JUMBox extends Box {

    /**
     * Create a new uninitialized box, for loading. Don't call this constructor
     */
    public JUMBox() {
        super("jumb");
    }

    /**
     * Create a new JUMBox
     * @param subtype the subtype which will be set on the description. Required.
     * @param label the label which will be set on the description. Theoretically optional, but effectively required for C2PA
     */
    public JUMBox(String subtype, String label) {
        super("jumb");
        add(new JumdBox(subtype, label));
    }

    /**
     * Given a JUMBox path eg "self#jumbf=a/b" or "jumbf=a/b" or "a/b", return 
     * the child that matches that box. The returned child will always be
     * a JUMBox, or null if no such child exists.
     * @param path the JUMBF path to match
     * @return the Box matching that path
     */
    public JUMBox find(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("self#")) {
            path = path.substring(5);
        }
        if (path.startsWith("jumbf=")) {
            path = path.substring(6);
        }
        Box ctx = this;
        if (path.charAt(0) == '/') {
            // absolute path
            while (ctx.parent() != null) {
                ctx = ctx.parent();
            }
            // Now we have (eg "/c2pa/foo" and ctx should have "c2pa" on it
            String first = path.substring(1, path.indexOf("/", 1));
            if (!(ctx instanceof JUMBox && first.equals(((JUMBox)ctx).label()))) {
                return null;
            }
            path = path.substring(path.indexOf("/", 1) + 1);
        }
        String[] l = path.split("/");
        if (l.length == 0) {
            return null;
        }
        for (int i=0;i<l.length;i++) {
            String search = l[i];
            Box child = ((JUMBox)ctx).first().next();
            while (child instanceof JUMBox) {
                String label = ((JUMBox)child).label();
                if (search.equals(label)) {
                    break;
                }
                child = child.next();
            }
            if (!(child instanceof JUMBox)) {
                return null;
            }
            ctx = child;
        }
        if (ctx != null && !((JumdBox)((JUMBox)ctx).first()).isRequestable()) {
            throw new IllegalStateException("Box " + ctx + " is not requestable: " + ctx.first());
        }
        return (JUMBox)ctx;
    }

    /**
     * Given a descendent of this box, return the JUMBF path (with the leading "self#jumbf=")
     * that would be used to request it, or <code>null</code> if it's not requestable or not found.
     * It will be returned as a relative path if possible, an absolute one if not.
     * @param child the descendent Box
     * @return the JUMBF path to request that box from this node
     */
    public String find(JUMBox child) {
        if (child == null) {
            return null;
        }
        String s = null;
        JUMBox b = child;
        while (b != null) {
            s = s == null ? b.label() : b.label() + "/" + s;
            if (b.parent() == null) {
                return "self#jumbf=/" + s;
            } else {
                b = (JUMBox)b.parent();
                if (b == this) {
                    return "self#jumbf=" + s;
                }
            }
        }
        return null;
    }

    private JumdBox desc() {
        return (JumdBox)first();
    }

    /**
     * Return the subtype of this box's "description" box, either a 4-character alpha-numeric
     * string (if the type is a "standard" subtype) or a 32-character hex string
     * @return the subtype
     */
    public String subtype() {
        JumdBox desc = desc();
        return desc == null ? null : desc.subtype();
    }

    /**
     * Return the label from this box's "description" box, or <code>null</code> if there is none.
     * @return the label
     */
    public String label() {
        JumdBox desc = desc();
        return desc == null ? null : desc.label();
    }

}
