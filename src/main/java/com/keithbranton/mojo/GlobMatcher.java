package com.keithbranton.mojo;

import java.io.File;

import org.codehaus.plexus.util.AbstractScanner;

public class GlobMatcher {
	private static class GlobScanner extends AbstractScanner {
		private GlobScanner(final String[] globs) {
			setIncludes(globs);
		}

		@Override
		public void scan() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String[] getIncludedFiles() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String[] getIncludedDirectories() {
			throw new UnsupportedOperationException();
		}

		@Override
		public File getBasedir() {
			throw new UnsupportedOperationException();
		}

		public boolean matches(final String name) {
			// System.out.println("Glob Testing " + name);
			return isIncluded(name);
		}
	}

	private final String relativedir;
	private final String absolutedir;
	private final GlobScanner globScanner;

	public GlobMatcher(final File absolutedir, final File relativedir, final String[] globs) {
		this.absolutedir = absolutedir.getAbsolutePath();
		this.relativedir = relativedir.getAbsolutePath() + "/";
		// System.out.println("absolutedir: " + this.absolutedir + ", relativedir: " + this.relativedir);
		globScanner = new GlobScanner(globs);
	}

	public boolean matches(final File file) {
		return globScanner.matches(file.getAbsolutePath().replace(absolutedir, "/"));
	}

	public File makeFile(final String name) {
		return new File((name.startsWith("/") ? absolutedir : relativedir) + name);
	}
}