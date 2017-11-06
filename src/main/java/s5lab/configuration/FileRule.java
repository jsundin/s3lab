package s5lab.configuration;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.File;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "rule")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ExcludeSymlinksFileRule.class, name = "exclude-symlinks"),
        @JsonSubTypes.Type(value = ExcludeDirectoryFileRule.class, name = "exclude-directory"),
        @JsonSubTypes.Type(value = ExcludeFilenamePrefixFileRule.class, name = "exclude-filename-prefix"),
        @JsonSubTypes.Type(value = ExcludeHiddenFilesFileRule.class, name = "exclude-hidden-files"),
        @JsonSubTypes.Type(value = ExcludeLongFilenamesRule.class, name = "exclude-long-filenames")
})
public interface FileRule {
  boolean accept(File f);
}
