import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class Main {
  public static void main(String[] args){
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    //System.out.println("Logs from your program will appear here!");

    // Uncomment this block to pass the first stage
    
    final String command = args[0];
    
    switch (command) {
      case "init" -> {
        final File root = new File(".git");
        new File(root, "objects").mkdirs();
        new File(root, "refs").mkdirs();
        final File head = new File(root, "HEAD");
    
        try {
          head.createNewFile();
          Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());
          System.out.println("Initialized git directory");
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      case "cat-file" -> {
        String hash = args[2];
        String dirHash = hash.substring(0, 2);
        String fileHash = hash.substring(2);
        File blobFile = new File("./.git/objects/" + dirHash + "/" + fileHash);
        try(BufferedReader blobRead = new BufferedReader (new InputStreamReader(new InflaterInputStream(new FileInputStream(blobFile))))) {
          String blob = blobRead.readLine();
          String content = blob.substring(blob.indexOf("\0")+1);
          System.out.print(content);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      case "hash-object" -> {// hash-object -w helloworld
        String input=args[1];
        String fileName;
        boolean createBlob=false;

        // check for argument
        if(input.equals("-w")){
          createBlob=true;
          fileName=args[2];
        }else{
          fileName=input;
        }

        // Read File Contents in bytes
        byte[] bytes;
        try{
          bytes=Files.readAllBytes(Paths.get(fileName));
        }catch(IOException e){
          throw new RuntimeException(e);
        }

        byte[] nullByte={0};
        byte[] lengthBytes=String.valueOf(bytes.length).getBytes();
        MessageDigest md;

        try{
          md=MessageDigest.getInstance("SHA-1");
          md.update("blob ".getBytes());
          md.update(lengthBytes);
          md.update(nullByte);
          md.update(bytes);
        }catch(NoSuchAlgorithmException e){
          throw new RuntimeException(e);
        }

        byte[] digest=md.digest();

        var hash=HexFormat.of().formatHex(digest);

        //The program will print out the 40-character SHA hash to output
        System.out.print(hash);

        if (createBlob) {
          // add file to the blobs directory
          try {
            String directoryHash = hash.substring(0, 2);
            String fileHash = hash.substring(2);
            File blobFile = new File("./.git/objects/" + directoryHash + "/" + fileHash);
            blobFile.getParentFile().mkdirs();
            blobFile.createNewFile();
            //zlib compress the file
            FileOutputStream fos = new FileOutputStream(blobFile);
            DeflaterOutputStream dos = new DeflaterOutputStream(fos);
            dos.write("blob ".getBytes());
            dos.write(lengthBytes);
            dos.write(nullByte);
            dos.write(bytes);
            dos.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }

      }

      default -> System.out.println("Unknown command: " + command);
    }


  }
}
