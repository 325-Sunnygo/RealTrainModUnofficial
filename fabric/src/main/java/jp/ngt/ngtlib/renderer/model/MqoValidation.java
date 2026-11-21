package jp.ngt.ngtlib.renderer.model;
/** Prevent a damaged model from silently becoming an incomplete graph. */
final class MqoValidation {
    private MqoValidation() {}
    static void validate(String text,String filename) {
        int chunk=0,remaining=0,lineNumber=0;
        try {
            for(String raw:text.split("\\R")) {
                lineNumber++;String line=raw.trim();if(line.isEmpty() || line.startsWith("//") || line.equals("{"))continue;
                if(chunk!=0) {
                    if(line.startsWith("}")) {if(remaining!=0)throw new IllegalArgumentException("Incomplete vertex/face list");chunk=0;continue;}
                    if(--remaining<0)throw new IllegalArgumentException("Too many vertex/face records");
                    String[] words=line.split("\\s+");
                    if(chunk==1) {
                        if(words.length<2)throw new IllegalArgumentException("Missing vertex coordinates");
                        for(int i=0;i<Math.min(3,words.length);i++)if(!Float.isFinite(Float.parseFloat(words[i])))throw new IllegalArgumentException("Non-finite vertex");
                    } else {
                        int count=Integer.parseInt(words[0]);
                        var vertices=java.util.regex.Pattern.compile("V\\(([^)]*)\\)").matcher(line);
                        if(!vertices.find())throw new IllegalArgumentException("Missing face indices");
                        String[] indices=vertices.group(1).trim().split("\\s+");if(indices.length!=count)throw new IllegalArgumentException("Face index count mismatch");
                        for(String index:indices)if(Integer.parseInt(index)<0)throw new IllegalArgumentException("Negative MQO index");
                        var uv=java.util.regex.Pattern.compile("UV\\(([^)]*)\\)").matcher(line);
                        if(uv.find()) {String[] values=uv.group(1).trim().split("\\s+");if(values.length!=count*2)throw new IllegalArgumentException("UV count mismatch");for(String value:values)if(!Float.isFinite(Float.parseFloat(value)))throw new IllegalArgumentException("Non-finite UV");}
                    }
                } else if(line.startsWith("vertex ") || line.startsWith("face ")) {
                    chunk=line.startsWith("vertex ")?1:2;remaining=Integer.parseInt(line.split("\\s+")[1]);if(remaining<0)throw new IllegalArgumentException("Negative list size");
                }
            }
            if(chunk!=0)throw new IllegalArgumentException("Unclosed vertex/face list");
        } catch(RuntimeException failure) {throw new ModelFormatException("Invalid MQO "+filename+" at line "+lineNumber,failure);}
    }
}
