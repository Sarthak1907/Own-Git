import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.DataFormatException;
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
          Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());
          System.out.println("Initialized git directory");
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      case "cat-file" -> {
        assert args[1].equals("-p");
        String blobSha = args[2];
        try {
          catFile(blobSha);
        } catch (IOException e) {
          throw new RuntimeException(e);
        } catch (DataFormatException e) {
          throw new RuntimeException(e);
        }
      }
      case "hash-object" -> {
        String flag = args[1];
        String filePath = args[2];
        try {
          var sha1 = hashObject(flag, filePath);
          System.out.println(toHexString(sha1));
        } catch (IOException e) {
          throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
          throw new RuntimeException(e);
        }
      }
      case "ls-tree" -> {
        String flag = null;
        String treeSha;
        if (args.length == 2) {
          // full
          treeSha = args[1];
        } else {
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
      case "commit-tree" -> {
        var treeSha = args[1];
        var commitSha = args[3];
        var message = args[5];
        byte[] sha = new byte[0];
        try {
          sha = commitTree(treeSha, commitSha, message);
        } catch (IOException e) {
          throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
          throw new RuntimeException(e);
        }
        System.out.println(toHexString(sha));
      }
      default -> System.out.println("Unknown command: " + command);
    }
  }

  private static byte[] commitTree(String treeSha, String commitSha, String message)
      throws IOException, NoSuchAlgorithmException {
    String author = "Alyssa P. Hacker <alisp@hacker.net>";
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    byteArrayOutputStream.write(String.format("tree %s\n", treeSha).getBytes());
    byteArrayOutputStream.write(String.format("parent %s\n", commitSha).getBytes());
    byteArrayOutputStream.write(String.format("author %s\n", author).getBytes());
    byteArrayOutputStream.write(String.format("committer %s\n", author).getBytes());
    byteArrayOutputStream.write('\n');
    byteArrayOutputStream.write(String.format("%s\n", message).getBytes());
    return writeObject("commit ", byteArrayOutputStream.toByteArray());
  }

  private static byte[] writeTree(File[] files) throws IOException, NoSuchAlgorithmException {
    List<TreeEntry> entries = new ArrayList<>();
    for (File f : files) {
      if (f.isDirectory()) {
        if (f.getName().equals(".git")) {
          continue;
        }
        var subDirSha = writeTree(Objects.requireNonNull(f.listFiles()));
        // write subdir entry
        entries.add(new TreeEntry("40000", f.getName(), subDirSha));
      } else {
        // write blob
        var blobSha = hashObject("-w", f.getPath());
        // write blob entry
        entries.add(new TreeEntry("100644", f.getName(), blobSha));
      }
    }
    // entries.forEach(System.out::println);
    // System.out.println();
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
    try (FileOutputStream fileOutputStream = new FileOutputStream(blobFile.toString());
        DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(fileOutputStream)) {
      deflaterOutputStream.write(treeStream.toByteArray());
      deflaterOutputStream.flush();
      fileOutputStream.flush();
    }
    System.err.println("written file: " + blobFile.toString());
    // return sha1
    return digest;
  }

  record TreeHeader(int contentSize) {
  };

  record TreeEntry(String mode, String name, byte[] sha1) {
  };

  private static void lsTree(String treeSha, String flag)
      throws IOException {
    var objectPaths = shaToObjectPaths(treeSha);
    byte[] bytes = Files.readAllBytes(objectPaths.fileName);
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    InflaterInputStream inflaterInputStream = new InflaterInputStream(bais);
    var inflatedBytes = inflaterInputStream.readAllBytes();
    boolean header = true;
    StringBuilder acc = new StringBuilder();
    List<TreeEntry> entries = new ArrayList<>();
    for (int i = 0; i < inflatedBytes.length; ++i) {
      if (inflatedBytes[i] != '\0') {
        acc.append((char) inflatedBytes[i]);
      } else {
        if (header) {
          // Print header
          // System.out.printf("header: %s\n", acc.toString());
          header = false;
          acc = new StringBuilder();
        } else {
          // read additional 20 bytes for sha of entry
          var parts = acc.toString().split(" ");
          var mode = parts[0];
          var name = parts[1];
          var shaBytes = Arrays.copyOfRange(inflatedBytes, i, i + 20);
          entries.add(new TreeEntry(mode, name, shaBytes));
          System.out.println(name);
          i += 20;
          acc = new StringBuilder();
        }
      }
    }
  }

  private static byte[] hashObject(String flag, String filePath)
      throws IOException, NoSuchAlgorithmException {
    assert flag.equals("-w");
    var path = Path.of(filePath);
    var bytes = Files.readAllBytes(path);
    var type = "blob ";
    return writeObject(type, bytes);
  }

  private static byte[] writeObject(String type, byte[] bytes)
      throws IOException, NoSuchAlgorithmException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    byteArrayOutputStream.write(type.getBytes());
    byteArrayOutputStream.write(
        String.format("%d", bytes.length).getBytes());
    byteArrayOutputStream.write((byte) '\0');
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
    var blobFile = Files.createFile(
        Paths.get(".git", "objects", dir, blobFilename));
    baos.writeTo(new FileOutputStream(blobFile.toString()));
    // System.out.println(sha1String);
    return digest;
  }

  private static String toHexString(byte[] buf) {
    StringBuilder stringBuilder = new StringBuilder();
    for (var b : buf) {
      stringBuilder.append(String.format("%02x", b));
    }
    return stringBuilder.toString();
  }

  record ObjectPaths(Path directory, Path fileName) {
  }

  private static ObjectPaths shaToObjectPaths(String sha) {
    String dir = sha.substring(0, 2);
    String fileName = sha.substring(2);
    return new ObjectPaths(
        Paths.get(".git", "objects", dir),
        Paths.get(".git", "objects", dir, fileName));
  }

  private static void catFile(String blobSha)
      throws IOException, DataFormatException {
    String dir = blobSha.substring(0, 2);
    String blobFileName = blobSha.substring(2);
    Path blobFilePath = Paths.get(".git", "objects", dir, blobFileName);
    byte[] bytes = Files.readAllBytes(blobFilePath);
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    InflaterInputStream inflaterInputStream = new InflaterInputStream(bais);
    byte[] buf = new byte[1024];
    int bytesRead = -1;
    StringBuilder stringBuilder = new StringBuilder();
    while ((bytesRead = inflaterInputStream.read(buf)) != -1) {
      stringBuilder.append(new String(Arrays.copyOf(buf, bytesRead)));
    }
    var result = stringBuilder.toString();
    var prefixIndex = result.indexOf(' ');
    var prefix = result.substring(0, prefixIndex);
    var lenIndex = result.indexOf('\0', prefixIndex);
    var len = result.substring(prefixIndex + 1, lenIndex);
    var ilen = Integer.parseInt(len);
    var contents = result.substring(lenIndex + 1);
    assert prefix.equals("blob");
    System.out.printf("%s", contents);
    assert contents.length() == ilen;
  }
}