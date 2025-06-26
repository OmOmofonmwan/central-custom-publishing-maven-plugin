package dev.ikm.cicd.central.publishing;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Mojo(name = "create-bundle", defaultPhase = LifecyclePhase.DEPLOY)
public class ZipStagingDirectoryMojo extends AbstractCustomCentralPublishingMojo {

    public static final Path META_DATA_FILE = Path.of("maven-metadata-central-staging.xml");

    @Parameter(defaultValue = "${project.build.directory}/central-staging")
    protected File stagingDirectory;

    @Parameter(defaultValue = "true")
    protected boolean removeMetaDataFiles;

    @Parameter(required = true)
    protected File pomFile;

    @Parameter(required = true)
    protected List<File> artifactFiles;

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        // Create the output directory
        File bundleFile = new File(project.getBuild().getDirectory(), "central-publishing/central-bundle.zip");
        File bundleDir = bundleFile.getParentFile();

        try {
            // Ensure the directory exists
            FileUtils.forceMkdir(bundleDir);

            // Delete the zip file if it exists
            if (bundleFile.exists()) {
                FileUtils.forceDelete(bundleFile);
            }

            // Read the POM file to get groupId, artifactId, and version
            Model model = readPomFile(pomFile);
            String groupId = model.getGroupId();
            String artifactId = model.getArtifactId();
            String version = model.getVersion();

            if (groupId == null || artifactId == null || version == null) {
                throw new MojoExecutionException("POM file must contain groupId, artifactId, and version");
            }

            // Create the m2 repository directory structure
            String m2Path = groupId.replace('.', '/') + "/" + artifactId + "/" + version;
            File m2Dir = new File(stagingDirectory, m2Path);
            FileUtils.forceMkdir(m2Dir);

            // Copy the POM file to the m2 directory
            File targetPomFile = new File(m2Dir, artifactId + "-" + version + ".pom");
            FileUtils.copyFile(pomFile, targetPomFile);

            // Copy all artifact files to the m2 directory
            for (File artifactFile : artifactFiles) {
                String fileName = artifactFile.getName();
                File targetFile = new File(m2Dir, fileName);
                FileUtils.copyFile(artifactFile, targetFile);
                getLog().info("Added artifact: " + fileName);
            }

            // Create the zip file
            try (ZipFile zipFile = new ZipFile(bundleFile)) {
                if (removeMetaDataFiles) {
                    getLog().debug("walking directory " + stagingDirectory.toPath());

                    try (Stream<Path> stream = Files.walk(stagingDirectory.toPath())) {
                        List<Path> toDelete = stream
                                .filter(path -> {
                                    getLog().debug("encountered path " + path.toFile().getAbsolutePath());
                                    return path.toFile().isFile() && path.getFileName().equals(META_DATA_FILE);
                                }).toList();

                        for (Path path : toDelete) {
                            try {
                                getLog().debug("deleting file " + path.toFile().getAbsolutePath());
                                FileUtils.forceDelete(path.toFile());
                            } catch (IOException e) {
                                throw new MojoExecutionException(e);
                            }
                        }
                    }
                }

                ZipParameters zipParameters = new ZipParameters();
                zipParameters.setIncludeRootFolder(false);
                zipFile.addFolder(stagingDirectory, zipParameters);

                getLog().info("Created bundle: " + bundleFile.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create bundle", e);
        }
    }

    private Model readPomFile(File pomFile) throws MojoExecutionException {
        try {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            return reader.read(new FileReader(pomFile));
        } catch (IOException | XmlPullParserException e) {
            throw new MojoExecutionException("Failed to read POM file", e);
        }
    }
}
