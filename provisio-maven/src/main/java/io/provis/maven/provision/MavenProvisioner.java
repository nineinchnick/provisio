package io.provis.maven.provision;

import io.tesla.aether.internal.DefaultTeslaAether;
import io.tesla.proviso.archive.Archiver;
import io.tesla.proviso.archive.UnArchiver;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.resolution.ArtifactResolutionException;


@Named
public class MavenProvisioner {

  @Inject
  private DefaultTeslaAether artifactResolver;

  private UnArchiver unarchiver;

  public MavenProvisioner() {
    unarchiver = UnArchiver.builder()
        .useRoot(false)
        .flatten(false)
        .build();
  }
  
  public File provision(String mavenVersion, File installDir) throws IOException, ArtifactResolutionException {    
    if (mavenVersion == null || mavenVersion.length() <= 0) {
      throw new IllegalArgumentException("Maven version not specified");
    }
   
    File binZip = artifactResolver.resolveArtifact("org.apache.maven:apache-maven:zip:bin:" + mavenVersion).getArtifact().getFile();

    installDir.mkdirs();
    if (!installDir.isDirectory()) {
      throw new IllegalStateException("Could not create Maven install directory " + installDir);
    }

    unarchiver.unarchive(binZip, installDir);

    File mvn = new File(installDir, "bin/mvn");
    if (!mvn.isFile()) {
      throw new IllegalStateException("Unpacking of Maven distro failed");
    }
    mvn.setExecutable(true);

    return installDir;
  }
}
