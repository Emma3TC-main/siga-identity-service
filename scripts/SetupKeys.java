import java.nio.file.*;
import java.security.*;
import java.util.*;
public class SetupKeys {
  public static void main(String[] args) throws Exception {
    Path dir=Path.of(".secrets"); Files.createDirectories(dir);
    if(Files.exists(dir.resolve("private.pem"))||Files.exists(Path.of(".env"))) {
      System.out.println("Existing configuration preserved"); return;
    }
    var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(3072);
    var pair=generator.generateKeyPair();
    write(dir.resolve("private.pem"),"PRIVATE KEY",pair.getPrivate().getEncoded());
    write(dir.resolve("public.pem"),"PUBLIC KEY",pair.getPublic().getEncoded());
    var random=new SecureRandom();byte[] key=new byte[32];random.nextBytes(key);
    Files.writeString(Path.of(".env"),"DB_PASSWORD="+token(random)+"\nRABBITMQ_PASSWORD="+token(random)+"\nMFA_ENCRYPTION_KEY="+Base64.getEncoder().encodeToString(key)+"\nIAM_BOOTSTRAP_PASSWORD="+token(random)+"\n");
    System.out.println("Created .secrets and .env; keep both private.");
  }
  static String token(SecureRandom random){byte[] data=new byte[24];random.nextBytes(data);return Base64.getUrlEncoder().withoutPadding().encodeToString(data);}
  static void write(Path path,String type,byte[] value)throws Exception{Files.writeString(path,"-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(value)+"\n-----END "+type+"-----\n");}
}
