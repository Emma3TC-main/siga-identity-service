import java.nio.file.*;
import java.security.*;
import java.util.*;
public class SetupKeys {
  public static void main(String[] args) throws Exception {
    Path root=args.length==0 ? Path.of(".") : Path.of(args[0]);
    Path env=root.resolve(".env");
    Path dir=root.resolve(".secrets");
    if(Files.exists(dir.resolve("private.pem"))||Files.exists(dir.resolve("public.pem"))||Files.exists(env)) {
      if(!Files.exists(dir.resolve("private.pem"))||!Files.exists(dir.resolve("public.pem"))||!Files.exists(env))
        throw new IllegalStateException("Incomplete existing configuration; restore matching keys/env, never regenerate over data.");
      System.out.println("Existing configuration preserved"); return;
    }
    Files.createDirectories(dir);
    var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(3072);
    var pair=generator.generateKeyPair();
    write(dir.resolve("private.pem"),"PRIVATE KEY",pair.getPrivate().getEncoded());
    write(dir.resolve("public.pem"),"PUBLIC KEY",pair.getPublic().getEncoded());
    var random=new SecureRandom();byte[] key=new byte[32];random.nextBytes(key);
    Files.writeString(env,"DB_PASSWORD="+token(random)+"\nRABBITMQ_PASSWORD="+token(random)+"\nMFA_ENCRYPTION_KEY="+Base64.getEncoder().encodeToString(key)+"\nIAM_BOOTSTRAP_PASSWORD="+token(random)+"\nJWT_PRIVATE_KEY="+dir.resolve("private.pem").toAbsolutePath().normalize().toUri()+"\nJWT_PUBLIC_KEY="+dir.resolve("public.pem").toAbsolutePath().normalize().toUri()+"\n");
    System.out.println("Created .secrets and .env; keep both private.");
  }
  static String token(SecureRandom random){byte[] data=new byte[24];random.nextBytes(data);return Base64.getUrlEncoder().withoutPadding().encodeToString(data);}
  static void write(Path path,String type,byte[] value)throws Exception{Files.writeString(path,"-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(value)+"\n-----END "+type+"-----\n");}
}
