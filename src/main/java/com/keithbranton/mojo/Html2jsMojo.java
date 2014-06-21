package com.keithbranton.mojo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.Scanner;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

/**
 * Maven/Java approximation of grunt-html2js functionality
 * 
 * @author Keith Branton
 */
@Mojo(name = "html2js", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class Html2jsMojo extends AbstractMojo {

	// plexus injected fields first
	@Parameter(defaultValue = "${project}", readonly = true)
	protected MavenProject project;

	/**
	 * Specifies the source of the template files.
	 */
	@Parameter(defaultValue = "${basedir}/src/main/templates/", required = true)
	private File sourceDir;

	/**
	 * Comma separated list of patterns to identify files to be treated as templates
	 */
	@Parameter(defaultValue = "**/*.html")
	private String include;

	/**
	 * Prefix to put before the cache key
	 */
	@Parameter
	private String prefix;

	/**
	 * Comma separated list of patterns to identify files to be ignored
	 */
	@Parameter
	private String exclude;

	/**
	 * Location for the generated templates js file
	 */
	@Parameter(defaultValue = "${basedir}/src/main/generated/js/templates.js", required = true)
	private File target;

	/**
	 * A flag to indicate if a require.js compatible wrapper should be written around the output
	 */
	@Parameter(defaultValue = "false", required = true)
	private boolean addRequireWrapper;

	@Component(role = org.sonatype.plexus.build.incremental.BuildContext.class)
	private BuildContext buildContext;

	// Local fields below this point
	private String[] includes;
	private String[] excludes;

	/** @see org.apache.maven.plugin.Mojo#execute() */
	@Override
	public void execute() throws MojoExecutionException {
		long start = System.currentTimeMillis();
		try {
			includes = include == null ? null : include.split(",");
			excludes = exclude == null ? null : exclude.split(",");
			prefix = prefix == null ? "" : prefix;

			getLog().debug("-------------------------------------------------");
			getLog().debug("---Html2js Mojo ---------------------------------");
			getLog().debug("---sourceDir: " + sourceDir.getAbsolutePath());
			getLog().debug("---includes: " + (includes == null ? "null" : Arrays.asList(includes)));
			getLog().debug("---excludes: " + (excludes == null ? "null" : Arrays.asList(excludes)));
			getLog().debug("---target: " + target.getAbsolutePath());
			getLog().debug("---addRequireWrapper: " + addRequireWrapper);
			getLog().debug("---prefix: \"" + prefix + "\"");
			getLog().debug("-------------------------------------------------");

			if (!isBuildNeeded()) {
				getLog().info("Html2js:: Nothing to do");
				return;
			}

			if (!target.getParentFile().exists()) {
				target.getParentFile().mkdirs();
			}

			try {
				doIt();
			} catch (final Exception e) {
				throw new MojoExecutionException("", e);
			}
		} finally {
			getLog().info("Html2js:: took " + (System.currentTimeMillis() - start) + "ms");
		}
	}

	/**
	 * We can skip if no files were deleted, modified or added since the last build AND the target file is still there
	 * 
	 * @return true if a build is needed, otherwise false
	 */
	private boolean isBuildNeeded() {
		if (!buildContext.isIncremental()) {
			// always needed if we're not doing an incremental build
			getLog().info("Html2js:: full build");
			return true;
		}

		// ensure the target exists
		if (!target.exists()) {
			getLog().info("Html2js:: detected target file missing");
			return true;
		}

		// check for any deleted files
		List<File> deleted = findFiles(buildContext.newDeleteScanner(sourceDir));
		for (File deletedFile : deleted) {
			getLog().info("Html2js:: detected deleted template: " + shorten(deletedFile));
		}
		// next check for any new/changed files
		List<File> changed = findFiles(buildContext.newScanner(sourceDir));
		for (File changedFile : changed) {
			getLog().info("Html2js:: detected new/changed template: " + shorten(changedFile));
		}
		if (changed.size() > 0 || deleted.size() > 0) {
			return true;
		}

		// determine the last modified template
		long lastModified = 0;
		File lastModifiedFile = null;
		for (File templateFile : findFiles()) {
			if (templateFile.lastModified() > lastModified) {
				lastModifiedFile = templateFile;
				lastModified = templateFile.lastModified();
			}
		}

		// check if the target is as recent as the last modified template
		if (lastModifiedFile != null && !buildContext.isUptodate(target, lastModifiedFile)) {
			getLog().info("Html2js:: target file was changed or is older than " + shorten(lastModifiedFile));
			return true;
		}
		return false;
	}

	private String shorten(final File file) {
		return shorten(file.getAbsolutePath());
	}

	private String shorten(final String absolute) {
		return absolute.replace(sourceDir.getAbsolutePath(), "");
	}

	private void doIt() throws Exception {
		if (sourceDir == null || !sourceDir.exists()) {
			throw new MojoExecutionException("Html2js:: Could not find the source folder: " + sourceDir.getAbsolutePath());
		}
		// first make a list of all templates
		List<File> files = findFiles();
		Collections.sort(files);
		List<String> lines = new ArrayList<>();

		if (addRequireWrapper) {
			lines.add("define(['angular'], function (angular){");
			lines.add("");
		}

		for (final File file : files) {
			getLog().debug("Html2js:: found: " + file.getName());
		}

		lines.add("angular.module('templates-main', ['" + Joiner.on("', '").join(Lists.transform(files, new Function<File, String>() {
			@Override
			public String apply(final File file) {
				return prefix + file.getAbsolutePath().replace(sourceDir.getAbsolutePath(), "");
			}
		})) + "']);");
		lines.add("");

		for (final File file : files) {
			String shortName = prefix + file.getAbsolutePath().replace(sourceDir.getAbsolutePath(), "");
			lines.add("angular.module('" + shortName + "', []).run(['$templateCache', function($templateCache) {");

			List<String> fileLines = null;
			try {
				fileLines = FileUtils.readLines(file);
			} catch (IOException ex) {
				throw new MojoExecutionException("Html2js:: Unable to read template file: " + file.getAbsolutePath(), ex);
			}
			if (fileLines.isEmpty()) {
				lines.add("\t$templateCache.put('" + shortName + "', \"\");");
			} else {
				lines.add("\t$templateCache.put('" + shortName + "',");
				for (String line : fileLines) {
					lines.add("\t\"" + line.replace("\\", "\\\\").replace("\"", "\\\"") + "\\n\" +");
				}
				lines.set(lines.size() - 1, StringUtils.chomp(lines.get(lines.size() - 1), "\\n\" +") + "\");");
			}

			lines.add("}]);");
			lines.add("");
		}

		if (addRequireWrapper) {
			lines.add("");
			lines.add("return null;");
			lines.add("});");
		}

		// finally emit the output file
		try {
			getLog().info("Html2js:: Writing output file: " + target.getAbsolutePath());
			FileUtils.writeLines(target, lines);
		} catch (final IOException ex) {
			throw new MojoExecutionException("Html2js:: Unable to write output file: " + target.getAbsolutePath(), ex);
		}

		buildContext.refresh(target);
	}

	private List<File> findFiles() {
		final DirectoryScanner scanner = new DirectoryScanner();
		scanner.setBasedir(sourceDir);
		return findFiles(scanner);
	}

	private List<File> findFiles(final Scanner scanner) {
		final List<File> results = new ArrayList<File>();
		if (includes != null && includes.length > 0) {
			scanner.setIncludes(includes);
		}
		if (excludes != null && excludes.length > 0) {
			scanner.setExcludes(excludes);
		}
		scanner.addDefaultExcludes();
		scanner.scan();
		for (final String name : scanner.getIncludedFiles()) {
			results.add(new File(scanner.getBasedir(), name));
		}
		return results;
	}
}
