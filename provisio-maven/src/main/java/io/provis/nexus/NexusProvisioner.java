package io.provis.nexus;

import io.tesla.aether.TeslaAether;
import io.tesla.proviso.archive.UnArchiver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.util.StringDigester;

import com.google.common.io.ByteStreams;

import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import de.pdark.decentxml.XMLParser;
import de.pdark.decentxml.XMLWriter;

@Named(NexusProvisioner.ID)
public class NexusProvisioner {

  static final String ID = "nexus";

  //@Inject
  private Logger logger = LoggerFactory.getLogger(NexusProvisioner.class);

  @Inject
  private TeslaAether artifactResolver;

  private UnArchiver unarchiver;

  public NexusProvisioner() {
    
    unarchiver = UnArchiver.builder()
      .useRoot(false)
      .flatten(false)
      .build();
  }
  
  public File provision(NexusProvisioningContext context) throws IOException, ArtifactResolutionException {

    String version = context.getVersion();
    File installationDirectory = context.getInstallationDirectory();
    File workDirectory = context.getWorkDirectory();

    if (version.length() <= 0) {
      throw new IllegalArgumentException("Nexus version not specified");
    }

    File nexusDistribution;

    if (context.isPro()) {
      nexusDistribution = artifactResolver.resolveArtifact("com.sonatype.nexus:nexus-professional:tar.gz:bundle:" + version).getArtifact().getFile();
    } else {
      nexusDistribution = artifactResolver.resolveArtifact("org.sonatype.nexus:nexus-oss-webapp:tar.gz:bundle:" + version).getArtifact().getFile();
    }
    //
    // Create the installation and work directories
    //
    FileUtils.mkdir(installationDirectory.getAbsolutePath());
    FileUtils.mkdir(workDirectory.getAbsolutePath());

    //
    // Unpack Nexus into the installation directory
    //    
    unarchiver.unarchive(nexusDistribution, installationDirectory);

    if(context.isPro()) {
      File testLicenseJar = artifactResolver.resolveArtifact("com.sonatype.nexus.pluginkit:nexus-pluginkit-testlm:1.3.2").getArtifact().getFile();   
      File webInfLib = new File(installationDirectory, "nexus/WEB-INF/lib");
      FileUtils.copyFileToDirectory(testLicenseJar, webInfLib);
    }
    
    //
    // Provision any Nexus plugin required
    //
    for (String plugin : context.getPlugins()) {
      addPlugin(plugin, workDirectory);
    }

    File securityConfigurationXml = SetUpNexusConfigFile(workDirectory, "security-configuration.xml");
    File securityXml = SetUpNexusConfigFile(workDirectory, "security.xml");

    addRealms(securityConfigurationXml, context.getRealms());
    addUsers(securityXml, context.getUsers());

    return installationDirectory;
  }

  private File SetUpNexusConfigFile(File workDirectory, String configFileName) throws IOException {
    InputStream is = getClass().getClassLoader().getResourceAsStream("nexus/conf/" + configFileName);
    File configXml = new File(workDirectory, "conf/" + configFileName);
    if (is != null) {
      if (!configXml.getParentFile().exists()) {
        configXml.getParentFile().mkdirs();
      }
      OutputStream os = new FileOutputStream(configXml);
      ByteStreams.copy(is, os);
    }
    return configXml;
  }

  //
  //  <?xml version="1.0"?>                                                                                                                                       
  //  <security-configuration>                                                                                                                                    
  //    <version>2.0.3</version>                                                                                                                                  
  //    <enabled>true</enabled>                                                                                                                                   
  //    <anonymousAccessEnabled>true</anonymousAccessEnabled>                                                                                                     
  //    <anonymousUsername>anonymous</anonymousUsername>                                                                                                          
  //    <anonymousPassword>{6xW8vwUKYaMHZa5LDPwrNa9+sqlDtm90JwhaeApXN10=}</anonymousPassword>                                                                     
  //    <realms>                                                                                                                                                  
  //      <realm>XmlAuthenticatingRealm</realm>                                                                                                                   
  //      <realm>XmlAuthorizingRealm</realm>                                                                                                                      
  //      <realm>GithubRealm</realm>                                                                                                                              
  //    </realms>                                                                                                                                                 
  //    <securityManager>default</securityManager>                                                                                                                
  //  </security-configuration>
  //
  public void addRealms(File securityConfigurationXml, List<String> realms) throws IOException {
    addRealms(securityConfigurationXml, realms.toArray(new String[realms.size()]));
  }

  public void addRealms(File securityConfigurationXml, String... realms) throws IOException {
    Document document = XMLParser.parse(securityConfigurationXml);
    Element e = document.getChild("/security-configuration/realms");
    for (String realm : realms) {
      e.addNode(new Element("realm").setText(realm));
    }
    writeResource(securityConfigurationXml, document);
  }

  private void addUsers(File securityXml, List<NexusProvisioningContext.User> users) throws IOException {
    Document document = XMLParser.parse(securityXml);
    Element e = document.getChild("/security/users");
    for (NexusProvisioningContext.User user : users) {
      Element userElement = new Element("user");
      userElement.addNode(new Element("id").setText(user.getUsername()));
      userElement.addNode(new Element("firstName").setText(user.getUsername()));
      userElement.addNode(new Element("password").setText(StringDigester.getSha1Digest(user.getPassword())));
      userElement.addNode(new Element("status").setText("active"));
      userElement.addNode(new Element("email").setText(user.getUsername() + "@yourcompany.com"));
      e.addNode(userElement);
    }
    writeResource(securityXml, document);
  }

  //
  // Add a plugin to the Nexus installation
  //
  public void addPlugin(String coord, File workDirectory) throws IOException, ArtifactResolutionException {
    File pluginsDirectory = new File(workDirectory, "plugin-repository");
    File pluginZip = artifactResolver.resolveArtifact(coord).getArtifact().getFile();
    unarchiver.unarchive(pluginZip, pluginsDirectory);
  }

  private void writeResource(File pom, Document document) throws IOException {

    String encoding = document.getEncoding();
    XMLWriter writer = new XMLWriter(encoding != null ? new OutputStreamWriter(new FileOutputStream(pom), encoding) : new OutputStreamWriter(new FileOutputStream(pom)));

    try {
      document.toXML(writer);
    } finally {
      IOUtil.close(writer);
    }
  }

}
