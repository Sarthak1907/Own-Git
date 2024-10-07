import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Scanner;
import java.util.zip.InflaterInputStream;
import javax.management.RuntimeErrorException;
import org.eclipse.jgit.api.Git;

public class Main {
  public static void main(String[] args) throws IOException {
    final String command = args[0];
    final File CURRENT_DIRECTORY = new File(".");
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
        final String subCommand = args[1];
        if (subCommand.equals("-p")) {
          final String hashCode = args[2];
          String folderName = new StringBuffer(hashCode).substring(0, 2);
          String fileName = new StringBuffer(hashCode).substring(2);
          File blobFile = new File("./.git/objects/" + folderName + "/" + fileName);
          try {
            InputStream fileContents = new InflaterInputStream(new FileInputStream(blobFile));
            Scanner reader = new Scanner(fileContents);
            String firstLine = reader.nextLine();
            System.out.print(firstLine.substring(firstLine.indexOf('\0') + 1));
            while (reader.hasNextLine()) {
              System.out.print(reader.nextLine());
            }
            reader.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }
      case "hash-object" -> {
        final String subCommand = args[1];
        if (subCommand.equals("-w")) {
          String fileName = args[2];
          // read contents of the file and make the hash
          try {
            byte[] hashCode = Utils.writeBlob(CURRENT_DIRECTORY + "/" + fileName);
            System.out.print(HexFormat.of().formatHex(hashCode));
          } catch (Exception e) {
            throw (new RuntimeException(e));
          }
        }
      }
      case "ls-tree" -> {
        final String subCommand = args[1];
        if (subCommand.equals("--name-only")) {
          String hashCode = args[2];
          String folderName = new StringBuffer(hashCode).substring(0, 2);
          String fileName = new StringBuffer(hashCode).substring(2);
          File file = new File(CURRENT_DIRECTORY.toString() + "/.git/objects/" + folderName, fileName);
          try {
            InflaterInputStream decompressedFile = new InflaterInputStream(new FileInputStream(file));
            int c;
            final var decompressedContent = new StringBuilder();
            while ((c = decompressedFile.read()) != -1) {
              decompressedContent.append((char) c);
            }
            String[] allLines = decompressedContent.toString().split("\0");
            ArrayList<String> allLinesArr = new ArrayList<>(List.of(allLines));
            List<String> allContent = allLinesArr.subList(1, allLinesArr.size() - 1);
            for (String line : allContent) {
              String[] value = line.split(" ");
              System.out.println(value[1]);
            }
            decompressedFile.close();
          } catch (IOException e) {
            throw (new RuntimeException(e));
          }
        }
      }
      case "write-tree" -> {
        try {
          byte[] hash = Utils.writeTree(CURRENT_DIRECTORY);
          System.out.println(HexFormat.of().formatHex(hash));
        } catch (Exception e) {
          throw (new RuntimeException(e));
        }
      }
      case "commit-tree" -> {
        // $ ./your_program.sh commit-tree <tree_sha> -p <commit_sha> -m <message>
        String treeSHA = args[1];
        // String pFlag = args[2];
        String commitSHA = args[3];
        // String mFlag = args[4];
        String message = args[5];
        try {
          byte[] hash = Utils.commitTree(treeSHA, commitSHA, message);
          System.out.println(HexFormat.of().formatHex(hash));
        } catch (Exception e) {
          throw (new RuntimeException(e));
        }
      }
      case "clone" -> {
        String repoLink = args[1];
        String dirName = args[2];
        File repoDir = new File(dirName);
        try {
          Git.cloneRepository().setURI(repoLink).setDirectory(repoDir).call();
        } catch (Exception e) {
          throw (new RuntimeException(e));
        }
      }
      default -> System.out.println("Unknown command: " + command);
    }
  }
}