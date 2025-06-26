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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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

    @Parameter(property = "gpg.passphrase")
    protected String passphrase;

    @Parameter(defaultValue = "true")
    protected boolean gpgSign;

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
            // Sign the POM file
            signFile(targetPomFile);
            // Generate checksums for the POM file
            generateChecksums(targetPomFile);
            getLog().info("Generated checksums for POM file");

            // Copy all artifact files to the m2 directory
            for (File artifactFile : artifactFiles) {
                String fileName = artifactFile.getName();
                File targetFile = new File(m2Dir, fileName);
                FileUtils.copyFile(artifactFile, targetFile);
                // Sign the artifact file
                signFile(targetFile);
                // Generate checksums for each artifact file
                generateChecksums(targetFile);
                getLog().info("Added artifact: " + fileName + " with signature and checksums");
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

    /**
     * Generate checksums for a file
     * @param file The file to generate checksums for
     * @throws MojoExecutionException If there is an error generating the checksums
     */
    private void generateChecksums(File file) throws MojoExecutionException {
        try {
            byte[] fileContent = Files.readAllBytes(file.toPath());

            // Generate MD5 checksum
            String md5Checksum = generateChecksum(fileContent, "MD5");
            writeChecksumFile(file, md5Checksum, ".md5");

            // Generate SHA-1 checksum
            String sha1Checksum = generateChecksum(fileContent, "SHA-1");
            writeChecksumFile(file, sha1Checksum, ".sha1");

            // Generate SHA-256 checksum
            String sha256Checksum = generateChecksum(fileContent, "SHA-256");
            writeChecksumFile(file, sha256Checksum, ".sha256");

            getLog().debug("Generated checksums for: " + file.getName());

            // Also generate checksums for the signature file if it exists
            File signatureFile = new File(file.getAbsolutePath() + ".asc");
            if (signatureFile.exists()) {
                byte[] signatureContent = Files.readAllBytes(signatureFile.toPath());

                // Generate MD5 checksum for signature
                String signatureMd5Checksum = generateChecksum(signatureContent, "MD5");
                writeChecksumFile(signatureFile, signatureMd5Checksum, ".md5");

                // Generate SHA-1 checksum for signature
                String signatureSha1Checksum = generateChecksum(signatureContent, "SHA-1");
                writeChecksumFile(signatureFile, signatureSha1Checksum, ".sha1");

                // Generate SHA-256 checksum for signature
                String signatureSha256Checksum = generateChecksum(signatureContent, "SHA-256");
                writeChecksumFile(signatureFile, signatureSha256Checksum, ".sha256");

                getLog().debug("Generated checksums for signature file: " + signatureFile.getName());
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new MojoExecutionException("Failed to generate checksums for " + file.getName(), e);
        }
    }

    /**
     * Generate a checksum for the given content using the specified algorithm
     * @param content The content to generate a checksum for
     * @param algorithm The algorithm to use (MD5, SHA-1, SHA-256)
     * @return The checksum as a hexadecimal string
     * @throws NoSuchAlgorithmException If the algorithm is not available
     */
    private String generateChecksum(byte[] content, String algorithm) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] hash = digest.digest(content);

        // Convert to hexadecimal string
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        return hexString.toString();
    }

    /**
     * Write a checksum to a file
     * @param originalFile The original file
     * @param checksum The checksum to write
     * @param extension The extension for the checksum file (.md5, .sha1, etc.)
     * @throws IOException If there is an error writing the file
     */
    private void writeChecksumFile(File originalFile, String checksum, String extension) throws IOException {
        File checksumFile = new File(originalFile.getAbsolutePath() + extension);
        try (FileOutputStream fos = new FileOutputStream(checksumFile)) {
            fos.write(checksum.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Sign a file using GPG
     * @param file The file to sign
     * @throws MojoExecutionException If there is an error signing the file
     */
    private void signFile(File file) throws MojoExecutionException {
        if (!gpgSign) {
            getLog().info("GPG signing is disabled, skipping");
            return;
        }

        try {
            getLog().info("Signing file: " + file.getName());

            List<String> command = new ArrayList<>();
            command.add("gpg");
            command.add("--detach-sign");
            command.add("--armor");

            // Add passphrase if provided
            if (passphrase != null && !passphrase.isEmpty()) {
                command.add("--passphrase");
                command.add(passphrase);
                command.add("--batch");
                command.add("--yes");
            }

            command.add(file.getAbsolutePath());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Read the output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    getLog().debug(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new MojoExecutionException("GPG signing failed with exit code: " + exitCode);
            }

            getLog().info("Successfully signed file: " + file.getName());
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to sign file: " + file.getName(), e);
        }
    }
}
