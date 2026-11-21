package com.portofino.realtrainmodunofficial.modelpack.transfer;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Shared, bounded file transfer. Only explicitly named ModelPack_*.zip archives are served. */
public final class ServerPackStorage {
    public static final int CHUNK=16384,MAX_FILES=128;
    public static final long MAX_FILE=128L*1024*1024,MAX_TOTAL=512L*1024*1024;
    private static volatile Path activeDirectory;
    private ServerPackStorage() {}
    public record Pack(String name,long size,String sha256) {
        public Pack {
            validateName(name);
            if(size<=0||size>MAX_FILE||sha256==null||!sha256.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid model pack metadata");
        }
    }
    public static void validateName(String name) {
        if(name==null||name.length()>200||!name.startsWith("ModelPack_")||!name.endsWith(".zip")
            ||name.contains("..")||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf(':')>=0
            ||name.chars().anyMatch(c->Character.isISOControl(c)))throw new IllegalArgumentException("Invalid model pack filename");
    }
    public static Path activeDirectory(){return activeDirectory;}
    public static void activate(Path directory){activeDirectory=directory;}
    public static List<Path> withActiveRoot(List<Path> roots) {
        var result=new ArrayList<>(roots);if(activeDirectory!=null)result.add(activeDirectory);return result;
    }
    public static String sha256(Path file) throws IOException {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            try(var input=Files.newInputStream(file)){byte[] bytes=new byte[32768];int count;while((count=input.read(bytes))>=0)digest.update(bytes,0,count);}
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    public static Map<Pack,Path> discover(Path gameDir) throws IOException {
        var result=new LinkedHashMap<Pack,Path>();long total=0;
        for(Path directory:List.of(gameDir.resolve("mods"),gameDir.resolve("mods/modelpacks"),gameDir.resolve("modelpacks"))) {
            if(!Files.isDirectory(directory))continue;
            try(var paths=Files.list(directory)) {
                for(Path path:paths.sorted().toList()) {
                    String name=path.getFileName().toString();
                    try{validateName(name);}catch(IllegalArgumentException ignored){continue;}
                    if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))continue;
                    long size=Files.size(path);
                    if(size<=0||size>MAX_FILE||total+size>MAX_TOTAL||result.size()>=MAX_FILES)continue;
                    if(result.keySet().stream().anyMatch(p->p.name.equals(name)))continue;
                    result.put(new Pack(name,size,sha256(path)),path);total+=size;
                }
            }
        }
        return result;
    }
    private static void validateZip(Path path,java.nio.charset.Charset charset) throws IOException {
        try(var zip=new java.util.zip.ZipFile(path.toFile(),charset)){if(zip.size()==0)throw new IOException("Empty model pack archive");}
    }
    public static final class Receiver implements AutoCloseable {
        private final Path directory;
        private final Pack pack;
        private final Path temporary;
        private long offset;
        private boolean complete;
        public Receiver(Path directory,Pack pack) throws IOException {
            this.directory=directory;this.pack=pack;Files.createDirectories(directory);
            temporary=Files.createTempFile(directory,".download-",".part");
        }
        public synchronized boolean accept(long position,byte[] data) throws IOException {
            if(complete||position!=offset||data.length==0||data.length>CHUNK||offset+data.length>pack.size)throw new IOException("Out-of-order or oversized model pack chunk");
            Files.write(temporary,data,StandardOpenOption.APPEND);offset+=data.length;
            if(offset==pack.size) {
                if(!sha256(temporary).equals(pack.sha256))throw new IOException("Model pack digest mismatch");
                try { validateZip(temporary,java.nio.charset.StandardCharsets.UTF_8); }
                catch(IllegalArgumentException | java.util.zip.ZipException invalidEncoding) {validateZip(temporary,java.nio.charset.Charset.forName("MS932"));}
                Files.move(temporary,directory.resolve(pack.name),StandardCopyOption.REPLACE_EXISTING);
                complete=true;
            }
            return complete;
        }
        public long offset(){return offset;}
        @Override public synchronized void close() throws IOException {if(!complete)Files.deleteIfExists(temporary);}
    }
}
