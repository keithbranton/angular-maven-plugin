package com.keithbranton.mojo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.Scanner;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

/**
 * Bundle an angularjs application for modular production deployment
 * 
 * Combine all files for each module in a project into a single module file
 * 
 * @author Keith Branton
 */
@Mojo(name = "join"// , defaultPhase = LifecyclePhase.GENERATE_SOURCES
)
public class JoinMojo extends AbstractMojo {
	// plexus injected fields first
	@Parameter(defaultValue = "${project}", readonly = true)
	protected MavenProject project;

	/**
	 * Specifies the location of the files to join.
	 */
	@Parameter(defaultValue = "${basedir}/src/main/js/", required = true)
	private File source;

	/**
	 * Filename of the main.js file
	 */
	@Parameter(defaultValue = "main.js")
	private String main;

	/**
	 * Filename of the app.js file
	 */
	@Parameter(defaultValue = "app.js")
	private String app;

	/**
	 * Pattern for modules
	 */
	@Parameter(defaultValue = "**/*Module.js", required = true)
	private String modules;

	/**
	 * Comma separated list of patterns of the templates to process
	 */
	@Parameter(defaultValue = "*.html,utility/*.html")
	private String templates;

	/**
	 * Comma separated list of patterns that identify files to be joined
	 */
	@Parameter(defaultValue = "/js/**/*.js")
	private String joinable;

	/**
	 * Location for the generated files
	 */
	@Parameter(defaultValue = "${target.dir}", required = true)
	private File target;

	/**
	 * Prefix to put before the cache key
	 */
	@Parameter(defaultValue = "")
	private final String prefix = "";

	@Component(role = org.sonatype.plexus.build.incremental.BuildContext.class)
	private BuildContext buildContext;

	// Local fields below this point
	private File mainFile, appFile;
	private String[] modulesArray;
	private String[] templatesArray;
	private String[] joinableArray;
	private final Map<String, String> moduleMap = new HashMap<>();

	/** @see org.apache.maven.plugin.Mojo#execute() */
	@Override
	public void execute() throws MojoExecutionException {
		long start = System.currentTimeMillis();
		try {
			mainFile = new File(source, main);
			appFile = new File(source, app);
			modulesArray = modules == null ? null : modules.split(",");
			templatesArray = templates == null ? null : templates.split(",");
			joinableArray = joinable == null ? null : joinable.split(",");

			getLog().info("-------------------------------------------------");
			getLog().info("---Join Mojo ------------------------------------");
			getLog().info("---source: " + source.getAbsolutePath());
			getLog().info("---main: " + mainFile);
			getLog().info("---app: " + appFile);
			getLog().info("---modules: " + (modulesArray == null ? "null" : Arrays.asList(modulesArray)));
			getLog().info("---templates: " + (templatesArray == null ? "null" : Arrays.asList(templatesArray)));
			getLog().info("---joinable: " + (joinableArray == null ? "null" : Arrays.asList(joinableArray)));
			getLog().info("---target: " + target.getAbsolutePath());
			getLog().info("---prefix: \"" + prefix + "\"");
			getLog().info("-------------------------------------------------");

			// first make a list of all source files
			List<Module> modules = new ArrayList<Module>();
			modules.add(new Module(mainFile, true));
			modules.add(new Module(appFile, false));
			for (File module : findModules()) {
				modules.add(new Module(module, false));
			}

			int count = 0;

			// process all the files except main - since they update the moduleMap array
			for (final Module module : modules) {
				if (!module.isMain() && module.isStale()) {
					processModule(module, moduleMap);
					count++;
				}
			}

			// process the main file last
			for (final Module module : modules) {
				if (module.isMain() && module.isStale()) {
					processMain(module);
					count++;
				}
			}

			// TODO delete files that should no longer be in target?

			if (count == 0) {
				getLog().info("Join:: Nothing to do.");
				return;
			}
		} catch (Exception e) {
			getLog().error(e);
			throw new MojoExecutionException("Join:: failed.", e);
		} finally {
			getLog().info("Join:: took " + (System.currentTimeMillis() - start) + "ms");
		}
	}

	private String shorten(final File file) {
		return shorten(file.getAbsolutePath());
	}

	private String shorten(final String absolute) {
		return absolute.replace(source.getAbsolutePath(), "");
	}

	private void processMain(final Module main) throws Exception {
		String contents = Files.toString(mainFile, Charsets.UTF_8).trim();

		// getLog().info("moduleMap: " + moduleMap);
		for (Map.Entry<String, String> entry : moduleMap.entrySet()) {
			// getLog().info("Replacing: " + entry.getKey() + " with " + entry.getValue());
			contents = contents.replace(entry.getKey(), entry.getValue());
		}

		// finally emit the output file
		emit("main", contents, main.getTarget());

		buildContext.refresh(target);
	}

	private void processModule(final Module module, final Map<String, String> moduleMap) throws Exception {
		// getLog().info("moduleName: " + moduleName);
		List<String> lines = new ArrayList<>();

		lines.add("define([ \""
				+ Joiner.on("\", \"").join(
						Iterables.concat(module.getReferences().keySet(),
								Sets.difference(module.getExternalDeps(), module.getReferences().keySet()))) + "\" ], function("
				+ Joiner.on(", ").join(module.getReferences().values()) + ") {\n");
		for (File dep : module.getInternalDeps()) {
			lines.add(module.getContents(dep).replaceAll("^\\s*define.*?function\\s*\\([^\\)]*\\)\\s*\\{", "(function() {")
					.replaceAll("\\s*\\}\\s*\\)\\s*;?\\s*$", "\n})();\n"));
		}
		// process the templates
		if (module.hasTemplates()) {
			lines.add("angular.module(\"" + module.getName() + "Templates\", []).run([\"$templateCache\", function($templateCache) {");
			for (final File file : module.getTemplates()) {
				String cacheKey = prefix + shorten(file);

				List<String> fileLines = null;
				try {
					fileLines = FileUtils.readLines(file);
				} catch (IOException ex) {
					throw new MojoExecutionException("Join:: Unable to read template file: " + file.getAbsolutePath(), ex);
				}
				if (fileLines.isEmpty()) {
					lines.add("\t$templateCache.put(\"" + cacheKey + "\", \"\");");
				} else {
					lines.add("\t$templateCache.put(\"" + cacheKey + "\",");
					for (String line : fileLines) {
						lines.add("\t\"" + line.replace("\\", "\\\\").replace("\"", "\\\"") + "\\n\" +");
					}
					lines.set(lines.size() - 1, StringUtils.chomp(lines.get(lines.size() - 1), "\\n\" +") + "\");");
				}

				lines.add("");
			}
			lines.add("}]);\n");
		}

		// now the module - last because of the return
		String moduleContents = module
				.getContents()
				.replaceAll("^\\s*define.*?function\\s*\\([^\\)]*\\)\\s*\\{", "return (function() {")
				.replaceAll("\\s*\\}\\s*\\)\\s*;?\\s*$", "\n})();")
				.replaceFirst(".module\\s*\\(([^,]+)\\s*,\\s*\\[\\s*([^\\]]+)\\]",
						".module($1, [ \"" + module.getName() + "Templates\", $2]")//
				.replaceFirst(".module\\s*\\(([^,]+)\\s*,\\s*\\[\\s*\\]", ".module($1, [ \"" + module.getName() + "Templates\" ]");
		lines.add(moduleContents);

		// the end for the define
		lines.add("});");

		// finally emit the output file
		emit(module.getName(), Joiner.on("\n").join(lines), module.getTarget());

		buildContext.refresh(target);
	}

	private void emit(final String moduleName, final String source, final File targetFile) throws MojoExecutionException {
		try {
			getLog().info("Join:: Writing output file: " + targetFile.getAbsolutePath());
			Files.write(source, targetFile, Charsets.UTF_8);
		} catch (final IOException ex) {
			throw new MojoExecutionException("Join:: Unable to write output file: " + targetFile.getAbsolutePath(), ex);
		}
	}

	private List<File> findModules() {
		final DirectoryScanner scanner = new DirectoryScanner();
		scanner.setBasedir(source);
		return findFiles(scanner);
	}

	private List<File> findFiles(final Scanner scanner) {
		final List<File> results = new ArrayList<File>();
		scanner.setIncludes(modulesArray);
		scanner.scan();
		for (final String name : scanner.getIncludedFiles()) {
			results.add(new File(scanner.getBasedir(), name));
		}
		return results;
	}

	private class Module {
		private final File file;
		private final File target;
		private final boolean main;
		private final String name;
		private final String contents;
		private final Map<String, String> references = new HashMap<>();
		private final Set<String> externalDeps = new HashSet<>();
		private final Set<File> internalDeps = new HashSet<>();
		private final Map<File, String> internalDepContents = new HashMap<>();
		private final List<File> templates;

		private Module(final File file, final boolean main) throws IOException {
			this.file = file;
			this.target = makeTarget(file);
			this.main = main;
			name = file.getAbsolutePath().replace(file.getParent() + "/", "").replaceAll("\\.js$", "");
			contents = Files.toString(file, Charsets.UTF_8).trim();
			templates = findTemplates(file.getParentFile());
			if (!main) {
				findDependencies(file, contents, internalDeps, internalDepContents, externalDeps, references);
			}
		}

		private File getTarget() {
			if (!target.getParentFile().exists()) {
				target.getParentFile().mkdirs();
			}
			return target;
		}

		private boolean isMain() {
			return main;
		}

		private String getContents() {
			return contents;
		}

		private String getContents(final File dep) {
			return internalDepContents.get(dep);
		}

		private String getName() {
			return name;
		}

		private Map<String, String> getReferences() {
			return references;
		}

		private Set<String> getExternalDeps() {
			return externalDeps;
		}

		private Set<File> getInternalDeps() {
			return internalDeps;
		}

		private List<File> getTemplates() {
			return templates;
		}

		private boolean hasTemplates() {
			return !templates.isEmpty();
		}

		private boolean isStale() {
			if (!target.exists()) {
				return true;
			}

			long result = file.lastModified();
			for (File file : internalDeps) {
				result = Math.max(result, file.lastModified());
			}
			for (File file : templates) {
				result = Math.max(result, file.lastModified());
			}
			return result > target.lastModified();
		}

		private Set<File> findDependencies(final File startFile, final String contents, final Set<File> internal,
				final Map<File, String> contentsMap, final Set<String> external, final Map<String, String> references) throws IOException {
			// getLog().info("Checking file: " + startFile.getAbsolutePath());
			File startDir = startFile.getParentFile();
			GlobMatcher globMatcher = new GlobMatcher(source.getParentFile(), startDir, joinableArray);
			try {
				Matcher matcher = Pattern.compile(
						"^\\s*define\\s*\\(\\s*\\[\\s*([^\\]]+)\\s*\\]\\s*,\\s*function\\s*\\(([^\\)]*)\\)\\s*\\{.*$", Pattern.DOTALL)
						.matcher(contents);
				if (matcher.matches()) {
					String[] deps = matcher.group(1).split("\\s*,\\s*");
					String[] refs = matcher.group(2).split("\\s*,\\s*");
					// getLog().info("Found deps: " + Arrays.asList(deps) + ", refs: " + Arrays.asList(refs));
					int i = 0;
					for (String dep : deps) {
						File depFile = globMatcher.makeFile(dequote(dep));
						if (globMatcher.matches(depFile)) {
							if (!internal.contains(depFile)) {
								String depContents = Files.toString(depFile, Charsets.UTF_8).trim();
								internal.add(depFile);
								contentsMap.put(depFile, depContents);
								findDependencies(depFile, depContents, internal, contentsMap, external, references);
							}
						} else {
							external.add(dequote(dep));
							if (i < refs.length) {
								references.put(dequote(dep), refs[i]);
							}
							// getLog().info(
							// "Processed external dependency: " + dep + ", external: " + external + ", references: " + references);
						}
						i++;
					}
					// getLog().info("Found define clause, dependency: " + internal);
				} else {
					getLog().warn("Join:: No define found, contents: " + contents);
				}
				return internal;
			} catch (Exception e) {
				throw new RuntimeException("oops, contents: " + contents, e);
			}

		}

		private String dequote(final String quoted) {
			String input = StringUtils.trim(quoted);
			if (input.length() > 1 && input.startsWith("'") && input.endsWith("'")) {
				return StringUtils.strip(input, "'");
			}
			if (input.length() > 1 && input.startsWith("\"") && input.endsWith("\"")) {
				return StringUtils.strip(input, "\"");
			}
			return input;
		}

		private List<File> findTemplates(final File baseDir) {
			final DirectoryScanner scanner = new DirectoryScanner();
			scanner.setBasedir(baseDir);
			scanner.setIncludes(templatesArray);
			scanner.addDefaultExcludes();
			scanner.scan();
			final List<File> results = new ArrayList<File>();
			for (final String name : scanner.getIncludedFiles()) {
				results.add(new File(scanner.getBasedir(), name));
			}
			Collections.sort(results);
			return results;
		}

	}

	private File makeTarget(final File file) {
		// getLog().info("makeTarget called for file: " + file);
		String path = file.getAbsolutePath().replace(source.getAbsolutePath(), target.getAbsolutePath());
		List<String> parts = Arrays.asList(path.split("/"));
		if (parts.size() >= 2) {
			String moduleFolder = parts.get(parts.size() - 2);
			if (parts.get(parts.size() - 1).equals(moduleFolder + "Module.js")) {
				// getLog().info("makeTarget found moduleFolder: " + moduleFolder);
				String fullPath = path.replace(target.getAbsolutePath(), "").replaceAll("\\.js$", "");
				path = path.replace("/" + moduleFolder + "/" + moduleFolder + "Module.js", "/" + moduleFolder + "Module.js");
				moduleMap.put(fullPath, path.replace(target.getAbsolutePath(), "").replaceAll("\\.js$", ""));
			}
		}
		// getLog().info("makeTarget returning: " + path);
		return new File(path);
	}
}
