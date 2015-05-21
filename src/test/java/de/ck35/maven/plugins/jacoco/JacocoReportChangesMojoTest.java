package de.ck35.maven.plugins.jacoco;

import static de.ck35.maven.plugins.jacoco.JacocoReportChangesMojo.CLASS_SUFFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

public class JacocoReportChangesMojoTest {

	@Test
	public void testLoadIncludesWithEmptyChanges() throws MojoExecutionException {
		final List<String> changedFiles = Collections.emptyList();
		JacocoReportChangesMojo mojo = new JacocoReportChangesMojo() {
			@Override
			public List<String> loadChangedFiles(String branchName) throws MojoExecutionException {
				return changedFiles;
			}
		};
		MavenProject project = mock(MavenProject.class);
		when(project.getCompileSourceRoots()).thenReturn(Arrays.asList("src/main/java"));
		when(project.getBasedir()).thenReturn(new File(System.getProperty("user.dir")));
		mojo.setProject(project);
		List<String> result = mojo.loadIncludes();
		assertEquals(1, result.size());
		assertEquals("", result.get(0));
	}
	
	@Test
	public void testLoadIncludes() throws MojoExecutionException {
		final List<String> changedFiles = Arrays.asList("src/main/java/a/A.java",
		                                                "src/test/java/a/B.java",
		                                          		"src/main/java/a/C.txt");
		JacocoReportChangesMojo mojo = new JacocoReportChangesMojo() {
			@Override
			public List<String> loadChangedFiles(String branchName) throws MojoExecutionException {
				return changedFiles;
			}
		};
		MavenProject project = mock(MavenProject.class);
		when(project.getCompileSourceRoots()).thenReturn(Arrays.asList("src/main/java"));
		when(project.getBasedir()).thenReturn(new File(System.getProperty("user.dir")));
		mojo.setProject(project);
		assertEquals(Arrays.asList("a" + System.getProperty("file.separator") + "A" + CLASS_SUFFIX), mojo.loadIncludes());
	}
	
	@Test
	public void testInjectDefaults() throws MojoExecutionException {
		new JacocoReportChangesMojo().injectDefaults();
	}
	
	@Test
	public void testInjectIncludes() throws MojoExecutionException {
		JacocoReportChangesMojo mojo = new JacocoReportChangesMojo() {
			@Override
			public List<String> loadIncludes() {
				return Collections.emptyList();
			}
		};
		mojo.injectIncludes();
	}
	
	@Test
	public void testLoadChangedFiles() throws MojoExecutionException {
		final String expectedBranchName = "test-branch";
		final Process process = mock(Process.class);
		when(process.getInputStream()).thenReturn(new ByteArrayInputStream("changed".getBytes()));
		when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
		JacocoReportChangesMojo mojo = new JacocoReportChangesMojo() {
			@Override
			protected Process gitDiffProcess(String branchName) throws IOException {
				assertEquals(expectedBranchName, branchName);
				return process;
			}
		};
		List<String> result = mojo.loadChangedFiles(expectedBranchName);
		assertEquals(1, result.size());
		assertEquals("changed", result.get(0));
	}
	
	@Test(expected=MojoExecutionException.class)
	public void testLoadChangedFilesFailedOnProcessCreate() throws MojoExecutionException {
		JacocoReportChangesMojo mojo = new JacocoReportChangesMojo() {
			@Override
			protected Process gitDiffProcess(String branchName) throws IOException {
				throw new IOException("Test");
			}
		};
		mojo.loadChangedFiles("any");
	}
	
	@Test
	public void testLoadChangedFilesWithNoChanges() throws MojoExecutionException {
		final Process process = mock(Process.class);
		when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
		when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
		JacocoReportChangesMojo mojo = new JacocoReportChangesMojo() {
			@Override
			protected Process gitDiffProcess(String branchName) throws IOException {
				return process;
			}
		};
		assertTrue(mojo.loadChangedFiles("any").isEmpty());
	}
	
	@Test(expected=MojoExecutionException.class)
	public void testLoadChangedFilesFailedOnGitError() throws MojoExecutionException {
		final Process process = mock(Process.class);
		when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
		when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("GIT_ERROR".getBytes()));
		JacocoReportChangesMojo mojo = new JacocoReportChangesMojo() {
			@Override
			protected Process gitDiffProcess(String branchName) throws IOException {
				return process;
			}
		};
		mojo.loadChangedFiles("any");
	}
	
	@Test(expected=MojoExecutionException.class)
	public void testInjectError() throws MojoExecutionException {
		JacocoReportChangesMojo mojo = new JacocoReportChangesMojo();
		mojo.inject(JacocoReportChangesMojo.class, "not-existing-field", "value");
	}
	
	@Test
	public void testAbsoluteProjectPath() {
		MavenProject project = mock(MavenProject.class);
		JacocoReportChangesMojo mojo = new JacocoReportChangesMojo();
		Path usrDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		when(project.getBasedir()).thenReturn(usrDir.toFile());
		mojo.setProject(project);
		assertEquals(usrDir.resolve("a"), mojo.absoluteProjectPath(usrDir.resolve("a").toString()));
	}
	
	@Test
	public void testExecuteSkip() throws MojoExecutionException {
		JacocoReportChangesMojo mojo = new JacocoReportChangesMojo() {
			public List<String> loadIncludes() throws MojoExecutionException {
				fail();
				return null;
			};
		};
		mojo.setJacocoSkip(true);
		mojo.execute();
	}
	
	@Test
	public void testConstants() {
		MavenProject project = mock(MavenProject.class);
		Renderer siteRenderer = mock(Renderer.class);
		JacocoReportChangesMojo mojo = new JacocoReportChangesMojo();
		mojo.setProject(project);
		mojo.setSiteRenderer(siteRenderer);
		assertEquals(project, mojo.getProject());
		assertEquals(siteRenderer, mojo.getSiteRenderer());
		assertEquals("jacoco-changes/index", mojo.getOutputName());
		assertEquals("JaCoCo Changes Test", mojo.getName(null));
		assertFalse(mojo.isJacocoSkip());
	}
	
	@Test
	public void testCanGenerateReportReturnsFalseWhenSuperReturnsFalse() throws MojoExecutionException {
	    final List<String> changedFiles = Arrays.asList("a", "b", "c");
        JacocoReportChangesMojo mojo = new JacocoReportChangesMojo() {
            public java.util.List<String> loadIncludes() throws MojoExecutionException {
                return changedFiles;
            };
        };
        mojo.setJacocoSkip(true);
        mojo.injectDefaults();
        assertFalse(mojo.canGenerateReport());
	}
	
	@Test
	public void testisSkipGenerationWhenNoChangesFoundReturnsFalseOnDefault() {
	    assertFalse(new JacocoReportChangesMojo().isSkipGenerationWhenNoChangesFound());
	}
	
	@Test
	public void testCanGenerateReportWithChanges() {
	    final List<String> changedFiles = Arrays.asList("a", "b", "c");
	    JacocoReportChangesMojo mojo = new JacocoReportChangesMojo() {
	        public java.util.List<String> loadIncludes() throws MojoExecutionException {
	            return changedFiles;
	        };
	    };
	    assertTrue(mojo.canGenerateReport(true));
	}
	
	@Test
    public void testCanGenerateReportWithChangesAndSkip() {
        final List<String> changedFiles = Arrays.asList("a", "b", "c");
        JacocoReportChangesMojo mojo = new JacocoReportChangesMojo() {
            public java.util.List<String> loadIncludes() throws MojoExecutionException {
                return changedFiles;
            };
        };
        mojo.setSkipGenerationWhenNoChangesFound(true);
        assertTrue(mojo.canGenerateReport(true));
    }
	
	@Test
    public void testCanGenerateReportWithLoadError() {
        JacocoReportChangesMojo mojo = new JacocoReportChangesMojo() {
            public java.util.List<String> loadIncludes() throws MojoExecutionException {
                throw new MojoExecutionException("Test");
            };
        };
        mojo.setSkipGenerationWhenNoChangesFound(true);
        assertFalse(mojo.canGenerateReport(true));
    }
	
	@Test
    public void testCanGenerateReportTrueWithNoChanges() {
        final List<String> changedFiles = Collections.emptyList();
        JacocoReportChangesMojo mojo = new JacocoReportChangesMojo() {
            public java.util.List<String> loadIncludes() throws MojoExecutionException {
                return changedFiles;
            };
        };
        assertTrue(mojo.canGenerateReport(true));
    }
	
	@Test
    public void testCanGenerateReportTrueWithNoChangesAndSkip() {
        final List<String> changedFiles = Collections.emptyList();
        JacocoReportChangesMojo mojo = new JacocoReportChangesMojo() {
            public java.util.List<String> loadIncludes() throws MojoExecutionException {
                return changedFiles;
            };
        };
        mojo.setSkipGenerationWhenNoChangesFound(true);
        assertFalse(mojo.canGenerateReport(true));
    }
}