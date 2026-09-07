package tca.model;

import java.util.*;

/** Parsed business object (twClass, id 12.*). */
public class BusinessObjectModel {
    public TwxObject object;
    public String id, name, documentation = "";
    public boolean shared, system;
    public final List<Property> properties = new ArrayList<>();
    public static class Property { public String name = "", classRef = "", typeName = "", documentation = "", defaultValue = ""; public boolean isList, required; }
}
