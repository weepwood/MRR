package com.zjcxph.imgapi.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

final class AuditSpoolKeys {
    private AuditSpoolKeys(){}
    static void requireIndependent(Environment env,String first,String second,ObjectMapper mapper)throws IOException{
        List<byte[]> a=new ArrayList<>(),b=new ArrayList<>();
        try{
            collect(env,first,mapper,a);collect(env,second,mapper,b);
            for(byte[] left:a)for(byte[] right:b)if(Arrays.equals(left,right))
                throw new IOException("Audit key domains overlap");
        }catch(IOException|RuntimeException e){throw new IOException("Audit key domains must be independent");}
        finally{a.forEach(k->Arrays.fill(k,(byte)0));b.forEach(k->Arrays.fill(k,(byte)0));}
    }

    private static void collect(Environment env,String prefix,ObjectMapper mapper,List<byte[]> target)throws IOException{
        String legacy=env.getProperty(prefix+"_KEY");
        if(legacy!=null)target.add(Base64.getDecoder().decode(legacy));
        String encoded=env.getProperty(prefix+"_KEY_RING");
        if(encoded==null)return;
        var object=mapper.readTree(encoded);
        if(object==null||!object.isObject()||object.size()>8)throw new IOException("Invalid audit key ring");
        var fields=object.fields();
        while(fields.hasNext()){
            var field=fields.next();
            if(!field.getValue().isTextual())throw new IOException("Invalid audit key ring");
            target.add(Base64.getDecoder().decode(field.getValue().asText()));
        }
    }

    static EncryptedAuditSpool open(Environment env,String prefix,Path directory,int records,long bytes,ObjectMapper mapper)throws IOException{
        byte[] legacy=null;
        Map<String,byte[]> ring=new HashMap<>();
        try{
            legacy=Base64.getDecoder().decode(env.getRequiredProperty(prefix+"_KEY"));
            if(legacy.length!=32||Arrays.equals(legacy,new byte[32]))throw new IOException("Invalid audit key");
            String active=env.getProperty(prefix+"_ACTIVE_KEY_ID");
            String encoded=env.getProperty(prefix+"_KEY_RING");
            if(encoded!=null){
                var object=mapper.readTree(encoded);
                if(!object.isObject()||object.size()>8)throw new IOException("Invalid audit key ring");
                var fields=object.fields();
                while(fields.hasNext()){
                    var field=fields.next();
                    if(!field.getValue().isTextual())throw new IOException("Invalid audit key ring");
                    byte[] value=Base64.getDecoder().decode(field.getValue().asText());
                    ring.put(field.getKey(),value);
                    if(value.length!=32||Arrays.equals(value,new byte[32]))throw new IOException("Invalid audit key");
                }
            }
            return new EncryptedAuditSpool(directory,legacy,ring,active,records,bytes,mapper);
        }catch(IOException|RuntimeException e){throw new IOException("Audit spool key configuration or initialization failed");}
        finally{
            if(legacy!=null)Arrays.fill(legacy,(byte)0);
            ring.values().forEach(key->Arrays.fill(key,(byte)0));
        }
    }
}
