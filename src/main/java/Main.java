import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
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
          bytes=Files.readAllBytes(Paths.get(fileName)); //Read all the content of the file in byte array
        }catch(IOException e){
          throw new RuntimeException(e);
        }

        byte[] nullByte={0}; //Explained in line 83
        byte[] lengthBytes=String.valueOf(bytes.length).getBytes(); //Representing the size of file in bytes
        MessageDigest md;

        try{
          md=MessageDigest.getInstance("SHA-1"); //This line uses "SHA-1" hashing algorithm to create a 40 character hash code to identify a file uniquely
          md.update("blob ".getBytes()); //This line updates the message digest with the bytes representing the string "blob ".
          md.update(lengthBytes);//This line updates the message digest with the byte array containing the file size
          md.update(nullByte);//This line updates the message digest with the single-byte array containing a 0 value so that when reading content from the file it could seperate content from its length. EX -> "11\0hello world"
          md.update(bytes);//This line updates the message digest with the actual content of the file
        }catch(NoSuchAlgorithmException e){
          throw new RuntimeException(e);
        }

        byte[] digest=md.digest();//This line finalizes the hashing process and retrieves the SHA-1 hash of the combined data (including file information and content) as a byte array.

        var hash=HexFormat.of().formatHex(digest);//Converting the SHA-1 hash from a byte array into a human-readable string format.

        //The program will print out the 40-character SHA hash to output
        System.out.print(hash);

        if (createBlob) {
          // add file to the blobs directory
          try {
            String directoryHash = hash.substring(0, 2);
            String fileHash = hash.substring(2);
            File blobFile = new File("./.git/objects/" + directoryHash + "/" + fileHash);
            blobFile.getParentFile().mkdirs();//It uses mkdirs() to create any missing parent directories for the blob file.
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

      case "ls-tree" -> {
        String flag = null;
        String treeSha;
        if(args.length == 2){
          //full
          treeSha = args[1];
        }else{
          flag = args[1];
          treeSha = args[2];
        }
        try {
            lsTree(treeSha, flag);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
      }
      case "write-tree" -> {
        var workingDir = Paths.get("").toAbsolutePath().toString();
        byte[] sha = null;
        try {
            sha = writeTree(Objects.requireNonNull(new File(workingDir).listFiles()));
            System.out.println(toHexString(sha));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
      }

      default -> System.out.println("Unknown command: " + command);
    }

  }

  record TreeHeader(int contentSize){};
  record TreeEntry(String mode, String name, byte[] sha1){};
  private static void lsTree(String treeSha, String flag) throws IOException {
    var objectPaths = shaToObjectPaths(treeSha);
    byte[] bytes = Files.readAllBytes(objectPaths.fileName);
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    InflaterInputStream inflaterInputStream = new InflaterInputStream(bais);
    var inflatedBytes = inflaterInputStream.readAllBytes();
    boolean header = true;
    StringBuilder acc = new StringBuilder();
    List<TreeEntry> entries = new ArrayList<>();
    for(int i=0;i<inflatedBytes.length;++i){
      if(inflatedBytes[i] != '\0'){
        acc.append((char)inflatedBytes[i]);
      }else{
        if(header){
          //Print header
          //System.out.printf("header: %s\n", acc.toString());
          header = false;
          acc = new StringBuilder();
        }else{
          //read additional 20 bytes for sha of entry
          var parts = acc.toString().split(" ");
          var mode = parts[0];
          var name = parts[1];
          var shaBytes = Arrays.copyOfRange(inflatedBytes, i, i+20);
          entries.add(new TreeEntry(mode, name, shaBytes));
          System.out.println(name);
          i+=20;
          acc = new StringBuilder();
        }
      }
    }
  }

  record ObjectPaths(Path directory, Path fileName) {}
  private static ObjectPaths shaToObjectPaths(String sha) {
    String dir = sha.substring(0, 2);
    String fileName = sha.substring(2);
    return new ObjectPaths(Paths.get(".git", "objects", dir), Paths.get(".git", "objects", dir, fileName));
  }

  private static byte[] writeTree(File[] files) throws IOException, NoSuchAlgorithmException {
    List<TreeEntry> entries = new ArrayList<>();
    for (File f : files) {
      if (f.isDirectory()) {
        if (f.getName().equals(".git")) {
          continue;
        }
        var subDirSha =
            writeTree(Objects.requireNonNull(f.listFiles()));
        // write subdir entry
        entries.add(new TreeEntry("40000", f.getName(), subDirSha));
      } else {
        // write blob
        var blobSha = hashObject("-w", f.getPath());
        // write blob entry
        entries.add(new TreeEntry("100644", f.getName(), blobSha));
      }
    }

    //        entries.forEach(System.out::println);
    //        System.out.println();
    // sort by name
    entries.sort(Comparator.comparing(k -> k.name));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    for (var entry : entries) {
      baos.write(entry.mode().getBytes());
      baos.write(' ');
      baos.write(entry.name.getBytes());
      baos.write('\0');
      baos.write(entry.sha1);
    }
    ByteArrayOutputStream treeStream = new ByteArrayOutputStream();
    treeStream.write("tree ".getBytes());
    treeStream.write(
        String.valueOf(baos.toByteArray().length).getBytes());
    treeStream.write('\0');
    baos.writeTo(treeStream);

    // get sha1 of bytes
    MessageDigest md = MessageDigest.getInstance("SHA-1");
    md.update(treeStream.toByteArray());
    var digest = md.digest();
    // from sha create dir and files in .git/objects
    var objectPaths = shaToObjectPaths(toHexString(digest));
    System.err.println("objctPaths: " + objectPaths);
    Files.createDirectories(objectPaths.directory);
    var blobFile = Files.createFile(objectPaths.fileName);
    // write compressed content to file.
    try (FileOutputStream fileOutputStream =
             new FileOutputStream(blobFile.toString());
         DeflaterOutputStream deflaterOutputStream =
             new DeflaterOutputStream(fileOutputStream)) {
      deflaterOutputStream.write(treeStream.toByteArray());
      deflaterOutputStream.flush();
      fileOutputStream.flush();
    }

    System.err.println("written file: " + blobFile.toString());
    //        ByteArrayOutputStream compressedStream = new
    //        ByteArrayOutputStream(); DeflaterOutputStream
    //        deflaterOutputStream = new
    //        DeflaterOutputStream(compressedStream);
    //        treeStream.writeTo(deflaterOutputStream);
    //        deflaterOutputStream.flush();
    //        deflaterOutputStream.close();
    //
    //        try (FileOutputStream fileOutputStream = new
    //        FileOutputStream(blobFile.toString())) {
    //            fileOutputStream.write(compressedStream.toByteArray());
    //            fileOutputStream.flush();
    //        }
    // return sha1
    return digest;
  }

  private static byte[] hashObject(String flag, String filePath) throws IOException, NoSuchAlgorithmException {
    assert flag.equals("-w");
    var path = Path.of(filePath);
    var bytes = Files.readAllBytes(path);
    ByteArrayOutputStream byteArrayOutputStream =
        new ByteArrayOutputStream();
    byteArrayOutputStream.write("blob ".getBytes());
    byteArrayOutputStream.write(
        String.format("%d", bytes.length).getBytes());
    byteArrayOutputStream.write((byte)'\0');
    byteArrayOutputStream.write(bytes);
    var blobBytes = byteArrayOutputStream.toByteArray();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DeflaterOutputStream dos = new DeflaterOutputStream(baos);
    byteArrayOutputStream.writeTo(dos);
    dos.flush();
    dos.close();
    // var deflatedBytes = baos.toByteArray();
    MessageDigest md = MessageDigest.getInstance("SHA-1");
    md.update(blobBytes);
    var digest = md.digest();
    var sha1String = toHexString(digest);
    var dir = sha1String.substring(0, 2);
    var blobFilename = sha1String.substring(2);
    Files.createDirectories(Paths.get(".git", "objects", dir));
    var blobFile = Files.createFile(Paths.get(".git", "objects", dir, blobFilename));
    baos.writeTo(new FileOutputStream(blobFile.toString()));
    //        System.out.println(sha1String);
    return digest;
  }

  private static String toHexString(byte[] buf) {
    StringBuilder stringBuilder = new StringBuilder();
    for (var b : buf) {
      stringBuilder.append(String.format("%02x", b));
    }
    return stringBuilder.toString();
  }

  
}
