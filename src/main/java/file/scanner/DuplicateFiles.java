package file.scanner;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <pre>
 * <B>Copyright:</B>   Izik Golan
 * <B>Owner:</B>       <a href="mailto:golan2@hotmail.com">Izik Golan</a>
 * <B>Creation:</B>    12/12/2014 11:04
 * <B>Since:</B>       BSM 9.21
 * <B>Description:</B>
 * Scan all sub folders to find files that are the same.
 *
 * TODO: What is considered as a "same file"
 * TODO: Use EhCache instead of map
 *
 * </pre>
 */
public class DuplicateFiles {

  private static final byte[] SEPARATOR       = " , ".getBytes();
  private static final String NEWLINE         = "\n";
  public  static final String ALL_SUMMARY_KEY = "ALL_SUMMARY_KEY";

  public static void main(String[] args) throws IOException {

    String rootFolder = args[0];
    String outputFile = args[1];

    final Map<String, DuplicateEntity> duplications = calculateDuplicateFiles(rootFolder);
    final DuplicateEntity summary = duplications.remove(ALL_SUMMARY_KEY);
    final ArrayList<DuplicateEntity> list = new ArrayList<>(duplications.values());
    Collections.sort(list, (o1, o2) -> Long.compare(o2.overallSize, o1.overallSize));    //ORDER BY overallSize DESC

    try (FileOutputStream fos = new FileOutputStream(outputFile);BufferedOutputStream bos = new BufferedOutputStream(fos)) {
      bos.write( ("Total of ["+Double.toString(summary.overallSize/1024.0)+"] Kb of files were scanned\n").getBytes() );
      for (DuplicateEntity entity : list) {
        for (Path path : entity.paths) {
          bos.write(Double.toString(entity.overallSize/1024.0).getBytes());
          bos.write(SEPARATOR);
          bos.write(entity.shortName.getBytes());
          bos.write(SEPARATOR);
          bos.write(Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toString().getBytes());
          bos.write(SEPARATOR);
          bos.write(path.toString().getBytes());
          bos.write(SEPARATOR);
          bos.write(NEWLINE.getBytes());
        }
        bos.write(NEWLINE.getBytes());
      }
    }


  }

  private static Map<String, DuplicateEntity> calculateDuplicateFiles(String rootFolder) throws IOException {
    DuplicateVisitor visitor = new DuplicateVisitor();
    Files.walkFileTree(Paths.get(rootFolder), visitor);
    visitor.removeNonDuplicates();
    return visitor.getDuplications();
  }

  private static class DuplicateVisitor implements FileVisitor<Path> {
    private static final DuplicateEntity ALL_SUMMARY_ENTITY = new DuplicateEntity(ALL_SUMMARY_KEY);
    private final Map<String, DuplicateEntity> duplicationsMap = new HashMap<>();  // key = file short name

    public DuplicateVisitor() {
      this.duplicationsMap.put(ALL_SUMMARY_KEY, ALL_SUMMARY_ENTITY);
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      String shortName = file.getFileName().toString();   //name with extension without path

      DuplicateEntity duplicateEntity = this.duplicationsMap.get(shortName);
      if (duplicateEntity==null) {
        duplicateEntity = new DuplicateEntity(shortName);
        duplicationsMap.put(shortName, duplicateEntity);
        duplicateEntity.shortName = shortName;
      }
      duplicateEntity.overallSize += Files.size(file);
      ALL_SUMMARY_ENTITY.overallSize += Files.size(file);
      duplicateEntity.paths.add(file);
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
      return FileVisitResult.CONTINUE;
    }

    public void removeNonDuplicates() {
      Set<String> fileNames = this.duplicationsMap.keySet();
      Iterator<String> iterator = fileNames.iterator();
      while (iterator.hasNext()) {
        String fileName = iterator.next();
        final DuplicateEntity duplicateEntity = this.duplicationsMap.get(fileName);
        if (duplicateEntity!=ALL_SUMMARY_ENTITY && duplicateEntity.size()<2) {
          iterator.remove();
        }
      }
    }

    public Map<String, DuplicateEntity> getDuplications() {
      return duplicationsMap;
    }
  }

  private static class DuplicateEntity {
    String shortName;
    List<Path> paths = new ArrayList<>();
    long overallSize;

    public DuplicateEntity(String shortName) {
      this.shortName = shortName;
    }

    public int size() {
      return paths.size();
    }
  }
}
