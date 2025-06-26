
[![License](https://img.shields.io/github/license/eitco/bom-maven-plugin.svg?style=for-the-badge)](https://opensource.org/license/mit)



[![Build status](https://img.shields.io/github/actions/workflow/status/eitco/central-custom-publishing-maven-plugin/deploy.yaml?branch=main&style=for-the-badge&logo=github)](https://github.com/ikmdev/central-custom-publishing-maven-plugin/actions/workflows/deploy.yaml)
[![Maven Central Version](https://img.shields.io/maven-central/v/dev.ikm.cicd/central-custom-publishing-maven-plugin?style=for-the-badge&logo=apachemaven)](https://central.sonatype.com/artifact/de.eitco.cicd/central-custom-publishing-maven-plugin)

# Central Custom Publishing Maven Plugin

This maven plugin provides single steps of the central publishing process using the underlying API of the 
[central publishing maven plugin](https://central.sonatype.org/publish/publish-portal-maven/).

## Usage

The plugin allows you to create a deployment bundle with multiple artifacts and publish it to Maven Central.

### Creating a Bundle

To create a bundle with multiple artifacts, use the `create-bundle` goal:

```xml
<plugin>
    <groupId>dev.ikm.cicd</groupId>
    <artifactId>central-custom-publishing-maven-plugin</artifactId>
    <version>${plugin.version}</version>
    <executions>
        <execution>
            <id>create-bundle</id>
            <goals>
                <goal>create-bundle</goal>
            </goals>
            <configuration>
                <pomFile>${path.to.pom.file}</pomFile>
                <artifactFiles>
                    <artifactFile>${path.to.artifact1}</artifactFile>
                    <artifactFile>${path.to.artifact2}</artifactFile>
                    <!-- Add more artifacts as needed -->
                </artifactFiles>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Publishing a Bundle

To publish the created bundle to Maven Central, use the `publish` goal:

```xml
<plugin>
    <groupId>dev.ikm.cicd</groupId>
    <artifactId>central-custom-publishing-maven-plugin</artifactId>
    <version>${plugin.version}</version>
    <executions>
        <execution>
            <id>publish-bundle</id>
            <goals>
                <goal>publish</goal>
            </goals>
            <configuration>
                <publishingServerId>ossrh</publishingServerId>
                <autoPublish>true</autoPublish>
                <deploymentName>${project.groupId}:${project.artifactId}:${project.version}</deploymentName>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Parameters

### create-bundle Goal

| Parameter | Description | Required | Default |
|-----------|-------------|----------|---------|
| pomFile | Path to the POM file | Yes | - |
| artifactFiles | List of artifact files to include in the bundle | Yes | - |
| stagingDirectory | Directory where the staging files will be created | No | ${project.build.directory}/central-staging |
| removeMetaDataFiles | Whether to remove metadata files from the bundle | No | true |
| gpgSign | Whether to sign the artifacts with GPG | No | true |
| passphrase | GPG passphrase for signing | No | - |

### publish Goal

| Parameter | Description | Required | Default |
|-----------|-------------|----------|---------|
| publishingServerId | Server ID in Maven settings.xml | No | ossrh |
| centralBaseUrl | Base URL for Maven Central | No | https://central.sonatype.org/api/v1 |
| autoPublish | Whether to automatically publish the bundle | No | false |
| deploymentName | Name of the deployment | No | ${project.groupId}:${project.artifactId}:${project.version} |
| tokenAuth | Whether to use token authentication | No | true |
| waitUntil | State to wait for (UPLOADED, PUBLISHED) | No | PUBLISHED |
| waitMaxTime | Maximum time to wait in seconds | No | 300 |
| waitPollingInterval | Polling interval in seconds | No | 5 |

## GPG Signing

The plugin automatically signs artifacts with GPG when creating a bundle. This is required for publishing to Maven Central. 

### GPG Configuration

To use GPG signing, you need to have GPG installed on your system and a GPG key pair configured. The plugin uses the `gpg` command-line tool to sign artifacts.

You can configure GPG signing with the following parameters:

```xml
<configuration>
    <gpgSign>true</gpgSign>
    <passphrase>${gpg.passphrase}</passphrase>
</configuration>
```

Alternatively, you can provide the passphrase via the command line:

```bash
mvn central-custom-publishing:create-bundle -Dgpg.passphrase=your_passphrase
```

If you don't provide a passphrase, the plugin will use the GPG agent if it's running, or prompt for the passphrase if needed.

To disable GPG signing (not recommended for Maven Central publishing):

```xml
<configuration>
    <gpgSign>false</gpgSign>
</configuration>
```

### How GPG Signing Works

The plugin performs the following GPG-related operations:

1. Signs each artifact (including the POM file) with a detached signature in ASCII armor format
2. Creates `.asc` signature files for each artifact
3. Generates checksums (MD5, SHA-1, SHA-256) for both the artifacts and their signatures
4. Includes all these files in the bundle that gets uploaded to Maven Central

The GPG signing is performed using the following command:

```
gpg --detach-sign --armor [--passphrase your_passphrase] file_to_sign
```

## Notes

The bundle will be created at `${project.build.directory}/central-publishing/central-bundle.zip` with a directory structure that matches the m2 repository format as required by Maven Central.
