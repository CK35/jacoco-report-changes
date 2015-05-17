package de.ck35.maven.plugins.jacoco;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReportException;
import org.jacoco.maven.AbstractReportMojo;
import org.jacoco.maven.ReportMojo;

/**
 * Generate a JaCoCo coverage report for changed files. A class file will appear inside the report
 * when it has been changed compared to a specified branch (defaults to master).
 *  
 * @author Christian Kaspari
 */
@Mojo(defaultPhase=LifecyclePhase.VERIFY,
	  name="report-changes",
	  requiresProject=true,
	  threadSafe=true)
public class JacocoReportChangesMojo extends ReportMojo {

	protected static final String JAVA_SUFFIX = ".java";
	protected static final String CLASS_SUFFIX = "*.class";

	@Parameter(defaultValue="master")
	private String branchName;
	
	@Parameter(property="outputDirectory", defaultValue="${project.reporting.outputDirectory}/jacoco-changes")
	private File jacocoOutputDirectory;
	
	@Parameter(property="dataFile", defaultValue="${project.build.directory}/jacoco.exec")
	private File jacocoDataFile;
	
	@Parameter(property="project.reporting.outputEncoding", defaultValue="UTF-8")
	private String jacocoOutputEncoding;
	
	@Parameter(property="project.build.sourceEncoding", defaultValue="UTF-8")
	private String jacocoSourceEncoding;
	
	@Parameter(property="excludes")
	private List<String> jacocoExcludes;
	
	@Parameter(property="jacoco.skip", defaultValue="false")
	private boolean jacocoSkip;
	
	@Component MavenProject project;
	@Component Renderer siteRenderer;
	
	/**
	 * Load a list of changed class files.
	 * 
	 * @return Changed class files.
	 * @throws MojoExecutionException If loading failed.
	 */
	public List<String> loadIncludes() throws MojoExecutionException {
		List<Path> sourceRoots = getCompileSourceRootPaths();
		List<String> result = new ArrayList<>();
		for(String changedFile : loadChangedFiles(branchName)) {
			Path path = Paths.get(System.getProperty("user.dir"), changedFile).toAbsolutePath();
			if(!path.getFileName().toString().endsWith(JAVA_SUFFIX)) {
				continue;
			}
			for(Path root : sourceRoots) {
				if(path.startsWith(root)) {
					String className = path.subpath(root.getNameCount(), path.getNameCount()).toString().replace(JAVA_SUFFIX, CLASS_SUFFIX);
					result.add(className);
				}
			}
		}
		if(result.isEmpty()) {
			return Collections.singletonList("");
		} else {
			return result;			
		}
	}
	
	/**
	 * Inject all "default" values like outputDirectory or outputEncoding into the fields of super class.
	 * 
	 * @throws MojoExecutionException If injecting failed.
	 */
	public void injectDefaults() throws MojoExecutionException {
		inject(ReportMojo.class, "outputDirectory", jacocoOutputDirectory);
		inject(ReportMojo.class, "dataFile", jacocoDataFile);
		inject(AbstractReportMojo.class, "outputEncoding", jacocoOutputEncoding);
		inject(AbstractReportMojo.class, "sourceEncoding", jacocoSourceEncoding);
		inject(AbstractReportMojo.class, "skip", jacocoSkip);
	}
	
	/**
	 * Inject the includes and excludes values into the fields of super class.
	 * 
	 * @throws MojoExecutionException If injecting failed.
	 */
	public void injectIncludes() throws MojoExecutionException {
		inject(AbstractReportMojo.class, "includes", loadIncludes());
		inject(AbstractReportMojo.class, "excludes", jacocoExcludes);
	}
	
	/**
	 * Inject a value into the given field of the given class.
	 * 
	 * @param target The class which contains the declared field.
	 * @param fieldName The name of the declared field.
	 * @param inject The value which should be injected.
	 * @throws MojoExecutionException If injecting failed.
	 */
	public void inject(Class<?> target, String fieldName, Object inject) throws MojoExecutionException {
		try {
			Field field = target.getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(this, inject);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			throw new MojoExecutionException("Could not inject field: '" + fieldName + "!", e);
		}
	}
	
	@Override
	public void execute() throws MojoExecutionException {
		injectDefaults();
		super.execute();
	}
	
	@Override
	protected void executeReport(final Locale locale) throws MavenReportException {
		try {
			injectDefaults();
			injectIncludes();
		} catch (MojoExecutionException e) {
			throw new MavenReportException("Report generation failed!", e);
		}
		super.executeReport(locale);
	}
	
	/**
	 * Resolve the given path. If given path is not absolute it will be resolved as a
	 * child of current project base directory.
	 * 
	 * @param path The path to resolve.
	 * @return The absolute path.
	 */
	public Path absoluteProjectPath(String path) {
		Path result = Paths.get(path);
		if(result.isAbsolute()) {
			return result;
		} else {			
			return getProject().getBasedir().toPath().resolve(result);
		}
	}
	
	/**
	 * @return The list of compile source roots.
	 */
	public List<Path> getCompileSourceRootPaths() {
		List<Path> result = new ArrayList<>();
		for(Object path : getProject().getCompileSourceRoots()) {
			result.add(absoluteProjectPath((String) path));
		}
		return result;
	}
	
	/**
	 * Load a list of changed files. The given branch name is used for detecting changes.
	 * 
	 * @param branchName The branch to compare and detect changes.
	 * @return A list of changed files.
	 */
	public List<String> loadChangedFiles(String branchName) throws MojoExecutionException {
		try {
			Process gitDiffProcess = gitDiffProcess(branchName);
			try {
				List<String> result = new ArrayList<>();
				try(BufferedReader reader = new BufferedReader(new InputStreamReader(gitDiffProcess.getInputStream()))) {
					for(String line = reader.readLine() ; line != null ; line = reader.readLine()) {					
						result.add(line);
					}
				}
				if(result.isEmpty()) {
					StringBuilder messageBuilder = new StringBuilder();
					try(BufferedReader reader = new BufferedReader(new InputStreamReader(gitDiffProcess.getErrorStream()))) {
						for(String line = reader.readLine() ; line != null ; line = reader.readLine()) {					
							messageBuilder.append(line).append("\n");
						}
					}
					String message = messageBuilder.toString();
					if(!message.isEmpty()) {
						throw new MojoExecutionException("Calling git diff failed: \n" + message);
					}
				}
				return result;
			} finally {
				gitDiffProcess.destroy();
			}
		} catch(IOException e) {
			throw new MojoExecutionException("Could not load changed files from git!", e);
		}
	}

	/**
	 * Create a git diff process.
	 * 
	 * @param branchName The branch which should be compared.
	 * @return The git process.
	 * @throws IOException If process creation failed.
	 */
	protected Process gitDiffProcess(String branchName) throws IOException {
		return new ProcessBuilder(Arrays.asList("git", "diff", "--name-only", branchName)).start();
	}
	
	@Override
	public String getOutputName() {
		return "jacoco-changes/index";
	}
	@Override
	public String getName(Locale locale) {
		return "JaCoCo Changes Test";
	}
	@Override
	public MavenProject getProject() {
		return project;
	}
	public void setProject(MavenProject project) {
		this.project = project;
	}
	@Override
	public Renderer getSiteRenderer() {
		return siteRenderer;
	}
	public void setSiteRenderer(Renderer siteRenderer) {
		this.siteRenderer = siteRenderer;
	}
	public boolean isJacocoSkip() {
		return jacocoSkip;
	}
	public void setJacocoSkip(boolean jacocoSkip) {
		this.jacocoSkip = jacocoSkip;
	}
}