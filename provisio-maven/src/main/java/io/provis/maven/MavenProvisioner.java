package io.provis.maven;

import io.provis.provision.SimpleProvisioner;

import java.io.File;
import java.io.IOException;

import javax.inject.Named;

@Named
public class MavenProvisioner extends SimpleProvisioner {

  public File provision(String mavenVersion, File installDirectory) throws IOException {
    if (mavenVersion == null || mavenVersion.length() <= 0) {
      throw new IllegalArgumentException("Maven version not specified");
    }

    File mvn = new File(installDirectory, "bin/mvn");
    // If we're working with snapshot versions re-provision
    if (mvn.exists() && !mavenVersion.contains("SNAPSHOT")) {
      return installDirectory;
    }

    File archive;
    if (mavenVersion.contains(":")) {
      // We have a coordinate
      archive = resolveFromRepository(mavenVersion);
    } else {
      archive = resolveFromRepository("org.apache.maven:apache-maven:zip:bin:" + mavenVersion);
    }
    
    installDirectory.mkdirs();
    if (!installDirectory.isDirectory()) {
      throw new IllegalStateException("Could not create Maven install directory " + installDirectory);
    }

    unarchiver.unarchive(archive, installDirectory);

    if (!mvn.isFile()) {
      throw new IllegalStateException("Unpacking of Maven distro failed");
    }
    mvn.setExecutable(true);

    return installDirectory;
  }
}
