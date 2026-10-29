package jp.ngt.ngtlib.util;

/** Legacy pack.json schema. Version strings are opaque, as in the original checker. */
public class PackInfo {
    public String name,homepage,updateURL,version;
    public PackInfo(String name,String homepage,String updateURL,String version){this.name=name;this.homepage=homepage;this.updateURL=updateURL;this.version=version;}
}
