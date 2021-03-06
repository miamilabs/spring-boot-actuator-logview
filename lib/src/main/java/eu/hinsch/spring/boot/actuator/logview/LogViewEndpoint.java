package eu.hinsch.spring.boot.actuator.logview;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import org.apache.commons.io.IOUtils;
import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpoint;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

@ControllerEndpoint(id = "log-view")
public class LogViewEndpoint {


    private final List<FileProvider> fileProviders;
    private final Configuration freemarkerConfig;
    private final String loggingPath;
    private final List<String> stylesheets;

    public LogViewEndpoint(String loggingPath, List<String> stylesheets) {
        this.loggingPath = loggingPath;
        this.stylesheets = stylesheets;
        fileProviders = asList(new FileSystemFileProvider(),
                new ZipArchiveFileProvider(),
                new TarGzArchiveFileProvider());
        freemarkerConfig = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        freemarkerConfig.setClassForTemplateLoading(this.getClass(), "/templates");
    }

    @GetMapping("/")
    @ResponseBody
    public String list(Model model, // TODO model should no longer be injected
                       @Nullable SortBy sortBy,
                       @Nullable boolean desc,
                       @Nullable String base) throws IOException, TemplateException {

        Path currentFolder = loggingPath(base);
        securityCheck(currentFolder, null);

        List<FileEntry> files = getFileProvider(currentFolder).getFileEntries(currentFolder);
        List<FileEntry> sortedFiles = sortFiles(files, sortBy, desc);

        model.addAttribute("sortBy", sortBy);
        model.addAttribute("desc", desc);
        model.addAttribute("files", sortedFiles);
        model.addAttribute("currentFolder", currentFolder.toAbsolutePath()
                .toString());
        model.addAttribute("base", base != null ? URLEncoder.encode(base, "UTF-8") : "");
        model.addAttribute("parent", getParent(currentFolder));
        model.addAttribute("stylesheets", stylesheets);

        return FreeMarkerTemplateUtils.processTemplateIntoString(freemarkerConfig.getTemplate("logview.ftl"), model);
    }

    //
    private FileProvider getFileProvider(Path folder) {
        return fileProviders.stream()
                .filter(provider -> provider.canHandle(folder))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("no file provider found for " + folder.toString()));
    }

    private String getParent(Path loggingPath) {
        Path basePath = loggingPath(null);
        String parent = "";
        if (!basePath.toString()
                .equals(loggingPath.toString())) {
            parent = loggingPath.getParent()
                    .toString();
            if (parent.startsWith(basePath.toString())) {
                parent = parent.substring(basePath.toString()
                        .length());
            }
        }
        return parent;
    }

    //
    private Path loggingPath(String base) {
        return base != null ? Paths.get(loggingPath, base) : Paths.get(loggingPath);
    }

    //
    private List<FileEntry> sortFiles(List<FileEntry> files, SortBy sortBy, boolean desc) {
        Comparator<FileEntry> comparator = null;
        if (sortBy != null) {
            switch (sortBy) {
                case SIZE:
                    comparator = Comparator.comparingLong(FileEntry::getSize);
                    break;
                case MODIFIED:
                    comparator = Comparator.comparingLong(a -> a.getModified()
                            .toMillis());
                    break;
                case FILENAME:
                    comparator = Comparator.comparing(FileEntry::getFilename);
                    break;
            }
        } else {
            comparator = Comparator.comparing(FileEntry::getFilename);
        }

        List<FileEntry> sortedFiles = files.stream()
                .sorted(comparator)
                .collect(toList());

        if (desc) {
            Collections.reverse(sortedFiles);
        }
        return sortedFiles;
    }

    @GetMapping("/view")
    public void view(@RequestParam String filename,
                     @RequestParam(required = false) String base,
                     @RequestParam(required = false) Integer tailLines,
                     @RequestParam(required = false) String searchText,
                     HttpServletResponse response) throws IOException {

        Path path = loggingPath(base);
        FileProvider fileProvider = getFileProvider(path);
        securityCheck(path, filename);
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        if (tailLines != null) {
            fileProvider.tailContent(path, filename, response.getOutputStream(), tailLines, searchText);
        } else {
            fileProvider.streamContent(path, filename, response.getOutputStream());
        }
    }

    @GetMapping("/search")
    public void search(@RequestParam String term, HttpServletResponse response) throws IOException {
        Path folder = loggingPath(null);
        List<FileEntry> files = getFileProvider(folder).getFileEntries(folder);
        List<FileEntry> sortedFiles = sortFiles(files, SortBy.MODIFIED, false);

        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        ServletOutputStream outputStream = response.getOutputStream();

        sortedFiles.stream()
                .filter(file -> file.getFileType()
                        .equals(FileType.FILE))
                .forEach(file -> searchAndStreamFile(file, term, outputStream));
    }

    private void searchAndStreamFile(FileEntry fileEntry, String term, OutputStream outputStream) {
        Path folder = loggingPath(null);
        try {
            List<String> lines = IOUtils.readLines(new FileInputStream(new File(folder.toFile()
                    .toString(), fileEntry.getFilename())))
                    .stream()
                    .filter(line -> line.contains(term))
                    .map(line -> "[" + fileEntry.getFilename() + "] " + line)
                    .collect(toList());
            for (String line : lines) {
                outputStream.write(line.getBytes());
                outputStream.write(System.lineSeparator()
                        .getBytes());
            }
        } catch (IOException e) {
            throw new RuntimeException("error reading file", e);
        }
    }

    private void securityCheck(Path base, String filename) {
        try {
            String canonicalLoggingPath = (filename != null ? new File(base.toFile()
                    .toString(), filename) : new File(base.toFile()
                    .toString())).getCanonicalPath();
            String baseCanonicalPath = new File(loggingPath).getCanonicalPath();
            String errorMessage = "File " + base.toString() + "/" + filename + " may not be located outside base path " + loggingPath;
            Assert.isTrue(canonicalLoggingPath.startsWith(baseCanonicalPath), errorMessage);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
