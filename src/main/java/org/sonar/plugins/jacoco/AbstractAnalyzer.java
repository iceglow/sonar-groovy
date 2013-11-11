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
package org.sonar.plugins.jacoco;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import org.apache.commons.lang.StringUtils;
import org.jacoco.core.analysis.*;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.runtime.WildcardMatcher;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.measures.CoverageMeasuresBuilder;
import org.sonar.api.measures.Measure;
import org.sonar.api.resources.JavaFile;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.ResourceUtils;
import org.sonar.api.utils.SonarException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * @author Evgeny Mandrikov
 */
public abstract class AbstractAnalyzer {

  public final void analyse(Project project, SensorContext context) {
    final File buildOutputDir = project.getFileSystem().getBuildOutputDir();
    if (!buildOutputDir.exists()) {
      JaCoCoUtils.LOG.info("Project coverage is set to 0% as build output directory does not exist: {}", buildOutputDir);
      return;
    }
    String path = getReportPath(project);
    File jacocoExecutionData = project.getFileSystem().resolvePath(path);

    WildcardMatcher excludes = new WildcardMatcher(Strings.nullToEmpty(getExcludes(project)));
    try {
      readExecutionData(jacocoExecutionData, buildOutputDir, context, excludes, project.getFileSystem().getSourceDirs());
    } catch (IOException e) {
      throw new SonarException(e);
    }
  }

  public final void readExecutionData(File jacocoExecutionData, File buildOutputDir, SensorContext context, WildcardMatcher excludes, List<File> sourceDirs) throws IOException {
    SessionInfoStore sessionInfoStore = new SessionInfoStore();
    ExecutionDataStore executionDataStore = new ExecutionDataStore();

    if (jacocoExecutionData == null || !jacocoExecutionData.exists() || !jacocoExecutionData.isFile()) {
      JaCoCoUtils.LOG.info("Project coverage is set to 0% as no JaCoCo execution data has been dumped: {}", jacocoExecutionData);
    } else {
      JaCoCoUtils.LOG.info("Analysing {}", jacocoExecutionData);
      ExecutionDataReader reader = new ExecutionDataReader(new FileInputStream(jacocoExecutionData));
      reader.setSessionInfoVisitor(sessionInfoStore);
      reader.setExecutionDataVisitor(executionDataStore);
      reader.read();
    }

    CoverageBuilder coverageBuilder = new CoverageBuilder();
    Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);
    analyzeAll(analyzer, buildOutputDir);

    int analyzedResources = 0;
    for (ISourceFileCoverage coverage : coverageBuilder.getSourceFiles()) {
      Resource resource = getResource(coverage, context, sourceDirs);
      if (resource != null) {
        if (!isExcluded(coverage, excludes)) {
          analyzeFile(resource, coverage, context);
        }
        analyzedResources++;
      }
    }
    if (analyzedResources == 0) {
      JaCoCoUtils.LOG.warn("Coverage information was not collected. Perhaps you forget to include debug information into compiled classes?");
    }
  }

  private static boolean isExcluded(ISourceFileCoverage coverage, WildcardMatcher excludesMatcher) {
    String name = coverage.getPackageName() + "/" + coverage.getName();
    return excludesMatcher.matches(name);
  }

  @VisibleForTesting
  static Resource getResource(ISourceFileCoverage coverage, SensorContext context, List<File> sourceDirs) {
    File file = null;

    for (File srcDir : sourceDirs) {
      file = new File(srcDir, coverage.getPackageName() + "/" + coverage.getName());
      if (file.exists()) break;
    }

    if (file == null) return null;

    org.sonar.api.resources.File resource = org.sonar.api.resources.File.fromIOFile(file, sourceDirs);

    org.sonar.api.resources.File resourceInContext = context.getResource(resource);
    if (null == resourceInContext) {
      return null; // Do not save measures on resource which doesn't exist in the context
    }
    if (ResourceUtils.isUnitTestClass(resourceInContext)) {
      return null; // Ignore unit tests
    }

    return resourceInContext;
  }

  /**
   * Copied from {@link Analyzer#analyzeAll(File)} in order to add logging.
   */
  private void analyzeAll(Analyzer analyzer, File file) {
    if (file.isDirectory()) {
      for (File f : file.listFiles()) {
        analyzeAll(analyzer, f);
      }
    } else if (file.getName().endsWith(".class")) {
      try {
        analyzer.analyzeAll(file);
      } catch (Exception e) {
        JaCoCoUtils.LOG.warn("Exception during analysis of file " + file.getAbsolutePath(), e);
      }
    }
  }

  private void analyzeFile(Resource resource, ISourceFileCoverage coverage, SensorContext context) {
    CoverageMeasuresBuilder builder = CoverageMeasuresBuilder.create();
    for (int lineId = coverage.getFirstLine(); lineId <= coverage.getLastLine(); lineId++) {
      final int hits;
      ILine line = coverage.getLine(lineId);
      switch (line.getInstructionCounter().getStatus()) {
        case ICounter.FULLY_COVERED:
        case ICounter.PARTLY_COVERED:
          hits = 1;
          break;
        case ICounter.NOT_COVERED:
          hits = 0;
          break;
        case ICounter.EMPTY:
          continue;
        default:
          JaCoCoUtils.LOG.warn("Unknown status for line {} in {}", lineId, resource);
          continue;
      }
      builder.setHits(lineId, hits);

      ICounter branchCounter = line.getBranchCounter();
      int conditions = branchCounter.getTotalCount();
      if (conditions > 0) {
        int coveredConditions = branchCounter.getCoveredCount();
        builder.setConditions(lineId, conditions, coveredConditions);
      }
    }

    saveMeasures(context, resource, builder.createMeasures());
  }

  protected abstract void saveMeasures(SensorContext context, Resource resource, Collection<Measure> measures);

  protected abstract String getReportPath(Project project);

  protected abstract String getExcludes(Project project);

}
