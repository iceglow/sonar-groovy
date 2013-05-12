/*
 * Sonar Groovy Plugin
 * Copyright (C) 2010 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */

package org.sonar.plugins.groovy.foundation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.AbstractSourceImporter;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.resources.ProjectFileSystem;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;

public class GroovySourceImporter extends AbstractSourceImporter {
  private static final Logger LOG = LoggerFactory.getLogger(GroovySourceImporter.class);

  private ProjectFileSystem projectFileSystem;

  /**
   * SONAR has no support for multiple source directories, except for adding endless crud using the Maven build helper plugin.
   * This is still unsatisfactory as the
   */
  public static Set<File> realFiles = new HashSet<File>();
  public static Set<File> realSourceDirs = new HashSet<File>();


  public GroovySourceImporter(Groovy groovy) {
    super(groovy);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

  @Override
  protected void analyse(ProjectFileSystem fileSystem, SensorContext context) {
    this.projectFileSystem = fileSystem;

    super.analyse(fileSystem, context);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  protected void parseDirs(SensorContext context, List<File> files, List<File> sourceDirs, boolean unitTest, Charset sourcesEncoding) {
    // problem is, for Groovy projects the source dirs is usually wrong and for Grails projects, its completely wrong. So lets check
    List<File> localSourceDirs = new ArrayList<File>(sourceDirs);
    List<File> localFiles = new ArrayList<File>(files);

    checkIncludes(localFiles, localSourceDirs, String.format("src/%s/groovy", unitTest ? "test" : "main"));

    // is this a Grails application?
    File grailsCheck = new File(projectFileSystem.getBasedir(), "grails-app");

    if (grailsCheck.exists()) {
      if (unitTest) {
        String grailsTestDirs = System.getProperty("sonar.grails.testDirs");

        if (grailsTestDirs == null) {
          checkIncludes(localFiles, localSourceDirs, "test/unit");
          checkIncludes(localFiles, localSourceDirs, "test/integration");
        } else {
          for(String testDir : grailsTestDirs.split(":")) {
            checkIncludes(localFiles, localSourceDirs, testDir);
          }
        }

      } else {
        String grailsDirs = System.getProperty("sonar.grails.dirs");

        if (grailsDirs == null) {
          checkIncludes(localFiles, localSourceDirs, "grails-app/conf");
          checkIncludes(localFiles, localSourceDirs, "grails-app/controllers");
          checkIncludes(localFiles, localSourceDirs, "grails-app/domain");
          checkIncludes(localFiles, localSourceDirs, "grails-app/services");
          checkIncludes(localFiles, localSourceDirs, "grails-app/taglib");
          checkIncludes(localFiles, localSourceDirs, "grails-app/util");
          checkIncludes(localFiles, localSourceDirs, "src/groovy");
          checkIncludes(localFiles, localSourceDirs, "src/java");

          grailsDirs = System.getProperty("sonar.grails.extraDirs");
        }

        if (grailsDirs != null) {
          for(String dir : grailsDirs.split(":")) {
            checkIncludes(localFiles, localSourceDirs, dir);
          }
        }
      }
    }

    // make sure the global holds test and non test directories
    realFiles.addAll(localFiles);
    realSourceDirs.addAll(localSourceDirs);

    super.parseDirs(context, localFiles, localSourceDirs, unitTest, sourcesEncoding);
  }

  private static final List<String> ignoredDirectories = Arrays.asList(".", "..", ".svn");

  private boolean recursiveAdd(List<File> realFiles, List<File> realSourceDirs, File current) {
    boolean foundFiles = false;

    for(File f : current.listFiles()) {
      if (f.isDirectory()) {
        if (!ignoredDirectories.contains(f.getName()))  {
          if (recursiveAdd(realFiles, realSourceDirs, f)) {
            foundFiles = true;
          }
        } else {
          LOG.debug(("skipped " + f.getAbsolutePath()));
        }
      } else {
        if (f.getName().endsWith(".groovy") || f.getName().endsWith(".java")) {
          realFiles.add(f);

          LOG.debug("added " + f.getAbsolutePath());
          foundFiles = true;
        }
      }
    }

    return foundFiles;
  }

  private void checkIncludes(List<File> realFiles, List<File> realSourceDirs, String offsetDir) {
    File dir = new File(projectFileSystem.getBasedir(), offsetDir);

    if (realSourceDirs.contains(dir)) {
      LOG.debug(String.format("%s already included", dir.getAbsolutePath()));
    } else if (!dir.exists()) {
      LOG.debug(String.format("%s does not exist", dir.getAbsolutePath()));
    } else {
      if (recursiveAdd(realFiles, realSourceDirs, dir)) {
        realSourceDirs.add(dir);
      }
    }
  }
}
