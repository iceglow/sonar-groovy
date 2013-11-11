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

package org.sonar.plugins.groovy;

import com.google.common.collect.ImmutableList;
import org.sonar.api.Properties;
import org.sonar.api.Property;
import org.sonar.api.SonarPlugin;
import org.sonar.plugins.groovy.cobertura.CoberturaMavenPluginHandler;
import org.sonar.plugins.groovy.cobertura.CoberturaSensor;
import org.sonar.plugins.groovy.codenarc.CodeNarcProfileExporter;
import org.sonar.plugins.groovy.codenarc.CodeNarcProfileImporter;
import org.sonar.plugins.groovy.codenarc.CodeNarcRuleRepository;
import org.sonar.plugins.groovy.codenarc.CodeNarcSensor;
import org.sonar.plugins.groovy.codenarc.SonarWayProfile;
import org.sonar.plugins.groovy.foundation.Groovy;
import org.sonar.plugins.groovy.foundation.GroovyColorizerFormat;
import org.sonar.plugins.groovy.foundation.GroovyCpdMapping;
import org.sonar.plugins.groovy.foundation.GroovySourceImporter;
import org.sonar.plugins.groovy.surefire.SurefireSensor;

import java.util.List;

@Properties({
  @Property(
    key = GroovyPlugin.CODENARC_REPORT_PATH,
    name = "Report file",
    description = "Path (absolute or relative) to CodeNarc XML report in case generation is not handle by the plugin.",
    module = true,
    project = true,
    global = false
  )
})
public class GroovyPlugin extends SonarPlugin {

  public static final String CODENARC_REPORT_PATH = "sonar.groovy.codenarc.reportPath";

  public List<?> getExtensions() {
    return ImmutableList.of(
      // Foundation
      Groovy.class,
      GroovyColorizerFormat.class,
      GroovySourceImporter.class,  // comes up first as it gathers all the source directories and files
      GroovyCpdMapping.class,
      GroovyCommonRulesEngineProvider.class,
      // CodeNarc
      CodeNarcRuleRepository.class,
      CodeNarcProfileExporter.class,
      CodeNarcProfileImporter.class,
      CodeNarcSensor.class,
      SonarWayProfile.class,
      // Main sensor
      GroovySensor.class,

      // Cobertura
      CoberturaSensor.class,
      CoberturaMavenPluginHandler.class,
      // Surefire
      SurefireSensor.class);
  }

}
