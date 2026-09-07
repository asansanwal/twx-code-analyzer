package tca.parse;

import java.util.*;
import java.util.regex.*;
import tca.model.*;
import tca.util.Xml;

public final class CoachViewParser {
    private CoachViewParser() {}
    public static CoachViewModel parse(TwxObject o) {
        CoachViewModel c = new CoachViewModel(); c.object = o; c.id = o.id; c.name = o.name; String x = o.xml;
        c.documentation = Xml.text(x, "description"); c.prototypeFunc = Xml.bool(x, "isPrototypeFunc"); c.template = Xml.bool(x, "isTemplate"); c.hasLabel = Xml.bool(x, "hasLabel"); c.paletteIcon = Xml.text(x, "paletteIcon"); c.previewHtml = Xml.text(x, "previewAdvHtml"); c.previewJs = Xml.text(x, "previewAdvJs");
        c.layoutXml = Xml.text(x, "layout"); c.layoutItems = BpdParser.count(c.layoutXml, "<ns2:layoutItem ") + BpdParser.count(c.layoutXml, "<layoutItem ");
        Matcher vm = Pattern.compile("<(?:ns2:)?viewUUID>([^<]*)</(?:ns2:)?viewUUID>").matcher(c.layoutXml); while (vm.find()) c.childViewRefs.add(vm.group(1));
        for (String b : Xml.blocks(x, "bindingType")) { c.bindingName = Xml.attrOf(b.substring(0, b.indexOf('>')), "name"); c.bindingType = Xml.text(b, "classId"); c.bindingList = Xml.bool(b, "isList"); }
        for (String b : Xml.blocks(x, "configOption")) { CoachViewModel.Option op = new CoachViewModel.Option(); op.name = Xml.attrOf(b.substring(0, b.indexOf('>')), "name"); op.label = Xml.text(b, "label"); op.propertyType = Xml.text(b, "propertyType"); op.classRef = Xml.text(b, "classId"); op.isList = Xml.bool(b, "isList"); op.description = Xml.text(b, "description"); op.groupName = Xml.text(b, "groupName"); c.options.add(op); }
        for (String tag : new String[] {"loadJsFunction", "unloadJsFunction", "viewJsFunction", "changeJsFunction", "validateJsFunction", "collaborationJsFunction"}) { String code = Xml.text(x, tag); if (!code.trim().isEmpty()) c.scripts.add(new CoachViewModel.Script("handler", tag.replace("JsFunction", ""), code)); }
        for (String b : Xml.blocks(x, "inlineScript")) { String type = Xml.text(b, "scriptType"), code = Xml.text(b, "scriptBlock"), name = Xml.attrOf(b.substring(0, b.indexOf('>')), "name"); if (!code.trim().isEmpty()) c.scripts.add(new CoachViewModel.Script("JS".equals(type) ? "inline" : "CSS".equals(type) ? "css" : "html", name, code)); }
        for (String b : Xml.blocks(x, "resource")) { String fp = Xml.text(b, "filePath"); c.resources.add(fp.isEmpty() ? Xml.text(b, "assetUuid") : fp); }
        for (String m : Xml.texts(x, "moduleId")) c.amdModules.add(m);
        return c;
    }
}
