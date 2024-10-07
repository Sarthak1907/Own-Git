import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
// import java.util.ArrayList;
import java.util.Arrays;
// import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class Main {
  public static void main(String[] args) {
    final String command = args[0];
    switch (command) {
      case "init" -> {
        final File root = new File(".git");
        new File(root, "objects").mkdirs();
        new File(root, "refs").mkdirs();
        final File head = new File(root, "HEAD");
        try {
          head.createNewFile();
          Files.write(head.toPath(), "ref: refs/heads/master\n".getBytes());
          System.out.println("Initialized git directory");
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      case "cat-file" -> {
        String blobSHA = args[2];
        readBlob(blobSHA);
      }
      case "hash-object" -> {
        String filePath = args[2];
        byte[] sha = writeBlob(filePath);
        System.out.print(toHexSHA(sha));
      }
      case "ls-tree" -> {
        String treeSHA = args[2];
        readTree(treeSHA);
      }
      case "write-tree" -> {
        byte[] sha = writeTree(new File("."));
        System.out.println(toHexSHA(sha));
      }
      default -> System.out.println("Unknown command: " + command);
    }
  }

  private static void readBlob(String sha) {
    String filePath = shaToPath(sha);
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(new InflaterInputStream(new FileInputStream(filePath))))) {
      String line = reader.readLine();
      String content = line.substring(line.indexOf('\0') + 1);
      System.out.print(content);
      while ((line = reader.readLine()) != null) {
        System.out.println(line);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static byte[] writeBlob(String filePath) {
    try {
      byte[] fileContent = Files.readAllBytes(Path.of(filePath));
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      buffer.write("blob".getBytes());
      buffer.write(" ".getBytes());
      buffer.write(String.valueOf(fileContent.length).getBytes());
      buffer.write(0);
      buffer.write(fileContent);
      byte[] blobContent = buffer.toByteArray();
      byte[] blobSHA = toBinarySHA(blobContent);
      String blobPath = shaToPath(toHexSHA(blobSHA));
      File blobFile = new File(blobPath);
      blobFile.getParentFile().mkdirs();
      DeflaterOutputStream out = new DeflaterOutputStream(new FileOutputStream(blobFile));
      out.write(blobContent);
      out.close();
      return blobSHA;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private static void readTree(String treeSHA) {
    String filePath = shaToPath(treeSHA);
    String content = null;
    try (InputStream in = new InflaterInputStream(new FileInputStream(filePath))) {
      content = new String(in.readAllBytes());
    } catch (IOException e) {
      e.printStackTrace();
    }
    int startEntry = content.indexOf('\0') + 1;
    for (int i = startEntry; i < content.length(); i++) {
      if (content.charAt(i) == '\0') {
        String entry = content.substring(startEntry, i);
        String fileName = entry.split(" ")[1];
        System.out.println(fileName);
        i = startEntry = i + 21;
      }
    }
  }

  private static byte[] writeTree(File dir) {
    try {
      File[] files = dir.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return !name.equals(".git");
        }
      });
      Arrays.sort(files);

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();

      for (File file : files) {
        if (file.isFile()) {
          byte[] sha = writeBlob(file.getAbsolutePath());
          buffer.write(("100644 " + file.getName() + "\0")
              .getBytes());
          buffer.write(sha);
        } else {
          byte[] sha = writeTree(file);
          buffer.write(("40000 " + file.getName() + "\0")
              .getBytes());
          buffer.write(sha);
        }
      }
      byte[] treeItems = buffer.toByteArray();
      byte[] treeHeader = ("tree " + treeItems.length + "\0").getBytes();
      ByteBuffer combined = ByteBuffer.allocate(
          treeHeader.length + treeItems.length);
      combined.put(treeHeader);
      combined.put(treeItems);
      byte[] treeContent = combined.array();
      byte[] treeSHA = toBinarySHA(treeContent);
      String treePath = shaToPath(toHexSHA(treeSHA));
      File blobFile = new File(treePath);
      blobFile.getParentFile().mkdirs();
      DeflaterOutputStream out = new DeflaterOutputStream(
          new FileOutputStream(blobFile));
      out.write(treeContent);
      out.close();

      return treeSHA;
    }

    catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  private static String shaToPath(String sha) {
    return String.format(".git/objects/%s/%s", sha.substring(0, 2),
        sha.substring(2));
  }

  private static String toHexSHA(byte[] data) {
    StringBuilder sb = new StringBuilder();
    for (byte b : data) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private static byte[] toBinarySHA(byte[] data) {
    byte[] sha = null;
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      sha = md.digest(data);
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    return sha;
  }
}