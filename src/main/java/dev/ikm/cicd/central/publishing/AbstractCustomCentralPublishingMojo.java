package dev.ikm.cicd.central.publishing;

import org.apache.maven.plugin.AbstractMojo;

import java.io.File;
import java.nio.file.Path;

public abstract class AbstractCustomCentralPublishingMojo extends AbstractMojo {

    public static final String CENTRAL_BUNDLE_PATH = "${project.build.directory}/central-publishing/central-bundle.zip";

}
