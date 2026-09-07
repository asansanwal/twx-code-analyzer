package tca.parse;

import java.util.*;
import tca.model.*;
import tca.util.Xml;

public final class BusinessObjectParser {
    private BusinessObjectParser() {}
    public static BusinessObjectModel parse(TwxObject o) {
        BusinessObjectModel b = new BusinessObjectModel(); b.object = o; b.id = o.id; b.name = o.name; String x = o.xml;
        b.documentation = Xml.text(x, "description"); b.shared = Xml.bool(x, "shared"); b.system = Xml.bool(x, "isSystem");
        String def = Xml.textRaw(x, "definition");
        for (String p : Xml.blocks(def, "property")) { BusinessObjectModel.Property pr = new BusinessObjectModel.Property(); pr.name = Xml.text(p, "name"); pr.classRef = Xml.text(p, "classRef"); pr.isList = Xml.bool(p, "arrayProperty"); pr.required = Xml.bool(p, "propertyRequired"); pr.documentation = Xml.text(p, "description"); pr.defaultValue = Xml.text(p, "propertyDefault"); b.properties.add(pr); }
        return b;
    }
}
