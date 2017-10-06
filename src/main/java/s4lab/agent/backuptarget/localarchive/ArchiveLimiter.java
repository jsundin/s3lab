package s4lab.agent.backuptarget.localarchive;

/**
 * @author johdin
 * @since 2017-10-06
 */
public abstract class ArchiveLimiter {
  public static ArchiveLimiter newFileCountLimiter(int maxFileCount) {
    return new ArchiveLimiter() {
      @Override
      boolean newArchive(int filesInArchive, long archiveSizeInBytes) {
        return filesInArchive >= maxFileCount;
      }
    };
  }

  public static ArchiveLimiter newFileSizeLimiter(long maxArchiveSizeInBytes) {
    return new ArchiveLimiter() {
      @Override
      boolean newArchive(int filesInArchive, long archiveSizeInBytes) {
        return archiveSizeInBytes >= maxArchiveSizeInBytes;
      }
    };
  }

  abstract boolean newArchive(int filesInArchive, long archiveSizeInBytes);
}
